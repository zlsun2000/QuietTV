package local.quiettv.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.media.MediaMetadata;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Executes rule parsing, persistence and routing against a synthetic installed app only. */
public final class RuleChecks extends Instrumentation {
    private int count;private static final String PKG="local.quiettv.checks",ID="example.synthetic",EVENT=PKG+".HISTORY";
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);Bundle progress=new Bundle();progress.putString("check",message);sendStatus(0,progress);}
    private interface Work {void run()throws Exception;}
    private void rejects(Work work,String message)throws Exception {boolean rejected=false;try{work.run();}catch(Exception expected){rejected=true;}check(rejected,message);}
    private JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
    private JSONObject spec(String type,String field)throws Exception{return new JSONObject().put("type",type).put("field",field);}
    private JSONObject rule()throws Exception {
        JSONObject fields=new JSONObject();fields.put("id",new JSONObject().put("key","item").put("type","id").put("required",true));fields.put("title",new JSONObject().put("key","name").put("type","text").put("required",true));fields.put("position",new JSONObject().put("key","at").put("type","time").put("unit","s"));fields.put("duration",new JSONObject().put("key","length").put("type","time").put("unit","s"));
        JSONObject extras=new JSONObject().put("item",spec("string","id")).put("progress",spec("long","position").put("unit","s")).put("flags",new JSONObject().put("type","json").put("value",new JSONObject().put("enabled",new JSONObject().put("type","boolean").put("value",true)).put("mode",new JSONObject().put("type","int").put("value",3))));
        return new JSONObject().put("schema",1).put("id",ID).put("revision",1).put("name","Synthetic rule").put("author","QuietTV tests").put("license","MIT").put("verification",new JSONObject().put("status","emulator").put("notes","Synthetic events only")).put("target",new JSONObject().put("package",PKG).put("minVersionCode",1).put("maxVersionCode",1)).put("adapter","broadcast").put("event",new JSONObject().put("add",EVENT).put("delete",EVENT+".DELETE").put("sourceFields",new JSONArray().put("packageName")).put("sourceValues",new JSONArray().put(PKG))).put("fields",fields).put("identity",new JSONArray().put("id")).put("routes",new JSONArray().put(new JSONObject().put("requires",new JSONArray().put("id").put("position")).put("capability","position").put("intent",new JSONObject().put("action",PKG+".RESUME").put("component",PKG+".RuleTargetActivity").put("extras",extras))));
    }
    private Intent event(String id){return new Intent(EVENT).putExtra("packageName",PKG).putExtra("item",id).putExtra("name","Synthetic item").putExtra("at",83).putExtra("length",300);}
    private PlaybackRecord latest(Context c){ArrayList<PlaybackRecord> list=new HistoryStore(c).list();return list.isEmpty()?null:list.get(0);}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();boolean owned=false;try{
        if(!(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator")))throw new AssertionError("Only a synthetic emulator may run rule checks");
        c.getSharedPreferences("playback_rules",0).edit().clear().commit();c.getSharedPreferences("playback_history",0).edit().clear().putInt("config_schema",1).putBoolean("enabled",true).putBoolean("generic_enabled",false).commit();
        PlaybackRules registry=PlaybackRules.get(c);registry.reload();HistoryStore history=new HistoryStore(c);
        check(registry.list().size()==2&&registry.find(PlaybackRule.BILI)!=null&&registry.find(PlaybackRule.QIYI)!=null,"both existing channel contracts load from bundled JSON");
        for(String file:new String[]{"example-broadcast.json","example-session.json"})try(InputStream input=getContext().getAssets().open(file)){PlaybackRule example=registry.parse(PlaybackRules.read(input));check(example.pkg.equals("org.example.player"),"contributor template "+file+" passes the real import validator");}
        JSONObject json=rule();PlaybackRule parsed=registry.parse(json.toString());check(parsed.revision==1&&registry.targetStatus(parsed).isEmpty(),"local config matches the installed synthetic package and app version");
        JSONObject bad=copy(json).put("script","arbitrary code");rejects(()->registry.parse(bad.toString()),"unknown executable rule fields are rejected");
        JSONObject fractional=copy(json).put("revision",1.5);rejects(()->registry.parse(fractional.toString()),"fractional rule revisions cannot be silently truncated");
        StringBuilder deep=new StringBuilder();for(int i=0;i<30;i++)deep.append('[');for(int i=0;i<30;i++)deep.append(']');rejects(()->registry.parse(deep.toString()),"deeply nested imports are rejected before JSON parsing");
        JSONObject foreign=copy(json);foreign.getJSONArray("routes").getJSONObject(0).getJSONObject("intent").put("component","com.other/com.other.Player");rejects(()->registry.parse(foreign.toString()),"component declarations cannot override the fixed target package");
        JSONObject uri=copy(json);uri.getJSONArray("routes").getJSONObject(0).getJSONObject("intent").put("data",new JSONObject().put("scheme","file").put("host","private"));rejects(()->registry.parse(uri.toString()),"file/content/script URI schemes cannot be configured");
        JSONObject rawUrl=copy(json);rawUrl.getJSONArray("routes").getJSONObject(0).getJSONObject("intent").getJSONObject("extras").put("item",new JSONObject().put("type","string").put("value","https://example.invalid/video.mp4"));rejects(()->registry.parse(rawUrl.toString()),"literal playback URLs cannot be persisted in rule parameters");
        JSONObject mismatch=copy(json);mismatch.getJSONObject("target").put("minVersionCode",2).put("maxVersionCode",3);check(registry.targetStatus(registry.parse(mismatch.toString())).contains("版本"),"app version mismatch is diagnosed");
        JSONObject cert=copy(json);cert.getJSONObject("target").put("certSha256",new JSONArray().put(String.format(Locale.US,"%064d",0)));check(registry.targetStatus(registry.parse(cert.toString())).contains("签名"),"target signing identity mismatch is diagnosed");
        byte[] oversized=new byte[PlaybackRule.MAX_BYTES+1];rejects(()->PlaybackRules.read(new ByteArrayInputStream(oversized)),"import reader stops at the 64 KB bound");
        registry.install(parsed);parsed=registry.find(ID);check(!registry.enabled(parsed),"imported rules remain disabled until selected");PlaybackRules.receive(c,event("one"));check(history.list().isEmpty(),"disabled rule receives no records");
        registry.enabled(parsed,true);owned=LegacyHistoryListener.acquire(c);check(owned&&registry.actions().contains(EVENT),"custom broadcast actions register without rebuilding the launcher");
        Thread.sleep(250);CountDownLatch delivered=new CountDownLatch(1);BroadcastReceiver changed=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){delivered.countDown();}};c.registerReceiver(changed,new IntentFilter(HistoryStore.CHANGED),"local.quiettv.launcher.permission.INTERNAL",null);
        try{c.sendBroadcast(event("one").setPackage(c.getPackageName()));check(delivered.await(5,TimeUnit.SECONDS),"package-targeted custom action reaches the runtime rule listener");}finally{c.unregisterReceiver(changed);}
        PlaybackRecord record=latest(c);check(record!=null&&record.configured()&&record.valid()&&record.position==83000&&record.duration==300000,"rules map content and normalize progress to milliseconds");
        check(record.ruleData.size()==1&&record.ruleData.get("id").equals("one"),"storage retains only bounded content identifiers for the route");String originalKey=record.key();
        Intent route=parsed.intent(parsed.route(PlaybackRules.data(record)),PlaybackRules.data(record));check(route.getLongExtra("progress",-1)==83&&new JSONObject(route.getStringExtra("flags")).getBoolean("enabled")&&new JSONObject(route.getStringExtra("flags")).getInt("mode")==3,"route converts seconds and preserves typed JSON parameters");
        JSONObject alias=copy(json);alias.getJSONArray("routes").getJSONObject(0).getJSONObject("intent").put("component","local.quiettv.channel.ResumeAlias");PlaybackRule aliasRule=registry.parse(alias.toString());Intent aliasRoute=aliasRule.intent(aliasRule.route(PlaybackRules.data(record)),PlaybackRules.data(record));check(c.getPackageManager().resolveActivity(aliasRoute,0)!=null&&PKG.equals(aliasRoute.getComponent().getPackageName()),"channel class namespaces can differ while the installed target package stays fixed");
        final long[] received={-1};CountDownLatch routed=new CountDownLatch(1);BroadcastReceiver destination=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){received[0]=intent.getLongExtra("progress",-1);routed.countDown();}};c.registerReceiver(destination,new IntentFilter(PKG+".ROUTE_RESULT"),"local.quiettv.launcher.permission.INTERNAL",null);
        try{check(registry.openRoute(record),"verified route resolves to the synthetic installed Activity");check(routed.await(5,TimeUnit.SECONDS)&&received[0]==83,"synthetic destination receives the requested progress without launching media");}finally{c.unregisterReceiver(destination);}
        PlaybackRules.receive(c,event("wrong-source").putExtra("packageName","com.other.app"));check(history.list().size()==1&&latest(c).key().equals(originalKey),"unmatched provider declarations are ignored");
        PlaybackRules.receive(c,event("https://private.example/play"));check(history.list().size()==1&&registry.status(parsed).contains("字段"),"URLs and malformed content identifiers cannot enter history");
        check(registry.preview(parsed).contains("没有启动"),"preflight checks the saved route without starting playback");registry.enabled(parsed,false);check(history.list().size()==1&&registry.action(record).equals("打开应用"),"disabling a rule retains history and falls back to opening the app");
        JSONObject newer=copy(json).put("revision",2);registry.install(registry.parse(newer.toString()));check(!registry.enabled(registry.find(ID))&&registry.canRollback(registry.find(ID)),"rule update saves a rollback and requires explicit enabling");registry.enabled(registry.find(ID),true);PlaybackRules.receive(c,event("one").putExtra("at",90));check(history.list().size()==1&&latest(c).key().equals(originalKey)&&latest(c).position==90000,"rule update preserves identity and advances existing progress");
        registry.rollback(registry.find(ID));check(registry.find(ID).revision==1&&!registry.enabled(registry.find(ID))&&latest(c).position==90000,"rollback preserves records and remains disabled");
        // Use the real saved array rather than rebuilding a model from an implementation mirror.
        PlaybackRecord persisted=HistoryStore.decode(new JSONArray(c.getSharedPreferences("playback_history",0).getString("records","[]")).getJSONObject(0));check(persisted.valid()&&persisted.key().equals(originalKey)&&persisted.ruleData.equals(record.ruleData),"saved records round-trip with their stable route data");
        JSONObject competing=copy(json).put("id","example.competing");registry.install(registry.parse(competing.toString()));registry.enabled(registry.find(ID),true);rejects(()->registry.enabled(registry.find("example.competing"),true),"overlapping enabled rules require a single explicit choice");registry.remove(registry.find("example.competing"));
        JSONObject backup=new JSONObject().put("custom",new JSONObject().put("type","string").put("value",new JSONObject().put(ID,registry.find(ID).json.toString()).toString())).put("enabled_"+ID,new JSONObject().put("type","boolean").put("value",true));check(PlaybackRules.validBackup(backup),"maintenance backups validate bounded rule configuration");backup.put("unexpected",new JSONObject().put("type","string").put("value","unused"));check(!PlaybackRules.validBackup(backup),"maintenance restore rejects unknown rule preference data");
        for(int i=0;i<10;i++)PlaybackRules.receive(c,event("bounded-"+i));check(history.list().size()==8,"configured sources retain the eight-item history limit");
        PlaybackRecord removed=latest(c);history.remove(removed.key());long now=System.currentTimeMillis();c.getSharedPreferences("playback_history",0).edit().putLong("removed_"+removed.key(),now-60000).putString("vendor_sessions",new JSONArray().put(new JSONObject().put("key",removed.key()).put("position",83000).put("started",now-120000).put("seen",now)).toString()).commit();PlaybackRules.receive(c,event(removed.ruleData.get("id")));boolean resurrected=false;for(PlaybackRecord r:history.list())resurrected|=r.key().equals(removed.key());check(!resurrected,"ongoing broadcast sessions cannot recreate a removed record after the short deletion guard");
        registry.remove(registry.find(ID));check(registry.find(ID)==null&&!history.list().isEmpty(),"removing a custom rule preserves its existing history");
        builtinChecks(c,registry);
        JSONObject session=copy(json).put("id","example.session").put("adapter","media_session");session.remove("event");session.getJSONObject("fields").getJSONObject("id").put("key","mediaId");session.getJSONObject("fields").getJSONObject("title").put("key","title");session.getJSONObject("fields").getJSONObject("position").put("key","position").put("unit","ms");session.getJSONObject("fields").getJSONObject("duration").put("key","duration").put("unit","ms");registry.install(registry.parse(session.toString()));registry.enabled(registry.find("example.session"),true);
        PlaybackRecord media=new PlaybackRecord();media.source=PKG;media.sourceLabel="Fixture";media.command="media/session";media.videoId=MediaRecordPolicy.digest("session");media.title="Synthetic session";media.position=123000;PlaybackRules.decorateSession(c,media,new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"session-item").build());check(media.ruleId.equals("example.session")&&media.ruleData.get("id").equals("session-item")&&media.valid(),"standard media metadata can supply an optional persistent route rule");
        maintenanceChecks(c,registry);
        result.putString("passed",count+" rule checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("failed",error.toString());finish(Activity.RESULT_CANCELED,result);}finally{if(owned)LegacyHistoryListener.release();}}
    private void builtinChecks(Context c,PlaybackRules registry)throws Exception {
        PlaybackRule bili=registry.find(PlaybackRule.BILI);Map<String,Object> event=new HashMap<>();event.put("videoId","123456");event.put("episodeId","789");event.put("videoName","Synthetic Bili");event.put("currentPosition",607479);
        for(String kind:new String[]{"tcl/ugc","tcl/pgc","tcl/pugc"}){event.put("cmdInfo",kind);PlaybackRecord r=PlaybackRules.record(c,bili,bili.extract(event,false));check(r.valid()&&r.key().equals(kind+"|123456|789"),"Bili "+kind+" retains legacy history identity");Intent route=bili.intent(bili.route(PlaybackRules.data(r)),PlaybackRules.data(r));JSONObject values=new JSONObject(route.getStringExtra("value"));check(values.getString("progress").equals("607")&&values.getString("forcePlayProgress").equals("1")&&values.getString(kind.equals("tcl/ugc")?"cId":"epId").equals("789"),"Bili "+kind+" JSON maps exact supplied IDs and forced seconds");if(kind.equals("tcl/pugc"))check(values.getString("isClass").equals("1"),"Bili course route retains its class flag");}
        PlaybackRule qiyi=registry.find(PlaybackRule.QIYI);event.put("cmdInfo","{\"videoId\":\"123456\",\"episodeId\":\"789\",\"chnId\":2}");event.put("duration",300);PlaybackRecord r=PlaybackRules.record(c,qiyi,qiyi.extract(event,false));Intent route=qiyi.intent(qiyi.route(PlaybackRules.data(r)),PlaybackRules.data(r));JSONObject values=new JSONObject(route.getStringExtra("playInfo"));check(r.valid()&&r.duration==300000&&values.getInt("history")==-1&&values.getInt("chnId")==2,"Qiyi JSON keeps typed detail routing and duration units");event.put("videoId","654321");rejects(()->qiyi.extract(event,false),"Qiyi outer and nested content IDs must agree");
    }
    private void maintenanceChecks(Context c,PlaybackRules registry)throws Exception {
        ActivityInfo receiver=c.getPackageManager().getReceiverInfo(new ComponentName(c,LocalMaintenanceReceiver.class),0);
        check(receiver.exported&&"android.permission.DUMP".equals(receiver.permission),"headless rule imports retain the platform DUMP permission boundary");
        Map<String,?> home=new HashMap<>(c.getSharedPreferences("home",0).getAll()),weather=new HashMap<>(c.getSharedPreferences("weather",0).getAll()),history=new HashMap<>(c.getSharedPreferences("playback_history",0).getAll());
        File file=new File(c.getExternalFilesDir(null),"playback-rule-import.json");LocalMaintenanceReceiver importer=new LocalMaintenanceReceiver();Intent action=new Intent("local.quiettv.launcher.IMPORT_RULE");String id="example.maintenance";
        try{
            JSONObject json=rule().put("id",id);save(file,json.toString());importer.onReceive(c,action);
            check(registry.find(id)!=null&&!registry.enabled(registry.find(id)),"headless import uses the registry and keeps a new rule disabled");
            registry.enabled(registry.find(id),true);importer.onReceive(c,action);
            check(registry.find(id).revision==1&&registry.enabled(registry.find(id)),"rejected same-revision import preserves the active rule and choice");
            Map<String,?> rules=new HashMap<>(c.getSharedPreferences("playback_rules",0).getAll());save(file,"{malformed");importer.onReceive(c,action);
            check(rules.equals(c.getSharedPreferences("playback_rules",0).getAll()),"malformed headless imports leave the registry preferences intact");
            char[] large=new char[PlaybackRule.MAX_BYTES+1];Arrays.fill(large,' ');save(file,new String(large));importer.onReceive(c,action);
            check(rules.equals(c.getSharedPreferences("playback_rules",0).getAll()),"headless rule files enforce the same 64 KB input bound");
            save(file,json.put("revision",2).toString());importer.onReceive(c,action);
            check(registry.find(id).revision==2&&!registry.enabled(registry.find(id))&&registry.canRollback(registry.find(id)),"headless updates disable the new revision and retain rollback");
            check(home.equals(c.getSharedPreferences("home",0).getAll())&&weather.equals(c.getSharedPreferences("weather",0).getAll())&&history.equals(c.getSharedPreferences("playback_history",0).getAll()),"rule-only import preserves desktop preferences, weather and every history field");
        }finally{if(file.isFile()&&!file.delete())throw new IOException("Cannot clean synthetic rule import");}
    }
    private static void save(File file,String value)throws Exception {try(Writer writer=new OutputStreamWriter(new FileOutputStream(file),"UTF-8")){writer.write(value);}}
}
