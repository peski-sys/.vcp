# Privacy

Last updated: 2026-09-14

.vcp operates entirely on-device. Manual mode handles a video the user explicitly chooses through Android's system document picker. Optional automatic processing observes newly added MediaStore videos after the user enables it and grants full video access.

The app:

- does not request internet access;
- requests broad video access only when the user explicitly enables automatic processing;
- keeps manual processing available without broad library access;
- does not upload video, metadata, diagnostics, or usage data;
- does not include analytics, advertising, account, or crash-upload SDKs;
- explicitly disables AndroidX Media3 platform diagnostics;
- never modifies or deletes source media;
- creates a compatible copy only after an explicit manual action or for a new video while user-enabled automatic processing is active;
- deliberately does not read location metadata; and
- excludes app data from Android cloud backup and device-to-device transfer.

During processing, the app writes a temporary MP4 to its private cache and then copies it to an app-owned MediaStore item under `Movies/vcp`. The MediaStore item remains hidden with `IS_PENDING` while the app validates it. Failed or canceled work is removed. A small local recovery marker lets the next run remove an interrupted app-owned pending item; Android may also retain app-private cache data until storage cleanup if the process is terminated abruptly.

The published copy contains the source video's stream-copied HEVC/HLG picture and AAC audio. Its Dolby Vision container declaration is omitted, so players present the copy as ordinary HLG. Encoded samples can still contain unsignaled Dolby Vision RPU NAL units. Other auxiliary/container metadata may not be retained. The original remains untouched.

Automatic processing requires `READ_MEDIA_VIDEO` on Android 13 and newer, or `READ_EXTERNAL_STORAGE` through Android 12. Android 14 and newer may offer selected-only access, but that is insufficient for reliable event-driven detection; the app keeps automatic processing off unless full video access is granted. Enabling it records the current MediaStore generation as a baseline, so existing videos are skipped. A content-triggered `JobScheduler` job wakes only after MediaStore changes, examines bounded batches of newer video rows, ignores the app's own output, and returns to idle. The setting, generation checkpoints, and a count-only last-result message are stored in private preferences. Disabling automatic processing cancels its jobs.

The optional 30-day reminder is independent. On Android 13 and newer, `POST_NOTIFICATIONS` is requested only when the user turns the reminder on. Each successfully verified copy records an anonymous creation timestamp and random deduplication token in private preferences; no filename, media URI, or video metadata is stored. A persisted one-shot `JobScheduler` task asks Android to deliver a local notification after 30 days. Android may defer the reminder. Turning it off cancels the task and clears pending timestamps. The reminder never modifies or removes media.

The system document provider may retain a record of the user's recent selection and may grant this app persistent read access to the selected URI. Access ends if the provider revokes the grant, the document is moved or deleted, or the app is uninstalled.

No video, filename, inspection result, or automatic-processing result is sent off the device.
