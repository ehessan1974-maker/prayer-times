#!/bin/bash
# ===== بناء تطبيق مواقيت الصلاة APK =====
# يتطلب: OpenJRE 21+ (java, keytool)، ecj.jar، Android build-tools 34 + platform android-34
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$PROJ")"
SDK=/home/z/my-project/.android-sdk
BT="$SDK/build-tools/android-14"
PLATFORM="$SDK/platforms/android-34/android.jar"
ECJ="$SDK/ecj.jar"
KEYSTORE="$PROJ/prayer-times.keystore"
KS_PASS="prayertimes2026"
PKG_DIR="com/ehessan/prayertimes"
OUT="$PROJ/build"

echo "==> 1/7 تجهيز المجلدات ونسخ أحدث HTML"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
mkdir -p "$PROJ/assets"
cp -f "$ROOT/prayer-times.html" "$PROJ/assets/prayer-times.html"

if [ ! -f "$KEYSTORE" ]; then
  echo "==> توليد مفتاح التوقيع (أول مرة فقط)"
  keytool -genkeypair -v -keystore "$KEYSTORE" -alias prayertimes \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=Prayer Times, OU=Apps, O=ehessan1974, L=Damascus, C=SY"
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
  --min-sdk-version 21 --target-sdk-version 34

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
  "$PROJ/java/$PKG_DIR/KeepAliveService.java"

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
