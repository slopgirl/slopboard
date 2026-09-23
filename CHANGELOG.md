# Changelog

Changes in slopboard on top of upstream [Simple Keyboard](https://github.com/rkkr/simple-keyboard).
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- "Ignore system vibration settings" is now "Ignore touch feedback setting": it still vibrates
  with Android's touch feedback off, but silent mode and battery saver turn keypress vibration
  off again.

## [1.0.0] - 2026-09-24

First slopboard release, based on upstream Simple Keyboard 6.7 (versionCode 148).

### Changed
- Own app identity: applicationId `dev.macroslop.slopboard` (debug builds: `.debug` suffix) and
  app name "slopboard", so it installs alongside upstream Simple Keyboard.

### Added
- Keyboard height in 1% steps. While the height dialog is open, the keyboard previews the
  height live and shows a drag handle on top to resize it directly; the slider follows. The
  bottom offset dialog previews live too, and both dialogs have a test field.
- Keypress sound styles: System (as before), Soft tap, Click, Typewriter, Mechanical and Bubble,
  under Key press settings. Picking one plays a preview. The sounds are synthesized on the
  device, so they have settings: pitch (±24 semitones), length (10–200%), tone (darker to
  brighter) and a random pitch variation per keypress.
- Emoji key next to the space bar (can be turned off under Preferences). It opens an emoji panel
  (Android's emoji picker, with categories and recently used emoji) with an ABC key back to the
  letters and a delete key. The panel is up to 1.5x the keyboard's height (at most 60% of the
  screen), with enough columns that emoji aren't scaled up. A held emoji pops up in a bigger
  bubble above your finger, like the key previews. Category titles use the keyboard's text color
  (they were invisible on dark themes). The APK grows from about 0.6 MB to
  1.6 MB.
- Keypress vibration duration setting: system default, off (0 ms) or 1–100 ms. The slider is
  non-linear — 1 ms steps up to 10 ms, then 2, 5 and 10 ms steps up to 100 ms.
- "Ignore system vibration settings" switch (on by default): keypress vibration still fires with
  touch feedback off, in silent mode or in battery saver.
- Test text field at the top of the key press settings, and in the vibration duration, sound
  volume and long-press delay dialogs. In the dialogs the keyboard uses the slider's value
  before it is saved.
- A long press that opens the popup of extra characters vibrates and clicks, and so does each
  move to another character in that popup.
- Moving the cursor by swiping the space bar (or the delete key) vibrates with your keypress
  vibration length and plays the key sound for each step, up to every 50 ms. Before, it only
  gave the system tick vibration (Android 10+), at most every 100 ms.
- Holding a key (including backspace) vibrates for every repeated character, not only the first
  press.
- `just keystore` creates a private release signing key and the gitignored `keystore.properties`
  that release builds are signed with.
- justfile, CHANGELOG and NOTES.
