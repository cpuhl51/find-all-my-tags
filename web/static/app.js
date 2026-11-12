(() => {
  const $ = (id) => document.getElementById(id);
  const t = window.t || ((s, v) => (v ? s.replace(/\{(\w+)\}/g, (m, k) => v[k] ?? m) : s));
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch {} },
  };

  // ---------- Karte ----------
  const map = L.map('map', { preferCanvas: true, zoomControl: false }).setView([48.2, 16.37], 11);
  L.control.zoom({ position: 'topright' }).addTo(map);
  const gsub = { subdomains: ['mt0', 'mt1', 'mt2', 'mt3'], maxZoom: 21, attribution: '&copy; Google' };
  const bases = {
    'OpenStreetMap': { layer: L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; OpenStreetMap contributors' }), imagery: false },
    'Google Streets': { layer: L.tileLayer('https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', gsub), imagery: false },
    'Google Satellite': { layer: L.tileLayer('https://{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}', gsub), imagery: true },
    'Google Hybrid': { layer: L.tileLayer('https://{s}.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', gsub), imagery: true },
    'Esri Satellite': { layer: L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 19, attribution: 'Imagery &copy; Esri, Maxar' }), imagery: true },
  };
  const applyImagery = (name) => document.body.classList.toggle('imagery', !!bases[name]?.imagery);
  const saved = store.get('layer');
  const startName = bases[saved] ? saved : 'OpenStreetMap';
  bases[startName].layer.addTo(map);
  applyImagery(startName);
  const control = {};
  Object.entries(bases).forEach(([name, b]) => { control[name] = b.layer; });
  L.control.layers(control, null, { position: 'topright' }).addTo(map);
  map.on('baselayerchange', (e) => { applyImagery(e.name); store.set('layer', e.name); });

  const Recenter = L.Control.extend({
    options: { position: 'topright' },
    onAdd() {
      const b = L.DomUtil.create('button', 'leaflet-bar recenter-btn');
      b.innerHTML = '◎'; b.title = t('Center on tag');
      L.DomEvent.on(b, 'click', (ev) => {
        L.DomEvent.stop(ev);
        const d = focused();
        if (d && d.last_lat != null) focusOn(d, true); else fitAll();
      });
      return b;
    },
  });
  map.addControl(new Recenter());

  const layers = { all: L.layerGroup().addTo(map), track: L.layerGroup().addTo(map), points: L.layerGroup().addTo(map), play: L.layerGroup().addTo(map) };
  let devices = [], focusId = store.get('focus') || null, historyOn = false;
  let range = { h: store.get('range') || '168' };
  let points = [], playTimer = null, fittedOnce = false;
  let readOnly = false, staleHours = 72, serverUrl = '';

  // Im Android-App läuft alles über die Native-Brücke, sonst über den Web-Server.
  const NATIVE = window.Native;
  // Das PHP-Paket für den eigenen Server setzt window.API_PATH (keine Umschreibregeln nötig).
  const apiUrl = (path) => (window.API_PATH ? window.API_PATH(path) : path);
  const loginUrl = () => (window.LOGIN_URL || '/login');
  async function api(path, opts = {}) {
    const r = await fetch(apiUrl(path), { credentials: 'same-origin', ...opts });
    if (r.status === 401) { location.href = loginUrl(); throw new Error('logged out'); }
    if (!r.ok) throw new Error((await r.json().catch(() => ({}))).detail || r.statusText);
    return r.json();
  }
  const data = {
    devices: () => NATIVE ? JSON.parse(NATIVE.devices()) : api('/api/devices'),
    history: (id, s, e) => NATIVE ? JSON.parse(NATIVE.history(id, s, e))
      : api(`/api/history/${encodeURIComponent(id)}?start=${s}&end=${e}`),

    pollNow: () => NATIVE ? NATIVE.pollNow() : api('/api/poll-now', { method: 'POST' }),
    locate: (id) => NATIVE ? NATIVE.locateNow(id) : api(`/api/locate/${encodeURIComponent(id)}`, { method: 'POST' }),
    sound: (id) => NATIVE ? NATIVE.playSound(id) : api(`/api/sound/${encodeURIComponent(id)}`, { method: 'POST' }),
    rename: (id, name) => NATIVE ? NATIVE.rename(id, name)
      : api(`/api/devices/${encodeURIComponent(id)}`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name }) }),
  };

  const fmt = (ts) => new Date(ts * 1000).toLocaleString(window.I18N ? (I18N.lang === 'de' ? 'de-AT' : 'en-GB') : [], { dateStyle: 'medium', timeStyle: 'short' });
  function ago(ts) {
    const s = Date.now() / 1000 - ts;
    if (s < 90) return t('just now');
    if (s < 3600) return t('{n} min ago', { n: Math.round(s / 60) });
    if (s < 86400) return t('{n} h ago', { n: Math.round(s / 3600) });
    return t('{n} days ago', { n: Math.round(s / 86400) });
  }
  function km(a, b) {
    const R = 6371, r = Math.PI / 180;
    const dLat = (b.lat - a.lat) * r, dLon = (b.lon - a.lon) * r;
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(h));
  }
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  // ---------- Symbol je Tag: Form + Farbe, beide verschieden ----------
  // 8 klar unterscheidbare Farben x 7 Formen. Weil 8 und 7 teilerfremd sind, ergibt
  // (Index mod 8, Index mod 7) für die ersten 56 Tags lauter verschiedene Kombinationen,
  // und benachbarte Tags unterscheiden sich immer in Farbe UND Form.
  const COLORS = ['#e6194b', '#2f7ff0', '#2db84d', '#ff8c00', '#9b30d9', '#00b8c4', '#f032c8', '#b88a00'];
  const SHAPES = ['circle', 'square', 'triangle', 'diamond', 'star', 'hexagon', 'cross'];
  const order = () => devices.slice().sort((a, b) => String(a.id).localeCompare(String(b.id)));
  function styleOf(d) {
    const i = Math.max(0, order().indexOf(d));
    return { color: COLORS[i % COLORS.length], shape: SHAPES[i % SHAPES.length] };
  }
  function shapePath(shape) {
    switch (shape) {
      case 'square': return '<rect x="3.5" y="3.5" width="17" height="17" rx="2.5"/>';
      case 'triangle': return '<path d="M12 2.5 22 20.5H2z"/>';
      case 'diamond': return '<path d="M12 1.5 22.5 12 12 22.5 1.5 12z"/>';
      case 'star': return '<path d="m12 1.8 3 6.6 7.2.8-5.4 4.9 1.5 7.1L12 17.6l-6.3 3.6 1.5-7.1-5.4-4.9 7.2-.8z"/>';
      case 'hexagon': return '<path d="M12 1.8 21.2 7v10L12 22.2 2.8 17V7z"/>';
      case 'cross': return '<path d="M8.5 2h7v6.5H22v7h-6.5V22h-7v-6.5H2v-7h6.5z"/>';
      default: return '<circle cx="12" cy="12" r="9.5"/>';
    }
  }
  function iconSvg(d, size, ring) {
    const s = styleOf(d);
    return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true" style="overflow:visible">`
      + (ring ? `<circle cx="12" cy="12" r="13.5" fill="none" stroke="${s.color}" stroke-width="2" stroke-dasharray="3 2"/>` : '')
      + `<g fill="${s.color}" stroke="#fff" stroke-width="1.8" stroke-linejoin="round">${shapePath(s.shape)}</g></svg>`;
  }

  // ---------- Akkustand (von der App per Bluetooth gelesen: 1 normal, 2 schwach, 3 kritisch) ----------
  const BATT = { 1: ['🟢', 'normal'], 2: ['🟡', 'low'], 3: ['🔴', 'critical – replace soon'] };
  const battDot = (d) => (d.battery && BATT[d.battery.level] ? BATT[d.battery.level][0] : '');
  function battLine(d) {
    const b = d.battery;
    const btn = NATIVE && NATIVE.checkBatteries ? ` <button type="button" class="batt-check">${esc(t('Check battery'))}</button>` : '';
    if (b && BATT[b.level]) {
      const [dot, label] = BATT[b.level];
      return `<div class="batt l${b.level}">${dot} ${esc(t('Battery'))}: <span class="lvl">${esc(t(label))}</span>`
        + ` <span class="muted">· ${esc(t('measured {ago}', { ago: ago(b.ts) }))}</span>${btn}</div>`;
    }
    if (!NATIVE) return '';
    const why = b && b.level === 0 ? t('no data from this tag') : t('not measured yet – bring the tag near your phone');
    return `<div class="batt">🔋 ${esc(t('Battery'))}: <span class="muted">${esc(why)}</span>${btn}</div>`;
  }

  // Adresse über OpenStreetMap Nominatim, nur wenn eingeschaltet (schickt Koordinaten an OSM).
  const addrCache = {};
  async function addressFor(lat, lon) {
    if (store.get('allowAddressLookup') !== '1') return '';
    const k = lat.toFixed(4) + ',' + lon.toFixed(4);
    if (addrCache[k] !== undefined) return addrCache[k];
    try {
      const r = await fetch(`https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=16&lat=${lat}&lon=${lon}`,
        { headers: { 'Accept': 'application/json' } });
      const j = await r.json();
      const a = j && j.display_name ? j.display_name.split(',').slice(0, 3).join(',').trim() : '';
      addrCache[k] = a; return a;
    } catch (e) { addrCache[k] = ''; return ''; }
  }

  // ---------- sichtbarer Kartenbereich (ohne den Teil, den das Panel verdeckt) ----------
  function freeArea() {
    const W = map.getSize().x, H = map.getSize().y;
    const p = $('panel').getBoundingClientRect();
    if (p.width > W * 0.7) return { x0: 0, x1: W, y0: 0, y1: Math.max(120, Math.min(H, p.top)) };  // Panel unten (Handy)
    return { x0: Math.min(W - 120, Math.max(0, p.right)), x1: W, y0: 0, y1: H };                   // Panel links (PC)
  }
  function panPadding() {
    const W = map.getSize().x, H = map.getSize().y, f = freeArea();
    return { paddingTopLeft: [f.x0 + 30, f.y0 + 40], paddingBottomRight: [W - f.x1 + 50, H - f.y1 + 30] };
  }
  function focusOn(d, zoomIn) {
    if (!d || d.last_lat == null) return;
    const z = zoomIn ? Math.max(map.getZoom(), 18) : map.getZoom();
    const f = freeArea(), size = map.getSize();
    // Zielpunkt: waagrecht mittig, senkrecht etwas über der Mitte des freien Bereichs
    const target = L.point((f.x0 + f.x1) / 2, f.y0 + (f.y1 - f.y0) * 0.42);
    const offset = target.subtract(size.divideBy(2));
    const center = map.unproject(map.project([d.last_lat, d.last_lon], z).subtract(offset), z);
    map.setView(center, z, { animate: true });
  }
  function fitAll() {
    const lls = devices.filter((d) => d.last_lat != null).map((d) => [d.last_lat, d.last_lon]);
    if (!lls.length) return;
    if (lls.length === 1) { focusOn(devices.find((d) => d.last_lat != null), true); return; }
    map.fitBounds(L.latLngBounds(lls), { ...panPadding(), maxZoom: 16 });
  }

  // ---------- Geräte ----------
  const focused = () => devices.find((x) => x.id === focusId) || null;

  async function loadDevices() {
    const res = await data.devices();
    devices = res.devices || [];
    readOnly = !!res.readonly;
    if (res.stale_hours != null) staleHours = Number(res.stale_hours) || 0;
    serverUrl = res.server_url || '';
    $('pcView').hidden = !serverUrl || !NATIVE;
    $('pollNow').hidden = readOnly;
    if (focusId && !focused()) { focusId = null; historyOn = false; }
    renderMarkers();
    renderOverview();
    renderDetail();
    const p = res.poller || {};
    $('pollStatus').textContent = p.enabled === false ? t('Automatic checking is off on this server.')
      : p.running ? t('Checking Google for new locations…')
      : p.last_error ? t('Last check failed: {e}', { e: p.last_error })
      : p.last_run ? (p.interval_minutes ? t('Last checked {ago} · every {n} min', { ago: ago(p.last_run), n: p.interval_minutes })
        : t('Last update from the app {ago}', { ago: ago(p.last_run) }))
      : (readOnly ? t('No data from the app yet.') : t('Starting…'));
    renderSync(res.backup);
    if (!fittedOnce && devices.some((d) => d.last_lat != null)) {
      fittedOnce = true;
      const d = focused();
      if (d) focusOn(d, true); else fitAll();
    }
  }

  // Tags, die auf der Karte (fast) übereinander liegen, werden zu einer Gruppe und
  // senkrecht untereinander daneben angeordnet, mit dünner Linie zum echten Ort.
  const STACK_R = 26, STACK_STEP = 28, STACK_DX = 34;
  function layout() {
    const items = devices.filter((d) => d.last_lat != null)
      .map((d) => ({ d, p: map.latLngToContainerPoint([d.last_lat, d.last_lon]) }));
    const groups = [];
    items.forEach((o) => {
      const g = groups.find((g) => g.c.distanceTo(o.p) < STACK_R);
      if (g) g.items.push(o); else groups.push({ c: o.p, real: [o.d.last_lat, o.d.last_lon], items: [o] });
    });
    groups.forEach((g) => {
      if (g.items.length < 2) { g.items[0].pos = [g.items[0].d.last_lat, g.items[0].d.last_lon]; return; }
      g.items.sort((a, b) => String(a.d.name).localeCompare(String(b.d.name)));
      const n = g.items.length, y0 = g.c.y - ((n - 1) * STACK_STEP) / 2;
      g.items.forEach((o, i) => { o.pos = map.containerPointToLatLng(L.point(g.c.x + STACK_DX, y0 + i * STACK_STEP)); });
    });
    return groups;
  }
  function renderMarkers() {
    layers.all.clearLayers();
    layout().forEach((g) => {
      const stacked = g.items.length > 1;
      if (stacked) {
        g.items.forEach((o) => L.polyline([g.real, o.pos], { color: '#94a3b8', weight: 1, opacity: 0.9, interactive: false }).addTo(layers.all));
        L.circleMarker(g.real, { radius: 4, color: '#fff', weight: 2, fillColor: '#475569', fillOpacity: 1, interactive: false }).addTo(layers.all);
      }
      g.items.forEach(({ d, pos }) => {
        const on = d.id === focusId, size = on ? 28 : 22;
        const icon = L.divIcon({ className: 'tag-icon', html: iconSvg(d, size, on), iconSize: [size, size], iconAnchor: [size / 2, size / 2] });
        L.marker(pos, { icon, zIndexOffset: on ? 1000 : 0, keyboard: false })
          .bindTooltip(`${battDot(d)} ${esc(d.name)}`.trim(), { permanent: true, direction: 'right', offset: [size / 2, 0], className: 'tag-label' })
          .on('click', () => select(d.id, true))
          .addTo(layers.all);
        if (d.last_accuracy && on) L.circle([d.last_lat, d.last_lon], { radius: d.last_accuracy, color: styleOf(d).color, weight: 1, fillOpacity: 0.08, interactive: false }).addTo(layers.all);
      });
    });
  }
  map.on('zoomend', () => renderMarkers());
  map.on('resize', () => renderMarkers());
  const isStale = (d) => staleHours > 0 && d.last_ts != null && Date.now() / 1000 - d.last_ts > staleHours * 3600;

  function renderOverview() {
    const el = $('latest');
    if (!devices.length) { el.innerHTML = `<span class="muted">${esc(t('No tags yet – waiting for first check'))}</span>`; return; }
    const list = devices.slice().sort((a, b) => (b.last_ts || 0) - (a.last_ts || 0));
    el.innerHTML = `<div class="ov-head"><b>${esc(t('All tags'))} (${devices.length})</b></div>`
      + '<div class="ov">' + list.map((d) => {
        const seen = d.last_ts != null ? ago(d.last_ts) : t('never');
        const st = isStale(d) || d.last_ts == null;
        return `<button type="button" class="ov-row${d.id === focusId ? ' on' : ''}${st ? ' stale' : ''}" data-id="${esc(d.id)}">${iconSvg(d, 20)}`
          + `<span class="ov-main"><span class="ov-name">${esc(d.name)}</span><span class="ov-seen">${st ? '⚠ ' : ''}${esc(seen)}</span></span>`
          + `<span class="ov-batt">${battDot(d)}</span></button>`;
      }).join('') + '</div>';
    el.querySelectorAll('.ov-row').forEach((r) => r.addEventListener('click', () => select(r.dataset.id)));
  }

  // Tipp auf einen Tag: im sichtbaren Bereich der Karte zentrieren und Details zeigen.
  // Die Gesamtansicht bleibt; ein zweiter Tipp auf denselben Tag hebt die Auswahl auf.
  function select(id, fromMap) {
    const panel = $('panel');
    if (fromMap) {
      if (panel.classList.contains('collapsed')) panel.classList.remove('collapsed');
      if (focusId === id) { const d0 = focused(); if (d0) setTimeout(() => focusOn(d0, false), 60); return; }
    }
    if (focusId === id) { focusId = null; historyOn = false; clearHistory(); }
    else { focusId = id; if (historyOn) loadHistory().catch(console.error); }
    store.set('focus', focusId || '');
    renderMarkers(); renderOverview(); renderDetail();
    const d = focused();
    if (d) {
      const body = panel.querySelector('.panel-body'); if (body) body.scrollTop = 0;
      panel.scrollTop = 0;
      setTimeout(() => focusOn(d, true), 60);  // nach dem Aufklappen, damit der freie Bereich stimmt
    }
  }

  function renderDetail() {
    const el = $('detail'), d = focused();
    $('historyBox').hidden = !(d && historyOn);
    if (!d) { el.hidden = true; el.innerHTML = ''; return; }
    el.hidden = false;
    const seen = d.last_ts != null
      ? `${esc(t('Last seen at {t}', { t: fmt(d.last_ts) }))} <span class="muted">(${ago(d.last_ts)})</span>`
      : `<span class="muted">${esc(t('No locations stored yet.'))}</span>`;
    el.innerHTML = `<div class="d-head">${iconSvg(d, 22)}<b>${esc(d.name)}</b><button type="button" class="icon" id="dClose">✕</button></div>`
      + `<div>${seen}</div>`
      + (d.last_lat != null ? `<div id="addr" class="muted">📍 ${d.last_lat.toFixed(5)}, ${d.last_lon.toFixed(5)}</div>` : '')
      + (isStale(d) ? `<div class="stale-note">⚠ ${esc(t('Not seen for over {n} – battery empty or out of range?', { n: staleText() }))}</div>` : '')
      + battLine(d)
      + (d.last_error ? `<div class="error small">${esc(d.last_error)}</div>` : '')
      + `<div class="acts${readOnly ? ' ro' : ''}">`
      + `<button type="button" id="aHist" class="${historyOn ? 'on' : ''}">🕓 ${esc(t('History'))}</button>`
      + (readOnly ? '' : `<button type="button" id="aLocate">📍 ${esc(t('Locate'))}</button>`
      + `<button type="button" id="aRing">🔔 ${esc(t('Ring'))}</button>`)
      + `<button type="button" id="aShare">↗ ${esc(t('Share'))}</button>`
      + `</div>`;
    $('dClose').onclick = () => select(d.id);
    $('aHist').onclick = () => {
      historyOn = !historyOn; renderDetail();
      if (historyOn) loadHistory().catch(alert); else { clearHistory(); focusOn(d, false); }
    };
    if (!readOnly) {
      $('aLocate').onclick = () => locateNow(d);
      $('aRing').onclick = async () => { try { await data.sound(d.id); $('pollStatus').textContent = t('Ringing the tag…'); } catch (e) { alert(e.message); } };
    }
    $('aShare').onclick = () => share(d);
    if (historyOn) $('histTitle').textContent = t('History of {name}', { name: d.name });
    if (d.last_lat != null) addressFor(d.last_lat, d.last_lon).then((a) => { const ae = $('addr'); if (ae && a) ae.textContent = '📍 ' + a; });
  }
  document.addEventListener('click', (e) => {
    if (e.target.closest && e.target.closest('.batt-check') && NATIVE && NATIVE.checkBatteries) NATIVE.checkBatteries();
  });

  function staleText() {
    return staleHours % 24 === 0 ? t('{n} days', { n: staleHours / 24 }) : t('{n} hours', { n: staleHours });
  }

  function renderSync(b) {
    const el = $('sync');
    if (!el) return;
    el.classList.remove('ok', 'busy', 'err', 'off');
    if (!b || !b.enabled) {
      el.classList.add('off'); el.textContent = t('Drive off');
      el.title = t('Google Drive sync is off. Turn it on in setup.');
    } else if (b.last_error) {
      el.classList.add('busy'); el.textContent = t('Saved · sync pending');
      el.title = t('Your locations ARE saved on the phone. Drive sync will retry automatically.') + ' (' + b.last_error + ')';
    } else if (b.last_run) {
      el.classList.add('ok'); el.textContent = t('Synced');
      el.title = t('Backed up to Google Drive {ago}', { ago: ago(b.last_run) }) + (b.mode ? ` (${b.mode})` : '');
    } else {
      el.classList.add('busy'); el.textContent = t('Syncing…');
      el.title = t('First Drive sync in progress…');
    }
  }

  // ---------- Verlauf (nur für den gewählten Tag, auf Wunsch) ----------
  function currentRange() {
    const now = Math.floor(Date.now() / 1000);
    if (range.h === 'today') { const d = new Date(); d.setHours(0, 0, 0, 0); return [Math.floor(d / 1000), now]; }
    if (range.h === 'custom') return [range.start, range.end];
    return [now - Number(range.h) * 3600, now];
  }
  function clearHistory() {
    stopPlay();
    layers.track.clearLayers(); layers.points.clearLayers(); layers.play.clearLayers();
    points = []; renderStats();
  }
  async function loadHistory() {
    clearHistory();
    const d = focused();
    if (!d || !historyOn) return;
    const [start, end] = currentRange();
    const res = await data.history(d.id, start, end);
    points = (res.points || []).filter((p) => p.lat != null);
    const color = styleOf(d).color;
    const lls = points.map((p) => [p.lat, p.lon]);
    if (lls.length > 1) L.polyline(lls, { color, weight: 3, opacity: 0.8 }).addTo(layers.track);
    if ($('showPoints').checked) {
      points.forEach((p, i) => {
        L.circleMarker([p.lat, p.lon], { radius: 4, color, weight: 1, fillColor: i === 0 ? '#16a34a' : '#fff', fillOpacity: 1 })
          .bindPopup(`<div class="popup"><b>${fmt(p.ts)}</b>${p.accuracy ? `± ${Math.round(p.accuracy)} m<br>` : ''}`
            + `${p.is_own_report ? t('Reported by your own phone') : t('Reported by Find Hub network')}<br>`
            + `<span class="muted">${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}</span></div>`)
          .addTo(layers.points);
      });
    }
    if (lls.length > 1) map.fitBounds(L.latLngBounds(lls), { ...panPadding(), maxZoom: 17 });
    const scrub = $('scrub');
    scrub.max = Math.max(0, points.length - 1); scrub.value = scrub.max;
    $('scrubLabel').innerHTML = '&nbsp;';
    renderStats();
  }
  function renderStats() {
    let dist = 0;
    for (let i = 1; i < points.length; i++) dist += km(points[i - 1], points[i]);
    const span = points.length ? points[points.length - 1].ts - points[0].ts : 0;
    const days = span / 86400;
    $('stats').innerHTML = [
      [points.length.toLocaleString(), t('locations')],
      [dist < 10 ? dist.toFixed(2) : Math.round(dist).toLocaleString(), t('km travelled')],
      [days >= 1 ? t('{n} d', { n: days.toFixed(days < 10 ? 1 : 0) }) : t('{n} h', { n: Math.round(span / 3600) }), t('time span')],
    ].map(([v, k]) => `<div class="stat"><div class="v">${v}</div><div class="k">${k}</div></div>`).join('');
  }

  // ---------- Wiedergabe ----------
  function showAt(i) {
    layers.play.clearLayers();
    const p = points[i], d = focused();
    if (!p || !d) return;
    L.polyline(points.slice(0, i + 1).map((q) => [q.lat, q.lon]), { color: '#f59e0b', weight: 5, opacity: 0.9 }).addTo(layers.play);
    L.circleMarker([p.lat, p.lon], { radius: 10, color: '#fff', weight: 3, fillColor: styleOf(d).color, fillOpacity: 1 }).addTo(layers.play);
    $('scrubLabel').textContent = `${i + 1} / ${points.length} · ${fmt(p.ts)}`;
    if (!map.getBounds().contains([p.lat, p.lon])) map.panTo([p.lat, p.lon]);
  }
  function stopPlay() { clearInterval(playTimer); playTimer = null; $('play').textContent = '▶'; }
  $('scrub').addEventListener('input', (e) => { stopPlay(); showAt(Number(e.target.value)); });
  $('play').addEventListener('click', () => {
    if (playTimer) return stopPlay();
    if (!points.length) return;
    const scrub = $('scrub');
    if (Number(scrub.value) >= points.length - 1) scrub.value = 0;
    $('play').textContent = '❚❚';
    const stepMs = Math.max(15, Math.min(300, 20000 / points.length));
    playTimer = setInterval(() => {
      const i = Number(scrub.value) + 1;
      if (i >= points.length) return stopPlay();
      scrub.value = i; showAt(i);
    }, stepMs);
  });

  // ---------- Bedienelemente ----------
  function markRange() {
    document.querySelectorAll('#ranges button').forEach((b) => b.classList.toggle('on', b.dataset.h === range.h));
    $('custom').hidden = range.h !== 'custom';
  }
  $('ranges').addEventListener('click', (e) => {
    const h = e.target.dataset?.h; if (!h) return;
    range = { h }; markRange();
    if (h === 'custom') {
      const toLocal = (d) => new Date(d - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
      $('to').value ||= toLocal(new Date()); $('from').value ||= toLocal(new Date(Date.now() - 7 * 86400000));
      return;
    }
    store.set('range', h); loadHistory().catch(alert);
  });
  $('applyCustom').addEventListener('click', () => {
    const s = new Date($('from').value), en = new Date($('to').value);
    if (isNaN(s) || isNaN(en) || s >= en) return alert(t('Pick a valid date range.'));
    range = { h: 'custom', start: Math.floor(s / 1000), end: Math.floor(en / 1000) };
    loadHistory().catch(alert);
  });
  $('showPoints').addEventListener('change', () => loadHistory().catch(alert));
  $('onlineAddress').checked = store.get('allowAddressLookup') === '1';
  $('onlineAddress').addEventListener('change', (e) => { store.set('allowAddressLookup', e.target.checked ? '1' : '0'); renderDetail(); });
  $('toggle').addEventListener('click', () => $('panel').classList.toggle('collapsed'));
  $('pollNow').addEventListener('click', async () => {
    try { await data.pollNow(); $('pollStatus').textContent = t('Checking Google for new locations…'); setTimeout(refresh, 15000); }
    catch (e) { alert(e.message); }
  });
  const download = (f) => {
    const d = focused(); if (!d) return;
    const [start, end] = currentRange();
    if (NATIVE) NATIVE.export(d.id, start, end, f);
    else location.href = `/api/history/${encodeURIComponent(d.id)}?start=${start}&end=${end}&format=${f}`;
  };
  $('csv').addEventListener('click', () => download('csv'));
  $('gpx').addEventListener('click', () => download('gpx'));

  // Hell/Dunkel für Oberfläche und Karte (getrennt einstellbar)
  document.querySelectorAll('.seg[data-theme-key]').forEach((seg) => {
    const key = seg.dataset.themeKey;
    const mark = () => seg.querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.dataset.v === (window.I18N ? I18N.getPref(key) : 'auto')));
    seg.addEventListener('click', (e) => { const v = e.target.dataset?.v; if (!v || !window.I18N) return; I18N.setTheme(key, v); mark(); });
    mark();
  });

  async function locateNow(d) {
    const btn = $('aLocate');
    if (btn) { btn.disabled = true; btn.textContent = t('📍 Locating…'); }
    $('pollStatus').textContent = t('Asking the tag for its current location…');
    const done = () => { const b = $('aLocate'); if (b) { b.disabled = false; b.textContent = '📍 ' + t('Locate'); } };
    try { await data.locate(d.id); } catch (e) { $('pollStatus').textContent = e.message; done(); return; }
    if (NATIVE) {
      let n = 0;
      const iv = setInterval(async () => { await refresh(); if (++n >= 8) { clearInterval(iv); done(); } }, 4000);
    } else { await refresh(); done(); }
  }
  async function share(d) {
    if (d.last_lat == null) { alert(t('No location yet to share.')); return; }
    const addr = await addressFor(d.last_lat, d.last_lon);
    const text = t('{name} — last seen {t}', { name: d.name, t: fmt(d.last_ts) }) + `\n${addr ? addr + '\n' : ''}`
      + `https://www.google.com/maps?q=${d.last_lat},${d.last_lon}`;
    if (NATIVE && NATIVE.share) NATIVE.share(text);
    else if (navigator.share) { try { await navigator.share({ title: d.name, text }); } catch (e) {} }
    else { try { await navigator.clipboard.writeText(text); alert(t('Location copied to clipboard')); } catch (e) { prompt(t('Copy:'), text); } }
  }

  if (window.TagTrackerApp) {
    $('server').hidden = false;
    $('server').addEventListener('click', () => window.TagTrackerApp.changeServer());
  }
  $('pcView').addEventListener('click', () => { if (!serverUrl) return; if (NATIVE && NATIVE.openUrl) NATIVE.openUrl(serverUrl); else window.open(serverUrl, '_blank'); });
  $('addrHelp').addEventListener('click', (e) => {
    if (NATIVE && NATIVE.openHelp) { e.preventDefault(); NATIVE.openHelp('address'); }
    else { e.preventDefault(); location.href = 'help.html?lang=' + (window.I18N ? I18N.lang : 'en') + '#address'; }
  });
  if (readOnly) $('logout').textContent = t('Sign out');
  if (NATIVE) {
    $('logout').textContent = t('System configuration & info');
    $('logout').addEventListener('click', () => NATIVE.openSettings());
  } else {
    $('logout').addEventListener('click', async () => { await api('/api/logout', { method: 'POST' }).catch(() => {}); location.href = loginUrl(); });
  }

  async function refresh() {
    try { await loadDevices(); if (historyOn && !playTimer) await loadHistory(); } catch (e) { console.error(e); }
  }
  // Die App ruft das nach einem Bluetooth-Akku-Scan auf.
  window.reloadDevices = () => loadDevices().catch(console.error);
  // Keine Aktualisierung im Minutentakt: neu geladen wird, wenn die Seite wieder sichtbar wird.
  document.addEventListener('visibilitychange', () => { if (!document.hidden) loadDevices().catch(console.error); });

  if (range.h === 'custom') range.h = '168';
  markRange();
  refresh();
})();
