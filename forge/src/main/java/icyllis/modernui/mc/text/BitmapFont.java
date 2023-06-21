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

import com.google.gson.*;
import icyllis.arc3d.opengl.GLTextureCompat;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.font.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

import static icyllis.arc3d.opengl.GLCore.*;

/**
 * Behaves like FontFamily, but directly provides a bitmap (which maybe colored) to replace
 * Unicode code points without text shaping. If such a font family wins the font itemization,
 * the layout engine will create a ReplacementRun, just like color emojis.
 * <p>
 * The bitmap is just a single texture atlas.
 *
 * @author BloCamLimb
 * @see net.minecraft.client.gui.font.providers.BitmapProvider
 * @since 3.6
 */
public class BitmapFont extends FontFamily implements AutoCloseable {

    private final ResourceLocation mName;

    private Bitmap mBitmap; // null after uploading to texture
    private final Int2ObjectMap<Glyph> mGlyphs = new Int2ObjectOpenHashMap<>();

    private final GLTextureCompat mTexture = new GLTextureCompat(GL_TEXTURE_2D);

    private final int mAscent;  // positive
    private final int mDescent; // positive

    private final int mSpriteWidth;
    private final int mSpriteHeight;
    private final float mScaleFactor;

    private BitmapFont(ResourceLocation name, Bitmap bitmap,
                       int[][] grid, int rows, int cols,
                       int height, int ascent) {
        super(null);
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
                glyph.y = -mAscent * TextLayoutEngine.BITMAP_SCALE;
                glyph.width = Math.round(mSpriteWidth * mScaleFactor * TextLayoutEngine.BITMAP_SCALE);
                glyph.height = Math.round(mSpriteHeight * mScaleFactor * TextLayoutEngine.BITMAP_SCALE);
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
            // Minecraft doesn't use texture views, read swizzles may not work, so we always use RGBA (colored)
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
        mTexture.allocate2D(GL_RGBA8, mBitmap.getWidth(), mBitmap.getHeight(), 0);
        try {
            long pixels = mBitmap.getPixels();
            mTexture.upload(0, 0, 0,
                    mBitmap.getWidth(), mBitmap.getHeight(),
                    0, 0, 0, 1,
                    GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            mTexture.setFilter(GL_NEAREST, GL_NEAREST);
            for (Glyph glyph : mGlyphs.values()) {
                glyph.texture = mTexture.get();
            }
        } finally {
            mBitmap.close();
            mBitmap = null;
        }
    }

    public void dumpAtlas(String path) {
        if (path != null && mBitmap == null && Core.isOnRenderThread()) {
            ModernUI.LOGGER.info(GlyphManager.MARKER, "Glyphs: {}", mGlyphs.size());
            try (var bitmap = Bitmap.download(
                    Bitmap.Format.RGBA_8888,
                    mTexture)) {
                bitmap.saveToPath(Bitmap.SaveFormat.PNG, 0, Path.of(path));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Render thread only
    @Nullable
    public Glyph getGlyph(int ch) {
        if (mBitmap != null) {
            createTextureLazy();
        }
        assert mBitmap == null;
        return mGlyphs.get(ch);
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
    public boolean hasGlyph(int ch) {
        return mGlyphs.containsKey(ch);
    }

    @Override
    public String getFamilyName() {
        // the bitmap name
        return mName.toString();
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
        mTexture.close();
    }

    public static class Glyph extends GLBakedGlyph {

        public final float advance;

        public Glyph(int advance) {
            this.advance = advance;
        }
    }
}
