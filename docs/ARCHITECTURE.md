# Veil Engine Architecture

## Boundary
Veil Engine must not depend on a particular launcher UI. Launcher surfaces query Veil Engine for a presentation decision before rendering or launching an app.

## Core model
- AppIdentity: package/component/profile identity.
- VeilPresentation: REAL, DISGUISED, HIDDEN, DECOY.
- DisguiseRule: target identity + decoy label/icon + activation profile.
- SecretRoute: protected trigger digest + destination + authentication policy.
- VeilProfile: NORMAL, PRIVACY, DECOY.
- VisibilityDecision: renderer/search decision generated centrally.

## Modules (planned)
- `veil-core`: pure policy/model code; unit-testable.
- `veil-storage`: encrypted local persistence and migration.
- `veil-auth`: biometric/device credential/PIN gate.
- `veil-launcher-bridge`: adapter between Launcher3/Lawnchair models and Veil decisions.
- `veil-decoys`: functional decoy activities.
- `veil-private-space`: Android 15+ hidden-profile integration.
- `app`: launcher UI and settings.

## Critical invariant
All launcher entry points (drawer, search, suggestions, shortcuts, recents exposed by launcher APIs) must use the same VisibilityDecision source. A protected app must not disappear from the drawer but leak through launcher search.

## Secret codes
Codes such as #1# are interpreted inside Veil-controlled input surfaces. Veil does not depend on intercepting the system dialer. Stored triggers use a memory-hard/KDF-backed verifier with per-record salt; raw codes are not persisted.

## Android Private Space
On Android 15+, Private Space visibility requires Veil to hold ROLE_HOME and declare ACCESS_HIDDEN_PROFILES. Locked private-space apps must not be discoverable through launcher search. Platform APIs remain the authority for profile lock state.

## Threat boundary
Veil protects launcher-level discovery and casual/forced navigation scenarios. It does not claim to conceal installed packages from Android Settings, ADB, device administrators, forensic tooling, or a compromised/rooted OS.
