#!/bin/bash
# ===== بناء تطبيق مواقيت الصلاة APK =====
# يتطلب: OpenJRE 21+ (java, keytool)، ecj.jar، Android build-tools 34 + platform android-34
# المسارات الافتراضية أدناه قابلة للتجاوز عبر متغيرات البيئة (بدون تعديل السكربت):
#   PT_SDK=/مسار/.android-sdk  PT_BT=/مسار/build-tools/34.0.0  \
#   PT_PLATFORM=/مسار/android.jar  PT_ECJ=/مسار/ecj.jar  KS_PASS=كلمة_السر  ./build-apk.sh
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$PROJ")"
SDK="${PT_SDK:-/home/z/my-project/.android-sdk}"
BT="${PT_BT:-$SDK/build-tools/android-14}"
PLATFORM="${PT_PLATFORM:-$SDK/platforms/android-34/android.jar}"
ECJ="${PT_ECJ:-$SDK/ecj.jar}"
KEYSTORE="$PROJ/prayer-times.keystore"
# v25.37 [أمني]: كلمة السر لم تعد مكتوبة في السكربت — مرّرها عبر متغير البيئة KS_PASS
# مثال: KS_PASS=xxxx ./build-apk.sh   (استخدم نفس كلمة السر المعروفة حتى يبقى التوقيع متطابقاً)
KS_PASS="${KS_PASS:-}"
PKG_DIR="com/ehessan/prayertimes"
OUT="$PROJ/build"

echo "==> 1/7 تجهيز المجلدات ونسخ أحدث HTML"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
mkdir -p "$PROJ/assets"
cp -f "$ROOT/prayer-times.html" "$PROJ/assets/prayer-times.html"

if [ ! -f "$KEYSTORE" ]; then
  if [ -z "$KS_PASS" ]; then
    echo "خطأ: لا يوجد keystore ولم تُمرَّر كلمة السر." >&2
    echo "الاستخدام: KS_PASS=<كلمة_السر> ./build-apk.sh" >&2
    exit 1
  fi
  echo "==> توليد مفتاح التوقيع (أول مرة فقط)"
  keytool -genkeypair -v -keystore "$KEYSTORE" -alias prayertimes \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=Prayer Times, OU=Apps, O=ehessan1974, L=Damascus, C=SY"
fi

if [ -z "$KS_PASS" ]; then
  echo "خطأ: مرّر كلمة سر الـ keystore عبر متغير البيئة KS_PASS" >&2
  echo "الاستخدام: KS_PASS=<كلمة_السر> ./build-apk.sh" >&2
  exit 1
fi

echo "==> 2/7 aapt2 compile"
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/res.zip"

echo "==> 3/7 aapt2 link"
"$BT/aapt2" link -o "$OUT/app-unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -R "$OUT/res.zip" \
  --java "$OUT/gen" \
  -A "$PROJ/assets" \
  --auto-add-overlay \
  --min-sdk-version 19 --target-sdk-version 34

echo "==> 4/7 تجميع الجافا (ecj)"
java -jar "$ECJ" -1.8 -nowarn \
  -cp "$PLATFORM" \
  -d "$OUT/classes" \
  "$OUT/gen/$PKG_DIR/R.java" \
  "$PROJ/java/$PKG_DIR/MainActivity.java" \
  "$PROJ/java/$PKG_DIR/PrayerTts.java" \
  "$PROJ/java/$PKG_DIR/AlarmScheduler.java" \
  "$PROJ/java/$PKG_DIR/AlarmReceiver.java" \
  "$PROJ/java/$PKG_DIR/BootReceiver.java" \
  "$PROJ/java/$PKG_DIR/PrayerAudioService.java" \
  "$PROJ/java/$PKG_DIR/KeepAliveService.java" \
  "$PROJ/java/$PKG_DIR/Tls12Helper.java" \
  "$PROJ/java/$PKG_DIR/UpdateChecker.java"

echo "==> 5/7 d8 (تحويل إلى dex)"
CLASSLIST=$(find "$OUT/classes" -name "*.class" | tr '\n' ' ')
"$BT/d8" --release --lib "$PLATFORM" --output "$OUT/dex" $CLASSLIST

echo "==> 6/7 إضافة dex للأرشيف + المحاذاة"
cd "$OUT/dex"
"$BT/aapt" add "$OUT/app-unsigned.apk" classes.dex > /dev/null
"$BT/zipalign" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

echo "==> 7/7 التوقيع والتحقق"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias prayertimes \
  --out "$PROJ/prayer-times.apk" "$OUT/app-aligned.apk"
"$BT/apksigner" verify "$PROJ/prayer-times.apk" && echo "APK SIGNED OK"

ls -la "$PROJ/prayer-times.apk"
