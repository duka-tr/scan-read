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
