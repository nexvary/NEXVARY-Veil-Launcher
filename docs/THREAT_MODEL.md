# NEXVARY Veil threat model — 0.2.0 alpha

## Security boundaries
Concealment changes presentation in Veil. It does not change installed Android package identity. Isolation belongs to Android Private Space. Policy enforcement belongs to provisioned device owners. This build implements STANDARD and a runtime-gated Private Space bridge; it does not claim MANAGED enforcement.

Veil is not a replacement lock screen, a custom ROM, forensic-proof, root-proof or ADB-proof. A person using Quick Settings, Android Settings, another launcher, permission dialogs, notifications or Recents may discover primary-profile apps. A decoy cannot guarantee protection under coercion; real utility notes and app content must not contain sensitive data.

## Authentication and persistence
Distinct 6–12-digit PRIVACY, DECOY and optional NORMAL PINs use PBKDF2-HMAC-SHA256, 210,000 iterations and independent 32-byte salts. All configured verifiers are evaluated before returning a match. Binding metadata and policies are stored together with AES-GCM using an Android Keystore key and AtomicFile. No network, analytics or crash reporting SDK is installed. No plaintext PINs are persisted or logged. Mutable PIN buffers are erased, although Android EditText/JVM may temporarily hold immutable copies: absolute memory erasure is not claimed.

Five failed attempts begin an encrypted persistent backoff, increasing to 16 minutes. It uses wall time across restarts; a user controlling the OS clock or rooted storage can weaken it. A hardware-backed PIN retry counter is not claimed. Configuration integrity/decryption failures deny all real app enumeration. The launcher never silently creates a new encryption key over an existing ciphertext.

Sessions are memory-only and expire against elapsed realtime. New activities/processes and reboot start DECOY. Rotation deliberately relocks. Screen-off triggers local DECOY and attempts to lock private profiles. Manual lock and configurable emergency long-press clear the launcher view and dismiss its tracked sensitive dialogs. Timeout is checked on resume, render and click, including app policy re-resolution at click time. UI expiry clears search and private-grid state. Standard launcher app launching is not a continuing guard over third-party app content.

## Routing
PRIVACY lists permitted policy results; DECOY and NORMAL use explicit per-profile allowlists, empty by default. App visibility rules include exact component and Android user serial. HIDDEN results are never presented by the catalog. DISGUISED uses a label and vector-icon preset but opens the exact real component/user. DECOY routes to a local utility. Settings within DECOY routes to local decoy settings, including the platform-resolved Settings package and the com.android.settings fallback. The launcher does not implement OS shortcuts or suggestions yet and does not publish sensitive ones.

## Private Space
Official references:
- https://developer.android.com/about/versions/15/behavior-changes-all#private-space
- https://developer.android.com/reference/android/content/pm/LauncherApps
- https://developer.android.com/reference/android/os/UserManager

Requires API 35+, ACCESS_HIDDEN_PROFILES and ROLE_HOME. Classification uses getLauncherUserInfo/userType. Private applications are placed in a separate container only during PRIVACY, never in the main search/grid. Quiet or locked users are not enumerated. Lock/unlock requests use UserManager.requestQuietModeEnabled and never report success based only on issuing a request. Failed duress-lock requests retain local concealment and report the unconfirmed OS lock in the authenticated center. Profile available/unavailable broadcasts refresh UI. Android 16's private-space settings IntentSender is accessible only through the protected center.

Physical-device and provisioned-private-profile testing are required before release. Unknown secondary-profile classification is treated as private. The separate container has lock/show/hide controls; OS authentication can be required for unlock.

## Recovery and non-goals
No automatic destructive wipe and no PIN-reset bypass. Forgotten PINs require choosing another HOME through Android settings and reinstalling Veil; this loses Veil configuration and utility notes, not other installed apps. Do not add covert surveillance, exfiltration or accessibility-based blocking. Custom arbitrary image icons, managed provisioning, backup export and advanced folder/shortcut features are outside this milestone.
