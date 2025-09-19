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
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix3x2f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Similar to {@link net.minecraft.client.gui.render.state.GlyphRenderState}.
 *
 * @param isColorEmoji whether the run is color emoji
 * @param isDirectMask whether the whole text uses normal or uniform scale
 */
public record TextRunRenderState(
        Matrix3x2f pose,
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        @Nullable ScreenRectangle scissorArea,
        float x, float top, int color, boolean dropShadow,
        GLBakedGlyph[] glyphs,
        float[] positions, int[] flags,
        int glyphStart, int glyphEnd, boolean isColorEmoji,
        boolean isDirectMask, float density, float shadowOffset
) implements GuiElementRenderState {
    @Override
    public void buildVertices(@Nonnull VertexConsumer vertexConsumer, float z) {
        float invDensity = 1.0f / density;
        int a = color >>> 24;
        int r = color >> 16 & 0xff;
        int g = color >> 8 & 0xff;
        int b = color & 0xff;
        final float baseline = top + TextLayout.sBaselineOffset;
        if (dropShadow && ModernTextRenderer.sAllowShadow && !isColorEmoji) {
            buildPass(vertexConsumer, z, invDensity, r >> 2, g >> 2, b >> 2, a, baseline, true);
        }
        buildPass(vertexConsumer, z, invDensity, r, g, b, a, baseline, false);
    }

    private void buildPass(@Nonnull VertexConsumer builder, float z, float invDensity,
                           final int startR, final int startG, final int startB, final int a,
                           float baseline, boolean isShadow) {
        int r;
        int g;
        int b;
        var glyphs = this.glyphs;
        var positions = this.positions;
        var flags = this.flags;
        var pose = this.pose;
        float x = this.x;
        if (isShadow) {
            x += shadowOffset;
            baseline += shadowOffset;
        }
        for (int i = glyphStart; i < glyphEnd; i++) {
            var glyph = glyphs[i];
            if (glyph == null) {
                continue;
            }
            final int bits = flags[i];
            float rx;
            float ry;
            final float w;
            final float h;
            boolean fakeItalic = false;
            int ascent = 0;
            if ((bits & CharacterStyle.NO_SHADOW_MASK) != 0 && isShadow) {
                continue;
            }
            if ((bits & CharacterStyle.ANY_BITMAP_REPLACEMENT) != 0) {
                final float scaleFactor;
                if (!isColorEmoji) {
                    ascent = -glyph.y / TextLayoutEngine.BITMAP_SCALE;
                    scaleFactor = 1f / TextLayoutEngine.BITMAP_SCALE;
                } else {
                    assert !isShadow;
                    ascent = TextLayout.STANDARD_BASELINE_OFFSET;
                    scaleFactor = TextLayoutProcessor.sBaseFontSize / GlyphManager.EMOJI_BASE;
                }
                fakeItalic = (bits & CharacterStyle.ITALIC_MASK) != 0;
                rx = x + positions[i << 1] + glyph.x * scaleFactor;
                ry = baseline + positions[i << 1 | 1] + glyph.y * scaleFactor;
                if (isShadow) {
                    // bitmap font shadow offset is always 1 pixel
                    rx += 1.0f - shadowOffset;
                    ry += 1.0f - shadowOffset;
                }

                w = glyph.width * scaleFactor;
                h = glyph.height * scaleFactor;
            } else {
                rx = x + positions[i << 1] + glyph.x * invDensity;
                ry = baseline + positions[i << 1 | 1] + glyph.y * invDensity;

                w = glyph.width * invDensity;
                h = glyph.height * invDensity;
            }
            if (isDirectMask) {
                // align to screen pixel center in 2D
                rx = Math.round(rx * density) * invDensity;
                ry = Math.round(ry * density) * invDensity;
            }
            if (isColorEmoji) {
                r = 0xff;
                g = 0xff;
                b = 0xff;
            } else if ((bits & CharacterStyle.IMPLICIT_COLOR_MASK) != 0) {
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
            float upSkew = 0;
            float downSkew = 0;
            if (fakeItalic) {
                upSkew = 0.25f * ascent;
                downSkew = 0.25f * (ascent - h);
            }
            builder.addVertexWith2DPose(pose, rx + upSkew, ry, z)
                    .setColor(r, g, b, a)
                    .setUv(glyph.u1, glyph.v1)
                    .setLight(LightTexture.FULL_BRIGHT);
            builder.addVertexWith2DPose(pose, rx + downSkew, ry + h, z)
                    .setColor(r, g, b, a)
                    .setUv(glyph.u1, glyph.v2)
                    .setLight(LightTexture.FULL_BRIGHT);
            builder.addVertexWith2DPose(pose, rx + w + downSkew, ry + h, z)
                    .setColor(r, g, b, a)
                    .setUv(glyph.u2, glyph.v2)
                    .setLight(LightTexture.FULL_BRIGHT);
            builder.addVertexWith2DPose(pose, rx + w + upSkew, ry, z)
                    .setColor(r, g, b, a)
                    .setUv(glyph.u2, glyph.v1)
                    .setLight(LightTexture.FULL_BRIGHT);
        }
    }

    @Nullable
    @Override
    public ScreenRectangle bounds() {
        // unused
        return null;
    }
}
