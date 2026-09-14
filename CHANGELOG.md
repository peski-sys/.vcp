# Changelog

Notable user-visible changes are recorded here. The project uses semantic versioning and keeps unreleased work separate from published versions.

## [Unreleased]

## [0.4.0-alpha01] - 2026-09-14

Initial public alpha release.

### Added

- Permission-free manual selection through Android's system document picker.
- Local detection of the confirmed iPhone Dolby Vision Profile 8.4 compatibility trigger.
- Lossless HEVC/HLG base-layer transmux with explicit video/audio transcode rejection.
- Transactional MediaStore publication with container, track, media-property, and first-frame validation.
- Opt-in, event-driven automatic processing with no polling or continuously running service.
- Optional one-shot 30-day local reminders that store no filename or media URI.
- Recovery of interrupted app-owned pending outputs and private staging files.
- Unit tests, Android lint, permission-boundary checks, and signed-release automation.

### Safety and privacy

- Originals are never modified or deleted.
- Failed or unverifiable outputs are discarded before becoming visible in Gallery.
- The app has no internet permission, analytics, advertising, account system, or crash-upload SDK.
- App data is excluded from cloud backup and device transfer.

### Known limitations

- Normalization is limited to the exact proven Profile 8/HLG base-layer case with no audio or one AAC track.
- Automatic processing requires full video-library access and remains opt-in.
- Device validation currently covers a controlled iPhone-to-Pixel 10 / Google Photos case; broader device testing remains welcome.
