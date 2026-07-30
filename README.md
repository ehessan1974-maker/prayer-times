# مواقيت الصلاة - نسخة الويب
## Prayer Times Web App

---

## 🚀 طريقة النشر على Vercel (مجاني)

### الطريقة الأسهل - بدون تثبيت أي شيء:

#### الخطوة 1: إنشاء حساب GitHub
إذا لم يكن لديك حساب، أنشئ واحد من: https://github.com/signup

#### الخطوة 2: إنشاء مستودع جديد
1. اذهب إلى: https://github.com/new
2. اسم المستودع: `prayer-times-web`
3. اختر **Private** (خاص)
4. انقر **Create repository**

#### الخطوة 3: رفع الملفات
1. في صفحة المستودع الجديد، انقر **uploading an existing file**
2. اسحب مجلد `prayer-web` بالكامل
   - أو ارفع الملفات واحدة تلو الأخرى
3. انقر **Commit changes**

#### الخطوة 4: ربط مع Vercel
1. اذهب إلى: https://vercel.com/signup
2. سجل دخول بحساب GitHub
3. انقر **Add New Project**
4. اختر مستودع `prayer-times-web`
5. اترك الإعدادات الافتراضية كما هي
6. انقر **Deploy**
7. ⏳ انتظر 2-3 دقائق...
8. ✅ تم! ستحصل على رابط مثل: `https://prayer-times-web.vercel.app`

#### الخطوة 5: تخصيص الرابط (اختياري)
1. في Vercel Dashboard، اختر مشروعك
2. اذهب إلى **Settings → Domains**
3. أضف اسم النطاق الذي تريده

---

## 📱 التطبيق كـ PWA
التطبيق يعمل كـ Progressive Web App:
- يمكن تثبيته على الهاتف من المتصفح
- يعمل بدون إنترنت (بعد التحميل الأول)
- يظهر كتطبيق مستقل على الشاشة الرئيسية

### تثبيته على الهاتف:
1. افتح الرابط في Chrome (أندرويد) أو Safari (آيفون)
2. Android: اضغط على القائمة ⋮ → **Add to Home Screen**
3. iPhone: اضغط على زر المشاركة ↑ → **Add to Home Screen**

---

## 🔧 طريقة التطوير المحلي (للمبرمجين)

### المتطلبات:
- Node.js 18+
- npm أو yarn

### التشغيل:
```bash
cd prayer-web
npm install
npm run dev
```
ثم افتح: http://localhost:3000

### البناء:
```bash
npm run build
npm start
```

---

## 📁 هيكل المشروع
```
prayer-web/
├── public/
│   ├── adhan.mp3          # صوت الأذان
│   ├── iqama.mp3          # صوت الإقامة
│   └── manifest.json       # PWA manifest
├── src/
│   └── app/
│       ├── globals.css     # كل التنسيقات
│       ├── layout.tsx      # تخطيط الصفحة الرئيسي
│       └── page.tsx         # صفحة التطبيق الرئيسية
├── next.config.js
├── package.json
└── .gitignore
```

---

## 👥 فريق العمل
- **البرمجة والتصميم**: د. إحسان العبد الله
- **الغرافيك**: منال برغل
- **الأفكار**: قيس وإياد وعلي العبد الله
