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
    RecentTile(MainActivity a,PlaybackRecord r,Covers cache){super(a);host=a;record=r;covers=cache;if(r.generic())try{appIcon=a.getPackageManager().getApplicationIcon(r.source);}catch(Exception ignored){}setTag("recent|"+r.key());setFocusableInTouchMode(true);setClickable(true);setContentDescription(r.title+"，"+r.details()+"，"+r.actionLabel());setOnFocusChangeListener(new OnFocusChangeListener(){public void onFocusChange(View v,boolean f){host.focusChanged(v,f);}});}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();RectF frame=new RectF(host.dp(3),host.dp(3),w-host.dp(3),h-host.dp(48));float radius=host.dp(12);
        p.setShader(new LinearGradient(0,0,w,frame.bottom,record.music()?new int[]{0xff52304b,0xff192135}:new int[]{0xff365569,0xff142536},null,Shader.TileMode.CLAMP));c.drawRoundRect(frame,radius,radius,p);p.setShader(null);
        c.save();path.reset();path.addRoundRect(frame,radius,radius,Path.Direction.CW);c.clipPath(path);
        Bitmap b=covers.get(record.cover,coverReady);
        if(b!=null){float width=record.music()?frame.height():frame.width();float scale=Math.max(width/b.getWidth(),frame.height()/b.getHeight());float bw=b.getWidth()*scale,bh=b.getHeight()*scale;RectF artwork=new RectF(frame.centerX()-width/2,frame.top,frame.centerX()+width/2,frame.bottom);c.save();c.clipRect(artwork);c.drawBitmap(b,null,new RectF(frame.centerX()-bw/2,frame.centerY()-bh/2,frame.centerX()+bw/2,frame.centerY()+bh/2),p);c.restore();}
        else if(appIcon!=null){int size=host.dp(64),left=(int)frame.centerX()-size/2,top=(int)frame.centerY()-size/2;appIcon.setBounds(left,top,left+size,top+size);appIcon.draw(c);}
        else {p.setColor(0xffb9d9e7);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(host.dp(25));p.setTypeface(Typeface.create("sans-serif-medium",0));c.drawText(record.service(),w/2,frame.centerY()+host.dp(6),p);}
        p.setShader(new LinearGradient(0,frame.bottom-host.dp(40),0,frame.bottom,new int[]{0x0007131c,0xd907131c},null,Shader.TileMode.CLAMP));c.drawRect(frame,p);p.setShader(null);
        p.setColor(0xffedf4f6);p.setTextAlign(Paint.Align.LEFT);p.setTextSize(host.dp(12));p.setTypeface(Typeface.create("sans-serif",0));c.drawText(record.service()+" · "+host.recordAction(record),host.dp(12),frame.bottom-host.dp(12),p);
        if(record.duration>0&&record.positionKnown){float fraction=Math.min(1,record.position/(float)record.duration);p.setColor(0x66ffffff);c.drawRect(frame.left,frame.bottom-host.dp(3),frame.right,frame.bottom,p);p.setColor(0xff8fe0d0);c.drawRect(frame.left,frame.bottom-host.dp(3),frame.left+frame.width()*fraction,frame.bottom,p);}
        c.restore();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(host.dp(isFocused()?2:1));p.setColor(isFocused()?0xfff2f5fa:0x35c2d6e2);c.drawRoundRect(frame,radius,radius,p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(host.dp(15));p.setColor(0xfff2f5fa);c.drawText(ellipsis(record.title,w-host.dp(12)),host.dp(6),h-host.dp(27),p);
        p.setTypeface(Typeface.create("sans-serif",0));p.setTextSize(host.dp(12));p.setColor(0xffb9c9d3);c.drawText(ellipsis(record.details(),w-host.dp(12)),host.dp(6),h-host.dp(8),p);
    }
    private String ellipsis(String text,float width){return TextUtils.ellipsize(text,new android.text.TextPaint(p),width,TextUtils.TruncateAt.END).toString();}
}
