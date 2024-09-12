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

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import icyllis.modernui.ModernUI;
import icyllis.modernui.mc.ModernUIMod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.fml.config.ModConfig;

import static icyllis.modernui.ModernUI.*;

public class ModernUIFabric extends ModernUIMod implements ModInitializer {

    // main thread
    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            ModernUIMod.sDevelopment = true;
            LOGGER.debug(MARKER, "Auto detected in Fabric development environment");
        } else if (ModernUI.class.getSigners() == null) {
            LOGGER.warn(MARKER, "Signature is missing");
        }

        sLegendaryTooltipsLoaded = FabricLoader.getInstance().isModLoaded("legendarytooltips");
        sUntranslatedItemsLoaded = FabricLoader.getInstance().isModLoaded("untranslateditems");

        NeoForgeModConfigEvents.loading(ID).register(Config::reloadCommon);
        NeoForgeModConfigEvents.reloading(ID).register(Config::reloadCommon);
        Config.initCommonConfig(
                spec -> NeoForgeConfigRegistry.INSTANCE.register(ID, ModConfig.Type.COMMON, spec,
                        ModernUI.NAME_CPT + "/common.toml")
        );

        LOGGER.info(MARKER, "Initialized Modern UI");
    }
}
