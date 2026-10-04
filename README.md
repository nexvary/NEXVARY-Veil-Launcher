# NEXVARY Veil Launcher

Privacy-first Android launcher focused on **application identity virtualization**: apps can be represented inside the launcher with decoy names/icons, hidden from ordinary launcher surfaces, and revealed through user-defined authenticated actions.

## Status
Foundation / pre-alpha. Do not rely on Veil as the sole protection for sensitive data.

## Architecture direction
- Launcher foundation: Android Launcher3 / Lawnchair 16 concepts and compatible Apache-2.0 components where adopted.
- Veil Engine: NEXVARY-owned privacy layer kept logically separate from upstream launcher code.
- Local-first: disguise mappings and secret actions are stored locally; no cloud dependency is required.
- Android 15+: optional Private Space integration when Veil is the default HOME app and the platform permits access.
- Graceful fallback: visual hiding is never described as OS-level removal. Apps may remain visible in Android Settings or other privileged/system surfaces.

## Planned Veil Engine
1. Identity virtualization — per-app decoy label and icon.
2. Hidden-app policy — remove selected apps from normal drawer/search surfaces.
3. Secret Action Router — user-defined codes/gestures that resolve to protected targets.
4. Decoy surfaces — benign functional screens such as Notes/Clock/Calculator.
5. Veil profiles — Normal, Privacy, and Decoy presentation profiles.
6. Authentication gate — device credential/biometric/PIN policies.
7. Emergency Veil — rapidly return to a safe presentation state.
8. Android Private Space bridge — standards-compliant profile container integration where supported.

## Security principles
- No plaintext storage of secret codes.
- No network permission for the Veil Engine unless a feature explicitly requires it.
- Fail closed for protected launcher search results.
- Avoid deceptive claims: launcher hiding is distinct from OS-level app hiding.
- Threat model and limitations are documented before stable release.

## Upstream and licensing
Lawnchair 16 is being evaluated as the primary launcher foundation. Lawnchair documents its project license as Apache License 2.0. Any adopted upstream code will retain required copyright/license notices and significant modifications will be documented. Lawnchair names/logos/trademarks are not part of NEXVARY branding.

NEXVARY Veil Launcher is an independent project and is not endorsed by Lawnchair.

## Target
Android 15 and Android 16 first, with compatibility evaluated for earlier supported Android versions.

## Package
`com.nexvary.veil`
