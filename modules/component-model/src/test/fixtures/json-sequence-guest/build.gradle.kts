plugins {
  kotlin("multiplatform") version "2.4.0"
  kotlin("plugin.serialization") version "2.4.0"
}

kotlin {
  wasmWasi {
    binaries.executable()
  }
  sourceSets {
    wasmWasiMain.dependencies {
      implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.0")
      implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
      implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-io:1.11.0")
    }
  }
}
