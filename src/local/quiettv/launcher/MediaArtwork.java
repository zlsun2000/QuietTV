package local.quiettv.launcher;

import android.content.*;
import android.graphics.*;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Copies only publicly supplied artwork into a bounded local cache. */
final class MediaArtwork {
    private final Context app;
    private final File directory;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private int pending;
    private boolean disposed;
    MediaArtwork(Context c){app=c.getApplicationContext();directory=new File(app.getCacheDir(),"media_artwork");directory.mkdirs();}
    static boolean local(String value){return value!=null&&value.matches("localart:[a-f0-9]{64}");}
    static File file(Context c,String reference){return local(reference)?new File(new File(c.getCacheDir(),"media_artwork"),reference.substring(9)):null;}
    boolean capture(final PlaybackRecord record,MediaMetadata metadata){
        if(disposed||pending>=8||metadata==null)return false;
        Bitmap art=metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);if(art==null)art=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);if(art==null)art=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        String address=metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);if(address==null||address.isEmpty())address=metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);if(address==null||address.isEmpty())address=metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        final Bitmap bitmap=art;final String uri=MediaRecordPolicy.text(address,1500);if(bitmap==null&&uri.isEmpty())return false;
        final String ref="localart:"+MediaRecordPolicy.digest(record.key());final File target=file(app,ref);pending++;
        worker.execute(new Runnable(){public void run(){boolean saved=false;Bitmap image=null;try{
            {
                image=bitmap;if(image==null)image=read(uri,record.source);if(image==null||image.isRecycled()||image.getWidth()<1||image.getHeight()<1)throw new IOException("No artwork");
                float scale=Math.min(1f,640f/Math.max(image.getWidth(),image.getHeight()));Bitmap output=Bitmap.createScaledBitmap(image,Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale)),true);File temp=new File(target.getPath()+".tmp");try{try(FileOutputStream out=new FileOutputStream(temp)){if(!output.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("Cannot encode artwork");}if(target.exists()&&!target.delete())throw new IOException("Cannot replace artwork");saved=temp.renameTo(target);}finally{temp.delete();if(output!=image)output.recycle();}trim();
            }
        }catch(Exception e){android.util.Log.w("QuietTVMedia","Artwork unavailable: "+e.getClass().getSimpleName());}finally{if(image!=null&&image!=bitmap&&!image.isRecycled())image.recycle();}
        final boolean success=saved;main.post(new Runnable(){public void run(){pending--;if(!disposed&&success&&new HistoryStore(app).patchCover(record.key(),ref))GenericMediaHistory.changed(app);}});
        }});return true;
    }
    private Bitmap read(String text,String pkg) throws Exception {
        Uri uri=Uri.parse(text);InputStream input;
        if("content".equals(uri.getScheme())){android.content.pm.ProviderInfo provider=app.getPackageManager().resolveContentProvider(uri.getAuthority(),0);boolean granted=app.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)==android.content.pm.PackageManager.PERMISSION_GRANTED;if(provider==null||!pkg.equals(provider.packageName)||(!provider.exported&&!granted))return null;input=app.getContentResolver().openInputStream(uri);}
        else return null;
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)return null;byte[] data=new byte[4096];int count;while((count=in.read(data))!=-1){if(out.size()+count>2097152)throw new IOException("Artwork too large");out.write(data,0,count);}byte[] bytes=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(options.outWidth<=0||options.outHeight<=0||options.outWidth>8192||options.outHeight>8192)return null;options.inSampleSize=1;while(options.outWidth/options.inSampleSize>640||options.outHeight/options.inSampleSize>640)options.inSampleSize*=2;options.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}
    }
    static String safeRemote(String value){if(!Covers.safeUrl(value).isEmpty())return Covers.thumbnailUrl(value);if(!Covers.safeMusicUrl(value).isEmpty())return value;return safeOtherRemote(value);}
    static String safeOtherRemote(String value){try{if(value!=null&&value.startsWith("http://"))value="https://"+value.substring(7);URI uri=new URI(value);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||value.length()>1500||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))return "";for(String domain:new String[]{"iqiyipic.com","qiyipic.com","nflximg.net","nflximg.com","nflxso.net","disney-plus.net"})if(host.equals(domain)||host.endsWith("."+domain))return value;}catch(Exception ignored){}return "";}
    private void trim(){File[] files=directory.listFiles();if(files==null||files.length<=16)return;Arrays.sort(files,new Comparator<File>(){public int compare(File a,File b){return Long.compare(b.lastModified(),a.lastModified());}});for(int n=16;n<files.length;n++)files[n].delete();}
    void dispose(){disposed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
