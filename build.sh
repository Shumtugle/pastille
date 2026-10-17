#!/bin/sh
# Builds a signed release APK without Gradle.
#
#   ANDROID_JAR   platform jar to compile against (API 34 or newer)
#   KEYSTORE      signing keystore
#   KS_PASS       keystore password
#   KS_ALIAS      key alias
#
# Needs on PATH: aapt2, javac, dx (or d8), zipalign, apksigner.
set -eu

APP_ID=io.github.shumtugle.pastille
VERSION_CODE=2
VERSION_NAME=1.1
MIN_SDK=26
TARGET_SDK=35

ROOT=$(cd "$(dirname "$0")" && pwd)
SRC="$ROOT/app/src/main"
OUT="$ROOT/out"
: "${ANDROID_JAR:?set ANDROID_JAR}"
: "${KEYSTORE:?set KEYSTORE}"
: "${KS_PASS:?set KS_PASS}"
: "${KS_ALIAS:?set KS_ALIAS}"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

# The package name lives here, not in the manifest, so it is set in one place.
sed "s|<manifest |<manifest package=\"$APP_ID\" |" "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"

aapt2 compile --dir "$SRC/res" -o "$OUT/res.zip"
aapt2 link -o "$OUT/unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$OUT/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version $MIN_SDK \
    --target-sdk-version $TARGET_SDK \
    --version-code $VERSION_CODE \
    --version-name $VERSION_NAME \
    "$OUT/res.zip"

find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt"

if command -v d8 >/dev/null 2>&1; then
    d8 --release --min-api $MIN_SDK --lib "$ANDROID_JAR" --output "$OUT/dex" \
        $(find "$OUT/classes" -name '*.class')
else
    dx --dex --min-sdk-version=$MIN_SDK --output="$OUT/dex/classes.dex" "$OUT/classes"
fi

cp "$OUT/unsigned.apk" "$OUT/with-dex.apk"
(cd "$OUT/dex" && zip -q -j "$OUT/with-dex.apk" classes.dex)

zipalign -f -p 4 "$OUT/with-dex.apk" "$OUT/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
    --key-pass "pass:$KS_PASS" --out "$OUT/pastille-$VERSION_NAME.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/pastille-$VERSION_NAME.apk"
echo "$OUT/pastille-$VERSION_NAME.apk"
