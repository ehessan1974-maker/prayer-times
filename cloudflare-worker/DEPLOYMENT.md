# 🚀 دليل نشر Cloudflare Worker

هذا الدليل يشرح كيف تنشئ الخادم الوسيط الذي يحمي GitHub PAT ويصدر رموزاً فريدة لكل مستخدم.

## 📋 المتطلبات

1. **حساب Cloudflare** (مجاني): https://dash.cloudflare.com/sign-up
2. **Node.js 18+** مثبّت على جهازك
3. **GitHub PAT** بصلاحية `Contents: Read and write` على مستودع `prayer-times`

## 🛠️ خطوات النشر

### 1️⃣ تثبيت Wrangler (CLI الخاص بـ Cloudflare)

```bash
npm install -g wrangler
# أو
yarn global add wrangler
```

### 2️⃣ تسجيل الدخول إلى Cloudflare

```bash
cd cloudflare-worker
wrangler login
```
سيفتح المتصفح لتسجيل الدخول إلى حسابك في Cloudflare.

### 3️⃣ إنشاء قاعدة بيانات D1

```bash
wrangler d1 create prayer-times-db
```

سيُعطيك مخرجاً مثل:
```
✅ Successfully created DB 'prayer-times-db'
database_id = "abc-123-def-456-..."
```

انسخ `database_id` والصقه في `wrangler.toml` بدل `REPLACE_WITH_YOUR_DATABASE_ID`.

### 4️⃣ تطبيق الـ schema على قاعدة البيانات

```bash
wrangler d1 execute prayer-times-db --file=schema.sql
```

### 5️⃣ تعديل `wrangler.toml`

افتح `wrangler.toml` وعدّل:
- `ADMIN_EMAIL` → بريدك الإلكتروني (سيُمنح صلاحية المدير تلقائياً)

### 6️⃣ إضافة المتغيرات السرية

```bash
# GitHub PAT
wrangler secret put GH_PAT
# الصق قيمة PAT: github_pat_xxxxx أو ghp_xxxxx

# سلسلة عشوائية لتوقيع JWT (40 حرف على الأقل)
wrangler secret put JWT_SECRET
# الصق سلسلة عشوائية (مثلاً: openssl rand -hex 32)

# v25.37: وسيط تيليجرام لتقارير الأجهزة (اختياري — يفعّل ميزة معرّفات الأجهزة)
wrangler secret put TG_BOT_TOKEN
# الصق توكن البوت من @BotFather: 123456789:ABCdef...

wrangler secret put TG_CHAT_ID
# الصق معرّف محادثة المدير (من @userinfobot مثلاً): 123456789
```

### 7️⃣ نشر الـ Worker

```bash
wrangler deploy
```

سيُعطيك مخرجاً مثل:
```
Deployed prayer-times-worker (1.23 sec)
  https://prayer-times-worker.<your-subdomain>.workers.dev
```

انسخ هذا الرابط — ستحتاجه في الخطوة التالية.

### 8️⃣ اختبار الـ Worker

```bash
# فحص الصحة
curl https://prayer-times-worker.your-subdomain.workers.dev/health

# التسجيل
curl -X POST https://prayer-times-worker.your-subdomain.workers.dev/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"secret123","name":"مستخدم اختبار"}'

# جلب الرسائل (عام)
curl https://prayer-times-worker.your-subdomain.workers.dev/messages
```

---

## 🎯 ربط التطبيق بالـ Worker

في `prayer-times.html`، عدّل:

```javascript
var WORKER_URL = "https://prayer-times-worker.your-subdomain.workers.dev";
```

و v25.37: لتفعيل تقارير الأجهزة عبر الوسيط الآمن، ضع الرابط نفسه في:

```javascript
var TG_WORKER_URL = "https://prayer-times-worker.your-subdomain.workers.dev";
```

بعد ذلك تُرسل تقارير الأجهزة عبر `POST /api/tg-report` — والتوكن محفوظ كـ secret على الـ worker ولا يظهر في كود التطبيق إطلاقاً.

ضع هذا المتغيّر في بداية ملف `prayer-times.html`.

---

## 👨‍💼 إدارة المستخدمين (للمدير)

بعد تسجيل حسابك بـ `ADMIN_EMAIL`، تحصل على صلاحيات إدارية. تستطيع:

### عرض كل المستخدمين

```bash
TOKEN="<your_jwt_token>"
curl https://prayer-times-worker.your-subdomain.workers.dev/admin/users \
  -H "Authorization: Bearer $TOKEN"
```

### إيقاف مستخدم (إلغاء صلاحيته)

```bash
curl -X POST https://prayer-times-worker.your-subdomain.workers.dev/admin/revoke \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"baduser@example.com"}'
```

### إعادة تفعيل مستخدم

```bash
curl -X POST https://prayer-times-worker.your-subdomain.workers.dev/admin/restore \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"baduser@example.com"}'
```

---

## 📊 ما الذي يحصل عليه كل مستخدم؟

| المعلومة | أين تُخزَّن | مرئية للمستخدم؟ |
|---------|-----------|-----------------|
| GitHub PAT | Cloudflare Secret | ❌ لا يراها أحد |
| قائمة المستخدمين | Cloudflare D1 | ❌ يراها المدير فقط |
| كلمات المرور | D1 (مجزّأة PBKDF2) | ❌ لا تُخزَّن كنص |
| JWT (للمستخدم) | localStorage في متصفحه | ✅ يراها هو فقط |

---

## 🔐 الفوائد الأمنية

1. **PAT مخفي تماماً** عن كل المستخدمين
2. **كل مستخدم له حساب فريد** (بريد + كلمة مرور)
3. **يمكن إلغاء مستخدم واحد** دون التأثير على الآخرين
4. **كلمات المرور مجزّأة** بـ PBKDF2 (لا يمكن فكّها)
5. **JWT ينتهي بعد 30 يوماً** تلقائياً (يتطلب إعادة تسجيل دخول)
6. **سجل كامل** بمن سجّل ومتى آخر دخول

---

## 🆘 استكشاف الأخطاء

### المشكلة: `wrangler login` لا يفتح المتصفح
الحل: انسخ الرابط من الـ terminal وافتحه يدوياً.

### المشكلة: `database_id is not set`
تأكد من نسخ الـ ID الصحيح من مخرجات `wrangler d1 create`.

### المشكلة: `GH_PAT secret not found`
شغّل: `wrangler secret put GH_PAT` والصق قيمة PAT.

### المشكلة: `JWT_SECRET secret not found`
شغّل: `wrangler secret put JWT_SECRET` وأدخل سلسلة عشوائية (40+ حرف).

### المشكلة: تقارير تيليجرام لا تصل (v25.37+)
تأكد من:
- `wrangler secret put TG_BOT_TOKEN` و `wrangler secret put TG_CHAT_ID`
- `TG_WORKER_URL` في `prayer-times.html` يشير لرابط الـ worker الصحيح
- حد المعدل: 8 طلبات لكل IP كل 10 دقائق (كافٍ لخاصية تقارير كل 24 ساعة)

### المشكلة: المستخدمون لا يستطيعون الحفظ
تأكد من أن:
- PAT له صلاحية `Contents: Read and write` على مستودع `prayer-times`
- المسار في `GH_PATH = "content.json"` صحيح
- الـ branch في `GH_BRANCH = "main"` صحيح

---

## 📁 ملفات المجلد

| الملف | الوصف |
|------|------|
| `worker.js` | كود الـ Worker (الـ backend) |
| `wrangler.toml` | إعدادات النشر |
| `schema.sql` | تعريف جدول users في D1 |
| `DEPLOYMENT.md` | هذا الدليل |
