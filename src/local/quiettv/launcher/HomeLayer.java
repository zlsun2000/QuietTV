package local.quiettv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

/** Keeps the real view hierarchy accessible while its pixels are mirrored. */
final class HomeLayer extends FrameLayout {
    private View mirrored;
    HomeLayer(Context context){super(context);}
    void mirrored(View view){if(mirrored!=view){mirrored=view;invalidate();}}
    @Override protected boolean drawChild(Canvas canvas,View child,long time){
        if(child==mirrored)return false;
        return super.drawChild(canvas,child,time);
    }
}
