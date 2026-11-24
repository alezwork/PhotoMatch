<p align="center">
  <a href="README.md">English</a> | <strong>Русский</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-11%2B%20(API%2030%2B)-3DDC84?logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/UI-Kotlin%20%2B%20Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin + Jetpack Compose">
  <img src="https://img.shields.io/badge/Core-Rust%20(JNI%20%2B%20Rayon)-DEA584?logo=rust&logoColor=black" alt="Ядро на Rust">
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline-22C55E" alt="100% офлайн">
</p>

# 📸 PhotoMatch

Быстрое, полностью офлайн Android-приложение для разбора галереи свайпами, как в Tinder: **вправо — оставить**, **влево — в корзину**, **вверх — в избранное**.

Создано для **телефонов и планшетов** (в любой ориентации) на **Kotlin + Jetpack Compose** с многопоточным **нативным ядром на Rust**, которое остаётся плавным даже на медиатеках из 100 000+ снимков.

---

## ✨ Возможности

- **Колода карточек со свайпами** — плавная физика свайпов со штампами, тактильным откликом, кнопками действий, отменой на 200 шагов (`↺`) и управлением с клавиатуры на планшетах (`→` оставить, `←` в корзину, `↑` избранное, `Z` отмена).
- **Адаптивный Bento-интерфейс** — тёмный edge-to-edge дизайн на Material 3: на планшетах (`≥ 640 dp`) колода и боковая панель свойств отображаются рядом, на телефонах свойства открываются нижним листом.
- **Свойства фото и цветовая палитра** — извлекает до 6 доминирующих цветов с HEX-кодами (`SwatchCard`), показывает дату, размер, разрешение, мегапиксели, папку и полные EXIF-данные (камера, объектив, диафрагма, выдержка, ISO, фокусное расстояние, GPS-координаты).
- **«Похожие снимки» и «Выбери лучшее»** — фоновый pHash-движок на Rust автоматически находит серии и дубли по всей галерее; выберите лучшие кадры в группе, а остальные отправьте в корзину одним нажатием.
- **Лента «В тот же момент»** — группирует соседние кадры, снятые в пределах 45 минут, чтобы видеть контекст прямо в панели свойств.
- **Удобные режимы разбора и видео** — разбор всей галереи, по **месяцам и годам**, либо **только видео** (с автовоспроизведением без звука через Media3 ExoPlayer).
- **Безопасная корзина и избранное** — файлы не удаляются безвозвратно в обход системы: пачка до 2 000 файлов отправляется через системный диалог `MediaStore.createTrashRequest` (можно восстановить 30 дней), а избранное синхронизируется с системной галереей через `MediaStore.createFavoriteRequest`.
- **100% локально и приватно** — приложение **не запрашивает разрешение на интернет**; ни одно фото и ни один хэш не покидают устройство.

---

## ⚡ Как достигается скорость на больших библиотеках (100k+ файлов)

```text
Android UI (Kotlin + Jetpack Compose + Material 3)
├── MediaRepository    Запрос MediaStore одним курсором (только метаданные, пиксели не читаются)
├── Coil 3 + Media3    Аппаратный ContentResolver.loadThumbnail + предзагрузка 5 карточек + ExoPlayer
└── NativeCore (JNI) ──▶ rust/ (photomatch_core · arm64-v8a & x86_64)
                          ├── hash.rs     Параллельный 64-битный pHash (DCT 32×32 → медиана 8×8) на rayon
                          ├── cluster.rs  Серии в окне 3 ч (≤10 бит) + глобальные дубли по 4×16-бит полосам (≤3 бит) + Union-Find
                          └── store.rs    Отказоустойчивые append-only бинарные логи (decisions.log и hashes.log, 16 Б/запись)
```

- **Мгновенный старт**: вся галерея считывается одним курсором по минимальному набору колонок.
- **Свайпы без задержек**: миниатюры идут через системный кэш (`ContentResolver.loadThumbnail`) и память Coil 3, а следующие 5 полноэкранных карточек предзагружаются заранее.
- **Хэши считаются один раз**: 32×32 ч/б миниатюры хэшируются пачками на всех ядрах CPU и сохраняются в бинарный append-only лог (`hashes.log`, 16 байт на запись с автоматической компактификацией при росте >2×; 100k записей ≈ 1.6 МБ).

---

## 📁 Структура проекта

```text
PhotoMatch/
├── app/
│   ├── build.gradle.kts                 # Конфигурация Android + автозадача cargoBuild
│   ├── proguard-rules.pro               # Сохраняет JNI-методы NativeCore для R8
│   └── src/main/
│       ├── AndroidManifest.xml          # Только разрешения на медиа (без INTERNET)
│       └── java/com/photomatch/
│           ├── MainActivity.kt          # Edge-to-edge экран, запросы разрешений и MediaStore
│           ├── AppViewModel.kt          # Состояние, колода, фоновое хэширование, группы, история undo
│           ├── core/NativeCore.kt       # JNI-обёртка над libphotomatch_core.so
│           ├── data/                    # Курсор MediaStore, EXIF, 6-цветная палитра, загрузчик миниатюр
│           └── ui/
│               ├── screens/             # Onboarding, Home (Bento), Deck, Details, Collections (Похожие/Месяцы/Корзина/Избранное)
│               ├── components/          # SwipeDeck, SwatchCard, AuroraBackdrop, DitherWave, Mascot
│               └── theme/               # Тёмная палитра, типографика, зернистость и переливы
├── rust/
│   ├── Cargo.toml                       # photomatch_core (jni + rayon, релизный профиль LTO + strip)
│   └── src/
│       ├── lib.rs                       # JNI-экспорты
│       ├── hash.rs                      # Параллельный 64-битный DCT pHash + юнит-тесты
│       ├── cluster.rs                   # Группировка серий и глобальных дублей, «моменты» + юнит-тесты
│       └── store.rs                     # Отказоустойчивое append-only KV-хранилище + юнит-тесты
├── tools/
│   ├── prep_library.py                  # Подготовка уменьшенных копий медиатеки с EXIF для эмулятора
│   └── tar_stream.py                    # Потоковая передача UTF-8 tar через adb exec-in (обход кириллицы на Windows)
├── README.md                            # Документация (английский)
└── README.ru.md                         # Документация (русский)
```

---

## 🔧 Сборка из исходников

### Требования
- **JDK 17**
- **Android SDK** (`compileSdk 37.1`, `minSdk 30`, **Android NDK**)
- **Rust** с таргетами Android и утилитой `cargo-ndk`:
  ```bash
  rustup target add aarch64-linux-android x86_64-linux-android
  cargo install cargo-ndk
  ```

### Сборка APK
Gradle автоматически компилирует Rust-ядро в `app/src/main/jniLibs/` (`arm64-v8a` и `x86_64`) задачей `cargoBuild` перед каждой сборкой:

```bash
# Отладочная сборка
./gradlew assembleDebug

# Оптимизированная release-сборка (R8 + сжатие ресурсов; холодный старт ~0.4 с против ~1.9 с у debug)
./gradlew assembleRelease
# Результат: app/build/outputs/apk/release/app-release.apk
```

> **Примечание:** в `app/build.gradle.kts` тип сборки `release` по умолчанию использует debug-подпись для удобной локальной установки. Перед публичной дистрибуцией подключите собственный keystore.

### Тесты Rust-ядра
```bash
cd rust
cargo test --release
```

---

## 🧪 Тестирование с большой медиатекой в эмуляторе

Чтобы проверить работу на крупной галерее в Android-эмуляторе (например, на AVD с разделом данных 24 ГБ):

1. Создайте копии под экран (2560 px по длинной стороне, JPEG q88, EXIF и даты файлов сохраняются, видео ≤ 10 МБ; **оригиналы только читаются**):
   ```bash
   python -I tools/prep_library.py "<папка-с-фото>" "<папка-копий>"
   ```
2. Передайте файлы в эмулятор потоком `tar` (в Windows `adb push` некорректно работает с кириллицей в путях):
   ```bash
   python -I tools/tar_stream.py "<папка-копий>" | adb -s emulator-5554 exec-in sh -c "mkdir -p /sdcard/Pictures/Library && cd /sdcard/Pictures/Library && tar -xf -"
   ```
3. Запустите сканирование MediaStore:
   ```bash
   adb -s emulator-5554 shell content call --uri content://media --method scan_volume --arg external_primary
   ```
