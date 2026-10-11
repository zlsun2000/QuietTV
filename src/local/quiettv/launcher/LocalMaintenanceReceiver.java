package local.quiettv.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** ADB maintenance only: guarded by the platform DUMP permission, never starts UI. */
public final class LocalMaintenanceReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context context,Intent intent){
        if(intent==null)return;UserManager user=(UserManager)context.getSystemService(Context.USER_SERVICE);if(Build.VERSION.SDK_INT>=24&&user!=null&&!user.isUserUnlocked())return;
        String action=intent.getAction();
        try{
            if("local.quiettv.launcher.EXPORT_STATE".equals(action)){JSONObject root=new JSONObject();for(String name:new String[]{"home","weather","playback_history"})root.put(name,export(context.getSharedPreferences(name,Context.MODE_PRIVATE)));write(context,"quiettv-state-backup.json",root);}
            else if("local.quiettv.launcher.IMPORT_STATE".equals(action)){File file=new File(context.getExternalFilesDir(null),"quiettv-state-backup.json");if(!file.isFile()||file.length()>262144)return;byte[] bytes=new byte[(int)file.length()];try(FileInputStream input=new FileInputStream(file)){int offset=0,n;while(offset<bytes.length&&(n=input.read(bytes,offset,bytes.length-offset))>0)offset+=n;if(offset!=bytes.length)return;}JSONObject root=new JSONObject(new String(bytes,"UTF-8"));for(String name:new String[]{"home","weather","playback_history"}){JSONObject prefs=root.optJSONObject(name);if(prefs!=null)restore(context.getSharedPreferences(name,Context.MODE_PRIVATE),prefs);}context.sendBroadcast(new Intent(HistoryStore.CHANGED).setPackage(context.getPackageName()));}
            else if("local.quiettv.launcher.EXTRAS_STATUS".equals(action))status(context);
            else if("local.quiettv.launcher.MEDIA_REBIND".equals(action)){GenericMediaHistory.rebind(context);status(context);}
            else if("local.quiettv.launcher.WEATHER_SETUP".equals(action)){
                // Service terms must be accepted in the visible settings UI.
                if(!"manual".equals(intent.getStringExtra("mode")))return;
                WeatherRepository repo=new WeatherRepository(context,()->{});
                try{if(!repo.consented())return;status(context);}finally{repo.dispose();}
            }
        }catch(Exception ignored){}
    }
    private static void status(Context c) throws Exception {
        JSONObject j=new JSONObject();SharedPreferences weather=c.getSharedPreferences("weather",Context.MODE_PRIVATE);HistoryStore history=new HistoryStore(c);j.put("version","0.12.0");j.put("historyCount",history.list().size());j.put("historyEnabled",history.enabled());j.put("musicHistoryEnabled",history.musicEnabled());int music=0;for(PlaybackRecord r:history.list())if(r.apple())music++;j.put("musicHistoryCount",music);j.put("genericHistoryEnabled",history.genericEnabled());j.put("notificationAccess",GenericMediaHistory.permission(c));j.put("mediaListenerConnected",MediaHistoryService.active);j.put("watchNextProviderAvailable",c.getPackageManager().resolveContentProvider("android.media.tv",0)!=null);int generic=0;for(PlaybackRecord r:history.list())if(r.generic())generic++;j.put("genericHistoryCount",generic);j.put("activeSessionCount",GenericMediaHistory.sessions(c).size());j.put("weatherEnabled",weather.getBoolean("enabled",false));j.put("weatherConsent",weather.getString("consent",""));
        try{JSONObject cache=new JSONObject(weather.getString("cache","{}"));j.put("weatherCached",cache.has("temperature"));j.put("weatherUpdated",cache.optLong("updated"));JSONObject place=cache.optJSONObject("place");if(place!=null)j.put("weatherCity",place.optString("name"));}catch(Exception ignored){j.put("weatherCached",false);}write(c,"extras-status.json",j);
    }
    private static JSONObject export(SharedPreferences prefs) throws Exception {JSONObject root=new JSONObject();for(Map.Entry<String,?> entry:prefs.getAll().entrySet()){Object value=entry.getValue();JSONObject item=new JSONObject();String type=value instanceof Set?"set":value instanceof Integer?"int":value instanceof Long?"long":value instanceof Float?"float":value instanceof Boolean?"boolean":"string";item.put("type",type);if(value instanceof Set){JSONArray a=new JSONArray();for(Object v:(Set<?>)value)a.put(String.valueOf(v));item.put("value",a);}else item.put("value",value);root.put(entry.getKey(),item);}return root;}
    private static void restore(SharedPreferences prefs,JSONObject root) throws Exception {SharedPreferences.Editor e=prefs.edit().clear();java.util.Iterator<String> keys=root.keys();while(keys.hasNext()){String key=keys.next();JSONObject item=root.getJSONObject(key);String type=item.getString("type");if("set".equals(type)){JSONArray a=item.getJSONArray("value");HashSet<String> values=new HashSet<>();for(int i=0;i<a.length();i++)values.add(a.getString(i));e.putStringSet(key,values);}else if("int".equals(type))e.putInt(key,item.getInt("value"));else if("long".equals(type))e.putLong(key,item.getLong("value"));else if("float".equals(type))e.putFloat(key,(float)item.getDouble("value"));else if("boolean".equals(type))e.putBoolean(key,item.getBoolean("value"));else if("string".equals(type))e.putString(key,item.getString("value"));}e.commit();}
    private static void write(Context c,String name,JSONObject j) throws Exception {File dir=c.getExternalFilesDir(null);if(dir==null)return;File target=new File(dir,name),tmp=new File(dir,name+".tmp");try(OutputStreamWriter out=new OutputStreamWriter(new FileOutputStream(tmp),"UTF-8")){out.write(j.toString());}if(!tmp.renameTo(target))throw new java.io.IOException("Cannot save status");}
}
