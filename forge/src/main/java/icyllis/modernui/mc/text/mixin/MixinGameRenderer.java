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

package icyllis.modernui.mc.text.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.mc.text.TextRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transition if we are rendering 3D world or 2D.
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "preloadUiShader", at = @At("TAIL"))
    private void onPreloadUiShader(ResourceProvider vanillaProvider, CallbackInfo ci) {
        TextRenderType.preloadShaders();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void renderLevelStart(float partialTick, long frameTimeNanos, PoseStack pStack, CallbackInfo ci) {
        TextLayoutEngine.sCurrentInWorldRendering = true;
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void renderLevelEnd(float partialTick, long frameTimeNanos, PoseStack pStack, CallbackInfo ci) {
        TextLayoutEngine.sCurrentInWorldRendering = false;
    }
}
