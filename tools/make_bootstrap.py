#!/usr/bin/env python3
"""Build the first-run database from the bundled content pack.

The resulting file is shipped as an asset so a fresh install opens instantly
instead of inserting ~10,000 rows on the device.
"""
import os
import struct
import sqlite3
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BIN = os.path.join(ROOT, "app/src/main/assets/content/content.bin")
OUT = os.path.join(ROOT, "app/src/main/assets/content/bootstrap.db")

NAME_MARKER = "<@$%*%$@>"

SCHEMA = [
    "CREATE TABLE IF NOT EXISTS co (_id INTEGER PRIMARY KEY, co_code TEXT UNIQUE NOT NULL,co_mode INTEGER NOT NULL,co_res_name TEXT NOT NULL,co_type INTEGER NOT NULL DEFAULT 0,co_res_flag INTEGER NOT NULL DEFAULT 0,co_custom_name TEXT NOT NULL DEFAULT '',co_custom_flag TEXT NOT NULL DEFAULT '',co_custom_color TEXT NOT NULL DEFAULT '',co_custom_code TEXT NOT NULL DEFAULT '',co_neighbors TEXT NOT NULL DEFAULT '',co_province_central TEXT NOT NULL DEFAULT '',co_continent INTEGER NOT NULL DEFAULT 0,co_flag_file BLOB NULL DEFAULT NULL,co_res_draw_flag_name TEXT NOT NULL DEFAULT '',co_capital TEXT NOT NULL DEFAULT '',co_name_capital TEXT NOT NULL DEFAULT '',ma_id INTEGER NOT NULL DEFAULT 0,co_status INTEGER NOT NULL DEFAULT 0)",
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_co__co_code ON co (co_code ASC)",
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_co__co_custom_name ON co (co_custom_name ASC)",
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_co__co_custom_code ON co (co_custom_code ASC)",
    "CREATE TABLE IF NOT EXISTS pr (_id INTEGER PRIMARY KEY, pr_code TEXT UNIQUE NOT NULL,co_id INTEGER NOT NULL, pr_mode INTEGER NOT NULL, pr_name TEXT NOT NULL DEFAULT '',pr_continent INTEGER NOT NULL DEFAULT 0,pr_custom_name TEXT NOT NULL DEFAULT '', FOREIGN KEY(co_id) REFERENCES co(_id) )",
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_pr__pr_code ON pr (pr_code ASC)",
    "CREATE TABLE IF NOT EXISTS ma (_id INTEGER PRIMARY KEY, ma_name TEXT NOT NULL,ma_type INTEGER NOT NULL,ma_dti DATETIME NOT NULL,ma_identyf INTEGER NOT NULL,ma_mode INTEGER NOT NULL,ma_round INTEGER NOT NULL,ma_status INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_ma__ma_name ON ma (ma_name ASC, ma_status ASC)",
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_ma__ma_identyf ON ma (ma_identyf ASC)",
    "CREATE TABLE IF NOT EXISTS mc (_id INTEGER PRIMARY KEY, ma_id INTEGER NOT NULL, co_id INTEGER NOT NULL, FOREIGN KEY(ma_id) REFERENCES ma(_id), FOREIGN KEY(co_id) REFERENCES co(_id) )",
    "CREATE INDEX IF NOT EXISTS idx_mc__ma_id ON mc (ma_id ASC)",
    "CREATE INDEX IF NOT EXISTS idx_mc__co_id ON mc (co_id ASC)",
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_mc__unique ON mc (ma_id, co_id ASC)",
    "CREATE TABLE IF NOT EXISTS pc (_id INTEGER PRIMARY KEY, mc_id INTEGER NOT NULL,pr_id INTEGER NOT NULL,pc_save_mc_id INTEGER NOT NULL,FOREIGN KEY(mc_id) REFERENCES mc(_id), FOREIGN KEY(pr_id) REFERENCES pr(_id) )",
    "CREATE INDEX IF NOT EXISTS idx_pc__mc_id ON pc (mc_id ASC)",
    "CREATE INDEX IF NOT EXISTS idx_pc__pr_id ON pc (pr_id ASC)",
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_pc__unique ON pc (mc_id, pr_id ASC)",
    "CREATE TABLE IF NOT EXISTS re (_id INTEGER PRIMARY KEY, re_msg TEXT NOT NULL,re_dti DATETIME NOT NULL)",
    "CREATE TABLE IF NOT EXISTS cf (_id INTEGER PRIMARY KEY, cf_name TEXT NOT NULL,cf_dti DATETIME NOT NULL,cf_dtu DATETIME NOT NULL,cf_value DATETIME NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_cf__cf_name ON cf (cf_name ASC)",
    "CREATE TABLE IF NOT EXISTS hi (_id INTEGER PRIMARY KEY, pc_id INTEGER NOT NULL,mc_id_old INTEGER NOT NULL,mc_id_new INTEGER NOT NULL,cf_dti DATETIME NOT NULL,hi_round INTEGER NOT NULL,FOREIGN KEY(pc_id) REFERENCES pc(_id), FOREIGN KEY(mc_id_old) REFERENCES mc(_id), FOREIGN KEY(mc_id_new) REFERENCES mc(_id) )",
    "CREATE INDEX IF NOT EXISTS idx_hi__pc_id ON hi (pc_id ASC)",
    "CREATE INDEX IF NOT EXISTS idx_hi__hi_round ON hi (hi_round ASC)",
]


def read_content():
    d = open(BIN, "rb").read()
    o = [4]

    def i32():
        v = struct.unpack_from(">i", d, o[0])[0]
        o[0] += 4
        return v

    def u8():
        v = d[o[0]]
        o[0] += 1
        return v

    def u32():
        v = struct.unpack_from(">I", d, o[0])[0]
        o[0] += 4
        return v

    def st():
        n = i32()
        v = d[o[0]:o[0] + n].decode()
        o[0] += n
        return v

    assert d[:4] == b"WPC2"
    countries = []
    for _ in range(i32()):
        countries.append({
            "code": st(), "mode": u8(),
            "base": u32(), "light": u32(), "dark": u32(),
            "name": st(), "flag": st(), "capitalProv": st(), "capitalName": st(),
            "continent": i32(), "neighbors": st(), "central": st(),
        })
    provinces = []
    for _ in range(i32()):
        p = {
            "code": st(), "name": st(), "mode": u8(), "country": st(),
            "continent": i32(), "neighbors": st(),
        }
        for _ in range(i32()):
            n = i32()
            o[0] += n * 8
        provinces.append(p)
    return countries, provinces


def hexcolor(v):
    return "#%06X" % (v & 0xFFFFFF)


def main():
    countries, provinces = read_content()
    if os.path.exists(OUT):
        os.remove(OUT)
    db = sqlite3.connect(OUT)
    db.execute("PRAGMA journal_mode=OFF")
    db.execute("PRAGMA synchronous=OFF")
    for sql in SCHEMA:
        db.execute(sql)

    co_id = {}
    for c in countries:
        cur = db.execute(
            "insert into co(co_code,co_mode,co_res_name,co_type,co_res_flag,co_custom_name,"
            "co_custom_flag,co_custom_color,co_custom_code,co_neighbors,co_province_central,"
            "co_continent,co_flag_file,co_res_draw_flag_name,co_capital,co_name_capital,ma_id,co_status) "
            "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (c["code"], c["mode"], c["flag"], 0, 0, NAME_MARKER + c["code"], "",
             hexcolor(c["base"]), c["code"].replace("L_", ""), c["neighbors"],
             c["central"] or c["capitalProv"], c["continent"], None,
             c["flag"], c["capitalProv"], c["capitalName"], 0, 0),
        )
        co_id[c["code"]] = cur.lastrowid

    pr_id = {}
    for p in provinces:
        cur = db.execute(
            "insert into pr(pr_code,co_id,pr_mode,pr_name,pr_continent,pr_custom_name) "
            "values (?,?,?,?,?,?)",
            (p["code"], co_id[p["country"]], p["mode"], p["name"], p["continent"], ""),
        )
        pr_id[p["code"]] = cur.lastrowid

    now = int(time.time() * 1000)
    cur = db.execute(
        "insert into ma(ma_name,ma_type,ma_dti,ma_identyf,ma_mode,ma_round,ma_status) "
        "values (?,?,?,?,?,?,?)",
        ("World", 1, now, 1, 0, 0, 0),
    )
    map_id = cur.lastrowid

    mc_id = {}
    for c in countries:
        cur = db.execute("insert into mc(ma_id,co_id) values (?,?)", (map_id, co_id[c["code"]]))
        mc_id[c["code"]] = cur.lastrowid

    for p in provinces:
        if p["mode"] != 3:
            continue
        mc = mc_id[p["country"]]
        db.execute(
            "insert into pc(mc_id,pr_id,pc_save_mc_id) values (?,?,?)",
            (mc, pr_id[p["code"]], mc),
        )

    db.execute(
        "insert into cf(cf_name,cf_dti,cf_dtu,cf_value) values (?,?,?,?)",
        ("VERSION_DATA_DB", now, now, "13"),
    )
    db.execute("PRAGMA user_version=13")
    db.commit()

    counts = {t: db.execute("select count(*) from %s" % t).fetchone()[0]
              for t in ("co", "pr", "ma", "mc", "pc")}
    db.close()
    print("bootstrap db:", counts, os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
