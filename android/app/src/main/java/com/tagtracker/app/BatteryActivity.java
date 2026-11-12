package com.tagtracker.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Shows the background-check history and battery model, so users understand the low usage. */
public class BatteryActivity extends Activity {

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle(I18n.t("Battery & activity"));
        ScrollView sc = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        root.setPadding(p, p, p, p);
        sc.addView(root);
        setContentView(sc);
        new Thread(this::load).start();
    }

    private void load() {
        String json;
        try {
            json = TagApp.py(this).callAttr("battery_log").toString();
        } catch (Exception e) {
            json = "{}";
        }
        final String fj = json;
        runOnUiThread(() -> render(fj));
    }

    private void render(String json) {
        root.removeAllViews();
        title(I18n.t("Very low battery use"));
        note(I18n.t("Tag Tracker is built to sip battery:\n"
                + "• It uses NO GPS (positions come from Google's network). Location access is only used for the optional Bluetooth battery check.\n"
                + "• It wakes only briefly every ") + TagApp.intervalMinutes(this) + I18n.t(" minutes, then sleeps.\n"
                + "• It closes its network connection between checks.\n"
                + "• Android (WorkManager) batches the checks at efficient moments."));

        int checksToday = 0, avgMs = 0;
        double activeSec = 0;
        JSONArray checks = new JSONArray();
        try {
            JSONObject o = new JSONObject(json);
            checksToday = o.optInt("checks_today");
            avgMs = o.optInt("avg_ms");
            activeSec = o.optDouble("active_seconds_today");
            checks = o.optJSONArray("checks");
            if (checks == null) checks = new JSONArray();
        } catch (Exception ignored) {
        }

        heading(I18n.t("Today"));
        info(I18n.t("Checks today: ") + checksToday);
        info(I18n.t("Total active time today: ~") + activeSec + I18n.t(" seconds  (the rest of the day it's asleep)"));
        info(I18n.t("Average per check: ~") + (avgMs / 1000.0) + " s");

        button(I18n.t("Open Android battery settings for this app"), v -> openBatterySettings());

        heading(I18n.t("Recent checks"));
        if (checks.length() == 0) {
            note(I18n.t("No checks logged yet. They appear here after the app runs in the background."));
        }
        for (int i = 0; i < checks.length(); i++) {
            JSONObject c = checks.optJSONObject(i);
            if (c == null) continue;
            long ts = c.optLong("ts") * 1000L;
            String when = DateFormat.format("MMM d, h:mm a", ts).toString();
            String line = when + "  ·  " + (c.optInt("ms") / 1000.0) + "s"
                    + (c.optInt("added") > 0 ? "  ·  +" + c.optInt("added") + I18n.t(" new") : "")
                    + (c.optBoolean("ok", true) ? "" : I18n.t("  ·  (failed)"));
            info(line);
        }
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private void title(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(22);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD); t.setPadding(0, dp(4), 0, dp(6)); root.addView(t); }
    private void heading(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(16);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD); t.setPadding(0, dp(16), 0, dp(4)); root.addView(t); }
    private void note(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(14);
        t.setPadding(0, dp(2), 0, dp(6)); root.addView(t); }
    private void info(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(14);
        t.setPadding(0, dp(3), 0, dp(3)); root.addView(t); }
    private void button(String s, android.view.View.OnClickListener onClick) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10); root.addView(b, lp);
    }
}
