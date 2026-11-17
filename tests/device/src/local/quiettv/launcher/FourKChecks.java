package local.quiettv.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Exercises only disposable emulator state and a locally generated image. */
public final class FourKChecks extends Instrumentation {
    private MainActivity activity;
    private int count;
    private boolean expectLowRam;
    interface Work { void run() throws Exception; }
    @Override public void onCreate(Bundle args) { super.onCreate(args);expectLowRam=args!=null&&"true".equals(args.getString("issue6ExpectLowRamPath"));start(); }
    private void main(Work work) {
        final Throwable[] error={null};
        runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new IllegalStateException(error[0]);
    }
    private Object get(Object object,String name)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private void check(boolean condition,String description) {
        if(!condition)throw new AssertionError(description);
        count++;Bundle progress=new Bundle();progress.putString("check","PASS "+description);sendStatus(0,progress);
    }
    private void waitFor(Work work)throws Exception {
        long deadline=SystemClock.uptimeMillis()+25000;Throwable last=null;
        while(SystemClock.uptimeMillis()<deadline){try{work.run();return;}catch(Throwable e){last=e;Thread.sleep(150);}}
        throw new AssertionError("Timed out: "+last);
    }
    private SurfaceView surface(String name)throws Exception {
        final SurfaceView[] result={null};
        main(()->{Object layer=get(activity,name);if(layer instanceof SurfaceView&&(Boolean)get(layer,"presenting"))result[0]=(SurfaceView)layer;});
        if(result[0]==null||!result[0].getHolder().getSurface().isValid())throw new IllegalStateException(name+" not ready");
        return result[0];
    }
    private void toggle()throws Exception {
        main(()->{Dialog sheet=(Dialog)get(activity,"sheet");View row=sheet.getWindow().getDecorView().findViewWithTag("control-action:4k");if(row==null)throw new IllegalStateException("4K option missing");row.requestFocus();row.performClick();});
    }
    private void copy(SurfaceView surface,boolean inspectBackground)throws Exception {
        Rect size=surface.getHolder().getSurfaceFrame();
        Bitmap bitmap=Bitmap.createBitmap(size.width(),size.height(),Bitmap.Config.ARGB_8888);
        CountDownLatch complete=new CountDownLatch(1);int[] status={-1};
        PixelCopy.request(surface.getHolder().getSurface(),bitmap,value->{status[0]=value;complete.countDown();},new Handler(Looper.getMainLooper()));
        if(!complete.await(10,TimeUnit.SECONDS)||status[0]!=PixelCopy.SUCCESS)throw new IllegalStateException("PixelCopy: "+status[0]);
        if(inspectBackground){int pixel=bitmap.getPixel(size.width()/2,100);check(Color.red(pixel)>Color.blue(pixel)*2,"4K surface contains the generated wallpaper (pixel="+Integer.toHexString(pixel)+")");}
        bitmap.recycle();
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        boolean virtual=(Build.FINGERPRINT.contains("generic")||Build.FINGERPRINT.startsWith("google/sdk_google_atv_x86/emulator_x86_arm:14/"))&&("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE));
        if(!virtual)throw new IllegalStateException("Disposable emulator required");
        Context c=getTargetContext();SharedPreferences home=c.getSharedPreferences("home",0);
        File fixture=new File(c.getCacheDir(),"synthetic-4k-wallpaper.png");
        Bitmap image=Bitmap.createBitmap(3840,2160,Bitmap.Config.ARGB_8888);image.eraseColor(0xffb45a20);
        try(FileOutputStream stream=new FileOutputStream(fixture)){if(!image.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IllegalStateException("fixture write failed");}finally{image.recycle();}
        home.edit().putBoolean("setup_seen",true).remove("home_4k").putString("wallpaper_kind","image").putString("wallpaper_path",fixture.getAbsolutePath()).putBoolean("wallpaper_motion",false).commit();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        c.startActivity(new Intent().setComponent(new ComponentName(c,MainActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activity=(MainActivity)waitForMonitorWithTimeout(monitor,15000);removeMonitor(monitor);if(activity==null)throw new IllegalStateException("No launcher");
        waitFor(()->{main(()->{if(get(activity,"desktopPage")==null)throw new IllegalStateException("No desktop");});});
        main(()->check(!home.getBoolean("home_4k",false)&&get(activity,"highResolution")==null,"4K defaults to off"));
        Display.Mode mode=activity.getWindowManager().getDefaultDisplay().getMode();
        ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        if(expectLowRam){java.lang.reflect.Method low=MainActivity.class.getDeclaredMethod("isLowRamDevice");low.setAccessible(true);check((Boolean)low.invoke(activity),"launcher reports the low-memory path");}
        boolean supported=Build.VERSION.SDK_INT>=26&&!expectLowRam&&!manager.isLowRamDevice()&&mode.getPhysicalWidth()>=3840&&mode.getPhysicalHeight()>=2160;
        main(()->activity.showHomeLayout(null));toggle();
        if(!supported){main(()->check(!home.getBoolean("home_4k",false)&&get(activity,"highResolution")==null,"unsupported or low-memory device keeps the standard path"));result.putString("passed",count+" 4K checks (unsupported path)");finish(Activity.RESULT_OK,result);return;}
        waitFor(()->{if(!home.getBoolean("home_4k",false))throw new IllegalStateException("4K not enabled");surface("highResolution");});
        SurfaceView desktop=surface("highResolution");Rect size=desktop.getHolder().getSurfaceFrame();
        check(size.width()==mode.getPhysicalWidth()&&size.height()==mode.getPhysicalHeight(),"desktop surface uses physical output dimensions");
        waitFor(()->{main(()->{Object wallpaper=get(activity,"wallpapers");Bitmap cached=(Bitmap)get(wallpaper,"cached");if(cached==null||cached.getWidth()!=3840||cached.getHeight()!=2160)throw new IllegalStateException("4K image not decoded");Object layer=get(activity,"highResolution");if(((Long)get(layer,"cachedRevision")).longValue()!=((Long)get(wallpaper,"backdropRevision")).longValue())throw new IllegalStateException("background not rendered");});});
        copy(desktop,true);
        waitFor(()->{main(()->{Object control=get(activity,"controlCenter");Object layer=get(control,"highResolution");if(layer==null||!(Boolean)get(layer,"presenting"))throw new IllegalStateException("Control center surface not ready");});});
        main(()->{Object control=get(activity,"controlCenter");SurfaceView layer=(SurfaceView)get(control,"highResolution");Rect bounds=layer.getHolder().getSurfaceFrame();check(bounds.width()>layer.getWidth()&&bounds.height()>layer.getHeight(),"control center uses a higher-resolution buffer");check(((Dialog)get(activity,"sheet")).getCurrentFocus()!=null,"control center retains remote focus");});
        main(()->activity.showSystemSettings());
        waitFor(()->surface("dialogHighResolution"));
        SurfaceView panel=surface("dialogHighResolution");Rect panelSize=panel.getHolder().getSurfaceFrame();check(panelSize.width()>panel.getWidth()&&panelSize.height()>panel.getHeight(),"settings panel uses a higher-resolution buffer");copy(panel,false);
        main(()->{Dialog dialog=(Dialog)get(activity,"sheet");check(dialog.getCurrentFocus()!=null,"settings panel retains remote focus");dialog.dismiss();activity.showHomeLayout(null);});
        toggle();waitFor(()->{main(()->{if(home.getBoolean("home_4k",true)||get(activity,"highResolution")!=null)throw new IllegalStateException("4K did not turn off");});});
        check(true,"turning 4K off restores standard rendering");
        toggle();waitFor(()->surface("highResolution"));
        main(()->activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL));
        waitFor(()->{main(()->{if(home.getBoolean("home_4k",true)||get(activity,"highResolution")!=null)throw new IllegalStateException("4K fallback missing");});});
        check(true,"critical memory pressure restores standard rendering");
        result.putString("passed",count+" 4K checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("failed",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
