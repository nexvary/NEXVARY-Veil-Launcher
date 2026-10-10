# Source security review — 0.2.0 alpha

- HOME activity is the only exported Activity. Extras/deep links never select an authenticated profile. Utility and Decoy Settings activities are not exported.
- No broadcast or PendingIntent grants access to configuration. Only platform profile/screen broadcasts refresh/relock.
- No network permission, analytics SDK, telemetry or PIN logging. allowBackup=false, legacy backup exclusions and Android 12+ cloud/device-transfer exclusions. Sensitive launcher/configuration/PIN screens use FLAG_SECURE; decoy utilities keep ordinary screenshot behavior.
- PIN and policy persistence uses authenticated encryption, AtomicFile, non-exported Keystore keys and salted slow KDF. Error paths remain DECOY and suppress real applications.
- Protected controls recheck the in-memory session before mutations. Async PIN completion uses a generation token; emergency invalidates it. Recreate/process restart intentionally does not restore PRIVACY.
- App clicks re-evaluate current policy and exact component/UserHandle. Private users are excluded while quiet/locked; OS authorization remains required.
- Residual limitations: wall-clock backoff, in-memory EditText/String copies, third-party recents/notifications, changing launcher, OS Settings, rooted/ADB access. No claim of managed enforcement.
- Release blockers: physical supported device/private-profile validation; physical OEM process/reboot tests (a real emulator termination/reboot harness now gates CI); release signing identity; broader accessibility and font-scale UI review.

## 0.3.0-alpha01 home/drawer revision

- Saved home and dock entries contain exact component/user identities, inside the existing authenticated encrypted configuration. Older configurations migrate to local tools only; no real DECOY apps are auto-approved.
- Home and dock intersect their ordered keys with the current policy-filtered catalog. Clicks re-resolve the exact identity and recheck the session. Pinning never grants visibility or launch authority.
- Shortcut edits remain in the protected center and recheck PRIVACY before mutation. Android Private Space entries remain separate from ordinary pins.
- Search opens a distinct drawer. Back, emergency and session expiration rebuild the correct home; private labels and queries are not restored from saved instance state.
- New tests cover drawer controls and gesture, encrypted profile-specific order, hidden pinned identities, and real shortcut selection/reordering via the management UI.
- Runtime validation for this revision must be recorded after CI; the alpha04 result is not evidence for this code. Permanent signing, physical OEM verification, broader accessibility review, custom icon import and managed enforcement remain outstanding.
