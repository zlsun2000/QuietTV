package local.quiettv.launcher;

/** Resolution-independent 16:9 TV layout. Does not override the OS display mode. */
final class UiMetrics {
    static float scale(int width,int height){return Math.min(width/1280f,height/720f);}
    static int units(int width,int height){return Math.round(width/scale(width,height));}
    private UiMetrics(){}
}
