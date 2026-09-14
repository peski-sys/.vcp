# Media findings

## Scope

These findings apply to the tested iPhone-originated files on a Pixel 10 with the tested Google Photos build. They identify a reproducible trigger; they are not a statement that Android cannot play Dolby Vision.

## Broken source

The iPhone HDR-on source reported:

- QuickTime/MOV container (`qt`)
- HEVC `hvc1`, Main 10, 10-bit, YUV 4:2:0
- BT.2020 primaries, HLG transfer, limited range
- Dolby Vision Version 1.0, Profile 8.4, `dvhe.08.05`
- Backward-compatible base layer plus RPU, no enhancement layer
- `hvcC` and `dvvC` configuration
- AAC-LC stereo audio
- Portrait rotation metadata and variable frame timing

It played correctly, but Google Photos did not generate a thumbnail and could not enter the editor.

## Working controls

Three controls narrowed the issue:

1. High Efficiency with HDR disabled produced HEVC without Dolby Vision and worked.
2. Most Compatible produced AVC/H.264 and worked.
3. The HDR-on file remuxed while stripping Dolby Vision RPU/configuration worked, while retaining the original HEVC Main 10, BT.2020 HLG video samples.

A remux that preserved the Dolby Vision Profile 8.4 configuration continued to fail.

## Conclusion

For this test matrix, the trigger is Dolby Vision Profile 8.4 metadata/signaling, not MOV by itself, HEVC by itself, 10-bit video by itself, HLG by itself, AAC audio, frame rate, or orientation.

The narrow compatible-copy strategy is therefore:

```text
Dolby Vision Profile 8.4 (HEVC HLG base + RPU)
        ↓ stream copy / transmux
HEVC Main 10 HLG in MP4, without a Dolby Vision container declaration
```

This does not re-encode frames, so it avoids generational compression loss. The in-app path preserves the encoded samples—including any in-band RPU NAL units—but omits the `dvvC` declaration that makes Android identify the track as Dolby Vision. The compatible copy is therefore presented as ordinary 10-bit HLG; the original remains the archival Dolby Vision version.

## Evidence still required on-device

The in-app Media3 output was validated on the test Pixel 10 on 2026-09-14 using a self-recorded iPhone Profile 8.4 portrait clip (1080p60, AAC stereo):

- The app completed its internal container, track, color, duration, audio, and first-frame checks.
- The published MP4 contained `hvc1` + `hvcC` and no Dolby Vision configuration declaration.
- Google Photos generated a thumbnail and opened the output in its editor.
- The selected MOV remained present and unchanged.

This confirms that omitting the Dolby Vision container declaration is sufficient for the tested Pixel/Photos combination while the encoded HEVC/HLG and AAC samples remain stream-copied. It does not establish universal behavior across Android devices or gallery versions.

Before automatic normalization is considered broadly validated:

- Test 24/30/60 fps, landscape/portrait, longer clips, no-audio clips, edited iPhone exports, and interrupted processing.
- Repeat the successful case on additional Pixel/Google Photos versions and at least one non-Pixel Android device.
