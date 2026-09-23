# Notes

Working notes for maintaining slopboard (mostly for Claude). User-facing changes go in
CHANGELOG.md.

## Repo
- `origin` = git@github.com:slopgirl/slopboard.git, `upstream` = github.com/rkkr/simple-keyboard.
  Work happens on `slop`; local `master` mirrors upstream.
- Commits are authored as slopgirl (identity set by ../gitconfig). Only rewrite our own
  commits, never upstream authors or history.
- applicationId `dev.macroslop.slopboard`, debug `dev.macroslop.slopboard.debug` (label
  "slopboard debug" from app/src/debug/res). The Java namespace stays
  `rkr.simplekeyboard.inputmethod` on purpose, to keep upstream merges easy.
  Gotcha: resources are packaged under the applicationId, so `getIdentifier` must use
  `res.getResourcePackageName(<known R id>)`, never `R.class.getPackage()` (crashed settings).
- The user's phone also has upstream Simple Keyboard from F-Droid (rkr.simplekeyboard.inputmethod);
  don't touch it.

## Build
- Only the release APK goes on the user's phone (`just install`); never install debug.
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

## Emoji key and panel
- Uses androidx `emoji2-emojipicker` (EmojiPickerView), so `android.useAndroidX=true`. It's the
  only dependency and costs ~1 MB of APK.
- Key: `CODE_EMOJI` (-14), `!code/key_emoji`, `!icon/emoji_key` (drawable/sym_keyboard_emoji),
  `emojiKeyStyle`. It's optional like the language switch key: Keyboard_Case attr
  `emojiKeyEnabled`, flag in KeyboardId/KeyboardLayoutSet.Params, pref `pref_show_emoji_key`.
  key_space_5kw/7kw have one `emojiKeyEnabled="true"` copy of each case, with the space bar one
  key narrower (10%p / 9%p).
- KeyboardCodesSet `ID_TO_NAME` and `DEFAULT` must stay index-aligned. Upstream's are already off
  at the end (key_left/key_right have no codes); key_emoji was added right after
  key_language_switch.
- Panel: EmojiPanelView in input_view.xml, over the MainKeyboardView. When shown it takes the
  keyboard's height and background, and the keyboard view goes INVISIBLE (not GONE, so the size
  is kept). `KeyboardSwitcher#getVisibleKeyboardView` returns the panel while it's shown, since
  LatinIME#onComputeInsets uses it for the touchable region. Closed on onFinishInputView.

## Settings test fields
- Settings activity and IME share a process, so settings can talk to the running keyboard
  directly.
- `Settings#setPreviewValue(key, value)` overlays unsaved values onto SettingsValues (only
  vibration duration, sound volume, long-press timeout read them). Seek bar dialogs with
  `latin:showTestField="true"` set it while dragging and clear it on close.
- `Settings#onSharedPreferenceChanged` now also pushes the new values to
  AudioAndHapticFeedbackManager; before, it only picked them up when input moved to another field.
- The key press screen's field sits above the preference ListView (KeyPressSettingsFragment
  #onCreateView), since an EditText inside the ListView loses focus.
- Keyboard-view settings such as the key popup only apply once the field is refocused.
