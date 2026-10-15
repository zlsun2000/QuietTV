package local.quiettv.checks;

import android.content.*;
import android.net.Uri;
import android.os.Build;

/** Disposable-emulator publisher. No media, network or owner-device operation. */
public final class TvProgramFixture extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent request){
        if(!(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator")))return;
        Uri table=Uri.parse("content://android.media.tv/watch_next_program");
        try{
            c.getContentResolver().delete(table,null,null);
            if(!"local.quiettv.checks.SEED_TV_PROGRAMS".equals(request.getAction()))return;
            for(int i=0;i<3;i++){
                ContentValues row=new ContentValues();row.put("title","QuietTV synthetic fixture");row.put("type",0);row.put("watch_next_type",i==2?1:0);row.put("searchable",1);row.put("internal_provider_id",i==2?"fixture.next":i==0?"80000001":"80000002");row.put("last_playback_position_millis",83000);row.put("duration_millis",300000);
                Intent route=new Intent("local.quiettv.checks.RESUME").setComponent(new ComponentName(i==1?"org.example.foreign":c.getPackageName(),i==1?"org.example.foreign.Player":"local.quiettv.checks.RuleTargetActivity"));
                row.put("intent_uri",route.toUri(Intent.URI_INTENT_SCHEME));c.getContentResolver().insert(table,row);
            }
            setResultCode(-1);setResultData("synthetic_programs_seeded");
        }catch(Exception ignored){setResultCode(0);setResultData("synthetic_programs_unavailable");}
    }
}
