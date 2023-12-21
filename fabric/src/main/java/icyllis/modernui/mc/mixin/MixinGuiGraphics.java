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

import icyllis.modernui.mc.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import java.util.List;

@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphics implements IModernGuiGraphics {

    @Unique
    private ItemStack tooltipStack = ItemStack.EMPTY;

    @Shadow
    public abstract int guiWidth();

    @Shadow
    public abstract int guiHeight();

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"))
    private void preRenderTooltip(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        tooltipStack = stack;
    }

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("TAIL"))
    private void postRenderTooltip(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        tooltipStack = ItemStack.EMPTY;
    }

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void onRenderTooltip(Font font, List<ClientTooltipComponent> components,
                                 int x, int y, ClientTooltipPositioner positioner,
                                 CallbackInfo ci) {
        if (TooltipRenderer.sTooltip) {
            if (!components.isEmpty()) {
                UIManager.getInstance().drawExtTooltip(tooltipStack,
                        (GuiGraphics) (Object) this,
                        components, x, y, font,
                        guiWidth(), guiHeight(), positioner);
            }
            ci.cancel();
        }
    }

    @Override
    public void modernUI_MC$setTooltipStack(@Nonnull ItemStack stack) {
        tooltipStack = stack;
    }
}
