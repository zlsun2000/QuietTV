package local.quiettv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Local playback records plus public metadata; never reads another app's private files. */
final class HistoryStore {
    static final String CHANGED="local.quiettv.launcher.HISTORY_CHANGED";
    private final SharedPreferences prefs;
    HistoryStore(Context context){prefs=context.getSharedPreferences("playback_history",Context.MODE_PRIVATE);if(!prefs.contains("config_schema")){boolean existing=!prefs.getAll().isEmpty();prefs.edit().putBoolean("enabled",prefs.getBoolean("enabled",existing)).putBoolean("music_enabled",prefs.getBoolean("music_enabled",existing)).putBoolean("generic_enabled",prefs.getBoolean("generic_enabled",existing)).putInt("config_schema",1).apply();}}
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
    ArrayList<PlaybackRecord> visible(){ArrayList<PlaybackRecord> result=new ArrayList<>();if(enabled())for(PlaybackRecord r:list())if((!r.apple()||musicEnabled())&&(!r.generic()||(genericEnabled()&&appEnabled(r.source))))result.add(r);return result;}
    boolean put(PlaybackRecord record){
        if(!enabled()||!record.valid()||(record.generic()&&(!genericEnabled()||!appEnabled(record.source))))return false;record.clamp();
        long removed=prefs.getLong("removed_"+record.key(),0),cleared=prefs.getLong("cleared_at",0);if((record.generic()?MediaRecordPolicy.suppressed(record.startedAt,record.updated,removed,cleared):record.updated<=cleared||removed>=record.updated)||removed>System.currentTimeMillis()-30000)return false;
        ArrayList<PlaybackRecord> records=list();
        for(int i=records.size()-1;i>=0;i--){PlaybackRecord old=records.get(i);if(old.key().equals(record.key())){record.inheritMetadata(old);records.remove(i);}else if(record.apple()&&old.apple())records.remove(i);}
        records.add(record);java.util.Collections.sort(records,new java.util.Comparator<PlaybackRecord>(){public int compare(PlaybackRecord a,PlaybackRecord b){return Long.compare(b.updated,a.updated);}});while(records.size()>8)records.remove(records.size()-1);save(records);return true;
    }
    void remove(String key){ArrayList<PlaybackRecord> records=list();for(int i=records.size()-1;i>=0;i--)if(records.get(i).key().equals(key))records.remove(i);prefs.edit().putLong("removed_"+key,System.currentTimeMillis()).apply();save(records);}
    void clear(){prefs.edit().remove("records").putLong("cleared_at",System.currentTimeMillis()).apply();}
    void removeVideo(String id){ArrayList<PlaybackRecord> records=list();for(int i=records.size()-1;i>=0;i--)if(records.get(i).bili()&&records.get(i).videoId.equals(id))records.remove(i);save(records);}
    boolean patchCover(String key,String cover){if(!enabled()||!genericEnabled())return false;ArrayList<PlaybackRecord> records=list();for(PlaybackRecord r:records)if(r.generic()&&r.key().equals(key)&&appEnabled(r.source)&&!cover.equals(r.cover)){r.cover=cover;save(records);return true;}return false;}
    boolean patchMetadata(String key,String cover,String episode,boolean episodeKnown,long checkedAt){if(!enabled())return false;ArrayList<PlaybackRecord> records=list();for(PlaybackRecord r:records)if(r.bili()&&r.key().equals(key)){boolean changed=(!cover.isEmpty()&&!cover.equals(r.cover))||(episodeKnown&&!episode.equals(r.episode));if(!cover.isEmpty())r.cover=cover;if(episodeKnown)r.episode=episode;r.metadataAt=checkedAt;save(records);return changed;}return false;}
    private void save(ArrayList<PlaybackRecord> records){JSONArray a=new JSONArray();for(PlaybackRecord r:records){try{JSONObject j=new JSONObject();j.put("source",r.source);j.put("source_label",r.sourceLabel);j.put("route_id",r.routeId);j.put("route_channel",r.routeChannel);j.put("content_id",r.contentId);j.put("audio",r.audio);j.put("position_known",r.positionKnown);j.put("started_at",r.startedAt);j.put("album",r.album);j.put("id",r.videoId);j.put("eid",r.episodeId);j.put("title",r.title);j.put("episode",r.episode);j.put("cover",r.cover);j.put("command",r.command);j.put("position",r.position);j.put("duration",r.duration);j.put("updated",r.updated);j.put("metadata_at",r.metadataAt);a.put(j);}catch(Exception ignored){}}prefs.edit().putString("records",a.toString()).apply();}
    private static PlaybackRecord decode(JSONObject j){PlaybackRecord r=new PlaybackRecord();r.source=j.optString("source",PlaybackRecord.PACKAGE);r.sourceLabel=j.optString("source_label");r.routeId=j.optString("route_id");r.routeChannel=j.optString("route_channel","0");r.contentId=j.optString("content_id");r.audio=j.optBoolean("audio");r.positionKnown=j.optBoolean("position_known",true);r.startedAt=j.optLong("started_at");r.album=j.optString("album");r.videoId=j.optString("id");r.episodeId=j.optString("eid");r.title=j.optString("title");r.episode=j.optString("episode");r.cover=j.optString("cover");r.command=j.optString("command");r.position=j.optLong("position");r.duration=j.optLong("duration");r.updated=j.optLong("updated");r.metadataAt=j.optLong("metadata_at");r.clamp();return r;}
}
