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

package icyllis.modernui.mc.forge;

import icyllis.modernui.ModernUI;
import icyllis.modernui.mc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.fml.loading.FMLEnvironment;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

import static icyllis.modernui.ModernUI.*;

/**
 * Mod class. INTERNAL.
 *
 * @author BloCamLimb
 */
@Mod(ModernUI.ID)
public final class ModernUIForge extends ModernUIMod {

    //public static final int BOOTSTRAP_ENABLE_DEBUG_INJECTORS = 0x4;

    //static volatile boolean sInterceptTipTheScales;

    //public static boolean sRemoveMessageSignature;
    //public static boolean sSecureProfilePublicKey;

    private static final Map<String, IEventBus> sModEventBuses = new HashMap<>();

    // mod-loading thread
    public ModernUIForge() {
        if (!FMLEnvironment.production) {
            ModernUIMod.sDevelopment = true;
            LOGGER.debug(MARKER, "Auto detected in FML development environment");
        } else if (ModernUI.class.getSigners() == null) {
            LOGGER.warn(MARKER, "Signature is missing");
        }

        // TipTheScales doesn't work with OptiFine
        if (ModList.get().isLoaded("tipthescales") && !ModernUIMod.sOptiFineLoaded) {
            //sInterceptTipTheScales = true;
            LOGGER.fatal(MARKER, "Detected TipTheScales without OptiFine");
            throw new UnsupportedOperationException("Please remove TipTheScales, Modern UI can do everything it can, " +
                    "and Modern UI is also compatible with OptiFine");
        }
        if (ModList.get().isLoaded("reblured")) {
            LOGGER.fatal(MARKER, "Detected ReBlurred");
            throw new UnsupportedOperationException("Please remove ReBlurred, Modern UI can do everything it can, " +
                    "and Modern UI has better performance than it");
        }

        if (FMLEnvironment.dist.isClient()) {
            Config.initClientConfig(
                    spec -> ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, spec,
                            ModernUI.NAME_CPT + "/client.toml")
            );
        }
        Config.initCommonConfig(
                spec -> ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, spec,
                        ModernUI.NAME_CPT + "/common.toml")
        );
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (Consumer<ModConfigEvent>) event -> Config.reload(event.getConfig())
        );
        LocalStorage.init();

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> Loader::init);

        /*if ((getBootstrapLevel() & BOOTSTRAP_ENABLE_DEBUG_INJECTORS) != 0) {
            MinecraftForge.EVENT_BUS.register(EventHandler.ClientDebug.class);
            LOGGER.debug(MARKER, "Enable Modern UI debug injectors");
        }*/

        if (ModernUIMod.sDevelopment) {
            ModList.get().forEachModContainer((modid, container) -> {
                if (container instanceof FMLModContainer) {
                    final String namespace = container.getNamespace();
                    if (!namespace.equals("forge")) {
                        sModEventBuses.put(namespace, ((FMLModContainer) container).getEventBus());
                    }
                }
            });
        }

        LOGGER.info(MARKER, "Initialized Modern UI");
    }

    /*public static void warnSetup(String key, Object... args) {
        ModLoader.get().addWarning(new ModLoadingWarning(null, ModLoadingStage.SIDED_SETUP, key, args));
    }*/

    @SuppressWarnings("UnusedReturnValue")
    public static <E extends Event & IModBusEvent> boolean post(@Nullable String ns, @Nonnull E e) {
        if (ns == null) {
            boolean handled = false;
            for (IEventBus bus : sModEventBuses.values()) {
                handled |= bus.post(e);
            }
            return handled;
        } else {
            IEventBus bus = sModEventBuses.get(ns);
            return bus != null && bus.post(e);
        }
    }

    private static class Loader {

        @SuppressWarnings("resource")
        public static void init() {
            new Client();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Client extends ModernUIClient {

        static {
            assert FMLEnvironment.dist.isClient();
        }

        private Client() {
            super();
            if (isTextEngineEnabled()) {
                ModernUIText.init();
                Config.initTextConfig(
                        spec -> ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, spec,
                                ModernUI.NAME_CPT + "/text.toml")
                );
                LOGGER.info(MARKER, "Initialized Modern UI text engine");
            }
            if (ModernUIMod.sDevelopment) {
                FMLJavaModLoadingContext.get().getModEventBus().register(Registration.ModClientDev.class);
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
                return languageManager.getJavaLocale();
            }
            return super.onGetSelectedLocale();
        }
    }
}
