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

import com.mojang.blaze3d.font.SpaceProvider;
import icyllis.arc3d.core.Strike;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.text.*;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * @see com.mojang.blaze3d.font.SpaceProvider
 */
public class SpaceFont implements Font {

    private final String mFontName;
    private final Int2FloatOpenHashMap mAdvances;

    private SpaceFont(String fontName, Int2FloatOpenHashMap advances) {
        mFontName = fontName;
        advances.defaultReturnValue(Float.NaN);
        mAdvances = advances;
    }

    @Nonnull
    public static SpaceFont create(ResourceLocation fontName, SpaceProvider.Definition definition) {
        return new SpaceFont(
                fontName.toString() + " / minecraft:space",
                new Int2FloatOpenHashMap(definition.advances())
        );
    }

    @Override
    public int getStyle() {
        return FontPaint.NORMAL;
    }

    @Override
    public String getFullName(@Nonnull Locale locale) {
        return mFontName;
    }

    @Override
    public String getFamilyName(@Nonnull Locale locale) {
        return mFontName;
    }

    @Override
    public int getMetrics(@Nonnull FontPaint paint, FontMetricsInt fm) {
        // unused in Minecraft
        return 0;
    }

    @Override
    public boolean hasGlyph(int ch, int vs) {
        return mAdvances.containsKey(ch);
    }

    @Override
    public float doSimpleLayout(char[] buf, int start, int limit,
                                FontPaint paint, IntArrayList glyphs,
                                FloatArrayList positions, float x, float y) {
        return doComplexLayout(buf, start, limit, start, limit,
                false, paint, glyphs, positions, null, 0,
                null, x, y);
    }

    @Override
    public float doComplexLayout(char[] buf,
                                 int contextStart, int contextLimit,
                                 int layoutStart, int layoutLimit,
                                 boolean isRtl, FontPaint paint,
                                 IntArrayList glyphs, FloatArrayList positions,
                                 float[] advances, int advanceOffset,
                                 Rect bounds, float x, float y) {
        // Measure code point in visual order
        // We simply ignore the context range

        // No text shaping, no BiDi support

        // Bitmap font cannot be scaled
        float scaleUp = (int) (paint.getFontSize() / TextLayoutProcessor.sBaseFontSize + 0.5);

        char _c1, _c2;
        float advance = 0;
        // Process code point in visual order
        for (int index = layoutStart; index < layoutLimit; index++) {
            int ch, i = index;
            _c1 = buf[index];
            if (Character.isHighSurrogate(_c1) && index + 1 < layoutLimit) {
                _c2 = buf[index + 1];
                if (Character.isLowSurrogate(_c2)) {
                    ch = Character.toCodePoint(_c1, _c2);
                    ++index;
                } else {
                    ch = _c1;
                }
            } else {
                ch = _c1;
            }

            float adv = getAdvance(ch);
            if (Float.isNaN(adv)) {
                continue;
            }

            adv *= scaleUp;
            if (advances != null) {
                advances[i - advanceOffset] = adv;
            }

            if (glyphs != null) {
                glyphs.add(ch);
            }
            if (positions != null) {
                positions.add(x + advance);
                positions.add(y);
            }

            advance += adv;
        }

        return advance;
    }

    @Override
    public Strike findOrCreateStrike(FontPaint paint) {
        return null;
    }

    public float getAdvance(int ch) {
        return mAdvances.get(ch);
    }
}
