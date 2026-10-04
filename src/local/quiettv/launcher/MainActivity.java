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
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.File;

/** Native TV UI; independently implemented, with FLauncher and Emotn UI references. */
public class MainActivity extends Activity {
    private static final int WHITE=0xfff2f5fa, MUTED=0xffa9b6c7;
    private SharedPreferences prefs;
    private Set<String> hidden, favorites;
    private final ArrayList<App> apps=new ArrayList<>();
    private final ArrayList<Group> groups=new ArrayList<>();
    private final ArrayList<ArrayList<View>> focusRows=new ArrayList<>();
    private final ArrayList<CardRow> cardRows=new ArrayList<>();
    private final HashMap<String,App> appMap=new HashMap<>();
    private final Handler handler=new Handler();
    private FrameLayout root;
    private LinearLayout content;
    private CardScroll scroll;
    private TextView clock,hint,selectionTitle,selectionSubtitle;
    private Dialog sheet;
    private String selected="",moving="";
    private boolean manage=false;
    private int gridColumns=5,theme=0;
    private float uiScale=1.5f;
    private int designWidth=1280;
    private InputSources inputSources;
    private Wallpapers wallpapers;
    private boolean foreground=false,startupShown=false,unlockReceiverRegistered=false;
    private final Runnable waitForUnlock=new Runnable(){public void run(){if(foreground&&prefs==null)resumeHome();}};
    private final Runnable resizeHome=new Runnable(){public void run(){if(!foreground||prefs==null||root==null)return;int w=root.getWidth(),h=root.getHeight();if(w<=0||h<=0)return;float scale=UiMetrics.scale(w,h);int units=UiMetrics.units(w,h);if(Math.abs(uiScale-scale)>.001f||designWidth!=units){uiScale=scale;designWidth=units;render();}}};
    private final BroadcastReceiver userUnlockReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(Intent.ACTION_USER_UNLOCKED.equals(i.getAction())&&foreground&&prefs==null)resumeHome();}};
    private static class App {String pkg,name;Intent intent;Drawable icon,banner;int color;InputSources.Port port;}
    private static class Group {String id,title;boolean row;Group(String i,String t,boolean r){id=i;title=t;row=r;}}
    private final Runnable tick=new Runnable(){public void run(){tickClock();refreshInputs();if(wallpapers!=null)wallpapers.status();handler.postDelayed(this,10000);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        updateUiMetrics();
        getWindow().getDecorView().setSystemUiVisibility(5894);
        if(Build.VERSION.SDK_INT>=24){registerReceiver(userUnlockReceiver,new IntentFilter(Intent.ACTION_USER_UNLOCKED));unlockReceiverRegistered=true;}
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
        hidden=new HashSet<>(prefs.getStringSet("hidden",Collections.<String>emptySet()));
        favorites=new HashSet<>(prefs.getStringSet("favorites",Collections.<String>emptySet()));
        theme=prefs.getInt("theme",0);inputSources=new InputSources(this);wallpapers=new Wallpapers(this,prefs);loadGroups();
    }
    private void resumeHome(){
        handler.removeCallbacks(waitForUnlock);
        getWindow().getDecorView().setSystemUiVisibility(5894);updateUiMetrics();
        if(prefs==null){
            if(!userStorageReady()){showStartup();handler.postDelayed(waitForUnlock,750);return;}
            initHome();
        }
        loadApps();render();wallpapers.resume();handler.removeCallbacks(tick);tick.run();
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
        label.addView(text("静幕",40,WHITE));label.addView(text("正在准备桌面…",18,WHITE));
        root.addView(label,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
    }
    @Override public void onResume(){super.onResume();foreground=true;resumeHome();}
    @Override public void onPause(){foreground=false;handler.removeCallbacks(waitForUnlock);handler.removeCallbacks(resizeHome);handler.removeCallbacks(tick);if(wallpapers!=null)wallpapers.pause();super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacks(waitForUnlock);handler.removeCallbacks(resizeHome);handler.removeCallbacks(tick);if(unlockReceiverRegistered)unregisterReceiver(userUnlockReceiver);if(wallpapers!=null)wallpapers.dispose();super.onDestroy();}
    int dp(float n){return Math.round(n*uiScale);}
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(size));t.setTextColor(color);t.setFontFeatureSettings("kern");t.setIncludeFontPadding(false);return t;}
    void loadGroups(){
        groups.clear();try{JSONArray a=new JSONArray(prefs.getString("groups","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);groups.add(new Group(o.getString("id"),o.getString("title"),o.optBoolean("row")));}}catch(JSONException ignored){}
        if(groups.isEmpty()){groups.add(new Group("favorites","常用",true));groups.add(new Group("all","全部应用",false));}
        if(findGroup("inputs")==null)groups.add(Math.max(0,groups.size()-1),new Group("inputs","输入源",true));
    }
    void saveGroups(){
        JSONArray a=new JSONArray();for(Group g:groups){JSONObject o=new JSONObject();try{o.put("id",g.id);o.put("title",g.title);o.put("row",g.row);a.put(o);}catch(JSONException ignored){}}prefs.edit().putString("groups",a.toString()).apply();
    }
    void save(){prefs.edit().putStringSet("hidden",new HashSet<>(hidden)).putStringSet("favorites",new HashSet<>(favorites)).apply();}
    void loadApps(){
        PackageManager pm=getPackageManager();appMap.clear();apps.clear();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            for(ResolveInfo ri:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                String pkg=ri.activityInfo.packageName;if(pkg.equals(getPackageName())||pkg.equals("com.tcl.cyberui")||pkg.equals("com.tcl.settings")||appMap.containsKey(pkg))continue;
                try{
                    ApplicationInfo ai=pm.getApplicationInfo(pkg,0);if(!ai.enabled)continue;
                    App a=new App();a.pkg=pkg;a.name=ri.loadLabel(pm).toString();a.icon=ri.loadIcon(pm);a.banner=ri.activityInfo.loadBanner(pm);a.color=iconColor(a.icon);
                    a.intent=new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(new ComponentName(pkg,ri.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);appMap.put(pkg,a);apps.add(a);
                }catch(Exception ignored){}
            }
        }
        for(InputSources.Port port:inputSources.scan()){
            App a=new App();a.port=port;a.pkg="input:"+port.id;a.name=port.name;a.color=0xff274453;
            a.intent=new Intent().setComponent(new ComponentName("com.tcl.tv","com.tcl.tv.TVActivity")).putExtra("targetSourceId",port.id).putExtra("referer",getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            apps.add(a);appMap.put(a.pkg,a);
        }
        Collections.sort(apps,new Comparator<App>(){public int compare(App a,App b){return a.name.compareToIgnoreCase(b.name);}});
        if(!prefs.getBoolean("favorites_initialized",false)){
            if(!prefs.contains("favorites")){for(String p:new String[]{"com.xiaodianshi.tv.yst","com.tcl.ui_mediaCenter","com.tcl.MultiScreenInteraction_TV"})if(appMap.containsKey(p)&&!hidden.contains(p))favorites.add(p);if(favorites.isEmpty()&&!apps.isEmpty())favorites.add(apps.get(0).pkg);}
            save();prefs.edit().putBoolean("favorites_initialized",true).apply();
        }
    }
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
        // Adding fresh focusable views may trigger Android's automatic initial focus.
        // Keep the requested card stable while those focus callbacks rebuild the page.
        final String desiredSelection=selected;
        root=new FrameLayout(this);wallpapers.attach(root,theme);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);root.addView(page,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        final FrameLayout measuredRoot=root;
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int oldL,int oldT,int oldR,int oldB){if(root==measuredRoot&&foreground&&r>l&&b>t){handler.removeCallbacks(resizeHome);handler.post(resizeHome);}}});
        focusRows.clear();cardRows.clear();gridColumns=5;
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(48),dp(12),dp(48),dp(6));
        TextView brand=text("静幕",18,WHITE);brand.setLetterSpacing(.12f);brand.setTypeface(Typeface.create("sans-serif-medium",0));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        ArrayList<View> tools=new ArrayList<>();tools.add(tool("输入源","input",0,new Runnable(){public void run(){focusInputs();}}));tools.add(tool("电视设置","settings",1,new Runnable(){public void run(){openSettings();}}));tools.add(tool("桌面设置","home-settings",2,new Runnable(){public void run(){showSettings();}}));for(View t:tools)header.addView(t);focusRows.add(tools);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.BOTTOM);hero.setPadding(dp(48),0,dp(48),dp(8));
        LinearLayout timeBlock=new LinearLayout(this);timeBlock.setOrientation(LinearLayout.VERTICAL);clock=text("",54,WHITE);clock.setTypeface(Typeface.create("sans-serif-light",0));timeBlock.addView(clock);
        TextView date=text(new SimpleDateFormat("M月d日  EEEE",Locale.CHINA).format(new Date()),14,0xffd4dde2);LinearLayout.LayoutParams dateLp=new LinearLayout.LayoutParams(-2,-2);dateLp.topMargin=dp(4);timeBlock.addView(date,dateLp);hero.addView(timeBlock,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setGravity(Gravity.RIGHT);selectionTitle=text("",26,WHITE);selectionTitle.setSingleLine(true);selectionTitle.setEllipsize(TextUtils.TruncateAt.END);selectionTitle.setGravity(Gravity.RIGHT);selectionTitle.setTypeface(Typeface.create("sans-serif-medium",0));details.addView(selectionTitle);
        selectionSubtitle=text("",12,0xffc6d3da);selectionSubtitle.setSingleLine(true);selectionSubtitle.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(-2,-2);detailLp.topMargin=dp(7);details.addView(selectionSubtitle,detailLp);hero.addView(details,new LinearLayout.LayoutParams(dp(490),-2));page.addView(hero,new LinearLayout.LayoutParams(-1,dp(96)));
        scroll=new CardScroll(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setClipChildren(true);scroll.setClipToPadding(true);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(36),dp(12),dp(36),dp(25));content.setClipChildren(false);content.setClipToPadding(false);scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(manage){addCategory(findGroup("inputs"),"整理输入源",true);addCategory(findGroup("all"),"整理应用",true);}else for(Group g:groups)addCategory(g,g.title,false);
        hint=text(manage?"确认键隐藏 / 显示应用     返回键完成整理":"方向键选择    确认键打开    长按确认键整理",11,0xffa3b4bf);hint.setPadding(dp(48),dp(4),dp(48),dp(8));page.addView(hint);
        View target=desiredSelection.isEmpty()?null:content.findViewWithTag(desiredSelection);if(target==null)target=header.findViewWithTag(desiredSelection);if(target==null&&focusRows.size()>1&&!focusRows.get(1).isEmpty())target=focusRows.get(1).get(0);if(target==null)target=tools.get(0);target.requestFocus();tickClock();
    }
    Group findGroup(String id){for(Group g:groups)if(g.id.equals(id))return g;return null;}
    void focusInputs(){for(ArrayList<View> row:focusRows)for(View v:row)if(v instanceof Tile&&((Tile)v).group.id.equals("inputs")){v.requestFocus();return;}Toast.makeText(this,"输入源信息暂时不可用",Toast.LENGTH_LONG).show();}
    String inputStatus(InputSources.Port p){if(p.enumName.endsWith("DTV")||p.enumName.endsWith("ATV"))return "天线 / 有线电视";return p.state==0?(p.number>0?"未接入":"未检测到信号"):p.state==1?(p.device.isEmpty()?"已接入 · 设备名未知":p.device):"状态暂不可读";}
    void refreshInputs(){
        if(inputSources==null)return;for(InputSources.Port p:inputSources.scan()){App a=appMap.get("input:"+p.id);if(a!=null)a.port=p;}
        for(ArrayList<View> row:focusRows)for(View v:row)if(v instanceof Tile&&((Tile)v).app.port!=null){Tile t=(Tile)v;t.setContentDescription(t.app.name+"，"+inputStatus(t.app.port));t.invalidate();}
        View focused=getCurrentFocus();if(focused instanceof Tile&&((Tile)focused).app.port!=null)updateSelection(focused);
    }
    void addCategory(final Group group,String title,boolean includeHidden){
        ArrayList<App> items=groupApps(group,includeHidden);LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(12),0,dp(12),dp(4));TextView label=text(title,17,WHITE);label.setTypeface(Typeface.create("sans-serif-medium",0));heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));heading.addView(text(items.size()+(group.id.equals("inputs")?" 个入口":" 个应用"),11,0xff9bafbc));content.addView(heading);
        boolean row=group.row&&!manage;if(items.isEmpty()){TextView empty=text(group.id.equals("favorites")?"长按应用卡片，添加到常用":"在应用菜单中添加到此分类",14,MUTED);empty.setPadding(dp(20),dp(25),dp(20),dp(25));content.addView(empty);return;}
        if(row){
            HorizontalScrollView h=new HorizontalScrollView(this);h.setHorizontalScrollBarEnabled(false);h.setClipChildren(false);h.setClipToPadding(false);CardRow line=new CardRow(this);cardRows.add(line);line.setPadding(dp(12),dp(6),dp(12),dp(10));line.setClipChildren(false);line.setClipToPadding(false);ArrayList<View> focuses=new ArrayList<>();
            boolean sources=group.id.equals("inputs");int cardWidth=(designWidth-96-(sources?3:gridColumns-1)*18)/(sources?4:gridColumns);
            for(int i=0;i<items.size();i++){App a=items.get(i);Tile tile=makeTile(a,group);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(cardWidth),dp(sources?84:cardWidth*9f/16+27));lp.rightMargin=i<items.size()-1?dp(18):0;line.addView(tile,lp);focuses.add(tile);}h.addView(line);content.addView(h);focusRows.add(focuses);
        }else{
            int available=designWidth-96;int cardWidth=(available-(gridColumns-1)*18)/gridColumns;
            for(int i=0;i<items.size();i+=gridColumns){CardRow line=new CardRow(this);cardRows.add(line);line.setPadding(dp(12),dp(6),dp(12),dp(24));line.setClipChildren(false);line.setClipToPadding(false);ArrayList<View> focuses=new ArrayList<>();
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
        v.animate().scaleX(focused?1.045f:1).scaleY(focused?1.045f:1).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();v.setTranslationZ(focused?dp(8):0);v.invalidate();if(focused){selected=(String)v.getTag();updateSelection(v);if(hint!=null)hint.setText(!moving.isEmpty()?"方向键移动卡片    确认键或返回键完成排序":manage?"确认键隐藏 / 显示应用    返回键完成整理":"方向键选择    确认键打开    长按确认键整理");v.post(new Runnable(){public void run(){if(v.isFocused()&&scroll!=null){if(v instanceof Tile)scroll.revealTile(v);else scroll.scrollTo(0,0);Rect r=new Rect(-dp(8),-dp(8),v.getWidth()+dp(8),v.getHeight()+dp(8));v.requestRectangleOnScreen(r,false);}}});}
    }
    // Keep partially visible rows in the focus graph, but paint only complete cards.
    // This rule applies to every scroll position, including repeated up/down moves.
    class CardRow extends LinearLayout {
        CardRow(Context c){super(c);}
        Rect cardBounds(){
            Rect bounds=new Rect();if(getChildCount()==0||content==null)return bounds;
            View card=getChildAt(0);bounds.set(0,0,card.getWidth(),card.getHeight());
            content.offsetDescendantRectToMyCoords(card,bounds);bounds.inset(0,-dp(8));return bounds;
        }
        @Override protected void dispatchDraw(Canvas canvas){
            Rect bounds=cardBounds();if(scroll==null||bounds.height()<=0)return;
            int top=scroll.getScrollY(),bottom=top+scroll.getHeight();
            if(bounds.top>=top&&bounds.bottom<=bottom)super.dispatchDraw(canvas);
        }
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
            for(CardRow row:cardRows)row.invalidate();
        }
    }
    void updateSelection(View v){if(selectionTitle==null)return;if(v instanceof Tile){Tile t=(Tile)v;selectionTitle.setText(t.app.name);selectionSubtitle.setText(t.app.port==null?t.group.title:inputStatus(t.app.port));}else if(v instanceof Tool){selectionTitle.setText(((Tool)v).label);selectionSubtitle.setText("主页快捷入口");}}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int code=e.getKeyCode();boolean arrow=code==19||code==20||code==21||code==22;if(arrow&&e.getAction()==KeyEvent.ACTION_DOWN){View focused=getCurrentFocus();if(!moving.isEmpty()&&focused instanceof Tile){moveApp((Tile)focused,code);return true;}
            for(int r=0;r<focusRows.size();r++){ArrayList<View> row=focusRows.get(r);int i=row.indexOf(focused);if(i<0)continue;int nr=r,ni=i;if(code==21)ni--;else if(code==22)ni++;else if(code==19)nr--;else nr++;if(nr>=0&&nr<focusRows.size()){ArrayList<View> next=focusRows.get(nr);if(nr!=r)ni=Math.min(ni,next.size()-1);if(ni>=0&&ni<next.size())next.get(ni).requestFocus();}return true;}
        }return super.dispatchKeyEvent(e);
    }
    void moveApp(Tile tile,int code){ArrayList<App> list=groupApps(tile.group,manage);int index=-1;for(int i=0;i<list.size();i++)if(list.get(i).pkg.equals(tile.app.pkg))index=i;int step=code==21?-1:code==22?1:tile.group.row?0:code==19?-gridColumns:gridColumns;int next=index+step;if(index>=0&&step!=0&&next>=0&&next<list.size()){Collections.swap(list,index,next);saveOrder(tile.group,list);render();}}
    void tickClock(){if(clock!=null)clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));}

    interface Choice {void pick(int index);}
    void panel(String title,String subtitle,final String[] choices,final Choice callback){
        if(sheet!=null)sheet.dismiss();sheet=new Dialog(this);final Dialog current=sheet;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(28),dp(34),dp(28),dp(24));GradientDrawable panelBackground=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff253a46,0xff101e2b});panelBackground.setStroke(dp(1),0x335d818e);box.setBackground(panelBackground);TextView heading=text(title,25,WHITE);heading.setTypeface(Typeface.create("sans-serif-medium",0));box.addView(heading);
        TextView sub=text(subtitle,13,MUTED);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(10);sp.bottomMargin=dp(24);box.addView(sub,sp);ScrollView list=new ScrollView(this);list.setVerticalScrollBarEnabled(false);LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);list.addView(options);box.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<choices.length;i++){final int index=i;final TextView button=text(choices[i],16,WHITE);button.setPadding(dp(16),dp(15),dp(16),dp(15));button.setGravity(Gravity.CENTER_VERTICAL);button.setFocusable(true);button.setClickable(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(6);options.addView(button,lp);button.setBackground(panelButton(false));button.setOnFocusChangeListener(new View.OnFocusChangeListener(){public void onFocusChange(View v,boolean f){button.setBackground(panelButton(f));button.setTextColor(f?0xff142034:WHITE);}});button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){current.dismiss();callback.pick(index);}});}
        TextView back=text("返回键关闭",12,MUTED);back.setPadding(dp(16),dp(15),0,0);box.addView(back);current.setContentView(box);Window w=current.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams lp=w.getAttributes();lp.width=dp(365);lp.height=-1;lp.gravity=Gravity.RIGHT;lp.dimAmount=.45f;w.setAttributes(lp);w.getDecorView().setSystemUiVisibility(5894);}current.setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface d){if(sheet==current)sheet=null;}});current.show();if(options.getChildCount()>0)options.getChildAt(0).requestFocus();
    }
    Drawable panelButton(boolean f){GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(10));d.setColor(f?0xffe7edf6:0xff202d42);return d;}
    void showSettings(){panel("桌面设置","按你的习惯，整理自己的主页",new String[]{"整理应用","管理分类","背景与卡片","电视系统设置","返回原厂桌面"},new Choice(){public void pick(int i){if(i==0){manage=true;selected="";render();}else if(i==1)showGroups();else if(i==2)showAppearance();else if(i==3)openSettings();else openOriginal();}});}
    void showAppearance(){panel("背景与卡片","图片缓慢移动，视频静音循环；离开桌面自动暂停",new String[]{"背景 · 自然山湖","自定义图片 / 动态视频",prefs.getBoolean("wallpaper_motion",true)?"图片动效 · 已开启":"图片动效 · 已关闭","配色背景",prefs.getBoolean("banners",true)?"应用横幅 · 已开启":"应用横幅 · 已关闭"},new Choice(){public void pick(int i){if(i==1){chooseWallpaper();return;}if(i==3){chooseTheme();return;}if(i==0)prefs.edit().putString("wallpaper_kind","builtin").apply();else if(i==2)prefs.edit().putBoolean("wallpaper_motion",!prefs.getBoolean("wallpaper_motion",true)).apply();else prefs.edit().putBoolean("banners",!prefs.getBoolean("banners",true)).apply();render();}});}
    void chooseTheme(){panel("配色背景","无需图片或视频，也可使用原来的配色",new String[]{"深海蓝","暮色紫","石墨黑","森林绿"},new Choice(){public void pick(int i){theme=i;prefs.edit().putInt("theme",theme).putString("wallpaper_kind","theme").apply();render();}});}
    void chooseWallpaper(){panel("自定义背景","选定后保存到电视，之后可以移除 U 盘",new String[]{"从 U 盘或本地导入","已保存背景","恢复内置风景"},new Choice(){public void pick(int i){if(i==0){if(checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},902);else browseRoots();}else if(i==1)browseFolder(wallpapers.library(),wallpapers.library());else{prefs.edit().putString("wallpaper_kind","builtin").apply();render();}}});}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==902){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)browseRoots();else Toast.makeText(this,"未授权，仍可使用内置风景和已保存背景",Toast.LENGTH_LONG).show();}}
    void browseRoots(){
        final ArrayList<File> roots=new ArrayList<>();final ArrayList<String> names=new ArrayList<>();File local=Environment.getExternalStorageDirectory();if(local!=null){roots.add(local);names.add("电视本地文件");}File[] storage=new File("/storage").listFiles();if(storage!=null)for(File f:storage){String n=f.getName();if(f.isDirectory()&&f.canRead()&&!n.equals("emulated")&&!n.equals("self")){roots.add(f);names.add("U 盘 / 存储 · "+n);}}roots.add(wallpapers.library());names.add("已保存背景");panel("选择存储位置","只读取你选定的图片或视频",names.toArray(new String[0]),new Choice(){public void pick(int i){browseFolder(roots.get(i),roots.get(i));}});
    }
    void browseFolder(final File folder,final File allowedRoot){
        try{String base=allowedRoot.getCanonicalPath(),path=folder.getCanonicalPath();if(!path.equals(base)&&!path.startsWith(base+File.separator))return;}catch(Exception e){return;}
        File[] files=folder.listFiles();if(files==null){Toast.makeText(this,"这个位置暂时无法读取",Toast.LENGTH_LONG).show();return;}final ArrayList<File> items=new ArrayList<>();for(File f:files)if(!f.getName().startsWith(".")&&(f.isDirectory()||Wallpapers.picture(f)||Wallpapers.video(f)))items.add(f);Collections.sort(items,new Comparator<File>(){public int compare(File a,File b){if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());}});final boolean parent=!folder.equals(allowedRoot);ArrayList<String> labels=new ArrayList<>();labels.add(parent?"返回上一级":"返回存储位置");for(File f:items)labels.add((f.isDirectory()?"文件夹 · ":Wallpapers.video(f)?"视频 · ":"图片 · ")+f.getName());panel(folder.equals(allowedRoot)?"选择图片或视频":folder.getName(),"图片 50 MB 内，视频 400 MB 内；视频静音循环",labels.toArray(new String[0]),new Choice(){public void pick(int i){if(i==0){if(parent)browseFolder(folder.getParentFile(),allowedRoot);else browseRoots();return;}File f=items.get(i-1);if(f.isDirectory())browseFolder(f,allowedRoot);else importWallpaper(f);}});
    }
    void importWallpaper(final File f){final ProgressDialog progress=new ProgressDialog(this);progress.setTitle("正在导入背景");progress.setMessage(f.getName());progress.setIndeterminate(true);progress.setCancelable(false);progress.show();wallpapers.importFile(f,new Wallpapers.Imported(){public void done(boolean ok,boolean video){progress.dismiss();Toast.makeText(MainActivity.this,ok?(video?"已应用视频背景":"已应用图片背景"):"导入失败，请选择可读取的图片或视频",Toast.LENGTH_LONG).show();if(ok)render();}});}
    void showGroups(){ArrayList<String> names=new ArrayList<>();for(Group g:groups)names.add(g.title+" · "+(g.row?"横向一排":"网格"));names.add("＋ 添加分类");panel("管理分类","每个分类可选择横排或网格",names.toArray(new String[0]),new Choice(){public void pick(int i){if(i==groups.size())addGroup();else editGroup(groups.get(i));}});}
    void addGroup(){panel("添加分类","长按应用卡片可添加到分类",new String[]{"影音","工具","游戏","其他","自定义名称"},new Choice(){public void pick(int i){if(i==4)editName(null);else createGroup(new String[]{"影音","工具","游戏","其他"}[i]);}});}
    void createGroup(String title){groups.add(Math.max(0,groups.size()-1),new Group("group-"+System.currentTimeMillis(),title,true));saveGroups();render();showGroups();}
    void editName(final Group group){final EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(18));input.setText(group==null?"":group.title);input.setHint("分类名称");input.setPadding(dp(16),dp(10),dp(16),dp(10));new AlertDialog.Builder(this).setTitle(group==null?"新分类":"分类名称").setView(input).setPositiveButton("保存",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){String name=input.getText().toString().trim();if(name.isEmpty())return;if(group==null)createGroup(name);else{group.title=name;saveGroups();render();}}}).setNegativeButton("取消",null).show();}
    void editGroup(final Group g){ArrayList<String> options=new ArrayList<>(Arrays.asList("重命名",g.row?"改为网格":"改为横向一排","分类上移","分类下移"));if(!g.id.equals("favorites")&&!g.id.equals("all")&&!g.id.equals("inputs"))options.add("删除此分类");panel(g.title,"只调整桌面的分类，不改变应用状态",options.toArray(new String[0]),new Choice(){public void pick(int i){if(i==0){editName(g);return;}if(i==1)g.row=!g.row;if(i==2||i==3){int at=groups.indexOf(g),next=at+(i==2?-1:1);if(next>=0&&next<groups.size())Collections.swap(groups,at,next);}if(i==4)groups.remove(g);saveGroups();render();showGroups();}});}
    void showAppMenu(final App a,final Group g){panel(a.name,a.port==null?"应用操作":inputStatus(a.port),new String[]{a.port==null?"打开应用":"进入输入源",favorites.contains(a.pkg)?"从常用移除":"添加到常用","移动位置",hidden.contains(a.pkg)?"显示在主页":"从主页隐藏","添加 / 移出分类",a.port==null?"应用信息":"刷新接入状态"},new Choice(){public void pick(int i){if(i==0){launch(a.intent,a.name);return;}if(i==1){if(favorites.contains(a.pkg))favorites.remove(a.pkg);else favorites.add(a.pkg);save();render();}else if(i==2){moving=g.id+"|"+a.pkg;selected=moving;render();}else if(i==3){if(hidden.contains(a.pkg))hidden.remove(a.pkg);else hidden.add(a.pkg);save();render();}else if(i==4)chooseMembership(a);else if(i==5){if(a.port==null)launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.pkg)),"应用信息");else refreshInputs();}}});}
    void chooseMembership(final App a){final ArrayList<Group> custom=new ArrayList<>();ArrayList<String> choices=new ArrayList<>();for(Group g:groups)if(!g.id.equals("favorites")&&!g.id.equals("all")&&!g.id.equals("inputs")){custom.add(g);boolean member=prefs.getStringSet("members_"+g.id,Collections.<String>emptySet()).contains(a.pkg);choices.add((member?"移出 · ":"添加到 · ")+g.title);}if(custom.isEmpty()){addGroup();return;}panel("应用分类",a.name,choices.toArray(new String[0]),new Choice(){public void pick(int i){Group g=custom.get(i);Set<String> m=new HashSet<>(prefs.getStringSet("members_"+g.id,Collections.<String>emptySet()));if(m.contains(a.pkg))m.remove(a.pkg);else m.add(a.pkg);prefs.edit().putStringSet("members_"+g.id,m).apply();render();}});}
    void launch(Intent i,String name){try{startActivity(i);}catch(Exception e){Toast.makeText(this,"暂时无法打开"+name,Toast.LENGTH_LONG).show();}}
    void openPackage(String p){Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(p);if(i==null)i=getPackageManager().getLaunchIntentForPackage(p);if(i==null){Toast.makeText(this,"请通过电视设置或遥控器切换输入源",Toast.LENGTH_LONG).show();return;}launch(i,"");}
    void openSettings(){launch(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.tcl.settings","com.tcl.settings.ui.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),"设置");}
    void openOriginal(){launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName("com.tcl.cyberui","com.tcl.cyberui.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),"原厂桌面");}
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
                float x=compact?dp(37):w/2,y=compact?r.centerY():r.centerY()-dp(6);p.setColor(0x22cce0e9);c.drawCircle(x,y,dp(22),p);drawSource(c,x,y);
                if(compact){p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextAlign(Paint.Align.LEFT);p.setColor(WHITE);p.setTextSize(dp(16));c.drawText(app.name,dp(73),y-dp(3),p);}
                p.setTypeface(Typeface.create("sans-serif",0));p.setTextAlign(compact?Paint.Align.LEFT:Paint.Align.CENTER);p.setTextSize(dp(11));p.setColor(app.port.state==1?0xffa5e8cb:0xffa5bac6);String status=TextUtils.ellipsize(inputStatus(app.port),new android.text.TextPaint(p),w-dp(compact?90:20),TextUtils.TruncateAt.END).toString();c.drawText(status,compact?dp(73):w/2,compact?y+dp(17):r.bottom-dp(9),p);
                if(app.port.number>0){p.setColor(app.port.state==1?0xff80dbb2:0xff647984);c.drawCircle(w-dp(15),dp(15),dp(3),p);}
            }
            else if(app.banner!=null&&prefs.getBoolean("banners",true)){int bw=Math.max(1,app.banner.getIntrinsicWidth()),bh=Math.max(1,app.banner.getIntrinsicHeight());float scale=Math.max(r.width()/bw,r.height()/bh);int sw=(int)(bw*scale),sh=(int)(bh*scale);int x=(int)(r.centerX()-sw/2f),y=(int)(r.centerY()-sh/2f);app.banner.setBounds(x,y,x+sw,y+sh);app.banner.draw(c);}
            else{p.setShader(new RadialGradient(w*.5f,r.centerY(),w*.65f,new int[]{0x30ffffff,0x00ffffff},null,Shader.TileMode.CLAMP));c.drawRect(r,p);p.setShader(null);int size=(int)(r.height()*.50);int x=(int)(w-size)/2,y=(int)(r.centerY()-size/2f);app.icon.setBounds(x,y,x+size,y+size);app.icon.draw(c);}
            if(!f||hidden.contains(app.pkg)){p.setColor(hidden.contains(app.pkg)?0x990a1220:0x180a1220);c.drawRect(r,p);}c.restore();p.setColor(f?WHITE:0x35c2d6e2);p.setStrokeWidth(dp(f?2:1));p.setStyle(Paint.Style.STROKE);c.drawRoundRect(r,radius,radius,p);p.setStyle(Paint.Style.FILL);
            if(!compact){p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(dp(13));p.setColor(hidden.contains(app.pkg)?MUTED:f?WHITE:0xffccd7dd);p.setTextAlign(Paint.Align.CENTER);String label=TextUtils.ellipsize(app.name,new android.text.TextPaint(p),w-dp(12),TextUtils.TruncateAt.END).toString();c.drawText(label,w/2,h-dp(7),p);}
            if(hidden.contains(app.pkg)){p.setColor(0xdd152033);c.drawRoundRect(new RectF(dp(10),dp(10),dp(62),dp(30)),dp(7),dp(7),p);p.setTextSize(dp(10));p.setColor(WHITE);c.drawText("已隐藏",dp(36),dp(24),p);}if(moving.equals(getTag())){p.setColor(0xdd152033);c.drawRoundRect(new RectF(dp(10),dp(10),w-dp(10),dp(35)),dp(7),dp(7),p);p.setTextSize(dp(12));p.setColor(WHITE);c.drawText("←  移动位置  →",w/2,dp(27),p);}
        }
        void drawSource(Canvas c,float x,float y){
            p.setColor(0xffdcebf1);p.setStrokeWidth(dp(1.5f));p.setStyle(Paint.Style.STROKE);
            if(app.port.number>0){Path port=new Path();port.moveTo(x-dp(12),y-dp(8));port.lineTo(x+dp(12),y-dp(8));port.lineTo(x+dp(9),y+dp(8));port.lineTo(x-dp(9),y+dp(8));port.close();c.drawPath(port,p);for(int i=0;i<5;i++)c.drawLine(x+dp((i-2)*4),y-dp(3),x+dp((i-2)*4),y+dp(3),p);}
            else if(app.port.enumName.contains("AV")){for(int i=0;i<3;i++){float xx=x+dp((i-1)*9);c.drawCircle(xx,y,dp(3.3f),p);c.drawLine(xx,y+dp(5),xx,y+dp(10),p);}}
            else{c.drawRoundRect(new RectF(x-dp(12),y-dp(8),x+dp(12),y+dp(8)),dp(3),dp(3),p);c.drawLine(x,y-dp(8),x-dp(7),y-dp(15),p);c.drawLine(x,y-dp(8),x+dp(7),y-dp(15),p);c.drawLine(x-dp(5),y+dp(12),x+dp(5),y+dp(12),p);}
            p.setStyle(Paint.Style.FILL);
        }
    }
    class Tool extends View {
        final String label;final int glyph;final Paint p=new Paint(3);
        Tool(Context c,String l,int g){super(c);label=l;glyph=g;setFocusableInTouchMode(true);setClickable(true);setContentDescription(l);setOnFocusChangeListener(new OnFocusChangeListener(){public void onFocusChange(View v,boolean f){focusChanged(v,f);}});}
        @Override protected void onDraw(Canvas c){float x=dp(21),y=getHeight()/2f;p.setStyle(Paint.Style.FILL);p.setColor(isFocused()?0xffedf4f6:0x38203240);c.drawRoundRect(new RectF(dp(1),dp(1),getWidth()-dp(1),getHeight()-dp(1)),dp(18),dp(18),p);p.setColor(isFocused()?0xff203442:0xffdae7ed);p.setStrokeWidth(dp(1.4f));p.setStyle(Paint.Style.STROKE);
            if(glyph==0){c.drawRoundRect(new RectF(x-dp(11),y-dp(7),x+dp(11),y+dp(7)),dp(2),dp(2),p);c.drawLine(x-dp(5),y+dp(11),x+dp(5),y+dp(11),p);}else if(glyph==1){for(int i=0;i<3;i++){float yy=y+dp((i-1)*6);c.drawLine(x-dp(10),yy,x+dp(10),yy,p);p.setStyle(Paint.Style.FILL);c.drawCircle(x+dp(i==1?4:-4),yy,dp(2.5f),p);p.setStyle(Paint.Style.STROKE);}}else{c.drawCircle(x,y,dp(6),p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(x+(float)Math.cos(a)*dp(8),y+(float)Math.sin(a)*dp(8),x+(float)Math.cos(a)*dp(11),y+(float)Math.sin(a)*dp(11),p);}}
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(11));p.setTypeface(Typeface.create("sans-serif-medium",0));c.drawText(label,dp(61),y+dp(4),p);
        }
    }
}
