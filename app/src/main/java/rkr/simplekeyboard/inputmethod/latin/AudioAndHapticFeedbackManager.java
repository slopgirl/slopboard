/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
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

package rkr.simplekeyboard.inputmethod.latin;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.common.Constants;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsValues;

/**
 * This class gathers audio feedback and haptic feedback functions.
 *
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {
    private static final long TICK_FREQUENCY = 100;
    private static final long DEFAULT_LEGACY_VIBRATION_DURATION = 20;
    private ExecutorService mBackgroundThread;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;
    private long mLastTickTime = 0;

    // Keypress sound styles other than "system" play bundled res/raw/keysound_<style>_<kind>
    // files (made by tools/keysounds.py) through a SoundPool. Only touched on mBackgroundThread.
    public static final String SOUND_STYLE_SYSTEM = "system";
    private static final int[] SOUND_EFFECTS = { AudioManager.FX_KEYPRESS_STANDARD,
            AudioManager.FX_KEYPRESS_DELETE, AudioManager.FX_KEYPRESS_RETURN,
            AudioManager.FX_KEYPRESS_SPACEBAR };
    private static final String[] SOUND_KINDS = { "key", "delete", "enter", "space" };
    // Volume for bundled sounds when the volume setting is "system default"; about -6 dB.
    private static final float DEFAULT_BUNDLED_SOUND_VOLUME = 0.5f;
    private Context mContext;
    private SoundPool mSoundPool;
    private String mSoundStyle = SOUND_STYLE_SYSTEM;
    private final int[] mSoundIds = new int[SOUND_KINDS.length];
    private float mPreviewVolumeOnLoad = Float.NaN;

    private static final AudioAndHapticFeedbackManager sInstance =
            new AudioAndHapticFeedbackManager();

    public static AudioAndHapticFeedbackManager getInstance() {
        return sInstance;
    }

    private AudioAndHapticFeedbackManager() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private void initInternal(final Context context) {
        mContext = context.getApplicationContext();
        mBackgroundThread = Executors.newSingleThreadExecutor();
        mBackgroundThread.execute(() -> {
            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        });
    }

    public boolean hasVibrator() {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    private boolean reevaluateIfSoundIsOn() {
        if (mSettingsValues == null || !mSettingsValues.mSoundOn || mAudioManager == null) {
            return false;
        }
        return mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    public void performAudioFeedback(final int code) {
        // if mAudioManager is null, we can't play a sound anyway, so return
        if (mAudioManager == null) {
            return;
        }
        if (!mSoundOn) {
            return;
        }
        final int sound;
        switch (code) {
        case Constants.CODE_DELETE:
            sound = AudioManager.FX_KEYPRESS_DELETE;
            break;
        case Constants.CODE_ENTER:
            sound = AudioManager.FX_KEYPRESS_RETURN;
            break;
        case Constants.CODE_SPACE:
            sound = AudioManager.FX_KEYPRESS_SPACEBAR;
            break;
        default:
            sound = AudioManager.FX_KEYPRESS_STANDARD;
            break;
        }
        playSoundEffect(sound, mSettingsValues.mKeypressSoundVolume);
    }

    public void playSoundEffect(final int effectType, final float volume) {
        if (mAudioManager == null) {
            return;
        }

        mBackgroundThread.execute(() -> {
            final int soundId = getSoundId(effectType);
            if (soundId != 0) {
                final float v = volume < 0 ? DEFAULT_BUNDLED_SOUND_VOLUME : volume;
                mSoundPool.play(soundId, v, v, 1 /* priority */, 0 /* loop */, 1f /* rate */);
            } else {
                mAudioManager.playSoundEffect(effectType, volume);
            }
        });
    }

    /** Switches to a keypress sound style, then plays its key sound once it has loaded. */
    public void previewSoundStyle(final String style, final float volume) {
        if (mBackgroundThread == null) {
            return;
        }
        mBackgroundThread.execute(() -> {
            if (style.equals(mSoundStyle) && mSoundPool != null) {
                mPreviewVolumeOnLoad = Float.NaN;
                playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume);
                return;
            }
            mPreviewVolumeOnLoad = volume;
            loadSoundStyle(style);
            if (mSoundPool == null) {
                // The system style has nothing to load.
                mPreviewVolumeOnLoad = Float.NaN;
                playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume);
            }
        });
    }

    // Must be called on the background thread.
    private int getSoundId(final int effectType) {
        if (mSoundPool == null) {
            return 0;
        }
        for (int i = 0; i < SOUND_EFFECTS.length; i++) {
            if (SOUND_EFFECTS[i] == effectType) {
                return mSoundIds[i];
            }
        }
        return 0;
    }

    // Must be called on the background thread.
    private void loadSoundStyle(final String style) {
        if (style.equals(mSoundStyle)) {
            return;
        }
        mSoundStyle = style;
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }
        if (SOUND_STYLE_SYSTEM.equals(style) || mContext == null) {
            return;
        }
        final SoundPool soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        // Resources live under the applicationId, which differs from R's Java package.
        final String resourcePackage = mContext.getResources().getResourcePackageName(
                R.raw.keysound_click_key);
        final int keySoundIndex = 0;
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> mBackgroundThread.execute(
                () -> {
                    if (pool == mSoundPool && sampleId == mSoundIds[keySoundIndex]
                            && !Float.isNaN(mPreviewVolumeOnLoad)) {
                        final float volume = mPreviewVolumeOnLoad;
                        mPreviewVolumeOnLoad = Float.NaN;
                        playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume);
                    }
                }));
        for (int i = 0; i < SOUND_KINDS.length; i++) {
            final int resId = mContext.getResources().getIdentifier(
                    "keysound_" + style + "_" + SOUND_KINDS[i], "raw", resourcePackage);
            mSoundIds[i] = resId != 0 ? soundPool.load(mContext, resId, 1) : 0;
        }
        mSoundPool = soundPool;
    }

    public void performHapticFeedback(final View viewToPerformHapticFeedbackOn) {
        if (!mSettingsValues.mVibrateOn || mVibrator == null) {
            return;
        }
        final int duration = mSettingsValues.mVibrationDuration;
        final boolean ignoreSystemSettings = mSettingsValues.mVibrationIgnoreSystemSettings;
        if (duration < 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (viewToPerformHapticFeedbackOn != null) {
                mBackgroundThread.execute(() -> {
                    viewToPerformHapticFeedbackOn.performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                });
            }
            return;
        }
        vibrate(duration, ignoreSystemSettings);
    }

    /**
     * Vibrates for the given duration in milliseconds. A negative duration plays the system
     * default click effect, zero does nothing.
     */
    public void vibrate(final int durationMs, final boolean ignoreSystemSettings) {
        if (mVibrator == null || durationMs == 0) {
            return;
        }
        mBackgroundThread.execute(() -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // No predefined effects: fall back to a short fixed pulse.
                final long ms = durationMs < 0 ? DEFAULT_LEGACY_VIBRATION_DURATION : durationMs;
                if (ignoreSystemSettings) {
                    mVibrator.vibrate(ms, getBypassAudioAttributes());
                } else {
                    mVibrator.vibrate(ms);
                }
                return;
            }
            final VibrationEffect effect;
            if (durationMs > 0) {
                effect = VibrationEffect.createOneShot(durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
            } else {
                effect = VibrationEffect.createOneShot(DEFAULT_LEGACY_VIBRATION_DURATION,
                        VibrationEffect.DEFAULT_AMPLITUDE);
            }
            vibrate(effect, ignoreSystemSettings);
        });
    }

    public void performTickFeedback() {
        if (!mSettingsValues.mVibrateOn
                || mVibrator == null
                || System.currentTimeMillis() - mLastTickTime < TICK_FREQUENCY ) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mLastTickTime = System.currentTimeMillis();
            final boolean ignoreSystemSettings = mSettingsValues.mVibrationIgnoreSystemSettings;
            mBackgroundThread.execute(() -> {
                vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK),
                        ignoreSystemSettings);
            });
        }
    }

    // Must be called on the background thread.
    @TargetApi(Build.VERSION_CODES.O)
    private void vibrate(final VibrationEffect effect, final boolean ignoreSystemSettings) {
        if (!ignoreSystemSettings) {
            mVibrator.vibrate(effect);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Alarm vibrations are not affected by the touch feedback setting, silent ringer
            // mode, Do Not Disturb (when alarms are allowed) or battery saver.
            // FLAG_BYPASS_INTERRUPTION_POLICY is silently dropped unless the app holds a
            // privileged permission, but it costs nothing to ask.
            mVibrator.vibrate(effect, new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .setFlags(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY,
                            VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY)
                    .build());
        } else {
            mVibrator.vibrate(effect, getBypassAudioAttributes());
        }
    }

    private static AudioAttributes getBypassAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    public void onSettingsChanged(final SettingsValues settingsValues) {
        mSettingsValues = settingsValues;
        mSoundOn = reevaluateIfSoundIsOn();
        setSoundStyle(settingsValues.mKeypressSoundStyle);
    }

    /** Loads a keypress sound style (see keypress-sound-styles.xml). */
    public void setSoundStyle(final String style) {
        if (mBackgroundThread != null) {
            mBackgroundThread.execute(() -> loadSoundStyle(style));
        }
    }

    public void onRingerModeChanged() {
        mSoundOn = reevaluateIfSoundIsOn();
    }
}
