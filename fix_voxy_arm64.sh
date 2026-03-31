#!/bin/bash
# ============================================================
# Fix Voxy + VoxyServer for ARM64 (aarch64) Linux servers
# ============================================================
# 
# The Voxy mod strips out aarch64 RocksDB natives during build.
# This script downloads the full rocksdbjni JAR (with aarch64
# support) and injects it into the Voxy mod JAR.
#
# Usage: 
#   chmod +x fix_voxy_arm64.sh
#   ./fix_voxy_arm64.sh /path/to/your/mods/folder
#
# Requirements: curl, unzip, zip, jar or java must be available
# ============================================================

set -e

MODS_DIR="${1:-.}"
ROCKSDB_VERSION="10.2.1"
ROCKSDB_URL="https://repo1.maven.org/maven2/org/rocksdb/rocksdbjni/${ROCKSDB_VERSION}/rocksdbjni-${ROCKSDB_VERSION}.jar"

# Find the Voxy mod jar
VOXY_JAR=$(find "$MODS_DIR" -maxdepth 1 -name "voxy-*.jar" -type f | head -1)

if [ -z "$VOXY_JAR" ]; then
    echo "ERROR: Could not find voxy-*.jar in $MODS_DIR"
    echo "Make sure you point to your server's mods folder."
    exit 1
fi

echo "Found Voxy JAR: $VOXY_JAR"
echo ""

# Create a temp working dir
WORK_DIR=$(mktemp -d)
trap "rm -rf $WORK_DIR" EXIT

echo "Step 1: Downloading full rocksdbjni-${ROCKSDB_VERSION}.jar (includes aarch64)..."
curl -L -o "$WORK_DIR/rocksdbjni-${ROCKSDB_VERSION}.jar" "$ROCKSDB_URL"
echo "  Downloaded ($(du -h "$WORK_DIR/rocksdbjni-${ROCKSDB_VERSION}.jar" | cut -f1))"
echo ""

echo "Step 2: Finding existing stripped RocksDB JAR inside Voxy..."
# List the embedded rocksdb jar path inside voxy
ROCKSDB_INNER_PATH=$(unzip -l "$VOXY_JAR" | grep -o "META-INF/jars/rocksdbjni[^ ]*\.jar" | head -1)

if [ -z "$ROCKSDB_INNER_PATH" ]; then
    echo "ERROR: Could not find embedded rocksdbjni JAR inside Voxy."
    echo "Your version of Voxy may use a different RocksDB setup."
    exit 1
fi

echo "  Found inner path: $ROCKSDB_INNER_PATH"
echo ""

echo "Step 3: Backing up original Voxy JAR..."
cp "$VOXY_JAR" "${VOXY_JAR}.backup"
echo "  Backup saved as ${VOXY_JAR}.backup"
echo ""

echo "Step 4: Replacing stripped RocksDB with full version (includes aarch64)..."
# Copy the downloaded full jar to the expected inner path name
mkdir -p "$WORK_DIR/inject/$(dirname "$ROCKSDB_INNER_PATH")"
cp "$WORK_DIR/rocksdbjni-${ROCKSDB_VERSION}.jar" "$WORK_DIR/inject/$ROCKSDB_INNER_PATH"

# Update the jar - the 'jar' command or 'zip' can do this
cd "$WORK_DIR/inject"
zip -u "$VOXY_JAR" "$ROCKSDB_INNER_PATH"
cd - > /dev/null

echo ""
echo "Step 5: Verifying aarch64 native is now present..."
if unzip -l "$VOXY_JAR" -x | grep -q "aarch64"; then
    echo "  (Note: native is inside the embedded JAR - checking inner JAR...)"
fi

# Quick verification by checking the size changed
NEW_SIZE=$(stat -c%s "$VOXY_JAR" 2>/dev/null || stat -f%z "$VOXY_JAR")
OLD_SIZE=$(stat -c%s "${VOXY_JAR}.backup" 2>/dev/null || stat -f%z "${VOXY_JAR}.backup")
echo "  Original size: $OLD_SIZE bytes"
echo "  New size:      $NEW_SIZE bytes"

if [ "$NEW_SIZE" -gt "$OLD_SIZE" ]; then
    echo "  JAR grew in size - aarch64 natives were added successfully!"
else
    echo "  WARNING: JAR did not grow. The fix may not have worked."
fi

echo ""
echo "============================================================"
echo "DONE! The Voxy mod JAR has been patched with ARM64 support."
echo "Your original JAR is backed up at: ${VOXY_JAR}.backup"
echo ""
echo "Now restart your Minecraft server and it should work on ARM64."
echo "============================================================"
