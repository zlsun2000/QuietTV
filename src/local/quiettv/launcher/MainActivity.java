package local.quiettv.launcher;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native TV UI; independently implemented, with FLauncher and Emotn UI references. */
public class MainActivity extends Activity {
    private static final int WHITE=0xfff2f5fa, MUTED=0xffa9b6c7;
    private SharedPreferences prefs;
    private Set<String> hidden, favorites;
    private final ArrayList<App> apps=new ArrayList<>();
    private final ArrayList<Group> groups=new ArrayList<>();
    private final ArrayList<ArrayList<View>> focusRows=new ArrayList<>();
    private final ArrayList<CardRow> cardRows=new ArrayList<>();
    private final ArrayList<SectionHeading> sectionHeadings=new ArrayList<>();
    private final HashMap<String,App> appMap=new HashMap<>();
    private final Handler handler=new Handler();
    private final ExecutorService storageWorker=Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private LinearLayout content;
    private CardScroll scroll;
    private TextView clock,dateLabel,hint,selectionTitle,selectionSubtitle;
    private Dialog sheet;
    private String selected="",moving="";
    private boolean manage=false;
    private int gridColumns=5,theme=0;
    private float uiScale=1.5f;
    private int designWidth=1280;
    private InputSources inputSources;
    private UiConfig ui;
    private Wallpapers wallpapers;
    private WeatherRepository weather;
    private WeatherButton weatherButton;
    private HistoryStore history;
    private Covers covers;
    private BiliMetadata biliMetadata;
    private boolean historyReceiverRegistered;
    private String historyLayout="";
    private final HashMap<String,String> recordActions=new HashMap<>();
    private final BroadcastReceiver historyChanged=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(foreground&&prefs!=null&&!manage){refreshHistoryCards();if(biliMetadata!=null)biliMetadata.refresh();}}};
    private boolean foreground=false,startupShown=false,unlockReceiverRegistered=false,packageReceiverRegistered=false,timeReceiverRegistered=false,appsDirty=true,appsLoading=false,appsUiDirty=true;
    private final Runnable waitForUnlock=new Runnable(){public void run(){if(foreground&&prefs==null)resumeHome();}};
    private final Runnable resizeHome=new Runnable(){public void run(){if(!foreground||prefs==null||root==null)return;int w=root.getWidth(),h=root.getHeight();if(w<=0||h<=0)return;float scale=UiMetrics.scale(w,h);int units=UiMetrics.units(w,h);if(Math.abs(uiScale-scale)>.001f||designWidth!=units){uiScale=scale;designWidth=units;render();}}};
    private final BroadcastReceiver userUnlockReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(Intent.ACTION_USER_UNLOCKED.equals(i.getAction())&&foreground&&prefs==null)resumeHome();}};
    private static class App {String pkg,name;Intent intent;Drawable icon,banner;int color;InputSources.Port port;}
    private static class Group {String id,title;boolean row;Group(String i,String t,boolean r){id=i;title=t;row=r;}}
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){public void onReceive(Context c,Intent i){appsDirty=true;if(foreground&&prefs!=null){handler.removeCallbacks(reloadApps);handler.postDelayed(reloadApps,400);}}};
    private final Runnable reloadApps=new Runnable(){public void run(){if(foreground&&prefs!=null)loadApps();}};
    private final BroadcastReceiver timeChanged=new BroadcastReceiver(){public void onReceive(Context c,Intent i){tickClock();}};
    private final Runnable tick=new Runnable(){public void run(){tickClock();refreshInputs();handler.postDelayed(this,60000);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null)selected=state.getString("quiettv.focus","");
        ui=UiConfig.load(this);
        updateUiMetrics();
        getWindow().getDecorView().setSystemUiVisibility(5894);
        if(Build.VERSION.SDK_INT>=24){registerReceiver(userUnlockReceiver,new IntentFilter(Intent.ACTION_USER_UNLOCKED));unlockReceiverRegistered=true;}
        IntentFilter timeFilter=new IntentFilter();timeFilter.addAction(Intent.ACTION_TIME_CHANGED);timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);timeFilter.addAction(Intent.ACTION_DATE_CHANGED);registerReceiver(timeChanged,timeFilter);timeReceiverRegistered=true;
    }
    private boolean userStorageReady(){if(Build.VERSION.SDK_INT<24)return true;UserManager users=(UserManager)getSystemService(USER_SERVICE);return users!=null&&users.isUserUnlocked();}
    private void updateUiMetrics(){
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int w=getWindow().getDecorView().getWidth(),h=getWindow().getDecorView().getHeight();
        if(w<=0||h<=0){w=metrics.widthPixels;h=metrics.heightPixels;}
        if(w>0&&h>0){uiScale=UiMetrics.scale(w,h);designWidth=UiMetrics.units(w,h);}
    }
    private void initHome(){
        // Existing credential-protected preferences and media stay in place.
        // Never access them before Android finishes unlocking the user.
        prefs=getSharedPreferences("home",MODE_PRIVATE);
        boolean freshInstall=!prefs.contains("config_schema")&&prefs.getAll().isEmpty();
        if(!prefs.contains("config_schema")){boolean existing=!prefs.getAll().isEmpty();prefs.edit().putInt("config_schema",1).putBoolean("setup_seen",existing).apply();}
        hidden=new HashSet<>(prefs.getStringSet("hidden",Collections.<String>emptySet()));
        favorites=new HashSet<>(prefs.getStringSet("favorites",Collections.<String>emptySet()));
        theme=prefs.getInt("theme",0);inputSources=new InputSources(this);inputSources.observe(this::applyInputScan);wallpapers=new Wallpapers(this,prefs);loadGroups();
        if(freshInstall&&!prefs.contains("wallpaper_motion")&&isLowRamDevice())prefs.edit().putBoolean("wallpaper_motion",false).apply();
        history=new HistoryStore(this);covers=new Covers(this);biliMetadata=new BiliMetadata(this);
        weather=new WeatherRepository(this,new WeatherRepository.Changed(){public void changed(){if(weatherButton!=null)weatherButton.invalidate();}});
        registerReceiver(historyChanged,new IntentFilter(HistoryStore.CHANGED),"local.quiettv.launcher.permission.INTERNAL",null);historyReceiverRegistered=true;
        IntentFilter packages=new IntentFilter();packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addAction(Intent.ACTION_PACKAGE_REPLACED);packages.addDataScheme("package");registerReceiver(packagesChanged,packages);packageReceiverRegistered=true;
    }
    private void resumeHome(){
        handler.removeCallbacks(waitForUnlock);
        getWindow().getDecorView().setSystemUiVisibility(5894);updateUiMetrics();
        if(prefs==null){
            if(!userStorageReady()){showStartup();handler.postDelayed(waitForUnlock,750);return;}
            initHome();
        }
        confirmHomeKeyReturn(getIntent());
        boolean needsRender=appsDirty||appsUiDirty||root==null;loadApps();wallpapers.resume();weather.start();if(needsRender)render();else refreshHistoryCards();handler.removeCallbacks(tick);tick.run();
        if(!prefs.getBoolean("setup_seen",false))handler.post(()->{if(foreground&&sheet==null){prefs.edit().putBoolean("setup_seen",true).apply();showSetup();}});
    }
    private void showStartup(){
        if(startupShown)return;startupShown=true;
        root=new FrameLayout(this);root.setBackgroundColor(0xff09131d);
        ImageView background=new ImageView(this);background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
        background.setImageBitmap(BitmapFactory.decodeResource(getResources(),R.raw.nature_lake,options));
        root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackgroundColor(0x66040b13);root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout label=new LinearLayout(this);label.setOrientation(LinearLayout.VERTICAL);label.setGravity(Gravity.CENTER);
        label.addView(text(getString(R.string.app_name),40,WHITE));label.addView(text(getString(R.string.loading_desktop),18,WHITE));
        root.addView(label,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
    }
    @Override public void onResume(){super.onResume();foreground=true;confirmHomeKeyReturn(getIntent());resumeHome();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);confirmHomeKeyReturn(intent);}
    private void confirmHomeKeyReturn(Intent intent){if(intent!=null&&intent.getBooleanExtra("quiettv.home_key_request",false)&&prefs!=null){prefs.edit().putLong("home_key_last_return",System.currentTimeMillis()).remove("home_key_pending").apply();intent.removeExtra("quiettv.home_key_request");}}
    private boolean isLowRamDevice(){try{android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);return manager!=null&&manager.isLowRamDevice();}catch(Exception ignored){return false;}}
    @Override public void onPause(){foreground=false;handler.removeCallbacks(waitForUnlock);handler.removeCallbacks(resizeHome);handler.removeCallbacks(tick);if(weather!=null)weather.stop();if(wallpapers!=null)wallpapers.pause();super.onPause();}
    @Override public void onSaveInstanceState(Bundle out){out.putString("quiettv.focus",selected);super.onSaveInstanceState(out);}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(wallpapers!=null)wallpapers.trimMemory(level);}
    @Override public void onDestroy(){handler.removeCallbacks(waitForUnlock);handler.removeCallbacks(resizeHome);handler.removeCallbacks(tick);handler.removeCallbacks(reloadApps);if(unlockReceiverRegistered)unregisterReceiver(userUnlockReceiver);if(timeReceiverRegistered)unregisterReceiver(timeChanged);if(historyReceiverRegistered)unregisterReceiver(historyChanged);if(packageReceiverRegistered)unregisterReceiver(packagesChanged);storageWorker.shutdownNow();if(inputSources!=null)inputSources.dispose();if(weather!=null)weather.dispose();if(covers!=null)covers.dispose();if(biliMetadata!=null)biliMetadata.dispose();if(wallpapers!=null)wallpapers.dispose();super.onDestroy();}
    int dp(float n){return Math.round(n*uiScale);}
    int sp(float n){return Math.round(dp(n)*getResources().getConfiguration().fontScale);}
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,sp(size));t.setTextColor(color);t.setFontFeatureSettings("kern");t.setIncludeFontPadding(false);return t;}
    void loadGroups(){
        groups.clear();try{JSONArray a=new JSONArray(prefs.getString("groups","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);groups.add(new Group(o.getString("id"),o.getString("title"),o.optBoolean("row")));}}catch(JSONException ignored){}
        if(groups.isEmpty()){groups.add(new Group("favorites",getString(R.string.group_favorites),true));groups.add(new Group("all",getString(R.string.group_all_apps),false));}
        if(findGroup("inputs")==null)groups.add(Math.max(0,groups.size()-1),new Group("inputs",getString(R.string.group_inputs),true));
    }
    void saveGroups(){
        JSONArray a=new JSONArray();for(Group g:groups){JSONObject o=new JSONObject();try{o.put("id",g.id);o.put("title",g.title);o.put("row",g.row);a.put(o);}catch(JSONException ignored){}}prefs.edit().putString("groups",a.toString()).apply();
    }
    void save(){prefs.edit().putStringSet("hidden",new HashSet<>(hidden)).putStringSet("favorites",new HashSet<>(favorites)).apply();}
    void loadApps(){
        if(!appsDirty||appsLoading)return;appsDirty=false;appsLoading=true;final PackageManager pm=getPackageManager();final String ownPackage=getPackageName();
        if(inputSources!=null)inputSources.scanAsync(this::applyInputScan);
        try{storageWorker.execute(()->{final ArrayList<App> found=new ArrayList<>();final HashSet<String> seen=new HashSet<>();for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            List<ResolveInfo> matches;try{matches=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0);}catch(Exception ignored){continue;}
            for(ResolveInfo ri:matches){if(ri==null||ri.activityInfo==null)continue;String pkg=ri.activityInfo.packageName;if(pkg==null||pkg.equals(ownPackage)||seen.contains(pkg))continue;try{ApplicationInfo ai=pm.getApplicationInfo(pkg,0);if(!ai.enabled||!ri.activityInfo.enabled)continue;CharSequence label=ri.loadLabel(pm);Drawable icon=ri.loadIcon(pm);if(label==null||icon==null)continue;App app=new App();app.pkg=pkg;app.name=label.toString();app.icon=icon;try{app.banner=ri.activityInfo.loadBanner(pm);}catch(Exception ignored){}app.color=iconColor(icon);app.intent=new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(new ComponentName(pkg,ri.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);found.add(app);seen.add(pkg);}catch(Exception ignored){}}
        }Collections.sort(found,new Comparator<App>(){public int compare(App a,App b){return a.name.compareToIgnoreCase(b.name);}});handler.post(()->{appsLoading=false;if(isFinishing()||isDestroyed())return;if(appsDirty){loadApps();return;}ArrayList<App> currentInputs=appsSnapshotInputs();appMap.clear();apps.clear();for(App app:found){appMap.put(app.pkg,app);apps.add(app);}for(App current:currentInputs){appMap.put(current.pkg,current);apps.add(current);}Collections.sort(apps,new Comparator<App>(){public int compare(App a,App b){return a.name.compareToIgnoreCase(b.name);}});if(!prefs.getBoolean("favorites_initialized",false)){if(!prefs.contains("favorites")){for(App app:apps)if(app.port==null&&!hidden.contains(app.pkg)){favorites.add(app.pkg);break;}}save();prefs.edit().putBoolean("favorites_initialized",true).apply();}appsUiDirty=true;if(foreground)render();});});}catch(Exception ignored){appsLoading=false;appsDirty=true;}
    }
    private ArrayList<App> appsSnapshotInputs(){ArrayList<App> result=new ArrayList<>();for(App old:apps)if(old.port!=null){App current=new App();current.port=old.port;current.pkg=old.pkg;current.name=old.name;current.color=old.color;current.intent=old.port.intent;result.add(current);}return result;}
    int iconColor(Drawable icon){
        try{Bitmap b=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Rect old=new Rect(icon.getBounds());icon.setBounds(0,0,12,12);icon.draw(c);icon.setBounds(old);long r=0,g=0,bl=0,n=0;
            for(int x=0;x<12;x++)for(int y=0;y<12;y++){int p=b.getPixel(x,y);if(Color.alpha(p)>100){r+=Color.red(p);g+=Color.green(p);bl+=Color.blue(p);n++;}}b.recycle();if(n>0)return Color.rgb((int)(r/n*.52+18),(int)(g/n*.52+18),(int)(bl/n*.52+18));
        }catch(Exception ignored){}return 0xff30435a;
    }
    ArrayList<App> groupApps(Group group,boolean includeHidden){
        Set<String> members=group.id.equals("favorites")?favorites:prefs.getStringSet("members_"+group.id,Collections.<String>emptySet());ArrayList<App> result=new ArrayList<>();
        for(App a:apps){if(!includeHidden&&hidden.contains(a.pkg))continue;if(group.id.equals("inputs")?a.port!=null:group.id.equals("all")?a.port==null:members.contains(a.pkg))result.add(a);}
        final ArrayList<String> order=readOrder(group.id);Collections.sort(result,new Comparator<App>(){public int compare(App a,App b){int ai=order.indexOf(a.pkg),bi=order.indexOf(b.pkg);if(ai<0)ai=Integer.MAX_VALUE;if(bi<0)bi=Integer.MAX_VALUE;if(ai!=bi)return Integer.compare(ai,bi);if(group.id.equals("inputs")){int an=a.port.enumName.endsWith("DTV")||a.port.enumName.endsWith("ATV")?0:a.port.number==0?99:a.port.number,bn=b.port.enumName.endsWith("DTV")||b.port.enumName.endsWith("ATV")?0:b.port.number==0?99:b.port.number;return Integer.compare(an,bn);}return a.name.compareToIgnoreCase(b.name);}});return result;
    }
    ArrayList<String> readOrder(String id){ArrayList<String> a=new ArrayList<>();try{JSONArray j=new JSONArray(prefs.getString("order_"+id,"[]"));for(int i=0;i<j.length();i++)a.add(j.getString(i));}catch(JSONException ignored){}return a;}
    void saveOrder(Group g,ArrayList<App> list){JSONArray a=new JSONArray();for(App item:list)a.put(item.pkg);for(String p:readOrder(g.id))if(!containsPackage(list,p))a.put(p);prefs.edit().putString("order_"+g.id,a.toString()).apply();}
    boolean containsPackage(ArrayList<App> list,String p){for(App a:list)if(a.pkg.equals(p))return true;return false;}

    void render(){
        appsUiDirty=false;
        // Adding fresh focusable views may trigger Android's automatic initial focus.
        // Keep the requested card stable while those focus callbacks rebuild the page.
        final String desiredSelection=selected;
        refreshRecordActions();
        root=new FrameLayout(this);wallpapers.attach(root,theme);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);root.addView(page,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        final FrameLayout measuredRoot=root;
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int oldL,int oldT,int oldR,int oldB){if(root==measuredRoot&&foreground&&r>l&&b>t){handler.removeCallbacks(resizeHome);handler.post(resizeHome);}}});
        focusRows.clear();cardRows.clear();sectionHeadings.clear();gridColumns=ui.columns;
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(48),dp(12),dp(48),dp(6));
        TextView brand=text(getString(R.string.app_name),ui.brandSize,WHITE);brand.setLetterSpacing(.12f);brand.setTypeface(Typeface.create("sans-serif-medium",0));header.addView(brand,new LinearLayout.LayoutParams(dp(76),-2));
        ArrayList<View> tools=new ArrayList<>();weatherButton=null;if(weather.enabled()){weatherButton=new WeatherButton(this,weather,new Runnable(){public void run(){showWeather();}});header.addView(weatherButton,new LinearLayout.LayoutParams(dp(ui.weatherWidth),dp(42)));tools.add(weatherButton);}header.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));if(hasInputs()||SystemEntrypoints.inputSettings(this)!=null)tools.add(tool(getString(R.string.toolbar_inputs),"input",0,new Runnable(){public void run(){focusInputs();}}));tools.add(tool(getString(R.string.toolbar_tv_settings),"settings",1,new Runnable(){public void run(){openSettings();}}));tools.add(tool(getString(R.string.toolbar_home_settings),"home-settings",2,new Runnable(){public void run(){showSettings();}}));for(View t:tools)if(t!=weatherButton)header.addView(t);focusRows.add(tools);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.BOTTOM);hero.setPadding(dp(48),0,dp(48),dp(8));
        LinearLayout timeBlock=new LinearLayout(this);timeBlock.setOrientation(LinearLayout.VERTICAL);clock=text("",ui.clockSize,WHITE);clock.setTypeface(Typeface.create("sans-serif-light",0));timeBlock.addView(clock);
        dateLabel=text("",14,0xffd4dde2);LinearLayout.LayoutParams dateLp=new LinearLayout.LayoutParams(-2,-2);dateLp.topMargin=dp(4);timeBlock.addView(dateLabel,dateLp);hero.addView(timeBlock,new LinearLayout.LayoutParams(dp(200),-2));
        hero.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setGravity(Gravity.END);selectionTitle=text("",26,WHITE);selectionTitle.setSingleLine(true);selectionTitle.setEllipsize(TextUtils.TruncateAt.END);selectionTitle.setGravity(Gravity.END);selectionTitle.setTypeface(Typeface.create("sans-serif-medium",0));details.addView(selectionTitle);
        selectionSubtitle=text("",12,0xffc6d3da);selectionSubtitle.setSingleLine(true);selectionSubtitle.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(-2,-2);detailLp.topMargin=dp(7);details.addView(selectionSubtitle,detailLp);hero.addView(details,new LinearLayout.LayoutParams(dp(490),-2));page.addView(hero,new LinearLayout.LayoutParams(-1,dp(96)));
        scroll=new CardScroll(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setClipChildren(true);scroll.setClipToPadding(true);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(36),dp(12),dp(36),dp(25));content.setClipChildren(false);content.setClipToPadding(false);scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(manage){addCategory(findGroup("inputs"),"整理输入源",true);addCategory(findGroup("all"),"整理应用",true);}else {
            Group favoriteGroup=findGroup("favorites");boolean primary=!history.visible().isEmpty()&&favoriteGroup!=null&&!groupApps(favoriteGroup,false).isEmpty();
            if(primary)addPrimary(favoriteGroup);
            boolean recentAdded=primary;for(Group g:groups){if(primary&&g.id.equals("favorites"))continue;if(!recentAdded&&!g.id.equals("favorites")){addRecent();recentAdded=true;}addCategory(g,g.title,false);}if(!recentAdded)addRecent();
        }
        hint=text(manage?getString(R.string.remote_organize_hint):getString(R.string.remote_navigation_hint),11,0xffa3b4bf);hint.setPaddingRelative(dp(48),dp(4),dp(48),dp(8));page.addView(hint);
        historyLayout=historyFingerprint();View target=desiredSelection.isEmpty()?null:root.findViewWithTag(desiredSelection);if(target==null)for(ArrayList<View> row:focusRows){if(!row.isEmpty()&&row.get(0).getParent() instanceof CardRow){target=row.get(0);break;}}if(target==null)target=tools.get(0);target.requestFocus();tickClock();
    }
    Group findGroup(String id){for(Group g:groups)if(g.id.equals(id))return g;return null;}
    // The first band groups applications and one complete content card side by side.
    // Both panes share vertical visibility and a single left/right focus sequence.
    void addPrimary(final Group favoriteGroup){
        ArrayList<App> favoriteApps=groupApps(favoriteGroup,false);ArrayList<PlaybackRecord> records=history.visible();
        LinearLayout band=new LinearLayout(this);band.setOrientation(LinearLayout.HORIZONTAL);band.setClipChildren(false);band.setClipToPadding(false);
        int available=designWidth-72,leftWidth=Math.round((available-24)*ui.favoriteRatio),rightWidth=available-24-leftWidth;
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setClipChildren(false);band.addView(left,new LinearLayout.LayoutParams(dp(leftWidth),-2));
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setClipChildren(false);LinearLayout.LayoutParams rightLp=new LinearLayout.LayoutParams(dp(rightWidth),-2);rightLp.leftMargin=dp(24);band.addView(right,rightLp);
        SectionHeading leftHeading=primaryHeading(getString(R.string.heading_favorite_apps),favoriteApps.size()+" 个应用"),rightHeading=primaryHeading(getString(R.string.heading_continue_playing),records.size()+" 项");left.addView(leftHeading);right.addView(rightHeading);
        PaneStrip appStrip=new PaneStrip(this);
        CardRow appRow=new CardRow(this);appRow.visibilityBand=band;appRow.heading=leftHeading;leftHeading.row=appRow;appRow.setPadding(dp(12),dp(6),dp(12),dp(10));appRow.setClipChildren(false);appRow.setClipToPadding(false);cardRows.add(appRow);
        ArrayList<View> focuses=new ArrayList<>();int appWidth=(leftWidth-24-18)/2;
        for(int index=0;index<favoriteApps.size();index++){Tile tile=makeTile(favoriteApps.get(index),favoriteGroup);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(appWidth),dp(appWidth*9f/16+27));lp.rightMargin=index<favoriteApps.size()-1?dp(18):0;appRow.addView(tile,lp);focuses.add(tile);}
        appStrip.addView(appRow);left.addView(appStrip,new LinearLayout.LayoutParams(dp(leftWidth),-2));
        RecentStrip recentStrip=new RecentStrip(this);CardRow recentRow=new CardRow(this);recentRow.visibilityBand=band;recentRow.heading=rightHeading;rightHeading.row=recentRow;recentRow.setPadding(dp(12),dp(6),dp(12),dp(10));recentRow.setClipChildren(false);recentRow.setClipToPadding(false);cardRows.add(recentRow);int recentWidth=rightWidth-24;
        for(int index=0;index<records.size();index++){RecentTile tile=recentTile(records.get(index));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(recentWidth),dp(recentWidth*9f/16+50));lp.rightMargin=index<records.size()-1?dp(24):0;recentRow.addView(tile,lp);focuses.add(tile);}
        recentStrip.addView(recentRow);right.addView(recentStrip,new LinearLayout.LayoutParams(dp(rightWidth),-2));content.addView(band);focusRows.add(focuses);content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(14)));
    }
    SectionHeading primaryHeading(String title,String count){SectionHeading heading=new SectionHeading(this);sectionHeadings.add(heading);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(12),0,dp(12),dp(4));TextView label=text(title,17,WHITE);label.setTypeface(Typeface.create("sans-serif-medium",0));heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));heading.addView(text(count,11,0xff9bafbc));return heading;}
    RecentTile recentTile(final PlaybackRecord record){RecentTile tile=new RecentTile(this,record,covers);tile.setId(View.generateViewId());tile.setOnClickListener(v->resumeRecord(record));tile.setOnLongClickListener(v->{panel(record.title,record.details()+(record.generic()?"\n应用提供的播放信息；重启后能否续播由原应用决定":""),new String[]{recordAction(record),"从桌面移除",record.apple()?"打开 Apple Music":record.bili()?"B 站全部历史":"打开 "+record.service()},new Choice(){public void pick(int i){if(i==0)resumeRecord(record);else if(i==1){history.remove(record.key());render();}else if(record.bili())openBiliHistory();else openPackage(record.source);}});return true;});return tile;}
    void refreshRecordActions(){recordActions.clear();recordActions.putAll(GenericMediaHistory.actions(this,history.visible()));}
    String recordAction(PlaybackRecord r){String value=recordActions.get(r.key());return value==null?r.actionLabel():value;}
    String historyFingerprint(){StringBuilder key=new StringBuilder();for(PlaybackRecord r:history.visible())key.append(r.key()).append('\n').append(r.title).append('\n').append(r.episode).append('\n').append(r.cover).append('\n').append(r.command).append('\n').append(r.routeId).append('\n').append(r.episodeId).append('\n').append(r.routeChannel).append('\n').append(r.sourceLabel).append('\n').append(r.audio).append('\n');return key.toString();}
    void refreshHistoryCards(){if(!historyLayout.equals(historyFingerprint())){render();return;}refreshRecordActions();HashMap<String,PlaybackRecord> latest=new HashMap<>();for(PlaybackRecord r:history.visible())latest.put(r.key(),r);for(ArrayList<View> row:focusRows)for(View view:row)if(view instanceof RecentTile){PlaybackRecord current=((RecentTile)view).record,fresh=latest.get(current.key());if(fresh!=null){current.position=fresh.position;current.duration=fresh.duration;current.positionKnown=fresh.positionKnown;current.updated=fresh.updated;((RecentTile)view).updateDescription();view.invalidate();}}View focused=getCurrentFocus();if(focused instanceof RecentTile)updateSelection(focused);}
    void resumeRecord(PlaybackRecord record){if(record.generic()){GenericMediaHistory.open(this,record);return;}if(record.apple()){openPackage(record.source);return;}resumeBili(record);}
    void addRecent(){
        ArrayList<PlaybackRecord> records=history.visible();if(records.isEmpty())return;
        SectionHeading heading=new SectionHeading(this);sectionHeadings.add(heading);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPaddingRelative(dp(12),0,dp(12),dp(4));TextView label=text(getString(R.string.heading_continue_playing),17,WHITE);label.setTypeface(Typeface.create("sans-serif-medium",0));heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));heading.addView(text(records.size()+" 项",11,0xff9bafbc));content.addView(heading);
        PaneStrip horizontal=new PaneStrip(this);CardRow row=new CardRow(this);row.heading=heading;heading.row=row;cardRows.add(row);row.setPadding(dp(12),dp(6),dp(12),dp(10));row.setClipChildren(false);row.setClipToPadding(false);ArrayList<View> focuses=new ArrayList<>();int width=(designWidth-96-3*20)/4;
        for(final PlaybackRecord record:records){RecentTile tile=recentTile(record);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(width),dp(width*9f/16+50));lp.rightMargin=record==records.get(records.size()-1)?0:dp(20);row.addView(tile,lp);focuses.add(tile);}
        horizontal.addView(row);content.addView(horizontal);focusRows.add(focuses);content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(6)));
    }
    void resumeBili(PlaybackRecord record){
        if(!record.valid()){openPackage(PlaybackRecord.PACKAGE);return;}
        try{startActivity(BiliResume.intent(record));}catch(Exception e){openBiliHistory();}
    }
    void openBiliHistory(){
        Uri uri=new Uri.Builder().scheme("yst").authority(PlaybackRecord.PACKAGE).appendQueryParameter("type","10").appendQueryParameter("from","quiettv").appendQueryParameter("out","1").build();
        launch(new Intent(Intent.ACTION_VIEW,uri).setPackage(PlaybackRecord.PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),"B 站历史");
    }
    void showWeather(){
        if(!weather.consented()){weatherConsent();return;}
        WeatherRepository.Data d=weather.data();String title=d==null?getString(R.string.weather_title):d.place.name+" · "+weather.degrees(d.temperature)+" · "+weather.conditionLabel(d.code);StringBuilder detail=new StringBuilder();
        if(d==null)detail.append(weather.message());else{for(int i=0;i<d.days.size();i++){WeatherRepository.Day day=d.days.get(i);detail.append(weather.dayLabel(day,System.currentTimeMillis())).append("  ").append(weather.conditionLabel(day.code)).append("  ").append(weather.degrees(day.low)).append(" — ").append(weather.degrees(day.high)).append('\n');}detail.append(getString(R.string.weather_updated,android.text.format.DateFormat.getTimeFormat(this).format(new Date(d.updated)))).append(d.stale()?getString(R.string.weather_stale):"");if(!weather.message().isEmpty())detail.append('\n').append(weather.message());}
        detail.append('\n').append(getString(R.string.weather_attribution));
        String unit=weather.unitLabel();
        panel(title,detail.toString(),new String[]{getString(R.string.weather_refresh),getString(R.string.weather_change_city),getString(R.string.temperature_unit)+" · "+unit,getString(R.string.weather_terms),getString(R.string.weather_disable)},i->{if(i==0){weather.refresh(true);Toast.makeText(this,R.string.weather_refreshing,Toast.LENGTH_SHORT).show();}else if(i==1)searchWeatherCity();else if(i==2){weather.fahrenheit(!weather.fahrenheit());showWeather();}else if(i==3)weatherServiceInfo();else{weather.enable(false);render();}});
    }
    void weatherConsent(){panel(getString(R.string.weather_consent_title),getString(R.string.weather_consent_body),new String[]{getString(R.string.weather_consent_accept),getString(R.string.weather_consent_view_terms),getString(R.string.weather_consent_cancel)},i->{if(i==0){weather.consent("manual");weather.enable(true);render();searchWeatherCity();}else if(i==1)weatherServiceInfo();});}
    void weatherServiceInfo(){panel(getString(R.string.weather_sources_title),getString(R.string.weather_sources_body),new String[]{getString(R.string.weather_source_open_meteo),getString(R.string.weather_source_cc_by),getString(R.string.weather_source_geonames),getString(R.string.weather_source_terms),getString(R.string.back)},i->{String[] urls={"https://open-meteo.com/en/licence","https://creativecommons.org/licenses/by/4.0/","https://www.geonames.org/about.html","https://open-meteo.com/en/terms"};if(i<urls.length)launch(new Intent(Intent.ACTION_VIEW,Uri.parse(urls[i])),getString(R.string.weather_title));});}
    void searchWeatherCity(){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(WHITE);input.setTextSize(18);input.setHint(R.string.weather_search_hint);input.setHintTextColor(MUTED);input.setPaddingRelative(dp(16),dp(15),dp(16),dp(15));input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.weather_select_city_title).setView(input).setPositiveButton(R.string.search,null).setNegativeButton(R.string.cancel,null).create();dialog.setOnShowListener(v->{final Runnable search=new Runnable(){public void run(){final String query=input.getText().toString().trim();if(query.isEmpty()||query.length()>80)return;dialog.dismiss();Toast.makeText(MainActivity.this,R.string.weather_searching,Toast.LENGTH_SHORT).show();weather.search(query,new WeatherRepository.Cities(){public void found(final ArrayList<WeatherRepository.Place> places,String error){if(!foreground)return;if(!error.isEmpty()){Toast.makeText(MainActivity.this,error,Toast.LENGTH_LONG).show();return;}if(places.isEmpty()){Toast.makeText(MainActivity.this,R.string.weather_city_not_found,Toast.LENGTH_LONG).show();return;}String[] names=new String[places.size()+1];for(int i=0;i<places.size();i++)names[i]=places.get(i).label();names[places.size()]="Open-Meteo / GeoNames";panel(getString(R.string.weather_select_city_title),getString(R.string.weather_city_source),names,new Choice(){public void pick(int i){if(i==places.size())weatherServiceInfo();else weather.select(places.get(i));}});}});}};dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v2->search.run());input.setOnEditorActionListener((v2,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){search.run();return true;}return false;});});dialog.show();input.requestFocus();
    }
    void showContentSettings(){panel("天气与继续播放","记录只保存在电视，合计最多 8 项",new String[]{weather.enabled()?"天气 · 已开启":"天气 · 已关闭","天气与城市设置",history.enabled()?"播放记录 · 已开启":"播放记录 · 已关闭",history.genericEnabled()?"其他应用自动识别 · 已开启":"其他应用自动识别 · 已关闭","媒体识别 · "+GenericMediaHistory.availability(this),"按应用管理播放记录","B 站全部历史","清空桌面播放记录"},i->{if(i==0){if(!weather.consented())weatherConsent();else{weather.enable(!weather.enabled());render();}}else if(i==1){if(!weather.consented()){weatherConsent();return;}if(!weather.enabled()){weather.enable(true);render();}showWeather();}else if(i==2){history.enabled(!history.enabled());render();}else if(i==3){history.genericEnabled(!history.genericEnabled());render();}else if(i==4)showMediaPermission();else if(i==5)showMediaApps();else if(i==6)openBiliHistory();else panel("清空桌面记录？","应用里的历史和音乐队列会保留",new String[]{"取消","清空"},n->{if(n==1){history.clear();render();}});});}
    void showMediaPermission(){panel("媒体识别权限",GenericMediaHistory.availability(this)+"\nAndroid 通过通知访问权限开放媒体会话。静幕只读取播放标题、应用提供的副标题、封面与进度，不读取或保存通知正文。\n只打开应用的卡片不保证重启后恢复内容；仍在运行的同一可识别内容才可能恢复当前播放。",new String[]{"打开系统授权设置","返回"},new Choice(){public void pick(int i){if(i==0){Intent settings=SystemEntrypoints.notificationAccessSettings(MainActivity.this);if(settings==null)Toast.makeText(MainActivity.this,"系统未提供通知访问设置入口",Toast.LENGTH_LONG).show();else launch(settings,"通知访问设置");}}});}
    void showMediaApps(){final ArrayList<App> eligible=new ArrayList<>();for(App app:apps)if(app.port==null&&GenericMediaHistory.eligible(this,app.pkg))eligible.add(app);if(eligible.isEmpty()){Toast.makeText(this,"尚无可识别的其他应用",Toast.LENGTH_LONG).show();return;}String[] labels=new String[eligible.size()];for(int i=0;i<labels.length;i++){App a=eligible.get(i);labels[i]=a.name+(history.appEnabled(a.pkg)?" · 已开启":" · 已关闭");}panel("按应用管理记录","只有公开播放信息的应用才会产生记录。关闭后隐藏已有记录，并停止新增。",labels,new Choice(){public void pick(int i){App a=eligible.get(i);history.appEnabled(a.pkg,!history.appEnabled(a.pkg));render();showMediaApps();}});}
    boolean hasInputs(){for(App a:apps)if(a.port!=null)return true;return false;}
    void focusInputs(){for(ArrayList<View> row:focusRows)for(View v:row)if(v instanceof Tile&&((Tile)v).group.id.equals("inputs")){v.requestFocus();return;}Intent fallback=SystemEntrypoints.inputSettings(this);if(fallback!=null)launch(fallback,"系统输入源设置");else Toast.makeText(this,"请使用遥控器输入源键或电视设置",Toast.LENGTH_LONG).show();}
    String inputStatus(InputSources.Port p){if(p.enumName.endsWith("DTV")||p.enumName.endsWith("ATV"))return "天线 / 有线电视";return p.state==0?(p.number>0?"未接入":"未检测到信号"):p.state==1?(p.device.isEmpty()?"已接入 · 设备名未知":p.device):"状态暂不可读";}
    void refreshInputs(){if(inputSources!=null)inputSources.scanAsync(this::applyInputScan);}
    private void applyInputScan(ArrayList<InputSources.Port> ports){
        if(!foreground||apps==null)return;HashMap<String,InputSources.Port> found=new HashMap<>();for(InputSources.Port p:ports)found.put(p.key(),p);
        boolean structureChanged=false,labelsChanged=false;
        for(int i=apps.size()-1;i>=0;i--){App a=apps.get(i);if(a.port!=null&&!found.containsKey(a.pkg)){apps.remove(i);appMap.remove(a.pkg);structureChanged=true;if(selected.equals(a.pkg))selected="";}}
        for(InputSources.Port p:ports){App a=appMap.get(p.key());if(a==null){a=new App();a.pkg=p.key();a.name=p.name;a.port=p;a.intent=p.intent;a.color=0xff274453;appMap.put(a.pkg,a);apps.add(a);structureChanged=true;labelsChanged=true;}else{if(!a.name.equals(p.name)){a.name=p.name;labelsChanged=true;}a.port=p;a.intent=p.intent;}}
        if(structureChanged||labelsChanged){Collections.sort(apps,new Comparator<App>(){public int compare(App a,App b){return a.name.compareToIgnoreCase(b.name);}});render();return;}
        for(ArrayList<View> row:focusRows)for(View v:row)if(v instanceof Tile&&((Tile)v).app.port!=null){Tile t=(Tile)v;t.setContentDescription(t.app.name+"，"+inputStatus(t.app.port));t.invalidate();}
        View focused=getCurrentFocus();if(focused instanceof Tile&&((Tile)focused).app.port!=null)updateSelection(focused);
    }
    void addCategory(final Group group,String title,boolean includeHidden){
        if(group==null||(group.id.equals("inputs")&&!hasInputs()))return;
        ArrayList<App> items=groupApps(group,includeHidden);SectionHeading heading=new SectionHeading(this);sectionHeadings.add(heading);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(12),0,dp(12),dp(4));TextView label=text(title,17,WHITE);label.setTypeface(Typeface.create("sans-serif-medium",0));heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));heading.addView(text(items.size()+(group.id.equals("inputs")?" 个入口":" 个应用"),11,0xff9bafbc));content.addView(heading);
        boolean row=group.row&&!manage;if(items.isEmpty()){TextView empty=text(group.id.equals("favorites")?getString(R.string.empty_favorites_hint):getString(R.string.empty_group_hint),14,MUTED);empty.setPaddingRelative(dp(20),dp(25),dp(20),dp(25));content.addView(empty);return;}
        if(row){
            PaneStrip h=new PaneStrip(this);CardRow line=new CardRow(this);line.heading=heading;heading.row=line;cardRows.add(line);line.setPadding(dp(12),dp(6),dp(12),dp(10));line.setClipChildren(false);line.setClipToPadding(false);ArrayList<View> focuses=new ArrayList<>();
            boolean sources=group.id.equals("inputs");int cardWidth=(designWidth-96-(sources?3:gridColumns-1)*18)/(sources?4:gridColumns);
            for(int i=0;i<items.size();i++){App a=items.get(i);Tile tile=makeTile(a,group);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(cardWidth),dp(sources?84:cardWidth*9f/16+27));lp.rightMargin=i<items.size()-1?dp(18):0;line.addView(tile,lp);focuses.add(tile);}h.addView(line);content.addView(h);focusRows.add(focuses);
        }else{
            int available=designWidth-96;int cardWidth=(available-(gridColumns-1)*18)/gridColumns;
            for(int i=0;i<items.size();i+=gridColumns){CardRow line=new CardRow(this);if(i==0){line.heading=heading;heading.row=line;}cardRows.add(line);line.setPadding(dp(12),dp(6),dp(12),dp(24));line.setClipChildren(false);line.setClipToPadding(false);ArrayList<View> focuses=new ArrayList<>();
                for(int j=0;j<gridColumns;j++){View v;if(i+j<items.size()){v=makeTile(items.get(i+j),group);focuses.add(v);}else v=new View(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(cardWidth),dp(cardWidth*9f/16+27));lp.rightMargin=j<gridColumns-1?dp(18):0;line.addView(v,lp);}content.addView(line);focusRows.add(focuses);
            }
        }
        View gap=new View(this);content.addView(gap,new LinearLayout.LayoutParams(1,dp(6)));
    }
    Tile makeTile(final App a,final Group group){
        final Tile t=new Tile(this,a,group);t.setTag(group.id+"|"+a.pkg);t.setId(View.generateViewId());t.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(!moving.isEmpty()){moving="";render();return;}if(manage){if(hidden.contains(a.pkg))hidden.remove(a.pkg);else hidden.add(a.pkg);save();t.setContentDescription(a.name+(hidden.contains(a.pkg)?"，已隐藏":"，已显示"));t.invalidate();hint.setText(a.name+(hidden.contains(a.pkg)?" 已隐藏":" 已显示"));}else launch(a.intent,a.name);}});
        t.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){selected=(String)t.getTag();showAppMenu(a,group);return true;}});return t;
    }
    View tool(String label,String tag,int glyph,final Runnable action){final Tool t=new Tool(this,label,glyph);t.setTag(tag);t.setId(View.generateViewId());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(92),dp(36));lp.leftMargin=dp(10);t.setLayoutParams(lp);t.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});return t;}
    void focusChanged(final View v,boolean focused){
        v.animate().scaleX(focused?1.045f:1).scaleY(focused?1.045f:1).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();v.setTranslationZ(focused?dp(8):0);v.invalidate();if(focused){selected=(String)v.getTag();updateSelection(v);if(hint!=null)hint.setText(!moving.isEmpty()?"方向键移动卡片    确认键或返回键完成排序":manage?"确认键隐藏 / 显示应用    返回键完成整理":"方向键选择    确认键打开    长按确认键整理");v.post(new Runnable(){public void run(){if(v.isFocused()&&scroll!=null){if(v.getParent() instanceof CardRow)scroll.revealTile(v);else scroll.scrollTo(0,0);Rect r=new Rect(-dp(8),-dp(8),v.getWidth()+dp(8),v.getHeight()+dp(8));v.requestRectangleOnScreen(r,false);if(v.getParent() instanceof CardRow&&((View)v.getParent()).getParent() instanceof RecentStrip)((RecentStrip)((View)v.getParent()).getParent()).scrollTo(Math.max(0,v.getLeft()-dp(12)),0);}}});}
    }
    // Keep partially visible rows in the focus graph, but paint only complete cards.
    // This rule applies to every scroll position, including repeated up/down moves.
    class SectionHeading extends LinearLayout {
        CardRow row;
        SectionHeading(Context c){super(c);}
        @Override protected void dispatchDraw(Canvas canvas){if(row==null||row.fullyVisible())super.dispatchDraw(canvas);}
    }
    class PaneStrip extends HorizontalScrollView {
        private final Paint indicatorPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path indicatorPath=new Path();
        PaneStrip(Context c){super(c);setHorizontalScrollBarEnabled(false);setClipChildren(true);setClipToPadding(true);}
        @Override protected void dispatchDraw(Canvas canvas){
            // Ancestors allow focus enlargement, so enforce this pane's viewport explicitly.
            int saved=canvas.save();canvas.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());super.dispatchDraw(canvas);
            if(getChildCount()>0&&getChildAt(0) instanceof CardRow){
                CardRow row=(CardRow)getChildAt(0);
                if(row.fullyVisible()&&row.getChildCount()>0){
                    View first=row.getChildAt(0),last=row.getChildAt(row.getChildCount()-1);float y=getScrollY()+first.getTop()+(first.getHeight()-dp(first instanceof RecentTile?48:27))/2f;
                    // Ignore blank edge padding; arrows describe hidden cards, not spare scroll range.
                    if(first.getLeft()<getScrollX()-dp(1))drawMore(canvas,getScrollX()+dp(16),y,-1);
                    if(last.getRight()>getScrollX()+getWidth()+dp(1))drawMore(canvas,getScrollX()+getWidth()-dp(16),y,1);
                }
            }
            canvas.restoreToCount(saved);
        }
        void drawMore(Canvas canvas,float x,float y,int direction){indicatorPaint.setStyle(Paint.Style.FILL);indicatorPaint.setColor(0xcc172633);canvas.drawCircle(x,y,dp(12),indicatorPaint);indicatorPaint.setStyle(Paint.Style.STROKE);indicatorPaint.setStrokeWidth(dp(1.8f));indicatorPaint.setStrokeCap(Paint.Cap.ROUND);indicatorPaint.setStrokeJoin(Paint.Join.ROUND);indicatorPaint.setColor(0xffedf4f6);indicatorPath.reset();indicatorPath.moveTo(x-direction*dp(3),y-dp(5));indicatorPath.lineTo(x+direction*dp(3),y);indicatorPath.lineTo(x-direction*dp(3),y+dp(5));canvas.drawPath(indicatorPath,indicatorPaint);}
        @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);invalidate();}
    }
    class RecentStrip extends PaneStrip {
        RecentStrip(Context c){super(c);}
    }
    class CardRow extends LinearLayout {
        SectionHeading heading;
        View visibilityBand;
        CardRow(Context c){super(c);}
        Rect cardBounds(){
            Rect bounds=new Rect();if(getChildCount()==0||content==null)return bounds;
            if(visibilityBand!=null){bounds.set(0,0,visibilityBand.getWidth(),visibilityBand.getHeight());content.offsetDescendantRectToMyCoords(visibilityBand,bounds);bounds.inset(0,-dp(8));return bounds;}
            View card=getChildAt(0);bounds.set(0,0,card.getWidth(),card.getHeight());
            content.offsetDescendantRectToMyCoords(card,bounds);if(heading!=null){Rect title=new Rect(0,0,heading.getWidth(),heading.getHeight());content.offsetDescendantRectToMyCoords(heading,title);bounds.top=Math.min(bounds.top,title.top);}bounds.inset(0,-dp(8));return bounds;
        }
        @Override protected void dispatchDraw(Canvas canvas){
            if(fullyVisible())super.dispatchDraw(canvas);
        }
        boolean fullyVisible(){Rect bounds=cardBounds();if(scroll==null||bounds.height()<=0)return false;int top=scroll.getScrollY(),bottom=top+scroll.getHeight();return bounds.top>=top&&bounds.bottom<=bottom;}
    }
    class CardScroll extends ScrollView {
        CardScroll(Context c){super(c);}
        // Vertical focus is handled below. Horizontal rows still reveal their cards normally.
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){return 0;}
        void revealTile(View tile){
            if(!(tile.getParent() instanceof CardRow)||getHeight()<=0)return;
            CardRow row=(CardRow)tile.getParent();Rect bounds=row.cardBounds();int y=getScrollY();
            if(!cardRows.isEmpty()&&row==cardRows.get(0))y=0;
            else if(bounds.top<y)y=bounds.top;
            else if(bounds.bottom>y+getHeight())y=bounds.bottom-getHeight();
            // Snap the focused row fully into view instead of exposing a thin neighbouring row.
            scrollTo(0,Math.max(0,y));
        }
        @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){
            super.onScrollChanged(x,y,oldX,oldY);
            // Rebuild row display lists when scrolling changes their visibility.
            for(CardRow row:cardRows){row.invalidate();if(row.getParent() instanceof PaneStrip)((PaneStrip)row.getParent()).invalidate();}
            for(SectionHeading heading:sectionHeadings)heading.invalidate();
        }
    }
    void updateSelection(View v){if(selectionTitle==null)return;if(v instanceof Tile){Tile t=(Tile)v;selectionTitle.setText(t.app.name);selectionSubtitle.setText(t.app.port==null?t.group.title:inputStatus(t.app.port));}else if(v instanceof RecentTile){PlaybackRecord r=((RecentTile)v).record;selectionTitle.setText(r.title);selectionSubtitle.setText(r.service()+" · "+r.details());}else if(v instanceof WeatherButton){selectionTitle.setText("天气");selectionSubtitle.setText("确认键查看预报和城市设置");}else if(v instanceof Tool){selectionTitle.setText(((Tool)v).label);selectionSubtitle.setText("主页快捷入口");}}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int code=e.getKeyCode();boolean arrow=code==19||code==20||code==21||code==22;if(arrow&&e.getAction()==KeyEvent.ACTION_DOWN){View focused=getCurrentFocus();if(!moving.isEmpty()&&focused instanceof Tile){moveApp((Tile)focused,code);return true;}
            for(int r=0;r<focusRows.size();r++){ArrayList<View> row=focusRows.get(r);int i=row.indexOf(focused);if(i<0)continue;int nr=r,ni=i;if(code==21)ni--;else if(code==22)ni++;else if(code==19)nr--;else nr++;if(nr>=0&&nr<focusRows.size()){ArrayList<View> next=focusRows.get(nr);if(nr!=r)ni=Math.min(ni,next.size()-1);if(ni>=0&&ni<next.size())next.get(ni).requestFocus();}return true;}
        }return super.dispatchKeyEvent(e);
    }
    void moveApp(Tile tile,int code){ArrayList<App> list=groupApps(tile.group,manage);int index=-1;for(int i=0;i<list.size();i++)if(list.get(i).pkg.equals(tile.app.pkg))index=i;int step=code==21?-1:code==22?1:tile.group.row?0:code==19?-gridColumns:gridColumns;int next=index+step;if(index>=0&&step!=0&&next>=0&&next<list.size()){Collections.swap(list,index,next);saveOrder(tile.group,list);render();}}
    void tickClock(){Date now=new Date();if(clock!=null)clock.setText(DateFormat.getTimeFormat(this).format(now));if(dateLabel!=null){Locale locale=Locale.getDefault();String skeleton=getString(R.string.header_date_skeleton);String pattern=DateFormat.getBestDateTimePattern(locale,skeleton);dateLabel.setText(new SimpleDateFormat(pattern,locale).format(now));}}

    interface Choice {void pick(int index);}
    void panel(String title,String subtitle,final String[] choices,final Choice callback){
        if(sheet!=null)sheet.dismiss();sheet=new Dialog(this);final Dialog current=sheet;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPaddingRelative(dp(28),dp(34),dp(28),dp(24));GradientDrawable panelBackground=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff253a46,0xff101e2b});panelBackground.setStroke(dp(1),0x335d818e);box.setBackground(panelBackground);TextView heading=text(title,25,WHITE);heading.setTypeface(Typeface.create("sans-serif-medium",0));box.addView(heading);
        TextView sub=text(subtitle,13,MUTED);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(10);sp.bottomMargin=dp(24);box.addView(sub,sp);ScrollView list=new ScrollView(this);list.setVerticalScrollBarEnabled(false);LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);list.addView(options);box.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<choices.length;i++){final int index=i;final TextView button=text(choices[i],16,WHITE);button.setPaddingRelative(dp(16),dp(15),dp(16),dp(15));button.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);button.setFocusable(true);button.setClickable(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(6);options.addView(button,lp);button.setBackground(panelButton(false));button.setOnFocusChangeListener(new View.OnFocusChangeListener(){public void onFocusChange(View v,boolean f){button.setBackground(panelButton(f));button.setTextColor(f?0xff142034:WHITE);}});button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){current.dismiss();callback.pick(index);}});}
        TextView back=text("返回键关闭",12,MUTED);back.setPaddingRelative(dp(16),dp(15),0,0);back.setGravity(Gravity.START);box.addView(back);current.setContentView(box);Window w=current.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams lp=w.getAttributes();lp.width=dp(365);lp.height=-1;lp.gravity=Gravity.END;lp.dimAmount=.45f;w.setAttributes(lp);w.getDecorView().setSystemUiVisibility(5894);}current.setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface d){if(sheet==current)sheet=null;}});current.show();if(options.getChildCount()>0)options.getChildAt(0).requestFocus();
    }
    Drawable panelButton(boolean f){GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(10));d.setColor(f?0xffe7edf6:0xff202d42);return d;}
    void showSettings(){panel("桌面设置","按你的习惯，整理自己的主页",new String[]{"整理应用","管理分类","背景与卡片","天气与继续播放","电视系统设置","打开其他桌面","Home 键修复","默认桌面与安装帮助"},new Choice(){public void pick(int i){if(i==0){manage=true;selected="";render();}else if(i==1)showGroups();else if(i==2)showAppearance();else if(i==3)showContentSettings();else if(i==4)openSettings();else if(i==5)openOriginal();else if(i==6)showHomeKeyFix();else showSetup();}});}
    void showAppearance(){panel("背景与卡片","图片缓慢移动，视频静音循环；离开桌面自动暂停",new String[]{"背景 · 自然山湖","自定义图片 / 动态视频",prefs.getBoolean("wallpaper_motion",true)?"图片动效 · 已开启":"图片动效 · 已关闭","配色背景",prefs.getBoolean("banners",true)?"应用横幅 · 已开启":"应用横幅 · 已关闭"},new Choice(){public void pick(int i){if(i==1){chooseWallpaper();return;}if(i==3){chooseTheme();return;}if(i==0)prefs.edit().putString("wallpaper_kind","builtin").apply();else if(i==2)prefs.edit().putBoolean("wallpaper_motion",!prefs.getBoolean("wallpaper_motion",true)).apply();else prefs.edit().putBoolean("banners",!prefs.getBoolean("banners",true)).apply();render();}});}
    void chooseTheme(){panel("配色背景","无需图片或视频，也可使用原来的配色",new String[]{"深海蓝","暮色紫","石墨黑","森林绿"},new Choice(){public void pick(int i){theme=i;prefs.edit().putInt("theme",theme).putString("wallpaper_kind","theme").apply();render();}});}
    void chooseWallpaper(){panel("自定义背景","选定后保存到电视，之后可以移除 U 盘",new String[]{"系统文件选择器","U 盘 / 旧系统文件浏览","已保存背景 · "+wallpapers.librarySummary(),"恢复内置风景"},new Choice(){public void pick(int i){if(i==0){pickDocument();return;}if(i==1){if(checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},902);else browseRoots();}else if(i==2)browseFolder(wallpapers.library(),wallpapers.library());else{prefs.edit().putString("wallpaper_kind","builtin").apply();render();}}});}
    void pickDocument(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"}).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(i,903);}catch(Exception first){try{Intent fallback=new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"}).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(fallback,903);}catch(Exception second){Toast.makeText(this,"系统没有文件选择器，可使用 U 盘 / 旧系统文件浏览",Toast.LENGTH_LONG).show();chooseWallpaper();}}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==903&&result==RESULT_OK&&data!=null&&data.getData()!=null){ProgressDialog progress=new ProgressDialog(this);progress.setTitle("正在导入背景");progress.setMessage("正在复制选定文件…");progress.setIndeterminate(true);progress.setCancelable(true);progress.setOnCancelListener(d->wallpapers.cancelImport());progress.show();wallpapers.importDocument(data.getData(),(ok,video)->{if(progress.isShowing())progress.dismiss();if(!isFinishing())Toast.makeText(this,ok?"已应用背景":"导入失败；请检查格式、容量、存储空间或文件提供器",Toast.LENGTH_LONG).show();if(ok&&!isFinishing())render();});}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==902){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)browseRoots();else Toast.makeText(this,"未授权，仍可使用内置风景和已保存背景",Toast.LENGTH_LONG).show();}}
    void browseRoots(){
        final ArrayList<File> roots=new ArrayList<>();final ArrayList<String> names=new ArrayList<>();File local=Environment.getExternalStorageDirectory();if(local!=null){roots.add(local);names.add("电视本地文件");}File[] storage=new File("/storage").listFiles();if(storage!=null)for(File f:storage){String n=f.getName();if(f.isDirectory()&&f.canRead()&&!n.equals("emulated")&&!n.equals("self")){roots.add(f);names.add("U 盘 / 存储 · "+n);}}roots.add(wallpapers.library());names.add("已保存背景");panel("选择存储位置","只读取你选定的图片或视频",names.toArray(new String[0]),new Choice(){public void pick(int i){browseFolder(roots.get(i),roots.get(i));}});
    }
    void browseFolder(final File folder,final File allowedRoot){
        try{String base=allowedRoot.getCanonicalPath(),path=folder.getCanonicalPath();if(!path.equals(base)&&!path.startsWith(base+File.separator))return;}catch(Exception e){return;}
        storageWorker.execute(()->{File[] files=folder.listFiles();final ArrayList<File> items=new ArrayList<>();if(files!=null)for(File f:files)if(!f.getName().startsWith(".")&&(f.isDirectory()||Wallpapers.picture(f)||Wallpapers.video(f)))items.add(f);Collections.sort(items,new Comparator<File>(){public int compare(File a,File b){if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());}});handler.post(()->{if(!foreground||isFinishing())return;if(files==null){Toast.makeText(MainActivity.this,"这个位置暂时无法读取",Toast.LENGTH_LONG).show();return;}showFolder(folder,allowedRoot,items,0);});});
    }
    private void showFolder(final File folder,final File allowedRoot,final ArrayList<File> items,final int page){final boolean parent=!folder.equals(allowedRoot);final int pageSize=40,start=page*pageSize,end=Math.min(items.size(),start+pageSize);ArrayList<String> labels=new ArrayList<>();labels.add(parent?"返回上一级":"返回存储位置");if(page>0)labels.add("上一页");for(int i=start;i<end;i++){File f=items.get(i);labels.add((f.isDirectory()?"文件夹 · ":Wallpapers.video(f)?"视频 · ":"图片 · ")+f.getName());}if(end<items.size())labels.add("下一页（"+(items.size()-end)+" 项未显示）");panel(folder.equals(allowedRoot)?"选择图片或视频":folder.getName(),"第 "+(page+1)+" 页 · "+items.size()+" 项；图片 50 MB 内、视频 400 MB 内；背景库上限 64 项 / 1 GB",labels.toArray(new String[0]),new Choice(){public void pick(int i){if(i==0){if(parent)browseFolder(folder.getParentFile(),allowedRoot);else browseRoots();return;}int itemStart=1;if(page>0){if(i==1){showFolder(folder,allowedRoot,items,page-1);return;}itemStart++;}int itemIndex=start+i-itemStart;if(itemIndex>=end){showFolder(folder,allowedRoot,items,page+1);return;}File f=items.get(itemIndex);if(f.isDirectory())browseFolder(f,allowedRoot);else if(wallpapers.inLibrary(f))manageSavedWallpaper(f);else importWallpaper(f);}});}
    void importWallpaper(final File f){final ProgressDialog progress=new ProgressDialog(this);progress.setTitle("正在导入背景");progress.setMessage(f.getName());progress.setIndeterminate(true);progress.setCancelable(true);progress.setOnCancelListener(d->wallpapers.cancelImport());progress.show();wallpapers.importFile(f,new Wallpapers.Imported(){public void done(boolean ok,boolean video){if(progress.isShowing())progress.dismiss();if(!isFinishing())Toast.makeText(MainActivity.this,ok?(video?"已应用视频背景":"已应用图片背景"):"导入失败；请检查格式、容量、存储空间或文件读取权限",Toast.LENGTH_LONG).show();if(ok&&!isFinishing())render();}});}
    void manageSavedWallpaper(final File file){String kind=prefs.getString("wallpaper_kind","builtin");final boolean current=(kind.equals("image")||kind.equals("video"))&&file.getAbsolutePath().equals(prefs.getString("wallpaper_path",""));panel("已保存背景",file.getName()+" · "+(file.length()/1048576)+" MB",new String[]{"应用此背景",current?"删除并恢复内置风景":"删除保存文件","返回"},i->{if(i==0)importWallpaper(file);else if(i==1){panel("删除保存背景？",current?"当前背景将先切回内置风景。":"此文件会从静幕的背景库中删除。",new String[]{"取消","删除"},n->{if(n==1){if(wallpapers.deleteSaved(file.getAbsolutePath())){Toast.makeText(this,"已删除",Toast.LENGTH_SHORT).show();render();}else Toast.makeText(this,"无法删除这个文件",Toast.LENGTH_LONG).show();}});}});}
    void showGroups(){ArrayList<String> names=new ArrayList<>();for(Group g:groups)names.add(g.title+" · "+(g.row?"横向一排":"网格"));names.add("＋ 添加分类");panel("管理分类","每个分类可选择横排或网格",names.toArray(new String[0]),new Choice(){public void pick(int i){if(i==groups.size())addGroup();else editGroup(groups.get(i));}});}
    void addGroup(){panel("添加分类","长按应用卡片可添加到分类",new String[]{"影音","工具","游戏","其他","自定义名称"},new Choice(){public void pick(int i){if(i==4)editName(null);else createGroup(new String[]{"影音","工具","游戏","其他"}[i]);}});}
    void createGroup(String title){groups.add(Math.max(0,groups.size()-1),new Group("group-"+System.currentTimeMillis(),title,true));saveGroups();render();showGroups();}
    void editName(final Group group){final EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,sp(18));input.setText(group==null?"":group.title);input.setHint("分类名称");input.setPaddingRelative(dp(16),dp(10),dp(16),dp(10));new AlertDialog.Builder(this).setTitle(group==null?"新分类":"分类名称").setView(input).setPositiveButton("保存",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){String name=input.getText().toString().trim();if(name.isEmpty())return;if(group==null)createGroup(name);else{group.title=name;saveGroups();render();}}}).setNegativeButton("取消",null).show();}
    void editGroup(final Group g){ArrayList<String> options=new ArrayList<>(Arrays.asList("重命名",g.row?"改为网格":"改为横向一排","分类上移","分类下移"));if(!g.id.equals("favorites")&&!g.id.equals("all")&&!g.id.equals("inputs"))options.add("删除此分类");panel(g.title,"只调整桌面的分类，不改变应用状态",options.toArray(new String[0]),new Choice(){public void pick(int i){if(i==0){editName(g);return;}if(i==1)g.row=!g.row;if(i==2||i==3){int at=groups.indexOf(g),next=at+(i==2?-1:1);if(next>=0&&next<groups.size())Collections.swap(groups,at,next);}if(i==4)groups.remove(g);saveGroups();render();showGroups();}});}
    void showAppMenu(final App a,final Group g){panel(a.name,a.port==null?"应用操作":inputStatus(a.port),new String[]{a.port==null?"打开应用":"进入输入源",favorites.contains(a.pkg)?"从常用移除":"添加到常用","移动位置",hidden.contains(a.pkg)?"显示在主页":"从主页隐藏","添加 / 移出分类",a.port==null?"应用信息":"刷新接入状态"},new Choice(){public void pick(int i){if(i==0){launch(a.intent,a.name);return;}if(i==1){if(favorites.contains(a.pkg))favorites.remove(a.pkg);else favorites.add(a.pkg);save();render();}else if(i==2){moving=g.id+"|"+a.pkg;selected=moving;render();}else if(i==3){if(hidden.contains(a.pkg))hidden.remove(a.pkg);else hidden.add(a.pkg);save();render();}else if(i==4)chooseMembership(a);else if(i==5){if(a.port==null){Intent info=SystemEntrypoints.appInfo(MainActivity.this,a.pkg);if(info==null)Toast.makeText(MainActivity.this,"系统未提供应用信息入口",Toast.LENGTH_LONG).show();else launch(info,"应用信息");}else refreshInputs();}}});}
    void chooseMembership(final App a){final ArrayList<Group> custom=new ArrayList<>();ArrayList<String> choices=new ArrayList<>();for(Group g:groups)if(!g.id.equals("favorites")&&!g.id.equals("all")&&!g.id.equals("inputs")){custom.add(g);boolean member=prefs.getStringSet("members_"+g.id,Collections.<String>emptySet()).contains(a.pkg);choices.add((member?"移出 · ":"添加到 · ")+g.title);}if(custom.isEmpty()){addGroup();return;}panel("应用分类",a.name,choices.toArray(new String[0]),new Choice(){public void pick(int i){Group g=custom.get(i);Set<String> m=new HashSet<>(prefs.getStringSet("members_"+g.id,Collections.<String>emptySet()));if(m.contains(a.pkg))m.remove(a.pkg);else m.add(a.pkg);prefs.edit().putStringSet("members_"+g.id,m).apply();render();}});}
    void launch(Intent i,String name){try{if(i==null)throw new ActivityNotFoundException();startActivity(i);}catch(Exception e){Toast.makeText(this,"暂时无法打开"+name,Toast.LENGTH_LONG).show();}}
    void openPackage(String p){Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(p);if(i==null)i=getPackageManager().getLaunchIntentForPackage(p);if(i==null){Toast.makeText(this,"这个应用尚未安装或没有可用入口",Toast.LENGTH_LONG).show();return;}launch(i,"");}
    void openSettings(){Intent i=SystemEntrypoints.settings(this);if(i!=null)launch(i,"设置");else Toast.makeText(this,"请使用遥控器或原桌面的系统设置入口",Toast.LENGTH_LONG).show();}
    void showSetup(){String status=SystemEntrypoints.defaultHome(this)?"静幕已是默认桌面":"静幕尚未设为默认桌面";panel("静幕 · 安装帮助",status+"\n\n基础桌面可以直接使用。天气和播放记录默认关闭，可在设置中按需开启。\n\n按 Home 返回桌面、重启后自动进入桌面分别由固件决定。部分 Google TV 的原厂桌面会优先接管 Home 键，可选开启 Home 键修复。原桌面保持可用，可通过系统默认应用设置恢复。",new String[]{"开始使用","选择默认桌面","Home 键修复","天气与继续播放","打开其他桌面","兼容信息"},i->{if(i==1)chooseDefaultHome();else if(i==2)showHomeKeyFix();else if(i==3)showContentSettings();else if(i==4)openOriginal();else if(i==5)showCompatibility();});}
    void chooseDefaultHome(){Intent i=SystemEntrypoints.homeSettings(this);if(i!=null){launch(i,"默认桌面设置");return;}panel("默认桌面设置","系统未提供通用的默认桌面设置页。可按 Home 查看是否出现桌面选择，选择静幕并设为始终使用；部分厂商需要使用电脑设置。\n\n恢复时在同一入口选择原桌面即可。",new String[]{"打开电视设置","返回"},n->{if(n==0)openSettings();});}
    void showHomeKeyFix(){
        boolean enabled=prefs.getBoolean("home_key_fix",false),service=homeKeyServiceEnabled(),connected=HomeKeyService.connected;long verified=prefs.getLong("home_key_last_return",0),pending=prefs.getLong("home_key_pending",0);String requested=enabled?"Home 键修复 · 已申请":"Home 键修复 · 已关闭";String serviceState=!service?"无障碍服务 · 未启用":connected?"无障碍服务 · 已连接":"无障碍服务 · 等待系统连接";String effect=pending>0&&System.currentTimeMillis()-pending<60000?"等待用户按 Home 验证":verified>0?"最近返回静幕 · "+new SimpleDateFormat("M/d HH:mm",Locale.getDefault()).format(new Date(verified)):"Home 键效果 · 尚未验证";
        panel("Home 键修复",requested+"\n"+serviceState+"\n"+effect+"\n\n部分固件会保留原厂 Home 路由，长按 Home 的系统菜单也可能受影响。先在系统无障碍设置中由你启用服务，再关闭此页并按一次遥控器 Home；返回后状态会记录为已返回。遇到按键异常，可关闭本功能或在系统设置中停用服务。",
            new String[]{requested,serviceState,"准备 Home 键验证","打开系统无障碍设置","返回"},
            new Choice(){public void pick(int i){
                if(i==0){prefs.edit().putBoolean("home_key_fix",!enabled).apply();HomeKeyService.requestFiltering(!enabled);if(!enabled&&!service){Toast.makeText(MainActivity.this,"还差一步：在系统无障碍设置中启用“静幕 · Home 键修复”",Toast.LENGTH_LONG).show();openAccessibilitySettings();}else showHomeKeyFix();}
                else if(i==1){if(service)showHomeKeyFix();else openAccessibilitySettings();}
                else if(i==2){prefs.edit().putLong("home_key_pending",System.currentTimeMillis()).apply();Toast.makeText(MainActivity.this,"请关闭此页后按遥控器 Home；返回静幕后会记录结果",Toast.LENGTH_LONG).show();}
                else if(i==3)openAccessibilitySettings();
            }});
    }
    boolean homeKeyServiceEnabled(){try{String values=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return DevicePolicy.accessibilityServiceEnabled(values,getPackageName(),HomeKeyService.class.getName());}catch(Exception e){return false;}}
    void openAccessibilitySettings(){Intent i=SystemEntrypoints.accessibilitySettings(this);if(i!=null)launch(i,"无障碍设置");else Toast.makeText(this,"系统未提供无障碍设置入口",Toast.LENGTH_LONG).show();}
    void showCompatibility(){
        String version="未知";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);boolean lowRam=manager!=null&&manager.isLowRamDevice();int inputCount=0;for(App app:apps)if(app.port!=null)inputCount++;
        int windowWidth=root==null?0:root.getWidth(),windowHeight=root==null?0:root.getHeight();
        DeviceCapability home=SystemEntrypoints.defaultHome(this)?DeviceCapability.available("home","静幕是当前默认桌面"):DeviceCapability.optional("home","其他桌面或系统不可读");
        DeviceCapability inputs=InputSources.capability(inputCount);
        DeviceCapability media=GenericMediaHistory.capability(this),video=wallpapers.videoCapability();
        DeviceCapability homeSettings=SystemEntrypoints.capability(this,"home_settings","系统未提供默认桌面设置入口",SystemEntrypoints.homeSettings(this));
        DeviceCapability notificationSettings=SystemEntrypoints.capability(this,"notification_settings","系统未提供通知访问设置入口",SystemEntrypoints.notificationAccessSettings(this));
        String detail="静幕 "+version+" · Android "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\n"+Build.MANUFACTURER+" "+Build.MODEL+(lowRam?" · 低内存设备":"")+"\n应用窗口："+windowWidth+" × "+windowHeight+" px\n"+capabilityLine("当前 HOME",home)+"\n"+capabilityLine("输入源",inputs)+"\n"+capabilityLine("媒体识别",media)+"\n"+capabilityLine("视频背景",video)+"\n"+capabilityLine("默认桌面设置",homeSettings)+"\n"+capabilityLine("通知访问设置",notificationSettings)+"\nHome 键修复："+(prefs.getBoolean("home_key_fix",false)?(homeKeyServiceEnabled()?(HomeKeyService.connected?"服务已连接":"服务已启用，等待连接"):"已申请，服务未启用"):"未开启，可选")+"\n\n多厂商实机验证仍在进行。HOME 路由、输入源、解码器和续播能力取决于设备及应用。";
        panel("兼容信息",detail,new String[]{"默认桌面帮助","返回"},i->{if(i==0)showSetup();});
    }
    String capabilityLine(String label,DeviceCapability capability){String state=capability.state==DeviceCapability.State.AVAILABLE?getString(R.string.capability_available):capability.state==DeviceCapability.State.OPTIONAL?getString(R.string.capability_optional):capability.state==DeviceCapability.State.WAITING?getString(R.string.capability_waiting):capability.state==DeviceCapability.State.UNSUPPORTED?getString(R.string.capability_unsupported):getString(R.string.capability_inactive);return label+"："+state+" · "+capability.reason;}
    void openOriginal(){final ArrayList<ResolveInfo> homes=SystemEntrypoints.otherHomes(this);if(homes.isEmpty()){panel("其他桌面","没有检测到可打开的其他桌面。请通过系统默认应用设置恢复；不要停用或卸载原桌面。",new String[]{"打开电视设置","返回"},i->{if(i==0)openSettings();});return;}String[] labels=new String[homes.size()];for(int n=0;n<labels.length;n++)labels[n]=homes.get(n).loadLabel(getPackageManager()).toString();Choice open=n->{ResolveInfo r=homes.get(n);launch(SystemEntrypoints.homeQuery().setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),labels[n]);};if(homes.size()==1)open.pick(0);else panel("打开其他桌面","这次打开不改变默认桌面；长期恢复请到系统默认应用设置选择",labels,open);}
    @Override public void onBackPressed(){if(prefs==null)return;if(!moving.isEmpty()){moving="";render();return;}if(manage){manage=false;selected="";render();return;}panel("返回原厂桌面？","新桌面的设置会自动保存",new String[]{"留在新桌面","返回原厂桌面"},new Choice(){public void pick(int i){if(i==1)openOriginal();}});}

    class Tile extends View {
        final App app;final Group group;final Paint p=new Paint(3);final RectF r=new RectF();final Path clip=new Path();
        Tile(Context c,App a,Group g){super(c);app=a;group=g;setFocusableInTouchMode(true);setClickable(true);setContentDescription(a.name+(a.port!=null?"，"+inputStatus(a.port):hidden.contains(a.pkg)?"，已隐藏":""));setOnFocusChangeListener(new OnFocusChangeListener(){public void onFocusChange(View v,boolean f){focusChanged(v,f);}});}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();boolean f=isFocused(),compact=app.port!=null&&group.id.equals("inputs")&&group.row&&!manage;float bottom=compact?h-dp(3):h-dp(27);r.set(dp(3),dp(3),w-dp(3),bottom);float radius=dp(12);p.setStyle(Paint.Style.FILL);
            p.setColor(f?0x5a030b13:0x35030b13);c.drawRoundRect(new RectF(r.left,r.top+dp(4),r.right,r.bottom+dp(4)),radius,radius,p);
            p.setShader(new LinearGradient(0,0,w,bottom,new int[]{app.port==null?app.color:0xbe273c48,0xea122330},null,Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,p);p.setShader(null);
            c.save();clip.reset();clip.addRoundRect(r,radius,radius,Path.Direction.CW);c.clipPath(clip);
            if(app.port!=null){
                boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;float x=compact?(rtl?w-dp(37):dp(37)):w/2,y=compact?r.centerY():r.centerY()-dp(6);p.setColor(0x22cce0e9);c.drawCircle(x,y,dp(22),p);drawSource(c,x,y);
                if(compact){float textX=rtl?w-dp(73):dp(73);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextAlign(rtl?Paint.Align.RIGHT:Paint.Align.LEFT);p.setColor(WHITE);p.setTextSize(sp(16));c.drawText(app.name,textX,y-dp(3),p);}
                float textX=compact?(rtl?w-dp(73):dp(73)):w/2;p.setTypeface(Typeface.create("sans-serif",0));p.setTextAlign(compact?(rtl?Paint.Align.RIGHT:Paint.Align.LEFT):Paint.Align.CENTER);p.setTextSize(sp(11));p.setColor(app.port.state==1?0xffa5e8cb:0xffa5bac6);String status=TextUtils.ellipsize(inputStatus(app.port),new android.text.TextPaint(p),w-dp(compact?90:20),TextUtils.TruncateAt.END).toString();c.drawText(status,textX,compact?y+dp(17):r.bottom-dp(9),p);
                if(app.port.number>0){p.setColor(app.port.state==1?0xff80dbb2:0xff647984);c.drawCircle(rtl?dp(15):w-dp(15),dp(15),dp(3),p);}
            }
            else if(app.banner!=null&&prefs.getBoolean("banners",true)){int bw=Math.max(1,app.banner.getIntrinsicWidth()),bh=Math.max(1,app.banner.getIntrinsicHeight());float scale=Math.max(r.width()/bw,r.height()/bh);int sw=(int)(bw*scale),sh=(int)(bh*scale);int x=(int)(r.centerX()-sw/2f),y=(int)(r.centerY()-sh/2f);app.banner.setBounds(x,y,x+sw,y+sh);app.banner.draw(c);}
            else{p.setShader(new RadialGradient(w*.5f,r.centerY(),w*.65f,new int[]{0x30ffffff,0x00ffffff},null,Shader.TileMode.CLAMP));c.drawRect(r,p);p.setShader(null);int size=(int)(r.height()*.50);int x=(int)(w-size)/2,y=(int)(r.centerY()-size/2f);app.icon.setBounds(x,y,x+size,y+size);app.icon.draw(c);}
            if(!f||hidden.contains(app.pkg)){p.setColor(hidden.contains(app.pkg)?0x990a1220:0x180a1220);c.drawRect(r,p);}c.restore();p.setColor(f?WHITE:0x35c2d6e2);p.setStrokeWidth(dp(f?2:1));p.setStyle(Paint.Style.STROKE);c.drawRoundRect(r,radius,radius,p);p.setStyle(Paint.Style.FILL);
            if(!compact){p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(sp(13));p.setColor(hidden.contains(app.pkg)?MUTED:f?WHITE:0xffccd7dd);p.setTextAlign(Paint.Align.CENTER);String label=TextUtils.ellipsize(app.name,new android.text.TextPaint(p),w-dp(12),TextUtils.TruncateAt.END).toString();c.drawText(label,w/2,h-dp(7),p);}
            boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;if(hidden.contains(app.pkg)){float badgeX=rtl?w-dp(62):dp(10),labelX=rtl?w-dp(36):dp(36);p.setColor(0xdd152033);c.drawRoundRect(new RectF(badgeX,dp(10),badgeX+dp(52),dp(30)),dp(7),dp(7),p);p.setTextSize(sp(10));p.setColor(WHITE);p.setTextAlign(Paint.Align.CENTER);c.drawText("已隐藏",labelX,dp(24),p);}if(moving.equals(getTag())){p.setColor(0xdd152033);c.drawRoundRect(new RectF(dp(10),dp(10),w-dp(10),dp(35)),dp(7),dp(7),p);p.setTextSize(sp(12));p.setColor(WHITE);String movement=rtl?"→  移动位置  ←":"←  移动位置  →";c.drawText(movement,w/2,dp(27),p);}
        }
        void drawSource(Canvas c,float x,float y){
            p.setColor(0xffdcebf1);p.setStrokeWidth(dp(1.5f));p.setStyle(Paint.Style.STROKE);
            if(app.port.number>0||app.port.enumName.contains("HDMI")){Path port=new Path();port.moveTo(x-dp(12),y-dp(8));port.lineTo(x+dp(12),y-dp(8));port.lineTo(x+dp(9),y+dp(8));port.lineTo(x-dp(9),y+dp(8));port.close();c.drawPath(port,p);for(int i=0;i<5;i++)c.drawLine(x+dp((i-2)*4),y-dp(3),x+dp((i-2)*4),y+dp(3),p);}
            else if(app.port.enumName.contains("AV")){for(int i=0;i<3;i++){float xx=x+dp((i-1)*9);c.drawCircle(xx,y,dp(3.3f),p);c.drawLine(xx,y+dp(5),xx,y+dp(10),p);}}
            else{c.drawRoundRect(new RectF(x-dp(12),y-dp(8),x+dp(12),y+dp(8)),dp(3),dp(3),p);c.drawLine(x,y-dp(8),x-dp(7),y-dp(15),p);c.drawLine(x,y-dp(8),x+dp(7),y-dp(15),p);c.drawLine(x-dp(5),y+dp(12),x+dp(5),y+dp(12),p);}
            p.setStyle(Paint.Style.FILL);
        }
    }
    class Tool extends View {
        final String label;final int glyph;final Paint p=new Paint(3);
        Tool(Context c,String l,int g){super(c);label=l;glyph=g;setFocusableInTouchMode(true);setClickable(true);setContentDescription(l);setOnFocusChangeListener(new OnFocusChangeListener(){public void onFocusChange(View v,boolean f){focusChanged(v,f);}});}
        @Override protected void onDraw(Canvas c){boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;float x=rtl?getWidth()-dp(21):dp(21),y=getHeight()/2f;p.setStyle(Paint.Style.FILL);p.setColor(isFocused()?0xffedf4f6:0x38203240);c.drawRoundRect(new RectF(dp(1),dp(1),getWidth()-dp(1),getHeight()-dp(1)),dp(18),dp(18),p);p.setColor(isFocused()?0xff203442:0xffdae7ed);p.setStrokeWidth(dp(1.4f));p.setStyle(Paint.Style.STROKE);
            if(glyph==0){c.drawRoundRect(new RectF(x-dp(11),y-dp(7),x+dp(11),y+dp(7)),dp(2),dp(2),p);c.drawLine(x-dp(5),y+dp(11),x+dp(5),y+dp(11),p);}else if(glyph==1){for(int i=0;i<3;i++){float yy=y+dp((i-1)*6);c.drawLine(x-dp(10),yy,x+dp(10),yy,p);p.setStyle(Paint.Style.FILL);c.drawCircle(x+dp(i==1?4:-4),yy,dp(2.5f),p);p.setStyle(Paint.Style.STROKE);}}else{c.drawCircle(x,y,dp(6),p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(x+(float)Math.cos(a)*dp(8),y+(float)Math.sin(a)*dp(8),x+(float)Math.cos(a)*dp(11),y+(float)Math.sin(a)*dp(11),p);}}
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(sp(11));p.setTypeface(Typeface.create("sans-serif-medium",0));c.drawText(label,dp(61),y+dp(4),p);
        }
    }
}
