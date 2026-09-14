# Testing

## Automated checks

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Unit tests cover:

- QuickTime `ftyp` parsing.
- Dolby Vision Profile 8.4 configuration parsing.
- RPU, base-layer, enhancement-layer, and HLG compatibility flags.
- HEVC 10-bit configuration parsing.
- Rejection of invalid/unbounded box markers.
- Constant, variable, and insufficient presentation-timing samples.
- Compatibility decisions for the known-broken file, known-working HLG control, and incomplete container access.
- Normalization eligibility for the exact HLG-compatible base layer and rejection of unproven variants.
- HEVC-only muxer capability reporting and final Dolby Vision-to-HEVC track declaration normalization.
- Safe output naming.
- Output validation for Dolby Vision container-declaration removal, HEVC/HLG preservation, audio channels, and duration.
- Automatic candidate filtering for pre-enable media, app-owned output, output-directory recursion, and newly transferred videos.
- Thirty-day reminder due-date calculation, oldest-event scheduling, and malformed-event rejection.

## Device matrix

Record the device, Android build, Google Photos version, source device, transfer method, and file result. Start with the Pixel 10 and both iPhone controls.

### HDR-on original

Expected inspector evidence:

- QuickTime major brand
- `hvc1` sample entry
- `hvcC` and `dvvC`
- `dvhe.08.05`
- Profile 8, Level 5
- RPU: yes
- Base layer: yes
- Enhancement layer: no
- Base-layer compatibility ID: 4 (HLG)
- 10-bit, BT.2020, HLG, limited range where Android reports those values
- Verdict: known trigger detected

### HDR-off original

Expected evidence:

- QuickTime major brand
- HEVC sample entry/config
- No Dolby Vision config
- 8-bit/BT.709 where reported
- Verdict: no known trigger detected

### Successful stripped remux

Expected evidence:

- MP4/ISO base media brand
- `hvc1` and `hvcC`
- No `dvvC`, `dvcC`, or `dvwC`
- HEVC Main 10, 10-bit, BT.2020 HLG
- Verdict: no known trigger detected, matching the working HLG base control

### In-app compatible copy

1. Inspect the HDR-on original and tap **Create compatible copy**.
2. Verify all three stages appear: remuxing, saving, and validation.
3. Verify success identifies the `_compatible.mp4` filename and that the item appears under `Movies/vcp`.
4. Open the new copy and confirm playback, thumbnail generation, and Google Photos editing.
5. Reinspect the new copy and verify `hvc1` + `hvcC`, HEVC Main 10, BT.2020 HLG, and no Dolby Vision configuration.
6. Verify the original remains present and byte-for-byte untouched.

Status: the 1080p60 portrait Profile 8.4 + AAC case passed all six checks on the test Pixel 10 on 2026-09-14. The remaining matrix must still be completed; one passing clip is not a universal compatibility claim.

## Manual behavior checks

- Canceling the picker leaves the previous screen unchanged.
- Rotating the device during inspection does not crash or present a stale prior result.
- Reopening the app restores a selected document when the provider supports persistent access.
- Selecting a remote-only/unreadable item returns a safe error and changes no file.
- HDR-off, native Pixel, unproven Dolby Vision, multi-audio, and non-AAC files never show the normalization action.
- Canceling normalization removes its hidden pending output and leaves the original unchanged.
- Leaving the app while normalization is active cancels it; rotating the device does not.
- A failed validation never produces a visible Gallery item.
- Relaunching after interrupted work cleans the app-owned pending item and private staging file.
- A track for which Android exposes no sample timestamps reports timing as unavailable, never as a completed zero-frame track.
- Light and dark themes remain readable.
- Long filenames remain readable and the compact result remains scrollable.
- No network permission appears in the merged manifest.

## Reminder device checks

1. Verify the reminder is off by default and automatic mode remains independent.
2. Enable it. On Android 13+, verify Android requests notification permission only at this point.
3. Deny the permission and verify the switch remains off and no reminder job is scheduled.
4. Grant the permission, create one compatible copy, and verify a single persisted reminder job is scheduled without storing a filename or URI.
5. Force a due event in a debug-only test environment and verify the notification opens .vcp, reports only a count, and does not modify either video.
6. Turn the reminder off and verify its job and pending timestamps are cleared.

## Automatic-mode device checks

1. Install fresh or turn automatic mode off, then verify no watcher job is scheduled.
2. Enable automatic mode and grant **full** video access. On Android 14+, selected-only access must leave the feature off.
3. Verify existing videos are not processed.
4. Add an HDR-off video and verify it is inspected then skipped without creating an output.
5. Add a fresh supported iPhone Profile 8.4 video and allow JobScheduler to run. Verify exactly one compatible copy appears and the original remains.
6. Verify creating the output does not trigger recursive copies.
7. Force the processing job with `adb shell cmd jobscheduler run -f dev.compatvideo.debug 1129270609` and inspect job state with `adb shell dumpsys jobscheduler dev.compatvideo.debug`.
8. Exercise battery-low, storage-low, reboot, permission revocation, job stop, and process-kill cases. Confirm incomplete pending output is cleaned and no source is removed.

Automatic processing is an alpha feature until this matrix passes on the target Pixel. Job timing is controlled by Android and is intentionally not promised as instantaneous.

Pixel 10 result on 2026-09-14: full-access opt-in scheduled only the watcher; a forced empty event ran and exited without output; a subsequent newly transferred supported video produced exactly one internally verified compatible copy; the processor exited, the generation checkpoint advanced, the watcher re-armed without recursive output, and the user confirmed the automatic copy had a thumbnail and worked in Google Photos editing.

## Test-data policy

Do not commit personal videos, filenames, or MediaInfo reports containing GPS coordinates or private MediaStore paths. Synthetic MP4 box bytes belong in unit tests; redistributable video fixtures require documented provenance and license.
