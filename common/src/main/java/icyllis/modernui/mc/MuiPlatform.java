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

package icyllis.modernui.mc;

import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Service interface, common only. This is loaded in MixinConfigPlugin.
 */
@ApiStatus.Internal
public abstract class MuiPlatform {

    private static final MuiPlatform INSTANCE = ServiceLoader.load(MuiPlatform.class).findFirst()
            .orElseThrow();

    @Nonnull
    public static MuiPlatform get() {
        return INSTANCE;
    }

    public abstract Path getBootstrapPath();

    public abstract boolean isClient();

    public abstract Map<String, Config.ConfigItem<?>> getConfigMap(int type);

    public abstract void saveConfig(int type);
}
