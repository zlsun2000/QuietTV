package local.quiettv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.KeyEvent;

/** The OS binds this filter when the user enables it. Home keys only; no window content is read. */
public final class HomeKeyService extends AccessibilityService {
    static volatile boolean connected;
    private static volatile HomeKeyService instance;
    private volatile boolean filtering;
    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override protected void onServiceConnected(){super.onServiceConnected();connected=true;instance=this;setFiltering(fixEnabled());}
    @Override public boolean onUnbind(Intent intent){connected=false;filtering=false;if(instance==this)instance=null;return super.onUnbind(intent);}
    @Override public void onDestroy(){connected=false;filtering=false;if(instance==this)instance=null;super.onDestroy();}
    static void requestFiltering(boolean enabled){HomeKeyService service=instance;if(service!=null)service.setFiltering(enabled);}
    private void setFiltering(boolean enabled){try{AccessibilityServiceInfo info=getServiceInfo();if(info==null)return;if(enabled)info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;else info.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;setServiceInfo(info);filtering=enabled;}catch(Exception ignored){filtering=false;}}
    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event==null||event.getKeyCode()!=KeyEvent.KEYCODE_HOME||!filtering||!fixEnabled())return false;
        if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){if(openHome())return true;setFiltering(false);return false;}
        // Consuming every Home press keeps the stock Google TV launcher from claiming it.
        return true;
    }
    private boolean fixEnabled(){
        // Credential-protected storage stays unreadable before the user unlocks.
        try{return getSharedPreferences("home",MODE_PRIVATE).getBoolean("home_key_fix",false);}
        catch(Exception e){return false;}
    }
    private boolean openHome(){
        try{
            Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(new ComponentName(this,MainActivity.class));
            home.putExtra("quiettv.home_key_request",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);return true;
        }catch(Exception ignored){return false;}
    }
}
