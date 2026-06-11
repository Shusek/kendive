---
sidebar_position: 5
sidebar_label: CLI
title: CLI
---
# Install and use the CLI

:::warning[Security Consideration]
The experimental CLI uses `inheritSystem()` by default, granting the Wasm module full access to the host filesystem, environment, and stdio. Do not use it with untrusted modules in its current form.
:::

The experimental Kotlin Runtime Web Assembly CLI is available as a published artifact:

```
https://repo1.maven.org/maven2/uk/shusek/krwa/cli/<version>/cli-<version>.sh
```

you can download the latest version and use it locally by typing:

```bash
export VERSION=$(curl -sS https://api.github.com/repos/Shusek/kotlin-runtime-web-assembly/tags --header "Accept: application/json" | jq -r '.[0].name')
curl -L -o krwa https://repo1.maven.org/maven2/uk/shusek/krwa/cli-experimental/${VERSION}/cli-experimental-${VERSION}.sh
chmod a+x krwa
./krwa
```

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT

docs.FileOps.writeResult("docs/experimental", "cli.md.result", "empty");
```
-->
