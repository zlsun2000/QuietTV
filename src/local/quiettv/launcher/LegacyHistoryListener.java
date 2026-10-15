package local.quiettv.launcher;

import android.content.*;
import android.os.Build;

/** Runtime delivery of the existing channel history contract on Android 8+.
 * No service is started to keep this listener alive; Activity/OS-bound service owners
 * share one registration. Explicit broadcasts still use the manifest receivers.
 */
final class LegacyHistoryListener extends BroadcastReceiver {
    private static volatile LegacyHistoryListener current;
    private static int owners;
    private final Context context;
    private LegacyHistoryListener(Context c){context=c.getApplicationContext();}
    static synchronized boolean acquire(Context c){
        if(current!=null){owners++;return true;}
        LegacyHistoryListener listener=new LegacyHistoryListener(c);
        IntentFilter filter=filter(c);
        try{listener.context.registerReceiver(listener,filter);current=listener;owners=1;return true;}catch(Exception ignored){return false;}
    }
    private static IntentFilter filter(Context c){IntentFilter filter=new IntentFilter();for(String action:PlaybackRules.get(c).actions())filter.addAction(action);if(filter.countActions()==0)filter.addAction("local.quiettv.launcher.NO_HISTORY_RULES");return filter;}
    static synchronized void refresh(){if(current==null)return;LegacyHistoryListener listener=current;try{listener.context.unregisterReceiver(listener);}catch(Exception ignored){}try{listener.context.registerReceiver(listener,filter(listener.context));}catch(Exception ignored){current=null;owners=0;}}
    static synchronized void release(){
        if(current==null||owners<=0)return;if(--owners>0)return;
        LegacyHistoryListener listener=current;current=null;
        try{listener.context.unregisterReceiver(listener);}catch(Exception ignored){}
    }
    static boolean skipManifest(Intent intent){return current!=null&&intent!=null&&intent.getPackage()==null&&intent.getComponent()==null;}
    @Override public void onReceive(Context c,Intent intent){
        // Package/component-targeted broadcasts are already delivered by Android to
        // the manifest receivers. Do not process the same event twice.
        if(intent==null)return;
        if(intent.getComponent()!=null)return;
        // Stock actions have a manifest receiver for package-targeted delivery. Imported
        // actions have only this runtime receiver, including package-targeted events.
        if(intent.getPackage()!=null&&(intent.getAction().equals("com.tv.history.add")||intent.getAction().equals("com.tv.history.del.tolauncher")))return;
        PlaybackRules.receive(c,intent);
    }
}
