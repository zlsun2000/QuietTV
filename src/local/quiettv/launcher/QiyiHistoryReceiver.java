package local.quiettv.launcher;

import android.content.*;
import android.os.*;
import org.json.*;

/** Uses the existing TCL history broadcasts of the installed Qiyi channel build. */
public final class QiyiHistoryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(LegacyHistoryListener.skipManifest(intent))return;receive(context,intent);
    }
    static void receive(Context context,Intent intent){
        if(intent==null)return;UserManager user=(UserManager)context.getSystemService(Context.USER_SERVICE);if(Build.VERSION.SDK_INT>=24&&user!=null&&!user.isUserUnlocked())return;
        try{
            Bundle extras=intent.getExtras();if(extras==null||!QiyiRoute.PACKAGE.equals(text(extras,"packageName",160)))return;
            HistoryStore store=new HistoryStore(context);if(!store.enabled()||!store.genericEnabled()||!store.appEnabled(QiyiRoute.PACKAGE)||!context.getPackageManager().getApplicationInfo(QiyiRoute.PACKAGE,0).enabled)return;
            String source=text(extras,"srcApp",160);if(!source.isEmpty()&&!source.equals(QiyiRoute.PACKAGE)&&!source.equals(QiyiRoute.ACTION))return;
            String video=text(extras,"videoId",18);
            if("com.tv.history.del.tolauncher".equals(intent.getAction())){if(!video.isEmpty()&&!PlaybackRecord.numeric(video))return;for(PlaybackRecord r:store.list())if(QiyiRoute.PACKAGE.equals(r.source)&&(video.isEmpty()||video.equals(r.routeId)))store.remove(r.key());GenericMediaHistory.changed(context);return;}
            if(!"com.tv.history.add".equals(intent.getAction())||!PlaybackRecord.numeric(video))return;
            String raw=text(extras,"cmdInfo",2048);JSONObject info=new JSONObject(raw);String episode=info.optString("episodeId"),declared=info.optString("videoId");if(!video.equals(declared)||!PlaybackRecord.numeric(episode))return;
            PlaybackRecord r=new PlaybackRecord();r.source=QiyiRoute.PACKAGE;r.sourceLabel=GenericMediaHistory.label(context,r.source);r.command="media/qiyi";r.routeId=video;r.episodeId=episode;r.routeChannel=info.optString("chnId","0");
            r.title=text(extras,"videoName",240);r.episode=QiyiRoute.episode(text(extras,"episodeName",160));r.cover=MediaArtwork.safeRemote(text(extras,"videoImgUrl",1500));r.position=number(extras,"currentPosition");r.duration=QiyiRoute.duration(number(extras,"duration"));r.positionKnown=extras.containsKey("currentPosition")&&r.position>=0;r.clamp();
            r.videoId=MediaRecordPolicy.identity(r.source,video+"|"+episode,r.title,r.episode,"",r.duration);if(!r.valid())return;
            for(PlaybackRecord old:store.list())if(old.command.equals("media/session")&&!r.episode.isEmpty()&&MediaRecordPolicy.sameItem(r,old)){MediaRecordPolicy.retainIdentity(r,old);break;}
            r.updated=System.currentTimeMillis();r.startedAt=started(context,r);if(store.put(r))GenericMediaHistory.changed(context);
        }catch(Exception e){android.util.Log.w("QuietTVMedia","Qiyi history unavailable: "+e.getClass().getSimpleName());}
    }
    private static long started(Context c,PlaybackRecord r) throws Exception {
        android.content.SharedPreferences prefs=c.getSharedPreferences("playback_history",Context.MODE_PRIVATE);JSONArray previous=new JSONArray(prefs.getString("vendor_sessions","[]")),next=new JSONArray();long start=r.updated;
        for(int i=0;i<Math.min(16,previous.length());i++){JSONObject item=previous.optJSONObject(i);if(item==null)continue;if(r.key().equals(item.optString("key"))){if(r.updated-item.optLong("seen")<3600000&&r.position>=item.optLong("position")-30000)start=item.optLong("started",r.updated);}else if(r.updated-item.optLong("seen")<259200000L)next.put(item);}
        JSONObject item=new JSONObject();item.put("key",r.key());item.put("position",r.position);item.put("started",start);item.put("seen",r.updated);JSONArray bounded=new JSONArray();bounded.put(item);for(int i=0;i<Math.min(15,next.length());i++)bounded.put(next.getJSONObject(i));prefs.edit().putString("vendor_sessions",bounded.toString()).apply();return start;
    }
    private static String text(Bundle b,String key,int max){Object value=b.get(key);return value instanceof String||value instanceof Number?MediaRecordPolicy.text(String.valueOf(value),max):"";}
    private static long number(Bundle b,String key){Object value=b.get(key);if(value instanceof Number)return ((Number)value).longValue();try{return Long.parseLong(String.valueOf(value));}catch(Exception ignored){return key.equals("currentPosition")?-1:0;}}
}
