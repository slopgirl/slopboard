# slopboard — Android keyboard (fork of rkkr/simple-keyboard)
# Uses Homebrew `gradle`; ./gradlew is broken (wrapper jar missing upstream).

default:
    @just --list

# Build the debug APK
debug:
    gradle assembleDebug -q

# Build the release APK (signed with keystore.properties, else the public dummy key)
release:
    gradle assembleRelease -q

# Build debug and release APKs
build: debug release

# Install the release APK on the connected device (never install debug builds)
install: release
    adb install -r app/build/outputs/apk/release/app-release.apk

# Print the signing certificate of the release APK
verify-release: release
    "$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk 2>/dev/null

# Regenerate the keypress sound styles in res/raw (tools/keysounds.py)
sounds:
    python3 tools/keysounds.py

# Play every generated keypress sound on this computer
sounds-play:
    for f in app/src/main/res/raw/keysound_*.wav; do echo "$f"; afplay "$f"; sleep 0.3; done

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
