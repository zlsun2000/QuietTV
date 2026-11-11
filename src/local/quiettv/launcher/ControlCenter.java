package local.quiettv.launcher;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Local control center. No system settings or permissions are changed here. */
final class ControlCenter extends Dialog {
    interface Action { void choose(int index); }
    private final MainActivity host;
    private final LinearLayout content;
    private final TextView time, date, network, footer;
    private final ArrayList<View> actions=new ArrayList<>();
    private int focused=0;

    ControlCenter(MainActivity activity,Action action){
        super(activity);host=activity;
        content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        content.setPaddingRelative(host.dp(28),host.dp(28),host.dp(28),host.dp(22));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff253a46,0xff101e2b});background.setStroke(host.dp(1),0x335d818e);content.setBackground(background);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        time=host.text("",36,0xfff2f5fa);time.setTypeface(Typeface.create("sans-serif-light",0));body.addView(time);
        date=host.text("",14,0xffd4dde2);LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(-1,-2);dateParams.topMargin=host.dp(4);body.addView(date,dateParams);
        network=host.text("",13,0xffc4d3db);LinearLayout.LayoutParams networkParams=new LinearLayout.LayoutParams(-1,-2);networkParams.topMargin=host.dp(12);networkParams.bottomMargin=host.dp(24);body.addView(network,networkParams);
        boolean twoColumns=activity.getResources().getConfiguration().fontScale>=1.3f;
        LinearLayout shortcuts=new LinearLayout(activity);shortcuts.setOrientation(LinearLayout.HORIZONTAL);body.addView(shortcuts,new LinearLayout.LayoutParams(-1,-2));
        String[] glyphs={"▣","◉","⚙","◐"};String[] labels={activity.getString(R.string.toolbar_inputs),activity.getString(R.string.control_network),activity.getString(R.string.toolbar_tv_settings),activity.getString(R.string.control_personalize)};
        for(int i=0;i<labels.length;i++){
            if(twoColumns&&i==2){shortcuts=new LinearLayout(activity);shortcuts.setOrientation(LinearLayout.HORIZONTAL);body.addView(shortcuts,new LinearLayout.LayoutParams(-1,-2));}
            final int index=i;LinearLayout shortcut=new LinearLayout(activity);shortcut.setOrientation(LinearLayout.VERTICAL);shortcut.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);shortcut.setPadding(host.dp(2),host.dp(3),host.dp(2),host.dp(8));shortcut.setFocusable(true);shortcut.setFocusableInTouchMode(true);shortcut.setClickable(true);shortcut.setTag("control-option:"+i);shortcut.setContentDescription(labels[i]);
            TextView icon=host.text(glyphs[i],22,0xffe7edf6);icon.setGravity(Gravity.CENTER);icon.setBackground(circle(false));shortcut.addView(icon,new LinearLayout.LayoutParams(host.dp(54),host.dp(54)));
            TextView label=host.text(labels[i],12,0xffe7edf6);label.setGravity(Gravity.CENTER);label.setMaxLines(2);LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-1,-2);labelParams.topMargin=host.dp(8);shortcut.addView(label,labelParams);
            shortcut.setOnFocusChangeListener((v,f)->{icon.setBackground(circle(f));icon.setTextColor(f?0xff142034:0xffe7edf6);if(f)focused=index;});shortcut.setOnClickListener(v->{dismiss();action.choose(index);});shortcuts.addView(shortcut,new LinearLayout.LayoutParams(0,-2,1));actions.add(shortcut);
        }
        TextView groups=host.text("⚙  "+activity.getString(R.string.toolbar_home_settings)+"\n"+activity.getString(R.string.control_desktop_settings_hint),16,0xfff2f5fa);groups.setPaddingRelative(host.dp(16),host.dp(16),host.dp(16),host.dp(16));groups.setFocusable(true);groups.setFocusableInTouchMode(true);groups.setClickable(true);groups.setTag("control-option:4");groups.setBackground(host.panelButton(false));LinearLayout.LayoutParams groupParams=new LinearLayout.LayoutParams(-1,-2);groupParams.topMargin=host.dp(28);body.addView(groups,groupParams);groups.setOnFocusChangeListener((v,f)->{groups.setBackground(host.panelButton(f));groups.setTextColor(f?0xff142034:0xfff2f5fa);if(f)focused=4;});groups.setOnClickListener(v->{dismiss();action.choose(4);});actions.add(groups);
        footer=host.text("",12,0xffa9b6c7);footer.setPaddingRelative(0,host.dp(16),0,0);content.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        setContentView(content);Window window=getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=window.getAttributes();p.width=host.dp(420);p.height=-1;p.gravity=Gravity.END;p.dimAmount=.45f;window.setAttributes(p);window.getDecorView().setSystemUiVisibility(5894);}
    }
    private GradientDrawable circle(boolean focused){GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(focused?0xffe7edf6:0xff283c50);d.setStroke(host.dp(1),focused?0xffe7edf6:0xff526b79);return d;}
    private String networkStatus(){try{ConnectivityManager cm=(ConnectivityManager)host.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return host.getString(R.string.control_network_unknown);NetworkInfo info=cm.getActiveNetworkInfo();return host.getString(info!=null&&info.isConnected()?R.string.control_connected:R.string.control_disconnected);}catch(RuntimeException e){return host.getString(R.string.control_network_unknown);}}
    void refresh(){Date now=new Date();time.setText(DateFormat.getTimeFormat(host).format(now));String pattern=DateFormat.getBestDateTimePattern(Locale.getDefault(),host.getString(R.string.header_date_skeleton));date.setText(new SimpleDateFormat(pattern,Locale.getDefault()).format(now));network.setText(networkStatus());String version="";try{version=host.getPackageManager().getPackageInfo(host.getPackageName(),0).versionName;}catch(Exception ignored){}footer.setText(host.getString(SystemEntrypoints.defaultHome(host)?R.string.control_home_active:R.string.control_home_unknown)+" · "+version);}
    void focusSaved(){if(!actions.isEmpty())actions.get(Math.min(focused,actions.size()-1)).requestFocus();}
    int focusIndex(){return focused;}
    void focusIndex(int index){focused=Math.max(0,Math.min(index,actions.size()-1));}
    /** Prepares and draws the reusable view once while idle; caller skips low-memory devices. */
    boolean warmUp(int height){
        int width=host.dp(420);if(width<=0||height<=0||(long)width*height>3000000L)return false;Bitmap bitmap=null;
        try{refresh();content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));content.layout(0,0,width,height);bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);content.draw(new Canvas(bitmap));}
        catch(OutOfMemoryError ignored){return false;}finally{if(bitmap!=null)bitmap.recycle();}return true;
    }
}
