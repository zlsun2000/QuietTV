package local.quiettv.launcher;

import android.graphics.*;
import android.text.TextUtils;
import android.view.View;

/** Secondary weather summary next to the brand, above the clock. */
final class WeatherButton extends View {
    private final MainActivity host;
    private final WeatherRepository weather;
    private final Paint paint=new Paint(3);
    private final UiIcon icon;
    private final android.graphics.drawable.Drawable focusOutline;

    WeatherButton(MainActivity a,WeatherRepository w,Runnable action){
        super(a);
        host=a;
        weather=w;
        icon=new UiIcon(UiIcon.Glyph.CLOUD,host.dp(25),0xffedf4f6,true);
        focusOutline=host.desktopFocusOutline(16);
        setTag("weather");
        setFocusableInTouchMode(true);
        setClickable(true);
        setOnFocusChangeListener((v,focused)->{
            setBackground(focused?focusOutline:null);
            host.focusChanged(v,focused);
        });
        setOnClickListener(v->action.run());
        setOnLongClickListener(v->{
            host.launch(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://open-meteo.com/")),"Open-Meteo");
            return true;
        });
    }
    @Override protected void onDraw(Canvas c){
        WeatherRepository.Data d=weather.data();
        if(d==null){line(c,weather.message(),14,27,15,0xffd4e0e7,"sans-serif");setContentDescription(weather.message());return;}
        int size = icon.getIntrinsicWidth();
        int left = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? getWidth() - host.dp(11) - size : host.dp(11);
        int top = (getHeight() - size) / 2;
        icon.setGlyph(weatherGlyph(d.code));
        icon.setBounds(left, top, left + size, top + size);
        icon.draw(c);
        String summary=d.place.name+"  "+weather.degrees(d.temperature)+"  "+weather.conditionLabel(d.code)+(d.stale()?host.getString(R.string.weather_status_stale):"");
        line(c,summary,47,21,16,0xffedf4f6,"sans-serif-medium");
        line(c,host.getString(R.string.weather_source_hint),47,35,9,0xffd4e0e7,"sans-serif");
        setContentDescription(host.getString(R.string.weather_content_description,d.place.name,weather.degrees(d.temperature),weather.conditionLabel(d.code)));
    }
    private void line(Canvas canvas, String value, int x, int y, int size, int color, String font) {
        boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        paint.setColor(color);
        paint.setTypeface(Typeface.create(font, 0));
        paint.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        paint.setTextSize(host.sp(size));
        paint.setShadowLayer(host.dp(3), 0, host.dp(1), 0xeb040b13);
        float width = Math.max(0, getWidth() - host.dp(x + 14));
        String text = TextUtils.ellipsize(value, new android.text.TextPaint(paint),
                width, TextUtils.TruncateAt.END).toString();
        canvas.drawText(text, rtl ? getWidth() - host.dp(x) : host.dp(x), host.dp(y), paint);
    }

    private UiIcon.Glyph weatherGlyph(int code) {
        if (code == 0 || code == 1) return UiIcon.Glyph.SUN;
        if (code == 45 || code == 48) return UiIcon.Glyph.FOG;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return UiIcon.Glyph.SNOW;
        if (code >= 95 && code <= 99) return UiIcon.Glyph.STORM;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return UiIcon.Glyph.RAIN;
        return UiIcon.Glyph.CLOUD;
    }
}
