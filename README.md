# ScanRead

**On-device screen translator for Android.** ScanRead reads the text shown on your screen, translates it, and draws the translation back over the original — right where it was, in a matching style — so the page reads as if it had been written in your language.

Everything runs locally on the device: no accounts, no paid APIs, and no internet connection required once language models are downloaded.

> **Status:** early development. Capture, on-device text recognition and translation work; seamless in-place rendering is in progress. See the [roadmap](#roadmap).

---

## Features

- **Floating button** – works on top of any app. Tap to translate what is currently on screen; drag it anywhere, and it snaps to the nearest edge.
- **Full-screen translation** – detects every text region on the screen and translates them all at once.
- **In-place rendering** – detects speech balloons, repaints their interior in the balloon's own color and fits the translation inside, so translated content blends into the image. Text outside balloons is covered in the surrounding color.
- **Region selection** – draw a rectangle to translate only part of the screen. *(planned)*
- **Fully offline** – text recognition and translation run on-device. Screen contents never leave the phone.
- **Localized UI** – every user-facing string lives in resource files, making new interface languages easy to add.

## How it works

```
 Screen capture  ─▶  Text recognition  ─▶  Text grouping  ─▶  Translation  ─▶  In-place rendering
 (MediaProjection)   (on-device OCR)       (lines → blocks)    (on-device)      (erase + redraw overlay)
```

1. **Capture** – the screen is mirrored through Android's `MediaProjection` API inside a foreground service. The app's own overlay is hidden for the moment of capture.
2. **Recognize** – on-device OCR returns text lines along with their positions.
3. **Group** – enclosed areas such as speech balloons are detected in the image; lines are split and merged so that each block holds the text of one area (side-by-side balloons are separated by their word layout), then normalized for translation (casing, hyphenated line breaks, glued words, dropped apostrophes).
4. **Translate** – each block is translated with an on-device model.
5. **Render** – the original text is covered using the surrounding background, and the translation is drawn into the same area on a transparent overlay in a comic-style font, hyphenated by the target language's rules when space is tight. Sound effects are left untouched.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for code-level details.

## Requirements

- Android 8.0 (API 26) or newer
- A one-time download of the language files (about 30 MB per language) from the home screen
- Permissions:
  - **Display over other apps** – floating button and translation overlay
  - **Screen capture** – requested by the system each time translation is started
  - **Notifications** *(optional, Android 13+)* – persistent notification with a stop action

> Some apps block screen capture for content protection. In those apps the captured image is blank and nothing can be translated.

## Building

1. Install [Android Studio](https://developer.android.com/studio) (it bundles the JDK and Android SDK).
2. Clone the repository and open the project folder in Android Studio.
3. Let Gradle sync, then run the `app` configuration on a device or emulator.

From the command line:

```bash
./gradlew assembleDebug        # build a debug APK
./gradlew installDebug         # build and install on a connected device
./gradlew testDebugUnitTest    # run unit tests
./gradlew lint                 # static checks (hard-coded text is treated as an error)
```

The debug build installs side by side with release builds (`.debug` application ID suffix).

## Project structure

```
app/src/main/
├── java/com/scanread/
│   ├── ScanReadApp.kt          Application entry point
│   ├── capture/                Screen capture (MediaProjection → Bitmap)
│   ├── core/                   Shared infrastructure (permissions, notifications)
│   ├── image/                  Platform-independent image analysis (enclosed areas)
│   ├── ocr/                    On-device text recognition engines
│   ├── overlay/                Views drawn above other apps
│   ├── pipeline/               Orchestrates capture → text analysis → translation
│   ├── render/                 Platform-independent drawing plans (balloon fills, text areas)
│   ├── service/                Foreground service that drives a translation session
│   ├── text/                   Platform-independent text layout and normalization logic
│   ├── translate/              Translation engine and language model abstractions (ML Kit implementation)
│   └── ui/                     Jetpack Compose screens and theme
└── res/
    ├── values/                 Default (English) strings, colors, dimensions
    └── values-tr/              Turkish translations

app/src/test/                   JVM unit tests
tools/opus-mt/                  Scripts that build the on-device Opus-MT model bundles
```

## Localization

All user-facing text is stored in `res/values*/strings.xml`; Lint fails the build on hard-coded text.

To add a language:

1. Create `app/src/main/res/values-<language code>/` (for example `values-de`).
2. Copy `values/strings.xml` into it and translate the values. Leave entries marked `translatable="false"` out.
3. Build. The language appears automatically in the system's per-app language settings (Android 13+).

## Roadmap

- [x] Project setup, permissions flow, floating button
- [x] Screen capture service
- [x] On-device text recognition and text-block grouping
- [x] On-device translation with language model management and caching
- [ ] In-place rendering (text removal, auto-fit typography)
- [ ] Region selection mode
- [ ] Settings (fonts, colors, languages) and translation cache
- [ ] Text-region detection model for complex layouts
- [ ] Additional source languages, including vertical text
- [ ] Pluggable higher-quality translation engines

## Translation engines

| Engine | Size | Quality | Notes |
|---|---|---|---|
| [Opus-MT](https://github.com/Helsinki-NLP/Opus-MT) via ONNX Runtime | ~220 MB per language pair | High | Preferred when installed; 4-bit model built with [`tools/opus-mt`](tools/opus-mt/README.md) |
| [ML Kit Translation](https://developers.google.com/ml-kit/language/translation) | ~30 MB per language | Basic | Fallback; many language pairs |

## Privacy

ScanRead processes screen contents only when you tap the floating button, entirely on your device. It does not collect, store, or transmit screen contents or analytics.

## License

To be decided.

### Third-party fonts

- [Comic Relief](https://github.com/loudifier/Comic-Relief) (Bold) © The Comic Relief Project Authors — [SIL Open Font License 1.1](app/src/main/assets/licenses/ComicRelief-OFL.txt). Used for rendered translations.

### Third-party models

- Opus-MT translation models © Language Technology Research Group, University of Helsinki — [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Tiedemann, J. & Thottingal, S. (2020). *OPUS-MT – Building open translation services for the World.* EAMT 2020. ScanRead redistributes them in 4-bit quantized form.
