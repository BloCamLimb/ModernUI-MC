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

package icyllis.modernui.mc.mixin;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import org.spongepowered.asm.mixin.Mixin;

@Deprecated
@Mixin(ClientTextTooltip.class)
public class MixinClientTextTooltip {

    /*@Redirect(method = "renderText",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch" +
                    "(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private int drawText(@Nonnull Font font, FormattedCharSequence text, float x, float y,
                         int color, boolean dropShadow, Matrix4f matrix, MultiBufferSource source,
                         Font.DisplayMode displayMode, int colorBackground, int packedLight) {
        if (TooltipRenderer.sTooltip) {
            // vanilla alpha threshold is 4, MULTIPLY BLENDING, UN_PREMULTIPLIED COLOR
            float a = (color >>> 24) / 255.0f;
            int alpha = (int) (TooltipRenderer.sAlpha * a * 255.0f + 0.5f);
            final int newColor = (Math.max(alpha, 4) << 24) | (color & 0xFFFFFF);
            return font.drawInBatch(text, x, y, newColor, dropShadow, matrix, source, displayMode, colorBackground,
                    packedLight);
        } else {
            return font.drawInBatch(text, x, y, color, dropShadow, matrix, source, displayMode, colorBackground,
                    packedLight);
        }
    }*/
}
