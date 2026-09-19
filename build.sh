#!/usr/bin/env bash
# Сборка InstaLite без Android Studio (Ubuntu 24.04):
#   sudo apt install openjdk-21-jdk-headless aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
# Использование: ./build.sh   → build/InstaLite.apk
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
KEYSTORE=${KEYSTORE:-keystore/instalite.jks}
KS_PASS=${KS_PASS:-instalite}

rm -rf build && mkdir -p build/classes

echo "1/5 javac"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -d build/classes $(find src -name '*.java')

echo "2/5 dex"
dalvik-exchange --dex --min-sdk-version=24 --output=build/classes.dex build/classes

echo "3/5 aapt"
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$ANDROID_JAR" -F build/unsigned.apk
(cd build && aapt add unsigned.apk classes.dex >/dev/null)

echo "4/5 zipalign"
zipalign -f -p 4 build/unsigned.apk build/aligned.apk

echo "5/5 sign"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -v -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias instalite -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=InstaLite, O=Personal, C=KZ" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias instalite \
  --out build/InstaLite.apk build/aligned.apk
apksigner verify --print-certs build/InstaLite.apk | head -3
echo "Готово: build/InstaLite.apk"
