# Veil Threat Matrix

NEXVARY Veil treats concealment as layered privacy, not as a claim that an ordinary launcher can replace Android security boundaries.

## Security modes

### Standard
Default HOME launcher. Controls Veil-owned surfaces: app drawer, search, labels, icons, shortcuts, decoy entries and profile presentation.

### Private Space
Android 15+ integration. Private-profile apps are placed in a separate launcher container. When the profile is locked, Veil must not expose those apps through its drawer or search.

### Managed
Optional deployment for devices intentionally provisioned for enterprise/device management. Device-policy and Lock Task capabilities are enabled only when Android grants the required management role. Standard installs must never pretend to have these controls.

## Threat matrix

| Surface / scenario | Standard | Private Space | Managed | Design response |
| --- | --- | --- | --- | --- |
| Veil app drawer | Strong | Strong | Strong | Central VisibilityDecision |
| Veil search | Strong | Strong | Strong | Same policy source; no alternate index |
| App labels/icons | Strong inside Veil | Strong inside Veil | Strong inside Veil | Presentation virtualization |
| Decoy/Duress profile | Strong inside Veil | Stronger with isolated apps | Strongest | Separate profile policy and believable content |
| Settings icon in Veil | Decoy/redirect only | Decoy + private profile lock | Can add policy restrictions | Never claim OS Settings is disabled in Standard |
| Quick Settings | OS controlled | OS controlled | Restrict only where policy permits | No fake guarantee |
| Recents | Limited | Private apps absent while locked | Policy-dependent | Avoid sensitive Veil previews |
| Notifications | Limited | Private apps stop/hidden while locked | Policy-dependent | Keep sensitive apps in Private Space |
| Share sheet / file picker | OS controlled | Isolation helps | Policy-dependent | Do not rely on launcher-only hiding |
| Installed-app listings | Not guaranteed | Better isolation, still not forensic hiding | Policy-dependent | Document residual exposure |
| USB / ADB | Not controlled | Not a forensic boundary | Can restrict on managed devices where supported | Treat as separate threat |
| Safe mode / launcher replacement | Not guaranteed | Not guaranteed | Stronger policy options | Recovery-safe design |
| Reboot | Veil state must fail closed | Private Space remains separate | Policy-dependent | Restore least-privilege profile |
| Shoulder/coercion scenario | Decoy profile | Decoy + locked private profile | Decoy + managed restrictions | Fast profile transition; no destructive default |

## Duress profile invariant

A Duress profile is a complete presentation policy, not merely an alternate wallpaper:
- independent visible-app allowlist;
- independent disguises and decoy destinations;
- no sensitive search results;
- no sensitive shortcuts or suggestions;
- decoy Settings entry owned by Veil;
- immediate Private Space lock when available and explicitly configured;
- non-destructive by default;
- recovery path known only to the owner.

## Engineering rules

1. Every Veil-owned discovery surface consumes the same VisibilityDecision.
2. HIDDEN means not rendered, searchable, suggested or shortcut-exposed by Veil.
3. DECOY never launches the protected target on a normal tap.
4. Standard mode never claims to disable system Settings, Quick Settings, ADB or other OS surfaces.
5. Managed-only controls are capability-gated at runtime.
6. Security state must fail closed after process death/reboot.
7. Sensitive data must live in an isolated/encrypted boundary; changing an icon is not data protection.
8. No destructive duress action is enabled by default.
