package local.quiettv.launcher;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A single remote-friendly panel. Browsing the left column previews the right column. */
final class ControlCenter extends Dialog {
    enum Section {
        HOME(R.string.settings_home_layout, UiIcon.Glyph.LAYOUT),
        NETWORK(R.string.control_network, UiIcon.Glyph.WIFI),
        INPUTS(R.string.toolbar_inputs, UiIcon.Glyph.INPUT),
        SETTINGS(R.string.control_settings, UiIcon.Glyph.SETTINGS);

        final int label;
        final UiIcon.Glyph glyph;

        Section(int label, UiIcon.Glyph glyph) {
            this.label = label;
            this.glyph = glyph;
        }
    }

    static final class Position {
        final Section section;
        final String action;

        Position(Section section, String action) {
            this.section = section;
            this.action = action;
        }
    }

    static final class Item {
        final String key, title, subtitle;
        final UiIcon.Glyph glyph;
        final Boolean checked;
        final boolean opensPage;
        final Runnable run;

        Item(String key, String title, String subtitle, UiIcon.Glyph glyph,
             Boolean checked, boolean opensPage, Runnable run) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.glyph = glyph;
            this.checked = checked;
            this.opensPage = opensPage;
            this.run = run;
        }

        boolean sameDisplay(Item other) {
            return key.equals(other.key) && title.equals(other.title)
                    && Objects.equals(subtitle, other.subtitle) && glyph == other.glyph
                    && Objects.equals(checked, other.checked) && opensPage == other.opensPage;
        }
    }

    static final class Page {
        final String status;
        final List<Item> items;

        Page(String status, List<Item> items) {
            this.status = status;
            this.items = items;
        }
    }

    private static final int INK = 0xff13252d;
    private static final int TEXT = 0xffeef6f8;
    private static final int MUTED = 0xffafc4ce;
    private static final int ACCENT = 0xffa6e3d9;
    private static final int FOCUS = 0xffdbf2ed;
    private static final Section[] SECTIONS = Section.values();
    private final MainActivity host;
    private final LinearLayout surface, rows;
    private final ScrollView detailScroll;
    private final TextView time, date, heading, status, help, footerStatus;
    private final String version;
    private final EnumMap<Section, Row> tabs = new EnumMap<>(Section.class);
    private final EnumMap<Section, String> lastAction = new EnumMap<>(Section.class);
    private final ArrayList<Row> actions = new ArrayList<>();
    private final int panelWidth, panelHeight;
    private Section section = Section.HOME;
    private String focusedAction;
    private List<Item> items = new ArrayList<>();
    private boolean rebuilding;

    ControlCenter(MainActivity host) {
        super(host);
        this.host = host;
        version = versionName();
        panelWidth = host.dp(802);
        float fontScale = host.getResources().getConfiguration().fontScale;
        panelHeight = host.dp(464 + Math.min(106, Math.round(110 * Math.max(0, fontScale - 1))));

        surface = column();
        surface.setTag("control-center");
        surface.setPaddingRelative(host.dp(24), host.dp(20), host.dp(24), host.dp(16));
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff203744, 0xff111e2a, 0xff0f1c26});
        panel.setCornerRadius(host.dp(22));
        panel.setStroke(host.dp(1), 0xff486573);
        surface.setBackground(panel);

        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = icon(UiIcon.Glyph.CONTROLS, 23, ACCENT);
        mark.setBackground(pill(0xff263f4a, 12, 0xff38545e));
        mark.setPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(8));
        header.addView(mark, new LinearLayout.LayoutParams(host.dp(39), host.dp(39)));
        TextView title = medium(host.getString(R.string.control_center), 26, TEXT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.setMarginStart(host.dp(12));
        header.addView(title, titleParams);
        LinearLayout when = column();
        when.setGravity(Gravity.END);
        time = medium("", 20, TEXT);
        date = host.text("", 12, MUTED);
        when.addView(time);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
        dateParams.topMargin = host.dp(3);
        when.addView(date, dateParams);
        header.addView(when, new LinearLayout.LayoutParams(-2, -2));
        surface.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(host);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
        bodyParams.topMargin = host.dp(17);
        surface.addView(body, bodyParams);

        ScrollView navigationScroll = scroller();
        LinearLayout navigation = column();
        navigation.setPaddingRelative(0, host.dp(3), 0, host.dp(3));
        navigationScroll.addView(navigation);
        body.addView(navigationScroll, new LinearLayout.LayoutParams(host.dp(185), -1));
        for (Section tab : SECTIONS) {
            Row view = new Row(host.getString(tab.label), null, tab.glyph, null, false);
            view.setTag("control-tab:" + tab.name());
            view.setOnFocusChangeListener((v, focused) -> {
                if (focused && !rebuilding) {
                    focusedAction = null;
                    select(tab);
                    updateHelp();
                }
                styleTabs();
            });
            view.setOnClickListener(v -> {
                select(tab);
                focusActions();
            });
            tabs.put(tab, view);
            addRow(navigation, view, 6);
        }

        detailScroll = scroller();
        detailScroll.setBackground(pill(0xff0e1c27, 15, 0xff2c4350));
        LinearLayout detail = column();
        detail.setPaddingRelative(host.dp(18), host.dp(15), host.dp(18), host.dp(12));
        detailScroll.addView(detail);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, -1, 1);
        detailParams.setMarginStart(host.dp(17));
        body.addView(detailScroll, detailParams);
        heading = medium("", 21, TEXT);
        heading.setTag("control-section-title");
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.bottomMargin = host.dp(10);
        detail.addView(heading, headingParams);
        status = host.text("", 13, MUTED);
        status.setTag("control-section-status");
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.bottomMargin = host.dp(12);
        detail.addView(status, statusParams);
        rows = column();
        detail.addView(rows);

        LinearLayout footer = new LinearLayout(host);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        help = host.text("", 12, MUTED);
        footer.addView(help, new LinearLayout.LayoutParams(0, -2, 1));
        footerStatus = host.text("", 12, MUTED);
        footerStatus.setTag("control-footer-status");
        footer.addView(footerStatus, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = host.dp(13);
        surface.addView(footer, footerParams);

        setContentView(surface);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = panelWidth;
            params.height = panelHeight;
            params.gravity = Gravity.CENTER;
            params.dimAmount = .40f;
            window.setAttributes(params);
            window.getDecorView().setSystemUiVisibility(5894);
        }
    }

    private String versionName() {
        try { return host.getPackageManager().getPackageInfo(host.getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return ""; }
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(host);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private ScrollView scroller() {
        ScrollView scroll = new ScrollView(host);
        scroll.setFocusable(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return scroll;
    }

    private TextView medium(String label, int size, int color) {
        TextView view = host.text(label, size, color);
        view.setTypeface(Typeface.create("sans-serif-medium", 0));
        return view;
    }

    private ImageView icon(UiIcon.Glyph glyph, int size, int color) {
        ImageView image = new ImageView(host);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setImageDrawable(new UiIcon(glyph, host.dp(size), color, false));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return image;
    }

    private GradientDrawable pill(int color, int radius, int border) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(host.dp(radius));
        background.setColor(color);
        if (border != 0) background.setStroke(host.dp(1), border);
        return background;
    }

    private void addRow(LinearLayout parent, Row row, int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = host.dp(gap);
        parent.addView(row, params);
    }

    Position position() { return new Position(section, focusedAction); }

    void position(Position position) {
        if (position == null) return;
        section = position.section;
        focusedAction = position.action;
        if (focusedAction != null) lastAction.put(section, focusedAction);
    }

    void present(Position position) {
        position(position);
        refresh();
        if (!isShowing()) {
            rebuilding = true;
            try { show(); }
            finally { rebuilding = false; }
        }
        focusSaved();
    }

    private void select(Section tab) {
        if (section == tab) return;
        section = tab;
        focusedAction = null;
        detailScroll.scrollTo(0, 0);
        refreshContent();
    }

    void refresh() {
        updateClock(new Date());
        String home = host.getString(SystemEntrypoints.defaultHome(host)
                ? R.string.control_home_active : R.string.control_home_unknown);
        footerStatus.setText(version.isEmpty() ? home : home + " · " + version);
        refreshContent();
    }

    void updateClock(Date now) {
        time.setText(DateFormat.getTimeFormat(host).format(now));
        Locale locale = Locale.getDefault();
        String pattern = DateFormat.getBestDateTimePattern(locale, host.getString(R.string.header_date_skeleton));
        date.setText(new SimpleDateFormat(pattern, locale).format(now));
    }

    void refresh(Section changed) {
        if (changed == section && isShowing()) refreshContent();
    }

    private void refreshContent() {
        Page page = host.controlPage(section);
        heading.setText(section.label);
        status.setText(page.status);
        status.setVisibility(page.status.isEmpty() ? View.GONE : View.VISIBLE);
        boolean same = sameRows(page.items);
        items = page.items;
        if (!same) rebuildActions();
        styleTabs();
        updateHelp();
    }

    private boolean sameRows(List<Item> next) {
        if (next.size() != items.size()) return false;
        for (int i = 0; i < next.size(); i++) {
            if (!next.get(i).sameDisplay(items.get(i))) return false;
        }
        return true;
    }

    private void rebuildActions() {
        String restore = focusedAction;
        rebuilding = true;
        rows.removeAllViews();
        actions.clear();
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            Item item = items.get(i);
            Row row = new Row(item.title, item.subtitle, item.glyph, item.checked, item.opensPage);
            row.setTag("control-action:" + item.key);
            row.setOnFocusChangeListener((v, focused) -> {
                if (focused && !rebuilding) {
                    focusedAction = items.get(index).key;
                    lastAction.put(section, focusedAction);
                    updateHelp();
                }
                row.style(false, focused);
            });
            row.setOnClickListener(v -> {
                row.requestFocus();
                items.get(index).run.run();
            });
            row.style(false, false);
            actions.add(row);
            addRow(rows, row, 6);
        }
        rebuilding = false;
        if (restore != null && isShowing()) {
            focusedAction = restore;
            focusActions();
        }
    }

    void focusSaved() {
        if (focusedAction != null) focusActions();
        else tabs.get(section).requestFocus();
    }

    private void focusActions() {
        if (actions.isEmpty()) {
            tabs.get(section).requestFocus();
            return;
        }
        String remembered = focusedAction == null ? lastAction.get(section) : focusedAction;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key.equals(remembered)) {
                actions.get(i).requestFocus();
                return;
            }
        }
        actions.get(0).requestFocus();
    }

    private void styleTabs() {
        for (Section tab : SECTIONS) {
            Row row = tabs.get(tab);
            row.setSelected(tab == section);
            row.style(tab == section, row.isFocused());
        }
    }

    private void updateHelp() {
        help.setText(focusedAction == null ? R.string.control_navigation : R.string.control_action_navigation);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (event.getAction() != KeyEvent.ACTION_DOWN || (key != KeyEvent.KEYCODE_DPAD_LEFT
                && key != KeyEvent.KEYCODE_DPAD_RIGHT && key != KeyEvent.KEYCODE_DPAD_UP
                && key != KeyEvent.KEYCODE_DPAD_DOWN)) return super.dispatchKeyEvent(event);

        boolean rtl = surface.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int towardActions = rtl ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        int towardTabs = rtl ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        int actionIndex = actions.indexOf(getCurrentFocus());
        if (actionIndex >= 0) {
            if (key == towardTabs) tabs.get(section).requestFocus();
            if (key == KeyEvent.KEYCODE_DPAD_UP && actionIndex > 0) actions.get(actionIndex - 1).requestFocus();
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && actionIndex + 1 < actions.size()) actions.get(actionIndex + 1).requestFocus();
        } else {
            if (key == towardActions) focusActions();
            int index = section.ordinal();
            if (key == KeyEvent.KEYCODE_DPAD_UP && index > 0) tabs.get(SECTIONS[index - 1]).requestFocus();
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && index + 1 < SECTIONS.length) tabs.get(SECTIONS[index + 1]).requestFocus();
        }
        return true;
    }

    /** Prepare the small reusable panel while idle. The caller skips this on low-memory TVs. */
    boolean warmUp(int windowHeight) {
        if (windowHeight <= 0 || (long)panelWidth * panelHeight > 3_000_000L) return false;
        Bitmap bitmap = null;
        try {
            refresh();
            surface.measure(View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(panelHeight, View.MeasureSpec.EXACTLY));
            surface.layout(0, 0, panelWidth, panelHeight);
            bitmap = Bitmap.createBitmap(panelWidth, panelHeight, Bitmap.Config.ARGB_8888);
            surface.draw(new Canvas(bitmap));
            return true;
        } catch (OutOfMemoryError ignored) {
            return false;
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private final class Row extends LinearLayout {
        private final UiIcon leading, trailing;
        private final TextView label, note;
        private final Toggle toggle;
        private int styleState = -1;

        Row(String title, String subtitle, UiIcon.Glyph glyph, Boolean checked, boolean opensPage) {
            super(host);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(host.dp(48));
            setPaddingRelative(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
            setFocusableInTouchMode(true);
            setClickable(true);
            setId(View.generateViewId());
            ImageView image = icon(glyph, 22, ACCENT);
            leading = (UiIcon)image.getDrawable();
            addView(image, new LinearLayout.LayoutParams(host.dp(22), host.dp(22)));
            LinearLayout text = column();
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
            textParams.setMarginStart(host.dp(11));
            textParams.setMarginEnd(host.dp(8));
            addView(text, textParams);
            label = medium(title, 16, TEXT);
            text.addView(label);
            note = subtitle == null ? null : host.text(subtitle, 12, MUTED);
            if (note != null) {
                LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
                noteParams.topMargin = host.dp(3);
                text.addView(note, noteParams);
            }
            text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            String description = subtitle == null ? title : title + " · " + subtitle;
            if (checked != null) description += " · " + host.getString(checked ? R.string.setting_on : R.string.setting_off);
            setContentDescription(description);

            toggle = checked == null ? null : new Toggle(checked);
            if (toggle != null) addView(toggle, new LinearLayout.LayoutParams(host.dp(37), host.dp(21)));
            ImageView chevron = opensPage ? icon(UiIcon.Glyph.CHEVRON, 16, MUTED) : null;
            trailing = chevron == null ? null : (UiIcon)chevron.getDrawable();
            if (chevron != null) addView(chevron, new LinearLayout.LayoutParams(host.dp(16), host.dp(16)));
        }

        void style(boolean active, boolean focused) {
            int state = focused ? 2 : active ? 1 : 0;
            if (styleState == state) return;
            styleState = state;
            int background = focused ? FOCUS : active ? 0xff284653 : 0xff1d303d;
            int border = focused ? FOCUS : active ? 0xff4a737b : 0;
            setBackground(pill(background, 10, border));
            label.setTextColor(focused ? INK : TEXT);
            if (note != null) note.setTextColor(focused ? 0xff355058 : MUTED);
            leading.setColor(focused ? INK : ACCENT);
            if (trailing != null) trailing.setColor(focused ? INK : MUTED);
            if (toggle != null) toggle.invalidate();
        }
    }

    private final class Toggle extends View {
        private final boolean checked;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Toggle(boolean checked) {
            super(host);
            this.checked = checked;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas c) {
            float radius = getHeight() / 2f;
            boolean focused = ((View)getParent()).isFocused();
            paint.setColor(checked ? 0xff438e80 : focused ? 0xffa8c1c3 : 0xff435966);
            c.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
            boolean atEnd = checked != (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
            paint.setColor(checked ? 0xffe7faf5 : focused ? INK : 0xffc6d5dd);
            c.drawCircle(atEnd ? getWidth() - radius : radius, radius, radius - host.dp(3), paint);
        }
    }
}
