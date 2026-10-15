package local.quiettv.launcher;

import android.content.*;

/** Existing explicit history contract; imported actions are registered while an owner is alive. */
public final class RuleHistoryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){if(!LegacyHistoryListener.skipManifest(intent))PlaybackRules.receive(c,intent);}
}
