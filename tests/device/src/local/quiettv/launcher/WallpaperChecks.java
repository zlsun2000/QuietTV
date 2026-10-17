package local.quiettv.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;

/** Actual background/return transitions and trim callbacks on disposable emulators. */
public final class WallpaperChecks extends Instrumentation {
    private MainActivity activity;private Wallpapers wallpaper;private int checks;
    private interface Work {void run()throws Exception;}
    private interface Ready {boolean get()throws Exception;}
    private void main(Work work){final Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);Bundle b=new Bundle();b.putString("check",name);sendStatus(0,b);}
    private boolean await(Ready ready)throws Exception{long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){final boolean[] ok={false};main(()->ok[0]=ready.get());if(ok[0])return true;Thread.sleep(100);}return false;}
    private boolean imageReady()throws Exception{Bitmap bitmap=(Bitmap)field(wallpaper,"cached");Object image=field(wallpaper,"image");return bitmap!=null&&!bitmap.isRecycled()&&image!=null&&field(image,"bitmap")==bitmap;}
    private void frames()throws Exception{CountDownLatch latch=new CountDownLatch(1);main(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(latch::countDown)));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("No rendered wallpaper frames");}
    private void roundTrip(int level,String label)throws Exception{
        final Object[] original=new Object[2];main(()->{original[0]=field(activity,"root");original[1]=field(wallpaper,"layer");});
        getTargetContext().startActivity(new Intent().setComponent(new ComponentName("local.quiettv.checks","local.quiettv.checks.WallpaperAwayActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check(await(()->!(Boolean)field(activity,"foreground")&&!(Boolean)field(wallpaper,"active")),label+" actually leaves the foreground");
        main(()->{activity.onTrimMemory(level);check(field(wallpaper,"cached")==null,label+" releases the hidden image cache");});
        getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        check(await(()->(Boolean)field(activity,"foreground")&&(Boolean)field(wallpaper,"active")),label+" resumes the same Activity");
        if(!wallpaper.kind().equals("theme"))check(await(this::imageReady),label+" restores the selected image after memory trim");
        frames();main(()->{check(original[0]==field(activity,"root")&&original[1]==field(wallpaper,"layer"),label+" keeps the existing desktop and wallpaper layer");});
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator")))throw new AssertionError("Disposable emulator only");
        Context c=getTargetContext();SharedPreferences home=c.getSharedPreferences("home",0);
        home.edit().putBoolean("setup_seen",true).putString("wallpaper_kind","builtin").putBoolean("wallpaper_motion",true).commit();c.getSharedPreferences("playback_history",0).edit().putBoolean("enabled",false).commit();c.getSharedPreferences("weather",0).edit().clear().commit();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=(MainActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);if(activity==null)throw new AssertionError("No launcher");check(await(()->{wallpaper=(Wallpapers)field(activity,"wallpapers");return wallpaper!=null&&imageReady();}),"initial builtin image is attached");frames();Thread.sleep(1200);
        for(int level:new int[]{20,40,80}){roundTrip(level,"builtin trim "+level);main(()->check(home.getString("wallpaper_kind","").equals("builtin"),"builtin selection survives trim "+level));}
        File photo=new File(c.getCacheDir(),"synthetic-wallpaper.png");Bitmap sample=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888);sample.eraseColor(0xff23ac75);try(OutputStream out=new FileOutputStream(photo)){sample.compress(Bitmap.CompressFormat.PNG,100,out);}sample.recycle();
        main(()->{home.edit().putString("wallpaper_kind","image").putString("wallpaper_path",photo.getPath()).putBoolean("wallpaper_motion",false).commit();activity.render();});check(await(()->imageReady()&&photo.getPath().equals(field(wallpaper,"cachedKey"))),"synthetic custom image is attached");
        for(int level:new int[]{20,40,80}){roundTrip(level,"custom image trim "+level);main(()->{check(home.getString("wallpaper_path","").equals(photo.getPath())&&home.getString("wallpaper_kind","").equals("image"),"custom selection survives trim "+level);Bitmap bitmap=(Bitmap)field(wallpaper,"cached");check(bitmap.getPixel(40,30)==0xff23ac75&&field(wallpaper,"motion")==null,"custom pixels and disabled motion survive trim "+level);});}
        main(()->{home.edit().putString("wallpaper_kind","theme").commit();activity.render();});roundTrip(20,"theme trim");main(()->check(field(wallpaper,"image")==null&&field(wallpaper,"cached")==null,"theme does not allocate an image on return"));
        File video=SyntheticVideo.create(new File(c.getCacheDir(),"synthetic-return.mp4"));main(()->{home.edit().putString("wallpaper_kind","video").putString("wallpaper_path",video.getPath()).commit();activity.render();});check(await(()->wallpaper.videoCapability().isAvailable()),"synthetic video produces initial frames");
        final android.media.MediaPlayer[] stalled={null};final long[] oldFrames={0};
        main(()->{stalled[0]=(android.media.MediaPlayer)field(wallpaper,"player");oldFrames[0]=(Long)field(wallpaper,"videoFrames");stalled[0].pause();stalled[0].setLooping(true);set(wallpaper,"lastVideoFrameAt",SystemClock.uptimeMillis()-2000);((Runnable)field(wallpaper,"videoEndGuard")).run();});
        check(await(()->field(wallpaper,"player")!=stalled[0]&&wallpaper.videoCapability().isAvailable()&&(Long)field(wallpaper,"videoFrames")>oldFrames[0]),"stopped playback restarts even when the decoder claims native looping");
        final int[] firstLoop={0};main(()->firstLoop[0]=(Integer)field(wallpaper,"videoLoops"));
        check(await(()->(Integer)field(wallpaper,"videoLoops")>=firstLoop[0]+3&&wallpaper.videoCapability().isAvailable()),"three complete playback rounds produce new frames without leaving the desktop");
        main(()->{check((Integer)field(wallpaper,"videoCompletions")>=3,"completed rounds are confirmed by actual completion callbacks");check(!((android.media.MediaPlayer)field(wallpaper,"player")).isLooping(),"explicit replay remains independent of native loop support");});
        final int[] oldFallbacks={0};final Object[] noCompletion={null};main(()->{oldFallbacks[0]=(Integer)field(wallpaper,"videoEndFallbacks");noCompletion[0]=field(wallpaper,"player");((android.media.MediaPlayer)noCompletion[0]).setOnCompletionListener(null);});
        check(await(()->(Integer)field(wallpaper,"videoEndFallbacks")>oldFallbacks[0]&&field(wallpaper,"player")!=noCompletion[0]&&wallpaper.videoCapability().isAvailable()),"tail watchdog restarts output when a completion callback is lost");
        main(()->{Object playing=field(wallpaper,"player");((Runnable)field(wallpaper,"videoEndGuard")).run();check(field(wallpaper,"player")==playing,"tail watchdog never cuts a playing round short");wallpaper.pause();((Runnable)field(wallpaper,"videoEndGuard")).run();check(field(wallpaper,"player")==null,"tail watchdog stays inactive while the desktop is paused");wallpaper.resume();});check(await(()->wallpaper.videoCapability().isAvailable()),"explicit looping resumes after pausing");
        roundTrip(80,"video trim");check(await(()->wallpaper.videoCapability().isAvailable()),"video decoder resumes and produces frames");
        main(()->{home.edit().putString("wallpaper_path",new File(c.getCacheDir(),"missing-video.mp4").getPath()).commit();activity.render();});check(await(()->(Boolean)field(wallpaper,"videoFailed")&&imageReady()),"unavailable video has a visible builtin fallback");roundTrip(20,"failed video trim");check(await(()->(Boolean)field(wallpaper,"videoFailed")&&imageReady()),"failed video fallback is restored on return");
        result.putString("passed",checks+" wallpaper checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("failed",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
