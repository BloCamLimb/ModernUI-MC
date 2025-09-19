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
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record TextEffectRenderState(
        Matrix3x2f pose,
        @Nullable ScreenRectangle scissorArea,
        float x, float top, int color, boolean dropShadow,
        float[] positions, int[] flags,
        float totalAdvance, float shadowOffset
) implements GuiElementRenderState {
    @Override
    public void buildVertices(@Nonnull VertexConsumer vertexConsumer, float z) {
        int a = color >>> 24;
        int r = color >> 16 & 0xff;
        int g = color >> 8 & 0xff;
        int b = color & 0xff;
        final float baseline = top + TextLayout.sBaselineOffset;
        if (dropShadow && ModernTextRenderer.sAllowShadow) {
            buildPass(vertexConsumer, z, r >> 2, g >> 2, b >> 2, a, baseline, true);
        }
        buildPass(vertexConsumer, z, r, g, b, a, baseline, false);
    }

    private void buildPass(@Nonnull VertexConsumer builder, float z,
                           final int startR, final int startG, final int startB, final int a,
                           float baseline, boolean isShadow) {
        int r;
        int g;
        int b;
        var positions = this.positions;
        var flags = this.flags;
        var pose = this.pose;
        float x = this.x;
        if (isShadow) {
            x += shadowOffset;
            baseline += shadowOffset;
        }
        for (int i = 0, e = flags.length; i < e; i++) {
            final int bits = flags[i];
            if ((bits & CharacterStyle.EFFECT_MASK) == 0) {
                continue;
            }
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
            final float rx1 = x + positions[i << 1];
            final float rx2 = x + ((i + 1 == e) ? totalAdvance : positions[(i + 1) << 1]);
            if ((bits & CharacterStyle.STRIKETHROUGH_MASK) != 0) {
                TextRenderEffect.drawStrikethrough(pose, builder, rx1, rx2, baseline, z,
                        r, g, b, a);
            }
            if ((bits & CharacterStyle.UNDERLINE_MASK) != 0) {
                TextRenderEffect.drawUnderline(pose, builder, rx1, rx2, baseline, z,
                        r, g, b, a);
            }
        }
    }

    @Nonnull
    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.TEXT;
    }

    @Nonnull
    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.singleTextureWithLightmap(EffectRenderType.getTexture());
    }

    @Nullable
    @Override
    public ScreenRectangle bounds() {
        // unused
        return null;
    }
}
