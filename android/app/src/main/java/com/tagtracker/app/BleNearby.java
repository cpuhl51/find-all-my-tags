package com.tagtracker.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Erkennt eigene Tags in Bluetooth-Reichweite des Handys (Find Hub Network, Service 0xFEAA)
 * und zeigt sie als „in der Nähe“ (📶). Die Zuordnung zum Tag macht server/nearby.py.
 */
final class BleNearby {
    static final ParcelUuid FHN = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
    private static final long MIN_GAP_MS = 50 * 1000L;   // bei offener App jede Minute
    private static volatile long lastScan = 0;
    /** Wie viele eigene Tags beim letzten Scan erkannt wurden. */
    static volatile int lastMatched = 0;
    /** Wie viele eigene Tags die App per Bluetooth erkennen kann (Schlüssel bekannt). */
    static volatile int lastKnown = 0;

    private BleNearby() {}

    /** Berechtigungen, die für den Scan nötig sind (je nach Android-Version). */
    static String[] neededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Ohne "neverForLocation", sonst filtert Android genau diese Art Funkrahmen heraus.
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    static boolean hasPermissions(Context ctx) {
        for (String p : neededPermissions()) {
            if (ctx.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    /**
     * Scannt (blockierend, nicht im UI-Thread aufrufen) und merkt sich, welche Tags in der Nähe sind.
     * @param force true = Mindestabstand zwischen zwei Scans ignorieren
     * @return Anzahl gesehener Find-Hub-Funkrahmen, oder -1 wenn nicht möglich
     */
    @SuppressLint("MissingPermission")
    static int scanAndUpdate(Context ctx, long durationMs, boolean force) {
        if (!hasPermissions(ctx)) return -1;
        long now = System.currentTimeMillis();
        if (!force && now - lastScan < MIN_GAP_MS) return 0;
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ad = bm != null ? bm.getAdapter() : null;
        if (ad == null || !ad.isEnabled()) return -1;
        BluetoothLeScanner sc = ad.getBluetoothLeScanner();
        if (sc == null) return -1;
        lastScan = now;

        final Set<String> frames = Collections.synchronizedSet(new LinkedHashSet<>());
        ScanCallback cb = new ScanCallback() {
            @Override
            public void onScanResult(int type, ScanResult r) {
                ScanRecord rec = r.getScanRecord();
                if (rec == null) return;
                byte[] sd = rec.getServiceData(FHN);
                if (sd != null && sd.length >= 21 && (sd[0] == 0x40 || sd[0] == 0x41)) frames.add(hex(sd));
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult r : results) onScanResult(0, r);
            }
        };
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceData(FHN, new byte[0]).build());
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        try {
            sc.startScan(filters, settings, cb);
            Thread.sleep(durationMs);
        } catch (SecurityException | InterruptedException e) {
            return -1;
        } finally {
            try {
                sc.stopScan(cb);
            } catch (Exception ignored) {
            }
        }
        lastMatched = 0;
        if (frames.isEmpty()) return 0;
        ingest(ctx, frames, force);
        return frames.size();
    }

    /** Gesehene Funkrahmen an Python geben, das sie den eigenen Tags zuordnet. */
    static void ingest(Context ctx, Set<String> frames, boolean wide) {
        try {
            JSONArray arr = new JSONArray();
            for (String f : frames) arr.put(f);
            JSONObject res = new JSONObject(TagApp.py(ctx).callAttr("ble_ingest", arr.toString(), wide).toString());
            lastMatched = res.optInt("matched", 0);
            lastKnown = res.optInt("known", 0);
        } catch (Exception ignored) {
        }
    }

    /** Find-Hub-Rahmen aus einem Scan-Ergebnis als Hex, oder null. */
    static String frameOf(ScanResult r) {
        ScanRecord rec = r.getScanRecord();
        if (rec == null) return null;
        byte[] sd = rec.getServiceData(FHN);
        if (sd != null && sd.length >= 21 && (sd[0] == 0x40 || sd[0] == 0x41)) return hex(sd);
        return null;
    }

    // ---------- Dauerhafte Suche im Hintergrund ----------

    /** Im Hintergrund liefert Android Ergebnisse nur mit "Standort immer erlauben". */
    static boolean canScanInBackground(Context ctx) {
        if (!hasPermissions(ctx)) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private static android.app.PendingIntent bgIntent(Context ctx) {
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= android.app.PendingIntent.FLAG_MUTABLE;
        return android.app.PendingIntent.getBroadcast(ctx, 77,
                new android.content.Intent(ctx, NearbyReceiver.class), flags);
    }

    private static BluetoothLeScanner scanner(Context ctx) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ad = bm != null ? bm.getAdapter() : null;
        if (ad == null || !ad.isEnabled()) return null;
        return ad.getBluetoothLeScanner();
    }

    /**
     * Startet eine dauerhafte, stromsparende Suche, die Android selbst ausführt (auch wenn die App zu ist).
     * Treffer kommen im NearbyReceiver an. Neu starten ist unschädlich.
     */
    @SuppressLint("MissingPermission")
    static void startBackground(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !hasPermissions(ctx)) return;
        if (TagApp.prefs(ctx).getBoolean(TagApp.KEY_PAUSED, false)) return;
        BluetoothLeScanner sc = scanner(ctx);
        if (sc == null) return;
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceData(FHN, new byte[0]).build());
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(20000)   // gesammelt alle ~20 s, spart Akku
                .build();
        try {
            android.app.PendingIntent pi = bgIntent(ctx);
            sc.stopScan(pi);
            sc.startScan(filters, settings, pi);
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("MissingPermission")
    static void stopBackground(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        BluetoothLeScanner sc = scanner(ctx);
        if (sc == null) return;
        try {
            sc.stopScan(bgIntent(ctx));
        } catch (Exception ignored) {
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }
}
