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
    private void check(boolean condition,String description){count++;if(!condition)throw new AssertionError(description);}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        // A hardware TV must never receive these synthetic upgrade preferences.
        boolean virtual=Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator");
        check(virtual,"only a synthetic emulator may run device checks");
        Context c=getTargetContext();SharedPreferences home=c.getSharedPreferences("home",0),history=c.getSharedPreferences("playback_history",0);
        MainActivity activity=(MainActivity)startActivitySync(new Intent().setComponent(new ComponentName(c,MainActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();Thread.sleep(1500);waitForIdleSync();
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
            weather.dispose();weatherPrefs.edit().clear().commit();
            check(!GenericMediaHistory.permission(c),"basic desktop requires no media grant");
            final boolean[] screen={false};runOnMainSync(()->{View root=activity.getWindow().getDecorView();screen[0]=root.getWidth()>0&&root.getHeight()>0&&activity.getCurrentFocus()!=null;});check(screen[0],"launcher lays out with a remote focus target");
            for(int key:new int[]{KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP})sendKeyDownUpSync(key);
            waitForIdleSync();final boolean[] focus={false};runOnMainSync(()->{View v=activity.getCurrentFocus();Rect visible=new Rect();focus[0]=v!=null&&v.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0;});check(focus[0],"remote traversal retains visible focus");
            check(SystemEntrypoints.defaultHome(c),"system resolves QuietTV as selected HOME");
            CountDownLatch latch=new CountDownLatch(1);boolean[] imported={false};Wallpapers wallpapers=(Wallpapers)field(activity,"wallpapers");
            wallpapers.importDocument(Uri.parse("content://local.quiettv.checks.documents/image"),(ok,video)->{imported[0]=ok&&!video;latch.countDown();});
            check(latch.await(10,TimeUnit.SECONDS)&&imported[0],"selected provider document imports without broad storage permission");
            File background=new File(home.getString("wallpaper_path",""));check(background.isFile()&&background.getName().matches("import-[0-9]+\\.png"),"unsafe provider display name cannot escape wallpaper library");
            check(background.getCanonicalPath().startsWith(wallpapers.library().getCanonicalPath()+File.separator),"import stays in the application library");
            home.edit().putString("wallpaper_kind","builtin").commit();runOnMainSync(()->activity.render());waitForIdleSync();
            Bitmap capture=getUiAutomation().takeScreenshot();if(capture!=null){File dir=c.getExternalFilesDir(null);if(dir!=null)try(OutputStream out=new FileOutputStream(new File(dir,"ci-fresh.png"))){capture.compress(Bitmap.CompressFormat.PNG,100,out);}capture.recycle();}
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
            check(SystemEntrypoints.defaultHome(c),"HOME preference survives covering install");
        }else throw new AssertionError("Unknown phase");
        result.putString("passed",phase+": "+count+" device checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("failed",e.getClass().getSimpleName()+": "+e.getMessage());finish(Activity.RESULT_CANCELED,result);}}
}
