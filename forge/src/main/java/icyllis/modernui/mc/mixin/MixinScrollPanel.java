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

import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScrollController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.widget.ScrollPanel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;

@Mixin(ScrollPanel.class)
public abstract class MixinScrollPanel implements ScrollController.IListener {

    @Shadow(remap = false)
    protected float scrollDistance;

    @Shadow(remap = false)
    protected abstract void applyScrollLimits();

    @Shadow(remap = false)
    protected abstract int getScrollAmount();

    @Shadow(remap = false)
    protected abstract int getMaxScroll();

    @Shadow(remap = false)
    private boolean scrolling;

    @Shadow(remap = false)
    @Final
    protected int height;

    @Shadow(remap = false)
    protected abstract int getBarHeight();

    @Shadow(remap = false)
    @Final
    private Minecraft client;

    @Unique
    private final ScrollController modernUI_MC$mScrollController = new ScrollController(this);

    /**
     * @author BloCamLimb
     * @reason Smoothing scrolling
     */
    @Overwrite
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            modernUI_MC$mScrollController.setMaxScroll(getMaxScroll());
            modernUI_MC$mScrollController.scrollBy(Math.round(-scrollY * getScrollAmount()));
            return true;
        }
        return false;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void preRender(GuiGraphics gr, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        modernUI_MC$mScrollController.update(MuiModApi.getElapsedTime());
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraftforge" +
            "/client/gui/widget/ScrollPanel;drawPanel(Lnet/minecraft/client/gui/GuiGraphics;" +
            "IILcom/mojang/blaze3d/vertex/Tesselator;II)V"), remap = false)
    private void preDrawPanel(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        gr.pose().pushPose();
        gr.pose().translate(0,
                ((int) (((int) scrollDistance - scrollDistance) * client.getWindow().getGuiScale())) / client.getWindow().getGuiScale(), 0);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraftforge" +
            "/client/gui/widget/ScrollPanel;drawPanel(Lnet/minecraft/client/gui/GuiGraphics;" +
            "IILcom/mojang/blaze3d/vertex/Tesselator;II)V"), remap = false)
    private void postDrawPanel(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        gr.pose().popPose();
    }

    @Override
    public void onScrollAmountUpdated(ScrollController controller, float amount) {
        scrollDistance = amount;
        applyScrollLimits();
    }

    /**
     * @author BloCamLimb
     * @reason Smooth scrolling
     */
    @Overwrite
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrolling) {
            int maxScroll = height - getBarHeight();
            float moved = (float) (deltaY / maxScroll);
            modernUI_MC$mScrollController.setMaxScroll(getMaxScroll());
            modernUI_MC$mScrollController.scrollBy(getMaxScroll() * moved);
            modernUI_MC$mScrollController.abortAnimation();
            return true;
        }
        return false;
    }
}
