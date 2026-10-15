package local.quiettv.launcher;

/** Validated local video or music record. Independent of Android for boundary tests. */
final class PlaybackRecord {
    static final String PACKAGE="com.xiaodianshi.tv.yst";
    static final String MUSIC_PACKAGE="com.applemusicktv";
    String source=PACKAGE,album="",sourceLabel="",contentId="",routeId="",routeChannel="0";
    boolean audio;
    String videoId="",episodeId="",title="",episode="",cover="",command="";
    String ruleId="";
    java.util.Map<String,String> ruleData=new java.util.LinkedHashMap<>();
    long position,duration,updated,metadataAt,startedAt;
    boolean positionKnown=true;
    /** Only records received over the verified TCL protocol use the TCL route. */
    boolean bili(){return PACKAGE.equals(source)&&command.matches("tcl/(ugc|pgc|pugc)");}
    boolean apple(){return MUSIC_PACKAGE.equals(source)&&!command.equals("media/session");}
    boolean generic(){return !bili()&&!apple();}
    boolean music(){return apple()||(generic()&&audio);}
    String service(){return apple()?"Apple Music":bili()?"B 站":sourceLabel.isEmpty()?source:sourceLabel;}
    boolean configured(){return command.equals("rule/broadcast");}
    String key(){return apple()?"applemusic|"+videoId:configured()?"rule|"+source+"|"+videoId:generic()?"generic|"+source+"|"+videoId:command+"|"+videoId+"|"+episodeId;}
    boolean valid(){return ruleFieldsValid()&&!title.isEmpty()&&title.length()<=240&&(apple()?musicId(videoId):bili()?numeric(videoId)&&(episodeId.isEmpty()||numeric(episodeId)):MediaRecordPolicy.packageName(source)&&sourceLabel.length()<=160&&episode.length()<=160&&album.length()<=160&&videoId.matches("[a-f0-9]{64}")&&(contentId.isEmpty()||contentId.matches("[a-f0-9]{64}"))&&(command.equals("media/session")||(configured()&&!ruleId.isEmpty()&&ruleData.containsKey("id"))||(command.equals("media/qiyi")&&QiyiRoute.valid(this))));}
    private boolean ruleFieldsValid(){if(!ruleId.isEmpty()&&!ruleId.matches("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+"))return false;if(ruleId.length()>80||ruleData.size()>4)return false;for(java.util.Map.Entry<String,String> item:ruleData.entrySet())if(!java.util.Arrays.asList("id","episodeId","variant","channel").contains(item.getKey())||!PlaybackRule.token(item.getValue()))return false;return true;}
    boolean canResume(){return valid()&&bili()&&!episodeId.isEmpty();}
    String actionLabel(){return apple()?"打开应用":canResume()?"继续播放":generic()&&!command.equals("media/qiyi")?"打开应用":"打开内容";}
    void inheritMetadata(PlaybackRecord old){if(!key().equals(old.key()))return;if(bili()&&old.metadataAt>0&&old.metadataAt>=metadataAt){episode=old.episode;if(!old.cover.isEmpty())cover=old.cover;}else{if(episode.isEmpty())episode=old.episode;if(cover.isEmpty())cover=old.cover;}if(generic()){if(!positionKnown&&old.positionKnown){position=old.position;positionKnown=true;}if(duration==0)duration=old.duration;}metadataAt=Math.max(metadataAt,old.metadataAt);}
    void clamp(){position=Math.max(0,Math.min(position,72*60*60*1000L));duration=Math.max(0,Math.min(duration,72*60*60*1000L));if(duration>0)position=Math.min(position,duration);}
    static boolean numeric(String value){return value!=null&&value.matches("[0-9]{1,18}")&&!value.matches("0+");}
    static boolean musicId(String value){return value!=null&&(value.matches("[1-9][0-9]{0,17}")||value.matches("i\\.[A-Za-z0-9]{1,80}"));}
    static String time(long milliseconds){long seconds=Math.max(0,milliseconds)/1000;return seconds>=3600?String.format(java.util.Locale.US,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(java.util.Locale.US,"%d:%02d",seconds/60,seconds%60);}
    String progress(){if(!positionKnown)return "进度未提供";if(duration>0&&position>=duration-10000&&duration>30000)return music()?"已听完":generic()?"已播完":"已看完";return position>0?(music()?"听到 ":generic()?"播放至 ":"看到 ")+time(position)+(duration>0?" / "+time(duration):""):music()||generic()?"最近播放":"最近观看";}
    String details(){return (episode.isEmpty()||(!music()&&episode.equals(title))?"":episode+" · ")+progress();}
}
