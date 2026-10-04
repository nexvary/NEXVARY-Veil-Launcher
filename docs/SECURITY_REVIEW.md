# Source security review — 0.2.0 alpha

- HOME activity is the only exported Activity. Extras/deep links never select an authenticated profile. Utility and Decoy Settings activities are not exported.
- No broadcast or PendingIntent grants access to configuration. Only platform profile/screen broadcasts refresh/relock.
- No network permission, analytics SDK, telemetry or PIN logging. allowBackup=false. All app-controlled screens use FLAG_SECURE.
- PIN and policy persistence uses authenticated encryption, AtomicFile, non-exported Keystore keys and salted slow KDF. Error paths remain DECOY and suppress real applications.
- Protected controls recheck the in-memory session before mutations. Async PIN completion uses a generation token; emergency invalidates it. Recreate/process restart intentionally does not restore PRIVACY.
- App clicks re-evaluate current policy and exact component/UserHandle. Private users are excluded while quiet/locked; OS authorization remains required.
- Residual limitations: wall-clock backoff, in-memory EditText/String copies, third-party recents/notifications, changing launcher, OS Settings, rooted/ADB access. No claim of managed enforcement.
- Release blockers: physical supported device/private-profile validation; expanded process-kill and OEM tests; release signing identity; broader accessibility and font-scale UI review.
