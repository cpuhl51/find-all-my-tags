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
LEVELS = {0: "unknown", 1: "normal", 2: "low", 3: "critical"}

_BEACONS = DATA_DIR / "beacons.json"
_STATE = DATA_DIR / "battery.json"
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

def remember(device_id: str, identity_key: bytes, anchor_ts: int, beacon_counter: int) -> None:
    """anchor_ts = Unix-Zeit des Berichts, beacon_counter = Uhrstand des Tags zu dieser Zeit."""
    if not identity_key or beacon_counter <= 0:
        return
    with _lock:
        data = _load(_BEACONS)
        old = data.get(device_id, {})
        if old.get("ts", 0) > anchor_ts and old.get("ik") == identity_key.hex():
            return  # wir haben schon einen neueren Anker
        data[device_id] = {"ik": identity_key.hex(), "ts": int(anchor_ts), "counter": int(beacon_counter)}
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
    if len(_eid_cache) > 2000:
        _eid_cache.clear()
    _eid_cache[key] = (eid, r)
    return eid, r


def _candidates(now: int) -> dict[bytes, tuple[str, int]]:
    """EID -> (device_id, r) für alle bekannten Tags rund um die geschätzte Tag-Uhr."""
    out = {}
    for device_id, b in _load(_BEACONS).items():
        est = b["counter"] + (now - b["ts"])
        base = est - est % ROTATION
        for k in range(-WINDOWS, WINDOWS + 1):
            w = base + k * ROTATION
            if w < 0:
                continue
            eid, r = _eid_and_r(b["ik"], w)
            out[eid] = (device_id, r)
    return out


def decode_level(hashed_flags: int, r: int) -> int:
    mask = hashlib.sha256(r.to_bytes(20, "big")).digest()[-1]
    return ((hashed_flags ^ mask) >> 5) & 0b11


def ingest(frames_hex: list[str], now: int | None = None) -> list[dict]:
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
    if not frames:
        return []
    cands = _candidates(now)
    if not cands:
        return []
    alerts = []
    matched = set()
    with _lock:
        state = _load(_STATE)
        for b in frames:
            hit = cands.get(b[1:21])
            if not hit:
                continue
            device_id, r = hit
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
    return alerts


def state() -> dict:
    """device_id -> {"level": 0..3, "ts": Zeit der Akku-Messung, "seen_ts": zuletzt per Bluetooth gesehen}"""
    return _load(_STATE)


def known_count() -> int:
    return len(_load(_BEACONS))
