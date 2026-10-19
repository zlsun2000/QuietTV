package local.quiettv.launcher;

import android.app.PendingIntent;
import android.content.*;
import android.content.pm.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;

/** Reads standard metadata; only controls the exact item still held by a live session. */
final class GenericMediaHistory {
    static ComponentName listener(Context c){return new ComponentName(c,MediaHistoryService.class);}
    static boolean permission(Context c){String values=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");if(values!=null)for(String v:values.split(":"))if(listener(c).equals(ComponentName.unflattenFromString(v)))return true;return false;}
    static String label(Context c,String pkg){try{return MediaRecordPolicy.text(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString(),160);}catch(Exception ignored){return pkg;}}
    static Intent launch(Context c,String pkg){PackageManager pm=c.getPackageManager();Intent intent=pm.getLeanbackLaunchIntentForPackage(pkg);if(intent==null)intent=pm.getLaunchIntentForPackage(pkg);return intent==null?null:intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);}
    static boolean eligible(Context c,String pkg){if(!MediaRecordPolicy.packageName(pkg)||c.getPackageName().equals(pkg))return false;try{return c.getPackageManager().getApplicationInfo(pkg,0).enabled&&launch(c,pkg)!=null;}catch(Exception ignored){return false;}}
    static boolean lowRamListenerUnsupported(Context c){try{return Build.VERSION.SDK_INT<=29&&((android.app.ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).isLowRamDevice();}catch(Exception ignored){return false;}}
    static boolean serviceConnected(){return MediaHistoryService.active;}
    static DeviceCapability capability(Context c){if(lowRamListenerUnsupported(c))return DeviceCapability.unsupported("media_sessions","此低内存系统不支持媒体识别");if(!permission(c))return DeviceCapability.optional("media_sessions","未授权，可选");if(serviceConnected())return DeviceCapability.available("media_sessions","已授权 · 正在运行");return DeviceCapability.waiting("media_sessions","已授权 · 等待系统连接");}
    static String availability(Context c){return capability(c).reason;}
    static List<MediaController> sessions(Context c){if(!permission(c)||lowRamListenerUnsupported(c))return Collections.emptyList();try{MediaSessionManager manager=(MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);return manager==null?Collections.<MediaController>emptyList():manager.getActiveSessions(listener(c));}catch(Exception ignored){return Collections.emptyList();}}
    // A snapshot for troubleshooting, not a permanent list of supported applications.
    static int observationRank(Integer state){return state==null?1:state==PlaybackState.STATE_PLAYING?5:state==PlaybackState.STATE_PAUSED?4:state==PlaybackState.STATE_STOPPED?3:2;}
    static Map<String,Integer> sessionObservations(Context c){
        HashMap<String,Integer> result=new HashMap<>();for(MediaController controller:sessions(c))try{
            String pkg=controller.getPackageName();if(!eligible(c,pkg))continue;
            PlaybackState state=controller.getPlaybackState();int rank=observationRank(state==null?null:state.getState());
            Integer old=result.get(pkg);if(old==null||rank>old)result.put(pkg,rank);
        }catch(Exception ignored){}return result;
    }
    static String observation(Context c,String pkg,Map<String,Integer> observed,DeviceCapability capability){
        if(!capability.isAvailable())return capability.reason;
        Integer rank=observed.get(pkg);return c.getString(rank==null?R.string.media_session_absent:rank==1?R.string.media_session_no_state:rank==5?R.string.media_session_playing:rank==4?R.string.media_session_paused:rank==3?R.string.media_session_stopped:R.string.media_session_idle);
    }
    static PlaybackRecord record(Context c,MediaController controller){
        String pkg=controller.getPackageName();if(!eligible(c,pkg))return null;MediaMetadata m=controller.getMetadata();PlaybackState state=controller.getPlaybackState();if(state==null)return null;if(m==null)m=new MediaMetadata.Builder().build();
        PlaybackRecord r=new PlaybackRecord();r.source=pkg;r.sourceLabel=label(c,pkg);r.command="media/session";
        r.title=MediaRecordPolicy.displayTitle(first(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),m.getString(MediaMetadata.METADATA_KEY_TITLE),240),r.sourceLabel,pkg);
        String artist=MediaRecordPolicy.text(m.getString(MediaMetadata.METADATA_KEY_ARTIST),160);r.album=MediaRecordPolicy.text(m.getString(MediaMetadata.METADATA_KEY_ALBUM),160);
        MediaController.PlaybackInfo info=controller.getPlaybackInfo();r.audio=info!=null&&info.getAudioAttributes()!=null&&info.getAudioAttributes().getContentType()==AudioAttributes.CONTENT_TYPE_MUSIC;
        r.episode=first(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),r.audio?artist:"",160);if(r.episode.equals(r.title)||r.episode.equals(r.sourceLabel))r.episode="";
        if(QiyiRoute.PACKAGE.equals(pkg))r.episode=QiyiRoute.episode(r.episode);
        r.duration=Math.max(0,Math.min(m.getLong(MediaMetadata.METADATA_KEY_DURATION),259200000L));r.positionKnown=state.getPosition()>=0;
        r.position=MediaRecordPolicy.position(state.getPosition(),r.duration,state.getState()==PlaybackState.STATE_PLAYING,state.getPlaybackSpeed(),state.getLastPositionUpdateTime(),SystemClock.elapsedRealtime());
        r.videoId=MediaRecordPolicy.identity(pkg,m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),r.title,r.episode,r.album,r.duration);
        String rawId=MediaRecordPolicy.text(m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),1024);if(!rawId.isEmpty())r.contentId=MediaRecordPolicy.digest(rawId);
        PlaybackRules.decorateSession(c,r,m);
        PlaybackRecord configured=null;for(PlaybackRecord old:new HistoryStore(c).list())if(MediaRecordPolicy.sameConfiguredSession(r,old)&&(configured==null||MediaRecordPolicy.metadataPlaceholder(configured)&&!MediaRecordPolicy.metadataPlaceholder(old)))configured=old;
        if(configured!=null)MediaRecordPolicy.retainConfiguredSession(r,configured);
        for(PlaybackRecord old:new HistoryStore(c).list())if(MediaRecordPolicy.sameItem(r,old)){MediaRecordPolicy.retainIdentity(r,old);if(old.command.equals("media/qiyi")&&!r.episode.isEmpty()){r.command=old.command;r.routeId=old.routeId;r.episodeId=old.episodeId;r.routeChannel=old.routeChannel;}if(r.ruleId.isEmpty()&&!old.ruleId.isEmpty()){r.ruleId=old.ruleId;r.ruleData=new LinkedHashMap<>(old.ruleData);}break;}
        r.updated=System.currentTimeMillis();r.clamp();return r.valid()?r:null;
    }
    private static String first(String a,String b,int max){String t=MediaRecordPolicy.text(a,max);return t.isEmpty()?MediaRecordPolicy.text(b,max):t;}
    static MediaController current(Context c,PlaybackRecord target){if(!target.generic()||!target.valid()||!MediaRecordPolicy.identifiable(target))return null;for(MediaController controller:sessions(c))try{if(!target.source.equals(controller.getPackageName()))continue;PlaybackRecord actual=record(c,controller);PlaybackState state=controller.getPlaybackState();if(actual!=null&&actual.key().equals(target.key())&&state!=null&&(state.getState()==PlaybackState.STATE_PLAYING||(state.getState()==PlaybackState.STATE_PAUSED&&(state.getActions()&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0)))return controller;}catch(Exception ignored){}return null;}
    static String actionFor(PlaybackRecord target,String activeKey){return target.generic()&&MediaRecordPolicy.identifiable(target)&&target.key().equals(activeKey)?"恢复当前播放":target.actionLabel();}
    static String action(Context c,PlaybackRecord r){MediaController controller=current(c,r);return controller!=null?"恢复当前播放":PlaybackRules.get(c).action(r);}
    static Map<String,String> actions(Context c,List<PlaybackRecord> records){HashMap<String,String> result=new HashMap<>();List<MediaController> active=sessions(c);for(PlaybackRecord target:records){String activeKey=null;if(target.generic()&&MediaRecordPolicy.identifiable(target))for(MediaController controller:active)try{if(!target.source.equals(controller.getPackageName()))continue;PlaybackRecord actual=record(c,controller);PlaybackState state=controller.getPlaybackState();if(actual!=null&&actual.key().equals(target.key())&&state!=null&&(state.getState()==PlaybackState.STATE_PLAYING||(state.getState()==PlaybackState.STATE_PAUSED&&(state.getActions()&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0))){activeKey=actual.key();break;}}catch(Exception ignored){}result.put(target.key(),activeKey!=null?"恢复当前播放":PlaybackRules.get(c).action(target));}return result;}
    static void open(Context c,PlaybackRecord r){
        MediaController controller=current(c,r);boolean opened=false;
        if(controller!=null)try{PendingIntent activity=controller.getSessionActivity();if(activity!=null&&r.source.equals(activity.getCreatorPackage())){activity.send();opened=true;}}catch(Exception ignored){}
        if(!opened&&controller==null)opened=PlaybackRules.get(c).openRoute(r);
        if(!opened)try{Intent intent=launch(c,r.source);if(intent!=null){c.startActivity(intent);opened=true;}}catch(Exception ignored){}
        if(!opened){android.widget.Toast.makeText(c,"应用尚未安装或已停用",android.widget.Toast.LENGTH_LONG).show();return;}
        if(controller!=null)new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){MediaController live=current(c,r);if(live!=null&&live.getPlaybackState().getState()==PlaybackState.STATE_PAUSED)try{live.getTransportControls().play();}catch(Exception ignored){}}},350);
    }
    static void changed(Context c){c.sendBroadcast(new Intent(HistoryStore.CHANGED).setPackage(c.getPackageName()),"local.quiettv.launcher.permission.INTERNAL");}
    static void rebind(Context c){if(Build.VERSION.SDK_INT>=24&&permission(c)&&!lowRamListenerUnsupported(c))try{android.service.notification.NotificationListenerService.requestRebind(listener(c));}catch(Exception ignored){}}
    private GenericMediaHistory(){}
}
