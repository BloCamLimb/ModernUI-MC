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

package icyllis.modernui.mc.text.mixin;

import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.mc.text.ModernTextRenderer;
import icyllis.modernui.mc.text.TextLayout;
import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.mc.text.TextRenderType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiTextRenderState.class)
public class MixinGuiTextRenderState {

    @Shadow
    @Final
    public Matrix3x2f pose;

    @Redirect(method = "ensurePrepared",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;prepareText" +
                    "(Lnet/minecraft/util/FormattedCharSequence;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private Font.PreparedText onPrepareText(Font font, FormattedCharSequence text,
                                            float x, float y, int color, boolean dropShadow,
                                            int backgroundColor) {
        TextLayout layout = TextLayoutEngine.getInstance().lookupFormattedLayout(text);
        Matrix3x2f ctm = pose;
        int mode;
        boolean isPureTranslation;
        if (!MathUtil.isApproxZero(ctm.m01) ||
                !MathUtil.isApproxZero(ctm.m10) ||
                !MathUtil.isApproxEqual(ctm.m00, 1.0f) ||
                !MathUtil.isApproxEqual(ctm.m11, 1.0f)) {
            isPureTranslation = false;
            if (ModernTextRenderer.sComputeDeviceFontSize &&
                    MathUtil.isApproxZero(ctm.m01) &&
                    MathUtil.isApproxZero(ctm.m10) &&
                    MathUtil.isApproxEqual(ctm.m00, ctm.m11)) {
                mode = TextRenderType.MODE_UNIFORM_SCALE;
            } else if (ModernTextRenderer.sAllowSDFTextIn2D) {
                mode = TextRenderType.MODE_SDF_FILL;
            } else {
                mode = TextRenderType.MODE_NORMAL;
            }
        } else {
            mode = TextRenderType.MODE_NORMAL;
            isPureTranslation = true;
        }
        // compute exact font size and position
        float uniformScale = 1;
        if (ModernTextRenderer.sComputeDeviceFontSize &&
                (isPureTranslation || mode == TextRenderType.MODE_UNIFORM_SCALE)) {
            // uniform scale case
            // extract the translation vector for snapping to pixel grid
            x += ctm.m20 / ctm.m00;
            y += ctm.m21 / ctm.m11;
            // we modified this.pose
            ctm.m20 = 0;
            ctm.m21 = 0;
            // total scale
            uniformScale = ctm.m00;
            if (MathUtil.isApproxEqual(uniformScale, 1)) {
                mode = TextRenderType.MODE_NORMAL;
            } else {
                float upperLimit = Math.max(1.0f,
                        (float) TextLayoutEngine.sMinPixelDensityForSDF / layout.getCreatedResLevel());
                if (uniformScale <= upperLimit) {
                    // uniform scale smaller and not too large
                    mode = TextRenderType.MODE_UNIFORM_SCALE;
                } else {
                    mode = ModernTextRenderer.sAllowSDFTextIn2D ? TextRenderType.MODE_SDF_FILL : TextRenderType.MODE_NORMAL;
                }
            }
        }
        return layout.prepareTextWithDensity(x, y, color, dropShadow, mode, uniformScale, backgroundColor);
    }
}
