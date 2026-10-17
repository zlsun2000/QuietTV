package local.quiettv.checks;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** An opaque, synthetic foreground app; never plays media or accesses accounts. */
public final class WallpaperAwayActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);TextView text=new TextView(this);text.setText("Synthetic wallpaper lifecycle check");setContentView(text);}
}
