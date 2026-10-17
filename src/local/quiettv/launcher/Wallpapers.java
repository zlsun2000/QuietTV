package local.quiettv.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
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
    private volatile Future<?> pendingImport;
    private boolean importBusy;private int importGeneration;
    private FrameLayout layer;private PhotoView image;private TextureView texture;
    private String attachedKey="";private boolean attachedMotion;
    private Bitmap cached;private String cachedKey="",loadingKey="";
    private MediaPlayer player;private Surface surface;private ValueAnimator motion;
    private boolean active=false,disposed=false,videoFailed=false;private String videoFailedPath="";
    private boolean videoReady=false;
    private int videoLoops=0,videoEndFallbacks=0,videoCompletions=0;
    private int videoErrorWhat=0,videoErrorExtra=0;
    private long videoFrames=0;
    private final Handler videoHandler=new Handler(Looper.getMainLooper());
    // Some TV decoders stop at the last frame without sending completion.
    // Only inspect the tail after a full playback interval has elapsed.
    private final Runnable videoEndGuard=new Runnable(){public void run(){
        final MediaPlayer p=player;
        if(p==null||!active||disposed||!videoReady)return;
        try{int duration=p.getDuration(),position=p.getCurrentPosition();
            int margin=Math.min(1000,Math.max(100,duration/10));
            long stalled=android.os.SystemClock.uptimeMillis()-lastVideoFrameAt;
            // isLooping() only echoes the requested setting. It does not prove
            // that a TV decoder actually started another round or emitted frames.
            if((!p.isPlaying()&&stalled>=500)||(duration>0&&position>=duration-margin&&stalled>=1500)){videoEndFallbacks++;repeatVideo(p);}
            else videoHandler.postDelayed(this,500);
        }catch(Exception e){if(player==p)failedVideo();}
    }};
    private int videoWidth,videoHeight;
    private long lastVideoFrameAt,guardVideoFrames;
    private final Runnable videoPrepareGuard=new Runnable(){public void run(){MediaPlayer p=player;if(p!=null&&!videoReady&&active&&!disposed)failedVideo();}};
    private final Runnable videoFrameGuard=new Runnable(){public void run(){MediaPlayer p=player;if(p==null||!active||disposed||!videoReady)return;try{long stalled=android.os.SystemClock.uptimeMillis()-lastVideoFrameAt;if((!p.isPlaying()||videoFrames==guardVideoFrames)&&stalled>15000){failedVideo();return;}guardVideoFrames=videoFrames;videoHandler.postDelayed(this,5000);}catch(Exception e){if(player==p)failedVideo();}}};
    Wallpapers(Activity a,SharedPreferences p){owner=a;prefs=p;}
    String kind(){return prefs.getString("wallpaper_kind","builtin");}
    DeviceCapability videoCapability(){if(!kind().equals("video"))return DeviceCapability.inactive("video_wallpaper","未使用");if(videoFailed)return DeviceCapability.unsupported("video_wallpaper","失败，已回退静态背景");if(videoReady&&videoFrames>0)return DeviceCapability.available("video_wallpaper",owner.getString(R.string.video_frames_output));if(videoReady)return DeviceCapability.waiting("video_wallpaper",owner.getString(R.string.video_waiting_first_frame));return DeviceCapability.waiting("video_wallpaper",active?"正在准备":"已暂停");}
    String videoStatus(){return videoCapability().reason;}
    File library(){File f=owner.getExternalFilesDir("backgrounds");if(f==null)f=new File(owner.getFilesDir(),"backgrounds");f.mkdirs();return f;}
    boolean inLibrary(File file){try{String root=library().getCanonicalPath()+File.separator;return file.getCanonicalPath().startsWith(root);}catch(Exception ignored){return false;}}
    String librarySummary(){File[] files=library().listFiles();long bytes=0;int count=0;if(files!=null)for(File f:files)if(f.isFile()&&!f.getName().startsWith(".")){bytes+=f.length();count++;}return count+" 项 · "+(bytes/1048576)+" MB";}
    boolean deleteSaved(String path){try{File file=new File(path);if(!inLibrary(file)||!file.isFile())return false;String oldKind=kind(),oldPath=prefs.getString("wallpaper_path","");boolean current=(oldKind.equals("image")||oldKind.equals("video"))&&file.getCanonicalPath().equals(oldPath);if(current&&!prefs.edit().putString("wallpaper_kind","builtin").commit())return false;if(file.delete())return true;if(current)prefs.edit().putString("wallpaper_kind",oldKind).putString("wallpaper_path",oldPath).commit();return false;}catch(Exception ignored){return false;}}
    static boolean video(File f){String n=f.getName().toLowerCase(java.util.Locale.ROOT);return n.matches(".*\\.(mp4|m4v|mkv|webm|mov)$");}
    static boolean picture(File f){String n=f.getName().toLowerCase(java.util.Locale.ROOT);return n.matches(".*\\.(jpg|jpeg|png|webp|bmp)$");}
    void attach(FrameLayout root,int theme){
        String key=kind()+"|"+prefs.getString("wallpaper_path","")+"|"+theme;boolean moving=prefs.getBoolean("wallpaper_motion",true);
        // Settings/history refreshes replace only the desktop controls. Keeping the attached
        // TextureView preserves its Surface, decoder and last frame during those refreshes.
        if(layer!=null&&layer.getParent()==root&&key.equals(attachedKey)){if(moving!=attachedMotion){attachedMotion=moving;animatePhoto();}return;}
        release();if(layer!=null&&layer.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)layer.getParent()).removeView(layer);
        attachedKey=key;attachedMotion=moving;image=null;texture=null;layer=new FrameLayout(owner);layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);root.addView(layer,0,new FrameLayout.LayoutParams(-1,-1));layer.addView(new LandscapeView(owner,theme),new FrameLayout.LayoutParams(-1,-1));
        if(kind().equals("theme")){status();return;}
        image=new PhotoView(owner);layer.addView(image,new FrameLayout.LayoutParams(-1,-1));loadImage();
        if(kind().equals("video")){
            texture=new TextureView(owner);final TextureView attachedTexture=texture;texture.setOpaque(false);layer.addView(texture,new FrameLayout.LayoutParams(-1,-1));
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){if(active)play();}
                public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){cropVideo();}
                public boolean onSurfaceTextureDestroyed(SurfaceTexture t){if(texture==attachedTexture)releasePlayer();return true;}
                public void onSurfaceTextureUpdated(SurfaceTexture t){if(texture==attachedTexture&&player!=null&&active){videoFrames++;lastVideoFrameAt=android.os.SystemClock.uptimeMillis();}}
            });
        }
        layer.addView(new Scrim(owner),new FrameLayout.LayoutParams(-1,-1));if(active)start();status();
    }
    private void loadImage(){
        final String key=kind().equals("image")?prefs.getString("wallpaper_path",""):"builtin";
        if(key.equals(cachedKey)&&cached!=null){showImage();return;}
        if(key.equals(loadingKey))return;loadingKey=key;
        final int w=owner.getResources().getDisplayMetrics().widthPixels,h=owner.getResources().getDisplayMetrics().heightPixels;
        worker.execute(new Runnable(){public void run(){Bitmap b=null;try{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;decode(key,bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;android.app.ActivityManager manager=(android.app.ActivityManager)owner.getSystemService(Activity.ACTIVITY_SERVICE);long maxPixels=manager!=null&&manager.isLowRamDevice()?2500000L:6000000L;while(bounds.outWidth>0&&bounds.outHeight>0&&((long)(bounds.outWidth/options.inSampleSize)*(bounds.outHeight/options.inSampleSize)>maxPixels||bounds.outWidth/options.inSampleSize>w*1.5f||bounds.outHeight/options.inSampleSize>h*1.5f)){if(options.inSampleSize>16384)break;options.inSampleSize*=2;}b=decode(key,options);}catch(Throwable ignored){}
            final Bitmap bitmap=b;owner.runOnUiThread(new Runnable(){public void run(){if(disposed){if(bitmap!=null)bitmap.recycle();return;}loadingKey="";String current=kind().equals("image")?prefs.getString("wallpaper_path",""):"builtin";if(!key.equals(current)){if(bitmap!=null)bitmap.recycle();return;}if(bitmap==null){Toast.makeText(owner,"图片暂时无法读取，请更换背景",Toast.LENGTH_LONG).show();return;}Bitmap previous=cached;if(image!=null)image.setImageBitmap(null);cached=bitmap;cachedKey=key;if(previous!=null&&previous!=bitmap&&!previous.isRecycled())previous.recycle();showImage();status();}});
        }});
    }
    private Bitmap decode(String key,BitmapFactory.Options o){o.inScaled=false;Bitmap bitmap=key.equals("builtin")?BitmapFactory.decodeResource(owner.getResources(),R.raw.nature_lake,o):BitmapFactory.decodeFile(key,o);if(bitmap==null||key.equals("builtin")||o.inJustDecodeBounds)return bitmap;try{android.media.ExifInterface exif=new android.media.ExifInterface(key);int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;default:return bitmap;}Bitmap oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(oriented!=bitmap)bitmap.recycle();return oriented;}catch(Exception ignored){return bitmap;}}
    private void showImage(){if(image!=null&&cached!=null){image.setImageBitmap(cached);if(active)animatePhoto();}}
    void resume(){
        active=true;
        // UI_HIDDEN can recycle the image while another app is in front. The
        // desktop and its layer survive that trip, so attach() need not run.
        if(image!=null&&cached==null&&!kind().equals("theme"))loadImage();
        start();status();
    }
    void pause(){active=false;release();status();}
    void dispose(){pause();disposed=true;worker.shutdownNow();}
    void trimMemory(int level){if(level<android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)return;if(motion!=null){motion.cancel();motion=null;}if(image!=null)image.setImageBitmap(null);if(cached!=null&&!cached.isRecycled())cached.recycle();cached=null;cachedKey="";if(active&&!kind().equals("theme"))loadImage();}
    private void start(){if(kind().equals("video")){if(texture!=null&&texture.isAvailable())play();}else animatePhoto();}
    private void animatePhoto(){
        if(motion!=null){motion.cancel();motion=null;}if(image==null)return;image.setProgress(-1);
        if(!active||kind().equals("video")||!prefs.getBoolean("wallpaper_motion",true))return;
        final PhotoView target=image;motion=ValueAnimator.ofFloat(0,1);motion.setDuration(45000);motion.setRepeatMode(ValueAnimator.REVERSE);motion.setRepeatCount(ValueAnimator.INFINITE);motion.setInterpolator(new android.view.animation.LinearInterpolator());motion.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){long last;public void onAnimationUpdate(ValueAnimator a){long now=android.os.SystemClock.uptimeMillis();if(now-last<33)return;last=now;target.setProgress((Float)a.getAnimatedValue());}});motion.start();
    }
    private void play(){
        if(player!=null||!active||texture==null||!texture.isAvailable())return;String path=prefs.getString("wallpaper_path","");if(path.equals(videoFailedPath)){videoFailed=true;texture.setVisibility(View.GONE);return;}videoFailed=false;videoLoops=0;videoEndFallbacks=0;videoCompletions=0;videoErrorWhat=0;videoErrorExtra=0;videoFrames=0;
        prepareVideo(false);
    }
    private void prepareVideo(final boolean repeating){
        if(player!=null||!active||disposed||texture==null||!texture.isAvailable())return;
        try{if(surface==null)surface=new Surface(texture.getSurfaceTexture());player=new MediaPlayer();player.setSurface(surface);player.setDataSource(prefs.getString("wallpaper_path",""));player.setVolume(0,0);player.setLooping(false);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer p){if(player!=p)return;videoHandler.removeCallbacks(videoPrepareGuard);try{videoReady=true;videoWidth=p.getVideoWidth();videoHeight=p.getVideoHeight();cropVideo();p.setVolume(0,0);if(active){p.start();if(repeating)videoLoops++;lastVideoFrameAt=android.os.SystemClock.uptimeMillis();guardVideoFrames=videoFrames;videoHandler.removeCallbacks(videoFrameGuard);videoHandler.postDelayed(videoFrameGuard,5000);armVideoEndGuard(p);}status();}catch(Exception e){if(player==p)failedVideo();}}});
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer p){if(player==p&&active&&!disposed&&videoReady){videoCompletions++;repeatVideo(p);}}});
            player.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer p,int what,int extra){if(player==p){videoErrorWhat=what;videoErrorExtra=extra;failedVideo();}return true;}});player.prepareAsync();videoHandler.postDelayed(videoPrepareGuard,20000);
        }catch(Exception e){failedVideo();}
    }
    private void armVideoEndGuard(MediaPlayer p){videoHandler.removeCallbacks(videoEndGuard);if(player==p&&active&&!disposed&&videoReady)videoHandler.postDelayed(videoEndGuard,Math.max(250L,(long)p.getDuration()+350L));}
    private void repeatVideo(MediaPlayer p){
        if(player!=p||!active||disposed||!videoReady)return;
        // Some Amlogic video-only decoders fail native looping and acknowledge a
        // seek without restarting output. Rebuild the decoder for every complete
        // round, retaining the Surface and its last frame while preparing.
        releaseDecoder();prepareVideo(true);
    }
    private void cropVideo(){if(texture==null||videoWidth<=0||videoHeight<=0)return;float w=texture.getWidth(),h=texture.getHeight();if(w==0||h==0)return;float scale=Math.max(w/videoWidth,h/videoHeight);Matrix m=new Matrix();m.setScale(videoWidth*scale/w,videoHeight*scale/h,w/2,h/2);texture.setTransform(m);}
    private void failedVideo(){if(videoFailed)return;videoFailed=true;videoFailedPath=prefs.getString("wallpaper_path","");releasePlayer();if(texture!=null)texture.setVisibility(View.GONE);Toast.makeText(owner,"这个视频暂时无法播放，已显示内置风景",Toast.LENGTH_LONG).show();status();}
    private void releaseDecoder(){videoHandler.removeCallbacks(videoEndGuard);videoHandler.removeCallbacks(videoPrepareGuard);videoHandler.removeCallbacks(videoFrameGuard);videoReady=false;MediaPlayer old=player;player=null;if(old!=null){try{old.release();}catch(Exception ignored){}}}
    private void releasePlayer(){releaseDecoder();if(surface!=null){surface.release();surface=null;}}
    private void release(){if(motion!=null){motion.cancel();motion=null;}releasePlayer();}
    private synchronized int beginImport(){if(disposed||importBusy)return -1;importBusy=true;return ++importGeneration;}
    void cancelImport(){Future<?> task=pendingImport;pendingImport=null;synchronized(this){importBusy=false;importGeneration++;}if(task!=null)task.cancel(true);}
    private void finishImport(int token,Imported callback,boolean success,boolean isVideo){owner.runOnUiThread(()->{synchronized(Wallpapers.this){if(token!=importGeneration)return;importBusy=false;pendingImport=null;}if(!disposed)callback.done(success,isVideo);});}
    private boolean libraryHasRoom(long incoming){return libraryHasRoom(incoming,null);}
    private boolean libraryHasRoom(long incoming,File excluded){File dir=library();File[] files=dir.listFiles();long used=0;int count=0;if(files!=null)for(File f:files)if(f.isFile()&&!f.getName().startsWith(".")&&(excluded==null||!f.equals(excluded))){used+=f.length();count++;}return WallpaperImportPolicy.libraryAllowed(count,used,incoming,dir.getUsableSpace());}
    private boolean libraryContentWithinBounds(long incoming){return libraryContentWithinBounds(incoming,null);}
    private boolean libraryContentWithinBounds(long incoming,File excluded){File[] files=library().listFiles();long used=0;int count=0;if(files!=null)for(File f:files)if(f.isFile()&&!f.getName().startsWith(".")&&(excluded==null||!f.equals(excluded))){used+=f.length();count++;}return count<WallpaperImportPolicy.MAX_LIBRARY_FILES&&incoming>0&&used<=WallpaperImportPolicy.MAX_LIBRARY_BYTES-incoming;}
    private boolean validImage(File file){BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);return options.outWidth>0&&options.outHeight>0;}
    void importFile(final File source,final Imported callback){
        final int token=beginImport();if(token<0){callback.done(false,false);return;}
        pendingImport=worker.submit(new Runnable(){public void run(){boolean success=false;File dest=null,temp=null;boolean owned=false;boolean isVideo=video(source);try{
            if(!source.isFile()||(!isVideo&&!picture(source)))throw new IOException();
            String filename=source.getName();long limit=WallpaperImportPolicy.limit(filename.substring(filename.lastIndexOf('.')+1).toLowerCase(java.util.Locale.ROOT));if(source.length()<=0||source.length()>limit)throw new IOException();
            String lib=library().getCanonicalPath()+File.separator;
            if(source.getCanonicalPath().startsWith(lib))dest=source;
            else{if(!libraryHasRoom(source.length()))throw new IOException();dest=new File(library(),System.nanoTime()+"-"+source.getName());temp=new File(dest.getPath()+".tmp");owned=true;try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];long copied=0;int n;while((n=in.read(b))!=-1){copied+=n;if(copied>limit||Thread.currentThread().isInterrupted())throw new IOException();out.write(b,0,n);}}if(!temp.renameTo(dest))throw new IOException();}
            if(Thread.currentThread().isInterrupted()||(!isVideo&&!validImage(dest)))throw new IOException();if(owned&&!libraryContentWithinBounds(dest.length(),dest))throw new IOException();
            if(!prefs.edit().putString("wallpaper_kind",isVideo?"video":"image").putString("wallpaper_path",dest.getCanonicalPath()).commit())throw new IOException();success=true;
        }catch(Exception ignored){}
        finally{if(temp!=null)temp.delete();if(!success&&owned&&dest!=null)dest.delete();finishImport(token,callback,success,isVideo);}
        }});
    }
    void importDocument(final android.net.Uri uri,final Imported callback){
        final int token=beginImport();if(token<0){callback.done(false,false);return;}
        pendingImport=worker.submit(()->{File dest=null,temp=null;boolean ok=false,isVideo=false;try{
            if(!"content".equals(uri.getScheme())&&!"file".equals(uri.getScheme()))throw new IOException();
            android.content.ContentResolver resolver=owner.getContentResolver();String name="";long expected=-1;
            try(android.database.Cursor cursor=resolver.query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME,android.provider.OpenableColumns.SIZE},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){name=cursor.getString(0);int size=cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);if(size>=0&&!cursor.isNull(size))expected=cursor.getLong(size);}}catch(Exception ignored){}
            String extension=WallpaperImportPolicy.extension(resolver.getType(uri),name);if(extension.isEmpty())throw new IOException();isVideo=WallpaperImportPolicy.video(extension);long limit=WallpaperImportPolicy.limit(extension);if(expected>limit||!libraryHasRoom(expected>0?expected:1))throw new IOException();
            File dir=library();dest=new File(dir,"import-"+System.nanoTime()+"."+extension);temp=new File(dir,"."+dest.getName()+".tmp");long total=0;
            try(InputStream in=resolver.openInputStream(uri);OutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException();byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1){total+=count;if(total>limit||Thread.currentThread().isInterrupted())throw new IOException();out.write(buffer,0,count);}if(total==0)throw new IOException();}
            if(Thread.currentThread().isInterrupted()||(!isVideo&&!validImage(temp))||!libraryContentWithinBounds(total))throw new IOException();if(!temp.renameTo(dest))throw new IOException();
            if(!prefs.edit().putString("wallpaper_kind",isVideo?"video":"image").putString("wallpaper_path",dest.getCanonicalPath()).commit())throw new IOException();ok=true;
        }catch(Exception ignored){}finally{if(temp!=null)temp.delete();if(!ok&&dest!=null)dest.delete();finishImport(token,callback,ok,isVideo);}});
    }
    void status(){try{File dir=owner.getExternalFilesDir(null);if(dir==null)return;String playing="false",looping="false",nativeLooping="false";int position=0,duration=0;try{if(player!=null&&videoReady){playing=String.valueOf(player.isPlaying());looping=String.valueOf(active&&!disposed);nativeLooping=String.valueOf(player.isLooping());position=player.getCurrentPosition();duration=player.getDuration();}}catch(Exception ignored){}try(Writer out=new FileWriter(new File(dir,"wallpaper-status.txt"))){out.write("kind="+kind()+"\nactive="+active+"\nphoto_motion="+(motion!=null&&motion.isRunning())+"\nvideo_playing="+playing+"\nvideo_looping="+looping+"\nvideo_native_looping="+nativeLooping+"\nvideo_loop_mode=explicit-restart-with-tail-watchdog\nvideo_loops="+videoLoops+"\nvideo_completions="+videoCompletions+"\nvideo_error_what="+videoErrorWhat+"\nvideo_error_extra="+videoErrorExtra+"\nvideo_end_fallbacks="+videoEndFallbacks+"\nvideo_frames="+videoFrames+"\nvideo_position="+position+"\nvideo_duration="+duration+"\nvideo_muted=true\nvideo_failed="+videoFailed+"\nbitmap="+(cached==null?"none":cached.getWidth()+"x"+cached.getHeight())+"\n");}}catch(Exception ignored){}}
    private static final class Scrim extends View {private final Paint p=new Paint(3);Scrim(Activity a){super(a);}protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setShader(new LinearGradient(0,0,0,h,new int[]{0x30040b13,0x20040b13,0x80040b13,0xe6040b13},new float[]{0,.2f,.52f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);}}
    private static final class PhotoView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private Bitmap bitmap;private float progress=-1;
        PhotoView(Activity a){super(a);}
        void setImageBitmap(Bitmap b){bitmap=b;invalidate();}
        void setProgress(float p){progress=p;invalidate();}
        protected void onDraw(Canvas c){if(bitmap==null||bitmap.isRecycled())return;float w=getWidth(),h=getHeight(),zoom=progress<0?1:1.03f+progress*.04f;float scale=Math.max(w/bitmap.getWidth(),h/bitmap.getHeight())*zoom;float dx=progress<0?0:w*.008f*(progress*2-1),dy=progress<0?0:h*.004f*(1-progress*2);c.save();c.translate(w/2+dx,h/2+dy);c.scale(scale,scale);c.drawBitmap(bitmap,-bitmap.getWidth()/2f,-bitmap.getHeight()/2f,paint);c.restore();}
    }
}
