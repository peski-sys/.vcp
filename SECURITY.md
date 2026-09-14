# Security policy

## Supported versions

The project is pre-release. Security fixes are applied to the latest revision only.

## Reporting a vulnerability

Use **Report a vulnerability** in the repository's Security tab when private vulnerability reporting is available. If it is unavailable, open a minimal public issue requesting a private contact channel and describe only the affected version and component.

Never post a private video, location data, device identifier, account detail, credential, or exploit payload in a public issue.

## Security boundaries

- Selected content URIs are untrusted input.
- MediaStore rows discovered by automatic mode are equally untrusted; permission does not imply format safety.
- ISO-BMFF box sizes and offsets must be validated before allocation or access.
- Parsing must remain bounded to container metadata; encoded payloads are not loaded for inspection.
- No source file may be overwritten or deleted.
- No output is considered successful until validation and atomic MediaStore publication complete.
- Automatic queries must remain generation-bounded, batch-limited, and exclude app-owned output to prevent recursive processing.
- Broad video access is optional, used only for local automatic detection, and must never become a prerequisite for manual mode.
- Network access is out of scope for the app.
