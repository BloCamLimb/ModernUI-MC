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

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexFormat;
import icyllis.modernui.ModernUI;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.mixin.AccessGameRenderer;
import net.fabricmc.fabric.impl.client.rendering.FabricShaderProgram;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Rarity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

public final class MuiFabricApi extends MuiModApi {

    public MuiFabricApi() {
        ModernUI.LOGGER.info(ModernUI.MARKER, "Created MuiFabricApi");
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
        ((AccessGameRenderer) gr).callLoadEffect(effect);
    }

    @Override
    public ShaderInstance makeShaderInstance(ResourceProvider resourceProvider,
                                             ResourceLocation resourceLocation,
                                             VertexFormat vertexFormat) throws IOException {
        return new FabricShaderProgram(resourceProvider, resourceLocation, vertexFormat);
    }

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
}
