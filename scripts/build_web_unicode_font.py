#!/usr/bin/env python3
"""Build Tango pro's Web font from pinned OFL-licensed Noto sources.

Requires fonttools (`python3 -m pip install fonttools`). The generated font keeps
the CJK coverage of Noto Sans SC and adds missing Latin, mathematical, and
general-symbol glyphs needed by arbitrary UTF-8 CSV imports.
"""

from __future__ import annotations

import copy
import hashlib
import sys
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path

try:
    from fontTools.ttLib import TTFont
except ImportError as error:
    raise SystemExit("fonttools is required: python3 -m pip install fonttools") from error


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "webApp/src/commonMain/composeResources/font/tango_pro_unicode.ttf"


@dataclass(frozen=True)
class Source:
    filename: str
    url: str
    sha256: str


SOURCES = {
    "cjk": Source(
        "NotoSansSC.ttf",
        "https://raw.githubusercontent.com/google/fonts/2894aab31764f10f29c421bdfd2340d3b382d384/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf",
        "a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da",
    ),
    "latin": Source(
        "NotoSans.ttf",
        "https://raw.githubusercontent.com/google/fonts/2984c575fdce412ee02b2baaba67672b9a9434d8/ofl/notosans/NotoSans%5Bwdth,wght%5D.ttf",
        "bfb7bb691513f12e734dc346c03a03f784912432d7e3fa8e56efcf906fe86b3d",
    ),
    "math": Source(
        "NotoSansMath.ttf",
        "https://raw.githubusercontent.com/google/fonts/dbd1ab6e65dc59bcda3ca8de9fd372f58f98e0af/ofl/notosansmath/NotoSansMath-Regular.ttf",
        "3f495fe933c06786e4d5f6d86b8ee70b6753a68ee3b9d87528726de0f6e2c47d",
    ),
    "symbols": Source(
        "NotoSansSymbols2.ttf",
        "https://raw.githubusercontent.com/google/fonts/7b6724ac7ececc713e9ba93af309f7520c9a80a3/ofl/notosanssymbols2/NotoSansSymbols2-Regular.ttf",
        "7d5fb73b7ca67a6798101741f5d280a3d016a56a197afcd4199dbb57b4b82a21",
    ),
}


LATIN_RANGES = (
    (0x00A0, 0x024F),
    (0x0300, 0x036F),
    (0x1E00, 0x1EFF),
    (0x2C60, 0x2C7F),
    (0xA700, 0xA7FF),
    (0xFB00, 0xFB06),
)
MATH_RANGES = ((0x2000, 0x2BFF), (0x1D400, 0x1D7FF), (0x1F780, 0x1F7D8))
SYMBOL_RANGES = ((0x2300, 0x2BFF), (0x1F000, 0x1FBFF))
REQUIRED_TEXT = (
    "日本語 简体中文 English Français cœur déjà garçon Português ação coração maçã não "
    "Español niño Deutsch Straße Polski żółć Čeština Příliš Türkçe ğüşİ "
    "≒ ≈ ≠ ≤ ≥ → ← ⇔ ⇒ ℃ № ∴ ± × ÷ ∞ √ ∑ ∫ ♡ ★ ✓ ☑"
)


def download(source: Source, directory: Path) -> Path:
    destination = directory / source.filename
    with urllib.request.urlopen(source.url) as response:
        data = response.read()
    digest = hashlib.sha256(data).hexdigest()
    if digest != source.sha256:
        raise RuntimeError(f"checksum mismatch for {source.filename}: {digest}")
    destination.write_bytes(data)
    return destination


def unicode_cmap(font: TTFont) -> dict[int, str]:
    result: dict[int, str] = {}
    for table in font["cmap"].tables:
        if table.isUnicode():
            result.update(table.cmap)
    return result


def in_ranges(codepoint: int, ranges: tuple[tuple[int, int], ...]) -> bool:
    return any(start <= codepoint <= end for start, end in ranges)


def add_missing_glyphs(
    target: TTFont,
    donor_path: Path,
    prefix: str,
    ranges: tuple[tuple[int, int], ...],
    glyph_order: list[str],
    known_names: set[str],
) -> None:
    donor = TTFont(donor_path)
    for tag in ("glyf", "hmtx", "cmap"):
        donor[tag]
    selected = {
        codepoint: name
        for codepoint, name in unicode_cmap(donor).items()
        if codepoint not in unicode_cmap(target) and in_ranges(codepoint, ranges)
    }
    renamed: dict[str, str] = {}

    def copy_glyph(name: str) -> str:
        if name in renamed:
            return renamed[name]
        new_name = f"{prefix}_{name}"
        suffix = 1
        while new_name in known_names:
            suffix += 1
            new_name = f"{prefix}_{name}_{suffix}"
        renamed[name] = new_name
        glyph = copy.deepcopy(donor["glyf"][name])
        if glyph.isComposite():
            for component in glyph.components:
                component.glyphName = copy_glyph(component.glyphName)
        target["glyf"].glyphs[new_name] = glyph
        target["hmtx"].metrics[new_name] = donor["hmtx"].metrics[name]
        if "gvar" in target:
            target["gvar"].variations[new_name] = []
        known_names.add(new_name)
        glyph_order.append(new_name)
        return new_name

    for codepoint, name in selected.items():
        new_name = copy_glyph(name)
        for table in target["cmap"].tables:
            if table.isUnicode() and (table.format != 4 or codepoint <= 0xFFFF):
                table.cmap[codepoint] = new_name


def rename_font(font: TTFont) -> None:
    replacements = {
        0: "Tango Pro Unicode font, derived from OFL-licensed Noto font sources.",
        1: "Tango Pro Unicode",
        2: "Regular",
        3: "TangoProUnicode-20260826",
        4: "Tango Pro Unicode Regular",
        6: "TangoProUnicode-Regular",
        16: "Tango Pro Unicode",
        17: "Regular",
    }
    for record in font["name"].names:
        value = replacements.get(record.nameID)
        if value is not None:
            record.string = value.encode(record.getEncoding(), errors="replace")


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="tango-pro-font-") as temporary:
        directory = Path(temporary)
        downloaded = {name: download(source, directory) for name, source in SOURCES.items()}
        target = TTFont(downloaded["cjk"])
        # These tables use glyph indexes, so decompile them before extending the order.
        for tag in ("glyf", "hmtx", "gvar", "HVAR", "cmap", "maxp"):
            if tag in target:
                target[tag]
        glyph_order = list(target.getGlyphOrder())
        known_names = set(glyph_order)
        add_missing_glyphs(target, downloaded["latin"], "latin", LATIN_RANGES, glyph_order, known_names)
        add_missing_glyphs(target, downloaded["math"], "math", MATH_RANGES, glyph_order, known_names)
        add_missing_glyphs(target, downloaded["symbols"], "symbol", SYMBOL_RANGES, glyph_order, known_names)
        target.setGlyphOrder(glyph_order)
        target["maxp"].numGlyphs = len(glyph_order)
        # Original glyphs still vary through gvar. Added static glyphs use hmtx widths.
        if "HVAR" in target:
            del target["HVAR"]
        rename_font(target)
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        target.save(OUTPUT)

    verified = TTFont(OUTPUT)
    covered = set(unicode_cmap(verified))
    missing = sorted({ord(character) for character in REQUIRED_TEXT if not character.isspace()} - covered)
    if missing:
        rendered = ", ".join(f"U+{codepoint:04X}" for codepoint in missing)
        raise RuntimeError(f"generated font is missing required codepoints: {rendered}")
    print(f"Generated {OUTPUT} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Font build failed: {error}", file=sys.stderr)
        raise
