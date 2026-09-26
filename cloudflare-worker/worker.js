/**
 * Cloudflare Worker — Prayer Times Backend
 * ============================================================
 * يقوم بدور وسيط آمن بين تطبيق مواقيت الصلاة و GitHub:
 *   - المستخدمون يسجلون بالبريد/الهاتف + كلمة مرور
 *   - كل مستخدم يحصل على JWT فريد
 *   - الـ Worker يستخدم GitHub PAT المخزّن كمتغيّر سرّي
 *   - يمكن إلغاء أي مستخدم دون غيره
 *   - المستخدمون لا يرون PAT أبداً
 *
 * التخزين: Cloudflare D1 (قاعدة بيانات SQL)
 *   جدول users: (id, email, password_hash, name, is_active, is_admin, created_at, last_login)
 *
 * متغيرات بيئة مطلوبة (Wrangler Secrets):
 *   - GH_PAT: GitHub Personal Access Token (Fine-grained على prayer-times)
 *   - GH_OWNER: ehessan1974-maker
 *   - GH_REPO: prayer-times
 *   - GH_PATH: content.json
 *   - GH_BRANCH: main
 *   - JWT_SECRET: سلسلة عشوائية لتوقيع JWT
 *   - ADMIN_EMAIL: بريد المدير (يحصل على صلاحية is_admin=true تلقائياً)
 *
 * Endpoints:
 *   POST /register     {email, password, name}        → {token, user}
 *   POST /login        {email, password}              → {token, user}
 *   GET  /me           [Authorization: Bearer <token>] → {user}
 *   GET  /messages                                     → content.json
 *   POST /messages     {messages}                     → {success, sha}
 *   GET  /admin/users  [admin token]                  → {users: [...]}
 *   POST /admin/revoke {email}   [admin token]        → {success}
 *   POST /admin/restore {email} [admin token]        → {success}
 *   POST /api/tg-report {text}                       → {success}  (v25.37: وسيط تيليجرام)
 *
 * v25.37: أُضيف /api/tg-report — وسيط آمن لإرسال تقارير الأجهزة إلى تيليجرام
 *   دون كشف توكن البوت في كود التطبيق العام. متغيرات سرّية مطلوبة:
 *   - TG_BOT_TOKEN: توكن البوت من @BotFather
 *   - TG_CHAT_ID: معرّف محادثة المدير
 */

// ===== Utilities =====

function jsonResponse(data, status = 200, extra = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      ...extra,
    },
  });
}

function cors(response) {
  response.headers.set('Access-Control-Allow-Origin', '*');
  response.headers.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  response.headers.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  return response;
}

// تجزئة كلمة المرور باستخدام PBKDF2 + salt عشوائي
async function hashPassword(password, saltHex) {
  const salt = saltHex
    ? hexToBytes(saltHex)
    : crypto.getRandomValues(new Uint8Array(16));
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    'raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']
  );
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt, iterations: 100000, hash: 'SHA-256' },
    keyMaterial, 256
  );
  return bytesToHex(salt) + ':' + bytesToHex(new Uint8Array(bits));
}

async function verifyPassword(password, storedHash) {
  const [saltHex, expectedHash] = storedHash.split(':');
  if (!saltHex || !expectedHash) return false;
  const computed = await hashPassword(password, saltHex);
  return computed === storedHash;
}

function hexToBytes(hex) {
  const arr = new Uint8Array(hex.length / 2);
  for (let i = 0; i < arr.length; i++) {
    arr[i] = parseInt(hex.substr(i * 2, 2), 16);
  }
  return arr;
}

function bytesToHex(arr) {
  return Array.from(arr).map(b => b.toString(16).padStart(2, '0')).join('');
}

// توليد JWT موقّع بـ HMAC-SHA256
async function signJWT(payload, secret) {
  const enc = new TextEncoder();
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const fullPayload = { ...payload, iat: now, exp: now + 30 * 24 * 3600 }; // 30 يوم

  const headerB64 = base64UrlEncode(enc.encode(JSON.stringify(header)));
  const payloadB64 = base64UrlEncode(enc.encode(JSON.stringify(fullPayload)));
  const data = `${headerB64}.${payloadB64}`;

  const key = await crypto.subtle.importKey(
    'raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const sig = await crypto.subtle.sign('HMAC', key, enc.encode(data));
  const sigB64 = base64UrlEncode(new Uint8Array(sig));

  return `${data}.${sigB64}`;
}

async function verifyJWT(token, secret) {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const [headerB64, payloadB64, sigB64] = parts;
  const data = `${headerB64}.${payloadB64}`;

  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['verify']
  );
  const sigBytes = base64UrlDecode(sigB64);
  if (!sigBytes) return null;

  const valid = await crypto.subtle.verify('HMAC', key, sigBytes, enc.encode(data));
  if (!valid) return null;

  try {
    const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(payloadB64)));
    if (payload.exp && Math.floor(Date.now() / 1000) > payload.exp) return null;
    return payload;
  } catch (e) {
    return null;
  }
}

function base64UrlEncode(arr) {
  const str = btoa(String.fromCharCode.apply(null, arr));
  return str.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64UrlDecode(str) {
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';
  const binary = atob(str);
  const arr = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) arr[i] = binary.charCodeAt(i);
  return arr;
}

// ===== GitHub API helpers =====

async function githubGetFile(env) {
  const url = `https://api.github.com/repos/${env.GH_OWNER}/${env.GH_REPO}/contents/${env.GH_PATH}?ref=${env.GH_BRANCH}`;
  const resp = await fetch(url, {
    headers: {
      'Authorization': `Bearer ${env.GH_PAT}`,
      'Accept': 'application/vnd.github+json',
      'User-Agent': 'prayer-times-worker',
    },
  });
  if (!resp.ok) {
    throw new Error(`GitHub API ${resp.status}: ${await resp.text()}`);
  }
  return await resp.json(); // { sha, content (base64), encoding, ... }
}

async function githubPutFile(env, content, sha) {
  const url = `https://api.github.com/repos/${env.GH_OWNER}/${env.GH_REPO}/contents/${env.GH_PATH}`;
  const body = {
    message: `تحديث الرسائل via Cloudflare Worker`,
    content: btoa(unescape(encodeURIComponent(content))),
    branch: env.GH_BRANCH,
  };
  if (sha) body.sha = sha;
  const resp = await fetch(url, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${env.GH_PAT}`,
      'Accept': 'application/vnd.github+json',
      'User-Agent': 'prayer-times-worker',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    throw new Error(`GitHub PUT ${resp.status}: ${await resp.text()}`);
  }
  return await resp.json();
}

// ===== Database helpers =====

async function dbGetUserByEmail(env, email) {
  const stmt = env.DB.prepare('SELECT * FROM users WHERE email = ?');
  const result = await stmt.bind(email.toLowerCase()).first();
  return result;
}

async function dbGetUserById(env, id) {
  const stmt = env.DB.prepare('SELECT * FROM users WHERE id = ?');
  return await stmt.bind(id).first();
}

async function dbCreateUser(env, email, passwordHash, name, isAdmin = false) {
  const id = crypto.randomUUID();
  const stmt = env.DB.prepare(
    'INSERT INTO users (id, email, password_hash, name, is_active, is_admin, created_at) VALUES (?, ?, ?, ?, 1, ?, ?)'
  );
  await stmt.bind(id, email.toLowerCase(), passwordHash, name, isAdmin ? 1 : 0, Date.now()).run();
  return await dbGetUserById(env, id);
}

async function dbUpdateLastLogin(env, id) {
  const stmt = env.DB.prepare('UPDATE users SET last_login = ? WHERE id = ?');
  await stmt.bind(Date.now(), id).run();
}

async function dbSetActive(env, email, isActive) {
  const stmt = env.DB.prepare('UPDATE users SET is_active = ? WHERE email = ?');
  await stmt.bind(isActive ? 1 : 0, email.toLowerCase()).run();
}

async function dbListUsers(env) {
  const stmt = env.DB.prepare('SELECT id, email, name, is_active, is_admin, created_at, last_login FROM users ORDER BY created_at DESC');
  const result = await stmt.all();
  return result.results || [];
}

function publicUser(user) {
  if (!user) return null;
  return {
    id: user.id,
    email: user.email,
    name: user.name,
    is_active: user.is_active === 1,
    is_admin: user.is_admin === 1,
    created_at: user.created_at,
    last_login: user.last_login,
  };
}

// ===== Route handlers =====

async function handleRegister(request, env) {
  let body;
  try { body = await request.json(); }
  catch (e) { return jsonResponse({ error: 'invalid JSON' }, 400); }

  const email = String(body.email || '').trim().toLowerCase();
  const password = String(body.password || '');
  const name = String(body.name || '').trim();

  if (!email || !password) {
    return jsonResponse({ error: 'البريد وكلمة المرور مطلوبان' }, 400);
  }
  if (password.length < 6) {
    return jsonResponse({ error: 'كلمة المرور يجب أن تكون 6 أحرف على الأقل' }, 400);
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return jsonResponse({ error: 'البريد الإلكتروني غير صحيح' }, 400);
  }

  // التحقق من عدم وجود مستخدم بنفس البريد
  const existing = await dbGetUserByEmail(env, email);
  if (existing) {
    return jsonResponse({ error: 'هذا البريد مسجّل مسبقاً' }, 409);
  }

  const passwordHash = await hashPassword(password);
  const isAdmin = email === (env.ADMIN_EMAIL || '').toLowerCase();
  const user = await dbCreateUser(env, email, passwordHash, name, isAdmin);
  await dbUpdateLastLogin(env, user.id);

  const token = await signJWT({ uid: user.id, email: user.email, admin: isAdmin }, env.JWT_SECRET);
  return jsonResponse({ token, user: publicUser(user) });
}

async function handleLogin(request, env) {
  let body;
  try { body = await request.json(); }
  catch (e) { return jsonResponse({ error: 'invalid JSON' }, 400); }

  const email = String(body.email || '').trim().toLowerCase();
  const password = String(body.password || '');

  if (!email || !password) {
    return jsonResponse({ error: 'البريد وكلمة المرور مطلوبان' }, 400);
  }

  const user = await dbGetUserByEmail(env, email);
  if (!user) {
    return jsonResponse({ error: 'بيانات الدخول غير صحيحة' }, 401);
  }
  if (user.is_active !== 1) {
    return jsonResponse({ error: 'تم إيقاف حسابك. تواصل مع المسؤول' }, 403);
  }

  const valid = await verifyPassword(password, user.password_hash);
  if (!valid) {
    return jsonResponse({ error: 'بيانات الدخول غير صحيحة' }, 401);
  }

  await dbUpdateLastLogin(env, user.id);
  const token = await signJWT(
    { uid: user.id, email: user.email, admin: user.is_admin === 1 },
    env.JWT_SECRET
  );
  return jsonResponse({ token, user: publicUser(user) });
}

async function handleMe(request, env) {
  const auth = request.headers.get('Authorization') || '';
  const token = auth.replace(/^Bearer\s+/i, '');
  const payload = await verifyJWT(token, env.JWT_SECRET);
  if (!payload) return jsonResponse({ error: 'غير مصرّح' }, 401);

  const user = await dbGetUserById(env, payload.uid);
  if (!user) return jsonResponse({ error: 'المستخدم غير موجود' }, 404);
  if (user.is_active !== 1) return jsonResponse({ error: 'تم إيقاف حسابك' }, 403);

  return jsonResponse({ user: publicUser(user) });
}

async function handleGetMessages(env) {
  try {
    const file = await githubGetFile(env);
    const content = decodeURIComponent(escape(atob(file.content.replace(/\n/g, ''))));
    return new Response(content, {
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': 'no-cache',
      },
    });
  } catch (e) {
    return jsonResponse({ error: 'فشل جلب الرسائل: ' + e.message }, 502);
  }
}

async function handleSaveMessages(request, env) {
  const auth = request.headers.get('Authorization') || '';
  const token = auth.replace(/^Bearer\s+/i, '');
  const payload = await verifyJWT(token, env.JWT_SECRET);
  if (!payload) return jsonResponse({ error: 'غير مصرّح' }, 401);

  const user = await dbGetUserById(env, payload.uid);
  if (!user || user.is_active !== 1) {
    return jsonResponse({ error: 'تم إيقاف حسابك' }, 403);
  }

  let body;
  try { body = await request.json(); }
  catch (e) { return jsonResponse({ error: 'invalid JSON' }, 400); }

  if (!body.messages || !Array.isArray(body.messages)) {
    return jsonResponse({ error: 'الحقل messages مطلوب كمصفوفة' }, 400);
  }

  try {
    // جلب الملف الحالي للحفاظ على البنية
    const file = await githubGetFile(env);
    const currentContent = decodeURIComponent(escape(atob(file.content.replace(/\n/g, ''))));
    let parsed;
    try { parsed = JSON.parse(currentContent); }
    catch (e) { parsed = []; }

    // استبدال رسائل category=ehemessages بالرسائل الجديدة
    // v25.37: إصلاح — كان الفلتر يفحص 'messages' بينما التطبيق يستخدم 'ehemessages'
    // فتتراكم النسخ المكررة بدل استبدال الرسائل
    const nonMessages = parsed.filter(item => item && item.cat !== 'ehemessages');
    const newContent = nonMessages.concat(body.messages);
    const newJsonString = JSON.stringify(newContent, null, 2);

    const result = await githubPutFile(env, newJsonString, file.sha);
    return jsonResponse({ success: true, sha: result.content.sha });
  } catch (e) {
    return jsonResponse({ error: 'فشل الحفظ: ' + e.message }, 502);
  }
}

async function handleAdminListUsers(request, env) {
  const auth = request.headers.get('Authorization') || '';
  const token = auth.replace(/^Bearer\s+/i, '');
  const payload = await verifyJWT(token, env.JWT_SECRET);
  if (!payload) return jsonResponse({ error: 'غير مصرّح' }, 401);
  if (!payload.admin) return jsonResponse({ error: 'صلاحيات المدير مطلوبة' }, 403);

  const users = await dbListUsers(env);
  return jsonResponse({ users: users.map(publicUser) });
}

async function handleAdminRevoke(request, env) {
  const auth = request.headers.get('Authorization') || '';
  const token = auth.replace(/^Bearer\s+/i, '');
  const payload = await verifyJWT(token, env.JWT_SECRET);
  if (!payload) return jsonResponse({ error: 'غير مصرّح' }, 401);
  if (!payload.admin) return jsonResponse({ error: 'صلاحيات المدير مطلوبة' }, 403);

  let body;
  try { body = await request.json(); }
  catch (e) { return jsonResponse({ error: 'invalid JSON' }, 400); }

  const email = String(body.email || '').trim().toLowerCase();
  if (!email) return jsonResponse({ error: 'البريد مطلوب' }, 400);

  await dbSetActive(env, email, false);
  return jsonResponse({ success: true, email });
}

async function handleAdminRestore(request, env) {
  const auth = request.headers.get('Authorization') || '';
  const token = auth.replace(/^Bearer\s+/i, '');
  const payload = await verifyJWT(token, env.JWT_SECRET);
  if (!payload) return jsonResponse({ error: 'غير مصرّح' }, 401);
  if (!payload.admin) return jsonResponse({ error: 'صلاحيات المدير مطلوبة' }, 403);

  let body;
  try { body = await request.json(); }
  catch (e) { return jsonResponse({ error: 'invalid JSON' }, 400); }

  const email = String(body.email || '').trim().toLowerCase();
  if (!email) return jsonResponse({ error: 'البريد مطلوب' }, 400);

  await dbSetActive(env, email, true);
  return jsonResponse({ success: true, email });
}

// ===== Telegram relay (v25.37) =====
// يرسل تقرير الجهاز إلى تيليجرام نيابة عن التطبيق دون كشف التوكن
// حماية أساسية: حد الحجم + حد المعدل البسيط لكل IP
const tgRateMap = new Map();
function tgRateLimited(ip) {
  const now = Date.now();
  const windowMs = 10 * 60 * 1000; // نافذة 10 دقائق
  const maxReq = 8;                // 8 طلبات كحد أقصى لكل IP في النافذة
  const entry = tgRateMap.get(ip);
  if (!entry || now - entry.start > windowMs) {
    tgRateMap.set(ip, { start: now, count: 1 });
    if (tgRateMap.size > 5000) tgRateMap.clear();
    return false;
  }
  entry.count++;
  return entry.count > maxReq;
}

async function handleTgReport(request, env) {
  if (!env.TG_BOT_TOKEN || !env.TG_CHAT_ID) {
    return jsonResponse({ error: 'Telegram relay غير مفعّل — ضع TG_BOT_TOKEN و TG_CHAT_ID كـ secrets' }, 503);
  }

  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  if (tgRateLimited(ip)) {
    return jsonResponse({ error: 'تم تجاوز حد الإرسال — حاول لاحقاً' }, 429);
  }

  let body;
  try { body = await request.json(); }
  catch (e) { return jsonResponse({ error: 'invalid JSON' }, 400); }

  const text = String(body.text || '');
  if (!text || text.length > 3000) {
    return jsonResponse({ error: 'الحقل text مطلوب (حتى 3000 حرف)' }, 400);
  }

  try {
    const resp = await fetch(`https://api.telegram.org/bot${env.TG_BOT_TOKEN}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chat_id: env.TG_CHAT_ID, text }),
    });
    if (!resp.ok) {
      return jsonResponse({ error: 'Telegram API ' + resp.status }, 502);
    }
    return jsonResponse({ success: true });
  } catch (e) {
    return jsonResponse({ error: 'فشل الإرسال: ' + e.message }, 502);
  }
}

// ===== Main entry =====

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;

    // CORS preflight
    if (method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization',
          'Access-Control-Max-Age': '86400',
        },
      });
    }

    try {
      // Public endpoints
      if (path === '/register' && method === 'POST') return await handleRegister(request, env);
      if (path === '/login' && method === 'POST') return await handleLogin(request, env);
      if (path === '/messages' && method === 'GET') return await handleGetMessages(env);

      // Authenticated endpoints
      if (path === '/me' && method === 'GET') return await handleMe(request, env);
      if (path === '/messages' && method === 'POST') return await handleSaveMessages(request, env);

      // Telegram relay (v25.37)
      if (path === '/api/tg-report' && method === 'POST') return await handleTgReport(request, env);

      // Admin endpoints
      if (path === '/admin/users' && method === 'GET') return await handleAdminListUsers(request, env);
      if (path === '/admin/revoke' && method === 'POST') return await handleAdminRevoke(request, env);
      if (path === '/admin/restore' && method === 'POST') return await handleAdminRestore(request, env);

      if (path === '/' || path === '/health') {
        return jsonResponse({ ok: true, service: 'prayer-times-worker', version: '1.0.0' });
      }

      return jsonResponse({ error: 'Not found' }, 404);
    } catch (e) {
      return jsonResponse({ error: 'Internal error: ' + e.message }, 500);
    }
  },
};
