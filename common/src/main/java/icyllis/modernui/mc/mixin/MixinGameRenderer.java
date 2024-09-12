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

import icyllis.modernui.mc.BlurHandler;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Although we already have a window resize callback in {@link MixinWindow},
 * we must resize the blur effect after the main render target resize.
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow
    @Nullable
    private PostChain blurEffect;

    @Inject(method = "resize", at = @At("TAIL"))
    private void onResize(int width, int height, CallbackInfo ci) {
        BlurHandler.INSTANCE.resize(width, height);
    }

    @Inject(method = "loadBlurEffect", at = @At("HEAD"), cancellable = true)
    private void onLoadBlurEffect(ResourceProvider resourceProvider, CallbackInfo ci) {
        if (BlurHandler.sOverrideVanillaBlur) {
            if (blurEffect != null) {
                blurEffect.close();
            }
            blurEffect = null;
            ci.cancel();
        }
    }

    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void onProcessBlurEffect(float partialTick, CallbackInfo ci) {
        if (BlurHandler.sOverrideVanillaBlur) {
            BlurHandler.INSTANCE.processBlurEffect(partialTick);
            ci.cancel();
        }
    }
}
