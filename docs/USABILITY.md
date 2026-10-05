# Veil alpha03 interaction guide

The first launch explains the private and ordinary-looking environments before showing any PIN fields. Setup has three steps: private PIN and confirmation, distinct alternative PIN and confirmation, then a review explaining how to return to the private environment and configure apps. The limited PIN is optional. These PINs control Veil only; Android's screen-lock credential is separate.

After setup, the protected Control Center opens. Choose **App visibility**, then the environment, search for an installed app and tap its row. The form explains Real, Hidden, Disguised and Decoy; ordinary and limited environments use their own allowlists. Disguised opens the real app with a different Veil label/icon. Decoy opens the selected local utility. Hidden removes the app from Veil's own surfaces. Android system surfaces remain outside STANDARD control.

The concealed home keeps ordinary utility icons, app search and the clock. Long-press the home clock to enter a PIN. Long-press search for Emergency Veil when enabled. These instructions appear only during initial setup and in the protected center; the configured DECOY home contains no protected configuration entry or explanatory privacy banner.

Home uses compact native icons in an adaptive grid. Local tools have compact mirrored Back navigation. Calculator includes functioning arithmetic keys, clear, delete and result actions. Notes has a large editing area and save feedback. Clock includes optional seconds and a working start/pause/reset stopwatch. Local Settings groups display and sound controls, persists its appearance selection and includes language, date/time and device details; its brightness and appearance apply to this screen. It does not block Android Settings from external system surfaces.

English and Arabic use resources and locale layout direction. Mathematical keys deliberately retain conventional left-to-right order. Sensitive pages keep FLAG_SECURE, PIN fields disable saved view state, and recreation still returns to the concealed environment.

## Verification

The alpha03 changes extend the instrumented suite with actual three-step PIN setup, calculator result and clock/stopwatch interaction. The suite publishes rendered welcome/setup, home, control-center and local utility screenshots for visual inspection. GitHub's gate remains responsible for unit tests, lint, APK assembly and emulator tests on Android 14, 15 and 16, plus authenticated force-stop and reboot/re-authentication checks. A release candidate additionally requires physical-device/OEM review and persistent secure signing; passing this UI milestone does not imply that those steps are complete.
