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

import com.google.gson.JsonParseException;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import icyllis.arc3d.core.Strike;
import icyllis.arc3d.engine.GPUResource;
import icyllis.arc3d.engine.Surface;
import icyllis.arc3d.opengl.*;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.font.*;
import icyllis.modernui.graphics.text.*;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import static icyllis.arc3d.opengl.GLCore.*;

/**
 * Directly provides a bitmap (mask format may be RGBA or grayscale) to replace
 * Unicode code points without text shaping. If such a font wins the font itemization,
 * the layout engine will create a ReplacementRun, just like color emojis.
 * <p>
 * The bitmap is just a single texture atlas.
 *
 * @author BloCamLimb
 * @see net.minecraft.client.gui.font.providers.BitmapProvider
 * @since 3.6
 */
public class BitmapFont implements Font, AutoCloseable {

    private final ResourceLocation mName;

    // this is auto GC, null after uploading to texture
    private Bitmap mBitmap;
    private final Int2ObjectMap<Glyph> mGlyphs = new Int2ObjectOpenHashMap<>();

    private GLTexture mTexture;

    private final int mAscent;  // positive
    private final int mDescent; // positive

    private final int mSpriteWidth;
    private final int mSpriteHeight;
    private final float mScaleFactor;

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

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int ch = grid[r][c];
                if (ch == '\u0000') {
                    continue; // padding
                }
                int actualWidth = getActualGlyphWidth(bitmap, mSpriteWidth, mSpriteHeight, c, r);
                Glyph glyph = new Glyph(Math.round(actualWidth * mScaleFactor) + 1);
                glyph.x = 0;
                glyph.y = (short) (-mAscent * TextLayoutEngine.BITMAP_SCALE);
                glyph.width = (short) Math.round(mSpriteWidth * mScaleFactor * TextLayoutEngine.BITMAP_SCALE);
                glyph.height = (short) Math.round(mSpriteHeight * mScaleFactor * TextLayoutEngine.BITMAP_SCALE);
                glyph.u1 = (float) (c * mSpriteWidth) / bitmap.getWidth();
                glyph.v1 = (float) (r * mSpriteHeight) / bitmap.getHeight();
                glyph.u2 = (float) (c * mSpriteWidth + mSpriteWidth) / bitmap.getWidth();
                glyph.v2 = (float) (r * mSpriteHeight + mSpriteHeight) / bitmap.getHeight();
                if (mGlyphs.put(ch, glyph) != null) {
                    ModernUI.LOGGER.warn("Codepoint '{}' declared multiple times in {}",
                            Integer.toHexString(ch), mName);
                }
            }
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
    private void createTextureLazy() {
        try {
            mTexture = (GLTexture) Core
                    .requireDirectContext()
                    .getResourceProvider()
                    .createTexture(
                            mBitmap.getWidth(),
                            mBitmap.getHeight(),
                            GLBackendFormat.make(GL_RGBA8),
                            1,
                            Surface.FLAG_BUDGETED,
                            mBitmap.getColorType(),
                            mBitmap.getColorType(),
                            mBitmap.getRowBytes(),
                            mBitmap.getAddress(),
                            mName.toString()
                    );
            Objects.requireNonNull(mTexture, "Failed to create font texture");

            int boundTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
            glBindTexture(GL_TEXTURE_2D, mTexture.getHandle());

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

            glBindTexture(GL_TEXTURE_2D, boundTexture);
        } finally {
            mBitmap.close();
            mBitmap = null;
        }
    }

    public void dumpAtlas(String path) {
        ModernUI.LOGGER.info(GlyphManager.MARKER,
                "BitmapFont: {}, glyphs: {}, texture: {}",
                mName, mGlyphs.size(), mTexture);
        if (path != null && mTexture != null && Core.isOnRenderThread()) {
            GLFontAtlas.dumpAtlas(
                    (GLCaps) Core.requireDirectContext().getCaps(),
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

    // Render thread only
    @Nullable
    public Glyph getGlyph(int ch) {
        Glyph glyph = mGlyphs.get(ch);
        if (glyph != null && mBitmap != null) {
            createTextureLazy();
            assert mBitmap == null;
        }
        return glyph;
    }

    @Nullable
    public Glyph getGlyphInfo(int ch) {
        return mGlyphs.get(ch);
    }

    public int getCurrentTexture() {
        return mTexture != null ? mTexture.getHandle() : 0;
    }

    public int getAscent() {
        return mAscent;
    }

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

            Glyph glyph = getGlyphInfo(ch);
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
                        scaleUp * 0.5f); // 1px spacing, center it
                positions.add(y);
            }

            advance += adv;
        }

        return advance;
    }

    @Override
    public Strike findOrCreateStrike(FontPaint paint) {
        // no support except for Minecraft
        return null;
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + mName.hashCode();
        result = 31 * result + mAscent;
        result = 31 * result + mDescent;
        result = 31 * result + mSpriteWidth;
        result = 31 * result + mSpriteHeight;
        result = 31 * result + Float.hashCode(mScaleFactor);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitmapFont that = (BitmapFont) o;
        if (mAscent != that.mAscent) return false;
        if (mDescent != that.mDescent) return false;
        if (mSpriteWidth != that.mSpriteWidth) return false;
        if (mSpriteHeight != that.mSpriteHeight) return false;
        if (mScaleFactor != that.mScaleFactor) return false;
        return mName.equals(that.mName);
    }

    @Override
    public void close() {
        if (mBitmap != null) {
            mBitmap.close();
            mBitmap = null;
        }
        mTexture = GPUResource.move(mTexture);
    }

    public static class Glyph extends BakedGlyph implements GlyphInfo {

        public final float advance;

        public Glyph(int advance) {
            this.advance = advance;
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
