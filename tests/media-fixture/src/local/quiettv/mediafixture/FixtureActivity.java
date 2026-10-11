package local.quiettv.mediafixture;

/** Required solely to represent a launchable TV app; the integration check never opens it. */
public final class FixtureActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){super.onCreate(state);finish();}
}
