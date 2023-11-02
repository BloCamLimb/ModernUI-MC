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

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.graphics.ImageStore;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.text.MuiTextCommand;
import icyllis.modernui.mc.text.TextLayoutEngine;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.config.ModConfig;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Environment(EnvType.CLIENT)
public class ModernUIFabricClient extends ModernUIClient implements ClientModInitializer {

    public static final Event<Runnable> START_RENDER_TICK = EventFactory.createArrayBacked(Runnable.class,
            callbacks -> () -> {
                for (Runnable runnable : callbacks) {
                    runnable.run();
                }
            });

    public static final Event<Runnable> END_RENDER_TICK = EventFactory.createArrayBacked(Runnable.class,
            callbacks -> () -> {
                for (Runnable runnable : callbacks) {
                    runnable.run();
                }
            });

    private String mSelectedLanguageCode;
    private Locale mSelectedJavaLocale;

    public ModernUIFabricClient() {
        super();
    }

    @Override
    protected void checkFirstLoadTypeface() {
        // No-op, on Fabric, this can only be loaded on main thread...
    }

    @Override
    public void onInitializeClient() {
        START_RENDER_TICK.register(EventHandler.Client::onRenderTick);
        END_RENDER_TICK.register(EventHandler.Client::onRenderTick);

        KeyBindingHelper.registerKeyBinding(UIManagerFabric.OPEN_CENTER_KEY);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ModernUIMod.location("client");
            }

            @Override
            public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
                ImageStore.getInstance().clear();
                Handler handler = Core.getUiHandlerAsync();
                // FML may throw ex, so it can be null
                if (handler != null) {
                    // Call in lambda, not in creating the lambda
                    handler.post(() -> UIManager.getInstance().updateLayoutDir(Config.CLIENT.mForceRtl.get()));
                }
            }
        });

        ModConfigEvents.loading(ID).register(Config::reloadAnyClient);
        ModConfigEvents.reloading(ID).register(Config::reloadAnyClient);

        ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> {
            UIManagerFabric.initializeRenderer();
            var windowMode = Config.CLIENT.mLastWindowMode;
            if (windowMode == Config.Client.WindowMode.FULLSCREEN_BORDERLESS) {
                // ensure it's applied and positioned
                windowMode.apply();
            }
        });

        Config.initClientConfig(
                spec -> ForgeConfigRegistry.INSTANCE.register(ID, ModConfig.Type.CLIENT, spec,
                        ModernUI.NAME_CPT + "/client.toml")
        );
        Config.initTextConfig(
                spec -> ForgeConfigRegistry.INSTANCE.register(ID, ModConfig.Type.CLIENT, spec,
                        ModernUI.NAME_CPT + "/text.toml")
        );

        if (isTextEngineEnabled()) {
            ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> {
                MuiModApi.addOnWindowResizeListener(TextLayoutEngine.getInstance());
            });

            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                MuiTextCommand.register(dispatcher);
            });

            new TextLayoutEngine();

            MuiModApi.addOnDebugDumpListener(TextLayoutEngine.getInstance());

            ClientTickEvents.END_CLIENT_TICK.register((mc) -> TextLayoutEngine.getInstance().onEndClientTick());

            LOGGER.info(MARKER, "Initialized Modern UI text engine");
        } else {
            // see MixinFontManager in another case
            ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() {
                    return ModernUIMod.location("font");
                }

                @Nonnull
                @Override
                public CompletableFuture<Void> reload(@Nonnull PreparationBarrier preparationBarrier,
                                                      @Nonnull ResourceManager resourceManager,
                                                      @Nonnull ProfilerFiller preparationProfiler,
                                                      @Nonnull ProfilerFiller reloadProfiler,
                                                      @Nonnull Executor preparationExecutor,
                                                      @Nonnull Executor reloadExecutor) {
                    return FontResourceManager.getInstance().reload(
                            preparationBarrier,
                            resourceManager,
                            preparationProfiler,
                            reloadProfiler,
                            preparationExecutor,
                            reloadExecutor
                    );
                }
            });

            new FontResourceManager();
        }
        LOGGER.info(MARKER, "Initialized Modern UI client");
    }

    @SuppressWarnings("ConstantValue")
    @Nonnull
    @Override
    protected Locale onGetSelectedLocale() {
        // Minecraft can be null if we're running DataGen
        // LanguageManager can be null if this method is being called too early
        Minecraft minecraft;
        LanguageManager languageManager;
        if ((minecraft = Minecraft.getInstance()) != null &&
                (languageManager = minecraft.getLanguageManager()) != null) {
            String languageCode = languageManager.getSelected();
            if (!languageCode.equals(mSelectedLanguageCode)) {
                mSelectedLanguageCode = languageCode;
                String[] langSplit = languageCode.split("_", 2);
                mSelectedJavaLocale = langSplit.length == 1
                        ? new Locale(langSplit[0])
                        : new Locale(langSplit[0], langSplit[1]);
            }
            return mSelectedJavaLocale;
        }
        return super.onGetSelectedLocale();
    }
}
