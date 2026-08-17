# Bug Report

Bug Report is a NeoForge mod and integration API for creating structured,
privacy-reviewed diagnostic reports for compatible Minecraft mods.

Compatible mods describe themselves through a small embedded API. They continue
to load when Bug Report is not installed; when it is installed, Bug Report
discovers their providers deterministically through NeoForge mod metadata.

## Project status

The M0 architecture and risk-closure milestone, the M1 contracts, registry,
and build-foundation milestone, the M2 secure headless reporting engine, and
the M3 local-export MVP are complete. The current `0.1.0-mvp` release provides
the complete physical-client workflow from provider/category selection through
typed forms, bounded collection, sanitization, explicit review, restart-safe
drafts, and user-confirmed local ZIP export.

The release scope, privacy boundary, compatibility notes, and known limitations
are summarized in [CHANGELOG.md](CHANGELOG.md).

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
- registry-bound report session creation that rejects absent and disabled
  providers, plus a loader-neutral state machine with typed invalid-transition
  errors, trusted provider-category selection, recovery paths, monotonic
  revisions, explicit cancellation, privacy-minimized bounded audit events, and
  immutable snapshots;
- immutable bounded form values and deterministic category-scoped field
  validation with stable codes and exact paths;
- bounded deterministic report-draft JSON with explicit schema migration and
  trusted registry rebinding, atomic revision-guarded local persistence, and
  conservative restart recovery that never restores collection or delivery
  authority;
- platform-bound approved source roots and planning-time exact-file resolution
  with typed failures, containment checks, followed/no-follow identity
  comparison, and end-of-plan replacement detection;
- deterministic bounded planning for exact, latest, filtered-directory,
  built-in summary, and user-selected screenshot source declarations;
- provider-tightenable source ceilings of 64 MiB per file and 128 MiB per
  source, with overflow-safe pre-collection size estimates;
- registry-bound category source coordination with privacy-safe provenance,
  isolated missing-source outcomes, and deterministic duplicate/conflict handling;
- bounded 64 KiB streaming of trusted planned files into private report
  workspaces, with source/workspace identity revalidation, effective per-file
  ceilings, atomic publication, and SHA-256 computed in the same pass;
- deterministic category file collection with polling progress, cooperative
  cancellation, isolated typed per-file outcomes, and a 128 MiB run ceiling;
- an executable headless lifecycle harness covering registry-backed session
  selection, collection, sanitization, review, package planning, explicit local
  export consent, deterministic ZIP output, and terminal completion;
- stable typed Core failure codes with allow-listed structured logging context;
  logs never use exception messages, paths, content, secrets, or stack traces
  as structured fields;
- dedicated-server runtime coverage and an enforced common-side source boundary;
- an isolated NeoForge `client` source set with a physical-client bootstrap and
  an opt-in `verifyClientBoundarySmoke` launch test;
- first-party `/bugreport` commands, trusted provider/category selection, a
  paged form screen covering every declarative field kind with Core validation,
  a revision-bound background collection-plan review over approved local roots
  with explicit source inclusion choices, and cancellable background file
  collection into a private product-owned workspace, followed by off-thread
  text sanitization, binary/warning confirmation, exact-byte sealing, and a
  trusted `READY` session transition;
- a bounded screenshot-attachment screen that can capture the current game view through a
  single-use session/revision authority or select up to eight recent PNG/JPEG files from the local
  `screenshots/` directory; selection binds the exact previewed bytes by size, modification time,
  and SHA-256 so replacement before collection is rejected, while accepted images are metadata-
  stripped through PNG re-encoding and retained as separately reviewable sensitive artifacts;
- a final package-summary screen that derives a deterministic ZIP plan from the
  exact sealed workspace, requires a separate explicit local-save click, and
  writes a private ZIP off the render thread into the product-owned
  `bugreport-exports` game-directory child, with an explicit button to open that
  validated folder in the platform file browser;
- a compact persisted report-history screen for completed and failed local
  deliveries, backed by the corruption-tolerant path-free Core history index;
- off-thread typed form autosave into a bounded product-owned draft directory,
  plus a restart-recovery screen that rebinds every draft to the current trusted
  registry and restores form editing without restoring collection, review, or
  export authority;
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

- remote report delivery and its transport policy;
- end-user report submission.

The current build is suitable for testing API packaging, optional installation,
side safety, provider discovery, the headless Core report lifecycle, and the
player-facing session, form, collection, sanitization, review, package summary,
and explicit local ZIP-export flow in real mods.

## Supported platform

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.227 or a compatible version accepted by the mod metadata

Bug Report is client-first, not entirely client-only. The API, provider class,
constructor, common bootstrap, and discovery snapshot are common-side code.
The NeoForge physical-client bootstrap is compiled from an isolated `client`
source set, which depends on common code while common code cannot compile
against it. Reporting UI, player commands, and local-file collection remain
physical-client features.

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
bugreport-neoforge/build/libs/bugreport-neoforge-0.1.0-mvp.jar
```

Copy it into the `mods` directory of a Minecraft 1.21.1 NeoForge instance.
The first client command boundary is available after the client has loaded:

```text
/bugreport
/bugreport list
/bugreport create <mod-id> [category-id]
/bugreport open [report-id]
/bugreport discard <report-id>
```

`/bugreport` opens the provider/category selector. `/bugreport create <mod-id>`
opens the same flow with a trusted provider preselected, while supplying both
the provider and category creates the session directly. Category selection
opens a paged form generated from the provider specification. It supports text,
reproduction steps, booleans, declared selections, arbitrary-precision numbers,
severity, side context, and read-only information; validation remains in Core.
`/bugreport open` reopens the most recently created currently resumable in-memory report;
when every live report is temporarily busy, it identifies the newest one as busy instead
of claiming that no session exists. An optional exact report ID selects another active
report. Active report IDs are offered with readable provider/category suggestion labels,
and the provider screen exposes a readable active-report picker. The command reopens the current safe UI checkpoint,
including the form, collection planning, artifact review, and local export confirmation.
A still-running collection, sanitization, or delivery is reported as busy and can
be opened after it reaches its next checkpoint.
Persisted form checkpoints can also be reopened by ID after restart. The
checkpoint remains available throughout the non-terminal workflow, but no
collection, workspace, review, or delivery authority is reconstructed from
disk: recovery conservatively returns to form editing and requires those steps
to run again.
Form edits are periodically saved off the render thread. Back waits for the
latest typed draft to be persisted, while Cancel discards both the session and
its canonical persisted draft and reports failure without cancelling when that
durable deletion cannot be completed. Successful form confirmation atomically
replaces the editable draft with a restart checkpoint for the exact validated
submission. The checkpoint is retained through collection, review, and failed
delivery. A confirmed export first replaces it with a non-recoverable delivery-intent
tombstone, before any ZIP is published. Completion and cancellation retain a
non-recoverable tombstone until best-effort cleanup; cleanup failure cannot change an
already published ZIP or terminal outcome. The provider selector exposes a bounded recovery
screen after restart. A recoverable entry is rebound to the exact current
provider version and category before it can resume; malformed, disabled,
missing, or structurally incompatible entries remain isolated and may only be
discarded. Recovery always returns to `FORM_IN_PROGRESS` and never restores
collection plans, workspace/prepared snapshots, consent, or export authority.
After a successful validation, **Plan** revalidates and confirms the typed
form, then builds a source and size preview in the background. The preview
allows declared available sources to be included or excluded before collection
copying or generator I/O; unavailable sources cannot be selected. Accepting the selection binds it to
the exact confirmation revision. Accepting a plan starts bounded file collection
in the background, displays polling progress, and allows cooperative cancellation.
Complete and partial results can then enter the sanitization/review screen.
Supported text is sanitized in the private workspace off the render thread;
failed artifacts remain unselectable, and binary artifacts or unresolved
findings require a separate explicit confirmation before inclusion. The review
and screenshot pickers retain their non-authoritative UI choices across an in-process
close/reopen while Core revalidates the exact plan and review authority before use. The review
shows metadata before its state controls and can explicitly open checksum-verified
original and sanitized text versions in the platform application; binary artifacts
have one exact reviewed version. Private retained originals are deleted before
the review can seal or when the report is cancelled. Accepting the review seals
and revalidates the exact selected bytes and advances the
session to `READY` with package authority retained by the application service.
The UI supplies only cancellation and typed inclusion/confirmation decisions:
it cannot select a sanitization pipeline, construct an execution/review token,
or mint prepared package authority.
Back returns to the form without losing its values and revokes the prior
confirmation and plan authority. Once the reviewed bytes are `READY`, the
export screen builds the deterministic package plan off-thread, shows its
provider/category, entry count, and byte total, then requires a distinct
explicit save action. The archive name is constrained to a single safe
`.bugreport.zip` filename and is published only in the validated
`bugreport-exports` child of the active game directory.

For a local physical-client boundary smoke test, run:

```powershell
.\gradlew.bat verifyClientBoundarySmoke
```

It launches an isolated development client with a test-only probe, verifies that
common provider discovery completes with an empty registry, verifies that the
Bug Report client bootstrap accepts a render-thread dispatch and builds the
production Brigadier command tree, writes a marker, and exits automatically.

For the complete automated M3 smoke, run:

```powershell
.\gradlew.bat verifyM3GameplaySmoke
```

This combines the physical-client bootstrap with the production application and
Core lifecycle: trusted form/collection authority, sanitization and explicit review,
fresh export retry authority, package construction, local consent, real ZIP publication,
and manifest/archive verification. It intentionally does not simulate mouse input or
judge visual screen layout; those remain part of the manual gameplay pass.
It does not require a player connection or manual world entry. It is
intentionally opt-in rather than part of CI `check`, because it needs a usable
graphical client environment.

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
            'com.cybersammy.bugreport:bugreport-api:0.3.0')) {
        version {
            strictly '[0.3.0,1.0.0)'
            prefer '0.3.0'
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
ApiVersion api = ApiVersion.parse("0.3.0");
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
import com.cybersammy.bugreport.api.specification.StandardFields;
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
                                    .addField(StandardFields.summary())
                                    .addField(StandardFields.description())
                                    .addField(StandardFields.reproductionSteps())
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

### Bounded world-state diagnostics

World saves and player files are never declarative source roots. A mod that
needs to expose a small purpose-built world-state summary must generate TEXT or
JSON through the bounded sink and require the versioned product capability:

```java
DiagnosticGeneratorSpecification worldSummary =
        DiagnosticGeneratorSpecification.worldStateExport(
                        DiagnosticGeneratorId.of("world_summary"),
                        (request, sink) -> sink.emitJson(
                                GeneratedArtifactId.of("summary"),
                                createBoundedSummary()))
                .labelKey(LocalizationKey.of("my_mod.bugreport.world_summary"))
                .privacy(PrivacyClassification.SENSITIVE)
                .contentType(DiagnosticContentType.JSON)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .executionContext(GeneratorExecutionContext.WORKER)
                .constraints(StandardCapabilities.boundedWorldStateExportMaximums())
                .build();

ProviderSpecification specification = ProviderSpecification.builder(/* ... */)
        .addGenerator(worldSummary)
        .requireCapability(StandardCapabilities.boundedWorldStateExport())
        // add a category that references worldSummary.id()
        .build();
```

Capability version 1.0 requires `SENSITIVE` classification, default exclusion,
explicit limits of at most four artifacts, one MiB per artifact, two MiB in
aggregate, and two seconds of callback time. Providers may request tighter
limits. The callback receives no path, workspace, stream, or permission to read
`saves/`; the generator kind gives the future UI enough trusted product context
to present a separate warning and obtain explicit opt-in before invoking the
callback. The two-second capability ceiling is not a game-thread blocking
budget: `GAME_THREAD_SNAPSHOT` execution will use a separate substantially
shorter capture budget and move longer serialization to a worker.

This contract constrains Bug Report orchestration and cooperating providers. It
is not a JVM sandbox: an installed Java mod executes in the same process and can
independently access APIs that are not exposed by Bug Report.

Core executes an explicitly requested generator only after resolving it through
the trusted registry and selected category. Each `emitText` or `emitJson` call
is UTF-8 encoded under the tighter of provider and product ceilings, checks
cancellation during output, computes SHA-256 while writing, and atomically
publishes an owner-private `generated-<sha>.txt` or `.json` artifact. Duplicate
IDs, representation mismatch, malformed Unicode, count or byte overflow,
callback failure, and cancellation roll back every artifact from that
invocation. Results expose canonical provenance and never expose local paths.
Serious JVM `Error` values also trigger best-effort rollback but are rethrown
unchanged; a rollback failure is retained as suppressed diagnostic context.

The category executor runs `WORKER` generators in canonical ID order on virtual
threads. It applies the tighter of the declared callback timeout and the
two-second product ceiling, atomically revokes the sink and interrupts its
worker before returning a timeout or cancellation outcome, continues after an
isolated ordinary provider failure, and charges only retained successful
artifacts to the shared report byte budget. Each generator produces a typed
collected, failed, timed-out, cancelled, budget-rejected, or
execution-context-unavailable outcome. JVM `Error` values remain fatal after
best-effort rollback.

Timeout is an in-process containment boundary, not forced thread termination.
Core interrupts the virtual worker and permanently closes its sink, so a
callback that ignores interruption cannot publish late output through Bug
Report. Java cannot forcibly terminate arbitrary mod code that ignores both
interruption and the provided cancellation signal. If timeout occurs inside an
active emission, the deadline path does not wait for the sink monitor: encoding
and publication observe the lock-free revocation, final publication is denied,
and the worker removes invocation-owned temporary or previously published
artifacts after the active operation unwinds.

Production orchestration uses `CategoryGeneratedDiagnosticExecutor.executeAsync`
so registry work, callback coordination, encoding, checksums, and filesystem I/O
run on a virtual worker. `GAME_THREAD_SNAPSHOT` callbacks alone are submitted
through a platform `GameThreadDispatcher`. They receive a capture-only sink
that has no workspace authority, materializes TEXT as an immutable `String`,
and accepts already-immutable bounded JSON metadata. The game-thread phase has
a separate 50 ms product ceiling, including dispatch queue delay. Successful
captured values are replayed through the normal bounded workspace sink on the
worker. A rejected or unavailable dispatcher produces a typed outcome; Core
never silently runs the callback on the wrong thread.

The NeoForge adapter routes physical-client capture through Minecraft's client
executor and enables dedicated-server capture only between server-started and
server-stopping lifecycle events. Timeout revokes capture authority but cannot
forcibly terminate arbitrary in-process provider code already executing on the
game thread, so providers must keep snapshot callbacks short and cooperative.

The current foundation validates and negotiates this declaration but does not
advertise the runtime capability yet. Providers that declare it remain disabled
until the bounded callback executor and review flow are implemented.

### Dynamic source paths

Use a dynamic source only when exact log or crash-report filenames cannot be
declared statically. The callback emits bounded `RelativePath` values below a
root chosen in advance; it never receives a `Path`, directory, stream, or root
location:

```java
DiagnosticSourceSpecification dynamicLogs =
        DiagnosticSourceSpecification.dynamicFiles(
                        DiagnosticSourceId.of("active_logs"),
                        LogicalRoot.GAME_LOGS,
                        (request, sink) -> {
                            sink.emit(RelativePath.of("current/client.log"));
                            sink.emit(RelativePath.of("current/network.log"));
                        })
                .labelKey(LocalizationKey.of("my_mod.bugreport.active_logs"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .constraints(CollectionConstraints.builder()
                        .maxMatchedFiles(2)
                        .maxBytesPerFile(4 * 1024 * 1024)
                        .maxTotalBytes(8 * 1024 * 1024)
                        .callbackTimeout(Duration.ofMillis(250))
                        .build())
                .build();
```

Dynamic paths are limited to `GAME_LOGS` and `CRASH_REPORTS`. Core runs the
callback on a virtual worker with a hard two-second product ceiling, rejects
null, duplicate, asynchronous, late, or over-limit emissions, sorts accepted
results canonically, and validates every result through the same no-follow
filesystem resolver as static selectors. One invalid or missing result rejects
the entire source plan; valid siblings are not collected partially.

### Isolated report workspaces

Core creates collection workspaces below one application-owned absolute root:

```java
FileReportWorkspaceStore workspaces =
        new FileReportWorkspaceStore(gameDirectory.resolve("bugreport/workspaces").toAbsolutePath());
ReportWorkspace workspace = workspaces.create(reportSession.snapshot().id());
```

Each workspace is a newly claimed directory named by the canonical
`ReportSessionId` and contains a versioned `.bugreport-workspace` ownership
marker. Existing paths are never reused or trusted, even if they contain a
plausible marker. Root components, the session directory, and the marker are
checked without following filesystem redirections and are revalidated before
the trusted handle is returned. Creation failure removes only entries whose
identity Core observed after creating them; uncertain or non-empty paths are
left in place and reported as `ROLLBACK_FAILED` for later safe recovery.

On POSIX filesystems, newly created root segments and session directories use
atomic `0700` permissions and markers use atomic `0600` permissions. On
ACL-capable non-POSIX filesystems, Core applies and verifies an owner-only ACL
before writing marker contents and fails creation if the restrictive ACL cannot
be established. Filesystems exposing neither POSIX permissions nor ACLs use an
explicit best-effort fallback and should be mounted with private defaults.

`ReportWorkspace.directory()` is Core-owned authority and must not be passed to
a provider. `WorkspaceSourceCollector` accepts only a trusted
`PlannedSourceFile`, re-resolves the approved logical source before opening and
after reading, and repeats workspace marker, identity, filesystem-store, and
private-permission checks. It streams through a fixed 64 KiB buffer, stops at
the effective provider/product byte ceiling carried by the plan, and computes
SHA-256 over the exact copied bytes in the same pass.

Copied bytes first enter an owner-only random temporary file. Core then claims
the deterministic opaque destination name with `CREATE_NEW` and atomically
replaces only that reservation, so an existing artifact is never overwritten
even on platforms whose atomic-move implementation replaces destinations by
default. The result exposes only the opaque artifact name, byte count,
checksum, provenance, and classification metadata—never an original or
workspace path. A failed copy removes only temporary or reserved entries whose
identity still matches; uncertain rollback is reported as `ROLLBACK_FAILED`.

The source-opening boundary also returns identity evidence for the channel it
opened. On Windows the NIO adapter uses `NOSHARE_DELETE`, preventing rename or
replacement of that path entry while the handle remains open; its immediate
observation is therefore bound to the channel for the copy lifetime. Portable
POSIX Java NIO exposes no file identity directly from `FileChannel`, so the
current fallback performs immediate and post-copy path identity revalidation
but cannot prove the opened handle against a malicious same-user ABA rename.
Supporting a POSIX native handle adapter is tracked explicitly and the current
boundary must not be described as an OS sandbox.

`FileCollectionCoordinator` consumes the conflict-free files of one
`CategorySourcePlan` in canonical order. A caller retains a one-shot
`CollectionRunControl`, polls immutable progress, and may request cancellation
from another thread. Cancellation is checked between bounded chunks and before
atomic publication; the active temporary artifact is rolled back and every
unstarted file receives a deterministic `CANCELLED` outcome. An ordinary typed
failure remains isolated, so unrelated files continue and the final result is
`COMPLETE`, `PARTIAL`, `FAILED`, or `CANCELLED`.

Cancellation and terminal publication are linearized through the same atomic
control state. A request that loses to terminal publication returns `false` and
does not leave the signal set. If cancellation wins, the run is `CANCELLED`;
files already published atomically remain successful outcomes even when no file
outcome itself remains to cancel.

The file run rejects a planning-time total above 128 MiB before writing and
enforces the same ceiling again against actual streamed bytes. Progress counts
processed bytes, including work later discarded after a failed file, and is not
a retained-workspace-size guarantee.

`CategoryCollectionCoordinator` closes the aggregate boundary for generated
diagnostics. The reviewed plan records exact file-source and generator choices;
its opaque fingerprint includes both canonical file observations and selected
generator IDs. File copying runs first, then only reviewed generators execute
in canonical ID order through the bounded worker or game-thread snapshot
executor. Generated output receives only the remainder of the same 128 MiB
report budget. One cancellation signal remains valid across both phases, and
the session service accepts a terminal result only while revision, provider,
category, and exact plan fingerprint still match. The combined terminal result
is coordinator-issued rather than publicly constructible; its status is
validated against child outcomes and its generator outcome IDs must exactly
match the reviewed selection.

Generated TEXT and JSON artifacts enter the same product-owned sanitization and
explicit review boundary as copied files. Evidence is tied to exact final byte
count and SHA-256, generator provenance is retained in the reviewed snapshot
and manifest, and excluded or failed generators cannot be reintroduced later.

`ReviewedWorkspaceSnapshotFactory` accepts only a `REVIEW_REQUIRED` session,
its matching file/generated collection results, and the exact artifact names
chosen during review. It terminally seals the workspace, rejects new Core write
authority, waits up to two seconds for already-started publication and rollback
operations, and quarantines the workspace when quiescence cannot be proven.
Unknown workspace entries, mismatched provenance, changed identity, size, or
SHA-256 fail closed. The resulting `ReviewedWorkspaceSnapshot` is path-free,
canonically ordered, byte-bound, and has no public constructor. Later consumers
must call `ReviewedWorkspaceSnapshotFactory.requireCurrent` before reading; a
same-user process or in-process mod is not treated as a filesystem sandbox.
Trusted lifecycle/history code authorizes abandoned-workspace cleanup with
exact session IDs; Core never infers abandonment from directory age or from the
marker alone:

```java
AbandonedWorkspaceCleanupResult cleanup =
        workspaces.cleanupAbandoned(confirmedAbandonedSessionIds);
```

The bounded pass processes IDs in canonical order, refuses sessions created by
the live store instance, validates every root component without following
symlinks or junctions, and requires the exact versioned marker. Only canonical
source, generated, and invocation-temporary artifact names are eligible for
deletion. Unknown entries, changed identities, unsafe permissions, or
filesystem redirection quarantine that workspace without blocking unrelated
sessions. Partial failures remain explicit because recursive filesystem
deletion cannot be transactional.

### Sanitization pipeline foundation

Core provides a deterministic streaming `SanitizationPipeline` for trusted
product text rules. Stages execute by explicit numeric order and stable stage
ID, process one bounded logical line at a time, and never receive a path or
workspace handle. The pipeline preserves `LF`, `CRLF`, and `CR` terminators,
checks cancellation while reading, and independently limits line, input,
output, match, finding, failure, and stage counts.

Automatic replacements and unresolved warnings both produce path-safe
`SanitizationFinding` metadata containing only the artifact name, stage ID,
original one-based UTF-16 location, classification, and action. Matched text is
never copied into finding metadata. A throwing, null-returning, or invalid stage
fails the artifact closed with `STAGE_FAILED`; its safe stage ID and line are
carried by the exception, while the original exception message is discarded.
No ordinary `SanitizationResult` can represent an incompletely checked output.

The pipeline reads from and writes to caller-owned streams.
`WorkspaceSanitizationCoordinator` is the trusted Core boundary that reads an
exact reviewed text artifact, first retains a private checksum-verified original
for explicit comparison, writes through a private temporary workspace file,
atomically publishes accepted output, recalculates its SHA-256 checksum, and
revalidates workspace ownership. Opening either version requires the exact active
application review authority and another checksum verification. Retained originals
are recognized by abandoned-workspace cleanup but are removed before terminal
sealing. The coordinator fails closed on sanitizer errors and never treats binary
artifacts as text. The resulting `SanitizationResult` and new artifact evidence are
then issued into a prepared snapshot.

`HomeDirectoryMaskingStage` and `UsernameMaskingStage` provide the first
product identity rules. Core never reads `user.home`, `user.name`, environment
variables, or loader state itself; a platform adapter supplies the already
selected values and an explicit `SENSITIVE` or `INSENSITIVE` comparison policy:

```java
SanitizationPipeline pipeline = new SanitizationPipeline(List.of(
        new HomeDirectoryMaskingStage(homeDirectory, caseSensitivity),
        new UsernameMaskingStage(username, caseSensitivity)));
```

The home stage runs first, accepts only bounded non-root absolute paths without
`.`/`..` traversal, recognizes Windows, POSIX, UNC, alternate, and repeated
escaped separators, and replaces the complete prefix with `<home>`. The
username stage uses Unicode letter/number token boundaries and replaces exact
remaining identities with `<user>` without changing larger words. Usernames
shorter than three Unicode code points are rejected because unrestricted
automatic replacement would corrupt ordinary diagnostic text; orchestration
must treat that configuration as unsupported rather than silently omit privacy
protection. Inserted safe replacements are protected from all later stages, so
a username such as `home` cannot rewrite `<home>`.

`ProductSanitization.textPipeline(...)` assembles the complete ordered product
policy. It detects email addresses, IPv4/IPv6 endpoints, context-labelled server
addresses and session identifiers, bearer credentials, API keys, Discord/Slack
webhooks, and known Minecraft access/client-token keys. Credential stages are
classified `PROHIBITED`, always redact, and cannot be weakened by a custom
profile. Detectors use bounded, context-aware syntax and intentionally avoid
guessing arbitrary UUIDs, domains, or high-entropy strings.

Three profiles are available per `LOG` or `CONFIGURATION` artifact policy:

- `STANDARD` redacts high-confidence identity and every credential; ambiguous
  network/server locations in logs remain visible with unresolved warnings;
- `STRICT_PRIVACY` redacts every supported match;
- `CUSTOM_REVIEW` uses caller-selected actions for non-prohibited stages and
  defaults unspecified matches to unresolved warnings.

Configuration policy redacts supported personal locations by default because
key/value context makes them more likely to be durable user data. Binary
content never enters the text pipeline: `assessBinary(...)` raises its privacy
floor to `SENSITIVE` and keeps it excluded pending explicit review. A warning
is not proof of safety or permission to package an artifact. All inserted
replacement ranges are opaque to later stages, and findings never retain raw
matched values.

### Manifest schema v1

Core now defines the first portable report manifest as the immutable
`ReportManifest` model and deterministic `ReportManifestJsonCodec`. Writers emit
schema `bugreport:report_manifest` version `1.0`; readers reject another schema
ID or major version and safely ignore bounded additive members from a newer
minor version. The UTF-8 JSON boundary rejects duplicate members, malformed
numbers, excessive nesting, oversized input, invalid identifiers, and
non-canonical archive paths.

`DecodedReportManifest.newerMinorVersion()` means that the known subset is safe
to inspect; it does not promise a lossless proxy round-trip. Encoding always
writes the current `1.0` schema and cannot preserve unknown future members.

The manifest records the report and producer identity, optional exact provider
target, physical environment, reviewed typed fields, capabilities, included
entry sizes and SHA-256 checksums, collection and sanitization outcomes,
bounded provenance, safe error codes, and namespaced extensions. Collections
are canonically ordered, aggregate counts and uncompressed bytes are bounded,
and an entry cannot weaken its declared privacy floor or contain prohibited
content. Raw source paths, matched secrets, provider exception messages,
credentials, and excluded artifacts are not part of the portable model.

Constructing or decoding a structurally valid manifest does not authorize
packaging. Decoded manifests are untrusted portable data. The package writer
must accept a factory-issued `ReportPackagePlan` and read only the exact
reviewed workspace artifacts named by it.

### Deterministic package plan

`ReportPackagePlanFactory` accepts only a factory-issued
`PreparedWorkspaceSnapshot`, never an ordinary reviewed snapshot plus a
caller-selected sanitization enum. The prepared snapshot retains the exact
final reviewed artifact identity together with trusted sanitization results or
explicit binary/warning review. Plan creation revalidates the sealed workspace,
requires the exact report/provider/version/category identity, and matches every
manifest content entry against byte count, SHA-256, content type, effective
privacy, quality role, collection kind, sanitization status/findings, and full
source/generator provenance. Missing evidence, extra, stale, or mismatched
artifacts fail closed with a path-safe typed error.

The plan fixes archive order as `manifest.json`, optional `report.md`, then
canonical `content/*` entries. Paths are lowercase, normalized, bounded, and
case-insensitively unique. Inline documents and workspace artifacts retain
exact sizes and checksums, and returned byte arrays are defensive copies.

Optional Markdown is generated only from known reviewed manifest metadata and
form values. Rendering is deterministic, UTF-8, limited to 1 MiB, normalizes
line endings, and escapes Markdown punctuation and inline HTML. It is a human
summary, not an authority or a replacement for `manifest.json`.

The internal prepared-snapshot issuing boundary is intentionally unavailable
to arbitrary manifest callers. `WorkspaceSanitizationCoordinator` creates
internal evidence containing the exact final artifact checksum,
size, and sanitizer result. `WorkspacePreparationCoordinator` issues authority
only when that evidence matches the reviewed artifact exactly and any warnings
or binary artifacts have explicit review evidence. Production package planning
therefore has no bypass path. A plan still does not
grant delivery consent.

### Streaming ZIP export

`ReportZipWriter` consumes only a `ReportPackagePlan` and an explicitly chosen
new `*.bugreport.zip` destination. It revalidates the sealed reviewed workspace
before and after streaming, reads only planned direct-child artifacts, verifies
their exact size and SHA-256 while copying, and checks cancellation between
bounded chunks. ZIP entries use fixed metadata and canonical plan order, so
identical inputs produce byte-identical archives. The writer creates an
owner-only sibling temporary file, never overwrites an existing destination,
and publishes only a complete validated archive through an atomic no-replace
filesystem link. The destination filesystem must prove owner-only file access
through POSIX permissions or ACLs; filesystems without either supported model
are rejected before the temporary archive is created. Failure or cancellation
removes the temporary output.

`ReportZipValidator` is a separate bounded read pass over the finished archive.
It rejects invalid ZIP structure, unsafe absolute or traversal names,
case-insensitive duplicates, reordered/missing/extra entries, expanded-size
overflow, and any byte-count or checksum mismatch with the trusted plan. Both
encoded archive bytes and aggregate uncompressed bytes have product ceilings.
Validation and export perform blocking filesystem I/O and must run off the UI
and game threads. A validated local archive still does not grant network
delivery consent.

### Restricted local transport

`ReportTransport` is an internal first-party separation boundary, not a public
provider or runtime plugin SPI. Its initial implementation, `LocalZipTransport`,
has no network or authentication authority and can only export a validated ZIP
to a user-selected local destination. It receives the already prepared package
plan and sealed workspace; it never recollects source files or changes reviewed
package contents.

Every export requires fresh consent issued through a package-private,
product-owned boundary only after a trusted UI or headless coordinator has
displayed the exact package, transport, and destination and received explicit
user confirmation. Raw consent implementations and minting operations are not
public APIs. Consent is bound to the canonical package
plan fingerprint, transport ID, and normalized destination, and is consumed by
one attempt. A failed export may be retried with the same immutable plan, but
requires new consent; changing the plan or destination also requires new
consent. Mismatched consent does not authorize or consume an otherwise valid
attempt.

The transport exposes typed success, cancellation, and failure results plus
monotonic entry/byte progress. Cancellation and ZIP failures remove partial
output, existing destinations are never overwritten, and progress bytes mean
uncompressed bytes processed during the attempt. Transport execution performs
blocking filesystem I/O and must run off the UI and game threads.

### Versioned local configuration

`ReportConfiguration` is the single immutable Core model for user-selected
limits, privacy posture, workspace location, and completed-report retention.
The configuration is stored as bounded canonical UTF-8 JSON with schema ID
`bugreport:configuration` and current schema `1.0`. The codec reads the legacy
`0.1` fixture through an explicit migration and rejects unknown schema versions,
duplicate members, malformed encoding, traversal-like workspace paths, and
values outside non-negotiable product ceilings.

The platform resolves `WorkspaceLocation` only below its own approved data root:
configuration never grants an arbitrary absolute filesystem path. `CleanupPolicy`
applies to future completed-report history; it deliberately does not grant
age-based deletion of abandoned workspaces. `FileReportConfigurationStore`
accepts only a pre-existing platform-trusted directory, uses its fixed
`bugreport.json` filename, revalidates every path segment against symlink or
filesystem redirection before each operation, and uses same-directory atomic
replacement. It returns typed, path-safe failures for invalid files,
unsupported atomic moves, and I/O errors.

The model is a configuration foundation. Existing hard product ceilings remain
authoritative; later lifecycle and history coordinators consume the decoded
values to apply user-selected limits and retention without broadening authority.

### Minimal report history

`ReportHistoryIndex` retains only the metadata required to recover a draft and
show a completed or failed report: session/provider/category identity, revision,
timestamp, high-level status, and (only for completed reports) a path-free ZIP
summary. It never stores form values, local archive paths, source paths,
sanitization findings, exception messages, or secrets.

`FileReportHistoryStore` uses a platform-trusted directory and fixed
`history.json` filename. It writes atomically and treats malformed, oversized,
or unsafe persisted bytes as a recoverable empty index without deleting the
original file. A future UI/history service can present that recovery condition
and decide whether to replace the corrupted index after explicit user action.

`StandardFields` provides immutable, localized declarations for summary,
description, reproduction steps, expected and actual behavior, severity, and
side/context. A provider may reuse any subset and combine it with its own
category-local fields. Free-form standard fields are bounded and carry a
`PERSONAL` privacy floor; product-defined selectors contain no provider options.
`required` applies only when a provider includes that field in a selected
category. It does not make the field globally mandatory for every provider.

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

Every registry rejection exposes a stable diagnostic code, an exact
`ValidationPath`, and a deterministic developer-facing message. Paths identify
the rejected bridge or specification property, such as `$.providerId`,
`$.specification.id`, or `$.specification.version`. Exception messages supplied
by third-party providers are not copied into diagnostics or normal logs. A
thrown `specification()` callback and an invalid `null` return have distinct
diagnostic codes and remediation messages.

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
