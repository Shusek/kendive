---
sidebar_position: 2
sidebar_label: Build time Compilation
title: Build Time Compilation
---
## Overview

:::warning[Security Consideration]
The compiler translates Wasm to JVM bytecode without post-compilation verification. Only compile Wasm modules you trust. See [Security Model](/docs/security/overview).
:::

The build time compiler backend is a drop-in replacement for the interpreter, and it passes 100% of the same 
spec tests that the interpreter already supports.

This compiler translates the WASM instructions to Java bytecode and stores them as `.class` files
that you package in your application.  The resulting code is usually expected to evaluate (much) faster and 
consume less memory than if it was interpreted.

The build time compiler has several advantages over the [Runtime Compiler](runtime-compiler.md) such as: 

- improved instance initialization time: the translation occurs at build time
- no reflection needed: easier to use with `native-image`
- fewer runtime dependencies: asm is only needed at build time
- distribute Wasm modules as self-contained jars: making it a convenient way to distribute software that was not originally meant to run on the Java platform

You can use the compiler at build time via the CLI or a Gradle integration.

### Interpreter Fall Back

The WASM to bytecode compiler translates each WASM function into JVM method.  Occasionally you will find WASM module where functions are bigger than the maximum method size allowed by the JVM.  In these rare cases, we fall back to executing these large functions in the interpreter.  

Since interpreted functions have worse performance, we want to make sure you are aware this is happening so the build time compiler will FAIL if it finds any functions that are too large.  The build tool will produce a message that contains text like:

```text
WASM function size exceeds the Java method size limits and cannot be compiled to Java bytecode. It can only be run in the interpreter. Either reduce the size of the function or enable the interpreter fallback mode: WASM function index: 3938
```

If this happens you can configure your build tool, to just issue warning messages, or to be silent.  Another way to silence the message is to configure the build too with an explicit list of functions that should be interpreted. Typically, you obtain the list of the functions by running the compiler once with `interpreterFallback` set to `WARN`

## Using Generated Modules

The build-time compiler emits a module class with `load` and `create` helpers.
Use the generated module by configuring the `MachineFactory`:

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT
//DEPS uk.shusek.krwa:runtime-jvm:0.3.0-SNAPSHOT

import uk.shusek.krwa.wasm.Parser;
import uk.shusek.krwa.wasm.WasmModule;
import uk.shusek.krwa.runtime.Instance;
import uk.shusek.krwa.runtime.Machine;
import uk.shusek.krwa.runtime.InterpreterMachine;

docs.FileOps.copyFromWasmCorpus("count_vowels.rs.wasm", "your.wasm");

// mocking up the generated code
class Add {

    public static WasmModule load() {
      return Parser.parse(new File("your.wasm"));
    }

    public static Machine create(Instance instance) {
        return new InterpreterMachine(instance);
    }

}
```
-->

```java
import uk.shusek.krwa.runtime.Instance;

// load the bundled module
var module = Add.load();

// instantiate the module with the pre-compiled code
var instance = Instance.builder(module).
        withMachineFactory(Add::create).
        build();
```

### Generating Module Exports and Imports

The build-time compiler can also generate typed Java wrappers for a module's exports and imports,
eliminating the need for the [`@WasmModuleInterface` annotation](../annotations/index.md#wasmmoduleinterface) and the annotation processor setup.

Set the `moduleInterface` parameter when invoking the compiler:

```text
wasmFile = src/main/resources/demo.wasm
name = org.acme.wasm.DemoModule
moduleInterface = org.acme.wasm.Demo
```

This generates `Demo_ModuleExports` and `Demo_ModuleImports` classes alongside the compiled module.
You can then use them directly in your code without any annotation:

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT
//DEPS uk.shusek.krwa:runtime-jvm:0.3.0-SNAPSHOT

import uk.shusek.krwa.wasm.Parser;
import uk.shusek.krwa.wasm.WasmModule;
import uk.shusek.krwa.runtime.Instance;
import uk.shusek.krwa.runtime.Machine;
import uk.shusek.krwa.runtime.InterpreterMachine;

docs.FileOps.copyFromWasmCorpus("count_vowels.rs.wasm", "demo.wasm");

// mocking up the generated code
class DemoModule {

    public static WasmModule load() {
      return Parser.parse(new File("demo.wasm"));
    }

    public static Machine create(Instance instance) {
        return new InterpreterMachine(instance);
    }

}

class Demo_ModuleExports {
    public Demo_ModuleExports(Instance instance) {}
}
```
-->

```java
var instance = Instance.builder(DemoModule.load()).
        withMachineFactory(DemoModule::create).
        build();
var exports = new Demo_ModuleExports(instance);
```

### Compile Parameters

```text
    <wasm file>
      Positional argument. The Wasm module to compile.

    --prefix (Default: uk.shusek.krwa.Wasm)
      The package and class-name prefix to use for generated resources.

    --source-dir (Default: .)
      The target folder for generated source files.

    --class-dir (Default: .)
      The target folder for generated class files.

    --wasm-dir (Default: .)
      The target folder for the stripped meta Wasm module.

    --interpreter-fallback (Default: FAIL)
      Action to take if the compiler needs to use the interpreter because a
      function is too big.

    --interpreted-functions
      The indexes of functions that should be interpreted, separated by commas.

    --module-interface
      Fully qualified name of the user's class for which to generate
      _ModuleExports and _ModuleImports wrapper classes. When set, eliminates
      the need for @WasmModuleInterface annotation and the annotation processor.
```

## Using Gradle [community]

Gradle users can leverage the [wasm2class-gradle-plugin](https://github.com/illarionov/wasm2class-gradle-plugin),
a third-party plugin that runs the AoT compiler at build time and enables the
use of pre-compiled Wasm code in Java, Kotlin, and Android projects.

To set it up, make sure MavenCentral is listed as a repository in the `pluginManagement` block of your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Configuration example in the `build.gradle.kts` file for the module:

```kotlin
plugins {
    id("at.released.wasm2class.plugin") version "<latest version>"
}

wasm2class {
    modules {
        // Target package for the generated classes
        targetPackage = "org.acme.wasm"
        //  Use "Add" as the base name for generated classes
        create("Add") {
            // Translate `add.wasm` into bytecode
            wasm = file("src/main/resources/add.wasm")
        }
    }
}
```

This generates the class `org.acme.wasm.Add`, which you can use to instantiate
the module just like shown earlier.

<!--
```java
docs.FileOps.writeResult("docs/execution", "build-time-compiler.md.result", "empty");
```
-->
