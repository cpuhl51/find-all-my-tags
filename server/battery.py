"""Akkustand der Tags über Bluetooth (Find Hub Network, "hashed flags").

Jeder Tag sendet laufend einen Funkrahmen (Service-Daten 0xFEAA):
    Byte 0      Rahmentyp 0x40 (normal) / 0x41 (Schutzmodus)
    Byte 1..20  EID, wechselt alle 1024 s
    Byte 21     "hashed flags" = flags XOR (letztes Byte von SHA256(r))
In den Flags stehen die Bits 5-6 für den Akku:
    0 = keine Angabe, 1 = normal, 2 = niedrig, 3 = kritisch
Quelle: Google Find Hub Network Accessory Specification.

Um einen Rahmen einem Tag zuzuordnen, braucht es den Schlüssel des Tags
(identity key) und dessen interne Uhr. Beides fällt bei jeder Standortabfrage
an: Google liefert zu jedem Bericht den Uhrstand des Tags (deviceTimeOffset).
Wir merken uns den letzten Uhrstand mit der Zeit dazu und rechnen hoch.

Alles bleibt auf dem Gerät (DATA_DIR), genau wie die übrigen Google-Schlüssel.
"""
from __future__ import annotations

import hashlib
import json
import threading
import time

from .config import DATA_DIR

ROTATION = 1024          # EID-Wechsel alle 2^10 Sekunden
WINDOWS = 3              # so viele Fenster vor/nach der Schätzung prüfen (Uhr-Abweichung)
WIDE_WINDOWS = 48        # Suche um "jetzt minus Kopplungsdatum", wenn kein Uhrstand bekannt ist (±13,6 h)
MANUAL_WINDOWS = 252     # beim Knopf "Akku prüfen": ±3 Tage
_DIAG = None             # wird unten gesetzt (Pfad der Diagnose-Datei)
LEVELS = {0: "unknown", 1: "normal", 2: "low", 3: "critical"}

_BEACONS = DATA_DIR / "beacons.json"
_STATE = DATA_DIR / "battery.json"
_DIAG = DATA_DIR / "battery_diag.json"
_lock = threading.Lock()
_eid_cache: dict[tuple[str, int], tuple[bytes, int]] = {}
last_matched = 0   # wie viele Tags beim letzten Scan erkannt wurden


def _load(path) -> dict:
    try:
        return json.loads(path.read_text())
    except Exception:
        return {}


def _save(path, data: dict) -> None:
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data))
    tmp.replace(path)


# ---------- beim Standortabruf: Schlüssel und Uhrstand merken ----------

def remember(device_id: str, identity_key: bytes, anchor_ts: int, beacon_counter: int, pair_date: int = 0) -> None:
    """anchor_ts = Unix-Zeit des Berichts, beacon_counter = Uhrstand des Tags zu dieser Zeit.
    pair_date = Kopplungsdatum (Ersatz, wenn Google keinen Uhrstand mitliefert, z. B. bei eigenen Berichten)."""
    if not identity_key:
        return
    with _lock:
        data = _load(_BEACONS)
        old = data.get(device_id, {})
        same_key = old.get("ik") == identity_key.hex()
        entry = dict(old) if same_key else {}
        entry["ik"] = identity_key.hex()
        if 1_400_000_000 < int(pair_date or 0) < time.time() + 86400:
            entry["pair"] = int(pair_date)
        if beacon_counter > 0 and not entry.get("exact") and anchor_ts >= entry.get("ts", 0):
            entry["ts"], entry["counter"] = int(anchor_ts), int(beacon_counter)
        if "counter" not in entry and "pair" not in entry:
            return
        data[device_id] = entry
        _save(_BEACONS, data)


# ---------- beim Bluetooth-Scan: Rahmen zuordnen und Akku lesen ----------

def _eid_and_r(ik_hex: str, window_start: int) -> tuple[bytes, int]:
    key = (ik_hex, window_start)
    hit = _eid_cache.get(key)
    if hit:
        return hit
    from FMDNCrypto.eid_generator import calculate_r
    from ecdsa import SECP160r1
    r = calculate_r(bytes.fromhex(ik_hex), window_start)
    eid = (r * SECP160r1.generator).x().to_bytes(20, "big")
    if len(_eid_cache) > 30000:
        _eid_cache.clear()
    _eid_cache[key] = (eid, r)
    return eid, r


def _candidates(now: int, wide: bool = False) -> dict[bytes, tuple[str, int, int]]:
    """EID -> (device_id, r, Fensterbeginn) für alle bekannten Tags rund um die geschätzte Tag-Uhr."""
    out = {}
    for device_id, b in _load(_BEACONS).items():
        guesses = []
        if b.get("counter"):
            guesses.append((b["counter"] + (now - b["ts"]), WINDOWS if b.get("exact") or not wide else 24))
        if b.get("pair") and not b.get("exact"):
            guesses.append((now - b["pair"], MANUAL_WINDOWS if wide else WIDE_WINDOWS))
        for est, span in guesses:
            base = est - est % ROTATION
            for k in range(-span, span + 1):
                w = base + k * ROTATION
                if w < 0:
                    continue
                eid, r = _eid_and_r(b["ik"], w)
                out[eid] = (device_id, r, w)
    return out


def _calibrate(device_id: str, window: int, now: int) -> None:
    """Passendes Signal gefunden: genauen Uhrstand merken, künftig nur noch eng suchen."""
    data = _load(_BEACONS)
    if device_id in data:
        data[device_id].update(ts=now, counter=window + ROTATION // 2, exact=True)
        _save(_BEACONS, data)


def decode_level(hashed_flags: int, r: int) -> int:
    mask = hashlib.sha256(r.to_bytes(20, "big")).digest()[-1]
    return ((hashed_flags ^ mask) >> 5) & 0b11


def ingest(frames_hex: list[str], now: int | None = None, wide: bool = False) -> list[dict]:
    """Nimmt die beim Scan gesehenen Service-Daten (Hex) entgegen.
    Speichert den Akkustand je Tag und gibt die Tags zurück, deren Akku
    gerade auf "niedrig" oder "kritisch" gefallen ist (für die Benachrichtigung)."""
    global last_matched
    last_matched = 0
    now = int(now or time.time())
    frames = []
    for h in frames_hex or []:
        try:
            b = bytes.fromhex(h)
        except ValueError:
            continue
        if len(b) >= 21 and b[0] in (0x40, 0x41):
            frames.append(b)
    raw = []
    for h in frames_hex or []:
        try:
            raw.append(bytes.fromhex(h))
        except ValueError:
            pass
    cands = _candidates(now, wide) if frames else {}
    _write_diag(now, raw, cands, wide)
    if not frames or not cands:
        return []
    alerts = []
    matched = set()
    with _lock:
        state = _load(_STATE)
        for b in frames:
            hit = cands.get(b[1:21])
            if not hit:
                continue
            device_id, r, window = hit
            if device_id not in matched:
                _calibrate(device_id, window, now)
            matched.add(device_id)
            level = decode_level(b[21], r) if len(b) >= 22 else 0
            prev = state.get(device_id, {})
            state[device_id] = {"level": level, "ts": now, "seen_ts": now}
            if level == 0 and prev.get("level"):
                # Tag gesehen, aber ohne Akku-Angabe: letzten bekannten Wert behalten
                state[device_id].update(level=prev["level"], ts=prev.get("ts", now))
            if level >= 2 and level > prev.get("level", 0):
                alerts.append({"id": device_id, "level": level})
        _save(_STATE, state)
    last_matched = len(matched)
    try:
        d = _load(_DIAG); d["matched"] = sorted(matched); _save(_DIAG, d)
    except Exception:
        pass
    return alerts


def _write_diag(now: int, raw: list, cands: dict, wide: bool) -> None:
    """Merkt sich den letzten Scan, damit man ohne Raten sieht, warum nichts zugeordnet wurde."""
    try:
        beacons = _load(_BEACONS)
        tags = {}
        for device_id, b in beacons.items():
            tags[device_id] = {
                "counter_anchor": bool(b.get("counter")), "exact": bool(b.get("exact")),
                "pair": b.get("pair"),
                "est_from_counter": (b["counter"] + now - b["ts"]) if b.get("counter") else None,
                "est_from_pair": (now - b["pair"]) if b.get("pair") else None,
            }
        _save(_DIAG, {
            "ts": now, "wide": wide, "candidates": len(cands),
            "frames": [{"type": f"{x[0]:02x}" if x else "", "len": len(x), "eid": x[1:9].hex()} for x in raw[:40]],
            "tags": tags, "matched": [],
        })
    except Exception:
        pass


def diag() -> dict:
    return _load(_DIAG)


def state() -> dict:
    """device_id -> {"level": 0..3, "ts": Zeit der Akku-Messung, "seen_ts": zuletzt per Bluetooth gesehen}"""
    return _load(_STATE)


def known_count() -> int:
    return len(_load(_BEACONS))


def summary() -> dict:
    """Für die Meldung nach dem Scan: wie viele Tags vorbereitet / schon genau bekannt sind."""
    data = _load(_BEACONS)
    return {"known": len(data), "exact": sum(1 for b in data.values() if b.get("exact")),
            "pair_only": sum(1 for b in data.values() if not b.get("counter"))}
