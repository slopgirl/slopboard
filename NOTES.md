# Notes

Working notes for maintaining slopboard (mostly for Claude). User-facing changes go in
CHANGELOG.md.

## Repo
- `origin` = git@github.com:slopgirl/slopboard.git, `upstream` = github.com/rkkr/simple-keyboard.
  Work happens on `slop`; local `master` mirrors upstream.
- Commits are authored as slopgirl (identity set by ../gitconfig). Only rewrite our own
  commits, never upstream authors or history.
- Package/applicationId is still `rkr.simplekeyboard.inputmethod` (renaming not decided yet).

## Build
- `./gradlew` is broken (upstream is missing `gradle/wrapper/gradle-wrapper.jar`); use Homebrew
  `gradle` through the justfile.
- Needs `local.properties` with `sdk.dir=...` (gitignored).
- `gradle lintDebug` fails on existing upstream errors (MissingTranslation, StringFormatMatches
  on `abbreviation_unit_milliseconds`). Not ours.

## Signing
- `keystore/dummy-release.keystore` is committed on purpose: PKCS12, alias `slopboard`,
  store/key password `slopboard`. It's public, so don't use it to distribute builds.
- For a real key create `keystore.properties` in the repo root (gitignored):
  `storeFile`, `storePassword`, `keyAlias`, `keyPassword` (storeFile relative to repo root).
- Debug and release are signed with different keys but share the applicationId, so switching
  between them on a device means uninstalling first.

## Vibration feature
- `pref_vibration_duration` int: -1 = system default (EFFECT_CLICK), 0 = off, 1–100 ms one-shot.
  Selectable values: `config_vibration_durations` in config-common.xml.
- `SeekBarDialogPreference` takes an optional `latin:values` integer-array: tick i = values[i].
  Stored values not in the list snap to the nearest one.
- `pref_vibration_ignore_system_settings` (default true) vibrates with USAGE_ALARM
  (VibrationAttributes on API 33+, AudioAttributes below). FLAG_BYPASS_INTERRUPTION_POLICY is
  set but ignored without a privileged permission. Still blocked by: alarm vibration intensity
  off, DND without alarms.
- API <29 with the default duration keeps the old `view.performHapticFeedback` path.
- Not yet tested on a real device.
