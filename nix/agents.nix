{
  pkgs,
  agent-toolbox,
  basePackages,
  androidSdkPath,
  javaHome,
}:
#
# The jailed agents the devshell launches (opencode today).
#
# `flake.nix` owns the project: the Android SDK, the devshell and the
# toolchain. This file owns the *agent* side of it: the sandbox the agent
# runs in, the environment it gets, and the opencode configuration.
#
# The sandbox is agent-toolbox's (github:butzist/agent-toolbox), which is
# built on jail.nix: the agent's configuration is baked into the package,
# `envFromFile` reads the secrets on the host at launch and forwards them
# into the jail, and `permissions` says what the jail may see of the host.
let
  atb = agent-toolbox.lib;

  androidEnv = {
    ANDROID_SDK_ROOT = androidSdkPath;
    ANDROID_HOME = androidSdkPath;
    ANDROID_AAPT2 = "${androidSdkPath}/build-tools/37.0.0/aapt2";
    JAVA_HOME = javaHome;
    GRADLE_OPTS = "-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx4g -Dandroid.aapt2FromMavenOverride=${androidSdkPath}/build-tools/37.0.0/aapt2";
  };

  # opencode's configuration. agent-toolbox bakes it into the package and
  # points OPENCODE_CONFIG at it, so it is the same for everyone using this
  # flake and does not depend on a global config file on the host.
  #
  # The opencode-go key is *not* in the configuration: it is read from the
  # host at launch (see `envFromFile` below) and opencode substitutes it
  # with its `{env:...}` syntax. The key therefore never reaches the nix
  # store.
  opencodeConfig = pkgs.lib.recursiveUpdate atb.defaultOpencodeConfig {
    model = "opencode-go/deepseek-v4.1-flash";
    provider.opencode-go.options.apiKey = "{env:OPENCODE_GO_API_KEY}";
  };

  # The project directory, mounted read-write. `$FLAKE_ROOT` is set by the
  # devshell (see the flake-root input), so the agent can work on the
  # project from anywhere, not just from the directory it was started in.
  bindFlakeRoot = atb.combinators.add-runtime ''
    if [ -n "''${FLAKE_ROOT:-}" ] && [ -d "$FLAKE_ROOT" ]; then
      RUNTIME_ARGS+=(--bind "$FLAKE_ROOT" "$FLAKE_ROOT")
    fi
  '';

  jailPermissions = c:
    atb.defaultOpencodePermissions c
    ++ [
      # The Android toolchain: SDK, JDK and the gradle helpers.
      (c.add-pkg-deps basePackages)
      # The devshell exports the AVD directory; the emulator runs outside
      # the jail, so the agent only needs to know where the AVDs are.
      (c.try-fwd-env "ANDROID_AVD_HOME")
      # Emulator acceleration and the auth token the SDK/emulator writes.
      (c.try-readwrite "/dev/kvm")
      (c.try-readwrite "/dev/shm")
      (c.try-readwrite (c.noescape "\"$HOME/.emulator_console_auth_token\""))
      # `gh` keeps its credentials here and may refresh `hosts.yml`.
      (c.try-readwrite (c.noescape "\"$HOME/.config/gh\""))
      # Global git config (credential helper, user identity). Read-only:
      # the agent may read it but must not modify the host's git setup.
      (c.try-readonly (c.noescape "\"$HOME/.config/git\""))
      # jail.nix `gpu`: ro-binds the PCI GPU dirs in /sys/devices, mounts
      # /sys/dev/char + /run/opengl-driver(-32) and --dev-binds /dev/dri.
      c.gpu
    ]
    ++ (pkgs.lib.mapAttrsToList (name: value: c.set-env name value) androidEnv);
in {
  opencode = atb.mkOpencodeJailed {
    name = "opencode";
    config = opencodeConfig;
    package = pkgs.opencode;
    permissions = jailPermissions;
    envFromFile = {
      # agenix decrypts this on the host into the user's runtime directory.
      # The wrapper reads it there, so the secret never enters the store
      # and the jail does not need access to the runtime directory.
      OPENCODE_GO_API_KEY = "\"$HOME/.config/opencode/opencode-go-token\"";
    };
  };
}
