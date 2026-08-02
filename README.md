# Bug Report

Bug Report is a NeoForge mod and integration API for creating structured,
privacy-reviewed diagnostic reports for compatible Minecraft mods.

Compatible mods describe themselves through a small embedded API. They continue
to load when Bug Report is not installed; when it is installed, Bug Report
discovers their providers deterministically through NeoForge mod metadata.

## Project status

The M0 architecture and risk-closure milestone is complete. M1 build and module
foundation is in progress.

Implemented and executable:

- optional `bugreport-api` embedding through NeoForge Jar-in-Jar;
- compatible API version selection and deduplication;
- provider discovery through NeoForge mod metadata;
- provider ownership and constructor validation;
- deterministic duplicate rejection and failure isolation;
- a loader-neutral production registry with immutable snapshots, canonical
  ordering, bridge/specification consistency checks, and structured diagnostics;
- deterministic capability negotiation with enabled, partially supported, and
  disabled provider states;
- dedicated-server runtime coverage and an enforced common-side source boundary;
- an example provider mod that starts with or without Bug Report installed;
- loader-neutral API and Core module boundaries;
- typed canonical identifiers, independent version domains, side and privacy
  classifications, collection constraints, validation results, localization
  keys, and bounded extension metadata;
- immutable declarative provider, category, field, diagnostic source, generated
  diagnostic, capability, and support-destination specifications;
- reproducible archives, local Maven publication, dependency locking, and CI
  verification.

Not implemented yet:

- execution of diagnostic collection callbacks;
- complete M1 testkit fixtures and exit-criteria verification;
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
- `bugreport-core` — loader-neutral internal report behavior;
- `bugreport-neoforge` — the installed Bug Report runtime mod;
- `bugreport-testkit` — internal integration and security test support;
- `example-mod` — minimal optional-integration example;
- `spike-fixtures` and `spike-runtime` — executable compatibility and failure
  scenarios.

## Building and installing the current prototype

Build and verify everything:

```powershell
.\gradlew.bat publishSpikeApis
.\gradlew.bat check
```

On Unix-like systems:

```bash
./gradlew publishSpikeApis
./gradlew check
```

Keep these as separate Gradle invocations on a clean checkout. The first
invocation creates the isolated Maven repository used by the executable API
compatibility fixtures; the second can then resolve their Jar-in-Jar version
ranges while constructing the task graph.

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
.\gradlew.bat :bugreport-api:publishMavenJavaPublicationToLocalRepository
```

This creates:

```text
build/local-maven
```

Add that repository to the integrating mod. For an external project, point the
path at the Bug Report checkout:

```groovy
repositories {
    maven {
        name = 'bugReportLocal'
        url = uri('C:/path/to/BugReport/build/local-maven')
        content {
            includeGroup 'com.cybersammy.bugreport'
        }
    }
}
```

This local path is a development setup only. Do not publish a consumer build
that depends on the developer's filesystem path.

The separate `publishSpikeApis` task publishes API compatibility fixtures to
`build/spike-maven`; that repository exists for this project's executable
version-negotiation tests and is not the normal integration repository.

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

### 3. Use the loader-neutral contract primitives

Public API values live under `com.cybersammy.bugreport.api` and its descriptive
child packages. They have no Minecraft, NeoForge, filesystem, network, UI, or
worker-runtime dependencies.

Use typed identifiers instead of passing unvalidated strings between contract
objects:

```java
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;

NamespaceId namespace = NamespaceId.of("my_mod");
ProviderId providerId = ProviderId.namespaced(namespace, "client");
CapabilityId capabilityId = CapabilityId.of("my_mod:environment_v1");
```

Identifiers preserve their exact canonical text. They are never lowercased or
otherwise normalized. `ProviderId` accepts the declaring mod ID for the default
provider or `<mod_id>:<local_name>` for an additional provider. Other global
identities, such as capabilities, transports, validation codes, and extension
metadata keys, always require the namespaced form.

The API separates artifact, persisted-schema, and capability versions:

```java
ApiVersion api = ApiVersion.parse("0.2.0");
SchemaVersion schema = SchemaVersion.parse("1.0");
CapabilityVersion capability = CapabilityVersion.parse("1.0");
```

`ApiVersion` accepts canonical SemVer with each numeric core component bounded
to `0..2147483647`. Its equality is exact-text identity, including build
metadata; it must not be used as a precedence or compatibility decision.

Provider-requested `CollectionConstraints` are optional tighter bounds; they
cannot raise product-owned ceilings. `PrivacyClassification` is an immutable
privacy floor that consumers may only make more restrictive.

`ExtensionMetadata` accepts immutable JSON-compatible data only. It rejects
duplicate top-level keys and enforces documented depth, entry-count, string,
number, and total-value bounds. It cannot register callbacks, classes, scripts,
or services. Required behavior belongs in explicit capability negotiation.

`ValidationResult` carries bounded, machine-readable issues with exact
`ValidationPath` values. Errors block a contract; warnings describe
non-blocking limitations.

### 4. Implement a provider

```java
package com.example.mymod.bugreport;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.Optional;

public final class MyBugReportProvider implements BugReportProvider {
    private static final ProviderSpecification SPECIFICATION =
            ProviderSpecification.builder(
                            ProviderId.parse("my_mod"),
                            ProviderVersion.parse("1.0.0"),
                            LocalizationKey.of("my_mod.bugreport.provider"))
                    .supportSide(SupportedSide.PHYSICAL_CLIENT)
                    .addCategory(
                            CategorySpecification.builder(
                                            CategoryId.of("general"),
                                            LocalizationKey.of(
                                                    "my_mod.bugreport.category.general"))
                                    .build())
                    .build();

    public MyBugReportProvider() {}

    @Override
    public String providerId() {
        return "my_mod";
    }

    @Override
    public String providerVersion() {
        return "1.0.0";
    }

    @Override
    public Optional<ProviderSpecification> specification() {
        return Optional.of(SPECIFICATION);
    }
}
```

`ProviderSpecification` is the immutable root of the integration contract. A
category owns its form fields and references provider-level diagnostic sources,
generated diagnostics, and support destinations by typed ID. The builder
rejects duplicate IDs, unresolved references, cross-provider destination and
capability IDs, unsupported physical sides, prohibited privacy declarations,
and invalid field/constraint combinations.

`ProviderVersion` identifies the release of this provider integration. It is a
separate domain from `ApiVersion` and must never be used for API artifact
selection or compatibility negotiation. Its exact text must match
`providerVersion()`, just as the specification ID must match `providerId()`;
the production registry rejects mismatches before accepting child declarations.

Filesystem sources use an approved `LogicalRoot` plus a validated
`RelativePath` or non-recursive `FilenamePattern`; the API never gives a
provider an absolute path. Generated diagnostics receive only the physical
side, a cancellation signal, and a bounded output sink. Destinations are
descriptive values: declaring one cannot transmit data or bypass the user's
review and confirmation.

The default `specification()` implementation returns an empty value so providers
compiled against the earlier API continue to link. New integrations should
return a complete specification. See `ExampleBugReportProvider` in
`example-mod` for sources, generated JSON, fields, capabilities, and a local
archive destination in one executable example.

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

### 5. Declare the provider in NeoForge metadata

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

### 6. Verify optional integration

Test at least these installations:

1. the integrating mod without Bug Report;
2. the integrating mod with Bug Report;
3. the integrating mod on a dedicated server;
4. multiple provider mods embedding compatible API versions.

With Bug Report installed, the current prototype logs:

```text
Bug Report provider discovery completed: providers=[my_mod], supportStates=[my_mod=ENABLED], discoveryDiagnostics=[], registryDiagnostics=[]
```

Provider IDs must be globally unique. If multiple providers return the same
ID, every registration for that ID is rejected. An invalid or throwing provider
does not prevent unrelated valid providers from loading. Providers that return
no M1 specification are diagnosed as legacy and are not added to the production
registry.

Capability requirements are negotiated independently from Java API linkage.
Compatibility requires the same capability ID and major version, with an
available minor version greater than or equal to the requested minimum. A
missing or incompatible required capability disables that provider; an
unsupported optional capability leaves it partially supported. Global offer
collisions reject every colliding offer and disable the providers that declared
them. Offers from disabled providers are removed until the immutable registry
state stabilizes.

## Build verification

The root `check` task verifies:

- unit tests and executable NeoForge compatibility scenarios;
- the current public API bytecode against the frozen `0.2.0` compatibility
  baseline, plus compatible-addition and known-breaking fixtures;
- the allowed production module dependency graph;
- loader, implementation, and physical-side source boundaries;
- local API publication with binary, sources, Javadoc, and POM artifacts;
- deterministic archive settings and packaged Apache license;
- source hygiene and generated-source isolation.

CI runs the same task on Java 21. Dependency locks are committed for production
modules and must be updated intentionally when dependencies change. CI also
performs a clean second build and compares SHA-256 hashes of every production
JAR.

The `jarJar` configuration is intentionally excluded from exact dependency
locking. Its declared version range is published as NeoForge compatibility
metadata; replacing that range with a local lock would change API negotiation.
The embedded preferred version remains explicit, and executable compatibility
fixtures verify every advertised range.

### Updating the API compatibility baseline

The committed baseline is
`config/api-baseline/bugreport-api-0.2.0.jar`. Normal builds only read it and
verify its pinned SHA-256, manifest, module name, license, and public/protected
binary surface. Additive binary-compatible API changes are reported but do not
fail the build; removals and incompatible signature or accessibility changes
do.

Baselines are immutable once created. After an intentional API version decision
and compatibility review, increase both `api_version` and
`api_baseline_version`, then create the absent baseline explicitly:

```powershell
.\gradlew.bat :bugreport-api:createApiBaseline `
    -PconfirmApiBaselineCreation=true
```

Review the resulting API surface and update `api_baseline_sha256` in
`gradle.properties` deliberately. The task refuses to run without the
confirmation property, when the current API and target baseline versions
differ, or when a baseline for that version already exists. Never replace a
baseline for an API version that has already been published. Reports are
written under `bugreport-api/build/reports/api-compatibility`.

## Direct source-level side boundaries

`bugreport-api` cannot reference Core, NeoForge, Minecraft, filesystem, or
network implementation types. `bugreport-core` cannot reference Minecraft or
NeoForge. Common production code must not reference physical-client Minecraft
classes, NeoForge client classes, rendering classes, or LWJGL. Future
client-only implementation belongs under an explicit `client` package.

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
