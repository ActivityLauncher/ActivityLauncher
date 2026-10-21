{
  description = "Activity Launcher";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    flake-root.url = "github:srid/flake-root";
    agent-toolbox.url = "github:butzist/agent-toolbox";
  };

  outputs = inputs @ {
    nixpkgs,
    flake-parts,
    agent-toolbox,
    ...
  }:
    flake-parts.lib.mkFlake {inherit inputs;} {
      systems = ["x86_64-linux" "aarch64-darwin"];
      imports = [
        inputs.flake-root.flakeModule
      ];
      perSystem = {
        config,
        system,
        ...
      }: let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        python = pkgs.python314.withPackages (ps: [
          ps.google-api-python-client
          ps.google-auth
          ps.google-auth-oauthlib
        ]);

        androidSdk = pkgs.androidenv.composeAndroidPackages {
          platformVersions = ["29" "37"];
          buildToolsVersions = ["37.0.0"];
          cmakeVersions = [];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = ["google_apis"];
          abiVersions = ["x86_64"];
          includeSources = false;
        };

        androidSdkPath = "${androidSdk.androidsdk}/libexec/android-sdk";
        jdk = pkgs.openjdk21_headless;
        javaHome = "${jdk}";

        androidPkgs = [
          jdk
          pkgs.android-tools
          androidSdk.androidsdk
          pkgs.coreutils
          pkgs.bash
          pkgs.findutils
          pkgs.which
        ];

        agentPkgs =
          androidPkgs
          ++ [
            python
            pkgs.gh
            pkgs.bash
            pkgs.coreutils
          ];

        # The jailed agents (opencode) and the jail they run in: see
        # nix/agents.nix, which owns the jail options and the Android env.
        agents = import ./nix/agents.nix {
          inherit pkgs agent-toolbox androidSdkPath javaHome;
          basePackages = agentPkgs;
        };
      in {
        devShells.default = pkgs.mkShell {
          name = "dev-shell";
          packages = agentPkgs ++ [agents.opencode];
          inputsFrom = [config.flake-root.devShell];
          shellHook = ''
            export ANDROID_SDK_ROOT="${androidSdkPath}"
            export ANDROID_HOME="$ANDROID_SDK_ROOT"
            export ANDROID_AVD_HOME="$HOME/.android/avd"
            export JAVA_HOME="${javaHome}"
            export ANDROID_AAPT2="${androidSdkPath}/build-tools/37.0.0/aapt2"
            export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx4g -Dandroid.aapt2FromMavenOverride=$ANDROID_AAPT2"

            # ADB server fix: ensure it talks to the outside emulator
            adb kill-server 2>/dev/null; adb start-server 2>/dev/null || true

            echo "Activity Launcher dev shell"
            echo "Run: gradlew app:assembleOssNoadsDebug"
            echo "Run: gradlew app:testOssNoadsDebugUnitTest"
            echo "Run: gradlew app:connectedCheck          # requires emulator"
            echo "Run: adb devices                         # list connected devices"
            echo "Run: python scripts/update-listing.py --help"
            echo ""
            echo "ADB: $(adb --version 2>&1 | head -1)"
          '';
        };

        packages.update-listing = pkgs.writeShellApplication {
          name = "update-listing";
          text = ''
            exec ${python}/bin/python ${./scripts/update-listing.py} "$@"
          '';
        };
      };
    };
}
