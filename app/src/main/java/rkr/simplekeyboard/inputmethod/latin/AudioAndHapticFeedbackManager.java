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
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rkr.simplekeyboard.inputmethod.latin.common.Constants;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsValues;

/**
 * This class gathers audio feedback and haptic feedback functions.
 *
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {
    private static final String TAG = AudioAndHapticFeedbackManager.class.getSimpleName();
    private static final long TICK_FREQUENCY = 100;
    private static final long DEFAULT_LEGACY_VIBRATION_DURATION = 20;
    private ExecutorService mBackgroundThread;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;
    private long mLastTickTime = 0;

    // Keypress sound styles other than "system" are synthesized by KeySoundSynth into WAV files
    // in the cache and played through a SoundPool. Only touched on mBackgroundThread.
    public static final String SOUND_STYLE_SYSTEM = "system";
    // Indexed by KeySoundSynth kind.
    private static final int[] SOUND_EFFECTS = { AudioManager.FX_KEYPRESS_STANDARD,
            AudioManager.FX_KEYPRESS_DELETE, AudioManager.FX_KEYPRESS_RETURN,
            AudioManager.FX_KEYPRESS_SPACEBAR };
    private static final String SOUND_CACHE_DIR = "keysounds";
    // Volume for synthesized sounds when the volume setting is "system default"; about -6 dB.
    private static final float DEFAULT_SYNTH_SOUND_VOLUME = 0.5f;
    // Largest random pitch change per press at 100% variation, in semitones.
    private static final float MAX_PITCH_VARIATION_SEMITONES = 1.5f;
    private Context mContext;
    private SoundPool mSoundPool;
    // Null for the system style.
    private KeySoundSynth.Config mSoundConfig;
    private final int[] mSoundIds = new int[KeySoundSynth.KIND_COUNT];
    private boolean mKeySoundLoaded;
    private float mPreviewVolumeOnLoad = Float.NaN;
    private int mPreviewVariationOnLoad;
    private final Random mVariationRandom = new Random();

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
        playSoundEffect(sound, mSettingsValues.mKeypressSoundVolume,
                mSettingsValues.mKeypressSoundVariation);
    }

    public void playSoundEffect(final int effectType, final float volume) {
        playSoundEffect(effectType, volume, 0);
    }

    private void playSoundEffect(final int effectType, final float volume,
            final int variationPercent) {
        if (mAudioManager == null) {
            return;
        }

        mBackgroundThread.execute(() -> {
            final int soundId = getSoundId(effectType);
            if (soundId != 0) {
                final float v = volume < 0 ? DEFAULT_SYNTH_SOUND_VOLUME : volume;
                final float semitones = (mVariationRandom.nextFloat() * 2 - 1)
                        * MAX_PITCH_VARIATION_SEMITONES * variationPercent / 100f;
                final float rate = (float) Math.pow(2, semitones / 12.0);
                mSoundPool.play(soundId, v, v, 1 /* priority */, 0 /* loop */, rate);
            } else {
                mAudioManager.playSoundEffect(effectType, volume);
            }
        });
    }

    /** Switches to the given sounds (null: system) in the background. */
    public void setSoundConfig(final KeySoundSynth.Config config) {
        if (mBackgroundThread != null) {
            mBackgroundThread.execute(() -> loadSounds(config));
        }
    }

    /**
     * Switches to the given sounds (null: system) and plays their key sound, once it's ready.
     * Used by the settings to let the user hear a change.
     */
    public void previewSound(final KeySoundSynth.Config config, final float volume,
            final int variationPercent) {
        if (mBackgroundThread == null) {
            return;
        }
        mBackgroundThread.execute(() -> {
            loadSounds(config);
            if (config == null || mKeySoundLoaded) {
                mPreviewVolumeOnLoad = Float.NaN;
                playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume, variationPercent);
            } else {
                // Played by the load listener.
                mPreviewVolumeOnLoad = volume;
                mPreviewVariationOnLoad = variationPercent;
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
    private void loadSounds(final KeySoundSynth.Config config) {
        if (config == null ? mSoundConfig == null : config.equals(mSoundConfig)) {
            return;
        }
        mSoundConfig = config;
        mKeySoundLoaded = false;
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }
        if (config == null || mContext == null) {
            return;
        }
        final File dir = new File(mContext.getCacheDir(), SOUND_CACHE_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "Cannot create " + dir);
            return;
        }
        // Only the current sounds are kept.
        final String prefix = config.getKey() + "_";
        final File[] oldFiles = dir.listFiles();
        if (oldFiles != null) {
            for (final File file : oldFiles) {
                if (!file.getName().startsWith(prefix)) {
                    file.delete();
                }
            }
        }
        final SoundPool soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> mBackgroundThread.execute(
                () -> {
                    if (pool != mSoundPool || sampleId != mSoundIds[KeySoundSynth.KIND_KEY]) {
                        return;
                    }
                    mKeySoundLoaded = true;
                    if (!Float.isNaN(mPreviewVolumeOnLoad)) {
                        final float volume = mPreviewVolumeOnLoad;
                        mPreviewVolumeOnLoad = Float.NaN;
                        playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume,
                                mPreviewVariationOnLoad);
                    }
                }));
        mSoundPool = soundPool;
        for (int kind = 0; kind < KeySoundSynth.KIND_COUNT; kind++) {
            final File file = new File(dir, prefix + kind + ".wav");
            try {
                if (!file.exists()) {
                    KeySoundSynth.renderToWav(config, kind, file);
                }
                mSoundIds[kind] = soundPool.load(file.getPath(), 1);
            } catch (final IOException e) {
                Log.w(TAG, "Cannot write " + file, e);
                mSoundIds[kind] = 0;
            }
        }
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
        setSoundConfig(settingsValues.mKeypressSoundConfig);
    }

    public void onRingerModeChanged() {
        mSoundOn = reevaluateIfSoundIsOn();
    }
}
