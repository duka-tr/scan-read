# Architecture

This document describes how the code is organized and the conventions it follows.

## Overview

ScanRead is a single-module Android app written in Kotlin. The UI uses Jetpack Compose; the overlay shown on top of other apps uses classic Android views, because it is managed directly through `WindowManager` from a service.

```
┌──────────────┐  consent result   ┌──────────────────────────┐
│ MainActivity │ ────────────────▶ │  ScreenTranslateService  │  (foreground, type: mediaProjection)
│  (Compose)   │ ◀──── isRunning ─ │                          │
└──────────────┘                   │  ├─ ScreenCapturer       │  MediaProjection → VirtualDisplay → ImageReader
                                   │  ├─ ScreenTextAnalyzer   │  OcrEngine → TextBlockBuilder
                                   │  ├─ FloatingButton       │  WindowManager overlay
                                   │  └─ TextBlockOverlay     │  WindowManager overlay (results)
                                   └──────────────────────────┘
```

## Text pipeline

```
         ┌─▶ OcrEngine ─────────▶ List<TextLine> ─┐
Bitmap ──┤   (ML Kit, on-device)  (text, words,   ├─▶ LineSplitter ─▶ TextBlockGrouper ─▶ TextNormalizer ─▶ List<TextBlock>
         │                         boxes)         │
         └─▶ EnclosedRegionMap ───────────────────┘
             (light areas such as balloon interiors)
```

- **`OcrEngine`** is an interface; `MlKitOcrEngine` uses the bundled Latin-script model, so recognition works offline and without Google Play services. Other scripts can be added as further engines.
- **`EnclosedRegionMap`** labels the connected light areas of the capture after sealing 1 px breaks in outlines. Each word or line can be mapped to the area it sits in (a `RegionLookup`). Areas that touch the screen border or are very large (page background) are marked as not enclosed.
- **`LineSplitter`** splits OCR lines that span two areas — OCR engines regularly read across adjacent balloons. Words in different areas, or separated by an unusually wide gap, start a new line.
- **`TextBlockGrouper`** merges lines into blocks instead of relying on the OCR engine's own grouping. Lines in different areas are never merged; lines in the same enclosed area are merged with relaxed rules (so emphasized, larger words stay in their balloon). Without area information, two lines belong together when their heights are similar, the vertical gap is small relative to line height, and they overlap horizontally.
- **`ColumnSplitter`** separates side-by-side balloons that ended up in one block (overlapping balloons form a single light area): words linked by small gaps on a line or by overlap on adjacent lines form one block each. Only blocks with three or more lines are considered.
- **`TextNormalizer`** joins lines, removes word-break hyphens and converts all-caps text to sentence case for better translation. `TextBlock.isAllCaps` keeps the original style so the translation can be rendered the same way.
- Everything under `text/` and `image/` is free of Android APIs and covered by JVM unit tests.

## Translation

```
List<TextBlock> ──▶ BlockTranslator ──▶ CachingTranslationEngine ──▶ TranslationEngine ──▶ List<TranslatedBlock> ──▶ TranslationOverlay
                    (restores caps,     (LRU, 500 entries)          (ML Kit, on-device)                              (TextFitter: largest
                     locale-aware)                                                                                    size that fits)
```

- With Opus-MT, **`WordSegmenter`** first splits words OCR glued together ("withyou" → "with you") using the model's own source vocabulary as a frequency dictionary; syllable-like parts are rejected so names stay intact. Dropped apostrophes are restored afterwards.
- **`BlockTranslator`** splits every block into sentences (`SentenceSplitter`) and sends all sentences of a screen to the engine as one batch; short exclamations are otherwise often dropped by translation models.
- **`TranslationEngine`** (batch API) and **`TranslationModelStore`** are interfaces. `TranslationBackends` picks the engine: **Opus-MT** (`translate/opusmt`) when its bundle is installed, **ML Kit** (`translate/mlkit`) otherwise.
- **`OpusMtTranslationEngine`** runs a 4-bit Marian model with ONNX Runtime: the encoder once per batch (up to 16 sentences, sorted by length), then the merged decoder with a key/value cache and greedy decoding. `SentencePieceTokenizer` is a dependency-free Kotlin port of SentencePiece's unigram encoder, verified against the reference library. Bundles are built by `tools/opus-mt/prepare_model.py` and stored in `files/models/<bundle>/`.
- **`LanguagePair`** currently translates from English into the device language (with a fallback when that is not possible). A language picker is planned.
- The home screen (`HomeViewModel`) downloads and deletes language models; the service refuses to translate until they are present.
- **`TextFitter`** finds the largest text size (up to roughly the original line height) at which the translation fits its text area without breaking words.

## Rendering

`BlockSurfacePlanner` (`render/`) decides per block how the original is hidden and where the translation goes:

- **Balloon** — the block alone occupies an enclosed area (`EnclosedRegionMap.shapeOf`): the balloon interior, including all text inside it (also characters OCR missed), is repainted in the balloon's average color, keeping the outline. The translation may use ~76% of the balloon's bounding box (an ellipse holds a ~71% rectangle), so longer translations fit.
- **Box** — otherwise, a rounded rectangle in the most common color around the text. It may grow up to 80% wider / 40% taller, but only into background-colored pixels, so it never covers outlines or artwork.

`SoundEffectFilter` leaves drawn lettering untouched: outside balloons, text much taller than the page's typical line height, or a single word the engine translates practically unchanged (a sound effect such as "BLAM"), is not covered. Rectangular covers also take in letter-sized marks on the line directly above or below a block, hiding short stylized lines OCR missed.

`TranslationRenderer` draws all covers before any text, so neighbouring covers never hide a translation. Text uses the bundled Patrick Hand font (narrow comic lettering). `TextFitter` keeps words whole when possible; if the text does not fit at the minimum size, it breaks lines only at hyphenation points of the target language (Android's patterns), and as a last resort overflows slightly.

An area only counts as a balloon when the text is near its center (≤ 22% offset) and at most 40% of it is enclosed dark content. Open balloons that leak into the panel background fail the centering test, which prevents repainting artwork. Text is dark on light covers and white on dark ones.

## Debugging recognition

Debug builds keep the 10 most recent captures (`capture_<stamp>.png`) and their analysis (`analysis_<stamp>.txt`: OCR lines, words, blocks, translations, timing) in the app's private storage:

```bash
adb exec-out run-as com.scanread.debug ls files/debug
adb exec-out run-as com.scanread.debug cat files/debug/<file> > <file>
```

`DebugDumpReplayTest` replays every dump in a directory through the text pipeline on the JVM, which makes it quick to tune grouping rules against real screens:

```bash
SCANREAD_DUMP_DIR=/path/to/dumps ./gradlew testDebugUnitTest --tests '*DebugDumpReplayTest*'
# results: /path/to/dumps/replay_<stamp>.txt
```

Captures may contain third-party content; keep them out of version control.

## Packages

| Package | Responsibility |
|---|---|
| `capture` | Owns the virtual display and returns the latest frame as a `Bitmap`. Handles screen size changes (rotation). |
| `core.permission` | Permission checks and settings intents. |
| `core.notification` | Notification channel registration. |
| `ocr` | `OcrEngine` abstraction and its ML Kit implementation. |
| `text` | Platform-independent models (`Box`, `TextWord`, `TextLine`, `TextBlock`), line splitting, grouping and normalization. |
| `image` | Platform-independent image analysis (`EnclosedRegionMap`, `RegionShape`). |
| `render` | Platform-independent drawing plans (`BlockSurface`, `BlockSurfacePlanner`). |
| `pipeline` | `ScreenTextAnalyzer` (capture → text blocks), `BlockTranslator` (blocks → translated blocks), debug dumps. |
| `translate` | `TranslationEngine`, `TranslationModelStore`, `LanguagePair`, caching; ML Kit implementations in `translate.mlkit`. |
| `overlay` | Views added through `WindowManager` above other apps (floating button, translation overlay, text fitting). |
| `service` | `ScreenTranslateService`, the lifecycle owner of a translation session. Exposes `isRunning` as a `StateFlow`. |
| `ui` | Compose screens (`*Route` = wiring and side effects, `*Screen` = stateless UI) and the theme. |

## Session lifecycle

1. The user grants the overlay permission (and optionally notifications) on the home screen.
2. Tapping **Start** launches the system screen-capture consent dialog.
3. The consent result is passed to `ScreenTranslateService`, which:
   1. promotes itself to the foreground (required before obtaining a `MediaProjection`),
   2. registers a `MediaProjection.Callback` (required on Android 14+ before creating a virtual display),
   3. starts `ScreenCapturer` and shows the `FloatingButton`.
4. Tapping the floating button hides it, waits briefly for a fresh frame, grabs the latest image and runs the text pipeline. Results are drawn by `TextBlockOverlay`; tapping the overlay or the button again dismisses them.
5. The session ends from the notification action, the home screen, or the system's capture indicator; `onDestroy` releases every resource.

Capture consent is single-use on recent Android versions, so each new session asks for it again.

## Conventions

- **No hard-coded user-facing text.** Strings live in `res/values/strings.xml` (English, default) with translations in `res/values-<lang>/`. Lint treats `HardcodedText` and `MissingTranslation` as errors.
- **Dependency versions** are declared only in `gradle/libs.versions.toml`.
- **Formatting** follows the Kotlin official style; see `.editorconfig`.
- **Compose screens** are split into a stateful `Route` and a stateless `Screen` with a preview.
