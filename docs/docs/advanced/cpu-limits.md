---
sidebar_position: 1
sidebar_label: CPU
title: CPU
---
# Limiting CPU usage

:::warning[Security Consideration]
Wasm modules can contain infinite loops. When running untrusted code, always set execution timeouts via thread interruption or an ExecutorService with a deadline. Without timeouts, a malicious module can consume 100% CPU indefinitely.
:::

Often, when running untrusted user code in our infrastructure, we want to have strong guarantees around the termination of the program.

To achieve this result there are, currently, two mechanisms in Kotlin Runtime Web Assembly:

## Interrupts

Wasm modules executed using Kotlin Runtime Web Assembly honour the carrier thread interruption mechanism, thus you can leverage it to implement absolute timeouts:

```bash
curl https://raw.githubusercontent.com/Shusek/kotlin-runtime-web-assembly/main/testing/wasm-corpus/src/main/resources/compiled/infinite-loop.c.wasm > infinite-loop.wasm
```

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT
//DEPS uk.shusek.krwa:runtime-jvm:0.3.0-SNAPSHOT

docs.FileOps.copyFromWasmCorpus("infinite-loop.c.wasm", "infinite-loop.wasm");
```
-->

Build and instantiate this infinite loop module:

```java
import uk.shusek.krwa.runtime.ExportFunction;
import uk.shusek.krwa.runtime.Instance;
import uk.shusek.krwa.wasm.Parser;

Instance instance = Instance.builder(Parser.parse(new File("./infinite-loop.wasm"))).build();
ExportFunction function = instance.export("run");
```

Now you can execute the Wasm module and control the execution using plain interrupts, with the low level Thread API:

```java
var thread = new Thread() {
    @Override
    public void run() {
        function.apply();
    }
};
thread.start();
Thread.sleep(200);
thread.interrupt();
```

Or using an `ExecutorService`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

ExecutorService service = Executors.newSingleThreadExecutor();
var future = service.submit(() -> function.apply());
try {
  future.get(100, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    // handle the failure
}
```

## [unsafe] Execution Listener

The Kotlin Runtime Web Assembly interpreter exposes an unsafe listener to granularly control the Wasm Modules execution.
Using it is extremely risky as the code will be evaluated for each and every Wasm instruction, use it with extreme caution.

```java
var instance =
    Instance.builder(Parser.parse(new File("./infinite-loop.wasm"))).withUnsafeExecutionListener(
        (instruction, stack) ->
            System.out.println("current instruction: " + instruction + ", stack size: " + stack.size())).build();
```

<!--
```java
docs.FileOps.writeResult("docs/advanced", "cpu-limits.md.result", "empty");
```
-->
