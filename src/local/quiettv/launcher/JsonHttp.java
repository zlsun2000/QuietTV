package local.quiettv.launcher;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Small bounded HTTPS client. No cookies, credentials, analytics or UI-thread work. */
final class JsonHttp {
    static final class HttpStatusException extends java.io.IOException {final int status;HttpStatusException(int value){super("HTTP "+value);status=value;}}
    static JSONObject get(String address) throws Exception {
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol())||!(url.getHost().equals("api.open-meteo.com")||url.getHost().equals("geocoding-api.open-meteo.com"))||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443))throw new IllegalArgumentException("Weather endpoint required");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","QuietTV");
        try {
            int status=c.getResponseCode();if(status!=200)throw new HttpStatusException(status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=in.read(buffer))!=-1){if(out.size()+n>524288)throw new java.io.IOException("Response too large");out.write(buffer,0,n);}
                return new JSONObject(new String(out.toByteArray(),"UTF-8"));
            }
        } finally {c.disconnect();}
    }
    private JsonHttp(){}
}
