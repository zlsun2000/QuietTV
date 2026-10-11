package local.quiettv.launcher;

import android.content.*;
import java.util.LinkedHashMap;
import org.json.JSONObject;

/** Installed TCL Qiyi's own public detail contract. Never forwards arbitrary cmdInfo. */
final class QiyiRoute {
    static final String PACKAGE="com.tcl.qiyiguo",ACTION=PACKAGE+".action.ACTION_DETAIL";
    static boolean valid(PlaybackRecord r){return PACKAGE.equals(r.source)&&PlaybackRecord.numeric(r.routeId)&&PlaybackRecord.numeric(r.episodeId)&&r.routeChannel.matches("[0-9]{1,4}");}
    static LinkedHashMap<String,Object> values(PlaybackRecord r){if(!valid(r))throw new IllegalArgumentException("Invalid Qiyi route");LinkedHashMap<String,Object> values=new LinkedHashMap<>();values.put("playType","history");values.put("videoId",r.routeId);values.put("episodeId",r.episodeId);values.put("history",-1);values.put("customer","tcl");values.put("chnId",Integer.parseInt(r.routeChannel));return values;}
    static Intent intent(PlaybackRecord r){return new Intent(ACTION).setPackage(PACKAGE).putExtra("playInfo",new JSONObject(values(r)).toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);}
    static String episode(String label){String value=MediaRecordPolicy.text(label,160);if(value.matches("[1-9][0-9]{0,3}"))return "第 "+value+" 集";return value.equals("0")?"":value;}
    static long duration(long seconds){return seconds>0&&seconds<=259200?seconds*1000:0;}
    private QiyiRoute(){}
}
