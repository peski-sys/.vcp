# Architecture

## Principles

1. Inspect before changing anything.
2. Treat “no known trigger” as narrower than “universally compatible.”
3. Prefer stream copy/remux over decode/encode.
4. Never modify or delete originals.
5. Keep video data local and avoid permissions until a feature truly needs them.
6. Keep idle work at zero; automatic processing uses MediaStore change triggers rather than polling.

## Inspection components

### `AndroidMediaInspector`

Coordinates Android's `MediaMetadataRetriever` and `MediaExtractor`. It reads source metadata, track formats, and a bounded set of presentation timestamps. Timing inspection advances sample metadata only; it does not decode video frames.

### `IsoBmffReader` and `IsoBmffParser`

`IsoBmffReader` walks top-level file boxes using positional reads. It skips `mdat`, seeks directly to `ftyp` and `moov`, and refuses to allocate more than 32 MiB for container metadata.

`IsoBmffParser` recognizes relevant sample entries and configuration records:

- `avc1`, `avc3`, `hvc1`, `hev1`
- `dvav`, `dva1`, `dvhe`, `dvh1`
- `avcC`, `hvcC`, `dvcC`, `dvvC`, `dvwC`

For Dolby Vision it exposes version, profile, level, RPU/base/enhancement-layer flags, and base-layer signal compatibility ID. For HEVC it exposes profile IDC, level IDC, chroma format, and luma/chroma bit depth.

The scan validates declared box sizes and only scans within `moov`. This avoids mistaking matching bytes in encoded media payloads for metadata boxes.

### `CompatibilityAnalyzer`

Pure decision logic with three outcomes:

- `NORMALIZATION_RECOMMENDED`: exact confirmed Dolby Vision Profile 8 base-layer case.
- `NO_KNOWN_TRIGGER`: the proven trigger was not found; not a universal compatibility guarantee.
- `INCONCLUSIVE`: a video exists, but container evidence is unavailable or an untested Dolby Vision form was found.

The confirmed Profile 8 + compatibility ID 4 strategy is the supported lossless base-layer remux. The analyzer never asks for a broad H.264 transcode when a stream copy is sufficient.

### Presentation

`InspectionViewModel` owns selection and normalization state. Blocking media and storage I/O runs on `Dispatchers.IO`; Media3 Transformer control stays on its required application looper. A new selection cancels presentation of any prior inspection. The persisted document URI is restored after configuration/process recreation when the provider continues to grant access.

`MainActivity` renders a deliberately minimal result and handles the system picker and runtime permission launchers. Detailed inspection facts remain an internal safety boundary instead of becoming a user-facing technical report. There is no database, dependency injection framework, navigation framework, continuously running service, or network stack.

## Manual normalization

Normalization is isolated behind these components:

```text
NormalizationPlanner(MediaFacts) -> NormalizationPlan?
ManualVideoNormalizer.execute(inputUri, plan) -> private staged MP4
MediaStoreVideoPublisher -> hidden pending output
OutputValidator + first-frame decode -> verified output
MediaStoreVideoPublisher.publish -> visible Gallery URI
```

Required transaction semantics:

1. Never write over the source URI.
2. Write to app-private staging storage.
3. Require Media3 to report video and audio as transmuxed; reject any transcode fallback.
4. Insert a new MediaStore item using `IS_PENDING` and copy the private output into it.
5. While it is hidden, validate container boxes, codec/sample entry, bit depth, HDR/color signaling, duration, dimensions, orientation, audio, and first-frame decoding.
6. Clear `IS_PENDING` only after every check passes.
7. Remove incomplete app-owned output on failure or cancellation; never remove the source.

The known Profile 8.4 case uses Media3's backward-compatible Dolby Vision base-layer transmux support with output video MIME `video/hevc`. `HevcBaseLayerMuxerFactory` delegates to Media3's default MP4 muxer, advertises only HEVC for video, and normalizes any remaining Dolby Vision track declaration to HEVC at the final `addTrack` boundary. The writer therefore emits `hvc1` + `hvcC`, not `dvvC`, while copying the encoded samples unchanged. No tone mapping or video/audio encoding is allowed for the proven Pixel path. Media3 platform diagnostics are disabled.

Only Profile 8 with RPU + base layer, no enhancement layer, and base-layer compatibility ID 4 is eligible. The current implementation also limits audio to zero or one AAC track so that it can prove audio was preserved without re-encoding. Everything else remains inspection-only.

The operation is foreground/manual. Leaving the app cancels it (configuration changes are exempt). A recovery marker identifies an app-owned pending MediaStore URI after process interruption; the next launch deletes it if it is still pending. Private staging files use a dedicated name prefix and are also cleaned on launch.

## Automatic mode

Automatic mode is explicitly enabled and requires full video-library access. Manual mode retains the system-picker path and works when the permission is declined.

```text
MediaStore video change
        ↓
MediaChangeJobService (content trigger only)
        ↓
AutomaticProcessingJobService (battery/storage-not-low constraints)
        ↓
generation/version query, at most 24 rows
        ↓
existing item? app output? unsupported facts? → checkpoint and skip
        ↓ exact supported case
shared ManualVideoNormalizer transaction → verified Gallery copy
```

`JobInfo.TriggerContentUri` is consumed each time it fires, so the watcher re-arms itself. It is not periodic or persisted. `AutomaticModeBootReceiver` re-arms it after a reboot only when the private opt-in preference is still enabled; opening the app also reconciles the schedule.

Enabling captures `MediaStore.getVersion()` and `getGeneration()` as a baseline. The scanner queries `GENERATION_MODIFIED` after its checkpoint but only considers rows whose `GENERATION_ADDED` is newer than the baseline. That combination avoids sweeping the existing library and still catches a newly inserted pending item after another app publishes it. A MediaStore version reset establishes a new baseline rather than processing the whole library.

The scanner excludes this package plus the current `Movies/vcp` and legacy `Movies/Compat Video` output paths, advances checkpoints for unsupported or malformed items so one file cannot create an infinite retry loop, and stores count-only status rather than source filenames. Manual and background normalization share a process-wide mutex. The processing job is cancellable and returns to JobScheduler for retry if Android stops it; the existing recovery marker removes an interrupted pending output before the next attempt.

## Optional original reminder

The reminder is independent of automatic processing. When enabled, each successfully verified copy records an anonymous timestamp with a random deduplication token in private preferences. No filename, content URI, or media metadata is stored. A persisted, one-shot `JobScheduler` task is scheduled for the oldest timestamp plus 30 days. When Android runs it, one local notification summarizes the due count, removes those timestamps, and schedules the next due event.

Android 13 and newer require `POST_NOTIFICATIONS`; the permission is requested only when the user enables the reminder. Turning the setting off cancels the job and clears pending reminder timestamps. Reminder delivery is inexact because Android controls background scheduling. It never modifies or removes media.
