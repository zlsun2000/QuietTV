package local.quiettv.launcher;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Tests the real system provider with isolated, locally published records only. */
public final class WatchNextChecks extends Instrumentation {
    private int count;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String label){count++;if(!ok)throw new AssertionError(label);Bundle b=new Bundle();b.putString("check",label);sendStatus(0,b);}
    private interface Work{void run()throws Exception;}
    private void rejects(Work work,String label)throws Exception{boolean failed=false;try{work.run();}catch(Exception expected){failed=true;}check(failed,label);}
    @Override public void onStart(){Context c=getTargetContext();Bundle output=new Bundle();boolean owned=false;try{
        if(Build.VERSION.SDK_INT<26||!(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator")))throw new AssertionError("Isolated API 26+ emulator only");
        c.getSharedPreferences("playback_history",0).edit().clear().putInt("config_schema",1).putBoolean("enabled",true).putBoolean("generic_enabled",true).commit();
        c.getSharedPreferences("playback_rules",0).edit().clear().commit();PlaybackRules registry=PlaybackRules.get(c);registry.reload();HistoryStore store=new HistoryStore(c);
        JSONObject json;try(java.io.InputStream in=getContext().getAssets().open("example-watch-next.json")){json=new JSONObject(PlaybackRules.read(in));}
        try(java.io.InputStream in=getContext().getAssets().open("netflix-watch-next.json")){PlaybackRule contributed=registry.parse(PlaybackRules.read(in));check(contributed.pkg.equals("com.netflix.ninja")&&contributed.adapter.equals("watch_next")&&contributed.target.has("certSha256"),"the published Netflix configuration passes the actual bounded import parser without installing Netflix");}
        try(java.io.InputStream in=getContext().getAssets().open("bilibili-tcl.json")){PlaybackRule contributed=registry.parse(PlaybackRules.read(in));check(contributed.json.toString().equals(registry.find(PlaybackRule.BILI).json.toString()),"the downloadable TCL Bilibili configuration matches the bundled contract and revision");}
        json.getJSONObject("target").put("package","local.quiettv.checks");json.getJSONObject("watchNext").getJSONObject("intent").put("action","local.quiettv.checks.RESUME").put("component","local.quiettv.checks.RuleTargetActivity");
        json.getJSONObject("watchNext").put("artwork",new JSONObject().put("source","poster").put("httpsHosts",new JSONArray()));
        PlaybackRule rule=registry.parse(json.toString());registry.install(rule);
        check(!registry.enabled(rule)&&read(c,rule).records.isEmpty(),"native provider imports stay disabled and do not collect history");
        JSONObject unsafe=new JSONObject(json.toString());unsafe.getJSONObject("fields").getJSONObject("id").put("key","intent_uri");rejects(()->registry.parse(unsafe.toString()),"native identifiers cannot map or persist raw provider links");
        JSONObject wrongCapability=new JSONObject(json.toString());wrongCapability.getJSONArray("routes").getJSONObject(0).put("capability","position");rejects(()->registry.parse(wrongCapability.toString()),"native routing cannot claim exact position control");
        JSONObject mixed=new JSONObject(json.toString());mixed.put("adapter","media_session");rejects(()->registry.parse(mixed.toString()),"native source declarations cannot be mixed with session adapters");
        registry.enabled(rule,true);WatchNextHistory.ReadResult loaded=read(c,rule);
        check(loaded.records.size()==1,"the system reader accepts only the owned continuing row and rejects foreign and next-episode rows");
        PlaybackRecord record=loaded.records.get(0);
        check(record.valid()&&record.watchNext()&&record.title.equals("QuietTV synthetic fixture")&&record.position==83000&&record.duration==300000,"native cards use actual publisher metadata and progress");
        runOnMainSync(()->WatchNextHistory.commit(c,Arrays.asList(new WatchNextHistory.Candidate(rule,record))));
        PlaybackRecord placeholder=new PlaybackRecord();placeholder.source=rule.pkg;placeholder.sourceLabel=GenericMediaHistory.label(c,rule.pkg);placeholder.title=placeholder.sourceLabel;placeholder.command="media/session";placeholder.videoId=MediaRecordPolicy.digest("placeholder");placeholder.updated=System.currentTimeMillis();placeholder.startedAt=placeholder.updated;store.put(placeholder);
        check(store.list().size()==2&&store.visible().size()==1&&store.visible().get(0).watchNext(),"real native content hides only the same app's empty session placeholder without deleting its identity");
        registry.enabled(rule,false);check(store.visible().size()==2,"disabling the native source restores the retained application fallback");registry.enabled(rule,true);store.remove(placeholder.key());
        String persisted=c.getSharedPreferences("playback_history",0).getString("records","");
        check(!persisted.contains("fixture%")&&!persisted.contains("payload")&&!persisted.contains("intent:")&&!persisted.contains("80000001")&&!persisted.contains("https:"),"stored native history contains no original publisher IDs or complete links");
        check(registry.action(record).equals("尝试继续观看")&&registry.preview(rule).contains("重新读取"),"native capability labels defer progress to the original app without a blocking UI query");
        check(read(c,rule,record).route!=null,"click lookup finds the same hashed publisher identity in a fresh system query");
        PlaybackRecord changed=HistoryStore.decode(new JSONArray(persisted).getJSONObject(0));changed.contentId=MediaRecordPolicy.digest("another publisher");changed.ruleData.put("id",changed.contentId);
        check(read(c,rule,changed).route==null,"a reused or mismatched system row cannot dispatch another content identity");
        String good=new Intent("local.quiettv.checks.RESUME").setData(Uri.parse("https://example.invalid/deeplink?payload=fixture%2Bvalue%2F%3D")).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).toUri(Intent.URI_INTENT_SCHEME);
        Intent httpSerialized=new Intent("local.quiettv.checks.RESUME").setComponent(new ComponentName(rule.pkg,"local.quiettv.checks.RuleTargetActivity")).setData(Uri.parse("https://example.invalid/deeplink?payload=x"));
        check(WatchNextHistory.nativeIntent(c,rule,httpSerialized.toUri(0)).getData().getFragment()==null,"ordinary URI plus Intent suffix uses Android parse mode zero rather than treating the suffix as a URL fragment");
        Intent rebuilt=WatchNextHistory.nativeIntent(c,rule,good);
        check(rebuilt.getFlags()==Intent.FLAG_ACTIVITY_NEW_TASK&&rebuilt.getComponent().getPackageName().equals(rule.pkg)&&rebuilt.getData().getEncodedQuery().equals("payload=fixture%2Bvalue%2F%3D"),"native dispatch reconstructs the fixed target, strips source flags and preserves opaque encoding only in memory");
        rejects(()->WatchNextHistory.nativeIntent(c,rule,good.replace("example.invalid","foreign.invalid")),"foreign native URI authorities are rejected");
        rejects(()->WatchNextHistory.nativeIntent(c,rule,good.replace("deeplink?","another?")),"undeclared native URI paths are rejected");
        rejects(()->WatchNextHistory.nativeIntent(c,rule,good.replace("payload=","unknown=")),"undeclared native URI parameters are rejected");
        rejects(()->WatchNextHistory.nativeIntent(c,rule,new Intent("local.quiettv.checks.RESUME").setData(Uri.parse("https://example.invalid/deeplink?payload=x&payload=y")).toUri(Intent.URI_INTENT_SCHEME)),"duplicate opaque URI parameters are rejected");
        rejects(()->WatchNextHistory.nativeIntent(c,rule,new Intent("local.quiettv.checks.RESUME").setData(Uri.parse("https://example.invalid/deeplink?payload=x")).putExtra("cookie","synthetic").toUri(Intent.URI_INTENT_SCHEME)),"unreviewed native Intent extras are rejected");
        CountDownLatch clicked=new CountDownLatch(1);BroadcastReceiver ack=new BroadcastReceiver(){public void onReceive(Context x,Intent i){if(i.getBooleanExtra("native_ok",false))clicked.countDown();}};
        c.registerReceiver(ack,new IntentFilter("local.quiettv.checks.ROUTE_RESULT"),"local.quiettv.launcher.permission.INTERNAL",null);
        try{runOnMainSync(()->registry.openRoute(record));check(clicked.await(8,TimeUnit.SECONDS),"the actual async native click opens the synthetic destination with the freshly verified route");}finally{c.unregisterReceiver(ack);}
        store.appEnabled(rule.pkg,false);check(read(c,rule).records.isEmpty()&&store.visible().isEmpty(),"per-app opt-out blocks collection and hides native cards");store.appEnabled(rule.pkg,true);
        store.genericEnabled(false);check(read(c,rule).records.isEmpty()&&store.visible().isEmpty(),"generic history opt-out also applies to configured native records");store.genericEnabled(true);
        registry.enabled(rule,false);check(registry.action(record).equals("打开应用")&&!store.list().isEmpty(),"disabling native rules keeps stored identities and safely degrades the action");registry.enabled(rule,true);
        WatchNextHistory.ReadResult stale=read(c,rule);JSONObject revision=new JSONObject(json.toString()).put("revision",2);registry.install(registry.parse(revision.toString()));registry.enabled(registry.find(rule.id),true);
        store.clear();runOnMainSync(()->WatchNextHistory.commit(c,Arrays.asList(new WatchNextHistory.Candidate(rule,stale.records.get(0)))));check(store.list().isEmpty(),"rule updates cannot commit a stale in-flight capture");
        PlaybackRule newer=registry.find(rule.id);WatchNextHistory.ReadResult afterClear=read(c,newer);runOnMainSync(()->WatchNextHistory.commit(c,Arrays.asList(new WatchNextHistory.Candidate(newer,afterClear.records.get(0)))));check(store.list().isEmpty(),"clearing local history does not resurrect old system records");
        c.getSharedPreferences("playback_history",0).edit().remove("cleared_at").remove("vendor_sessions").commit();
        owned=LegacyHistoryListener.acquire(c);long until=SystemClock.elapsedRealtime()+5000;while(store.list().isEmpty()&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);
        check(!store.list().isEmpty(),"the shared Activity/service listener collects opted-in native records without a new service");
        long artUntil=SystemClock.elapsedRealtime()+5000;PlaybackRecord artRecord=store.list().get(0);while(!MediaArtwork.local(artRecord.cover)&&SystemClock.elapsedRealtime()<artUntil){Thread.sleep(100);artRecord=store.list().get(0);}
        check(MediaArtwork.local(artRecord.cover)&&MediaArtwork.file(c,artRecord.cover).isFile(),"system publisher content artwork is cached locally with the real record");
        JSONObject remoteJson=new JSONObject(json.toString());remoteJson.getJSONObject("watchNext").getJSONObject("artwork").put("httpsHosts",new JSONArray().put("*.example.invalid"));
        rejects(()->registry.parse(remoteJson.toString()),"remote publisher artwork requires an explicitly pinned target signing identity");
        remoteJson.getJSONObject("target").put("certSha256",new JSONArray().put(String.format(java.util.Locale.US,"%064d",0)));PlaybackRule remote=registry.parse(remoteJson.toString());
        check(WatchNextArtwork.allowed(remote,"https://cdn.example.invalid/poster.jpg")!=null,"explicit artwork domain policy accepts a publisher CDN subdomain");
        check(WatchNextArtwork.allowed(remote,"https://cdn.example.invalid.evil.invalid/poster.jpg")==null&&WatchNextArtwork.allowed(remote,"https://example.invalid/poster.jpg")==null,"artwork host matching respects domain boundaries and wildcard scope");
        check(WatchNextArtwork.allowed(remote,"http://cdn.example.invalid/poster.jpg")==null&&WatchNextArtwork.allowed(remote,"https://user@cdn.example.invalid/poster.jpg")==null&&WatchNextArtwork.allowed(remote,"https://cdn.example.invalid/poster.jpg?token=synthetic")==null,"artwork requests reject cleartext, user information and queries");
        JSONObject withQuery=new JSONObject(remoteJson.toString());withQuery.getJSONObject("watchNext").getJSONObject("artwork").put("queryKeys",new JSONArray().put("client").put("platform"));PlaybackRule queryRule=registry.parse(withQuery.toString());
        check(WatchNextArtwork.allowed(queryRule,"https://cdn.example.invalid/poster.jpg?client=tv&platform=android")!=null&&WatchNextArtwork.allowed(queryRule,"https://cdn.example.invalid/poster.jpg?client=tv&platform=android&token=bad")==null&&WatchNextArtwork.allowed(queryRule,"https://cdn.example.invalid/poster.jpg?client=tv&client=other&platform=android")==null,"explicit publisher image parameters are bounded and reject undeclared or duplicate keys");
        JSONObject credentials=new JSONObject(withQuery.toString());credentials.getJSONObject("watchNext").getJSONObject("artwork").put("queryKeys",new JSONArray().put("authToken"));rejects(()->registry.parse(credentials.toString()),"artwork parameter policies cannot declare account credentials");
        check(!WatchNextArtwork.publicAddress(java.net.InetAddress.getByName("127.0.0.1"))&&!WatchNextArtwork.publicAddress(java.net.InetAddress.getByName("192.168.31.91"))&&!WatchNextArtwork.publicAddress(java.net.InetAddress.getByName("169.254.1.1"))&&!WatchNextArtwork.publicAddress(java.net.InetAddress.getByName("100.64.0.1"))&&!WatchNextArtwork.publicAddress(java.net.InetAddress.getByName("fc00::1")),"artwork downloads reject local, private, link-local and shared-address destinations");
        store.remove(record.key());WatchNextHistory.refresh();Thread.sleep(1000);check(store.list().isEmpty(),"a source refresh respects local removal tombstones");
        output.putString("passed",count+" Watch Next integration checks");finish(Activity.RESULT_OK,output);
    }catch(Throwable failure){output.putString("failed",failure.toString());finish(Activity.RESULT_CANCELED,output);}finally{if(owned)LegacyHistoryListener.release();}}
    private WatchNextHistory.ReadResult read(Context c,PlaybackRule rule)throws Exception{return read(c,rule,null);}
    private WatchNextHistory.ReadResult read(Context c,PlaybackRule rule,PlaybackRecord record)throws Exception{return WatchNextHistory.read(c,rule,new CancellationSignal(),SystemClock.elapsedRealtime()+7000,record);}
}
