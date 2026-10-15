package local.quiettv.launcher;

import java.security.MessageDigest;
import java.util.Locale;

/** Pure policy shared by live sessions, legacy broadcasts and boundary checks. */
final class MediaRecordPolicy {
    static boolean packageName(String value){return value!=null&&value.length()<=160&&value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");}
    static String text(String value,int limit){if(value==null)return "";String clean=value.replaceAll("[\\p{Cntrl}]"," ").trim();return clean.length()<=limit?clean:"";}
    static String identity(String pkg,String id,String title,String subtitle,String album,long duration){
        // Some apps reuse media IDs for a whole series. Keep the supplied episode label.
        String raw=pkg+"\n"+text(id,1024)+"\n"+title+"\n"+subtitle+"\n"+album;
        // Duration is not an identity: many apps populate it after playback begins.
        return digest(raw);
    }
    static String digest(String raw){try{byte[] data=MessageDigest.getInstance("SHA-256").digest(raw.getBytes("UTF-8"));StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static long position(long base,long duration,boolean playing,float speed,long lastUpdate,long now){
        long result=Math.max(0,Math.min(base,259200000L));
        if(base>=0&&playing&&lastUpdate>0&&now>=lastUpdate&&!Float.isNaN(speed)&&!Float.isInfinite(speed)&&speed>0&&speed<=16){double value=result+Math.min(now-lastUpdate,259200000L)*(double)speed;result=(long)Math.min(value,259200000L);}
        return duration>0?Math.min(result,duration):result;
    }
    static boolean suppressed(long started,long updated,long removed,long cleared){return updated<=cleared||removed>=updated||(started>0&&(started<=removed||started<=cleared));}
    /** Sessions may report no usable title; keep the record anchored to the source instead of dropping it. */
    static String displayTitle(String title,String sourceLabel,String pkg){
        String name=sourceLabel==null||sourceLabel.isEmpty()?pkg:sourceLabel;
        return title==null||title.isEmpty()||title.equals(name)?name:title;
    }
    /** Placeholder records cannot prove which item a live session holds, so they never claim live resume. */
    static boolean identifiable(PlaybackRecord record){
        if(record==null)return false;
        if(!record.contentId.isEmpty())return true;
        String name=record.sourceLabel==null||record.sourceLabel.isEmpty()?record.source:record.sourceLabel;
        return !record.title.isEmpty()&&!record.title.equals(name);
    }
    static boolean sameItem(PlaybackRecord a,PlaybackRecord b){if(!a.generic()||!b.generic()||a.configured()||b.configured()||!a.source.equals(b.source)||!a.title.equals(b.title))return false;if(!a.contentId.isEmpty()&&!b.contentId.isEmpty())return a.contentId.equals(b.contentId)&&(a.episode.equals(b.episode)||a.episode.isEmpty()||b.episode.isEmpty())&&(a.album.equals(b.album)||a.album.isEmpty()||b.album.isEmpty());return a.episode.equals(b.episode)&&a.album.equals(b.album);}
    static void retainIdentity(PlaybackRecord record,PlaybackRecord old){if(!sameItem(record,old))return;record.videoId=old.videoId;if(record.contentId.isEmpty())record.contentId=old.contentId;if(record.episode.isEmpty())record.episode=old.episode;if(record.album.isEmpty())record.album=old.album;}
    private MediaRecordPolicy(){}
}
