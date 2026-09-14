## Summary

Describe the user-visible change and the evidence supporting it.

## Safety boundary

Explain how the change preserves originals, handles untrusted media, and avoids unsupported fallback behavior.

## Verification

- [ ] `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleRelease` pass.
- [ ] Tests cover new parsing or decision behavior.
- [ ] Relevant physical-device checks are documented.
- [ ] Permissions and dependencies were reviewed.
- [ ] Documentation and `[Unreleased]` changelog entries are updated.
- [ ] No private media, local configuration, generated binaries, or signing material is included.
