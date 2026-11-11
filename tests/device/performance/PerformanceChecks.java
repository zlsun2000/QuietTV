package local.quiettv.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;

/** Synthetic fixture for explicitly disposable Android TV emulators only. */
public final class PerformanceChecks extends Instrumentation {
    private static final String PREFIX="local.quiettv.synthetic.app";
    private static final int PAUSE_MS=200, CYCLES=3;
    private MainActivity activity;
    interface Work { void run() throws Exception; }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void main(Work work){final Throwable[] failure={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new RuntimeException(failure[0]);}
    private static Field field(Object object,String name)throws Exception{Field result=object.getClass().getDeclaredField(name);result.setAccessible(true);return result;}
    private static Object get(Object object,String name)throws Exception{return field(object,name).get(object);}
    private static void set(Object object,String name,Object value)throws Exception{field(object,name).set(object,value);}
    private void status(String key,String value){Bundle b=new Bundle();b.putString(key,value);sendStatus(0,b);}
    private String shell(String command)throws Exception{
        ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand(command);if(descriptor==null)throw new IllegalStateException("Shell command unavailable");
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>512*1024)throw new IllegalStateException("gfxinfo exceeds bounded output");out.write(buffer,0,n);}return out.toString("UTF-8");}
    }
    private String memory(){Debug.MemoryInfo m=new Debug.MemoryInfo();Debug.getMemoryInfo(m);Runtime vm=Runtime.getRuntime();return "pss_kb="+m.getTotalPss()+" dalvik_pss_kb="+m.dalvikPss+" native_pss_kb="+m.nativePss+" other_pss_kb="+m.otherPss+" java_heap_used_kb="+(vm.totalMemory()-vm.freeMemory())/1024;}
    private void settle()throws Exception{waitForIdleSync();Thread.sleep(1000);waitForIdleSync();}
    @SuppressWarnings({"unchecked","deprecation"}) private void fixture(Context c)throws Exception{
        SharedPreferences home=c.getSharedPreferences("home",0),history=c.getSharedPreferences("playback_history",0);
        home.edit().putBoolean("setup_seen",true).putBoolean("banners",false).putBoolean("wallpaper_motion",false).putString("wallpaper_kind","theme").putInt("theme",2).putString("groups","[{\"id\":\"favorites\",\"title\":\"Favorites\",\"row\":true},{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]").commit();
        history.edit().putBoolean("enabled",true).putBoolean("generic_enabled",true).remove("records").commit();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);c.startActivity(new Intent().setComponent(new ComponentName(c,MainActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=(MainActivity)waitForMonitorWithTimeout(monitor,15000);removeMonitor(monitor);if(activity==null)throw new IllegalStateException("Launcher did not start");settle();
        main(()->{if((Boolean)get(activity,"packageReceiverRegistered")){activity.unregisterReceiver((android.content.BroadcastReceiver)get(activity,"packagesChanged"));set(activity,"packageReceiverRegistered",false);}((Handler)get(activity,"handler")).removeCallbacks((Runnable)get(activity,"reloadApps"));activity.loadApps();});
        long deadline=SystemClock.uptimeMillis()+15000;boolean ready=false;while(SystemClock.uptimeMillis()<deadline){final boolean[] done={false};main(()->done[0]=!(Boolean)get(activity,"appsLoading")&&!(Boolean)get(activity,"appsDirty"));if(done[0]){ready=true;break;}Thread.sleep(100);}if(!ready)throw new IllegalStateException("Package discovery did not settle");
        main(()->{
            ArrayList<Object> apps=(ArrayList<Object>)get(activity,"apps");Class<?> type=Class.forName("local.quiettv.launcher.MainActivity$App");Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);apps.clear();
            Drawable icon=c.getResources().getDrawable(c.getApplicationInfo().icon);for(int i=0;i<42;i++){Object item=constructor.newInstance();set(item,"pkg",PREFIX+i);set(item,"name",String.format(Locale.US,"Synthetic app %02d",i));set(item,"intent",new Intent(Intent.ACTION_MAIN));set(item,"icon",icon);set(item,"banner",null);set(item,"color",0xff345b70);apps.add(item);}
            set(activity,"favorites",new HashSet<>(Arrays.asList(PREFIX+"0",PREFIX+"1",PREFIX+"2")));
            PlaybackRecord record=new PlaybackRecord();record.source=getContext().getPackageName();record.sourceLabel="Synthetic player";record.command="media/session";record.videoId=MediaRecordPolicy.digest("performance-fixture");record.title="Synthetic playback title";record.position=123000;record.duration=900000;record.updated=System.currentTimeMillis();if(!new HistoryStore(c).put(record))throw new IllegalStateException("Synthetic playback record rejected");
            Properties properties=new Properties();properties.setProperty("grid.columns","5");set(activity,"ui",new UiConfig(properties));activity.loadGroups();activity.render();
        });settle();
        main(()->{View root=(View)get(activity,"root");View first=root.findViewWithTag("favorites|"+PREFIX+"0");if(first==null)throw new IllegalStateException("Synthetic favorite missing");boolean recent=false;for(ArrayList<View> row:(ArrayList<ArrayList<View>>)get(activity,"focusRows"))for(View v:row)if(v instanceof RecentTile)recent=true;if(!recent)throw new IllegalStateException("Synthetic playback card missing");if(!first.requestFocus())throw new IllegalStateException("Cannot focus synthetic favorite");});settle();
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        boolean virtual=(Build.FINGERPRINT.contains("generic")||Build.FINGERPRINT.startsWith("google/sdk_google_atv_x86/emulator_x86_arm:14/"))&&("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE));if(!virtual)throw new IllegalStateException("Disposable emulator required");
        Context c=getTargetContext();fixture(c);DisplayMetrics dm=c.getResources().getDisplayMetrics();ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);String version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;
        status("fixture","42 synthetic apps; 3 favorites; 1 synthetic media-session record; theme=graphite; grid=5; 3 cycles, each RIGHT x4, DOWN x5, LEFT x4, UP x5; pause="+PAUSE_MS+"ms; size="+dm.widthPixels+"x"+dm.heightPixels+"; density="+dm.densityDpi+"; lowRam="+(am!=null&&am.isLowRamDevice())+"; version="+version);
        shell("dumpsys gfxinfo local.quiettv.launcher reset");status("memory_before",memory());long start=SystemClock.elapsedRealtime();StringBuilder path=new StringBuilder();int[] keys={KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_UP};int[] repetitions={4,5,4,5};
        for(int cycle=0;cycle<CYCLES;cycle++)for(int group=0;group<keys.length;group++)for(int i=0;i<repetitions[group];i++){sendKeyDownUpSync(keys[group]);Thread.sleep(PAUSE_MS);final String[] kind={"none"};main(()->{View focused=activity.getCurrentFocus();if(focused==null)return;Object tag=focused.getTag();String text=tag==null?"":tag.toString();kind[0]=text.startsWith("favorites|"+PREFIX)||text.startsWith("all|"+PREFIX)?"app":text.startsWith("recent|")?"recent":"other";});path.append(kind[0].charAt(0));}
        waitForIdleSync();long elapsed=SystemClock.elapsedRealtime()-start;status("focus_path",path.toString());status("memory_after",memory());status("elapsed_ms",String.valueOf(elapsed));String gfx=shell("dumpsys gfxinfo local.quiettv.launcher framestats");if(!gfx.contains("Total frames rendered")||!gfx.contains("---PROFILEDATA---"))throw new IllegalStateException("Frame statistics unavailable");status("gfxinfo",gfx);result.putString("passed","synthetic performance fixture complete");finish(Activity.RESULT_OK,result);
    }catch(Throwable failure){result.putString("failed",failure.toString());finish(Activity.RESULT_CANCELED,result);}}
}
