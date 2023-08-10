/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.text;

import net.minecraft.network.chat.Style;
import net.minecraft.util.*;

import javax.annotation.Nonnull;

/**
 * Similar to {@link FormattedTextWrapper}, used in EditBox.
 */
public class VanillaTextWrapper implements FormattedCharSequence {

    @Nonnull
    public final String mText;

    public VanillaTextWrapper(@Nonnull String text) {
        mText = text;
    }

    /**
     * Needed only when compositing, do not use explicitly. This should be equivalent to
     * {@link StringDecomposer#iterate(String, Style, FormattedCharSink)}.
     *
     * @param sink code point consumer
     * @return true if all chars consumed, false otherwise
     * @see FormattedCharSequence#forward(String, Style)
     */
    @Override
    public boolean accept(@Nonnull FormattedCharSink sink) {
        // do not reorder, transfer code points in logical order
        return StringDecomposer.iterate(mText, Style.EMPTY, sink);
    }
}
