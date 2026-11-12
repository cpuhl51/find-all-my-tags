<?php
// Erstmalige Einrichtung: Passwort für die PC-Ansicht festlegen, Upload-Schlüssel erzeugen.
require __DIR__ . '/lib.php';
famt_ensure_data_dir();

if (!is_dir(DATA_DIR) || !is_writable(DATA_DIR)) {
    famt_page('Find All My Tags', '<h1>Find All My Tags</h1>'
        . '<p class="err" lang="de">Der Ordner „data“ kann nicht angelegt oder beschrieben werden. Bitte im FTP-Programm Schreibrechte für diesen Ordner setzen (z. B. 750 oder 755).</p>'
        . '<p class="err" lang="en">The “data” folder cannot be created or written. Please give it write permission in your FTP program (e.g. 750 or 755).</p>');
}

$cfg = famt_config();
famt_session();

// Schon eingerichtet: Schlüssel nur nach Anmeldung erneut zeigen
if ($cfg && empty($_SESSION['famt_just_setup'])) {
    if (!famt_logged_in()) { header('Location: login.php'); exit; }
}

$error = '';
if (!$cfg && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    $pw = (string)($_POST['pw'] ?? '');
    $pw2 = (string)($_POST['pw2'] ?? '');
    if (strlen($pw) < 8) $error = 'short';
    elseif ($pw !== $pw2) $error = 'mismatch';
    else {
        $cfg = [
            'password_hash' => password_hash($pw, PASSWORD_DEFAULT),
            'upload_token' => bin2hex(random_bytes(20)),
            'created' => time(),
        ];
        famt_save_json(CONFIG_FILE, $cfg);
        session_regenerate_id(true);
        $_SESSION['famt_ok'] = true;
        $_SESSION['famt_just_setup'] = true;
    }
}

if (!$cfg) {
    $msg = $error === 'short' ? '<p class="err" lang="de">Das Passwort braucht mindestens 8 Zeichen.</p><p class="err" lang="en">The password needs at least 8 characters.</p>'
        : ($error === 'mismatch' ? '<p class="err" lang="de">Die beiden Passwörter stimmen nicht überein.</p><p class="err" lang="en">The two passwords do not match.</p>' : '');
    famt_page('Find All My Tags – Einrichtung', '<h1>Find All My Tags</h1>'
        . '<p lang="de">Willkommen! Lege ein Passwort für die PC-Ansicht fest. Damit meldest du dich später im Browser an.</p>'
        . '<p lang="en">Welcome! Choose a password for the PC view. You will use it to sign in from your browser.</p>'
        . $msg
        . '<form method="post" style="display:grid;gap:10px">'
        . '<label><span lang="de">Passwort (mind. 8 Zeichen)</span><span lang="en">Password (min. 8 characters)</span><input type="password" name="pw" required minlength="8" autocomplete="new-password"></label>'
        . '<label><span lang="de">Passwort wiederholen</span><span lang="en">Repeat password</span><input type="password" name="pw2" required minlength="8" autocomplete="new-password"></label>'
        . '<button class="primary" type="submit"><span lang="de">Einrichten</span><span lang="en">Set up</span></button></form>');
}

$url = famt_base_url();
famt_page('Find All My Tags – Einrichtung', '<h1>✓ <span lang="de">Eingerichtet</span><span lang="en">All set</span></h1>'
    . '<p lang="de">Trage in der App unter <b>Systemkonfiguration und Infos → 7. Eigener Server</b> diese zwei Werte ein:</p>'
    . '<p lang="en">In the app, open <b>System configuration &amp; info → 7. Own server</b> and enter these two values:</p>'
    . '<div><b lang="de">Server-Adresse</b><b lang="en">Server address</b><code>' . famt_h($url) . '</code></div>'
    . '<div><b lang="de">Upload-Schlüssel</b><b lang="en">Upload key</b><code>' . famt_h($cfg['upload_token']) . '</code></div>'
    . '<p class="muted small" lang="de">Den Schlüssel siehst du hier jederzeit wieder, wenn du angemeldet bist. Gib ihn niemandem weiter – wer ihn hat, kann Standorte auf deinen Server laden.</p>'
    . '<p class="muted small" lang="en">You can see the key here again any time while signed in. Keep it private – anyone with it can upload locations to your server.</p>'
    . '<a href="index.php"><button class="primary" type="button" style="width:100%"><span lang="de">Zur Karte</span><span lang="en">Open map</span></button></a>');
