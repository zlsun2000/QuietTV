package local.quiettv.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Regression scenarios on a disposable emulator only; no accounts, apps or user media. */
public final class DisplayChecks extends Instrumentation {
    private boolean focusRefreshOnly,moveOnly,transitionOnly,expectLowRamPath;
    private int count;private final ArrayList<String> failures=new ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);focusRefreshOnly=args!=null&&"true".equals(args.getString("focusRefreshOnly"));moveOnly=args!=null&&"true".equals(args.getString("issue6MoveOnly"));transitionOnly=args!=null&&"true".equals(args.getString("issue6TransitionOnly"));expectLowRamPath=args!=null&&"true".equals(args.getString("issue6ExpectLowRamPath"));start();}
    interface Work {void run()throws Exception;}
    private void main(Work work){final Throwable[] failure={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new RuntimeException(failure[0]);}
    private Object field(Object owner,String name)throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void set(Object owner,String name,Object value)throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private void check(boolean ok,String text){count++;Bundle progress=new Bundle();progress.putString("check",(ok?"PASS ":"FAIL ")+text);sendStatus(0,progress);if(!ok)failures.add(text);}
    private void frames(Activity activity)throws Exception {CountDownLatch latch=new CountDownLatch(1);main(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(latch::countDown)));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("No frames");Thread.sleep(220);}
    private void holdLayout(MainActivity a,boolean hold)throws Exception {if(Build.VERSION.SDK_INT>=29)main(()->ViewGroup.class.getMethod("suppressLayout",boolean.class).invoke(field(a,"root"),hold));}
    @SuppressWarnings("unchecked") private ArrayList<MainActivity.CardRow> rows(MainActivity a)throws Exception {return (ArrayList<MainActivity.CardRow>)field(a,"cardRows");}
    private int visible(MainActivity activity)throws Exception {int n=0;HashSet<android.graphics.Rect> bands=new HashSet<>();for(MainActivity.CardRow row:rows(activity))if(row.fullyVisible()&&bands.add(row.cardBounds()))n++;return n;}
    @SuppressWarnings("unchecked") private ArrayList<ArrayList<View>> focusRows(MainActivity a)throws Exception{return (ArrayList<ArrayList<View>>)field(a,"focusRows");}
    private int rowContaining(MainActivity a,String prefix)throws Exception{ArrayList<ArrayList<View>> graph=focusRows(a);for(int i=0;i<graph.size();i++)for(View v:graph.get(i))if(v.getTag()!=null&&v.getTag().toString().startsWith(prefix))return i;return -1;}
    private void choose(MainActivity a,int index)throws Exception{main(()->{Dialog dialog=(Dialog)field(a,"sheet");View choice=dialog.getWindow().getDecorView().findViewWithTag("settings-option:"+index);if(choice==null)throw new AssertionError("Missing settings choice "+index);choice.requestFocus();choice.performClick();});frames(a);}
    private void toggle(MainActivity a,String key)throws Exception{
        main(()->{
            Dialog dialog=(Dialog)field(a,"sheet");
            View row=dialog.getWindow().getDecorView().findViewWithTag("control-action:"+key);
            if(row==null)throw new AssertionError("Missing home layout action "+key);
            row.requestFocus();
            row.performClick();
        });
        frames(a);
    }
    private boolean hasHeading(MainActivity a,int label)throws Exception{
        ArrayList<View> matches=new ArrayList<>();
        ((View)field(a,"content")).findViewsWithText(matches,a.getString(label),View.FIND_VIEWS_WITH_TEXT);
        return !matches.isEmpty();
    }
    private void homeLayout(MainActivity a,SharedPreferences home,SharedPreferences history)throws Exception{
        // Model an existing 0.14.x home store: no new keys and no recent group.
        // The app list and enabled synthetic playback record are the display fixture.
        main(()->{home.edit().remove("home_split_rows").remove("home_show_favorites").putString("groups","[{\"id\":\"favorites\",\"title\":\"Favorites\",\"row\":false},{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]").commit();a.loadGroups();a.render();});frames(a);
        main(()->{int favorite=rowContaining(a,"favorites|"),recent=rowContaining(a,"recent|");check(favorite>0&&favorite==recent,"legacy home defaults to the shared favorite/recent band");check(!home.contains("home_split_rows")&&!home.contains("home_show_favorites"),"legacy defaults need no migration or preference reset");});
        main(a::showSettings);frames(a);choose(a,5);toggle(a,"split");
        main(()->{int favorite=rowContaining(a,"favorites|"),recent=rowContaining(a,"recent|");check(home.getBoolean("home_split_rows",false),"split switch is saved");check(favorite>0&&recent>favorite,"split switch creates separate ordered rows");int favoriteCards=0;for(View v:focusRows(a).get(favorite))if(v.getTag()!=null&&v.getTag().toString().startsWith("favorites|"))favoriteCards++;check(favoriteCards==3,"split favorites is one horizontal row despite legacy grid preference");});
        String records=history.getString("records","");
        toggle(a,"playback");
        main(()->{
            check(!history.getBoolean("enabled",true),"playback switch saves the disabled state");
            check(rowContaining(a,"recent|")==-1&&rowContaining(a,"empty|recent")==-1
                    &&!hasHeading(a,R.string.heading_continue_playing),"disabled playback removes the whole split section");
            check(!records.isEmpty()&&records.equals(history.getString("records","")),"turning off playback preserves stored records");
        });
        toggle(a,"playback");
        main(()->check(rowContaining(a,"recent|")>0,"turning playback back on restores the saved card"));
        // Move recent past inputs and then favorites past both via the real category editor.
        main(a::showGroups);frames(a);choose(a,1);choose(a,1);main(a::showGroups);frames(a);choose(a,0);choose(a,2);choose(a,2);
        main(()->{int favorite=rowContaining(a,"favorites|"),recent=rowContaining(a,"recent|");check(recent>0&&favorite>recent,"category move controls reorder both independent rows");a.loadGroups();a.render();});frames(a);
        main(()->check(rowContaining(a,"recent|")>0&&rowContaining(a,"favorites|")>rowContaining(a,"recent|"),"group order survives reload from legacy-upgraded preferences"));
        main(()->a.showHomeLayout(null));frames(a);toggle(a,"favorites");
        main(()->{check(!home.getBoolean("home_show_favorites",true),"favorites visibility switch is saved");check(rowContaining(a,"favorites|")==-1&&rowContaining(a,"recent|")>0,"hidden favorites leave an independent recent row");});
        toggle(a,"split");
        main(()->{check(!home.getBoolean("home_split_rows",true)&&!home.getBoolean("home_show_favorites",true),"both switch values survive reopening and toggling");check(rowContaining(a,"favorites|")==-1&&rowContaining(a,"recent|")>0,"hidden favorites leave recent independent in default layout");});
        main(()->{Dialog dialog=(Dialog)field(a,"sheet");if(dialog!=null)dialog.dismiss();home.edit().remove("home_split_rows").remove("home_show_favorites").commit();a.render();});frames(a);
    }
    @SuppressWarnings("unchecked")
    private void inputVisibility(MainActivity a,SharedPreferences home)throws Exception{
        // Keep the synthetic HDMI port authoritative for the rest of this emulator test.
        InputSources scanner=(InputSources)field(a,"inputSources");
        if(scanner!=null){
            main(()->{set(a,"inputSources",null);scanner.dispose();});
            ExecutorService worker=(ExecutorService)field(scanner,"worker");
            if(!worker.awaitTermination(10,TimeUnit.SECONDS))
                throw new AssertionError("Input discovery did not stop before installing the fixture");
            waitForIdleSync();
        }
        InputSources.Port port=new InputSources.Port();
        port.id=73;port.number=1;port.name="Fixture HDMI";port.enumName="ANDROID_HDMI";
        port.intent=new Intent(Intent.ACTION_VIEW);
        Method scan=MainActivity.class.getDeclaredMethod("applyInputScan",ArrayList.class);
        scan.setAccessible(true);
        main(()->{
            home.edit().remove("home_show_inputs").commit();
            ((Set<String>)field(a,"favorites")).add(port.key());
            scan.invoke(a,new ArrayList<>(Collections.singletonList(port)));
            a.showHomeLayout(null);
        });frames(a);
        main(()->{
            View root=(View)field(a,"root");
            check(root.findViewWithTag("inputs|"+port.key())!=null,"legacy home shows an available input by default");
            check(root.findViewWithTag("favorites|"+port.key())!=null,"an input can also be saved to favorites");
        });
        toggle(a,"inputs");
        main(()->{
            View root=(View)field(a,"root");
            check(!home.getBoolean("home_show_inputs",true),"input visibility saves the disabled state");
            check(rowContaining(a,"inputs|")==-1&&!hasHeading(a,R.string.group_inputs)
                    &&root.findViewWithTag("favorites|"+port.key())==null,"hiding inputs removes the row and its shortcuts from home");
            Dialog dialog=(Dialog)field(a,"sheet");
            View decor=dialog.getWindow().getDecorView();
            decor.findViewWithTag("control-tab:INPUTS").requestFocus();
            check(decor.findViewWithTag("control-action:"+port.key())!=null,"hidden home inputs are still selectable in the control center");
            decor.findViewWithTag("control-tab:HOME").requestFocus();
        });
        toggle(a,"inputs");
        main(()->{
            View root=(View)field(a,"root");
            check(root.findViewWithTag("inputs|"+port.key())!=null
                    &&root.findViewWithTag("favorites|"+port.key())!=null,"showing inputs restores the row and the saved favorite");
            ((Dialog)field(a,"sheet")).dismiss();
            ((Set<String>)field(a,"favorites")).remove(port.key());
            home.edit().remove("home_show_inputs").commit();
            scan.invoke(a,new ArrayList<InputSources.Port>());
        });frames(a);
    }
    private void recentPresentation(MainActivity a)throws Exception{
        main(()->{
            PlaybackRecord r=new PlaybackRecord();r.source="local.quiettv.synthetic.player";r.sourceLabel="非常长的测试来源 Synthetic Player";r.command="media/session";r.videoId=MediaRecordPolicy.digest("presentation");r.title="一段很长的中文标题 A long English title for the complete focused description";r.positionKnown=false;
            RecentTile tile=new RecentTile(a,r,(Covers)field(a,"covers"));String description=String.valueOf(tile.getContentDescription());
            check(description.contains(r.title)&&description.contains(r.service()),"recent description retains full title and source");check(description.contains("进度未提供")&&description.contains("操作："+a.recordAction(r)),"recent description separates unknown progress and actual action");check("打开应用".equals(a.recordAction(r)),"generic fallback is described as opening the app");
            r.positionKnown=true;r.position=61000;r.duration=120000;tile.updateDescription();check(tile.getContentDescription().toString().contains(r.progress()),"updated recent description uses the supplied progress");
            android.content.res.Configuration original=new android.content.res.Configuration(a.getResources().getConfiguration());try{android.content.res.Configuration enlarged=new android.content.res.Configuration(original);enlarged.fontScale=1.6f;a.getResources().updateConfiguration(enlarged,a.getResources().getDisplayMetrics());for(int direction:new int[]{View.LAYOUT_DIRECTION_LTR,View.LAYOUT_DIRECTION_RTL}){tile.setLayoutDirection(direction);tile.layout(0,0,a.dp(240),a.dp(185));android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(tile.getWidth(),tile.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);tile.draw(new android.graphics.Canvas(bitmap));bitmap.recycle();}check(true,"recent card draws at large font scale in both layout directions");}finally{a.getResources().updateConfiguration(original,a.getResources().getDisplayMetrics());}
        });
    }
    private void emptyAndOrganize(MainActivity a,SharedPreferences home,SharedPreferences history)throws Exception{
        main(()->{home.edit().putBoolean("home_split_rows",true).putBoolean("home_show_favorites",true).putString("groups","[{\"id\":\"favorites\",\"title\":\"Favorites\",\"row\":true},{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]").commit();history.edit().putBoolean("enabled",false).putBoolean("generic_enabled",false).remove("records").commit();set(a,"favorites",new HashSet<String>());a.loadGroups();a.render();});frames(a);
        main(()->{
            check(rowContaining(a,"empty|recent")==-1&&!hasHeading(a,R.string.heading_continue_playing),
                    "disabled playback with no records has no placeholder");
            history.edit().putBoolean("enabled",true).commit();a.render();
        });frames(a);
        main(()->{View root=(View)field(a,"root"),favorites=root.findViewWithTag("empty|favorites"),recent=root.findViewWithTag("empty|recent");check(favorites!=null&&recent!=null&&favorites.isFocusable()&&recent.isFocusable(),"empty favorites and enabled history provide reachable actions");favorites.requestFocus();});frames(a);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);frames(a);
        main(()->{check("empty|recent".equals(a.getCurrentFocus().getTag()),"remote moves from empty favorites to empty history");a.getCurrentFocus().performClick();});frames(a);
        main(()->{Dialog dialog=(Dialog)field(a,"sheet");check(dialog!=null&&dialog.isShowing(),"empty history opens playback settings");if(dialog!=null)dialog.dismiss();history.edit().putBoolean("enabled",true).putBoolean("generic_enabled",false).commit();a.render();});frames(a);
        main(()->{android.widget.TextView recent=((View)field(a,"root")).findViewWithTag("empty|recent");check(recent!=null&&recent.getText().toString().equals(a.getString(R.string.empty_recent_detection_off)),"disabled media detection has a distinct empty explanation");history.edit().putBoolean("generic_enabled",true).commit();a.render();});frames(a);
        main(()->{android.widget.TextView recent=((View)field(a,"root")).findViewWithTag("empty|recent");if(!GenericMediaHistory.lowRamListenerUnsupported(a)&&!GenericMediaHistory.permission(a))check(recent!=null&&recent.getText().toString().equals(a.getString(R.string.empty_recent_permission)),"optional notification access is reported without treating it as the cause of all missing records");home.edit().remove("order_favorites").putString("groups","[{\"id\":\"favorites\",\"title\":\"Favorites\",\"row\":false},{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]").commit();set(a,"favorites",new HashSet<>(Arrays.asList("local.quiettv.synthetic.app0","local.quiettv.synthetic.app1","local.quiettv.synthetic.app2")));a.loadGroups();a.render();});frames(a);
        main(()->{View first=((View)field(a,"root")).findViewWithTag("favorites|local.quiettv.synthetic.app0");first.requestFocus();first.performLongClick();});frames(a);choose(a,2);
        main(()->{a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));check(!home.contains("order_favorites"),"split favorites retains horizontal movement with an old grid preference");a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));check(home.contains("order_favorites"),"right moves a split favorite and saves order");a.getCurrentFocus().performClick();home.edit().remove("order_favorites").commit();a.render();});frames(a);
        main(()->{View first=((View)field(a,"root")).findViewWithTag("favorites|local.quiettv.synthetic.app0");check(first!=null,"synthetic favorite is available to organize");if(first!=null){first.requestFocus();first.performLongClick();}});frames(a);choose(a,1);
        main(()->check(a.getCurrentFocus()!=null&&"favorites|local.quiettv.synthetic.app1".equals(a.getCurrentFocus().getTag()),"removing a favorite keeps focus on the next item"));
        main(()->a.getCurrentFocus().performLongClick());frames(a);choose(a,3);
        main(()->{check(a.getCurrentFocus()!=null&&"favorites|local.quiettv.synthetic.app2".equals(a.getCurrentFocus().getTag()),"hiding an item keeps focus on the next visible item");home.edit().remove("home_split_rows").remove("home_show_favorites").commit();a.render();});frames(a);
    }
    private void moveCase(MainActivity a,SharedPreferences home,SharedPreferences history,String group,boolean split,boolean grid)throws Exception{
        final String prefix="local.quiettv.synthetic.app",key=group+"|"+prefix+"0";
        main(()->{
            home.edit().putBoolean("home_split_rows",split).putBoolean("home_show_favorites",true)
                    .remove("order_"+group).putString("groups","[{\"id\":\"favorites\",\"title\":\"Favorites\",\"row\":false},{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]").commit();
            history.edit().putBoolean("enabled",true).putBoolean("generic_enabled",true).remove("records").commit();
            PlaybackRecord record=new PlaybackRecord();record.source="local.quiettv.checks";record.sourceLabel="Fixture";record.command="media/session";record.videoId=MediaRecordPolicy.digest("move-check");record.title="Synthetic display record";record.updated=System.currentTimeMillis();new HistoryStore(a).put(record);
            set(a,"hidden",new HashSet<String>());set(a,"favorites",new HashSet<>(Arrays.asList(prefix+"0",prefix+"1",prefix+"2")));
            Properties properties=new Properties();properties.setProperty("grid.columns","5");set(a,"ui",new UiConfig(properties));a.loadGroups();a.render();
        });frames(a);
        final Object[] page={null};
        main(()->{page[0]=field(a,"desktopPage");View tile=((View)field(a,"root")).findViewWithTag(key);if(tile==null)throw new AssertionError("Missing "+key);tile.requestFocus();tile.performLongClick();});frames(a);choose(a,2);
        main(()->{
            check(page[0]==field(a,"desktopPage"),group+" "+split+" enters move mode without replacing the desktop");
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));
            check(!home.contains("order_"+group),group+" "+split+" ignores movement beyond the first item");
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,grid?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_RIGHT));
            check(page[0]==field(a,"desktopPage"),group+" "+split+" moves without replacing the desktop");
            check(a.getCurrentFocus()!=null&&key.equals(a.getCurrentFocus().getTag()),group+" "+split+" keeps focus on the moved app");
            ArrayList<String> order=a.readOrder(group);int destination=grid?5:1;
            check(order.size()>destination&&order.get(destination).equals(prefix+"0")&&order.get(0).equals(prefix+destination),group+" "+split+" persists the same swap order");
        });frames(a);
        main(()->{
            if(grid)a.onBackPressed();else a.getCurrentFocus().performClick();
            check(page[0]==field(a,"desktopPage"),group+" "+split+" finishes moving without replacing the desktop");
            check(a.getCurrentFocus()!=null&&key.equals(a.getCurrentFocus().getTag()),group+" "+split+" retains focus after finishing");
            a.getCurrentFocus().performLongClick();
        });frames(a);
        main(()->{Dialog dialog=(Dialog)field(a,"sheet");android.widget.TextView title=dialog==null?null:dialog.getWindow().getDecorView().findViewWithTag("settings-title");check(title!=null&&"App 00".contentEquals(title.getText()),group+" "+split+" opens the moved app's own menu");if(dialog!=null)dialog.dismiss();a.render();});frames(a);
        main(()->{View tile=((View)field(a,"root")).findViewWithTag(key);check(tile!=null&&tile.getParent() instanceof MainActivity.CardRow&&((MainActivity.CardRow)tile.getParent()).indexOfChild(tile)==(grid?0:1),group+" "+split+" restores the moved order after rendering");});
    }
    private void moveCases(MainActivity a,SharedPreferences home,SharedPreferences history)throws Exception{
        moveCase(a,home,history,"favorites",false,false);
        moveCase(a,home,history,"favorites",true,false);
        moveCase(a,home,history,"all",true,true);
    }
    private void rowTransition(MainActivity a,SharedPreferences home)throws Exception{
        final String prefix="all|local.quiettv.synthetic.app";
        main(()->{home.edit().remove("order_all").putString("groups","[{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]").commit();a.loadGroups();a.render();});frames(a);
        main(()->{View first=((View)field(a,"root")).findViewWithTag(prefix+"0");if(first==null)throw new AssertionError("Missing first transition card");first.requestFocus();});frames(a);
        main(()->{
            View target=((View)field(a,"root")).findViewWithTag(prefix+"5");if(target==null)throw new AssertionError("Missing next transition card");
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
            ActivityManager manager=(ActivityManager)a.getSystemService(Context.ACTIVITY_SERVICE);
            boolean animate=!expectLowRamPath&&(manager==null||!manager.isLowRamDevice())&&(Build.VERSION.SDK_INT<26||android.animation.ValueAnimator.areAnimatorsEnabled());
            check(a.getCurrentFocus()==target,"vertical transition selects the next row");
            check(animate?target.getTranslationY()>0:target.getTranslationY()==0,"vertical transition follows the low-memory and system-animation settings");
        });frames(a);
        main(()->{View target=a.getCurrentFocus();check(target!=null&&Math.abs(target.getTranslationY())<.01f,"vertical transition settles at the original position");check(target!=null&&target.getParent() instanceof MainActivity.CardRow&&((MainActivity.CardRow)target.getParent()).fullyVisible(),"vertical transition leaves the focused row complete");
            for(int i=0;i<3;i++)a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
            for(int i=0;i<2;i++)a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));
        });frames(a);
        main(()->{View target=a.getCurrentFocus();check(target!=null&&(prefix+"10").equals(target.getTag()),"rapid vertical keys keep the most recent focus target");check(target!=null&&target.getParent() instanceof MainActivity.CardRow&&((MainActivity.CardRow)target.getParent()).fullyVisible(),"rapid vertical keys keep the focused row complete");});
    }
    private void capacity(MainActivity a,String label)throws Exception {
        main(()->{if(((ArrayList<?>)field(a,"apps")).size()!=42)throw new AssertionError("Synthetic layout fixture was replaced during "+label);});
        final int[] actual={0},expected={0};final boolean[] focused={false};main(()->{View focus=a.getCurrentFocus();actual[0]=visible(a);if(focus==null||!(focus.getParent() instanceof MainActivity.CardRow))return;MainActivity.CardRow row=(MainActivity.CardRow)focus.getParent();focused[0]=row.fullyVisible();android.graphics.Rect bounds=row.cardBounds();MainActivity.CardScroll scroll=(MainActivity.CardScroll)field(a,"scroll");View content=(View)field(a,"content");HashSet<android.graphics.Rect> bands=new HashSet<>();for(MainActivity.CardRow r:rows(a))bands.add(r.cardBounds());for(int y=Math.max(0,bounds.bottom-scroll.getHeight());y<=Math.min(bounds.top,content.getHeight()-scroll.getHeight());y++){int n=0;for(android.graphics.Rect r:bands)if(r.top>=y&&r.bottom<=y+scroll.getHeight())n++;expected[0]=Math.max(expected[0],n);}});
        if(!focused[0]||actual[0]!=expected[0])main(()->{View focus=a.getCurrentFocus();MainActivity.CardScroll scroll=(MainActivity.CardScroll)field(a,"scroll");Bundle diagnostic=new Bundle();diagnostic.putString("layout",label+" focus="+(focus==null?"none":focus.getClass().getSimpleName()+"/"+focus.getTag())+" scroll="+scroll.getScrollY()+" height="+scroll.getHeight()+" rows="+rows(a).size());sendStatus(0,diagnostic);});
        check(focused[0],label+" focused row is complete");check(actual[0]==expected[0],label+" complete-row capacity "+actual[0]+" == "+expected[0]);
    }
    @Override public void onStart(){Bundle result=new Bundle();MainActivity activity=null;try{
        boolean virtual=Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator")||(Build.FINGERPRINT.startsWith("google/sdk_google_atv_x86/emulator_x86_arm:14/")&&"ranchu".equals(Build.HARDWARE));if(!virtual)throw new AssertionError("Only a disposable emulator may run regressions");
        Context c=getTargetContext();SharedPreferences home=c.getSharedPreferences("home",0),history=c.getSharedPreferences("playback_history",0);
        home.edit().putBoolean("setup_seen",true).putString("groups","[{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]").putString("wallpaper_kind","builtin").commit();history.edit().putBoolean("enabled",false).commit();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);c.startActivity(new Intent().setComponent(new ComponentName(c,MainActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=(MainActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);if(activity==null)throw new AssertionError("No launcher");final MainActivity a=activity;frames(a);Thread.sleep(1200);frames(a);
        // Play Store package updates may arrive minutes after boot. This test
        // owns an in-memory 42-app layout, not the real installed-app list.
        // Isolate only this fixture from package discovery; production package
        // observation remains enabled in every normal launcher and other suite.
        main(()->{
            if((Boolean)field(a,"packageReceiverRegistered")){a.unregisterReceiver((BroadcastReceiver)field(a,"packagesChanged"));set(a,"packageReceiverRegistered",false);}
            ((Handler)field(a,"handler")).removeCallbacks((Runnable)field(a,"reloadApps"));
            a.loadApps();
        });
        // Drain any scan already in flight before substituting synthetic data.
        long scanDeadline=SystemClock.uptimeMillis()+10000;boolean settled=false;
        while(SystemClock.uptimeMillis()<scanDeadline){final boolean[] ready={false};main(()->ready[0]=!(Boolean)field(a,"appsLoading")&&!(Boolean)field(a,"appsDirty"));if(ready[0]){settled=true;break;}frames(a);}
        if(!settled)throw new AssertionError("Initial app discovery did not finish");
        for(int scenario:moveOnly?new int[]{25}:focusRefreshOnly||transitionOnly?new int[]{5}:new int[]{5,6,15,16,25,26}){final int columns=scenario%10;
            main(()->{
                Dialog sheet=(Dialog)field(a,"sheet");if(sheet!=null)sheet.dismiss();ArrayList list=(ArrayList)field(a,"apps");Object template=list.get(0);Class<?> type=template.getClass();Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);list.clear();
                for(int i=0;i<42;i++){Object item=constructor.newInstance();for(String name:new String[]{"intent","icon","banner","color"})set(item,name,field(template,name));set(item,"pkg","local.quiettv.synthetic.app"+i);set(item,"name",String.format(Locale.US,"App %02d",i));list.add(item);}
                home.edit().putString("groups",scenario>=10?"[{\"id\":\"favorites\",\"title\":\"Favorites\",\"row\":true},{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]":"[{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]").commit();set(a,"favorites",new HashSet<>(Arrays.asList("local.quiettv.synthetic.app0","local.quiettv.synthetic.app1","local.quiettv.synthetic.app2")));history.edit().putBoolean("enabled",scenario>=20).putBoolean("generic_enabled",true).remove("records").commit();if(scenario>=20){PlaybackRecord r=new PlaybackRecord();r.source="local.quiettv.checks";r.sourceLabel="Fixture";r.command="media/session";r.videoId=MediaRecordPolicy.digest("display");r.title="Synthetic display record";r.updated=System.currentTimeMillis();new HistoryStore(c).put(r);}
                Properties properties=new Properties();properties.setProperty("grid.columns",String.valueOf(columns));set(a,"ui",new UiConfig(properties));a.loadGroups();a.render();
            });frames(a);
            if(moveOnly){moveCases(a,home,history);result.putString(failures.isEmpty()?"passed":"failed",count+" move checks; failures="+failures.toString());finish(failures.isEmpty()?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);return;}
            if(transitionOnly){rowTransition(a,home);result.putString(failures.isEmpty()?"passed":"failed",count+" transition checks; failures="+failures.toString());finish(failures.isEmpty()?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);return;}
            final int[] initial={0};main(()->initial[0]=visible(a));check(initial[0]>=1,"scenario "+scenario+" starts with complete rows");
            // Delayed package events rebuild the desktop while a remote target is
            // below the initial viewport. Restore visibility after actual layout.
            final Object[] selected={null};main(()->{ArrayList<ArrayList<View>> graph=(ArrayList<ArrayList<View>>)field(a,"focusRows");ArrayList<View> last=graph.get(graph.size()-1);View v=last.get(last.size()-1);v.requestFocus();selected[0]=v.getTag();});frames(a);
            holdLayout(a,true);try{main(a::render);frames(a);}finally{holdLayout(a,false);}frames(a);final boolean[] restored={false};main(()->{View v=a.getCurrentFocus();android.graphics.Rect rect=new android.graphics.Rect();restored[0]=v!=null&&selected[0].equals(v.getTag())&&v.getGlobalVisibleRect(rect)&&rect.width()>0&&rect.height()>0&&v.getParent() instanceof MainActivity.CardRow&&((MainActivity.CardRow)v.getParent()).fullyVisible();});
            check(restored[0],"scenario "+scenario+" refresh restores the selected offscreen card only after layout");
            final Object[] moved={null};holdLayout(a,true);try{main(()->{a.render();a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));moved[0]=a.getCurrentFocus().getTag();});frames(a);}finally{holdLayout(a,false);}frames(a);main(()->{View v=a.getCurrentFocus();restored[0]=v!=null&&moved[0].equals(v.getTag())&&v.getParent() instanceof MainActivity.CardRow&&((MainActivity.CardRow)v.getParent()).fullyVisible();});
            check(restored[0],"scenario "+scenario+" keys during refresh keep the newer target visible without stale focus callbacks");
            if(focusRefreshOnly)continue;
            // Follow real DPAD focus transitions through enough rows to move the viewport,
            // then return to a middle row that should recover the initial visible capacity.
            main(()->rows(a).get(0).getChildAt(0).requestFocus());frames(a);
            for(int cycle=0;cycle<3;cycle++){
                for(int i=0;i<3;i++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);frames(a);}
                for(int i=0;i<2;i++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);frames(a);}
                capacity(a,"scenario "+scenario+" cycle "+cycle);
                main(()->rows(a).get(0).getChildAt(0).requestFocus());frames(a);
            }
            main(()->rows(a).get(0).getChildAt(0).requestFocus());frames(a);
            for(int step=0;step<5;step++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);frames(a);capacity(a,"scenario "+scenario+" down "+step);}
            for(int step=0;step<4;step++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);frames(a);capacity(a,"scenario "+scenario+" up "+step);}
        }
        if(focusRefreshOnly){result.putString(failures.isEmpty()?"passed":"failed",count+" focus refresh checks; failures="+failures.toString());finish(failures.isEmpty()?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);return;}
        homeLayout(a,home,history);
        inputVisibility(a,home);
        recentPresentation(a);
        emptyAndOrganize(a,home,history);
        moveCases(a,home,history);
        rowTransition(a,home);
        Wallpapers wallpaper=(Wallpapers)field(a,"wallpapers");File video=SyntheticVideo.create(new File(c.getCacheDir(),"synthetic-wallpaper.mp4"));CountDownLatch imported=new CountDownLatch(1);final boolean[] ok={false};wallpaper.importFile(video,(success,isVideo)->{ok[0]=success&&isVideo;imported.countDown();});check(imported.await(10,TimeUnit.SECONDS)&&ok[0],"synthetic silent video imports");main(a::render);
        long deadline=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<deadline){frames(a);final boolean[] ready={false};main(()->ready[0]=wallpaper.videoCapability().isAvailable());if(ready[0])break;}
        final Object[] original=new Object[4];final long[] before={0};main(()->{original[0]=field(wallpaper,"layer");original[1]=field(wallpaper,"texture");original[2]=field(wallpaper,"surface");original[3]=field(wallpaper,"player");before[0]=(Long)field(wallpaper,"videoFrames");});check(original[3]!=null&&before[0]>0,"video decoder produces actual frames");
        for(int i=0;i<6;i++){
            // Cross a real playback boundary between settings changes. A completed
            // round may legitimately replace its decoder, but render itself must not.
            if(i==3)Thread.sleep(4500);
            final boolean[] stable={false};main(()->{Object decoder=field(wallpaper,"player");home.edit().putBoolean("banners",!home.getBoolean("banners",true)).commit();a.render();stable[0]=decoder==field(wallpaper,"player");});
            frames(a);main(()->stable[0]&=original[0]==field(wallpaper,"layer")&&original[1]==field(wallpaper,"texture")&&original[2]==field(wallpaper,"surface"));check(stable[0],"settings refresh "+i+" preserves the video layer and Surface, and keeps the decoder during render");
        }
        Thread.sleep(4500);frames(a);final boolean[] playing={false};main(()->playing[0]=wallpaper.videoCapability().isAvailable()&&(Long)field(wallpaper,"videoFrames")>before[0]);check(playing[0],"video keeps producing frames after refreshes and a loop");
        result.putString(failures.isEmpty()?"passed":"failed",count+" display checks; failures="+failures.toString());finish(failures.isEmpty()?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }catch(Throwable error){result.putString("failed",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
