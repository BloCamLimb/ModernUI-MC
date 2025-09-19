/*
 * Modern UI.
 * Copyright (C) 2025 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import icyllis.arc3d.core.Rect2f;
import icyllis.modernui.mc.GradientRectangleRenderState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * When this object is created, all glyphs are added to the font atlas.
 */
// Only used for vanilla GUI rendering
public class ModernPreparedText implements Font.PreparedText {

    public static final ModernPreparedText EMPTY = new ModernPreparedText(
            1, 0, false, 0, 0,
            0, 0, null,
            new ArrayList<>(), false, 0,
            null, null, null
    );

    private final float density;
    private final float shadowOffset;
    private final boolean dropShadow;
    private final int color;
    private final int bgColor;
    private final float x;
    private final float top;
    private final ScreenRectangle bounds;
    private final ArrayList<TextRun> runs;
    private final boolean hasEffect;
    private final float totalAdvance;
    private final GLBakedGlyph[] glyphs;
    private final float[] positions;
    private final int[] flags;

    ModernPreparedText(float density, float shadowOffset, boolean dropShadow, int color,
                       int bgColor, float x, float top, ScreenRectangle bounds,
                       ArrayList<TextRun> runs, boolean hasEffect, float totalAdvance,
                       GLBakedGlyph[] glyphs, float[] positions, int[] flags) {
        this.density = density;
        this.shadowOffset = shadowOffset;
        this.dropShadow = dropShadow;
        this.color = color;
        this.bgColor = bgColor;
        this.x = x;
        this.top = top;
        this.bounds = bounds;
        this.runs = runs;
        this.hasEffect = hasEffect;
        this.totalAdvance = totalAdvance;
        this.glyphs = glyphs;
        this.positions = positions;
        this.flags = flags;
    }

    ModernPreparedText(float x, float top, int color, boolean dropShadow,
                       int preferredMode, int bgColor, float density,
                       GLBakedGlyph[] glyphs, TextLayout layout) {

        final float invDensity = 1.0f / density;
        float shadowOffset = 0;
        if (dropShadow) {
            shadowOffset = ModernTextRenderer.sShadowOffset;
            if (preferredMode == TextRenderType.MODE_NORMAL) {
                // align to screen pixel center in 2D
                shadowOffset = Math.round(shadowOffset * density) * invDensity;
            }
        }

        final var positions = layout.getPositions();
        final var flags = layout.getGlyphFlags();

        final float baseline = top + TextLayout.sBaselineOffset;

        GpuTextureView prevTexture = null;
        int prevMode = -1;
        RenderPipeline pipeline = null;

        GpuTextureView fontTexture = null;

        Rect2f bounds = Rect2f.makeInfiniteInverted();

        assert preferredMode == TextRenderType.MODE_NORMAL ||
                preferredMode == TextRenderType.MODE_SDF_FILL;
        if ((bgColor & 0xFF000000) != 0) {
            bounds.joinNoCheck(x - 1, top - 1,
                    x + layout.getTotalAdvance() + 1, top + 9);
        }

        ArrayList<TextRun> textRuns = new ArrayList<>();
        boolean glyphArrayIsCopied = false;

        for (int i = 0, e = glyphs.length; i < e; i++) {
            var glyph = glyphs[i];
            if (glyph == null) {
                continue;
            }
            final int bits = flags[i];
            float rx;
            float ry;
            final float w;
            final float h;
            final int mode;
            final GpuTextureView texture;
            boolean fakeItalic = false;
            int ascent = 0;
            boolean isBitmapFont = false;
            boolean isColorEmoji = false;
            if ((bits & CharacterStyle.OBFUSCATED_MASK) != 0) {
                var chars = (GlyphManager.FastCharSet) glyph;
                int fastIndex = TextLayout.RANDOM.nextInt(chars.glyphs.size());
                glyph = chars.glyphs.get(fastIndex);
                // Determine the random glyph to be drawn in next frame
                if (!glyphArrayIsCopied) {
                    glyphArrayIsCopied = true;
                    glyphs = glyphs.clone();
                }
                glyphs[i] = glyph;
            }
            if ((bits & CharacterStyle.ANY_BITMAP_REPLACEMENT) != 0) {
                final float scaleFactor;
                if (layout.getFont(i) instanceof BitmapFont bitmapFont) {
                    texture = GlyphManager.getInstance().getCurrentTexture(bitmapFont);
                    ascent = -glyph.y / TextLayoutEngine.BITMAP_SCALE;
                    scaleFactor = 1f / TextLayoutEngine.BITMAP_SCALE;
                    isBitmapFont = true;
                } else {
                    texture = GlyphManager.getInstance().getEmojiTexture();
                    ascent = TextLayout.STANDARD_BASELINE_OFFSET;
                    scaleFactor = TextLayoutProcessor.sBaseFontSize / GlyphManager.EMOJI_BASE;
                    isColorEmoji = true;
                }
                fakeItalic = (bits & CharacterStyle.ITALIC_MASK) != 0;
                rx = x + positions[i << 1] + glyph.x * scaleFactor;
                ry = baseline + positions[i << 1 | 1] + glyph.y * scaleFactor;

                w = glyph.width * scaleFactor;
                h = glyph.height * scaleFactor;
                mode = TextRenderType.MODE_NORMAL; // for color emoji
            } else {
                mode = preferredMode;
                rx = x + positions[i << 1] + glyph.x * invDensity;
                ry = baseline + positions[i << 1 | 1] + glyph.y * invDensity;

                w = glyph.width * invDensity;
                h = glyph.height * invDensity;
                if (fontTexture == null) {
                    fontTexture = GlyphManager.getInstance().getFontTexture();
                }
                texture = fontTexture;
            }
            if (pipeline == null || prevTexture != texture || prevMode != mode) {
                // no need to check isBitmapFont
                prevTexture = texture;
                prevMode = mode;
                pipeline = TextRenderType.getPipelineForGui(mode, isBitmapFont);
                if (!textRuns.isEmpty()) {
                    textRuns.getLast().glyphEnd = i;
                }
                textRuns.add(new TextRun(pipeline, texture, i, isColorEmoji,
                        preferredMode == TextRenderType.MODE_NORMAL));
            }
            float upSkew = 0;
            float downSkew = 0;
            if (fakeItalic) {
                upSkew = 0.25f * ascent;
                downSkew = 0.25f * (ascent - h);
            }
            bounds.joinNoCheck(
                    rx + downSkew, ry, rx + w + upSkew, ry + h
            );
        }
        if (!textRuns.isEmpty()) {
            textRuns.getLast().glyphEnd = glyphs.length;
        }

        if (layout.hasEffect()) {
            // initialize texture if not
            EffectRenderType.getTexture();
            bounds.joinNoCheck(x, baseline + TextRenderEffect.STRIKETHROUGH_OFFSET,
                    x + layout.getTotalAdvance(), baseline + (TextRenderEffect.UNDERLINE_OFFSET + TextRenderEffect.UNDERLINE_THICKNESS));
        }

        ScreenRectangle finalBounds = null;
        if (!bounds.isEmpty()) {
            int L = (int) Math.floor(bounds.left()), T = (int) Math.floor(bounds.top()),
                    R = (int) Math.ceil(bounds.right()), B = (int) Math.ceil(bounds.bottom());
            finalBounds = new ScreenRectangle(L, T, R - L + (dropShadow ? 1 : 0), B - T);
        }


        this.density = density;
        this.shadowOffset = shadowOffset;
        this.dropShadow = dropShadow;
        this.color = color;
        this.bgColor = bgColor;
        this.x = x;
        this.top = top;
        this.bounds = finalBounds;
        this.runs = textRuns;
        this.hasEffect = layout.hasEffect();
        this.totalAdvance = layout.getTotalAdvance();
        this.glyphs = glyphs;
        this.positions = positions;
        this.flags = flags;
    }

    @Override
    public void visit(@Nonnull Font.GlyphVisitor glyphVisitor) {
        throw new UnsupportedOperationException("Modern Text Engine");
    }

    @Nullable
    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void submitRuns(GuiRenderState renderState, Matrix3x2f pose,
                           @Nullable ScreenRectangle scissor) {
        if ((bgColor & 0xFF000000) != 0) {
            // this is only used by CartographyTableScreen, emit as normal fills
            renderState.submitGlyphToCurrentLayer(
                    new GradientRectangleRenderState(
                            RenderPipelines.GUI,
                            TextureSetup.noTexture(),
                            pose,
                            x - 1, top - 1,
                            x + totalAdvance + 1, top + 9,
                            bgColor, bgColor, bgColor, bgColor,
                            scissor, null
                    )
            );
        }
        // For-index is 2x faster than enhanced-for
        for (int i = 0; i < runs.size(); i++) {
            var run = runs.get(i);
            renderState.submitGlyphToCurrentLayer(
                    new TextRunRenderState(pose, run.pipeline,
                            TextureSetup.singleTextureWithLightmap(run.textureView),
                            scissor,
                            x, top, color, dropShadow,
                            glyphs, positions, flags,
                            run.glyphStart, run.glyphEnd,
                            run.isColorEmoji, run.isDirectMask,
                            density, shadowOffset)
            );
        }
        if (hasEffect) {
            renderState.submitGlyphToCurrentLayer(
                    new TextEffectRenderState(pose,
                            scissor,
                            x, top, color, dropShadow,
                            positions, flags,
                            totalAdvance, shadowOffset)
            );
        }
    }

    /**
     * GPU-baked text sub run.
     */
    static class TextRun {

        public final RenderPipeline pipeline;
        public final GpuTextureView textureView;
        public final int glyphStart;
        public int glyphEnd;
        public final boolean isColorEmoji;
        public final boolean isDirectMask;

        public TextRun(RenderPipeline pipeline, GpuTextureView textureView, int glyphStart,
                       boolean isColorEmoji, boolean isDirectMask) {
            this.pipeline = pipeline;
            this.textureView = textureView;
            this.glyphStart = glyphStart;
            this.isColorEmoji = isColorEmoji;
            this.isDirectMask = isDirectMask;
        }
    }
}
