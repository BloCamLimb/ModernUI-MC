/*
 * Modern UI.
 * Copyright (C) 2024 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import icyllis.modernui.mc.mixin.AccessGuiGraphics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;

/**
 * Extension of {@link GuiGraphics}, add methods to draw rounded rectangles
 * in Minecraft GUI system.
 * <p>
 * Each time you want to use this class, you create a new ExtendedGuiGraphics
 * using an existing GuiGraphics instance. There is no difference in operating
 * on this instance and the original instance.
 * <p>
 * Unlike vanilla {@link GuiGraphics}, this class cannot use batch rendering,
 * thus each draw call will be flushed immediately.
 * <p>
 * Due to precision problem, if {@link #pose()} is not a pure translation, then
 * you should increase z by 0.1 for subsequent draws.
 *
 * @since 3.11.1
 */
public class ExtendedGuiGraphics {

    private final GuiGraphics guiGraphics;
    private final MultiBufferSource.BufferSource bufferSource;

    private int mColorTL = ~0;
    private int mColorTR = ~0;
    private int mColorBR = ~0;
    private int mColorBL = ~0;

    private float mDepth = 0;
    private float mWidth = 1;

    public ExtendedGuiGraphics(@Nonnull GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
        this.bufferSource = ((AccessGuiGraphics) guiGraphics).getBufferSource();
    }

    /**
     * Reference to pose stack to transform geometries' local coordinates.
     */
    public PoseStack pose() {
        return guiGraphics.pose();
    }

    /**
     * Set the solid color used to draw subsequent geometries.
     * This method overrides the gradient colors set by {@link #setGradient(Orientation, int, int)}.
     *
     * @param color the 0xAARRGGBB color
     */
    public void setColor(int color) {
        mColorTL = color;
        mColorTR = color;
        mColorBR = color;
        mColorBL = color;
    }

    /**
     * Set the gradient colors used to draw subsequent geometries.
     * The colors are linearly interpolated in sRGB color space.
     * This method overrides the solid color set by {@link #setColor(int)}.
     *
     * @param orientation the gradient orientation
     * @param startColor  the from 0xAARRGGBB color
     * @param endColor    the to 0xAARRGGBB color
     */
    public void setGradient(@Nonnull Orientation orientation, int startColor, int endColor) {
        int midColor = TooltipRenderer.lerpInLinearSpace(0.5f, startColor, endColor);
        switch (orientation) {
            case TOP_BOTTOM -> {
                mColorTL = startColor;
                mColorTR = startColor;
                mColorBR = endColor;
                mColorBL = endColor;
            }
            case TR_BL -> {
                mColorTL = midColor;
                mColorTR = startColor;
                mColorBR = midColor;
                mColorBL = endColor;
            }
            case RIGHT_LEFT -> {
                mColorTL = endColor;
                mColorTR = startColor;
                mColorBR = startColor;
                mColorBL = endColor;
            }
            case BR_TL -> {
                mColorTL = endColor;
                mColorTR = midColor;
                mColorBR = startColor;
                mColorBL = midColor;
            }
            case BOTTOM_TOP -> {
                mColorTL = endColor;
                mColorTR = endColor;
                mColorBR = startColor;
                mColorBL = startColor;
            }
            case BL_TR -> {
                mColorTL = midColor;
                mColorTR = endColor;
                mColorBR = midColor;
                mColorBL = startColor;
            }
            case LEFT_RIGHT -> {
                mColorTL = startColor;
                mColorTR = endColor;
                mColorBR = endColor;
                mColorBL = startColor;
            }
            case TL_BR -> {
                mColorTL = startColor;
                mColorTR = midColor;
                mColorBR = endColor;
                mColorBL = midColor;
            }
        }
    }

    public float getDepth() {
        return mDepth;
    }

    /**
     * Set the depth of the draw.
     */
    public void setDepth(float depth) {
        mDepth = depth;
    }

    public float getStrokeWidth() {
        return mWidth;
    }

    /**
     * Set the thickness used to stroke geometries.
     * The default is 1 mc pixel (Minecraft GUI scaled coordinates).
     */
    public void setStrokeWidth(float width) {
        if (width >= 0.0f) {
            mWidth = width;
        }
    }

    /**
     * Draw a circle at (centerX, centerY) with radius. The
     * circle will be filled with the current color.
     *
     * @param centerX the x-coordinate of the center of the circle to be drawn
     * @param centerY the y-coordinate of the center of the circle to be drawn
     * @param radius  the radius of the circle to be drawn
     */
    public void fillCircle(float centerX, float centerY, float radius) {
        drawRoundRect(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                mDepth, radius, -1.0f);
    }

    /**
     * Draw a circle at (centerX, centerY) with radius. The
     * circle will be stroked with the current stroke width and color.
     *
     * @param centerX the x-coordinate of the center of the circle to be drawn
     * @param centerY the y-coordinate of the center of the circle to be drawn
     * @param radius  the radius of the circle to be drawn
     * @see #setStrokeWidth(float)
     */
    public void strokeCircle(float centerX, float centerY, float radius) {
        drawRoundRect(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                mDepth, radius, mWidth * 0.5f);
    }

    /**
     * Draw a rectangle with rounded corners within a rectangular bounds. The
     * round rectangle will be filled with the current color.
     *
     * @param left   the left of the rectangular bounds
     * @param top    the top of the rectangular bounds
     * @param right  the right of the rectangular bounds
     * @param bottom the bottom of the rectangular bounds
     * @param radius the radius used to round the corners
     */
    public void fillRoundRect(float left, float top, float right, float bottom,
                              float radius) {
        drawRoundRect(left, top, right, bottom,
                mDepth, radius, -1.0f);
    }

    /**
     * Draw a rectangle with rounded corners within a rectangular bounds. The
     * round rectangle will be stroked with the current stroke width and color.
     *
     * @param left   the left of the rectangular bounds
     * @param top    the top of the rectangular bounds
     * @param right  the right of the rectangular bounds
     * @param bottom the bottom of the rectangular bounds
     * @param radius the radius used to round the corners
     * @see #setStrokeWidth(float)
     */
    public void strokeRoundRect(float left, float top, float right, float bottom,
                                float radius) {
        drawRoundRect(left, top, right, bottom,
                mDepth, radius, mWidth * 0.5f);
    }

    /**
     * Controls how the gradient is oriented relative to the draw's bounds
     */
    public enum Orientation {
        /**
         * draw the gradient from the top to the bottom
         */
        TOP_BOTTOM,
        /**
         * draw the gradient from the top-right to the bottom-left
         */
        TR_BL,
        /**
         * draw the gradient from the right to the left
         */
        RIGHT_LEFT,
        /**
         * draw the gradient from the bottom-right to the top-left
         */
        BR_TL,
        /**
         * draw the gradient from the bottom to the top
         */
        BOTTOM_TOP,
        /**
         * draw the gradient from the bottom-left to the top-right
         */
        BL_TR,
        /**
         * draw the gradient from the left to the right
         */
        LEFT_RIGHT,
        /**
         * draw the gradient from the top-left to the bottom-right
         */
        TL_BR,
    }

    private void drawRoundRect(float left, float top, float right, float bottom,
                               float depth, float radius, float thickness) {
        if (!(left < right && top < bottom)) { // also capture NaN
            return;
        }
        if (thickness == 0.0f) {
            return;
        }
        if (!Float.isFinite(radius) || radius < 0.0f) { // NaN, Inf, negative
            radius = 0;
        }
        CompiledShaderProgram shader = RenderSystem.setShader(GuiRenderType.SHADER_ROUND_RECT);
        if (shader == null) {
            return;
        }
        Matrix4f pose = guiGraphics.pose().last().pose();
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;
        float extentX = (right - left) * 0.5f;
        float extentY = (bottom - top) * 0.5f;
        radius = Math.min(radius, Math.min(extentX, extentY));
        float outset = 1 + thickness; // conservative
        if ((pose.properties() & Matrix4f.PROPERTY_TRANSLATION) != 0) {
            // pure translation
            shader.safeGetUniform("u_Rect")
                    .set(centerX + pose.m30(), centerY + pose.m31(), extentX, extentY);
            shader.safeGetUniform("u_Radii")
                    .set(radius, thickness);
            var buffer = bufferSource.getBuffer(GuiRenderType.roundRect());
            buffer.addVertex(pose, left - outset, top - outset, depth).setColor(mColorTL);
            buffer.addVertex(pose, left - outset, bottom + outset, depth).setColor(mColorBL);
            buffer.addVertex(pose, right + outset, bottom + outset, depth).setColor(mColorBR);
            buffer.addVertex(pose, right + outset, top - outset, depth).setColor(mColorTR);

            // we modify uniform for each draw, so cannot do batch rendering
            guiGraphics.flush();
        } else {
            // here we modify global model view, so cannot do batch rendering
            guiGraphics.flush();

            // we expect local coordinates, concat pose with model view
            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().mul(pose);
            shader.safeGetUniform("u_Rect")
                    .set(centerX, centerY, extentX, extentY);
            shader.safeGetUniform("u_Radii")
                    .set(radius, thickness);
            var buffer = bufferSource.getBuffer(GuiRenderType.roundRect());
            buffer.addVertex(left - outset, top - outset, depth).setColor(mColorTL);
            buffer.addVertex(left - outset, bottom + outset, depth).setColor(mColorBL);
            buffer.addVertex(right + outset, bottom + outset, depth).setColor(mColorBR);
            buffer.addVertex(right + outset, top - outset, depth).setColor(mColorTR);

            // we modify uniform for each draw, so cannot do batch rendering
            guiGraphics.flush();
            RenderSystem.getModelViewStack().popMatrix();
        }
    }
}
