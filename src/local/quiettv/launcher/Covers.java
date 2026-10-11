package local.quiettv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.net.URI;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays bounded artwork supplied locally by media applications; never downloads covers. */
final class Covers {
    private final File directory;
    private final Context app;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> bitmaps=new LruCache<>(12);
    private final CoverCallbacks pending=new CoverCallbacks();
    private final HashMap<String,Long> failed=new HashMap<>();
    private final HashMap<String,Integer> failures=new HashMap<>();
    private boolean disposed;
    Covers(Context c){app=c.getApplicationContext();directory=new File(c.getCacheDir(),"history_covers");directory.mkdirs();}
    static String safeUrl(String text){try{if(text==null||text.length()>1500)return "";if(text.startsWith("//"))text="https:"+text;if(text.startsWith("http://"))text="https://"+text.substring(7);URI u=new URI(text);String host=u.getHost();return "https".equals(u.getScheme())&&host!=null&&(host.equals("hdslb.com")||host.endsWith(".hdslb.com")||host.equals("biliimg.com")||host.endsWith(".biliimg.com"))&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&(u.getPort()==-1||u.getPort()==443)?text:"";}catch(Exception e){return "";}}
    static String safeMusicUrl(String text){try{URI u=new URI(text);String host=u.getHost();return text.length()<=1500&&"https".equals(u.getScheme())&&host!=null&&host.endsWith(".mzstatic.com")&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&(u.getPort()==-1||u.getPort()==443)&&u.getPath().startsWith("/image/thumb/")?text:"";}catch(Exception e){return "";}}
    static String thumbnailUrl(String text){String safe=safeUrl(text);if(safe.isEmpty()){String music=safeMusicUrl(text);return music.isEmpty()?MediaArtwork.safeOtherRemote(text):music;}try{URI uri=new URI(safe);if(!uri.getPath().startsWith("/bfs/"))return safe;int style=safe.indexOf('@',safe.indexOf('/',8));if(style>=0)safe=safe.substring(0,style);return safe+"@960w_540h_1c.jpg";}catch(Exception e){return "";}}
    Bitmap get(String input,final Runnable ready){
        final String address=MediaArtwork.local(input)?input:"";if(address.isEmpty()||disposed)return null;Bitmap b=bitmaps.get(address);if(b!=null)return b;Long failure=failed.get(address);if(failure!=null&&System.currentTimeMillis()<failure)return null;if(!pending.begin(address,ready))return null;
        worker.execute(new Runnable(){public void run(){Bitmap decoded=null;try{
            File file=MediaArtwork.file(app,address);
            if(file==null||!file.isFile())throw new java.io.IOException("Artwork cache absent");
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>640||o.outHeight/o.inSampleSize>640)o.inSampleSize*=2;o.inJustDecodeBounds=false;decoded=BitmapFactory.decodeFile(file.getPath(),o);if(decoded==null)file.delete();trim();
        }catch(Exception e){android.util.Log.w("QuietTVCovers","Public cover unavailable: "+e.getClass().getSimpleName());}
        final Bitmap result=decoded;main.post(new Runnable(){public void run(){final java.util.ArrayList<Runnable> callbacks=pending.take(address);if(disposed)return;if(result!=null){bitmaps.put(address,result);failed.remove(address);failures.remove(address);for(Runnable callback:callbacks)callback.run();}else{int count=failures.containsKey(address)?failures.get(address)+1:1;failures.put(address,count);final long delay=count==1?15000:count==2?60000:300000;failed.put(address,System.currentTimeMillis()+delay);if(count<=2)main.postDelayed(new Runnable(){public void run(){if(!disposed)for(Runnable callback:callbacks)callback.run();}},delay);}}});
        }});return null;
    }
    private void trim(){File[] files=directory.listFiles();if(files==null||files.length<=16)return;Arrays.sort(files,new Comparator<File>(){public int compare(File a,File b){return Long.compare(b.lastModified(),a.lastModified());}});for(int i=16;i<files.length;i++)files[i].delete();}
    void dispose(){disposed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);pending.clear();bitmaps.evictAll();}
}
