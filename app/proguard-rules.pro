# Project-specific R8 rules.

# ONNX Runtime's native library looks up its Java classes, fields and methods by name (JNI).
# Its bundled consumer rules do not cover all of them (e.g. OnnxJavaType was renamed), which breaks
# the Opus-MT translation engine in minified builds.
-keep class ai.onnxruntime.** { *; }
