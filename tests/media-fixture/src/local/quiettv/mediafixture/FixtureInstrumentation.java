package local.quiettv.mediafixture;

import android.app.Instrumentation;
import android.graphics.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;

/** Publishes paused metadata. No activity, service notification, audio or controls. */
public final class FixtureInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        MediaSession session=new MediaSession(getTargetContext(),"QuietTVMetadataFixture");session.setFlags(0);
        Bundle result=new Bundle();
        try{
            publish(session,1,123000);session.setActive(true);Thread.sleep(4000);
            publish(session,2,150000);Thread.sleep(4000);
            publish(session,2,-1);Thread.sleep(2000);
            result.putString("result","Published two paused episodes and a final unknown-position update; no audio/video or UI started.");
        }catch(Exception e){result.putString("error",e.getClass().getSimpleName());}finally{session.release();}
        finish(0,result);
    }
    private void publish(MediaSession session,int episode,long position){
        Bitmap bitmap=Bitmap.createBitmap(240,135,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(episode==1?0xff365569:0xff4b365b);Paint paint=new Paint(3);paint.setColor(Color.WHITE);paint.setTextSize(24);canvas.drawText("Fixture "+episode,24,76,paint);
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"shared-series-id").putString(MediaMetadata.METADATA_KEY_TITLE,"QuietTV metadata check").putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,"第 "+episode+" 集").putLong(MediaMetadata.METADATA_KEY_DURATION,600000).putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON,bitmap).build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(0).setState(PlaybackState.STATE_PAUSED,position,0,SystemClock.elapsedRealtime()).build());
    }
}
