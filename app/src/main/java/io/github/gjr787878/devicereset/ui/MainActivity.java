package io.github.gjr787878.devicereset.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.github.gjr787878.devicereset.Config;
import io.github.gjr787878.devicereset.GlassButtonDrawable;
import io.github.gjr787878.devicereset.xposed.Identity;
import io.github.gjr787878.devicereset.xposed.IdentityGenerator;
import io.github.gjr787878.devicereset.xposed.SentinelDetector;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_LANG = "app_language";
    private static final String LANG_ZH = "zh";
    private static final String LANG_EN = "en";
    private static final String LANG_RU = "ru";
    private static final String UPDATE_REPO = "GJR787878/DeviceResetSpooferX";
    private static final String REPO_HOME_URL = "https://github.com/GJR787878/DeviceResetSpooferX";

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BLUE = 0xFF0A84FF;
    private static final int COLOR_GRAY = 0xFFCCCCCC;
    private static final int COLOR_DARK_BG = 0xFF000000;
    private static final int COLOR_DIALOG_BG = 0xFF1C1C1E;

    private String currentLang;
    private FrameLayout contentFrame;
    private Button navApps, navSettings;
    private View appsView, settingsView;
    private LinearLayout appListContainer;
    private TextView appCountText;
    private GlassButtonDrawable[] hookGlass = new GlassButtonDrawable[7];
    private TextView[] hookTv = new TextView[7];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("devicereset_ui", MODE_PRIVATE);
        currentLang = prefs.getString(PREFS_LANG, LANG_EN);
        buildRootUI();
        checkUpdate(false);
    }

    private void buildRootUI() {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_DARK_BG);

        contentFrame = new FrameLayout(this);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentLp.bottomMargin = Math.round(80 * d);
        root.addView(contentFrame, contentLp);

        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        GradientDrawable navBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF06A6A72, 0x882C2C2E});
        navBg.setCornerRadius(28 * d);
        navBg.setStroke(Math.round(1 * d), 0x55FFFFFF);
        navBar.setBackground(navBg);
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(64 * d));
        navLp.gravity = Gravity.BOTTOM;
        navLp.leftMargin = Math.round(16 * d);
        navLp.rightMargin = Math.round(16 * d);
        navLp.bottomMargin = Math.round(16 * d);
        root.addView(navBar, navLp);

        navApps = makeNavButton("Apps");
        navSettings = makeNavButton("Settings");
        LinearLayout.LayoutParams navBtnLp = new LinearLayout.LayoutParams(
                0, Math.round(48 * d), 1f);
        navBtnLp.leftMargin = Math.round(4 * d);
        navBtnLp.rightMargin = Math.round(4 * d);
        navBar.addView(navApps, navBtnLp);
        navBar.addView(navSettings, navBtnLp);

        setContentView(root);
        appsView = buildAppsPage();
        settingsView = buildSettingsPage();
        contentFrame.addView(appsView);
        switchTab(0);
    }

    private Button makeNavButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private void switchTab(int idx) {
        boolean isApps = idx == 0;
        contentFrame.removeAllViews();
        contentFrame.addView(isApps ? appsView : settingsView);
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable selBg = new GradientDrawable();
        selBg.setColor(0x330A84FF);
        selBg.setCornerRadius(20 * d);
        navApps.setTextColor(isApps ? COLOR_BLUE : COLOR_WHITE);
        navSettings.setTextColor(isApps ? COLOR_WHITE : COLOR_BLUE);
        navApps.setBackground(isApps ? selBg : null);
        navSettings.setBackground(isApps ? null : selBg);
        navApps.setOnClickListener(v -> switchTab(0));
        navSettings.setOnClickListener(v -> switchTab(1));
        if (isApps) refreshAppList();
    }

    // ===== 应用页 =====
    private View buildAppsPage() {
        float d = getResources().getDisplayMetrics().density;
        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(Math.round(24 * d), Math.round(48 * d), Math.round(24 * d), Math.round(32 * d));

        TextView title = new TextView(this);
        title.setText("Select App");
        title.setTextSize(28);
        title.setTextColor(COLOR_WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        ll.addView(title);

        appCountText = new TextView(this);
        appCountText.setText("");
        appCountText.setTextSize(13);
        appCountText.setTextColor(COLOR_GRAY);
        appCountText.setGravity(Gravity.END);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.topMargin = Math.round(8 * d);
        ll.addView(appCountText, countLp);

        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = Math.round(16 * d);
        ll.addView(appListContainer, listLp);

        sv.addView(ll);
        return sv;
    }

    private void refreshAppList() {
        appListContainer.removeAllViews();
        appCountText.setText("Scanning...");
        new Thread(() -> {
            final java.util.List<String> pkgs = scanPackagesWithIdentity();
            runOnUiThread(() -> {
                appCountText.setText(pkgs.size() + " app" + (pkgs.size() == 1 ? "" : "s")
                        + " with spoofed identity");
                if (pkgs.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("No apps with spoofed identity found.\nEnsure target apps are checked in LSPosed scope and launched at least once.");
                    empty.setTextSize(14);
                    empty.setTextColor(COLOR_GRAY);
                    empty.setPadding(0, 40, 0, 0);
                    appListContainer.addView(empty);
                    return;
                }
                PackageManager pm = getPackageManager();
                float d = getResources().getDisplayMetrics().density;
                for (String pkg : pkgs) {
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                        String name = pm.getApplicationLabel(ai).toString();
                        appListContainer.addView(makeAppRow(ai, name, pkg, d));
                    } catch (Throwable e) {
                        appListContainer.addView(makeAppRow(null, pkg, pkg, d));
                    }
                }
            });
        }).start();
    }

    private View makeAppRow(ApplicationInfo ai, String name, String pkg, float d) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(16 * d), Math.round(14 * d), Math.round(16 * d), Math.round(14 * d));
        row.setBackground(new GlassButtonDrawable(24 * d, 1 * d, false));

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(Math.round(48 * d), Math.round(48 * d)));
        try { if (ai != null) icon.setImageDrawable(getPackageManager().getApplicationIcon(ai)); } catch (Throwable ignored) {}
        row.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = Math.round(14 * d);
        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(16);
        tvName.setTextColor(COLOR_WHITE);
        TextView tvPkg = new TextView(this);
        tvPkg.setText(pkg);
        tvPkg.setTextSize(12);
        tvPkg.setTextColor(COLOR_GRAY);
        textCol.addView(tvName);
        textCol.addView(tvPkg);
        row.addView(textCol, textLp);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setColor(COLOR_BLUE);
        dotBg.setShape(GradientDrawable.OVAL);
        dot.setBackground(dotBg);
        row.addView(dot, new LinearLayout.LayoutParams(Math.round(12 * d), Math.round(12 * d)));

        row.setOnClickListener(v -> showAppDetailDialog(pkg, name));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = Math.round(10 * d);
        row.setLayoutParams(rowLp);
        return row;
    }

    // ===== 应用详情弹窗 =====
    private void showAppDetailDialog(String pkg, String appName) {
        String json = readIdentityFile(pkg);
        if (json == null) { Toast.makeText(this, "Failed to read identity", Toast.LENGTH_SHORT).show(); return; }
        Identity id = Identity.fromJson(json);
        if (id == null) { Toast.makeText(this, "Failed to parse identity", Toast.LENGTH_SHORT).show(); return; }

        float d = getResources().getDisplayMetrics().density;
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Math.round(20 * d), Math.round(20 * d), Math.round(20 * d), Math.round(16 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_DIALOG_BG);
        bg.setCornerRadius(24 * d);
        root.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("App: " + appName + "\n" + pkg);
        title.setTextSize(16);
        title.setTextColor(COLOR_WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svLp.topMargin = Math.round(12 * d);
        svLp.bottomMargin = Math.round(12 * d);

        TextView content = new TextView(this);
        content.setTextSize(13);
        content.setTextColor(COLOR_GRAY);
        StringBuilder sb = new StringBuilder();
        if (id.androidId != null) sb.append("Android ID: ").append(id.androidId).append("\n");
        if (id.advertisingId != null) sb.append("Ad ID: ").append(id.advertisingId).append("\n");
        if (id.appSetId != null) sb.append("AppSet ID: ").append(id.appSetId).append("\n");
        if (id.imei != null) sb.append("IMEI: ").append(id.imei).append("\n");
        if (id.meid != null) sb.append("MEID: ").append(id.meid).append("\n");
        if (id.serial != null) sb.append("Serial: ").append(id.serial).append("\n");
        if (id.macAddress != null) sb.append("MAC: ").append(id.macAddress).append("\n");
        if (id.gsfId != null) sb.append("GSF ID: ").append(id.gsfId).append("\n");
        sb.append("\n");
        if (id.brand != null) sb.append("Brand: ").append(id.brand).append("\n");
        if (id.model != null) sb.append("Model: ").append(id.model).append("\n");
        if (id.manufacturer != null) sb.append("Manufacturer: ").append(id.manufacturer).append("\n");
        if (id.fingerprint != null) sb.append("Fingerprint: ").append(id.fingerprint).append("\n");
        if (id.buildId != null) sb.append("Build ID: ").append(id.buildId).append("\n");
        sb.append("\n");
        if (id.networkOperatorName != null) sb.append("Carrier: ").append(id.networkOperatorName).append("\n");
        if (id.networkOperator != null) sb.append("Carrier Code: ").append(id.networkOperator).append("\n");
        if (id.imsi != null) sb.append("IMSI: ").append(id.imsi).append("\n");
        if (id.iccid != null) sb.append("ICCID: ").append(id.iccid).append("\n");
        content.setText(sb.toString());
        sv.addView(content);
        root.addView(sv, svLp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnCustom = makeGlassBtn("Customize", 13);
        Button btnRandom = makeGlassBtn("Random", 13);
        Button btnBack = makeGlassBtn("Back", 13);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnLp.leftMargin = Math.round(3 * d);
        btnLp.rightMargin = Math.round(3 * d);
        btnRow.addView(btnCustom, btnLp);
        btnRow.addView(btnRandom, btnLp);
        btnRow.addView(btnBack, btnLp);
        root.addView(btnRow);

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(Math.round(340 * d), Math.round(520 * d));
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.6f);
        }
        dialog.show();

        btnBack.setOnClickListener(v -> dialog.dismiss());
        btnRandom.setOnClickListener(v -> {
            Identity newId = IdentityGenerator.generateRandom();
            writeIdentityFile(pkg, newId.toJson());
            Toast.makeText(this, "Random identity generated", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshAppList();
        });
        btnCustom.setOnClickListener(v -> {
            dialog.dismiss();
            showCustomizeDialog(pkg, appName, id);
        });
    }

    private Button makeGlassBtn(String text, float size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(size);
        b.setTextColor(COLOR_WHITE);
        b.setAllCaps(false);
        float d = getResources().getDisplayMetrics().density;
        b.setBackground(new GlassButtonDrawable(24 * d, 1 * d, false));
        return b;
    }

    // ===== 自定义界面 =====
    interface FieldSetter { void set(String val); }

    private void showCustomizeDialog(String pkg, String appName, Identity currentId) {
        float d = getResources().getDisplayMetrics().density;
        final Identity[] workingId = {currentId != null ? currentId : IdentityGenerator.generateRandom()};

        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);
        root.setPadding(Math.round(24 * d), Math.round(40 * d), Math.round(24 * d), Math.round(24 * d));

        TextView title = new TextView(this);
        title.setText("Customize Identity");
        title.setTextSize(22);
        title.setTextColor(COLOR_WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText(appName + " (" + pkg + ")");
        sub.setTextSize(13);
        sub.setTextColor(COLOR_GRAY);
        root.addView(sub);

        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svLp.topMargin = Math.round(16 * d);
        svLp.bottomMargin = Math.round(12 * d);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        addSectionLabel(form, "Device Profile", d);
        Button deviceBtn = makeGlassBtn("Device: " + (workingId[0].model != null ? workingId[0].model : "Tap to select"), 14);
        form.addView(deviceBtn, makeFormLp(d));
        deviceBtn.setOnClickListener(v -> showDevicePicker(workingId[0], deviceBtn));

        addSectionLabel(form, "Carrier", d);
        Button carrierBtn = makeGlassBtn("Carrier: " + (workingId[0].networkOperatorName != null ? workingId[0].networkOperatorName : "Tap to select"), 14);
        form.addView(carrierBtn, makeFormLp(d));
        carrierBtn.setOnClickListener(v -> showCarrierPicker(workingId[0], carrierBtn));

        addSectionLabel(form, "Identifiers", d);
        addFieldRow(form, "Android ID", workingId[0].androidId, d, val -> workingId[0].androidId = val, true);
        addFieldRow(form, "Ad ID (AAID)", workingId[0].advertisingId, d, val -> workingId[0].advertisingId = val, true);
        addFieldRow(form, "AppSet ID", workingId[0].appSetId, d, val -> workingId[0].appSetId = val, true);
        addFieldRow(form, "GSF ID", workingId[0].gsfId, d, val -> workingId[0].gsfId = val, true);
        addFieldRow(form, "IMEI", workingId[0].imei, d, val -> workingId[0].imei = val, true);
        addFieldRow(form, "MEID", workingId[0].meid, d, val -> workingId[0].meid = val, true);
        addFieldRow(form, "Serial", workingId[0].serial, d, val -> workingId[0].serial = val, true);
        addFieldRow(form, "MAC Address", workingId[0].macAddress, d, val -> workingId[0].macAddress = val, true);

        addSectionLabel(form, "Build Info", d);
        addFieldRow(form, "Build ID", workingId[0].buildId, d, val -> workingId[0].buildId = val, false);
        addFieldRow(form, "Bootloader", workingId[0].bootloader, d, val -> workingId[0].bootloader = val, false);
        addFieldRow(form, "Radio Version", workingId[0].radioVersion, d, val -> workingId[0].radioVersion = val, false);

        addSectionLabel(form, "SIM Info", d);
        addFieldRow(form, "IMSI", workingId[0].imsi, d, val -> workingId[0].imsi = val, false);
        addFieldRow(form, "ICCID", workingId[0].iccid, d, val -> workingId[0].iccid = val, false);

        sv.addView(form);
        root.addView(sv, svLp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnSave = makeGlassBtn("Save", 15);
        Button btnCancel = makeGlassBtn("Cancel", 15);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.leftMargin = Math.round(4 * d);
        blp.rightMargin = Math.round(4 * d);
        btnRow.addView(btnCancel, blp);
        btnRow.addView(btnSave, blp);
        root.addView(btnRow);

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setDimAmount(0f);
        }
        dialog.show();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            // 保存前确保所有 EditText 的值已提交
            writeIdentityFile(pkg, workingId[0].toJson());
            Toast.makeText(this, "Identity saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshAppList();
        });
    }

    private void addFieldRow(LinearLayout form, String label, String currentVal, float d, FieldSetter setter, boolean canRandom) {
        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextSize(12);
        lbl.setTextColor(COLOR_GRAY);
        form.addView(lbl);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        EditText et = new EditText(this);
        et.setText(currentVal != null ? currentVal : "");
        et.setTextSize(13);
        et.setTextColor(COLOR_WHITE);
        et.setHintTextColor(0x66FFFFFF);
        et.setBackgroundColor(0x22FFFFFF);
        et.setPadding(Math.round(12 * d), Math.round(10 * d), Math.round(12 * d), Math.round(10 * d));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(et, etLp);

        if (canRandom) {
            Button rnd = makeGlassBtn("Rnd", 11);
            LinearLayout.LayoutParams rndLp = new LinearLayout.LayoutParams(Math.round(60 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
            rndLp.leftMargin = Math.round(8 * d);
            row.addView(rnd, rndLp);
            rnd.setOnClickListener(v -> {
                Identity tmp = IdentityGenerator.generateRandom();
                String newVal = "?";
                if ("Android ID".equals(label)) newVal = tmp.androidId;
                else if ("Ad ID (AAID)".equals(label)) newVal = tmp.advertisingId;
                else if ("AppSet ID".equals(label)) newVal = tmp.appSetId;
                else if ("GSF ID".equals(label)) newVal = tmp.gsfId;
                else if ("IMEI".equals(label)) newVal = tmp.imei;
                else if ("MEID".equals(label)) newVal = tmp.meid;
                else if ("Serial".equals(label)) newVal = tmp.serial;
                else if ("MAC Address".equals(label)) newVal = tmp.macAddress;
                et.setText(newVal);
            });
        }
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) setter.set(et.getText().toString().trim());
        });
        form.addView(row, makeFormLp(d));
    }

    private LinearLayout.LayoutParams makeFormLp(float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(10 * d);
        return lp;
    }

    private void addSectionLabel(LinearLayout form, String text, float d) {
        TextView lbl = new TextView(this);
        lbl.setText(text);
        lbl.setTextSize(15);
        lbl.setTextColor(COLOR_WHITE);
        lbl.setTypeface(lbl.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(16 * d);
        lp.bottomMargin = Math.round(8 * d);
        form.addView(lbl, lp);
    }

    private void showDevicePicker(Identity id, Button updateBtn) {
        try {
            java.lang.reflect.Field f = IdentityGenerator.class.getDeclaredField("DEVICE_PROFILES");
            f.setAccessible(true);
            String[][] profiles = (String[][]) f.get(null);
            String[] names = new String[profiles.length];
            for (int i = 0; i < profiles.length; i++) names[i] = profiles[i][0] + " " + profiles[i][1];
            new AlertDialog.Builder(this)
                    .setTitle("Select Device")
                    .setItems(names, (d, which) -> {
                        String[] p = profiles[which];
                        id.brand = p[0]; id.model = p[1]; id.manufacturer = p[2];
                        id.device = p[3]; id.product = p[4]; id.hardware = p[5]; id.fingerprint = p[6];
                        updateBtn.setText("Device: " + p[1]);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showCarrierPicker(Identity id, Button updateBtn) {
        try {
            java.lang.reflect.Field f = IdentityGenerator.class.getDeclaredField("CARRIER_PROFILES");
            f.setAccessible(true);
            String[][] carriers = (String[][]) f.get(null);
            String[] names = new String[carriers.length];
            for (int i = 0; i < carriers.length; i++) names[i] = carriers[i][1] + " (" + carriers[i][0] + ")";
            new AlertDialog.Builder(this)
                    .setTitle("Select Carrier")
                    .setItems(names, (d, which) -> {
                        String[] c = carriers[which];
                        id.networkOperator = c[0]; id.networkOperatorName = c[1];
                        id.simOperator = c[0]; id.simOperatorName = c[1];
                        id.simCountryIso = c[2]; id.networkCountryIso = c[2];
                        Identity tmp = IdentityGenerator.generateRandom();
                        id.imsi = c[0] + tmp.imsi.substring(Math.min(c[0].length(), tmp.imsi.length()));
                        updateBtn.setText("Carrier: " + c[1]);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ===== 设置页 =====
    private View buildSettingsPage() {
        float d = getResources().getDisplayMetrics().density;
        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(Math.round(24 * d), Math.round(48 * d), Math.round(24 * d), Math.round(32 * d));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(28);
        title.setTextColor(COLOR_WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        ll.addView(title);

        addSettingsSection(ll, "Language", d);
        Button btnLang = makeGlassBtn("Language: English", 15);
        ll.addView(btnLang, makeFormLp(d));
        btnLang.setOnClickListener(v -> {
            if (LANG_EN.equals(currentLang)) currentLang = LANG_ZH;
            else if (LANG_ZH.equals(currentLang)) currentLang = LANG_RU;
            else currentLang = LANG_EN;
            getSharedPreferences("devicereset_ui", MODE_PRIVATE)
                    .edit().putString(PREFS_LANG, currentLang).apply();
            btnLang.setText("Language: " + (LANG_EN.equals(currentLang) ? "English" : LANG_ZH.equals(currentLang) ? "中文" : "Русский"));
            Toast.makeText(this, "Language changed, restart app to apply", Toast.LENGTH_SHORT).show();
        });

        Button btnUpdate = makeGlassBtn("Check Update", 15);
        ll.addView(btnUpdate, makeFormLp(d));
        btnUpdate.setOnClickListener(v -> checkUpdate(true));

        addSettingsSection(ll, "Spoofing Options", d);
        String[] hookLabels = {"Android ID", "Ad ID (AAID)", "IMEI/MEID", "Device Model (Build)", "MAC Address", "GSF ID", "Carrier Info"};
        for (int i = 0; i < 7; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(16 * d), Math.round(12 * d), Math.round(16 * d), Math.round(12 * d));
            hookGlass[i] = new GlassButtonDrawable(24 * d, 1 * d, false);
            row.setBackground(hookGlass[i]);
            hookTv[i] = new TextView(this);
            hookTv[i].setText(hookLabels[i]);
            hookTv[i].setTextSize(15);
            hookTv[i].setTextColor(COLOR_WHITE);
            row.addView(hookTv[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final int idx = i;
            row.setOnClickListener(v -> toggleHook(idx));
            ll.addView(row, makeFormLp(d));
        }
        loadHookStates();

        addSettingsSection(ll, "Maintenance", d);
        Button btnReset = makeGlassBtn("Reset Identity (by package)", 15);
        ll.addView(btnReset, makeFormLp(d));
        btnReset.setOnClickListener(v -> showResetDialog());

        Button btnAbout = makeGlassBtn("About", 15);
        ll.addView(btnAbout, makeFormLp(d));
        btnAbout.setOnClickListener(v -> showAboutDialog());

        addSettingsSection(ll, "Usage", d);
        addInfoBlock(ll, "How to use",
                "1. LSPosed Manager -> Modules -> Enable this module -> Check target apps in Scope\n" +
                "2. Reboot phone (required)\n" +
                "3. Clear target app data (System Settings -> Apps -> Storage -> Clear data)\n" +
                "4. Reopen the app to get a brand new device identity\n" +
                "5. Use the Apps tab to view and customize per-app identities", d);
        addInfoBlock(ll, "How it works",
                "The module places a hidden sentinel file in the target app's private directory. " +
                "Clearing app data deletes the entire private directory, including the sentinel file. " +
                "On next launch, the module detects the missing sentinel and generates a new device identity.", d);
        addInfoBlock(ll, "Spoofed identifiers",
                "* Android ID (SSAID)\n* Advertising ID (AAID) / AppSet ID\n* IMEI / MEID / IMSI / ICCID\n" +
                "* Serial number / MAC address\n* GSF ID\n* Device: Brand, Model, Manufacturer, Build fingerprint\n" +
                "* Carrier: Code, Name, Country", d);
        addInfoBlock(ll, "Warnings",
                "* For personal privacy protection and technical testing only\n" +
                "* Some apps detect Xposed/Root traces, account ban risk exists\n" +
                "* For packed apps, enable \"Exclude resource hooks\" in LSPosed scope\n" +
                "* Native-layer system property reads cannot be intercepted by Java hooks\n" +
                "* Test on non-critical apps first\n" +
                "* Troubleshooting: LSPosed -> Logs -> Search \"DeviceReset\"", d);

        sv.addView(ll);
        return sv;
    }

    private void addSettingsSection(LinearLayout ll, String text, float d) {
        TextView lbl = new TextView(this);
        lbl.setText(text);
        lbl.setTextSize(16);
        lbl.setTextColor(COLOR_WHITE);
        lbl.setTypeface(lbl.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(20 * d);
        lp.bottomMargin = Math.round(10 * d);
        ll.addView(lbl, lp);
    }

    private void addInfoBlock(LinearLayout ll, String title, String content, float d) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(COLOR_BLUE);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        ll.addView(t);
        TextView c = new TextView(this);
        c.setText(content);
        c.setTextSize(12);
        c.setTextColor(COLOR_GRAY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(12 * d);
        ll.addView(c, lp);
    }

    private void toggleHook(int idx) {
        boolean current = readHookState(idx);
        boolean next = !current;
        writeHookState(idx, next);
        hookTv[idx].setTextColor(next ? COLOR_BLUE : COLOR_WHITE);
        hookGlass[idx].setGlassSelected(next);
    }

    private boolean readHookState(int idx) {
        try {
            switch (idx) {
                case 0: return Config.isHookAndroidId(this);
                case 1: return Config.isHookAdId(this);
                case 2: return Config.isHookImei(this);
                case 3: return Config.isHookBuild(this);
                case 4: return Config.isHookMac(this);
                case 5: return Config.isHookGsf(this);
                case 6: return Config.isHookCarrier(this);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void writeHookState(int idx, boolean val) {
        try {
            switch (idx) {
                case 0: Config.setHookAndroidId(this, val); break;
                case 1: Config.setHookAdId(this, val); break;
                case 2: Config.setHookImei(this, val); break;
                case 3: Config.setHookBuild(this, val); break;
                case 4: Config.setHookMac(this, val); break;
                case 5: Config.setHookGsf(this, val); break;
                case 6: Config.setHookCarrier(this, val); break;
            }
        } catch (Throwable ignored) {}
    }

    private void loadHookStates() {
        for (int i = 0; i < 7; i++) {
            boolean state = readHookState(i);
            hookTv[i].setTextColor(state ? COLOR_BLUE : COLOR_WHITE);
            hookGlass[i].setGlassSelected(state);
        }
    }

    // ===== Root 工具 =====
    private java.util.List<String> scanPackagesWithIdentity() {
        java.util.List<String> result = new java.util.ArrayList<>();
        try {
            Process su = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(su.getOutputStream());
            os.writeBytes("for d in /data/data/*/; do pkg=$(basename \"$d\"); if [ -f \"$d/files/.identity_sentinel\" ]; then echo \"$pkg\"; fi; done\n");
            os.writeBytes("exit\n");
            os.flush();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(su.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) result.add(line);
            }
            reader.close();
            su.waitFor();
        } catch (Throwable ignored) {}
        return result;
    }

    private String readIdentityFile(String packageName) {
        try {
            String path = "/data/data/" + packageName + "/files/.identity_sentinel";
            Process su = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(su.getOutputStream());
            os.writeBytes("cat '" + path + "'\n");
            os.writeBytes("exit\n");
            os.flush();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(su.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            int rc = su.waitFor();
            String output = sb.toString().trim();
            if (rc == 0 && !output.isEmpty() && output.startsWith("{")) return output;
            return null;
        } catch (Throwable t) { return null; }
    }

    private void writeIdentityFile(String packageName, String json) {
        try {
            String path = "/data/data/" + packageName + "/files/.identity_sentinel";
            Process su = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(su.getOutputStream());
            os.writeBytes("echo '" + json.replace("'", "'\\''") + "' > '" + path + "'\n");
            os.writeBytes("chmod 600 '" + path + "'\n");
            os.writeBytes("exit\n");
            os.flush();
            su.waitFor();
        } catch (Throwable ignored) {}
    }

    // ===== 对话框 =====
    private void showResetDialog() {
        final EditText input = new EditText(this);
        input.setHint("Enter package name, e.g. com.example.app");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        layout.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Reset Identity")
                .setMessage("Enter the package name to reset. Next launch will get a new identity.")
                .setView(layout)
                .setPositiveButton("Reset", (d, w) -> {
                    String pkg = input.getText().toString().trim();
                    if (pkg.isEmpty()) { Toast.makeText(this, "Please enter package name", Toast.LENGTH_SHORT).show(); return; }
                    try {
                        boolean ok = SentinelDetector.resetIdentity(pkg, this);
                        Toast.makeText(this, ok ? "Reset " + pkg : "Reset failed, ensure ROOT", Toast.LENGTH_LONG).show();
                    } catch (Throwable t) {
                        Toast.makeText(this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("DeviceResetSpooferX")
                .setMessage("Version: 3.0.0\n\nLSPosed module that auto-generates new device identity after clearing app data.\n\nApplies to all apps checked in LSPosed scope.\nSupports Chinese / English / Russian")
                .setPositiveButton("OK", null)
                .show();
    }

    // ===== 更新检测 =====
    private void checkUpdate(boolean manual) {
        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { versionName = "0"; }
        UpdateChecker.check(UPDATE_REPO, versionName,
                (latest, tag, hasUpdate, error) -> runOnUiThread(() -> {
                    if (hasUpdate) {
                        showUpdateDialog(latest, tag);
                    } else if (manual) {
                        Toast.makeText(this, error != null ? "Update check failed: " + error : "You are up to date",
                                Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void showUpdateDialog(String latest, String tag) {
        new AlertDialog.Builder(this)
                .setTitle("New version v" + latest)
                .setMessage("Download now?")
                .setPositiveButton("Download", (d, w) -> {
                    String asset = "DeviceResetSpooferX-v" + latest + ".apk";
                    String direct = "https://github.com/" + UPDATE_REPO + "/releases/download/" + tag + "/" + asset;
                    String[] mirrors = {"https://ghfast.top/", "https://gh-proxy.com/", "https://ghproxy.net/",
                            "https://mirror.ghproxy.com/", "https://github.moeyy.xyz/"};
                    String[] urls = new String[mirrors.length + 1];
                    for (int i = 0; i < mirrors.length; i++) urls[i] = mirrors[i] + direct;
                    urls[mirrors.length] = direct;
                    AppDownloader.start(this, urls, REPO_HOME_URL,
                            "DeviceResetSpooferX-v" + latest + ".apk", latest, currentLang);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
