<p align="center">
  <img src="docs/assets/vcp-logo.png" alt=".vcp" width="320">
</p>

# .vcp

Video Compatibility Processor is a small native Android utility for videos that play on Android but fail to receive a thumbnail or open in a gallery editor.

It targets one confirmed compatibility problem: iPhone Dolby Vision Profile 8.4 video that loses thumbnail and editing support in Google Photos on the tested Pixel 10. For that exact case, .vcp creates a compatible HEVC/HLG MP4 copy and keeps the original untouched.

## What it does

- Processes one video selected through Android's system picker, without broad library access.
- Optionally watches for newly added videos using Android's event-driven `JobScheduler` APIs.
- Detects the narrow, tested Dolby Vision Profile 8.4 + HLG base-layer case.
- Stream-copies the HEVC video and AAC audio instead of decoding and re-encoding them.
- Publishes a new verified MP4 under `Movies/vcp` only after container, track, duration, color, audio, and first-frame checks pass.
- Optionally posts a one-time local reminder 30 days after a copy is created.
- Never deletes, replaces, or uploads the original video.

Automatic processing and reminders are independent, opt-in settings. The app performs no polling and has no continuously running service.

## Supported case

Automatic normalization is intentionally conservative. A video is eligible only when .vcp can prove all of the following:

- Dolby Vision Profile 8 is declared;
- an RPU and backward-compatible base layer are present;
- no enhancement layer is present;
- the base-layer compatibility ID is 4 (HLG);
- the underlying video is 10-bit HEVC/HLG; and
- the file has no audio or one AAC audio track.

Everything else is left unchanged. “No known issue found” means the confirmed trigger was not detected; it is not a universal compatibility guarantee.

## Quality and metadata

For the supported case, AndroidX Media3 transmuxes already-compressed video and audio samples into a new MP4. .vcp rejects the operation if Media3 reports that either stream was transcoded, so the compatible copy has no generation loss from video or audio re-encoding.

The copy deliberately omits the Dolby Vision container declaration while retaining the HEVC/HLG base stream. Auxiliary camera metadata and non-audio/video tracks may not be carried into the new container. The new Gallery item also has a new filename and storage date. Keep the original as the archival Dolby Vision file.

The controlled device results and exact media evidence are documented in [Media findings](docs/MEDIA_FINDINGS.md).

## Install

Download the APK attached to a published [GitHub Release](../../releases). Release assets use this naming scheme:

```text
vcp-<version>.apk       Android installer
vcp-<version>.aab       Store-distribution bundle; not directly installable
SHA256SUMS.txt          Artifact checksums
```

The current version is a pre-release. Android may ask you to allow installation from the browser or file manager used to open the APK.

Verify a downloaded APK before installing it:

```powershell
Get-FileHash .\vcp-0.4.0-alpha01.apk -Algorithm SHA256
```

Compare the result with the matching line in `SHA256SUMS.txt` from the same release.

## Privacy and permissions

.vcp has no internet permission, analytics, advertising, account system, backend, or crash-upload SDK.

| Permission | Why it exists |
| --- | --- |
| Video-library access | Requested only when automatic processing is enabled. Manual selection works without it. |
| Notifications | Requested only when the optional 30-day reminder is enabled. |
| Receive boot completed | Restores user-enabled event and reminder jobs after a reboot. |

Location metadata is deliberately not read. App data is excluded from cloud backup and device transfer. See the complete [privacy policy](PRIVACY.md) and [distribution notes](docs/DISTRIBUTION.md).

## Build from source

Requirements:

- JDK 17 or newer capable of running Gradle 9.7.1
- Android SDK Platform 37.0
- Android SDK Build Tools 36.0.0

Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

macOS or Linux:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. An unsigned, minified release APK is produced for local verification unless the documented release-signing environment variables are supplied.

## Project documentation

- [Architecture and safety invariants](docs/ARCHITECTURE.md)
- [Testing matrix](docs/TESTING.md)
- [Media findings](docs/MEDIA_FINDINGS.md)
- [Distribution and store policy](docs/DISTRIBUTION.md)
- [Maintainer release process](docs/RELEASING.md)
- [Changelog](CHANGELOG.md)

## Contributing and security

Read [CONTRIBUTING.md](CONTRIBUTING.md) before proposing a format rule or processing change. Test media must be self-created or explicitly redistributable; do not upload personal videos or unredacted media reports.

Report security concerns according to [SECURITY.md](SECURITY.md). Community participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
