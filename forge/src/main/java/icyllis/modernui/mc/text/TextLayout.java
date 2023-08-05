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
import icyllis.arc3d.engine.Engine;
import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.graphics.font.BakedGlyph;
import icyllis.modernui.graphics.text.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Random;

/**
 * The layout contains all glyph layout information and rendering information.
 * <p>
 * This is a Minecraft alternative of framework's {@link icyllis.modernui.graphics.text.ShapedText}.
 */
public class TextLayout {

    /**
     * For obfuscated characters.
     */
    private static final Random RANDOM = new Random();

    /**
     * Sometimes naive, too simple.
     * <p>
     * This singleton cannot be inserted into the cache!
     */
    public static final TextLayout EMPTY = new TextLayout(new char[0], new int[0], new float[0],
            null, new Font[0], new float[0], new int[0], new int[]{0}, 0, false, false, 2, ~0) {
        @Nonnull
        @Override
        TextLayout get() {
            throw new UnsupportedOperationException();
        }

        @Override
        boolean tick() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float drawText(@Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source,
                              float x, float y, int r, int g, int b, int a, boolean isShadow,
                              int preferredMode, int bgColor, int packedLight) {
            return 0;
        }

        @Override
        public void drawTextOutline(@Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, float x, float y,
                                    int r, int g, int b, int a, int packedLight) {
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
    private final int[] mGlyphs;
    private transient BakedGlyph[] mBakedGlyphs;
    private transient BakedGlyph[] mBakedGlyphsForSDF;

    /**
     * Position x1 y1 x2 y2... relative to the same point, for rendering glyphs.
     * These values are not offset to glyph additional baseline but aligned.
     * Same indexing with {@link #mGlyphs}, align to left, in visual order.
     * <p>
     * Note the values are scaled to Minecraft GUI coordinates.
     */
    private final float[] mPositions;

    private final byte[] mFontIndices;
    private final Font[] mFonts;

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
     *    1       COLOR_EMOJI_REPLACEMENT
     *   1        BITMAP_REPLACEMENT
     *  1         IMPLICIT_COLOR
     * |--------|
     */
    /**
     * Glyph rendering flags. Same indexing with {@link #mGlyphs}, in visual order.
     */
    private final int[] mGlyphFlags;

    /*
     * Glyphs to relative char indices of the strip string (without formatting codes).
     * For vanilla layout ({@link VanillaLayoutKey} and {@link TextLayoutEngine#lookupVanillaLayout(String)}),
     * these will be adjusted to string index (with formatting codes).
     * Same indexing with {@link #mGlyphs}, in visual order.
     */
    //private final int[] mCharIndices;

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
    //private final boolean mHasFastDigit;
    private final boolean mHasColorEmoji;
    final int mCreatedResLevel;
    final int mComputedFlags;

    /**
     * Elapsed time in seconds since last use.
     */
    private transient int mTimer = 0;

    private TextLayout(@Nonnull TextLayout layout) {
        mTextBuf = layout.mTextBuf;
        mGlyphs = layout.mGlyphs;
        mPositions = layout.mPositions;
        mFontIndices = layout.mFontIndices;
        mFonts = layout.mFonts;
        mAdvances = layout.mAdvances;
        mGlyphFlags = layout.mGlyphFlags;
        mLineBoundaries = layout.mLineBoundaries;
        mTotalAdvance = layout.mTotalAdvance;
        mHasEffect = layout.mHasEffect;
        mHasColorEmoji = layout.mHasColorEmoji;
        mCreatedResLevel = layout.mCreatedResLevel;
        mComputedFlags = layout.mComputedFlags;
    }

    TextLayout(@Nonnull char[] textBuf, @Nonnull int[] glyphs,
               @Nonnull float[] positions, @Nullable byte[] fontIndices,
               @Nonnull Font[] fonts, @Nullable float[] advances,
               @Nonnull int[] glyphFlags, @Nullable int[] lineBoundaries,
               float totalAdvance, boolean hasEffect, boolean hasColorEmoji,
               int createdResLevel, int computedFlags) {
        mTextBuf = textBuf;
        mGlyphs = glyphs;
        mPositions = positions;
        mFontIndices = fontIndices;
        mFonts = fonts;
        mAdvances = advances;
        mGlyphFlags = glyphFlags;
        mLineBoundaries = lineBoundaries;
        mTotalAdvance = totalAdvance;
        mHasEffect = hasEffect;
        mHasColorEmoji = hasColorEmoji;
        mCreatedResLevel = createdResLevel;
        mComputedFlags = computedFlags;
        assert mAdvances == null ||
                mTextBuf.length == mAdvances.length;
        assert mGlyphs.length * 2 == mPositions.length;
        assert mGlyphs.length == mGlyphFlags.length;
    }

    /**
     * Make a new empty node. For those have no rendering info but store them into cache.
     *
     * @return a new empty node as fallback
     */
    @Nonnull
    public static TextLayout makeEmpty() {
        return new TextLayout(EMPTY);
    }

    /**
     * Cache access.
     *
     * @return this with timer reset
     */
    @Nonnull
    TextLayout get() {
        mTimer = 0;
        return this;
    }

    /**
     * Cache access. Increment internal timer by one (second).
     *
     * @return true to recycle
     */
    boolean tick() {
        // Evict if not used in 3 seconds
        return ++mTimer > 3;
    }

    @Nonnull
    private BakedGlyph[] prepareGlyphs(int resLevel) {
        TextLayoutEngine engine = TextLayoutEngine.getInstance();
        BakedGlyph[] glyphs = new BakedGlyph[mGlyphs.length];
        for (int i = 0; i < glyphs.length; i++) {
            if ((mGlyphFlags[i] & CharacterStyle.OBFUSCATED_MASK) != 0) {
                glyphs[i] = engine.lookupFastChars(
                        getFont(i),
                        resLevel
                );
            } else {
                glyphs[i] = engine.lookupGlyph(
                        getFont(i),
                        resLevel,
                        mGlyphs[i]
                );
            }
        }
        return glyphs;
    }

    @Nonnull
    private BakedGlyph[] getGlyphs(int resLevel) {
        if (resLevel == mCreatedResLevel) {
            if (mBakedGlyphs == null) {
                mBakedGlyphs = prepareGlyphs(resLevel);
            }
            return mBakedGlyphs;
        } else {
            if (mBakedGlyphsForSDF == null) {
                mBakedGlyphsForSDF = prepareGlyphs(resLevel);
            }
            return mBakedGlyphsForSDF;
        }
    }

    /**
     * Render this text in Minecraft render system.
     *
     * @param matrix        the transform matrix
     * @param source        the vertex buffer source
     * @param x             the left pos of the text line to render
     * @param y             the baseline of the text line to render
     * @param r             the default red value (0...255, was divided by 4 if isShadow=true)
     * @param g             the default green value (0...255, was divided by 4 if isShadow=true)
     * @param b             the default blue value (0...255, was divided by 4 if isShadow=true)
     * @param a             the alpha value (0...255)
     * @param isShadow      whether to use a darker color to draw?
     * @param preferredMode a render mode, normal, see through or SDF
     * @param bgColor       the background color of the text in 0xAARRGGBB format
     * @param packedLight   see {@link net.minecraft.client.renderer.LightTexture}
     * @return the total advance, always positive
     */
    public float drawText(@Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source,
                          float x, float y, int r, int g, int b, int a, boolean isShadow,
                          int preferredMode, int bgColor, int packedLight) {
        final int startR = r;
        final int startG = g;
        final int startB = b;
        final float resLevel = preferredMode == TextRenderType.MODE_SDF_FILL
                ? TextLayoutEngine.getResLevelForSDF(mCreatedResLevel)
                : mCreatedResLevel;

        final var glyphs = getGlyphs((int) resLevel);
        final var positions = mPositions;
        final var flags = mGlyphFlags;
        //final boolean alignPixels = TextLayoutProcessor.sAlignPixels;

        y += sBaselineOffset;

        int prevTexture = -1;
        VertexConsumer builder = null;

        int standardTexture = TextLayoutEngine.getInstance().getGlyphManager()
                .getCurrentTexture(Engine.MASK_FORMAT_A8);

        boolean seeThrough = preferredMode == TextRenderType.MODE_SEE_THROUGH;
        for (int i = 0, e = glyphs.length; i < e; i++) {
            var glyph = glyphs[i];
            if (glyph == null) {
                continue;
            }
            final int bits = flags[i];
            float rx = 0;
            final float ry;
            final float w;
            final float h;
            final int effMode;
            final int texture;
            if ((bits & CharacterStyle.ANY_BITMAP_REPLACEMENT) != 0) {
                if (isShadow) {
                    continue;
                }
                float scaleFactor = 1f / TextLayoutEngine.BITMAP_SCALE;
                if ((bits & CharacterStyle.COLOR_EMOJI_REPLACEMENT) != 0) {
                    scaleFactor *= TextLayoutProcessor.sBaseFontSize / TextLayoutProcessor.DEFAULT_BASE_FONT_SIZE;
                }
                rx = x + positions[i << 1] + (float) glyph.x * scaleFactor;
                ry = y + positions[i << 1 | 1] + (float) glyph.y * scaleFactor;

                w = (float) glyph.width * scaleFactor;
                h = (float) glyph.height * scaleFactor;
                effMode = seeThrough ? preferredMode : TextRenderType.MODE_NORMAL;
                texture = TextLayoutEngine.getInstance().getCurrentTexture(getFont(i));
            } else {
                effMode = preferredMode;
                if ((bits & CharacterStyle.OBFUSCATED_MASK) != 0) {
                    var chars = (TextLayoutEngine.FastCharSet) glyph;
                    int fastIndex = RANDOM.nextInt(chars.glyphs.length);
                    glyph = chars.glyphs[fastIndex];
                    // 0 is standard, no additional offset
                    if (fastIndex != 0) {
                        rx += chars.offsets[fastIndex];
                    }
                }
                rx += x + positions[i << 1] + glyph.x / resLevel;
                ry = y + positions[i << 1 | 1] + glyph.y / resLevel;

                w = glyph.width / resLevel;
                h = glyph.height / resLevel;
                texture = standardTexture;
            }
            /*if (alignPixels) {
                rx = Math.round(rx * scale) / scale;
                ry = Math.round(ry * scale) / scale;
            }*/
            if ((bits & CharacterStyle.IMPLICIT_COLOR_MASK) != 0) {
                r = startR;
                g = startG;
                b = startB;
            } else {
                r = bits >> 16 & 0xff;
                g = bits >> 8 & 0xff;
                b = bits & 0xff;
                if (isShadow) {
                    r >>= 2;
                    g >>= 2;
                    b >>= 2;
                }
            }
            if (builder == null || prevTexture != texture) {
                // bitmap texture and grayscale texture are different
                prevTexture = texture;
                builder = source.getBuffer(TextRenderType.getOrCreate(prevTexture, effMode));
            }
            builder.vertex(matrix, rx, ry, 0.0025f)
                    .color(r, g, b, a)
                    .uv(glyph.u1, glyph.v1)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx, ry + h, 0.0025f)
                    .color(r, g, b, a)
                    .uv(glyph.u1, glyph.v2)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx + w, ry + h, 0.0025f)
                    .color(r, g, b, a)
                    .uv(glyph.u2, glyph.v2)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx + w, ry, 0.0025f)
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

        if ((bgColor & 0xFF000000) != 0) {
            y -= sBaselineOffset;
            a = bgColor >>> 24;
            r = bgColor >> 16 & 0xff;
            g = bgColor >> 8 & 0xff;
            b = bgColor & 0xff;
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
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void drawTextOutline(@Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source,
                                float x, float y, int r, int g, int b, int a, int packedLight) {
        final float resLevel = TextLayoutEngine.getResLevelForSDF(mCreatedResLevel);

        final var glyphs = getGlyphs((int) resLevel);
        final var positions = mPositions;
        final var flags = mGlyphFlags;
        //final boolean alignPixels = TextLayoutProcessor.sAlignPixels;

        y += sBaselineOffset;
        VertexConsumer builder = source.getBuffer(
                TextRenderType.getOrCreate(
                        TextLayoutEngine.getInstance().getGlyphManager()
                                .getCurrentTexture(Engine.MASK_FORMAT_A8),
                        TextRenderType.MODE_SDF_STROKE
                )
        );

        final float sBloat = 1.0f / resLevel;
        for (int i = 0, e = glyphs.length; i < e; i++) {
            var glyph = glyphs[i];
            if (glyph == null) {
                continue;
            }
            final int bits = flags[i];
            float rx = 0;
            final float ry;
            final float w;
            final float h;
            if ((bits & CharacterStyle.ANY_BITMAP_REPLACEMENT) != 0) {
                continue;
            } else {
                if ((bits & CharacterStyle.OBFUSCATED_MASK) != 0) {
                    var chars = (TextLayoutEngine.FastCharSet) glyph;
                    int fastIndex = RANDOM.nextInt(chars.glyphs.length);
                    glyph = chars.glyphs[fastIndex];
                    // 0 is standard, no additional offset
                    if (fastIndex != 0) {
                        rx += chars.offsets[fastIndex];
                    }
                }
                rx += x + positions[i << 1] + glyph.x / resLevel;
                ry = y + positions[i << 1 | 1] + glyph.y / resLevel;

                w = glyph.width / resLevel;
                h = glyph.height / resLevel;
            }
            /*if (alignPixels) {
                rx = Math.round(rx * scale) / scale;
                ry = Math.round(ry * scale) / scale;
            }*/
            float uBloat = (glyph.u2 - glyph.u1) / glyph.width;
            float vBloat = (glyph.v2 - glyph.v1) / glyph.height;
            builder.vertex(matrix, rx - sBloat, ry - sBloat, 0.002f)
                    .color(r, g, b, a)
                    .uv(glyph.u1 - uBloat, glyph.v1 - vBloat)
                    .uv2(packedLight)
                    .endVertex();
            builder.vertex(matrix, rx - sBloat, ry + h + sBloat, 0.002f)
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
    public int[] getGlyphs() {
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
    public float[] getAdvances() {
        return mAdvances;
    }

    /**
     * Returns which font should be used for the i-th glyph.
     *
     * @param i the index
     * @return the font
     */
    public Font getFont(int i) {
        if (mFontIndices != null) {
            return mFonts[mFontIndices[i]];
        }
        return mFonts[0];
    }

    /**
     * Returns the number of chars (i.e. the length of char array) of the full stripped
     * string (without formatting codes).
     *
     * @return length of the text
     */
    public int getCharCount() {
        return mTextBuf.length;
    }

    /**
     * Glyph rendering flags. Same indexing with {@link #getGlyphs()}, in visual order.
     *
     * @see CharacterStyle
     */
    @Nonnull
    public int[] getGlyphFlags() {
        return mGlyphFlags;
    }

    /*
     * Glyphs to relative char indices of the strip string (without formatting codes). However,
     * for vanilla layout {@link VanillaLayoutKey} and {@link TextLayoutEngine#lookupVanillaLayout(String)},
     * these will be adjusted to string index (with formatting codes).
     * Same indexing with {@link #getGlyphs()}, in visual order.
     */
    /*@Nonnull
    public int[] getCharIndices() {
        return mCharIndices;
    }*/

    /**
     * Strip indices that are boundaries for Unicode line breaking, in logical order.
     * 0 is not included. Last value is always the text length (without formatting codes).
     */
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
     * Precomputed value that indicates whether flags array contains any color emoji replacement flag.
     */
    public boolean hasColorEmoji() {
        return mHasColorEmoji;
    }

    /**
     * @return measurable memory size in bytes of this object
     */
    public int getMemorySize() {
        int m = 0;
        m += 16 + MathUtil.align8(mTextBuf.length << 1);
        m += 16 + MathUtil.align8(mGlyphs.length << 2); // glyphs
        m += 16 + MathUtil.align8(mPositions.length << 2); // positions
        if (mFontIndices != null) {
            m += 16 + MathUtil.align8(mFontIndices.length);
        }
        m += 16 + MathUtil.align8(mFonts.length << 2);
        if (mAdvances != null) {
            m += 16 + MathUtil.align8(mAdvances.length << 2);
        }
        m += 16 + MathUtil.align8(mGlyphFlags.length << 2); // flags
        if (mLineBoundaries != null) {
            m += 16 + MathUtil.align8(mLineBoundaries.length << 2);
        }
        if (mBakedGlyphs != null) {
            m += 16 + MathUtil.align8(mBakedGlyphs.length << 2);
        }
        if (mBakedGlyphsForSDF != null) {
            m += 16 + MathUtil.align8(mBakedGlyphsForSDF.length << 2);
        }
        return m + 24;
    }

    @Override
    public String toString() {
        return "TextLayoutNode{" +
                "text=" + toEscapeChars(mTextBuf) +
                ",glyphs=" + mGlyphs.length +
                ",length=" + mTextBuf.length +
                ",positions=" + toPositionString(mPositions) +
                ",advances=" + Arrays.toString(mAdvances) +
                ",charFlags=" + toFlagString(mGlyphFlags) +
                ",lineBoundaries" + Arrays.toString(mLineBoundaries) +
                ",totalAdvance=" + mTotalAdvance +
                ",hasEffect=" + mHasEffect +
                ",hasColorEmoji=" + mHasColorEmoji +
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
