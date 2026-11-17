package local.quiettv.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;

/** Renders the existing desktop views into a separate high-resolution surface. */
final class HiResSurface extends SurfaceView implements SurfaceHolder.Callback {
    private final Activity owner;
    private final HomeLayer host;
    private final Wallpapers wallpapers;
    private final Runnable failure;
    private View page, background;
    private Bitmap backdropCache;
    private long cachedRevision=-1;
    private boolean ready, scheduled, presenting, failed, cacheUnavailable;
    private int bufferWidth, bufferHeight, fastFrames;
    private long pulseUntil, lastFrame, frameInterval=33;
    private final ViewTreeObserver.OnPreDrawListener listener=()->{schedule();return true;};
    private final Runnable frame=this::drawFrame;

    HiResSurface(Activity activity, HomeLayer root, Wallpapers wallpapers, Runnable failure) {
        super(activity);
        owner=activity;host=root;this.wallpapers=wallpapers;this.failure=failure;
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.RGBA_8888);
        getHolder().addCallback(this);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();host.getViewTreeObserver().addOnPreDrawListener(listener);}
    @Override protected void onDetachedFromWindow(){ViewTreeObserver observer=host.getViewTreeObserver();if(observer.isAlive())observer.removeOnPreDrawListener(listener);removeCallbacks(frame);restore();backdropCache=null;super.onDetachedFromWindow();}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);updateBufferSize(width,height);}
    static Point targetSize(Display.Mode mode){
        float cap=Math.min(1f,Math.min(3840f/mode.getPhysicalWidth(),2160f/mode.getPhysicalHeight()));
        return new Point(Math.round(mode.getPhysicalWidth()*cap),Math.round(mode.getPhysicalHeight()*cap));
    }
    void updateBufferSize(int width,int height){
        if(width<=0||height<=0)return;
        Display display=owner.getWindowManager().getDefaultDisplay();Point logical=new Point();display.getRealSize(logical);Display.Mode mode=display.getMode();
        if(logical.x<=0||logical.y<=0||mode.getPhysicalWidth()<=0||mode.getPhysicalHeight()<=0)return;
        // Keep this opt-in path bounded to 4K even on an 8K display. Several
        // full-size RGBA buffers are allocated by the compositor.
        Point target=targetSize(mode);
        int w=Math.max(1,Math.min(target.x,Math.round(width*(target.x/(float)logical.x))));
        int h=Math.max(1,Math.min(target.y,Math.round(height*(target.y/(float)logical.y))));
        if(w!=bufferWidth||h!=bufferHeight){bufferWidth=w;bufferHeight=h;backdropCache=null;cacheUnavailable=false;getHolder().setFixedSize(w,h);}
    }
    void content(View source){if(page!=source){restore();page=source;}schedule();}
    void pulse(long duration){pulseUntil=Math.max(pulseUntil,SystemClock.uptimeMillis()+duration);schedule();}
    void releaseCache(){backdropCache=null;cachedRevision=-1;cacheUnavailable=false;}
    private void schedule(){if(!failed&&ready&&!scheduled&&isAttachedToWindow()){scheduled=true;postOnAnimationDelayed(frame,Math.max(0,frameInterval-(SystemClock.uptimeMillis()-lastFrame)));}}
    private void restore(){if(presenting)host.mirrored(null);presenting=false;}
    private void fail(Throwable error){if(failed)return;failed=true;restore();Log.w("QuietTV4K","Rendering unavailable: "+error.getClass().getSimpleName());post(failure);}
    private void drawFrame(){
        scheduled=false;
        if(!ready||failed||page==null||page.getWidth()<=0||page.getHeight()<=0)return;
        long started=SystemClock.uptimeMillis();lastFrame=started;
        Canvas canvas=null;boolean success=false;
        try{
            canvas=getHolder().lockHardwareCanvas();
            if(canvas==null)return;
            canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            View next=wallpapers==null?null:wallpapers.highResolutionBackdrop();
            if(background!=next){background=next;releaseCache();}
            if(background!=null&&background.getWidth()>0&&background.getHeight()>0){
                if(wallpapers.staticBackdrop()&&!cacheUnavailable){
                    long revision=wallpapers.backdropRevision();
                    if(backdropCache==null||cachedRevision!=revision){
                        try{
                            Bitmap replacement=Bitmap.createBitmap(canvas.getWidth(),canvas.getHeight(),Bitmap.Config.ARGB_8888);
                            Canvas cached=new Canvas(replacement);cached.scale(canvas.getWidth()/(float)page.getWidth(),canvas.getHeight()/(float)page.getHeight());background.draw(cached);
                            backdropCache=replacement;cachedRevision=revision;
                        }catch(RuntimeException|OutOfMemoryError ignored){backdropCache=null;cacheUnavailable=true;}
                    }
                }else{backdropCache=null;cachedRevision=-1;}
                if(backdropCache!=null)canvas.drawBitmap(backdropCache,0,0,null);
                else{int save=canvas.save();canvas.scale(canvas.getWidth()/(float)page.getWidth(),canvas.getHeight()/(float)page.getHeight());background.draw(canvas);canvas.restoreToCount(save);}
            }
            int save=canvas.save();canvas.scale(canvas.getWidth()/(float)page.getWidth(),canvas.getHeight()/(float)page.getHeight());page.draw(canvas);canvas.restoreToCount(save);
            success=true;
        }catch(RuntimeException|OutOfMemoryError e){fail(e);}
        finally{if(canvas!=null)try{getHolder().unlockCanvasAndPost(canvas);}catch(RuntimeException e){success=false;fail(e);}}
        if(success&&!presenting){host.mirrored(page);presenting=true;Log.i("QuietTV4K","Desktop surface "+bufferWidth+"x"+bufferHeight);}
        long elapsed=SystemClock.uptimeMillis()-started;
        if(elapsed>30){frameInterval=50;fastFrames=0;}else if(elapsed>15){frameInterval=33;fastFrames=0;}else if(++fastFrames>=30){frameInterval=17;fastFrames=0;}
        if(SystemClock.uptimeMillis()<pulseUntil)schedule();
    }
    @Override public void surfaceCreated(SurfaceHolder holder){}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){
        ready=true;schedule();
        // Some TV firmware may silently clamp a requested buffer. Allow the
        // resize to settle, then return to the ordinary window if it stayed small.
        if(width<bufferWidth||height<bufferHeight)postDelayed(()->{
            if(!ready||failed)return;
            android.graphics.Rect actual=getHolder().getSurfaceFrame();
            if(actual.width()<bufferWidth||actual.height()<bufferHeight)fail(new IllegalStateException("Surface size was limited"));
        },1500);
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder){ready=false;removeCallbacks(frame);scheduled=false;restore();releaseCache();}
}
