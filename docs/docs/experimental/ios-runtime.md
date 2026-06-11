---
sidebar_label: iOS Runtime
title: iOS Runtime Portability
---

# iOS Runtime Portability

KRWA is currently built as a JVM runtime. Running the runtime on iOS requires moving the
interpreter path to Kotlin Multiplatform and keeping JVM-only execution modes behind JVM source
sets.

## Target Scope

The first portable target is:

- the `wasm` binary model and parser,
- the interpreted `runtime` execution path,
- host imports expressed with Kotlin APIs,
- memory implementations that do not depend on JVM reflection, `ByteBuffer`, `VarHandle`, or
  `sun.misc.Unsafe`.

The following remain JVM-only until they get platform-specific implementations:

- runtime compilation and build-time compiled machine classes,
- ASM bytecode generation,
- JPMS `module-info.java`,
- reflection-based class loading,
- JVM filesystem and stream APIs,
- WASI filesystem wiring based on `java.nio.file`.

## Current Blockers

These APIs prevent directly moving `wasm` and `runtime` sources to `commonMain`:

- `java.io.InputStream`, `java.io.OutputStream`, `File`,
- `java.nio.ByteBuffer`, `Path`, `Files`, `Charset`,
- `java.util.function.*`, Java streams and `Optional`,
- `Thread`, object monitors, `ConcurrentHashMap`,
- `VarHandle`, `MethodHandles`, `sun.misc.Unsafe`, reflection.

## Migration Direction

Use Kotlin APIs in common code first, then provide platform adapters:

- parse from `ByteArray` or `okio.Source` instead of `InputStream`,
- expose UTF-8 string helpers through Kotlin `encodeToByteArray` and `decodeToString`,
- replace `java.util.function.*` contracts with Kotlin function types,
- keep JVM bytecode compiler modules separate from the portable interpreter runtime,
- introduce platform memory implementations before moving `Instance` defaults to common code.

The JVM memory implementations can keep using JVM fences and optimized byte access. The portable
memory contract should not reference those JVM implementation classes directly.

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT

docs.FileOps.writeResult("docs/experimental", "ios-runtime.md.result", "empty");
```
-->
