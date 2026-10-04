# NEXVARY Veil Launcher

Privacy-first Android launcher focused on **application identity virtualization**: apps can be represented inside the launcher with decoy names/icons, hidden from ordinary launcher surfaces, and revealed through user-defined authenticated actions.

## Status
0.2.0 alpha development milestone. This is not a release candidate; see the security review and the latest CI for verified results and remaining device/OEM checks.

## Implemented paths
- Encrypted multi-PIN setup and PRIVACY / DECOY / optional NORMAL routing.
- Keystore AES-GCM config, salted PBKDF2-HMAC-SHA256 verifiers, persistent failed-attempt backoff, expiring memory-only sessions.
- Default fail-closed DECOY after recreation/process restart, manual lock and configurable emergency Search long-press.
- Profile allowlists and exact component/user visibility rules; adaptive grid, substituted labels and vector icon presets.
- Functional local Calculator, Notes and Clock; local Decoy Settings, including brightness, sound, language and appearance.
- LauncherApps exact-component launching and Android 15+ Private Space runtime gates, quiet-state filtering and separate lockable/hideable container.
- Arabic RTL and English resources, window insets and AndroidX gesture Back.
- Unit tests, Android 15 instrumentation gate, lint, debug artifacts and source security/leakage documentation.

## First use
1. Open Veil and choose Set up profiles.
2. Set distinct 6–12-digit Private and Alternative PINs, with confirmations. Limited PIN is optional.
3. In Control Center choose Use as default Home.
4. Configure each profile's app allowlist and presentation. Empty DECOY/NORMAL lists show only local utilities.
5. Long press the home clock to enter a PIN. Long press Search for Emergency Veil (can be disabled in Control Center).
6. Private Space needs Android 15+, the default Home role, an existing private profile and OS authorization. Android 16 adds its platform settings link.

No automatic destructive wiping. There is no PIN recovery bypass: remember both codes. Android system surfaces remain accessible in STANDARD. See [threat model](docs/THREAT_MODEL.md), [leakage matrix](docs/THREAT_MATRIX.md) and [security review](docs/SECURITY_REVIEW.md).

## Development checks
With Android SDK 36, JDK 17 and Gradle 8.13:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
gradle connectedDebugAndroidTest
```

Application ID: `com.nexvary.veil`. minSdk 28; compileSdk/targetSdk 36. Architecture remains the existing single Android application with logically separated core/auth/storage/bridge/decoy packages; no replacement foundation was introduced.

## Upstream and licensing
Lawnchair 16 is being evaluated as the primary launcher foundation. Lawnchair documents its project license as Apache License 2.0. Any adopted upstream code will retain required copyright/license notices and significant modifications will be documented. Lawnchair names/logos/trademarks are not part of NEXVARY branding.

NEXVARY Veil Launcher is an independent project and is not endorsed by Lawnchair.

## Target
Android 15 and Android 16 first, with compatibility evaluated for earlier supported Android versions.

## Package
`com.nexvary.veil`
