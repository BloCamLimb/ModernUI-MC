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

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.mixin.AccessGameRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Rarity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MuiFabricApi extends MuiModApi {

    public MuiFabricApi() {
        ModernUIMod.LOGGER.info(ModernUIMod.MARKER, "Created MuiFabricApi");
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T extends Screen & MuiScreen> T createScreen(@Nonnull Fragment fragment,
                                                         @Nullable ScreenCallback callback,
                                                         @Nullable Screen previousScreen,
                                                         @Nullable CharSequence title) {
        return (T) new SimpleScreen(UIManager.getInstance(),
                fragment, callback, previousScreen, title);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T extends AbstractContainerMenu, U extends Screen & MenuAccess<T> & MuiScreen>
    U createMenuScreen(@Nonnull Fragment fragment,
                       @Nullable ScreenCallback callback,
                       @Nonnull T menu,
                       @Nonnull Inventory inventory,
                       @Nonnull Component title) {
        return (U) new MenuScreen<>(UIManager.getInstance(),
                fragment, callback, menu, inventory, title);
    }

    @Override
    public boolean isGLVersionPromoted() {
        // we are unknown about this
        return false;
    }

    @Override
    public void loadEffect(GameRenderer gr, ResourceLocation effect) {
        ((AccessGameRenderer) gr).invokeSetPostEffect(effect);
    }

    /*@Override
    public ShaderInstance makeShaderInstance(ResourceProvider resourceProvider,
                                             ResourceLocation resourceLocation,
                                             VertexFormat vertexFormat) throws IOException {
        return new FabricShaderProgram(resourceProvider, resourceLocation, vertexFormat);
    }*/

    @Override
    public boolean isKeyBindingMatches(KeyMapping keyMapping, InputConstants.Key key) {
        return key.getType() == InputConstants.Type.KEYSYM
                ? keyMapping.matches(key.getValue(), InputConstants.UNKNOWN.getValue())
                : keyMapping.matches(InputConstants.UNKNOWN.getValue(), key.getValue());
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
    public void submitGuiElementRenderState(GuiGraphics graphics, GuiElementRenderState renderState) {
        graphics.guiRenderState.submitGuiElement(renderState);
    }

    @Override
    public void submitPictureInPictureRenderState(GuiGraphics graphics, PictureInPictureRenderState renderState) {
        graphics.guiRenderState.submitPicturesInPictureState(renderState);
    }

    @Override
    public ScreenRectangle peekScissorStack(GuiGraphics graphics) {
        return graphics.scissorStack.peek();
    }

    @Override
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
    }
}
