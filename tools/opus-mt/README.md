# Opus-MT model tools

ScanRead's high-quality translation engine runs [Opus-MT](https://github.com/Helsinki-NLP/Opus-MT) (Marian) models on-device with ONNX Runtime. These tools turn a published ONNX export into a compact bundle the app can load.

## Why a custom bundle

Public ONNX exports are built for browsers and are large (about 530 MB for `opus-mt-tc-big-en-tr` in int8), mostly because the 57k × 1024 shared embedding is stored several times. `prepare_model.py`:

1. replaces the LM head's runtime `Transpose` of the shared embedding with a constant, so it can be quantized;
2. quantizes `MatMul` and `Gather` weights to 4 bits (block size 32, symmetric) with ONNX Runtime;
3. exports the SentencePiece tokenizer as plain text files.

For `opus-mt-tc-big-en-tr` this gives **~223 MB** (encoder 85 MB, decoder 138 MB). On a set of real comic dialogue lines, the 4-bit model produced the same output as the int8 export for most sentences, with the remaining differences being equivalent rewordings.

## Usage

```bash
python -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python prepare_model.py --repo onnx-community/opus-mt-tc-big-en-tr --out build/opus-mt-en-tr

# Debug builds: copy the bundle onto a connected device
sh install_on_device.sh build/opus-mt-en-tr
```

On Windows (Git Bash) run the install script with `MSYS_NO_PATHCONV=1` so device paths are not rewritten.

To verify the app's tokenizer against the real SentencePiece library:

```bash
SCANREAD_OPUS_BUNDLE=build/opus-mt-en-tr ./gradlew testDebugUnitTest --tests '*SentencePieceTokenizerTest*'
```

## Publishing a bundle

The app downloads bundles from GitHub release assets of this repository and verifies every file against the size and SHA-256 pinned in `OpusMtCatalog.kt`.

1. Create a release with the tag listed in the catalog (e.g. `model-opus-mt-en-tr-v1`) and upload `encoder.onnx`, `decoder.onnx`, `vocab.txt`, `source_pieces.tsv`, `model.properties` and `ATTRIBUTION.txt` as assets:
   ```bash
   gh release create model-opus-mt-en-tr-v1 build/opus-mt-en-tr/{encoder.onnx,decoder.onnx,vocab.txt,source_pieces.tsv,model.properties,ATTRIBUTION.txt} \
     --title "Opus-MT en→tr model (4-bit)" --notes-file build/opus-mt-en-tr/ATTRIBUTION.txt
   ```
2. When a bundle changes, publish it under a new tag and update the tag, sizes and digests in `OpusMtCatalog.kt` (`sha256sum <file>`).

The base URL comes from the Gradle property `scanread.modelReleasesUrl` (default: this repository's releases). To test downloads without publishing, serve a directory containing `<tag>/<files>` locally and forward the port to the device:

```bash
python -m http.server 8765 --bind 127.0.0.1      # in the directory that contains <tag>/
adb reverse tcp:8765 tcp:8765
./gradlew installDebug -Pscanread.modelReleasesUrl=http://localhost:8765
```

Debug builds allow plain HTTP to `localhost` only; release builds require HTTPS.

## Bundle format

| File | Content |
|---|---|
| `encoder.onnx`, `decoder.onnx` | 4-bit encoder and merged decoder (with key/value cache branch) |
| `vocab.txt` | model vocabulary, one token per line (line number = id) |
| `source_pieces.tsv` | source-language SentencePiece pieces: piece, score, type |
| `model.properties` | special token ids and decoder dimensions |
| `tokenizer_samples.tsv` | reference tokenizations for tests (not needed on device) |
| `ATTRIBUTION.txt` | model license and attribution |

## License

Opus-MT models are released under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) by the Language Technology Research Group at the University of Helsinki. Redistributed bundles must keep `ATTRIBUTION.txt`.
