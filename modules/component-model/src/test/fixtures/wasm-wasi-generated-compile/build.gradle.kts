plugins {
  kotlin("multiplatform") version "2.4.0"
}

kotlin {
  wasmWasi {
    binaries.executable()
  }
}
