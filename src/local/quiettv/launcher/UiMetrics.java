package local.quiettv.launcher;

/** Resolution-independent 16:9 TV layout. Does not override the OS display mode. */
final class UiMetrics {
    static float scale(int width,int height){return width>0&&height>0?Math.min(width/1280f,height/720f):1f;}
    static int units(int width,int height){return width>0&&height>0?Math.round(width/scale(width,height)):1280;}
    private UiMetrics(){}
}
