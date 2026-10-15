package local.quiettv.checks;

import android.app.Activity;
import android.content.*;
import android.os.*;

/** Synthetic destination: acknowledges typed parameters without playing media. */
public final class RuleTargetActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);Intent input=getIntent();Intent result=new Intent("local.quiettv.checks.ROUTE_RESULT").setPackage("local.quiettv.launcher");result.putExtra("item",input.getStringExtra("item"));result.putExtra("progress",input.getLongExtra("progress",-1));result.putExtra("flags",input.getStringExtra("flags"));sendBroadcast(result,"local.quiettv.launcher.permission.INTERNAL");finish();}
}
