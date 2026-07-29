# Bug Report

Bug Report is a NeoForge mod and integration API for creating structured,
privacy-reviewed diagnostic reports for compatible Minecraft mods.

Compatible mods describe themselves through a small embedded API. They continue
to load when Bug Report is not installed; when it is installed, Bug Report
discovers their providers deterministically through NeoForge mod metadata.

## Project status

The project is currently at the M0 architecture-prototype stage.

Implemented and executable:

- optional `bugreport-api` embedding through NeoForge Jar-in-Jar;
- compatible API version selection and deduplication;
- provider discovery through NeoForge mod metadata;
- provider ownership and constructor validation;
- deterministic duplicate rejection and failure isolation;
- dedicated-server runtime coverage and an enforced common-side source boundary;
- an example provider mod that starts with or without Bug Report installed.

Not implemented yet:

- diagnostic specification and collection callbacks;
- the production provider registry;
- report creation, review, sanitization, export, or submission;
- player commands and UI.

The current build is suitable for testing API packaging, optional installation,
side safety, and provider discovery in real mods. It is not yet a functional
player-facing reporting mod.

## Supported platform

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.227 or a compatible version accepted by the mod metadata

Bug Report is client-first, not entirely client-only. The API, provider class,
constructor, bootstrap, and discovery snapshot are common-side code. The future
reporting UI, player commands, and local-file collection are physical-client
features.

## Repository modules

- `bugreport-api` — loader-neutral contracts embedded by compatible mods;
- `bugreport-neoforge` — the installed Bug Report runtime mod;
- `example-mod` — minimal optional-integration example;
- `spike-fixtures` and `spike-runtime` — executable compatibility and failure
  scenarios.

## Building and installing the current prototype

Build and verify everything:

```powershell
.\gradlew.bat check
```

On Unix-like systems:

```bash
./gradlew check
```

The installable development JAR is produced at:

```text
bugreport-neoforge/build/libs/bugreport-neoforge-0.0.1-spike.jar
```

Copy it into the `mods` directory of a Minecraft 1.21.1 NeoForge instance.
There is no player-facing command or screen yet; successful provider discovery
is currently observable in `logs/latest.log`.

## Integrating Bug Report API into another mod

The complete reference implementation is in `example-mod`.

### 1. Make the API available to Gradle

The API is not published to a public Maven repository yet. Build the local
repository first:

```powershell
.\gradlew.bat publishSpikeApis
```

This creates:

```text
build/spike-maven
```

Add that repository to the integrating mod. For an external project, point the
path at the Bug Report checkout:

```groovy
repositories {
    maven {
        name = 'bugReportLocal'
        url = uri('C:/path/to/BugReport/build/spike-maven')
        content {
            includeGroup 'com.cybersammy.bugreport'
        }
    }
}
```

This local path is a development setup only. Do not publish a consumer build
that depends on the developer's filesystem path.

### 2. Embed the API with Jar-in-Jar

With the NeoForge ModDev plugin configured, add:

```groovy
dependencies {
    jarJar(implementation(
            'com.cybersammy.bugreport:bugreport-api:0.2.0')) {
        version {
            strictly '[0.2.0,1.0.0)'
            prefer '0.2.0'
        }
    }
}
```

The embedded range declares which compatible API version NeoForge may select
when several mods include Bug Report API. Choose a range matching the API
contract against which the provider was compiled.

Do **not** add a required dependency on the `bugreport` mod. The integrating mod
must start normally when Bug Report is absent.

### 3. Implement a provider

```java
package com.example.mymod.bugreport;

import com.cybersammy.bugreport.api.BugReportProvider;

public final class MyBugReportProvider implements BugReportProvider {
    public MyBugReportProvider() {}

    @Override
    public String providerId() {
        return "my_mod";
    }

    @Override
    public String providerVersion() {
        return "1.0.0";
    }
}
```

The provider contract currently requires:

- a public, concrete provider class;
- a public no-argument constructor;
- a stable provider ID owned by the declaring mod;
- a class belonging to the JAR/module that declares it;
- class initialization and construction safe on both physical sides;
- a cheap, side-effect-free constructor with no event registration, file I/O,
  network I/O, or diagnostic collection.

Keep the NeoForge `@Mod` entrypoint separate from the provider implementation.
Bug Report constructs the provider itself during discovery.

The default provider ID is the declaring NeoForge mod ID:

```text
my_mod
```

Additional providers from the same mod use:

```text
my_mod:client
```

The mod ID component contains 2–64 lowercase ASCII letters, digits, or
underscores and starts with a letter. The optional local component contains
1–64 characters under the same rule. Bug Report rejects uppercase, whitespace,
Unicode, punctuation, extra separators, and IDs owned by another mod; it does
not silently normalize them.

### 4. Declare the provider in NeoForge metadata

Add the provider class name to the declaring mod's block in
`META-INF/neoforge.mods.toml`:

```toml
[modproperties.my_mod]
bugreportProviders=[
    "com.example.mymod.bugreport.MyBugReportProvider"
]
```

`my_mod` must be the declaring mod ID, and the provider class must be packaged
in that mod's own module. A declaration cannot claim a provider class owned by
another mod.

A stable automatic module name is recommended:

```groovy
tasks.named('jar', Jar) {
    manifest {
        attributes 'Automatic-Module-Name': 'com.example.mymod'
    }
}
```

### 5. Verify optional integration

Test at least these installations:

1. the integrating mod without Bug Report;
2. the integrating mod with Bug Report;
3. the integrating mod on a dedicated server;
4. multiple provider mods embedding compatible API versions.

With Bug Report installed, the current prototype logs:

```text
Bug Report provider discovery completed: providers=[my_mod], diagnostics=[]
```

Provider IDs must be globally unique. If multiple providers return the same
ID, every registration for that ID is rejected. An invalid or throwing provider
does not prevent unrelated valid providers from loading.

## Direct source-level side boundaries

`bugreport-api` and common production code must not reference physical-client
Minecraft classes, NeoForge client classes, rendering classes, or LWJGL.
Future client-only implementation belongs under an explicit `client` package.

The build enforces direct source-level client namespace boundaries:

```powershell
.\gradlew.bat verifyCommonSideBoundaries
```

This M0 check is intentionally a lightweight source scanner, not a complete
bytecode dependency-graph analysis. A separate source set, client module, or
bytecode-level verification may replace it when the first client implementation
is introduced.

Remote dedicated-server logs, configs, worlds, and generated diagnostics are
outside the 1.0 client boundary. Supporting them requires a separately designed
server companion and permission protocol.

## License

Bug Report is licensed under the [Apache License 2.0](LICENSE).
