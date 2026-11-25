[![Debug Build](https://github.com/ActivityLauncher/ActivityLauncher/actions/workflows/android-master.yml/badge.svg)](https://github.com/orgs/ActivityLauncher/repositories)
[![Discord](https://img.shields.io/badge/Discord-Join%20our%20server-5865F2?logo=discord&logoColor=white)](https://discord.gg/gzEugJBNMr)

# Activity Launcher

Launches hidden activities and creates shortcuts for installed apps.

This organization contains the app's source code and related projects like
plugins for launching (e.g.,
[Shizuku Launch Plugin](https://github.com/ActivityLauncher/ActivityLauncherShizukuPlugin))
and shortcut creation.

<a href="https://f-droid.org/packages/de.szalkowski.activitylauncher/" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>
<a href="https://play.google.com/store/apps/details?id=de.szalkowski.activitylauncher.pro" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80"/></a>

## Android developer verification (OSS builds)

OSS builds embed an `assets/adi-registration.properties` file when the
`ADI_REGISTRATION_SNIPPET` environment variable (or Gradle property) is set.
CI supplies it from the `ADI_REGISTRATION_SNIPPET` secret. If no snippet is
provided, the asset is omitted from the APK.

## Support and Feature Requests

Join our [Discord server](https://discord.gg/gzEugJBNMr) for support, feature
requests, and community discussions.

## Translations

Translations can be updated via Pull Requests on GitHub. Feel free to contribute
by submitting PRs to improve or add translations.
