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
    private View controlView(String tag)throws Exception{return dialog().getWindow().getDecorView().findViewWithTag(tag);}
    private View tab(ControlCenter.Section section)throws Exception{return controlView("control-tab:"+section.name());}
    private View action(String key)throws Exception{return controlView("control-action:"+key);}
    private void frames() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        // The decor view can detach during Activity recreation on older Android TV.
        main(() -> {
            Choreographer scheduler = Choreographer.getInstance();
            scheduler.postFrameCallback(first -> scheduler.postFrameCallback(second -> latch.countDown()));
        });
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("No UI frames");
        Thread.sleep(120);
    }
    private void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);Bundle b=new Bundle();b.putString("check",name);sendStatus(0,b);}
    private void click(int index)throws Exception{main(()->{View button=option(index);if(button==null)throw new AssertionError("Missing settings action "+index);button.requestFocus();button.performClick();});frames();}
    private void clickAction(String key)throws Exception{
        main(()->{View row=action(key);if(row==null)throw new AssertionError("Missing control action "+key);row.requestFocus();row.performClick();});
        frames();
    }
    private void press(int key)throws Exception{sendKeyDownUpSync(key);frames();}
    private void back()throws Exception{press(KeyEvent.KEYCODE_BACK);}
    private void controlPage(ControlCenter.Section section,String key)throws Exception{
        main(()->{
            Dialog current=dialog();
            check(current instanceof ControlCenter&&current.isShowing(),section+" is in the control center");
            TextView title=(TextView)controlView("control-section-title");
            check(title.getText().toString().equals(activity.getString(section.label)),section+" has the correct heading");
            View expected=key==null?tab(section):action(key);
            check(expected!=null&&current.getCurrentFocus()==expected,section+" focuses "+(key==null?"its tab":key));
        });
    }
    private void page(int title,int focus,String name)throws Exception{main(()->{Dialog d=dialog();check(d!=null&&d.isShowing(),name+" remains in settings");TextView heading=d.getWindow().getDecorView().findViewWithTag("settings-title");check(heading.getText().toString().equals(activity.getString(title)),name+" returns to the correct page");if(focus>=0)check(d.getCurrentFocus()==option(focus),name+" retains the selected action");});}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator")||(Build.FINGERPRINT.startsWith("google/sdk_google_atv_x86/emulator_x86_arm:14/")&&"ranchu".equals(Build.HARDWARE))))throw new AssertionError("Disposable emulator only");
        Context c=getTargetContext();SharedPreferences home=c.getSharedPreferences("home",0),history=c.getSharedPreferences("playback_history",0);
        home.edit().putBoolean("setup_seen",true).putBoolean("wallpaper_motion",true).putBoolean("banners",true).putString("wallpaper_kind","builtin").remove("home_split_rows").remove("home_show_favorites").remove("home_show_inputs").commit();
        history.edit().putInt("config_schema",1).putBoolean("enabled",false).putBoolean("generic_enabled",false).commit();c.getSharedPreferences("weather",0).edit().clear().commit();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);c.startActivity(new Intent().setComponent(new ComponentName(c,MainActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=(MainActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);if(activity==null)throw new AssertionError("No launcher");frames();Thread.sleep(800);frames();
        // A real pointer event enters touch mode; programmatic clicks alone do not.
        long downAt=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(downAt,downAt,MotionEvent.ACTION_DOWN,10,10,0),up=MotionEvent.obtain(downAt,downAt+20,MotionEvent.ACTION_UP,10,10,0);try{sendPointerSync(down);sendPointerSync(up);}finally{down.recycle();up.recycle();}frames();main(()->check(activity.getWindow().getDecorView().isInTouchMode(),"pointer event enters touch mode before settings"));
        main(activity::showControlCenter);frames();controlPage(ControlCenter.Section.HOME,null);
        main(()->check(tab(ControlCenter.Section.HOME).isInTouchMode(),"control center retains focus in touch mode"));
        main(()->{
            String defaultHome=activity.getString(SystemEntrypoints.defaultHome(activity)
                    ?R.string.control_home_active:R.string.control_home_unknown);
            String version=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;
            check(((TextView)controlView("control-footer-status")).getText().toString().equals(defaultHome+" · "+version),
                    "control center footer reports default home state and version");
        });
        press(KeyEvent.KEYCODE_DPAD_RIGHT);controlPage(ControlCenter.Section.HOME,"split");
        press(KeyEvent.KEYCODE_DPAD_DOWN);controlPage(ControlCenter.Section.HOME,"favorites");
        press(KeyEvent.KEYCODE_DPAD_LEFT);controlPage(ControlCenter.Section.HOME,null);
        press(KeyEvent.KEYCODE_DPAD_DOWN);controlPage(ControlCenter.Section.NETWORK,null);
        press(KeyEvent.KEYCODE_DPAD_DOWN);controlPage(ControlCenter.Section.INPUTS,null);
        clickAction("refresh-inputs");controlPage(ControlCenter.Section.INPUTS,"refresh-inputs");
        back();main(()->check(dialog()==null,"Back from input controls closes the panel"));
        main(()->((View)field("root")).findViewWithTag("network-status").performClick());frames();
        controlPage(ControlCenter.Section.NETWORK,null);
        main(()->{
            View indicator=((View)field("root")).findViewWithTag("network-status");
            String network=activity.getString(NetworkStatus.read(activity).label);
            check(indicator.getContentDescription().toString().equals(network),"desktop indicator reflects Android network state");
            check(((TextView)controlView("control-section-status")).getText().toString().startsWith(network),"network page uses the same status as the desktop");
        });
        press(KeyEvent.KEYCODE_DPAD_UP);controlPage(ControlCenter.Section.HOME,null);
        clickAction("appearance");page(R.string.settings_appearance,0,"control personalization");
        back();controlPage(ControlCenter.Section.HOME,"appearance");
        main(()->tab(ControlCenter.Section.SETTINGS).requestFocus());frames();
        clickAction("desktop-settings");page(R.string.toolbar_home_settings,0,"settings root");
        back();controlPage(ControlCenter.Section.SETTINGS,"desktop-settings");
        clickAction("desktop-settings");page(R.string.toolbar_home_settings,0,"settings root after returning");
        click(0);click(2);page(R.string.settings_appearance,2,"image motion change");check(!home.getBoolean("wallpaper_motion",true),"image motion saves immediately");click(2);click(4);page(R.string.settings_appearance,4,"second appearance change");check(!home.getBoolean("banners",true),"banner change saves without leaving settings");
        click(3);click(1);page(R.string.appearance_colors,1,"theme change");check(home.getInt("theme",-1)==1,"theme selection is persisted");back();page(R.string.settings_appearance,3,"theme Back");back();page(R.string.toolbar_home_settings,0,"appearance Back");
        click(2);main(()->check(option(3)==null,"playback landing page contains only three primary actions"));
        click(0);controlPage(ControlCenter.Section.HOME,null);
        clickAction("playback");controlPage(ControlCenter.Section.HOME,"playback");
        check(history.getBoolean("enabled",false)&&!history.getBoolean("generic_enabled",false),"showing history preserves source choices");
        clickAction("playback");back();page(R.string.settings_playback,0,"home layout Back");
        click(1);click(0);page(R.string.playback_sources,0,"media detection toggle");click(0);
        click(1);page(R.string.media_permission_title,0,"permission explanation");back();page(R.string.playback_sources,1,"permission Back");click(2);page(R.string.media_apps_title,-1,"per-app sources");main(()->{View first=option(0);if(first!=null){first.requestFocus();first.performClick();}});frames();page(R.string.media_apps_title,-1,"per-app change");back();page(R.string.playback_sources,2,"source list Back");back();page(R.string.settings_playback,1,"sources Back");
        click(2);click(0);page(R.string.playback_rules,-1,"advanced integrations");back();page(R.string.settings_advanced,0,"rules Back");int records=new HistoryStore(c).list().size();click(1);click(0);page(R.string.settings_advanced,1,"cancel history deletion");check(new HistoryStore(c).list().size()==records,"cancel leaves existing history intact");back();back();
        click(3);click(2);page(R.string.weather_title,2,"weather units");WeatherRepository weather=(WeatherRepository)field("weather");check(!weather.enabled()&&!weather.consented(),"changing units does not consent to or enable network weather");click(0);page(R.string.weather_consent_title,0,"weather consent");click(2);page(R.string.weather_title,0,"declining weather terms");back();
        click(1);click(1);click(0);click(1);main(()->check(dialog()!=null&&dialog().getCurrentFocus()==option(1),"category layout changes keep their editor and focus"));back();page(R.string.settings_categories,0,"category Back");back();page(R.string.settings_apps,1,"categories Back");click(0);main(()->check(dialog()==null&&(Boolean)field("manage"),"app organization intentionally opens the desktop editor"));back();page(R.string.settings_apps,0,"organization Back");back();
        Configuration original=new Configuration(activity.getResources().getConfiguration());
        try{main(()->{Configuration large=new Configuration(original);large.fontScale=1.6f;large.setLocale(Locale.US);activity.getResources().updateConfiguration(large,activity.getResources().getDisplayMetrics());activity.render();activity.showSettings();});frames();page(R.string.toolbar_home_settings,1,"English large-font settings");
            for(int index:new int[]{0,4}){final int i=index;main(()->option(i).requestFocus());frames();main(()->{View button=option(i);Rect visible=new Rect();check(button.getGlobalVisibleRect(visible)&&visible.height()==button.getHeight(),"large-font action "+i+" is completely reachable");});}
            main(()->{activity.invalidateControlCenter();activity.showHomeLayout(null);});frames();
            controlPage(ControlCenter.Section.HOME,null);
            main(()->{
                View date=(View)field("dateLabel");Rect visible=new Rect();
                check(date.getGlobalVisibleRect(visible)&&visible.height()==date.getHeight(),"large-font home date is not clipped");
            });
            press(KeyEvent.KEYCODE_DPAD_RIGHT);
            main(()->action("split").requestFocus());frames();
            for(String key:new String[]{"split","favorites","playback","inputs"}){
                controlPage(ControlCenter.Section.HOME,key);
                main(()->{
                    View row=action(key);Rect visible=new Rect();
                    check(row.getGlobalVisibleRect(visible)&&visible.height()==row.getHeight(),"large-font switch "+key+" is completely reachable");
                });
                if(!"inputs".equals(key))press(KeyEvent.KEYCODE_DPAD_DOWN);
            }
            main(activity::showSettings);frames();
        }finally{main(()->{activity.getResources().updateConfiguration(original,activity.getResources().getDisplayMetrics());activity.invalidateControlCenter();});}
        back();controlPage(ControlCenter.Section.HOME,"inputs");
        back();main(()->check(dialog()==null,"Back from control center closes the panel"));
        main(activity::showSettings);frames();click(5);
        clickAction("split");clickAction("favorites");clickAction("playback");clickAction("inputs");
        check(home.getBoolean("home_split_rows",false)&&!home.getBoolean("home_show_favorites",true)
                &&history.getBoolean("enabled",false)&&!home.getBoolean("home_show_inputs",true),"home layout saves all four switches");
        back();page(R.string.toolbar_home_settings,5,"layout Back from desktop settings");
        MainActivity previous=activity;
        ActivityMonitor recreated=addMonitor(MainActivity.class.getName(),null,false);
        MainActivity next=null;
        try {
            main(previous::recreate);
            // The monitor can report the old instance as it finishes resuming.
            long deadline=SystemClock.uptimeMillis()+10000;
            long remaining=deadline-SystemClock.uptimeMillis();
            while(remaining>0) {
                next=(MainActivity)recreated.waitForActivityWithTimeout(remaining);
                if(next!=null&&next!=previous)break;
                remaining=deadline-SystemClock.uptimeMillis();
            }
        } finally {
            removeMonitor(recreated);
        }
        if(next==null||next==previous)throw new AssertionError("No recreated launcher");
        activity=next;
        frames();
        main(()->{
            if(!activity.getWindow().getDecorView().isAttachedToWindow())
                throw new AssertionError("Recreated launcher window is detached");
        });
        main(activity::showControlCenter);frames();controlPage(ControlCenter.Section.HOME,null);
        main(()->{
            String[] keys={"split","favorites","playback","inputs"};
            boolean[] states={true,false,true,false};
            for(int i=0;i<keys.length;i++){
                String state=activity.getString(states[i]?R.string.setting_on:R.string.setting_off);
                check(action(keys[i]).getContentDescription().toString().endsWith(" · "+state),keys[i]+" is restored after Activity recreation");
            }
        });
        home.edit().remove("home_split_rows").remove("home_show_favorites").remove("home_show_inputs").commit();
        history.edit().putBoolean("enabled",false).commit();
        result.putString("passed",checks+" settings navigation checks");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("failed",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
