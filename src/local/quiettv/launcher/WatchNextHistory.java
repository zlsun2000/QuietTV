package local.quiettv.launcher;

import android.content.*;
import android.content.pm.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import org.json.*;
import java.util.*;

/** Opt-in system Watch Next adapter. Native links exist only during a bounded read.
 * Stored records contain a hashed publisher identity and a numeric row locator.
 * Clicking re-reads the provider and constructs a fresh, tightly scoped Intent.
 */
final class WatchNextHistory {
    private static final Uri TABLE=Uri.parse("content://android.media.tv/watch_next_program");
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String[] COLUMNS={"_id","package_name","internal_provider_id","title","episode_title","last_playback_position_millis","duration_millis","last_engagement_time_utc_millis","watch_next_type","intent_uri","poster_art_uri"};
    private static volatile WatchNextHistory current;
    private static boolean opening;
    static final class ReadResult {final ArrayList<PlaybackRecord> records=new ArrayList<>();final HashMap<String,String> artwork=new HashMap<>();Intent route;int rows;String status="empty";}
    static final class Candidate {final PlaybackRule rule;final PlaybackRecord record;final String artwork;Candidate(PlaybackRule r,PlaybackRecord value){this(r,value,"");}Candidate(PlaybackRule r,PlaybackRecord value,String art){rule=r;record=value;artwork=art;}}
    private final Context context;
    private final MediaArtwork artwork;
    private boolean registered,busy,again,disposed;
    private CancellationSignal cancellation;
    private final Runnable capture=()->scan();
    private final ContentObserver observer=new ContentObserver(MAIN){@Override public void onChange(boolean self){schedule();}};
    private final SharedPreferences.OnSharedPreferenceChangeListener choices=(prefs,key)->{if(Arrays.asList("enabled","generic_enabled","blocked_media_apps").contains(key))MAIN.post(()->refresh());};

    private WatchNextHistory(Context c){context=c.getApplicationContext();artwork=new MediaArtwork(context);context.getSharedPreferences("playback_history",0).registerOnSharedPreferenceChangeListener(choices);}
    static synchronized void start(Context c){if(current==null)current=new WatchNextHistory(c);refresh();}
    static synchronized void refresh(){if(current!=null)MAIN.post(()->{WatchNextHistory value=current;if(value!=null)value.configure();});}
    static synchronized void stop(){WatchNextHistory value=current;current=null;if(value==null)return;value.disposed=true;value.artwork.dispose();MAIN.removeCallbacks(value.capture);if(value.cancellation!=null)value.cancellation.cancel();value.unregister();value.context.getSharedPreferences("playback_history",0).unregisterOnSharedPreferenceChangeListener(value.choices);}
    private void unregister(){if(registered)try{context.getContentResolver().unregisterContentObserver(observer);}catch(Exception ignored){}registered=false;}
    private void configure(){
        if(disposed)return;boolean available=false;for(PlaybackRule rule:PlaybackRules.get(context).list())if(eligible(context,rule)){available=true;break;}
        if(!available){unregister();if(cancellation!=null)cancellation.cancel();return;}
        if(!registered)try{context.getContentResolver().registerContentObserver(TABLE,true,observer);registered=true;}catch(Exception ignored){}
        schedule();
    }
    private void schedule(){if(disposed)return;MAIN.removeCallbacks(capture);MAIN.postDelayed(capture,500);}
    private void scan(){
        if(disposed)return;if(busy){again=true;return;}busy=true;cancellation=new CancellationSignal();final CancellationSignal cancel=cancellation;
        final Runnable timeout=()->cancel.cancel();MAIN.postDelayed(timeout,8000);
        new Thread(()->{
            ArrayList<Candidate> records=new ArrayList<>();PlaybackRules registry=PlaybackRules.get(context);long deadline=SystemClock.elapsedRealtime()+7000;
            try{for(PlaybackRule rule:registry.list())if(eligible(context,rule)&&SystemClock.elapsedRealtime()<deadline){
                ReadResult found=read(context,rule,cancel,deadline,null);for(PlaybackRecord record:found.records)records.add(new Candidate(rule,record,found.artwork.get(record.key())));registry.note(rule,found.records.isEmpty()?"系统暂未提供匹配的继续观看记录":"已读取系统继续观看记录；点击时重新校验入口");
            }}catch(Exception ignored){}
            MAIN.post(()->{MAIN.removeCallbacks(timeout);if(!disposed&&!cancel.isCanceled())commit(context,records);busy=false;if(!disposed&&again){again=false;schedule();}});
        },"QuietTV-watch-next").start();
    }
    static String availability(Context c){
        if(Build.VERSION.SDK_INT<26)return "系统不支持 Watch Next";
        UserManager users=(UserManager)c.getSystemService(Context.USER_SERVICE);if(users==null||!users.isUserUnlocked())return "等待用户解锁";
        if(c.checkSelfPermission(TvHistoryInspection.READ)!=PackageManager.PERMISSION_GRANTED)return "需要授权电视节目表读取权限";
        ProviderInfo provider=c.getPackageManager().resolveContentProvider("android.media.tv",0);
        if(provider==null||provider.applicationInfo==null||(provider.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return "没有可验证的系统电视节目表";
        return "";
    }
    static boolean eligible(Context c,PlaybackRule rule){
        if(rule==null||!rule.adapter.equals("watch_next")||!availability(c).isEmpty())return false;
        PlaybackRules registry=PlaybackRules.get(c);PlaybackRule installed=registry.find(rule.id);HistoryStore store=new HistoryStore(c);
        return installed!=null&&installed.revision==rule.revision&&installed.json.toString().equals(rule.json.toString())&&registry.enabled(installed)&&registry.targetStatus(installed).isEmpty()&&store.enabled()&&store.genericEnabled()&&store.appEnabled(rule.pkg);
    }
    static void validate(JSONObject spec)throws Exception {
        PlaybackRule.keys(spec,"types,intent,artwork");if(spec.has("artwork"))WatchNextArtwork.validate(spec.getJSONObject("artwork"));JSONArray types=spec.getJSONArray("types");PlaybackRule.require(types.length()>0&&types.length()<=4,"Watch Next 类型数量不正确");HashSet<String> seen=new HashSet<>();for(int i=0;i<types.length();i++)PlaybackRule.require(Arrays.asList("continue","next","new","watchlist").contains(types.getString(i))&&seen.add(types.getString(i)),"Watch Next 类型不正确或重复");
        JSONObject intent=spec.getJSONObject("intent");PlaybackRule.keys(intent,"action,component,data");PlaybackRule.require(PlaybackRule.action(PlaybackRule.string(intent,"action",160)),"系统入口 action 不正确");
        if(intent.has("component"))PlaybackRule.require(PlaybackRule.string(intent,"component",240).matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"),"系统入口组件不正确");
        JSONObject data=intent.getJSONObject("data");PlaybackRule.keys(data,"scheme,host,path,queryKeys");String scheme=PlaybackRule.string(data,"scheme",40);PlaybackRule.require(scheme.matches("[a-z][a-z0-9+.-]*")&&!Arrays.asList("file","content","javascript","data","intent","http").contains(scheme),"不允许的系统入口 scheme");
        PlaybackRule.require(PlaybackRule.string(data,"host",160).matches("[a-zA-Z0-9.-]+"),"系统入口 host 不正确");JSONArray path=data.getJSONArray("path");PlaybackRule.require(path.length()<=8,"系统入口路径过长");for(int i=0;i<path.length();i++){String part=path.getString(i);PlaybackRule.require(part.equals("*")||part.matches("[A-Za-z0-9_-]{1,80}"),"系统入口路径须为固定段或单段通配符");}
        JSONArray query=data.getJSONArray("queryKeys");PlaybackRule.require(query.length()<=20,"系统入口参数过多");seen.clear();for(int i=0;i<query.length();i++){String key=query.getString(i);PlaybackRule.require(PlaybackRule.key(key)&&!key.matches("(?i).*(password|token|cookie|authorization|credential|play.?url|stream.?url|media.?url).*")&&seen.add(key),"系统入口参数不正确或重复");}
    }
    /** No source flags, selector, extras, categories or arbitrary component are forwarded. */
    static Intent nativeIntent(Context c,PlaybackRule rule,String raw)throws Exception {
        PlaybackRule.require(raw!=null&&!raw.isEmpty()&&raw.length()<=4096,"系统入口缺失或过长");
        Intent source=Intent.parseUri(raw,0);JSONObject spec=rule.json.getJSONObject("watchNext").getJSONObject("intent"),data=spec.getJSONObject("data");
        PlaybackRule.require(spec.getString("action").equals(source.getAction())&&source.getSelector()==null&&source.getClipData()==null&&source.getType()==null&&(source.getExtras()==null||source.getExtras().isEmpty()),"系统入口与声明不匹配");
        PlaybackRule.require(source.getPackage()==null||rule.pkg.equals(source.getPackage()),"系统入口包不匹配");
        ComponentName component=source.getComponent();PlaybackRule.require(component==null||rule.pkg.equals(component.getPackageName())&&spec.has("component")&&component.getClassName().equals(spec.getString("component")),"系统入口组件不匹配");
        Uri uri=source.getData();PlaybackRule.require(uri!=null&&uri.isHierarchical()&&data.getString("scheme").equals(uri.getScheme())&&data.getString("host").equals(uri.getHost())&&data.getString("host").equals(uri.getEncodedAuthority())&&uri.getFragment()==null,"系统入口 URI 不匹配");
        JSONArray expected=data.getJSONArray("path");List<String> parts=uri.getPathSegments();PlaybackRule.require(expected.length()==parts.size(),"系统入口路径不匹配");Uri.Builder fresh=new Uri.Builder().scheme(data.getString("scheme")).authority(data.getString("host"));
        for(int i=0;i<expected.length();i++){String part=parts.get(i);PlaybackRule.require(expected.getString(i).equals(part)||expected.getString(i).equals("*")&&PlaybackRule.token(part)&&!part.contains("/"),"系统入口路径不匹配");fresh.appendPath(part);}
        JSONArray keys=data.getJSONArray("queryKeys");Set<String> actual=uri.getQueryParameterNames();PlaybackRule.require(actual.size()==keys.length(),"系统入口参数不匹配");
        String encoded=uri.getEncodedQuery();PlaybackRule.require((encoded==null?0:encoded.split("&",-1).length)==keys.length(),"系统入口参数重复或缺失");
        for(int i=0;i<keys.length();i++){String key=keys.getString(i);List<String> values=uri.getQueryParameters(key);PlaybackRule.require(actual.contains(key)&&values.size()==1&&!values.get(0).isEmpty()&&values.get(0).length()<=3072&&!values.get(0).matches("(?s).*[\\p{Cntrl}].*"),"系统入口参数不匹配");}
        // Preserve the encoded opaque publisher parameter only for this dispatch.
        if(encoded!=null)fresh.encodedQuery(encoded);
        Intent result=new Intent(spec.getString("action")).setPackage(rule.pkg).setData(fresh.build()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(spec.has("component"))result.setComponent(new ComponentName(rule.pkg,spec.getString("component")));
        ResolveInfo resolved=c.getPackageManager().resolveActivity(result,0);PlaybackRule.require(resolved!=null&&resolved.activityInfo!=null&&resolved.activityInfo.enabled&&resolved.activityInfo.exported&&rule.pkg.equals(resolved.activityInfo.packageName)&&(resolved.activityInfo.permission==null||c.checkSelfPermission(resolved.activityInfo.permission)==PackageManager.PERMISSION_GRANTED),"系统入口不可用");return result;
    }
    private static boolean accepts(PlaybackRule rule,int type)throws Exception {JSONArray types=rule.json.getJSONObject("watchNext").getJSONArray("types");String[] names={"continue","next","new","watchlist"};if(type<0||type>=names.length)return false;for(int i=0;i<types.length();i++)if(names[type].equals(types.getString(i)))return true;return false;}
    /** Package-filtered, newest first, at most 32 rows. Never persists intent_uri. */
    static ReadResult read(Context c,PlaybackRule rule,CancellationSignal cancel,long deadline,PlaybackRecord selected)throws Exception {
        ReadResult result=new ReadResult();if(!eligible(c,rule))return result;
        Uri uri=TABLE.buildUpon().appendQueryParameter("package",rule.pkg).build();
        try(Cursor cursor=c.getContentResolver().query(uri,COLUMNS,null,null,"last_engagement_time_utc_millis DESC",cancel)){
            int rows=0;while(cursor!=null&&rows++<32&&SystemClock.elapsedRealtime()<deadline&&cursor.moveToNext()){
                cancel.throwIfCanceled();result.rows++;if(!rule.pkg.equals(cursor.getString(1)))throw new SecurityException("Provider scope mismatch");
                try{
                    result.status="type_not_selected";if(cursor.isNull(8)||!accepts(rule,cursor.getInt(8)))continue;
                    result.status="publisher_identity_invalid";String publisher=cursor.getString(2),locator=cursor.getString(0);if(publisher==null||publisher.isEmpty()||publisher.length()>4096||!PlaybackRecord.numeric(locator))continue;
                    Map<String,Object> event=new HashMap<>();String hash=MediaRecordPolicy.digest(rule.pkg+"\n"+publisher);event.put("providerIdHash",hash);event.put("title",cursor.getString(3));event.put("episodeTitle",cursor.getString(4));
                    if(!cursor.isNull(5)&&cursor.getLong(5)>=0)event.put("position",cursor.getLong(5));if(!cursor.isNull(6)&&cursor.getLong(6)>0)event.put("duration",cursor.getLong(6));
                    Map<String,String> values=rule.extract(event,false);if(rule.route(values)==null)continue;
                    PlaybackRecord record=PlaybackRules.record(c,rule,values);record.command="rule/watch_next";record.contentId=hash;record.routeId=locator;
                    result.status="engagement_time_invalid";long engagement=cursor.getLong(7);if(engagement<=0||engagement>System.currentTimeMillis()+300000)continue;record.updated=engagement;record.startedAt=engagement;
                    result.status="record_invalid";if(!record.valid())continue;
                    if(selected!=null&&(!selected.key().equals(record.key())||!selected.contentId.equals(hash)||!selected.ruleData.equals(record.ruleData)))continue;
                    result.status="route_mismatch";Intent route=nativeIntent(c,rule,cursor.getString(9));
                    if(selected!=null){result.route=route;return result;}
                    result.records.add(record);String art=cursor.getString(10);if(rule.json.getJSONObject("watchNext").has("artwork")&&art!=null&&art.length()<=1500)result.artwork.put(record.key(),art);result.status="matched";
                }catch(PlaybackRule.Invalid invalid){result.status=invalid.getMessage();}catch(Exception ignored){result.status="unavailable_field_or_route";}
            }
        }return result;
    }
    static void commit(Context c,List<Candidate> records){
        boolean changed=false;HistoryStore store=new HistoryStore(c);PlaybackRules registry=PlaybackRules.get(c);
        for(Candidate candidate:records)try{PlaybackRecord record=candidate.record;PlaybackRule rule=candidate.rule;if(!eligible(c,rule))continue;
            PlaybackRecord previous=null;for(PlaybackRecord old:store.list())if(old.key().equals(record.key())){previous=old;break;}
            if(previous!=null&&previous.updated>record.updated)continue;
            if(current!=null&&candidate.artwork!=null&&!candidate.artwork.isEmpty())current.artwork.captureWatchNext(record,rule,candidate.artwork);
            if(previous!=null&&previous.updated==record.updated&&previous.position==record.position&&previous.positionKnown==record.positionKnown&&previous.duration==record.duration&&previous.title.equals(record.title)&&previous.episode.equals(record.episode)&&previous.routeId.equals(record.routeId))continue;
            record.startedAt=PlaybackRules.started(c,record);changed|=store.put(record);
        }catch(Exception ignored){}
        if(changed)GenericMediaHistory.changed(c);
    }
    static void open(Context context,PlaybackRule rule,PlaybackRecord selected){
        if(opening)return;opening=true;Context c=context.getApplicationContext();CancellationSignal cancel=new CancellationSignal();Runnable timeout=()->cancel.cancel();MAIN.postDelayed(timeout,8000);
        new Thread(()->{
            Intent resolved=null;try{resolved=read(c,rule,cancel,SystemClock.elapsedRealtime()+7000,selected).route;}catch(Exception ignored){}
            final Intent route=resolved;MAIN.post(()->{opening=false;MAIN.removeCallbacks(timeout);PlaybackRules registry=PlaybackRules.get(c);
                if(!eligible(c,rule)){Toast.makeText(c,"规则或权限已改变，请重新选择记录",Toast.LENGTH_LONG).show();return;}
                boolean present=false;for(PlaybackRecord record:new HistoryStore(c).visible())if(record.key().equals(selected.key()))present=true;if(!present)return;
                try{if(route!=null&&!cancel.isCanceled()){c.startActivity(route);registry.note(rule,"已发送系统继续观看入口；播放进度由原应用决定");return;}}catch(Exception ignored){}
                registry.note(rule,"系统记录已变化或入口不可用，回退打开应用");try{Intent app=GenericMediaHistory.launch(c,rule.pkg);if(app!=null)c.startActivity(app);}catch(Exception ignored){}Toast.makeText(c,"续播入口不可用，已尝试打开应用",Toast.LENGTH_LONG).show();
            });
        },"QuietTV-watch-next-open").start();
    }
}
