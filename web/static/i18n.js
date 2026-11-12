/* Find All My Tags – Deutsch/Englisch.
 * Quelltext ist Englisch. t("...") liefert Deutsch, wenn Deutsch gewählt ist.
 * Sprache: ?lang=de|en (setzt die App), sonst gespeicherte Wahl, sonst Browsersprache.
 * Statische Texte im HTML werden automatisch übersetzt (Textknoten, title, placeholder).
 * Längere Texte stehen im HTML doppelt: <div lang="de">…</div><div lang="en">…</div>.
 */
(() => {
  const qs = new URLSearchParams(location.search).get('lang');
  let saved = null;
  try { saved = localStorage.getItem('lang'); } catch (e) {}
  const pick = (v) => (v === 'de' || v === 'en' ? v : null);
  const LANG = pick(qs) || pick(saved) || ((navigator.language || '').toLowerCase().startsWith('de') ? 'de' : 'en');
  if (pick(qs)) { try { localStorage.setItem('lang', qs); } catch (e) {} }
  document.documentElement.lang = LANG;
  document.documentElement.dataset.lang = LANG;

  // ---------- Hell/Dunkel: Oberfläche und Karte getrennt (auto | light | dark) ----------
  const getPref = (k) => { try { const v = localStorage.getItem(k); return v === 'light' || v === 'dark' ? v : 'auto'; } catch (e) { return 'auto'; } };
  const mq = window.matchMedia ? matchMedia('(prefers-color-scheme: dark)') : null;
  function applyTheme() {
    const sys = mq && mq.matches ? 'dark' : 'light';
    const ui = getPref('theme'), mp = getPref('mapTheme');
    const eff = ui === 'auto' ? sys : ui;
    document.documentElement.dataset.theme = eff;
    document.documentElement.dataset.map = mp === 'auto' ? eff : mp;
  }
  function setTheme(key, v) { try { localStorage.setItem(key, v); } catch (e) {} applyTheme(); }
  applyTheme();
  if (mq && mq.addEventListener) mq.addEventListener('change', applyTheme);

  const DE = {
    // Hauptseite
    'Google Drive sync': 'Google-Drive-Synchronisierung',
    'Hide panel': 'Bereich ein-/ausblenden',
    'Tag': 'Tag',
    '🅿️ Park & guard': '🅿️ Abstellen & bewachen',
    '📍 Locate now': '📍 Jetzt orten',
    '🔔 Ring': '🔔 Klingeln',
    '↗ Share': '↗ Teilen',
    'History': 'Verlauf',
    'Today': 'Heute',
    '24 h': '24 Std.',
    '2 days': '2 Tage',
    '7 days': '7 Tage',
    '30 days': '30 Tage',
    '90 days': '90 Tage',
    '1 year': '1 Jahr',
    'Custom': 'Eigener Zeitraum',
    'From': 'Von',
    'To': 'Bis',
    'Show': 'Anzeigen',
    'Playback': 'Wiedergabe',
    'Play': 'Abspielen',
    'Show individual points': 'Einzelne Punkte anzeigen',
    'Auto-refresh every minute': 'Jede Minute automatisch aktualisieren',
    'Online address lookup (sends coordinates to Nominatim)': 'Adresse online suchen (schickt Koordinaten an Nominatim)',
    'Check now': 'Jetzt abfragen',
    'Rename tag': 'Tag umbenennen',
    'Change server': 'Server ändern',
    'Sign out': 'Abmelden',
    'Settings': 'Einstellungen',
    'Center on tag': 'Auf Tag zentrieren',
    'Satellite (Esri)': 'Satellit (Esri)',
    // app.js
    'just now': 'gerade eben',
    '{n} min ago': 'vor {n} Min.',
    '{n} h ago': 'vor {n} Std.',
    '{n} days ago': 'vor {n} Tagen',
    'No tags yet – waiting for first check': 'Noch keine Tags – warte auf die erste Abfrage',
    'Automatic checking is off on this server.': 'Automatische Abfrage ist auf diesem Server aus.',
    'Checking Google for new locations…': 'Frage Google nach neuen Standorten …',
    'Last check failed: {e}': 'Letzte Abfrage fehlgeschlagen: {e}',
    'Last checked {ago} · every {n} min': 'Zuletzt abgefragt {ago} · alle {n} Min.',
    'Starting…': 'Startet …',
    '🔒 Parked & guarded — tap when you ride': '🔒 Abgestellt & bewacht – tippen, wenn du losfährst',
    'Drive off': 'Drive aus',
    'Google Drive sync is off. Turn it on in setup.': 'Google-Drive-Synchronisierung ist aus. Du kannst sie in der Einrichtung einschalten.',
    'Saved · sync pending': 'Gespeichert · Sync ausstehend',
    'Your locations ARE saved on the phone. Drive sync will retry automatically.': 'Deine Standorte SIND am Handy gespeichert. Die Drive-Synchronisierung versucht es automatisch erneut.',
    'Synced': 'Synchronisiert',
    'Backed up to Google Drive {ago}': 'In Google Drive gesichert {ago}',
    'Syncing…': 'Synchronisiert …',
    'First Drive sync in progress…': 'Erste Drive-Synchronisierung läuft …',
    'No tag selected.': 'Kein Tag ausgewählt.',
    'No locations stored yet.': 'Noch keine Standorte gespeichert.',
    'Last seen at {t}': 'Zuletzt gesehen am {t}',
    '📍 finding address…': '📍 suche Adresse …',
    '{n} locations stored in total': '{n} Standorte insgesamt gespeichert',
    '{name} – latest': '{name} – zuletzt',
    'Open in Google Maps': 'In Google Maps öffnen',
    'Reported by your own phone': 'Gemeldet von deinem eigenen Handy',
    'Reported by Find Hub network': 'Gemeldet vom Find-Hub-Netzwerk',
    'locations': 'Standorte',
    'km travelled': 'km zurückgelegt',
    'time span': 'Zeitraum',
    '{n} d': '{n} T.',
    '{n} h': '{n} Std.',
    'Pick a valid date range.': 'Bitte einen gültigen Zeitraum wählen.',
    '📍 Locating…': '📍 Orte …',
    'Asking the tag for its current location…': 'Frage den Tag nach seinem aktuellen Standort …',
    'Guarding: you’ll be alerted if the tag moves.': 'Bewacht: Du wirst gewarnt, wenn sich der Tag bewegt.',
    'Riding mode: movement alerts are off.': 'Fahrmodus: Bewegungsalarm ist aus.',
    'Ringing the tag…': 'Tag klingelt …',
    'No location yet to share.': 'Noch kein Standort zum Teilen.',
    '{name} — last seen {t}': '{name} – zuletzt gesehen am {t}',
    'Location copied to clipboard': 'Standort in die Zwischenablage kopiert',
    'Copy:': 'Kopieren:',
    'New name for this tag': 'Neuer Name für diesen Tag',
    // Gesamtansicht
    'All tags': 'Alle Tags',
    'tap a tag for its history': 'Tag antippen für den Verlauf',
    'Pick a tag first (dropdown or map).': 'Bitte zuerst einen Tag wählen (Liste oder Karte).',
    // Akku
    'Battery': 'Akku',
    'normal': 'normal',
    'low': 'schwach',
    'critical – replace soon': 'kritisch – bald tauschen',
    'no data from this tag': 'Tag meldet keinen Wert',
    'measured {ago}': 'gemessen {ago}',
    'not measured yet – bring the tag near your phone': 'noch nicht gemessen – Tag in die Nähe des Handys bringen',
    'Check battery': 'Akku prüfen',
    'Battery is only readable via Bluetooth, in the Android app.': 'Der Akku lässt sich nur per Bluetooth in der Android-App lesen.',
    // Anmeldeseite (Server)
    'Find All My Tags – Sign in': 'Find All My Tags – Anmelden',
    "Sign in to see your tag's travel history.": 'Melde dich an, um den Verlauf deiner Tags zu sehen.',
    'Username': 'Benutzername',
    'Password': 'Passwort',
    'Sign in': 'Anmelden',
    'Not affiliated with Google. Independent, open-source. For tracking your own property.': 'Keine Verbindung zu Google. Unabhängig, Open Source. Zum Orten deines eigenen Eigentums.',
    // Ansicht
    'Display': 'Oberfläche',
    'Map': 'Karte',
    'Auto': 'Auto',
    'Light': 'Hell',
    'Dark': 'Dunkel',
    'View': 'Ansicht',
    // Tag-Details
    'History': 'Verlauf',
    'Locate': 'Orten',
    'Ring': 'Klingeln',
    'Share': 'Teilen',
    'Rename': 'Umbenennen',
    'Hide history': 'Verlauf aus',
    'History of {name}': 'Verlauf von {name}',
    'Check all now': 'Alle jetzt abfragen',
    'Last seen': 'Zuletzt gesehen',
    'never': 'noch nie',
    'Name is only changed in this app, not at Google.': 'Der Name wird nur in dieser App geändert, nicht bei Google.',
    // 1.3.0
    "Show the tag's address in the details": 'Adresse des Tags in den Details anzeigen',
    'What happens here?': 'Was passiert dabei?',
    '🖥 PC view': '🖥 PC-Ansicht',
    'System configuration & info': 'Systemkonfiguration und Infos',
    'Not seen for over {n} – battery empty or out of range?': 'Seit über {n} nicht gesehen – Batterie leer oder außer Reichweite?',
    '{n} days': '{n} Tagen',
    '{n} hours': '{n} Stunden',
    'Last update from the app {ago}': 'Letzte Aktualisierung von der App {ago}',
    'No data from the app yet.': 'Noch keine Daten von der App.',
    'nearby': 'in der Nähe',
    'Nearby – just recognised by Bluetooth ({ago})': 'In der Nähe – gerade per Bluetooth erkannt ({ago})',
    'Last location from Google: {t}': 'Letzter Standort von Google: {t}',
    'Google Drive backup: off': 'Google-Drive-Sicherung: aus',
    'Google Drive backup: pending, will retry (locations are safe on the phone)': 'Google-Drive-Sicherung: ausstehend, wird wiederholt (Standorte sind am Handy sicher)',
    'Google Drive backup: saved {ago}': 'Google-Drive-Sicherung: gesichert {ago}',
    'Google Drive backup: first sync running…': 'Google-Drive-Sicherung: erste Sicherung läuft …',
    'Update all now': 'Jetzt alle aktualisieren',
    'Battery very low': 'Akku sehr niedrig',
    'Battery low': 'Akku niedrig',
    'never seen': 'noch nie gesehen',
    'not seen for {n}': 'seit {n} nicht gesehen',
    // Sprache
    'Language': 'Sprache',
  };

  function t(en, vars) {
    let s = (LANG === 'de' && DE[en] != null) ? DE[en] : en;
    if (vars) s = s.replace(/\{(\w+)\}/g, (m, k) => (vars[k] != null ? vars[k] : m));
    return s;
  }

  function translateNode(root) {
    if (LANG !== 'de' || !root) return;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    for (const n of nodes) {
      const raw = n.nodeValue, key = raw.trim();
      if (key && DE[key] != null) n.nodeValue = raw.replace(key, DE[key]);
    }
    const els = root.querySelectorAll ? root.querySelectorAll('[title],[placeholder],[aria-label]') : [];
    for (const el of els) {
      for (const a of ['title', 'placeholder', 'aria-label']) {
        const v = el.getAttribute(a);
        if (v && DE[v] != null) el.setAttribute(a, DE[v]);
      }
    }
  }

  function setLang(code) {
    try { localStorage.setItem('lang', code); } catch (e) {}
    if (window.Native && window.Native.setLang) { window.Native.setLang(code); return; }
    const u = new URL(location.href); u.searchParams.set('lang', code); location.href = u.toString();
  }

  window.I18N = { lang: LANG, t, translateNode, setLang, setTheme, getPref };
  window.t = t;

  const run = () => {
    translateNode(document.body);
    if (document.title && DE[document.title]) document.title = DE[document.title];
    // Umschalter (falls auf der Seite vorhanden)
    document.querySelectorAll('[data-lang-switch]').forEach((b) => {
      b.innerHTML = LANG === 'en' ? '<b>EN</b>/DE' : 'EN/<b>DE</b>';
      b.title = LANG === 'de' ? 'Switch to English' : 'Auf Deutsch umschalten';
      b.addEventListener('click', () => setLang(LANG === 'de' ? 'en' : 'de'));
    });
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run); else run();
})();
