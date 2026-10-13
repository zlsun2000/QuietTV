package local.quiettv.launcher;

import android.content.*;
import android.content.pm.*;
import android.provider.Settings;
import java.util.*;

/** Resolves existing, exported system entry points without changing package or HOME state. */
final class SystemEntrypoints {
    static Intent homeQuery(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);}
    static boolean available(Context c,Intent intent){
        if(intent==null)return false;
        try{ResolveInfo r=c.getPackageManager().resolveActivity(intent,0);
            return r!=null&&r.activityInfo!=null&&r.activityInfo.enabled&&r.activityInfo.applicationInfo.enabled&&r.activityInfo.exported&&
                (r.activityInfo.permission==null||c.checkSelfPermission(r.activityInfo.permission)==PackageManager.PERMISSION_GRANTED);
        }catch(Exception e){return false;}
    }
    static Intent first(Context c,Intent... intents){for(Intent i:intents)if(available(c,i))return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);return null;}
    static Intent settings(Context c){return first(c,new Intent(Settings.ACTION_SETTINGS),
        new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.tcl.settings","com.tcl.settings.ui.MainActivity")));}
    static Intent inputSettings(Context c){return first(c,new Intent("android.settings.TV_INPUT_SETTINGS"));}
    static Intent homeSettings(Context c){return first(c,new Intent(Settings.ACTION_HOME_SETTINGS));}
    static Intent accessibilitySettings(Context c){return first(c,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
    static Intent notificationAccessSettings(Context c){return first(c,new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
    static Intent appInfo(Context c,String pkg){if(!MediaRecordPolicy.packageName(pkg))return null;return first(c,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.fromParts("package",pkg,null)));}
    static boolean defaultHome(Context c){try{ResolveInfo r=c.getPackageManager().resolveActivity(homeQuery(),PackageManager.MATCH_DEFAULT_ONLY);return r!=null&&r.activityInfo!=null&&c.getPackageName().equals(r.activityInfo.packageName);}catch(Exception e){return false;}}
    static ArrayList<ResolveInfo> otherHomes(Context c){
        ArrayList<ResolveInfo> result=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        for(ResolveInfo r:c.getPackageManager().queryIntentActivities(homeQuery(),0)){
            if(r.activityInfo==null||c.getPackageName().equals(r.activityInfo.packageName))continue;
            Intent i=homeQuery().setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name));
            if(available(c,i)&&seen.add(r.activityInfo.packageName+"/"+r.activityInfo.name))result.add(r);
        }
        Collections.sort(result,(a,b)->a.loadLabel(c.getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(c.getPackageManager()).toString()));
        return result;
    }
    private SystemEntrypoints(){}
}
