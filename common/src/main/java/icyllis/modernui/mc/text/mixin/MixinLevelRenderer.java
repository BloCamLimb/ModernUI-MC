/*
 * Modern UI.
 * Copyright (C) 2019-2022 BloCamLimb. All rights reserved.
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

import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.mc.text.TextRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handle deferred rendering and transparency sorting (painter's algorithm).
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    // neoforge has lambda$addMainPass$1 but different signature from forge
    @Inject(method = {"method_62214", "lambda$addMainPass$2", "lambda$addMainPass$1" +
            "(Lnet/minecraft/client/renderer/FogParameters;Lnet/minecraft/client/DeltaTracker;" +
            "Lnet/minecraft/client/Camera;Lnet/minecraft/util/profiling/ProfilerFiller;Lorg/joml/Matrix4f;" +
            "Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/resource/ResourceHandle;" +
            "Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;" +
            "Lcom/mojang/blaze3d/resource/ResourceHandle;Lnet/minecraft/client/renderer/culling/Frustum;" +
            "ZLcom/mojang/blaze3d/resource/ResourceHandle;)V"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V"))
    private void endTextBatch(CallbackInfo ci) {
        if (TextLayoutEngine.sUseTextShadersInWorld) {
            TextRenderType firstSDFFillType = TextRenderType.getFirstSDFFillType();
            TextRenderType firstSDFStrokeType = TextRenderType.getFirstSDFStrokeType();
            if (firstSDFFillType != null) {
                renderBuffers.bufferSource().endBatch(firstSDFFillType);
            }
            if (firstSDFStrokeType != null) {
                renderBuffers.bufferSource().endBatch(firstSDFStrokeType);
            }
        }
    }
}
