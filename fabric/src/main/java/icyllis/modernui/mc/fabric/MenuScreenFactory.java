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

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nonnull;
import java.util.Objects;

@FunctionalInterface
public interface MenuScreenFactory<T extends AbstractContainerMenu> extends
        MenuScreens.ScreenConstructor<T, AbstractContainerScreen<T>> {

    /**
     * Helper method that down-casts the screen factory.
     *
     * @param factory the factory
     * @param <T>     the menu type
     * @return the factory
     */
    static <T extends AbstractContainerMenu> MenuScreenFactory<T> create(MenuScreenFactory<T> factory) {
        return factory;
    }

    @Nonnull
    @Override
    default AbstractContainerScreen<T> create(@Nonnull T menu,
                                              @Nonnull Inventory inventory,
                                              @Nonnull Component title) {
        return new MenuScreen<>(UIManager.getInstance(),
                Objects.requireNonNullElseGet(createFragment(menu), Fragment::new),
                menu,
                inventory,
                title);
    }

    /**
     * Creates a new {@link Fragment} for the given menu. This method is called on the main thread.
     * <p>
     * Specially, the main {@link Fragment} subclass can implement {@link ICapabilityProvider}
     * to provide capabilities, some of which may be internally handled by the framework.
     * For example, {@link ScreenCallback} to describe the screen properties.
     * <p>
     * Note: You should not interact player inventory or block container via the Fragment.
     * Instead, use {@link T ContainerMenu} and {@link ContainerMenuView}.
     *
     * @param menu the container menu
     * @return the main fragment
     */
    @Nonnull
    Fragment createFragment(T menu);
}
