<p align="center">
  <strong>English</strong> | <a href="README.ru.md">Русский</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-11%2B%20(API%2030%2B)-3DDC84?logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/UI-Kotlin%20%2B%20Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin + Jetpack Compose">
  <img src="https://img.shields.io/badge/Core-Rust%20(JNI%20%2B%20Rayon)-DEA584?logo=rust&logoColor=black" alt="Rust Core">
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline-22C55E" alt="100% Offline">
</p>

# 📸 PhotoMatch

A fast, 100% offline Android gallery cleaner designed for Tinder-style photo & video triage: **swipe right to keep**, **swipe left to trash**, **swipe up to favorite**.

Built for both **phones and tablets** (any orientation) with **Kotlin + Jetpack Compose** and a multi-threaded **Rust native core** that stays responsive even on 100,000+ item photo libraries.

---

## ✨ Key Features

- **Tinder-Style Swipe Deck** — fluid gesture deck with decision stamps, haptic feedback, action buttons, 200-step undo (`↺`), and tablet hardware keyboard shortcuts (`→` keep, `←` trash, `↑` favorite, `Z` undo).
- **Adaptive Bento UI** — edge-to-edge Material 3 dark interface tailored for both form factors: side-by-side deck + inspector panel on tablets (`≥ 640 dp`) and a modal bottom sheet on phones.
- **Rich Inspector & Dominant Color Palette** — extracts up to 6 dominant colors with hex swatches alongside date, size, resolution, megapixels, album folder, and full EXIF metadata (camera, lens, aperture, shutter speed, ISO, focal length, GPS coordinates).
- **Similar Photos & "Pick the Best"** — background Rust pHash engine automatically groups burst shots and near-duplicates across the entire library; pick the keeper(s) in each set and send the rest to trash in one tap.
- **"Same Moment" Strip** — clusters consecutive shots within a 45-minute window so you always see surrounding context while deciding.
- **Smart Scopes & Video Triage** — review the full library, filter by **month/year timeline**, or triage **videos only** (with muted Media3 ExoPlayer autoplay).
- **Safe System Trash & Favorites** — never permanently wipes files behind your back: moves up to 2,000 marked items in one batch through Android's `MediaStore.createTrashRequest` (recoverable for 30 days) and syncs favorites to the system gallery via `MediaStore.createFavoriteRequest`.
- **100% Private & On-Device** — requests **zero network permissions**; no photos, hashes, or metadata ever leave your device.

---

## ⚡ Performance Architecture (Built for 100k+ Libraries)

```text
Android UI (Kotlin + Jetpack Compose + Material 3)
├── MediaRepository    Single-cursor MediaStore query (metadata only, zero pixel I/O)
├── Coil 3 + Media3    Hardware ContentResolver.loadThumbnail + 5-card lookahead prefetch + ExoPlayer
└── NativeCore (JNI) ──▶ rust/ (photomatch_core · arm64-v8a & x86_64)
                          ├── hash.rs     Parallel 64-bit pHash (32×32 separable DCT → 8×8 median) via rayon
                          ├── cluster.rs  Time-window bursts (3 h, ≤10 bits) + global 4×16-bit LSH dups (≤3 bits) + Union-Find
                          └── store.rs    Crash-safe append-only binary logs (decisions.log & hashes.log, 16 B/record)
```

- **Instant startup**: Reads MediaStore in a single minimal-column query without decoding images.
- **Zero-lag swiping**: Uses Android's hardware thumbnail cache + Coil 3 memory cache and pre-decodes the next 5 full-screen cards ahead of the active card.
- **Compute once, cache forever**: Grayscale 32×32 thumbnails are hashed in parallel batches across all CPU cores and persisted in an append-only 16-byte/record binary log (`hashes.log`, auto-compacted when >2× live size; 100k entries ≈ 1.6 MB).

---

## 📁 Project Structure

```text
PhotoMatch/
├── app/
│   ├── build.gradle.kts                 # Android app config + automatic cargoBuild task
│   ├── proguard-rules.pro               # Keeps JNI NativeCore entry points for R8
│   └── src/main/
│       ├── AndroidManifest.xml          # Media permissions only (no INTERNET permission)
│       └── java/com/photomatch/
│           ├── MainActivity.kt          # Edge-to-edge host, permission & MediaStore launchers
│           ├── AppViewModel.kt          # State, deck, background hashing, grouping, undo stack
│           ├── core/NativeCore.kt       # JNI bridge to libphotomatch_core.so
│           ├── data/                    # MediaStore cursor, EXIF, 6-color palette, thumbnail fetcher
│           └── ui/
│               ├── screens/             # Onboarding, Home (Bento), Deck, Details, Collections (Similar/Timeline/Trash/Favorites)
│               ├── components/          # SwipeDeck, SwatchCard, AuroraBackdrop, DitherWave, Mascot
│               └── theme/               # Dark palette, typography, grain & iridescent effects
├── rust/
│   ├── Cargo.toml                       # photomatch_core (jni + rayon, LTO + strip release profile)
│   └── src/
│       ├── lib.rs                       # JNI exports
│       ├── hash.rs                      # Parallel 64-bit DCT pHash + unit tests
│       ├── cluster.rs                   # Burst + global LSH duplicate grouping, moments + unit tests
│       └── store.rs                     # Crash-safe append-only binary KV store + unit tests
├── tools/
│   ├── prep_library.py                  # Creates downscaled EXIF-preserving copies for emulator testing
│   └── tar_stream.py                    # Streams UTF-8 tar over adb exec-in (Windows Unicode workaround)
├── README.md                            # Documentation (English)
└── README.ru.md                         # Documentation (Russian)
```

---

## 🔧 Building from Source

### Prerequisites
- **JDK 17**
- **Android SDK** (`compileSdk 37.1`, `minSdk 30`, **Android NDK**)
- **Rust** toolchain with Android targets and `cargo-ndk`:
  ```bash
  rustup target add aarch64-linux-android x86_64-linux-android
  cargo install cargo-ndk
  ```

### Build APKs
Gradle automatically compiles the Rust core into `app/src/main/jniLibs/` (`arm64-v8a` and `x86_64`) via the `cargoBuild` task before every Android build:

```bash
# Debug build
./gradlew assembleDebug

# Optimized Release build (R8 minification + resource shrinking, ~0.4 s cold start vs ~1.9 s debug)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

> **Note:** In `app/build.gradle.kts`, the `release` build type uses the debug signing config by default for easy local sideloading. Configure your own release keystore before distributing publicly.

### Run Rust Core Unit Tests
```bash
cd rust
cargo test --release
```

---

## 🧪 Testing with a Large Library on an Emulator

To benchmark or test with a real multi-gigabyte photo library on an Android emulator (e.g. a phone or tablet AVD with a 24 GB data partition):

1. Create screen-sized copies (2560 px long side, JPEG quality 88, EXIF & timestamps preserved, videos ≤ 10 MB; **originals are read-only**):
   ```bash
   python -I tools/prep_library.py "<path-to-photos>" "<staging-dir>"
   ```
2. Stream the staging folder into the emulator over `adb` (uses UTF-8 tar streaming because Windows `adb push` struggles with non-ASCII/Cyrillic paths):
   ```bash
   python -I tools/tar_stream.py "<staging-dir>" | adb -s emulator-5554 exec-in sh -c "mkdir -p /sdcard/Pictures/Library && cd /sdcard/Pictures/Library && tar -xf -"
   ```
3. Trigger MediaStore indexing:
   ```bash
   adb -s emulator-5554 shell content call --uri content://media --method scan_volume --arg external_primary
   ```
