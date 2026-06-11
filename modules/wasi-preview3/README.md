# KRWA WASI Preview 3 Kotlin Facade

`uk.shusek.krwa:wasi-preview3` is the first-party Kotlin entrypoint for the
WASI Preview 3 release-candidate support in KRWA.

It depends on `component-model`, re-exports the WIT runtime handle types, and
adds Kotlin coroutine conveniences on top:

- `KotlinWasiPreview3`, a Kotlin-facing builder/facade around `WasiPreview3`,
- `WasiPreview3.await(future)` for typed `WitFuture<T>` values,
- `WitFuture<T>.asDeferred(...)`,
- `Deferred<T>.toCompletedWitFuture(...)`,
- byte stream helpers for `stream<u8>`,
- typed list stream helpers for host-side stream values,
- Kotlin-first clock and random builder APIs using `kotlin.time.Duration`,
  Kotlin lambdas, `kotlin.random.Random`, and unsigned seeds,
- `WasiFileSystem`, an Okio-style first-party facade over WASI preopened
  directories.

Example:

```kotlin
import kotlinx.coroutines.runBlocking
import uk.shusek.krwa.wasi.preview3.KotlinWasiPreview3
import uk.shusek.krwa.wasi.preview3.asDeferred

fun main() = runBlocking {
    val runtime = KotlinWasiPreview3.builder()
        .withNetworking()
        .build()

    val future = runtime.completed("ready")
    val value = runtime.await(future)
    val deferred = future.asDeferred(runtime.wasi, this)

    check(value == deferred.await())
}
```

This module does not replace the canonical ABI model. `future<T>` and
`stream<T>` still cross the Wasm boundary as `WitFuture<T>` and `WitStream<T>`
handles; the facade is the Kotlin-friendly layer for first-party users.

Clock and random configuration also stay Kotlin-facing:

```kotlin
import kotlin.random.Random
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource
import uk.shusek.krwa.wasi.preview3.WasiInstant

val monotonicBase = TimeSource.Monotonic.markNow()
val runtime = KotlinWasiPreview3.builder()
    .withFixedWallClock(WasiInstant.fromEpochSeconds(1_780_963_200L), resolution = 1.nanoseconds)
    .withMonotonicClock { monotonicBase.elapsedNow() }
    .withMonotonicResolution(1.nanoseconds)
    .withSecureRandom(Random.Default)
    .withInsecureSeed(11uL, 12uL)
    .build()
```

Generated WIT contracts can target this facade package directly:

```kotlin
val kotlin = KotlinWitBindings.builder(witPackage)
    .withPackageName("example.generated")
    .withRuntimePackageName("uk.shusek.krwa.wasi.preview3")
    .build()
    .generate()
```

Filesystem usage stays capability-based: first preopen a host directory, then use
the facade rooted at the guest path:

```kotlin
val runtime = KotlinWasiPreview3.builder()
    .withPreopenedDirectory("/", "data")
    .build()

val fs = runtime.fileSystem("/")
fs.writeText("out/result.txt", "done")
val bytes = fs.readBytes("out/result.txt")
val stream = fs.readWitByteStream("out/result.txt", runtime.wasi)
```

The facade rejects paths that escape the preopen root, so `../outside.txt` is not
accepted.
