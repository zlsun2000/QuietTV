package local.quiettv.launcher;

import android.content.ComponentName;
import android.content.Intent;
import java.util.LinkedHashMap;
import org.json.JSONObject;

/** Bilibili's generic external route supports forced progress; the TCL wrapper does not. */
final class BiliResume {
    static LinkedHashMap<String,String> values(PlaybackRecord record){
        if(!record.valid()||!record.bili())throw new IllegalArgumentException("Invalid Bilibili record");
        LinkedHashMap<String,String> values=new LinkedHashMap<>();
        boolean pgc=!"tcl/ugc".equals(record.command);
        values.put("isBangumi",pgc?"1":"0");
        values.put(pgc?"seasonId":"avId",record.videoId);
        if(!record.episodeId.isEmpty())values.put(pgc?"epId":"cId",record.episodeId);
        if("tcl/pugc".equals(record.command))values.put("isClass","1");
        // History broadcasts use milliseconds; the generic external value uses seconds.
        values.put("progress",String.valueOf(Math.max(0,Math.min(record.position,259200000L))/1000));
        values.put("forcePlayProgress","1");
        values.put("fullScreenPlay","1");
        values.put("stay","0");
        values.put("home","0");
        return values;
    }
    static Intent intent(PlaybackRecord record){
        return new Intent("com.xiaodianshi.tv.yst.external")
            .setComponent(new ComponentName(PlaybackRecord.PACKAGE,"com.xiaodianshi.tv.yst.ui.transition.TransitionActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("type","3").putExtra("value",new JSONObject(values(record)).toString())
            .putExtra("from","quiettv").putExtra("out","1");
    }
    private BiliResume(){}
}
