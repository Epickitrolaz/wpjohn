# World Provinces — Reborn

A modern, ad-free, offline rebuild of the map-painting game **World Provinces**
(`eu.uvdb.game.worldprovinces`, version 1.39.1). It reads the original app's
database without conversion, so an existing world can be continued exactly
where it was left off.

<img src="docs/home.png" width="32%"> <img src="docs/map.png" width="32%"> <img src="docs/countries.png" width="32%">

## What is this

The original is a sandbox map game: a world map split into 4,658 provinces,
217 built-in countries with flags and capitals, and a paint-style interaction
that lets you redraw borders and build your own civilisations.

This rebuild keeps the part that matters — the world and the saved game — and
replaces everything around it:

- **Material 3 interface** designed from scratch (dark, high-contrast, edge to edge).
- **No ads, no trackers, no network access at all.** The release manifest has no
  `INTERNET` permission.
- **Full database compatibility.** Worlds, countries, custom colours, custom
  flags, custom province names, borders, capitals and history rows all survive
  an import/export round trip.
- **Built for slow devices.** Geometry is parsed once and drawn from a cached
  bitmap; gestures never re-render the world.

## Features

| | |
|---|---|
| Map | Whole world, 4,658 provinces, 217 countries |
| Navigation | Pinch zoom, drag, double-tap, zoom buttons, continent jumps |
| Painting | Pick a country, tap provinces; every change is undoable |
| Countries | Create, rename, recolour, set a custom flag, delete |
| Provinces | Inspect, rename (custom names are stored in the original table) |
| Worlds | Unlimited maps, each with its own borders and countries |
| Backup | Import/export the original database, automatic backup before import |
| Offline | Everything works without a connection |

## Importing the original database

1. In the original app use **Input → Backup Database to File** and save the
   file (it is usually called `worldprovincesdb`).
2. Copy it to the phone's **Downloads** folder (or use any file provider).
3. In this app tap **Import database from the original app** and choose the file.
4. Confirm. The current data is backed up automatically and can be restored
   with **Restore data from the last import**.

The imported file is used as-is: this app reads and writes the same SQLite
schema (`co`, `pr`, `ma`, `mc`, `pc`, `hi`, `cf`, `re`), version 13. Exporting
produces a file the original app can read back.

## Building

Requirements: JDK 17, Android SDK with platform 35 and build-tools 35.0.0.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/path/to/android-sdk
gradle :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk` (~3.7 MB).

The release build is signed with the key in `keystore/release.jks`
(alias/password: `wpreborn`). Keep that file if you want future builds to
install as updates over the current app.

### Regenerating the bundled data

The map data is extracted from the resource tables of the original APK:

```bash
python3 tools/extract_data.py /path/to/apktool/output
python3 tools/make_bootstrap.py
```

`extract_data.py` writes `app/src/main/assets/content/content.bin` and the flag
PNGs. `make_bootstrap.py` builds `bootstrap.db`, a ready-made first-run
database, so a fresh install opens instantly instead of inserting 10,000 rows.

## Performance notes

- Province polygons are parsed once into `android.graphics.Path` objects and
  kept in memory (~54,000 points).
- The visible world is rendered on a background thread into a reused bitmap.
  Panning and pinching only transform the last rendered bitmap, so no geometry
  is redrawn during a gesture.
- Only idle viewports are re-rendered; the renderer is debounced and skips
  frames while fingers are down.
- Country borders are drawn as one combined path instead of one stroke per
  province.
- Tap hit-testing uses bounding boxes first and polygon tests only for the few
  candidates under the finger.
- Measured on the test emulator (software GPU): ~65 ms per full-world render,
  ~62 MB total PSS.

## Project layout

```
app/src/main/java/io/wpreborn/app/
  data/WorldContent.kt   content pack reader
  data/Geo.kt            paths, bounds, draw order, thumbnails
  data/WorldDb.kt        original-compatible SQLite schema
  data/WorldRepository.kt maps, painting, countries, import/export
  map/MapView.kt         renderer, gestures, hit testing
  ui/                    Compose screens and theme
tools/                   data extraction and bootstrap scripts
```

## Known limitations

- The original app's round-replay and statistics screens are not rebuilt; the
  `hi` history table is still written and preserved.
- Capital provinces are displayed and imported but not editable yet.
- Undo is available for the current session (300 steps per world).
- The UI is English only.

## Data and attribution

This is a personal, non-commercial project and is not affiliated with the
author of the original game. Map geometry, country metadata and flag images
were extracted from the copy of the original application provided by its owner.
National flags are public-domain symbols. If you intend to distribute the app
publicly, replace the extracted assets with your own or verify the licence
terms of the original content.
