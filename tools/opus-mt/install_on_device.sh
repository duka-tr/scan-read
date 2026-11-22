#!/usr/bin/env sh
# Copies a prepared Opus-MT bundle into a debug build's private storage over adb.
#
# Usage: install_on_device.sh <bundle-dir> [bundle-name] [package]
#   bundle-name  directory name the app expects (default: opus-mt-en-tr)
#   package      application id (default: tr.duka.scanread.debug)
set -eu

BUNDLE_DIR=${1:?bundle directory required}
BUNDLE_NAME=${2:-opus-mt-en-tr}
PACKAGE=${3:-tr.duka.scanread.debug}
STAGING=/data/local/tmp/scanread-$BUNDLE_NAME
TARGET=files/models/$BUNDLE_NAME
ADB=${ADB:-adb}

"$ADB" shell rm -rf "$STAGING"
"$ADB" shell mkdir -p "$STAGING"
for file in encoder.onnx decoder.onnx vocab.txt source_pieces.tsv model.properties ATTRIBUTION.txt; do
    echo "pushing $file"
    "$ADB" push "$BUNDLE_DIR/$file" "$STAGING/$file" >/dev/null
done
"$ADB" shell chmod -R a+r "$STAGING"

# run-as can only write inside the app's own storage, so copy from the world-readable staging area.
"$ADB" shell run-as "$PACKAGE" rm -rf "$TARGET"
"$ADB" shell run-as "$PACKAGE" mkdir -p "$TARGET"
for file in encoder.onnx decoder.onnx vocab.txt source_pieces.tsv model.properties ATTRIBUTION.txt; do
    "$ADB" shell run-as "$PACKAGE" cp "$STAGING/$file" "$TARGET/$file"
done
"$ADB" shell rm -rf "$STAGING"
"$ADB" shell run-as "$PACKAGE" ls -l "$TARGET"
