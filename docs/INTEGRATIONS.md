# Bug Report mod integrations

This page tracks publicly available Minecraft mods that declare a Bug Report
provider. It is a discovery and compatibility registry, not an endorsement of a
mod or a guarantee about future releases.

Bug Report remains optional for integrating mods. A listed mod must continue to
start and function normally when the `bugreport` runtime mod is absent. Players
install only the normal JAR of the integrating mod; its compatible
`bugreport-api` library is embedded through NeoForge Jar-in-Jar.

For implementation instructions, dependency coordinates, complete provider
examples, and verification steps, see
[Integrating Bug Report API into another mod](../README.md#integrating-bug-report-api-into-another-mod).

## Public integrations

There are no third-party public integrations listed yet.

| Mod | Project and source | Provider ID | Minecraft / loader | API | Report categories | Status |
| --- | --- | --- | --- | --- | --- | --- |
| _No integrations submitted_ | — | — | — | — | — | — |

The repository's `example-mod` is an executable compatibility fixture and a
reference implementation. It is intentionally not listed as a public player mod.

## Status definitions

- **Verified** — Bug Report maintainers reviewed the declared provider boundary
  and reproduced the integration with the listed public releases.
- **Community** — the mod author or maintainer reports that the listed releases
  work, but Bug Report maintainers have not independently reproduced them.
- **Experimental** — the integration is public but intentionally targets an
  unstable or incomplete contract.
- **Outdated** — the listed integration is known not to work with the current
  supported Bug Report runtime or platform.

Only Bug Report maintainers assign or change **Verified** status. A community
submission can be useful before independent verification and should not claim
that Bug Report maintainers audited the integrating mod's diagnostic content.

## Requesting a listing

Open a pull request against this file and add one table row. The pull request
must include:

1. the mod name and its public Modrinth or CurseForge project page;
2. a public source repository or a stable link to the provider implementation;
3. every declared provider ID;
4. the released mod version tested with Bug Report;
5. the Minecraft and NeoForge versions tested;
6. the embedded `bugreport-api` version and declared Jar-in-Jar compatibility
   range;
7. a short list of the report categories exposed to players;
8. confirmation that the mod starts with and without Bug Report installed and
   on a dedicated server where applicable.

Use **Community** as the requested status unless a Bug Report maintainer has
already completed independent verification. A typical entry is:

```markdown
| [Example Mod](https://modrinth.com/mod/example) | [Source](https://github.com/example/example-mod) | `example_mod` | 1.21.1 / NeoForge 21.1.x | 0.3.0 (`[0.3.0,1.0.0)`) | Gameplay, crashes | Community |
```

Keep category names short and user-facing. Do not put filesystem paths,
credentials, report contents, or other sensitive diagnostics in the registry or
its pull request.

## Verification checklist

Before requesting **Verified** status, provide reproducible evidence for all of
the following:

- the released integrating-mod JAR embeds the declared API version through
  NeoForge Jar-in-Jar;
- provider discovery reaches the expected enabled or partially supported state;
- provider IDs, versions, categories, fields, sources, generators, privacy
  classifications, and capabilities match the published integration;
- the integrating mod loads without Bug Report installed;
- the integrating mod loads with the supported Bug Report release installed;
- common/provider code remains safe on a dedicated server, while player-facing
  reporting stays on the physical client;
- a representative report can complete the selection, collection,
  sanitization, review, and local ZIP-export flow without bypassing user
  confirmation.

Verification applies only to the exact releases recorded in the table. Mod
authors should update their row when changing the provider contract, embedded
API range, Minecraft version, or loader support.
