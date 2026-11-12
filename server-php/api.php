<?php
// Lese-Schnittstelle für die Karte (gleiche Pfade wie der Python-Server, nur lesend).
require __DIR__ . '/lib.php';
famt_ensure_data_dir();

$raw = (string)($_GET['path'] ?? '');
$parts = parse_url($raw);
$path = $parts['path'] ?? '';
$q = [];
if (!empty($parts['query'])) parse_str($parts['query'], $q);

if ($path === '/api/logout') {
    famt_session();
    $_SESSION = [];
    session_destroy();
    famt_json(['ok' => true]);
}
if (!famt_logged_in()) famt_json(['detail' => 'login required'], 401);

$meta = famt_load_json(DATA_DIR . '/meta.json', []);

if ($path === '/api/devices') {
    $devices = famt_load_json(DATA_DIR . '/devices.json', []);
    famt_json([
        'devices' => array_values($devices),
        'readonly' => true,
        'stale_hours' => $meta['stale_hours'] ?? 72,
        'poller' => ['enabled' => true, 'running' => false, 'last_run' => $meta['last_upload'] ?? null, 'interval_minutes' => null],
        'backup' => null,
    ]);
}

if (strpos($path, '/api/history/') === 0) {
    $id = rawurldecode(substr($path, strlen('/api/history/')));
    $start = (int)($q['start'] ?? 0);
    $end = (int)($q['end'] ?? PHP_INT_MAX);
    $format = (string)($q['format'] ?? '');
    $points = [];
    $file = famt_points_file($id);
    if (is_file($file)) {
        $fh = fopen($file, 'r');
        while (($line = fgets($fh)) !== false) {
            $p = json_decode($line, true);
            if (!is_array($p)) continue;
            if ($p['ts'] < $start || $p['ts'] > $end) continue;
            $points[] = $p;
        }
        fclose($fh);
    }
    usort($points, function ($a, $b) { return $a['ts'] <=> $b['ts']; });

    if ($format === 'csv' || $format === 'gpx') {
        $devices = famt_load_json(DATA_DIR . '/devices.json', []);
        $name = $devices[$id]['name'] ?? 'tag';
        $safe = preg_replace('/[^A-Za-z0-9_-]+/', '_', $name);
        if ($format === 'csv') {
            header('Content-Type: text/csv; charset=utf-8');
            header('Content-Disposition: attachment; filename="' . $safe . '.csv"');
            $out = fopen('php://output', 'w');
            fputcsv($out, ['tag', 'time_utc', 'unix', 'lat', 'lon', 'accuracy_m', 'own_report']);
            foreach ($points as $p) {
                fputcsv($out, [$name, gmdate('Y-m-d H:i:s', $p['ts']), $p['ts'], $p['lat'], $p['lon'], $p['accuracy'] ?? '', !empty($p['is_own_report']) ? 1 : 0]);
            }
            exit;
        }
        header('Content-Type: application/gpx+xml; charset=utf-8');
        header('Content-Disposition: attachment; filename="' . $safe . '.gpx"');
        echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n"
            . '<gpx version="1.1" creator="Find All My Tags" xmlns="http://www.topografix.com/GPX/1/1"><trk><name>' . famt_h($name) . '</name><trkseg>' . "\n";
        foreach ($points as $p) {
            if ($p['lat'] === null) continue;
            echo '<trkpt lat="' . (float)$p['lat'] . '" lon="' . (float)$p['lon'] . '"><time>' . gmdate('Y-m-d\TH:i:s\Z', $p['ts']) . '</time></trkpt>' . "\n";
        }
        echo "</trkseg></trk></gpx>\n";
        exit;
    }
    famt_json(['points' => $points]);
}

famt_json(['detail' => 'not available on this server'], 404);
