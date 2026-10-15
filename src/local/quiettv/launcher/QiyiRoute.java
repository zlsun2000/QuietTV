package local.quiettv.launcher;

/** Validation and identity compatibility for records written before rule support. */
final class QiyiRoute {
    static final String PACKAGE="com.tcl.qiyiguo",ACTION=PACKAGE+".action.ACTION_DETAIL";
    static boolean valid(PlaybackRecord r){return PACKAGE.equals(r.source)&&PlaybackRecord.numeric(r.routeId)&&PlaybackRecord.numeric(r.episodeId)&&r.routeChannel.matches("[0-9]{1,4}");}
    static String episode(String label){String value=MediaRecordPolicy.text(label,160);if(value.matches("[1-9][0-9]{0,3}"))return "第 "+value+" 集";return value.equals("0")?"":value;}
    static long duration(long seconds){return seconds>0&&seconds<=259200?seconds*1000:0;}
    private QiyiRoute(){}
}
