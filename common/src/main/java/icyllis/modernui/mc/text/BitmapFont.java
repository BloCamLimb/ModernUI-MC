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

import com.google.gson.JsonParseException;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.*;
import icyllis.arc3d.opengl.*;
import icyllis.arc3d.sketch.Typeface;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.text.Font;
import icyllis.modernui.graphics.text.*;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Unmodifiable;
import org.lwjgl.opengl.GL33C;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;

import static icyllis.modernui.mc.ModernUIMod.LOGGER;

/**
 * Directly provides a bitmap (mask format may be RGBA or grayscale) to replace
 * Unicode code points without text shaping. If such a font wins the font itemization,
 * the layout engine will create a ReplacementRun, just like color emojis.
 * <p>
 * Thread safety: this class is not thread safe, it must be safely published. Bitmap font
 * can be created from any thread; the glyph info can be queried from any thread; it can
 * only be rendered and closed on Minecraft main thread (i.e. OpenGL thread).
 *
 * @author BloCamLimb
 * @see net.minecraft.client.gui.font.providers.BitmapProvider
 * @since 3.6
 */
public class BitmapFont implements Font, AutoCloseable {

    /**
     * Minecraft allows 256x256 bitmap font texture at most. However, we only want to
     * stitch small glyphs into the texture atlas, as we only have one 4096x4096 atlas.
     * Otherwise, we will create a dedicated texture for the bitmap font.
     * <p>
     * This value must be less than {@link GlyphManager#IMAGE_SIZE}.
     */
    public static final int MAX_ATLAS_DIMENSION = 128;
    /**
     * @see net.minecraft.client.gui.font.FontTexture#SIZE
     */
    @SuppressWarnings("JavadocReference")
    public static final int FONT_TEXTURE_SIZE = 256;

    public static float sBitmapOffset = 0.5f;

    private final ResourceLocation mName;

    private Bitmap mBitmap;
    private final Int2ObjectOpenHashMap<Glyph> mGlyphs = new Int2ObjectOpenHashMap<>();

    // used if mSpriteWidth or mSpriteHeight > MAX_ATLAS_DIMENSION
    @SharedPtr
    private GLTexture mTexture; // lazy init
    private Int2ObjectOpenHashMap<GLBakedGlyph> mBakedGlyphs;

    private final int mAscent;  // positive
    private final int mDescent; // positive

    private final int mSpriteWidth;
    private final int mSpriteHeight;
    private final float mScaleFactor;
    private final int[][] mCodepointGrid;

    private BitmapFont(ResourceLocation name, Bitmap bitmap,
                       int[][] grid, int rows, int cols,
                       int height, int ascent) {
        mName = name;
        mBitmap = bitmap;
        mAscent = ascent;
        mDescent = height - ascent;
        mSpriteWidth = bitmap.getWidth() / cols;
        mSpriteHeight = bitmap.getHeight() / rows;
        mScaleFactor = (float) height / mSpriteHeight;
        mCodepointGrid = grid;

        // height <= 0 means nothing to render
        boolean isEmpty = height <= 0 || mSpriteWidth <= 0 || mSpriteHeight <= 0 ||
                mSpriteWidth > FONT_TEXTURE_SIZE || mSpriteHeight > FONT_TEXTURE_SIZE ||
                bitmap.getWidth() > Short.MAX_VALUE || bitmap.getHeight() > Short.MAX_VALUE;
        boolean useDedicatedTexture = !isEmpty &&
                (mSpriteWidth > MAX_ATLAS_DIMENSION || mSpriteHeight > MAX_ATLAS_DIMENSION);
        if (useDedicatedTexture) {
            mBakedGlyphs = new Int2ObjectOpenHashMap<>();
        }

        int numEmptyGlyphs = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int ch = grid[r][c];
                if (ch == '\u0000') {
                    numEmptyGlyphs++;
                    continue; // padding
                }
                int actualWidth = getActualGlyphWidth(bitmap, mSpriteWidth, mSpriteHeight, c, r);
                // (width == 0) means the glyph is fully transparent
                if (actualWidth <= 0) {
                    numEmptyGlyphs++;
                }
                // Note: this must be (int) (0.5 + value) to match vanilla behavior,
                // do not use Math.round() as results are different for negative values
                Glyph glyph = new Glyph((int) (0.5 + actualWidth * mScaleFactor) + 1,
                        c * mSpriteWidth, r * mSpriteHeight,
                        actualWidth <= 0);
                if (mGlyphs.put(ch, glyph) != null) {
                    LOGGER.warn(GlyphManager.MARKER, "Codepoint '{}' declared multiple times in {}",
                            Integer.toHexString(ch), mName);
                }
                if (useDedicatedTexture) {
                    GLBakedGlyph bakedGlyph = new GLBakedGlyph();
                    setGlyphMetrics(bakedGlyph);
                    // always create 256x256 texture
                    bakedGlyph.u1 = (float) (c * mSpriteWidth) / getTextureWidth();
                    bakedGlyph.v1 = (float) (r * mSpriteHeight) / getTextureHeight();
                    bakedGlyph.u2 = (float) (c * mSpriteWidth + mSpriteWidth) / getTextureWidth();
                    bakedGlyph.v2 = (float) (r * mSpriteHeight + mSpriteHeight) / getTextureHeight();
                    mBakedGlyphs.put(ch, bakedGlyph);
                }
            }
        }

        if (isEmpty || numEmptyGlyphs == (cols * rows)) {
            // nothing to render, free the bitmap and empty the atlas holder
            mBitmap.close();
            mBitmap = null;
            mBakedGlyphs = null;
        }
    }

    @Nonnull
    public static BitmapFont create(BitmapProvider.Definition definition, ResourceManager manager) {
        int height = definition.height();
        int ascent = definition.ascent();
        if (ascent > height) {
            throw new JsonParseException("Ascent " + ascent + " higher than height " + height);
        }
        int[][] grid = definition.codepointGrid();
        if (grid.length == 0 || grid[0].length == 0) {
            throw new JsonParseException("Expected to find data in chars, found none.");
        }
        int rows = grid.length;
        int cols = grid[0].length;
        var file = definition.file();
        var location = file.withPrefix("textures/");
        try (InputStream stream = manager.open(location)) {
            //XXX: Minecraft doesn't use texture views, read swizzles may not work,
            // so we always use RGBA (colored)
            var opts = new BitmapFactory.Options();
            opts.inPreferredFormat = Bitmap.Format.RGBA_8888;
            Bitmap bitmap = BitmapFactory.decodeStream(stream, opts);
            Objects.requireNonNull(bitmap);
            return new BitmapFont(file, bitmap, grid, rows, cols, height, ascent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int getActualGlyphWidth(Bitmap bitmap, int width, int height, int col, int row) {
        int i;
        for (i = width - 1; i >= 0; i--) {
            int x = col * width + i;
            for (int j = 0; j < height; j++) {
                int y = row * height + j;
                if (bitmap.getPixelARGB(x, y) >>> 24 == 0) {
                    continue;
                }
                return i + 1;
            }
        }
        return i + 1;
    }

    // create texture from bitmap on render thread
    private void createTexture() {
        assert mBitmap != null;
        ImmediateContext context = Core.requireImmediateContext();
        ImageDesc desc = context.getCaps().getDefaultColorImageDesc(
                Engine.ImageType.k2D,
                mBitmap.getColorType(),
                getTextureWidth(),
                getTextureHeight(),
                1,
                ISurface.FLAG_SAMPLED_IMAGE
        );
        Objects.requireNonNull(desc);
        mTexture = (GLTexture) context
                .getResourceProvider()
                .findOrCreateImage(
                        desc,
                        true,
                        mName.toString()
                );
        if (mTexture == null) {
            LOGGER.error(GlyphManager.MARKER, "Failed to create font texture for {}", mName);
            return;
        }
        boolean res = ((GLDevice) context.getDevice()).writePixels(
                mTexture, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(),
                mBitmap.getColorType(), mBitmap.getColorType(),
                mBitmap.getRowBytes(), mBitmap.getAddress()
        );
        assert res;

        int boundTexture = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, mTexture.getHandle());

        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);

        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, boundTexture);
    }

    public void dumpAtlas(int index, String path) {
        LOGGER.info(GlyphManager.MARKER, "BitmapFont {}: {}, ascent: {}, descent: {}, numGlyphs: {}, " +
                        "nothingToDraw: {}, fitsInAtlas: {}, dedicatedTexture: {}",
                index, mName, getAscent(), getDescent(), mGlyphs.size(),
                nothingToDraw(), fitsInAtlas(), mTexture);
        if (path != null && mTexture != null && Core.isOnRenderThread()) {
            GLFontAtlas.dumpAtlas(
                    (GLCaps) Core.requireImmediateContext().getCaps(),
                    mTexture,
                    Bitmap.Format.RGBA_8888,
                    path);
        }
    }

    @Override
    public int getStyle() {
        return FontPaint.NORMAL;
    }

    @Override
    public String getFullName(@Nonnull Locale locale) {
        return mName.toString();
    }

    @Override
    public String getFamilyName(@Nonnull Locale locale) {
        return mName.toString();
    }

    @Override
    public int getMetrics(@Nonnull FontPaint paint, FontMetricsInt fm) {
        // unused in Minecraft
        return 0;
    }

    @Nullable
    public Glyph getGlyph(int ch) {
        return mGlyphs.get(ch);
    }

    /**
     * True means all glyphs have nothing to render (texture is fully transparent or
     * has negative metrics).
     */
    public boolean nothingToDraw() {
        return mBitmap == null;
    }

    /**
     * True to use a texture atlas that is managed by {@link GlyphManager},
     * false to use a dedicated texture that is managed by this instance.
     */
    public boolean fitsInAtlas() {
        return mBakedGlyphs == null;
    }

    private int getTextureWidth() {
        assert mBitmap != null;
        // create 256x256 texture at least
        return Math.max(mBitmap.getWidth(), FONT_TEXTURE_SIZE);
    }

    private int getTextureHeight() {
        assert mBitmap != null;
        // create 256x256 texture at least
        return Math.max(mBitmap.getHeight(), FONT_TEXTURE_SIZE);
    }

    @SuppressWarnings("ConstantValue")
    public void setGlyphMetrics(@Nonnull GLBakedGlyph glyph) {
        // bearing x, bearing y
        glyph.x = 0;
        // there shouldn't be any overflow, because vanilla uses float,
        // integers between −16777216 and 16777216 can be exactly represented
        assert (16777216 * TextLayoutEngine.BITMAP_SCALE) <= Integer.MAX_VALUE;
        glyph.y = -mAscent * TextLayoutEngine.BITMAP_SCALE;
        glyph.width = (short) MathUtil.clamp(
                Math.round(mSpriteWidth * mScaleFactor * TextLayoutEngine.BITMAP_SCALE),
                0, Short.MAX_VALUE);
        glyph.height = (short) MathUtil.clamp(
                Math.round(mSpriteHeight * mScaleFactor * TextLayoutEngine.BITMAP_SCALE),
                0, Short.MAX_VALUE);
    }

    public boolean getGlyphImage(int ch, long dst) {
        assert mBitmap != null;
        Glyph src = getGlyph(ch);
        if (src == null || src.isEmpty) {
            return false;
        }
        int dstRowBytes = mSpriteWidth * mBitmap.getFormat().getBytesPerPixel();
        PixelUtils.copyImage(
                mBitmap.getPixmap().getAddress(src.offsetX, src.offsetY),
                mBitmap.getRowBytes(),
                dst,
                dstRowBytes,
                dstRowBytes,
                mSpriteHeight
        );
        return true;
    }

    /**
     * Called by {@link GlyphManager} to fetch the dedicated texture info.
     */
    // Render thread only
    @Nullable
    public GLBakedGlyph getBakedGlyph(int ch) {
        assert mBitmap != null && mBakedGlyphs != null;
        GLBakedGlyph glyph = mBakedGlyphs.get(ch);
        if (glyph != null && mTexture == null) {
            createTexture();
        }
        return glyph;
    }

    /**
     * Called by {@link GlyphManager} to fetch the dedicated texture info.
     */
    // Render thread only
    public int getCurrentTexture() {
        return mTexture != null ? mTexture.getHandle() : 0;
    }

    // positive
    public int getAscent() {
        return mAscent;
    }

    // positive
    public int getDescent() {
        return mDescent;
    }

    public int getSpriteWidth() {
        return mSpriteWidth;
    }

    public int getSpriteHeight() {
        return mSpriteHeight;
    }

    public float getScaleFactor() {
        return mScaleFactor;
    }

    @Unmodifiable
    public int[][] getCodepointGrid() {
        return mCodepointGrid;
    }

    @Override
    public boolean hasGlyph(int ch, int vs) {
        return mGlyphs.containsKey(ch);
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

            Glyph glyph = getGlyph(ch);
            if (glyph == null) {
                continue;
            }

            float adv = glyph.advance * scaleUp;
            if (advances != null) {
                advances[i - advanceOffset] = adv;
            }

            if (glyphs != null) {
                glyphs.add(ch);
            }
            if (positions != null) {
                positions.add(x + advance +
                        scaleUp * sBitmapOffset); // 1px spacing, center it
                positions.add(y);
            }

            advance += adv;
        }

        return advance;
    }

    @Override
    public Typeface getNativeTypeface() {
        // no support except for Minecraft
        return null;
    }

    @Override
    public int hashCode() {
        int result = mName.hashCode();
        result = 31 * result + mAscent;
        result = 31 * result + mDescent;
        result = 31 * result + Arrays.deepHashCode(mCodepointGrid);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitmapFont that = (BitmapFont) o;
        if (mAscent != that.mAscent) return false;
        if (mDescent != that.mDescent) return false;
        if (!mName.equals(that.mName)) return false;
        return Arrays.deepEquals(mCodepointGrid, that.mCodepointGrid);
    }

    @Override
    public void close() {
        if (mBitmap != null) {
            mBitmap.close();
            mBitmap = null;
        }
        mTexture = RefCnt.move(mTexture);
    }

    public static class Glyph implements GlyphInfo {

        public final float advance;
        /**
         * Pixel location in bitmap.
         */
        public final short offsetX;
        public final short offsetY;
        /**
         * True if the glyph is fully transparent.
         */
        public final boolean isEmpty;

        public Glyph(int advance, int offsetX, int offsetY, boolean isEmpty) {
            this.advance = advance;
            this.offsetX = (short) offsetX;
            this.offsetY = (short) offsetY;
            this.isEmpty = isEmpty;
        }

        @Override
        public float getAdvance() {
            return advance;
        }

        @Nonnull
        @Override
        public net.minecraft.client.gui.font.glyphs.BakedGlyph bake(
                @Nonnull Function<SheetGlyphInfo, net.minecraft.client.gui.font.glyphs.BakedGlyph> function) {
            return EmptyGlyph.INSTANCE;
        }
    }
}
