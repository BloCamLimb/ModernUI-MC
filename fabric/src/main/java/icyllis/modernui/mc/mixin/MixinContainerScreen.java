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

import icyllis.modernui.mc.IModernGuiGraphics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(AbstractContainerScreen.class)
public class MixinContainerScreen {

    @Inject(method = "renderTooltip",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;setTooltipForNextFrame" +
                            "(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;" +
                            "IILnet/minecraft/resources/ResourceLocation;)V"),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void preRenderTooltip(GuiGraphics gr, int x, int y, CallbackInfo ci, ItemStack stack) {
        ((IModernGuiGraphics) gr).modernUI_MC$setTooltipStack(stack);
    }

    @Inject(method = "renderTooltip",
            at = @At(value = "INVOKE",
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;setTooltipForNextFrame" +
                            "(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;" +
                            "IILnet/minecraft/resources/ResourceLocation;)V"),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void postRenderTooltip(GuiGraphics gr, int x, int y, CallbackInfo ci, ItemStack stack) {
        ((IModernGuiGraphics) gr).modernUI_MC$setTooltipStack(ItemStack.EMPTY);
    }
}
