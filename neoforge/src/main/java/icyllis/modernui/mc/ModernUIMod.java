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

package icyllis.modernui.mc;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nonnull;
import java.nio.file.Path;

import static icyllis.modernui.ModernUI.*;

public abstract class ModernUIMod {

    // false to disable extensions
    public static final String BOOTSTRAP_DISABLE_TEXT_ENGINE = "modernui_mc_disableTextEngine";
    public static final String BOOTSTRAP_DISABLE_SMOOTH_SCROLLING = "modernui_mc_disableSmoothScrolling";
    public static final String BOOTSTRAP_DISABLE_ENHANCED_TEXT_FIELD = "modernui_mc_disableEnhancedTextField";

    // this creates config folder
    public static final Path BOOTSTRAP_PATH = FMLPaths.getOrCreateGameRelativePath(
            FMLPaths.CONFIGDIR.get().resolve(NAME_CPT)).resolve("bootstrap.properties");

    public static volatile boolean sDevelopment;
    public static volatile boolean sDeveloperMode;

    protected static boolean sOptiFineLoaded;
    protected static boolean sIrisApiLoaded;
    protected static volatile boolean sLegendaryTooltipsLoaded;
    protected static volatile boolean sUntranslatedItemsLoaded;

    static {
        try {
            Class<?> clazz = Class.forName("optifine.Installer");
            sOptiFineLoaded = true;
            try {
                String version = (String) clazz.getMethod("getOptiFineVersion").invoke(null);
                LOGGER.info(MARKER, "OptiFine installed: {}", version);
            } catch (Exception e) {
                LOGGER.info(MARKER, "OptiFine installed...");
            }
        } catch (Exception ignored) {
        }
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            sIrisApiLoaded = true;
            LOGGER.info(MARKER, "Iris API installed...");
        } catch (Exception ignored) {
        }
    }

    @Nonnull
    public static ResourceLocation location(String path) {
        return new ResourceLocation(ID, path);
    }

    public static boolean isOptiFineLoaded() {
        return sOptiFineLoaded;
    }

    public static boolean isIrisApiLoaded() {
        return sIrisApiLoaded;
    }

    public static boolean isLegendaryTooltipsLoaded() {
        return sLegendaryTooltipsLoaded;
    }

    public static boolean isUntranslatedItemsLoaded() {
        return sUntranslatedItemsLoaded;
    }

    public static boolean isDeveloperMode() {
        return sDeveloperMode || sDevelopment;
    }
}
