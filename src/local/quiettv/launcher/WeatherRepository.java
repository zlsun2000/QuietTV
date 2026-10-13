package local.quiettv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cached city weather; refreshes only while the launcher is visible. */
final class WeatherRepository {
    interface Changed {void changed();}
    interface Cities {void found(ArrayList<Place> places,String error);}
    static final int TERMS_VERSION=20261011;
    static final long FRESH_MS=30*60*1000L;
    static final class Place {
        String name,area,country,zone;double latitude,longitude;
        String label(){return name+(area.isEmpty()||area.equals(name)?"":" · "+area)+(country.isEmpty()?"":" · "+country);}
        JSONObject json() throws Exception {JSONObject j=new JSONObject();j.put("name",name);j.put("area",area);j.put("country",country);j.put("zone",zone);j.put("lat",latitude);j.put("lon",longitude);return j;}
        static Place read(JSONObject j){Place p=new Place();p.name=j.optString("name","");p.area=j.optString("area","");p.country=j.optString("country","");p.zone=j.optString("zone","auto");p.latitude=j.optDouble("lat",Double.NaN);p.longitude=j.optDouble("lon",Double.NaN);return valid(p)?p:null;}
    }
    static final class Day {String date;int code;double low,high;}
    static final class Data {
        Place place;double temperature;int code;long updated;boolean manual;ArrayList<Day> days=new ArrayList<>();
        boolean stale(){long age=System.currentTimeMillis()-updated;return age<0||age>2*60*60*1000L;}
    }
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Changed changed;
    private Data data;
    private boolean active,busy,disposed;
    private int generation;
    private long retryAt,lastForceAt,lastSearchAt;
    private String message;
    private final Runnable timer=new Runnable(){public void run(){refresh(false);}};
    WeatherRepository(Context c,Changed callback){context=c.getApplicationContext();prefs=context.getSharedPreferences("weather",Context.MODE_PRIVATE);changed=callback;message=context.getString(R.string.weather_fetching);if(!prefs.getString("place","").isEmpty())try{data=read(new JSONObject(prefs.getString("cache","{}")));}catch(Exception ignored){}if(data==null)message=context.getString(R.string.weather_pending);}
    Data data(){return data;}
    boolean loading(){return busy;}
    String message(){return message;}
    boolean enabled(){return prefs.getBoolean("enabled",false)&&consented();}
    boolean fahrenheit(){return prefs.getBoolean("fahrenheit",false);}
    void fahrenheit(boolean enabled){prefs.edit().putBoolean("fahrenheit",enabled).apply();changed.changed();}
    String unitLabel(){return context.getString(fahrenheit()?R.string.temperature_fahrenheit:R.string.temperature_celsius);}
    String degrees(double celsius){double value=fahrenheit()?fahrenheitValue(celsius):celsius;return Math.round(value)+"°"+(fahrenheit()?"F":"C");}
    String conditionLabel(int code){int id=code==0?R.string.weather_condition_clear:code==1?R.string.weather_condition_mainly_clear:code==2?R.string.weather_condition_partly_cloudy:code==3?R.string.weather_condition_overcast:code==45||code==48?R.string.weather_condition_fog:code>=51&&code<=57?R.string.weather_condition_drizzle:code>=61&&code<=67?R.string.weather_condition_rain:code>=71&&code<=77?R.string.weather_condition_snow:code>=80&&code<=82?R.string.weather_condition_showers:code==85||code==86?R.string.weather_condition_snow_showers:code>=95&&code<=99?R.string.weather_condition_thunderstorm:R.string.weather_condition_unknown;return context.getString(id);}
    String dayLabel(Day day,long now){long offset=dayOffset(data,day,now);if(offset==0)return context.getString(R.string.weather_day_today);if(offset==1)return context.getString(R.string.weather_day_tomorrow);if(offset==2)return context.getString(R.string.weather_day_after_tomorrow);try{if(day.date.length()>=10){java.util.Date date=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(day.date);Locale locale=context.getResources().getConfiguration().locale;String pattern=android.text.format.DateFormat.getBestDateTimePattern(locale,context.getString(R.string.header_date_skeleton));return new java.text.SimpleDateFormat(pattern,locale).format(date);}}catch(Exception ignored){}return day.date;}
    boolean consented(){return "manual".equals(prefs.getString("consent",""))&&prefs.getInt("service_terms",0)==TERMS_VERSION;}
    void consent(String mode){if(!"manual".equals(mode))return;prefs.edit().putString("consent",mode).putInt("service_terms",TERMS_VERSION).putBoolean("enabled",true).apply();}
    void enable(boolean value){prefs.edit().putBoolean("enabled",value).apply();if(value&&active)refresh(false);else main.removeCallbacks(timer);changed.changed();}
    void start(){if(disposed)return;active=true;refresh(false);}
    void stop(){active=false;main.removeCallbacks(timer);}
    void dispose(){disposed=true;stop();worker.shutdownNow();}
    void select(Place p){if(!valid(p))return;try{generation++;busy=false;data=null;retryAt=0;lastForceAt=0;prefs.edit().putString("place",p.json().toString()).remove("cache").apply();message=context.getString(R.string.weather_fetching)+" · "+p.name;changed.changed();refresh(true);}catch(Exception ignored){}}
    void refresh(boolean force){
        if(disposed||!active||!enabled()||busy)return;
        if(prefs.getString("place","").isEmpty()){message=context.getString(R.string.weather_select_city);changed.changed();return;}
        long now=System.currentTimeMillis();main.removeCallbacks(timer);if(force&&now-lastForceAt<60000){message=context.getString(R.string.weather_wait_before_refresh);changed.changed();return;}if(force)lastForceAt=now;
        if(!force&&data!=null&&now>=data.updated&&now-data.updated<FRESH_MS){main.postDelayed(timer,FRESH_MS-(now-data.updated));return;}
        if(!force&&now<retryAt){main.postDelayed(timer,retryAt-now);return;}
        final int request=generation;busy=true;message=context.getString(R.string.weather_updating);changed.changed();
        final String manualPlace=prefs.getString("place","");
        worker.execute(new Runnable(){public void run(){
            Data result=null;String failure="";
            try {
                Place p=manualPlace.isEmpty()?null:Place.read(new JSONObject(manualPlace));
                boolean manual=p!=null;
                if(!valid(p))throw new java.io.IOException("Location unavailable");
                result=forecast(p,manual);
            } catch(Exception error){failure=errorMessage(context,error);}
            final Data fetched=result;final String requestError=failure;
            main.post(new Runnable(){public void run(){
                if(disposed||request!=generation)return;busy=false;
                if(fetched!=null){data=fetched;message="";retryAt=0;try{prefs.edit().putString("cache",json(fetched).toString()).apply();}catch(Exception ignored){}}
                else {message=data==null?requestError:context.getString(R.string.weather_stale_error,requestError);retryAt=System.currentTimeMillis()+5*60*1000L;}
                if(active){changed.changed();main.postDelayed(timer,fetched!=null?FRESH_MS:5*60*1000L);}
            }});
        }});
    }
    void search(final String query,final Cities callback){
        if(disposed||!consented())return;
        long now=System.currentTimeMillis();if(now-lastSearchAt<5000){callback.found(new ArrayList<Place>(),context.getString(R.string.weather_search_throttled));return;}lastSearchAt=now;
        worker.execute(new Runnable(){public void run(){
            final ArrayList<Place> places=new ArrayList<>();String failure="";
            try {
                String language=context.getResources().getConfiguration().locale.getLanguage();if(language==null||language.isEmpty())language="en";
                JSONObject j=JsonHttp.get("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(query,"UTF-8")+"&count=6&language="+URLEncoder.encode(language,"UTF-8")+"&format=json");
                JSONArray a=j.optJSONArray("results");
                if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Place p=new Place();p.name=o.optString("name");p.area=o.optString("admin1");p.country=o.optString("country");p.zone=o.optString("timezone","auto");p.latitude=o.optDouble("latitude",Double.NaN);p.longitude=o.optDouble("longitude",Double.NaN);if(valid(p))places.add(p);}
            } catch(Exception error){failure=errorMessage(context,error);}
            final String requestError=failure;main.post(new Runnable(){public void run(){if(!disposed&&active)callback.found(places,requestError);}});
        }});
    }
    private static Data forecast(Place p,boolean manual) throws Exception {
        String endpoint=String.format(Locale.US,"https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=3&timezone=auto",p.latitude,p.longitude);
        JSONObject j=JsonHttp.get(endpoint);JSONObject current=j.getJSONObject("current");JSONObject daily=j.getJSONObject("daily");
        Data d=new Data();d.place=p;d.manual=manual;d.temperature=current.getDouble("temperature_2m");d.code=current.getInt("weather_code");d.updated=System.currentTimeMillis();
        if(!finite(d.temperature)||d.temperature < -100||d.temperature>70)throw new java.io.IOException("Invalid weather");
        JSONArray dates=daily.getJSONArray("time"),lows=daily.getJSONArray("temperature_2m_min"),highs=daily.getJSONArray("temperature_2m_max"),codes=daily.getJSONArray("weather_code");
        for(int i=0;i<Math.min(3,dates.length());i++){Day day=new Day();day.date=dates.getString(i);day.low=lows.getDouble(i);day.high=highs.getDouble(i);day.code=codes.getInt(i);d.days.add(day);}return d;
    }
    static String condition(int code){if(code==0)return "晴";if(code==1)return "晴间多云";if(code==2)return "多云";if(code==3)return "阴";if(code==45||code==48)return "雾";if(code>=51&&code<=57)return "毛毛雨";if(code>=61&&code<=67)return "雨";if(code>=71&&code<=77)return "雪";if(code>=80&&code<=82)return "阵雨";if(code==85||code==86)return "阵雪";if(code>=95&&code<=99)return "雷雨";return "天气";}
    static double fahrenheitValue(double celsius){return celsius*9.0/5.0+32.0;}
    static String dayLabel(Data data,Day day,long now){try{java.util.TimeZone zone=java.util.TimeZone.getTimeZone(data.place.zone);java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setTimeZone(zone);String today=format.format(new java.util.Date(now));java.text.SimpleDateFormat utc=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);utc.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));long current=utc.parse(today).getTime(),target=utc.parse(day.date).getTime();long offset=Math.round((target-current)/86400000.0);if(offset==0)return "今天";if(offset==1)return "明天";if(offset==2)return "后天";return day.date.length()>=10?day.date.substring(5):day.date;}catch(Exception ignored){return day.date;}}
    private static long dayOffset(Data data,Day day,long now){try{java.util.TimeZone zone=java.util.TimeZone.getTimeZone(data.place.zone);java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setTimeZone(zone);String today=format.format(new java.util.Date(now));java.text.SimpleDateFormat utc=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);utc.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));long current=utc.parse(today).getTime(),target=utc.parse(day.date).getTime();return Math.round((target-current)/86400000.0);}catch(Exception ignored){return Long.MIN_VALUE;}}
    static String errorMessage(Throwable error){while(error.getCause()!=null)error=error.getCause();if(error instanceof java.net.UnknownHostException)return "网络或 DNS 不可用";if(error instanceof java.net.SocketTimeoutException)return "天气服务响应超时";if(error instanceof JsonHttp.HttpStatusException){int status=((JsonHttp.HttpStatusException)error).status;return status==429?"天气服务暂时限流":status>=500?"天气服务暂不可用":"天气服务拒绝了请求";}if(error instanceof javax.net.ssl.SSLException)return "安全连接失败，请检查系统日期与网络";if(error instanceof org.json.JSONException)return "天气服务返回了无法识别的数据";return "天气暂不可用，请检查网络";}
    private static String errorMessage(Context context,Throwable error){while(error.getCause()!=null)error=error.getCause();if(error instanceof java.net.UnknownHostException)return context.getString(R.string.weather_error_dns);if(error instanceof java.net.SocketTimeoutException)return context.getString(R.string.weather_error_timeout);if(error instanceof JsonHttp.HttpStatusException){int status=((JsonHttp.HttpStatusException)error).status;return context.getString(status==429?R.string.weather_error_rate_limited:status>=500?R.string.weather_error_service:R.string.weather_error_rejected);}if(error instanceof javax.net.ssl.SSLException)return context.getString(R.string.weather_error_tls);if(error instanceof org.json.JSONException)return context.getString(R.string.weather_error_data);return context.getString(R.string.weather_error_generic);}
    static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}
    static boolean valid(Place p){return p!=null&&!p.name.isEmpty()&&p.name.length()<100&&finite(p.latitude)&&finite(p.longitude)&&Math.abs(p.latitude)<=90&&Math.abs(p.longitude)<=180;}
    private static JSONObject json(Data d) throws Exception {JSONObject j=new JSONObject();j.put("place",d.place.json());j.put("temperature",d.temperature);j.put("code",d.code);j.put("updated",d.updated);j.put("manual",d.manual);JSONArray a=new JSONArray();for(Day day:d.days){JSONObject o=new JSONObject();o.put("date",day.date);o.put("low",day.low);o.put("high",day.high);o.put("code",day.code);a.put(o);}j.put("days",a);return j;}
    private static Data read(JSONObject j) throws Exception {Place p=Place.read(j.getJSONObject("place"));if(p==null)return null;Data d=new Data();d.place=p;d.temperature=j.getDouble("temperature");if(!finite(d.temperature))return null;d.code=j.getInt("code");d.updated=j.getLong("updated");d.manual=j.optBoolean("manual");JSONArray a=j.getJSONArray("days");for(int i=0;i<Math.min(3,a.length());i++){JSONObject o=a.getJSONObject(i);Day day=new Day();day.date=o.getString("date");day.low=o.getDouble("low");day.high=o.getDouble("high");day.code=o.getInt("code");d.days.add(day);}return d;}
}
