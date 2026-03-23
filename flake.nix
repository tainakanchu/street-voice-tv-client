{
  description = "StreetVoice TV Client - dev environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs/main";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, flake-utils, android-nixpkgs }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; config.allowUnfree = true; };

        androidSdk = android-nixpkgs.sdk.${system} (sdkPkgs: with sdkPkgs; [
          build-tools-34-0-0
          build-tools-35-0-0
          cmdline-tools-latest
          platform-tools
          platforms-android-35
        ]);
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            # Java
            jdk17

            # Node.js (research scripts)
            nodejs_22

            # Android
            androidSdk

            # Utilities
            git
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.jdk17.home}"
            export ANDROID_HOME="${androidSdk}/share/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export PATH="$ANDROID_HOME/platform-tools:$PATH"

            echo "StreetVoice TV dev environment ready"
            echo "  Java:    $(java -version 2>&1 | head -1)"
            echo "  Node:    $(node --version)"
            echo "  Android: $ANDROID_HOME"
          '';
        };
      }
    );
}
