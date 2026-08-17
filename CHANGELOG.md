# Changelog

## 0.1.0-mvp — 2026-08-17

First public MVP beta for Minecraft 1.21.1 on NeoForge 21.1.227 or a compatible
21.1.x release. Java 21 is required.

### Added

- `/bugreport` commands and the complete physical-client reporting workflow.
- Registry-backed provider and category selection with typed generated forms.
- Bounded collection of declared logs, crash reports, exact configuration files,
  provider-generated diagnostics, and explicitly selected screenshots.
- Current-view screenshot capture, normalized PNG publication, metadata stripping,
  multiple selection, previews, and mandatory sensitive review.
- Product-owned text sanitization with original/sanitized comparison, findings,
  privacy classification, and explicit binary confirmation.
- Deterministic manifest/package planning and private local ZIP export after a
  separate user confirmation.
- Safe in-process reopen, autosaved form drafts, restart recovery, minimal terminal
  history, failed-delivery retry, and non-recoverable export/terminal tombstones.

### Privacy and security

- This release performs no network upload and stores no remote credential.
- Providers cannot mint filesystem, sanitization, review, package, consent, or
  delivery authority.
- Collection and sanitization never modify original game files.
- Package bytes come only from the reviewed private workspace and are revalidated
  by size and SHA-256 before export.
- Sensitive screenshots and unresolved findings require explicit review.
- Users may exclude allowed artifacts and must explicitly confirm the final ZIP.

### Compatibility

- Runtime mod version: `0.1.0-mvp`.
- Independently versioned loader-neutral provider API: `0.3.0`.
- Provider fixtures cover current and supported older embedded API contracts.
- Compatible provider mods continue to load when Bug Report is absent.
- Persisted draft, configuration, history, and manifest formats are versioned,
  bounded, and migration- or rejection-tested.

### Known limitations

- The product workflow is available on the physical client only.
- Dedicated servers expose no Bug Report UI, collection command, workspace, or
  delivery workflow.
- Export uses the validated `bugreport-exports` directory below the game directory;
  arbitrary destination selection is planned for M4.
- There is no built-in general/unknown-mod report, automatic crash suggestion,
  cloud upload, issue-tracker submission, email attachment, or credential store.
- Automatic sanitization cannot prove that arbitrary diagnostic text is free of
  every secret; users must review the exact output before export.
