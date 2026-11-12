#!/bin/sh
# Baut das Server-Paket für die PC-Ansicht: dist/find-all-my-tags-server.zip
# Inhalt: PHP-Dateien + dieselbe Karte wie in der App (web/).
set -e
cd "$(dirname "$0")/.."
OUT=dist/find-all-my-tags-server
rm -rf "$OUT" "$OUT.zip"
mkdir -p "$OUT/static"
cp server-php/*.php server-php/LIESMICH.txt "$OUT/"
cp web/index.html "$OUT/map.html"
cp web/help.html web/datenschutz.html web/version.json "$OUT/"
cp -r web/static/. "$OUT/static/"
# Für den Server unnötig: App-Manifest-Verweis stört nicht, bleibt drin.
(cd dist && zip -qr find-all-my-tags-server.zip find-all-my-tags-server)
echo "dist/find-all-my-tags-server.zip"
