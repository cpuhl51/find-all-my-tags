<?php
// PC-Ansicht: dieselbe Karte wie in der App, nur lesend.
require __DIR__ . '/lib.php';
famt_ensure_data_dir();
if (!famt_config()) { header('Location: setup.php'); exit; }
if (!famt_logged_in()) { header('Location: login.php'); exit; }

$html = (string)file_get_contents(__DIR__ . '/map.html');
$inject = '<script>window.API_PATH = function (p) { return "api.php?path=" + encodeURIComponent(p); };'
    . 'window.LOGIN_URL = "login.php";</script>';
$html = str_replace('</head>', $inject . "\n</head>", $html);
header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: no-store');
echo $html;
