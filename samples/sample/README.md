# Kotlin Runtime Web Assembly Kotlin/WASI Sample

This is a standalone Gradle project. It uses Kotlin Multiplatform 2.4.0 to build a
`wasmWasi` guest module and a JVM host that runs the guest with Kotlin Runtime Web Assembly.

Run:

```shell
./gradlew runShowcase
```

The sample uses `includeBuild("../..")`, so Gradle substitutes the
`uk.shusek.krwa:*:0.3.0-SNAPSHOT` dependencies from the repository checkout. It does
not require public Maven artifacts. The showcase builds the Kotlin/WASI
executable and verifies:

- core Wasm parsing, instantiation, exports, branches, traps, memory, and host imports,
- `Store`-based cross-module imports,
- WASI Preview 1 by running the Kotlin 2.4 `wasmWasi` guest under Kotlin Runtime Web Assembly,
- WIT parsing and generated Kotlin contracts,
- WASIp3 RC metadata and generated Kotlin `suspend` plus typed `future`/`stream` handle contracts,
- WASIp3 RC runtime imports for CLI args/env/cwd, clocks, random, HTTP client, filesystem
  preopens and byte streams, TCP/UDP sockets, and canonical `future`/`stream` intrinsics,
- the first-party `wasi-preview3` facade with coroutine `await`/`Deferred`,
  Kotlin clock/random configuration, and byte-stream plus filesystem adapters,
- Component Model packaging/unbundling through `wasm-tools`,
- `WasmPlugin` canonical ABI calls,
- WASIp2 host wiring via `WasiPreview2`.
