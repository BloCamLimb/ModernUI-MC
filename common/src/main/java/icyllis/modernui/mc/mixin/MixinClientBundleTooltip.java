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

import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import org.spongepowered.asm.mixin.Mixin;

@Deprecated
@Mixin(ClientBundleTooltip.class)
public class MixinClientBundleTooltip {

    /*@Redirect(method = "blit",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V"))
    private void setColor(float r, float g, float b, float a) {
        if (TooltipRenderer.sTooltip) {
            RenderSystem.enableBlend();
            // MULTIPLY BLENDING, UN_PREMULTIPLIED COLOR
            RenderSystem.setShaderColor(r, g, b, TooltipRenderer.sAlpha * a);
        } else {
            RenderSystem.setShaderColor(r, g, b, a);
        }
    }*/
}
