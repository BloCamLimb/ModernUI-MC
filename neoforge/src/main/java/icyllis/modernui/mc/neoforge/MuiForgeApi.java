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

package icyllis.modernui.mc.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;

import javax.annotation.Nullable;

public final class MuiForgeApi extends MuiModApi {

    public MuiForgeApi() {
        ModernUIMod.LOGGER.info(ModernUIMod.MARKER, "Created MuiForgeAPI");
    }

    @Override
    public void loadEffect(GameRenderer gr, Identifier effect) {
        gr.setPostEffect(effect);
    }

    /*@Override
    public ShaderInstance makeShaderInstance(ResourceProvider resourceProvider,
                                             ResourceLocation resourceLocation,
                                             VertexFormat vertexFormat) throws IOException {
        return new ShaderInstance(resourceProvider, resourceLocation, vertexFormat);
    }*/

    @Override
    public boolean isKeyBindingMatches(KeyMapping keyMapping, KeyEvent keyEvent) {
        InputConstants.Key key = InputConstants.getKey(keyEvent);
        return keyMapping.isActiveAndMatches(key);
    }

    @Override
    public Style applyRarityTo(Rarity rarity, Style baseStyle) {
        return rarity.getStyleModifier().apply(baseStyle);
    }

    @Override
    public GpuDevice getRealGpuDevice() {
        GpuDevice gpuDevice = RenderSystem.getDevice();
        /*try {
            // The ValidationGpuDevice prevents you from creating external textures, that's terrible
            if (gpuDevice instanceof net.neoforged.neoforge.client.blaze3d.validation.ValidationGpuDevice validationGpuDevice) {
                gpuDevice = validationGpuDevice.getRealDevice();
            }
        } catch (Throwable ignored) {

        }*/
        return gpuDevice;
    }

    @Override
    public GpuTexture getRealGpuTexture(GpuTexture faker) {
        GpuTexture gpuTexture = faker;
        /*try {
            if (gpuTexture instanceof net.neoforged.neoforge.client.blaze3d.validation.ValidationGpuTexture validationGpuTexture) {
                gpuTexture = validationGpuTexture.getRealTexture();
            }
        } catch (Throwable ignored) {

        }*/
        return gpuTexture;
    }

    @Override
    public void submitGuiElementRenderState(GuiGraphicsExtractor graphics, GuiElementRenderState renderState) {
        graphics.submitGuiElementRenderState(renderState);
    }

    @Override
    public void submitPictureInPictureRenderState(GuiGraphicsExtractor graphics, PictureInPictureRenderState renderState) {
        graphics.submitPictureInPictureRenderState(renderState);
    }

    @Nullable
    @Override
    public ScreenRectangle peekScissorStack(GuiGraphicsExtractor graphics) {
        return graphics.peekScissorStack();
    }

    /*@Override
    public RenderType createRenderType(String name, int bufferSize,
                                       boolean affectsCrumbling, boolean sortOnUpload,
                                       RenderPipeline renderPipeline,
                                       @Nullable RenderStateShard textureState,
                                       boolean lightmap) {
        var builder = RenderType.CompositeState.builder();
        if (textureState != null) {
            builder.setTextureState((RenderStateShard.EmptyTextureStateShard) textureState);
        }
        if (lightmap) {
            builder.setLightmapState(RenderStateShard.LIGHTMAP);
        }
        return RenderType.create(
                name, bufferSize, affectsCrumbling, sortOnUpload, renderPipeline,
                builder.createCompositeState(false)
        );
    }*/

    @Override
    public RenderType createRenderType(String name, RenderSetup allState) {
        return RenderType.create(name, allState);
    }
}
