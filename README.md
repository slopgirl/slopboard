# slopboard

A fork of [Simple Keyboard](https://github.com/rkkr/simple-keyboard) with a few extras:

- Adjustable keypress vibration duration (0–100 ms, fine steps at the low end)
- Option to vibrate even when the system has touch feedback off, is silent or in battery saver
- Emoji key with a built-in emoji picker
- Keypress sound styles (soft tap, click, typewriter, mechanical, bubble), synthesized on the
  device with adjustable pitch, length, tone and variation
- Test text fields in the key press settings

See [CHANGELOG.md](CHANGELOG.md) for everything that differs from upstream.

### Building

Requires the Android SDK (`local.properties` with `sdk.dir`), Gradle and
[just](https://github.com/casey/just):

```sh
just debug     # app/build/outputs/apk/debug/app-debug.apk
just release   # app/build/outputs/apk/release/app-release.apk
```

Release builds need a signing key. `just keystore` creates a private one in `keystore/` plus
`keystore.properties` pointing at it (both gitignored; back them up). To use an existing key,
write `keystore.properties` yourself:

```properties
storeFile=path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

---

*Upstream README:*

# Simple Keyboard

[![Crowdin](https://d322cqt584bo4o.cloudfront.net/simple-keyboard/localized.svg)](https://crowdin.com/project/simple-keyboard)

<img src="images/screenshot-0.png"
      alt="closeup"
      width="500"/>
      
## About

Features:
- Small size (<1MB)
- Adjustable keyboard height for more screen space
- Number row
- Swipe space to move pointer
- Delete swipe
- Custom theme colors
- Minimal permissions (only Vibrate)
- Ads-free

Feature it doesn't have and probably will never have:
- Emojis
- GIFs
- Spell checker
- Swipe typing

## Downloads

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/packages/rkr.simplekeyboard.inputmethod/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
      alt="Get it on Google Play"
      height="80">](https://play.google.com/store/apps/details?id=rkr.simplekeyboard.inputmethod)

## Credits

Licensed under Apache License Version 2

This keyboard is based on AOSP LatinIME keyboard. You can get the original source code in https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
