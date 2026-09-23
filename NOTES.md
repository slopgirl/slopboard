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
- Release signing: gitignored `keystore.properties` (storeFile relative to repo root) +
  `keystore/release.keystore`, created by `just keystore` (random password, alias slopboard).
  Without it `just release` stops; plain `gradle assembleRelease` makes an unsigned APK.
- Never commit a keystore. The early public dummy key (password "slopboard") was purged from
  history on 2026-09-24; it had been pushed to GitHub before that, so treat it as public.
- Losing the key means installed builds can't be updated, only uninstalled + reinstalled.

## Versioning
- slopboard has its own semver, starting at 1.0.0 (versionCode 149, continuing upstream's
  148 so it's never a downgrade). Tags: `v<version>`. Bump versionName/versionCode in
  app/build.gradle and move CHANGELOG's Unreleased entries into a version section.

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
- Panel: layout/emoji_panel.xml, included over the MainKeyboardView in BOTH layout/ and
  layout-v28/input_view.xml (forgetting v28 crashed the keyboard on Android 9+). It copies the
  keyboard view's padding (v28 fitsSystemWindows adds nav-bar padding). When shown it takes the
  keyboard's height and background, and the keyboard view goes INVISIBLE (not GONE, so the size
  is kept). `KeyboardSwitcher#getVisibleKeyboardView` returns the panel while it's shown, since
  LatinIME#onComputeInsets uses it for the touchable region. Closed on onFinishInputView.

- EmojiPickerView renders each emoji to a bitmap at 30 sp and scales it to the cell, so
  EmojiPanelView sets the column count to keep cells no wider than that bitmap. It hides emoji
  the device font can't render (UnicodeRenderableManager), unless EmojiCompat is loaded.

- Emoji preview bubble: EmojiPanelView#dispatchTouchEvent finds the androidx EmojiView under
  the finger and adds a TextView to the IME window's android.R.id.content (like the key
  previews). The emoji is read by reflection (EmojiViewAccess), so proguard-rules.pro keeps
  EmojiView#getEmoji. The bubble hides past touch slop (scroll) and on up/cancel. The long-press
  variants popup is its own window and covers the bubble.

## Keyboard height handle
- Settings#addPreviewListener: LatinIME reloads the keyboard for height/bottom offset previews,
  and shows KeyboardResizeHandleView (layout/keyboard_resize_handle.xml, in both input_view
  layouts) while a PREF_KEYBOARD_HEIGHT preview exists.
- Seek bar dialogs with a test field set their preview as soon as they open, which is how the
  keyboard knows the height dialog is open, and they follow preview changes from elsewhere
  (ValueProxy#getValueFromPreview).
- The handle is placed via its bottomMargin = keyboard view height (layout listener in
  KeyboardSwitcher). onComputeInsets adds its height to the touchable/visible area.

## Keypress sound styles
- `pref_keypress_sound_style` (ListPreference, default "system"). Values and names, plus the
  ranges of the sound settings, are in res/values/keypress-sound-styles.xml.
- Non-system styles are synthesized on the device by `KeySoundSynth` (plain Java, no Android
  deps, so it can be run on the desktop JVM to check output). `KeySoundSynth.Config` = style +
  pitch/length/tone: the pitch scales all frequencies, the length all durations and time
  constants, and the tone the filter cutoffs. Rendered to cacheDir/keysounds/<config>_<kind>.wav
  (old configs are deleted) and played through a SoundPool (USAGE_ASSISTANCE_SONIFICATION). All
  SoundPool use is on the manager's single background thread.
- Pitch variation isn't rendered: it's a random SoundPool playback rate per press (up to ±1.5
  semitones at 100%).
- Settings dialogs preview through `AudioAndHapticFeedbackManager#previewSound(config, ...)`,
  which plays once the new key sound has loaded (SoundPool loads async).
- "System default" volume plays synthesized sounds at 0.5 (about -6 dB); the system style keeps
  AudioManager#playSoundEffect.
- History: sounds were first rendered offline by tools/keysounds.py into res/raw (removed).

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
