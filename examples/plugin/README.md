# Plugin manifest example

This directory shows the manifest side of the protocol described in
[`PLUGIN_ARCHITECTURE.md`](../../PLUGIN_ARCHITECTURE.md).

Activity Launcher identifies trusted callers with manifest permissions. The `playStore` distribution
declares `de.szalkowski.activitylauncher.permission.*`, and the
`oss` distribution declares `de.szalkowski.activitylauncher.oss.permission.*`, both using
`signature|knownSigner`. The trusted digests are listed in
[`untranslatable.xml`](../../app/src/main/res/values/untranslatable.xml) and explained in
[`SIGNING_CERTIFICATES.md`](../../SIGNING_CERTIFICATES.md).

Android has no `permission A OR permission B` attribute. This example therefore exposes two aliases
for the same action and target activity: `.LaunchActivity` is gated by the Play family, and
`.OssLaunchActivity` is gated by the OSS family. Activity Launcher asks `PackageManager` for both,
then filters out handlers whose permission it does not hold and collapses aliases with the same
target, so the chooser shows one usable entry.

Do **not** declare these permissions in the plugin; only Activity Launcher declares them. A plugin
that declares the same name would cause `INSTALL_FAILED_DUPLICATE_PERMISSION`.

The same pattern can be used with `CREATE_SHORTCUT` aliases.
