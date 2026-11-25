# Plugin Architecture

Activity Launcher supports external plugins for shortcut creation and activity launching. This document describes the intent-based protocol used to communicate with these plugins.

## Caller trust and permissions

The `distribution` dimension has two values: `oss` and `playStore`. Each value declares only its own
permission namespace:

- `playStore`: `de.szalkowski.activitylauncher.permission.CREATE_SHORTCUT` and
  `de.szalkowski.activitylauncher.permission.LAUNCH_ACTIVITY`
- `oss`: `de.szalkowski.activitylauncher.oss.permission.CREATE_SHORTCUT` and
  `de.szalkowski.activitylauncher.oss.permission.LAUNCH_ACTIVITY`

The declarations use `signature|knownSigner` with `android:knownCerts`, listing the verified
first-party digests documented in [SIGNING_CERTIFICATES.md](SIGNING_CERTIFICATES.md). On Android 12
(API 31) and newer, the system grants each declared permission to every listed first-party build. On
older versions `knownSigner` is ignored and permissions fall back to plain `signature`.

Android does not support `permission A OR permission B` on one component. A plugin that wants to
accept both distributions exposes **two aliases for the same target activity**, one per permission
family, as shown in [`examples/plugin/AndroidManifest.xml`](examples/plugin/AndroidManifest.xml).
`PackageManager.queryIntentActivities()` returns both aliases even when the caller cannot use one, so
Activity Launcher filters handlers by permissions it actually holds and collapses aliases that target
the same activity before showing the chooser.

An app can always launch its own component protected by `android:permission` because Android gives a
UID blanket access to components it owns. Third-party callers without a held permission are denied.

## Shortcut Creation Flow

When a user requests to create a shortcut, Activity Launcher sends a `CREATE_SHORTCUT` intent. A plugin can register for this action to handle the shortcut creation (e.g., to provide custom icons, labels, or to use a different shortcut pinning mechanism).

### Intent: `activitylauncher.intent.action.CREATE_SHORTCUT`

| Extra               | Type   | Description                                                           |
|---------------------|--------|-----------------------------------------------------------------------|
| `name`              | String | The suggested name for the shortcut.                                  |
| `icon`              | Bundle | The icon as a `IconCompat` bundle.                                    |
| `intent`            | String | The pre-configured `LAUNCH_SHORTCUT` intent URI.                      |

**Note:** Plugins should use the provided `intent` URI as the shortcut's target intent. This intent already contains the `shortcut_id` and signed fallback metadata.

### Sequence Diagram

```mermaid
sequenceDiagram
    participant AL as Activity Launcher
    participant Plugin as Shortcut Plugin
    participant System as Android System
    participant Home as Home Screen

    AL->>AL: Record Shortcut in DB
    AL->>AL: Generate Signed LAUNCH_SHORTCUT Intent
    AL->>System: Start Activity (CREATE_SHORTCUT)
    System->>Plugin: Receive Intent
    Plugin->>Plugin: Extract name, icon, and intent
    Plugin->>System: requestPinShortcut(name, icon, intent)
    System-->>Home: Shortcut Created
```

## Shortcut Launch Flow

When a shortcut created by a plugin is clicked, it launches Activity Launcher's `ShortcutActivity` with the `LAUNCH_SHORTCUT` action (as configured in the `intent` extra).

### Intent: `activitylauncher.intent.action.LAUNCH_SHORTCUT`

| Extra           | Type   | Description                                                           |
|-----------------|--------|-----------------------------------------------------------------------|
| `shortcut_id`   | String | The UUID of the shortcut in Activity Launcher's database.             |
| `intent`        | String | The target intent URI (fallback).                                     |
| `sign`          | String | The signature of the target intent (fallback).                        |
| `launch_plugin` | String | Flattened `ComponentName` of a launch plugin (fallback).              |

### Sequence Diagram

```mermaid
sequenceDiagram
    participant User
    participant Home as Home Screen
    participant AL as ShortcutActivity
    participant LP as Launch Plugin
    participant Target as Target Activity

    User->>Home: Click Shortcut
    Home->>AL: Start Activity (LAUNCH_SHORTCUT)
    AL->>AL: Fetch Shortcut from DB (via shortcut_id)
    alt Found in DB
        AL->>AL: Use DB metadata
    else Not found in DB
        AL->>AL: Validate signature of fallback intent extra
    end
    
    alt Has launch_plugin
        AL->>LP: Start Activity (LAUNCH_ACTIVITY)
        LP->>Target: Start Activity
    else Default Launch
        AL->>Target: Start Activity
    end
```

## Launch Delegation

If the shortcut requires launch delegation (specified in the database), `ShortcutActivity` will delegate the launch to the specified component.

### Intent: `activitylauncher.intent.action.LAUNCH_ACTIVITY`

Sent to the component specified as the launch plugin.

| Extra    | Type   | Description                                      |
|----------|--------|--------------------------------------------------|
| `intent` | String | The validated target intent encoded as a URI.    |

If no launch plugin is provided, Activity Launcher performs the default launch using `context.startActivity()`.

## Legacy Support

Activity Launcher maintains backward compatibility for existing shortcuts on users' home screens that were created using legacy protocols.

### Legacy LAUNCH_SHORTCUT Extras

Supported for backward compatibility during launch, but **no longer used during creation**:

| Extra           | Type   | Description                                                           |
|-----------------|--------|-----------------------------------------------------------------------|
| `intent`        | String | The target intent URI.                                                |
| `sign`          | String | The signature of the target intent.                                   |
| `launch_plugin` | String | Flattened `ComponentName` of a plugin to handle the launch.           |
| `extra_intent`  | String | Alternative key for `intent` (v2.2.0 and below).                      |

## Intent Encoding

To ensure robustness and support for complex intents (including extras), Activity Launcher uses `Intent.toUri(Intent.URI_INTENT_SCHEME)` for encoding intents. Legacy URIs (flag `0`) are still supported for backward compatibility.
