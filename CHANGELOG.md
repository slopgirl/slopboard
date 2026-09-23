# Changelog

Changes in slopboard on top of upstream [Simple Keyboard](https://github.com/rkkr/simple-keyboard).
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

Based on upstream 6.7 (versionCode 148).

### Changed
- Own app identity: applicationId `dev.macroslop.slopboard` (debug builds: `.debug` suffix) and
  app name "slopboard", so it installs alongside upstream Simple Keyboard.

### Added
- Keyboard height in 1% steps. While the height dialog is open, the keyboard previews the
  height live and shows a drag handle on top to resize it directly; the slider follows. The
  bottom offset dialog previews live too, and both dialogs have a test field.
- Keypress sound styles: System (as before), Soft tap, Click, Typewriter, Mechanical and Bubble,
  under Key press settings. Picking one plays a preview. The sounds are synthesized by
  `tools/keysounds.py` (`just sounds`), about 230 KB in total.
- Emoji key next to the space bar (can be turned off under Preferences). It opens an emoji panel
  (Android's emoji picker, with categories and recently used emoji) with an ABC key back to the
  letters and a delete key. The panel is up to 1.5x the keyboard's height (at most 60% of the
  screen), with enough columns that emoji aren't scaled up. A held emoji pops up in a bigger
  bubble above your finger, like the key previews. The APK grows from about 0.6 MB to
  1.6 MB.
- Keypress vibration duration setting: system default, off (0 ms) or 1–100 ms. The slider is
  non-linear — 1 ms steps up to 10 ms, then 2, 5 and 10 ms steps up to 100 ms.
- "Ignore system vibration settings" switch (on by default): keypress vibration still fires with
  touch feedback off, in silent mode or in battery saver.
- Test text field at the top of the key press settings, and in the vibration duration, sound
  volume and long-press delay dialogs. In the dialogs the keyboard uses the slider's value
  before it is saved.
- Holding a key (including backspace) vibrates for every repeated character, not only the first
  press.
- Release builds are signed out of the box with a public dummy keystore; a gitignored
  `keystore.properties` switches to a real key.
- justfile, CHANGELOG and NOTES.
