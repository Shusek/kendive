@file:OptIn(kotlin.time.ExperimentalTime::class)

package uk.shusek.krwa.sample

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant as KotlinInstant
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import okio.Path.Companion.toPath
import uk.shusek.krwa.component.ComponentModelException
import uk.shusek.krwa.component.KotlinWitBindings
import uk.shusek.krwa.component.WasiPreview
import uk.shusek.krwa.component.WasiPreview2
import uk.shusek.krwa.component.WasiPreview3
import uk.shusek.krwa.component.WasmComponentTools
import uk.shusek.krwa.component.WasmPlugin
import uk.shusek.krwa.component.withInsecureRandom
import uk.shusek.krwa.component.withSecureRandom
import uk.shusek.krwa.component.WitPackage
import uk.shusek.krwa.runtime.HostFunction
import uk.shusek.krwa.runtime.ImportValues
import uk.shusek.krwa.runtime.Instance
import uk.shusek.krwa.runtime.Store
import uk.shusek.krwa.runtime.TrapException
import uk.shusek.krwa.runtime.WasmFunctionHandle
import uk.shusek.krwa.tools.wasm.Wat2Wasm
import uk.shusek.krwa.wasi.WasiExitException
import uk.shusek.krwa.wasi.WasiOptions
import uk.shusek.krwa.wasi.WasiPreview1
import uk.shusek.krwa.wasi.preview3.KotlinWasiPreview3
import uk.shusek.krwa.wasi.preview3.WasiInstant
import uk.shusek.krwa.wasi.preview3.asByteArray
import uk.shusek.krwa.wasi.preview3.asDeferred
import uk.shusek.krwa.wasi.preview3.readWitByteStream
import uk.shusek.krwa.wasi.preview3.writeWitByteStream
import uk.shusek.krwa.wasm.Parser
import uk.shusek.krwa.wasm.types.FunctionType
import uk.shusek.krwa.wasm.types.ValType

fun main() {
    val guestWasm =
        Path.of(
            requireNotNull(System.getProperty("krwa.sample.kotlinWasiWasm")) {
                "Missing -Dkrwa.sample.kotlinWasiWasm"
            }
        )

    Showcase(guestWasm).run()
}

private class Showcase(private val kotlinWasiGuest: Path) {
    private val completed = ArrayList<String>()

    fun run() {
        coreRuntime()
        hostImportsAndMemory()
        crossModuleStore()
        trapHandling()
        kotlinWasiPreview1()
        kotlinWasiPreview1Component()
        witBindings()
        wasiPreview3Contracts()
        wasiPreview3Runtime()
        componentModelPlugin()
        println("Kotlin Runtime Web Assembly runtime sample passed ${completed.size} checks:")
        completed.forEach { println(" - $it") }
    }

    private fun coreRuntime() {
        val instance =
            Instance.builder(
                    Parser.parse(
                        Wat2Wasm.parse(
                            """
                            (module
                              (func (export "add") (param i32) (param i32) (result i32)
                                local.get 0
                                local.get 1
                                i32.add)
                              (func (export "fac") (param i32) (result i32)
                                (local i32)
                                i32.const 1
                                local.set 1
                                block
                                  loop
                                    local.get 0
                                    i32.eqz
                                    br_if 1
                                    local.get 1
                                    local.get 0
                                    i32.mul
                                    local.set 1
                                    local.get 0
                                    i32.const 1
                                    i32.sub
                                    local.set 0
                                    br 0
                                  end
                                end
                                local.get 1))
                            """
                                .trimIndent()
                        )
                    )
                )
                .build()

        requireValue(42L, instance.export("add").apply(19L, 23L)[0], "core add export")
        requireValue(720L, instance.export("fac").apply(6L)[0], "branching factorial export")
        completed += "core Wasm parsing, instantiation, branches, and exports"
    }

    private fun hostImportsAndMemory() {
        var observed = ""
        val log =
            HostFunction(
                "host",
                "log",
                FunctionType.of(listOf(ValType.I32, ValType.I32), emptyList()),
                WasmFunctionHandle { instance, args ->
                    observed = instance.memory().readString(args[0].toInt(), args[1].toInt())
                    null
                },
            )
        val instance =
            Instance.builder(
                    Parser.parse(
                        Wat2Wasm.parse(
                            """
                            (module
                              (import "host" "log" (func (param i32) (param i32)))
                              (memory (export "memory") 1)
                              (data (i32.const 64) "hello from guest")
                              (func (export "run")
                                i32.const 64
                                i32.const 16
                                call 0))
                            """
                                .trimIndent()
                        )
                    )
                )
                .withImportValues(ImportValues.builder().addFunction(log).build())
                .build()

        instance.export("run").apply()
        requireValue("hello from guest", observed, "host import memory read")
        instance.memory().writeString(128, "host wrote memory")
        requireValue("host wrote memory", instance.memory().readString(128, 17), "host memory write")
        completed += "host imports and linear-memory read/write"
    }

    private fun crossModuleStore() {
        val store = Store()
        store.instantiate(
            "math",
            Parser.parse(
                Wat2Wasm.parse(
                    """
                    (module
                      (func (export "inc") (param i32) (result i32)
                        local.get 0
                        i32.const 1
                        i32.add))
                    """
                        .trimIndent()
                )
            ),
        )
        val consumer =
            store.instantiate(
                "consumer",
                Parser.parse(
                    Wat2Wasm.parse(
                        """
                        (module
                          (import "math" "inc" (func (param i32) (result i32)))
                          (func (export "run") (result i32)
                            i32.const 41
                            call 0))
                        """
                            .trimIndent()
                    )
                ),
            )
        requireValue(42L, consumer.export("run").apply()[0], "store cross-module import")
        completed += "Store registration and cross-module imports"
    }

    private fun trapHandling() {
        val instance =
            Instance.builder(
                    Parser.parse(
                        Wat2Wasm.parse(
                            """
                            (module
                              (func (export "fail")
                                unreachable))
                            """
                                .trimIndent()
                        )
                    )
                )
                .build()

        requireThrows<TrapException>("unreachable trap") { instance.export("fail").apply() }
        completed += "trap propagation"
    }

    private fun kotlinWasiPreview1() {
        require(Files.isRegularFile(kotlinWasiGuest)) {
            "Kotlin/WASI guest was not built: $kotlinWasiGuest"
        }

        val tempDir = Files.createTempDirectory("krwa-sample-wasi1")
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val wasi =
            WasiPreview1.builder()
                .withOptions(
                    WasiOptions.builder()
                        .withRandom(Random(7))
                        .withStdout(stdout)
                        .withStderr(stderr)
                        .withStdin(ByteArrayInputStream(ByteArray(0)))
                        .withArguments(listOf("kotlin-guest.wasm", "alpha", "beta"))
                        .withEnvironment("KRWA_SAMPLE", "preview1")
                        .withDirectory("/", tempDir.toOkioPath())
                        .build()
                )
                .build()

        try {
            try {
                Instance.builder(Parser.parse(kotlinWasiGuest))
                    .withImportValues(ImportValues.builder().addFunction(*wasi.toHostFunctions()).build())
                    .build()
            } catch (exit: WasiExitException) {
                requireValue(0, exit.exitCode(), "Kotlin/WASI guest exit code")
            }

            val text = stdout.toString(UTF_8)
            require(text.contains("Hello from Kotlin/WASI 2.4")) { text }
            require(text.contains("args.wasi=kotlin-guest.wasm,alpha,beta")) { text }
            require(text.contains("clock.realtime=true")) { text }
            require(text.contains("clock.monotonic=true")) { text }
            require(text.contains("coroutine.result=42")) { text }
            require(text.contains("env.KRWA_SAMPLE=preview1")) { text }
            val randomChecksum =
                Regex("""random\.checksum=(\d+)""").find(text)?.groupValues?.get(1)?.toInt()
            require(randomChecksum != null && randomChecksum > 0) { text }
            require(text.contains("fs.roundtrip=preview1-file-ok")) { text }
            requireValue(
                "preview1-file-ok",
                Files.readString(tempDir.resolve("krwa-wasi-probe.txt")),
                "Kotlin/WASI guest preopened file",
            )
            require(stderr.toString(UTF_8).contains("stderr.probe=ok")) {
                "Unexpected stderr: ${stderr.toString(UTF_8)}"
            }
        } finally {
            Files.deleteIfExists(tempDir.resolve("krwa-wasi-probe.txt"))
            Files.deleteIfExists(tempDir)
        }
        completed +=
            "Kotlin 2.4 wasmWasi guest with coroutines, random, env, stdio, and filesystem on WASI Preview 1"
    }

    private fun kotlinWasiPreview1Component() {
        require(Files.isRegularFile(kotlinWasiGuest)) {
            "Kotlin/WASI guest was not built: $kotlinWasiGuest"
        }

        val tempDir = Files.createTempDirectory("krwa-sample-wasi1-component")
        val witPath = tempDir.resolve("plugin.wit")
        val componentPath = tempDir.resolve("kotlin-wasi.component.wasm")
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        Files.writeString(
            witPath,
            """
            package sample:kotlin-wasi;

            interface api {
              run: func() -> u32;
            }

            world plugin {
              export api;
            }
            """
                .trimIndent(),
        )

        try {
            Files.write(
                componentPath,
                WasmComponentTools.componentFromCore(
                    witPath.toOkioPath(),
                    "plugin",
                    kotlinWasiGuest.toOkioPath(),
                ),
            )

            try {
                WasmPlugin.builderFromComponent(componentPath.toOkioPath())
                    .withWasiPreview3(WasiPreview3.builder().build())
                    .build()
                error("Expected Kotlin/WASI component to require withWasiPreview1")
            } catch (expected: ComponentModelException) {
                require(expected.message?.contains("withWasiPreview1") == true) { expected.message.orEmpty() }
            }

            val wasi =
                WasiPreview1.builder()
                    .withOptions(
                        WasiOptions.builder()
                            .withRandom(Random(11))
                            .withStdout(stdout)
                            .withStderr(stderr)
                            .withStdin(ByteArrayInputStream(ByteArray(0)))
                            .withArguments(listOf("component.wasm", "--component"))
                            .withEnvironment("KRWA_SAMPLE", "component")
                            .withDirectory("/", tempDir.toOkioPath())
                            .build()
                    )
                    .build()
            val plugin =
                WasmPlugin.builderFromComponent(componentPath.toOkioPath())
                    .withWasiPreview1(wasi)
                    .build()

            requireValue(42L, plugin.call("api.run"), "Kotlin/WASI component Preview1 probe")
            require(stdout.toString(UTF_8).contains("component.stdout=ok")) {
                "Unexpected component stdout: ${stdout.toString(UTF_8)}"
            }
            require(stderr.toString(UTF_8).contains("component.stderr=ok")) {
                "Unexpected component stderr: ${stderr.toString(UTF_8)}"
            }
            requireValue(
                "preview1-file-ok",
                Files.readString(tempDir.resolve("krwa-wasi-probe.txt")),
                "Kotlin/WASI component preopened file",
            )
        } finally {
            Files.deleteIfExists(tempDir.resolve("krwa-wasi-probe.txt"))
            Files.deleteIfExists(componentPath)
            Files.deleteIfExists(witPath)
            Files.deleteIfExists(tempDir)
        }

        completed +=
            "Kotlin 2.4 wasmWasi component packaging with bundled WASI Preview 1 adapter and host wiring"
    }

    private fun witBindings() {
        val wit = WitPackage.parse(pluginWit())
        val generated = KotlinWitBindings.generate(wit, "uk.shusek.krwa.sample.generated")
        require(generated.contains("interface Api")) { generated }
        require(generated.contains("fun len(input: String): UInt")) { generated }
        completed += "WIT parsing and Kotlin contract generation"
    }

    private fun wasiPreview3Contracts() {
        requireValue("0.3.0-rc-2026-03-15", WasiPreview.PREVIEW3.version(), "WASIp3 version")
        require(WasiPreview.PREVIEW3.isReleaseCandidate()) { "Expected WASIp3 to be RC metadata" }
        require(WasiPreview.PREVIEW3.isComponentModel()) { "Expected WASIp3 to use Component Model" }

        val wit = WitPackage.parse(wasip3Wit())
        val generated = KotlinWitBindings.generate(wit, "uk.shusek.krwa.sample.generated.wasip3")

        require(generated.contains("public suspend fun waitFor(duration: ULong)")) { generated }
        require(generated.contains("public fun getRandomBytes(len: ULong): UByteArray")) { generated }
        require(
            generated.contains(
                "public suspend fun handle(request: WitStream<UByte>): " +
                    "WitFuture<WitResult<Unit, String>>"
            )
        ) {
            generated
        }
        completed += "WASIp3 RC metadata and Kotlin async/future/stream handle contracts"
    }

    private fun wasiPreview3Runtime() {
        wasiPreview3CliClocksRandom()
        wasiPreview3HttpClient()
        wasiPreview3Filesystem()
        wasiPreview3Sockets()
        wasiPreview3CanonicalIntrinsics()
        wasiPreview3KotlinFacade()
        completed +=
            "WASIp3 RC runtime for CLI, clocks, random, HTTP, filesystem, sockets, canonical async intrinsics, and Kotlin Deferred/stream/clock/random/file facades"
    }

    private fun wasiPreview3CliClocksRandom() {
        val version = WasiPreview3.DEFAULT_VERSION
        var monotonicReads = 0
        val runtime =
            KotlinWasiPreview3.builder()
                .withArguments("guest.wasm", "alpha", "beta")
                .withEnvironment("MODE", "p3")
                .withInitialCwd("/work")
                .withFixedWallClock(
                    WasiInstant.fromEpochSeconds(1_700_000_000L, 42),
                    resolution = 123.nanoseconds,
                )
                .withMonotonicClock {
                    monotonicReads += 1
                    if (monotonicReads == 1) {
                        1_000_000L.nanoseconds
                    } else {
                        1_000_123L.nanoseconds
                    }
                }
                .withMonotonicResolution(456.nanoseconds)
                .withSecureRandom(kotlin.random.Random(7L))
                .withInsecureSeed(11uL, 12uL)
                .build()
        val wasi = runtime.wasi
        val witPackage =
            WitPackage.parse(
                """
                package sample:wasi3-sync;

                world plugin {
                  import wasi:cli/environment@${version};
                  import wasi:clocks/system-clock@${version};
                  import wasi:clocks/monotonic-clock@${version};
                  import wasi:random/random@${version};
                  import wasi:random/insecure-seed@${version};
                  export api;
                }

                interface api {
                  run: func() -> u64;
                }

                package wasi:cli@${version} {
                  interface environment {
                    get-environment: func() -> list<tuple<string, string>>;
                    get-arguments: func() -> list<string>;
                    get-initial-cwd: func() -> option<string>;
                  }
                }

                package wasi:clocks@${version} {
                  interface types {
                    type duration = u64;
                  }
                  interface system-clock {
                    use types.{duration};
                    record instant {
                      seconds: s64,
                      nanoseconds: u32,
                    }
                    now: func() -> instant;
                    get-resolution: func() -> duration;
                  }
                  interface monotonic-clock {
                    use types.{duration};
                    type mark = u64;
                    now: func() -> mark;
                    get-resolution: func() -> duration;
                    wait-for: async func(how-long: duration);
                  }
                }

                package wasi:random@${version} {
                  interface random {
                    get-random-bytes: func(max-len: u64) -> list<u8>;
                    get-random-u64: func() -> u64;
                  }
                  interface insecure-seed {
                    get-insecure-seed: func() -> tuple<u64, u64>;
                  }
                }
                """
                    .trimIndent()
            )
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    compileWat(
                        """
                        (module
                          (import "wasi:cli/environment@${version}" "get-arguments" (func _D_args (param i32)))
                          (import "wasi:cli/environment@${version}" "get-environment" (func _D_env (param i32)))
                          (import "wasi:cli/environment@${version}" "get-initial-cwd" (func _D_cwd (param i32)))
                          (import "wasi:clocks/system-clock@${version}" "now" (func _D_system_now (param i32)))
                          (import "wasi:clocks/system-clock@${version}" "get-resolution" (func _D_system_resolution (result i64)))
                          (import "wasi:clocks/monotonic-clock@${version}" "now" (func _D_monotonic_now (result i64)))
                          (import "wasi:clocks/monotonic-clock@${version}" "get-resolution" (func _D_monotonic_resolution (result i64)))
                          (import "wasi:clocks/monotonic-clock@${version}" "[async-lower]wait-for" (func _D_monotonic_wait_for (param i64) (result i32)))
                          (import "wasi:random/random@${version}" "get-random-bytes" (func _D_random_bytes (param i64) (param i32)))
                          (import "wasi:random/insecure-seed@${version}" "get-insecure-seed" (func _D_seed (param i32)))
                          (memory (export "memory") 1)
                          (global _D_heap (mut i32) (i32.const 256))
                          (func (export "canonical_abi_realloc")
                            (param _D_old i32) (param _D_old_size i32)
                            (param _D_align i32) (param _D_new_size i32)
                            (result i32)
                            (local _D_ptr i32)
                            (local.set _D_ptr
                              (i32.and
                                (i32.add (global.get _D_heap) (i32.sub (local.get _D_align) (i32.const 1)))
                                (i32.xor
                                  (i32.sub (local.get _D_align) (i32.const 1))
                                  (i32.const -1))))
                            (global.set _D_heap (i32.add (local.get _D_ptr) (local.get _D_new_size)))
                            (local.get _D_ptr))
                          (func _D_run (result i64)
                            (call _D_args (i32.const 64))
                            (if (i32.ne (i32.load (i32.const 68)) (i32.const 3)) (then unreachable))
                            (call _D_env (i32.const 80))
                            (if (i32.ne (i32.load (i32.const 84)) (i32.const 1)) (then unreachable))
                            (call _D_cwd (i32.const 96))
                            (if (i32.ne (i32.load8_u (i32.const 96)) (i32.const 1)) (then unreachable))
                            (if (i32.ne (i32.load (i32.const 104)) (i32.const 5)) (then unreachable))
                            (call _D_system_now (i32.const 112))
                            (if (i64.ne (i64.load (i32.const 112)) (i64.const 1700000000)) (then unreachable))
                            (if (i64.ne (call _D_system_resolution) (i64.const 123)) (then unreachable))
                            (if (i64.ne (call _D_monotonic_now) (i64.const 123)) (then unreachable))
                            (if (i64.ne (call _D_monotonic_resolution) (i64.const 456)) (then unreachable))
                            (if (i32.ne (call _D_monotonic_wait_for (i64.const 0)) (i32.const 0)) (then unreachable))
                            (call _D_random_bytes (i64.const 4) (i32.const 128))
                            (if (i32.ne (i32.load (i32.const 132)) (i32.const 4)) (then unreachable))
                            (call _D_seed (i32.const 144))
                            (if (i64.ne (i64.load (i32.const 144)) (i64.const 11)) (then unreachable))
                            (if (i64.ne (i64.load (i32.const 152)) (i64.const 12)) (then unreachable))
                            (i64.const 7))
                          (export "api.run" (func _D_run))
                        )
                        """
                    )
                )
                .withWasiPreview3(wasi)
                .build()

        requireValue(7L, plugin.call("api.run"), "WASIp3 CLI, clocks, random")
    }

    private fun wasiPreview3HttpClient() {
        val version = WasiPreview3.DEFAULT_VERSION
        val requestLine = AtomicReference<String?>()
        val serverFailure = AtomicReference<Throwable?>()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val serverThread =
                Thread(
                    {
                        try {
                            server.accept().use { socket ->
                                val reader =
                                    BufferedReader(
                                        InputStreamReader(
                                            socket.getInputStream(),
                                            StandardCharsets.ISO_8859_1,
                                        )
                                    )
                                requestLine.set(reader.readLine())
                                while (true) {
                                    val line = reader.readLine()
                                    if (line == null || line.isEmpty()) {
                                        break
                                    }
                                }
                                socket
                                    .getOutputStream()
                                    .write(
                                        ("HTTP/1.1 203 Accepted\r\n" +
                                                "Content-Length: 5\r\n" +
                                                "X-P3: ok\r\n" +
                                                "Connection: close\r\n" +
                                                "\r\nreply")
                                            .toByteArray(StandardCharsets.ISO_8859_1)
                                    )
                            }
                        } catch (e: Throwable) {
                            serverFailure.set(e)
                        }
                    },
                    "krwa-sample-wasi3-http",
                )
            serverThread.isDaemon = true
            serverThread.start()

            val authority = "127.0.0.1:${server.localPort}"
            val pathWithQuery = "/probe?x=p3"
            val witPackage =
                WitPackage.parse(
                    """
                    package sample:wasi3-http-client;

                    world plugin {
                      import wasi:http/types@${version};
                      import wasi:http/client@${version};
                      export api;
                    }

                    interface api {
                      run: func() -> u32;
                    }

                    package wasi:http@${version} {
                      interface client {
                        use types.{request, response, error-code};
                        send: async func(request: request) -> result<response, error-code>;
                      }

                      interface types {
                        variant error-code {
                          HTTP-request-denied,
                          HTTP-request-URI-invalid,
                          HTTP-request-method-invalid,
                          connection-refused,
                          connection-timeout,
                          internal-error(option<string>),
                        }

                        variant header-error {
                          invalid-syntax,
                          forbidden,
                          immutable,
                        }

                        resource fields {
                          constructor();
                        }

                        type headers = fields;
                        type trailers = fields;

                        resource request-options;

                        resource request {
                          new: static func(
                            headers: headers,
                            contents: option<stream<u8>>,
                            trailers: future<result<option<trailers>, error-code>>,
                            options: option<request-options>,
                          ) -> tuple<request, future<result<_, error-code>>>;
                          set-authority: func(authority: option<string>) -> result;
                          set-path-with-query: func(path-with-query: option<string>) -> result;
                        }

                        resource response {
                          get-status-code: func() -> u16;
                        }
                      }
                    }
                    """
                        .trimIndent()
                )
            val plugin =
                WasmPlugin.builder(witPackage)
                    .withModule(
                        compileWat(
                            """
                            (module
                              (import "wasi:http/types@${version}" "[constructor]fields" (func _D_fields_new (result i32)))
                              (import "wasi:http/types@${version}" "[static]request.new" (func _D_request_new (param i32 i32 i32 i32 i32 i32 i32)))
                              (import "wasi:http/types@${version}" "[method]request.set-authority" (func _D_set_authority (param i32 i32 i32 i32) (result i32)))
                              (import "wasi:http/types@${version}" "[method]request.set-path-with-query" (func _D_set_path (param i32 i32 i32 i32) (result i32)))
                              (import "wasi:http/client@${version}" "send" (func _D_send (param i32) (result i32)))
                              (import "wasi:http/client@${version}" "[async-lower][future-read-0]send" (func _D_send_future_read (param i32 i32) (result i32)))
                              (import "wasi:http/types@${version}" "[method]response.get-status-code" (func _D_status (param i32) (result i32)))
                              (memory (export "memory") 1)
                              (global _D_heap (mut i32) (i32.const 256))
                              (data (i32.const 16) "${authority}")
                              (data (i32.const 64) "${pathWithQuery}")
                              (func (export "canonical_abi_realloc")
                                (param _D_old i32) (param _D_old_size i32)
                                (param _D_align i32) (param _D_new_size i32)
                                (result i32)
                                (local _D_ptr i32)
                                (local.set _D_ptr
                                  (i32.and
                                    (i32.add (global.get _D_heap) (i32.sub (local.get _D_align) (i32.const 1)))
                                    (i32.xor
                                      (i32.sub (local.get _D_align) (i32.const 1))
                                      (i32.const -1))))
                                (global.set _D_heap (i32.add (local.get _D_ptr) (local.get _D_new_size)))
                                (local.get _D_ptr))
                              (func _D_run (result i32)
                                (local _D_request i32)
                                (local _D_response i32)
                                (local _D_future i32)
                                (local _D_send_status i32)
                                (call _D_request_new
                                  (call _D_fields_new)
                                  (i32.const 0)
                                  (i32.const 0)
                                  (i32.const 0)
                                  (i32.const 0)
                                  (i32.const 0)
                                  (i32.const 96))
                                (local.set _D_request (i32.load (i32.const 96)))
                                (if
                                  (i32.ne
                                    (call _D_set_authority
                                      (local.get _D_request)
                                      (i32.const 1)
                                      (i32.const 16)
                                      (i32.const ${authority.length}))
                                    (i32.const 0))
                                  (then unreachable))
                                (if
                                  (i32.ne
                                    (call _D_set_path
                                      (local.get _D_request)
                                      (i32.const 1)
                                      (i32.const 64)
                                      (i32.const ${pathWithQuery.length}))
                                    (i32.const 0))
                                  (then unreachable))
                                (local.set _D_future (call _D_send (local.get _D_request)))
                                (local.set _D_send_status (call _D_send_future_read (local.get _D_future) (i32.const 128)))
                                (if (i32.ne (local.get _D_send_status) (i32.const 0)) (then unreachable))
                                (if (i32.ne (i32.load8_u (i32.const 128)) (i32.const 0)) (then unreachable))
                                (local.set _D_response (i32.load (i32.const 132)))
                                (call _D_status (local.get _D_response)))
                              (export "api.run" (func _D_run))
                            )
                            """
                        )
                    )
                    .withWasiPreview3(
                        KotlinWasiPreview3.builder()
                            .withNetworking()
                            .build()
                            .wasi
                    )
                    .build()

            requireValue(203L, plugin.call("api.run"), "WASIp3 HTTP client")
            serverThread.join(2_000L)
        }

        serverFailure.get()?.let { throw IllegalStateException("WASIp3 HTTP sample server failed", it) }
        requireValue("GET /probe?x=p3 HTTP/1.1", requestLine.get(), "WASIp3 HTTP request line")
    }

    private fun wasiPreview3Filesystem() {
        val version = WasiPreview3.DEFAULT_VERSION
        val tempDir = Files.createTempDirectory("krwa-sample-wasi3-fs")
        val source = tempDir.resolve("hello.txt")
        try {
            Files.writeString(source, "hello", UTF_8)
            val witPackage =
                WitPackage.parse(
                    """
                    package sample:wasi3-filesystem;

                    world plugin {
                      import wasi:filesystem/types@${version};
                      import wasi:filesystem/preopens@${version};
                      export api;
                    }

                    interface api {
                      run: func() -> u32;
                    }

                    package wasi:filesystem@${version} {
                      interface types {
                        type filesize = u64;

                        flags descriptor-flags {
                          read,
                          write,
                          file-integrity-sync,
                          data-integrity-sync,
                          requested-write-sync,
                          mutate-directory,
                        }

                        flags path-flags {
                          symlink-follow,
                        }

                        flags open-flags {
                          create,
                          directory,
                          exclusive,
                          truncate,
                        }

                        variant error-code {
                          access,
                          bad-descriptor,
                          exist,
                          io,
                          is-directory,
                          loop,
                          no-entry,
                          not-directory,
                          not-empty,
                          unsupported,
                          not-permitted,
                          read-only,
                          other(option<string>),
                        }

                        resource descriptor {
                          open-at: async func(
                            path-flags: path-flags,
                            path: string,
                            open-flags: open-flags,
                            %flags: descriptor-flags,
                          ) -> result<descriptor, error-code>;
                          read-via-stream: func(offset: filesize) -> tuple<stream<u8>, future<result<_, error-code>>>;
                        }
                      }

                      interface preopens {
                        use types.{descriptor};
                        get-directories: func() -> list<tuple<descriptor, string>>;
                      }
                    }
                    """
                        .trimIndent()
                )
            val plugin =
                WasmPlugin.builder(witPackage)
                    .withModule(
                        compileWat(
                            """
                            (module
                              (import "wasi:filesystem/preopens@${version}" "get-directories" (func _D_get_directories (param i32)))
                              (import "wasi:filesystem/types@${version}" "[method]descriptor.open-at" (func _D_open_at (param i32 i32 i32 i32 i32 i32) (result i32)))
                              (import "wasi:filesystem/types@${version}" "[async-lower][future-read-0][method]descriptor.open-at" (func _D_open_at_future_read (param i32 i32) (result i32)))
                              (import "wasi:filesystem/types@${version}" "[method]descriptor.read-via-stream" (func _D_read_stream (param i32) (param i64) (param i32)))
                              (import "wasi:filesystem/types@${version}" "[async-lower][stream-read-0][method]descriptor.read-via-stream" (func _D_stream_read (param i32 i32 i32) (result i32)))
                              (import "wasi:filesystem/types@${version}" "[async-lower][future-read-1][method]descriptor.read-via-stream" (func _D_future_read (param i32 i32) (result i32)))
                              (memory (export "memory") 1)
                              (global _D_heap (mut i32) (i32.const 256))
                              (data (i32.const 16) "hello.txt")
                              (func (export "canonical_abi_realloc")
                                (param _D_old i32) (param _D_old_size i32)
                                (param _D_align i32) (param _D_new_size i32)
                                (result i32)
                                (local _D_ptr i32)
                                (local.set _D_ptr
                                  (i32.and
                                    (i32.add (global.get _D_heap) (i32.sub (local.get _D_align) (i32.const 1)))
                                    (i32.xor
                                      (i32.sub (local.get _D_align) (i32.const 1))
                                      (i32.const -1))))
                                (global.set _D_heap (i32.add (local.get _D_ptr) (local.get _D_new_size)))
                                (local.get _D_ptr))
                              (func _D_run (result i32)
                                (local _D_base i32)
                                (local _D_file i32)
                                (local _D_stream i32)
                                (local _D_future i32)
                                (local _D_status i32)
                                (call _D_get_directories (i32.const 48))
                                (local.set _D_base (i32.load (i32.load (i32.const 48))))
                                (local.set _D_future
                                  (call _D_open_at
                                    (local.get _D_base)
                                    (i32.const 0)
                                    (i32.const 16)
                                    (i32.const 9)
                                    (i32.const 0)
                                    (i32.const 1)))
                                (local.set _D_status (call _D_open_at_future_read (local.get _D_future) (i32.const 64)))
                                (if (i32.ne (local.get _D_status) (i32.const 0)) (then unreachable))
                                (if (i32.ne (i32.load8_u (i32.const 64)) (i32.const 0)) (then unreachable))
                                (local.set _D_file (i32.load (i32.const 68)))
                                (call _D_read_stream (local.get _D_file) (i64.const 0) (i32.const 96))
                                (local.set _D_stream (i32.load (i32.const 96)))
                                (local.set _D_future (i32.load (i32.const 100)))
                                (local.set _D_status (call _D_future_read (local.get _D_future) (i32.const 160)))
                                (if (i32.ne (local.get _D_status) (i32.const 0)) (then unreachable))
                                (if (i32.ne (i32.load8_u (i32.const 160)) (i32.const 0)) (then unreachable))
                                (local.set _D_status
                                  (call _D_stream_read
                                    (local.get _D_stream)
                                    (i32.const 128)
                                    (i32.const 16)))
                                (if (i32.ne (local.get _D_status) (i32.const 81)) (then unreachable))
                                (i32.add
                                  (i32.add
                                    (i32.add
                                      (i32.add
                                        (i32.load8_u (i32.const 128))
                                        (i32.load8_u (i32.const 129)))
                                      (i32.load8_u (i32.const 130)))
                                    (i32.load8_u (i32.const 131)))
                                  (i32.load8_u (i32.const 132))))
                              (export "api.run" (func _D_run))
                            )
                            """
                        )
                    )
                    .withWasiPreview3(
                        KotlinWasiPreview3.builder()
                            .withPreopenedDirectory("/", tempDir.toString())
                            .build()
                            .wasi
                    )
                    .build()

            requireValue(532L, plugin.call("api.run"), "WASIp3 filesystem preopen stream")
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(tempDir)
        }
    }

    private fun wasiPreview3Sockets() {
        val version = WasiPreview3.DEFAULT_VERSION
        val serverFailure = AtomicReference<Throwable?>()
        val tcpAccepted = AtomicReference(false)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { tcpServer ->
            DatagramSocket(0, InetAddress.getLoopbackAddress()).use { udpServer ->
                udpServer.soTimeout = 2_000
                val tcpThread =
                    Thread(
                        {
                            try {
                                tcpServer.accept().use {
                                    tcpAccepted.set(true)
                                }
                            } catch (e: Throwable) {
                                serverFailure.set(e)
                            }
                        },
                        "krwa-sample-wasi3-tcp",
                    )
                tcpThread.isDaemon = true
                tcpThread.start()

                val witPackage =
                    WitPackage.parse(
                        """
                        package sample:wasi3-sockets;

                        world plugin {
                          import wasi:sockets/types@${version};
                          export api;
                        }

                        interface api {
                          run: func() -> u32;
                        }

                        package wasi:sockets@${version} {
                          interface types {
                            variant error-code {
                              access-denied,
                              not-supported,
                              invalid-argument,
                              out-of-memory,
                              timeout,
                              invalid-state,
                              address-not-bindable,
                              address-in-use,
                              remote-unreachable,
                              connection-refused,
                              connection-broken,
                              connection-reset,
                              connection-aborted,
                              datagram-too-large,
                              other(option<string>),
                            }

                            enum ip-address-family {
                              ipv4,
                              ipv6,
                            }

                            type ipv4-address = tuple<u8, u8, u8, u8>;
                            type ipv6-address = tuple<u16, u16, u16, u16, u16, u16, u16, u16>;

                            record ipv4-socket-address {
                              port: u16,
                              address: ipv4-address,
                            }

                            record ipv6-socket-address {
                              port: u16,
                              flow-info: u32,
                              address: ipv6-address,
                              scope-id: u32,
                            }

                            variant ip-socket-address {
                              ipv4(ipv4-socket-address),
                              ipv6(ipv6-socket-address),
                            }

                            resource tcp-socket {
                              create: static func(address-family: ip-address-family) -> result<tcp-socket, error-code>;
                              connect: async func(remote-address: ip-socket-address) -> result<_, error-code>;
                              get-local-address: func() -> result<ip-socket-address, error-code>;
                              get-remote-address: func() -> result<ip-socket-address, error-code>;
                            }

                            resource udp-socket {
                              create: static func(address-family: ip-address-family) -> result<udp-socket, error-code>;
                              send: async func(data: list<u8>, remote-address: option<ip-socket-address>) -> result<_, error-code>;
                            }
                          }
                        }
                        """
                            .trimIndent()
                    )
                val plugin =
                    WasmPlugin.builder(witPackage)
                        .withModule(
                            compileWat(
                                """
                                (module
                                  (import "wasi:sockets/types@${version}" "[static]tcp-socket.create" (func _D_tcp_create (param i32) (param i32)))
                                  (import "wasi:sockets/types@${version}" "[async-lower][method]tcp-socket.connect" (func _D_tcp_connect (param i32 i32) (result i32)))
                                  (import "wasi:sockets/types@${version}" "[method]tcp-socket.get-local-address" (func _D_tcp_local (param i32) (param i32)))
                                  (import "wasi:sockets/types@${version}" "[method]tcp-socket.get-remote-address" (func _D_tcp_remote (param i32) (param i32)))
                                  (import "wasi:sockets/types@${version}" "[static]udp-socket.create" (func _D_udp_create (param i32) (param i32)))
                                  (import "wasi:sockets/types@${version}" "[async-lower][method]udp-socket.send" (func _D_udp_send (param i32 i32) (result i32)))
                                  (memory (export "memory") 1)
                                  (data (i32.const 16) "ping")
                                  (func _D_run (result i32)
                                    (local _D_tcp i32)
                                    (local _D_udp i32)
                                    (local _D_status i32)
                                    (call _D_tcp_create (i32.const 0) (i32.const 64))
                                    (if (i32.ne (i32.load8_u (i32.const 64)) (i32.const 0)) (then unreachable))
                                    (local.set _D_tcp (i32.load (i32.const 68)))
                                    (i32.store (i32.const 32) (local.get _D_tcp))
                                    (i32.store8 (i32.const 36) (i32.const 0))
                                    (i32.store16 (i32.const 40) (i32.const ${tcpServer.localPort}))
                                    (i32.store8 (i32.const 42) (i32.const 127))
                                    (i32.store8 (i32.const 43) (i32.const 0))
                                    (i32.store8 (i32.const 44) (i32.const 0))
                                    (i32.store8 (i32.const 45) (i32.const 1))
                                    (local.set _D_status (call _D_tcp_connect (i32.const 32) (i32.const 80)))
                                    (if (i32.ne (local.get _D_status) (i32.const 0)) (then unreachable))
                                    (if (i32.ne (i32.load8_u (i32.const 80)) (i32.const 0)) (then unreachable))
                                    (call _D_tcp_local (local.get _D_tcp) (i32.const 96))
                                    (if (i32.ne (i32.load8_u (i32.const 96)) (i32.const 0)) (then unreachable))
                                    (call _D_tcp_remote (local.get _D_tcp) (i32.const 144))
                                    (if (i32.ne (i32.load8_u (i32.const 144)) (i32.const 0)) (then unreachable))
                                    (call _D_udp_create (i32.const 0) (i32.const 192))
                                    (if (i32.ne (i32.load8_u (i32.const 192)) (i32.const 0)) (then unreachable))
                                    (local.set _D_udp (i32.load (i32.const 196)))
                                    (i32.store (i32.const 224) (local.get _D_udp))
                                    (i32.store (i32.const 228) (i32.const 16))
                                    (i32.store (i32.const 232) (i32.const 4))
                                    (i32.store8 (i32.const 236) (i32.const 1))
                                    (i32.store8 (i32.const 240) (i32.const 0))
                                    (i32.store16 (i32.const 244) (i32.const ${udpServer.localPort}))
                                    (i32.store8 (i32.const 246) (i32.const 127))
                                    (i32.store8 (i32.const 247) (i32.const 0))
                                    (i32.store8 (i32.const 248) (i32.const 0))
                                    (i32.store8 (i32.const 249) (i32.const 1))
                                    (local.set _D_status (call _D_udp_send (i32.const 224) (i32.const 288)))
                                    (if (i32.ne (local.get _D_status) (i32.const 0)) (then unreachable))
                                    (if (i32.ne (i32.load8_u (i32.const 288)) (i32.const 0)) (then unreachable))
                                    (i32.const 42))
                                  (export "api.run" (func _D_run))
                                )
                                """
                            )
                        )
                        .withWasiPreview3(
                            KotlinWasiPreview3.builder()
                                .withNetworking()
                                .build()
                                .wasi
                        )
                        .build()

                requireValue(42L, plugin.call("api.run"), "WASIp3 TCP/UDP sockets")
                val packet = DatagramPacket(ByteArray(16), 16)
                udpServer.receive(packet)
                tcpThread.join(2_000L)

                requireValue(
                    "ping",
                    String(packet.data, packet.offset, packet.length, StandardCharsets.ISO_8859_1),
                    "WASIp3 UDP payload",
                )
                require(tcpAccepted.get()) { "Expected WASIp3 TCP connection to be accepted" }
            }
        }

        serverFailure.get()?.let { throw IllegalStateException("WASIp3 TCP sample server failed", it) }
    }

    private fun wasiPreview3CanonicalIntrinsics() {
        val version = WasiPreview3.DEFAULT_VERSION
        val streamWit =
            WitPackage.parse(
                """
                package sample:wasi3-stream-intrinsics;

                world plugin {
                  export api;
                }

                interface api {
                  run: func() -> u32;
                }

                package wasi:cli@${version} {
                  interface stdin {
                    read-via-stream: func() -> tuple<stream<u8>, future<result>>;
                  }
                }
                """
                    .trimIndent()
            )
        val streamPlugin =
            WasmPlugin.builder(streamWit)
                .withModule(
                    compileWat(
                        """
                        (module
                          (import "wasi:cli/stdin@${version}" "[stream-new-0]read-via-stream" (func _D_stream_new (result i64)))
                          (import "wasi:cli/stdin@${version}" "[async-lower][stream-write-0]read-via-stream" (func _D_stream_write (param i32 i32 i32) (result i32)))
                          (import "wasi:cli/stdin@${version}" "[stream-drop-writable-0]read-via-stream" (func _D_drop_writable (param i32)))
                          (import "wasi:cli/stdin@${version}" "[async-lower][stream-read-0]read-via-stream" (func _D_stream_read (param i32 i32 i32) (result i32)))
                          (import "wasi:cli/stdin@${version}" "[stream-drop-readable-0]read-via-stream" (func _D_drop_readable (param i32)))
                          (memory (export "memory") 1)
                          (data (i32.const 32) "abc")
                          (func _D_run (result i32)
                            (local _D_pair i64)
                            (local _D_reader i32)
                            (local _D_writer i32)
                            (local _D_write_status i32)
                            (local _D_read_status i32)
                            (local.set _D_pair (call _D_stream_new))
                            (local.set _D_reader (i32.wrap_i64 (local.get _D_pair)))
                            (local.set _D_writer (i32.wrap_i64 (i64.shr_u (local.get _D_pair) (i64.const 32))))
                            (local.set _D_write_status
                              (call _D_stream_write
                                (local.get _D_writer)
                                (i32.const 32)
                                (i32.const 3)))
                            (if (i32.ne (local.get _D_write_status) (i32.const 48)) (then unreachable))
                            (call _D_drop_writable (local.get _D_writer))
                            (local.set _D_read_status
                              (call _D_stream_read
                                (local.get _D_reader)
                                (i32.const 64)
                                (i32.const 8)))
                            (if (i32.ne (local.get _D_read_status) (i32.const 49)) (then unreachable))
                            (call _D_drop_readable (local.get _D_reader))
                            (i32.add
                              (i32.add
                                (i32.load8_u (i32.const 64))
                                (i32.load8_u (i32.const 65)))
                              (i32.load8_u (i32.const 66))))
                          (export "api.run" (func _D_run))
                        )
                        """
                    )
                )
                .withWasiPreview3(KotlinWasiPreview3.builder().build().wasi)
                .build()

        requireValue(294L, streamPlugin.call("api.run"), "WASIp3 canonical stream intrinsics")

        val futureWit =
            WitPackage.parse(
                """
                package sample:wasi3-future-intrinsics;

                world plugin {
                  import seed: func() -> future<u32>;
                  export api;
                }

                interface api {
                  run: func() -> u32;
                }
                """
                    .trimIndent()
            )
        val futureWasi = KotlinWasiPreview3.builder().build().wasi
        val futurePlugin =
            WasmPlugin.builder(futureWit)
                .withModule(
                    compileWat(
                        """
                        (module
                          (import "plugin" "[future-new-0]seed" (func _D_future_new (result i64)))
                          (import "plugin" "[async-lower][future-write-0]seed" (func _D_future_write (param i32 i32) (result i32)))
                          (import "plugin" "[async-lower][future-read-0]seed" (func _D_future_read (param i32 i32) (result i32)))
                          (import "plugin" "[future-drop-writable-0]seed" (func _D_drop_writable (param i32)))
                          (import "plugin" "[future-drop-readable-0]seed" (func _D_drop_readable (param i32)))
                          (memory (export "memory") 1)
                          (func _D_run (result i32)
                            (local _D_pair i64)
                            (local _D_reader i32)
                            (local _D_writer i32)
                            (local _D_status i32)
                            (local.set _D_pair (call _D_future_new))
                            (local.set _D_reader (i32.wrap_i64 (local.get _D_pair)))
                            (local.set _D_writer (i32.wrap_i64 (i64.shr_u (local.get _D_pair) (i64.const 32))))
                            (i32.store (i32.const 32) (i32.const 123456))
                            (local.set _D_status
                              (call _D_future_write
                                (local.get _D_writer)
                                (i32.const 32)))
                            (if (i32.ne (local.get _D_status) (i32.const 0)) (then unreachable))
                            (call _D_drop_writable (local.get _D_writer))
                            (local.set _D_status
                              (call _D_future_read
                                (local.get _D_reader)
                                (i32.const 64)))
                            (if (i32.ne (local.get _D_status) (i32.const 0)) (then unreachable))
                            (call _D_drop_readable (local.get _D_reader))
                            (i32.load (i32.const 64)))
                          (export "api.run" (func _D_run))
                        )
                        """
                    )
                )
                .withHostImport("plugin", "seed") { futureWasi.completedFuture(0L) }
                .withWasiPreview3(futureWasi)
                .build()

        requireValue(123456L, futurePlugin.call("api.run"), "WASIp3 canonical future intrinsics")
    }

    private fun wasiPreview3KotlinFacade() {
        val tempDir = Files.createTempDirectory("krwa-sample-wasi3-facade")
        runBlocking {
            try {
                val runtime =
                    KotlinWasiPreview3.builder()
                        .withPreopenedDirectory("/", tempDir.toString())
                        .build()
                val future = runtime.completed("first-party")
                val fs = runtime.fileSystem("/")

                requireValue("first-party", runtime.await(future), "WASIp3 Kotlin facade await")
                requireValue(
                    "first-party",
                    future.asDeferred(runtime.wasi, this).await(),
                    "WASIp3 Kotlin facade Deferred",
                )
                requireValue(
                    "bytes",
                    runtime.byteStream("bytes".toByteArray(UTF_8)).asByteArray(runtime.wasi).toString(UTF_8),
                    "WASIp3 Kotlin facade byte stream",
                )

                fs.writeText("facade/file.txt", "file")
                fs.appendText("facade/file.txt", "-facade")
                requireValue("file-facade", fs.readText("facade/file.txt"), "WASIp3 Kotlin facade filesystem")
                fs.writeWitByteStream(
                    "facade/copy.txt",
                    fs.readWitByteStream("facade/file.txt", runtime.wasi),
                    runtime.wasi,
                )
                requireValue("file-facade", fs.readText("facade/copy.txt"), "WASIp3 Kotlin facade filesystem stream")
            } finally {
                Files.deleteIfExists(tempDir.resolve("facade/copy.txt"))
                Files.deleteIfExists(tempDir.resolve("facade/file.txt"))
                Files.deleteIfExists(tempDir.resolve("facade"))
                Files.deleteIfExists(tempDir)
            }
        }
    }

    private fun componentModelPlugin() {
        val tempDir = Files.createTempDirectory("krwa-sample")
        val witPath = tempDir.resolve("plugin.wit")
        val corePath = tempDir.resolve("plugin.core.wasm")
        val embeddedPath = tempDir.resolve("plugin.embedded.wasm")
        val componentPath = tempDir.resolve("plugin.component.wasm")
        Files.writeString(witPath, pluginWit())
        Files.write(corePath, componentCoreModule())
        Files.write(
            embeddedPath,
            WasmComponentTools.embedWit(witPath.toOkioPath(), "plugin", corePath.toOkioPath()),
        )
        Files.write(componentPath, WasmComponentTools.componentNew(embeddedPath.toOkioPath()))

        val stdout = Buffer()
        val wasi2 =
            WasiPreview2.builder()
                .withStdout(stdout)
                .withStderr(Buffer())
                .withStdin(Buffer())
                .withArguments("plugin.component.wasm", "--sample")
                .withEnvironment("KRWA_SAMPLE", "wasip2")
                .withInitialCwd("/")
                .withPreopenedDirectory("/", tempDir.toString())
                .withTerminalStdout(true)
                .withNetworking(false)
                .withFixedWallClock(KotlinInstant.parse("2026-06-08T00:00:00Z"))
                .withSecureRandom(Random(7))
                .withInsecureRandom(Random(8))
                .withInsecureSeed(11L, 12L)
                .build()

        val plugin =
            WasmPlugin.builderFromComponent(componentPath.toOkioPath())
                .withWasiPreview2(wasi2)
                .build()

        requireValue(6L, plugin.call("api.len", "Kotlin"), "component plugin API")
        require(plugin.exports().containsKey("api.len")) { "Expected api.len export" }
        completed += "Component Model unbundling, WasmPlugin, canonical ABI, and WASIp2 host wiring"
    }

    private fun componentCoreModule(): ByteArray =
        Wat2Wasm.parse(
            """
            (module
              (memory (export "memory") 1)
              (global (mut i32) (i32.const 1024))
              (func (export "canonical_abi_realloc")
                (param i32) (param i32) (param i32) (param i32)
                (result i32)
                global.get 0
                global.get 0
                local.get 3
                i32.add
                global.set 0)
              (func (export "len") (param i32) (param i32) (result i32)
                local.get 1)
              (export "api.len" (func 1))
              (export "api#len" (func 1))
              (export "api/len" (func 1))
              (export "sample:runtime/api#len" (func 1)))
            """
                .trimIndent()
        )

    private fun pluginWit(): String =
        """
        package sample:runtime;

        interface api {
          len: func(input: string) -> u32;
        }

        world plugin {
          export api;
        }
        """
            .trimIndent()

    private fun wasip3Wit(): String =
        """
        package wasi:clocks@0.3.0-rc-2026-03-15;

        interface monotonic-clock {
          wait-for: async func(duration: u64);
        }

        world imports {
          import monotonic-clock;
        }

        package wasi:random@0.3.0-rc-2026-03-15;

        interface random {
          get-random-bytes: func(len: u64) -> list<u8>;
        }

        world imports {
          import random;
        }

        package wasi:http@0.3.0-rc-2026-03-15;

        interface incoming-handler {
          handle: async func(request: stream<u8>) -> future<result<_, string>>;
        }

        world service {
          include wasi:clocks/imports@0.3.0-rc-2026-03-15;
          include wasi:random/imports@0.3.0-rc-2026-03-15;
          export incoming-handler;
        }
        """
            .trimIndent()

    private fun compileWat(source: String): ByteArray =
        Wat2Wasm.parse(source.trimIndent().replace("_D_", "$"))

    private fun <T> requireValue(expected: T, actual: T, label: String) {
        require(expected == actual) { "$label: expected <$expected>, got <$actual>" }
    }

    private inline fun <reified T : Throwable> requireThrows(label: String, block: () -> Unit) {
        try {
            block()
        } catch (actual: Throwable) {
            require(actual is T) { "$label: expected ${T::class.java.name}, got $actual" }
            return
        }
        error("$label: expected ${T::class.java.name}")
    }
}

private fun Path.toOkioPath(): okio.Path = toString().toPath(normalize = true)
