"""Builds an on-device Opus-MT translation bundle for ScanRead.

Steps:
  1. Download the ONNX export of an Opus-MT (Marian) model from Hugging Face.
  2. Replace the LM head's runtime Transpose of the shared embedding with a constant, so it can be
     weight-quantized (otherwise a full-precision copy of the 57k x 1024 embedding remains).
  3. Quantize MatMul and Gather weights to 4 bits (block-wise, symmetric) with ONNX Runtime.
  4. Export the tokenizer as plain text files that the app reads without extra dependencies.

Usage:
  python prepare_model.py --repo onnx-community/opus-mt-tc-big-en-tr --out build/opus-mt-en-tr

Output bundle:
  encoder.onnx, decoder.onnx      quantized encoder / merged decoder (with KV cache branch)
  vocab.txt                       one token per line; the line number is the token id
  source_pieces.tsv               SentencePiece pieces of the source language: piece, score, type
  model.properties                special token ids and decoder dimensions
  tokenizer_samples.tsv           reference tokenizations used to verify the app's tokenizer
  ATTRIBUTION.txt                 model license and attribution
"""
import argparse
import json
import os
import urllib.request

import onnx
import sentencepiece as spm
from onnx import numpy_helper
from onnxruntime.quantization import QuantFormat
from onnxruntime.quantization.matmul_nbits_quantizer import DefaultWeightOnlyQuantConfig, MatMulNBitsQuantizer

FILES = [
    "config.json",
    "source.spm",
    "target.spm",
    "vocab.json",
    "onnx/encoder_model.onnx",
    "onnx/decoder_model_merged.onnx",
]

SAMPLES = [
    "Hello there!",
    "No way! That's not fair!",
    "When you're older, maybe you'll understand.",
    "It doesn't matter. If the train is late, then we'll just walk to the station together!",
    "Hey, Mr. Baker!",
    "Wait...   what?! 100% sure — “quoted” café naïve",
    "ÄÖÜ ß 1st 2nd ½ ﬁne Ｆｕｌｌ",
]


def download(repo: str, cache: str) -> None:
    for name in FILES:
        target = os.path.join(cache, name)
        if os.path.exists(target):
            continue
        os.makedirs(os.path.dirname(target), exist_ok=True)
        url = f"https://huggingface.co/{repo}/resolve/main/{name}"
        print("downloading", url)
        urllib.request.urlretrieve(url, target)


def untie_lm_head(model: onnx.ModelProto, shared_weight: str) -> None:
    weight = next(i for i in model.graph.initializer if i.name == shared_weight)
    transposed = numpy_helper.from_array(numpy_helper.to_array(weight).T.copy(), "lm_head.weight_t")
    model.graph.initializer.append(transposed)
    for node in model.graph.node:
        for attribute in node.attribute:
            if attribute.type != onnx.AttributeProto.GRAPH:
                continue
            graph = attribute.g
            for transpose in [n for n in graph.node if n.op_type == "Transpose" and list(n.input) == [shared_weight]]:
                graph.node.remove(transpose)
                for consumer in graph.node:
                    for index, name in enumerate(consumer.input):
                        if name == transpose.output[0]:
                            consumer.input[index] = transposed.name


def quantize(model: onnx.ModelProto, block_size: int) -> onnx.ModelProto:
    original_opset = {o.domain: o.version for o in model.opset_import}
    config = DefaultWeightOnlyQuantConfig(
        block_size=block_size,
        is_symmetric=True,
        quant_format=QuantFormat.QOperator,
        op_types_to_quantize=("MatMul", "Gather"),
        quant_axes=(("MatMul", 0), ("Gather", 1)),
    )
    quantizer = MatMulNBitsQuantizer(model, algo_config=config)
    quantizer.process()
    result = quantizer.model.model
    # The quantizer may raise the default opset, but existing nodes still use the original one.
    for opset in result.opset_import:
        if opset.domain in ("", "ai.onnx") and "" in original_opset:
            opset.version = original_opset[""]
    return result


def export_tokenizer(cache: str, out: str, config: dict) -> None:
    vocab = json.load(open(os.path.join(cache, "vocab.json"), encoding="utf-8"))
    by_id = sorted(vocab.items(), key=lambda item: item[1])
    assert [i for _, i in by_id] == list(range(len(by_id))), "vocab ids must be contiguous"
    with open(os.path.join(out, "vocab.txt"), "w", encoding="utf-8", newline="\n") as f:
        for token, _ in by_id:
            f.write(token + "\n")

    source = spm.SentencePieceProcessor(model_file=os.path.join(cache, "source.spm"))
    with open(os.path.join(out, "source_pieces.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for piece_id in range(source.get_piece_size()):
            piece_type = (
                "unknown" if source.is_unknown(piece_id)
                else "control" if source.is_control(piece_id)
                else "normal"
            )
            f.write(f"{source.id_to_piece(piece_id)}\t{source.get_score(piece_id)}\t{piece_type}\n")

    unk = vocab["<unk>"]
    with open(os.path.join(out, "tokenizer_samples.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for sample in SAMPLES:
            ids = [vocab.get(p, unk) for p in source.encode(sample, out_type=str)] + [config["eos_token_id"]]
            f.write(sample + "\t" + " ".join(map(str, ids)) + "\n")

    d_model = config["d_model"]
    heads = config["decoder_attention_heads"]
    properties = {
        "eos_token_id": config["eos_token_id"],
        "pad_token_id": config["pad_token_id"],
        "decoder_start_token_id": config["decoder_start_token_id"],
        "unk_token_id": unk,
        "decoder_layers": config["decoder_layers"],
        "decoder_attention_heads": heads,
        "head_dim": d_model // heads,
        "max_position_embeddings": config.get("max_position_embeddings", 512),
    }
    with open(os.path.join(out, "model.properties"), "w", encoding="utf-8", newline="\n") as f:
        for key, value in properties.items():
            f.write(f"{key}={value}\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo", default="onnx-community/opus-mt-tc-big-en-tr")
    parser.add_argument("--out", required=True)
    parser.add_argument("--cache", default="build/cache")
    parser.add_argument("--block-size", type=int, default=32)
    args = parser.parse_args()

    cache = os.path.join(args.cache, args.repo.replace("/", "__"))
    os.makedirs(args.out, exist_ok=True)
    download(args.repo, cache)
    config = json.load(open(os.path.join(cache, "config.json"), encoding="utf-8"))

    encoder = onnx.load(os.path.join(cache, "onnx/encoder_model.onnx"))
    onnx.save_model(quantize(encoder, args.block_size), os.path.join(args.out, "encoder.onnx"))

    decoder = onnx.load(os.path.join(cache, "onnx/decoder_model_merged.onnx"))
    untie_lm_head(decoder, "model.shared.weight_merged_0")
    onnx.save_model(quantize(decoder, args.block_size), os.path.join(args.out, "decoder.onnx"))

    export_tokenizer(cache, args.out, config)
    with open(os.path.join(args.out, "ATTRIBUTION.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(
            f"Model: {args.repo} (ONNX export of Helsinki-NLP Opus-MT), quantized to 4 bits by ScanRead.\n"
            "Original work: Tiedemann, J. & Thottingal, S. (2020). OPUS-MT - Building open translation\n"
            "services for the World. EAMT 2020.\n"
            "License: CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)\n"
        )

    for name in sorted(os.listdir(args.out)):
        print(f"{os.path.getsize(os.path.join(args.out, name)) / 1e6:8.1f} MB  {name}")


if __name__ == "__main__":
    main()
