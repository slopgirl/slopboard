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
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.emoji2.emojipicker.EmojiPickerView;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.common.Constants;
import rkr.simplekeyboard.inputmethod.latin.utils.ViewLayoutUtils;

/**
 * Emoji picker shown in place of the keyboard by the emoji key, with a bar to go back to the
 * letters and a delete key that repeats while held.
 */
public final class EmojiPanelView extends LinearLayout {
    private static final float MAX_HEIGHT_TO_KEYBOARD = 1.5f;
    private static final float MAX_HEIGHT_TO_SCREEN = 0.6f;
    // EmojiPickerView draws each emoji into a bitmap at this text size and scales that to its
    // grid cell (see androidx EmojiView), so cells wider than the bitmap make emoji blurry.
    private static final float PICKER_EMOJI_TEXT_SIZE_SP = 30;

    // Emoji preview bubble: a multiple of the grid cell, drawn above the held emoji.
    private static final float BUBBLE_SIZE_TO_CELL = 1.8f;
    private static final float BUBBLE_TEXT_TO_CELL = 1.1f;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int mRepeatStartTimeout;
    private final int mRepeatInterval;

    private KeyboardActionListener mListener = KeyboardActionListener.EMPTY_LISTENER;
    private Runnable mOnBackToLetters;
    private int mDeleteRepeatCount;

    private TextView mBubble;
    private boolean mTrackingEmoji;
    private float mDownRawX;
    private float mDownRawY;
    private final int mTouchSlop;
    private final int[] mLocation = new int[2];
    private final Rect mRect = new Rect();

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
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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
        picker.setEmojiGridColumns(getSharpColumnCount());
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

    @Override
    public boolean dispatchTouchEvent(final MotionEvent event) {
        trackEmojiUnderFinger(event);
        return super.dispatchTouchEvent(event);
    }

    // Shows the emoji under the finger in a bubble while it's held; a scroll or lifting the
    // finger hides it. The picker's own variants popup (long press) is a separate window above.
    private void trackEmojiUnderFinger(final MotionEvent event) {
        switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            mDownRawX = event.getRawX();
            mDownRawY = event.getRawY();
            final View emojiView = findEmojiViewAt(this, (int) mDownRawX, (int) mDownRawY);
            mTrackingEmoji = emojiView != null && showBubble(emojiView);
            break;
        case MotionEvent.ACTION_MOVE:
            if (mTrackingEmoji && (Math.abs(event.getRawX() - mDownRawX) > mTouchSlop
                    || Math.abs(event.getRawY() - mDownRawY) > mTouchSlop)) {
                mTrackingEmoji = false;
                hideBubble();
            }
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            mTrackingEmoji = false;
            hideBubble();
            break;
        default:
            break;
        }
    }

    private View findEmojiViewAt(final View view, final int rawX, final int rawY) {
        if (view.getVisibility() != View.VISIBLE || !view.getGlobalVisibleRect(mRect)
                || !mRect.contains(rawX, rawY)) {
            return null;
        }
        if (EmojiViewAccess.isEmojiView(view)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                final View found = findEmojiViewAt(group.getChildAt(i), rawX, rawY);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean showBubble(final View emojiView) {
        final CharSequence emoji = EmojiViewAccess.getEmoji(emojiView);
        final ViewGroup windowContent = getRootView().findViewById(android.R.id.content);
        if (emoji == null || windowContent == null) {
            return false;
        }
        if (mBubble == null) {
            mBubble = new TextView(getContext());
            mBubble.setGravity(Gravity.CENTER);
            mBubble.setIncludeFontPadding(false);
        }
        if (mBubble.getParent() != windowContent) {
            if (mBubble.getParent() != null) {
                ((ViewGroup) mBubble.getParent()).removeView(mBubble);
            }
            windowContent.addView(mBubble);
        }
        final int cell = emojiView.getWidth();
        final int size = Math.round(cell * BUBBLE_SIZE_TO_CELL);
        mBubble.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        mBubble.setTextSize(TypedValue.COMPLEX_UNIT_PX, cell * BUBBLE_TEXT_TO_CELL);
        mBubble.setText(emoji);
        final Drawable background = getContext().getDrawable(
                R.drawable.keyboard_key_feedback_background).mutate();
        if (getBackground() != null) {
            // Follows a custom keyboard color like the key previews do.
            background.setColorFilter(getBackground().getColorFilter());
        }
        mBubble.setBackground(background);

        // Centered above the emoji, kept inside the window horizontally.
        windowContent.getLocationOnScreen(mLocation);
        final int contentX = mLocation[0];
        final int contentY = mLocation[1];
        emojiView.getLocationOnScreen(mLocation);
        final int x = mLocation[0] - contentX + (cell - size) / 2;
        final int y = mLocation[1] - contentY - size;
        mBubble.setTranslationX(Math.max(0, Math.min(windowContent.getWidth() - size, x)));
        mBubble.setTranslationY(Math.max(0, y));
        mBubble.setVisibility(View.VISIBLE);
        mBubble.bringToFront();
        return true;
    }

    private void hideBubble() {
        if (mBubble != null) {
            mBubble.setVisibility(View.GONE);
        }
    }

    // The fewest grid columns whose cells are no wider than the picker's emoji bitmaps.
    private int getSharpColumnCount() {
        final TextPaint paint = new TextPaint();
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                PICKER_EMOJI_TEXT_SIZE_SP, getResources().getDisplayMetrics()));
        final Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
        final int bitmapSize = metrics.bottom - metrics.top;
        final int width = getResources().getDisplayMetrics().widthPixels;
        return Math.max(1, (width + bitmapSize - 1) / bitmapSize);
    }

    private void sendDelete(final int repeatCount) {
        mListener.onPressKey(Constants.CODE_DELETE, repeatCount, true);
        mListener.onCodeInput(Constants.CODE_DELETE, Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE, repeatCount > 0);
        mListener.onReleaseKey(Constants.CODE_DELETE, false);
    }

    /** Shows the panel over the keyboard view, with the keyboard's padding and background. */
    public void show(final View keyboardView) {
        // Taller than the keyboard when there's room: emoji need more space than keys.
        final int keyboardHeight = keyboardView.getHeight();
        final int screenHeight = getResources().getDisplayMetrics().heightPixels;
        getLayoutParams().height = Math.max(keyboardHeight, Math.min(
                Math.round(keyboardHeight * MAX_HEIGHT_TO_KEYBOARD),
                Math.round(screenHeight * MAX_HEIGHT_TO_SCREEN)));
        // Same insets as the keyboard, which pads itself for the navigation bar on newer Android.
        setPadding(keyboardView.getPaddingLeft(), keyboardView.getPaddingTop(),
                keyboardView.getPaddingRight(), keyboardView.getPaddingBottom());
        ViewLayoutUtils.copyBackground(keyboardView, this);
        setVisibility(View.VISIBLE);
        requestLayout();
    }

    public void hide() {
        mHandler.removeCallbacks(mDeleteRepeat);
        mTrackingEmoji = false;
        hideBubble();
        setVisibility(View.GONE);
    }

    public boolean isShowing() {
        return getVisibility() == View.VISIBLE;
    }
}
