/*
 * Modern UI.
 * Copyright (C) 2025 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import icyllis.modernui.mc.TooltipRenderer;
import icyllis.modernui.mc.UIManager;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public class MixinGuiRenderer {

    @Shadow
    @Final
    private GuiRenderState renderState;

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;prepare()V"))
    private void beforePrepare(CallbackInfo ci) {
        UIManager.getInstance().renderScreenLayer(renderState);
    }

    @Inject(method = "executeDrawRange",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms" +
                    "(Lcom/mojang/blaze3d/systems/RenderPass;)V", shift = At.Shift.AFTER, remap = false),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onExecuteDrawRange(Supplier<String> $$0, RenderTarget $$1, GpuBufferSlice $$2,
                                    int $$3, int $$4, CallbackInfo ci,
                                    RenderPass renderPass) {
        if (TooltipRenderer.sTooltip) {
            GpuBufferSlice tooltipUniforms = UIManager.getInstance().mTooltipRenderer.mUniforms;
            if (tooltipUniforms != null) {
                renderPass.setUniform("ModernTooltip", tooltipUniforms);
            }
        }
    }
}
