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
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.client.blaze3d.validation.ValidationGpuDevice;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MuiForgeApi extends MuiModApi {

    public MuiForgeApi() {
        ModernUIMod.LOGGER.info(ModernUIMod.MARKER, "Created MuiForgeAPI");
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
        try {
            if (net.neoforged.fml.loading.FMLConfig.getBoolConfigValue(net.neoforged.fml.loading.FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
                // Newer FancyModLoader no longer promotes GL version, we must disable early window control.
                // However, when Arc3D is updated, or we update to Minecraft 1.21.9, this will no longer be necessary.
                // Ideally we can use GraphicsBootstrapper to avoid the first boot crash, but that requires a service layer.
                net.neoforged.fml.loading.FMLConfig.updateConfig(net.neoforged.fml.loading.FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, Boolean.FALSE);
                ModernUIMod.LOGGER.warn(ModernUIMod.MARKER, "Disable early window control to promote GL version, you need to launch the game again!");
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public void loadEffect(GameRenderer gr, ResourceLocation effect) {
        gr.setPostEffect(effect);
    }

    /*@Override
    public ShaderInstance makeShaderInstance(ResourceProvider resourceProvider,
                                             ResourceLocation resourceLocation,
                                             VertexFormat vertexFormat) throws IOException {
        return new ShaderInstance(resourceProvider, resourceLocation, vertexFormat);
    }*/

    @Override
    public boolean isKeyBindingMatches(KeyMapping keyMapping, InputConstants.Key key) {
        return keyMapping.isActiveAndMatches(key);
    }

    @Override
    public Style applyRarityTo(Rarity rarity, Style baseStyle) {
        return rarity.getStyleModifier().apply(baseStyle);
    }

    @Override
    public GpuDevice getRealGpuDevice() {
        GpuDevice gpuDevice = RenderSystem.getDevice();
        // The ValidationGpuDevice prevents you from creating external textures, that's terrible
        if (gpuDevice instanceof ValidationGpuDevice validationGpuDevice) {
            gpuDevice = validationGpuDevice.getRealDevice();
        }
        return gpuDevice;
    }

    @Override
    public void submitGuiElementRenderState(GuiGraphics graphics, GuiElementRenderState renderState) {
        graphics.submitGuiElementRenderState(renderState);
    }

    @Override
    public void submitPictureInPictureRenderState(GuiGraphics graphics, PictureInPictureRenderState renderState) {
        graphics.submitPictureInPictureRenderState(renderState);
    }

    @Nullable
    @Override
    public ScreenRectangle peekScissorStack(GuiGraphics graphics) {
        return graphics.peekScissorStack();
    }
}
