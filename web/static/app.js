(() => {
  const $ = (id) => document.getElementById(id);
  const t = window.t || ((s, v) => (v ? s.replace(/\{(\w+)\}/g, (m, k) => v[k] ?? m) : s));
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch {} },
  };

  const map = L.map('map', { preferCanvas: true, zoomControl: false }).setView([20, 0], 2);
  L.control.zoom({ position: 'topright' }).addTo(map);

  const gsub = { subdomains: ['mt0', 'mt1', 'mt2', 'mt3'], maxZoom: 21, attribution: '&copy; Google' };
  const bases = {
    'OpenStreetMap': { layer: L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; OpenStreetMap contributors' }), imagery: false },
    'Google Streets': { layer: L.tileLayer('https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', gsub), imagery: false },
    'Google Satellite': { layer: L.tileLayer('https://{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}', gsub), imagery: true },
    'Google Hybrid': { layer: L.tileLayer('https://{s}.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', gsub), imagery: true },
    [t('Satellite (Esri)')]: { layer: L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 19, attribution: 'Imagery &copy; Esri, Maxar' }), imagery: true },
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

  // "Center on tag" control
  const Recenter = L.Control.extend({
    options: { position: 'topright' },
    onAdd() {
      const b = L.DomUtil.create('button', 'leaflet-bar recenter-btn');
      b.innerHTML = '◎'; b.title = t('Center on tag');
      L.DomEvent.on(b, 'click', (ev) => {
        L.DomEvent.stop(ev);
        if (deviceId === ALL) { fitAll(); return; }
        const d = devices.find((x) => x.id === deviceId);
        if (d && d.last_lat != null) map.setView([d.last_lat, d.last_lon], 16);
      });
      return b;
    },
  });
  map.addControl(new Recenter());

  const layers = { all: L.layerGroup().addTo(map), track: L.layerGroup().addTo(map), points: L.layerGroup().addTo(map), live: L.layerGroup().addTo(map), play: L.layerGroup().addTo(map) };
  const ALL = '__all';
  const PALETTE = ['#2563eb', '#dc2626', '#16a34a', '#d97706', '#9333ea', '#0891b2', '#db2777', '#65a30d', '#ea580c', '#4f46e5', '#0d9488', '#b91c1c', '#7c3aed', '#ca8a04', '#059669', '#be185d'];
  // Eigene Farbe je Tag: gespeicherte Farbe, sonst feste Farbe aus der Palette (nach Position).
  const colorOf = (d) => (d && d.color && d.color !== '#2563eb' ? d.color : PALETTE[Math.max(0, devices.indexOf(d)) % PALETTE.length]);
  let devices = [], deviceId = store.get('device') || ALL, range = { h: store.get('range') || '168' };
  let points = [], fitted = false, playTimer = null;

  // Runs against either the web server (fetch) or, inside the Android app, the Native bridge.
  const NATIVE = window.Native;

  async function api(path, opts = {}) {
    const r = await fetch(path, { credentials: 'same-origin', ...opts });
    if (r.status === 401) { location.href = '/login'; throw new Error('logged out'); }
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
    setParked: (on) => NATIVE ? NATIVE.setParked(on)
      : api('/api/guard', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ parked: on }) }),
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

  // ---------- Akkustand (per Bluetooth von der App gelesen: 1 normal, 2 schwach, 3 kritisch) ----------
  const BATT = { 1: ['🟢', 'normal'], 2: ['🟡', 'low'], 3: ['🔴', 'critical – replace soon'] };
  const battDot = (d) => (d.battery && BATT[d.battery.level] ? BATT[d.battery.level][0] + ' ' : '');
  function battLine(d) {
    const b = d.battery;
    const btn = NATIVE && NATIVE.checkBatteries ? ` <button type="button" class="batt-check">${esc(t('Check battery'))}</button>` : '';
    if (b && BATT[b.level]) {
      const [dot, label] = BATT[b.level];
      return `<br><span class="batt l${b.level}">${dot} ${esc(t('Battery'))}: <span class="lvl">${esc(t(label))}</span>`
        + ` <span class="muted">· ${esc(t('measured {ago}', { ago: ago(b.ts) }))}</span>${btn}</span>`;
    }
    if (!NATIVE) return '';
    const why = b && b.level === 0 ? t('no data from this tag') : t('not measured yet – bring the tag near your phone');
    return `<br><span class="batt">🔋 ${esc(t('Battery'))}: <span class="muted">${esc(why)}</span>${btn}</span>`;
  }
  document.addEventListener('click', (e) => {
    if (e.target.closest && e.target.closest('.batt-check') && NATIVE && NATIVE.checkBatteries) NATIVE.checkBatteries();
  });

  // Reverse-geocode a point to a short address (OpenStreetMap Nominatim), cached per ~11m.
  const addrCache = {};
  async function addressFor(lat, lon) {
    // Never send raw location coordinates for reverse-geocoding without consent.
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

  function currentRange() {
    const now = Math.floor(Date.now() / 1000);
    if (range.h === 'today') { const d = new Date(); d.setHours(0, 0, 0, 0); return [Math.floor(d / 1000), now]; }
    if (range.h === 'custom') return [range.start, range.end];
    return [now - Number(range.h) * 3600, now];
  }

  // ---------- devices ----------
  async function loadDevices() {
    const res = await data.devices();
    devices = res.devices;
    const sel = $('device');
    sel.innerHTML = devices.length ? `<option value="${ALL}">🗺 ${esc(t('All tags'))} (${devices.length})</option>` + devices.map((d) => `<option value="${esc(d.id)}">${battDot(d)}${esc(d.name)}</option>`).join('')
      : `<option value="">${esc(t('No tags yet – waiting for first check'))}</option>`;
    if (deviceId !== ALL && !devices.find((d) => d.id === deviceId)) deviceId = devices.length ? ALL : null;
    if (deviceId) sel.value = deviceId;
    renderLatest();
    renderAll();
    const p = res.poller;
    $('pollStatus').textContent = !p.enabled ? t('Automatic checking is off on this server.')
      : p.running ? t('Checking Google for new locations…')
      : p.last_error ? t('Last check failed: {e}', { e: p.last_error })
      : p.last_run ? t('Last checked {ago} · every {n} min', { ago: ago(p.last_run), n: p.interval_minutes }) : t('Starting…');
    renderSync(res.backup);
    renderPark(res.guard);
    renderLicense(res.license);
  }

  let licensed = true;
  function openLicense() {
    if (NATIVE && NATIVE.openLicense) NATIVE.openLicense();
    
  }
  function renderLicense(lic) {
    // Server (no license field) = full features. Only the phone app gates.
    licensed = !lic || lic.licensed !== false;
    const el = $('pro');
    if (!el) return;
    el.hidden = licensed;
    
    // lock history ranges beyond the free window
    document.querySelectorAll('#ranges button').forEach((b) => {
      const free = b.dataset.h === 'today' || b.dataset.h === '24';
      b.classList.toggle('locked', !licensed && !free);
    });
  }

  let parked = false;
  function renderPark(on) {
    parked = !!on;
    const el = $('park');
    if (!el) return;
    el.classList.toggle('armed', parked);
    el.classList.toggle('riding', !parked);
    el.textContent = parked ? t('🔒 Parked & guarded — tap when you ride') : t('🅿️ Park & guard');
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

  function renderLatest() {
    const d = devices.find((x) => x.id === deviceId);
    const el = $('latest');
    layers.live.clearLayers();
    if (deviceId === ALL) { renderOverview(el); return; }
    if (!d) { el.innerHTML = `<span class="muted">${esc(t('No tag selected.'))}</span>`; return; }
    if (d.last_ts == null) {
      el.innerHTML = `<b>${esc(d.name)}</b><br><span class="muted">${esc(t('No locations stored yet.'))}</span>`
        + battLine(d) + (d.last_error ? `<br><span class="error">${esc(d.last_error)}</span>` : '');
      return;
    }
    el.innerHTML = `<b>${esc(d.name)}</b><br>${esc(t('Last seen at {t}', { t: fmt(d.last_ts) }))} <span class="muted">(${ago(d.last_ts)})</span><br>`
      + `<span id="addr" class="muted">${esc(t('📍 finding address…'))}</span><br>`
      + `<span class="muted">${esc(t('{n} locations stored in total', { n: d.point_count.toLocaleString() }))}</span>`
      + battLine(d)
      + (d.last_error ? `<br><span class="error small">${esc(d.last_error)}</span>` : '');
    addressFor(d.last_lat, d.last_lon).then((a) => {
      const ae = $('addr'); if (ae) ae.textContent = a ? '📍 ' + a : `📍 ${d.last_lat.toFixed(5)}, ${d.last_lon.toFixed(5)}`;
    });
    const ll = [d.last_lat, d.last_lon];
    if (d.last_accuracy) L.circle(ll, { radius: d.last_accuracy, color: colorOf(d), weight: 1, fillOpacity: 0.12 }).addTo(layers.live);
    L.circleMarker(ll, { radius: 9, color: '#fff', weight: 3, fillColor: colorOf(d), fillOpacity: 1 })
      .bindPopup(`<div class="popup"><b>${esc(t('{name} – latest', { name: d.name }))}</b>${fmt(d.last_ts)}<br>`
        + `<a target="_blank" rel="noopener" href="https://www.google.com/maps?q=${d.last_lat},${d.last_lon}">${esc(t('Open in Google Maps'))}</a></div>`)
      .addTo(layers.live);
  }

  // ---------- alle Tags gleichzeitig ----------
  function selectDevice(id) {
    deviceId = id; store.set('device', id); fitted = false;
    $('device').value = id;
    renderLatest(); renderAll(); loadHistory().catch(alert);
  }
  function renderAll() {
    layers.all.clearLayers();
    devices.forEach((d) => {
      if (d.last_lat == null) return;
      const sel = d.id === deviceId;
      if (!sel && deviceId !== ALL) {
        // andere Tags klein und blass im Hintergrund
        L.circleMarker([d.last_lat, d.last_lon], { radius: 5, color: '#fff', weight: 1, fillColor: colorOf(d), fillOpacity: 0.55 })
          .bindTooltip(esc(d.name), { direction: 'top' })
          .on('click', () => selectDevice(d.id)).addTo(layers.all);
        return;
      }
      if (sel) return; // der gewählte Tag wird in renderLatest gezeichnet
      L.circleMarker([d.last_lat, d.last_lon], { radius: 9, color: '#fff', weight: 3, fillColor: colorOf(d), fillOpacity: 1 })
        .bindTooltip(`${battDot(d)}${esc(d.name)}`, { permanent: true, direction: 'right', offset: [10, 0], className: 'tag-label' })
        .on('click', () => selectDevice(d.id)).addTo(layers.all);
    });
  }
  function fitAll() {
    const lls = devices.filter((d) => d.last_lat != null).map((d) => [d.last_lat, d.last_lon]);
    if (lls.length === 1) map.setView(lls[0], 15);
    else if (lls.length) map.fitBounds(L.latLngBounds(lls).pad(0.2), { maxZoom: 16 });
  }
  function renderOverview(el) {
    const list = devices.slice().sort((a, b) => (b.last_ts || 0) - (a.last_ts || 0));
    el.innerHTML = `<b>${esc(t('All tags'))}</b> <span class="muted">· ${esc(t('tap a tag for its history'))}</span>`
      + '<div class="ov">' + list.map((d) => {
        const b = d.battery && BATT[d.battery.level];
        const seen = d.last_ts != null ? `${fmt(d.last_ts)} <span class="muted">(${ago(d.last_ts)})</span>` : `<span class="muted">${esc(t('No locations stored yet.'))}</span>`;
        return `<button type="button" class="ov-row" data-id="${esc(d.id)}"><span class="ov-dot" style="background:${colorOf(d)}"></span>`
          + `<span class="ov-main"><span class="ov-name">${esc(d.name)}</span><span class="ov-seen">${seen}</span></span>`
          + `<span class="ov-batt" title="${b ? esc(t('Battery') + ': ' + t(b[1])) : ''}">${b ? b[0] : ''}</span></button>`;
      }).join('') + '</div>';
    el.querySelectorAll('.ov-row').forEach((r) => r.addEventListener('click', () => selectDevice(r.dataset.id)));
  }

  // ---------- history ----------
  async function loadHistory() {
    stopPlay();
    layers.track.clearLayers(); layers.points.clearLayers(); layers.play.clearLayers();
    if (!deviceId || deviceId === ALL) {
      points = []; renderStats();
      if (deviceId === ALL && !fitted) { fitAll(); fitted = true; }
      return;
    }
    const [start, end] = currentRange();
    const res = await data.history(deviceId, start, end);
    points = res.points.filter((p) => p.lat != null);
    const color = colorOf(devices.find((d) => d.id === deviceId));
    const lls = points.map((p) => [p.lat, p.lon]);
    if (lls.length > 1) L.polyline(lls, { color, weight: 3, opacity: 0.75 }).addTo(layers.track);
    if ($('showPoints').checked) {
      points.forEach((p, i) => {
        L.circleMarker([p.lat, p.lon], { radius: 4, color, weight: 1, fillColor: i === 0 ? '#16a34a' : '#fff', fillOpacity: 1 })
          .bindPopup(`<div class="popup"><b>${fmt(p.ts)}</b>${p.accuracy ? `± ${Math.round(p.accuracy)} m<br>` : ''}`
            + `${p.is_own_report ? t('Reported by your own phone') : t('Reported by Find Hub network')}<br>`
            + `<span class="muted">${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}</span></div>`)
          .addTo(layers.points);
      });
    }
    if (!fitted) {
      const bounds = lls.length ? L.latLngBounds(lls) : null;
      const d = devices.find((x) => x.id === deviceId);
      if (bounds) map.fitBounds(bounds.pad(0.15), { maxZoom: 16 });
      else if (d?.last_lat != null) map.setView([d.last_lat, d.last_lon], 15);
      fitted = true;
    }
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

  // ---------- playback ----------
  function showAt(i) {
    layers.play.clearLayers();
    const p = points[i];
    if (!p) return;
    const color = colorOf(devices.find((d) => d.id === deviceId));
    L.polyline(points.slice(0, i + 1).map((q) => [q.lat, q.lon]), { color: '#f59e0b', weight: 5, opacity: 0.9 }).addTo(layers.play);
    L.circleMarker([p.lat, p.lon], { radius: 10, color: '#fff', weight: 3, fillColor: color, fillOpacity: 1 }).addTo(layers.play);
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

  // ---------- controls ----------
  function markRange() {
    document.querySelectorAll('#ranges button').forEach((b) => b.classList.toggle('on', b.dataset.h === range.h));
    $('custom').hidden = range.h !== 'custom';
  }
  $('ranges').addEventListener('click', (e) => {
    const h = e.target.dataset?.h; if (!h) return;
    if (!licensed && !(h === 'today' || h === '24')) { openLicense(); return; }
    range = { h }; markRange();
    if (h === 'custom') {
      const toLocal = (d) => new Date(d - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
      $('to').value ||= toLocal(new Date()); $('from').value ||= toLocal(new Date(Date.now() - 7 * 86400000));
      return;
    }
    store.set('range', h); fitted = false; loadHistory().catch(alert);
  });
  $('applyCustom').addEventListener('click', () => {
    const s = new Date($('from').value), en = new Date($('to').value);
    if (isNaN(s) || isNaN(en) || s >= en) return alert(t('Pick a valid date range.'));
    range = { h: 'custom', start: Math.floor(s / 1000), end: Math.floor(en / 1000) };
    fitted = false; loadHistory().catch(alert);
  });
  $('device').addEventListener('change', (e) => {
    const d = devices.find((x) => x.id === e.target.value);
    if (d && d.locked) { openLicense(); e.target.value = deviceId; return; }
    selectDevice(e.target.value);
  });
  $('showPoints').addEventListener('change', () => loadHistory().catch(alert));
  if ($('onlineAddress')) {
    $('onlineAddress').checked = store.get('allowAddressLookup') === '1';
    $('onlineAddress').addEventListener('change', (e) => {
      store.set('allowAddressLookup', e.target.checked ? '1' : '0');
      renderLatest();
    });
  }
  $('toggle').addEventListener('click', () => $('panel').classList.toggle('collapsed'));
  $('pollNow').addEventListener('click', async () => {
    try { await data.pollNow(); $('pollStatus').textContent = t('Checking Google for new locations…'); setTimeout(refresh, 15000); }
    catch (e) { alert(e.message); }
  });
  const download = (f) => {
    if (!needTag()) return;
    if (!licensed) { openLicense(); return; }
    const [start, end] = currentRange();
    if (NATIVE) NATIVE.export(deviceId, start, end, f);
    else location.href = `/api/history/${encodeURIComponent(deviceId)}?start=${start}&end=${end}&format=${f}`;
  };
  $('csv').addEventListener('click', () => download('csv'));
  $('gpx').addEventListener('click', () => download('gpx'));

  const needTag = () => { if (!deviceId || deviceId === ALL) { alert(t('Pick a tag first (dropdown or map).')); return false; } return true; };
  async function locateNow() {
    if (deviceId === ALL) { try { await data.pollNow(); $('pollStatus').textContent = t('Checking Google for new locations…'); setTimeout(refresh, 15000); } catch (e) { alert(e.message); } return; }
    if (!deviceId) return;
    const btn = $('locate');
    btn.disabled = true; btn.textContent = t('📍 Locating…');
    $('pollStatus').textContent = t('Asking the tag for its current location…');
    const done = () => { btn.disabled = false; btn.textContent = t('📍 Locate now'); };
    try {
      await data.locate(deviceId);
    } catch (e) { $('pollStatus').textContent = e.message; done(); return; }
    if (NATIVE) {
      // The app fetches in the background and returns at once; poll for the saved point.
      let t = 0;
      const iv = setInterval(async () => { await refresh(); if (++t >= 8) { clearInterval(iv); done(); } }, 4000);
    } else {
      await refresh(); done();
    }
  }
  $('park').addEventListener('click', async () => {
    if (!licensed) { openLicense(); return; }  // theft/guard alerts are a Pro (key) feature
    const next = !parked;
    renderPark(next); // optimistic
    try { await data.setParked(next); } catch (e) { alert(e.message); }
    $('pollStatus').textContent = next
      ? t('Guarding: you’ll be alerted if the tag moves.')
      : t('Riding mode: movement alerts are off.');
    refresh();
  });
  $('locate').addEventListener('click', locateNow);
  $('ring').addEventListener('click', async () => {
    if (!needTag()) return;
    try { await data.sound(deviceId); $('pollStatus').textContent = t('Ringing the tag…'); }
    catch (e) { alert(e.message); }
  });
  $('share').addEventListener('click', async () => {
    if (!needTag()) return;
    const d = devices.find((x) => x.id === deviceId);
    if (!d || d.last_lat == null) { alert(t('No location yet to share.')); return; }
    const addr = await addressFor(d.last_lat, d.last_lon);
    const text = t('{name} — last seen {t}', { name: d.name, t: fmt(d.last_ts) }) + `\n${addr ? addr + '\n' : ''}`
      + `https://www.google.com/maps?q=${d.last_lat},${d.last_lon}`;
    if (NATIVE && NATIVE.share) NATIVE.share(text);
    else if (navigator.share) { try { await navigator.share({ title: d.name, text }); } catch (e) {} }
    else { try { await navigator.clipboard.writeText(text); alert(t('Location copied to clipboard')); } catch (e) { prompt(t('Copy:'), text); } }
  });
  $('rename').addEventListener('click', async () => {
    if (!needTag()) return;
    const d = devices.find((x) => x.id === deviceId); if (!d) return;
    const name = prompt(t('New name for this tag'), d.name); if (!name) return;
    await data.rename(d.id, name);
    refresh();
  });
  if (window.TagTrackerApp) {
    $('server').hidden = false;
    $('server').addEventListener('click', () => window.TagTrackerApp.changeServer());
  }
  if (NATIVE) {
    // In the standalone app there is no login; "Sign out" becomes "Settings".
    $('logout').textContent = t('Settings');
    $('logout').addEventListener('click', () => NATIVE.openSettings());
  } else {
    $('logout').addEventListener('click', async () => { await api('/api/logout', { method: 'POST' }); location.href = '/login'; });
  }

  async function refresh() {
    try { await loadDevices(); if (!playTimer) await loadHistory(); } catch (e) { console.error(e); }
  }
  // Die App ruft das nach einem Bluetooth-Akku-Scan auf.
  window.reloadDevices = () => loadDevices().catch(console.error);
  setInterval(() => { if ($('autoRefresh').checked && !document.hidden) refresh(); }, 60000);

  if (range.h === 'custom') range.h = '168';
  markRange();
  refresh();
})();
