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

import icyllis.modernui.mc.ConfigItem;
import icyllis.modernui.mc.MuiPlatform;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Map;

import static icyllis.modernui.ModernUI.NAME_CPT;

public final class MuiPlatformForge extends MuiPlatform {

    // this creates config folder
    public static final Path BOOTSTRAP_PATH = FMLPaths.getOrCreateGameRelativePath(
            FMLPaths.CONFIGDIR.get().resolve(NAME_CPT)).resolve("bootstrap.properties");

    @Override
    public Path getBootstrapPath() {
        return BOOTSTRAP_PATH;
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.getDist().isClient();
    }

    @Override
    public Map<String, ConfigItem<?>> getConfigMap(int type) {
        return ConfigImpl.getConfigMap(type);
    }

    @Override
    public void saveConfig(int type) {
        ConfigImpl.saveConfig(type);
    }
}
