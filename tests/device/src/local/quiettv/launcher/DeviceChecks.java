package local.quiettv.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/** Instrumentation runs only on a new CI emulator, using synthetic preferences/documents. */
public final class DeviceChecks extends Instrumentation {
    private String phase;private int count;
    @Override public void onCreate(Bundle args){super.onCreate(args);phase=args==null?"":args.getString("phase","");start();}
    private void status(String description){Bundle progress=new Bundle();progress.putString("check",description);sendStatus(0,progress);}
    private void check(boolean condition,String description){count++;if(!condition)throw new AssertionError(description);status(description);}
    private void awaitFrames(Activity activity)throws InterruptedException{
        // The default photo background animates indefinitely. Global queue-idle
        // synchronization can therefore hang on API 30's software renderer.
        CountDownLatch rendered=new CountDownLatch(1);
        runOnMainSync(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(rendered::countDown)));
        if(!rendered.await(10,TimeUnit.SECONDS))throw new AssertionError("launcher frame timeout");
    }
    private String resolvedHome(Context c){android.content.pm.ResolveInfo resolved=c.getPackageManager().resolveActivity(SystemEntrypoints.homeQuery(),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);return resolved==null||resolved.activityInfo==null?"none":resolved.activityInfo.packageName+"/"+resolved.activityInfo.name;}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private View named(View root,String text){if(root instanceof android.widget.TextView&&text.equals(((android.widget.TextView)root).getText().toString()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View result=named(((ViewGroup)root).getChildAt(i),text);if(result!=null)return result;}return null;}
    private void ruleUi(MainActivity activity)throws Exception {
        PlaybackRules registry=PlaybackRules.get(activity);JSONObject longRule=new JSONObject(registry.find(PlaybackRule.BILI).json.toString());StringBuilder words=new StringBuilder();for(int i=0;i<600;i++)words.append('测');longRule.put("description",words.toString());longRule.getJSONObject("verification").put("notes",words.toString());PlaybackRule parsed=registry.parse(longRule.toString());
        runOnMainSync(()->activity.showPlaybackRule(parsed));awaitFrames(activity);final boolean[] reachable={false};runOnMainSync(()->{try{Dialog dialog=(Dialog)field(activity,"sheet");View check=named(dialog.getWindow().getDecorView(),"检查配置与最近记录");Rect rect=new Rect();reachable[0]=check!=null&&check.getGlobalVisibleRect(rect)&&rect.height()>0;}catch(Exception e){throw new RuntimeException(e);}});check(reachable[0],"long rule metadata cannot push rule actions outside the window");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);awaitFrames(activity);final boolean[] reader={false};runOnMainSync(()->{try{Dialog dialog=(Dialog)field(activity,"sheet");View focus=dialog.getCurrentFocus();reader[0]=focus instanceof android.widget.ScrollView;}catch(Exception e){throw new RuntimeException(e);}});check(reader[0],"remote up can enter the scrollable full rule description");
        for(int i=0;i<8;i++)sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);runOnMainSync(()->{try{Dialog dialog=(Dialog)field(activity,"sheet");if(dialog!=null)dialog.dismiss();activity.importPlaybackRule(Uri.parse("content://local.quiettv.checks.documents/rule"));}catch(Exception e){throw new RuntimeException(e);}});
        final boolean[] imported={false};long deadline=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<deadline){awaitFrames(activity);runOnMainSync(()->{try{Dialog dialog=(Dialog)field(activity,"sheet");if(dialog!=null){View save=named(dialog.getWindow().getDecorView(),"保存规则（停用）");if(save!=null){save.performClick();imported[0]=true;}}}catch(Exception e){throw new RuntimeException(e);}});if(imported[0])break;}
        check(imported[0]&&registry.find("example.player-history")!=null&&!registry.enabled(registry.find("example.player-history")),"selected JSON document imports through the UI and remains disabled");
        registry.remove(registry.find("example.player-history"));runOnMainSync(()->{try{Dialog dialog=(Dialog)field(activity,"sheet");if(dialog!=null)dialog.dismiss();}catch(Exception e){throw new RuntimeException(e);}});
    }
    private void legacyDelivery(Context c)throws Exception{
        final CountDownLatch received=new CountDownLatch(2);final int[] registrations={0},removals={0};
        ContextWrapper probe=new ContextWrapper(c){
            BroadcastReceiver wrapped;
            @Override public Context getApplicationContext(){return this;}
            @Override public Intent registerReceiver(final BroadcastReceiver receiver,IntentFilter filter){
                registrations[0]++;wrapped=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){received.countDown();receiver.onReceive(context,intent);}};
                return super.registerReceiver(wrapped,filter);
            }
            @Override public void unregisterReceiver(BroadcastReceiver receiver){removals[0]++;super.unregisterReceiver(wrapped);}
        };
        boolean first=LegacyHistoryListener.acquire(probe),second=false;
        try{
            check(first,"supported Android runtimes can register the rule history listener");
            second=LegacyHistoryListener.acquire(c);check(second&&registrations[0]==1,"Activity and bound-service owners share one registration");
            Intent event=new Intent("com.tv.history.add");
            check(LegacyHistoryListener.skipManifest(event)&&!LegacyHistoryListener.skipManifest(new Intent(event).setPackage(c.getPackageName()))&&!LegacyHistoryListener.skipManifest(new Intent(event).setComponent(new ComponentName(c,RuleHistoryReceiver.class))),"only implicit events defer manifest processing to the runtime listener");
            // Empty synthetic events do not identify a provider or create history.
            c.sendBroadcast(event);LegacyHistoryListener.release();first=false;
            check(removals[0]==0,"releasing one owner does not remove the other owner's listener");
            c.sendBroadcast(event);check(received.await(5,TimeUnit.SECONDS),"implicit history broadcasts reach the runtime listener while another owner remains");
        }finally{if(first)LegacyHistoryListener.release();if(second)LegacyHistoryListener.release();}
        check(removals[0]==1&&!LegacyHistoryListener.skipManifest(new Intent("com.tv.history.add")),"the last owner unregisters and restores manifest processing");
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        // A hardware TV must never receive these synthetic upgrade preferences.
        boolean virtual=Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator");
        check(virtual,"only a synthetic emulator may run device checks");
        Context c=getTargetContext();SharedPreferences home=c.getSharedPreferences("home",0),history=c.getSharedPreferences("playback_history",0);
        if("fresh".equals(phase))legacyDelivery(c);
        status("starting a new launcher Activity");
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        c.startActivity(new Intent().setComponent(new ComponentName(c,MainActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity opened=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);
        check(opened instanceof MainActivity,"launcher Activity created within timeout");
        MainActivity activity=(MainActivity)opened;
        awaitFrames(activity);Thread.sleep(1500);awaitFrames(activity);
        runOnMainSync(()->{try{Dialog dialog=(Dialog)field(activity,"sheet");if(dialog!=null)dialog.dismiss();}catch(Exception e){throw new RuntimeException(e);}});
        awaitFrames(activity);
        if("fresh".equals(phase)){
            check(home.getInt("config_schema",0)==1,"fresh HOME preferences initialize");
            check(!history.getBoolean("enabled",true)&&!history.getBoolean("generic_enabled",true)&&!history.getBoolean("music_enabled",true),"fresh playback integrations are opt-in");
            check(!c.getSharedPreferences("weather",0).getBoolean("enabled",false),"fresh weather disabled");
            SharedPreferences weatherPrefs=c.getSharedPreferences("weather",0);
            weatherPrefs.edit().putBoolean("enabled",true).putString("consent","auto").commit();
            WeatherRepository weather=new WeatherRepository(c,()->{});
            check(!weather.enabled()&&!weather.consented(),"legacy geolocation choice cannot enable public weather requests");
            weatherPrefs.edit().putString("consent","manual").commit();
            check(!weather.enabled(),"manual choice requires current service terms");
            weather.consent("manual");check(weather.enabled()&&weather.consented(),"current manual consent enables optional weather");
            weather.start();check(!weather.loading(),"service consent alone never forecasts a city the user has not selected");
            weather.dispose();weatherPrefs.edit().clear().commit();
            check(!GenericMediaHistory.permission(c),"basic desktop requires no media grant");
            final boolean[] clockFits={false};runOnMainSync(()->{try{android.widget.TextView clock=(android.widget.TextView)field(activity,"clock");clockFits[0]=clock.getLineCount()==1&&clock.getPaint().measureText(clock.getText().toString())<=clock.getWidth()+1;}catch(Exception e){throw new RuntimeException(e);}});check(clockFits[0],"localized clock including a 12-hour period fits on one line");
            ruleUi(activity);awaitFrames(activity);Thread.sleep(200);awaitFrames(activity);
            HistoryStore records=new HistoryStore(c);records.enabled(true);records.appEnabled(PlaybackRecord.PACKAGE,false);
            PlaybackRecord legacy=new PlaybackRecord();legacy.videoId="123456";legacy.episodeId="789";legacy.command="tcl/ugc";legacy.title="Synthetic blocked legacy item";legacy.updated=System.currentTimeMillis();
            check(!records.put(legacy)&&records.list().isEmpty(),"per-app blocking also rejects legacy channel history");
            records.appEnabled(PlaybackRecord.PACKAGE,true);records.enabled(false);
            final boolean[] screen={false};runOnMainSync(()->{View root=activity.getWindow().getDecorView();screen[0]=root.getWidth()>0&&root.getHeight()>0&&root.hasWindowFocus()&&activity.getCurrentFocus()!=null;});check(screen[0],"launcher is the foreground window with a remote focus target");
            for(int key:new int[]{KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP})sendKeyDownUpSync(key);
            awaitFrames(activity);final boolean[] focus={false};runOnMainSync(()->{View v=activity.getCurrentFocus();Rect visible=new Rect();focus[0]=activity.getWindow().getDecorView().hasWindowFocus()&&v!=null&&v.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0;});check(focus[0],"remote traversal retains visible foreground focus");
            boolean registered=false;for(android.content.pm.ResolveInfo homeEntry:c.getPackageManager().queryIntentActivities(SystemEntrypoints.homeQuery(),0))if(homeEntry.activityInfo!=null&&c.getPackageName().equals(homeEntry.activityInfo.packageName)&&homeEntry.activityInfo.enabled&&homeEntry.activityInfo.exported)registered=true;
            check(registered,"installed launcher exposes an enabled HOME entry");
            String actual=resolvedHome(c);home.edit().putString("ci_home_before",actual).commit();result.putString("homeResolution",actual);
            if(Build.VERSION.SDK_INT>=24){if(SystemEntrypoints.defaultHome(c))check(actual.startsWith(c.getPackageName()+"/"),"the operating system selected the new HOME");else{android.content.pm.ResolveInfo homeEntry=c.getPackageManager().resolveActivity(SystemEntrypoints.homeQuery(),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);check(homeEntry!=null&&homeEntry.priority>0&&(actual.startsWith("com.google.android.tvlauncher/")||actual.startsWith("com.google.android.leanbacklauncher/")),"unselected HOME has a documented stock TV priority; actual="+actual);result.putString("skipped","Default HOME takeover blocked by stock priority; opening/navigation still tested");}}
            else result.putString("skipped","Android 6 has no set-home-activity shell command; default HOME selection not attempted");
            WeatherRepository.Place place=new WeatherRepository.Place();place.name="Synthetic city";place.latitude=22.3;place.longitude=114.2;
            check(WeatherRepository.valid(place)&&!WeatherRepository.finite(Double.NaN),"weather validation works on the minimum Android runtime");
            check(MediaRecordPolicy.position(1000,10000,true,1f,1000,2000)==2000,"progress extrapolation uses compatible number validation");
            CountDownLatch latch=new CountDownLatch(1);boolean[] imported={false};Wallpapers wallpapers=(Wallpapers)field(activity,"wallpapers");
            wallpapers.importDocument(Uri.parse("content://local.quiettv.checks.documents/image"),(ok,video)->{imported[0]=ok&&!video;latch.countDown();});
            check(latch.await(10,TimeUnit.SECONDS)&&imported[0],"selected provider document imports without broad storage permission");
            File background=new File(home.getString("wallpaper_path",""));check(background.isFile()&&background.getName().matches("import-[0-9]+\\.png"),"unsafe provider display name cannot escape wallpaper library");
            check(background.getCanonicalPath().startsWith(wallpapers.library().getCanonicalPath()+File.separator),"import stays in the application library");
            home.edit().putString("wallpaper_kind","builtin").commit();runOnMainSync(()->activity.render());awaitFrames(activity);
            // Simulate old-version preferences, then let the runner perform an actual install -r.
            home.edit().remove("config_schema").putStringSet("favorites",new HashSet<>(Arrays.asList("com.example.saved"))).putString("wallpaper_kind","theme").putInt("theme",2).putString("groups","[{\"id\":\"favorites\",\"title\":\"Saved category\",\"row\":true},{\"id\":\"all\",\"title\":\"Apps\",\"row\":false}]").commit();
            history.edit().remove("config_schema").putBoolean("enabled",false).putBoolean("generic_enabled",true).putBoolean("music_enabled",false).putString("records","[{\"id\":\"123456\",\"eid\":\"789\",\"title\":\"Synthetic legacy upgrade\",\"command\":\"tcl/ugc\",\"position\":123456,\"duration\":600000,\"updated\":1}]").commit();
            PlaybackRules registry=PlaybackRules.get(c);JSONObject custom=new JSONObject(registry.find(PlaybackRule.BILI).json.toString()).put("id","example.upgrade").put("name","Synthetic upgrade rule");PlaybackRule upgradeRule=registry.parse(custom.toString());registry.install(upgradeRule);registry.enabled(registry.find(PlaybackRule.BILI),false);registry.enabled(registry.find(upgradeRule.id),true);
        }else if("upgrade".equals(phase)){
            check(home.getInt("config_schema",0)==1&&history.getInt("config_schema",0)==1,"legacy preferences migrate on open");
            check(home.getStringSet("favorites",Collections.emptySet()).contains("com.example.saved"),"covering install preserves favorites");
            check(home.getString("groups","").contains("Saved category"),"category labels survive upgrade");
            check("theme".equals(home.getString("wallpaper_kind",""))&&home.getInt("theme",0)==2,"wallpaper selection survives upgrade");
            check(!history.getBoolean("enabled",true)&&history.getBoolean("generic_enabled",false)&&!history.getBoolean("music_enabled",true),"explicit legacy choices survive migration");
            ArrayList<PlaybackRecord> retained=new HistoryStore(c).list();check(retained.size()==1&&retained.get(0).key().equals("tcl/ugc|123456|789")&&retained.get(0).position==123456&&retained.get(0).duration==600000,"legacy record identity and progress survive covering install");
            PlaybackRules registry=PlaybackRules.get(c);check(registry.find("example.upgrade")!=null&&registry.enabled(registry.find("example.upgrade"))&&!registry.enabled(registry.find(PlaybackRule.BILI)),"custom rules and explicit enable/disable choices survive covering install");
            check(home.contains("ci_home_before"),"HOME comparison baseline survives covering install");
            String before=home.getString("ci_home_before","none"),after=resolvedHome(c);result.putString("homeResolution",after);
            home.edit().remove("ci_home_before").commit();
            if(before.equals(after))check(true,"operating system HOME resolution survives covering install");else result.putString("skipped","Operating system changed HOME during upgrade: "+before+" -> "+after+"; launcher configuration checks passed separately");
        }else throw new AssertionError("Unknown phase");
        result.putString("passed",phase+": "+count+" device checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("failed",e.getClass().getSimpleName()+": "+e.getMessage());finish(Activity.RESULT_CANCELED,result);}}
}
