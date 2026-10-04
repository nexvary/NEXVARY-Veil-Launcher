# Threat Model

## Primary scenarios
1. A thief obtains an unlocked phone and immediately browses launcher apps.
2. A nearby person is temporarily handed an unlocked phone.
3. A user wants a benign launcher presentation while preserving rapid authenticated access to selected apps.

## Protected assets
- Discoverability of selected apps from Veil surfaces.
- Decoy mappings and secret routes.
- Accidental disclosure through launcher search/suggestions.

## Non-goals
- Defeating Android Settings, ADB, root, MDM/device-owner administrators, or forensic extraction.
- Replacing full-disk/file-based encryption.
- Modifying or impersonating the installed package at OS level.
- Capturing system-dialer secret codes.

## Required controls
- Central visibility policy for every launcher surface.
- Authenticated transition out of Decoy/Privacy modes.
- Encrypted-at-rest configuration.
- No secrets in logs, analytics, crash breadcrumbs, backups, or screenshots controlled by Veil.
- Rate limiting/backoff for secret-code attempts.
- Explicit recovery path so users cannot permanently lock themselves out.

## Abuse resistance
Veil must remain a user-controlled launcher privacy tool. Features must not silently monitor other people, exfiltrate credentials/data, or conceal background surveillance.
