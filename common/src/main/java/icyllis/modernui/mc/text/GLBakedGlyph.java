/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
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

/**
 * This class holds information for a glyph about its pre-rendered image in a
 * GL texture. The glyph must be laid-out so that it has something to render
 * in a context.
 *
 * @see GlyphManager
 * @see GLFontAtlas
 * @since 2.0
 */
public class GLBakedGlyph {

    /**
     * The horizontal offset to baseline in pixels, i.e. bearing X.
     */
    public int x; // x = Integer.MIN_VALUE means invalid

    /**
     * The vertical offset to baseline in pixels, i.e. bearing Y.
     */
    public int y;

    /**
     * The width of this glyph image in pixels (w/o padding).
     * For bitmap font, this is not one-to-one mapped to texture coordinates.
     */
    public short width;

    /**
     * The height of this glyph image in pixels (w/o padding).
     * For bitmap font, this is not one-to-one mapped to texture coordinates.
     */
    public short height;

    /**
     * The horizontal texture coordinate of the upper-left corner.
     */
    public float u1;

    /**
     * The vertical texture coordinate of the upper-left corner.
     */
    public float v1;

    /**
     * The horizontal texture coordinate of the lower-right corner.
     */
    public float u2;

    /**
     * The vertical texture coordinate of the lower-right corner.
     */
    public float v2;

    public GLBakedGlyph() {
        x = Integer.MIN_VALUE;
    }

    @Override
    public String toString() {
        return "GLBakedGlyph{x=" + x +
                ",y=" + y +
                ",w=" + width +
                ",h=" + height +
                ",u1=" + u1 +
                ",v1=" + v1 +
                ",u2=" + u2 +
                ",v2=" + v2 +
                '}';
    }
}
