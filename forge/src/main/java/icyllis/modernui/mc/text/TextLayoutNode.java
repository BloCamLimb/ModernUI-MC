/*
 * Modern UI.
 * Copyright (C) 2019-2022 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import icyllis.modernui.graphics.font.GLBakedGlyph;
import net.minecraft.client.renderer.MultiBufferSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Random;

/**
 * The layout node contains all glyph layout information and rendering information.
 */
public class TextLayoutNode {

    /**
     * For obfuscated characters.
     */
    private static final Random RANDOM = new Random();

    /**
     * Sometimes naive, too simple.
     * <p>
     * This singleton cannot be inserted into the cache!
     */
    public static final TextLayoutNode EMPTY = new TextLayoutNode(new char[0], new GLBakedGlyph[0], new float[0],
            new float[0], new int[0], new int[0], new int[0], 0, false, false, false) {
        @Nonnull
        @Override
        TextLayoutNode get() {
            throw new UnsupportedOperationException();
        }

        @Override
        boolean tick(int threshold) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float drawText(@Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, @Nullable String raw,
                              float x, float y, int r, int g, int b, int a, boolean isShadow, boolean seeThrough,
                              int background, int packedLight, float guiScale, float resLevel) {
            return 0;
        }

        @Override
        public void drawTextGlow(@Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, float x, float y,
                                 int r, int g, int b, int a, int packedLight, float guiScale, float resLevel) {
            // noop
        }
    };

    /**
     * Default vertical adjustment to string position.
     */
    public static final int DEFAULT_BASELINE_OFFSET = 7;

    /**
     * Config vertical adjustment to string position.
     */
    public static float sBaselineOffset = DEFAULT_BASELINE_OFFSET;

    /**
     * The copied text buffer without formatting codes in logical order.
     */
    private final char[] mTextBuf;

    /**
     * All baked glyphs for rendering, empty glyphs have been removed from this array.
     * The order is visually left-to-right (i.e. in visual order). Fast digit chars and
     * obfuscated chars are {@link icyllis.modernui.mc.text.TextLayoutEngine.FastCharSet}.
     */
    private final GLBakedGlyph[] mGlyphs;

    /**
     * Position x1 y1 x2 y2... relative to the same point, for rendering glyphs.
     * These values are not offset to glyph additional baseline but aligned.
     * Same indexing with {@link #mGlyphs}, align to left, in visual order.
     * <p>
     * Note the values are scaled to Minecraft GUI coordinates.
     */
    private final float[] mPositions;

    /**
     * The length and order are relative to the raw string (with formatting codes).
     * Only grapheme cluster bounds have advances, others are zeros. For example:
     * [13.57, 0, 14.26, 0, 0]. {@link #mGlyphs}.length may less than grapheme cluster
     * count (invisible glyphs are removed). Logical order.
     * <p>
     * Note the values are scaled to Minecraft GUI coordinates.
     */
    private final float[] mAdvances;

    /*
     * lower 24 bits - 0xRRGGBB color
     * higher 8 bits
     * |--------|
     *         1  BOLD
     *        1   ITALIC
     *       1    UNDERLINE
     *      1     STRIKETHROUGH
     *     1      OBFUSCATED
     *    1       FAST_DIGIT_REPLACEMENT
     *   1        BITMAP_REPLACEMENT
     *  1         IMPLICIT_COLOR
     * |--------|
     */
    /**
     * Glyph rendering flags. Same indexing with {@link #mGlyphs}, in visual order.
     */
    private final int[] mCharFlags;

    /**
     * Glyphs to relative char indices of the strip string (without formatting codes).
     * For vanilla layout ({@link VanillaLayoutKey} and {@link TextLayoutEngine#lookupVanillaNode(String)}),
     * these will be adjusted to string index (with formatting codes).
     * Same indexing with {@link #mGlyphs}, in visual order.
     */
    private final int[] mCharIndices;

    /**
     * Strip indices that are boundaries for Unicode line breaking, in logical order.
     * 0 is not included. Last value is always the text length (without formatting codes).
     */
    private final int[] mLineBoundaries;

    /**
     * Total advance of this text node.
     * <p>
     * Note the values are scaled to Minecraft GUI coordinates.
     */
    private final float mTotalAdvance;

    /**
     * Precomputed value that indicates whether flags array contains any text effect flag.
     */
    private final boolean mHasEffect;
    private final boolean mHasFastDigit;
    private final boolean mHasColorBitmap;

    /**
     * Elapsed time in seconds since last use.
     */
    private transient int mTimer = 0;

    private TextLayoutNode(@Nonnull TextLayoutNode node) {
        mTextBuf = node.mTextBuf;
        mGlyphs = node.mGlyphs;
        mPositions = node.mPositions;
        mAdvances = node.mAdvances;
        mCharFlags = node.mCharFlags;
        mCharIndices = node.mCharIndices;
        mLineBoundaries = node.mLineBoundaries;
        mTotalAdvance = node.mTotalAdvance;
        mHasEffect = node.mHasEffect;
        mHasFastDigit = node.mHasFastDigit;
        mHasColorBitmap = node.mHasColorBitmap;
    }

    TextLayoutNode(@Nonnull char[] textBuf, @Nonnull GLBakedGlyph[] glyphs, @Nonnull float[] positions,
                   @Nonnull float[] advances, @Nonnull int[] charFlags, @Nonnull int[] charIndices,
                   @Nonnull int[] lineBoundaries, float totalAdvance, boolean hasEffect,
                   boolean hasFastDigit, boolean hasColorBitmap) {
        mTextBuf = textBuf;
        mGlyphs = glyphs;
        mPositions = positions;
        mAdvances = advances;
        mCharFlags = charFlags;
        mCharIndices = charIndices;
        mLineBoundaries = lineBoundaries;
        mTotalAdvance = totalAdvance;
        mHasEffect = hasEffect;
        mHasFastDigit = hasFastDigit;
        mHasColorBitmap = hasColorBitmap;
        assert mTextBuf.length == mAdvances.length;
        assert mGlyphs.length * 2 == mPositions.length;
        assert mGlyphs.length == mCharFlags.length;
        assert mGlyphs.length == mCharIndices.length;
    }

    /**
     * Make a new empty node. For those have no rendering info but store them into cache.
     *
     * @return a new empty node as fallback
     */
    @Nonnull
    public static TextLayoutNode makeEmpty() {
        return new TextLayoutNode(EMPTY);
    }

    /**
     * Cache access.
     *
     * @return this with timer reset
     */
    @Nonnull
    TextLayoutNode get() {
        mTimer = 0;
        return this;
    }

    /**
     * Cache access. Increment internal timer by one (second).
     *
     * @return true to recycle
     */
    boolean tick(int threshold) {
        return ++mTimer > threshold;
    }

    /**
     * Render this text in Minecraft render system.
     *
     * @param matrix      the transform matrix
     * @param source      the vertex buffer source
     * @param raw         the raw string of vanilla layout for fast digit replacement
     * @param x           the left pos of the text line to render
     * @param y           the baseline of the text line to render
     * @param r           the default red value (0...255, was divided by 4 if isShadow=true)
     * @param g           the default green value (0...255, was divided by 4 if isShadow=true)
     * @param b           the default blue value (0...255, was divided by 4 if isShadow=true)
     * @param a           the alpha value (0...255)
     * @param isShadow    whether to use a darker color to draw?
     * @param seeThrough  whether this text visible behind a wall?
     * @param background  the background color of the text in 0xAARRGGBB format
     * @param packedLight see {@link net.minecraft.client.renderer.LightTexture}
     * @param guiScale    the gui scale factor
     * @param resLevel    the resolution level used to create this node
     * @return the total advance, always positive
     */
    public float drawText(@Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, @Nullable String raw,
                          float x, float y, int r, int g, int b, int a, boolean isShadow, boolean seeThrough,
                          int background, int packedLight, float guiScale, float resLevel) {
        if (mGlyphs.length == 0) {
            // e.g. text contains only spaces
            return mTotalAdvance;
        }
        final int startR = r;
        final int startG = g;
        final int startB = b;

        final var glyphs = mGlyphs;
        final var positions = mPositions;
        final var flags = mCharFlags;
        //final boolean alignPixels = TextLayoutProcessor.sAlignPixels;

        y += sBaselineOffset;

        int texture = -1;
        VertexConsumer builder = null;

        for (int i = 0, e = glyphs.length; i < e; i++) {
            var glyph = glyphs[i];
            final int flag = flags[i];
            final float rx;
            final float ry;
            final float w;
            final float h;
            if ((flag & CharacterStyle.BITMAP_REPLACEMENT) != 0) {
                if (isShadow) {
                    continue;
                }
                rx = x + positions[i << 1] + (float) glyph.x / TextLayoutEngine.BITMAP_SCALE;
                ry = y + positions[(i << 1) + 1] + (float) glyph.y / TextLayoutEngine.BITMAP_SCALE;
                w = (float) glyph.width / TextLayoutEngine.BITMAP_SCALE;
                h = (float) glyph.height / TextLayoutEngine.BITMAP_SCALE;
            } else {
                if (raw != null && (flag & CharacterStyle.FAST_DIGIT_REPLACEMENT) != 0) {
                    var chars = (TextLayoutEngine.FastCharSet) glyph;
                    int fastIndex = raw.charAt(mCharIndices[i]) - '0';
                    if (fastIndex < 0 || fastIndex > 9) {
                        continue;
                    }
                    glyph = chars.glyphs[fastIndex];
                    if (fastIndex != 0) {
                        rx = x + positions[i << 1] + glyph.x / resLevel + chars.offsets[fastIndex];
                    } else {
                        // 0 is standard, no additional offset
                        rx = x + positions[i << 1] + glyph.x / resLevel;
                    }
                } else if ((flag & CharacterStyle.OBFUSCATED_MASK) != 0) {
                    var chars = (TextLayoutEngine.FastCharSet) glyph;
                    int fastIndex = RANDOM.nextInt(chars.glyphs.length);
                    glyph = chars.glyphs[fastIndex];
                    if (fastIndex != 0) {
                        rx = x + positions[i << 1] + glyph.x / resLevel + chars.offsets[fastIndex];
                    } else {
                        // 0 is standard, no additional offset
                        rx = x + positions[i << 1] + glyph.x / resLevel;
                    }
                } else {
                    rx = x + positions[i << 1] + glyph.x / resLevel;
                }
                ry = y + positions[(i << 1) + 1] + glyph.y / resLevel;

                w = glyph.width / resLevel;
                h = glyph.height / resLevel;
            }
            /*if (alignPixels) {
                rx = Math.round(rx * scale) / scale;
                ry = Math.round(ry * scale) / scale;
            }*/
            if ((flag & CharacterStyle.IMPLICIT_COLOR_MASK) != 0) {
                r = startR;
                g = startG;
                b = startB;
            } else {
                r = flag >> 16 & 0xff;
                g = flag >> 8 & 0xff;
                b = flag & 0xff;
                if (isShadow) {
                    r >>= 2;
                    g >>= 2;
                    b >>= 2;
                }
            }
            if (builder == null || texture != glyph.texture) {
                texture = glyph.texture;
                builder = source.getBuffer(TextRenderType.getOrCreate(texture, seeThrough));
            }
            builder.vertex(matrix, rx, ry, 0)
                    .color(r, g, b, a)
                    .uv(glyph.u1, glyph.v1)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx, ry + h, 0)
                    .color(r, g, b, a)
                    .uv(glyph.u1, glyph.v2)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx + w, ry + h, 0)
                    .color(r, g, b, a)
                    .uv(glyph.u2, glyph.v2)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx + w, ry, 0)
                    .color(r, g, b, a)
                    .uv(glyph.u2, glyph.v1)
                    .uv2(packedLight)
                    .endVertex();
        }

        builder = null;

        if (mHasEffect) {
            builder = source.getBuffer(EffectRenderType.getRenderType(seeThrough));
            for (int i = 0, e = glyphs.length; i < e; i++) {
                final int flag = flags[i];
                if ((flag & CharacterStyle.EFFECT_MASK) == 0) {
                    continue;
                }
                if ((flag & CharacterStyle.IMPLICIT_COLOR_MASK) != 0) {
                    r = startR;
                    g = startG;
                    b = startB;
                } else {
                    r = flag >> 16 & 0xff;
                    g = flag >> 8 & 0xff;
                    b = flag & 0xff;
                    if (isShadow) {
                        r >>= 2;
                        g >>= 2;
                        b >>= 2;
                    }
                }
                //TODO wrong in RTL direction
                final float rx1 = x + positions[i << 1];
                final float rx2 = x + ((i + 1 == e) ? mTotalAdvance : positions[(i + 1) << 1]);
                if ((flag & CharacterStyle.STRIKETHROUGH_MASK) != 0) {
                    TextRenderEffect.drawStrikethrough(matrix, builder, rx1, rx2, y,
                            r, g, b, a, packedLight);
                }
                if ((flag & CharacterStyle.UNDERLINE_MASK) != 0) {
                    TextRenderEffect.drawUnderline(matrix, builder, rx1, rx2, y,
                            r, g, b, a, packedLight);
                }
            }
        }

        if ((background & 0xFF000000) != 0) {
            y -= sBaselineOffset;
            a = background >>> 24;
            r = background >> 16 & 0xff;
            g = background >> 8 & 0xff;
            b = background & 0xff;
            if (builder == null) {
                builder = source.getBuffer(EffectRenderType.getRenderType(seeThrough));
            }
            builder.vertex(matrix, x - 1, y + 9, TextRenderEffect.EFFECT_DEPTH)
                    .color(r, g, b, a).uv(0, 1).uv2(packedLight).endVertex();
            builder.vertex(matrix, x + mTotalAdvance + 1, y + 9, TextRenderEffect.EFFECT_DEPTH)
                    .color(r, g, b, a).uv(1, 1).uv2(packedLight).endVertex();
            builder.vertex(matrix, x + mTotalAdvance + 1, y - 1, TextRenderEffect.EFFECT_DEPTH)
                    .color(r, g, b, a).uv(1, 0).uv2(packedLight).endVertex();
            builder.vertex(matrix, x - 1, y - 1, TextRenderEffect.EFFECT_DEPTH)
                    .color(r, g, b, a).uv(0, 0).uv2(packedLight).endVertex();
        }

        return mTotalAdvance;
    }

    /**
     * Special case of drawText() when drawing the glowing outline of drawText8xOutline().
     * No fast digit replacement, no shadow, no background, no underline, no strikethrough,
     * no bitmap replacement, force to use input color, can have obfuscated rendering (but should not).
     *
     * @param matrix      the position transformation
     * @param source      the vertex buffer source
     * @param x           the left pos of the text line to render
     * @param y           the baseline of the text line to render
     * @param r           the default outline red value (0...255)
     * @param g           the default outline green value (0...255)
     * @param b           the default outline blue value (0...255)
     * @param a           the alpha value (0...255)
     * @param packedLight see {@link net.minecraft.client.renderer.LightTexture}
     * @param guiScale    the gui scale factor
     * @param resLevel    the resolution level used to create this node
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void drawTextGlow(@Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source,
                             float x, float y, int r, int g, int b, int a, int packedLight,
                             float guiScale, float resLevel) {
        if (mGlyphs.length == 0) {
            return;
        }

        final var glyphs = mGlyphs;
        final var positions = mPositions;
        final var flags = mCharFlags;
        //final boolean alignPixels = TextLayoutProcessor.sAlignPixels;

        y += sBaselineOffset;

        int texture = -1;
        VertexConsumer builder = null;

        final float sBloat = Math.min(1.0f, 3.0f / resLevel);
        for (int i = 0, e = glyphs.length; i < e; i++) {
            var glyph = glyphs[i];
            final int flag = flags[i];
            final float rx;
            final float ry;
            final float w;
            final float h;
            if ((flag & CharacterStyle.BITMAP_REPLACEMENT) != 0) {
                continue;
            } else {
                if ((flag & CharacterStyle.OBFUSCATED_MASK) != 0) {
                    var chars = (TextLayoutEngine.FastCharSet) glyph;
                    int fastIndex = RANDOM.nextInt(chars.glyphs.length);
                    glyph = chars.glyphs[fastIndex];
                    if (fastIndex != 0) {
                        rx = x + positions[i << 1] + glyph.x / resLevel + chars.offsets[fastIndex];
                    } else {
                        // 0 is standard, no additional offset
                        rx = x + positions[i << 1] + glyph.x / resLevel;
                    }
                } else {
                    rx = x + positions[i << 1] + glyph.x / resLevel;
                }
                ry = y + positions[(i << 1) + 1] + glyph.y / resLevel;

                w = glyph.width / resLevel;
                h = glyph.height / resLevel;
            }
            /*if (alignPixels) {
                rx = Math.round(rx * scale) / scale;
                ry = Math.round(ry * scale) / scale;
            }*/
            if (builder == null || texture != glyph.texture) {
                texture = glyph.texture;
                builder = source.getBuffer(TextRenderType.getOrCreateGlow(texture));
            }
            float uBloat = 1.5f * (glyph.u2 - glyph.u1) / glyph.width;
            float vBloat = 1.5f * (glyph.v2 - glyph.v1) / glyph.height;
            builder.vertex(matrix, rx - sBloat, ry - sBloat, 0.0001f)
                    .color(r, g, b, a)
                    .uv(glyph.u1 - uBloat, glyph.v1 - vBloat)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx - sBloat, ry + h + sBloat, 0.0001f)
                    .color(r, g, b, a)
                    .uv(glyph.u1 - uBloat, glyph.v2 + vBloat)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx + w + sBloat, ry + h + sBloat, 0)
                    .color(r, g, b, a)
                    .uv(glyph.u2 + uBloat, glyph.v2 + vBloat)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx + w + sBloat, ry - sBloat, 0)
                    .color(r, g, b, a)
                    .uv(glyph.u2 + uBloat, glyph.v1 - vBloat)
                    .uv2(packedLight)
                    .endVertex();
        }
    }

    /**
     * The copied text buffer without formatting codes in logical order.
     */
    @Nonnull
    public char[] getTextBuf() {
        return mTextBuf;
    }

    /**
     * All baked glyphs for rendering, empty glyphs have been removed from this array.
     * The order is visually left-to-right (i.e. in visual order). Fast digit chars and
     * obfuscated chars are {@link icyllis.modernui.mc.text.TextLayoutEngine.FastCharSet}.
     */
    @Nonnull
    public GLBakedGlyph[] getGlyphs() {
        return mGlyphs;
    }

    /**
     * Position x1 y1 x2 y2... relative to the same point, for rendering glyphs.
     * These values are not offset to glyph additional baseline but aligned.
     * Same indexing with {@link #getGlyphs()}, align to left, in visual order.
     * <p>
     * Note the values are scaled to Minecraft GUI coordinates.
     */
    @Nonnull
    public float[] getPositions() {
        return mPositions;
    }

    /**
     * The length and order are relative to the raw string (with formatting codes).
     * Only grapheme cluster bounds have advances, others are zeros. For example:
     * [13.57, 0, 14.26, 0, 0]. {@link #getGlyphs()}.length may less than grapheme
     * cluster count (invisible glyphs are removed). Logical order.
     * <p>
     * Note the values are scaled to Minecraft GUI coordinates.
     */
    @Nonnull
    public float[] getAdvances() {
        return mAdvances;
    }

    /**
     * Returns the number of chars (i.e. the length of char array) of the full stripped
     * string (without formatting codes).
     *
     * @return length of the text
     */
    public int getLength() {
        return mTextBuf.length;
    }

    /**
     * Glyph rendering flags. Same indexing with {@link #getGlyphs()}, in visual order.
     *
     * @see CharacterStyle
     */
    @Nonnull
    public int[] getCharFlags() {
        return mCharFlags;
    }

    /**
     * Glyphs to relative char indices of the strip string (without formatting codes). However,
     * for vanilla layout {@link VanillaLayoutKey} and {@link TextLayoutEngine#lookupVanillaNode(String)},
     * these will be adjusted to string index (with formatting codes).
     * Same indexing with {@link #getGlyphs()}, in visual order.
     */
    @Nonnull
    public int[] getCharIndices() {
        return mCharIndices;
    }

    /**
     * Strip indices that are boundaries for Unicode line breaking, in logical order.
     * 0 is not included. Last value is always the text length (without formatting codes).
     */
    @Nonnull
    public int[] getLineBoundaries() {
        return mLineBoundaries;
    }

    /**
     * Total advance of this text node.
     * <p>
     * Note the values are scaled to Minecraft GUI coordinates.
     */
    public float getTotalAdvance() {
        return mTotalAdvance;
    }

    /**
     * Precomputed value that indicates whether flags array contains any text effect flag.
     */
    public boolean hasEffect() {
        return mHasEffect;
    }

    /**
     * Precomputed value that indicates whether flags array contains any fast digit replacement flag.
     */
    public boolean hasFastDigit() {
        return mHasFastDigit;
    }

    /**
     * Precomputed value that indicates whether flags array contains any bitmap replacement flag.
     */
    public boolean hasColorBitmap() {
        return mHasColorBitmap;
    }

    /**
     * @return measurable memory size in bytes of this object
     */
    public int getMemorySize() {
        int size = 0;
        int glyphs = mGlyphs.length;
        size += 32 + (((glyphs + 1) >> 1) << 4); // glyphs + charIndices
        size += 16 + (((glyphs + 1) >> 1) << 3); // flags
        size += 16 + (glyphs << 3); // positions
        size += 16 + (((mTextBuf.length + 3) >> 1) << 2);
        size += 16 + (((mAdvances.length + 1) >> 1) << 3);
        size += 16 + (((mLineBoundaries.length + 1) >> 1) << 3);
        return size + 24;
    }

    @Override
    public String toString() {
        return "TextLayoutNode{" +
                "text=" + toEscapeChars(mTextBuf) +
                ",glyphs=" + mGlyphs.length +
                ",length=" + mTextBuf.length +
                ",positions=" + toPositionString(mPositions) +
                ",advances=" + Arrays.toString(mAdvances) +
                ",charFlags=" + toFlagString(mCharFlags) +
                ",charIndices=" + Arrays.toString(mCharIndices) +
                ",lineBoundaries" + Arrays.toString(mLineBoundaries) +
                ",totalAdvance=" + mTotalAdvance +
                ",hasEffect=" + mHasEffect +
                ",hasFastDigit=" + mHasFastDigit +
                ",hasColorBitmap=" + mHasColorBitmap +
                '}';
    }

    @Nonnull
    private static String toEscapeChars(@Nonnull char[] a) {
        int iMax = a.length - 1;
        if (iMax == -1)
            return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; ; i++) {
            b.append("\\u");
            String s = Integer.toHexString(a[i]);
            b.append("0".repeat(4 - s.length()));
            b.append(s);
            if (i == iMax)
                return b.toString();
        }
    }

    @Nonnull
    private static String toPositionString(@Nonnull float[] a) {
        int iMax = a.length - 1;
        if (iMax == -1)
            return "[]";
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ; i++) {
            b.append('(');
            b.append(a[i++]);
            b.append(',');
            b.append(a[i]);
            b.append(')');
            if (i == iMax)
                return b.append(']').toString();
            b.append(", ");
        }
    }

    @Nonnull
    private static String toFlagString(@Nonnull int[] a) {
        int iMax = a.length - 1;
        if (iMax == -1)
            return "[]";
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ; i++) {
            b.append("0x");
            b.append(Integer.toHexString(a[i]));
            if (i == iMax)
                return b.append(']').toString();
            b.append(" ");
        }
    }
}
