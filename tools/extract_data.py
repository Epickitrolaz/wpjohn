#!/usr/bin/env python3
"""Extract World Provinces map data from the decoded APK resources.

Outputs (into app/src/main/assets/content/):
  content.bin  compact binary: countries, provinces, packed polygon geometry
  flags/*.png  national flag images referenced by the original resources
"""
import os
import re
import struct
import sys
import shutil

SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/opencode/wp/apktool_out"
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/assets/content")

arrays = open(os.path.join(SRC, "res/values/arrays.xml"), encoding="utf-8").read()
strings = open(os.path.join(SRC, "res/values/strings.xml"), encoding="utf-8").read()


def strmap():
    d = {}
    for name, val in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', strings, re.S):
        val = val.strip()
        val = (val.replace("\\'", "'").replace("\\@", "@").replace("\\?", "?")
               .replace("\\#", "#").replace("\\n", " ").replace("&amp;", "&")
               .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"'))
        d[name] = val
    return d


SMAP = strmap()


def items(name):
    for tag in ("array", "string-array"):
        m = re.search(r'<%s name="%s">(.*?)</%s>' % (tag, re.escape(name), tag), arrays, re.S)
        if m:
            return [i.strip() for i in re.findall(r'<item>(.*?)</item>', m.group(1), re.S)]
    return None


def unescape(s):
    return (s.replace("\\#", "#").replace("\\'", "'").replace("\\@", "@")
            .replace("\\?", "?").replace("\\\\", "\\"))


def resolve(s):
    s = s.strip()
    if s.startswith("@string/"):
        return SMAP.get(s[8:], "")
    return unescape(s)


def color(s):
    s = unescape(s.strip())
    if not s:
        return 0
    if s.startswith("#"):
        h = s[1:]
        if len(h) == 6:
            return 0xFF000000 | int(h, 16)
        if len(h) == 8:
            return int(h, 16)
    return 0


def parse_path(d):
    """Path subset used by World Provinces.

    Each "M" starts a new sub path. Tokens are separated by spaces and are
    either "x,y", "V<y>" or "H<x>". There is no implicit command repetition:
    a V token becomes (previous x, y) and an H token becomes (x, previous y).
    """
    subpaths = []
    for chunk in d.split("M")[1:]:
        pts = []
        prev_x = 0.0
        prev_y = 0.0
        for token in chunk.split(" "):
            token = token.strip()
            if not token:
                continue
            if "V" in token:
                token = ("%r," % prev_x) + token.replace("V", "")
            if "H" in token:
                token = token.replace("H", "") + (",%r" % prev_y)
            parts = token.split(",")
            if len(parts) < 2:
                continue
            try:
                x = float(parts[0])
                y = float(parts[1])
            except ValueError:
                continue
            pts.append(x)
            pts.append(y)
            prev_x = x
            prev_y = y
        if len(pts) >= 6:
            subpaths.append(pts)
    return subpaths


class W:
    def __init__(self, path):
        self.f = open(path, "wb")

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.f.close()

    def i32(self, v):
        self.f.write(struct.pack(">i", v))

    def u32(self, v):
        self.f.write(struct.pack(">I", v & 0xFFFFFFFF))

    def u8(self, v):
        self.f.write(struct.pack("<B", v))

    def str(self, s):
        b = (s or "").encode("utf-8")
        self.i32(len(b))
        self.f.write(b)

    def f32(self, v):
        self.f.write(struct.pack(">f", v))


country_names = re.findall(r'<(?:string-)?array name="(map_country_[^"]+)"', arrays)
province_names = re.findall(r'<string-array name="(map_province_[^"]+)"', arrays)

countries = []
for n in country_names:
    it = items(n)
    if not it or len(it) < 14:
        continue
    code = unescape(it[0])
    countries.append({
        "code": code,
        "mode": int(it[1] or 0),
        "base": color(it[2]),
        "light": color(it[3]) or color(it[2]),
        "dark": color(it[4]) or color(it[2]),
        "name": resolve(it[6]) or resolve(it[9]) or code,
        "flag": unescape(it[5]).replace("@drawable/", "") if it[5].startswith("@drawable/") else "",
        "capitalProv": unescape(it[12]),
        "capitalName": unescape(it[13]),
        "continent": int(it[10] or 0),
        "neighbors": unescape(it[7]),
        "central": unescape(it[8]),
    })

# global geometry bounds
minx = miny = 1e9
maxx = maxy = -1e9
provinces = []
for n in province_names:
    it = items(n)
    if not it or len(it) < 16:
        continue
    subs = parse_path(it[8])
    if not subs:
        continue
    for sp in subs:
        for k in range(0, len(sp), 2):
            minx = min(minx, sp[k]); maxx = max(maxx, sp[k])
            miny = min(miny, sp[k + 1]); maxy = max(maxy, sp[k + 1])
    provinces.append((it, subs))

cc = {c["code"]: c for c in countries}
out = []
for it, subs in provinces:
    code = unescape(it[0])
    country = unescape(it[4])
    if country not in cc:
        # orphan province: associate with itself to keep data
        country = country
    out.append({
        "code": code,
        "name": resolve(it[2]) or code,
        "mode": int(it[3] or 0),
        "country": country,
        "continent": int(it[15] or 0),
        "neighbors": unescape(it[10]),
        "subs": subs,
    })

print("countries", len(countries), "provinces", len(out))
print("bounds", minx, miny, maxx, maxy)

os.makedirs(OUT, exist_ok=True)
os.makedirs(os.path.join(OUT, "flags"), exist_ok=True)

with W(os.path.join(OUT, "content.bin")) as w:
    w.f.write(b"WPC2")
    w.i32(len(countries))
    for c in countries:
        w.str(c["code"]); w.u8(c["mode"])
        w.u32(c["base"]); w.u32(c["light"]); w.u32(c["dark"])
        w.str(c["name"]); w.str(c["flag"])
        w.str(c["capitalProv"]); w.str(c["capitalName"])
        w.i32(c["continent"]); w.str(c["neighbors"]); w.str(c["central"])
    w.i32(len(out))
    for p in out:
        w.str(p["code"]); w.str(p["name"]); w.u8(p["mode"])
        w.str(p["country"]); w.i32(p["continent"]); w.str(p["neighbors"])
        w.i32(len(p["subs"]))
        for sp in p["subs"]:
            pts = [(sp[k] - minx, sp[k + 1] - miny) for k in range(0, len(sp), 2)]
            w.i32(len(pts))
            for px, py in pts:
                w.f32(px); w.f32(py)

flags = set(c["flag"] for c in countries if c["flag"])
copied = 0
for f in flags:
    src = os.path.join(SRC, "res/drawable/%s.png" % f)
    if os.path.exists(src):
        shutil.copyfile(src, os.path.join(OUT, "flags", f + ".png"))
        copied += 1
print("flags copied", copied)

with open(os.path.join(OUT, "meta.txt"), "w") as f:
    f.write("bounds %f %f %f %f\n" % (minx, miny, maxx, maxy))

# quick continent report
from collections import Counter
print("continent distribution", Counter(c["continent"] for c in countries))
print("country mode distribution", Counter(c["mode"] for c in countries))
print("province mode distribution", Counter(p["mode"] for p in out))
