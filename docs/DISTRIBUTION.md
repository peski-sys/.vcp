# Distribution

## GitHub Releases

GitHub Releases are the primary direct-distribution channel. A version tag matching `vcp.versionName` triggers the release workflow, which:

1. validates the tag, version code, changelog, Gradle wrapper, and signing configuration;
2. runs unit tests and Android lint;
3. builds a minified, signed APK and Android App Bundle;
4. verifies both signatures;
5. generates SHA-256 checksums; and
6. creates a draft GitHub Release for final maintainer review.

Only the signed APK is intended for direct installation. The AAB is intended for store submission. Signing keys and passwords are repository secrets and must never be committed. See [RELEASING.md](RELEASING.md).

## Google Play permission review

Automatic mode declares broad video access because its core function is to detect newly added incompatible videos without requiring the user to select every item. Android's picker is sufficient for manual mode but cannot provide event-driven access to every future video.

Google Play treats `READ_MEDIA_VIDEO` as restricted. A Play release that includes automatic mode must submit the Photo and Video Permissions declaration, describe automatic compatibility normalization as core functionality, explain why a picker cannot implement it, and provide a review video. Approval is not assumed.

If Play review does not accept that core-use justification, distribution requires a separate manual-only build variant without broad library access. Do not weaken the permission explanation or request access earlier merely to pass review.

Relevant policy and platform references:

- [Google Play restricted permissions and picker alternatives](https://support.google.com/googleplay/android-developer/answer/16935362)
- [Android granular media permissions](https://developer.android.com/about/versions/13/behavior-changes-13#granular-media-permissions)
- [Android permission-minimization guidance](https://developer.android.com/privacy-and-security/minimize-permission-requests)
- [`JobInfo.TriggerContentUri`](https://developer.android.com/reference/android/app/job/JobInfo.TriggerContentUri)

## Declared permissions

| Permission | Scope and reason |
| --- | --- |
| `READ_MEDIA_VIDEO` | Full video access on Android 13+; requested only when automatic mode is enabled. |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Detects selected-only grants on Android 14+ so the app does not claim full automatic coverage. |
| `READ_EXTERNAL_STORAGE` (maximum SDK 32) | Equivalent read access for automatic mode on Android 11–12. |
| `POST_NOTIFICATIONS` | Requested only when the optional 30-day reminder is enabled on Android 13+. |
| `RECEIVE_BOOT_COMPLETED` | Restores opted-in event and one-shot reminder jobs after reboot. |

The packaged app deliberately has no internet, network-state, wake-lock, foreground-service, all-files, write-storage, analytics, advertising, or account permission/dependency. CI fails if a prohibited permission is merged into the APK.

## Store disclosure

Every store listing and in-app disclosure must state that:

- inspection and compatible-copy creation occur locally;
- automatic mode is optional and manual mode works without broad access;
- only videos added after automatic mode is enabled are considered;
- originals are always kept;
- no media or metadata is collected, transmitted, sold, or shared; and
- automatic mode can be disabled and video access can be revoked at any time.
