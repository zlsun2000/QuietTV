package local.quiettv.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;

/** Consumes only the separate test package's synthetic system TV-provider rows. */
public final class TvHistoryChecks extends Instrumentation {
    private int count;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);Bundle progress=new Bundle();progress.putString("check",message);sendStatus(0,progress);}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences prefs=c.getSharedPreferences("playback_history",0);Map<String,?> original=new HashMap<>(prefs.getAll());try{
        if(Build.VERSION.SDK_INT<26||!(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator")))throw new AssertionError("TV provider checks require an API 26+ synthetic emulator");
        Map<String,?> home=new HashMap<>(c.getSharedPreferences("home",0).getAll()),weather=new HashMap<>(c.getSharedPreferences("weather",0).getAll()),rules=new HashMap<>(c.getSharedPreferences("playback_rules",0).getAll());
        prefs.edit().putBoolean("enabled",true).putBoolean("generic_enabled",true).commit();
        JSONObject report=TvHistoryInspection.inspect(c,"local.quiettv.checks",new CancellationSignal());JSONObject watch=report.getJSONObject("watchNext");
        check(report.getBoolean("readTvListingsGranted")&&report.getString("status").equals("inspected"),"normal TV-listings authorization permits the explicit scoped inspection");
        check(watch.getString("status").equals("ok"),"the real system Watch Next provider accepts the projected columns and package filter");
        check(watch.getInt("rowsInspected")==3&&watch.getInt("boundedIds")==3&&watch.getInt("numericIds")==2,"system-provider fixture IDs are classified without persisting their values");
        check(watch.getInt("resolvableOwnedRoutes")==2&&watch.getInt("continueRows")==2&&watch.getInt("continueRowsWithIdAndRoute")==1,"foreign routes are rejected and next episodes are distinguished from continuing items");
        check(watch.getInt("rowsWithPositionValue")==3&&watch.getInt("rowsWithDuration")==3,"progress and duration presence is counted without exposing the numbers");
        check(!report.toString().matches("(?s).*(80000001|80000002|QuietTV synthetic|intent:).*"),"the diagnostic report excludes original content IDs, titles and Intents");
        check(report.getJSONObject("previewPrograms").getString("status").equals("ok")&&report.getJSONObject("previewPrograms").getInt("rowsInspected")==0,"recommendation rows remain separate from Watch Next history");
        restoreChoices(prefs,original);
        check(original.equals(prefs.getAll())&&home.equals(c.getSharedPreferences("home",0).getAll())&&weather.equals(c.getSharedPreferences("weather",0).getAll())&&rules.equals(c.getSharedPreferences("playback_rules",0).getAll()),"permissioned inspections do not modify any launcher preference store");
        result.putString("passed",count+" TV provider checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("failed",e.toString());finish(Activity.RESULT_CANCELED,result);}finally{restoreChoices(prefs,original);}}
    private static void restoreChoices(SharedPreferences prefs,Map<String,?> original){SharedPreferences.Editor edit=prefs.edit();for(String key:new String[]{"enabled","generic_enabled"}){if(original.containsKey(key))edit.putBoolean(key,(Boolean)original.get(key));else edit.remove(key);}edit.commit();}
}
