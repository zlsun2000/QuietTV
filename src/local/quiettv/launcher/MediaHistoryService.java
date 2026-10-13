package local.quiettv.launcher;

import android.content.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;
import android.service.notification.NotificationListenerService;
import java.util.*;

/** The OS binds this listener. Notification bodies are never read or retained. */
public final class MediaHistoryService extends NotificationListenerService {
    static volatile boolean active;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<MediaSession.Token,Track> tracks=new LinkedHashMap<>();
    private MediaSessionManager manager;
    private MediaArtwork artwork;
    private boolean connected;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions=new MediaSessionManager.OnActiveSessionsChangedListener(){public void onActiveSessionsChanged(List<MediaController> list){final List<MediaController> copy=list==null?null:new ArrayList<>(list);handler.post(()->replace(copy));}};
    private final Runnable poll=new Runnable(){public void run(){if(!connected)return;for(Track t:new ArrayList<>(tracks.values()))t.capture(false);handler.postDelayed(this,15000);}};
    @Override public void onListenerConnected(){
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(this::connectListener);return;}connectListener();
    }
    private void connectListener(){
        if(connected)return;
        if(GenericMediaHistory.lowRamListenerUnsupported(this))return;
        connected=true;active=true;artwork=new MediaArtwork(this);manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        try{if(manager==null)throw new IllegalStateException("Media session manager unavailable");manager.addOnActiveSessionsChangedListener(sessions,new ComponentName(this,MediaHistoryService.class),handler);replace(manager.getActiveSessions(new ComponentName(this,MediaHistoryService.class)));handler.removeCallbacks(poll);handler.postDelayed(poll,15000);}catch(Exception e){disconnect();}
    }
    private void replace(List<MediaController> list){
        if(!connected)return;HashSet<MediaSession.Token> retained=new HashSet<>();
        if(list!=null)for(MediaController c:list){if(retained.size()>=16)break;if(!GenericMediaHistory.eligible(this,c.getPackageName()))continue;MediaSession.Token token=c.getSessionToken();retained.add(token);if(!tracks.containsKey(token)){Track t=new Track(c);tracks.put(token,t);c.registerCallback(t.callback,handler);t.capture(true);}}
        boolean removed=false;for(MediaSession.Token token:new ArrayList<>(tracks.keySet()))if(!retained.contains(token)){Track t=tracks.remove(token);t.capture(true);t.release();removed=true;}if(removed)GenericMediaHistory.changed(this);
    }
    private void disconnect(){connected=false;active=false;handler.removeCallbacksAndMessages(null);for(Track t:tracks.values())t.release();tracks.clear();if(manager!=null)try{manager.removeOnActiveSessionsChangedListener(sessions);}catch(Exception ignored){}if(artwork!=null)artwork.dispose();artwork=null;}
    @Override public void onListenerDisconnected(){if(Looper.myLooper()!=Looper.getMainLooper())handler.post(this::disconnect);else disconnect();}
    @Override public void onDestroy(){disconnect();super.onDestroy();}
    // Deliberately do not implement onNotificationPosted/onNotificationRemoved.
    private final class Track {
        final MediaController controller;
        String lastKey="",lastArt="";
        long started,lastWrite,lastPosition=-1;
        int previousState=PlaybackState.STATE_NONE;
        PlaybackRecord currentItem;
        final Runnable deferred=new Runnable(){public void run(){capture(true);}};
        final MediaController.Callback callback=new MediaController.Callback(){
            @Override public void onMetadataChanged(MediaMetadata metadata){lastArt="";schedule();}
            @Override public void onPlaybackStateChanged(PlaybackState state){schedule();}
            @Override public void onSessionDestroyed(){handler.post(()->{capture(true);release();tracks.remove(controller.getSessionToken());GenericMediaHistory.changed(MediaHistoryService.this);});}
        };
        Track(MediaController c){controller=c;}
        void schedule(){handler.removeCallbacks(deferred);handler.postDelayed(deferred,300);}
        void release(){handler.removeCallbacks(deferred);try{controller.unregisterCallback(callback);}catch(Exception ignored){}}
        void capture(boolean event){
            try{
                HistoryStore store=new HistoryStore(MediaHistoryService.this);if(!store.enabled()||!store.genericEnabled()||!store.appEnabled(controller.getPackageName()))return;
                PlaybackState state=controller.getPlaybackState();if(state==null)return;int mode=state.getState();
                if(mode!=PlaybackState.STATE_PLAYING&&mode!=PlaybackState.STATE_PAUSED&&mode!=PlaybackState.STATE_STOPPED){previousState=mode;return;}
                PlaybackRecord record=GenericMediaHistory.record(MediaHistoryService.this,controller);if(record==null){previousState=mode;return;}
                if(currentItem!=null)MediaRecordPolicy.retainIdentity(record,currentItem);currentItem=record;
                boolean changed=!lastKey.equals(record.key()),playing=mode==PlaybackState.STATE_PLAYING;long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();
                // Do not import idle library selections or paused sessions with unknown history.
                if(changed&&!playing&&(record.position<=0||state.getLastPositionUpdateTime()<=0||elapsed-state.getLastPositionUpdateTime()>86400000L))return;
                if(changed||(playing&&(previousState==PlaybackState.STATE_STOPPED||previousState==PlaybackState.STATE_NONE))){started=playing?now:now-Math.max(0,elapsed-state.getLastPositionUpdateTime());lastKey=record.key();lastWrite=0;lastPosition=-1;}
                record.startedAt=started;record.updated=playing?now:Math.max(started,now-Math.max(0,elapsed-state.getLastPositionUpdateTime()));
                if(!playing&&!event&&!changed){previousState=mode;return;}
                if(!changed&&record.position==lastPosition&&mode==previousState&&elapsed-lastWrite<60000){if(artwork!=null&&!lastArt.equals(record.key())&&artwork.capture(record,controller.getMetadata()))lastArt=record.key();return;}
                previousState=mode;lastPosition=record.position;lastWrite=elapsed;
                if(store.put(record)){
                    GenericMediaHistory.changed(MediaHistoryService.this);
                    if(artwork!=null&&!lastArt.equals(record.key())&&artwork.capture(record,controller.getMetadata()))lastArt=record.key();
                }
            }catch(Exception e){android.util.Log.w("QuietTVMedia","Session metadata unavailable: "+e.getClass().getSimpleName());}
        }
    }
}
