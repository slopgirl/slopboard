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

import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;

/**
 * Reads the emoji off androidx's internal EmojiView (the picker's grid cell), which has no
 * public API for it. Kept from R8 by proguard-rules.pro. Everything degrades to "not an emoji
 * view" if the library changes.
 */
final class EmojiViewAccess {
    private static final String TAG = EmojiViewAccess.class.getSimpleName();
    private static final Class<?> EMOJI_VIEW_CLASS;
    private static final Method GET_EMOJI;

    static {
        Class<?> emojiViewClass = null;
        Method getEmoji = null;
        try {
            emojiViewClass = Class.forName("androidx.emoji2.emojipicker.EmojiView");
            getEmoji = emojiViewClass.getMethod("getEmoji");
        } catch (final ReflectiveOperationException e) {
            Log.w(TAG, "EmojiView not accessible, no emoji preview bubble", e);
            emojiViewClass = null;
        }
        EMOJI_VIEW_CLASS = emojiViewClass;
        GET_EMOJI = getEmoji;
    }

    private EmojiViewAccess() {
        // This utility class is not publicly instantiable.
    }

    static boolean isEmojiView(final View view) {
        return EMOJI_VIEW_CLASS != null && EMOJI_VIEW_CLASS.isInstance(view);
    }

    static CharSequence getEmoji(final View emojiView) {
        try {
            return (CharSequence) GET_EMOJI.invoke(emojiView);
        } catch (final ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }
}
