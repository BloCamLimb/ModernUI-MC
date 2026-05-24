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

package icyllis.modernui.mc.fabric;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.mixin.AccessGameRenderer;
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

public final class MuiFabricApi extends MuiModApi {

    public MuiFabricApi() {
        ModernUIMod.LOGGER.info(ModernUIMod.MARKER, "Created MuiFabricApi");
    }

    @Override
    public void loadEffect(GameRenderer gr, Identifier effect) {
        ((AccessGameRenderer) gr).invokeSetPostEffect(effect);
    }

    /*@Override
    public ShaderInstance makeShaderInstance(ResourceProvider resourceProvider,
                                             ResourceLocation resourceLocation,
                                             VertexFormat vertexFormat) throws IOException {
        return new FabricShaderProgram(resourceProvider, resourceLocation, vertexFormat);
    }*/

    @Override
    public boolean isKeyBindingMatches(KeyMapping keyMapping, KeyEvent keyEvent) {
        return keyMapping.matches(keyEvent);
    }

    @Override
    public Style applyRarityTo(Rarity rarity, Style baseStyle) {
        return baseStyle.withColor(rarity.color());
    }

    @Override
    public GpuDevice getRealGpuDevice() {
        GpuDevice gpuDevice = RenderSystem.getDevice();
        return gpuDevice;
    }

    @Override
    public GpuTexture getRealGpuTexture(GpuTexture faker) {
        GpuTexture gpuTexture = faker;
        return gpuTexture;
    }

    @Override
    public void submitGuiElementRenderState(GuiGraphicsExtractor graphics, GuiElementRenderState renderState) {
        graphics.guiRenderState.addGuiElement(renderState);
    }

    @Override
    public void submitPictureInPictureRenderState(GuiGraphicsExtractor graphics, PictureInPictureRenderState renderState) {
        graphics.guiRenderState.addPicturesInPictureState(renderState);
    }

    @Override
    public ScreenRectangle peekScissorStack(GuiGraphicsExtractor graphics) {
        return graphics.scissorStack.peek();
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
