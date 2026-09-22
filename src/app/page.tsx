'use client';

import { useState, useEffect, useCallback, useRef } from 'react';

// ====== CONSTANTS ======
const PRAYER_NAMES: Record<string, string> = {
  Fajr: 'الفجر', Sunrise: 'الشروق', Dhuhr: 'الظهر',
  Asr: 'العصر', Maghrib: 'المغرب', Isha: 'العشاء',
};
const PRAYER_ORDER = ['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'];
const DAY_NAMES = ['الأَحَدُ', 'الاِثْنَيْنُ', 'الثُّلَاثَاءُ', 'الأَرْبِعَاءُ', 'الخَمِيسُ', 'الجُمُعَةُ', 'السَّبْتُ'];
const HIJRI_MONTHS = ['المحرّم', 'صَفَر', 'رَبيع الأوَّل', 'رَبيع الآخر', 'جُمادى الأولى', 'جُمادى الآخرة', 'رَجَب', 'شَعبان', 'رَمَضان', 'شَوَّال', 'ذو القَعدة', 'ذو الحِجّة'];
const GREG_MONTHS_SHAMI = ['كانون الثاني', 'شباط', 'آذار', 'نيسان', 'أيار', 'حزيران', 'تموز', 'آب', 'أيلول', 'تشرين الأول', 'تشرين الثاني', 'كانون الأول'];
const GREG_MONTHS_GLOBAL = ['يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو', 'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'];
const DEFAULT_IQAMA: Record<string, number> = { Fajr: 30, Dhuhr: 20, Asr: 20, Maghrib: 10, Isha: 10 };
const DEFAULT_LAT = 33.5138;
const DEFAULT_LNG = 36.2765;
const HIJRI_MONTH_DAYS = 29.5306;

// ====== HELPERS ======
function toAr(num: number): string {
  const d = ['٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'];
  return num.toString().split('').map(c => d[parseInt(c)] || c).join('');
}

function fmtDiff(ms: number): string {
  const t = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(t / 3600);
  const m = Math.floor((t % 3600) / 60);
  const s = t % 60;
  return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

function correctHijri(day: number, month: number, year: number, offset: number) {
  const lengths = [30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 29];
  let d = day + offset, m = month - 1, y = year;
  if (d > 0) {
    while (d > lengths[m]) { d -= lengths[m]; m++; if (m > 11) { m = 0; y++; } }
  } else {
    while (d <= 0) { m--; if (m < 0) { m = 11; y--; } d += lengths[m]; }
  }
  return { day: d, month: m + 1, year: y };
}

function calcRamadan(hY: number, hM: number, hD: number, correction: number) {
  const c = correctHijri(hD, hM, hY, correction);
  hM = c.month; hD = c.day; hY = c.year;
  if (hM === 9) return { isRamadan: true, days: 30 - hD };
  let months: number;
  if (hM < 9) months = 9 - hM;
  else months = 12 - hM + 9;
  return { isRamadan: false, days: Math.ceil(months * HIJRI_MONTH_DAYS - hD) };
}

interface AladhanResponse {
  data: {
    timings: Record<string, string>;
    date: {
      hijri: { day: string; month: { number: number; ar: string }; year: string };
      gregorian: { day: string; month: { number: number }; year: string };
    };
  };
}

type PrayerData = AladhanResponse['data'];

interface NextPrayer {
  name: string;
  diffMs: number;
}

export default function Home() {
  const [latitude, setLatitude] = useState(DEFAULT_LAT);
  const [longitude, setLongitude] = useState(DEFAULT_LNG);
  const [isManual, setIsManual] = useState(false);
  const [prayerData, setPrayerData] = useState<PrayerData | null>(null);
  const [prayerTimings, setPrayerTimings] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [locError, setLocError] = useState<string | null>(null);
  const [dateCorrection, setDateCorrection] = useState(0);
  const [iqamaOffsets, setIqamaOffsets] = useState<Record<string, number>>({ ...DEFAULT_IQAMA });
  const [audioEnabled, setAudioEnabled] = useState(false);
  const [audioActivated, setAudioActivated] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [showCredits, setShowCredits] = useState(false);
  const [inputLat, setInputLat] = useState('');
  const [inputLng, setInputLng] = useState('');
  const [now, setNow] = useState(new Date());
  const [online, setOnline] = useState(true);

  const adhanRef = useRef<HTMLAudioElement>(null);
  const iqamaRef = useRef<HTMLAudioElement>(null);
  const adhanPlayedRef = useRef(new Set<string>());
  const iqamaPlayedRef = useRef(new Set<string>());

  // Restore from localStorage
  useEffect(() => {
    const sl = localStorage.getItem('manual-lat');
    const sm = localStorage.getItem('manual-lng');
    if (sl && sm) {
      setLatitude(parseFloat(sl));
      setLongitude(parseFloat(sm));
      setIsManual(true);
    }
    const dc = parseInt(localStorage.getItem('hijri-date-correction') || '0');
    setDateCorrection(dc);
    const io = localStorage.getItem('iqama-offsets');
    if (io) {
      try { setIqamaOffsets({ ...DEFAULT_IQAMA, ...JSON.parse(io) }); } catch {}
    }
    if (localStorage.getItem('audio-enabled') === 'true') {
      setAudioEnabled(true);
      setAudioActivated(true);
    }
  }, []);

  // Fetch prayer times
  const fetchPrayerTimes = useCallback(async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const d = new Date();
      const dd = d.getDate().toString().padStart(2, '0');
      const mm = (d.getMonth() + 1).toString().padStart(2, '0');
      const yyyy = d.getFullYear();
      const url = `https://api.aladhan.com/v1/timings/${dd}-${mm}-${yyyy}?latitude=${latitude}&longitude=${longitude}&method=4`;
      const res = await fetch(url);
      if (!res.ok) throw new Error('فشل في جلب البيانات: ' + res.status);
      const json: AladhanResponse = await res.json();
      const data = json.data;
      setPrayerData(data);
      const timings: Record<string, string> = {};
      ['Fajr', 'Sunrise', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'].forEach(k => {
        const raw = data.timings[k];
        const match = raw ? raw.match(/^(\d{1,2}:\d{2})/) : null;
        timings[k] = match ? match[1] : '';
      });
      setPrayerTimings(timings);
      setLoading(false);
    } catch (e: any) {
      setErrorMsg(e.message || 'فشل في جلب مواقيت الصلاة');
      setLoading(false);
    }
  }, [latitude, longitude]);

  // Request geolocation
  const requestLocation = useCallback(() => {
    setLocError(null);
    if (!navigator.geolocation) {
      setLocError('المتصفح لا يدعم تحديد الموقع');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      pos => {
        setLatitude(pos.coords.latitude);
        setLongitude(pos.coords.longitude);
        setIsManual(false);
        localStorage.removeItem('manual-lat');
        localStorage.removeItem('manual-lng');
        setLocError(null);
      },
      err => {
        const msgs: Record<number, string> = {
          1: 'تم رفض إذن تحديد الموقع - يمكنك إدخال الموقع يدوياً من الإعدادات',
          2: 'الموقع غير متاح - يمكنك إدخال الموقع يدوياً من الإعدادات',
          3: 'انتهت مهلة طلب الموقع - يمكنك إدخال الموقع يدوياً من الإعدادات',
        };
        setLocError(msgs[err.code] || 'فشل في تحديد الموقع');
      },
      { enableHighAccuracy: false, timeout: 15000, maximumAge: 600000 }
    );
  }, []);

  // Auto-fetch on mount
  useEffect(() => {
    if (isManual) {
      fetchPrayerTimes();
    } else {
      requestLocation();
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Re-fetch when location changes
  useEffect(() => {
    if (latitude !== DEFAULT_LAT || longitude !== DEFAULT_LNG) {
      fetchPrayerTimes();
    }
  }, [latitude, longitude]); // eslint-disable-line react-hooks/exhaustive-deps

  // Live clock
  useEffect(() => {
    const interval = setInterval(() => {
      setNow(new Date());
      setOnline(navigator.onLine);
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  // Adhan & Iqama audio check
  useEffect(() => {
    if (!audioEnabled || !audioActivated || !prayerData) return;
    const nowMs = now.getTime();
    for (const k of PRAYER_ORDER) {
      const t = prayerTimings[k];
      if (!t) continue;
      const p = t.split(':').map(Number);
      const d = new Date();
      d.setHours(p[0], p[1], 0, 0);
      const diff = Math.abs(d.getTime() - nowMs) / 1000;
      const pk = k + '-' + now.toDateString();
      if (diff <= 3 && !adhanPlayedRef.current.has(pk)) {
        adhanPlayedRef.current.add(pk);
        playAdhan();
      }
    }
    const ni = findNextIqama(nowMs);
    if (ni) {
      const iKey = ni.name + '-iqama-' + now.toDateString();
      const remS = Math.floor(ni.diffMs / 1000);
      if (remS <= 3 && !iqamaPlayedRef.current.has(iKey)) {
        iqamaPlayedRef.current.add(iKey);
        playIqama();
      }
    }
  }, [now]); // eslint-disable-line react-hooks/exhaustive-deps

  // Clear played set daily
  useEffect(() => {
    const interval = setInterval(() => {
      adhanPlayedRef.current.clear();
      iqamaPlayedRef.current.clear();
    }, 86400000);
    return () => clearInterval(interval);
  }, []);

  function findNext(nowMs: number): NextPrayer | null {
    for (const k of PRAYER_ORDER) {
      const t = prayerTimings[k];
      if (!t) continue;
      const p = t.split(':').map(Number);
      const d = new Date();
      d.setHours(p[0], p[1], 0, 0);
      if (d.getTime() > nowMs) return { name: k, diffMs: d.getTime() - nowMs };
    }
    const ft = prayerTimings['Fajr'];
    if (ft) {
      const p = ft.split(':').map(Number);
      const d = new Date();
      d.setDate(d.getDate() + 1);
      d.setHours(p[0], p[1], 0, 0);
      return { name: 'Fajr', diffMs: d.getTime() - nowMs };
    }
    return null;
  }

  function findNextIqama(nowMs: number): NextPrayer | null {
    for (const k of PRAYER_ORDER) {
      const t = prayerTimings[k];
      if (!t) continue;
      const p = t.split(':').map(Number);
      const d = new Date();
      d.setHours(p[0], p[1], 0, 0);
      const iq = new Date(d.getTime() + (iqamaOffsets[k] || 10) * 60000);
      if (iq.getTime() > nowMs) return { name: k, diffMs: iq.getTime() - nowMs };
    }
    const ft = prayerTimings['Fajr'];
    if (ft) {
      const p = ft.split(':').map(Number);
      const d = new Date();
      d.setDate(d.getDate() + 1);
      d.setHours(p[0], p[1], 0, 0);
      const iq = new Date(d.getTime() + (iqamaOffsets['Fajr'] || 30) * 60000);
      return { name: 'Fajr', diffMs: iq.getTime() - nowMs };
    }
    return null;
  }

  function playAdhan() {
    if (adhanRef.current) {
      adhanRef.current.currentTime = 0;
      adhanRef.current.play().catch(() => {});
      setTimeout(() => { if (adhanRef.current) { adhanRef.current.pause(); adhanRef.current.currentTime = 0; } }, 180000);
    }
  }
  function playIqama() {
    if (iqamaRef.current) {
      iqamaRef.current.currentTime = 0;
      iqamaRef.current.play().catch(() => {});
      setTimeout(() => { if (iqamaRef.current) { iqamaRef.current.pause(); iqamaRef.current.currentTime = 0; } }, 180000);
    }
  }

  function activateAudio() {
    if (adhanRef.current) { adhanRef.current.play().catch(() => {}); adhanRef.current.pause(); adhanRef.current.currentTime = 0; }
    if (iqamaRef.current) { iqamaRef.current.play().catch(() => {}); iqamaRef.current.pause(); iqamaRef.current.currentTime = 0; }
    setAudioActivated(true);
    setAudioEnabled(true);
    localStorage.setItem('audio-enabled', 'true');
  }

  function applyManualLocation() {
    const lat = parseFloat(inputLat);
    const lng = parseFloat(inputLng);
    if (isNaN(lat) || isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) return;
    setLatitude(lat);
    setLongitude(lng);
    setIsManual(true);
    localStorage.setItem('manual-lat', lat.toString());
    localStorage.setItem('manual-lng', lng.toString());
    setLocError(null);
  }

  function resetToAuto() {
    localStorage.removeItem('manual-lat');
    localStorage.removeItem('manual-lng');
    setIsManual(false);
    requestLocation();
  }

  function changeCorrection(delta: number) {
    const newC = dateCorrection + delta;
    setDateCorrection(newC);
    localStorage.setItem('hijri-date-correction', newC.toString());
  }

  function updateIqama(key: string, val: number) {
    const newOffsets = { ...iqamaOffsets, [key]: Math.max(1, Math.min(60, val)) };
    setIqamaOffsets(newOffsets);
    localStorage.setItem('iqama-offsets', JSON.stringify(newOffsets));
  }

  // ====== RENDER ======
  const np = prayerData ? findNext(now.getTime()) : null;
  const ni = prayerData ? findNextIqama(now.getTime()) : null;
  const prayerRows = [['Fajr', 'Sunrise', 'Dhuhr'], ['Asr', 'Maghrib', 'Isha']];

  const h = now.getHours().toString().padStart(2, '0');
  const m = now.getMinutes().toString().padStart(2, '0');
  const s = now.getSeconds().toString().padStart(2, '0');

  // Date rendering
  let dateHtml = '';
  if (prayerData) {
    const hd = prayerData.date.hijri;
    const dayName = DAY_NAMES[now.getDay()];
    let rawDay = parseInt(hd.day), rawMonth = hd.month.number, rawYear = parseInt(hd.year);
    let hDay: string, hMonth: string, corrected = false;
    if (dateCorrection !== 0) {
      const c = correctHijri(rawDay, rawMonth, rawYear, dateCorrection);
      hDay = toAr(c.day); hMonth = HIJRI_MONTHS[c.month - 1]; rawYear = c.year; corrected = true;
    } else {
      hDay = toAr(rawDay); hMonth = hd.month.ar;
    }
    const hYear = toAr(rawYear);
    const gDay = toAr(now.getDate());
    const gMonthShami = GREG_MONTHS_SHAMI[now.getMonth()];
    const gMonthGlobal = GREG_MONTHS_GLOBAL[now.getMonth()];
    const gMonthNum = now.getMonth() + 1;
    const gYear = now.getFullYear();

    dateHtml = `
      <div class="year-row">
        <div class="year-box"><span>${hYear} هجري</span></div>
        <div class="day-name-box"><span>${dayName}</span></div>
        <div class="year-box"><span>${gYear} ميلادي</span></div>
      </div>
      <div class="month-row">
        <div class="month-box"><span>${hMonth} - ${toAr(rawMonth)}</span>${corrected ? '<span class="corrected-tag">مُصحَّح</span>' : ''}</div>
        <div class="month-box"><span>${gMonthShami} - ${gMonthGlobal} - ${gMonthNum}</span></div>
      </div>
      <div class="day-row">
        <div class="day-num">${hDay}</div>
        <div class="day-num-greg">${gDay}</div>
      </div>`;
  }

  // Ramadan rendering
  let ramadanHtml = '';
  if (prayerData) {
    const hd = prayerData.date.hijri;
    const info = calcRamadan(parseInt(hd.year), hd.month.number, parseInt(hd.day), dateCorrection);
    if (info.isRamadan) {
      ramadanHtml = `<div class="header-ramadan ramadan-active"><span class="hr-icon">🌙</span><span class="hr-title">رمضان كريم - متبقّي ${toAr(info.days)} يوم</span></div>`;
    } else {
      ramadanHtml = `<div class="header-ramadan"><span class="hr-icon">🕌</span><span class="hr-title">باقي لرمضان ${toAr(info.days)} يوم</span></div>`;
    }
  }

  // Location message
  let locHtml = '';
  if (isManual) {
    locHtml = `<div class="loc-manual"><span>📍 موقع يدوي</span><div class="coords">${latitude.toFixed(4)}°N, ${longitude.toFixed(4)}°E</div></div>`;
  } else if (locError) {
    locHtml = `<div class="loc-msg"><span>📍 ${locError}</span><div class="coords">${latitude.toFixed(2)}°N, ${longitude.toFixed(2)}°E (دمشق)</div><div class="hint">لتغيير الموقع، افتح الإعدادات ⚙️ وأدخل إحداثياتك يدوياً</div></div>`;
  }

  return (
    <>
      {/* Header */}
      <div className="header">
        <div className="header-inner">
          <h1>🕌 مواقيت الصلاة</h1>
          {prayerData && <div dangerouslySetInnerHTML={{ __html: ramadanHtml }} />}
          <div className="header-btns">
            <span style={{ fontSize: '14px' }}>{online ? '🟢' : '🔴'}</span>
            <button className="icon-btn" onClick={() => { setShowSettings(true); setInputLat(latitude.toFixed(4)); setInputLng(longitude.toFixed(4)); }}>⚙️</button>
          </div>
        </div>
      </div>

      {/* Main */}
      <div className="main">
        {/* Loading */}
        {loading && (
          <div className="loading">
            <div className="spinner" />
            <p style={{ color: '#64748b', fontSize: '14px' }}>جارٍ جلب مواقيت الصلاة...</p>
          </div>
        )}

        {/* Error */}
        {!loading && errorMsg && (
          <div className="error-box">
            <p>{errorMsg}</p>
            <button className="retry-btn" onClick={fetchPrayerTimes}>🔄 إعادة المحاولة</button>
          </div>
        )}

        {/* Content */}
        {!loading && !errorMsg && prayerData && (
          <>
            <div dangerouslySetInnerHTML={{ __html: locHtml }} />

            {/* Date */}
            <div className="date-section" dangerouslySetInnerHTML={{ __html: dateHtml }} />

            {/* Time + Countdowns */}
            <div className="time-row">
              <div className="current-time">
                <div className="clock">{h}:{m}:{s}</div>
              </div>
              <div className="cd-box cd-next">
                <div className="label">باقي لصلاة {np ? PRAYER_NAMES[np.name] : '--'}</div>
                <div className="time">{np ? fmtDiff(np.diffMs) : '--:--:--'}</div>
              </div>
              <div className="cd-box cd-iqama">
                <div className="label">باقي لإقامة {ni ? PRAYER_NAMES[ni.name] : '--'}</div>
                <div className="time">{ni ? fmtDiff(ni.diffMs) : '--:--:--'}</div>
              </div>
            </div>

            {/* Prayer Grid */}
            <div className="prayer-section">
              {prayerRows.map((row, ri) => (
                <div className="prayer-row" key={ri}>
                  {row.map(k => {
                    const active = np && np.name === k;
                    return (
                      <div className={`prayer-cell${active ? ' active' : ''}`} key={k}>
                        <div className="name">{PRAYER_NAMES[k]}</div>
                        <div className="ptime">{prayerTimings[k] || '--:--'}</div>
                        {active && <div className="next-tag">● الصلاة القادمة</div>}
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      {/* Audio Float */}
      {!audioActivated && !audioEnabled && (
        <div className="audio-float">
          <button onClick={activateAudio}>🔊 تفعيل صوت الأذان</button>
        </div>
      )}

      {/* Hidden Audio */}
      <audio ref={adhanRef} src="/adhan.mp3" preload="auto" />
      <audio ref={iqamaRef} src="/iqama.mp3" preload="auto" />

      {/* EHE Button */}
      <EHEButton onClick={() => setShowCredits(true)} />

      {/* Credits Modal */}
      <div className={`credits-modal ${showCredits ? 'show' : ''}`} onClick={(e) => { if (e.currentTarget === e.target) setShowCredits(false); }}>
        <div className="credits-content">
          <div className="icon">🕌</div>
          <h2>فريق العمل</h2>
          <div className="credits-list">
            <div className="credits-item"><div className="role">💻 البرمجة والتصميم</div><div className="name">د. إحسان العبد الله</div></div>
            <div className="credits-item"><div className="role">🎨 الغرافيك</div><div className="name">منال برغل</div></div>
            <div className="credits-item"><div className="role">💡 الأفكار</div><div className="name">قيس وإياد وعلي العبد الله</div></div>
          </div>
          <button className="credits-close" onClick={() => setShowCredits(false)}>إغلاق</button>
        </div>
      </div>

      {/* Settings Modal */}
      <div className={`modal-overlay ${showSettings ? 'show' : ''}`} onClick={(e) => { if (e.currentTarget === e.target) setShowSettings(false); }}>
        <div className="modal">
          <button className="modal-close" onClick={() => setShowSettings(false)}>✕</button>
          <h2>⚙️ الإعدادات</h2>

          {/* Location */}
          <div className="modal-section">
            <div className="sec-title">📍 الموقع {isManual && <span style={{ display: 'inline', fontSize: '10px', background: '#fef3c7', color: '#d97706', padding: '2px 8px', borderRadius: '12px' }}>يدوي</span>}</div>
            <div className="setting-box">
              <span>خط العرض: <strong style={{ fontFamily: 'monospace' }}>{latitude.toFixed(4)}</strong></span>
              <span>خط الطول: <strong style={{ fontFamily: 'monospace' }}>{longitude.toFixed(4)}</strong></span>
            </div>
            <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '8px' }}>أدخل الإحداثيات يدوياً (اختياري):</div>
            <div className="loc-inputs">
              <div style={{ flex: 1 }}><label>خط العرض</label><input type="number" step="0.0001" value={inputLat} onChange={(e) => setInputLat(e.target.value)} /></div>
              <div style={{ flex: 1 }}><label>خط الطول</label><input type="number" step="0.0001" value={inputLng} onChange={(e) => setInputLng(e.target.value)} /></div>
            </div>
            <div className="btn-row">
              <button className="btn btn-outline btn-sm" onClick={applyManualLocation}>📍 تطبيق</button>
              {isManual && <button className="btn btn-outline btn-sm" onClick={resetToAuto}>🔄 تلقائي</button>}
              <button className="btn btn-outline btn-sm" onClick={requestLocation}>📍 إعادة طلب</button>
            </div>
          </div>

          {/* Hijri Correction */}
          <div className="modal-section">
            <div className="sec-title">📅 تصحيح التاريخ الهجري</div>
            <div className="sec-desc">أضف أو اطرح أيام لتصحيح التاريخ الهجري</div>
            <div className="setting-box">
              <span>تصحيح (أيام):</span>
              <div className="correction-controls">
                <button className="minus-btn" onClick={() => changeCorrection(-1)}>−</button>
                <span className="val">{dateCorrection > 0 ? '+' : ''}{dateCorrection}</span>
                <button className="plus-btn" onClick={() => changeCorrection(1)}>+</button>
              </div>
            </div>
          </div>

            {/* Iqama Offsets */}
          <div className="modal-section">
            <div className="sec-title">🕌 فواصل الإقامة (بالدقائق)</div>
            {PRAYER_ORDER.map(k => (
              <div className="setting-box" key={k}>
                <span>{PRAYER_NAMES[k]}</span>
                <input type="number" min="1" max="60" value={iqamaOffsets[k]} onChange={(e) => updateIqama(k, parseInt(e.target.value))}/>
              </div>
            ))}
          </div>

          {/* Audio */}
          <div className="modal-section">
            <div className="setting-box">
              <span className="sec-title" style={{ margin: 0 }}>🔊 الإشعارات الصوتية</span>
              <button
                className={audioEnabled ? 'audio-on' : 'audio-off'}
                onClick={() => {
                  const newState = !audioEnabled;
                  setAudioEnabled(newState);
                  localStorage.setItem('audio-enabled', newState ? 'true' : 'false');
                }}
              >
                {audioEnabled ? '🔊 مفعّل' : '🔇 معطّل'}
              </button>
            </div>
          </div>

          {/* Buttons */}
          <div className="btn-row">
            <button className="btn btn-outline" onClick={() => { setIqamaOffsets({ ...DEFAULT_IQAMA }); localStorage.setItem('iqama-offsets', JSON.stringify(DEFAULT_IQAMA)); }}>إعادة تعيين الفواصل</button>
            <button className="btn btn-primary" onClick={() => { fetchPrayerTimes(); setShowSettings(false); }}>تحديث المواقيت</button>
          </div>
        </div>
      </div>
    </>
  );
}

// ====== EHE Draggable Button ======
function EHEButton({ onClick }: { onClick: () => void }) {
  const btnRef = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    const btn = btnRef.current;
    if (!btn) return;
    let isDragging = false;
    let startX = 0, startY = 0, startLeft = 0, startTop = 0;
    let hasMoved = false;

    const saved = localStorage.getItem('ehe-pos');
    if (saved) {
      try {
        const pos = JSON.parse(saved);
        btn.style.left = pos.left;
        btn.style.top = pos.top;
        btn.style.right = 'auto';
      } catch {}
    }

    function onStart(e: MouseEvent | TouchEvent) {
      const touch = (e as TouchEvent).touches ? (e as TouchEvent).touches[0] : e as MouseEvent;
      isDragging = true;
      hasMoved = false;
      startX = touch.clientX;
      startY = touch.clientY;
      const rect = btn!.getBoundingClientRect();
      startLeft = rect.left;
      startTop = rect.top;
      btn!.style.left = startLeft + 'px';
      btn!.style.top = startTop + 'px';
      btn!.style.right = 'auto';
      e.preventDefault();
    }

    function onMove(e: MouseEvent | TouchEvent) {
      if (!isDragging || !btn) return;
      const touch = (e as TouchEvent).touches ? (e as TouchEvent).touches[0] : e as MouseEvent;
      const dx = touch.clientX - startX;
      const dy = touch.clientY - startY;
      if (Math.abs(dx) > 4 || Math.abs(dy) > 4) { hasMoved = true; setDragging(true); }
      const btnSize = 50;
      btn.style.left = Math.max(0, Math.min(window.innerWidth - btnSize, startLeft + dx)) + 'px';
      btn.style.top = Math.max(0, Math.min(window.innerHeight - btnSize, startTop + dy)) + 'px';
    }

    function onEnd() {
      if (!isDragging || !btn) return;
      isDragging = false;
      setDragging(false);
      localStorage.setItem('ehe-pos', JSON.stringify({ left: btn.style.left, top: btn.style.top }));
      if (!hasMoved) onClick();
    }

    btn.addEventListener('mousedown', onStart);
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onEnd);
    btn.addEventListener('touchstart', onStart, { passive: false });
    document.addEventListener('touchmove', onMove, { passive: false });
    document.addEventListener('touchend', onEnd);

    return () => {
      btn.removeEventListener('mousedown', onStart);
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onEnd);
      btn.removeEventListener('touchstart', onStart);
      document.removeEventListener('touchmove', onMove);
      document.removeEventListener('touchend', onEnd);
    };
  }, [onClick]);

  return (
    <div ref={btnRef} className={`ehe-btn ${dragging ? 'dragging' : ''}`}>ehe</div>
  );
}
