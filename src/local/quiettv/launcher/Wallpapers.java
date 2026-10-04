package local.quiettv.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.MediaPlayer;
import android.view.*;
import android.widget.*;
import android.animation.ValueAnimator;
import java.io.*;
import java.util.concurrent.*;

/** Local-only wallpaper renderer. No service, network, system wallpaper or audio changes. */
final class Wallpapers {
    interface Imported {void done(boolean success,boolean video);}
    private final Activity owner;private final SharedPreferences prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private FrameLayout layer;private PhotoView image;private TextureView texture;
    private Bitmap cached;private String cachedKey="",loadingKey="";
    private MediaPlayer player;private Surface surface;private ValueAnimator motion;
    private boolean active=false,disposed=false,videoFailed=false;
    private int videoWidth,videoHeight;
    Wallpapers(Activity a,SharedPreferences p){owner=a;prefs=p;}
    String kind(){return prefs.getString("wallpaper_kind","builtin");}
    File library(){File f=owner.getExternalFilesDir("backgrounds");if(f==null)f=new File(owner.getFilesDir(),"backgrounds");f.mkdirs();return f;}
    static boolean video(File f){String n=f.getName().toLowerCase(java.util.Locale.ROOT);return n.matches(".*\\.(mp4|m4v|mkv|webm|mov)$");}
    static boolean picture(File f){String n=f.getName().toLowerCase(java.util.Locale.ROOT);return n.matches(".*\\.(jpg|jpeg|png|webp|bmp)$");}
    void attach(FrameLayout root,int theme){
        release();image=null;texture=null;layer=new FrameLayout(owner);layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);root.addView(layer,new FrameLayout.LayoutParams(-1,-1));layer.addView(new LandscapeView(owner,theme),new FrameLayout.LayoutParams(-1,-1));
        if(kind().equals("theme")){status();return;}
        image=new PhotoView(owner);layer.addView(image,new FrameLayout.LayoutParams(-1,-1));loadImage();
        if(kind().equals("video")){
            texture=new TextureView(owner);final TextureView attachedTexture=texture;texture.setOpaque(false);layer.addView(texture,new FrameLayout.LayoutParams(-1,-1));
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){if(active)play();}
                public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){cropVideo();}
                public boolean onSurfaceTextureDestroyed(SurfaceTexture t){if(texture==attachedTexture)releasePlayer();return true;}
                public void onSurfaceTextureUpdated(SurfaceTexture t){}
            });
        }
        layer.addView(new Scrim(owner),new FrameLayout.LayoutParams(-1,-1));if(active)start();status();
    }
    private void loadImage(){
        final String key=kind().equals("image")?prefs.getString("wallpaper_path",""):"builtin";
        if(key.equals(cachedKey)&&cached!=null){showImage();return;}
        if(key.equals(loadingKey))return;loadingKey=key;
        final int w=owner.getResources().getDisplayMetrics().widthPixels,h=owner.getResources().getDisplayMetrics().heightPixels;
        worker.execute(new Runnable(){public void run(){Bitmap b=null;try{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;decode(key,bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>w*1.5f||bounds.outHeight/options.inSampleSize>h*1.5f)options.inSampleSize*=2;b=decode(key,options);}catch(Throwable ignored){}
            final Bitmap bitmap=b;owner.runOnUiThread(new Runnable(){public void run(){if(disposed){if(bitmap!=null)bitmap.recycle();return;}loadingKey="";String current=kind().equals("image")?prefs.getString("wallpaper_path",""):"builtin";if(!key.equals(current)){if(bitmap!=null)bitmap.recycle();return;}if(bitmap==null){Toast.makeText(owner,"图片暂时无法读取，请更换背景",Toast.LENGTH_LONG).show();return;}cached=bitmap;cachedKey=key;showImage();status();}});
        }});
    }
    private Bitmap decode(String key,BitmapFactory.Options o){o.inScaled=false;Bitmap bitmap=key.equals("builtin")?BitmapFactory.decodeResource(owner.getResources(),R.raw.nature_lake,o):BitmapFactory.decodeFile(key,o);if(bitmap==null||key.equals("builtin")||o.inJustDecodeBounds)return bitmap;try{android.media.ExifInterface exif=new android.media.ExifInterface(key);int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;default:return bitmap;}Bitmap oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(oriented!=bitmap)bitmap.recycle();return oriented;}catch(Exception ignored){return bitmap;}}
    private void showImage(){if(image!=null&&cached!=null){image.setImageBitmap(cached);if(active)animatePhoto();}}
    void resume(){active=true;start();status();}
    void pause(){active=false;release();status();}
    void dispose(){pause();disposed=true;worker.shutdownNow();}
    private void start(){if(kind().equals("video")){if(texture!=null&&texture.isAvailable())play();}else animatePhoto();}
    private void animatePhoto(){
        if(motion!=null){motion.cancel();motion=null;}if(image==null)return;image.setProgress(-1);
        if(!active||kind().equals("video")||!prefs.getBoolean("wallpaper_motion",true))return;
        final PhotoView target=image;motion=ValueAnimator.ofFloat(0,1);motion.setDuration(45000);motion.setRepeatMode(ValueAnimator.REVERSE);motion.setRepeatCount(ValueAnimator.INFINITE);motion.setInterpolator(new android.view.animation.LinearInterpolator());motion.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){long last;public void onAnimationUpdate(ValueAnimator a){long now=android.os.SystemClock.uptimeMillis();if(now-last<33)return;last=now;target.setProgress((Float)a.getAnimatedValue());}});motion.start();
    }
    private void play(){
        if(player!=null||!active||texture==null||!texture.isAvailable())return;videoFailed=false;
        try{surface=new Surface(texture.getSurfaceTexture());player=new MediaPlayer();player.setSurface(surface);player.setDataSource(prefs.getString("wallpaper_path",""));player.setVolume(0,0);player.setLooping(true);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer p){if(player!=p)return;videoWidth=p.getVideoWidth();videoHeight=p.getVideoHeight();cropVideo();p.setVolume(0,0);if(active)p.start();status();}});
            player.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer p,int what,int extra){failedVideo();return true;}});player.prepareAsync();
        }catch(Exception e){failedVideo();}
    }
    private void cropVideo(){if(texture==null||videoWidth<=0||videoHeight<=0)return;float w=texture.getWidth(),h=texture.getHeight();if(w==0||h==0)return;float scale=Math.max(w/videoWidth,h/videoHeight);Matrix m=new Matrix();m.setScale(videoWidth*scale/w,videoHeight*scale/h,w/2,h/2);texture.setTransform(m);}
    private void failedVideo(){videoFailed=true;releasePlayer();if(texture!=null)texture.setVisibility(View.GONE);Toast.makeText(owner,"这个视频暂时无法播放，已显示内置风景",Toast.LENGTH_LONG).show();status();}
    private void releasePlayer(){if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}if(surface!=null){surface.release();surface=null;}}
    private void release(){if(motion!=null){motion.cancel();motion=null;}releasePlayer();}
    void importFile(final File source,final Imported callback){
        worker.execute(new Runnable(){public void run(){boolean success=false;File dest=null;boolean isVideo=video(source);try{
            if(!source.isFile()||(!isVideo&&!picture(source)))throw new IOException();
            if(source.length()>(isVideo?400L:50L)*1024*1024)throw new IOException();
            String lib=library().getCanonicalPath()+File.separator;
            if(source.getCanonicalPath().startsWith(lib))dest=source;
            else{dest=new File(library(),System.currentTimeMillis()+"-"+source.getName());try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(dest)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException();out.write(b,0,n);}}}
            if(!isVideo){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(dest.getPath(),o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException();}
            prefs.edit().putString("wallpaper_kind",isVideo?"video":"image").putString("wallpaper_path",dest.getCanonicalPath()).commit();success=true;
        }catch(Exception ignored){if(dest!=null&&!dest.equals(source))dest.delete();}
        final boolean ok=success;final boolean vid=isVideo;owner.runOnUiThread(new Runnable(){public void run(){if(!disposed)callback.done(ok,vid);}});
        }});
    }
    void status(){try{File dir=owner.getExternalFilesDir(null);if(dir==null)return;String playing="false",looping="false";int position=0,duration=0;try{if(player!=null){playing=String.valueOf(player.isPlaying());looping=String.valueOf(player.isLooping());position=player.getCurrentPosition();duration=player.getDuration();}}catch(Exception ignored){}try(Writer out=new FileWriter(new File(dir,"wallpaper-status.txt"))){out.write("kind="+kind()+"\nactive="+active+"\nphoto_motion="+(motion!=null&&motion.isRunning())+"\nvideo_playing="+playing+"\nvideo_looping="+looping+"\nvideo_position="+position+"\nvideo_duration="+duration+"\nvideo_muted=true\nvideo_failed="+videoFailed+"\nbitmap="+(cached==null?"none":cached.getWidth()+"x"+cached.getHeight())+"\n");}}catch(Exception ignored){}}
    private static final class Scrim extends View {private final Paint p=new Paint(3);Scrim(Activity a){super(a);}protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setShader(new LinearGradient(0,0,0,h,new int[]{0x30040b13,0x20040b13,0x80040b13,0xe6040b13},new float[]{0,.2f,.52f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);}}
    private static final class PhotoView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private Bitmap bitmap;private float progress=-1;
        PhotoView(Activity a){super(a);}
        void setImageBitmap(Bitmap b){bitmap=b;invalidate();}
        void setProgress(float p){progress=p;invalidate();}
        protected void onDraw(Canvas c){if(bitmap==null||bitmap.isRecycled())return;float w=getWidth(),h=getHeight(),zoom=progress<0?1:1.03f+progress*.04f;float scale=Math.max(w/bitmap.getWidth(),h/bitmap.getHeight())*zoom;float dx=progress<0?0:w*.008f*(progress*2-1),dy=progress<0?0:h*.004f*(1-progress*2);c.save();c.translate(w/2+dx,h/2+dy);c.scale(scale,scale);c.drawBitmap(bitmap,-bitmap.getWidth()/2f,-bitmap.getHeight()/2f,paint);c.restore();}
    }
}
