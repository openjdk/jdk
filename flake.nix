{
  description = "Eclipse Platform Releng Aggregator development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };
        # Use the latest Java version
        # jdk = pkgs.jdk;
        jdk = pkgs.jdk25_headless;
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            jdk
            gnumake
            autoconf
            maven
          ];

          shellHook = ''
            export JAVA_HOME=${jdk}
            export PATH="$JAVA_HOME/bin:$PATH"
            echo "Java version:"
            java -version
            echo ""
            echo "Maven version:"
            mvn -version
          '';
        };
      }
    );
}
