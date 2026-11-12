<?php
// Empfängt Tag-Liste und neue Standorte von der App (POST, JSON, Header X-Upload-Token).
require __DIR__ . '/lib.php';
famt_ensure_data_dir();

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') famt_json(['detail' => 'POST only'], 405);
$cfg = famt_config();
if (!$cfg) famt_json(['detail' => 'server not set up yet – open setup.php in the browser'], 409);

$token = (string)($_SERVER['HTTP_X_UPLOAD_TOKEN'] ?? '');
if ($token === '' || !hash_equals((string)$cfg['upload_token'], $token)) {
    sleep(1);
    famt_json(['detail' => 'wrong upload key'], 403);
}

$body = json_decode((string)file_get_contents('php://input'), true);
if (!is_array($body)) famt_json(['detail' => 'invalid JSON'], 400);

$lock = fopen(DATA_DIR . '/.lock', 'c');
flock($lock, LOCK_EX);

// Tag-Liste: nur ersetzen, wenn sie mitgeschickt wurde
$devices = famt_load_json(DATA_DIR . '/devices.json', []);
if (!empty($body['devices']) && is_array($body['devices'])) {
    $new = [];
    foreach ($body['devices'] as $d) {
        if (!is_array($d) || !isset($d['id'])) continue;
        $id = (string)$d['id'];
        $new[$id] = [
            'id' => $id,
            'name' => mb_substr((string)($d['name'] ?? 'Tag'), 0, 80),
            'last_ts' => isset($d['last_ts']) ? (int)$d['last_ts'] : null,
            'last_lat' => isset($d['last_lat']) ? (float)$d['last_lat'] : null,
            'last_lon' => isset($d['last_lon']) ? (float)$d['last_lon'] : null,
            'last_accuracy' => isset($d['last_accuracy']) ? (float)$d['last_accuracy'] : null,
            'point_count' => (int)($d['point_count'] ?? 0),
            'battery' => (isset($d['battery']) && is_array($d['battery'])) ? [
                'level' => (int)($d['battery']['level'] ?? 0),
                'ts' => (int)($d['battery']['ts'] ?? 0),
                'seen_ts' => (int)($d['battery']['seen_ts'] ?? 0),
            ] : null,
        ];
    }
    $devices = $new;
    famt_save_json(DATA_DIR . '/devices.json', $devices);
}

// Standorte: je Tag eine Datei, doppelte Punkte (gleiche Zeit + Ort) werden übersprungen
$added = 0;
$byDevice = [];
foreach (($body['points'] ?? []) as $p) {
    if (!is_array($p) || !isset($p['device_id'], $p['ts'])) continue;
    $byDevice[(string)$p['device_id']][] = $p;
}
foreach ($byDevice as $id => $list) {
    $file = famt_points_file($id);
    $seen = [];
    if (is_file($file)) {
        $fh = fopen($file, 'r');
        while (($line = fgets($fh)) !== false) {
            $o = json_decode($line, true);
            if (is_array($o)) $seen[$o['ts'] . '|' . $o['lat'] . '|' . $o['lon']] = true;
        }
        fclose($fh);
    }
    $out = '';
    foreach ($list as $p) {
        $row = [
            'ts' => (int)$p['ts'],
            'lat' => isset($p['lat']) ? (float)$p['lat'] : null,
            'lon' => isset($p['lon']) ? (float)$p['lon'] : null,
            'accuracy' => isset($p['accuracy']) ? (float)$p['accuracy'] : null,
            'is_own_report' => !empty($p['is_own_report']),
        ];
        $k = $row['ts'] . '|' . $row['lat'] . '|' . $row['lon'];
        if (isset($seen[$k])) continue;
        $seen[$k] = true;
        $out .= json_encode($row) . "\n";
        $added++;
    }
    if ($out !== '') file_put_contents($file, $out, FILE_APPEND | LOCK_EX);
}

$meta = famt_load_json(DATA_DIR . '/meta.json', []);
$meta['last_upload'] = time();
if (isset($body['stale_hours'])) $meta['stale_hours'] = (int)$body['stale_hours'];
famt_save_json(DATA_DIR . '/meta.json', $meta);

flock($lock, LOCK_UN);
famt_json(['ok' => true, 'added' => $added, 'devices' => count($devices)]);
