/*
 * Modern UI.
 * Copyright (C) 2019-2025 BloCamLimb. All rights reserved.
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

import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScrollController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mixin(AbstractSelectionList.class)
public abstract class MixinSelectionList extends AbstractContainerWidget implements ScrollController.IListener {

    public MixinSelectionList(int x, int y, int width, int height, Component msg) {
        super(x, y, width, height, msg);
    }

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Unique
    @Nullable
    private ScrollController modernUI_MC$scrollController;

    @Unique
    private boolean modernUI_MC$callSuperSetScrollAmount;

    /**
     * @author BloCamLimb
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible) {
            return false;
        }
        if (scrollY != 0) {
            if (modernUI_MC$scrollController != null) {
                modernUI_MC$scrollController.setMaxScroll(maxScrollAmount());
                modernUI_MC$scrollController.scrollBy(Math.round(-scrollY * 40));
            } else {
                super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
            return true;
        }
        return false;
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void preRender(GuiGraphics gr, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (modernUI_MC$scrollController == null) {
            modernUI_MC$scrollController = new ScrollController(this);
            modernUI_MC$skipAnimationTo(scrollAmount());
        }
        modernUI_MC$scrollController.update(MuiModApi.getElapsedTime());
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client" +
            "/gui/components/AbstractSelectionList;renderHeader(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void preRenderHeader(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        gr.pose().pushPose();
        gr.pose().translate(0,
                ((int) (((int) scrollAmount() - scrollAmount()) * minecraft.getWindow().getGuiScale())) / minecraft.getWindow().getGuiScale(), 0);
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client" +
            "/gui/components/AbstractSelectionList;renderHeader(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void postRenderHeader(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float partialTicks,
                                  CallbackInfo ci) {
        gr.pose().popPose();
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client" +
            "/gui/components/AbstractSelectionList;renderListItems(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void preRenderList(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        gr.pose().pushPose();
        gr.pose().translate(0,
                ((int) (((int) scrollAmount() - scrollAmount()) * minecraft.getWindow().getGuiScale())) / minecraft.getWindow().getGuiScale(), 0);
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client" +
            "/gui/components/AbstractSelectionList;renderListItems(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void postRenderList(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        gr.pose().popPose();
    }

    /**
     * @author BloCamLimb
     */
    @Override
    public void setScrollAmount(double target) {
        if (modernUI_MC$scrollController != null && !modernUI_MC$callSuperSetScrollAmount) {
            modernUI_MC$skipAnimationTo(target);
        } else
            super.setScrollAmount(target);
    }

    @Override
    public void onScrollAmountUpdated(ScrollController controller, float amount) {
        modernUI_MC$callSuperSetScrollAmount = true;
        setScrollAmount(amount);
        modernUI_MC$callSuperSetScrollAmount = false;
    }

    @Unique
    public void modernUI_MC$skipAnimationTo(double target) {
        assert modernUI_MC$scrollController != null;
        modernUI_MC$scrollController.setMaxScroll(maxScrollAmount());
        modernUI_MC$scrollController.scrollTo((float) target);
        modernUI_MC$scrollController.abortAnimation();
    }
}
