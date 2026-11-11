package local.quiettv.launcher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

/** QuietTV's line icons, drawn on the same 24-unit grid. */
final class UiIcon extends Drawable {
    enum Glyph {
        CONTROLS, LAYOUT, WIFI, ETHERNET, LINK, OFFLINE, INPUT, HDMI, AV, TV,
        PICTURE, APPS, PLAYBACK, SETTINGS, REFRESH, CHEVRON, EYE_OFF,
        SUN, CLOUD, RAIN, SNOW, FOG, STORM
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int size;
    private Glyph glyph;
    private int color;
    private int alpha = 255;
    private final boolean outline;

    UiIcon(Glyph glyph, int size, int color, boolean outline) {
        this.glyph = glyph;
        this.size = size;
        this.color = color;
        this.outline = outline;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setGlyph(Glyph glyph) {
        if (this.glyph == glyph) return;
        this.glyph = glyph;
        invalidateSelf();
    }

    void setColor(int color) {
        if (this.color == color) return;
        this.color = color;
        invalidateSelf();
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        int saved = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
        if (glyph == Glyph.CHEVRON && getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            canvas.scale(-1, 1, 12, 12);
        }
        if (outline) drawGlyph(canvas, 0xc8051019, 3.6f);
        drawGlyph(canvas, color, 1.75f);
        canvas.restoreToCount(saved);
    }

    private void drawGlyph(Canvas c, int tint, float stroke) {
        paint.setColor(tint);
        paint.setAlpha(Color.alpha(tint) * alpha / 255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        switch (glyph) {
            case CONTROLS:
                c.drawLine(4, 7, 8, 7, paint);
                c.drawLine(13, 7, 20, 7, paint);
                c.drawCircle(10.5f, 7, 2.5f, paint);
                c.drawLine(4, 17, 12, 17, paint);
                c.drawLine(17, 17, 20, 17, paint);
                c.drawCircle(14.5f, 17, 2.5f, paint);
                break;
            case LAYOUT:
                box(c, 3, 4, 21, 20, 2.5f);
                c.drawLine(3, 10, 21, 10, paint);
                c.drawLine(12, 10, 12, 20, paint);
                break;
            case WIFI:
                wifi(c);
                break;
            case ETHERNET:
                line(c, true, 6, 4, 18, 4, 18, 13, 15, 13, 15, 16, 9, 16, 9, 13, 6, 13);
                c.drawLine(12, 16, 12, 21, paint);
                for (int x = 9; x <= 15; x += 3) c.drawLine(x, 7, x, 9, paint);
                break;
            case LINK:
                c.drawCircle(12, 12, 8.5f, paint);
                c.drawOval(8.2f, 3.5f, 15.8f, 20.5f, paint);
                c.drawLine(3.5f, 12, 20.5f, 12, paint);
                break;
            case OFFLINE:
                c.drawArc(1.4f, 5.5f, 22.6f, 23f, 233, 33, false, paint);
                c.drawArc(5.4f, 10, 18.6f, 20.5f, 244, 22, false, paint);
                c.drawCircle(12, 18.2f, .8f, paint);
                c.drawLine(4, 4, 20, 20, paint);
                break;
            case INPUT:
                c.drawRoundRect(10, 5, 21, 19, 2.5f, 2.5f, paint);
                c.drawLine(3, 12, 15, 12, paint);
                line(c, false, 12, 8.5f, 15.5f, 12, 12, 15.5f);
                break;
            case HDMI:
                line(c, true, 3, 7, 21, 7, 21, 13, 18, 17, 6, 17, 3, 13);
                for (int x = 7; x <= 17; x += 2) c.drawLine(x, 10.5f, x, 13, paint);
                break;
            case AV:
                for (int x = 6; x <= 18; x += 6) {
                    c.drawCircle(x, 10, 2.2f, paint);
                    c.drawLine(x, 15, x, 19, paint);
                }
                break;
            case TV:
                box(c, 3, 4, 21, 17, 2.6f);
                c.drawLine(12, 17, 12, 20, paint);
                c.drawLine(8, 20, 16, 20, paint);
                break;
            case PICTURE:
                box(c, 3, 4, 21, 20, 2.5f);
                c.drawCircle(8, 9, 1.5f, paint);
                line(c, false, 3, 16, 8, 12, 12, 15.5f, 16, 10.5f, 21, 15);
                break;
            case APPS:
                box(c, 4, 4, 10, 10, 1.5f);
                box(c, 14, 4, 20, 10, 1.5f);
                box(c, 4, 14, 10, 20, 1.5f);
                box(c, 14, 14, 20, 20, 1.5f);
                break;
            case PLAYBACK:
                box(c, 3, 5, 21, 19, 2.5f);
                line(c, true, 10, 8.8f, 15.5f, 12, 10, 15.2f);
                break;
            case SETTINGS:
                line(c, true, 10, 3, 14, 3, 14.7f, 5.4f, 16.5f, 6.5f, 19, 6, 21, 9.5f,
                        19.3f, 11, 19.3f, 13, 21, 14.5f, 19, 18, 16.5f, 17.5f, 14.7f, 18.6f,
                        14, 21, 10, 21, 9.3f, 18.6f, 7.5f, 17.5f, 5, 18, 3, 14.5f, 4.7f, 13,
                        4.7f, 11, 3, 9.5f, 5, 6, 7.5f, 6.5f, 9.3f, 5.4f);
                c.drawCircle(12, 12, 3.2f, paint);
                break;
            case REFRESH:
                c.drawArc(4, 4, 20, 20, -54, 294, false, paint);
                line(c, false, 20, 5, 20, 10, 15, 10);
                break;
            case CHEVRON:
                line(c, false, 9, 6, 15, 12, 9, 18);
                break;
            case EYE_OFF:
                path.reset();
                path.moveTo(3, 12);
                path.cubicTo(7, 6, 13, 5, 17, 8);
                path.cubicTo(19, 9, 20, 11, 21, 12);
                path.cubicTo(19, 15, 16, 18, 12, 18);
                path.cubicTo(8, 18, 5, 15, 3, 12);
                c.drawPath(path, paint);
                c.drawLine(4, 4, 20, 20, paint);
                break;
            case SUN:
                c.drawCircle(12, 12, 4.5f, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    float dx = (float)Math.cos(angle);
                    float dy = (float)Math.sin(angle);
                    c.drawLine(12 + 7.4f * dx, 12 + 7.4f * dy, 12 + 9.6f * dx, 12 + 9.6f * dy, paint);
                }
                break;
            case CLOUD:
                cloud(c);
                break;
            case RAIN:
                cloud(c);
                for (int x = 7; x <= 17; x += 5) c.drawLine(x, 17.5f, x - 1.2f, 21, paint);
                break;
            case SNOW:
                cloud(c);
                for (int x = 7; x <= 17; x += 5) c.drawCircle(x, 19.8f, .65f, paint);
                break;
            case FOG:
                cloud(c);
                c.drawLine(5, 18.5f, 19, 18.5f, paint);
                c.drawLine(8, 21.5f, 16, 21.5f, paint);
                break;
            case STORM:
                cloud(c);
                line(c, false, 12.5f, 16, 10, 19.2f, 13.5f, 19.2f, 11.5f, 22);
                break;
        }
    }

    private void wifi(Canvas c) {
        c.drawArc(1.4f, 5.5f, 22.6f, 23f, 225, 90, false, paint);
        c.drawArc(5.4f, 10, 18.6f, 20.5f, 225, 90, false, paint);
        c.drawArc(9, 14, 15, 19.5f, 225, 90, false, paint);
        c.drawCircle(12, 18.5f, .65f, paint);
    }

    private void cloud(Canvas c) {
        path.reset();
        path.moveTo(7, 15);
        path.cubicTo(2, 15, 2, 8, 7, 8);
        path.cubicTo(7.6f, 2.5f, 16.5f, 2.8f, 17.1f, 8);
        path.cubicTo(22, 7.6f, 22.8f, 15, 17, 15);
        path.close();
        c.drawPath(path, paint);
    }

    private void box(Canvas c, float left, float top, float right, float bottom, float radius) {
        c.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    private void line(Canvas c, boolean closed, float... points) {
        path.reset();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        if (closed) path.close();
        c.drawPath(path, paint);
    }
}
