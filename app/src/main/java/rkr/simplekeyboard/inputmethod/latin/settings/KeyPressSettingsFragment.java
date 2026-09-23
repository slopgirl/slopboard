/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2021 wittmane
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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.AudioAndHapticFeedbackManager;
import rkr.simplekeyboard.inputmethod.latin.KeySoundSynth;

/**
 * "Preferences" settings sub screen.
 *
 * This settings sub screen handles the following input preferences.
 * - Vibrate on keypress
 * - Keypress vibration duration
 * - Ignore system vibration settings
 * - Sound on keypress
 * - Keypress sound volume
 * - Keypress sound style, and the pitch, length, tone and variation of synthesized styles
 * - Popup on keypress
 * - Key long press delay
 *
 * A text field above the list lets the keyboard be tried with the current settings.
 */
public final class KeyPressSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_key_press);

        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        AudioAndHapticFeedbackManager.init(context);

        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATE_ON);
            removePreference(Settings.PREF_VIBRATION_DURATION);
            removePreference(Settings.PREF_VIBRATION_IGNORE_SYSTEM_SETTINGS);
        }

        setupKeypressVibrationDurationSettings();
        setupKeypressSoundVolumeSettings();
        setupKeypressSoundStyleSettings();
        setupKeyLongpressTimeoutSettings();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // Pinned above the preference list rather than inside it, where the ListView would take
        // the focus away from the text field.
        final View preferenceList = super.onCreateView(inflater, container, savedInstanceState);
        final LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(inflater.inflate(R.layout.settings_test_field, layout, false));
        layout.addView(preferenceList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return layout;
    }

    private void setupKeypressVibrationDurationSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_VIBRATION_DURATION);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readVibrationDuration(prefs);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultVibrationDuration();
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }

            @Override
            public void feedbackValue(final int value) {
                AudioAndHapticFeedbackManager.getInstance().vibrate(value,
                        Settings.readVibrationIgnoreSystemSettings(prefs, res));
            }
        });
    }

    private void setupKeypressSoundVolumeSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEYPRESS_SOUND_VOLUME);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            private static final float PERCENTAGE_FLOAT = 100.0f;

            private float getValueFromPercentage(final int percentage) {
                return percentage / PERCENTAGE_FLOAT;
            }

            private int getPercentageFromValue(final float floatValue) {
                return (int)(floatValue * PERCENTAGE_FLOAT);
            }

            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putFloat(key, getValueFromPercentage(value)).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(Settings.readKeypressSoundVolume(prefs));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(Settings.readDefaultKeypressSoundVolume());
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return Integer.toString(value);
            }

            @Override
            public void feedbackValue(final int value) {
                AudioAndHapticFeedbackManager.getInstance().playSoundEffect(
                        AudioManager.FX_KEYPRESS_STANDARD, getValueFromPercentage(value));
            }

            @Override
            public Object getPreviewValue(final int value) {
                return getValueFromPercentage(value);
            }
        });
    }

    private void setupKeypressSoundStyleSettings() {
        final Preference pref = findPreference(Settings.PREF_KEYPRESS_SOUND_STYLE);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        // Loaded here too, so the volume dialog plays the chosen style even when the keyboard
        // isn't running.
        AudioAndHapticFeedbackManager.getInstance().setSoundConfig(
                Settings.readKeypressSoundConfig(prefs, res, null, null));
        updateSynthSoundSettingsEnabled(Settings.readKeypressSoundStyle(prefs, res));
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            previewSound(Settings.PREF_KEYPRESS_SOUND_STYLE, newValue);
            updateSynthSoundSettingsEnabled((String) newValue);
            return true;
        });

        setupSynthSoundSetting(Settings.PREF_KEYPRESS_SOUND_PITCH,
                value -> res.getString(R.string.keypress_sound_pitch_value, value));
        setupSynthSoundSetting(Settings.PREF_KEYPRESS_SOUND_LENGTH,
                value -> res.getString(R.string.abbreviation_unit_percent, value));
        setupSynthSoundSetting(Settings.PREF_KEYPRESS_SOUND_TONE, value -> value == 0
                ? res.getString(R.string.keypress_sound_tone_neutral)
                : res.getString(value < 0 ? R.string.keypress_sound_tone_darker
                        : R.string.keypress_sound_tone_brighter, value));
        setupSynthSoundSetting(Settings.PREF_KEYPRESS_SOUND_VARIATION,
                value -> res.getString(R.string.abbreviation_unit_percent, value));
    }

    private static final String[] SYNTH_SOUND_SETTINGS = {
            Settings.PREF_KEYPRESS_SOUND_PITCH, Settings.PREF_KEYPRESS_SOUND_LENGTH,
            Settings.PREF_KEYPRESS_SOUND_TONE, Settings.PREF_KEYPRESS_SOUND_VARIATION };

    // The pitch, length, tone and variation only apply to synthesized styles.
    private void updateSynthSoundSettingsEnabled(final String style) {
        for (final String key : SYNTH_SOUND_SETTINGS) {
            setPreferenceEnabled(key, KeySoundSynth.hasStyle(style));
        }
    }

    // Plays the key sound with the saved sound settings, but {@code key} set to {@code value}.
    private void previewSound(final String key, final Object value) {
        final SharedPreferences prefs = getSharedPreferences();
        final int variation = Settings.PREF_KEYPRESS_SOUND_VARIATION.equals(key) ? (Integer) value
                : Settings.readKeypressSoundInt(prefs, Settings.PREF_KEYPRESS_SOUND_VARIATION);
        AudioAndHapticFeedbackManager.getInstance().previewSound(
                Settings.readKeypressSoundConfig(prefs, getResources(), key, value),
                Settings.readKeypressSoundVolume(prefs), variation);
    }

    private interface ValueText {
        String get(int value);
    }

    private void setupSynthSoundSetting(final String key, final ValueText valueText) {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference) findPreference(key);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeypressSoundInt(prefs, key);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultKeypressSoundInt(key);
            }

            @Override
            public String getValueText(final int value) {
                return valueText.get(value);
            }

            @Override
            public void feedbackValue(final int value) {
                previewSound(key, value);
            }
        });
    }

    private void setupKeyLongpressTimeoutSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEY_LONGPRESS_TIMEOUT);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeyLongpressTimeout(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultKeyLongpressTimeout(res);
            }

            @Override
            public String getValueText(final int value) {
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }
}
