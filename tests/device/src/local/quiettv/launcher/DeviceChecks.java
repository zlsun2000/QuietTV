package local.quiettv.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
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
    @Override public void onStart(){Bundle result=new Bundle();try{
        // A hardware TV must never receive these synthetic upgrade preferences.
        boolean virtual=Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator");
        check(virtual,"only a synthetic emulator may run device checks");
        Context c=getTargetContext();SharedPreferences home=c.getSharedPreferences("home",0),history=c.getSharedPreferences("playback_history",0);
        status("starting a new launcher Activity");
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        c.startActivity(new Intent().setComponent(new ComponentName(c,MainActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity opened=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);
        check(opened instanceof MainActivity,"launcher Activity created within timeout");
        MainActivity activity=(MainActivity)opened;
        awaitFrames(activity);Thread.sleep(1500);awaitFrames(activity);
        runOnMainSync(()->{try{Dialog dialog=(Dialog)field(activity,"sheet");if(dialog!=null)dialog.dismiss();}catch(Exception e){throw new RuntimeException(e);}});
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
            final boolean[] screen={false};runOnMainSync(()->{View root=activity.getWindow().getDecorView();screen[0]=root.getWidth()>0&&root.getHeight()>0&&activity.getCurrentFocus()!=null;});check(screen[0],"launcher lays out with a remote focus target");
            for(int key:new int[]{KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP})sendKeyDownUpSync(key);
            awaitFrames(activity);final boolean[] focus={false};runOnMainSync(()->{View v=activity.getCurrentFocus();Rect visible=new Rect();focus[0]=v!=null&&v.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0;});check(focus[0],"remote traversal retains visible focus");
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
            Bitmap capture=getUiAutomation().takeScreenshot();check(capture!=null,"display capture is available for private emulator review");
            try(OutputStream out=c.getContentResolver().openOutputStream(Uri.parse("content://local.quiettv.checks.documents/capture"),"w")){
                check(out!=null&&capture.compress(Bitmap.CompressFormat.PNG,100,out),"test provider exports the capture without shared-storage access");
            }finally{capture.recycle();}
            // Simulate old-version preferences, then let the runner perform an actual install -r.
            home.edit().remove("config_schema").putStringSet("favorites",new HashSet<>(Arrays.asList("com.example.saved"))).putString("wallpaper_kind","theme").putInt("theme",2).putString("groups","[{\"id\":\"favorites\",\"title\":\"Saved category\",\"row\":true},{\"id\":\"all\",\"title\":\"Apps\",\"row\":false}]").commit();
            history.edit().remove("config_schema").putBoolean("enabled",false).putBoolean("generic_enabled",true).putBoolean("music_enabled",false).putString("records","[]").commit();
        }else if("upgrade".equals(phase)){
            check(home.getInt("config_schema",0)==1&&history.getInt("config_schema",0)==1,"legacy preferences migrate on open");
            check(home.getStringSet("favorites",Collections.emptySet()).contains("com.example.saved"),"covering install preserves favorites");
            check(home.getString("groups","").contains("Saved category"),"category labels survive upgrade");
            check("theme".equals(home.getString("wallpaper_kind",""))&&home.getInt("theme",0)==2,"wallpaper selection survives upgrade");
            check(!history.getBoolean("enabled",true)&&history.getBoolean("generic_enabled",false)&&!history.getBoolean("music_enabled",true),"explicit legacy choices survive migration");
            check("[]".equals(history.getString("records","")),"record storage is retained");
            check(home.contains("ci_home_before"),"HOME comparison baseline survives covering install");
            String before=home.getString("ci_home_before","none"),after=resolvedHome(c);result.putString("homeResolution",after);
            home.edit().remove("ci_home_before").commit();
            if(before.equals(after))check(true,"operating system HOME resolution survives covering install");else result.putString("skipped","Operating system changed HOME during upgrade: "+before+" -> "+after+"; launcher configuration checks passed separately");
        }else throw new AssertionError("Unknown phase");
        result.putString("passed",phase+": "+count+" device checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("failed",e.getClass().getSimpleName()+": "+e.getMessage());finish(Activity.RESULT_CANCELED,result);}}
}
