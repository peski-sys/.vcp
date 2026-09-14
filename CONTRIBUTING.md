# Contributing

Contributions are welcome when they preserve the project's narrow scope, local-first privacy model, and original-safe processing rules.

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Before opening a change

- Open an issue for a new format rule or processing strategy.
- Include reproducible evidence; do not infer incompatibility from codec names alone.
- Keep “works on this tested device/app version” separate from universal Android claims.
- Prefer Android framework or Jetpack media APIs over bundled native binaries.
- Avoid new permissions and dependencies unless the feature cannot be implemented safely without them.

## Development

Run before submitting:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Add unit tests for parser and decision changes. Media-processing changes also need device tests that verify output tracks, duration, resolution, orientation, audio, color/HDR signaling, decodability, MediaStore publication, and the target gallery behavior.

Pull requests should be focused, explain their user-visible effect and safety boundary, update documentation when behavior changes, and keep the changelog's `[Unreleased]` section current.

## Diagnostics and fixtures

External diagnostic tools can expose filenames, relative paths, timestamps, and GPS metadata. Redact private values before posting. Never commit private videos or GPS metadata.

Test media must be self-created for the project or have a license that permits redistribution. Add its provenance, license, generation settings, expected hash, and expected inspection result next to the fixture.

## Code style

- Keep I/O, facts, decisions, and presentation separate.
- Report missing metadata as unknown; do not guess.
- Bound all metadata allocation and sampling work.
- Fail without changing source media.
- Use the Gradle wrapper and keep lint clean.
- Never commit generated APK/AAB files, local SDK configuration, signing material, or private media.
