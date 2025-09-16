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
import org.joml.Matrix4f;

import javax.annotation.Nonnull;

/**
 * Draw underline or strikethrough.
 */
public class TextRenderEffect {

    /**
     * Offset from the string's baseline as which to draw the underline
     */
    private static final float UNDERLINE_OFFSET = 2f / 3f;

    /**
     * Offset from the string's baseline as which to draw the strikethrough line
     */
    private static final float STRIKETHROUGH_OFFSET = -7f / 2f;

    /**
     * Thickness of the underline
     */
    private static final float UNDERLINE_THICKNESS = 9f / 12f;

    /**
     * Thickness of the strikethrough line
     */
    private static final float STRIKETHROUGH_THICKNESS = 9f / 12f;

    /**
     * Offset Z to ensure that effects render over characters in 3D world
     */
    public static final float EFFECT_DEPTH = 0.01f;

    /*
     * Start X offset of this effect to the start x of the whole text
     */
    //protected final float start;

    /*
     * End X offset of this effect to the start x of the whole text
     */
    //protected final float end;

    /*
     * The color in 0xRRGGBB format, or {@link FormattingStyle#NO_COLOR}
     */
    //protected final int color;

    /*
     * Combination of {@link #UNDERLINE_MASK} and {@link #STRIKETHROUGH_MASK}
     */
    //private final byte type;

    private TextRenderEffect() {
    }

    public static void drawUnderline(@Nonnull VertexConsumer builder, float start, float end,
                                     float baseline, int r, int g, int b, int a) {
        baseline += UNDERLINE_OFFSET;
        builder.addVertex(start, baseline + UNDERLINE_THICKNESS, EFFECT_DEPTH)
                .setColor(r, g, b, a);
        builder.addVertex(end, baseline + UNDERLINE_THICKNESS, EFFECT_DEPTH)
                .setColor(r, g, b, a);
        builder.addVertex(end, baseline, EFFECT_DEPTH)
                .setColor(r, g, b, a);
        builder.addVertex(start, baseline, EFFECT_DEPTH)
                .setColor(r, g, b, a);
    }

    public static void drawUnderline(@Nonnull Matrix4f matrix, @Nonnull VertexConsumer builder,
                                     float start, float end, float baseline,
                                     int r, int g, int b, int a, int light) {
        baseline += UNDERLINE_OFFSET;
        builder.addVertex(matrix, start, baseline + UNDERLINE_THICKNESS, EFFECT_DEPTH)
                .setColor(r, g, b, a)
                .setUv(0, 1)
                .setLight(light);
        builder.addVertex(matrix, end, baseline + UNDERLINE_THICKNESS, EFFECT_DEPTH)
                .setColor(r, g, b, a)
                .setUv(1, 1)
                .setLight(light);
        builder.addVertex(matrix, end, baseline, EFFECT_DEPTH)
                .setColor(r, g, b, a)
                .setUv(1, 0)
                .setLight(light);
        builder.addVertex(matrix, start, baseline, EFFECT_DEPTH)
                .setColor(r, g, b, a)
                .setUv(0, 0)
                .setLight(light);
    }

    public static void drawStrikethrough(@Nonnull VertexConsumer builder, float start, float end,
                                         float baseline, int r, int g, int b, int a) {
        baseline += STRIKETHROUGH_OFFSET;
        builder.addVertex(start, baseline + STRIKETHROUGH_THICKNESS, EFFECT_DEPTH)
                .setColor(r, g, b, a);
        builder.addVertex(end, baseline + STRIKETHROUGH_THICKNESS, EFFECT_DEPTH)
                .setColor(r, g, b, a);
        builder.addVertex(end, baseline, EFFECT_DEPTH)
                .setColor(r, g, b, a);
        builder.addVertex(start, baseline, EFFECT_DEPTH)
                .setColor(r, g, b, a);
    }

    public static void drawStrikethrough(@Nonnull Matrix4f matrix, @Nonnull VertexConsumer builder,
                                         float start, float end, float baseline,
                                         int r, int g, int b, int a, int light) {
        baseline += STRIKETHROUGH_OFFSET;
        builder.addVertex(matrix, start, baseline + STRIKETHROUGH_THICKNESS, EFFECT_DEPTH)
                .setColor(r, g, b, a)
                .setUv(0, 1)
                .setLight(light);
        builder.addVertex(matrix, end, baseline + STRIKETHROUGH_THICKNESS, EFFECT_DEPTH)
                .setColor(r, g, b, a)
                .setUv(1, 1)
                .setLight(light);
        builder.addVertex(matrix, end, baseline, EFFECT_DEPTH)
                .setColor(r, g, b, a)
                .setUv(1, 0)
                .setLight(light);
        builder.addVertex(matrix, start, baseline, EFFECT_DEPTH)
                .setColor(r, g, b, a)
                .setUv(0, 0)
                .setLight(light);
    }

    /*@Nonnull
    public static EffectRenderInfo underline(float start, float end, int color) {
        return new EffectRenderInfo(start, end, color, UNDERLINE);
    }

    @Nonnull
    public static EffectRenderInfo strikethrough(float start, float end, int color) {
        return new EffectRenderInfo(start, end, color, STRIKETHROUGH);
    }*/

    /*private static RenderType getRenderType(boolean seeThrough) {
        return seeThrough ? seeThroughType : normalType;
    }*/
}
