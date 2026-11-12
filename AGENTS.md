# AGENTS.md

Notes for anyone (human or agent) changing this repository.

## What this is

**World Provinces — Reborn** is a from-scratch Android rebuild of the map game
*World Provinces* (`eu.uvdb.game.worldprovinces`, v1.39.1). It is a personal,
non-commercial project: modern UI, no ads, no network, and it reads the original
game's SQLite database without conversion so existing saves keep working.

Two hard constraints shape almost every decision:

1. **Database compatibility is sacred.** Any file this app writes must still open
   in the original app, and any file the original app writes must open here.
2. **It must stay fast on cheap phones.** The world is 4,658 polygons; nothing
   may redraw geometry during a gesture.

## Layout

```
app/src/main/java/io/wpreborn/app/
  MainActivity.kt            entry point; forces the dark theme
  WorldViewModel.kt          all UI state, undo stacks, coroutine plumbing
  data/WorldContent.kt       reads assets/content/content.bin (immutable map facts)
  data/Geo.kt                parsed Paths, bounds, draw order, colour helpers, thumbnails
  data/WorldDb.kt            SQLiteOpenHelper with the original schema + bootstrap fallback
  data/WorldRepository.kt    maps, painting, countries, provinces, import/export/backup
  map/MapView.kt             custom View: rendering, gestures, hit testing
  ui/App.kt                  root scaffold, boot/busy/error screens, snackbars
  ui/HomeScreen.kt           world list, thumbnails, import/restore, NameDialog
  ui/MapScreen.kt            map chrome, palette, country/province dialogs
  ui/Theme.kt                Material 3 colour scheme and type scale
  ui/Common.kt               flag loading, colour dots, preset palette
app/src/main/assets/content/
  content.bin                extracted geometry + country metadata (do not hand-edit)
  flags/*.png                the 218 flags referenced by content.bin
  bootstrap.db               prebuilt first-run database (do not hand-edit)
tools/extract_data.py        regenerates content.bin and flags from a decoded APK
tools/make_bootstrap.py      regenerates bootstrap.db from content.bin
.github/workflows/release.yml  CI: build + publish a GitHub release on push to main
```

## Build

JDK 17 and Android SDK platform 35 + build-tools 35.0.0 are required. The Gradle
version is pinned by the wrapper, so use it rather than a system `gradle`:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/root/android-sdk
./gradlew :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew :app:assembleDebug
```

Locally, `local.properties` with `sdk.dir=...` also works; it is gitignored.
There are no unit or instrumentation tests — verification is manual (see below).

### Versioning

`versionCode` and `versionName` come from `WP_VERSION_CODE` / `WP_VERSION_NAME`
(Gradle property or environment variable), defaulting to `1` / `2.0`. CI sets
`versionCode = github.run_number` and `versionName = 2.0.<run_number>`, so never
hard-code a version bump in `build.gradle.kts`; publish by pushing to `main`.

### Signing

`keystore/release.jks` (alias and passwords `wpreborn`) is committed on purpose so
successive builds install as updates. CI prefers `keystore/wp-ci.jks`, restored
from the `KEYSTORE_BASE64` secret when it exists, and falls back to the committed
key. Optional secrets: `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

CI refuses to publish an APK that requests `android.permission.INTERNET`. Keep it
that way: the "no network" promise is part of the product.

## Data pipeline

The map is extracted from the original APK's string arrays. To regenerate:

```bash
apktool d -f -s -o /tmp/wp base.apk
python3 tools/extract_data.py /tmp/wp/apktool_out
python3 tools/make_bootstrap.py
```

`content.bin` is big-endian `WPC2`: countries (code, mode, base/light/dark colour,
name, flag, capital, continent, neighbours, central province) then provinces
(code, name, mode, country, continent, neighbours, polygons as `x,y` float pairs,
already normalised to start at 0,0).

Path parsing is **not** standard SVG. The original encodes points as
space-separated `x,y` tokens where a `V` token means "previous x, this y" and an
`H` token means "this x, previous y"; every `M` starts a new subpath and there is
no implicit command repetition. `tools/extract_data.py` implements exactly this —
do not "fix" it to a normal SVG parser.

Never edit `content.bin`, the flag PNGs, or `bootstrap.db` by hand. Change the
generators, re-run them, and commit the regenerated assets together with the code
that consumes them.

## Database contract

Database name: `worldprovincesdb`, `user_version` 13. Tables:

- `co` countries (`co_code` unique, `co_custom_name`, `co_custom_color`,
  `co_custom_flag`, `co_flag_file` BLOB, `co_capital`, `co_name_capital`, `ma_id`,
  `co_status`), indexes on code / custom name / custom code.
- `pr` provinces (`pr_code` unique, `co_id`, `pr_mode`, `pr_name`,
  `pr_custom_name`, `pr_continent`).
- `ma` maps/worlds, `mc` map-country membership, `pc` province ownership
  (`mc_id`, `pr_id`, `pc_save_mc_id`), `hi` paint history, `cf` key/value,
  `re` log.

Rules that must not regress:

- Built-in countries use a marker name `<@$%*%$@><CODE>` in `co_custom_name`;
  the display name is resolved from `content.bin` when the marker is present.
  Never write a resolved name into the database, or the original app loses the
  link between the row and its resources.
- `pc.pc_save_mc_id` holds the previous owner when a province changes hands;
  `hi` rows record every change with a round number. Painting must update both.
- `pr_mode`: `3` = land (paintable), `2` = sea (blended with ocean), `1` and `6`
  = ocean. Only land provinces are created in `pc` and only land is hit-testable.
- Imports replace the whole file. Always back up first
  (`files/database_backups`, five most recent), and keep the "restore last
  backup" path working.
- Opening a file must never mutate it beyond what SQLite itself does; export is
  a byte copy of the database after closing the helper.

## Performance rules

The current numbers (software-GPU emulator): ~65 ms full-world render, ~62 MB PSS,
1.6–1.8 s cold start. Do not regress these:

- `Geo.paths` is built once; never re-parse path strings or build `Path` objects
  per frame.
- Rendering happens on the single `map-renderer` thread into a reused bitmap.
  `onDraw` only blits the last bitmap; during gestures it must not draw geometry.
- `scheduleRender()` is debounced and never blocked by a gesture: while
  `gesturing` it renders coarse frames (no anti-aliasing, borders or grid) on a
  ~55 ms throttle, then a full-quality frame when the gesture ends. Keep the
  `renderInFlight` / `pendingRender` / `forceFullQuality` handshake so frames
  cannot pile up and the last frame is always sharp.
- Draw order is ocean → ocean extras → seas → land (`Geo.drawOrder`). Borders are
  one combined land-only path, not one stroke per province.
- Hit testing rejects by bounding box before any polygon test, and only land
  provinces are considered.
- Thumbnails reuse the same geometry at low resolution, are rendered off the main
  thread, and are requested per visible card (never in a loop over every world).
  `WorldRepository.loadColors` is the lightweight path they use: two queries and
  a colour array, no country objects.
- Never select `co_flag_file` in list queries. Imported databases can hold
  hundreds of custom flag blobs; `CountryFlag` fetches one blob on demand via
  `WorldRepository.countryFlagBytes`.

## Gesture handling

`MapView` tracks one `activePointerId`. On `ACTION_POINTER_UP` it must rebase
`lastX/lastY/downX/downY` to the surviving pointer and forbid panning until the
next `ACTION_DOWN`; otherwise the next move compares against the finger that
left and the map teleports. Two-finger pan comes from the scale detector's
focus delta, and `scaleFactor` is clamped to `0.5..2` per event as a guard.

## Imported databases

- Only `ma_status = 0` worlds are real; `ma_status = 1` rows are the original
  app's `map_tech<timestamp>` scratch maps and must stay hidden from the list.
- Import streams into `databases/import_probe.db`, validates, backs up (skipped
  above 256 MB), then renames over the live database. Never copy the file into
  the cache directory: originals reach ~500 MB.
- A world can hold 1,600+ countries, 1.4 million `mc` rows and 6.6 million `pc`
  rows. Anything that iterates worlds must stay lazy.

## UI conventions

- Compose + Material 3, dark theme only (`WpTheme(dark = true)`), edge-to-edge.
- Colour and spacing come from `ui/Theme.kt` (`Ink`, `Night`, `SurfaceNight`,
  `Periwinkle`, `Gold`, `Mist`, `Muted`) — avoid new hard-coded colours except
  translucent overlays.
- All database work goes through `WorldRepository` on `Dispatchers.IO`; the
  ViewModel publishes immutable state and reloads `MapRuntime` after a mutation
  rather than patching arrays in place.
- Dialogs take/return plain values; `NameDialog` uses `TextFieldValue` so the
  cursor starts at the end of the prefilled text.

## Manual verification

The emulator is the test suite. Typical smoke pass after a change to the map or
data layer (device 1220×2712 in the screenshots):

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n io.wpreborn.app/.MainActivity
# home → open a world → jump continent → tap a province → Rename → paint → undo
```

Things worth checking: map fills the screen after the first asynchronous render;
Europe/Asia jumps centre correctly; tapping selects the smallest land province
under the finger; painting recolours immediately and undo restores the previous
owner; renaming a province survives a relaunch; a new country can be created and
painted; export writes a file to Downloads and re-importing it preserves custom
names and colours. `adb logcat -s WPRender` prints render timings.

Release builds are not debuggable, so `run-as` only works on debug builds when
inspecting the database on-device.

## Commit style

Imperative subject under ~72 characters, then a short paragraph explaining the
*why* when the change is not obvious. Keep generated assets in their own commit
when they change independently of behaviour.
