package local.quiettv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.view.KeyEvent;

/** The OS binds this filter when the user enables it. Home keys only; no window content is read. */
public final class HomeKeyService extends AccessibilityService {
    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event==null||event.getKeyCode()!=KeyEvent.KEYCODE_HOME||!fixEnabled())return false;
        if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())openHome();
        // Consuming every Home press keeps the stock Google TV launcher from claiming it.
        return true;
    }
    private boolean fixEnabled(){
        // Credential-protected storage stays unreadable before the user unlocks.
        try{return getSharedPreferences("home",MODE_PRIVATE).getBoolean("home_key_fix",false);}
        catch(Exception e){return false;}
    }
    private void openHome(){
        try{
            Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(new ComponentName(this,MainActivity.class));
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }catch(Exception ignored){}
    }
}
