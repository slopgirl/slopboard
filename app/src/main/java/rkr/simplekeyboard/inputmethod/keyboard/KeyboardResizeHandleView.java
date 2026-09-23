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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;
import rkr.simplekeyboard.inputmethod.latin.utils.ResourceUtils;

/**
 * Bar above the keyboard, shown while the keyboard height dialog is open. Dragging it previews
 * the keyboard height live (Settings#setPreviewValue); the dialog follows and saves it on OK.
 */
public final class KeyboardResizeHandleView extends View {
    private final Paint mGripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mGripRect = new RectF();
    private final float mGripWidth;
    private final float mGripHeight;
    private final int mMinPercent;
    private final int mMaxPercent;

    private float mDragStartRawY;
    private int mDragStartHeight;
    private int mPercent;

    public KeyboardResizeHandleView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final Resources res = context.getResources();
        final TypedArray a = context.obtainStyledAttributes(new int[] { R.attr.functionalTextColor });
        final int color = a.getColor(0, 0xFF808080);
        a.recycle();
        mGripPaint.setColor(color);
        mTextPaint.setColor(color);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 12, res.getDisplayMetrics()));
        mGripWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40,
                res.getDisplayMetrics());
        mGripHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4,
                res.getDisplayMetrics());
        mMinPercent = res.getInteger(R.integer.config_min_keyboar_height);
        mMaxPercent = res.getInteger(R.integer.config_max_keyboar_height);
    }

    /** Called with the keyboard's current height scale, to label the handle. */
    public void setHeightScale(final float scale) {
        mPercent = Math.round(scale * 100);
        invalidate();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        final float centerX = getWidth() / 2f;
        final float gripTop = getHeight() * 0.25f;
        mGripRect.set(centerX - mGripWidth / 2, gripTop, centerX + mGripWidth / 2,
                gripTop + mGripHeight);
        canvas.drawRoundRect(mGripRect, mGripHeight / 2, mGripHeight / 2, mGripPaint);
        final float textY = getHeight() * 0.8f;
        canvas.drawText(getResources().getString(R.string.abbreviation_unit_percent, mPercent),
                centerX, textY, mTextPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final int defaultHeight = ResourceUtils.getDefaultKeyboardHeight(getResources());
        switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            mDragStartRawY = event.getRawY();
            mDragStartHeight = Math.round(defaultHeight * mPercent / 100f);
            return true;
        case MotionEvent.ACTION_MOVE:
            // Dragging up makes the keyboard taller.
            final float height = mDragStartHeight + (mDragStartRawY - event.getRawY());
            final int percent = Math.max(mMinPercent, Math.min(mMaxPercent,
                    Math.round(height * 100 / defaultHeight)));
            if (percent != mPercent) {
                setHeightScale(percent / 100f);
                Settings.getInstance().setPreviewValue(Settings.PREF_KEYBOARD_HEIGHT,
                        percent / 100f);
            }
            return true;
        default:
            return true;
        }
    }
}
