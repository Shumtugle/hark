#!/bin/bash
# Сборка Hark. Запускать из корня проекта, где лежит AndroidManifest.xml.
#
#   ./build.sh <версия>
#
# Ключ подписи ожидается в ../keys/shumtugle.keystore,
# пароль — в файлах ../.pw1 и ../.pw2 (по строке в каждом).
set -e

VER="${1:-1.0.0}"
SDK="${SDK:-/home/claude/sdk/android-33.jar}"
KEYS="${KEYS:-/home/claude/keys/keys/shumtugle.keystore}"
PW1="${PW1:-/home/claude/.pw1}"
PW2="${PW2:-/home/claude/.pw2}"
OUT="${OUT:-/mnt/user-data/outputs}"

echo "── Hark $VER ─────────────────────────"

rm -rf gen cls classes.dex base.ap_ unsigned.zip aligned.apk
mkdir -p gen cls

echo "1. ресурсы + R.java"
aapt package -f -m -J gen -M AndroidManifest.xml -S res -I "$SDK" -F base.ap_

echo "2. javac"
javac -nowarn -encoding UTF-8 --release 8 -classpath "$SDK" -d cls $(find src gen -name "*.java")

echo "3. dex"
dalvik-exchange --dex --min-sdk-version=26 --output=classes.dex cls

echo "4. упаковка"
cp base.ap_ unsigned.zip
zip -q unsigned.zip classes.dex

echo "5. выравнивание"
zipalign -f 4 unsigned.zip aligned.apk

echo "6. подпись"
apksigner sign --ks "$KEYS" --ks-pass "file:$PW1" --key-pass "file:$PW2" \
  --v2-signing-enabled true --v3-signing-enabled true --min-sdk-version 26 \
  --out "$OUT/hark-$VER.apk" aligned.apk

echo "7. проверка"
apksigner verify --print-certs "$OUT/hark-$VER.apk" | grep -iE "DN:" | head -1
python3 - "$OUT/hark-$VER.apk" <<'PY'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
i = z.getinfo('resources.arsc')
print('resources.arsc:', 'STORED' if i.compress_type == 0 else 'DEFLATED')
PY
aapt dump badging "$OUT/hark-$VER.apk" | grep -E "^package|sdkVersion|targetSdk"
ls -la "$OUT/hark-$VER.apk"
echo "── готово ───────────────────────────"
