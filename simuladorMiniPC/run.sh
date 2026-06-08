#!/usr/bin/env bash
# run.sh – Compile and launch simuladorMiniPC on macOS (aarch64 / x86_64)
# Requires: JDK 17+ in PATH
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

FX_DIR="$SCRIPT_DIR/libs/javafx-mac/lib"

# ── Validate JavaFX SDK presence ──────────────────────────────────────────────
if [ ! -f "$FX_DIR/javafx.base.jar" ]; then
    echo "ERROR: JavaFX macOS SDK not found at $FX_DIR"
    echo "Run the following to download it:"
    echo "  cd libs && curl -L -o openjfx-mac.zip https://download2.gluonhq.com/openjfx/17.0.14/openjfx-17.0.14_osx-aarch64_bin-sdk.zip && unzip openjfx-mac.zip && mv javafx-sdk-17.0.14 javafx-mac && rm openjfx-mac.zip"
    exit 1
fi

CP="$FX_DIR/javafx.base.jar:$FX_DIR/javafx.graphics.jar:$FX_DIR/javafx.controls.jar"
MOD_PATH="$FX_DIR"
ADD_MODS="javafx.controls,javafx.base,javafx.graphics"

# ── Compile ───────────────────────────────────────────────────────────────────
echo "Compiling..."
mkdir -p build/classes

# Build file list
find src -name "*.java" > build/filelist_compile.txt

javac \
    --module-path "$MOD_PATH" \
    --add-modules "$ADD_MODS" \
    -cp "$CP" \
    -source 17 -target 17 \
    -d build/classes \
    -encoding UTF-8 \
    @build/filelist_compile.txt

echo "Build OK."

# ── Copy JSON config files (resource files) ───────────────────────────────────
find src -name "*.json" | while read -r f; do
    rel="${f#src/}"
    dest="build/classes/$rel"
    mkdir -p "$(dirname "$dest")"
    cp "$f" "$dest"
done

# ── Run ───────────────────────────────────────────────────────────────────────
echo "Launching..."
java \
    --module-path "$MOD_PATH" \
    --add-modules "$ADD_MODS" \
    -cp "$CP:build/classes" \
    simuladorminipc.MainFrame
