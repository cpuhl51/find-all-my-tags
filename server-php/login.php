<?php
// Anmeldung für die PC-Ansicht
require __DIR__ . '/lib.php';
famt_ensure_data_dir();
$cfg = famt_config();
if (!$cfg) { header('Location: setup.php'); exit; }
famt_session();

$fail = false;
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    if (password_verify((string)($_POST['pw'] ?? ''), (string)$cfg['password_hash'])) {
        session_regenerate_id(true);
        $_SESSION['famt_ok'] = true;
        header('Location: index.php');
        exit;
    }
    sleep(1);  // bremst das Durchprobieren von Passwörtern
    $fail = true;
}
if (famt_logged_in()) { header('Location: index.php'); exit; }

famt_page('Find All My Tags', '<h1>Find All My Tags</h1>'
    . '<p class="muted" lang="de">Melde dich an, um deine Tags auf der Karte zu sehen.</p>'
    . '<p class="muted" lang="en">Sign in to see your tags on the map.</p>'
    . ($fail ? '<p class="err" lang="de">Falsches Passwort.</p><p class="err" lang="en">Wrong password.</p>' : '')
    . '<form method="post" style="display:grid;gap:10px">'
    . '<label><span lang="de">Passwort</span><span lang="en">Password</span><input type="password" name="pw" required autofocus autocomplete="current-password"></label>'
    . '<button class="primary" type="submit"><span lang="de">Anmelden</span><span lang="en">Sign in</span></button></form>'
    . '<p class="muted small" lang="de">Keine Verbindung zu Google. Unabhängig, Open Source.</p>'
    . '<p class="muted small" lang="en">Not affiliated with Google. Independent, open-source.</p>');
