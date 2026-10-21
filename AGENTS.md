# Project Overview
Activity Launcher is an Android application that launches hidden activities and creates shortcuts for installed apps. It is an open-source utility tool.

# Tech Stack
- **Language**: Kotlin
- **Build System**: Gradle (Kotlin DSL)
- **Minimum SDK**: 16
- **Target SDK**: 37
- **Compile SDK**: 37

## Key Libraries
- **Dependency Injection**: Hilt
- **UI Toolkit**: Android Views (XML Layouts) with ViewBinding
- **Navigation**: Android Jetpack Navigation Component
- **UI Components**: Material Design 3 (Material Components)
- **Compatibility**: AndroidX (AppCompat, Core-KTX, Preference, MultiDex)

# Project Structure
The project follows a Domain-Driven Design (DDD) structure.

## Root Directory
- `app/`: Main application module
- `descriptions/`: Store listing descriptions
- `whatsnew/`: Changelogs/Release notes

## App Module (`app/src/main/java/de/szalkowski/activitylauncher/`)
- `domain/`: Domain models and repository/infrastructure interfaces.
- `data/`: Data source implementations, repositories, and infrastructure.
- `presentation/`: UI Layer (Fragments, ViewModels, Adapters).
- `app/`: Application class and DI modules.
- `entrypoint/`: Activities, Services, Receivers (App entry points).
- `core/`: Shared utilities and base classes.

# Build Variants
The project uses `productFlavors` with a "distribution" dimension and an "ads" dimension.
Distribution:
1.  **oss**: Pure FOSS.
2.  **playStore**: Includes Play Store specific features like in-app reviews.

Ads:
1.  **noads**: Ad-free version.
2.  **ads**: Version with ads (Playwire).

# Development Guidelines
- **View Binding**: Used for interacting with XML layouts.
- **Hilt**: Used for dependency injection.
- **Navigation**: Uses the Navigation Component. Navigation graph is in `res/navigation`.
- **Naming Conventions**: 
    - Interfaces for data access are named `*Repository`.
    - Specific action performers are named by their role (e.g., `ShortcutCreator`, `ActivitySharer`).
    - Interfaces are defined in `domain`, implementations in `data`.
- **Use Cases**: Complex business logic should be extracted from Repositories and ViewModels into standalone Use Cases (Interactors) in the `domain` layer.
- **Translations**:
    - All `strings.xml` files MUST be kept in sync with the base `values/strings.xml`.
    - Strings in all resource files MUST be ordered alphabetically by their `name` attribute.
    - Technical strings and those that should not be translated MUST be placed in `untranslatable.xml` with `translatable="false"`.
    - Do not translate `whatsnew` files.
    - **Language-Specific Rules**:
        - **Serbian (sr-rRS)**: ALWAYS use Cyrillic script.
        - **Kurdish (ku-rTR)**: ALWAYS use Kurdish Arabic script (Sorani).
        - **Kurmanji (kmr-rTR)**: ALWAYS use Kurdish Latin script (Hawar).

# Maintenance Scripts
Located in the `scripts/` directory:
- `sort-strings.py`: Run this after adding new strings to the base `strings.xml` to synchronize all translation files and maintain alphabetical ordering.
- `check-strings.py`: Run this to verify that all translations are in sync, have correct placeholders, and do not contain untranslated English strings.
- `update-listing.py`: Used to update the Play Store listing from resource strings and description files.

# Handling non-FOSS features
Features that are not free and open-source (like Google Play Services APIs) are abstracted into interfaces in the `domain` layer.
Implementations are provided in flavor-specific source sets and bound using Hilt modules in each flavor.

# Verification
Always check if the project builds after applying changes.
When adding new features or modifying existing ones, you **MUST** add or update relevant unit tests and Android (instrumented) tests where possible to ensure correctness and prevent regressions.

## Relevant Commands
- **Build Debug APK**: `./gradlew app:assembleOssNoadsDebug`
- **Check Lint**: `./gradlew app:lintDebug`
- **Run Unit Tests**: `./gradlew app:testOssNoadsDebugUnitTest`
- **Run Integration Tests**: `./gradlew app:connectedOssNoadsDebugAndroidTest`

## Running from the Jailed OpenCode Agent
`opencode` is the system-installed jailed launcher from the NixOS/home-manager config, not a project package. It reads `opencode.json` for the model. When `.envrc` is present, or when it is started from `nix develop`, the launcher forwards the dev-shell PATH and environment into the jail. The dev shell exports `GRADLE_OPTS` with `-Dandroid.aapt2FromMavenOverride` to point AGP at the NixOS-compatible AAPT2 binary. Use `./gradlew` (not the `gradlew` shell function, which is only available in the outer dev shell).

The dev shell provides `opencode-android`, a wrapper around the same system
launcher that sets `JAIL_EXTRA_ARGS` to bind `~/.android` and the emulator
console token. It does not expose KVM or Wayland. Use it from the dev shell when
the agent needs to inspect AVDs or talk to an emulator on the host.

**Note**: Integration tests (`connectedOssNoadsDebugAndroidTest`) require a running emulator. Keep the emulator on the host; the jail shares the host network so the agent can reach the host adb server. `opencode-android` only adds the AVD directory and emulator console token, not KVM. In both cases restart the adb daemon so the agent can discover the device:
```
emulator -avd Resizable_Experimental -no-window -no-audio &
adb kill-server && adb start-server
adb wait-for-device
./gradlew :app:connectedOssNoadsDebugAndroidTest
```

# Code Style and Formatting
This project uses Spotless for automatic code formatting.
- `spotlessApply` on save or before commit.
