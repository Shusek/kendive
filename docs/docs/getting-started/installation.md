---
sidebar_position: 1
sidebar_label: Installation
title: Installation
---
# Installation

Every push to `main` publishes the current Kotlin Runtime Web Assembly
`0.3.0-SNAPSHOT` artifacts to a public GitHub Pages Maven repository. Use snapshots
for development builds. Use a real release version from Maven Central once a
release is published.

## Gradle Snapshot

Add the public KRWA Maven repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://shusek.github.io/kotlin-runtime-web-assembly/maven")
    }
}
```

Then depend on the modules you need:

```kotlin
// build.gradle.kts
val krwaVersion = "0.3.0-SNAPSHOT"

dependencies {
    implementation(platform("uk.shusek.krwa:bom:$krwaVersion"))
    implementation("uk.shusek.krwa:runtime")
    implementation("uk.shusek.krwa:wasm")
    implementation("uk.shusek.krwa:wasi")
    implementation("uk.shusek.krwa:component-model")
}
```

## Gradle Composite Build

For local KRWA changes that are not committed yet, keep KRWA checked out next to
your application and point Gradle at that checkout:

```kotlin
// settings.gradle.kts
includeBuild("../kotlin-runtime-web-assembly")
```

```kotlin
// build.gradle.kts
val krwaVersion = "0.3.0-SNAPSHOT"

dependencies {
    implementation("uk.shusek.krwa:runtime:$krwaVersion")
    implementation("uk.shusek.krwa:wasm:$krwaVersion")
    implementation("uk.shusek.krwa:wasi:$krwaVersion")
    implementation("uk.shusek.krwa:component-model:$krwaVersion")
}
```

Adjust the `includeBuild` path to your local checkout.

## Local Gradle Publish

Publish an uncommitted checkout to your local Gradle dependency repository:

```shell
git clone https://github.com/Shusek/kotlin-runtime-web-assembly.git
cd kotlin-runtime-web-assembly
./gradlew publishToMavenLocal
```

Then use the local snapshot from Gradle:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

val krwaVersion = "0.3.0-SNAPSHOT"

dependencies {
    implementation(platform("uk.shusek.krwa:bom:$krwaVersion"))
    implementation("uk.shusek.krwa:runtime")
    implementation("uk.shusek.krwa:wasm")
    implementation("uk.shusek.krwa:wasi")
    implementation("uk.shusek.krwa:component-model")
}
```

## Public Releases

For public releases, use a real released version from Maven Central instead of
`0.3.0-SNAPSHOT`, and do not enable the snapshot or `mavenLocal()`
repositories unless you intentionally want snapshots to override published
artifacts.

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT

docs.FileOps.writeResult("docs/getting-started", "installation.md.result", "empty");
```
-->
