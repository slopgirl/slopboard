/*
 * Copyright (C) 2026 slopgirl
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

package rkr.simplekeyboard.inputmethod.keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.emoji2.emojipicker.EmojiPickerView;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.common.Constants;

/**
 * Emoji picker shown in place of the keyboard by the emoji key, with a bar to go back to the
 * letters and a delete key that repeats while held.
 */
public final class EmojiPanelView extends LinearLayout {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int mRepeatStartTimeout;
    private final int mRepeatInterval;

    private KeyboardActionListener mListener = KeyboardActionListener.EMPTY_LISTENER;
    private Runnable mOnBackToLetters;
    private int mDeleteRepeatCount;

    private final Runnable mDeleteRepeat = new Runnable() {
        @Override
        public void run() {
            mDeleteRepeatCount++;
            sendDelete(mDeleteRepeatCount);
            mHandler.postDelayed(this, mRepeatInterval);
        }
    };

    public EmojiPanelView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mRepeatStartTimeout = context.getResources().getInteger(
                R.integer.config_key_repeat_start_timeout);
        mRepeatInterval = context.getResources().getInteger(R.integer.config_key_repeat_interval);
    }

    public void setListeners(final KeyboardActionListener listener,
            final Runnable onBackToLetters) {
        mListener = listener;
        mOnBackToLetters = onBackToLetters;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final EmojiPickerView picker = findViewById(R.id.emoji_panel_picker);
        picker.setOnEmojiPickedListener(item -> {
            mListener.onPressKey(Constants.CODE_OUTPUT_TEXT, 0, true);
            mListener.onTextInput(item.getEmoji());
            mListener.onReleaseKey(Constants.CODE_OUTPUT_TEXT, false);
        });
        findViewById(R.id.emoji_panel_letters).setOnClickListener(v -> {
            if (mOnBackToLetters != null) {
                mOnBackToLetters.run();
            }
        });
        findViewById(R.id.emoji_panel_delete).setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.setPressed(true);
                mDeleteRepeatCount = 0;
                sendDelete(0);
                mHandler.postDelayed(mDeleteRepeat, mRepeatStartTimeout);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                v.setPressed(false);
                mHandler.removeCallbacks(mDeleteRepeat);
                return true;
            default:
                return true;
            }
        });
    }

    private void sendDelete(final int repeatCount) {
        mListener.onPressKey(Constants.CODE_DELETE, repeatCount, true);
        mListener.onCodeInput(Constants.CODE_DELETE, Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE, repeatCount > 0);
        mListener.onReleaseKey(Constants.CODE_DELETE, false);
    }

    /** Shows the panel over the keyboard view, with the keyboard's size, padding and background. */
    public void show(final View keyboardView) {
        getLayoutParams().height = keyboardView.getHeight();
        // Same insets as the keyboard, which pads itself for the navigation bar on newer Android.
        setPadding(keyboardView.getPaddingLeft(), keyboardView.getPaddingTop(),
                keyboardView.getPaddingRight(), keyboardView.getPaddingBottom());
        final Drawable background = keyboardView.getBackground();
        if (background != null && background.getConstantState() != null) {
            final Drawable copy = background.getConstantState().newDrawable().mutate();
            // The custom keyboard color is a color filter, which the constant state leaves out.
            copy.setColorFilter(background.getColorFilter());
            setBackground(copy);
        }
        setVisibility(View.VISIBLE);
        requestLayout();
    }

    public void hide() {
        mHandler.removeCallbacks(mDeleteRepeat);
        setVisibility(View.GONE);
    }

    public boolean isShowing() {
        return getVisibility() == View.VISIBLE;
    }
}
