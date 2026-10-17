package local.quiettv.launcher;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.*;

/** Exercises the actual settings dialogs on an explicitly disposable emulator. */
public final class SettingsChecks extends Instrumentation {
    private MainActivity activity;
    private int checks;
    private interface Work {void run()throws Exception;}
    private void main(Work work){final Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private Dialog dialog()throws Exception{return (Dialog)field("sheet");}
    private View option(int index)throws Exception{return dialog().getWindow().getDecorView().findViewWithTag("settings-option:"+index);}
    private void frames()throws Exception{CountDownLatch latch=new CountDownLatch(1);main(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(latch::countDown)));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("No rendered frames");Thread.sleep(120);}
    private void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);Bundle b=new Bundle();b.putString("check",name);sendStatus(0,b);}
    private void click(int index)throws Exception{main(()->{View button=option(index);if(button==null)throw new AssertionError("Missing settings action "+index);button.requestFocus();button.performClick();});frames();}
    private void back()throws Exception{sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);frames();}
    private void page(int title,int focus,String name)throws Exception{main(()->{Dialog d=dialog();check(d!=null&&d.isShowing(),name+" remains in settings");TextView heading=d.getWindow().getDecorView().findViewWithTag("settings-title");check(heading.getText().toString().equals(activity.getString(title)),name+" returns to the correct page");if(focus>=0)check(d.getCurrentFocus()==option(focus),name+" retains the selected action");});}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator")))throw new AssertionError("Disposable emulator only");
        Context c=getTargetContext();SharedPreferences home=c.getSharedPreferences("home",0),history=c.getSharedPreferences("playback_history",0);
        home.edit().putBoolean("setup_seen",true).putBoolean("wallpaper_motion",true).putBoolean("banners",true).putString("wallpaper_kind","builtin").commit();
        history.edit().putInt("config_schema",1).putBoolean("enabled",false).putBoolean("generic_enabled",false).commit();c.getSharedPreferences("weather",0).edit().clear().commit();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);c.startActivity(new Intent().setComponent(new ComponentName(c,MainActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=(MainActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);if(activity==null)throw new AssertionError("No launcher");frames();Thread.sleep(800);frames();
        main(activity::showSettings);frames();page(R.string.toolbar_home_settings,0,"settings root");
        click(0);click(2);page(R.string.settings_appearance,2,"image motion change");check(!home.getBoolean("wallpaper_motion",true),"image motion saves immediately");click(2);click(4);page(R.string.settings_appearance,4,"second appearance change");check(!home.getBoolean("banners",true),"banner change saves without leaving settings");
        click(3);click(1);page(R.string.appearance_colors,1,"theme change");check(home.getInt("theme",-1)==1,"theme selection is persisted");back();page(R.string.settings_appearance,3,"theme Back");back();page(R.string.toolbar_home_settings,0,"appearance Back");
        click(2);main(()->check(option(3)==null,"playback landing page contains only three primary actions"));click(0);page(R.string.settings_playback,0,"history toggle");check(history.getBoolean("enabled",false)&&!history.getBoolean("generic_enabled",false),"showing history does not silently enable another data source");click(0);click(1);click(0);page(R.string.playback_sources,0,"media detection toggle");click(0);
        click(1);page(R.string.media_permission_title,0,"permission explanation");back();page(R.string.playback_sources,1,"permission Back");click(2);page(R.string.media_apps_title,-1,"per-app sources");main(()->{View first=option(0);if(first!=null){first.requestFocus();first.performClick();}});frames();page(R.string.media_apps_title,-1,"per-app change");back();page(R.string.playback_sources,2,"source list Back");back();page(R.string.settings_playback,1,"sources Back");
        click(2);click(0);page(R.string.playback_rules,-1,"advanced integrations");back();page(R.string.settings_advanced,0,"rules Back");int records=new HistoryStore(c).list().size();click(1);click(0);page(R.string.settings_advanced,1,"cancel history deletion");check(new HistoryStore(c).list().size()==records,"cancel leaves existing history intact");back();back();
        click(3);click(2);page(R.string.weather_title,2,"weather units");WeatherRepository weather=(WeatherRepository)field("weather");check(!weather.enabled()&&!weather.consented(),"changing units does not consent to or enable network weather");click(0);page(R.string.weather_consent_title,0,"weather consent");click(2);page(R.string.weather_title,0,"declining weather terms");back();
        click(1);click(1);click(0);click(1);main(()->check(dialog()!=null&&dialog().getCurrentFocus()==option(1),"category layout changes keep their editor and focus"));back();page(R.string.settings_categories,0,"category Back");back();page(R.string.settings_apps,1,"categories Back");click(0);main(()->check(dialog()==null&&(Boolean)field("manage"),"app organization intentionally opens the desktop editor"));back();page(R.string.settings_apps,0,"organization Back");back();
        Configuration original=new Configuration(activity.getResources().getConfiguration());
        try{main(()->{Configuration large=new Configuration(original);large.fontScale=1.6f;large.setLocale(Locale.US);activity.getResources().updateConfiguration(large,activity.getResources().getDisplayMetrics());activity.showSettings();});frames();page(R.string.toolbar_home_settings,1,"English large-font settings");
            for(int index:new int[]{0,4}){final int i=index;main(()->option(i).requestFocus());frames();main(()->{View button=option(i);Rect visible=new Rect();check(button.getGlobalVisibleRect(visible)&&visible.height()==button.getHeight(),"large-font action "+i+" is completely reachable");});}
        }finally{main(()->activity.getResources().updateConfiguration(original,activity.getResources().getDisplayMetrics()));}
        main(()->dialog().cancel());frames();main(()->check(dialog()==null,"Back from settings root closes the panel"));
        result.putString("passed",checks+" settings navigation checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("failed",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
