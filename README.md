<p align="center"><img src="docs/images/logo.svg" width="128" height="128" alt="ScanRead logo"></p>

<h1 align="center">ScanRead</h1>

**On-device screen translator for Android.** ScanRead reads the text shown on your screen, translates it, and draws the translation back over the original — right where it was, in a matching style — so the page reads as if it had been written in your language.

Everything runs locally on the device: no accounts, no ads, no paid APIs, and no internet connection required once the language files are downloaded.

> **Status:** early release. Works well for Latin-script text such as comics and other pages with speech balloons. See the [roadmap](#roadmap) for what is next.

---

## Features

- **Floating button** – works on top of any app. Tap to translate what is currently on screen; tap again or touch the translation to dismiss it. Drag the button anywhere; it snaps to the nearest edge.
- **In-place rendering** – detects speech balloons, repaints their interior in the balloon's own color and fits the translation inside in a comic-style font, so translated content blends into the image. Sound effects are left untouched.
- **High-quality offline translation** – Opus-MT neural translation models run on the device; Google ML Kit covers other language pairs.
- **Follows the screen** – the translation disappears as soon as you scroll or switch apps.
- **Private by design** – screen content is processed only when you tap the button, only on the device, and is never stored or sent anywhere.
- **Configurable** – language pair, button size and opacity, translation text size, sound-effect handling, app language.

## Install

Download the latest APK from [Releases](https://github.com/duka-tr/scan-read/releases/latest) and open it on your phone (allow installing from your browser or file manager when asked). The first-run guide walks you through the permissions and the language files. The app tells you when a new version is available.

## How it works

```
 Screen capture  ─▶  Text recognition  ─▶  Text grouping  ─▶  Translation  ─▶  In-place rendering
 (MediaProjection)   (on-device OCR)       (lines → blocks)    (on-device)      (erase + redraw overlay)
```

1. **Capture** – the screen is mirrored through Android's `MediaProjection` API inside a foreground service. The app's own overlay is hidden for the moment of capture.
2. **Recognize** – on-device OCR returns text lines along with their positions.
3. **Group** – enclosed areas such as speech balloons are detected in the image; lines are split and merged so that each block holds the text of one area (side-by-side balloons are separated by their word layout), then normalized for translation (casing, hyphenated line breaks, glued words, dropped apostrophes).
4. **Translate** – all sentences on the screen are translated in one batch with an on-device model.
5. **Render** – the original text is covered using the surrounding background, and the translation is drawn into the same area on a transparent overlay, hyphenated by the target language's rules when space is tight.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for code-level details.

## Requirements

- Android 8.0 (API 26) or newer
- A one-time download of the language files: the high-quality model (about 225 MB, resumable, verified by checksum) or the basic ML Kit files (about 30 MB per language)
- Permissions:
  - **Display over other apps** – floating button and translation overlay
  - **Screen capture** – requested by the system each time translation is started
  - **Notifications** *(optional, Android 13+)* – shows the language pair and a button to stop translation

> Some apps block screen capture for content protection. In those apps the captured image is blank and nothing can be translated.

## Translation engines

| Engine | Size | Quality | Language pairs |
|---|---|---|---|
| [Opus-MT](https://github.com/Helsinki-NLP/Opus-MT) via ONNX Runtime | ~225 MB per pair | High | English → Turkish (more planned) |
| [ML Kit Translation](https://developers.google.com/ml-kit/language/translation) | ~30 MB per language | Basic | Latin-script sources into 50+ languages |

The high-quality model is used automatically when it is installed for the selected pair. Models are built with [`tools/opus-mt`](tools/opus-mt/README.md) and published as release assets.

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

Release builds are signed with a key described in `keystore.properties` (not part of the repository):

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Optional Gradle properties: `scanread.modelReleasesUrl` (where models are downloaded from), `scanread.tips=true` (enables optional Google Play tips) and `scanread.distribution=play` (Google Play builds: turns off the in-app update check, since Play handles updates).

## Project structure

```
app/src/main/
├── java/tr/duka/scanread/
│   ├── ScanReadApp.kt          Application entry point
│   ├── capture/                Screen capture and screen-change detection
│   ├── core/                   Shared infrastructure (settings, permissions, notifications, app language)
│   ├── image/                  Platform-independent image analysis (enclosed areas)
│   ├── ocr/                    On-device text recognition engines
│   ├── overlay/                Views drawn above other apps
│   ├── pipeline/               Orchestrates capture → text analysis → translation
│   ├── render/                 Platform-independent drawing plans (balloon fills, text areas)
│   ├── service/                Foreground service that drives a translation session
│   ├── text/                   Platform-independent text layout and normalization logic
│   ├── translate/              Translation engines (Opus-MT, ML Kit) and model management
│   ├── ui/                     Jetpack Compose screens and theme
│   └── update/                 New-version check against GitHub releases (GitHub builds only)
└── res/
    ├── values/                 Default (English) strings, colors, dimensions
    └── values-tr/              Turkish translations

app/src/test/                   JVM unit tests
tools/opus-mt/                  Scripts that build and publish the on-device Opus-MT model bundles
docs/images/                    Logo (SVG master) and store / social images
```

## Localization

All user-facing text is stored in `res/values*/strings.xml`; Lint fails the build on hard-coded text.

To add a language:

1. Create `app/src/main/res/values-<language code>/` (for example `values-de`).
2. Copy `values/strings.xml` into it and translate the values. Leave entries marked `translatable="false"` out.
3. Build. The language appears automatically in the system's per-app language settings (Android 13+).

Translations of the app are welcome as pull requests.

## Roadmap

- [x] Floating button, screen capture, on-device OCR
- [x] Balloon-aware grouping and in-place rendering
- [x] High-quality offline translation (Opus-MT) with in-app model download
- [x] Settings, language selection, first-run guide
- [ ] Separate model repository with a signed catalog, so new language pairs arrive without an app update
- [ ] More high-quality language pairs
- [ ] Region selection (long-press the floating button)
- [ ] Japanese, Korean and Chinese sources, including vertical text
- [ ] Text-region detection model for complex layouts

## Privacy

ScanRead processes screen contents only when you tap the floating button, entirely on your device. It does not collect, store, or transmit screen contents, personal data or analytics. The internet is used only to download language files and, in builds from GitHub, to check the release list for a newer version (at most daily; can be turned off). Google Play builds can offer optional tips; payment is handled entirely by Google Play, and tipping unlocks nothing. See the [privacy policy](https://duka.tr/scanread/privacy/).

## Support and contact

- Report problems and ideas in [Issues](https://github.com/duka-tr/scan-read/issues).
- Contact: [support@duka.tr](mailto:support@duka.tr)
- Starring the repository helps others find the project.

## Contributing

Pull requests are welcome, including translations. Please read the duka.tr
[contributing guidelines](https://github.com/duka-tr/.github/blob/main/CONTRIBUTING.md) first, and report security
issues privately as described in the [security policy](https://github.com/duka-tr/.github/blob/main/SECURITY.md).

Specific to this project:

- Every user-facing text goes into `strings.xml` in English and Turkish; Lint fails the build on hard-coded text.
- Screen content never leaves the device. No analytics, and no network calls beyond downloading language files and
  the GitHub release check.
- Changes to text grouping, normalization or rendering come with unit tests. Debug dumps make it quick to check them
  against real screens (see [Debugging recognition](docs/ARCHITECTURE.md#debugging-recognition)).
- Do not commit screenshots, captures or pages from comics; tests use neutral sample sentences.
- Run `./gradlew testDebugUnitTest lint` before opening a pull request.

## License

ScanRead is licensed under the [Apache License 2.0](LICENSE). Copyright 2026 duka.tr. See [NOTICE](NOTICE) for third-party components.

### Third-party components

- **Opus-MT translation models** © Language Technology Research Group, University of Helsinki — [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Tiedemann, J. & Thottingal, S. (2020). *OPUS-MT – Building open translation services for the World.* EAMT 2020. Redistributed in 4-bit quantized form as release assets.
- **Comic Relief** font © The Comic Relief Project Authors — [SIL Open Font License 1.1](app/src/main/assets/licenses/ComicRelief-OFL.txt).
- **ONNX Runtime** © Microsoft Corporation — MIT License.
- **Google ML Kit** — [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms).
- **AndroidX, Jetpack Compose, Kotlin** — Apache License 2.0.
