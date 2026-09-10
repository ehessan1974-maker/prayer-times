# مواقيت الصلاة — Prayer Times 🕌

تطبيق مواقيت صلاة شامل بملف HTML واحد (عربي RTL يعمل بلا إنترنت) + تطبيق أندرويد APK + وضع الشاشة الذكية (Smart TV).

## 📱 آخر إصدار: v15 (versionCode 18)

**تحميل أحدث APK مباشرة:**
- https://raw.githubusercontent.com/ehessan1974-maker/prayer-times/main/public/prayer-times-v15.apk
- https://raw.githubusercontent.com/ehessan1974-maker/prayer-times/main/public/prayer-times.apk
- https://raw.githubusercontent.com/ehessan1974-maker/prayer-times/main/android-app/prayer-times-v15.apk

**أحدث HTML (يعمل في أي متصفح):**
- https://raw.githubusercontent.com/ehessan1974-maker/prayer-times/main/prayer-times.html

## ✨ الميزات
- مواقيت الصلاة بحساب فلكي دقيق (GPS أو إحداثيات يدوية أو اختيار مدينة)
- أذان / إقامة / دعاء / تنبيهات عدّ تنازلي صوتية (الأصوات في `public/`)
- 🧭 بوصلة القبلة التفاعلية بحساس الاتجاه (v14) مع دعم إذن iOS
- 🌙 الوضع الليلي/النهاري محفوظ محلياً (v14)
- 📺 وضع الشاشة الذكية 1600×900 بلا تمرير (v12) + أشرطة تمرير عريضة واضحة (v15)
- خط الثلث لاسم اليوم مضمّن Base64 + نسخة الريبو
- شريط رسائل متحرك يُدار بمحرر داخل التطبيق ويُحفَظ في `content.json` هنا في الريبو
- إذاعة واختيار المدن + إعادة معايرة

## 🗂️ خريطة الملفات

| المسار | الوصف |
|---|---|
| `prayer-times.html` | **النسخة الأساسية المحدّثة** (v15) |
| `prayer-times-v12.html` … `prayer-times-v15.html` | أرشيف الأجيال الأخيرة (v2–v11 في تاريخ الريبو) |
| `public/prayer-times.html` | نسخة الويب المطابقة للأساسية |
| `public/prayer-times.apk` + `prayer-times-v2…v15.apk` | كل أجيال APK (المسمّاة والقياسية) |
| `public/*.mp3` | أصوات الأذان والإقامة والشروق والدعاء والتنبيهات |
| `android-app/` | مصدر الأندرويد كاملاً: Java + Manifest + res + keystore + `build-apk.sh` |
| `android-app/assets/` | أصول الـ APK (HTML + الأصوات) — مطلوبة لإعادة البناء |
| `DTHULUTH.TTF` / `THULUTH.TTF` / `ثلث *.TTF` | خطوط الثلث (يحمّلها التطبيق من هنا) |
| `content.json` | بيانات الشريط المتحرك (يُحرَّر من داخل التطبيق عبر 🔑 Token) |
| `pc-autostart/` | تشغيل تلقائي عند إقلاع ويندوز (.bat) ولينكس (.desktop) |
| `poetry/README.md` | دراسة المعلقات العشر |
| `worklog.md` | سجل التطوير الكامل (المهام والقرارات والتحققات) |

## 🔨 إعادة بناء APK

```bash
cd android-app
./build-apk.sh
```

المتطلبات: Java 21+ و Android build-tools 34 و platform android-34 و ecj.jar (المسارات داخل السكربت).
الناتج: `prayer-times.apk` موقّع بمفتاح `prayer-times.keystore`.

## 📦 اتفاقية إصدار جديد (N)

عند كل جيل جديد، يُرفَع **بلا حذف أي ملف أبداً**:
1. `prayer-times-vN.html` (جذر — أرشيف الجيل)
2. تحديث `prayer-times.html` (جذر) و `public/prayer-times.html`
3. بناء APK ورفعه بأربعة مسارات: `public/prayer-times-vN.apk` + `public/prayer-times.apk` + `android-app/prayer-times-vN.apk` + `android-app/prayer-times.apk`
4. تحديث `android-app/AndroidManifest.xml` (versionCode / versionName)

> ملاحظة: `index.html` وملفات `src/` هي بقايا هيكل Next.js قديمة ولا علاقة لها بالبرنامج.
