/*
 * Modern UI.
 * Copyright (C) 2019-2026 BloCamLimb. All rights reserved.
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

import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fc;

import javax.annotation.Nonnull;

/**
 * Runtime knobs and the world (3D) entry points of the Modern Text Engine.
 *
 * <p>Minecraft 26.2 removed the old MultiBufferSource based Font.drawInBatch
 * entry points. GUI text now flows through Font.prepareText and GuiRenderState;
 * the renderer implementation for that path lives in {@link ModernPreparedText}.
 * World text does not go through Font at all, the feature renderers are taken over
 * directly and call into here.
 */
public final class ModernTextRenderer {

    public static volatile boolean sAllowShadow = true;
    public static volatile float sShadowOffset = 1.0f;
    public static volatile float sOutlineOffset = 0.5f;
    public static volatile boolean sComputeDeviceFontSize = true;
    public static volatile boolean sAllowSDFTextIn2D = true;
    public static volatile boolean sTweakExperienceText = true;
    public static final float OUTLINE_DEPTH = 0.01f;

    public ModernTextRenderer(TextLayoutEngine engine) {
    }

    public static int chooseMode(@Nonnull Font.DisplayMode displayMode) {
        return displayMode == Font.DisplayMode.SEE_THROUGH
                ? TextRenderType.MODE_SEE_THROUGH
                : TextRenderType.MODE_SDF_FILL;
    }

    public static void drawWorldText(@Nonnull FormattedCharSequence text,
                                     float x, float y, int color, boolean dropShadow,
                                     @Nonnull Matrix4fc matrix,
                                     @Nonnull TextLayout.BufferSource source,
                                     @Nonnull Font.DisplayMode displayMode,
                                     int bgColor, int packedLight) {
        if (text == FormattedCharSequence.EMPTY) {
            return;
        }
        TextLayout layout = TextLayoutEngine.getInstance().lookupFormattedLayout(text);
        layout.drawText(matrix, source, x, y, color, dropShadow,
                chooseMode(displayMode),
                displayMode == Font.DisplayMode.POLYGON_OFFSET,
                bgColor, packedLight);
    }

    /**
     * The text of a glowing entity or a glowing sign. Vanilla draws eight offset copies
     * of the string behind it; the Modern Text Engine draws a single SDF stroke instead.
     */
    public static void drawWorldText8xOutline(@Nonnull FormattedCharSequence text,
                                              float x, float y, int color, int outlineColor,
                                              @Nonnull Matrix4fc matrix,
                                              @Nonnull TextLayout.BufferSource source,
                                              int packedLight) {
        if (text == FormattedCharSequence.EMPTY) {
            return;
        }
        final boolean isBlack = (color & 0xFFFFFF) == 0;
        if (isBlack) {
            color = outlineColor;
        }
        TextLayout layout = TextLayoutEngine.getInstance().lookupFormattedLayout(text);
        layout.drawTextWithOutline(matrix, source, x, y, color, outlineColor,
                !isBlack && TextLayoutEngine.sUseTextShadersInWorld, packedLight);
    }
}
