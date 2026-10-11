package local.quiettv.launcher;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pure parsing retained for compatible local metadata. No catalog requests in the public build. */
final class BiliMetadata {
    BiliMetadata(Context context){}
    void refresh(){}
    static final class Info {String cover="",episode="";boolean episodeKnown;}
    static Info parse(PlaybackRecord r,JSONObject response){
        if(response==null||response.optInt("code",-1)!=0)return null;Info info=new Info();
        if(r.command.equals("tcl/pgc")){
            JSONObject season=response.optJSONObject("result");if(season==null||!r.videoId.equals(season.optString("season_id")))return null;
            info.cover=Covers.safeUrl(season.optString("cover"));JSONObject episode=find(season.optJSONArray("episodes"),r.episodeId,"id");
            JSONArray sections=season.optJSONArray("section");if(episode==null&&sections!=null)for(int n=0;n<Math.min(sections.length(),100);n++){JSONObject section=sections.optJSONObject(n);if(section!=null&&(episode=find(section.optJSONArray("episodes"),r.episodeId,"id"))!=null)break;}
            if(episode!=null){info.episode=episodeLabel(episode.optString("title"),episode.optString("long_title"));info.episodeKnown=!info.episode.isEmpty();String art=Covers.safeUrl(episode.optString("cover"));if(!art.isEmpty())info.cover=art;}
        }else if(r.command.equals("tcl/ugc")){
            JSONObject video=response.optJSONObject("data");if(video==null||!r.videoId.equals(video.optString("aid")))return null;info.cover=Covers.safeUrl(video.optString("pic"));JSONArray pages=video.optJSONArray("pages");JSONObject part=find(pages,r.episodeId,"cid");
            if(part!=null){info.episodeKnown=true;if(pages.length()>1&&part.optInt("page")>0){String label="P"+part.optInt("page"),title=bounded(part.optString("part"),120);info.episode=title.isEmpty()?label:label+" · "+title;}}
        }else return null;
        return !info.cover.isEmpty()||info.episodeKnown?info:null;
    }
    private static JSONObject find(JSONArray a,String id,String key){if(a==null||!PlaybackRecord.numeric(id))return null;for(int n=0;n<Math.min(a.length(),2000);n++){JSONObject item=a.optJSONObject(n);if(item!=null&&id.equals(item.optString(key)))return item;}return null;}
    static String episodeLabel(String title,String longTitle){title=bounded(title,60);longTitle=bounded(longTitle,90);String label=title.matches("[0-9]{1,4}")?"第 "+Integer.parseInt(title)+" 集":title;if(longTitle.isEmpty()||longTitle.equals(title)||longTitle.equals(label))return label;return label.isEmpty()?longTitle:label+" · "+longTitle;}
    private static String bounded(String value,int max){String text=value==null?"":value.trim();return text.length()<=max?text:"";}
    void dispose(){}
}
