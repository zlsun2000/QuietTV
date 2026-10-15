package local.quiettv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.SystemClock;
import org.json.*;

/** Explicit ADB-only diagnosis. Stores counts, never titles, IDs, URIs or progress values. */
final class TvHistoryInspection {
    static final String READ="android.permission.READ_TV_LISTINGS";
    private static final int LIMIT=32;

    static JSONObject inspect(Context c,String pkg,CancellationSignal cancel)throws Exception {
        JSONObject result=new JSONObject().put("schema",1);
        if(pkg==null||!MediaRecordPolicy.packageName(pkg)||pkg.equals(c.getPackageName()))return result.put("status","invalid_package");
        result.put("package",pkg);
        PackageInfo target;
        try{target=c.getPackageManager().getPackageInfo(pkg,PackageManager.GET_PROVIDERS);}catch(PackageManager.NameNotFoundException e){return result.put("status","target_missing");}
        result.put("targetVersionCode",android.os.Build.VERSION.SDK_INT>=28?target.getLongVersionCode():target.versionCode);
        JSONArray providers=new JSONArray();
        if(target.providers!=null)for(ProviderInfo p:target.providers){
            if(providers.length()>=16)break;
            providers.put(new JSONObject().put("authority",p.authority).put("exported",p.exported).put("readPermission",p.readPermission==null?JSONObject.NULL:p.readPermission).put("writePermission",p.writePermission==null?JSONObject.NULL:p.writePermission));
        }
        result.put("declaredProviders",providers);
        boolean permission=c.checkSelfPermission(READ)==PackageManager.PERMISSION_GRANTED;
        result.put("readTvListingsGranted",permission);
        HistoryStore history=new HistoryStore(c);
        if(!history.enabled()||!history.genericEnabled()||!history.appEnabled(pkg))return result.put("status","history_disabled");
        if(!permission)return result.put("status","read_permission_required");
        ProviderInfo system=c.getPackageManager().resolveContentProvider("android.media.tv",0);
        if(system==null)return result.put("status","provider_missing");
        if(system.applicationInfo==null||(system.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return result.put("status","provider_unverified");
        result.put("providerPackage",system.packageName);
        long deadline=SystemClock.elapsedRealtime()+7000;
        result.put("watchNext",query(c,pkg,"watch_next_program",true,cancel,deadline));
        result.put("previewPrograms",query(c,pkg,"preview_program",false,cancel,deadline));
        JSONArray adapters=new JSONArray();for(PlaybackRule rule:PlaybackRules.get(c).list())if(rule.pkg.equals(pkg)&&rule.adapter.equals("watch_next")&&PlaybackRules.get(c).enabled(rule)){
            JSONObject adapter=new JSONObject().put("rule",rule.id).put("eligible",WatchNextHistory.eligible(c,rule));
            if(WatchNextHistory.eligible(c,rule)){WatchNextHistory.ReadResult read=WatchNextHistory.read(c,rule,cancel,deadline,null);adapter.put("rowsInspected",read.rows).put("matched",read.records.size()).put("status",read.status);}adapters.put(adapter);
        }result.put("adapters",adapters);return result.put("status","inspected");
    }

    private static JSONObject query(Context c,String pkg,String table,boolean watch,CancellationSignal cancel,long deadline)throws Exception {
        JSONObject report=new JSONObject();
        String[] columns=watch?new String[]{"package_name","internal_provider_id","intent_uri","last_playback_position_millis","duration_millis","watch_next_type","poster_art_uri","thumbnail_uri"}:new String[]{"package_name","internal_provider_id","intent_uri","last_playback_position_millis","duration_millis"};
        Uri uri=new Uri.Builder().scheme("content").authority("android.media.tv").appendPath(table).appendQueryParameter("package",pkg).build();
        int rows=0,ids=0,numeric=0,routes=0,positions=0,durations=0,continuing=0,continuingRoutes=0;JSONArray shapes=new JSONArray();java.util.HashSet<String> seen=new java.util.HashSet<>();
        try(Cursor cursor=c.getContentResolver().query(uri,columns,null,null,null,cancel)){
            if(cursor==null)return report.put("status","no_cursor");
            while(rows<LIMIT&&SystemClock.elapsedRealtime()<deadline&&cursor.moveToNext()){
                cancel.throwIfCanceled();
                if(!pkg.equals(cursor.getString(0)))return new JSONObject().put("status","provider_scope_mismatch");
                rows++;
                String id=cursor.getString(1);boolean safe=id!=null&&id.length()<=128&&id.matches("[A-Za-z0-9][A-Za-z0-9_./~-]{0,127}")&&!id.contains("..");
                if(safe)ids++;if(id!=null&&id.matches("[1-9][0-9]{0,17}"))numeric++;
                boolean owned=ownedRoute(c,pkg,cursor.getString(2));if(owned)routes++;
                if(owned&&shapes.length()<4){JSONObject shape=routeShape(cursor.getString(2));shape.put("providerIdType",valueShape(id));if(seen.add(shape.toString()))shapes.put(shape);}
                if(!cursor.isNull(3)&&cursor.getLong(3)>=0)positions++;
                if(!cursor.isNull(4)&&cursor.getLong(4)>0)durations++;
                if(watch&&shapes.length()>0){JSONObject artwork=new JSONObject();for(int col=6;col<=7;col++){String art=cursor.getString(col);if(art==null||art.length()>4096)continue;android.net.Uri source=android.net.Uri.parse(art);JSONObject kind=new JSONObject().put("scheme",source.getScheme()).put("host",source.getHost()).put("length",art.length()).put("hasQuery",source.getQuery()!=null).put("hasFragment",source.getFragment()!=null).put("port",source.getPort());JSONArray queryKeys=new JSONArray();if(source.isHierarchical())for(String key:source.getQueryParameterNames())if(queryKeys.length()<20&&safeKey(key))queryKeys.put(key);kind.put("queryKeys",queryKeys);if("content".equals(source.getScheme())){ProviderInfo owner=c.getPackageManager().resolveContentProvider(source.getAuthority(),0);kind.put("ownedProvider",owner!=null&&pkg.equals(owner.packageName));}artwork.put(col==6?"poster":"thumbnail",kind);}shapes.getJSONObject(shapes.length()-1).put("artwork",artwork);}
                if(watch&&!cursor.isNull(5)&&cursor.getInt(5)==0){continuing++;if(safe&&owned)continuingRoutes++;}
            }
            report.put("status","ok").put("rowsInspected",rows).put("boundedIds",ids).put("numericIds",numeric).put("resolvableOwnedRoutes",routes).put("rowsWithPositionValue",positions).put("rowsWithDuration",durations).put("limitReached",rows==LIMIT||SystemClock.elapsedRealtime()>=deadline).put("routeShapes",shapes);
            if(watch)report.put("continueRows",continuing).put("continueRowsWithIdAndRoute",continuingRoutes);
        }catch(SecurityException e){report.put("status","permission_denied");}
        catch(android.os.OperationCanceledException e){report.put("status","cancelled");}
        catch(Exception e){report.put("status","unavailable");}
        return report;
    }

    /** Only interface names and redacted value shapes leave the process. */
    private static JSONObject routeShape(String raw)throws Exception {
        Intent intent=Intent.parseUri(raw,0);JSONObject shape=new JSONObject();
        shape.put("action",intent.getAction()).put("package",intent.getPackage()).put("component",intent.getComponent()==null?JSONObject.NULL:intent.getComponent().flattenToShortString()).put("flags",intent.getFlags());
        Uri uri=intent.getData();if(uri!=null){JSONObject data=new JSONObject().put("scheme",uri.getScheme()).put("host",uri.getHost()).put("port",uri.getPort()).put("hasFragment",uri.getFragment()!=null).put("authorityIsHost",uri.getHost()!=null&&uri.getHost().equals(uri.getEncodedAuthority()));JSONArray path=new JSONArray();for(String part:uri.getPathSegments()){if(path.length()>=8)break;path.put(java.util.Arrays.asList("watch","title","browse","deeplink","play","video","videos","details").contains(part)?part:valueShape(part));}data.put("path",path);JSONObject query=new JSONObject();if(uri.isHierarchical())for(String key:uri.getQueryParameterNames())if(query.length()<20&&safeKey(key))query.put(key,valueShape(uri.getQueryParameter(key)));data.put("query",query);shape.put("data",data);}
        JSONObject extras=new JSONObject();android.os.Bundle bundle=intent.getExtras();if(bundle!=null)for(String key:bundle.keySet())if(extras.length()<20&&safeKey(key))extras.put(key,valueShape(bundle.get(key)));return shape.put("extras",extras);
    }
    private static boolean safeKey(String key){return key!=null&&key.matches("[A-Za-z][A-Za-z0-9_.-]{0,79}")&&!key.matches("(?i).*(password|token|cookie|authorization|credential|play.?url|stream.?url|media.?url).*");}
    private static Object valueShape(Object raw)throws Exception {
        if(raw==null||raw==JSONObject.NULL)return "{absent}";
        if(raw instanceof Boolean)return "{boolean}";if(raw instanceof Number)return "{number}";
        if(!(raw instanceof String))return "{unsupported}";String value=(String)raw;if(value.isEmpty())return "{empty}";
        if(value.matches("[0-9]{1,18}"))return "{numeric}";
        if(value.length()<=4096&&value.trim().startsWith("{"))try{PlaybackRule.boundedJson(value,8);JSONObject input=new JSONObject(value),keys=new JSONObject();java.util.Iterator<String> names=input.keys();while(names.hasNext()&&keys.length()<16){String key=names.next();if(safeKey(key)){Object child=input.opt(key);keys.put(key,child instanceof String&&((String)child).trim().startsWith("{")?"{json}":child instanceof Number?"{number}":child instanceof Boolean?"{boolean}":child==null||child==JSONObject.NULL?"{absent}":"{string}");}}return new JSONObject().put("jsonKeys",keys);}catch(Exception ignored){}
        return value.matches("[A-Za-z][A-Za-z0-9+.-]{0,30}:.*")?"{uri_or_urn}":PlaybackRule.token(value)?"{token}":"{text}";
    }

    private static boolean ownedRoute(Context c,String pkg,String raw){
        if(raw==null||raw.isEmpty()||raw.length()>4096)return false;
        try{
            Intent intent=Intent.parseUri(raw,0);
            if(intent.getPackage()!=null&&!pkg.equals(intent.getPackage())||intent.getComponent()!=null&&!pkg.equals(intent.getComponent().getPackageName()))return false;
            intent.setPackage(pkg);
            ResolveInfo info=c.getPackageManager().resolveActivity(intent,0);
            return info!=null&&info.activityInfo!=null&&info.activityInfo.enabled&&info.activityInfo.exported&&pkg.equals(info.activityInfo.packageName)&&(info.activityInfo.permission==null||c.checkSelfPermission(info.activityInfo.permission)==PackageManager.PERMISSION_GRANTED);
        }catch(Exception e){return false;}
    }
    private TvHistoryInspection(){}
}
