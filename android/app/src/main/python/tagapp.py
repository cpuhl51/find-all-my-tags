"""Python side of the standalone Android app.

Reuses the server package (db, finder, poller) that is bundled into the APK at build
time. Java calls these functions through Chaquopy; everything returns plain strings.
"""
import json
import os
import time
import traceback

_ready = False


def init(files_dir: str):
    global _ready
    if _ready:
        return
    os.environ["TT_DATA_DIR"] = files_dir
    os.environ["TT_POLLING"] = "0"
    import certifi
    os.environ.setdefault("SSL_CERT_FILE", certifi.where())
    from server import db
    db.init()
    _ready = True


def _status_path():
    from server.config import DATA_DIR
    return DATA_DIR / "app_status.json"


def _load_status() -> dict:
    try:
        return json.loads(_status_path().read_text())
    except Exception:
        return {}


def _save_status(**kw):
    s = _load_status()
    s.update(kw)
    _status_path().write_text(json.dumps(s))


# ---------- account ----------

def account_json() -> str:
    from server import finder
    try:
        st = finder.auth_state()
    except Exception as e:
        return json.dumps({"connected": False, "signed_in": False, "unlocked": False,
                           "email": "", "error": str(e)})
    st["connected"] = st["signed_in"] and st["unlocked"]
    return json.dumps(st)


def sign_in(oauth_token: str) -> str:
    from server import finder
    return finder.sign_in_with_oauth_token(oauth_token)


def shared_key_url() -> str:
    from server import finder
    return finder.shared_key_url()


def import_secrets(path: str) -> str:
    from server import finder
    return json.dumps(finder.import_secrets_file(path))


def save_vault_keys(vault_keys: str):
    from server import finder
    finder.save_vault_keys(vault_keys)


def list_tags_json() -> str:
    from server import db, finder
    tags = finder.list_trackers()
    for name, cid in tags:
        db.upsert_device(cid, name)
    return json.dumps([{"name": n, "id": i} for n, i in tags])


# ---------- polling ----------

def _log_check(duration_ms: int, added: int, ok: bool):
    """Keep a short rolling log of background checks so users can SEE the battery activity."""
    s = _load_status()
    log = s.get("check_log", [])
    log.append({"ts": int(time.time()), "ms": int(duration_ms), "added": int(added), "ok": bool(ok)})
    _save_status(check_log=log[-60:])  # keep last ~60 checks


def battery_log() -> str:
    """Returns the check history + simple stats for the Battery/Activity screen."""
    s = _load_status()
    log = s.get("check_log", [])
    now = int(time.time())
    day = [c for c in log if now - c["ts"] < 86400]
    avg_ms = round(sum(c["ms"] for c in log) / len(log)) if log else 0
    active_s_day = round(sum(c["ms"] for c in day) / 1000.0, 1)
    return json.dumps({
        "checks": list(reversed(log))[:40],
        "checks_today": len(day),
        "avg_ms": avg_ms,
        "active_seconds_today": active_s_day,  # total CPU time spent checking today
        "interval_note": "No GPS. Each check wakes for a few seconds, then sleeps.",
    })


def poll() -> int:
    """Runs one check of all tags. Returns number of new locations."""
    from server import finder, poller
    _save_status(running=True)
    _t0 = time.time()
    added = 0
    ok = True
    try:
        added = poller.poll_once()
        _save_status(running=False, last_run=int(time.time()), last_error=poller.status["last_error"],
                     last_added=added)
        return added
    except BaseException as e:  # SystemExit from the library must not kill the app
        ok = False
        traceback.print_exc()
        _save_status(running=False, last_run=int(time.time()), last_error=str(e) or type(e).__name__)
        return 0
    finally:
        try:
            _log_check((time.time() - _t0) * 1000, added, ok)
        except Exception:
            pass
        # Auto-sync new points to the default Drive folder (fresh token minted each time,
        # so it keeps working 24/7 with no re-login). Best-effort; never fails the poll.
        try:
            if _drive_default_on() and _load_status().get("last_added"):
                drive_default_upload()
        except Exception as e:
            _save_status(drive_default_error=str(e))
        # Eigener Server (PC-Ansicht): neue Punkte hochladen. Zusatz, darf die Abfrage nie stören.
        try:
            if server_info()["configured"]:
                server_upload()
        except Exception as e:
            _save_status(server_error=str(e) or type(e).__name__)
        # Close the network connection so the app uses ~no battery until the next check.
        try:
            finder.stop_listening()
        except Exception:
            pass


def _haversine_m(a_lat, a_lon, b_lat, b_lon) -> float:
    import math
    r = 6371000.0
    p = math.pi / 180
    dlat = (b_lat - a_lat) * p
    dlon = (b_lon - a_lon) * p
    h = (math.sin(dlat / 2) ** 2
         + math.cos(a_lat * p) * math.cos(b_lat * p) * math.sin(dlon / 2) ** 2)
    return 2 * r * math.asin(math.sqrt(h))


def guard_on() -> bool:
    return _load_status().get("guard", False)


def set_guard(on: bool):
    _save_status(guard=bool(on))


def new_movements(threshold_m: float = 150) -> str:
    """Theft/guard alerts: devices whose newest saved point is more than threshold metres
    from the point before it (i.e. it MOVED). Each moving point is reported only once.
    threshold is above typical GPS jitter so a parked tag doesn't false-alarm."""
    from server import db
    s = _load_status()
    seen = s.get("guard_seen", {})
    out = []
    for d in db.get_devices():
        pts = [p for p in db.get_history(d["id"], 0, 2 ** 40) if p["lat"] is not None]
        if len(pts) < 2:
            continue
        a, b = pts[-2], pts[-1]
        if b["ts"] <= seen.get(d["id"], 0):
            continue  # already evaluated this point
        seen[d["id"]] = b["ts"]
        dm = _haversine_m(a["lat"], a["lon"], b["lat"], b["lon"])
        if dm >= threshold_m:
            out.append({"name": d["name"], "id": d["id"], "lat": b["lat"], "lon": b["lon"],
                        "moved_m": round(dm), "ts": b["ts"]})
    _save_status(guard_seen=seen)
    return json.dumps(out)


def _drive_default_on() -> bool:
    return _load_status().get("drive_default", False)


def set_drive_default(on: bool):
    _save_status(drive_default=bool(on))


def locate_now(device_id: str) -> int:
    """Fetches this one tag's current location right now and saves it. Returns new-point count.
    Raises a clear error if step 2 (encryption unlock) hasn't been done."""
    from server import db, finder
    st = finder.auth_state()
    if not st["signed_in"]:
        raise RuntimeError("Not signed in yet. Do step 1 in Setup.")
    if not st["unlocked"]:
        raise RuntimeError("Encryption is locked. Do step 2 (Unlock encryption keys) in Setup to see locations.")
    added = db.insert_locations(device_id, finder.locate(device_id))
    return added


def play_sound(device_id: str):
    from server import finder
    if not finder.auth_state()["signed_in"]:
        raise RuntimeError("Not signed in yet.")
    finder.play_sound(device_id, True)


def stop_sound(device_id: str):
    from server import finder
    finder.play_sound(device_id, False)


def _month_bounds(ym: str):
    import calendar
    y, m = int(ym[:4]), int(ym[5:7])
    start = int(time.mktime((y, m, 1, 0, 0, 0, 0, 0, -1)))
    ny, nm = (y + 1, 1) if m == 12 else (y, m + 1)
    return start, int(time.mktime((ny, nm, 1, 0, 0, 0, 0, 0, -1))) - 1


# ~1000 rows per chunk keeps each gzipped file well under 100 KB (~10-20 KB on the wire).
CHUNK_ROWS = 1000


def drive_default_upload() -> str:
    """Minimal-bandwidth sync: history is split into fixed ~100 KB chunks (chunk-00000.csv.gz …)
    under TagTracker/history/. Rows are only appended, so a filled chunk never changes — each
    sync re-uploads ONLY the last, growing chunk (gzipped, ~10-20 KB). Nothing else is re-sent."""
    from server import db, drive_sync, finder
    from server.config import DATA_DIR
    if not finder.auth_state()["signed_in"]:
        raise RuntimeError("Not signed in to Google yet. Do Setup → Sign in first.")
    try:
        token = drive_sync.token_from_login()
    except Exception as e:
        raise RuntimeError("Couldn't get Google Drive access from the sign-in. Use the CUSTOM Drive file "
                           "option in Setup instead (it's reliable). Details: " + str(e))
    client = drive_sync.DriveClient(token)
    try:
        folder = client.history_folder()
    except Exception as e:
        raise RuntimeError("Google Drive rejected access (the unofficial login may not grant Drive). "
                           "Use the CUSTOM Drive file option in Setup instead. Details: " + str(e))

    # Free tier: back up only the last 1 day, as one small file. Pro: full chunked history.
    if not license_state()["licensed"]:
        floor = int(time.time()) - 24 * 3600
        tmp = str(DATA_DIR / "recent-1day.csv.gz")
        db.export_csv(tmp, None, floor, 2 ** 40, gz=True)
        client.upload(tmp, "recent-1day.csv.gz", folder, content_type="application/gzip")
        _save_status(drive_default_last=int(time.time()), drive_default_error=None)
        return "synced last 1 day (free)"

    total = db.location_count()
    nchunks = max(1, (total + CHUNK_ROWS - 1) // CHUNK_ROWS)
    manifest = _load_status().get("drive_chunks", {})
    tmp = str(DATA_DIR / "chunk.csv.gz")
    changed = 0
    for i in range(nchunks):
        rows = min(CHUNK_ROWS, total - i * CHUNK_ROWS)
        if manifest.get(str(i)) == rows:
            continue  # sealed, unchanged -> skip (no upload)
        db.export_chunk(tmp, i * CHUNK_ROWS, CHUNK_ROWS, gz=True)
        client.upload(tmp, f"chunk-{i:05d}.csv.gz", folder, content_type="application/gzip")
        manifest[str(i)] = rows
        changed += 1
    _save_status(drive_chunks=manifest, drive_default_last=int(time.time()), drive_default_error=None)
    return f"synced {changed} chunk(s)"


def drive_default_restore() -> int:
    """Cross-device retrieve, backward-compatible: scans the WHOLE TagTracker folder tree and
    imports EVERY CSV it finds — new gzipped chunks, old monthly files, the original
    history.csv, or any hand-made CSV. All merged safely (duplicates are ignored)."""
    from server import db, drive_sync, finder
    from server.config import DATA_DIR
    if not finder.auth_state()["signed_in"]:
        return 0
    client = drive_sync.DriveClient(drive_sync.token_from_login())
    root = client.ensure_folder("TagTracker")
    tmp = str(DATA_DIR / "restore-file")
    added = 0
    seen = set()

    def scan(folder_id):
        nonlocal added
        if folder_id in seen:
            return
        seen.add(folder_id)
        for f in client.list_children(folder_id):
            name = (f.get("name") or "").lower()
            if f.get("mimeType") == drive_sync.FOLDER_MIME:
                scan(f["id"])  # recurse into history/, csv/, etc.
            elif name.endswith(".csv.gz") or name.endswith(".gz"):
                client.download_id(f["id"], tmp)
                try:
                    added += db.import_gz(tmp)
                except Exception:
                    pass
            elif name.endswith(".csv"):
                client.download_id(f["id"], tmp)
                try:
                    added += db.import_csv(tmp)
                except Exception:
                    pass

    scan(root)
    return added


def record_backup(error: str | None):
    _save_status(backup_last_run=int(time.time()), backup_error=error)


# ---------- data for the map UI (same shapes as the web server's API) ----------

def set_phone_id(phone_id: str):
    _save_status(phone_id=phone_id)


def license_state(phone_id: str = "") -> dict:
    # Find All My Tags: keine Lizenzsperre. Alle Tags, voller Verlauf, alle Funktionen frei.
    return {"licensed": True, "tags": 10 ** 6, "free_hours": 0, "reason": "", "expiry": 0}


def set_license(key: str, phone_id: str) -> str:
    from server import license as lic
    key = (key or "").strip()
    now = int(time.time())
    r = lic.verify(key, phone_id, now)  # start the clock at 'now' for duration keys
    if r["licensed"]:
        s = _load_status()
        # Re-entering the SAME key keeps its original activation (doesn't restart the clock).
        activated = s.get("license_activated", 0) if s.get("license_key") == key and s.get("license_activated") else now
        _save_status(license_key=key, license_activated=activated)
        r = lic.verify(key, phone_id, activated)
    return json.dumps(r)


def clear_license():
    _save_status(license_key=None, license_activated=0)


def devices_json(interval_minutes: int, backup_enabled: bool, parked: bool = False, phone_id: str = "") -> str:
    from server import db, license as lic
    s = _load_status()
    info = license_state(phone_id)
    devices = db.get_devices()
    try:
        from server import battery
        bstate = battery.state()
    except Exception:
        bstate = {}
    for d in devices:
        b = bstate.get(d["id"])
        d["battery"] = b if b else None
    return json.dumps({
        "devices": devices,
        "stale_hours": stale_hours(),
        "server_url": server_info()["url"],
        "license": info,
        "guard": bool(parked),
        "poller": {"enabled": True, "running": bool(s.get("running")), "last_run": s.get("last_run"),
                   "last_error": s.get("last_error"), "interval_minutes": interval_minutes},
        "backup": {
            "enabled": backup_enabled or bool(s.get("drive_default")),
            "mode": "default" if s.get("drive_default") else ("custom" if backup_enabled else None),
            "last_run": s.get("drive_default_last") or s.get("backup_last_run"),
            "last_error": s.get("drive_default_error") or s.get("backup_error"),
        },
    })


def history_json(device_id: str, start: int, end: int, phone_id: str = "") -> str:
    from server import db, license as lic
    info = license_state(phone_id)
    if not info["licensed"]:
        # Free tier: only the last 24h of history.
        floor = int(time.time()) - lic.FREE_HISTORY_HOURS * 3600
        start = max(int(start), floor)
    return json.dumps({"points": db.get_history(device_id, int(start), int(end)), "licensed": info["licensed"]})


def rename(device_id: str, name: str):
    from server import db
    db.rename_device(device_id, name.strip() or "Tag", None)


def export_csv(path: str, device_id: str | None = None, start: int = 0, end: int = 2**40) -> int:
    from server import db
    return db.export_csv(path, device_id or None, int(start), int(end))


def export_gpx(path: str, device_id: str, start: int, end: int) -> int:
    from server import db
    pts = [p for p in db.get_history(device_id, int(start), int(end)) if p["lat"] is not None]
    body = "".join(
        f'<trkpt lat="{p["lat"]}" lon="{p["lon"]}"><time>'
        f'{time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(p["ts"]))}</time></trkpt>' for p in pts)
    with open(path, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="Tag Tracker" '
                'xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>' + body + "</trkseg></trk></gpx>")
    return len(pts)


def import_csv(path: str) -> int:
    from server import db
    return db.import_csv(path)


def demo():
    from server import manage
    manage.demo()


# ---------- Akkustand per Bluetooth ----------

def ble_ingest(frames_json: str) -> str:
    """frames_json = JSON-Liste der beim Scan gesehenen 0xFEAA-Service-Daten (Hex).
    Gibt JSON-Liste der Tags zurück, deren Akku neu auf niedrig/kritisch gefallen ist."""
    from server import battery, db
    frames = json.loads(frames_json or "[]")
    alerts = battery.ingest(frames)
    names = {d["id"]: d["name"] for d in db.get_devices()}
    for a in alerts:
        a["name"] = names.get(a["id"], "Tag")
    _save_status(ble_last_scan=int(time.time()))
    return json.dumps({"alerts": alerts, "matched": battery.last_matched, "frames": len(frames)})


def ble_known() -> int:
    """Wie viele Tags wir per Bluetooth erkennen können (nach dem ersten Standortabruf)."""
    from server import battery
    return battery.known_count()


# ---------- Warnung "lange nicht gesehen" ----------

def stale_hours() -> int:
    """0 = aus. Standard 72 Stunden (3 Tage)."""
    v = _load_status().get("stale_hours")
    return 72 if v is None else int(v)


def set_stale_hours(hours: int):
    _save_status(stale_hours=max(0, int(hours)))


def stale_alerts() -> str:
    """Tags, die NEU länger als die Schwelle nicht gesehen wurden (je Tag nur einmal,
    bis er wieder auftaucht). JSON-Liste [{id, name, hours}]."""
    from server import db
    h = stale_hours()
    if h <= 0:
        return "[]"
    now = int(time.time())
    s = _load_status()
    told = s.get("stale_told", {})
    out = []
    try:
        from server import battery
        seen = {k: v.get("seen_ts") or 0 for k, v in battery.state().items()}
    except Exception:
        seen = {}
    for d in db.get_devices():
        ts = max(d.get("last_ts") or 0, seen.get(d["id"], 0)) or None   # auch per Bluetooth gesehen zählt
        if ts is None:
            continue
        if now - ts > h * 3600:
            if told.get(d["id"]) != ts:      # für genau diesen letzten Standort noch nicht gemeldet
                told[d["id"]] = ts
                out.append({"id": d["id"], "name": d["name"], "hours": round((now - ts) / 3600)})
        else:
            told.pop(d["id"], None)          # wieder gesehen: später erneut warnen dürfen
    _save_status(stale_told=told)
    return json.dumps(out)


# ---------- Eigener Server (PC-Ansicht) ----------

def _norm_url(url: str) -> str:
    url = (url or "").strip()
    if url and not url.lower().startswith(("http://", "https://")):
        url = "https://" + url
    if url and not url.endswith("/"):
        url += "/"
    return url


def server_info() -> dict:
    s = _load_status()
    url = s.get("server_url") or ""
    return {"configured": bool(url and s.get("server_token")), "url": url,
            "last": s.get("server_last"), "error": s.get("server_error")}


def server_info_json() -> str:
    return json.dumps(server_info())


def set_server(url: str, token: str) -> str:
    """Leere Adresse = ausschalten. Prüft die Verbindung sofort."""
    url = _norm_url(url)
    token = (token or "").strip()
    if not url:
        _save_status(server_url=None, server_token=None, server_last_id=0, server_error=None, server_last=None)
        return "off"
    old = _load_status()
    prev = {k: old.get(k) for k in ("server_url", "server_token", "server_last_id")}
    _save_status(server_url=url, server_token=token, server_last_id=0, server_error=None)
    try:
        return server_upload()
    except Exception:
        # Neue Adresse klappt nicht: vorherige (funktionierende) Einrichtung wiederherstellen
        _save_status(**prev)
        raise


def server_upload() -> str:
    """Schickt Tag-Liste (mit Akku) und alle Punkte seit dem letzten Upload an upload.php."""
    import requests
    from server import db
    s = _load_status()
    url, token = s.get("server_url"), s.get("server_token")
    if not url or not token:
        raise RuntimeError("Kein Server eingetragen.")
    try:
        from server import battery
        bstate = battery.state()
    except Exception:
        bstate = {}
    devices = []
    for d in db.get_devices():
        devices.append({k: d.get(k) for k in ("id", "name", "last_ts", "last_lat", "last_lon", "last_accuracy", "point_count")}
                       | {"battery": bstate.get(d["id"])})
    last_id = int(s.get("server_last_id") or 0)
    sent = 0
    while True:
        with db.connect() as c:
            rows = [dict(r) for r in c.execute(
                "SELECT id, device_id, ts, lat, lon, altitude, accuracy, is_own_report, semantic_name "
                "FROM locations WHERE id > ? ORDER BY id LIMIT 2000", (last_id,)).fetchall()]
        body = {"devices": devices, "points": rows, "stale_hours": stale_hours()}
        r = requests.post(url + "upload.php", json=body, timeout=30,
                          headers={"X-Upload-Token": token, "User-Agent": "FindAllMyTags"})
        if r.status_code == 403:
            raise RuntimeError("Server lehnt den Upload-Schlüssel ab (403).")
        if r.status_code != 200:
            raise RuntimeError(f"Server antwortet mit Fehler {r.status_code}.")
        if rows:
            last_id = rows[-1]["id"]
            sent += len(rows)
            _save_status(server_last_id=last_id)
        devices = []          # Tag-Liste nur beim ersten Paket mitschicken
        if len(rows) < 2000:
            break
    _save_status(server_last=int(time.time()), server_error=None)
    return f"ok:{sent}"
