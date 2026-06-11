@file:OptIn(kotlin.time.ExperimentalTime::class)

package uk.shusek.krwa.component

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Instant as KotlinInstant
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uk.shusek.krwa.tools.wasm.Wat2Wasm

class WasiPreview3Test {
    @Test
    fun handlesHttpServiceWorldReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val pathWithQuery = "/service?name=kotlin"
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi-http3;

                world plugin {
                  include wasi:http/service@$version;
                }

                package wasi:clocks@$version {
                  world imports {}
                }

                package wasi:random@$version {
                  world imports {}
                }

	                package wasi:cli@$version {
	                  interface types {
	                    enum error-code {
	                      io,
	                      illegal-byte-sequence,
	                      pipe,
	                    }
	                  }
	                  interface stdout {
	                    use types.{error-code};
	                    write-via-stream: func(data: stream<u8>) -> future<result<_, error-code>>;
	                  }
	                  interface stderr {
	                    use types.{error-code};
	                    write-via-stream: func(data: stream<u8>) -> future<result<_, error-code>>;
	                  }
	                  interface stdin {
	                    use types.{error-code};
	                    read-via-stream: func() -> tuple<stream<u8>, future<result<_, error-code>>>;
	                  }
	                }

                package wasi:http@$version {
                  world service {
                    include wasi:clocks/imports@$version;
                    include wasi:random/imports@$version;
                    import wasi:cli/stdout@$version;
                    import wasi:cli/stderr@$version;
                    import wasi:cli/stdin@$version;
                    import client;
                    export handler;
                  }

                  interface client {
                    use types.{request, response, error-code};
                    send: async func(request: request) -> result<response, error-code>;
                  }

                  interface handler {
                    use types.{request, response, error-code};
                    handle: async func(request: request) -> result<response, error-code>;
                  }

                  interface types {
                    variant error-code {
                      internal-error,
                    }
                    variant header-error {
                      invalid-syntax,
                      forbidden,
                      immutable,
                    }
                    resource fields {
                      constructor();
                      append: func(name: string, value: list<u8>) -> result<_, header-error>;
                    }
                    type headers = fields;
                    resource request {
                      get-path-with-query: func() -> option<string>;
                      get-headers: func() -> headers;
                    }
	                    resource response {
	                      new: static func(
	                        headers: headers,
	                        contents: option<stream<u8>>,
	                        trailers: future<result<option<headers>, error-code>>,
	                      ) -> tuple<response, future<result<_, error-code>>>;
	                      get-status-code: func() -> u16;
	                      set-status-code: func(status-code: u16) -> result;
	                      get-headers: func() -> headers;
                    }
                  }
                }
                """
                    .trimIndent()
            )
        val wasi = WasiPreview3.builder().build()
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[method]request.get-path-with-query\" (func" +
                            " \$path (param i32) (param i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[constructor]fields\" (func \$fields_new (result" +
                            " i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[method]fields.append\" (func \$append (param i32)" +
                            " (param i32) (param i32) (param i32) (param i32)" +
                            " (param i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[static]response.new\" (func \$response_new" +
                            " (param i32) (param i32) (param i32) (param i32)" +
                            " (param i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[method]response.set-status-code\" (func \$set_status" +
                            " (param i32) (param i32) (result i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (global \$heap (mut i32) (i32.const 256))\n" +
                            "  (data (i32.const 16) \"x-preview\")\n" +
                            "  (data (i32.const 32) \"ok\")\n" +
                            "  (func (export \"canonical_abi_realloc\")\n" +
                            "    (param \$old i32) (param \$old_size i32)\n" +
                            "    (param \$align i32) (param \$new_size i32)\n" +
                            "    (result i32)\n" +
                            "    (local \$ptr i32)\n" +
                            "    (local.set \$ptr\n" +
                            "      (i32.and\n" +
                            "        (i32.add (global.get \$heap)" +
                            " (i32.sub (local.get \$align) (i32.const 1)))\n" +
                            "        (i32.xor\n" +
                            "          (i32.sub (local.get \$align) (i32.const 1))\n" +
                            "          (i32.const -1))))\n" +
                            "    (global.set \$heap\n" +
                            "      (i32.add (local.get \$ptr) (local.get" +
                            " \$new_size)))\n" +
                            "    (local.get \$ptr))\n" +
                            "  (func \$handle (param \$request i32) (result i32)\n" +
                            "    (local \$fields i32)\n" +
                            "    (local \$response i32)\n" +
                            "    (call \$path (local.get \$request) (i32.const 64))\n" +
                            "    (if\n" +
                            "      (i32.or\n" +
                            "        (i32.ne (i32.load8_u (i32.const 64))" +
                            " (i32.const 1))\n" +
                            "        (i32.ne (i32.load (i32.const 72)) (i32.const " +
                            pathWithQuery.length +
                            ")))\n" +
                            "      (then unreachable))\n" +
                            "    (local.set \$fields (call \$fields_new))\n" +
                            "    (call \$append\n" +
                            "      (local.get \$fields)\n" +
                            "      (i32.const 16)\n" +
                            "      (i32.const 9)\n" +
                            "      (i32.const 32)\n" +
                            "      (i32.const 2)\n" +
                            "      (i32.const 96))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 96))" +
                            " (i32.const 0))\n" +
                            "      (then unreachable))\n" +
                            "    (call \$response_new\n" +
                            "      (local.get \$fields)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 112))\n" +
                            "    (local.set \$response (i32.load (i32.const 112)))\n" +
                            "    (if (i32.ne (call \$set_status (local.get \$response)" +
                            " (i32.const 201)) (i32.const 0))\n" +
                            "      (then unreachable))\n" +
                            "    (i32.store8 (i32.const 128) (i32.const 0))\n" +
                            "    (i32.store (i32.const 132) (local.get \$response))\n" +
                            "    (i32.const 128))\n" +
                            "  (export \"wasi:http/handler@$version.handle\"" +
                            " (func \$handle))\n" +
                            ")\n"
                    )
                )
                .withWasiPreview3(wasi)
                .build()

        val response =
            wasi.handleHttpRequest(
                plugin,
                "GET",
                pathWithQuery,
                "http",
                "localhost",
                mapOf<String, List<ByteArray>>(),
                ByteArray(0),
            )

        assertEquals(201, response.statusCode())
        assertTrue(response.bodyFinished())
        assertArrayEquals(ByteArray(0), response.body())
        assertArrayEquals(
            "ok".toByteArray(StandardCharsets.ISO_8859_1),
            response.headers()["x-preview"]!![0],
        )
    }

    @Test
    fun sendsHttpClientRequestReleaseCandidateWithoutJson() {
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
                    "wasi3-http-client-test",
                )
            serverThread.setDaemon(true)
            serverThread.start()

            val authority = "127.0.0.1:" + server.localPort
            val pathWithQuery = "/probe?x=p3"
            val witPackage =
                WitPackage.parse(
                    """
                    package example:wasi3-http-client;

                    world plugin {
                      import wasi:http/types@$version;
                      import wasi:http/client@$version;
                      export api;
                    }

                    interface api {
                      run: func() -> u32;
                    }

                    package wasi:http@$version {
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
                        Wat2Wasm.parse(
                            "(module\n" +
                                "  (import \"wasi:http/types@$version\"" +
                                " \"[constructor]fields\" (func \$fields_new" +
                                " (result i32)))\n" +
                                "  (import \"wasi:http/types@$version\"" +
                                " \"[static]request.new\" (func \$request_new" +
                                " (param i32) (param i32) (param i32) (param i32)" +
                                " (param i32) (param i32) (param i32)))\n" +
                                "  (import \"wasi:http/types@$version\"" +
                                " \"[method]request.set-authority\" (func" +
                                " \$set_authority (param i32) (param i32)" +
                                " (param i32) (param i32) (result i32)))\n" +
                                "  (import \"wasi:http/types@$version\"" +
                                " \"[method]request.set-path-with-query\" (func" +
                                " \$set_path (param i32) (param i32) (param i32)" +
                                " (param i32) (result i32)))\n" +
                                "  (import \"wasi:http/client@$version\" \"send\"" +
                                " (func \$send (param i32) (result i32)))\n" +
                                "  (import \"wasi:http/client@$version\"" +
                                " \"[async-lower][future-read-0]send\"" +
                                " (func \$send_future_read (param i32 i32) (result i32)))\n" +
                                "  (import \"wasi:http/types@$version\"" +
                                " \"[method]response.get-status-code\" (func" +
                                " \$status (param i32) (result i32)))\n" +
                                "  (memory (export \"memory\") 1)\n" +
                                "  (global \$heap (mut i32) (i32.const 256))\n" +
                                "  (data (i32.const 16) \"" +
                                authority +
                                "\")\n" +
                                "  (data (i32.const 64) \"" +
                                pathWithQuery +
                                "\")\n" +
                                "  (func (export \"canonical_abi_realloc\")\n" +
                                "    (param \$old i32) (param \$old_size i32)\n" +
                                "    (param \$align i32) (param \$new_size i32)\n" +
                                "    (result i32)\n" +
                                "    (local \$ptr i32)\n" +
                                "    (local.set \$ptr\n" +
                                "      (i32.and\n" +
                                "        (i32.add (global.get \$heap)" +
                                " (i32.sub (local.get \$align) (i32.const 1)))\n" +
                                "        (i32.xor\n" +
                                "          (i32.sub (local.get \$align) (i32.const 1))\n" +
                                "          (i32.const -1))))\n" +
                                "    (global.set \$heap\n" +
                                "      (i32.add (local.get \$ptr) (local.get" +
                                " \$new_size)))\n" +
                                "    (local.get \$ptr))\n" +
                                "  (func \$run (result i32)\n" +
                                "    (local \$request i32)\n" +
                                "    (local \$response i32)\n" +
                                "    (local \$future i32)\n" +
                                "    (local \$send_status i32)\n" +
                                "    (call \$request_new\n" +
                                "      (call \$fields_new)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 96))\n" +
                                "    (local.set \$request (i32.load (i32.const 96)))\n" +
                                "    (if\n" +
                                "      (i32.ne\n" +
                                "        (call \$set_authority\n" +
                                "          (local.get \$request)\n" +
                                "          (i32.const 1)\n" +
                                "          (i32.const 16)\n" +
                                "          (i32.const " +
                                authority.length +
                                "))\n" +
                                "        (i32.const 0))\n" +
                                "      (then (return (i32.const 98))))\n" +
                                "    (if\n" +
                                "      (i32.ne\n" +
                                "        (call \$set_path\n" +
                                "          (local.get \$request)\n" +
                                "          (i32.const 1)\n" +
                                "          (i32.const 64)\n" +
                                "          (i32.const " +
                                pathWithQuery.length +
                                "))\n" +
                                "        (i32.const 0))\n" +
                                "      (then (return (i32.const 97))))\n" +
                                "    (local.set \$future (call \$send (local.get \$request)))\n" +
                                "    (local.set \$send_status\n" +
                                "      (call \$send_future_read (local.get \$future)" +
                                " (i32.const 128)))\n" +
                                "    (if (i32.ne (local.get \$send_status) (i32.const 0))\n" +
                                "      (then (return (i32.const 96))))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 128))" +
                                " (i32.const 0))\n" +
                                "      (then (return (i32.const 99))))\n" +
                                "    (local.set \$response (i32.load (i32.const 132)))\n" +
                                "    (call \$status (local.get \$response)))\n" +
                                "  (export \"api.run\" (func \$run))\n" +
                                ")\n"
                        )
                    )
                    .withWasiPreview3(WasiPreview3.builder().withNetworking().build())
                    .build()

            assertEquals(203L, plugin.call("api.run"))
            serverThread.join(2_000L)
        }

        if (serverFailure.get() != null) {
            throw AssertionError("HTTP test server failed", serverFailure.get())
        }
        assertEquals("GET /probe?x=p3 HTTP/1.1", requestLine.get())
    }

    @Test
    fun preservesHttpTrailersReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi3-http-trailers;

                world plugin {
                  import wasi:http/types@$version;
                  export api;
                }

                interface api {
                  use wasi:http/types@$version.{request, response, trailers, error-code};
                  make-request: func(
                    trailers: future<result<option<trailers>, error-code>>,
                  ) -> request;
                  consume-request: func(
                    request: request,
                  ) -> future<result<option<trailers>, error-code>>;
                  make-response: func(
                    trailers: future<result<option<trailers>, error-code>>,
                  ) -> response;
                  consume-response: func(
                    response: response,
                  ) -> future<result<option<trailers>, error-code>>;
                }

                package wasi:http@$version {
                  interface types {
                    variant error-code {
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
                      consume-body: static func(
                        this: request,
                        res: future<result<_, error-code>>,
                      ) -> tuple<stream<u8>, future<result<option<trailers>, error-code>>>;
                    }

                    resource response {
                      new: static func(
                        headers: headers,
                        contents: option<stream<u8>>,
                        trailers: future<result<option<trailers>, error-code>>,
                      ) -> tuple<response, future<result<_, error-code>>>;
                      consume-body: static func(
                        this: response,
                        res: future<result<_, error-code>>,
                      ) -> tuple<stream<u8>, future<result<option<trailers>, error-code>>>;
                    }
                  }
                }
                """
                    .trimIndent()
            )
        val wasi = WasiPreview3.builder().build()
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[constructor]fields\" (func \$fields_new (result" +
                            " i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[static]request.new\" (func \$request_new" +
                            " (param i32) (param i32) (param i32) (param i32)" +
                            " (param i32) (param i32) (param i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[static]request.consume-body\" (func" +
                            " \$request_consume (param i32) (param i32)" +
                            " (param i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[static]response.new\" (func \$response_new" +
                            " (param i32) (param i32) (param i32) (param i32)" +
                            " (param i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[static]response.consume-body\" (func" +
                            " \$response_consume (param i32) (param i32)" +
                            " (param i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (global \$heap (mut i32) (i32.const 256))\n" +
                            "  (func (export \"canonical_abi_realloc\")\n" +
                            "    (param \$old i32) (param \$old_size i32)\n" +
                            "    (param \$align i32) (param \$new_size i32)\n" +
                            "    (result i32)\n" +
                            "    (local \$ptr i32)\n" +
                            "    (local.set \$ptr\n" +
                            "      (i32.and\n" +
                            "        (i32.add (global.get \$heap)" +
                            " (i32.sub (local.get \$align) (i32.const 1)))\n" +
                            "        (i32.xor\n" +
                            "          (i32.sub (local.get \$align) (i32.const 1))\n" +
                            "          (i32.const -1))))\n" +
                            "    (global.set \$heap\n" +
                            "      (i32.add (local.get \$ptr) (local.get" +
                            " \$new_size)))\n" +
                            "    (local.get \$ptr))\n" +
                            "  (func \$make_request (param \$trailers i32)" +
                            " (result i32)\n" +
                            "    (call \$request_new\n" +
                            "      (call \$fields_new)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (local.get \$trailers)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 64))\n" +
                            "    (i32.load (i32.const 64)))\n" +
                            "  (func \$consume_request (param \$request i32)" +
                            " (result i32)\n" +
                            "    (call \$request_consume\n" +
                            "      (local.get \$request)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 80))\n" +
                            "    (i32.load (i32.const 84)))\n" +
                            "  (func \$make_response (param \$trailers i32)" +
                            " (result i32)\n" +
                            "    (call \$response_new\n" +
                            "      (call \$fields_new)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (local.get \$trailers)\n" +
                            "      (i32.const 96))\n" +
                            "    (i32.load (i32.const 96)))\n" +
                            "  (func \$consume_response (param \$response i32)" +
                            " (result i32)\n" +
                            "    (call \$response_consume\n" +
                            "      (local.get \$response)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 112))\n" +
                            "    (i32.load (i32.const 116)))\n" +
                            "  (export \"api.make-request\" (func \$make_request))\n" +
                            "  (export \"api.consume-request\" (func" +
                            " \$consume_request))\n" +
                            "  (export \"api.make-response\" (func \$make_response))\n" +
                            "  (export \"api.consume-response\" (func" +
                            " \$consume_response))\n" +
                            ")\n"
                    )
                )
                .withWasiPreview3(wasi)
                .build()

        val requestTrailerFields =
            wasi.httpFields(
                mapOf(
                    "x-request-trailer" to
                        listOf("request-done".toByteArray(StandardCharsets.ISO_8859_1))
                )
            )
        val responseTrailerFields =
            wasi.httpFields(
                mapOf(
                    "x-response-trailer" to
                        listOf("response-done".toByteArray(StandardCharsets.ISO_8859_1))
                )
            )
        val requestFuture = wasi.completedFuture(WitResult.ok(WitValue.some(requestTrailerFields)))
        val responseFuture =
            wasi.completedFuture(WitResult.ok(WitValue.some(responseTrailerFields)))

        val request = (plugin.call("api.make-request", requestFuture) as Number).toLong()
        val response = (plugin.call("api.make-response", responseFuture) as Number).toLong()

        assertTrailerResult(wasi.httpRequestTrailers(request), "x-request-trailer", "request-done")
        assertTrailerResult(
            wasi.httpResponseTrailers(response),
            "x-response-trailer",
            "response-done",
        )

        val requestConsumeFuture = plugin.call("api.consume-request", request) as WitFuture<*>
        val responseConsumeFuture = plugin.call("api.consume-response", response) as WitFuture<*>

        assertTrailerMap(
            trailerFutureSnapshot(wasi, requestConsumeFuture),
            "x-request-trailer",
            "request-done",
        )
        assertTrailerMap(
            trailerFutureSnapshot(wasi, responseConsumeFuture),
            "x-response-trailer",
            "response-done",
        )
    }

    @Test
    fun readsHttpRequestBodyWithCanonicalIntrinsicsReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi3-http-body-canonical;

                world plugin {
                  import wasi:http/types@$version;
                  export api;
                }

                interface api {
                  run: func() -> u32;
                }

                package wasi:http@$version {
                  interface types {
                    variant error-code {
                      internal-error(option<string>),
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
                      consume-body: static func(
                        this: request,
                        res: future<result<_, error-code>>,
                      ) -> tuple<stream<u8>, future<result<option<trailers>, error-code>>>;
                    }
                  }
                }
                """
                    .trimIndent()
            )
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[constructor]fields\" (func \$fields_new (result i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[static]request.new\" (func \$request_new" +
                            " (param i32 i32 i32 i32 i32 i32 i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[static]request.consume-body\" (func" +
                            " \$request_consume (param i32 i32 i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[stream-new-0][static]request.new\" (func" +
                            " \$stream_new (result i64)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[async-lower][stream-write-0][static]request.new\"" +
                            " (func \$stream_write (param i32 i32 i32) (result i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[stream-drop-writable-0][static]request.new\"" +
                            " (func \$drop_writable (param i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[async-lower][stream-read-1][static]request.consume-body\"" +
                            " (func \$stream_read (param i32 i32 i32) (result i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[async-lower][future-read-2][static]request.consume-body\"" +
                            " (func \$future_read (param i32 i32) (result i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (data (i32.const 32) \"body\")\n" +
                            "  (func \$run (result i32)\n" +
                            "    (local \$pair i64)\n" +
                            "    (local \$reader i32)\n" +
                            "    (local \$writer i32)\n" +
                            "    (local \$request i32)\n" +
                            "    (local \$stream i32)\n" +
                            "    (local \$future i32)\n" +
                            "    (local \$status i32)\n" +
                            "    (local.set \$pair (call \$stream_new))\n" +
                            "    (local.set \$reader (i32.wrap_i64 (local.get \$pair)))\n" +
                            "    (local.set \$writer\n" +
                            "      (i32.wrap_i64\n" +
                            "        (i64.shr_u (local.get \$pair) (i64.const 32))))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$stream_write\n" +
                            "        (local.get \$writer)\n" +
                            "        (i32.const 32)\n" +
                            "        (i32.const 4)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 64))\n" +
                            "      (then unreachable))\n" +
                            "    (call \$drop_writable (local.get \$writer))\n" +
                            "    (call \$request_new\n" +
                            "      (call \$fields_new)\n" +
                            "      (i32.const 1)\n" +
                            "      (local.get \$reader)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 64))\n" +
                            "    (local.set \$request (i32.load (i32.const 64)))\n" +
                            "    (call \$request_consume\n" +
                            "      (local.get \$request)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 80))\n" +
                            "    (local.set \$stream (i32.load (i32.const 80)))\n" +
                            "    (local.set \$future (i32.load (i32.const 84)))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$future_read\n" +
                            "        (local.get \$future)\n" +
                            "        (i32.const 160)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                            "      (then unreachable))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 160))" +
                            " (i32.const 0))\n" +
                            "      (then unreachable))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$stream_read\n" +
                            "        (local.get \$stream)\n" +
                            "        (i32.const 128)\n" +
                            "        (i32.const 4)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 65))\n" +
                            "      (then unreachable))\n" +
                            "    (i32.add\n" +
                            "      (i32.add\n" +
                            "        (i32.add\n" +
                            "          (i32.add\n" +
                            "            (local.get \$status)\n" +
                            "            (i32.load8_u (i32.const 128)))\n" +
                            "          (i32.load8_u (i32.const 129)))\n" +
                            "        (i32.load8_u (i32.const 130)))\n" +
                            "      (i32.load8_u (i32.const 131))))\n" +
                            "  (export \"api.run\" (func \$run))\n" +
                            ")\n"
                    )
                )
                .withWasiPreview3(WasiPreview3.builder().build())
                .build()

        assertEquals(495L, plugin.call("api.run"))
    }

    @Test
    fun readsHttpResponseBodyWithCanonicalIntrinsicsReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi3-http-response-body-canonical;

                world plugin {
                  import wasi:http/types@$version;
                  export api;
                }

                interface api {
                  run: func() -> u32;
                }

                package wasi:http@$version {
                  interface types {
                    variant error-code {
                      internal-error(option<string>),
                    }

                    resource fields {
                      constructor();
                    }

                    type headers = fields;
                    type trailers = fields;

                    resource response {
                      new: static func(
                        headers: headers,
                        contents: option<stream<u8>>,
                        trailers: future<result<option<trailers>, error-code>>,
                      ) -> tuple<response, future<result<_, error-code>>>;
                      consume-body: static func(
                        this: response,
                        res: future<result<_, error-code>>,
                      ) -> tuple<stream<u8>, future<result<option<trailers>, error-code>>>;
                    }
                  }
                }
                """
                    .trimIndent()
            )
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[constructor]fields\" (func \$fields_new (result i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[static]response.new\" (func \$response_new" +
                            " (param i32 i32 i32 i32 i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[static]response.consume-body\" (func" +
                            " \$response_consume (param i32 i32 i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[stream-new-0][static]response.new\" (func" +
                            " \$stream_new (result i64)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[async-lower][stream-write-0][static]response.new\"" +
                            " (func \$stream_write (param i32 i32 i32) (result i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[stream-drop-writable-0][static]response.new\"" +
                            " (func \$drop_writable (param i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[async-lower][stream-read-1][static]response.consume-body\"" +
                            " (func \$stream_read (param i32 i32 i32) (result i32)))\n" +
                            "  (import \"wasi:http/types@$version\"" +
                            " \"[async-lower][future-read-2][static]response.consume-body\"" +
                            " (func \$future_read (param i32 i32) (result i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (data (i32.const 32) \"pong\")\n" +
                            "  (func \$run (result i32)\n" +
                            "    (local \$pair i64)\n" +
                            "    (local \$reader i32)\n" +
                            "    (local \$writer i32)\n" +
                            "    (local \$response i32)\n" +
                            "    (local \$stream i32)\n" +
                            "    (local \$future i32)\n" +
                            "    (local \$status i32)\n" +
                            "    (local.set \$pair (call \$stream_new))\n" +
                            "    (local.set \$reader (i32.wrap_i64 (local.get \$pair)))\n" +
                            "    (local.set \$writer\n" +
                            "      (i32.wrap_i64\n" +
                            "        (i64.shr_u (local.get \$pair) (i64.const 32))))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$stream_write\n" +
                            "        (local.get \$writer)\n" +
                            "        (i32.const 32)\n" +
                            "        (i32.const 4)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 64))\n" +
                            "      (then unreachable))\n" +
                            "    (call \$drop_writable (local.get \$writer))\n" +
                            "    (call \$response_new\n" +
                            "      (call \$fields_new)\n" +
                            "      (i32.const 1)\n" +
                            "      (local.get \$reader)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 96))\n" +
                            "    (local.set \$response (i32.load (i32.const 96)))\n" +
                            "    (call \$response_consume\n" +
                            "      (local.get \$response)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 128))\n" +
                            "    (local.set \$stream (i32.load (i32.const 128)))\n" +
                            "    (local.set \$future (i32.load (i32.const 132)))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$future_read\n" +
                            "        (local.get \$future)\n" +
                            "        (i32.const 160)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                            "      (then unreachable))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 160))" +
                            " (i32.const 0))\n" +
                            "      (then unreachable))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$stream_read\n" +
                            "        (local.get \$stream)\n" +
                            "        (i32.const 64)\n" +
                            "        (i32.const 4)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 65))\n" +
                            "      (then unreachable))\n" +
                            "    (i32.add\n" +
                            "      (i32.add\n" +
                            "        (i32.add\n" +
                            "          (i32.add\n" +
                            "            (local.get \$status)\n" +
                            "            (i32.load8_u (i32.const 64)))\n" +
                            "          (i32.load8_u (i32.const 65)))\n" +
                            "        (i32.load8_u (i32.const 66)))\n" +
                            "      (i32.load8_u (i32.const 67))))\n" +
                            "  (export \"api.run\" (func \$run))\n" +
                            ")\n"
                    )
                )
                .withWasiPreview3(WasiPreview3.builder().build())
                .build()

        assertEquals(501L, plugin.call("api.run"))
    }

    @Test
    fun linksSocketsDnsTcpAndUdpReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi3-sockets;

                world plugin {
                  import wasi:sockets/types@$version;
                  import wasi:sockets/ip-name-lookup@$version;
                  export api;
                }

                interface api {
                  run: func() -> u32;
                }

                package wasi:sockets@$version {
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

                    variant ip-address {
                      ipv4(ipv4-address),
                      ipv6(ipv6-address),
                    }

                    resource tcp-socket {
                      create: static func(address-family: ip-address-family) -> result<tcp-socket, error-code>;
                      get-address-family: func() -> ip-address-family;
                    }

                    resource udp-socket {
                      create: static func(address-family: ip-address-family) -> result<udp-socket, error-code>;
                      get-address-family: func() -> ip-address-family;
                    }
                  }

                  interface ip-name-lookup {
                    use types.{ip-address};
                    variant error-code {
                      access-denied,
                      invalid-argument,
                      name-unresolvable,
                      temporary-resolver-failure,
                      permanent-resolver-failure,
                      other(option<string>),
                    }
                    resolve-addresses: async func(name: string) -> result<list<ip-address>, error-code>;
                  }
                }
                """
                    .trimIndent()
            )
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"wasi:sockets/ip-name-lookup@$version\"" +
                            " \"resolve-addresses\" (func \$resolve" +
                            " (param i32) (param i32) (result i32)))\n" +
                            "  (import \"wasi:sockets/ip-name-lookup@$version\"" +
                            " \"[async-lower][future-read-0]resolve-addresses\"" +
                            " (func \$resolve_future_read (param i32 i32) (result i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[static]tcp-socket.create\" (func \$tcp_create" +
                            " (param i32) (param i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[method]tcp-socket.get-address-family\" (func" +
                            " \$tcp_family (param i32) (result i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[static]udp-socket.create\" (func \$udp_create" +
                            " (param i32) (param i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[method]udp-socket.get-address-family\" (func" +
                            " \$udp_family (param i32) (result i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (global \$heap (mut i32) (i32.const 256))\n" +
                            "  (data (i32.const 16) \"127.0.0.1\")\n" +
                            "  (func (export \"canonical_abi_realloc\")\n" +
                            "    (param \$old i32) (param \$old_size i32)\n" +
                            "    (param \$align i32) (param \$new_size i32)\n" +
                            "    (result i32)\n" +
                            "    (local \$ptr i32)\n" +
                            "    (local.set \$ptr\n" +
                            "      (i32.and\n" +
                            "        (i32.add (global.get \$heap)" +
                            " (i32.sub (local.get \$align) (i32.const 1)))\n" +
                            "        (i32.xor\n" +
                            "          (i32.sub (local.get \$align) (i32.const 1))\n" +
                            "          (i32.const -1))))\n" +
                            "    (global.set \$heap\n" +
                            "      (i32.add (local.get \$ptr) (local.get" +
                            " \$new_size)))\n" +
                            "    (local.get \$ptr))\n" +
                            "  (func \$run (result i32)\n" +
                            "    (local \$tcp i32)\n" +
                            "    (local \$udp i32)\n" +
                            "    (local \$future i32)\n" +
                            "    (local \$status i32)\n" +
                            "    (local.set \$future (call \$resolve (i32.const 16)" +
                            " (i32.const 9)))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$resolve_future_read (local.get \$future)" +
                            " (i32.const 64)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                            "      (then (return (i32.const 91))))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                            " (i32.const 0))\n" +
                            "      (then (return (i32.const 90))))\n" +
                            "    (if (i32.eqz (i32.load (i32.const 72)))\n" +
                            "      (then (return (i32.const 89))))\n" +
                            "    (call \$tcp_create (i32.const 0) (i32.const 96))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 96))" +
                            " (i32.const 0))\n" +
                            "      (then (return (i32.const 88))))\n" +
                            "    (local.set \$tcp (i32.load (i32.const 100)))\n" +
                            "    (call \$udp_create (i32.const 0) (i32.const 112))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 112))" +
                            " (i32.const 0))\n" +
                            "      (then (return (i32.const 87))))\n" +
                            "    (local.set \$udp (i32.load (i32.const 116)))\n" +
                            "    (i32.add\n" +
                            "      (i32.mul (call \$tcp_family (local.get \$tcp))" +
                            " (i32.const 10))\n" +
                            "      (call \$udp_family (local.get \$udp))))\n" +
                            "  (export \"api.run\" (func \$run))\n" +
                            ")\n"
                    )
                )
                .withWasiPreview3(WasiPreview3.builder().withNetworking().build())
                .build()

        assertEquals(0L, plugin.call("api.run"))
    }

    @Test
    fun performsTcpConnectAndUdpSendReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val serverFailure = AtomicReference<Throwable?>()
        val tcpAccepted = AtomicReference<Boolean>(false)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { tcpServer ->
            DatagramSocket(0, InetAddress.getLoopbackAddress()).use { udpServer ->
                udpServer.soTimeout = 2_000
                val tcpThread =
                    Thread(
                        {
                            try {
                                tcpServer.accept().use { tcpAccepted.set(true) }
                            } catch (e: Throwable) {
                                serverFailure.set(e)
                            }
                        },
                        "wasi3-tcp-connect-test",
                    )
                tcpThread.isDaemon = true
                tcpThread.start()

                val witPackage =
                    WitPackage.parse(
                        """
                        package example:wasi3-socket-io;

                        world plugin {
                          import wasi:sockets/types@$version;
                          export api;
                        }

                        interface api {
                          run: func() -> u32;
                        }

                        package wasi:sockets@$version {
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
                            Wat2Wasm.parse(
                                "(module\n" +
                                    "  (import \"wasi:sockets/types@$version\"" +
                                    " \"[static]tcp-socket.create\" (func" +
                                    " \$tcp_create (param i32) (param i32)))\n" +
                                    "  (import \"wasi:sockets/types@$version\"" +
                                    " \"[async-lower][method]tcp-socket.connect\" (func" +
                                    " \$tcp_connect (param i32 i32) (result i32)))\n" +
                                    "  (import \"wasi:sockets/types@$version\"" +
                                    " \"[method]tcp-socket.get-local-address\" (func" +
                                    " \$tcp_local (param i32) (param i32)))\n" +
                                    "  (import \"wasi:sockets/types@$version\"" +
                                    " \"[method]tcp-socket.get-remote-address\" (func" +
                                    " \$tcp_remote (param i32) (param i32)))\n" +
                                    "  (import \"wasi:sockets/types@$version\"" +
                                    " \"[static]udp-socket.create\" (func" +
                                    " \$udp_create (param i32) (param i32)))\n" +
                                    "  (import \"wasi:sockets/types@$version\"" +
                                    " \"[async-lower][method]udp-socket.send\" (func" +
                                    " \$udp_send (param i32 i32) (result i32)))\n" +
                                    "  (memory (export \"memory\") 1)\n" +
                                    "  (data (i32.const 16) \"ping\")\n" +
                                    "  (func \$run (result i32)\n" +
                                    "    (local \$tcp i32)\n" +
                                    "    (local \$udp i32)\n" +
                                    "    (local \$status i32)\n" +
                                    "    (call \$tcp_create (i32.const 0) (i32.const 64))\n" +
                                    "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                                    " (i32.const 0))\n" +
                                    "      (then (return (i32.const 90))))\n" +
                                    "    (local.set \$tcp (i32.load (i32.const 68)))\n" +
                                    "    (i32.store (i32.const 32) (local.get \$tcp))\n" +
                                    "    (i32.store8 (i32.const 36) (i32.const 0))\n" +
                                    "    (i32.store16 (i32.const 40) (i32.const " +
                                    tcpServer.localPort +
                                    "))\n" +
                                    "    (i32.store8 (i32.const 42) (i32.const 127))\n" +
                                    "    (i32.store8 (i32.const 43) (i32.const 0))\n" +
                                    "    (i32.store8 (i32.const 44) (i32.const 0))\n" +
                                    "    (i32.store8 (i32.const 45) (i32.const 1))\n" +
                                    "    (local.set \$status\n" +
                                    "      (call \$tcp_connect (i32.const 32) (i32.const 80)))\n" +
                                    "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                    "      (then (return (i32.const 84))))\n" +
                                    "    (if (i32.ne (i32.load8_u (i32.const 80))" +
                                    " (i32.const 0))\n" +
                                    "      (then (return (i32.const 89))))\n" +
                                    "    (call \$tcp_local (local.get \$tcp) (i32.const 96))\n" +
                                    "    (if (i32.ne (i32.load8_u (i32.const 96))" +
                                    " (i32.const 0))\n" +
                                    "      (then (return (i32.const 88))))\n" +
                                    "    (call \$tcp_remote (local.get \$tcp) (i32.const 144))\n" +
                                    "    (if (i32.ne (i32.load8_u (i32.const 144))" +
                                    " (i32.const 0))\n" +
                                    "      (then (return (i32.const 87))))\n" +
                                    "    (call \$udp_create (i32.const 0) (i32.const 192))\n" +
                                    "    (if (i32.ne (i32.load8_u (i32.const 192))" +
                                    " (i32.const 0))\n" +
                                    "      (then (return (i32.const 86))))\n" +
                                    "    (local.set \$udp (i32.load (i32.const 196)))\n" +
                                    "    (i32.store (i32.const 224) (local.get \$udp))\n" +
                                    "    (i32.store (i32.const 228) (i32.const 16))\n" +
                                    "    (i32.store (i32.const 232) (i32.const 4))\n" +
                                    "    (i32.store8 (i32.const 236) (i32.const 1))\n" +
                                    "    (i32.store8 (i32.const 240) (i32.const 0))\n" +
                                    "    (i32.store16 (i32.const 244) (i32.const " +
                                    udpServer.localPort +
                                    "))\n" +
                                    "    (i32.store8 (i32.const 246) (i32.const 127))\n" +
                                    "    (i32.store8 (i32.const 247) (i32.const 0))\n" +
                                    "    (i32.store8 (i32.const 248) (i32.const 0))\n" +
                                    "    (i32.store8 (i32.const 249) (i32.const 1))\n" +
                                    "    (local.set \$status\n" +
                                    "      (call \$udp_send (i32.const 224) (i32.const 288)))\n" +
                                    "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                    "      (then (return (i32.const 83))))\n" +
                                    "    (if (i32.ne (i32.load8_u (i32.const 288))" +
                                    " (i32.const 0))\n" +
                                    "      (then (return (i32.const 85))))\n" +
                                    "    (i32.const 42))\n" +
                                    "  (export \"api.run\" (func \$run))\n" +
                                    ")\n"
                            )
                        )
                        .withWasiPreview3(WasiPreview3.builder().withNetworking().build())
                        .build()

                assertEquals(42L, plugin.call("api.run"))
                val packet = DatagramPacket(ByteArray(16), 16)
                udpServer.receive(packet)
                tcpThread.join(2_000L)

                assertEquals(
                    "ping",
                    String(packet.data, packet.offset, packet.length, StandardCharsets.ISO_8859_1),
                )
                assertEquals(true, tcpAccepted.get())
            }
        }

        if (serverFailure.get() != null) {
            throw AssertionError("TCP test server failed", serverFailure.get())
        }
    }

    @Test
    fun receivesTcpDataAfterReceiveStreamIsCreatedReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val serverFailure = AtomicReference<Throwable?>()
        val allowWrite = CountDownLatch(1)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { tcpServer ->
            val tcpThread =
                Thread(
                    {
                        try {
                            tcpServer.accept().use { socket ->
                                if (!allowWrite.await(2, TimeUnit.SECONDS)) {
                                    throw AssertionError("Timed out waiting to write TCP payload")
                                }
                                socket
                                    .getOutputStream()
                                    .write("late".toByteArray(StandardCharsets.ISO_8859_1))
                                socket.getOutputStream().flush()
                            }
                        } catch (e: Throwable) {
                            serverFailure.set(e)
                        }
                    },
                    "wasi3-tcp-lazy-receive-test",
                )
            tcpThread.isDaemon = true
            tcpThread.start()

            val witPackage =
                WitPackage.parse(
                    """
                    package example:wasi3-tcp-lazy-receive;

                    world plugin {
                      import wasi:sockets/types@$version;
                      export api;
                    }

                    interface api {
                      receive: func() -> stream<u8>;
                    }

                    package wasi:sockets@$version {
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
                          receive: func() -> tuple<stream<u8>, future<result<_, error-code>>>;
                        }
                      }
                    }
                    """
                        .trimIndent()
                )
            val wasi = WasiPreview3.builder().withNetworking().build()
            val plugin =
                WasmPlugin.builder(witPackage)
                    .withModule(
                        Wat2Wasm.parse(
                            "(module\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[static]tcp-socket.create\" (func" +
                                " \$tcp_create (param i32) (param i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[method]tcp-socket.connect\" (func" +
                                " \$tcp_connect (param i32 i32 i32 i32 i32 i32" +
                                " i32 i32 i32 i32 i32 i32 i32) (result i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[async-lower][future-read-0][method]tcp-socket.connect\"" +
                                " (func \$connect_future_read (param i32 i32) (result i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[method]tcp-socket.receive\" (func" +
                                " \$tcp_receive (param i32 i32)))\n" +
                                "  (memory (export \"memory\") 1)\n" +
                                "  (func \$receive (result i32)\n" +
                                "    (local \$tcp i32)\n" +
                                "    (local \$stream i32)\n" +
                                "    (local \$future i32)\n" +
                                "    (local \$status i32)\n" +
                                "    (call \$tcp_create (i32.const 0) (i32.const 64))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                                " (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (local.set \$tcp (i32.load (i32.const 68)))\n" +
                                "    (local.set \$future\n" +
                                "      (call \$tcp_connect\n" +
                                "      (local.get \$tcp)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const " +
                                tcpServer.localPort +
                                ")\n" +
                                "      (i32.const 127)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 1)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$connect_future_read (local.get \$future)" +
                                " (i32.const 80)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 80))" +
                                " (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (call \$tcp_receive (local.get \$tcp)" +
                                " (i32.const 96))\n" +
                                "    (local.set \$stream (i32.load (i32.const 96)))\n" +
                                "    (local.get \$stream))\n" +
                                "  (export \"api.receive\" (func \$receive))\n" +
                                ")\n"
                        )
                    )
                    .withWasiPreview3(wasi)
                    .build()

            val stream = plugin.call("api.receive") as WitStream<*>
            allowWrite.countDown()

            assertArrayEquals(
                "late".toByteArray(StandardCharsets.ISO_8859_1),
                wasi.streamBytes(stream),
            )
            tcpThread.join(2_000L)
        }

        if (serverFailure.get() != null) {
            throw AssertionError("TCP lazy receive test server failed", serverFailure.get())
        }
    }

    @Test
    fun readsTcpReceiveStreamWithCanonicalIntrinsicsReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val serverFailure = AtomicReference<Throwable?>()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { tcpServer ->
            val tcpThread =
                Thread(
                    {
                        try {
                            tcpServer.accept().use { socket ->
                                socket
                                    .getOutputStream()
                                    .write("wave".toByteArray(StandardCharsets.ISO_8859_1))
                                socket.getOutputStream().flush()
                            }
                        } catch (e: Throwable) {
                            serverFailure.set(e)
                        }
                    },
                    "wasi3-tcp-receive-canonical-test",
                )
            tcpThread.isDaemon = true
            tcpThread.start()

            val witPackage =
                WitPackage.parse(
                    """
                    package example:wasi3-tcp-receive-canonical;

                    world plugin {
                      import wasi:sockets/types@$version;
                      export api;
                    }

                    interface api {
                      run: func() -> u32;
                    }

                    package wasi:sockets@$version {
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
                          receive: func() -> tuple<stream<u8>, future<result<_, error-code>>>;
                        }
                      }
                    }
                    """
                        .trimIndent()
                )
            val plugin =
                WasmPlugin.builder(witPackage)
                    .withModule(
                        Wat2Wasm.parse(
                            "(module\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[static]tcp-socket.create\" (func" +
                                " \$tcp_create (param i32) (param i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[method]tcp-socket.connect\" (func" +
                                " \$tcp_connect (param i32 i32 i32 i32 i32 i32" +
                                " i32 i32 i32 i32 i32 i32 i32) (result i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[async-lower][future-read-0][method]tcp-socket.connect\"" +
                                " (func \$connect_future_read (param i32 i32) (result i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[method]tcp-socket.receive\" (func" +
                                " \$tcp_receive (param i32 i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[async-lower][stream-read-0][method]tcp-socket.receive\"" +
                                " (func \$stream_read (param i32 i32 i32) (result i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[async-lower][future-read-1][method]tcp-socket.receive\"" +
                                " (func \$future_read (param i32 i32) (result i32)))\n" +
                                "  (memory (export \"memory\") 1)\n" +
                                "  (func \$run (result i32)\n" +
                                "    (local \$tcp i32)\n" +
                                "    (local \$stream i32)\n" +
                                "    (local \$future i32)\n" +
                                "    (local \$status i32)\n" +
                                "    (call \$tcp_create (i32.const 0) (i32.const 64))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                                " (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (local.set \$tcp (i32.load (i32.const 68)))\n" +
                                "    (local.set \$future\n" +
                                "      (call \$tcp_connect\n" +
                                "      (local.get \$tcp)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const " +
                                tcpServer.localPort +
                                ")\n" +
                                "      (i32.const 127)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 1)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 0)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$connect_future_read (local.get \$future)" +
                                " (i32.const 80)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 80))" +
                                " (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (call \$tcp_receive (local.get \$tcp)" +
                                " (i32.const 96))\n" +
                                "    (local.set \$stream (i32.load (i32.const 96)))\n" +
                                "    (local.set \$future (i32.load (i32.const 100)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$future_read\n" +
                                "        (local.get \$future)\n" +
                                "        (i32.const 160)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 160))" +
                                " (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$stream_read\n" +
                                "        (local.get \$stream)\n" +
                                "        (i32.const 128)\n" +
                                "        (i32.const 4)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 64))\n" +
                                "      (then unreachable))\n" +
                                "    (i32.add\n" +
                                "      (i32.add\n" +
                                "        (i32.add\n" +
                                "          (i32.add\n" +
                                "            (local.get \$status)\n" +
                                "            (i32.load8_u (i32.const 128)))\n" +
                                "          (i32.load8_u (i32.const 129)))\n" +
                                "        (i32.load8_u (i32.const 130)))\n" +
                                "      (i32.load8_u (i32.const 131))))\n" +
                                "  (export \"api.run\" (func \$run))\n" +
                                ")\n"
                        )
                    )
                    .withWasiPreview3(WasiPreview3.builder().withNetworking().build())
                    .build()

            assertEquals(499L, plugin.call("api.run"))
            tcpThread.join(2_000L)
        }

        if (serverFailure.get() != null) {
            throw AssertionError("TCP canonical receive test server failed", serverFailure.get())
        }
    }

    @Test
    fun sendsTcpStreamWithCanonicalIntrinsicsReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val serverFailure = AtomicReference<Throwable?>()
        val received = AtomicReference<String?>()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { tcpServer ->
            val tcpThread =
                Thread(
                    {
                        try {
                            tcpServer.accept().use { socket ->
                                val input = socket.getInputStream()
                                val bytes = ByteArray(4)
                                var offset = 0
                                while (offset < bytes.size) {
                                    val read = input.read(bytes, offset, bytes.size - offset)
                                    if (read < 0) {
                                        break
                                    }
                                    offset += read
                                }
                                received.set(String(bytes, 0, offset, StandardCharsets.ISO_8859_1))
                            }
                        } catch (e: Throwable) {
                            serverFailure.set(e)
                        }
                    },
                    "wasi3-tcp-send-canonical-test",
                )
            tcpThread.isDaemon = true
            tcpThread.start()

            val witPackage =
                WitPackage.parse(
                    """
                    package example:wasi3-tcp-send-canonical;

                    world plugin {
                      import wasi:sockets/types@$version;
                      export api;
                    }

                    interface api {
                      run: func() -> u32;
                    }

                    package wasi:sockets@$version {
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
                          send: async func(data: stream<u8>) -> result<_, error-code>;
                        }
                      }
                    }
                    """
                        .trimIndent()
                )
            val plugin =
                WasmPlugin.builder(witPackage)
                    .withModule(
                        Wat2Wasm.parse(
                            "(module\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[static]tcp-socket.create\" (func" +
                                " \$tcp_create (param i32) (param i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[method]tcp-socket.connect\" (func" +
                                " \$tcp_connect (param i32 i32 i32 i32 i32 i32" +
                                " i32 i32 i32 i32 i32 i32 i32) (result i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[async-lower][future-read-0][method]tcp-socket.connect\"" +
                                " (func \$connect_future_read (param i32 i32) (result i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[stream-new-0][method]tcp-socket.send\"" +
                                " (func \$stream_new (result i64)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[async-lower][stream-write-0][method]tcp-socket.send\"" +
                                " (func \$stream_write (param i32 i32 i32) (result i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[stream-drop-writable-0][method]tcp-socket.send\"" +
                                " (func \$drop_writable (param i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[method]tcp-socket.send\" (func \$tcp_send" +
                                " (param i32 i32) (result i32)))\n" +
                                "  (import \"wasi:sockets/types@$version\"" +
                                " \"[async-lower][future-read-1][method]tcp-socket.send\"" +
                                " (func \$send_future_read (param i32 i32) (result i32)))\n" +
                                "  (memory (export \"memory\") 1)\n" +
                                "  (data (i32.const 32) \"send\")\n" +
                                "  (func \$run (result i32)\n" +
                                "    (local \$tcp i32)\n" +
                                "    (local \$future i32)\n" +
                                "    (local \$status i32)\n" +
                                "    (local \$pair i64)\n" +
                                "    (local \$reader i32)\n" +
                                "    (local \$writer i32)\n" +
                                "    (call \$tcp_create (i32.const 0) (i32.const 64))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                                " (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (local.set \$tcp (i32.load (i32.const 68)))\n" +
                                "    (local.set \$future\n" +
                                "      (call \$tcp_connect\n" +
                                "        (local.get \$tcp)\n" +
                                "        (i32.const 0)\n" +
                                "        (i32.const " +
                                tcpServer.localPort +
                                ")\n" +
                                "        (i32.const 127)\n" +
                                "        (i32.const 0)\n" +
                                "        (i32.const 0)\n" +
                                "        (i32.const 1)\n" +
                                "        (i32.const 0)\n" +
                                "        (i32.const 0)\n" +
                                "        (i32.const 0)\n" +
                                "        (i32.const 0)\n" +
                                "        (i32.const 0)\n" +
                                "        (i32.const 0)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$connect_future_read (local.get \$future)" +
                                " (i32.const 80)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 80))" +
                                " (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (local.set \$pair (call \$stream_new))\n" +
                                "    (local.set \$reader (i32.wrap_i64 (local.get \$pair)))\n" +
                                "    (local.set \$writer\n" +
                                "      (i32.wrap_i64\n" +
                                "        (i64.shr_u (local.get \$pair) (i64.const 32))))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$stream_write (local.get \$writer)" +
                                " (i32.const 32) (i32.const 4)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 64))\n" +
                                "      (then unreachable))\n" +
                                "    (call \$drop_writable (local.get \$writer))\n" +
                                "    (local.set \$future (call \$tcp_send (local.get \$tcp)" +
                                " (local.get \$reader)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$send_future_read (local.get \$future)" +
                                " (i32.const 128)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 128))" +
                                " (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (i32.const 42))\n" +
                                "  (export \"api.run\" (func \$run))\n" +
                                ")\n"
                        )
                    )
                    .withWasiPreview3(WasiPreview3.builder().withNetworking().build())
                    .build()

            assertEquals(42L, plugin.call("api.run"))
            tcpThread.join(2_000L)
        }

        if (serverFailure.get() != null) {
            throw AssertionError("TCP canonical send test server failed", serverFailure.get())
        }
        assertEquals("send", received.get())
    }

    @Test
    fun receivesUdpDatagramReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val guestPort = DatagramSocket(0, InetAddress.getLoopbackAddress()).use { it.localPort }
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi3-udp-receive;

                world plugin {
                  import wasi:sockets/types@$version;
                  export api;
                }

                interface api {
                  run: func() -> u32;
                }

                package wasi:sockets@$version {
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

                    resource udp-socket {
                      create: static func(address-family: ip-address-family) -> result<udp-socket, error-code>;
                      bind: func(local-address: ip-socket-address) -> result<_, error-code>;
                      receive: async func() -> result<tuple<list<u8>, ip-socket-address>, error-code>;
                    }
                  }
                }
                """
                    .trimIndent()
            )
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[static]udp-socket.create\" (func \$udp_create" +
                            " (param i32) (param i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[method]udp-socket.bind\" (func \$udp_bind" +
                            " (param i32 i32 i32 i32 i32 i32 i32 i32 i32 i32" +
                            " i32 i32 i32 i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[async-lower][method]udp-socket.receive\" (func" +
                            " \$udp_receive (param i32 i32) (result i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (global \$heap (mut i32) (i32.const 512))\n" +
                            "  (func (export \"canonical_abi_realloc\")\n" +
                            "    (param \$old i32) (param \$old_size i32)\n" +
                            "    (param \$align i32) (param \$new_size i32)\n" +
                            "    (result i32)\n" +
                            "    (local \$ptr i32)\n" +
                            "    (local.set \$ptr\n" +
                            "      (i32.and\n" +
                            "        (i32.add (global.get \$heap)" +
                            " (i32.sub (local.get \$align) (i32.const 1)))\n" +
                            "        (i32.xor\n" +
                            "          (i32.sub (local.get \$align) (i32.const 1))\n" +
                            "          (i32.const -1))))\n" +
                            "    (global.set \$heap\n" +
                            "      (i32.add (local.get \$ptr) (local.get" +
                            " \$new_size)))\n" +
                            "    (local.get \$ptr))\n" +
                            "  (func \$run (result i32)\n" +
                            "    (local \$udp i32)\n" +
                            "    (local \$data i32)\n" +
                            "    (local \$status i32)\n" +
                            "    (call \$udp_create (i32.const 0) (i32.const 64))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                            " (i32.const 0))\n" +
                            "      (then (return (i32.const 90))))\n" +
                            "    (local.set \$udp (i32.load (i32.const 68)))\n" +
                            "    (call \$udp_bind\n" +
                            "      (local.get \$udp)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const " +
                            guestPort +
                            ")\n" +
                            "      (i32.const 127)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 1)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 0)\n" +
                            "      (i32.const 96))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 96))" +
                            " (i32.const 0))\n" +
                            "      (then (return (i32.const 89))))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$udp_receive (local.get \$udp) (i32.const 128)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                            "      (then (return (i32.const 85))))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 128))" +
                            " (i32.const 0))\n" +
                            "      (then (return (i32.const 88))))\n" +
                            "    (if (i32.ne (i32.load (i32.const 136))" +
                            " (i32.const 4))\n" +
                            "      (then (return (i32.const 87))))\n" +
                            "    (local.set \$data (i32.load (i32.const 132)))\n" +
                            "    (if (i32.ne (i32.load (local.get \$data))" +
                            " (i32.const 1735290736))\n" +
                            "      (then (return (i32.const 86))))\n" +
                            "    (i32.const 43))\n" +
                            "  (export \"api.run\" (func \$run))\n" +
                            ")\n"
                    )
                )
                .withWasiPreview3(WasiPreview3.builder().withNetworking().build())
                .build()

        val result = AtomicReference<Any?>()
        val failure = AtomicReference<Throwable?>()
        val thread =
            Thread(
                {
                    try {
                        result.set(plugin.call("api.run"))
                    } catch (e: Throwable) {
                        failure.set(e)
                    }
                },
                "wasi3-udp-receive-component",
            )
        thread.start()

        DatagramSocket().use { sender ->
            val bytes = "pong".toByteArray(StandardCharsets.ISO_8859_1)
            val packet =
                DatagramPacket(bytes, bytes.size, InetAddress.getLoopbackAddress(), guestPort)
            repeat(10) {
                sender.send(packet)
                if (result.get() != null || failure.get() != null) {
                    return@repeat
                }
                runBlocking { delay(50L) }
            }
        }
        thread.join(2_000L)

        if (failure.get() != null) {
            throw AssertionError("UDP receive component failed", failure.get())
        }
        assertEquals(43L, result.get())
    }

    @Test
    fun acceptsTcpConnectionFromListenStreamReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val wasi = WasiPreview3.builder().withNetworking().build()
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi3-tcp-listen;

                world plugin {
                  import wasi:sockets/types@$version;
                  export api;
                }

                interface api {
                  use wasi:sockets/types@$version.{tcp-socket};
                  listen: func() -> tuple<stream<tcp-socket>, u32>;
                  accept: func(listener: stream<tcp-socket>) -> u32;
                }

                package wasi:sockets@$version {
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
                      listen: func() -> result<stream<tcp-socket>, error-code>;
                      get-local-address: func() -> result<ip-socket-address, error-code>;
                      get-remote-address: func() -> result<ip-socket-address, error-code>;
                    }
                  }
                }
                """
                    .trimIndent()
            )
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[static]tcp-socket.create\" (func" +
                            " \$tcp_create (param i32) (param i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[method]tcp-socket.listen\" (func \$tcp_listen" +
                            " (param i32) (param i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[async-lower][stream-read-0][method]tcp-socket.listen\"" +
                            " (func \$tcp_listen_read (param i32 i32 i32) (result i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[method]tcp-socket.get-local-address\" (func" +
                            " \$tcp_local (param i32) (param i32)))\n" +
                            "  (import \"wasi:sockets/types@$version\"" +
                            " \"[method]tcp-socket.get-remote-address\" (func" +
                            " \$tcp_remote (param i32) (param i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (func \$listen (result i32)\n" +
                            "    (local \$tcp i32)\n" +
                            "    (local \$stream i32)\n" +
                            "    (local \$port i32)\n" +
                            "    (call \$tcp_create (i32.const 0) (i32.const 64))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                            " (i32.const 0))\n" +
                            "      (then (return (call \$listen_error (i32.const 90)))))\n" +
                            "    (local.set \$tcp (i32.load (i32.const 68)))\n" +
                            "    (call \$tcp_listen (local.get \$tcp)" +
                            " (i32.const 80))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 80))" +
                            " (i32.const 0))\n" +
                            "      (then (return (call \$listen_error (i32.const 89)))))\n" +
                            "    (local.set \$stream (i32.load (i32.const 84)))\n" +
                            "    (call \$tcp_local (local.get \$tcp)" +
                            " (i32.const 96))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 96))" +
                            " (i32.const 0))\n" +
                            "      (then (return (call \$listen_error (i32.const 88)))))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 100))" +
                            " (i32.const 0))\n" +
                            "      (then (return (call \$listen_error (i32.const 87)))))\n" +
                            "    (local.set \$port (i32.load16_u (i32.const 104)))\n" +
                            "    (i32.store (i32.const 160) (local.get \$stream))\n" +
                            "    (i32.store (i32.const 164) (local.get \$port))\n" +
                            "    (i32.const 160))\n" +
                            "  (func \$listen_error (param \$code i32) (result i32)\n" +
                            "    (i32.store (i32.const 160) (i32.const 0))\n" +
                            "    (i32.store (i32.const 164) (local.get \$code))\n" +
                            "    (i32.const 160))\n" +
                            "  (func \$accept (param \$stream i32) (result i32)\n" +
                            "    (local \$accepted i32)\n" +
                            "    (local \$status i32)\n" +
                            "    (local.set \$status\n" +
                            "      (call \$tcp_listen_read\n" +
                            "        (local.get \$stream)\n" +
                            "        (i32.const 192)\n" +
                            "        (i32.const 1)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 16))\n" +
                            "      (then (return (i32.const 86))))\n" +
                            "    (local.set \$accepted (i32.load (i32.const 192)))\n" +
                            "    (call \$tcp_remote (local.get \$accepted) (i32.const 208))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 208))" +
                            " (i32.const 0))\n" +
                            "      (then (return (i32.const 85))))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 212))" +
                            " (i32.const 0))\n" +
                            "      (then (return (i32.const 84))))\n" +
                            "    (if (i32.eqz (i32.load16_u (i32.const 216)))\n" +
                            "      (then (return (i32.const 83))))\n" +
                            "    (i32.const 42))\n" +
                            "  (export \"api.listen\" (func \$listen))\n" +
                            "  (export \"api.accept\" (func \$accept))\n" +
                            ")\n"
                    )
                )
                .withWasiPreview3(wasi)
                .build()

        val result = plugin.call("api.listen") as List<*>
        val stream = result[0] as WitStream<*>
        val port = (result[1] as Number).toInt()
        assertTrue(port > 1024, "TCP listen diagnostic code or privileged port: $port")

        val clientFailure = AtomicReference<Throwable?>()
        val clientThread =
            Thread(
                {
                    try {
                        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                            socket
                                .getOutputStream()
                                .write("hello".toByteArray(StandardCharsets.ISO_8859_1))
                        }
                    } catch (e: Throwable) {
                        clientFailure.set(e)
                    }
                },
                "wasi3-tcp-listen-client",
            )
        clientThread.isDaemon = true
        clientThread.start()

        assertEquals(42L, plugin.call("api.accept", stream))
        clientThread.join(2_000L)

        if (clientFailure.get() != null) {
            throw AssertionError("TCP listen client failed", clientFailure.get())
        }

        val helperClientFailure = AtomicReference<Throwable?>()
        val helperClientThread =
            Thread(
                {
                    try {
                        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                            socket
                                .getOutputStream()
                                .write("again".toByteArray(StandardCharsets.ISO_8859_1))
                        }
                    } catch (e: Throwable) {
                        helperClientFailure.set(e)
                    }
                },
                "wasi3-tcp-listen-helper-client",
            )
        helperClientThread.isDaemon = true
        helperClientThread.start()
        val accepted =
            when (val acceptResult = wasi.acceptTcpConnection(stream)) {
                is WitResult.Ok -> acceptResult.value()
                is WitResult.Err ->
                    throw AssertionError("TCP accept failed: ${acceptResult.value()}")
            }
        helperClientThread.join(2_000L)

        if (helperClientFailure.get() != null) {
            throw AssertionError("TCP listen helper client failed", helperClientFailure.get())
        }
        val local =
            when (val localResult = wasi.tcpLocalAddress(accepted)) {
                is WitResult.Ok -> localResult.value()
                is WitResult.Err ->
                    throw AssertionError("accepted local address failed: ${localResult.value()}")
            }
        val remote =
            when (val remoteResult = wasi.tcpRemoteAddress(accepted)) {
                is WitResult.Ok -> remoteResult.value()
                is WitResult.Err ->
                    throw AssertionError("accepted remote address failed: ${remoteResult.value()}")
            }

        assertEquals(port, socketAddressPort(local))
        assertTrue(socketAddressPort(remote) > 0)
    }

    @Test
    fun linksCliClocksAndRandomReleaseCandidateImportsWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        var monotonicReads = 0
        val wasi =
            WasiPreview3.builder()
                .withArguments("guest.wasm", "alpha", "beta")
                .withEnvironment("MODE", "p3")
                .withInitialCwd("/work")
                .withFixedWallClock(KotlinInstant.fromEpochSeconds(1_700_000_000L, 42))
                .withWallClockResolutionNanos(123L)
                .withMonotonicClock {
                    monotonicReads += 1
                    if (monotonicReads == 1) 1_000_000L else 1_000_123L
                }
                .withMonotonicResolutionNanos(456L)
                .withSecureRandom(Random(7L))
                .withInsecureSeed(11L, 12L)
                .build()
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi3-sync;

                world plugin {
                  import wasi:cli/environment@$version;
                  import wasi:clocks/system-clock@$version;
                  import wasi:clocks/monotonic-clock@$version;
                  import wasi:random/random@$version;
                  import wasi:random/insecure-seed@$version;
                  export api;
                }

                interface api {
                  run: func() -> u64;
                }

                package wasi:cli@$version {
                  interface environment {
                    get-environment: func() -> list<tuple<string, string>>;
                    get-arguments: func() -> list<string>;
                    get-initial-cwd: func() -> option<string>;
                  }
                }

                package wasi:clocks@$version {
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

                package wasi:random@$version {
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
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"wasi:cli/environment@$version\"" +
                            " \"get-arguments\" (func \$args (param i32)))\n" +
                            "  (import \"wasi:cli/environment@$version\"" +
                            " \"get-environment\" (func \$env (param i32)))\n" +
                            "  (import \"wasi:cli/environment@$version\"" +
                            " \"get-initial-cwd\" (func \$cwd (param i32)))\n" +
                            "  (import \"wasi:clocks/system-clock@$version\"" +
                            " \"now\" (func \$system_now (param i32)))\n" +
                            "  (import \"wasi:clocks/system-clock@$version\"" +
                            " \"get-resolution\" (func \$system_resolution" +
                            " (result i64)))\n" +
                            "  (import \"wasi:clocks/monotonic-clock@$version\"" +
                            " \"now\" (func \$monotonic_now (result i64)))\n" +
                            "  (import \"wasi:clocks/monotonic-clock@$version\"" +
                            " \"get-resolution\" (func \$monotonic_resolution" +
                            " (result i64)))\n" +
                            "  (import \"wasi:clocks/monotonic-clock@$version\"" +
                            " \"[async-lower]wait-for\" (func \$monotonic_wait_for" +
                            " (param i64) (result i32)))\n" +
                            "  (import \"wasi:random/random@$version\"" +
                            " \"get-random-bytes\" (func \$random_bytes" +
                            " (param i64) (param i32)))\n" +
                            "  (import \"wasi:random/insecure-seed@$version\"" +
                            " \"get-insecure-seed\" (func \$seed (param i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (global \$heap (mut i32) (i32.const 256))\n" +
                            "  (func (export \"canonical_abi_realloc\")\n" +
                            "    (param \$old i32) (param \$old_size i32)\n" +
                            "    (param \$align i32) (param \$new_size i32)\n" +
                            "    (result i32)\n" +
                            "    (local \$ptr i32)\n" +
                            "    (local.set \$ptr\n" +
                            "      (i32.and\n" +
                            "        (i32.add (global.get \$heap)" +
                            " (i32.sub (local.get \$align) (i32.const 1)))\n" +
                            "        (i32.xor\n" +
                            "          (i32.sub (local.get \$align) (i32.const 1))\n" +
                            "          (i32.const -1))))\n" +
                            "    (global.set \$heap\n" +
                            "      (i32.add (local.get \$ptr) (local.get" +
                            " \$new_size)))\n" +
                            "    (local.get \$ptr))\n" +
                            "  (func \$run (result i64)\n" +
                            "    (call \$args (i32.const 64))\n" +
                            "    (if (i32.ne (i32.load (i32.const 68))" +
                            " (i32.const 3)) (then unreachable))\n" +
                            "    (call \$env (i32.const 80))\n" +
                            "    (if (i32.ne (i32.load (i32.const 84))" +
                            " (i32.const 1)) (then unreachable))\n" +
                            "    (call \$cwd (i32.const 96))\n" +
                            "    (if (i32.ne (i32.load8_u (i32.const 96))" +
                            " (i32.const 1)) (then unreachable))\n" +
                            "    (if (i32.ne (i32.load (i32.const 104))" +
                            " (i32.const 5)) (then unreachable))\n" +
                            "    (call \$system_now (i32.const 112))\n" +
                            "    (if (i64.ne (i64.load (i32.const 112))" +
                            " (i64.const 1700000000)) (then unreachable))\n" +
                            "    (if (i64.ne (call \$system_resolution)" +
                            " (i64.const 123)) (then unreachable))\n" +
                            "    (if (i64.ne (call \$monotonic_now)" +
                            " (i64.const 123)) (then unreachable))\n" +
                            "    (if (i64.ne (call \$monotonic_resolution)" +
                            " (i64.const 456)) (then unreachable))\n" +
                            "    (if (i32.ne (call \$monotonic_wait_for (i64.const 0))" +
                            " (i32.const 0)) (then unreachable))\n" +
                            "    (call \$random_bytes (i64.const 4) (i32.const 128))\n" +
                            "    (if (i32.ne (i32.load (i32.const 132))" +
                            " (i32.const 4)) (then unreachable))\n" +
                            "    (call \$seed (i32.const 144))\n" +
                            "    (if (i64.ne (i64.load (i32.const 144))" +
                            " (i64.const 11)) (then unreachable))\n" +
                            "    (if (i64.ne (i64.load (i32.const 152))" +
                            " (i64.const 12)) (then unreachable))\n" +
                            "    (i64.const 7))\n" +
                            "  (export \"api.run\" (func \$run))\n" +
                            ")\n"
                    )
                )
                .withWasiPreview3(wasi)
                .build()

        assertEquals(7L, plugin.call("api.run"))
    }

    @Test
    fun linksFilesystemPreopensAndDescriptorsReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val tempDir = Files.createTempDirectory("krwa-wasi3-filesystem")
        val probe = tempDir.resolve("probe.txt")
        try {
            val witPackage =
                WitPackage.parse(
                    """
                    package example:wasi3-filesystem;

                    world plugin {
                      import wasi:filesystem/types@$version;
                      import wasi:filesystem/preopens@$version;
                      export api;
                    }

                    interface api {
                      run: func() -> u32;
                    }

                    package wasi:clocks@$version {
                      interface system-clock {
                        record instant {
                          seconds: s64,
                          nanoseconds: u32,
                        }
                      }
                    }

                    package wasi:filesystem@$version {
                      interface types {
                        use wasi:clocks/system-clock@$version.{instant};

                        type filesize = u64;
                        type link-count = u64;

                        variant descriptor-type {
                          directory,
                          regular-file,
                          symbolic-link,
                          other(option<string>),
                        }

                        flags descriptor-flags {
                          read,
                          write,
                          file-integrity-sync,
                          data-integrity-sync,
                          requested-write-sync,
                          mutate-directory,
                        }

                        record descriptor-stat {
                          %type: descriptor-type,
                          link-count: link-count,
                          size: filesize,
                          data-access-timestamp: option<instant>,
                          data-modification-timestamp: option<instant>,
                          status-change-timestamp: option<instant>,
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

                        record directory-entry {
                          %type: descriptor-type,
                          name: string,
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

                        record metadata-hash-value {
                          lower: u64,
                          upper: u64,
                        }

                        resource descriptor {
                          open-at: async func(
                            path-flags: path-flags,
                            path: string,
                            open-flags: open-flags,
                            %flags: descriptor-flags,
                          ) -> result<descriptor, error-code>;
                          stat: async func() -> result<descriptor-stat, error-code>;
                          metadata-hash: async func() -> result<metadata-hash-value, error-code>;
                          read-directory: func() -> tuple<stream<directory-entry>, future<result<_, error-code>>>;
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
            val wasi =
                WasiPreview3.builder().withPreopenedDirectory("/", tempDir.toString()).build()
            val plugin =
                WasmPlugin.builder(witPackage)
                    .withModule(
                        Wat2Wasm.parse(
                            "(module\n" +
                                "  (import \"wasi:filesystem/preopens@$version\"" +
                                " \"get-directories\" (func \$get_directories (param" +
                                " i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[async-lower][method]descriptor.open-at\" (func \$open_at" +
                                " (param i32) (param i32) (result i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[async-lower][method]descriptor.stat\" (func \$stat (param" +
                                " i32) (param i32) (result i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[async-lower][method]descriptor.metadata-hash\" (func \$hash" +
                                " (param i32) (param i32) (result i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[method]descriptor.read-directory\" (func \$read_dir" +
                                " (param i32) (param i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[async-lower][stream-read-0][method]descriptor.read-directory\"" +
                                " (func \$read_dir_stream (param i32 i32 i32) (result i32)))\n" +
                                "  (memory (export \"memory\") 1)\n" +
                                "  (global \$heap (mut i32) (i32.const 256))\n" +
                                "  (data (i32.const 16) \"probe.txt\")\n" +
                                "  (func (export \"canonical_abi_realloc\")\n" +
                                "    (param \$old i32) (param \$old_size i32)\n" +
                                "    (param \$align i32) (param \$new_size i32)\n" +
                                "    (result i32)\n" +
                                "    (local \$ptr i32)\n" +
                                "    (local.set \$ptr\n" +
                                "      (i32.and\n" +
                                "        (i32.add (global.get \$heap)" +
                                " (i32.sub (local.get \$align) (i32.const 1)))\n" +
                                "        (i32.xor\n" +
                                "          (i32.sub (local.get \$align) (i32.const 1))\n" +
                                "          (i32.const -1))))\n" +
                                "    (global.set \$heap\n" +
                                "      (i32.add (local.get \$ptr) (local.get" +
                                " \$new_size)))\n" +
                                "    (local.get \$ptr))\n" +
                                "  (func \$run (result i32)\n" +
                                "    (local \$base i32)\n" +
                                "    (local \$file i32)\n" +
                                "    (local \$dir_stream i32)\n" +
                                "    (local \$status i32)\n" +
                                "    (call \$get_directories (i32.const 48))\n" +
                                "    (if (i32.ne (i32.load (i32.const 52))" +
                                " (i32.const 1)) (then unreachable))\n" +
                                "    (local.set \$base (i32.load (i32.load (i32.const 48))))\n" +
                                "    (i32.store (i32.const 32) (local.get \$base))\n" +
                                "    (i32.store8 (i32.const 36) (i32.const 0))\n" +
                                "    (i32.store (i32.const 40) (i32.const 16))\n" +
                                "    (i32.store (i32.const 44) (i32.const 9))\n" +
                                "    (i32.store8 (i32.const 48) (i32.const 1))\n" +
                                "    (i32.store8 (i32.const 49) (i32.const 3))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$open_at\n" +
                                "        (i32.const 32)\n" +
                                "        (i32.const 64)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))" +
                                " (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                                " (i32.const 0)) (then unreachable))\n" +
                                "    (local.set \$file (i32.load (i32.const 68)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$stat (local.get \$file) (i32.const 96)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))" +
                                " (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 96))" +
                                " (i32.const 0)) (then unreachable))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$hash (local.get \$file) (i32.const 176)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))" +
                                " (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 176))" +
                                " (i32.const 0)) (then unreachable))\n" +
                                "    (call \$read_dir (local.get \$base) (i32.const 224))\n" +
                                "    (if (i32.eqz (i32.load (i32.const 224)))" +
                                " (then unreachable))\n" +
                                "    (if (i32.eqz (i32.load (i32.const 228)))" +
                                " (then unreachable))\n" +
                                "    (local.set \$dir_stream (i32.load (i32.const 224)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$read_dir_stream\n" +
                                "        (local.get \$dir_stream)\n" +
                                "        (i32.const 304)\n" +
                                "        (i32.const 1)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 17))" +
                                " (then unreachable))\n" +
                                "    (if (i32.ne (i32.load (i32.const 324))" +
                                " (i32.const 9)) (then unreachable))\n" +
                                "    (local.get \$file))\n" +
                                "  (export \"api.run\" (func \$run))\n" +
                                ")\n"
                        )
                    )
                    .withWasiPreview3(wasi)
                    .build()

            assertTrue((plugin.call("api.run") as Long) > 0)
            assertTrue(Files.exists(probe))
        } finally {
            Files.deleteIfExists(probe)
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    fun exposesFilesystemReadViaStreamBytesToHost() {
        val version = WasiPreview3.DEFAULT_VERSION
        val tempDir = Files.createTempDirectory("krwa-wasi3-filesystem-stream")
        val source = tempDir.resolve("hello.txt")
        try {
            Files.writeString(source, "hello", StandardCharsets.UTF_8)
            val witPackage =
                WitPackage.parse(
                    """
                    package example:wasi3-filesystem-stream;

                    world plugin {
                      import wasi:filesystem/types@$version;
                      import wasi:filesystem/preopens@$version;
                      export api;
                    }

                    interface api {
                      read: func() -> stream<u8>;
                    }

                    package wasi:filesystem@$version {
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
            val wasi =
                WasiPreview3.builder().withPreopenedDirectory("/", tempDir.toString()).build()
            val plugin =
                WasmPlugin.builder(witPackage)
                    .withModule(
                        Wat2Wasm.parse(
                            "(module\n" +
                                "  (import \"wasi:filesystem/preopens@$version\"" +
                                " \"get-directories\" (func \$get_directories (param" +
                                " i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[method]descriptor.open-at\" (func \$open_at" +
                                " (param i32) (param i32) (param i32) (param i32)" +
                                " (param i32) (param i32) (result i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[async-lower][future-read-0][method]descriptor.open-at\"" +
                                " (func \$open_at_future_read (param i32 i32) (result i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[method]descriptor.read-via-stream\" (func" +
                                " \$read_stream (param i32) (param i64) (param i32)))\n" +
                                "  (memory (export \"memory\") 1)\n" +
                                "  (global \$heap (mut i32) (i32.const 256))\n" +
                                "  (data (i32.const 16) \"hello.txt\")\n" +
                                "  (func (export \"canonical_abi_realloc\")\n" +
                                "    (param \$old i32) (param \$old_size i32)\n" +
                                "    (param \$align i32) (param \$new_size i32)\n" +
                                "    (result i32)\n" +
                                "    (local \$ptr i32)\n" +
                                "    (local.set \$ptr\n" +
                                "      (i32.and\n" +
                                "        (i32.add (global.get \$heap)" +
                                " (i32.sub (local.get \$align) (i32.const 1)))\n" +
                                "        (i32.xor\n" +
                                "          (i32.sub (local.get \$align) (i32.const 1))\n" +
                                "          (i32.const -1))))\n" +
                                "    (global.set \$heap\n" +
                                "      (i32.add (local.get \$ptr) (local.get" +
                                " \$new_size)))\n" +
                                "    (local.get \$ptr))\n" +
                                "  (func \$read (result i32)\n" +
                                "    (local \$base i32)\n" +
                                "    (local \$file i32)\n" +
                                "    (local \$stream i32)\n" +
                                "    (local \$future i32)\n" +
                                "    (local \$status i32)\n" +
                                "    (call \$get_directories (i32.const 48))\n" +
                                "    (local.set \$base (i32.load (i32.load (i32.const 48))))\n" +
                                "    (local.set \$future\n" +
                                "      (call \$open_at\n" +
                                "      (local.get \$base)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 16)\n" +
                                "      (i32.const 9)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 1)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$open_at_future_read (local.get \$future)" +
                                " (i32.const 64)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                                " (i32.const 0)) (then unreachable))\n" +
                                "    (local.set \$file (i32.load (i32.const 68)))\n" +
                                "    (call \$read_stream (local.get \$file) (i64.const 0)" +
                                " (i32.const 96))\n" +
                                "    (local.set \$stream (i32.load (i32.const 96)))\n" +
                                "    (local.set \$future (i32.load (i32.const 100)))\n" +
                                "    (local.get \$stream))\n" +
                                "  (export \"api.read\" (func \$read))\n" +
                                ")\n"
                        )
                    )
                    .withWasiPreview3(wasi)
                    .build()

            val stream = plugin.call("api.read") as WitStream<*>

            assertArrayEquals("hello".toByteArray(StandardCharsets.UTF_8), wasi.streamBytes(stream))
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    fun readsFilesystemByteStreamThroughCanonicalIntrinsicReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val tempDir = Files.createTempDirectory("krwa-wasi3-filesystem-canonical-stream")
        val source = tempDir.resolve("hello.txt")
        try {
            Files.writeString(source, "hello", StandardCharsets.UTF_8)
            val witPackage =
                WitPackage.parse(
                    """
                    package example:wasi3-filesystem-canonical-stream;

                    world plugin {
                      import wasi:filesystem/types@$version;
                      import wasi:filesystem/preopens@$version;
                      export api;
                    }

                    interface api {
                      run: func() -> u32;
                    }

                    package wasi:filesystem@$version {
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
                        Wat2Wasm.parse(
                            "(module\n" +
                                "  (import \"wasi:filesystem/preopens@$version\"" +
                                " \"get-directories\" (func \$get_directories (param" +
                                " i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[method]descriptor.open-at\" (func \$open_at" +
                                " (param i32) (param i32) (param i32) (param i32)" +
                                " (param i32) (param i32) (result i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[async-lower][future-read-0][method]descriptor.open-at\"" +
                                " (func \$open_at_future_read (param i32 i32) (result i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[method]descriptor.read-via-stream\" (func" +
                                " \$read_stream (param i32) (param i64) (param i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[async-lower][stream-read-0][method]descriptor.read-via-stream\"" +
                                " (func \$stream_read (param i32 i32 i32) (result i32)))\n" +
                                "  (import \"wasi:filesystem/types@$version\"" +
                                " \"[async-lower][future-read-1][method]descriptor.read-via-stream\"" +
                                " (func \$future_read (param i32 i32) (result i32)))\n" +
                                "  (memory (export \"memory\") 1)\n" +
                                "  (global \$heap (mut i32) (i32.const 256))\n" +
                                "  (data (i32.const 16) \"hello.txt\")\n" +
                                "  (func (export \"canonical_abi_realloc\")\n" +
                                "    (param \$old i32) (param \$old_size i32)\n" +
                                "    (param \$align i32) (param \$new_size i32)\n" +
                                "    (result i32)\n" +
                                "    (local \$ptr i32)\n" +
                                "    (local.set \$ptr\n" +
                                "      (i32.and\n" +
                                "        (i32.add (global.get \$heap)" +
                                " (i32.sub (local.get \$align) (i32.const 1)))\n" +
                                "        (i32.xor\n" +
                                "          (i32.sub (local.get \$align) (i32.const 1))\n" +
                                "          (i32.const -1))))\n" +
                                "    (global.set \$heap\n" +
                                "      (i32.add (local.get \$ptr) (local.get" +
                                " \$new_size)))\n" +
                                "    (local.get \$ptr))\n" +
                                "  (func \$run (result i32)\n" +
                                "    (local \$base i32)\n" +
                                "    (local \$file i32)\n" +
                                "    (local \$stream i32)\n" +
                                "    (local \$future i32)\n" +
                                "    (local \$status i32)\n" +
                                "    (call \$get_directories (i32.const 48))\n" +
                                "    (local.set \$base (i32.load (i32.load (i32.const 48))))\n" +
                                "    (local.set \$future\n" +
                                "      (call \$open_at\n" +
                                "      (local.get \$base)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 16)\n" +
                                "      (i32.const 9)\n" +
                                "      (i32.const 0)\n" +
                                "      (i32.const 1)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$open_at_future_read (local.get \$future)" +
                                " (i32.const 64)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 64))" +
                                " (i32.const 0)) (then unreachable))\n" +
                                "    (local.set \$file (i32.load (i32.const 68)))\n" +
                                "    (call \$read_stream (local.get \$file) (i64.const 0)" +
                                " (i32.const 96))\n" +
                                "    (local.set \$stream (i32.load (i32.const 96)))\n" +
                                "    (local.set \$future (i32.load (i32.const 100)))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$future_read\n" +
                                "        (local.get \$future)\n" +
                                "        (i32.const 160)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                                "      (then unreachable))\n" +
                                "    (if (i32.ne (i32.load8_u (i32.const 160))" +
                                " (i32.const 0)) (then unreachable))\n" +
                                "    (local.set \$status\n" +
                                "      (call \$stream_read\n" +
                                "        (local.get \$stream)\n" +
                                "        (i32.const 128)\n" +
                                "        (i32.const 16)))\n" +
                                "    (if (i32.ne (local.get \$status) (i32.const 81))\n" +
                                "      (then unreachable))\n" +
                                "    (i32.add\n" +
                                "      (i32.add\n" +
                                "        (i32.add\n" +
                                "          (i32.add\n" +
                                "            (i32.load8_u (i32.const 128))\n" +
                                "            (i32.load8_u (i32.const 129)))\n" +
                                "          (i32.load8_u (i32.const 130)))\n" +
                                "        (i32.load8_u (i32.const 131)))\n" +
                                "      (i32.load8_u (i32.const 132))))\n" +
                                "  (export \"api.run\" (func \$run))\n" +
                                ")\n"
                        )
                    )
                    .withWasiPreview3(
                        WasiPreview3.builder()
                            .withPreopenedDirectory("/", tempDir.toString())
                            .build()
                    )
                    .build()

            assertEquals(532L, plugin.call("api.run"))
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    fun supportsCanonicalByteStreamIntrinsicsReleaseCandidateWithoutJson() {
        val version = WasiPreview3.DEFAULT_VERSION
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi3-stream-intrinsics;

                world plugin {
                  export api;
                }

                interface api {
                  run: func() -> u32;
                }

                package wasi:cli@$version {
                  interface stdin {
                    read-via-stream: func() -> tuple<stream<u8>, future<result>>;
                  }
                }
                """
                    .trimIndent()
            )
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"wasi:cli/stdin@$version\"" +
                            " \"[stream-new-0]read-via-stream\" (func" +
                            " \$stream_new (result i64)))\n" +
                            "  (import \"wasi:cli/stdin@$version\"" +
                            " \"[async-lower][stream-write-0]read-via-stream\" (func" +
                            " \$stream_write (param i32 i32 i32) (result i32)))\n" +
                            "  (import \"wasi:cli/stdin@$version\"" +
                            " \"[stream-drop-writable-0]read-via-stream\" (func" +
                            " \$drop_writable (param i32)))\n" +
                            "  (import \"wasi:cli/stdin@$version\"" +
                            " \"[async-lower][stream-read-0]read-via-stream\" (func" +
                            " \$stream_read (param i32 i32 i32) (result i32)))\n" +
                            "  (import \"wasi:cli/stdin@$version\"" +
                            " \"[stream-drop-readable-0]read-via-stream\" (func" +
                            " \$drop_readable (param i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (data (i32.const 32) \"abc\")\n" +
                            "  (func \$run (result i32)\n" +
                            "    (local \$pair i64)\n" +
                            "    (local \$reader i32)\n" +
                            "    (local \$writer i32)\n" +
                            "    (local \$write_status i32)\n" +
                            "    (local \$read_status i32)\n" +
                            "    (local.set \$pair (call \$stream_new))\n" +
                            "    (local.set \$reader (i32.wrap_i64 (local.get \$pair)))\n" +
                            "    (local.set \$writer\n" +
                            "      (i32.wrap_i64\n" +
                            "        (i64.shr_u (local.get \$pair) (i64.const 32))))\n" +
                            "    (local.set \$write_status\n" +
                            "      (call \$stream_write\n" +
                            "        (local.get \$writer)\n" +
                            "        (i32.const 32)\n" +
                            "        (i32.const 3)))\n" +
                            "    (if (i32.ne (local.get \$write_status) (i32.const 48))\n" +
                            "      (then unreachable))\n" +
                            "    (call \$drop_writable (local.get \$writer))\n" +
                            "    (local.set \$read_status\n" +
                            "      (call \$stream_read\n" +
                            "        (local.get \$reader)\n" +
                            "        (i32.const 64)\n" +
                            "        (i32.const 8)))\n" +
                            "    (if (i32.ne (local.get \$read_status) (i32.const 49))\n" +
                            "      (then unreachable))\n" +
                            "    (call \$drop_readable (local.get \$reader))\n" +
                            "    (i32.add\n" +
                            "      (i32.add\n" +
                            "        (i32.load8_u (i32.const 64))\n" +
                            "        (i32.load8_u (i32.const 65)))\n" +
                            "      (i32.load8_u (i32.const 66))))\n" +
                            "  (export \"api.run\" (func \$run))\n" +
                            ")\n"
                    )
                )
                .withWasiPreview3(WasiPreview3.builder().build())
                .build()

        assertEquals(294L, plugin.call("api.run"))
    }

    @Test
    fun supportsCanonicalFutureIntrinsicsReleaseCandidateWithoutJson() {
        val witPackage =
            WitPackage.parse(
                """
                package example:wasi3-future-intrinsics;

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
        val wasi = WasiPreview3.builder().build()
        val plugin =
            WasmPlugin.builder(witPackage)
                .withModule(
                    Wat2Wasm.parse(
                        "(module\n" +
                            "  (import \"plugin\" \"[future-new-0]seed\" (func" +
                            " \$future_new (result i64)))\n" +
                            "  (import \"plugin\" \"[async-lower][future-write-0]seed\"" +
                            " (func \$future_write (param i32 i32) (result i32)))\n" +
                            "  (import \"plugin\" \"[async-lower][future-read-0]seed\"" +
                            " (func \$future_read (param i32 i32) (result i32)))\n" +
                            "  (import \"plugin\" \"[future-drop-writable-0]seed\"" +
                            " (func \$drop_writable (param i32)))\n" +
                            "  (import \"plugin\" \"[future-drop-readable-0]seed\"" +
                            " (func \$drop_readable (param i32)))\n" +
                            "  (memory (export \"memory\") 1)\n" +
                            "  (func \$run (result i32)\n" +
                            "    (local \$pair i64)\n" +
                            "    (local \$reader i32)\n" +
                            "    (local \$writer i32)\n" +
                            "    (local \$status i32)\n" +
                            "    (local.set \$pair (call \$future_new))\n" +
                            "    (local.set \$reader (i32.wrap_i64 (local.get \$pair)))\n" +
                            "    (local.set \$writer\n" +
                            "      (i32.wrap_i64\n" +
                            "        (i64.shr_u (local.get \$pair) (i64.const 32))))\n" +
                            "    (i32.store (i32.const 32) (i32.const 123456))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$future_write\n" +
                            "        (local.get \$writer)\n" +
                            "        (i32.const 32)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                            "      (then unreachable))\n" +
                            "    (call \$drop_writable (local.get \$writer))\n" +
                            "    (local.set \$status\n" +
                            "      (call \$future_read\n" +
                            "        (local.get \$reader)\n" +
                            "        (i32.const 64)))\n" +
                            "    (if (i32.ne (local.get \$status) (i32.const 0))\n" +
                            "      (then unreachable))\n" +
                            "    (call \$drop_readable (local.get \$reader))\n" +
                            "    (i32.load (i32.const 64)))\n" +
                            "  (export \"api.run\" (func \$run))\n" +
                            ")\n"
                    )
                )
                .withHostImport("plugin", "seed") { wasi.completedFuture(0L) }
                .withWasiPreview3(wasi)
                .build()

        assertEquals(123456L, plugin.call("api.run"))
    }

    private fun socketAddressPort(value: Any?): Int {
        val variant = value as WitValue.Variant
        val payload = variant.value() as Map<*, *>
        return (payload["port"] as Number).toInt()
    }

    @Suppress("UNCHECKED_CAST")
    private fun assertTrailerResult(
        result: WitResult<Map<String, List<ByteArray>>?, Any?>,
        name: String,
        value: String,
    ) {
        when (result) {
            is WitResult.Ok<*, *> ->
                assertTrailerMap(result.value() as Map<String, List<ByteArray>>?, name, value)
            is WitResult.Err<*, *> ->
                throw AssertionError("expected HTTP trailers, got error ${result.value()}")
        }
    }

    private fun trailerFutureSnapshot(
        wasi: WasiPreview3,
        future: WitFuture<*>,
    ): Map<String, List<ByteArray>>? {
        return when (val result = wasi.futureValue(future)) {
            is WitResult.Ok<*, *> -> {
                val fields = result.value() ?: return null
                wasi.httpFieldsSnapshot(fields as WitResource<*>)
            }
            is WitResult.Err<*, *> ->
                throw AssertionError("expected HTTP trailers future, got error ${result.value()}")
            else -> throw AssertionError("expected HTTP trailers future result, got $result")
        }
    }

    private fun assertTrailerMap(
        trailers: Map<String, List<ByteArray>>?,
        name: String,
        value: String,
    ) {
        assertTrue(trailers != null, "expected HTTP trailers")
        val values = trailers!![name.lowercase()]
        assertTrue(values != null, "expected trailer $name")
        assertArrayEquals(value.toByteArray(StandardCharsets.ISO_8859_1), values!![0])
    }
}
