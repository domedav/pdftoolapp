#!/bin/bash
# Consolidated Build Script for Termux/Debian Chroot Environment
PROJECT_DIR=$(pwd)
DOWNLOADS_DIR="/sdcard/Download"

# --- Versioning ---
DATE_STR=$(date +%Y.%m.%d)
BUILDNUM_FILE="$PROJECT_DIR/.buildnum"
[ ! -f "$BUILDNUM_FILE" ] && echo 1 > "$BUILDNUM_FILE"
BUILDNUM=$(cat "$BUILDNUM_FILE")
NEW_BUILDNUM=$((BUILDNUM + 1))
echo "$NEW_BUILDNUM" > "$BUILDNUM_FILE"
VERSION_NAME="${DATE_STR}.${BUILDNUM}"

echo "Starting Release Build v$VERSION_NAME..."

# Step 1: Clean and Build in Debian Chroot
proot-distro login debian --bind "$PROJECT_DIR":/workspace -- bash <<EOF
    export ANDROID_HOME=/opt/android-sdk
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
    export PATH=\$PATH:/opt/gradle/bin
    cd /workspace

    echo "--- Step 1: Compiling & Optimizing with Gradle ---"
    gradle clean :app:assembleRelease \
      -PversionName=$VERSION_NAME \
      -PversionCode=$BUILDNUM || exit 1

    # Fix Structure (Addressing AGP 9.0.0 Resource Issues)
    echo "--- Step 2: Fixing APK Structure ---"
    
    AP_FILE="app/build/intermediates/linked_resources_binary_format/release/processReleaseResources/linked-resources-binary-format-release.ap_"
    RAW_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
    FIXED_APK="app/build/outputs/apk/release/app-fixed.apk"
    
    if [ ! -f "\$AP_FILE" ]; then echo "Error: .ap_ file not found"; exit 1; fi
    if [ ! -f "\$RAW_APK" ]; then echo "Error: Unsigned APK not found"; exit 1; fi

    cp "\$AP_FILE" "\$FIXED_APK"
    
    mkdir -p /tmp/apk_code
    unzip -q -o "\$RAW_APK" -d /tmp/apk_code
    
    cd /tmp/apk_code
    rm -rf META-INF/*.SF META-INF/*.RSA META-INF/*.DSA AndroidManifest.xml res/ resources.arsc
    
    zip -u -r -q -n .so:.prof:.profm "/workspace/\$FIXED_APK" .
    
    echo "APK structure fixed. Running zipalign..."
    
    ALIGNED_APK="/workspace/app/build/outputs/apk/release/app-aligned.apk"
    zipalign -f -p 4 "/workspace/\$FIXED_APK" "\$ALIGNED_APK" || exit 1
EOF

if [ $? -ne 0 ]; then
    echo "ERROR: Build or structural fix phase failed."
    exit 1
fi

ALIGNED_APK="$PROJECT_DIR/app/build/outputs/apk/release/app-aligned.apk"
FINAL_APK="$PROJECT_DIR/app/build/outputs/apk/release/pdftool_signed.apk"

# NOTE: For local signing, ensure you have your keystore available.
# F-Droid builds will ignore this and use their own signing process.
if [ -f "app/release-key.jks" ]; then
    echo "--- Step 3: Final Signing ---"
    apksigner sign \
      --ks "app/release-key.jks" \
      --ks-key-alias "release" \
      --ks-pass 'pass:pdftool2026!' \
      --key-pass 'pass:pdftool2026!' \
      --out "$FINAL_APK" \
      --min-sdk-version 24 \
      "$ALIGNED_APK" || { echo "ERROR: Signing failed"; exit 1; }

    echo "--- Step 4: Final Verification ---"
    apksigner verify -v "$FINAL_APK" || { echo "ERROR: Verification failed"; exit 1; }
else
    echo "WARNING: app/release-key.jks not found. Skipping signing."
    echo "Unsigned APK is available at: $ALIGNED_APK"
    FINAL_APK="$ALIGNED_APK"
fi

# --- Export ---
if [ -f "$FINAL_APK" ]; then
    cp "$FINAL_APK" "$DOWNLOADS_DIR/pdftool-$VERSION_NAME.apk"
    echo "--------------------------------------------------"
    echo "Build Complete!"
    echo "Version: $VERSION_NAME"
    echo "File: $DOWNLOADS_DIR/pdftool-$VERSION_NAME.apk"
    echo "--------------------------------------------------"
else
    echo "FAILED: Final APK was never created."
    exit 1
fi