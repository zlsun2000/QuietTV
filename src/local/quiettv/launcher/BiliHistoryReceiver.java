package local.quiettv.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;

/** Receives the TCL history protocol already emitted by this TV's Bilibili build. */
public final class BiliHistoryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null)return;
        UserManager users=(UserManager)context.getSystemService(Context.USER_SERVICE);
        if(android.os.Build.VERSION.SDK_INT>=24&&users!=null&&!users.isUserUnlocked())return;
        try {
            if(!context.getPackageManager().getApplicationInfo(PlaybackRecord.PACKAGE,0).enabled)return;
            Bundle extras=intent.getExtras();if(extras==null)return;
            // Legacy broadcasts have no authenticated sender UID on Android 9. Treat every
            // extra as untrusted: fixed target, numeric IDs, recognized commands, no intents.
            String source=string(extras,"srcApp",160),pkg=string(extras,"packageName",160);
            if(!PlaybackRecord.PACKAGE.equals(source)&&!PlaybackRecord.PACKAGE.equals(pkg)&&!"com.xiaodianshi.tv.yst.external".equals(source))return;
            PlaybackRecord r=new PlaybackRecord();r.videoId=string(extras,"videoId",18);r.episodeId=string(extras,"episodeId",18);r.title=string(extras,"videoName",240);r.episode=string(extras,"episodeName",160);r.command=string(extras,"cmdInfo",40);r.cover=Covers.safeUrl(string(extras,"videoImgUrl",1500));r.position=number(extras,"currentPosition");r.duration=number(extras,"duration");r.updated=System.currentTimeMillis();r.clamp();
            HistoryStore store=new HistoryStore(context);if(!store.enabled())return;
            if("com.tv.history.del.tolauncher".equals(intent.getAction())){if(!PlaybackRecord.numeric(r.videoId))return;store.removeVideo(r.videoId);GenericMediaHistory.changed(context);return;}
            if(!r.valid())return;
            if("com.tv.history.add".equals(intent.getAction()))store.put(r);
            else return;
            GenericMediaHistory.changed(context);
        }catch(Exception ignored){}
    }
    private static String string(Bundle b,String key,int max){Object value=b.get(key);if(!(value instanceof String)&&!(value instanceof Number))return "";String text=String.valueOf(value).trim();return text.length()<=max?text:"";}
    private static long number(Bundle b,String key){Object v=b.get(key);if(v instanceof Number)return ((Number)v).longValue();try{return Long.parseLong(String.valueOf(v));}catch(Exception e){return 0;}}
}
