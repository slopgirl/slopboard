# slopboard — Android keyboard (fork of rkkr/simple-keyboard)
# Uses Homebrew `gradle`; ./gradlew is broken (wrapper jar missing upstream).

default:
    @just --list

# Build the debug APK
debug:
    gradle assembleDebug -q

# Build the release APK, signed with the key from keystore.properties (see `just keystore`)
release:
    @test -f keystore.properties || { echo "No keystore.properties: run 'just keystore' first" >&2; exit 1; }
    gradle assembleRelease -q

# Create a private release signing key and keystore.properties (both gitignored). Refuses to
# overwrite an existing key: losing it means apps signed with it can't be updated any more.
keystore:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ -e keystore.properties ] || [ -e keystore/release.keystore ]; then
        echo "keystore.properties or keystore/release.keystore already exists; not overwriting" >&2
        exit 1
    fi
    mkdir -p keystore
    pass=$(openssl rand -base64 24 | tr -d '/+=')
    keytool -genkeypair -keystore keystore/release.keystore -storetype PKCS12 \
        -alias slopboard -keyalg RSA -keysize 4096 -validity 36500 \
        -storepass "$pass" -keypass "$pass" -dname "CN=slopboard, O=slopgirl" 2>&1 | tail -1
    printf 'storeFile=keystore/release.keystore\nstorePassword=%s\nkeyAlias=slopboard\nkeyPassword=%s\n' \
        "$pass" "$pass" > keystore.properties
    chmod 600 keystore.properties keystore/release.keystore
    echo "Created keystore/release.keystore and keystore.properties. Back both up somewhere safe."

# Build debug and release APKs
build: debug release

# Install the release APK on the connected device (never install debug builds)
install: release
    adb install -r app/build/outputs/apk/release/app-release.apk

# Print the signing certificate of the release APK
verify-release: release
    "$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk 2>/dev/null

# Run Android lint (upstream already has failing checks)
lint:
    gradle lintDebug

# Remove build outputs
clean:
    gradle clean -q

# Fetch upstream and show commits not yet merged into this branch
upstream:
    git fetch upstream
    git log --oneline HEAD..upstream/master
