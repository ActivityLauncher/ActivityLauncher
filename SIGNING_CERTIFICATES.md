# Signing certificates

Activity Launcher is published under several package names and, historically, with several signing
keys. The app declares its permission trust in the manifest with
[`knownSigner`](https://developer.android.com/about/versions/12/features#known-signer), so the
trusted certificate digests are part of the installed APK rather than fetched at runtime.

## Manifest contract

The `distribution` flavor dimension has two values, `oss` and `playStore`. Each distribution declares
only its own permission namespace:

| Distribution | Declared permissions |
|--------------|----------------------|
| `playStore` | `de.szalkowski.activitylauncher.permission.CREATE_SHORTCUT`, `de.szalkowski.activitylauncher.permission.LAUNCH_ACTIVITY` |
| `oss` | `de.szalkowski.activitylauncher.oss.permission.CREATE_SHORTCUT`, `de.szalkowski.activitylauncher.oss.permission.LAUNCH_ACTIVITY` |

The Play declarations live in
[`app/src/playStore/AndroidManifest.xml`](app/src/playStore/AndroidManifest.xml); Play free and Pro
share one signing key. The OSS declarations live in
[`app/src/oss/AndroidManifest.xml`](app/src/oss/AndroidManifest.xml); both GitHub OSS and F-Droid are
built from this distribution. Because those two OSS builds use different signing keys and different
package IDs, install only one of them at a time; declaring the same OSS permission names from both
would cause `INSTALL_FAILED_DUPLICATE_PERMISSION`.

Both sets use `android:protectionLevel="signature|knownSigner"` and
`android:knownCerts="@array/trusted_signing_certificates"`. The digest array is
[`app/src/main/res/values/untranslatable.xml`](app/src/main/res/values/untranslatable.xml). Every
first-party build requests both namespaces, so a plugin can expose one alias per family and Activity
Launcher can filter handlers to the family it actually holds.

On Android 12 (API 31) and newer, the system grants each declared permission to any requesting app
whose current or lineage signing certificate matches one of these digests. On older Android versions,
`knownSigner` and `knownCerts` are ignored: the permissions behave as plain `signature`, so only apps
signed with the declaring distribution's key receive them. The app still builds with `compileSdk 37`
and installs on older devices; the only lost behavior is cross-key first-party access on those older
devices.

A component with `android:permission` is still callable by its own app because Android grants a UID
blanket access to components it owns. A first-party build can therefore launch its own protected
aliases even when no other first-party build supplied a grant. That self-access is not a grant of the
permission: third-party callers remain denied.

## Trusted certificates

The digests are SHA-256 certificate fingerprints, uppercase hex without separators. Digests must be
plain string values in the `knownCerts` array; colons are not accepted by the platform.

| Certificate SHA-256 | Package / source | Why it is trusted |
|---------------------|------------------|-------------------|
| `76ADD486A275E14096D7C9AABE25A5266D19C761B9427B60BB596F79A03ED4F7` | Current Google Play free (`de.szalkowski.activitylauncher`), current Google Play Pro (`de.szalkowski.activitylauncher.pro`), and current GitHub OSS releases (`de.szalkowski.activitylauncher.oss`) | Current first-party release key. Verified against the published Play APKs and the project's own GitHub releases. |
| `19C1562D74E33BFFDAF2E4B5D96DA26587934A6F73A162E0A96DCE564C200960` | Historical Google Play Pro | Older Pro releases used this key before moving to the current first-party key. It remains trusted so legacy Pro installs keep working. |
| `2015709D519FD686B1E37B334F12082CBAFCB9F5126A68A25B172043EBB581DE` | Official F-Droid build of `de.szalkowski.activitylauncher` | F-Droid builds and signs the published source with the F-Droid project key. Verified from the F-Droid index and reproducibility records. |
| `45648A8BB6CBC4F5FD39CD9C12BD250F2585617CA518F4778EA89C75495B9298` | Historical GitHub OSS releases | Older OSS releases were published with this key. It remains trusted so legacy installs keep working. |

Only these verified first-party keys are listed. Unverified or third-party fingerprints must not be
added: any listed digest is permanently treated as a first-party caller by every installed Activity
Launcher build on API 31+.

## Adding or rotating a key

1. Verify the fingerprint from the publishing source (store listing, signed index, or release
   artifact) and record the source in the table above.
2. Add the uppercase, separator-free digest to
   [`known_signing_certificates.xml`](app/src/main/res/values/known_signing_certificates.xml).
3. Update [`PLUGIN_ARCHITECTURE.md`](PLUGIN_ARCHITECTURE.md) and
   [`examples/plugin/AndroidManifest.xml`](examples/plugin/AndroidManifest.xml) if a new package name
   needs to be requested by plugins.
4. Run the build and instrumented tests.
