package local.quiettv.launcher;

import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.io.*;
import java.net.*;

/** Optional, reviewed publisher artwork only; see docs/ARTWORK-REVIEW.md.
 * No catalog lookup, credentials, redirects, URL persistence or remote logging.
 */
final class WatchNextArtwork {
    static void validate(JSONObject art)throws Exception {
        PlaybackRule.keys(art,"source,httpsHosts,queryKeys");
        JSONArray query=art.optJSONArray("queryKeys");if(query!=null){PlaybackRule.require(query.length()<=8,"封面参数过多");java.util.HashSet<String> names=new java.util.HashSet<>();for(int i=0;i<query.length();i++){String key=query.getString(i);PlaybackRule.require(PlaybackRule.key(key)&&!key.matches("(?i).*(password|token|cookie|authorization|credential|account|profile|session|play.?url|stream.?url|media.?url).*")&&names.add(key),"封面参数名不正确");}}PlaybackRule.require(art.getString("source").equals("poster"),"仅支持系统海报字段");
        JSONArray hosts=art.getJSONArray("httpsHosts");PlaybackRule.require(hosts.length()<=4,"封面域名过多");
        for(int i=0;i<hosts.length();i++){String host=hosts.getString(i);PlaybackRule.require(host.length()<=160&&host.matches("(?:\\*\\.)?[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")&&!host.endsWith(".local")&&!host.endsWith(".localhost")&&!host.matches("[0-9.]+"),"封面域名必须是公开 DNS 名称");}
    }
    static URL allowed(PlaybackRule rule,String value)throws Exception {
        JSONObject art=rule.json.getJSONObject("watchNext").getJSONObject("artwork");
        if(value==null||value.length()>1500)return null;URI uri=new URI(value);String host=uri.getHost();
        if(!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getFragment()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||!uri.getRawPath().startsWith("/"))return null;
        android.net.Uri supplied=android.net.Uri.parse(value);JSONArray query=art.optJSONArray("queryKeys");int queryCount=query==null?0:query.length();java.util.Set<String> actual=supplied.getQueryParameterNames();
        if(actual.size()!=queryCount||(supplied.getEncodedQuery()==null?0:supplied.getEncodedQuery().split("&",-1).length)!=queryCount)return null;
        for(int i=0;i<queryCount;i++){String key=query.getString(i);java.util.List<String> values=supplied.getQueryParameters(key);if(!actual.contains(key)||values.size()!=1||!PlaybackRule.token(values.get(0)))return null;}
        JSONArray hosts=art.getJSONArray("httpsHosts");for(int i=0;i<hosts.length();i++){String expected=hosts.getString(i);if(expected.equals(host)||expected.startsWith("*.")&&host.endsWith(expected.substring(1))&&host.length()>expected.length()-1)return uri.toURL();}return null;
    }
    static boolean publicAddress(InetAddress address){
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
        byte[] raw=address.getAddress();if(raw.length==16)return (raw[0]&0xfe)!=0xfc;
        int first=raw[0]&255,second=raw[1]&255;return first!=0&&first!=127&&!(first==100&&second>=64&&second<=127)&&!(first==169&&second==254)&&!(first==192&&second==0)&&first<224;
    }
    static Bitmap read(Context c,PlaybackRule rule,String value)throws Exception {
        if(!WatchNextHistory.eligible(c,rule))return null;URL url=allowed(rule,value);if(url==null)return null;
        for(InetAddress address:InetAddress.getAllByName(url.getHost()))if(!publicAddress(address))return null;
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(4000);connection.setReadTimeout(4000);connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);connection.setRequestProperty("Accept","image/*");connection.setRequestProperty("User-Agent","QuietTV");
        try{
            if(!WatchNextHistory.eligible(c,rule)||connection.getResponseCode()!=200)return null;
            String type=connection.getContentType();if(type==null||!type.toLowerCase(java.util.Locale.US).startsWith("image/")||connection.getContentLength()>2097152)return null;
            long deadline=android.os.SystemClock.elapsedRealtime()+6000;
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int size;while((size=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()>deadline||out.size()+size>2097152)throw new IOException("Artwork bound");out.write(buffer,0,size);}
                byte[] bytes=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(options.outWidth<1||options.outHeight<1||options.outWidth>8192||options.outHeight>8192)return null;
                options.inSampleSize=1;while(options.outWidth/options.inSampleSize>640||options.outHeight/options.inSampleSize>640)options.inSampleSize*=2;options.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            }
        }finally{connection.disconnect();}
    }
    private WatchNextArtwork(){}
}
