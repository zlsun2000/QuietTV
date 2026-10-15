package local.quiettv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/** Local playback records plus public metadata; never reads another app's private files. */
final class HistoryStore {
    static final String CHANGED="local.quiettv.launcher.HISTORY_CHANGED";
    private static final int MAX_REMOVED_KEYS=64;
    private final Context context;
    private final SharedPreferences prefs;
    HistoryStore(Context context){this.context=context.getApplicationContext();prefs=context.getSharedPreferences("playback_history",Context.MODE_PRIVATE);if(!prefs.contains("config_schema")){boolean existing=!prefs.getAll().isEmpty();prefs.edit().putBoolean("enabled",prefs.getBoolean("enabled",existing)).putBoolean("music_enabled",prefs.getBoolean("music_enabled",existing)).putBoolean("generic_enabled",prefs.getBoolean("generic_enabled",existing)).putInt("config_schema",1).apply();}}
    boolean enabled(){return prefs.getBoolean("enabled",true);}
    boolean musicEnabled(){return prefs.getBoolean("music_enabled",true);}
    void musicEnabled(boolean value){prefs.edit().putBoolean("music_enabled",value).apply();}
    void enabled(boolean enabled){prefs.edit().putBoolean("enabled",enabled).apply();}
    boolean genericEnabled(){return prefs.getBoolean("generic_enabled",true);}
    void genericEnabled(boolean value){prefs.edit().putBoolean("generic_enabled",value).apply();}
    boolean appEnabled(String pkg){return !prefs.getStringSet("blocked_media_apps",java.util.Collections.<String>emptySet()).contains(pkg);}
    void appEnabled(String pkg,boolean enabled){java.util.HashSet<String> blocked=new java.util.HashSet<>(prefs.getStringSet("blocked_media_apps",java.util.Collections.<String>emptySet()));if(enabled)blocked.remove(pkg);else blocked.add(pkg);prefs.edit().putStringSet("blocked_media_apps",blocked).apply();}
    ArrayList<PlaybackRecord> list(){
        ArrayList<PlaybackRecord> list=new ArrayList<>();
        try{JSONArray a=new JSONArray(prefs.getString("records","[]"));for(int i=0;i<Math.min(8,a.length());i++){PlaybackRecord r=decode(a.getJSONObject(i));if(r.valid())list.add(r);}}catch(Exception ignored){}
        return list;
    }
    ArrayList<PlaybackRecord> visible(){ArrayList<PlaybackRecord> result=new ArrayList<>();if(enabled())for(PlaybackRecord r:list())if(appEnabled(r.source)&&(!r.apple()||musicEnabled())&&(!r.generic()||r.configured()||genericEnabled())&&sourceAvailable(r))result.add(r);return result;}
    private boolean sourceAvailable(PlaybackRecord record){try{return context.getPackageManager().getApplicationInfo(record.source,0).enabled;}catch(Exception ignored){return false;}}
    boolean put(PlaybackRecord record){
        if(!enabled()||!record.valid()||!appEnabled(record.source)||(record.generic()&&!record.configured()&&!genericEnabled()))return false;record.clamp();
        long removed=prefs.getLong("removed_"+record.key(),0),cleared=prefs.getLong("cleared_at",0);if((record.generic()?MediaRecordPolicy.suppressed(record.startedAt,record.updated,removed,cleared):record.updated<=cleared||removed>=record.updated)||removed>System.currentTimeMillis()-30000)return false;
        ArrayList<PlaybackRecord> records=list();
        for(int i=records.size()-1;i>=0;i--){PlaybackRecord old=records.get(i);if(old.key().equals(record.key())){record.inheritMetadata(old);records.remove(i);}else if(record.apple()&&old.apple())records.remove(i);}
        records.add(record);java.util.Collections.sort(records,new java.util.Comparator<PlaybackRecord>(){public int compare(PlaybackRecord a,PlaybackRecord b){return Long.compare(b.updated,a.updated);}});while(records.size()>8)records.remove(records.size()-1);save(records);return true;
    }
    void remove(String key){ArrayList<PlaybackRecord> records=list();for(int i=records.size()-1;i>=0;i--)if(records.get(i).key().equals(key))records.remove(i);prefs.edit().putLong("removed_"+key,System.currentTimeMillis()).apply();save(records);pruneRemoved();}
    void clear(){prefs.edit().remove("records").putLong("cleared_at",System.currentTimeMillis()).apply();}
    void removeVideo(String id){ArrayList<PlaybackRecord> records=list();SharedPreferences.Editor e=prefs.edit();long now=System.currentTimeMillis();for(int i=records.size()-1;i>=0;i--)if(records.get(i).bili()&&records.get(i).videoId.equals(id)){e.putLong("removed_"+records.get(i).key(),now);records.remove(i);}e.apply();save(records);pruneRemoved();}
    boolean patchCover(String key,String cover){if(!enabled()||!genericEnabled())return false;ArrayList<PlaybackRecord> records=list();for(PlaybackRecord r:records)if(r.generic()&&r.key().equals(key)&&appEnabled(r.source)&&!cover.equals(r.cover)){r.cover=cover;save(records);return true;}return false;}
    boolean patchMetadata(String key,String cover,String episode,boolean episodeKnown,long checkedAt){if(!enabled())return false;ArrayList<PlaybackRecord> records=list();for(PlaybackRecord r:records)if(r.bili()&&r.key().equals(key)){boolean changed=(!cover.isEmpty()&&!cover.equals(r.cover))||(episodeKnown&&!episode.equals(r.episode));if(!cover.isEmpty())r.cover=cover;if(episodeKnown)r.episode=episode;r.metadataAt=checkedAt;save(records);return changed;}return false;}
    private void save(ArrayList<PlaybackRecord> records){JSONArray a=new JSONArray();for(PlaybackRecord r:records){try{JSONObject j=new JSONObject();j.put("source",r.source);j.put("source_label",r.sourceLabel);j.put("route_id",r.routeId);j.put("route_channel",r.routeChannel);j.put("content_id",r.contentId);j.put("audio",r.audio);j.put("position_known",r.positionKnown);j.put("started_at",r.startedAt);j.put("album",r.album);j.put("id",r.videoId);j.put("eid",r.episodeId);j.put("title",r.title);j.put("episode",r.episode);j.put("cover",r.cover);j.put("command",r.command);j.put("position",r.position);j.put("duration",r.duration);j.put("updated",r.updated);j.put("metadata_at",r.metadataAt);j.put("rule_id",r.ruleId);j.put("rule_data",new JSONObject(r.ruleData));a.put(j);}catch(Exception ignored){}}prefs.edit().putString("records",a.toString()).apply();}
    private void pruneRemoved(){HashMap<String,Long> removed=new HashMap<>();for(java.util.Map.Entry<String,?> entry:prefs.getAll().entrySet())if(entry.getKey().startsWith("removed_")&&entry.getValue() instanceof Long)removed.put(entry.getKey().substring(8),(Long)entry.getValue());if(removed.size()<=MAX_REMOVED_KEYS)return;HashSet<String> active=new HashSet<>();for(android.media.session.MediaController controller:GenericMediaHistory.sessions(context)){if(active.size()>=16)break;try{PlaybackRecord r=GenericMediaHistory.record(context,controller);if(r!=null)active.add(r.key());}catch(Exception ignored){}}ArrayList<String> newest=new ArrayList<>(removed.keySet());Collections.sort(newest,(a,b)->Long.compare(removed.get(b),removed.get(a)));HashSet<String> keep=new HashSet<>(active);for(String key:newest){if(keep.size()>=MAX_REMOVED_KEYS)break;keep.add(key);}SharedPreferences.Editor editor=prefs.edit();for(String key:removed.keySet())if(!keep.contains(key))editor.remove("removed_"+key);editor.apply();}
    static PlaybackRecord decode(JSONObject j){PlaybackRecord r=new PlaybackRecord();r.source=j.optString("source",PlaybackRecord.PACKAGE);r.sourceLabel=j.optString("source_label");r.routeId=j.optString("route_id");r.routeChannel=j.optString("route_channel","0");r.contentId=j.optString("content_id");r.audio=j.optBoolean("audio");r.positionKnown=j.optBoolean("position_known",true);r.startedAt=j.optLong("started_at");r.album=j.optString("album");r.videoId=j.optString("id");r.episodeId=j.optString("eid");r.title=j.optString("title");r.episode=j.optString("episode");r.cover=j.optString("cover");r.command=j.optString("command");r.position=j.optLong("position");r.duration=j.optLong("duration");r.updated=j.optLong("updated");r.metadataAt=j.optLong("metadata_at");r.ruleId=j.optString("rule_id");JSONObject data=j.optJSONObject("rule_data");if(data!=null){java.util.Iterator<String> names=data.keys();while(names.hasNext()){String name=names.next();r.ruleData.put(name,data.optString(name));}}r.clamp();return r;}
}
