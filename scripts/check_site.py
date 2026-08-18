#!/usr/bin/env python3
from html.parser import HTMLParser
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent


class SiteParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = []
        self.links = []
        self.images = []
        self.json_ld = []
        self._in_json_ld = False
        self._json_parts = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if values.get("id"):
            self.ids.append(values["id"])
        if tag == "a":
            self.links.append(values.get("href", ""))
        if tag == "img":
            self.images.append(values)
        if tag == "script" and values.get("type") == "application/ld+json":
            self._in_json_ld = True
            self._json_parts = []

    def handle_data(self, data):
        if self._in_json_ld:
            self._json_parts.append(data)

    def handle_endtag(self, tag):
        if tag == "script" and self._in_json_ld:
            self.json_ld.append(json.loads("".join(self._json_parts)))
            self._in_json_ld = False


def fail(message):
    print(f"Site check failed: {message}", file=sys.stderr)
    raise SystemExit(1)


parser = SiteParser()
parser.feed((ROOT / "index.html").read_text(encoding="utf-8"))

if len(parser.ids) != len(set(parser.ids)):
    fail("duplicate HTML id")
required_sections = {"features", "csv", "download", "install", "community"}
missing_sections = required_sections.difference(parser.ids)
if missing_sections:
    fail(f"missing required section ids: {', '.join(sorted(missing_sections))}")
for link in parser.links:
    if link.startswith("#") and link[1:] not in parser.ids:
        fail(f"missing anchor target: {link}")
for image in parser.images:
    if not image.get("src") or "alt" not in image:
        fail("image is missing src or alt")
    if not (ROOT / image["src"]).is_file():
        fail(f"missing image asset: {image['src']}")

if not parser.json_ld or parser.json_ld[0].get("@type") != "SoftwareApplication":
    fail("SoftwareApplication JSON-LD is missing")

for asset in ("tokens.css", "assets/site.css", "robots.txt", "sitemap.xml", ".nojekyll"):
    if not (ROOT / asset).is_file():
        fail(f"missing site asset: {asset}")

ET.parse(ROOT / "sitemap.xml")
print("Site structure: PASS")
