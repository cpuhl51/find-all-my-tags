package com.tagtracker.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;

/** Setup and settings: Google connection, background checking, Google Drive backup. */
public class SettingsActivity extends Activity {
    private static final int REQ_SIGN_IN = 1, REQ_UNLOCK = 2, REQ_DRIVE_FILE = 3, REQ_RESTORE = 4, REQ_IMPORT = 5;
    private static final int[] INTERVALS = {15, 30, 60, 120, 240};

    private LinearLayout root;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(I18n.t("Tag Tracker setup"));

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 31) render();
    }

    private void render() {
        root.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText(I18n.t("Loading…"));
        loading.setPadding(0, dp(20), 0, 0);
        root.addView(loading);
        // Read the Google auth state OFF the UI thread — Python start can take seconds (avoids ANR).
        new Thread(() -> {
            boolean signedIn = false, unlocked = false;
            String email = "", error = null;
            try {
                JSONObject a = new JSONObject(TagApp.py(this).callAttr("account_json").toString());
                signedIn = a.optBoolean("signed_in");
                unlocked = a.optBoolean("unlocked");
                email = a.optString("email");
                error = a.has("error") && !a.isNull("error") ? a.optString("error") : null;
            } catch (Exception e) {
                error = e.getMessage();
            }
            final boolean fSignedIn = signedIn, fUnlocked = unlocked;
            final String fEmail = email, fError = error;
            runOnUiThread(() -> buildUi(fSignedIn, fUnlocked, fEmail, fError));
        }).start();
    }

    private void buildUi(boolean signedIn, boolean unlocked, String email, String error) {
        if (isFinishing() || isDestroyed()) return;
        root.removeAllViews();

        // Welcome / onboarding intro
        TextView welcome = new TextView(this);
        welcome.setText(I18n.t("Set up Tag Tracker"));
        welcome.setTextSize(22);
        welcome.setTypeface(welcome.getTypeface(), android.graphics.Typeface.BOLD);
        welcome.setPadding(0, dp(6), 0, dp(2));
        root.addView(welcome);
        note(I18n.t("Find Hub shows only one tag at a time and only its last spot. This app shows all your tags on one map "
                + "and saves their full history. New here? Read the quick guide first – it explains everything."));
        Button guide = new Button(this);
        guide.setText(I18n.t("📖  How it works (guide)"));
        guide.setAllCaps(false);
        guide.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.bottomMargin = dp(8);
        root.addView(guide, glp);
        progress(signedIn, unlocked);

        heading(I18n.t("1. Connect your Google account"));
        note(I18n.t("Two quick steps on Google's own pages (the app never sees your password or PIN). "
                + "Do both – locations stay hidden until step 2 is done."));

        // Step 1
        info((signedIn ? "✓ " : "① ") + I18n.t("Step 1: Sign in")
                + (signedIn && !email.isEmpty() ? " – " + email : ""));
        button(signedIn ? I18n.t("Sign in again") : I18n.t("Sign in to Google"),
                v -> startAuth(GoogleAuthActivity.MODE_SIGN_IN, REQ_SIGN_IN));

        // Step 2
        info((unlocked ? "✓ " : "② ") + I18n.t("Step 2: Unlock encryption (needed to see locations)"));
        button(unlocked ? I18n.t("Unlock again") : I18n.t("Unlock encryption keys"),
                v -> startAuth(GoogleAuthActivity.MODE_UNLOCK, REQ_UNLOCK));
        if (signedIn && !unlocked) {
            note(I18n.t("⚠ You're signed in and your tags are listed, but the map will stay empty until you finish "
                    + "step 2. Tap “Unlock encryption keys” and enter your phone's screen lock."));
        }
        if (signedIn && unlocked) info(I18n.t("✓ All set – the app can read and decrypt your tags."));
        if (error != null) info(I18n.t("Note: ") + error);

        // Option B alternative: import a login file made on a PC (most reliable).
        note(I18n.t("— or —"));
        note(I18n.t("Option B: If the steps above fail, run the desktop setup once on a PC "
                + "(python -m server.manage google-login), copy the resulting data/google_secrets.json to this "
                + "phone, and import it here. No re-login needed on the phone."));
        button(I18n.t("Import login file (google_secrets.json)"), v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "*/*"});
            try {
                startActivityForResult(i, REQ_IMPORT);
            } catch (Exception e) {
                toast(I18n.t("No file picker available"));
            }
        });

        heading(I18n.t("2. Background checking"));
        note(I18n.t("The app checks Find Hub in the background with Android WorkManager (Google's battery-efficient "
                + "scheduler) and saves every location. It uses almost no battery while idle: it only wakes for a "
                + "few seconds per check, needs a network connection, and closes the connection in between. It uses "
                + "no GPS (locations come from Google's network). Location access is only used for the optional Bluetooth battery check. "
                + "Android's minimum interval is 15 minutes; pick a longer one to use even less battery."));
        int interval = TagApp.intervalMinutes(this);
        info(I18n.t("Currently every ") + interval + I18n.t(" minutes."));
        LinearLayout rowI = row();
        for (int m : INTERVALS) {
            Button b = chip(m + I18n.t(" min"), v -> {
                TagApp.prefs(this).edit().putInt(TagApp.KEY_INTERVAL, m).apply();
                PollWorker.schedule(this);
                render();
            });
            if (m == interval) b.setEnabled(false);
            rowI.addView(b);
        }
        root.addView(rowI);
        note(I18n.t("Longer interval = less battery. 240 min is the battery-saver option. See exactly what the app "
                + "does and its tiny active time in Battery & activity:"));
        button(I18n.t("Battery & activity →"), v -> startActivity(new Intent(this, BatteryActivity.class)));
        if (!isIgnoringBattery()) {
            note(I18n.t("Optional: only if your phone (Xiaomi/Samsung/etc.) aggressively kills background apps, allow "
                    + "unrestricted running so checks aren't skipped. Not required on most phones."));
            button(I18n.t("Allow unrestricted background (optional)"), v -> requestIgnoreBattery());
        } else {
            info(I18n.t("✓ Unrestricted background running is allowed."));
        }
        boolean bgNotify = TagApp.prefs(this).getBoolean(TagApp.KEY_BG_NOTIFY, true);
        info(bgNotify ? I18n.t("✓ A status notification shows while the app runs in the background.")
                : I18n.t("Background status notification is off."));
        button(bgNotify ? I18n.t("Hide background notification") : I18n.t("Show background notification"), v -> {
            TagApp.prefs(this).edit().putBoolean(TagApp.KEY_BG_NOTIFY, !bgNotify).apply();
            render();
        });

        note(I18n.t("Closing the app (swiping it away) does NOT stop tracking – it keeps checking and saving in "
                + "the background, which is what you want. Use the button below only when you want to FULLY stop it."));
        boolean paused = TagApp.prefs(this).getBoolean(TagApp.KEY_PAUSED, false);
        if (paused) {
            info(I18n.t("⏸ Background tracking is STOPPED. No locations are being saved."));
            button(I18n.t("Resume background tracking"), v -> {
                TagApp.prefs(this).edit().putBoolean(TagApp.KEY_PAUSED, false).apply();
                PollWorker.schedule(this);
                toast(I18n.t("Tracking resumed"));
                render();
            });
        } else {
            button(I18n.t("⏹ Stop tracking (full exit)"), v -> {
                TagApp.prefs(this).edit().putBoolean(TagApp.KEY_PAUSED, true).apply();
                PollWorker.stop(this);
                toast(I18n.t("Background tracking stopped. Tap Resume to start again."));
                render();
            });
        }

        heading(I18n.t("3. Storage & sync"));
        note(I18n.t("Your locations are ALWAYS saved on the phone first – they can never be lost, even with no "
                + "internet or if Drive is busy. Google Drive is an extra cloud copy on top. Choose:\n"
                + "• CONTINUOUS SYNC (recommended): phone + Google Drive, syncs after each check. If offline or "
                + "rate-limited, it just retries later – nothing is lost.\n"
                + "• LOCAL ONLY: keep everything on the phone, no cloud (turn both options below off)."));

        // Default (automatic) mode: one fixed folder in the SAME Google account, no file picking.
        // Signing into the app on another phone with the same account pulls the history back.
        boolean autoDrive = TagApp.prefs(this).getBoolean(TagApp.KEY_DRIVE_AUTO, false);
        info(autoDrive ? I18n.t("✓ DEFAULT automatic sync is ON (folder ‘TagTracker’ in your Google account).")
                : I18n.t("DEFAULT: automatic sync to a ‘TagTracker’ folder in your own Google account – no "
                  + "file picking, and it comes back automatically when you sign in on another phone."));
        button(autoDrive ? I18n.t("Sync default folder now") : I18n.t("Use automatic default sync (recommended)"), v -> {
            TagApp.prefs(this).edit().putBoolean(TagApp.KEY_DRIVE_AUTO, true).apply();
            new Thread(() -> {
                try {
                    TagApp.py(this).callAttr("set_drive_default", true);
                    int got = TagApp.py(this).callAttr("drive_default_restore").toInt();
                    TagApp.py(this).callAttr("drive_default_upload");
                    toast(got > 0 ? I18n.t("Default sync on – pulled ") + got + I18n.t(" saved location(s) from Drive")
                            : I18n.t("Default sync on – Drive folder ready"));
                } catch (Exception e) {
                    toast(I18n.t("Automatic sync needs the Google sign-in first: ") + e.getMessage());
                }
                runOnUiThread(this::render);
            }).start();
        });
        if (autoDrive) {
            button(I18n.t("Turn off automatic sync"), v -> {
                TagApp.prefs(this).edit().putBoolean(TagApp.KEY_DRIVE_AUTO, false).apply();
                new Thread(() -> { try { TagApp.py(this).callAttr("set_drive_default", false); } catch (Exception ignored) {} }).start();
                render();
            });
        }

        note(I18n.t("— or use a CUSTOM file (any account / location) —\n"
                + "In the picker, tap ☰ and choose GOOGLE DRIVE (not phone storage), pick a folder, and name it "
                + "e.g. TagTracker-history.csv."));
        if (DriveBackup.enabled(this)) {
            long last = TagApp.prefs(this).getLong(TagApp.KEY_LAST_BACKUP, 0);
            info(I18n.t("✓ Auto-syncing to your Drive file.") + (last > 0
                    ? I18n.t("  Last sync: ") + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(last)) : I18n.t("  (no sync yet)")));
            button(I18n.t("Sync to Drive now"), v -> new Thread(() -> {
                String err = DriveBackup.backupNow(this);
                toast(err == null ? I18n.t("Synced to Drive") : I18n.t("Sync failed: ") + err);
                runOnUiThread(this::render);
            }).start());
            button(I18n.t("Restore history from a Drive file"), v -> pickRestoreFile());
            button(I18n.t("Choose a different file"), v -> pickDriveFile());
            button(I18n.t("Turn off Drive sync"), v -> {
                TagApp.prefs(this).edit().remove(TagApp.KEY_DRIVE_URI).apply();
                render();
            });
        } else {
            button(I18n.t("Choose Google Drive file"), v -> pickDriveFile());
            button(I18n.t("Restore history from a Drive file"), v -> pickRestoreFile());
        }

        heading(I18n.t("4. Your tags"));
        button(I18n.t("Refresh tag list from Google"), v -> new Thread(() -> {
            try {
                JSONArray tags = new JSONArray(TagApp.py(this).callAttr("list_tags_json").toString());
                toast(tags.length() + I18n.t(" tag(s) found"));
            } catch (Exception e) {
                toast(I18n.t("Could not list tags: ") + e.getMessage());
            }
        }).start());
        button(I18n.t("Check for locations now"), v -> {
            PollWorker.runNow(this);
            toast(I18n.t("Checking Find Hub…"));
        });

        heading(I18n.t("5. Tag batteries (Bluetooth)"));
        note(I18n.t("Tags send their battery level (normal / low / critical) over Bluetooth. The app can only read it "
                + "when a tag is near this phone – then you get a notification if a battery gets low. "
                + "Exact percentages are not sent by the tags. Run “Check for locations now” once first."));
        if (!BleBattery.hasPermissions(this)) {
            button(I18n.t("Allow Bluetooth battery check"), v -> requestPermissions(BleBattery.neededPermissions(), 31));
        } else {
            info(I18n.t("✓ Battery check runs whenever you open the app."));
            button(I18n.t("Show battery diagnostics"), v -> showBatteryDiag());
            if (BleBattery.canScanInBackground(this)) {
                info(I18n.t("✓ Also checked in the background with every location check."));
            } else {
                note(I18n.t("To also check in the background, set location access for this app to “Allow all the time”."));
                button(I18n.t("Open app permissions"), v -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", getPackageName(), null)));
                    } catch (Exception ignored) {
                    }
                });
            }
        }

        heading(I18n.t("6. Warning: tag not seen for a long time"));
        note(I18n.t("If a tag has not been seen for longer than this, you get a notification once – often a sign of an "
                + "empty battery or a lost tag. The tag is also marked with ⚠ in the list."));
        int stale = TagApp.prefs(this).getInt("stale_hours", 72);
        LinearLayout rowS = row();
        int[] staleOpts = {24, 72, 168, 0};
        for (int h : staleOpts) {
            String label = h == 0 ? I18n.t("Off") : (h / 24) + I18n.t(" day(s)");
            Button b = chip(label, v -> {
                TagApp.prefs(this).edit().putInt("stale_hours", h).apply();
                new Thread(() -> { try { TagApp.py(this).callAttr("set_stale_hours", h); } catch (Exception ignored) {} }).start();
                render();
            });
            if (h == stale) b.setEnabled(false);
            rowS.addView(b);
        }
        root.addView(rowS);

        heading(I18n.t("7. Own server (view on PC)"));
        note(I18n.t("Optional: the app sends your tags' locations to YOUR OWN web server after every check, so you can "
                + "see the same map on a PC. Upload the Find All My Tags server package (PHP) to your web space first; "
                + "its setup page shows the upload key. Nothing is sent anywhere else."));
        String srv = TagApp.prefs(this).getString("server_url", "");
        if (srv.isEmpty()) {
            button(I18n.t("Set up server"), v -> showServerDialog());
        } else {
            info(I18n.t("✓ Uploading to: ") + srv);
            button(I18n.t("Open PC view in browser"), v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(srv))); } catch (Exception ignored) {}
            });
            button(I18n.t("Upload now"), v -> new Thread(() -> {
                String msg;
                try {
                    String r = TagApp.py(this).callAttr("server_upload").toString();
                    msg = I18n.t("Uploaded: ") + r.replace("ok:", "") + I18n.t(" new location(s)");
                } catch (Exception e) {
                    msg = I18n.t("Upload failed: ") + e.getMessage();
                }
                final String m = msg;
                runOnUiThread(() -> toast(m));
            }).start());
            button(I18n.t("Change server"), v -> showServerDialog());
            button(I18n.t("Turn off server upload"), v -> {
                TagApp.prefs(this).edit().remove("server_url").apply();
                new Thread(() -> { try { TagApp.py(this).callAttr("set_server", "", ""); } catch (Exception ignored) {} }).start();
                render();
            });
        }

        heading(I18n.t("8. App lock (this phone only)"));
        note(I18n.t("Protect the app with a password so no one who picks up your phone can open your tracker. "
                + "The password and hint are stored ONLY on this phone – never sent to Google or Drive, so they "
                + "can't be recovered from anywhere else. If you forget it and the hint doesn't help, reinstall the app."));
        if (TagApp.hasAppLock(this)) {
            info(I18n.t("✓ App lock is ON."));
            button(I18n.t("Change app password"), v -> showAppLockDialog());
            button(I18n.t("Turn off app lock"), v -> { TagApp.setAppLock(this, null, null); render(); });
        } else {
            button(I18n.t("Set an app password"), v -> showAppLockDialog());
        }

        heading(I18n.t("Legal & privacy"));
        note(I18n.t("Not affiliated with Google. This app uses an UNOFFICIAL method to read the Find network – "
                + "Google may block it at any time, and it may violate Google's Terms of Service. For tracking "
                + "YOUR OWN property only, never a person without consent. Your data stays on your phone and your "
                + "own Google Drive; nothing is sent to the developer. Provided AS IS, no warranty. Open-source "
                + "(GPL-3.0), built on GoogleFindMyTools."));
        button(I18n.t("Read full disclaimer & privacy"), v -> showLegal());

        status = new TextView(this);
        status.setPadding(0, dp(16), 0, 0);
        root.addView(status);
        button(I18n.t("Open map"), v -> { startActivity(new Intent(this, MainActivity.class)); finish(); });
        button(I18n.t("Help"), v -> startActivity(new Intent(this, HelpActivity.class)));
    }

    private void pickRestoreFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/comma-separated-values", "text/plain"});
        try {
            startActivityForResult(i, REQ_RESTORE);
        } catch (Exception e) {
            toast(I18n.t("No file picker available"));
        }
    }

    private void progress(boolean signedIn, boolean unlocked) {
        LinearLayout r = row();
        r.setPadding(0, dp(2), 0, dp(10));
        addStep(r, I18n.t("Sign in"), signedIn);
        addStep(r, I18n.t("Unlock"), unlocked);
        addStep(r, I18n.t("Background"), true);
        root.addView(r);
    }

    private void addStep(LinearLayout r, String label, boolean done) {
        TextView t = new TextView(this);
        t.setText((done ? "✓ " : "○ ") + label);
        t.setTextSize(12);
        t.setPadding(dp(11), dp(6), dp(11), dp(6));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(999));
        g.setColor(done ? 0xFF16A34A : 0x1A64748B);
        t.setBackground(g);
        t.setTextColor(done ? 0xFFFFFFFF : 0xFF64748B);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        r.addView(t, lp);
    }

    private void showLegal() {
        String t = I18n.t("NOT AFFILIATED WITH GOOGLE\n"
                + "Tag Tracker is an independent, open-source app. It is not created, endorsed or supported by "
                + "Google. “Google”, “Find Hub” and “Android” are trademarks of Google LLC.\n\n"
                + "UNOFFICIAL METHOD / YOUR RISK\n"
                + "It reads the Find network using the open-source GoogleFindMyTools, an unofficial method. Google "
                + "provides no public API and may change or block this at any time. Using an unofficial client may "
                + "violate Google's Terms of Service. You use this app at your own risk, including risk to your "
                + "Google account.\n\n"
                + "ACCEPTABLE USE\n"
                + "Track only trackers you own or are authorised to track. Do NOT use it to locate or monitor any "
                + "person without their knowledge and consent – that is illegal in many places. If something is "
                + "stolen, give the location to the police; do not confront anyone.\n\n"
                + "PRIVACY\n"
                + "Your history stays on your phone and, if enabled, in your own Google Drive. The app has no servers "
                + "of its own and sends no data to the developer or any third party. Your Google tokens stay on your "
                + "device.\n\n"
                + "NO WARRANTY\n"
                + "Provided “AS IS”, without warranty of any kind. The authors are not liable for any damages, "
                + "data loss, account issues, or missed/incorrect locations.\n\n"
                + "LICENSE\n"
                + "GNU GPL v3.0. Built on GoogleFindMyTools by Leon Böttger (GPL-3.0).");
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(13);
        int pad = dp(20);
        tv.setPadding(pad, pad, pad, pad);
        ScrollView sc = new ScrollView(this);
        sc.addView(tv);
        new AlertDialog.Builder(this).setTitle(I18n.t("Legal, disclaimer & privacy"))
                .setView(sc).setPositiveButton(I18n.t("Close"), null).show();
    }

    /** Bericht über den letzten Bluetooth-Scan anzeigen und zum Weiterschicken teilen. */
    private void showBatteryDiag() {
        new Thread(() -> {
            String txt;
            try {
                txt = TagApp.py(this).callAttr("ble_diag").toString();
            } catch (Exception e) {
                txt = "Fehler: " + e.getMessage();
            }
            final String report = txt;
            runOnUiThread(() -> {
                TextView tv = new TextView(this);
                tv.setText(report);
                tv.setTextIsSelectable(true);
                tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                tv.setTextSize(11);
                int pad = dp(16);
                tv.setPadding(pad, pad, pad, pad);
                android.widget.ScrollView sv = new android.widget.ScrollView(this);
                sv.addView(tv);
                new AlertDialog.Builder(this)
                        .setTitle(I18n.t("Battery diagnostics"))
                        .setView(sv)
                        .setPositiveButton(I18n.t("Share"), (d, w) -> {
                            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report);
                            startActivity(Intent.createChooser(i, I18n.t("Share")));
                        })
                        .setNegativeButton(I18n.t("Close"), null)
                        .show();
            });
        }).start();
    }

    private void showServerDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, 0);
        EditText url = new EditText(this);
        url.setHint(I18n.t("Server address, e.g. https://example.com/tags/"));
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(TagApp.prefs(this).getString("server_url", ""));
        EditText token = new EditText(this);
        token.setHint(I18n.t("Upload key (shown on the server's setup page)"));
        token.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        box.addView(url);
        box.addView(token);
        new AlertDialog.Builder(this)
                .setTitle(I18n.t("Own server (view on PC)"))
                .setView(box)
                .setPositiveButton(I18n.t("Save"), (d, w) -> {
                    String u = url.getText().toString().trim(), k = token.getText().toString().trim();
                    if (u.isEmpty() || k.isEmpty()) { toast(I18n.t("Please enter address and key.")); return; }
                    toast(I18n.t("Testing connection…"));
                    new Thread(() -> {
                        String msg;
                        boolean ok = false;
                        try {
                            TagApp.py(this).callAttr("set_server", u, k);
                            String norm = new JSONObject(TagApp.py(this).callAttr("server_info_json").toString()).optString("url", u);
                            TagApp.prefs(this).edit().putString("server_url", norm).apply();
                            msg = I18n.t("✓ Connected – locations are uploaded after every check.");
                            ok = true;
                        } catch (Exception e) {
                            msg = I18n.t("Connection failed: ") + e.getMessage();
                        }
                        final String m = msg;
                        runOnUiThread(() -> { toast(m); render(); });
                    }).start();
                })
                .setNegativeButton(I18n.t("Cancel"), null)
                .show();
    }

    private void showAppLockDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, 0);
        EditText pw = new EditText(this);
        pw.setHint(I18n.t("New app password (min 4)"));
        pw.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText hint = new EditText(this);
        hint.setHint(I18n.t("Recovery hint (e.g. ‘usual PIN’)"));
        box.addView(pw);
        box.addView(hint);
        new AlertDialog.Builder(this)
                .setTitle(I18n.t("Set app password"))
                .setView(box)
                .setPositiveButton(I18n.t("Save"), (d, w) -> {
                    String p = pw.getText().toString();
                    if (p.length() < 4) { toast(I18n.t("Password too short")); return; }
                    TagApp.setAppLock(this, p, hint.getText().toString());
                    TagApp.appUnlocked = true;
                    toast(I18n.t("App lock set"));
                    render();
                })
                .setNegativeButton(I18n.t("Cancel"), null)
                .show();
    }

    private void startAuth(String mode, int req) {
        Intent i = new Intent(this, GoogleAuthActivity.class);
        i.putExtra(GoogleAuthActivity.EXTRA_MODE, mode);
        startActivityForResult(i, req);
    }

    private void pickDriveFile() {
        // ACTION_CREATE_DOCUMENT lets the user create a new file (or pick an existing one to overwrite) in Drive.
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE, "TagTracker-history.csv");
        try {
            startActivityForResult(i, REQ_DRIVE_FILE);
        } catch (Exception e) {
            toast(I18n.t("No file picker available"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQ_DRIVE_FILE && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            TagApp.prefs(this).edit().putString(TagApp.KEY_DRIVE_URI, uri.toString())
                    .putLong(TagApp.KEY_LAST_BACKUP, 0).apply();
            new Thread(() -> {
                String err = DriveBackup.backupNow(this);
                toast(err == null ? I18n.t("Drive sync set up") : I18n.t("Sync failed: ") + err);
                runOnUiThread(this::render);
            }).start();
        } else if (requestCode == REQ_IMPORT && data != null && data.getData() != null) {
            Uri uri = data.getData();
            new Thread(() -> {
                try {
                    File tmp = new File(getCacheDir(), "import-secrets.json");
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         OutputStream out = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    String stateJson = TagApp.py(this).callAttr("import_secrets", tmp.getAbsolutePath()).toString();
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    JSONObject st = new JSONObject(stateJson);
                    toast(st.optBoolean("unlocked")
                            ? I18n.t("Login imported – signed in and unlocked. Tap Check now.")
                            : I18n.t("Login imported, but it has no encryption key (unlock part). Locations may not decrypt."));
                    runOnUiThread(this::render);
                } catch (Exception e) {
                    toast(I18n.t("Import failed: ") + e.getMessage());
                }
            }).start();
        } else if (requestCode == REQ_RESTORE && data != null && data.getData() != null) {
            Uri uri = data.getData();
            new Thread(() -> {
                try {
                    File tmp = new File(getCacheDir(), "restore.csv");
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         OutputStream out = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    int added = TagApp.py(this).callAttr("import_csv", tmp.getAbsolutePath()).toInt();
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    toast(I18n.t("Restored ") + added + I18n.t(" location(s) from the file"));
                } catch (Exception e) {
                    toast(I18n.t("Restore failed: ") + e.getMessage());
                }
            }).start();
        }
        render();
    }

    // ---------- battery optimisation ----------

    private boolean isIgnoringBattery() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    @SuppressLint("BatteryLife")
    private void requestIgnoreBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // ---------- tiny view helpers ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private void heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setPadding(0, dp(22), 0, dp(4));
        root.addView(t);
    }

    private void note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setPadding(0, 0, 0, dp(8));
        root.addView(t);
    }

    private void info(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setPadding(0, dp(4), 0, dp(4));
        root.addView(t);
    }

    private void button(String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        root.addView(b, lp);
    }

    private Button chip(String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }
}
