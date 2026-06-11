---
sidebar_position: 1
sidebar_label: From Chicory
title: Migrating from Chicory
---

# Migrating from Chicory to Kotlin Runtime Web Assembly

Kotlin Runtime Web Assembly is a fork of [Chicory](https://github.com/dylibso/chicory) by Dylibso, Inc.
This guide documents all breaking changes for users migrating from Chicory.

## Artifact Coordinates

| Chicory | Kotlin Runtime Web Assembly |
|---------|--------|
| `com.dylibso.chicory:runtime` | `uk.shusek.krwa:runtime` |
| `com.dylibso.chicory:compiler` | `uk.shusek.krwa:compiler` |
| `com.dylibso.chicory:wasm` | `uk.shusek.krwa:wasm` |
| `com.dylibso.chicory:wasi` | `uk.shusek.krwa:wasi` |
| `com.dylibso.chicory:annotations` | `uk.shusek.krwa:annotations` |
| `com.dylibso.chicory:annotations-processor` | `uk.shusek.krwa:annotations-processor` |
| `com.dylibso.chicory:log` | `uk.shusek.krwa:log` |
| `com.dylibso.chicory:bom` | `uk.shusek.krwa:bom` |

All module artifact names (`runtime`, `compiler`, `wasm`, etc.) are unchanged.

## Package Names

All packages have moved from `com.dylibso.chicory` to `uk.shusek.krwa`:

```
com.dylibso.chicory.runtime   ->  uk.shusek.krwa.runtime
com.dylibso.chicory.compiler  ->  uk.shusek.krwa.compiler
com.dylibso.chicory.wasm      ->  uk.shusek.krwa.wasm
com.dylibso.chicory.wasi      ->  uk.shusek.krwa.wasi
```

A global find-and-replace of `com.dylibso.chicory` to `uk.shusek.krwa` in your imports covers this.

## Exception Classes

The base exception and interruption exception have been renamed to better reflect their semantics:

| Chicory | Kotlin Runtime Web Assembly | Rationale |
|---------|--------|-----------|
| `ChicoryException` | `WasmEngineException` | Base for engine errors, distinct from `WasmException` (Wasm-level tagged exceptions from the exception-handling proposal) |
| `ChicoryInterruptedException` | `WasmInterruptedException` | Host-initiated interruption of Wasm execution |

All spec-aligned exception names are unchanged: `TrapException`, `InvalidException`, `MalformedException`, `UnlinkableException`, `UninstantiableException`.

## System Properties

| Chicory | Kotlin Runtime Web Assembly |
|---------|--------|
| `chicory.hugeMethodLimit` | `krwa.hugeMethodLimit` |
| `chicory.memCopyWorkaround` | `krwa.memCopyWorkaround` |
| `chicory.compiler.printUseOfInterpretedFunctions` | `krwa.compiler.printUseOfInterpretedFunctions` |

## CLI Binaries

| Chicory | Kotlin Runtime Web Assembly |
|---------|--------|
| `chicory` | `krwa` |
| `chicory-compiler` | `krwa-compiler` |

## Logger Name

The JUL/System logger name has changed from `"chicory"` to `"krwa"`.
If you configure logging levels for the runtime, update your logging configuration accordingly.

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT

docs.FileOps.writeResult("docs/migration", "from-chicory.md.result", "empty");
```
-->
