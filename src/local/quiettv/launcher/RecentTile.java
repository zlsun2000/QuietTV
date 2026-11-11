package local.quiettv.launcher;

import android.graphics.*;
import android.text.TextUtils;
import android.view.View;

/** A full-row-compatible content card, using the existing launcher focus behavior. */
final class RecentTile extends View {
    final PlaybackRecord record;
    private final MainActivity host;
    private final Covers covers;
    private final Paint p=new Paint(3);
    private final Path path=new Path();
    private final Runnable coverReady=new Runnable(){public void run(){invalidate();}};
    private android.graphics.drawable.Drawable appIcon;
    RecentTile(MainActivity a,PlaybackRecord r,Covers cache){super(a);host=a;record=r;covers=cache;if(r.generic())try{appIcon=a.getPackageManager().getApplicationIcon(r.source);}catch(Exception ignored){}setTag("recent|"+r.key());setFocusableInTouchMode(true);setClickable(true);updateDescription();setOnFocusChangeListener(new OnFocusChangeListener(){public void onFocusChange(View v,boolean f){host.focusChanged(v,f);}});}
    void updateDescription(){setContentDescription(record.title+"，来源："+record.service()+"，"+record.details()+"，操作："+host.recordAction(record));}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();RectF frame=new RectF(host.dp(3),host.dp(3),w-host.dp(3),h-host.dp(48));float radius=host.dp(12);
        p.setShader(new LinearGradient(0,0,w,frame.bottom,record.music()?new int[]{0xff52304b,0xff192135}:new int[]{0xff365569,0xff142536},null,Shader.TileMode.CLAMP));c.drawRoundRect(frame,radius,radius,p);p.setShader(null);
        c.save();path.reset();path.addRoundRect(frame,radius,radius,Path.Direction.CW);c.clipPath(path);
        Bitmap b=covers.get(record.cover,coverReady);
        if(b!=null){float width=record.music()?frame.height():frame.width();float scale=Math.max(width/b.getWidth(),frame.height()/b.getHeight());float bw=b.getWidth()*scale,bh=b.getHeight()*scale;RectF artwork=new RectF(frame.centerX()-width/2,frame.top,frame.centerX()+width/2,frame.bottom);c.save();c.clipRect(artwork);c.drawBitmap(b,null,new RectF(frame.centerX()-bw/2,frame.centerY()-bh/2,frame.centerX()+bw/2,frame.centerY()+bh/2),p);c.restore();}
        else if(appIcon!=null){int size=host.dp(64),left=(int)frame.centerX()-size/2,top=(int)frame.centerY()-size/2;appIcon.setBounds(left,top,left+size,top+size);appIcon.draw(c);}
        else {p.setColor(0xffb9d9e7);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(host.sp(25));p.setTypeface(Typeface.create("sans-serif-medium",0));c.drawText(ellipsis(record.service(),Math.max(0,frame.width()-host.dp(24))),w/2,frame.centerY()+host.dp(6),p);}
        p.setShader(new LinearGradient(0,frame.bottom-host.dp(40),0,frame.bottom,new int[]{0x0007131c,0xd907131c},null,Shader.TileMode.CLAMP));c.drawRect(frame,p);p.setShader(null);
        boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
        // Reserve independent space for the real action and the source, so a
        // long app name cannot conceal whether this opens an app or content.
        float inset=host.dp(9),gap=host.dp(6),inner=Math.max(0,frame.width()-inset*2);
        p.setTextSize(host.sp(11));p.setTypeface(Typeface.create("sans-serif-medium",0));String action=host.recordAction(record);float actionWidth=Math.min(inner*.52f,p.measureText(action)+host.dp(12));
        float actionLeft=rtl?frame.left+inset:frame.right-inset-actionWidth,actionRight=actionLeft+actionWidth;
        float bandBottom=frame.bottom-host.dp(8),bandHeight=Math.max(host.dp(23),host.sp(12)*1.3f+host.dp(6));float bandTop=bandBottom-bandHeight;
        p.setColor(0xe6172734);c.drawRoundRect(new RectF(actionLeft,bandTop,actionRight,bandBottom),host.dp(5),host.dp(5),p);
        p.setColor(0xfff2f5fa);p.setTextAlign(Paint.Align.CENTER);float actionY=(bandTop+bandBottom-p.ascent()-p.descent())/2;c.drawText(ellipsis(action,Math.max(0,actionWidth-host.dp(8))),(actionLeft+actionRight)/2,actionY,p);
        p.setColor(0xffd8e6ec);p.setTextAlign(rtl?Paint.Align.RIGHT:Paint.Align.LEFT);p.setTextSize(host.sp(12));p.setTypeface(Typeface.create("sans-serif",0));float sourceWidth=Math.max(0,inner-actionWidth-gap);float sourceX=rtl?frame.right-inset:frame.left+inset;float sourceY=(bandTop+bandBottom-p.ascent()-p.descent())/2;c.drawText(ellipsis(record.service(),sourceWidth),sourceX,sourceY,p);
        if(record.duration>0&&record.positionKnown){float fraction=Math.min(1,record.position/(float)record.duration);p.setColor(0x66ffffff);c.drawRect(frame.left,frame.bottom-host.dp(3),frame.right,frame.bottom,p);p.setColor(0xff8fe0d0);float progressWidth=frame.width()*fraction;c.drawRect(rtl?frame.right-progressWidth:frame.left,frame.bottom-host.dp(3),rtl?frame.right:frame.left+progressWidth,frame.bottom,p);}
        c.restore();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(host.dp(isFocused()?2:1));p.setColor(isFocused()?0xfff2f5fa:0x35c2d6e2);c.drawRoundRect(frame,radius,radius,p);p.setStyle(Paint.Style.FILL);
        p.setColor(0xda0b1923);c.drawRoundRect(host.dp(3),frame.bottom+host.dp(2),w-host.dp(3),h-host.dp(2),host.dp(5),host.dp(5),p);
        float textX=rtl?w-host.dp(6):host.dp(6);p.setTextAlign(rtl?Paint.Align.RIGHT:Paint.Align.LEFT);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(Math.min(host.sp(15),host.dp(17)));p.setColor(0xfff2f5fa);c.drawText(ellipsis(record.title,w-host.dp(12)),textX,h-host.dp(27),p);
        p.setTypeface(Typeface.create("sans-serif",0));p.setTextSize(Math.min(host.sp(12),host.dp(14)));p.setColor(0xffb9c9d3);c.drawText(ellipsis(record.details(),w-host.dp(12)),textX,h-host.dp(8),p);
    }
    private String ellipsis(String text,float width){return TextUtils.ellipsize(text,new android.text.TextPaint(p),width,TextUtils.TruncateAt.END).toString();}
}
