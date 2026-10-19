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
    private boolean focusRefreshOnly;
    private int count;private final ArrayList<String> failures=new ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);focusRefreshOnly=args!=null&&"true".equals(args.getString("focusRefreshOnly"));start();}
    interface Work {void run()throws Exception;}
    private void main(Work work){final Throwable[] failure={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new RuntimeException(failure[0]);}
    private Object field(Object owner,String name)throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void set(Object owner,String name,Object value)throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private void check(boolean ok,String text){count++;Bundle progress=new Bundle();progress.putString("check",(ok?"PASS ":"FAIL ")+text);sendStatus(0,progress);if(!ok)failures.add(text);}
    private void frames(Activity activity)throws Exception {CountDownLatch latch=new CountDownLatch(1);main(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(latch::countDown)));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("No frames");Thread.sleep(220);}
    private void holdLayout(MainActivity a,boolean hold)throws Exception {if(Build.VERSION.SDK_INT>=29)main(()->ViewGroup.class.getMethod("suppressLayout",boolean.class).invoke(field(a,"root"),hold));}
    @SuppressWarnings("unchecked") private ArrayList<MainActivity.CardRow> rows(MainActivity a)throws Exception {return (ArrayList<MainActivity.CardRow>)field(a,"cardRows");}
    private int visible(MainActivity activity)throws Exception {int n=0;HashSet<android.graphics.Rect> bands=new HashSet<>();for(MainActivity.CardRow row:rows(activity))if(row.fullyVisible()&&bands.add(row.cardBounds()))n++;return n;}
    private void capacity(MainActivity a,String label)throws Exception {
        main(()->{if(((ArrayList<?>)field(a,"apps")).size()!=42)throw new AssertionError("Synthetic layout fixture was replaced during "+label);});
        final int[] actual={0},expected={0};final boolean[] focused={false};main(()->{View focus=a.getCurrentFocus();actual[0]=visible(a);if(focus==null||!(focus.getParent() instanceof MainActivity.CardRow))return;MainActivity.CardRow row=(MainActivity.CardRow)focus.getParent();focused[0]=row.fullyVisible();android.graphics.Rect bounds=row.cardBounds();MainActivity.CardScroll scroll=(MainActivity.CardScroll)field(a,"scroll");View content=(View)field(a,"content");HashSet<android.graphics.Rect> bands=new HashSet<>();for(MainActivity.CardRow r:rows(a))bands.add(r.cardBounds());for(int y=Math.max(0,bounds.bottom-scroll.getHeight());y<=Math.min(bounds.top,content.getHeight()-scroll.getHeight());y++){int n=0;for(android.graphics.Rect r:bands)if(r.top>=y&&r.bottom<=y+scroll.getHeight())n++;expected[0]=Math.max(expected[0],n);}});
        if(!focused[0]||actual[0]!=expected[0])main(()->{View focus=a.getCurrentFocus();MainActivity.CardScroll scroll=(MainActivity.CardScroll)field(a,"scroll");Bundle diagnostic=new Bundle();diagnostic.putString("layout",label+" focus="+(focus==null?"none":focus.getClass().getSimpleName()+"/"+focus.getTag())+" scroll="+scroll.getScrollY()+" height="+scroll.getHeight()+" rows="+rows(a).size());sendStatus(0,diagnostic);});
        check(focused[0],label+" focused row is complete");check(actual[0]==expected[0],label+" complete-row capacity "+actual[0]+" == "+expected[0]);
    }
    @Override public void onStart(){Bundle result=new Bundle();MainActivity activity=null;try{
        boolean virtual=Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator");if(!virtual)throw new AssertionError("Only a disposable emulator may run regressions");
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
        for(int scenario:focusRefreshOnly?new int[]{5}:new int[]{5,6,15,16,25,26}){final int columns=scenario%10;
            main(()->{
                Dialog sheet=(Dialog)field(a,"sheet");if(sheet!=null)sheet.dismiss();ArrayList list=(ArrayList)field(a,"apps");Object template=list.get(0);Class<?> type=template.getClass();Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);list.clear();
                for(int i=0;i<42;i++){Object item=constructor.newInstance();for(String name:new String[]{"intent","icon","banner","color"})set(item,name,field(template,name));set(item,"pkg","local.quiettv.synthetic.app"+i);set(item,"name",String.format(Locale.US,"App %02d",i));list.add(item);}
                home.edit().putString("groups",scenario>=10?"[{\"id\":\"favorites\",\"title\":\"Favorites\",\"row\":true},{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]":"[{\"id\":\"all\",\"title\":\"Synthetic grid\",\"row\":false}]").commit();set(a,"favorites",new HashSet<>(Arrays.asList("local.quiettv.synthetic.app0","local.quiettv.synthetic.app1","local.quiettv.synthetic.app2")));history.edit().putBoolean("enabled",scenario>=20).putBoolean("generic_enabled",true).remove("records").commit();if(scenario>=20){PlaybackRecord r=new PlaybackRecord();r.source="local.quiettv.checks";r.sourceLabel="Fixture";r.command="media/session";r.videoId=MediaRecordPolicy.digest("display");r.title="Synthetic display record";r.updated=System.currentTimeMillis();new HistoryStore(c).put(r);}
                Properties properties=new Properties();properties.setProperty("grid.columns",String.valueOf(columns));set(a,"ui",new UiConfig(properties));a.loadGroups();a.render();
            });frames(a);
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
