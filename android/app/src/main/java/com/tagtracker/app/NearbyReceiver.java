package com.tagtracker.app;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Empfängt die Treffer der dauerhaften Bluetooth-Suche (BleNearby.startBackground).
 * Höchstens einmal pro Minute wird zugeordnet, damit der Akku geschont wird.
 */
public class NearbyReceiver extends BroadcastReceiver {
    private static final long GAP_MS = 60 * 1000L;
    private static final Set<String> buffer = new LinkedHashSet<>();
    private static long lastIngest = 0;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        ArrayList<ScanResult> list;
        try {
            list = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
        } catch (Exception e) {
            return;
        }
        if (list == null || list.isEmpty()) return;
        final Set<String> todo;
        synchronized (buffer) {
            for (ScanResult r : list) {
                String f = BleNearby.frameOf(r);
                if (f != null && buffer.size() < 200) buffer.add(f);
            }
            long now = System.currentTimeMillis();
            if (buffer.isEmpty() || now - lastIngest < GAP_MS) return;
            lastIngest = now;
            todo = new LinkedHashSet<>(buffer);
            buffer.clear();
        }
        final Context app = ctx.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                BleNearby.ingest(app, todo, false);
            } finally {
                pr.finish();
            }
        }).start();
    }
}
