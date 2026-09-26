/**
 * Prayer Times — بديل Deno Deploy لوسيط تيليجرام (بدل Cloudflare Worker)
 * ============================================================
 * الهدف نفسه: يستقبل POST /api/tg-report بالشكل {text: "..."} ويعيد الإرسال
 * إلى تيليجرام باستخدام TG_BOT_TOKEN و TG_CHAT_ID المخزنين كمتغيرات بيئة،
 * دون كشف التوكن في كود التطبيق العام.
 *
 * خطوات النشر على dash.deno.com (5 دقائق، متصفح فقط):
 *   1) Log in with GitHub → Create New Playground
 *   2) امسح الكود الافتراضي والصق هذا الملف كاملاً
 *   3) من زر الإعدادات (⚙️ أو Settings) → Environment Variables:
 *        TG_BOT_TOKEN = توكن البوت الجديد من BotFather
 *        TG_CHAT_ID   = 628644087
 *   4) Save & Deploy → انسخ رابط المشروع (مثل https://prayer-times.deno.dev)
 *   5) اختبار: افتح الرابط في المتصفح — يجب أن يرجع {"ok":true,...}
 *   6) أرسل الرابط لي لضبط TG_WORKER_URL في التطبيق
 *
 * متوافق 100% مع tgSendReport في prayer-times.html (يفحص r.ok فقط)
 */

const TG_TOKEN = () => Deno.env.get("TG_BOT_TOKEN") || "";
const TG_CHAT = () => Deno.env.get("TG_CHAT_ID") || "";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Access-Control-Max-Age": "86400",
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...CORS },
  });
}

// حد معدل بسيط: 8 طلبات لكل IP كل 10 دقائق (نفس منطق نسخة Cloudflare)
const rateMap = new Map();
function rateLimited(ip) {
  const now = Date.now();
  const windowMs = 10 * 60 * 1000; // نافذة 10 دقائق
  const maxReq = 8;                // 8 طلبات كحد أقصى لكل IP
  const entry = rateMap.get(ip);
  if (!entry || now - entry.start > windowMs) {
    rateMap.set(ip, { start: now, count: 1 });
    if (rateMap.size > 5000) rateMap.clear();
    return false;
  }
  entry.count++;
  return entry.count > maxReq;
}

async function handleTgReport(req) {
  if (!TG_TOKEN() || !TG_CHAT()) {
    return json(
      { error: "Telegram relay غير مفعّل — ضع TG_BOT_TOKEN و TG_CHAT_ID في Environment Variables" },
      503
    );
  }

  const ip = (req.headers.get("x-forwarded-for") || "").split(",")[0].trim() ||
             req.headers.get("cf-connecting-ip") || "unknown";
  if (rateLimited(ip)) {
    return json({ error: "تم تجاوز حد الإرسال — حاول لاحقاً" }, 429);
  }

  let body;
  try { body = await req.json(); }
  catch { return json({ error: "invalid JSON" }, 400); }

  const text = String((body && body.text) || "");
  if (!text || text.length > 3000) {
    return json({ error: "الحقل text مطلوب (حتى 3000 حرف)" }, 400);
  }

  try {
    // parse_mode HTML لعرض معرّف الجهاز بخط منسق (نفس تنسيق رسالة التطبيق)
    const resp = await fetch(`https://api.telegram.org/bot${TG_TOKEN()}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: TG_CHAT(), text, parse_mode: "HTML" }),
    });
    if (!resp.ok) {
      return json({ error: "Telegram API " + resp.status }, 502);
    }
    return json({ success: true });
  } catch (e) {
    return json({ error: "فشل الإرسال: " + (e && e.message ? e.message : String(e)) }, 502);
  }
}

Deno.serve(async (req) => {
  const url = new URL(req.url);
  const path = url.pathname;

  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS });
  }

  if (path === "/api/tg-report" && req.method === "POST") {
    return await handleTgReport(req);
  }

  if ((path === "/" || path === "/health") && req.method === "GET") {
    return json({ ok: true, service: "prayer-times-report-relay", version: "1.0.0-deno" });
  }

  return json({ error: "Not found" }, 404);
});
