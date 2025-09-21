/*
 * Modern UI.
 * Copyright (C) 2021-2025 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.core.RectanglePacker;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ISurface;
import icyllis.arc3d.engine.ImmediateContext;
import icyllis.arc3d.opengl.GLCaps;
import icyllis.arc3d.opengl.GLDevice;
import icyllis.arc3d.opengl.GLTexture;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.b3d.GlTexture_Wrapped;
import icyllis.modernui.text.TextUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.lwjgl.opengl.GL45C;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static icyllis.modernui.mc.ModernUIMod.LOGGER;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Maintains a font texture atlas, which is specified with a font strike (style and
 * size). Glyphs are dynamically generated with mipmaps, each glyph is represented as
 * a {@link GLBakedGlyph}.
 * <p>
 * The initial texture size is 1024*1024, and each resize double the height and width
 * alternately. For example, 1024*1024 -> 1024*2048 -> 2048*2048.
 * Each 512*512 area becomes a chunk, and has its {@link RectanglePacker}.
 * The OpenGL texture ID will change due to expanding the texture size.
 * <p>
 * For {@link Engine#MASK_FORMAT_ARGB}, we have non-premultiplied alpha.
 *
 * @see GlyphManager
 * @see GLBakedGlyph
 * @see icyllis.arc3d.granite.DrawAtlas
 */
//TODO handle too many glyphs?
@RenderThread
public class GLFontAtlas implements AutoCloseable {

    // max texture size is 1024 at least
    // we compact texture at 1/4 max area
    public static final int CHUNK_SIZE = 512;
    /*
     * Max mipmap level.
     */
    //public static final int MIPMAP_LEVEL = 4;

    // OpenHashMap uses less memory than RBTree/AVLTree, but higher than ArrayMap
    private final Long2ObjectOpenHashMap<GLBakedGlyph> mGlyphs = new Long2ObjectOpenHashMap<>();

    // texture can change by resizing
    @RawPtr
    GLTexture mTexture = null; // managed by wrapper
    GlTexture_Wrapped mTextureWrapper = null;
    GpuTextureView mTextureWrapperView = null;

    boolean mResizeRequested = false;

    private final List<Chunk> mChunks = new ArrayList<>();

    // current texture size
    private int mWidth = 0;
    private int mHeight = 0;

    private final Rect2i mRect = new Rect2i();

    private record Chunk(int x, int y, RectanglePacker packer) {
    }

    private final ImmediateContext mContext;
    private final int mMaskFormat;
    private final int mBorderWidth;
    private final int mMaxTextureSize;

    // we prefer sComputeDeviceFontSize and sAllowSDFTextIn2D (i.e. direct mask)
    // then linear sampling on the font atlas is not necessary,
    // unless either of them is disabled or Shaders (in 3D) are used
    public static volatile boolean sLinearSamplingA8Atlas = false;

    /**
     * Linear sampling with mipmaps;
     */
    private final boolean mLinearSampling;

    // overflow and wrap
    private int mLastCompactChunkIndex;

    @RenderThread
    public GLFontAtlas(ImmediateContext context, int maskFormat, int borderWidth,
                       boolean linearSampling) {
        mContext = context;
        mMaskFormat = maskFormat;
        mBorderWidth = borderWidth;
        // 64MB at most
        mMaxTextureSize = Math.min(
                mContext.getMaxTextureSize(),
                maskFormat == Engine.MASK_FORMAT_A8
                        ? 8192
                        : 4096
        );
        mLinearSampling = linearSampling;
        assert mMaxTextureSize >= 1024;
        assert mBorderWidth >= 0 && mBorderWidth <= 2;
    }

    /**
     * When the key is absent, this method computes a new instance and returns it.
     * When the key is present but was called {@link #setNoPixels(long)} with it,
     * then this method returns null, which means there's nothing to render.
     *
     * @param key a key
     * @return the baked glyph or null if no pixels
     */
    @Nullable
    public GLBakedGlyph getGlyph(long key) {
        // static factory
        return mGlyphs.computeIfAbsent(key, __ -> new GLBakedGlyph());
    }

    public void setNoPixels(long key) {
        mGlyphs.put(key, null);
    }

    public boolean stitch(@NonNull GLBakedGlyph glyph, long pixels) {
        if (mWidth == 0) {
            resize(); // first init
        }

        // the source image includes border, but glyph.width/height does not include
        var rect = mRect;
        rect.set(0, 0,
                glyph.width + mBorderWidth * 2, glyph.height + mBorderWidth * 2);
        boolean inserted = false;
        for (Chunk chunk : mChunks) {
            if (chunk.packer.addRect(rect)) {
                inserted = true;
                rect.offset(chunk.x, chunk.y);
                break;
            }
        }
        if (!inserted) {
            mResizeRequested = true;
            return false;
        }

        // include border
        int colorType = mMaskFormat == Engine.MASK_FORMAT_ARGB
                ? ColorInfo.CT_RGBA_8888
                : ColorInfo.CT_ALPHA_8;
        int rowBytes = rect.width() * ColorInfo.bytesPerPixel(colorType);
        boolean res = ((GLDevice) mContext.getDevice()).writePixels(
                mTexture,
                rect.x(), rect.y(),
                rect.width(), rect.height(),
                colorType,
                colorType,
                rowBytes,
                pixels
        );
        if (!res) {
            LOGGER.warn(GlyphManager.MARKER, "Failed to write glyph pixels");
        }

        // exclude border
        glyph.u1 = (float) (rect.mLeft + mBorderWidth) / mWidth;
        glyph.v1 = (float) (rect.mTop + mBorderWidth) / mHeight;
        glyph.u2 = (float) (rect.mRight - mBorderWidth) / mWidth;
        glyph.v2 = (float) (rect.mBottom - mBorderWidth) / mHeight;

        return true;
    }

    boolean resize() {
        mResizeRequested = false;
        if (mTexture == null) {
            // initialize 4 or 16 chunks
            mWidth = mHeight = mMaskFormat == Engine.MASK_FORMAT_A8
                    ? CHUNK_SIZE * 4
                    : CHUNK_SIZE * 2;
            mTexture = createTexture();
            mTextureWrapper = new GlTexture_Wrapped(mTexture); // transfer ownership
            mTextureWrapperView = MuiModApi.get().getRealGpuDevice().createTextureView(mTextureWrapper);
            for (int x = 0; x < mWidth; x += CHUNK_SIZE) {
                for (int y = 0; y < mHeight; y += CHUNK_SIZE) {
                    mChunks.add(new Chunk(x, y, RectanglePacker.make(CHUNK_SIZE, CHUNK_SIZE)));
                }
            }
        } else {
            final int oldWidth = mWidth;
            final int oldHeight = mHeight;

            if (oldWidth == mMaxTextureSize && oldHeight == mMaxTextureSize) {
                LOGGER.warn(GlyphManager.MARKER, "Font atlas reached max texture size, " +
                        "mask format: {}, max size: {}, current texture: {}", mMaskFormat, mMaxTextureSize, mTexture);
                return false;
            }

            final boolean vertical;
            if (mHeight != mWidth) {
                mWidth <<= 1;
                for (int x = mWidth / 2; x < mWidth; x += CHUNK_SIZE) {
                    for (int y = 0; y < mHeight; y += CHUNK_SIZE) {
                        mChunks.add(new Chunk(x, y, RectanglePacker.make(CHUNK_SIZE, CHUNK_SIZE)));
                    }
                }
                vertical = false;
            } else {
                mHeight <<= 1;
                for (int x = 0; x < mWidth; x += CHUNK_SIZE) {
                    for (int y = mHeight / 2; y < mHeight; y += CHUNK_SIZE) {
                        mChunks.add(new Chunk(x, y, RectanglePacker.make(CHUNK_SIZE, CHUNK_SIZE)));
                    }
                }
                vertical = true;
            }

            // copy to new texture
            GLTexture newTexture = createTexture();
            boolean res = ((GLDevice) mContext.getDevice()).copyImage(
                    mTexture,
                    0, 0,
                    newTexture,
                    0, 0,
                    oldWidth, oldHeight
            );
            if (!res) {
                LOGGER.warn(GlyphManager.MARKER, "Failed to copy to new texture");
            }

            mTextureWrapperView.close();
            mTextureWrapper.close();
            mTexture = newTexture;
            mTextureWrapper = new GlTexture_Wrapped(mTexture); // transfer ownership
            mTextureWrapperView = MuiModApi.get().getRealGpuDevice().createTextureView(mTextureWrapper);

            if (vertical) {
                //mTexture.clear(0, 0, mHeight >> 1, mWidth, mHeight >> 1);
                for (GLBakedGlyph glyph : mGlyphs.values()) {
                    if (glyph == null) {
                        continue;
                    }
                    glyph.v1 *= 0.5f;
                    glyph.v2 *= 0.5f;
                }
            } else {
                //mTexture.clear(0, mWidth >> 1, 0, mWidth >> 1, mHeight);
                for (GLBakedGlyph glyph : mGlyphs.values()) {
                    if (glyph == null) {
                        continue;
                    }
                    glyph.u1 *= 0.5f;
                    glyph.u2 *= 0.5f;
                }
            }

            // we later generate mipmap
        }

        int boundTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindTexture(GL_TEXTURE_2D, mTexture.getHandle());

        // this is a fallback sampling method, generally used for direct mask, NEAREST is performant
        // when used for SDF, a sampler object will override this setting
        /*glTexParameteri(
                GL_TEXTURE_2D,
                GL_TEXTURE_MAG_FILTER,
                GL_NEAREST
        );
        glTexParameteri(
                GL_TEXTURE_2D,
                GL_TEXTURE_MIN_FILTER,
                mLinearSampling && (sLinearSamplingA8Atlas ||
                        mMaskFormat == Engine.MASK_FORMAT_ARGB)   // color emoji requires linear sampling
                        ? GL_LINEAR_MIPMAP_LINEAR
                        : GL_NEAREST
        );*/
        boolean linear = mLinearSampling && (sLinearSamplingA8Atlas ||
                mMaskFormat == Engine.MASK_FORMAT_ARGB);   // color emoji requires linear sampling
        mTextureWrapper.setTextureFilter(linear ? FilterMode.LINEAR : FilterMode.NEAREST, FilterMode.NEAREST, linear);

        if (mMaskFormat == Engine.MASK_FORMAT_A8) {
            //XXX: un-premultiplied, so 111r rather than rrrr
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_R, GL_ONE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_G, GL_ONE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_B, GL_ONE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_A, GL_RED);
        }

        glBindTexture(GL_TEXTURE_2D, boundTexture);

        return true;
    }

    private GLTexture createTexture() {
        var desc = mContext.getCaps().getDefaultColorImageDesc(
                Engine.ImageType.k2D,
                Engine.maskFormatToColorType(mMaskFormat),
                mWidth, mHeight,
                1,
                ISurface.FLAG_SAMPLED_IMAGE | (mLinearSampling ? ISurface.FLAG_MIPMAPPED : 0)
        );
        Objects.requireNonNull(desc, "No suitable image descriptor");
        return (GLTexture) Objects.requireNonNull(mContext
                .getResourceProvider()
                .findOrCreateImage(
                        desc,
                        /*budgeted*/ true,
                        "FontAtlas" + mMaskFormat
                ), "Failed to create font atlas");
    }

    public GLTexture getTexture() {
        return mTexture;
    }

    public int getMaskFormat() {
        return mMaskFormat;
    }

    public boolean compact() {
        if (mWidth < mMaxTextureSize &&
                mHeight < mMaxTextureSize) {
            // not reach 1/4 of max area
            return false;
        }
        assert mChunks.size() > 1;
        //TODO this implementation is not ideal and we need a review
        double coverage = 0;
        for (Chunk chunk : mChunks) {
            coverage += chunk.packer.getCoverage();
        }
        int chunksPerDim = mMaxTextureSize / CHUNK_SIZE;
        // clear 1/4 coverage of max
        double maxCoverage = chunksPerDim * chunksPerDim * 0.25f;
        if (coverage <= maxCoverage) {
            return false;
        }
        double coverageToClean = Math.max(coverage - maxCoverage, maxCoverage);
        boolean cleared = false;
        // clear 16 chunks at most
        for (int iChunk = 0;
             iChunk < Math.min(16, mChunks.size()) && coverageToClean > 0;
             iChunk++) {
            // let index overflow and wrap
            assert MathUtil.isPow2(mChunks.size());
            int index = (mLastCompactChunkIndex++) & (mChunks.size() - 1);
            Chunk chunk = mChunks.get(index);
            double cc = chunk.packer.getCoverage();
            if (cc == 0) {
                continue;
            }
            coverageToClean -= cc;
            chunk.packer.clear();
            float cu1 = (float) chunk.x / mWidth;
            float cv1 = (float) chunk.y / mHeight;
            float cu2 = cu1 + (float) CHUNK_SIZE / mWidth;
            float cv2 = cv1 + (float) CHUNK_SIZE / mHeight;
            for (var glyph : mGlyphs.values()) {
                if (glyph == null) continue;
                if (glyph.u1 >= cu1 && glyph.u2 < cu2 &&
                        glyph.v1 >= cv1 && glyph.v2 < cv2) {
                    // invalidate glyph image
                    glyph.x = Integer.MIN_VALUE;
                }
            }
            cleared = true;
        }
        return cleared;
    }

    public void debug(String name, @Nullable String path) {
        if (path == null) {
            LOGGER.info(GlyphManager.MARKER, name);
            for (var glyph : mGlyphs.long2ObjectEntrySet()) {
                LOGGER.info(GlyphManager.MARKER, "Key 0x{}: {}",
                        Long.toHexString(glyph.getLongKey()), glyph.getValue());
            }
        } else if (Core.isOnRenderThread()) {
            LOGGER.info(GlyphManager.MARKER, "{}, Glyphs: {}", name, mGlyphs.size());
            if (mTexture == null)
                return;
            dumpAtlas((GLCaps) mContext.getCaps(), mTexture,
                    mMaskFormat == Engine.MASK_FORMAT_ARGB
                            ? Bitmap.Format.RGBA_8888
                            : Bitmap.Format.GRAY_8,
                    path);
        }
    }

    @RenderThread
    public static void dumpAtlas(GLCaps caps, GLTexture texture, Bitmap.Format format, String path) {
        // debug only
        if (caps.hasDSASupport()) {
            final int width = texture.getWidth();
            final int height = texture.getHeight();
            @SuppressWarnings("resource") final Bitmap bitmap =
                    Bitmap.createBitmap(width, height, format);
            glPixelStorei(GL_PACK_ROW_LENGTH, 0);
            glPixelStorei(GL_PACK_SKIP_ROWS, 0);
            glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            int externalGlFormat = switch (format) {
                case GRAY_8 -> GL_RED;
                case GRAY_ALPHA_88 -> GL_RG;
                case RGB_888 -> GL_RGB;
                case RGBA_8888 -> GL_RGBA;
                default -> throw new IllegalArgumentException();
            };
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            GL45C.glGetTextureImage(texture.getHandle(), 0, externalGlFormat, GL_UNSIGNED_BYTE,
                    (int) bitmap.getSize(), bitmap.getAddress());
            CompletableFuture.runAsync(() -> {
                try (bitmap) {
                    bitmap.saveToPath(Bitmap.SaveFormat.PNG, 0, Path.of(path));
                } catch (IOException e) {
                    LOGGER.warn(GlyphManager.MARKER, "Failed to save font atlas", e);
                }
            });
        }
    }

    @Override
    public void close() {
        if (mTexture != null) {
            mTextureWrapperView.close();
            mTextureWrapper.close();
            mTexture = null;
            mTextureWrapper = null;
            mTextureWrapperView = null;
        }
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getGlyphCount() {
        return mGlyphs.size();
    }

    public long getMemorySize() {
        return mTexture != null ? mTexture.getMemorySize() : 0;
    }

    public void dumpInfo(PrintWriter pw, String name) {
        int validGlyphs = 0;
        int emptyGlyphs = 0;
        int evictedGlyphs = 0;
        for (var glyph : mGlyphs.values()) {
            if (glyph == null) {
                emptyGlyphs++;
            } else if (glyph.x == Integer.MIN_VALUE) {
                evictedGlyphs++;
            } else {
                validGlyphs++;
            }
        }
        pw.print(name);
        pw.printf(": NumGlyphs=%d (in-use: %d, empty: %d, evicted: %d)",
                getGlyphCount(), validGlyphs, emptyGlyphs, evictedGlyphs);
        pw.print(", Coverage=");
        pw.printf("%.4f", getCoverage());
        pw.print(", GPUMemorySize=");
        long memorySize = getMemorySize();
        TextUtils.binaryCompact(pw, memorySize);
        pw.print(" (");
        pw.print(memorySize);
        pw.println(" bytes)");
    }

    /**
     * @return 0..1
     */
    public double getCoverage() {
        if (mChunks.isEmpty()) {
            return 0;
        }
        double coverage = 0;
        for (Chunk chunk : mChunks) {
            coverage += chunk.packer.getCoverage();
        }
        return coverage / mChunks.size();
    }
}
