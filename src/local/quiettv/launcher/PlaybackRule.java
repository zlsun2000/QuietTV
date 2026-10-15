package local.quiettv.launcher;

import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.*;

/** Bounded declarative mappings. Rules never execute code or forward received Intents. */
final class PlaybackRule {
    static final int MAX_BYTES=65536;
    static final String BILI="quiettv.bili-tcl",QIYI="quiettv.qiyi-tcl";
    static final Set<String> ROUTE_FIELDS=new HashSet<>(Arrays.asList("id","episodeId","position","duration","variant","channel"));
    final JSONObject json,target,fields;
    final String id,name,pkg,adapter,addAction,deleteAction;
    final int revision;
    final JSONArray routes,identity;
    final boolean builtin;

    static final class Invalid extends Exception {Invalid(String message){super(message);}}
    PlaybackRule(String raw,boolean bundled)throws Exception {
        require(raw!=null&&raw.getBytes("UTF-8").length<=MAX_BYTES,"规则文件超过 64 KB");
        boundedJson(raw,16);json=new JSONObject(raw);builtin=bundled;
        keys(json,"schema,id,name,revision,author,license,description,verification,target,adapter,event,watchNext,fields,identity,routes");
        require(integer(json,"schema",-1)==1,"不支持的规则规范版本");
        id=string(json,"id",80);require(id.matches("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+"),"规则 ID 格式不正确");
        name=string(json,"name",120);long version=integer(json,"revision",0);require(version>0&&version<=Integer.MAX_VALUE,"revision 必须为正整数");revision=(int)version;
        string(json,"author",160);string(json,"license",80);optionalText(json,"description",600);
        JSONObject verification=json.getJSONObject("verification");keys(verification,"status,notes");
        require(Arrays.asList("unverified","historical","emulator","device").contains(verification.getString("status")),"验证状态不正确");string(verification,"notes",600);
        target=json.getJSONObject("target");keys(target,"package,minVersionCode,maxVersionCode,certSha256,minSdk,maxSdk");
        pkg=string(target,"package",160);require(MediaRecordPolicy.packageName(pkg)&&!pkg.equals("local.quiettv.launcher"),"目标包名不正确");
        long min=integer(target,"minVersionCode",0),max=integer(target,"maxVersionCode",Long.MAX_VALUE);require(min>=0&&max>=min,"应用版本范围不正确");
        require(integer(target,"minSdk",23)>=23&&integer(target,"maxSdk",1000)>=integer(target,"minSdk",23)&&integer(target,"maxSdk",1000)<=1000,"系统版本范围不正确");
        if(target.has("certSha256")){JSONArray certs=target.getJSONArray("certSha256");require(certs.length()>0&&certs.length()<=4,"签名数量不正确");for(int i=0;i<certs.length();i++)require(certs.getString(i).matches("[a-f0-9]{64}"),"签名应为小写 SHA-256");}
        adapter=string(json,"adapter",32);require(Arrays.asList("broadcast","media_session","watch_next").contains(adapter),"未知接入适配器");
        if(adapter.equals("watch_next")){WatchNextHistory.validate(json.getJSONObject("watchNext"));if(json.getJSONObject("watchNext").has("artwork")&&json.getJSONObject("watchNext").getJSONObject("artwork").getJSONArray("httpsHosts").length()>0)require(target.has("certSha256"),"联网封面必须限定目标应用签名");}else require(!json.has("watchNext"),"此适配器不支持 Watch Next 声明");
        String add="",del="";
        if(adapter.equals("broadcast")){
            JSONObject event=json.getJSONObject("event");keys(event,"add,delete,sourceFields,sourceValues");
            add=string(event,"add",160);require(action(add),"广播 action 不正确");
            if(event.has("delete")){del=string(event,"delete",160);require(action(del)&&!del.equals(add),"删除广播 action 不正确");}
            JSONArray sourceFields=event.getJSONArray("sourceFields"),values=event.getJSONArray("sourceValues");
            require(sourceFields.length()>0&&sourceFields.length()<=4&&values.length()>0&&values.length()<=4,"广播来源声明不完整");
            for(int i=0;i<sourceFields.length();i++)require(key(sourceFields.getString(i)),"来源字段不正确");
            for(int i=0;i<values.length();i++){String value=values.getString(i);require(value.equals(pkg)||value.startsWith(pkg+"."),"来源声明必须属于目标应用");}
        }else require(!json.has("event"),"媒体会话规则无需广播声明");
        addAction=add;deleteAction=del;fields=json.getJSONObject("fields");require(fields.length()<=8,"字段过多");
        keys(fields,"id,episodeId,title,subtitle,position,duration,variant,channel");require(fields.has("id")&&fields.has("title"),"缺少 id/title 映射");
        Iterator<String> names=fields.keys();while(names.hasNext())validateField(names.next());
        if(adapter.equals("watch_next")){
            require(fields.length()<=5,"Watch Next 只支持系统公开的五个规范字段");
            String[][] mapping={{"id","providerIdHash"},{"title","title"},{"subtitle","episodeTitle"},{"position","position"},{"duration","duration"}};
            HashSet<String> allowed=new HashSet<>();for(String[] item:mapping){allowed.add(item[0]);if(fields.has(item[0])){JSONObject spec=fields.getJSONObject(item[0]);keys(spec,"key,type,required,unit");require(spec.getString("key").equals(item[1]),"Watch Next 字段须使用规范键");if(item[0].equals("id"))require(spec.getString("type").equals("id"),"系统内容身份使用摘要");if(spec.has("unit"))require(spec.getString("unit").equals("ms"),"系统时间单位为 ms");}}
            Iterator<String> nativeFields=fields.keys();while(nativeFields.hasNext())require(allowed.contains(nativeFields.next()),"不支持的 Watch Next 字段");
        }
        identity=json.getJSONArray("identity");require(identity.length()>0&&identity.length()<=4,"内容标识不完整");
        HashSet<String> ids=new HashSet<>();for(int i=0;i<identity.length();i++){String field=identity.getString(i);require(Arrays.asList("id","episodeId","variant","channel").contains(field)&&fields.has(field)&&ids.add(field),"内容标识字段不正确或重复");}
        require(ids.contains("id"),"内容标识必须包含 id");
        routes=json.getJSONArray("routes");require(routes.length()>0&&routes.length()<=8,"续播入口数量不正确");
        for(int i=0;i<routes.length();i++)validateRoute(routes.getJSONObject(i));
    }
    private void validateField(String name)throws Exception {
        JSONObject spec=fields.getJSONObject(name);keys(spec,"key,jsonKey,equalsKey,equalsJsonKey,type,required,default,values,unit");
        require(key(string(spec,"key",160)),"字段名不正确");if(spec.has("jsonKey"))require(key(string(spec,"jsonKey",80)),"JSON 字段名不正确");
        if(spec.has("equalsKey"))require(key(string(spec,"equalsKey",160)),"交叉校验字段不正确");if(spec.has("equalsJsonKey"))require(spec.has("equalsKey")&&key(string(spec,"equalsJsonKey",80)),"交叉校验 JSON 字段不正确");
        String type=spec.getString("type");
        require((name.equals("position")||name.equals("duration"))?type.equals("time"):(name.equals("title")||name.equals("subtitle"))?type.equals("text"):Arrays.asList("id","numeric","enum").contains(type),"字段类型不正确："+name);
        if(type.equals("time"))require(Arrays.asList("ms","s").contains(spec.getString("unit")),"时间单位必须为 ms 或 s");else require(!spec.has("unit"),"非时间字段不能指定单位");
        if(spec.has("required"))require(spec.get("required") instanceof Boolean,"required 必须为布尔值");
        if(type.equals("enum")){JSONArray values=spec.getJSONArray("values");require(values.length()>0&&values.length()<=12,"枚举值数量不正确");for(int i=0;i<values.length();i++)require(token(values.getString(i)),"枚举值不正确");}
        else require(!spec.has("values"),"只有枚举可指定 values");
        if(spec.has("default"))normalize(name,spec,spec.get("default"));
        if(name.equals("id")||name.equals("title"))require(spec.optBoolean("required"),"id/title 必须为必填");
        if(!Arrays.asList("id","title","subtitle","position","duration").contains(name))require(!type.equals("text"),"路由字段需要有界标识");
    }
    private void validateRoute(JSONObject route)throws Exception {
        keys(route,"when,requires,capability,intent");String capability=route.getString("capability");require(adapter.equals("watch_next")?capability.equals("native"):capability.equals("content")||capability.equals("position"),"入口能力不正确");
        if(route.has("when")){JSONObject when=route.getJSONObject("when");require(when.length()<=4,"条件过多");Iterator<String> keys=when.keys();while(keys.hasNext()){String field=keys.next();require(ROUTE_FIELDS.contains(field)&&fields.has(field),"条件字段不正确");JSONArray values=when.getJSONArray(field);require(values.length()>0&&values.length()<=12,"条件值数量不正确");for(int i=0;i<values.length();i++)require(token(values.getString(i)),"条件值不正确");}}
        if(route.has("requires")){JSONArray required=route.getJSONArray("requires");require(required.length()<=6,"必需字段过多");for(int i=0;i<required.length();i++)require(ROUTE_FIELDS.contains(required.getString(i))&&fields.has(required.getString(i)),"必需字段未定义");}
        if(adapter.equals("watch_next")){require(!route.has("intent"),"系统入口由 Watch Next 声明限定，不能存入路由");return;}
        JSONObject intent=route.getJSONObject("intent");keys(intent,"action,component,data,extras");require(action(string(intent,"action",160)),"目标 action 不正确");
        // Channel application IDs may differ from their Java class namespace. The
        // ComponentName's package and PackageManager result enforce actual ownership.
        if(intent.has("component")){String component=string(intent,"component",240);require(component.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"),"组件必须为完整类名，不能包含包名路由");}
        if(intent.has("data")){
            JSONObject data=intent.getJSONObject("data");keys(data,"scheme,host,path,query");String scheme=string(data,"scheme",40);require(scheme.matches("[a-z][a-z0-9+.-]*")&&!Arrays.asList("file","content","javascript","data","intent","http").contains(scheme),"不允许的 URI scheme");
            require(string(data,"host",160).matches("[A-Za-z0-9.-]+"),"URI host 不正确");
            if(data.has("path")){JSONArray path=data.getJSONArray("path");require(path.length()<=8,"路径过长");for(int i=0;i<path.length();i++)validateValue(path.getJSONObject(i),false);}
            if(data.has("query"))validateValues(data.getJSONObject("query"),false);
        }
        if(intent.has("extras"))validateValues(intent.getJSONObject("extras"),true);
        require(intent.has("component")||intent.has("data")||intent.has("extras"),"入口没有组件或参数");
    }
    private void validateValues(JSONObject values,boolean nested)throws Exception {require(values.length()<=20,"参数过多");Iterator<String> names=values.keys();while(names.hasNext()){String name=names.next();require(key(name),"参数名不正确");require(!name.matches("(?i).*(password|access.?token|auth.?token|cookie|authorization|credential|play.?url|stream.?url|media.?url).*"),"规则不能包含凭据或播放 URL 参数");validateValue(values.getJSONObject(name),nested);}}
    private void validateValue(JSONObject spec,boolean nested)throws Exception {
        keys(spec,"type,field,value,unit,optional");String type=spec.getString("type");
        require(Arrays.asList("string","int","long","boolean","json").contains(type),"参数类型不正确");
        if(spec.has("optional"))require(spec.get("optional") instanceof Boolean,"optional 必须为布尔值");
        if(type.equals("json")){require(nested&&!spec.has("field")&&!spec.has("unit"),"JSON 参数不可嵌套或转换");validateValues(spec.getJSONObject("value"),false);return;}
        require(spec.has("field")!=spec.has("value"),"参数必须指定 field 或 value");
        if(spec.has("field")){String field=spec.getString("field");require(ROUTE_FIELDS.contains(field)&&fields.has(field),"参数引用了未知字段");if(spec.has("unit"))require((field.equals("position")||field.equals("duration"))&&Arrays.asList("ms","s").contains(spec.getString("unit")),"参数时间单位不正确");}
        else{require(!spec.has("unit"),"常量无需时间转换");typed(spec.get("value"),type);}
    }
    boolean sourceMatches(Map<String,Object> extras){
        try{JSONObject event=json.getJSONObject("event");JSONArray fields=event.getJSONArray("sourceFields"),values=event.getJSONArray("sourceValues");for(int i=0;i<fields.length();i++){Object raw=extras.get(fields.getString(i));if(!(raw instanceof String))continue;for(int j=0;j<values.length();j++)if(raw.equals(values.getString(j)))return true;}}catch(Exception ignored){}return false;
    }
    Map<String,String> extract(Map<String,Object> event,boolean deletion)throws Exception {
        LinkedHashMap<String,String> values=new LinkedHashMap<>();Iterator<String> names=fields.keys();
        while(names.hasNext()){
            String name=names.next();if(deletion&&!name.equals("id"))continue;JSONObject spec=fields.getJSONObject(name);Object raw=event.get(spec.getString("key"));
            if(spec.has("jsonKey")&&raw instanceof String){require(((String)raw).length()<=4096,"JSON 元数据过长");boundedJson((String)raw,8);raw=new JSONObject((String)raw).opt(spec.getString("jsonKey"));}
            if(raw==null||raw==JSONObject.NULL||"".equals(raw))raw=spec.opt("default");
            if(raw==null||raw==JSONObject.NULL||"".equals(raw)){require(!spec.optBoolean("required"),"缺少字段："+name);continue;}
            String normalized=normalize(name,spec,raw);
            if(!deletion&&spec.has("equalsKey")){Object check=event.get(spec.getString("equalsKey"));if(spec.has("equalsJsonKey")){require(check instanceof String&&((String)check).length()<=4096,"交叉校验字段缺失");boundedJson((String)check,8);check=new JSONObject((String)check).opt(spec.getString("equalsJsonKey"));}require(normalized.equals(normalize(name,spec,check)),"内容标识不一致");}
            values.put(name,normalized);
        }
        return values;
    }
    private static String normalize(String name,JSONObject spec,Object raw)throws Exception {
        require(raw instanceof String||raw instanceof Number,"字段类型不匹配："+name);String value=String.valueOf(raw).trim(),type=spec.getString("type");
        if(type.equals("time")){long time=Long.parseLong(value);require(time>=0&&time<=(spec.getString("unit").equals("s")?259200:259200000),"进度超出范围");return String.valueOf(spec.getString("unit").equals("s")?time*1000:time);}
        if(type.equals("text")){String text=MediaRecordPolicy.text(value,name.equals("title")?240:160);require(!text.isEmpty()||value.isEmpty(),"文本字段过长");return text;}
        require(type.equals("numeric")?PlaybackRecord.numeric(value):token(value),"内容标识不正确："+name);
        if(type.equals("enum")){boolean found=false;JSONArray allowed=spec.getJSONArray("values");for(int i=0;i<allowed.length();i++)found|=value.equals(allowed.getString(i));require(found,"不支持的枚举值："+name);}
        return value;
    }
    String identity(Map<String,String> values)throws Exception {StringBuilder key=new StringBuilder(pkg);for(int i=0;i<identity.length();i++){String name=identity.getString(i);key.append('\n').append(name).append('=').append(values.containsKey(name)?values.get(name):"");}return MediaRecordPolicy.digest(key.toString());}
    JSONObject route(Map<String,String> values)throws Exception {
        for(String name:ROUTE_FIELDS)if(values.containsKey(name)&&fields.has(name)&&!name.equals("position")&&!name.equals("duration"))normalize(name,fields.getJSONObject(name),values.get(name));
        for(int i=0;i<routes.length();i++){
            JSONObject route=routes.getJSONObject(i),when=route.optJSONObject("when");boolean matches=true;
            if(when!=null){Iterator<String> fields=when.keys();while(fields.hasNext()){String name=fields.next();JSONArray allowed=when.getJSONArray(name);boolean found=false;for(int j=0;j<allowed.length();j++)found|=allowed.getString(j).equals(values.get(name));matches&=found;}}
            JSONArray required=route.optJSONArray("requires");if(required!=null)for(int j=0;j<required.length();j++)matches&=values.containsKey(required.getString(j))&&!values.get(required.getString(j)).isEmpty();
            if(route.getString("capability").equals("position"))matches&=values.containsKey("position");
            if(matches)return route;
        }return null;
    }
    Intent intent(JSONObject route,Map<String,String> values)throws Exception {
        JSONObject spec=route.getJSONObject("intent");Intent intent=new Intent(spec.getString("action")).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(spec.has("component"))intent.setComponent(new ComponentName(pkg,spec.getString("component")));
        if(spec.has("data")){JSONObject data=spec.getJSONObject("data");Uri.Builder uri=new Uri.Builder().scheme(data.getString("scheme")).authority(data.getString("host"));JSONArray path=data.optJSONArray("path");if(path!=null)for(int i=0;i<path.length();i++){Object segment=value(path.getJSONObject(i),values);require(segment!=null,"路径缺少内容标识");uri.appendPath(String.valueOf(segment));}JSONObject query=data.optJSONObject("query");if(query!=null){Iterator<String> names=query.keys();while(names.hasNext()){String name=names.next();Object value=value(query.getJSONObject(name),values);if(value!=null)uri.appendQueryParameter(name,String.valueOf(value));}}intent.setData(uri.build());}
        JSONObject extras=spec.optJSONObject("extras");if(extras!=null){Iterator<String> names=extras.keys();while(names.hasNext()){String name=names.next();Object value=value(extras.getJSONObject(name),values);if(value instanceof String)intent.putExtra(name,(String)value);else if(value instanceof Integer)intent.putExtra(name,(Integer)value);else if(value instanceof Long)intent.putExtra(name,(Long)value);else if(value instanceof Boolean)intent.putExtra(name,(Boolean)value);}}
        return intent;
    }
    private Object value(JSONObject spec,Map<String,String> values)throws Exception {
        String type=spec.getString("type");if(type.equals("json")){JSONObject object=new JSONObject(),children=spec.getJSONObject("value");Iterator<String> names=children.keys();while(names.hasNext()){String name=names.next();Object value=value(children.getJSONObject(name),values);if(value!=null)object.put(name,value);}return object.toString();}
        Object raw=spec.opt("value");if(spec.has("field")){String field=spec.getString("field");raw=values.get(field);if(raw==null&&spec.optBoolean("optional"))return null;require(raw!=null,"续播缺少字段："+field);if(spec.optString("unit").equals("s"))raw=String.valueOf(Long.parseLong((String)raw)/1000);}
        return typed(raw,type);
    }
    private static Object typed(Object raw,String type)throws Exception {
        if(type.equals("string")){require(raw instanceof String||raw instanceof Number,"字符串参数类型不正确");String value=String.valueOf(raw);require(value.length()<=240&&!value.matches("(?s).*[\\p{Cntrl}].*"),"字符串参数过长或包含控制字符");require(!value.matches("(?is).*[a-z][a-z0-9+.-]{1,30}:.*"),"URI 应使用固定 scheme/host 和内容标识构造，不能内嵌 URL");return value;}
        if(type.equals("boolean")){require(raw instanceof Boolean||"true".equals(raw)||"false".equals(raw),"布尔参数不正确");return Boolean.valueOf(String.valueOf(raw));}
        require(raw instanceof Number||raw instanceof String,"数值参数不正确");long value=Long.parseLong(String.valueOf(raw));if(type.equals("long"))return value;require(type.equals("int")&&value>=Integer.MIN_VALUE&&value<=Integer.MAX_VALUE,"整数参数超出范围");return (int)value;
    }
    static boolean token(String value){return value!=null&&value.matches("[A-Za-z0-9][A-Za-z0-9_./~-]{0,127}")&&!value.contains(":")&&!value.contains("..");}
    static void boundedJson(String raw,int maximum)throws Invalid {boolean quoted=false,escaped=false;int depth=0;for(int i=0;i<raw.length();i++){char ch=raw.charAt(i);if(quoted){if(escaped)escaped=false;else if(ch=='\\')escaped=true;else if(ch=='"')quoted=false;}else if(ch=='"')quoted=true;else if(ch=='{'||ch=='['){depth++;require(depth<=maximum,"JSON 嵌套过深");}else if(ch=='}'||ch==']')depth--;}}
    static boolean key(String value){return value!=null&&value.matches("[A-Za-z_][A-Za-z0-9_.-]{0,159}");}
    static boolean action(String value){return key(value)&&value.contains(".");}
    static void require(boolean ok,String message)throws Invalid {if(!ok)throw new Invalid(message);}
    static void keys(JSONObject object,String allowed)throws Exception {Set<String> valid=new HashSet<>(Arrays.asList(allowed.split(",")));Iterator<String> names=object.keys();while(names.hasNext())require(valid.contains(names.next()),"发现不支持的配置字段");}
    static String string(JSONObject object,String key,int limit)throws Exception {Object value=object.get(key);require(value instanceof String&&!((String)value).trim().isEmpty()&&((String)value).length()<=limit,"缺少或过长的字段："+key);return ((String)value).trim();}
    static void optionalText(JSONObject object,String key,int limit)throws Exception {if(object.has(key))string(object,key,limit);}
    static long integer(JSONObject object,String key,long fallback)throws Exception {if(!object.has(key))return fallback;Object value=object.get(key);require(value instanceof Integer||value instanceof Long,"版本必须为整数");return ((Number)value).longValue();}
}
