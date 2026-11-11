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
    private View control(int index)throws Exception{return dialog().getWindow().getDecorView().findViewWithTag("control-option:"+index);}
    private void frames()throws Exception{CountDownLatch latch=new CountDownLatch(1);main(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(latch::countDown)));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("No rendered frames");Thread.sleep(120);}
    private void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);Bundle b=new Bundle();b.putString("check",name);sendStatus(0,b);}
    private void click(int index)throws Exception{main(()->{View button=option(index);if(button==null)throw new AssertionError("Missing settings action "+index);button.requestFocus();button.performClick();});frames();}
    private void back()throws Exception{sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);frames();}
    private void page(int title,int focus,String name)throws Exception{main(()->{Dialog d=dialog();check(d!=null&&d.isShowing(),name+" remains in settings");TextView heading=d.getWindow().getDecorView().findViewWithTag("settings-title");check(heading.getText().toString().equals(activity.getString(title)),name+" returns to the correct page");if(focus>=0)check(d.getCurrentFocus()==option(focus),name+" retains the selected action");});}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator")||(Build.FINGERPRINT.startsWith("google/sdk_google_atv_x86/emulator_x86_arm:14/")&&"ranchu".equals(Build.HARDWARE))))throw new AssertionError("Disposable emulator only");
        Context c=getTargetContext();SharedPreferences home=c.getSharedPreferences("home",0),history=c.getSharedPreferences("playback_history",0);
        home.edit().putBoolean("setup_seen",true).putBoolean("wallpaper_motion",true).putBoolean("banners",true).putString("wallpaper_kind","builtin").remove("home_split_rows").remove("home_show_favorites").commit();
        history.edit().putInt("config_schema",1).putBoolean("enabled",false).putBoolean("generic_enabled",false).commit();c.getSharedPreferences("weather",0).edit().clear().commit();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);c.startActivity(new Intent().setComponent(new ComponentName(c,MainActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=(MainActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);if(activity==null)throw new AssertionError("No launcher");frames();Thread.sleep(800);frames();
        // A real pointer event enters touch mode; programmatic clicks alone do not.
        long downAt=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(downAt,downAt,MotionEvent.ACTION_DOWN,10,10,0),up=MotionEvent.obtain(downAt,downAt+20,MotionEvent.ACTION_UP,10,10,0);try{sendPointerSync(down);sendPointerSync(up);}finally{down.recycle();up.recycle();}frames();main(()->check(activity.getWindow().getDecorView().isInTouchMode(),"pointer event enters touch mode before settings"));
        main(activity::showControlCenter);frames();main(()->{check(dialog()!=null&&control(0)!=null&&control(4)!=null,"control center offers input and desktop settings");control(0).requestFocus();control(0).performClick();});frames();page(R.string.toolbar_inputs,0,"input page");main(()->check(option(0).isInTouchMode()&&option(0).isFocused(),"input page retains focus in touch mode"));final int[] inputSettingsIndex={0};main(()->{while(option(inputSettingsIndex[0]+1)!=null)inputSettingsIndex[0]++;});click(inputSettingsIndex[0]);page(R.string.control_input_settings,0,"input settings page");back();page(R.string.toolbar_inputs,inputSettingsIndex[0],"input settings Back");back();main(()->check(dialog()!=null&&dialog().getCurrentFocus()==control(0),"input Back restores control center focus"));
        main(()->{control(3).requestFocus();control(3).performClick();});frames();page(R.string.settings_appearance,0,"control personalization");back();main(()->check(dialog()!=null&&dialog().getCurrentFocus()==control(3),"personalization Back restores control center focus"));
        main(()->{control(4).requestFocus();control(4).performClick();});frames();page(R.string.toolbar_home_settings,0,"settings root");
        click(0);click(2);page(R.string.settings_appearance,2,"image motion change");check(!home.getBoolean("wallpaper_motion",true),"image motion saves immediately");click(2);click(4);page(R.string.settings_appearance,4,"second appearance change");check(!home.getBoolean("banners",true),"banner change saves without leaving settings");
        click(3);click(1);page(R.string.appearance_colors,1,"theme change");check(home.getInt("theme",-1)==1,"theme selection is persisted");back();page(R.string.settings_appearance,3,"theme Back");back();page(R.string.toolbar_home_settings,0,"appearance Back");
        click(2);main(()->check(option(3)==null,"playback landing page contains only three primary actions"));click(0);page(R.string.settings_playback,0,"history toggle");check(history.getBoolean("enabled",false)&&!history.getBoolean("generic_enabled",false),"showing history does not silently enable another data source");click(0);click(1);click(0);page(R.string.playback_sources,0,"media detection toggle");click(0);
        click(1);page(R.string.media_permission_title,0,"permission explanation");back();page(R.string.playback_sources,1,"permission Back");click(2);page(R.string.media_apps_title,-1,"per-app sources");main(()->{View first=option(0);if(first!=null){first.requestFocus();first.performClick();}});frames();page(R.string.media_apps_title,-1,"per-app change");back();page(R.string.playback_sources,2,"source list Back");back();page(R.string.settings_playback,1,"sources Back");
        click(2);click(0);page(R.string.playback_rules,-1,"advanced integrations");back();page(R.string.settings_advanced,0,"rules Back");int records=new HistoryStore(c).list().size();click(1);click(0);page(R.string.settings_advanced,1,"cancel history deletion");check(new HistoryStore(c).list().size()==records,"cancel leaves existing history intact");back();back();
        click(3);click(2);page(R.string.weather_title,2,"weather units");WeatherRepository weather=(WeatherRepository)field("weather");check(!weather.enabled()&&!weather.consented(),"changing units does not consent to or enable network weather");click(0);page(R.string.weather_consent_title,0,"weather consent");click(2);page(R.string.weather_title,0,"declining weather terms");back();
        click(1);click(1);click(0);click(1);main(()->check(dialog()!=null&&dialog().getCurrentFocus()==option(1),"category layout changes keep their editor and focus"));back();page(R.string.settings_categories,0,"category Back");back();page(R.string.settings_apps,1,"categories Back");click(0);main(()->check(dialog()==null&&(Boolean)field("manage"),"app organization intentionally opens the desktop editor"));back();page(R.string.settings_apps,0,"organization Back");back();
        Configuration original=new Configuration(activity.getResources().getConfiguration());
        try{main(()->{Configuration large=new Configuration(original);large.fontScale=1.6f;large.setLocale(Locale.US);activity.getResources().updateConfiguration(large,activity.getResources().getDisplayMetrics());activity.render();activity.showSettings();});frames();page(R.string.toolbar_home_settings,1,"English large-font settings");
            for(int index:new int[]{0,4}){final int i=index;main(()->option(i).requestFocus());frames();main(()->{View button=option(i);Rect visible=new Rect();check(button.getGlobalVisibleRect(visible)&&visible.height()==button.getHeight(),"large-font action "+i+" is completely reachable");});}
            main(()->{activity.invalidateControlCenter();activity.showControlCenter();});frames();
            main(()->{View first=control(0),last=control(3);check(first!=null&&last!=null,"large-font control shortcuts remain present");TextView label=(TextView)((android.widget.LinearLayout)last).getChildAt(1);check(label.getLineCount()==1,"large-font personalization label fits without splitting");View date=(View)field("dateLabel");Rect visible=new Rect();check(date.getGlobalVisibleRect(visible)&&visible.height()==date.getHeight(),"large-font home date is not clipped");first.requestFocus();});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);frames();main(()->check(dialog().getCurrentFocus()==control(1),"large-font first row navigates right"));sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);frames();main(()->check(dialog().getCurrentFocus()==control(3),"large-font second row navigates down"));
            main(activity::showSettings);frames();
        }finally{main(()->activity.getResources().updateConfiguration(original,activity.getResources().getDisplayMetrics()));}
        main(()->dialog().cancel());frames();main(()->check(dialog()!=null&&control(4)!=null,"Back from settings root opens control center"));main(()->dialog().cancel());frames();main(()->check(dialog()==null,"Back from control center closes the panel"));
        main(activity::showSettings);frames();click(5);click(0);click(1);
        check(home.getBoolean("home_split_rows",false)&&!home.getBoolean("home_show_favorites",true),"home layout switches save both choices");
        ActivityMonitor recreated=addMonitor(MainActivity.class.getName(),null,false);main(activity::recreate);
        MainActivity next=(MainActivity)waitForMonitorWithTimeout(recreated,10000);removeMonitor(recreated);if(next==null)throw new AssertionError("No recreated launcher");activity=next;frames();
        main(activity::showSettings);frames();click(5);page(R.string.settings_home_layout,0,"home layout after recreation");
        main(()->{check(((TextView)option(0)).getText().toString().equals(activity.settingState(activity.getString(R.string.home_split_rows),true)),"split choice is restored after Activity recreation");check(((TextView)option(1)).getText().toString().equals(activity.settingState(activity.getString(R.string.home_show_favorites),false)),"favorite visibility choice is restored after Activity recreation");});
        home.edit().remove("home_split_rows").remove("home_show_favorites").commit();
        result.putString("passed",checks+" settings navigation checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("failed",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
