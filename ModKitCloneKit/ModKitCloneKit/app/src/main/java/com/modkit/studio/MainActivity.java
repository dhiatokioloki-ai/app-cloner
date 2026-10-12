package com.modkit.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 13, 21);
    private static final int PANEL = Color.rgb(21, 27, 42);
    private static final int PANEL_2 = Color.rgb(28, 36, 53);
    private static final int TEXT = Color.rgb(240, 244, 250);
    private static final int MUTED = Color.rgb(151, 163, 183);
    private static final int ACCENT = Color.rgb(182, 255, 92);
    private static final int ACCENT_DARK = Color.rgb(37, 57, 27);

    private android.content.SharedPreferences prefs;
    private LinearLayout root;
    private String screen = "home";
    private String pendingPackage = "";
    private String pendingExport = "";
    private ApplicationInfo selectedApp;
    private static final int REQUEST_PICK_APK = 705;
    private static final int REQUEST_SAVE_CLONE = 706;
    private String studioSourcePath = "";
    private String studioSourcePackage = "";
    private String studioSourceLabel = "";
    private int studioSourceSplitCount = 0;
    private EditText studioAppNameField;
    private EditText studioPackageIdField;
    private TextView studioSourceStatus;
    private Spinner studioOrientation;
    private CheckBox studioRemoveInternet;
    private File builtCloneApk;
    private ProgressDialog buildProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("modkit", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        render();
    }

    @Override
    public void onBackPressed() {
        if ("detail".equals(screen)) {
            selectedApp = null;
            screen = "apps";
        } else if (!"home".equals(screen)) {
            screen = "home";
        } else {
            super.onBackPressed();
            return;
        }
        render();
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private GradientDrawable shape(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radius));
        if (strokeWidth > 0) gd.setStroke(dp(strokeWidth), strokeColor);
        return gd;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void pad(View v, int left, int top, int right, int bottom) {
        v.setPadding(dp(left), dp(top), dp(right), dp(bottom));
    }

    private void render() {
        root = column();
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = row();
        pad(top, 20, 13, 20, 11);
        TextView logo = text("M", 23, ACCENT, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(shape(ACCENT_DARK, 13, ACCENT_DARK, 0));
        top.addView(logo, lp(42, 42));
        LinearLayout brand = column();
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0, -2, 1f);
        brandParams.leftMargin = dp(11);
        top.addView(brand, brandParams);
        brand.addView(text("MODKIT", 17, TEXT, true));
        brand.addView(text("ANDROID APP WORKSHOP", 9, MUTED, true));
        TextView status = text("LOCAL", 10, ACCENT, true);
        status.setGravity(Gravity.CENTER);
        pad(status, 12, 7, 12, 7);
        status.setBackground(shape(ACCENT_DARK, 20, ACCENT_DARK, 0));
        top.addView(status);
        root.addView(top, lp(-1, -2));
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(34, 42, 59));
        root.addView(line, lp(-1, 1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = column();
        pad(body, 18, 20, 18, 24);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(scroll, scrollParams);

        switch (screen) {
            case "apps": showApps(body); break;
            case "profiles": showProfiles(body); break;
            case "studio": showStudio(body); break;
            case "tools": showTools(body); break;
            case "detail": showAppDetails(body); break;
            default: showHome(body); break;
        }
        root.addView(bottomNav(), lp(-1, 66));
    }

    private LinearLayout bottomNav() {
        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        pad(nav, 7, 6, 7, 8);
        nav.setBackgroundColor(Color.rgb(13, 17, 27));
        String[][] items = {{"home", "⌂", "Home"}, {"apps", "▦", "Apps"}, {"profiles", "◈", "Profiles"}, {"studio", "✳", "Studio"}, {"tools", "⚙", "Tools"}};
        for (String[] item : items) {
            final String target = item[0];
            LinearLayout cell = column();
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(screen.equals(target) ? shape(ACCENT_DARK, 14, ACCENT_DARK, 0) : shape(Color.TRANSPARENT, 14, Color.TRANSPARENT, 0));
            TextView glyph = text(item[1], 19, screen.equals(target) ? ACCENT : MUTED, true);
            glyph.setGravity(Gravity.CENTER);
            TextView name = text(item[2], 10, screen.equals(target) ? ACCENT : MUTED, screen.equals(target));
            name.setGravity(Gravity.CENTER);
            cell.addView(glyph);
            cell.addView(name);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
            p.setMargins(dp(3), 0, dp(3), 0);
            nav.addView(cell, p);
            cell.setOnClickListener(v -> { screen = target; selectedApp = null; render(); });
        }
        return nav;
    }

    private void heading(LinearLayout into, String kicker, String title, String description) {
        TextView k = text(kicker.toUpperCase(Locale.ROOT), 10, ACCENT, true);
        k.setLetterSpacing(0.12f);
        into.addView(k, lp(-1, -2));
        TextView h = text(title, 28, TEXT, true);
        LinearLayout.LayoutParams hp = lp(-1, -2);
        hp.topMargin = dp(4);
        into.addView(h, hp);
        TextView d = text(description, 13, MUTED, false);
        d.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams dp0 = lp(-1, -2);
        dp0.topMargin = dp(5);
        dp0.bottomMargin = dp(17);
        into.addView(d, dp0);
    }

    private LinearLayout card(LinearLayout into, int marginBottom) {
        LinearLayout c = column();
        pad(c, 15, 15, 15, 15);
        c.setBackground(shape(PANEL, 19, Color.rgb(38, 47, 65), 1));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.bottomMargin = dp(marginBottom);
        into.addView(c, p);
        return c;
    }

    private Button button(String caption, boolean primary, Runnable action) {
        Button b = new Button(this);
        b.setText(caption);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.rgb(15, 23, 11) : TEXT);
        b.setPadding(dp(12), dp(7), dp(12), dp(7));
        b.setMinHeight(dp(44));
        b.setBackground(shape(primary ? ACCENT : PANEL_2, 13, primary ? ACCENT : Color.rgb(47, 58, 78), 1));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void addButton(LinearLayout into, String title, boolean primary, Runnable action) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(8);
        into.addView(button(title, primary, action), p);
    }

    private void addSmallBadge(LinearLayout into, String caption) {
        TextView b = text(caption, 10, ACCENT, true);
        pad(b, 9, 5, 9, 5);
        b.setBackground(shape(ACCENT_DARK, 40, ACCENT_DARK, 0));
        into.addView(b, lp(-2, -2));
    }

    private void showHome(LinearLayout body) {
        LinearLayout hero = column();
        pad(hero, 18, 20, 18, 20);
        hero.setBackground(shape(Color.rgb(25, 37, 31), 22, Color.rgb(59, 82, 51), 1));
        addSmallBadge(hero, "YOUR DEVICE · YOUR PRESETS");
        TextView title = text("Make Android\nwork your way.", 30, TEXT, true);
        title.setLineSpacing(dp(1), 1f);
        LinearLayout.LayoutParams t = lp(-1, -2); t.topMargin = dp(13);
        hero.addView(title, t);
        TextView subtitle = text("Keep app profiles, display presets and build recipes together in one tidy workshop.", 13, Color.rgb(190, 204, 190), false);
        subtitle.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams st = lp(-1, -2); st.topMargin = dp(9);
        hero.addView(subtitle, st);
        addButton(hero, "Open app library  →", true, () -> { screen = "apps"; render(); });
        LinearLayout.LayoutParams h = lp(-1, -2); h.bottomMargin = dp(20);
        body.addView(hero, h);

        TextView statsTitle = text("WORKSPACE", 11, MUTED, true);
        statsTitle.setLetterSpacing(0.11f);
        body.addView(statsTitle, lp(-1, -2));
        LinearLayout stats = row();
        LinearLayout c1 = column(); c1.setBackground(shape(PANEL, 17, Color.rgb(38,47,65), 1)); pad(c1, 14, 14, 12, 14);
        LinearLayout c2 = column(); c2.setBackground(shape(PANEL, 17, Color.rgb(38,47,65), 1)); pad(c2, 14, 14, 12, 14);
        c1.addView(text(String.valueOf(getUserApps().size()), 26, TEXT, true));
        c1.addView(text("USER APPS", 10, MUTED, true));
        c2.addView(text(String.valueOf(loadProfiles().length()), 26, TEXT, true));
        c2.addView(text("SAVED PROFILES", 10, MUTED, true));
        LinearLayout.LayoutParams statP = new LinearLayout.LayoutParams(0, -2, 1f); statP.topMargin = dp(10); statP.rightMargin = dp(5);
        stats.addView(c1, statP);
        LinearLayout.LayoutParams statP2 = new LinearLayout.LayoutParams(0, -2, 1f); statP2.topMargin = dp(10); statP2.leftMargin = dp(5);
        stats.addView(c2, statP2);
        body.addView(stats, lp(-1, -2));

        TextView quick = text("QUICK TOOLS", 11, MUTED, true);
        LinearLayout.LayoutParams q = lp(-1, -2); q.topMargin = dp(22); q.bottomMargin = dp(10);
        body.addView(quick, q);
        quickCard(body, "▦", "App library", "Search installed apps and open Android App Info.", "apps", "Browse apps");
        quickCard(body, "◈", "Profiles", "Save landscape, resolution and offline-ready preferences.", "profiles", "Manage profiles");
        quickCard(body, "✳", "Build Studio", "Prepare and export a package-ID build recipe as JSON.", "studio", "Open studio");
        quickCard(body, "⚙", "Display tools", "Copy display-size and density commands for ADB.", "tools", "View tools");
        TextView note = text("Heads-up: profiles and recipes are saved locally. Android keeps certain controls privileged, so the app shows exactly what can be changed from a normal app.", 11, MUTED, false);
        note.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams np = lp(-1, -2); np.topMargin = dp(5);
        body.addView(note, np);
    }

    private void quickCard(LinearLayout body, String glyph, String title, String desc, String target, String actionLabel) {
        LinearLayout c = card(body, 10);
        LinearLayout r = row();
        TextView icon = text(glyph, 21, ACCENT, true); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(ACCENT_DARK, 12, ACCENT_DARK, 0));
        r.addView(icon, lp(42, 42));
        LinearLayout words = column();
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f); wp.leftMargin = dp(11);
        r.addView(words, wp);
        words.addView(text(title, 15, TEXT, true));
        TextView d = text(desc, 11, MUTED, false); d.setLineSpacing(dp(1), 1f);
        words.addView(d);
        c.addView(r);
        addButton(c, actionLabel + "  →", false, () -> { screen = target; render(); });
    }

    private void showApps(LinearLayout body) {
        heading(body, "APP LIBRARY", "Installed apps", "Pick an app to view its package ID, launch it, or create a profile.");
        EditText search = edit("Search app name or package ID");
        LinearLayout.LayoutParams ep = lp(-1, 49); ep.bottomMargin = dp(13);
        body.addView(search, ep);
        LinearLayout list = column();
        body.addView(list, lp(-1, -2));
        renderAppList(list, "");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderAppList(list, s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(14);
        e.setHint(hint);
        e.setSelectAllOnFocus(false);
        pad(e, 14, 3, 14, 3);
        e.setBackground(shape(PANEL, 13, Color.rgb(48, 58, 76), 1));
        return e;
    }

    private List<ApplicationInfo> getUserApps() {
        List<ApplicationInfo> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();
        try {
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !getPackageName().equals(info.packageName)) apps.add(info);
            }
        } catch (Exception ignored) {}
        Collections.sort(apps, new Comparator<ApplicationInfo>() {
            @Override public int compare(ApplicationInfo a, ApplicationInfo b) {
                return pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString());
            }
        });
        return apps;
    }

    private void renderAppList(LinearLayout list, String query) {
        list.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int hits = 0;
        for (ApplicationInfo app : getUserApps()) {
            String name;
            try { name = getPackageManager().getApplicationLabel(app).toString(); }
            catch (Exception e) { name = app.packageName; }
            final String nameForClone = name;
            if (!name.toLowerCase(Locale.ROOT).contains(q) && !app.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
            hits++;
            LinearLayout c = card(list, 9);
            LinearLayout r = row();
            ImageView icon = new ImageView(this);
            try { icon.setImageDrawable(getPackageManager().getApplicationIcon(app)); }
            catch (Exception e) { icon.setImageResource(android.R.drawable.sym_def_app_icon); }
            r.addView(icon, lp(48, 48));
            LinearLayout info = column();
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1f); ip.leftMargin = dp(12);
            r.addView(info, ip);
            info.addView(text(name, 14, TEXT, true));
            TextView pkg = text(app.packageName, 10, MUTED, false);
            pkg.setMaxLines(2);
            info.addView(pkg);
            TextView arrow = text("›", 29, ACCENT, false); arrow.setGravity(Gravity.CENTER);
            r.addView(arrow, lp(24, 40));
            c.addView(r);
            c.setOnClickListener(v -> { selectedApp = app; screen = "detail"; render(); });
        }
        if (hits == 0) {
            LinearLayout c = card(list, 0);
            c.addView(text("No apps found", 15, TEXT, true));
            c.addView(text("Try another search. Some system apps are hidden from this library.", 12, MUTED, false));
        }
    }

    private void showAppDetails(LinearLayout body) {
        if (selectedApp == null) { screen = "apps"; showApps(body); return; }
        String name;
        Drawable icon;
        try { name = getPackageManager().getApplicationLabel(selectedApp).toString(); } catch (Exception e) { name = selectedApp.packageName; }
        try { icon = getPackageManager().getApplicationIcon(selectedApp); } catch (Exception e) { icon = getDrawable(android.R.drawable.sym_def_app_icon); }
        TextView back = text("‹  BACK TO LIBRARY", 11, ACCENT, true);
        LinearLayout.LayoutParams bp = lp(-1, 35); bp.bottomMargin = dp(10);
        body.addView(back, bp);
        back.setOnClickListener(v -> { screen = "apps"; render(); });
        LinearLayout c = card(body, 13);
        LinearLayout top = row();
        ImageView iv = new ImageView(this); iv.setImageDrawable(icon); top.addView(iv, lp(62, 62));
        LinearLayout detail = column(); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f); p.leftMargin = dp(13); top.addView(detail, p);
        detail.addView(text(name, 20, TEXT, true));
        detail.addView(text("INSTALLED APPLICATION", 9, ACCENT, true));
        c.addView(top);
        TextView packLabel = text("PACKAGE ID", 10, MUTED, true);
        LinearLayout.LayoutParams pl = lp(-1, -2); pl.topMargin = dp(18); pl.bottomMargin = dp(5); c.addView(packLabel, pl);
        TextView packageText = text(selectedApp.packageName, 13, TEXT, false); packageText.setTextIsSelectable(true); c.addView(packageText);
        addButton(c, "Launch app", true, () -> launchPackage(selectedApp.packageName));
        addButton(c, "Open Android App Info", false, () -> openAppSettings(selectedApp.packageName));
        final String packageForProfile = selectedApp.packageName;
        addButton(body, "⧉  Build a separate APK clone", true, () -> openCloneStudio(selectedApp, name));
        addButton(body, "⧉  Build a separate APK clone", true,
        () -> openCloneStudio(selectedApp, nameForClone));
        addButton(body, "＋  Create profile for this app", false, () -> { pendingPackage = packageForProfile; screen = "profiles"; render(); showCreateProfileDialog(packageForProfile); });
        LinearLayout info = card(body, 0);
        info.addView(text("WHAT MODKIT CAN DO", 10, ACCENT, true));
        TextView explain = text("It can identify and launch the installed app, store settings for it, and open Android's own app settings. Changing its package ID requires rebuilding a compatible APK; forcing another app's orientation or making an online-only game offline cannot be done by a normal app alone.", 12, MUTED, false);
        explain.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams ex = lp(-1, -2); ex.topMargin = dp(7); info.addView(explain, ex);
    }

    private void launchPackage(String packageName) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(packageName);
            if (i == null) { toast("No launch screen found for this app."); return; }
            startActivity(i);
            toast("Opened app. Offline access depends on the game itself.");
        } catch (Exception e) { toast("Couldn't launch this app."); }
    }

    private void openAppSettings(String packageName) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
            startActivity(i);
        } catch (Exception e) { toast("Android App Info could not be opened."); }
    }

    private JSONArray loadProfiles() {
        try { return new JSONArray(prefs.getString("profiles", "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private void saveProfiles(JSONArray list) {
        prefs.edit().putString("profiles", list.toString()).apply();
    }

    private void showProfiles(LinearLayout body) {
        heading(body, "PROFILE MANAGER", "Your presets", "Save settings per game. Profiles are stored on this phone.");
        addButton(body, "＋  New profile", true, () -> showCreateProfileDialog(pendingPackage));
        pendingPackage = "";
        JSONArray profiles = loadProfiles();
        LinearLayout.LayoutParams gap = lp(-1, 15); body.addView(new View(this), gap);
        if (profiles.length() == 0) {
            LinearLayout c = card(body, 10);
            TextView empty = text("No profiles yet", 16, TEXT, true);
            c.addView(empty);
            TextView desc = text("Create presets like Landscape · 720p, Retro 4:3, or Offline-ready.", 12, MUTED, false);
            LinearLayout.LayoutParams d = lp(-1, -2); d.topMargin = dp(5); c.addView(desc, d);
        }
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.optJSONObject(i);
            if (profile == null) continue;
            final String id = profile.optString("id");
            final String pkg = profile.optString("package");
            final String name = profile.optString("name", "Untitled profile");
            final String orient = profile.optString("orientation", "Landscape");
            final String resolution = profile.optString("resolution", "1280 × 720");
            final boolean offline = profile.optBoolean("offline", false);
            LinearLayout c = card(body, 10);
            LinearLayout r = row();
            TextView glyph = text("◈", 21, ACCENT, true); glyph.setGravity(Gravity.CENTER); glyph.setBackground(shape(ACCENT_DARK, 13, ACCENT_DARK, 0)); r.addView(glyph, lp(44, 44));
            LinearLayout info = column(); LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1f); ip.leftMargin = dp(11); r.addView(info, ip);
            info.addView(text(name, 15, TEXT, true));
            TextView pp = text(pkg.isEmpty() ? "No target app set" : pkg, 10, MUTED, false); pp.setMaxLines(2); info.addView(pp);
            c.addView(r);
            TextView meta = text(orient + "  ·  " + resolution + (offline ? "  ·  OFFLINE-READY" : ""), 10, ACCENT, true);
            LinearLayout.LayoutParams mp = lp(-1, -2); mp.topMargin = dp(12); c.addView(meta, mp);
            LinearLayout buttons = row(); LinearLayout.LayoutParams btns = lp(-1, -2); btns.topMargin = dp(8); c.addView(buttons, btns);
            Button launch = button("Launch", true, () -> { if (!pkg.isEmpty()) launchPackage(pkg); else toast("Edit this profile and choose an app package ID."); });
            Button more = button("Details", false, () -> showProfileDetails(id));
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1f); half.rightMargin = dp(4); buttons.addView(launch, half);
            LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -2, 1f); half2.leftMargin = dp(4); buttons.addView(more, half2);
        }
        LinearLayout info = card(body, 0);
        info.addView(text("PROFILE BEHAVIOUR", 10, ACCENT, true));
        TextView note = text("A profile records your chosen preferences and can launch its target app. Android does not let an ordinary app apply another app's orientation or resolution directly. Use Tools for the optional ADB display presets.", 12, MUTED, false);
        note.setLineSpacing(dp(3), 1f); LinearLayout.LayoutParams np = lp(-1, -2); np.topMargin = dp(6); info.addView(note, np);
    }

    private void showCreateProfileDialog(String packagePrefill) {
        LinearLayout form = column(); pad(form, 5, 4, 5, 0);
        EditText name = edit("Profile name (e.g. Retro landscape)");
        EditText pkg = edit("Target app package ID (optional)");
        if (packagePrefill != null && !packagePrefill.trim().isEmpty()) pkg.setText(packagePrefill.trim());
        form.addView(name, lp(-1, 48));
        LinearLayout.LayoutParams p = lp(-1, 48); p.topMargin = dp(8); form.addView(pkg, p);
        TextView ol = text("ORIENTATION", 10, MUTED, true); LinearLayout.LayoutParams olp = lp(-1, -2); olp.topMargin = dp(12); form.addView(ol, olp);
        Spinner orientation = spinner(new String[]{"Landscape", "Reverse landscape", "Portrait", "Keep app default"}); form.addView(orientation, lp(-1, 48));
        TextView rl = text("RESOLUTION PRESET", 10, MUTED, true); LinearLayout.LayoutParams rlp = lp(-1, -2); rlp.topMargin = dp(10); form.addView(rl, rlp);
        Spinner resolution = spinner(new String[]{"1280 × 720 (16:9)", "1920 × 1080 (16:9)", "960 × 540 (16:9)", "1024 × 768 (4:3)", "Device default"}); form.addView(resolution, lp(-1, 48));
        CheckBox offline = new CheckBox(this); offline.setText("Mark as offline-ready preference"); offline.setTextColor(TEXT); offline.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams cp = lp(-1, -2); cp.topMargin = dp(8); form.addView(offline, cp);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Create profile").setView(form)
                .setNegativeButton("Cancel", (d, w) -> {}).setPositiveButton("Save profile", (d, w) -> {
                    String profileName = name.getText().toString().trim();
                    String packageName = pkg.getText().toString().trim();
                    if (profileName.isEmpty()) profileName = "My profile";
                    try {
                        JSONArray list = loadProfiles(); JSONObject o = new JSONObject();
                        o.put("id", String.valueOf(System.currentTimeMillis())); o.put("name", profileName); o.put("package", packageName);
                        o.put("orientation", orientation.getSelectedItem().toString()); o.put("resolution", resolution.getSelectedItem().toString());
                        o.put("offline", offline.isChecked()); o.put("created", System.currentTimeMillis()); list.put(o); saveProfiles(list);
                        toast("Profile saved on this phone.");
                        screen = "profiles"; render();
                    } catch (JSONException e) { toast("Could not save this profile."); }
                }).create();
        dialog.show();
    }

    private void showProfileDetails(String id) {
        JSONArray list = loadProfiles();
        for (int i = 0; i < list.length(); i++) {
            JSONObject p = list.optJSONObject(i);
            if (p == null || !id.equals(p.optString("id"))) continue;
            String msg = "Target: " + (p.optString("package").isEmpty() ? "Not set" : p.optString("package")) +
                    "\nOrientation: " + p.optString("orientation") + "\nResolution: " + p.optString("resolution") +
                    "\nOffline-ready note: " + (p.optBoolean("offline") ? "On" : "Off") +
                    "\n\nThese are saved preferences. A normal Android app cannot enforce another app's orientation or make a server-based game work offline.";
            final JSONObject item = p;
            new AlertDialog.Builder(this).setTitle(p.optString("name")).setMessage(msg)
                    .setNeutralButton("Delete", (d, w) -> deleteProfile(item.optString("id")))
                    .setNegativeButton("Close", null)
                    .setPositiveButton("Launch", (d, w) -> { String target = item.optString("package"); if (!target.isEmpty()) launchPackage(target); else toast("No target app set."); }).show();
            return;
        }
    }

    private void deleteProfile(String id) {
        JSONArray old = loadProfiles(); JSONArray replacement = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject p = old.optJSONObject(i);
            if (p != null && !id.equals(p.optString("id"))) replacement.put(p);
        }
        saveProfiles(replacement); toast("Profile deleted."); render();
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent); v.setTextColor(TEXT); v.setTextSize(13); return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent); v.setTextColor(TEXT); v.setBackgroundColor(PANEL); v.setTextSize(13); v.setPadding(dp(12), dp(12), dp(12), dp(12)); return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setBackground(shape(PANEL, 12, Color.rgb(48, 58, 76), 1));
        return s;
    }

    private void openCloneStudio(ApplicationInfo app, String label) {
        if (app == null) return;
        studioSourcePath = app.sourceDir == null ? "" : app.sourceDir;
        studioSourcePackage = app.packageName == null ? "" : app.packageName;
        studioSourceLabel = label == null || label.trim().isEmpty() ? studioSourcePackage : label;
        studioSourceSplitCount = app.splitSourceDirs == null ? 0 : app.splitSourceDirs.length;
        screen = "studio";
        selectedApp = null;
        render();
    }

    private void showStudio(LinearLayout body) {
        heading(body, "APK CLONE STUDIO", "Build a separate copy", "Choose an installed app or APK file, give the clone a different package ID, and build a signed APK.");

        LinearLayout source = card(body, 12);
        source.addView(text("SOURCE APK", 10, ACCENT, true));
        studioSourceStatus = text(sourceSummary(), 12, TEXT, false);
        studioSourceStatus.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams sourceStatusParams = lp(-1, -2);
        sourceStatusParams.topMargin = dp(8);
        source.addView(studioSourceStatus, sourceStatusParams);
        addButton(source, "Choose an APK file…", false, this::pickSourceApk);

        LinearLayout c = card(body, 12);
        c.addView(text("CLONE IDENTITY", 10, ACCENT, true));
        studioAppNameField = edit("New app display name");
        String suggestedLabel = studioSourceLabel.isEmpty() ? "My App Clone" : studioSourceLabel + " Clone";
        studioAppNameField.setText(suggestedLabel);
        LinearLayout.LayoutParams p1 = lp(-1, 49); p1.topMargin = dp(10); c.addView(studioAppNameField, p1);

        studioPackageIdField = edit("New package ID, e.g. com.example.game.clone");
        String suggestedId = studioSourcePackage.isEmpty() ? "com.example.app.clone" : studioSourcePackage + ".clone";
        studioPackageIdField.setText(suggestedId);
        LinearLayout.LayoutParams p2 = lp(-1, 49); p2.topMargin = dp(8); c.addView(studioPackageIdField, p2);

        TextView ol = text("LAUNCH ORIENTATION", 10, MUTED, true);
        LinearLayout.LayoutParams op = lp(-1, -2); op.topMargin = dp(13); c.addView(ol, op);
        studioOrientation = spinner(new String[]{"Landscape", "Reverse landscape", "Portrait", "Keep app default"});
        c.addView(studioOrientation, lp(-1, 48));

        studioRemoveInternet = new CheckBox(this);
        studioRemoveInternet.setText("Remove INTERNET permission (can break online features)");
        studioRemoveInternet.setTextColor(TEXT);
        studioRemoveInternet.setTextSize(12);
        studioRemoveInternet.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams cb = lp(-1, -2); cb.topMargin = dp(8); c.addView(studioRemoveInternet, cb);

        addButton(c, "Validate package ID", false, () -> {
            String id = studioPackageIdField.getText().toString().trim();
            if (!ApkCloneEngine.isValidPackageId(id)) {
                toast("Use a valid ID such as com.example.game.clone");
            } else if (!studioSourcePackage.isEmpty() && id.equals(studioSourcePackage)) {
                toast("The clone must use a different package ID.");
            } else {
                toast("Package ID looks valid.");
            }
        });
        addButton(c, "Build and sign clone APK", true, this::confirmCloneBuild);
        addButton(c, "Export settings recipe (.json)", false, this::exportCloneRecipe);

        LinearLayout info = card(body, 12);
        info.addView(text("IMPORTANT LIMITS", 10, ACCENT, true));
        TextView explain = text(
                "• The clone is rebuilt and signed with a local key. It installs as a separate app because its package ID is different.\n" +
                "• This works best with single-file APKs. Some store-installed apps are split into several APKs; cloning only the base file may fail or miss assets.\n" +
                "• Some games verify their signature, depend on online servers, or protect their code. Those may not work after rebuilding.\n" +
                "• Removing INTERNET blocks normal network access, but it cannot add offline game data or make a server-based game playable offline.\n" +
                "• The display-resolution tool changes the phone display globally through ADB; an ordinary app cannot force a per-game pixel resolution by itself.",
                11, MUTED, false);
        explain.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams e = lp(-1, -2); e.topMargin = dp(8); info.addView(explain, e);

        LinearLayout files = card(body, 0);
        files.addView(text("SOURCE / OUTPUT", 10, ACCENT, true));
        addButton(files, "Pick a different source APK", false, this::pickSourceApk);
        addButton(files, "Open Android install-permission settings", false, () -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e1) { toast("That setting is not available on this Android version."); }
        });
    }

    private String sourceSummary() {
        if (studioSourcePath == null || studioSourcePath.trim().isEmpty()) {
            return "No source selected yet. Open Apps, pick an installed app, then tap Build a separate APK clone, or choose an APK file here.";
        }
        String summary = (studioSourceLabel.isEmpty() ? "Selected APK" : studioSourceLabel)
                + (studioSourcePackage.isEmpty() ? "" : "\nPackage: " + studioSourcePackage);
        if (studioSourceSplitCount > 0) {
            summary += "\n⚠ Android reports " + studioSourceSplitCount + " split APK part(s). The current build uses the base APK only, so this clone may be incomplete.";
        } else {
            summary += "\nStandalone APK source";
        }
        return summary;
    }

    private void pickSourceApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_PICK_APK); }
        catch (Exception e) { toast("The Android file picker could not be opened."); }
    }

    private void handlePickedApk(Uri uri) {
        toast("Importing APK…");
        new Thread(() -> {
            File destination = new File(getFilesDir(), "modkit-imported-source.apk");
            try (InputStream input = getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(destination, false)) {
                if (input == null) throw new IOException("The selected file could not be opened.");
                copyStream(input, output);
                android.content.pm.PackageInfo info = getPackageManager().getPackageArchiveInfo(destination.getAbsolutePath(), 0);
                if (info == null || info.applicationInfo == null || info.packageName == null) {
                    throw new IOException("That file does not look like a readable APK.");
                }
                info.applicationInfo.sourceDir = destination.getAbsolutePath();
                info.applicationInfo.publicSourceDir = destination.getAbsolutePath();
                String label = info.applicationInfo.loadLabel(getPackageManager()).toString();
                runOnUiThread(() -> {
                    studioSourcePath = destination.getAbsolutePath();
                    studioSourcePackage = info.packageName;
                    studioSourceLabel = label == null || label.trim().isEmpty() ? info.packageName : label;
                    studioSourceSplitCount = 0;
                    if (studioSourceStatus != null) studioSourceStatus.setText(sourceSummary());
                    if (studioAppNameField != null) studioAppNameField.setText(studioSourceLabel + " Clone");
                    if (studioPackageIdField != null) studioPackageIdField.setText(studioSourcePackage + ".clone");
                    toast("Source APK selected.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Could not read APK")
                        .setMessage(e.getMessage() == null ? "Choose a different APK file." : e.getMessage())
                        .setPositiveButton("OK", null).show());
            }
        }, "modkit-import-apk").start();
    }

    private void confirmCloneBuild() {
        String id = studioPackageIdField == null ? "" : studioPackageIdField.getText().toString().trim();
        String name = studioAppNameField == null ? "" : studioAppNameField.getText().toString().trim();
        if (studioSourcePath == null || studioSourcePath.isEmpty() || !new File(studioSourcePath).isFile()) {
            toast("Choose an installed app or source APK first.");
            pickSourceApk();
            return;
        }
        if (!ApkCloneEngine.isValidPackageId(id)) { toast("Fix the package ID first."); return; }
        if (!studioSourcePackage.isEmpty() && id.equals(studioSourcePackage)) {
            toast("Use a different package ID so the clone can be installed separately."); return;
        }
        if (name.trim().isEmpty()) name = "My App Clone";
        final String cloneName = name;
        final String cloneId = id;
        final String orientation;
        String selectedOrientation = studioOrientation == null ? "Keep app default" : studioOrientation.getSelectedItem().toString();
        if ("Landscape".equals(selectedOrientation)) orientation = "landscape";
        else if ("Reverse landscape".equals(selectedOrientation)) orientation = "reverseLandscape";
        else if ("Portrait".equals(selectedOrientation)) orientation = "portrait";
        else orientation = "keep";
        final boolean removeInternet = studioRemoveInternet != null && studioRemoveInternet.isChecked();
        String confirm = "Source: " + studioSourceLabel + "\nNew name: " + cloneName + "\nNew package ID: " + cloneId
                + "\nOrientation: " + selectedOrientation
                + "\nRemove Internet permission: " + (removeInternet ? "Yes" : "No")
                + "\n\nThe original installed app will not be replaced. The rebuild may fail for protected or split APKs.";
        new AlertDialog.Builder(this).setTitle("Build APK clone?").setMessage(confirm)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Build", (dialog, which) -> runCloneBuild(cloneName, cloneId, orientation, removeInternet))
                .show();
    }

    private void runCloneBuild(String cloneName, String cloneId, String orientation, boolean removeInternet) {
        final File input = new File(studioSourcePath);
        ApkCloneEngine.Options options = new ApkCloneEngine.Options();
        options.displayName = cloneName;
        options.packageId = cloneId;
        options.orientation = orientation;
        options.removeInternetPermission = removeInternet;
        final File outputDir = new File(getFilesDir(), "modkit-output");
        buildProgressDialog = new ProgressDialog(this);
        buildProgressDialog.setTitle("Building clone APK");
        buildProgressDialog.setMessage("Preparing tools…");
        buildProgressDialog.setIndeterminate(true);
        buildProgressDialog.setCancelable(false);
        buildProgressDialog.show();

        new Thread(() -> {
            try {
                File result = ApkCloneEngine.build(getApplicationContext(), input, outputDir, options,
                        message -> runOnUiThread(() -> {
                            if (buildProgressDialog != null && buildProgressDialog.isShowing()) buildProgressDialog.setMessage(message);
                        }));
                runOnUiThread(() -> {
                    builtCloneApk = result;
                    if (buildProgressDialog != null) buildProgressDialog.dismiss();
                    buildProgressDialog = null;
                    new AlertDialog.Builder(this).setTitle("Clone APK is ready")
                            .setMessage("Created and signed:\n" + result.getName() + "\n\nNext, save it to Downloads or another folder. Then install the saved APK if Android allows it.")
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Save APK…", (d, w) -> saveBuiltClone())
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (buildProgressDialog != null) buildProgressDialog.dismiss();
                    buildProgressDialog = null;
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    new AlertDialog.Builder(this).setTitle("Clone build failed")
                            .setMessage(message + "\n\nSome apps cannot be rebuilt because they use split packages, unusual resources, signature checks, or anti-tamper protection.")
                            .setPositiveButton("OK", null).show();
                });
            }
        }, "modkit-clone-build").start();
    }

    private void saveBuiltClone() {
        if (builtCloneApk == null || !builtCloneApk.isFile()) { toast("No finished APK is available."); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        intent.putExtra(Intent.EXTRA_TITLE, builtCloneApk.getName());
        try { startActivityForResult(intent, REQUEST_SAVE_CLONE); }
        catch (Exception e) { toast("The Android save picker could not be opened."); }
    }

    private void exportCloneRecipe() {
        String id = studioPackageIdField == null ? "" : studioPackageIdField.getText().toString().trim();
        if (!ApkCloneEngine.isValidPackageId(id)) { toast("Fix the package ID first."); return; }
        try {
            JSONObject recipe = new JSONObject();
            recipe.put("format", "modkit-clone-recipe-v2");
            recipe.put("sourcePackageId", studioSourcePackage);
            recipe.put("sourceLabel", studioSourceLabel);
            recipe.put("newPackageId", id);
            recipe.put("appName", studioAppNameField == null ? "My App Clone" : studioAppNameField.getText().toString().trim());
            recipe.put("orientation", studioOrientation == null ? "Keep app default" : studioOrientation.getSelectedItem().toString());
            recipe.put("removeInternetPermission", studioRemoveInternet != null && studioRemoveInternet.isChecked());
            recipe.put("note", "Recipe only. To create an APK, select a source and use Build and sign clone APK.");
            pendingExport = recipe.toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "modkit-clone-recipe.json");
            startActivityForResult(intent, 704);
        } catch (Exception e) { toast("Could not prepare the recipe file."); }
    }

    private void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        output.flush();
    }

    private boolean isValidPackageId(String id) {
        return ApkCloneEngine.isValidPackageId(id);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_PICK_APK) {
            handlePickedApk(uri);
            return;
        }
        if (requestCode == REQUEST_SAVE_CLONE) {
            if (builtCloneApk == null || !builtCloneApk.isFile()) { toast("No APK was ready to save."); return; }
            new Thread(() -> {
                try (InputStream input = new FileInputStream(builtCloneApk);
                     OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    if (output == null) throw new IOException("The selected destination could not be opened.");
                    copyStream(input, output);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "APK saved. Opening Android installer…", Toast.LENGTH_LONG).show();
                        launchInstaller(uri);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Could not save APK")
                            .setMessage(e.getMessage() == null ? "Choose a different destination." : e.getMessage())
                            .setPositiveButton("OK", null).show());
                }
            }, "modkit-save-apk").start();
            return;
        }
        if (requestCode == 704) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("No output stream");
                out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                out.flush();
                toast("Recipe exported.");
            } catch (Exception e) { toast("Could not write the selected file."); }
        }
    }

    private void launchInstaller(Uri apkUri) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this).setTitle("Allow APK installation")
                    .setMessage("Android blocked the installer because ModKit is not allowed to install apps yet. Enable Allow from this source, return to ModKit, and open the saved APK from Downloads.")
                    .setPositiveButton("Open setting", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) {}
                    }).setNegativeButton("OK", null).show();
            return;
        }
        try {
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("APK saved")
                    .setMessage("The APK is saved. Open it from the folder you selected to install it.")
                    .setPositiveButton("OK", null).show();
        }
    }

    private void showTools(LinearLayout body) {
        heading(body, "DEVICE TOOLS", "Display presets", "See the current screen and copy optional ADB commands for global display overrides.");
        LinearLayout device = card(body, 12);
        device.addView(text("THIS DEVICE", 10, ACCENT, true));
        DisplayMetrics metrics = new DisplayMetrics();
        try { ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics); }
        catch (Exception e) { metrics = getResources().getDisplayMetrics(); }
        addInfoRow(device, "Model", Build.MANUFACTURER + " " + Build.MODEL);
        addInfoRow(device, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        addInfoRow(device, "Physical display", metrics.widthPixels + " × " + metrics.heightPixels);
        addInfoRow(device, "Density", Math.round(metrics.densityDpi) + " dpi");

        LinearLayout warning = card(body, 12);
        warning.addView(text("ADB DISPLAY OVERRIDES", 10, ACCENT, true));
        TextView desc = text("These commands change the device display globally, not just one game. Copy a command and run it from a trusted computer with USB debugging enabled. Restore defaults afterwards.", 12, MUTED, false);
        desc.setLineSpacing(dp(3), 1f); LinearLayout.LayoutParams d = lp(-1, -2); d.topMargin = dp(7); warning.addView(desc, d);
        addButton(warning, "Copy 1280 × 720 command", false, () -> copy("adb shell wm size 1280x720\nadb shell wm density 240", "720p display commands copied"));
        addButton(warning, "Copy 960 × 540 command", false, () -> copy("adb shell wm size 960x540\nadb shell wm density 160", "540p display commands copied"));
        addButton(warning, "Copy 1024 × 768 command", false, () -> copy("adb shell wm size 1024x768\nadb shell wm density 160", "4:3 display commands copied"));
        addButton(warning, "Copy restore-defaults commands", true, () -> copy("adb shell wm size reset\nadb shell wm density reset", "Restore commands copied"));

        LinearLayout system = card(body, 0);
        system.addView(text("ANDROID SETTINGS SHORTCUTS", 10, ACCENT, true));
        addButton(system, "Open Display settings", false, () -> openSettings(Settings.ACTION_DISPLAY_SETTINGS));
        addButton(system, "Open App settings", false, () -> openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        addButton(system, "Open network settings", false, () -> openSettings(Settings.ACTION_WIRELESS_SETTINGS));
        TextView note = text("ModKit does not run ADB commands itself and does not root the phone. This keeps global display changes visible and reversible.", 11, MUTED, false);
        note.setLineSpacing(dp(2), 1f); LinearLayout.LayoutParams np = lp(-1, -2); np.topMargin = dp(9); system.addView(note, np);
    }

    private void addInfoRow(LinearLayout into, String name, String value) {
        LinearLayout r = row(); LinearLayout.LayoutParams rp = lp(-1, -2); rp.topMargin = dp(11); into.addView(r, rp);
        TextView l = text(name, 12, MUTED, false); r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = text(value, 12, TEXT, true); v.setGravity(Gravity.RIGHT); v.setMaxWidth(dp(205)); r.addView(v);
    }

    private void copy(String value, String message) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("ModKit commands", value));
            toast(message);
        } catch (Exception e) { toast("Couldn't copy to clipboard."); }
    }

    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (Exception e) { toast("That settings page isn't available on this device."); }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
