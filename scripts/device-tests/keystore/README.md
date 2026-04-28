# Device-test signing keystore

This is a public, intentionally weak debug keystore committed to the
repository on purpose. It is used **only** to sign the device-test APK
that the maintainer installs on their phone to resolve the
`device-test (real-hardware)` PR check.

Why a committed keystore:

- Each CI runner generates its own ephemeral debug keystore by default.
  That makes every device-test APK have a different signing certificate,
  so `adb install -r` rejects the new APK with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` until the previous version is
  uninstalled.
- A stable, committed keystore means every device-test APK across CI
  runs is signed by the same certificate, so reinstall just works.

Why not a real signing key:

- This APK is for one purpose only — exercising cn1-bluetooth on the
  maintainer's test device. It is never published. The keystore being
  public has no security cost.
- The keystore password is `android`, the alias is `androiddebugkey`,
  and the key password is `android`. These are the canonical Android
  debug-keystore values for a reason: they signal "this is not a
  release key" to anyone reading the code.

If you fork this repo for your own development, you can keep this
keystore as-is. Just don't reuse it for a publishable APK.
