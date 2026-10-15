package local.quiettv.launcher;

import android.content.*;
import android.content.pm.*;
import android.media.MediaMetadata;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Local rule registry, bounded imports, diagnostics and verified target dispatch. */
final class PlaybackRules {
    private static PlaybackRules instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final LinkedHashMap<String,PlaybackRule> bundled=new LinkedHashMap<>(),rules=new LinkedHashMap<>();
    private final HashMap<String,String> diagnostics=new HashMap<>();
    private static final int MAX_CUSTOM=16,MAX_TOTAL=524288;
    static synchronized PlaybackRules get(Context c){if(instance==null)instance=new PlaybackRules(c.getApplicationContext());return instance;}
    private PlaybackRules(Context c){
        context=c;prefs=c.getSharedPreferences("playback_rules",Context.MODE_PRIVATE);
        for(int resource:new int[]{R.raw.rule_bili_tcl,R.raw.rule_qiyi_tcl})try(InputStream input=c.getResources().openRawResource(resource)){PlaybackRule rule=new PlaybackRule(read(input),true);bundled.put(rule.id,rule);}catch(Exception ignored){}
        reload();
    }
    synchronized void reload(){
        rules.clear();rules.putAll(bundled);
        try{JSONObject custom=new JSONObject(prefs.getString("custom","{}"));Iterator<String> names=custom.keys();int count=0;while(names.hasNext()&&count++<MAX_CUSTOM){String id=names.next();try{PlaybackRule rule=new PlaybackRule(custom.getString(id),false);if(id.equals(rule.id)&&sameTarget(rule))rules.put(id,rule);}catch(Exception ignored){}}}catch(Exception ignored){}
    }
    synchronized ArrayList<PlaybackRule> list(){return new ArrayList<>(rules.values());}
    synchronized PlaybackRule find(String id){return rules.get(id);}
    synchronized boolean enabled(PlaybackRule rule){return prefs.getBoolean("enabled_"+rule.id,rule.builtin);}
    synchronized void enabled(PlaybackRule rule,boolean enabled)throws PlaybackRule.Invalid {if(enabled)for(PlaybackRule other:rules.values())PlaybackRule.require(other.id.equals(rule.id)||!enabled(other)||!other.pkg.equals(rule.pkg)||!other.adapter.equals(rule.adapter)||other.target.optLong("maxVersionCode",Long.MAX_VALUE)<rule.target.optLong("minVersionCode",0)||rule.target.optLong("maxVersionCode",Long.MAX_VALUE)<other.target.optLong("minVersionCode",0),"请先停用此应用同一接入方式的重叠规则");PlaybackRule.require(prefs.edit().putBoolean("enabled_"+rule.id,enabled).commit(),"无法保存规则开关");diagnostics.remove(rule.id);updated();}
    private void updated(){new Handler(Looper.getMainLooper()).post(()->{LegacyHistoryListener.refresh();GenericMediaHistory.changed(context);});}
    private boolean sameTarget(PlaybackRule rule){PlaybackRule original=rules.get(rule.id);if(original==null)original=bundled.get(rule.id);return original==null||original.pkg.equals(rule.pkg);}
    PlaybackRule parse(String raw)throws Exception {PlaybackRule rule=new PlaybackRule(raw,false);PlaybackRule.require(sameTarget(rule),"内置规则的目标包名不能更换");return rule;}
    synchronized void install(PlaybackRule rule)throws Exception {
        PlaybackRule.require(sameTarget(rule),"规则目标不一致");PlaybackRule old=rules.get(rule.id);
        PlaybackRule.require(old==null||rule.revision>old.revision,"同一规则的新 revision 必须更大");
        JSONObject custom=new JSONObject(prefs.getString("custom","{}"));PlaybackRule.require(custom.has(rule.id)||custom.length()<MAX_CUSTOM,"最多导入 16 个规则");
        custom.put(rule.id,rule.json.toString());PlaybackRule.require(custom.toString().getBytes("UTF-8").length<=MAX_TOTAL,"规则总容量超过 512 KB");
        SharedPreferences.Editor editor=prefs.edit().putString("custom",custom.toString()).putBoolean("enabled_"+rule.id,false);
        if(old!=null)editor.putString("previous_"+rule.id,old.json.toString());else editor.remove("previous_"+rule.id);
        PlaybackRule.require(editor.commit(),"无法保存规则");reload();updated();
    }
    synchronized boolean canRollback(PlaybackRule rule){return prefs.contains("previous_"+rule.id)||(!rule.builtin&&bundled.containsKey(rule.id));}
    synchronized void rollback(PlaybackRule rule)throws Exception {
        JSONObject custom=new JSONObject(prefs.getString("custom","{}"));String previous=prefs.getString("previous_"+rule.id,"");
        if(previous.isEmpty()){PlaybackRule.require(bundled.containsKey(rule.id),"没有可回退版本");custom.remove(rule.id);}
        else{PlaybackRule prior=parse(previous);PlaybackRule.require(prior.id.equals(rule.id),"回退版本不匹配");PlaybackRule original=bundled.get(rule.id);if(original!=null&&original.json.toString().equals(prior.json.toString()))custom.remove(rule.id);else custom.put(rule.id,prior.json.toString());}
        PlaybackRule.require(prefs.edit().putString("custom",custom.toString()).remove("previous_"+rule.id).putBoolean("enabled_"+rule.id,false).commit(),"无法回退规则");reload();updated();
    }
    synchronized void remove(PlaybackRule rule)throws Exception {
        PlaybackRule.require(!rule.builtin&&!bundled.containsKey(rule.id),"内置规则可停用或恢复内置版本");JSONObject custom=new JSONObject(prefs.getString("custom","{}"));custom.remove(rule.id);
        PlaybackRule.require(prefs.edit().putString("custom",custom.toString()).remove("previous_"+rule.id).remove("enabled_"+rule.id).commit(),"无法删除规则");diagnostics.remove(rule.id);reload();updated();
    }
    synchronized Set<String> actions(){HashSet<String> actions=new HashSet<>();for(PlaybackRule rule:rules.values())if(enabled(rule)&&rule.adapter.equals("broadcast")){actions.add(rule.addAction);if(!rule.deleteAction.isEmpty())actions.add(rule.deleteAction);}return actions;}
    String targetStatus(PlaybackRule rule){
        try{
            PackageManager pm=context.getPackageManager();PackageInfo info=pm.getPackageInfo(rule.pkg,PackageManager.GET_SIGNATURES);
            if(info.applicationInfo==null||!info.applicationInfo.enabled)return "目标应用已停用";
            long version=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
            if(version<rule.target.optLong("minVersionCode",0)||version>rule.target.optLong("maxVersionCode",Long.MAX_VALUE))return "应用版本不在适用范围";
            if(Build.VERSION.SDK_INT<rule.target.optInt("minSdk",23)||Build.VERSION.SDK_INT>rule.target.optInt("maxSdk",1000))return "系统版本不在适用范围";
            JSONArray certs=rule.target.optJSONArray("certSha256");if(certs!=null){boolean match=false;for(Signature signature:info.signatures){String digest=digest(signature.toByteArray());for(int i=0;i<certs.length();i++)match|=digest.equals(certs.getString(i));}if(!match)return "目标应用签名不匹配";}
            return "";
        }catch(PackageManager.NameNotFoundException e){return "目标应用尚未安装";}catch(Exception ignored){return "无法校验目标应用";}
    }
    private static String digest(byte[] data)throws Exception {StringBuilder result=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(data))result.append(String.format(Locale.US,"%02x",value&255));return result.toString();}
    synchronized String status(PlaybackRule rule){
        if(!enabled(rule))return "已停用";String target=targetStatus(rule);if(!target.isEmpty())return target;
        HistoryStore store=new HistoryStore(context);if(!store.enabled())return "播放记录总开关已关闭";if(!store.appEnabled(rule.pkg))return "此应用的播放记录已关闭";
        if((rule.adapter.equals("media_session")||rule.adapter.equals("watch_next")||rule.id.equals(PlaybackRule.QIYI))&&!store.genericEnabled())return "其他应用自动识别已关闭";
        if(rule.adapter.equals("media_session")&&!GenericMediaHistory.capability(context).isAvailable())return GenericMediaHistory.availability(context);
        if(rule.adapter.equals("watch_next")&&!WatchNextHistory.availability(context).isEmpty())return WatchNextHistory.availability(context);
        String last=diagnostics.get(rule.id);return last==null?rule.adapter.equals("broadcast")?"等待匹配的历史广播":rule.adapter.equals("watch_next")?"等待系统继续观看记录":"等待匹配的媒体会话":last;
    }
    String describe(PlaybackRule rule){
        JSONObject verification=rule.json.optJSONObject("verification");String range=rule.target.optLong("minVersionCode",0)+"–"+(rule.target.has("maxVersionCode")?String.valueOf(rule.target.optLong("maxVersionCode")):"未设上限");
        return (rule.adapter.equals("watch_next")&&rule.json.optJSONObject("watchNext").has("artwork")?"此规则启用系统海报：可从规则限定的 HTTPS 主机下载图片并缓存；对方可见 IP 与图片路径。\n\n":"")+rule.json.optString("description")+"\n\n目标："+rule.pkg+"\n应用 versionCode："+range+"\n接入："+(rule.adapter.equals("broadcast")?"历史广播":rule.adapter.equals("watch_next")?"系统 Watch Next":"媒体会话")+"\n作者："+rule.json.optString("author")+" · "+rule.json.optString("license")+"\n验证："+verification.optString("status")+"\n"+verification.optString("notes")+"\n\n"+(rule.target.has("certSha256")?"已限定目标应用签名。":"未限定应用签名；包名声明不代表广播发送者已经认证。")+"\n停用或更新规则会保留已有记录；接口失效时可打开原应用。";
    }
    String preview(PlaybackRule rule){
        if(rule.adapter.equals("watch_next"))return "配置语法已通过校验。\n"+status(rule)+"\n点击时重新读取系统记录并校验入口；进度由原应用决定。\n本次预检没有启动应用。";
        String target=targetStatus(rule);StringBuilder result=new StringBuilder("配置语法与字段声明已通过校验。\n目标：").append(target.isEmpty()?"应用和版本匹配":target);
        for(PlaybackRecord record:new HistoryStore(context).list())if(rule.id.equals(ruleId(record))&&rule.pkg.equals(record.source))try{Intent intent=route(rule,record);result.append("\n最近记录：").append(intent==null?"无匹配或可解析的入口":"可构造并解析目标入口");result.append("\n本次预检没有启动应用或发送播放命令。");return result.toString();}catch(Exception ignored){result.append("\n最近记录：与当前规则字段不匹配");return result.toString();}
        return result.append("\n尚无对应记录；开启后可等待原应用提供信息。\n").append(status(rule)).toString();
    }
    synchronized void note(PlaybackRule rule,String message){diagnostics.put(rule.id,message);}
    static void receive(Context c,Intent intent){receive(c,intent,null);}
    static void receive(Context c,Intent intent,String only){
        if(intent==null)return;UserManager users=(UserManager)c.getSystemService(Context.USER_SERVICE);if(Build.VERSION.SDK_INT>=24&&(users==null||!users.isUserUnlocked()))return;
        try{
            PlaybackRules registry=get(c);HistoryStore store=new HistoryStore(c);if(!store.enabled())return;Bundle extras=intent.getExtras();if(extras==null)return;
            // No sender authentication exists in these legacy contracts. Values are always
            // untrusted, target packages are fixed, and no received Intent/URI is forwarded.
            Map<String,Object> event=new HashMap<>();for(String key:extras.keySet()){Object value=extras.get(key);if((value instanceof String&&((String)value).length()<=4096)||value instanceof Number)event.put(key,value);if(event.size()>40)return;}
            for(PlaybackRule rule:registry.list()){
                if(only!=null&&!only.equals(rule.id)||!registry.enabled(rule)||!rule.adapter.equals("broadcast")||!store.appEnabled(rule.pkg))continue;
                boolean delete=intent.getAction()!=null&&intent.getAction().equals(rule.deleteAction);
                if(!delete&&!rule.addAction.equals(intent.getAction())||!rule.sourceMatches(event))continue;
                if(!registry.targetStatus(rule).isEmpty()){registry.note(rule,registry.targetStatus(rule));continue;}
                if(rule.id.equals(PlaybackRule.QIYI)&&!store.genericEnabled())continue;
                try{
                    Map<String,String> values=rule.extract(event,delete);
                    if(delete){String id=values.get("id");if(id==null)continue;for(PlaybackRecord record:store.list())if(record.source.equals(rule.pkg)&&rule.id.equals(ruleId(record))&&id.equals(data(record).get("id")))store.remove(record.key());GenericMediaHistory.changed(c);registry.note(rule,"已处理删除事件");continue;}
                    PlaybackRecord record=record(c,rule,values);PlaybackRule.require(record.valid(),"记录字段不完整或不匹配");
                    if(record.command.equals("media/qiyi"))for(PlaybackRecord old:store.list())if(old.command.equals("media/session")&&!record.episode.isEmpty()&&MediaRecordPolicy.sameItem(record,old)){MediaRecordPolicy.retainIdentity(record,old);break;}
                    if(record.generic())record.startedAt=started(c,record);
                    if(store.put(record)){registry.note(rule,"已接收有效记录");GenericMediaHistory.changed(c);}else registry.note(rule,"记录被开关或删除标记过滤");
                }catch(Exception ignored){registry.note(rule,"字段缺失、类型不匹配或内容标识无效");}
            }
        }catch(Exception ignored){}
    }
    static long started(Context c,PlaybackRecord record)throws Exception {
        SharedPreferences history=c.getSharedPreferences("playback_history",Context.MODE_PRIVATE);JSONArray previous=new JSONArray(history.getString("vendor_sessions","[]")),next=new JSONArray();long start=record.updated;
        for(int i=0;i<Math.min(16,previous.length());i++){JSONObject item=previous.optJSONObject(i);if(item==null)continue;if(record.key().equals(item.optString("key"))){if(record.updated-item.optLong("seen")<3600000&&record.position>=item.optLong("position")-30000)start=item.optLong("started",record.updated);}else if(record.updated-item.optLong("seen")<259200000L)next.put(item);}
        JSONObject item=new JSONObject();item.put("key",record.key());item.put("position",record.position);item.put("started",start);item.put("seen",record.updated);JSONArray bounded=new JSONArray();bounded.put(item);for(int i=0;i<Math.min(15,next.length());i++)bounded.put(next.getJSONObject(i));history.edit().putString("vendor_sessions",bounded.toString()).apply();return start;
    }
    static PlaybackRecord record(Context c,PlaybackRule rule,Map<String,String> values)throws Exception {
        PlaybackRecord r=new PlaybackRecord();r.source=rule.pkg;r.sourceLabel=GenericMediaHistory.label(c,rule.pkg);r.ruleId=rule.id;r.ruleData=stored(values);
        r.title=get(values,"title");r.episode=get(values,"subtitle");r.positionKnown=values.containsKey("position");r.position=number(values,"position");r.duration=number(values,"duration");r.command="rule/broadcast";r.videoId=rule.identity(values);r.updated=System.currentTimeMillis();r.startedAt=r.updated;
        if(rule.id.equals(PlaybackRule.BILI)&&rule.pkg.equals(PlaybackRecord.PACKAGE)){r.videoId=get(values,"id");r.episodeId=get(values,"episodeId");r.command=get(values,"variant");PlaybackRule.require(r.bili(),"B 站兼容命令不匹配");}
        else if(rule.id.equals(PlaybackRule.QIYI)&&rule.pkg.equals(QiyiRoute.PACKAGE)){r.command="media/qiyi";r.routeId=get(values,"id");r.episodeId=get(values,"episodeId");r.routeChannel=get(values,"channel");r.episode=QiyiRoute.episode(r.episode);r.videoId=MediaRecordPolicy.identity(r.source,r.routeId+"|"+r.episodeId,r.title,r.episode,"",r.duration);}
        r.clamp();return r;
    }
    static void decorateSession(Context c,PlaybackRecord record,MediaMetadata metadata){
        PlaybackRules registry=get(c);Map<String,Object> event=new HashMap<>();event.put("mediaId",metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));event.put("title",record.title);event.put("subtitle",record.episode);if(record.positionKnown)event.put("position",record.position);event.put("duration",record.duration);
        for(PlaybackRule rule:registry.list())if(rule.adapter.equals("media_session")&&rule.pkg.equals(record.source)&&registry.enabled(rule)&&registry.targetStatus(rule).isEmpty())try{
            Map<String,String> values=rule.extract(event,false);if(!record.ruleId.isEmpty()){registry.note(rule,"同一应用存在多条匹配规则，请只启用一条");continue;}
            record.ruleId=rule.id;record.ruleData=stored(values);registry.note(rule,"已匹配媒体会话字段");
        }catch(Exception ignored){registry.note(rule,"媒体会话缺少规则所需字段");}
    }
    private static Map<String,String> stored(Map<String,String> values){Map<String,String> result=new LinkedHashMap<>();for(String key:new String[]{"id","episodeId","variant","channel"})if(values.containsKey(key))result.put(key,values.get(key));return result;}
    static String ruleId(PlaybackRecord r){return !r.ruleId.isEmpty()?r.ruleId:r.bili()?PlaybackRule.BILI:r.command.equals("media/qiyi")?PlaybackRule.QIYI:"";}
    static Map<String,String> data(PlaybackRecord r){Map<String,String> values=new LinkedHashMap<>(r.ruleData);if(r.bili()){values.put("id",r.videoId);if(!r.episodeId.isEmpty())values.put("episodeId",r.episodeId);values.put("variant",r.command);}else if(r.command.equals("media/qiyi")){values.put("id",r.routeId);values.put("episodeId",r.episodeId);values.put("channel",r.routeChannel);}if(r.positionKnown)values.put("position",String.valueOf(r.position));if(r.duration>0)values.put("duration",String.valueOf(r.duration));return values;}
    private PlaybackRule usable(PlaybackRecord record){PlaybackRule rule=find(ruleId(record));if(rule==null||!enabled(rule)||!rule.pkg.equals(record.source)||!targetStatus(rule).isEmpty())return null;return rule;}
    private Intent route(PlaybackRule rule,PlaybackRecord record)throws Exception {
        Map<String,String> values=data(record);JSONObject route=rule.route(values);if(route==null)return null;Intent intent=rule.intent(route,values);
        ResolveInfo target=context.getPackageManager().resolveActivity(intent,0);if(target==null||target.activityInfo==null||!target.activityInfo.enabled||!target.activityInfo.exported||!rule.pkg.equals(target.activityInfo.packageName)){note(rule,"目标应用没有可用的续播入口");return null;}
        if(target.activityInfo.permission!=null&&context.checkSelfPermission(target.activityInfo.permission)!=PackageManager.PERMISSION_GRANTED){note(rule,"目标续播入口需要未授予的权限");return null;}
        return intent;
    }
    String action(PlaybackRecord record){
        PlaybackRule rule=usable(record);if(rule!=null&&record.watchNext())return WatchNextHistory.eligible(context,rule)?"尝试继续观看":"打开应用";if(rule!=null)try{JSONObject route=rule.route(data(record));if(route!=null&&route(rule,record)!=null)return route.getString("capability").equals("position")?"尝试继续播放":"打开内容";}catch(Exception ignored){note(rule,"记录与当前规则不匹配");}
        return "打开应用";
    }
    boolean openRoute(PlaybackRecord record){
        if(!record.valid())return false;PlaybackRule rule=usable(record);if(rule==null)return false;
        if(record.watchNext()){if(!WatchNextHistory.eligible(context,rule))return false;WatchNextHistory.open(context,rule,record);return true;}
        try{Intent intent=route(rule,record);if(intent!=null){context.startActivity(intent);note(rule,"已发送续播请求；实际播放需在目标应用确认");return true;}}catch(Exception ignored){note(rule,"续播请求失败，已回退打开应用");}return false;
    }
    static String read(InputStream input)throws Exception {PlaybackRule.require(input!=null,"无法读取规则文件");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){PlaybackRule.require(bytes.size()+count<=PlaybackRule.MAX_BYTES,"规则文件超过 64 KB");bytes.write(buffer,0,count);}String raw=new String(bytes.toByteArray(),"UTF-8");return raw.startsWith("\ufeff")?raw.substring(1):raw;}
    static boolean validBackup(JSONObject backup){try{
        if(backup.length()>MAX_CUSTOM*2+5)return false;int previous=0;Iterator<String> keys=backup.keys();
        while(keys.hasNext()){String key=keys.next();JSONObject item=backup.getJSONObject(key);String type=item.getString("type");if(key.equals("custom")){
            if(!type.equals("string"))return false;String raw=item.getString("value");if(raw.getBytes("UTF-8").length>MAX_TOTAL)return false;JSONObject custom=new JSONObject(raw);if(custom.length()>MAX_CUSTOM)return false;Iterator<String> names=custom.keys();while(names.hasNext()){String id=names.next();PlaybackRule rule=new PlaybackRule(custom.getString(id),false);if(!id.equals(rule.id)||!reservedTarget(rule))return false;}
        }else if(key.startsWith("previous_")){if(!type.equals("string"))return false;String raw=item.getString("value");PlaybackRule rule=new PlaybackRule(raw,false);if(!key.substring(9).equals(rule.id)||!reservedTarget(rule))return false;previous+=raw.getBytes("UTF-8").length;if(previous>MAX_CUSTOM*PlaybackRule.MAX_BYTES)return false;
        }else if(key.startsWith("enabled_")){if(!type.equals("boolean")||!(item.get("value") instanceof Boolean)||!key.substring(8).matches("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+"))return false;
        }else return false;}
        return true;
    }catch(Exception ignored){return false;}}
    private static boolean reservedTarget(PlaybackRule rule){return (!rule.id.equals(PlaybackRule.BILI)||rule.pkg.equals(PlaybackRecord.PACKAGE))&&(!rule.id.equals(PlaybackRule.QIYI)||rule.pkg.equals(QiyiRoute.PACKAGE));}
    private static String get(Map<String,String> values,String key){String value=values.get(key);return value==null?"":value;}
    private static long number(Map<String,String> values,String key){String value=values.get(key);return value==null?0:Long.parseLong(value);}
}
