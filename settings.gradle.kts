pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "krwa"

include(":annotations:annotations")
include(":annotations:processor")
include(":bom")
include(":build-time-compiler")
include(":build-time-compiler-cli")
include(":cli")
include(":codegen")
include(":compiler")
include(":compiler-tests")
include(":component-model")
include(":dircache")
include(":docs-lib")
include(":fuzz")
include(":jmh")
include(":ios-runtime-smoke")
include(":log")
include(":machine-tests")
include(":nightly-testsuite")
include(":runtime")
include(":runtime-tests")
include(":simd")
include(":test-gen-lib")
include(":wabt")
include(":wasi")
include(":wasi-preview3")
include(":wasi-test-gen")
include(":wasi-tests")
include(":wasm")
include(":wasm-corpus")
include(":wasm-tools")

val projectDirectories =
    mapOf(
        ":annotations" to "modules/annotations",
        ":annotations:annotations" to "modules/annotations/annotations",
        ":annotations:processor" to "modules/annotations/processor",
        ":bom" to "modules/bom",
        ":build-time-compiler" to "tools/build-time-compiler",
        ":build-time-compiler-cli" to "tools/build-time-compiler-cli",
        ":cli" to "tools/cli",
        ":codegen" to "tools/codegen",
        ":compiler" to "tools/compiler",
        ":compiler-tests" to "testing/compiler-tests",
        ":component-model" to "modules/component-model",
        ":dircache" to "modules/dircache",
        ":docs-lib" to "modules/docs-lib",
        ":fuzz" to "testing/fuzz",
        ":jmh" to "testing/jmh",
        ":ios-runtime-smoke" to "samples/ios-runtime-smoke",
        ":log" to "modules/log",
        ":machine-tests" to "testing/machine-tests",
        ":nightly-testsuite" to "testing/nightly-testsuite",
        ":runtime" to "modules/runtime",
        ":runtime-tests" to "testing/runtime-tests",
        ":simd" to "modules/simd",
        ":test-gen-lib" to "tools/test-gen-lib",
        ":wabt" to "tools/wabt",
        ":wasi" to "modules/wasi",
        ":wasi-preview3" to "modules/wasi-preview3",
        ":wasi-test-gen" to "tools/wasi-test-gen",
        ":wasi-tests" to "testing/wasi-tests",
        ":wasm" to "modules/wasm",
        ":wasm-corpus" to "testing/wasm-corpus",
        ":wasm-tools" to "tools/wasm-tools",
    )

projectDirectories.forEach { (path, directory) ->
    project(path).projectDir = file(directory)
}
