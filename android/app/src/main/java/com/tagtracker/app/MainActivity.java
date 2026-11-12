package com.tagtracker.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Standalone map screen. Everything runs on the phone: the bundled web UI (web/) is served
 * from the APK assets and talks to the Python core through the "Native" JS bridge instead of
 * an HTTP server. No login, no remote server.
 */
public class MainActivity extends Activity {
    private WebView web;
    private WebViewAssetLoader loader;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final boolean fresh = savedInstanceState == null;
        if (fresh && !TagApp.prefs(this).getBoolean(TagApp.KEY_ONBOARDED, false)) {
            // Brand-new user: show the illustrated "how it works" guide, which leads into setup.
            TagApp.prefs(this).edit().putBoolean(TagApp.KEY_ONBOARDED, true).apply();
            startActivity(new Intent(this, HelpActivity.class).putExtra(HelpActivity.EXTRA_ONBOARD, true));
        } else if (fresh) {
            // Returning user: route to setup if not signed in yet. Off the main thread (avoids ANR).
            new Thread(() -> {
                boolean signedIn = false;
                try {
                    signedIn = new JSONObject(TagApp.py(this).callAttr("account_json").toString())
                            .optBoolean("signed_in");
                } catch (Exception ignored) {
                }
                if (!signedIn) runOnUiThread(() -> startActivity(new Intent(this, SettingsActivity.class)));
            }).start();
        }

        loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebChromeClient(new android.webkit.WebChromeClient());
        web.addJavascriptInterface(new Native(), "Native");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 9);
        }
        // Bewegungsalarm gibt es nicht mehr (es werden alle Tags gezeigt): sicher ausschalten.
        TagApp.prefs(this).edit().putBoolean(TagApp.KEY_GUARD, false).apply();
        if (!TagApp.prefs(this).getBoolean(TagApp.KEY_PAUSED, false)) PollWorker.schedule(this);
        new Thread(() -> {
            try { TagApp.py(this).callAttr("set_phone_id", TagApp.deviceId(this)); } catch (Exception ignored) {}
        }).start();
        maybeRestoreFromDrive();
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("https://appassets.androidplatform.net/assets/web/index.html?lang=" + I18n.code(this));
    }

    /** JS -> Python bridge. Method names match the fetch() paths the web UI would call on the server. */
    class Native {
        @JavascriptInterface
        public String devices() {
            return TagApp.py(MainActivity.this).callAttr(
                    "devices_json", TagApp.intervalMinutes(MainActivity.this),
                    DriveBackup.enabled(MainActivity.this),
                    TagApp.prefs(MainActivity.this).getBoolean(TagApp.KEY_GUARD, false),
                    TagApp.deviceId(MainActivity.this)).toString();
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void openHelp(String anchor) {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, HelpActivity.class)
                    .putExtra(HelpActivity.EXTRA_ANCHOR, anchor)));
        }

        @JavascriptInterface
        public String lang() {
            return I18n.code(MainActivity.this);
        }

        @JavascriptInterface
        public void setLang(String code) {
            runOnUiThread(() -> switchLanguage(code));
        }

        @JavascriptInterface
        public void checkBatteries() {
            runOnUiThread(MainActivity.this::checkBatteries);
        }

        @JavascriptInterface
        public void openLicense() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, HelpActivity.class)
                    .putExtra(HelpActivity.EXTRA_PAGE, "info.html")));
        }

        @JavascriptInterface
        public void share(String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, I18n.t(MainActivity.this, "Share location")));
            });
        }

        @JavascriptInterface
        public void setParked(boolean parked) {
            TagApp.prefs(MainActivity.this).edit().putBoolean(TagApp.KEY_GUARD, parked).apply();
            if (parked) {
                // Arm from the CURRENT position: don't alert on the move that's already stored.
                new Thread(() -> {
                    try {
                        TagApp.py(MainActivity.this).callAttr("new_movements");
                    } catch (Exception ignored) {
                    }
                }).start();
            }
        }

        @JavascriptInterface
        public String history(String deviceId, long start, long end) {
            return TagApp.py(MainActivity.this).callAttr("history_json", deviceId, start, end,
                    TagApp.deviceId(MainActivity.this)).toString();
        }

        @JavascriptInterface
        public void pollNow() {
            PollWorker.runNow(MainActivity.this);
        }

        @JavascriptInterface
        public void locateNow(String deviceId) {
            new Thread(() -> {
                try {
                    int n = TagApp.py(MainActivity.this).callAttr("locate_now", deviceId).toInt();
                    toastUi(n > 0 ? I18n.t("Location updated – saved to history") : I18n.t("No new location right now"));
                    DriveBackup.backupIfDue(MainActivity.this, n > 0);
                } catch (Exception e) {
                    toastUi(msg(e));
                }
            }).start();
        }

        @JavascriptInterface
        public void playSound(String deviceId) {
            new Thread(() -> {
                try {
                    TagApp.py(MainActivity.this).callAttr("play_sound", deviceId);
                    toastUi(I18n.t("Ringing the tag…"));
                } catch (Exception e) {
                    toastUi(msg(e));
                }
            }).start();
        }

        @JavascriptInterface
        public void rename(String deviceId, String name) {
            TagApp.py(MainActivity.this).callAttr("rename", deviceId, name);
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        }

        /** Writes an export to the cache and hands it to a share/save chooser. */
        @JavascriptInterface
        public void export(String deviceId, long start, long end, String format) {
            new Thread(() -> {
                try {
                    String name = "track-" + start + "-" + end + "." + format;
                    File out = new File(getCacheDir(), name);
                    if (format.equals("gpx")) {
                        TagApp.py(MainActivity.this).callAttr("export_gpx", out.getAbsolutePath(), deviceId, start, end);
                    } else {
                        TagApp.py(MainActivity.this).callAttr("export_csv", out.getAbsolutePath(), deviceId, start, end);
                    }
                    Uri uri = androidx.core.content.FileProvider.getUriForFile(
                            MainActivity.this, getPackageName() + ".files", out);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType(format.equals("gpx") ? "application/gpx+xml" : "text/csv");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    runOnUiThread(() -> startActivity(Intent.createChooser(share, I18n.t("Save or share ") + name)));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            I18n.t("Export failed: ") + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }

    private void toastUi(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
    }

    /** On a fresh device with automatic Drive sync on, pull history back from the default folder. */
    private void maybeRestoreFromDrive() {
        if (!TagApp.prefs(this).getBoolean(TagApp.KEY_DRIVE_AUTO, false)) return;
        new Thread(() -> {
            try {
                int got = TagApp.py(this).callAttr("drive_default_restore").toInt();
                if (got > 0) {
                    toastUi(I18n.t("Restored ") + got + I18n.t(" location(s) from your Google Drive"));
                    runOnUiThread(() -> { if (web != null) web.reload(); });
                }
            } catch (Exception ignored) {
                // Not signed in yet, or offline; ignore silently.
            }
        }).start();
    }

    private static String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 0, I18n.t(this, "Check for locations now"));
        menu.add(0, 7, 0, I18n.t(this, "Check tag batteries (Bluetooth)"));
        menu.add(0, 2, 0, I18n.t(this, "Refresh"));
        menu.add(0, 3, 0, I18n.t(this, "Setup & settings"));
        menu.add(0, 6, 0, I18n.t(this, "Phone battery & activity"));
        menu.add(0, 8, 0, I18n.de(this) ? "Language: English" : "Sprache: Deutsch");
        menu.add(0, 5, 0, I18n.t(this, "About this app"));
        menu.add(0, 4, 0, I18n.t(this, "Help"));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                PollWorker.runNow(this);
                Toast.makeText(this, I18n.t(this, "Checking Find Hub…"), Toast.LENGTH_SHORT).show();
                return true;
            case 2:
                web.reload();
                return true;
            case 3:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case 4:
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            case 5:
                startActivity(new Intent(this, HelpActivity.class)
                        .putExtra(HelpActivity.EXTRA_PAGE, "info.html"));
                return true;
            case 6:
                startActivity(new Intent(this, BatteryActivity.class));
                return true;
            case 7:
                checkBatteries();
                return true;
            case 8:
                switchLanguage(I18n.de(this) ? "en" : "de");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // App lock gate: require the app password (this phone only) once per launch.
        if (TagApp.hasAppLock(this) && !TagApp.appUnlocked) {
            startActivity(new Intent(this, LockActivity.class));
        }
        // Akkustand still im Hintergrund lesen, wenn Bluetooth-Scan schon erlaubt ist.
        if (BleBattery.hasPermissions(this)) runBatteryScan(false);
    }

    private static final int REQ_BLE = 21;

    /** Menüpunkt "Akkustand prüfen": fragt bei Bedarf nach der Berechtigung und scannt. */
    void checkBatteries() {
        if (!BleBattery.hasPermissions(this)) {
            Toast.makeText(this, I18n.t(this, "Bluetooth access is needed to read the batteries of tags near you."),
                    Toast.LENGTH_LONG).show();
            requestPermissions(BleBattery.neededPermissions(), REQ_BLE);
            return;
        }
        runBatteryScan(true);
    }

    private void runBatteryScan(boolean userAsked) {
        if (userAsked) Toast.makeText(this, I18n.t(this, "Searching for tags nearby (10 s)…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int known = 0;
            try {
                known = TagApp.py(this).callAttr("ble_known").toInt();
            } catch (Exception ignored) {
            }
            int seen = BleBattery.scanAndUpdate(this, userAsked ? 10000 : 6000, userAsked);
            final int k = known;
            runOnUiThread(() -> {
                if (seen > 0) web.evaluateJavascript("window.reloadDevices && window.reloadDevices()", null);
                if (!userAsked) return;
                String msg;
                if (seen < 0) msg = I18n.t(this, "Bluetooth is off or not allowed.");
                else if (k == 0) msg = I18n.t(this, "Run \"Check for locations now\" once first, then try again.");
                else if (seen == 0) msg = I18n.t(this, "No tag in Bluetooth range right now.");
                else if (BleBattery.lastMatched == 0) msg = seen + I18n.t(this, " Find Hub signal(s) received, but none matched one of your ")
                        + BleBattery.lastKnown + I18n.t(this, " prepared tags yet. Bring a tag right next to the phone and try again.");
                else msg = BleBattery.lastMatched + I18n.t(this, " tag(s) nearby recognised – battery updated.");
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_BLE && BleBattery.hasPermissions(this)) runBatteryScan(true);
    }

    /** Sprache umstellen und den Bildschirm neu laden. */
    void switchLanguage(String code) {
        I18n.set(this, code);
        invalidateOptionsMenu();
        web.loadUrl("https://appassets.androidplatform.net/assets/web/index.html?lang=" + I18n.code(this));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
