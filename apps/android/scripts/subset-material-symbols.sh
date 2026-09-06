#!/usr/bin/env bash
# Regenerate the bundled Material Symbols Rounded subset shared by core/ui.
#
# Source: google/material-design-icons variable font (Apache-2.0).
# Requires: curl, python3 with fonttools (pip3 install --user fonttools).
#
# Adding a glyph:
#   1. Look up the Rounded glyph on https://fonts.google.com/icons and copy its
#      codepoint (e.g. "eb47" for kitchen).
#   2. Add the codepoint to UNICODES below and reference the same "\uXXXX"
#      escape at the call site (asset icons live in AssetIcons.kt, navigation
#      glyphs in MainActivity.kt).
#   3. Run this script from the repository root:
#        bash apps/android/scripts/subset-material-symbols.sh
set -euo pipefail

FONT_URL="https://raw.githubusercontent.com/google/material-design-icons/master/variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"
OUT="$(cd "$(dirname "$0")/.." && pwd)/core/ui/src/main/res/font/material_symbols_rounded.ttf"

# Asset glyphs (AssetIcons.kt): devices, laptop, smartphone, tablet, headphones,
# watch, photo_camera, sports_esports, menu_book, keyboard, directions_bike, home.
# Navigation glyphs (MainActivity.kt): favorite, auto_graph, settings, add.
UNICODES="U+E326,U+E31E,U+E7BA,U+E32F,U+F01F,U+E334,U+E412,U+EA28,U+EA19,U+E312,U+E52F,U+E9B2,U+E87E,U+E4FB,U+E8B8,U+E145"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

curl -fsSL "$FONT_URL" -o "$tmp_dir/full.ttf"
python3 -m fontTools.subset "$tmp_dir/full.ttf" \
  --unicodes="$UNICODES" \
  --layout-features='*' \
  --output-file="$OUT"

echo "wrote $OUT ($(wc -c < "$OUT") bytes)"
