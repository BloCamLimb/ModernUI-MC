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
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphics implements IModernGuiGraphics {

    // equivalent to Forge
    @Unique
    private ItemStack modernUI_MC$tooltipStack = ItemStack.EMPTY;

    @Shadow
    public abstract int guiWidth();

    @Shadow
    public abstract int guiHeight();

    @Shadow
    protected abstract void renderTooltipInternal(Font arg, List<ClientTooltipComponent> list, int m, int n,
                                                  ClientTooltipPositioner arg2, @Nullable ResourceLocation arg3);

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"))
    private void preRenderTooltip(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        modernUI_MC$tooltipStack = stack;
    }

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("TAIL"))
    private void postRenderTooltip(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        modernUI_MC$tooltipStack = ItemStack.EMPTY;
    }

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;" +
            "IILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"), cancellable = true)
    private void onRenderTooltip(Font font, List<Component> components, Optional<TooltipComponent> tooltipComponent,
                                 int x, int y, @Nullable ResourceLocation tooltipStyle, CallbackInfo ci) {
        if (TooltipRenderer.sTooltip && TooltipRenderer.sLineWrapping_FabricOnly) {
            if (!components.isEmpty()) {
                var transformedComponents = modernUI_MC$transformComponents(
                        font, components, tooltipComponent, x
                );
                renderTooltipInternal(font, transformedComponents,
                        x, y, DefaultTooltipPositioner.INSTANCE, tooltipStyle);
                ci.cancel();
            }
        }
    }

    // equivalent to Forge
    @Unique
    private List<ClientTooltipComponent> modernUI_MC$transformComponents(
            Font font, List<Component> components, Optional<TooltipComponent> tooltipComponent,
            int x) {
        List<ClientTooltipComponent> result = new ArrayList<>(components.size() + 1);

        int screenWidth = guiWidth();
        int tooltipWidth = 0;
        int[] widths = new int[components.size()];
        for (int i = 0; i < components.size(); i++) {
            widths[i] = font.width(components.get(i));
            tooltipWidth = Math.max(tooltipWidth, widths[i]);
        }

        int tooltipX = x + TooltipRenderer.TOOLTIP_SPACE;
        if (tooltipX + tooltipWidth + TooltipRenderer.H_BORDER > screenWidth) {
            tooltipX = x - TooltipRenderer.TOOLTIP_SPACE - TooltipRenderer.H_BORDER - tooltipWidth;
            if (tooltipX < TooltipRenderer.H_BORDER) {
                if (x > screenWidth / 2)
                    tooltipWidth = x - TooltipRenderer.TOOLTIP_SPACE - TooltipRenderer.H_BORDER * 2;
                else
                    tooltipWidth = screenWidth - TooltipRenderer.TOOLTIP_SPACE - TooltipRenderer.H_BORDER - x;
            }
        }

        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);
            if (widths[i] > tooltipWidth) {
                for (var line : font.split(component, tooltipWidth)) {
                    result.add(ClientTooltipComponent.create(line));
                }
            } else {
                result.add(ClientTooltipComponent.create(component.getVisualOrderText()));
            }
            if (i == 0 && tooltipComponent.isPresent()) {
                result.add(ClientTooltipComponent.create(tooltipComponent.get()));
            }
        }

        return result;
    }

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void onRenderTooltip(Font font, List<ClientTooltipComponent> components,
                                 int x, int y, ClientTooltipPositioner positioner,
                                 @Nullable ResourceLocation tooltipStyle,
                                 CallbackInfo ci) {
        if (TooltipRenderer.sTooltip) {
            if (!components.isEmpty()) {
                UIManager.getInstance().drawExtTooltip(modernUI_MC$tooltipStack,
                        (GuiGraphics) (Object) this,
                        components, x, y, font,
                        guiWidth(), guiHeight(), positioner, tooltipStyle);
                ci.cancel();
            }
        }
    }

    @Override
    public void modernUI_MC$setTooltipStack(@Nonnull ItemStack stack) {
        modernUI_MC$tooltipStack = stack;
    }
}
