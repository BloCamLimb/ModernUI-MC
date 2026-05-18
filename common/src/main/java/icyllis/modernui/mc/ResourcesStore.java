/*
 * Modern UI.
 * Copyright (C) 2026 BloCamLimb. All rights reserved.
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

import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.resources.AssetManager;
import icyllis.modernui.resources.ResourcesImpl;
import icyllis.modernui.resources.ResourcesLoader;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public class ResourcesStore implements PreparableReloadListener {

    private static final ResourcesStore INSTANCE = new ResourcesStore();

    //TODO this is not implemented in ModernUI fw
    private ResourcesLoader mPackLoader;

    private volatile Map<String, ResourcesLoader> mLoadersByMod = Collections.emptyMap();

    private ResourcesStore() {
    }

    public static ResourcesStore getInstance() {
        return INSTANCE;
    }

    /**
     * Called when resources reloaded.
     */
    @Nonnull
    @Override
    public CompletableFuture<Void> reload(@Nonnull PreparationBarrier preparationBarrier,
                                          @Nonnull ResourceManager resourceManager,
                                          @Nonnull Executor preparationExecutor,
                                          @Nonnull Executor reloadExecutor) {
        final Map<String, ResourcesLoader> oldLoaders = mLoadersByMod;
        return CompletableFuture.supplyAsync(() -> {
                    var listeners = MuiModApi.snapOnUpdateLoaderListeners();
                    var result = new HashMap<String, ResourcesLoader>();

                    for (var e : listeners) {
                        var modid = e.getKey();
                        var newLoader = e.getValue().onUpdateLoader(oldLoaders.get(modid), resourceManager);
                        if (newLoader != null) {
                            result.put(modid, newLoader);
                        }
                    }

                    return result;
                }, preparationExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(r -> {
                    if (r.equals(oldLoaders)) {
                        return;
                    }
                    mLoadersByMod = r;
                    Handler handler = Core.getUiHandlerAsync();
                    if (handler != null) {
                        handler.post(() -> {

                            var ab = new AssetManager.Builder();
                            r.values().forEach(ab::addLoader);

                            var resources = ModernUI.getInstance().getResources();

                            resources.getImpl().clearAllCaches();
                            resources.setImpl(
                                    new ResourcesImpl(ab.build(), resources.getDisplayMetrics(), resources.getConfiguration())
                            );

                            Config.CLIENT.applyTheme(true);
                        });
                    }
                }, reloadExecutor);
    }
}
