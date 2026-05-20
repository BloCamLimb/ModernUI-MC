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

package icyllis.modernui.mc.text.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import icyllis.modernui.mc.text.ModernPreparedText;
import icyllis.modernui.mc.text.TextRenderType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;

@Mixin(GuiRenderer.class)
public class MixinGuiRenderer {

    @Shadow
    @Final
    GuiRenderState renderState;

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    private void prepareText() {
        renderState.forEachText(guiTextRenderState -> {
            Matrix3x2fc pose = guiTextRenderState.pose;
            ScreenRectangle scissor = guiTextRenderState.scissor;
            Font.PreparedText preparedText = guiTextRenderState.ensurePrepared();
            if (preparedText instanceof ModernPreparedText) {
                ((ModernPreparedText) preparedText).submitRuns(renderState, pose, scissor);
            } else {
                // some mods subclass GuiTextRenderState to return a custom PreparedText,
                // fallback to vanilla logic
                preparedText.visit(new Font.GlyphVisitor() {
                    @Override
                    public void acceptGlyph(TextRenderable.Styled glyph) {
                        renderState.addGlyphToCurrentLayer(new GlyphRenderState(pose, glyph, scissor));
                    }

                    @Override
                    public void acceptEffect(TextRenderable glyph) {
                        renderState.addGlyphToCurrentLayer(new GlyphRenderState(pose, glyph, scissor));
                    }
                });
            }
        });
    }

    // setup bilinear sampler for SDF text

    /*@Unique
    private FilterMode modernUI_MC$oldMinFilter;
    @Unique
    private FilterMode modernUI_MC$oldMagFilter;
    @Unique
    private boolean modernUI_MC$oldUseMipmaps;

    @Inject(method = "executeDraw", at = @At("HEAD"))
    private void beforeExecuteDraw(@Coerce Object $$0, RenderPass $$1, GpuBuffer $$2, VertexFormat.IndexType $$3, CallbackInfo ci) {
        AccessGuiRendererDraw draw = (AccessGuiRendererDraw) $$0;
        if (draw.pipeline() == TextRenderType.getPipelineSDFFill()) {
            @SuppressWarnings("resource") GpuTextureView ptrTexView = draw.textureSetup().texure0();
            assert ptrTexView != null;
            AccessGpuTexture tex = (AccessGpuTexture) ptrTexView.texture();
            modernUI_MC$oldMinFilter = tex.getMinFilter();
            modernUI_MC$oldMagFilter = tex.getMagFilter();
            modernUI_MC$oldUseMipmaps = tex.getUseMipmaps();
            ptrTexView.texture().setTextureFilter(FilterMode.LINEAR, FilterMode.LINEAR, true);
        }
    }

    @Inject(method = "executeDraw", at = @At("TAIL"))
    private void afterExecuteDraw(@Coerce Object $$0, RenderPass $$1, GpuBuffer $$2, VertexFormat.IndexType $$3, CallbackInfo ci) {
        if (modernUI_MC$oldMinFilter != null) {
            AccessGuiRendererDraw draw = (AccessGuiRendererDraw) $$0;
            @SuppressWarnings("resource") GpuTextureView ptrTexView = draw.textureSetup().texure0();
            assert ptrTexView != null;
            ptrTexView.texture().setTextureFilter(modernUI_MC$oldMinFilter, modernUI_MC$oldMagFilter, modernUI_MC$oldUseMipmaps);
            modernUI_MC$oldMinFilter = null;
            modernUI_MC$oldMagFilter = null;
        }
    }*/
}
