@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

val krwaVersion = "0.3.0-SNAPSHOT"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget("25"))
        }
    }

    wasmWasi {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        val wasmWasiMain by getting {
            dependencies {
                implementation(libs.kotlinxCoroutinesCore)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("uk.shusek.krwa:annotations:$krwaVersion")
                implementation("uk.shusek.krwa:runtime:$krwaVersion")
                implementation("uk.shusek.krwa:wasi:$krwaVersion")
                implementation("uk.shusek.krwa:wasi-preview3:$krwaVersion")
                implementation("uk.shusek.krwa:wasm:$krwaVersion")
                implementation("uk.shusek.krwa:wasm-tools:$krwaVersion")
                implementation("uk.shusek.krwa:component-model:$krwaVersion")
            }
        }
    }
}

fun locateKotlinWasiExecutable(): File {
    val wasmFiles =
        layout.buildDirectory.asFile.get().walkTopDown().filter {
            it.isFile && it.extension == "wasm" && "wasmWasi" in it.invariantSeparatorsPath
        }
            .toList()
    require(wasmFiles.size == 1) {
        "Expected exactly one wasmWasi executable, found: ${wasmFiles.joinToString()}"
    }
    return wasmFiles.single()
}

val runShowcaseHost by tasks.registering(JavaExec::class) {
    group = "verification internals"
    description = "Builds the Kotlin/WASI guest and runs the Kotlin Runtime Web Assembly runtime showcase host."
    dependsOn("jvmJar", "compileProductionExecutableKotlinWasmWasi")
    mainClass.set("uk.shusek.krwa.sample.ShowcaseKt")
    classpath(tasks.named("jvmJar"), configurations.named("jvmRuntimeClasspath"))
    doFirst {
        systemProperty("krwa.sample.kotlinWasiWasm", locateKotlinWasiExecutable().absolutePath)
    }
}

val runShowcase by tasks.registering {
    group = "verification"
    description = "Runs the standalone sample showcase."
    dependsOn(runShowcaseHost)
}

tasks.register("checkSample") {
    group = "verification"
    description = "Runs the standalone sample verification."
    dependsOn(runShowcase)
}
