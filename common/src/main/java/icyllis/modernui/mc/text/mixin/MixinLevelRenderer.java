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
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
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

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V"))
    private void endTextBatch(float partialTick,
                              long finishNanoTime,
                              boolean renderBlockOutline,
                              Camera camera,
                              GameRenderer gameRenderer,
                              LightTexture lightTexture,
                              Matrix4f modelViewMatrix,
                              Matrix4f projectionMatrix,
                              CallbackInfo ci) {
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
