package local.quiettv.launcher;

import android.graphics.*;
import android.text.TextUtils;
import android.view.View;

/** Secondary weather summary next to the brand, above the clock. */
final class WeatherButton extends View {
    private final MainActivity host;
    private final WeatherRepository weather;
    private final Paint paint=new Paint(3);
    WeatherButton(MainActivity a,WeatherRepository w,Runnable action){super(a);host=a;weather=w;setTag("weather");setFocusableInTouchMode(true);setClickable(true);setOnFocusChangeListener(new OnFocusChangeListener(){public void onFocusChange(View v,boolean f){host.focusChanged(v,f);}});setOnClickListener(v->action.run());setOnLongClickListener(v->{host.launch(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://open-meteo.com/")),"Open-Meteo");return true;});}
    @Override protected void onDraw(Canvas c){
        RectF frame=new RectF(host.dp(2),host.dp(2),getWidth()-host.dp(2),getHeight()-host.dp(2));
        paint.setStyle(Paint.Style.FILL);paint.setColor(isFocused()?0x88203240:0x30203240);c.drawRoundRect(frame,host.dp(16),host.dp(16),paint);
        if(isFocused()){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(host.dp(2));paint.setColor(0xffedf4f6);c.drawRoundRect(frame,host.dp(16),host.dp(16),paint);}
        WeatherRepository.Data d=weather.data();
        if(d==null){line(c,weather.message(),14,27,15,0xffd4e0e7,"sans-serif");setContentDescription(weather.message());return;}
        icon(c,host.dp(23),host.dp(21),d.code);
        String summary=d.place.name+"  "+WeatherRepository.degrees(d.temperature)+"  "+WeatherRepository.condition(d.code)+(d.stale()?" · 旧":"");
        line(c,summary,47,21,16,0xffedf4f6,"sans-serif-medium");
        line(c,"Open-Meteo.com ↗ 长按",47,35,9,0xffd4e0e7,"sans-serif");
        setContentDescription(d.place.name+"，"+WeatherRepository.degrees(d.temperature)+"，"+WeatherRepository.condition(d.code)+"，来源 Open-Meteo，确认键查看预报，长按打开来源网站");
    }
    private void line(Canvas c,String value,int x,int y,int size,int color,String font){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTypeface(Typeface.create(font,0));paint.setTextAlign(Paint.Align.LEFT);paint.setTextSize(host.dp(size));String text=TextUtils.ellipsize(value,new android.text.TextPaint(paint),getWidth()-host.dp(x+14),TextUtils.TruncateAt.END).toString();c.drawText(text,host.dp(x),host.dp(y),paint);}
    private void icon(Canvas c,float x,float y,int code){
        paint.setColor(0xffedf4f6);paint.setStrokeWidth(host.dp(2));paint.setStyle(Paint.Style.STROKE);
        if(code<=1){c.drawCircle(x,y,host.dp(8),paint);for(int i=0;i<8;i++){double angle=i*Math.PI/4;c.drawLine(x+(float)Math.cos(angle)*host.dp(13),y+(float)Math.sin(angle)*host.dp(13),x+(float)Math.cos(angle)*host.dp(17),y+(float)Math.sin(angle)*host.dp(17),paint);}}
        else {Path cloud=new Path();cloud.moveTo(x-host.dp(12),y+host.dp(5));cloud.cubicTo(x-host.dp(21),y+host.dp(5),x-host.dp(18),y-host.dp(9),x-host.dp(7),y-host.dp(7));cloud.cubicTo(x-host.dp(4),y-host.dp(18),x+host.dp(13),y-host.dp(14),x+host.dp(11),y-host.dp(4));cloud.cubicTo(x+host.dp(23),y-host.dp(5),x+host.dp(20),y+host.dp(7),x+host.dp(12),y+host.dp(5));cloud.close();c.drawPath(cloud,paint);if(code>=51)for(int i=0;i<3;i++)c.drawLine(x+host.dp((i-1)*8),y+host.dp(10),x+host.dp((i-1)*8-3),y+host.dp(15),paint);}
    }
}
