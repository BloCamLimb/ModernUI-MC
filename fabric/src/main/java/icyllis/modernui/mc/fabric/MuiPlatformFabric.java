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

package icyllis.modernui.mc.fabric;

import icyllis.modernui.ModernUI;
import icyllis.modernui.mc.Config;
import icyllis.modernui.mc.MuiPlatform;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class MuiPlatformFabric extends MuiPlatform {

    // this creates config folder
    public static final Path BOOTSTRAP_PATH;

    static {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(ModernUI.NAME_CPT);
        if (!Files.isDirectory(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        BOOTSTRAP_PATH = path.resolve("bootstrap.properties");
    }

    @Override
    public Path getBootstrapPath() {
        return BOOTSTRAP_PATH;
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public Map<String, Config.ConfigItem<?>> getConfigMap(int type) {
        return ConfigImpl.getConfigMap(type);
    }

    @Override
    public void saveConfig(int type) {
        ConfigImpl.saveConfig(type);
    }
}
