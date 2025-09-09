/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import icyllis.modernui.mc.BlurHandler;
import icyllis.modernui.mc.UIManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Although we already have a window resize callback in {@link MixinWindow},
 * we must resize the blur effect after the main render target resize.
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void onProcessBlurEffect(CallbackInfo ci) {
        if (BlurHandler.sOverrideVanillaBlur) {
            BlurHandler.INSTANCE.processBlurEffect(resourcePool);
            ci.cancel();
        }
    }

    @ModifyArg(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update" +
                    "(IIDJLnet/minecraft/client/DeltaTracker;I)V"),
            index = 5)
    private int onGetBlurRadius(int option) {
        return BlurHandler.INSTANCE.getBlurRadius(option);
    }

    @Inject(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getToastManager()" +
                    "Lnet/minecraft/client/gui/components/toasts/ToastManager;"))
    private void onRenderToasts(DeltaTracker ticker, boolean isTicking, CallbackInfo ci) {
        UIManager.getInstance().renderAbove(guiRenderState);
    }
}
