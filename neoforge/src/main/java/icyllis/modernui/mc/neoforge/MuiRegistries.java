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

import icyllis.modernui.mc.ModernUIMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

@SuppressWarnings("unused")
public final class MuiRegistries {

    /**
     * Sounds (Client only, no registration)
     */
    public static final SoundEvent BUTTON_CLICK_1 = SoundEvent.createVariableRangeEvent(ModernUIMod.location("button1"));
    public static final SoundEvent BUTTON_CLICK_2 = SoundEvent.createVariableRangeEvent(ModernUIMod.location("button2"));

    /**
     * Container Menus (Development only)
     */
    public static final ResourceLocation
            TEST_MENU_KEY = ModernUIMod.location("test");
    public static final DeferredHolder<MenuType<?>, MenuType<TestContainerMenu>> TEST_MENU = DeferredHolder.create(
            Registries.MENU, TEST_MENU_KEY);

    /**
     * Items (Development only)
     */
    public static final ResourceLocation
            PROJECT_BUILDER_ITEM_KEY = ModernUIMod.location("project_builder");
    public static final DeferredHolder<Item, Item> PROJECT_BUILDER_ITEM = DeferredHolder.create(
            Registries.ITEM, PROJECT_BUILDER_ITEM_KEY);
}
