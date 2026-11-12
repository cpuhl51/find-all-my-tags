<?php
// Find All My Tags – Server-Paket (PC-Ansicht). Gemeinsame Hilfsfunktionen.
// Alle Daten liegen im Ordner data/ (per .htaccess vor direktem Abruf geschützt).

declare(strict_types=1);

const FAMT_VERSION = '1.4.1';
define('DATA_DIR', __DIR__ . '/data');
define('CONFIG_FILE', DATA_DIR . '/config.json');

function famt_ensure_data_dir(): void {
    if (!is_dir(DATA_DIR)) @mkdir(DATA_DIR, 0750, true);
    if (!is_dir(DATA_DIR . '/points')) @mkdir(DATA_DIR . '/points', 0750, true);
    $ht = DATA_DIR . '/.htaccess';
    if (!file_exists($ht)) {
        @file_put_contents($ht, "# Kein direkter Zugriff auf die Daten\n<IfModule mod_authz_core.c>\n  Require all denied\n</IfModule>\n<IfModule !mod_authz_core.c>\n  Order allow,deny\n  Deny from all\n</IfModule>\n");
    }
    if (!file_exists(DATA_DIR . '/index.html')) @file_put_contents(DATA_DIR . '/index.html', '');
}

function famt_config(): ?array {
    if (!is_file(CONFIG_FILE)) return null;
    $c = json_decode((string)file_get_contents(CONFIG_FILE), true);
    return is_array($c) ? $c : null;
}

function famt_save_json(string $file, $data): void {
    $tmp = $file . '.tmp';
    file_put_contents($tmp, json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), LOCK_EX);
    rename($tmp, $file);
}

function famt_load_json(string $file, $default) {
    if (!is_file($file)) return $default;
    $d = json_decode((string)file_get_contents($file), true);
    return $d === null ? $default : $d;
}

function famt_session(): void {
    if (session_status() === PHP_SESSION_ACTIVE) return;
    $secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off');
    session_name('famt');
    session_set_cookie_params(['lifetime' => 60 * 60 * 24 * 30, 'path' => '/', 'httponly' => true, 'samesite' => 'Lax', 'secure' => $secure]);
    session_start();
}

function famt_logged_in(): bool {
    famt_session();
    return !empty($_SESSION['famt_ok']);
}

function famt_json($data, int $code = 200): void {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function famt_points_file(string $deviceId): string {
    return DATA_DIR . '/points/' . sha1($deviceId) . '.jsonl';
}

function famt_h(string $s): string {
    return htmlspecialchars($s, ENT_QUOTES, 'UTF-8');
}

function famt_base_url(): string {
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $dir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
    return ($https ? 'https' : 'http') . '://' . $host . $dir . '/';
}

// Kleine Seitenhülle für Einrichtung und Anmeldung (zweisprachig über lang-Blöcke)
function famt_page(string $title, string $body): void {
    header('Content-Type: text/html; charset=utf-8');
    echo '<!doctype html><html lang="de"><head><meta charset="utf-8">'
        . '<meta name="viewport" content="width=device-width, initial-scale=1">'
        . '<title>' . famt_h($title) . '</title>'
        . '<link rel="stylesheet" href="static/style.css"><script src="static/i18n.js"></script>'
        . '<style>.famt{min-height:100vh;display:grid;place-items:center;padding:16px;background:var(--bg)}'
        . '.famt .card{width:100%;max-width:440px;background:var(--surface);border:1px solid var(--border);border-radius:18px;padding:24px;box-shadow:var(--shadow-lg);display:grid;gap:12px}'
        . '.famt code{word-break:break-all;background:var(--chip);padding:6px 8px;border-radius:8px;display:block;font-size:14px}'
        . '.famt .err{color:var(--danger);font-weight:600}.famt h1{font-size:20px;margin:0}</style>'
        . '</head><body><div class="famt"><div class="card">' . $body . '</div></div></body></html>';
    exit;
}
