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

package icyllis.modernui.mc;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import icyllis.modernui.graphics.MathUtil;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The vanilla {@link net.minecraft.client.gui.render.state.ColoredRectangleRenderState}
 * only allows integer coordinates and vertical gradients, this class removes these restrictions.
 *
 * @since 3.12
 */
public record GradientRectangleRenderState(
        @Nonnull RenderPipeline pipeline,
        @Nonnull TextureSetup textureSetup,
        @Nonnull Matrix3x2f pose,
        float left, float top, float right, float bottom,
        int colorUL, int colorUR, int colorLR, int colorLL,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    /**
     * @param colorUL top-left color
     * @param colorUR top-right color
     * @param colorLR bottom-right color
     * @param colorLL bottom-left color
     */
    public GradientRectangleRenderState(@Nonnull RenderPipeline pipeline,
                                        @Nonnull TextureSetup textureSetup,
                                        @Nonnull Matrix3x2f pose,
                                        float left, float top, float right, float bottom,
                                        int colorUL, int colorUR, int colorLR, int colorLL,
                                        @Nullable ScreenRectangle scissorArea) {
        this(pipeline, textureSetup, pose,
                left, top, right, bottom,
                colorUL, colorUR, colorLR, colorLL,
                scissorArea,
                getBounds(left, top, right, bottom, pose, scissorArea));
    }

    @Override
    public void buildVertices(@Nonnull VertexConsumer consumer, float z) {
        consumer.addVertexWith2DPose(pose, right, bottom, z).setColor(colorLR);
        consumer.addVertexWith2DPose(pose, right, top, z).setColor(colorUR);
        consumer.addVertexWith2DPose(pose, left, top, z).setColor(colorUL);
        consumer.addVertexWith2DPose(pose, left, bottom, z).setColor(colorLL);
    }

    /**
     * Null bounds = empty bounds = nothing to draw.
     * See {@link icyllis.modernui.graphics.Matrix#mapRectOut}
     */
    //@formatter:off
    @Nullable
    public static ScreenRectangle getBounds(float left, float top, float right, float bottom,
                                            @Nonnull Matrix3x2f pose, @Nullable ScreenRectangle scissor) {
        float x1 = pose.m00 * left +  pose.m10 * top    + pose.m20;
        float y1 = pose.m01 * left +  pose.m11 * top    + pose.m21;
        float x2 = pose.m00 * right + pose.m10 * top    + pose.m20;
        float y2 = pose.m01 * right + pose.m11 * top    + pose.m21;
        float x3 = pose.m00 * left +  pose.m10 * bottom + pose.m20;
        float y3 = pose.m01 * left +  pose.m11 * bottom + pose.m21;
        float x4 = pose.m00 * right + pose.m10 * bottom + pose.m20;
        float y4 = pose.m01 * right + pose.m11 * bottom + pose.m21;

        // Minecraft's bounds calculation is wrong,
        // width should be: ceil(right) - floor(left)
        // instead of     : ceil(right - left)
        int L = (int) Math.floor(MathUtil.min(x1, x2, x3, x4));
        int T = (int) Math.floor(MathUtil.min(y1, y2, y3, y4));
        int R = (int) Math.ceil (MathUtil.max(x1, x2, x3, x4));
        int B = (int) Math.ceil (MathUtil.max(y1, y2, y3, y4));

        if (L >= R || T >= B) {
            return null;
        }

        ScreenRectangle bounds = new ScreenRectangle(L, T, R - L, B - T);
        return scissor != null ? scissor.intersection(bounds) : bounds;
    }
    //@formatter:on
}
