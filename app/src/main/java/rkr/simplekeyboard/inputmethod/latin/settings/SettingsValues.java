/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2024 wittmane
 * Copyright (C) 2019 Micha LaQua
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.inputmethod.EditorInfo;

import java.util.Map;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.InputAttributes;

// Non-final for testing via mock library.
public class SettingsValues {
    public static final float DEFAULT_SIZE_SCALE = 1.0f; // 100%

    // From resources:
    public final SpacingAndPunctuations mSpacingAndPunctuations;
    // From configuration:
    public final boolean mHasHardwareKeyboard;
    public final int mDisplayOrientation;
    // From preferences, in the same order as xml/prefs.xml:
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final int mVibrationDuration;
    public final boolean mVibrationIgnoreSystemSettings;
    public final boolean mSoundOn;
    public final boolean mKeyPreviewPopupOn;
    public final boolean mUseOnScreen;
    public final boolean mShowsLanguageSwitchKey;
    public final boolean mShowEmojiKey;
    public final boolean mImeSwitchEnabled;
    public final int mKeyLongpressTimeout;
    public final boolean mShowSpecialChars;
    public final boolean mShowNumberRow;
    public final boolean mSpaceSwipeEnabled;
    public final boolean mDeleteSwipeEnabled;

    // From the input box
    public final InputAttributes mInputAttributes;

    // Deduced settings
    public final float mKeypressSoundVolume;
    public final String mKeypressSoundStyle;
    public final int mKeyPreviewPopupDismissDelay;

    // Debug settings
    public final float mKeyboardHeightScale;

    public final int mBottomOffsetPortrait;

    public SettingsValues(final SharedPreferences prefs, final Resources res,
            final InputAttributes inputAttributes, final Map<String, Object> previewValues) {
        // Get the resources
        mSpacingAndPunctuations = new SpacingAndPunctuations(res);

        // Store the input attributes
        mInputAttributes = inputAttributes;

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true);
        mVibrateOn = Settings.readVibrationEnabled(prefs, res);
        mVibrationDuration = previewOr(previewValues, Settings.PREF_VIBRATION_DURATION,
                Settings.readVibrationDuration(prefs));
        mVibrationIgnoreSystemSettings = Settings.readVibrationIgnoreSystemSettings(prefs, res);
        mSoundOn = Settings.readKeypressSoundEnabled(prefs, res);
        mKeyPreviewPopupOn = Settings.readKeyPreviewPopupEnabled(prefs, res);
        mUseOnScreen = Settings.readUseOnScreenKeyboard(prefs);
        mShowsLanguageSwitchKey = Settings.readShowLanguageSwitchKey(prefs);
        mShowEmojiKey = Settings.readShowEmojiKey(prefs);
        mImeSwitchEnabled = Settings.readEnableImeSwitch(prefs);
        mHasHardwareKeyboard = Settings.readHasHardwareKeyboard(res.getConfiguration());

        // Compute other readable settings
        mKeyLongpressTimeout = previewOr(previewValues, Settings.PREF_KEY_LONGPRESS_TIMEOUT,
                Settings.readKeyLongpressTimeout(prefs, res));
        mKeypressSoundVolume = previewOr(previewValues, Settings.PREF_KEYPRESS_SOUND_VOLUME,
                Settings.readKeypressSoundVolume(prefs));
        mKeypressSoundStyle = Settings.readKeypressSoundStyle(prefs, res);
        mKeyPreviewPopupDismissDelay = res.getInteger(R.integer.config_key_preview_linger_timeout);
        mKeyboardHeightScale = previewOr(previewValues, Settings.PREF_KEYBOARD_HEIGHT,
                Settings.readKeyboardHeight(prefs, DEFAULT_SIZE_SCALE));
        mBottomOffsetPortrait = previewOr(previewValues, Settings.PREF_BOTTOM_OFFSET_PORTRAIT,
                Settings.readBottomOffsetPortrait(prefs));
        mDisplayOrientation = res.getConfiguration().orientation;
        mShowSpecialChars = Settings.readShowSpecialChars(prefs);
        mShowNumberRow = Settings.readShowNumberRow(prefs);
        mSpaceSwipeEnabled = Settings.readSpaceSwipeEnabled(prefs);
        mDeleteSwipeEnabled = Settings.readDeleteSwipeEnabled(prefs);
    }

    // Returns the unsaved preview value for key if there is one (see Settings#setPreviewValue).
    @SuppressWarnings("unchecked")
    private static <T> T previewOr(final Map<String, Object> previewValues, final String key,
            final T savedValue) {
        final Object previewValue = previewValues.get(key);
        return previewValue != null ? (T) previewValue : savedValue;
    }

    public boolean isWordSeparator(final int code) {
        return mSpacingAndPunctuations.isWordSeparator(code);
    }

    public boolean isLanguageSwitchKeyDisabled() {
        return !mShowsLanguageSwitchKey;
    }

    public boolean isSameInputType(final EditorInfo editorInfo) {
        return mInputAttributes.isSameInputType(editorInfo);
    }

    public boolean hasSameOrientation(final Configuration configuration) {
        return mDisplayOrientation == configuration.orientation;
    }
}
