package local.quiettv.renderprobe;

import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/** Standalone synthetic experiment; no QuietTV data, network, or system changes. */
public final class ProbeActivity extends Activity implements Choreographer.FrameCallback {
    private static final String TAG = "Q4KPROBE";
    private static final long RUN_MS = 8000;
    private final Handler handler = new Handler();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long[] intervals = new long[2000];
    private final long[] draws = new long[2000];
    private String mode, surfaceFormat;
    private SurfaceView surface;
    private View normalView;
    private boolean ready, running;
    private int nativeWidth, nativeHeight, surfaceWidth, surfaceHeight;
    private int intervalCount, drawCount, startPss;
    private long started, previousFrame;

    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        mode = "surface".equals(getIntent().getStringExtra("mode")) ? "surface" : "view";
        surfaceFormat = "rgba".equals(getIntent().getStringExtra("format")) ? "rgba" : "default";
        Display display = getWindowManager().getDefaultDisplay();
        Point logical = new Point();
        display.getRealSize(logical);
        Display.Mode current = display.getMode();
        nativeWidth = current.getPhysicalWidth();
        nativeHeight = current.getPhysicalHeight();
        ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        Log.i(TAG, "START mode=" + mode + " logical=" + logical.x + "x" + logical.y
                + " display_mode=" + nativeWidth + "x" + nativeHeight + " sdk=" + Build.VERSION.SDK_INT
                + " low_ram=" + (manager != null && manager.isLowRamDevice()) + " surface_format=" + surfaceFormat);

        FrameLayout root = new FrameLayout(this);
        if ("surface".equals(mode)) {
            surface = new SurfaceView(this);
            if ("rgba".equals(surfaceFormat)) surface.getHolder().setFormat(PixelFormat.RGBA_8888);
            surface.getHolder().setFixedSize(nativeWidth, nativeHeight);
            surface.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override public void surfaceCreated(SurfaceHolder h) { Log.i(TAG, "SURFACE_CREATED"); }
                @Override public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {
                    surfaceWidth = width; surfaceHeight = height; ready = true;
                    Log.i(TAG, "SURFACE_CHANGED buffer=" + width + "x" + height + " format=" + format);
                    scheduleStart();
                }
                @Override public void surfaceDestroyed(SurfaceHolder h) { ready = false; Log.i(TAG, "SURFACE_DESTROYED"); }
            });
            root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        } else {
            normalView = new View(this) {
                @Override protected void onDraw(Canvas canvas) {
                    long start = System.nanoTime();
                    drawScene(canvas);
                    noteDraw(start);
                }
            };
            root.addView(normalView, new FrameLayout.LayoutParams(-1, -1));
            normalView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, oright, ob) -> {
                if (!ready && r > l && b > t) {
                    ready = true; surfaceWidth = r - l; surfaceHeight = b - t;
                    Log.i(TAG, "VIEW_LAYOUT window=" + surfaceWidth + "x" + surfaceHeight);
                    scheduleStart();
                }
            });
        }
        TextView overlay = new TextView(this);
        overlay.setText("Native focus overlay  OK");
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(18);
        overlay.setPadding(28, 12, 28, 12);
        overlay.setBackgroundColor(0xdd183449);
        overlay.setFocusableInTouchMode(true);
        overlay.setOnFocusChangeListener((v, hasFocus) -> Log.i(TAG, "NATIVE_FOCUS " + hasFocus));
        overlay.setOnClickListener(v -> { overlay.setText("Native focus overlay  selected"); Log.i(TAG, "NATIVE_CLICK"); });
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        overlayParams.setMargins(0, 20, 32, 0);
        root.addView(overlay, overlayParams);
        setContentView(root);
        overlay.requestFocus();
    }

    private void scheduleStart() {
        if (running) return;
        running = true;
        handler.postDelayed(() -> {
            if (!ready || isFinishing()) { Log.e(TAG, "ERROR surface_not_ready"); return; }
            started = SystemClock.uptimeMillis();
            startPss = pss();
            Choreographer.getInstance().postFrameCallback(this);
        }, 1500);
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!ready || isFinishing()) { running = false; return; }
        if (previousFrame != 0 && intervalCount < intervals.length) intervals[intervalCount++] = frameTimeNanos - previousFrame;
        previousFrame = frameTimeNanos;
        if (SystemClock.uptimeMillis() - started >= RUN_MS) {
            Log.i(TAG, "RESULT mode=" + mode + " callback_count=" + intervalCount
                    + " interval_p50_ms=" + percentile(intervals, intervalCount, 50)
                    + " interval_p95_ms=" + percentile(intervals, intervalCount, 95)
                    + " draw_count=" + drawCount + " draw_p95_ms=" + percentile(draws, drawCount, 95)
                    + " pss_start_kb=" + startPss + " pss_end_kb=" + pss());
            capture();
            running = false;
            return;
        }
        if (surface != null) {
            SurfaceHolder holder = surface.getHolder();
            Canvas canvas = null;
            long start = System.nanoTime();
            try {
                canvas = Build.VERSION.SDK_INT >= 26 ? holder.lockHardwareCanvas() : holder.lockCanvas();
                if (canvas != null) drawScene(canvas);
            } catch (RuntimeException e) {
                Log.e(TAG, "ERROR surface_draw=" + e.getClass().getSimpleName());
                running = false;
                return;
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
            noteDraw(start);
        } else {
            normalView.invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void noteDraw(long start) {
        if (running && drawCount < draws.length) draws[drawCount++] = System.nanoTime() - start;
    }
    private static int pss() { Debug.MemoryInfo info = new Debug.MemoryInfo(); Debug.getMemoryInfo(info); return info.getTotalPss(); }
    private static long percentile(long[] values, int count, int pct) {
        if (count == 0) return -1;
        long[] copy = Arrays.copyOf(values, count); Arrays.sort(copy);
        return Math.round(copy[Math.max(0, (int)Math.ceil(count * pct / 100.0) - 1)] / 1000000.0);
    }

    private void drawScene(Canvas canvas) {
        int w = canvas.getWidth(), h = canvas.getHeight();
        canvas.drawColor(0xff101c28);
        int saved = canvas.save();
        canvas.scale(w / 1920f, h / 1080f);
        paint.setShader(new LinearGradient(0, 0, 1920, 1080, 0xff263e50, 0xff0b1622, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 1920, 1080, paint); paint.setShader(null);
        paint.setColor(Color.WHITE); paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(54); canvas.drawText("QuietTV 4K rendering experiment", 72, 150, paint);
        paint.setTextSize(28); canvas.drawText("Synthetic cards and vector text; no personal content", 72, 210, paint);
        for (int i = 0; i < 5; i++) {
            float x = 72 + 360 * i;
            paint.setColor(i == 1 ? 0xff82c9bd : 0xff385267);
            canvas.drawRoundRect(x, 290, x + 320, 500, 18, 18, paint);
            paint.setColor(Color.WHITE); paint.setTextSize(25); canvas.drawText("Synthetic app " + (i + 1), x + 20, 550, paint);
        }
        paint.setTextSize(24); paint.setColor(0xffdeebed); canvas.drawText("One buffer-pixel stripe test below", 72, 820, paint);
        canvas.restoreToCount(saved);
        int left = w / 4, top = h - Math.max(90, h / 8);
        paint.setAntiAlias(false);
        for (int x = 0; x < 256; x++) { paint.setColor((x & 1) == 0 ? Color.WHITE : Color.BLACK); canvas.drawRect(left + x, top, left + x + 1, top + 64, paint); }
        paint.setAntiAlias(true);
    }

    private void capture() {
        if (Build.VERSION.SDK_INT < 26) { Log.i(TAG, "CAPTURE_UNSUPPORTED sdk=" + Build.VERSION.SDK_INT); return; }
        Bitmap bitmap = Bitmap.createBitmap(surfaceWidth, surfaceHeight, Bitmap.Config.ARGB_8888);
        PixelCopy.OnPixelCopyFinishedListener done = result -> {
            try {
                if (result != PixelCopy.SUCCESS) { Log.e(TAG, "ERROR pixelcopy=" + result); return; }
                int x = bitmap.getWidth() / 4, y = bitmap.getHeight() - Math.max(90, bitmap.getHeight() / 8) + 10;
                int mismatch = 0;
                for (int i = 0; i < 64; i++) {
                    int expected = (i & 1) == 0 ? Color.WHITE : Color.BLACK;
                    if (bitmap.getPixel(x + i, y) != expected) mismatch++;
                }
                File file = new File(getFilesDir(), "probe-" + mode + ".png");
                try (FileOutputStream out = new FileOutputStream(file)) { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new java.io.IOException("PNG failed"); }
                Log.i(TAG, "CAPTURE mode=" + mode + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                        + " stripe_mismatches=" + mismatch + " file=" + file.getName());
            } catch (Exception e) {
                Log.e(TAG, "ERROR capture=" + e.getClass().getSimpleName());
            } finally { bitmap.recycle(); }
        };
        if (surface != null) PixelCopy.request(surface.getHolder().getSurface(), bitmap, done, handler);
        else PixelCopy.request(getWindow(), bitmap, done, handler);
    }
}
