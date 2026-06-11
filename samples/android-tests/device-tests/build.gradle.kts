plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "uk.shusek.krwa.runtimeTests"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val krwaDimension = "krwaDimension"
    flavorDimensions += krwaDimension
    productFlavors {
        create("runtime") { dimension = krwaDimension }
        // add future modules similar to the runtime configuration above.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

    packaging {
        resources {
            pickFirsts.add("logging.properties")
            pickFirsts.add("THIRD-PARTY.txt")
            excludes.add("META-INF/jpms.args")
        }
    }
}

dependencies {
    // common dependencies can be added here
    // if you need to add a dependency on a specific module, you can use
    // "androidTest<productFlavorName>Implementation"(<your dependency>)
    // e.g.
    // "androidTestRuntimeImplementation"(libs.krwa.runtime)
    androidTestImplementation(libs.krwa.wasi)
    androidTestImplementation(libs.krwa.runtime)
    androidTestImplementation(libs.krwa.wasm)
    androidTestImplementation(libs.krwa.wasmCorpus)
    androidTestImplementation(libs.junit.jupiter.api)
}
