/*
 * Modern UI.
 * Copyright (C) 2019-2025 BloCamLimb. All rights reserved.
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
import icyllis.modernui.util.DataSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
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
    public ModernUIForge(FMLJavaModLoadingContext context) {
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
            warnSetup("You should remove TipTheScales, Modern UI already includes its features, " +
                    "and Modern UI is also compatible with OptiFine");
        }
        if (ModList.get().isLoaded("reblured")) {
            LOGGER.fatal(MARKER, "Detected ReBlurred");
            warnSetup("You should remove ReBlurred, Modern UI already includes its features, " +
                    "and Modern UI has better performance than it");
        }
        sLegendaryTooltipsLoaded = ModList.get().isLoaded("legendarytooltips");
        sUntranslatedItemsLoaded = ModList.get().isLoaded("untranslateditems");

        Config.initCommonConfig(
                spec -> context.registerConfig(ModConfig.Type.COMMON, spec,
                        ModernUI.NAME_CPT + "/common.toml")
        );
        context.getModEventBus().addListener(
                (Consumer<ModConfigEvent>) event -> Config.reloadCommon(event.getConfig())
        );
        LocalStorage.init();

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> Loader.init(context));

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

    public static void warnSetup(String key, Object... args) {
        ModLoader.get().addWarning(new ModLoadingWarning(null, ModLoadingStage.SIDED_SETUP, key, args));
    }

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
        public static void init(FMLJavaModLoadingContext context) {
            new Client(context);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Client extends ModernUIClient {

        static {
            assert FMLEnvironment.dist.isClient();
        }

        private Client(FMLJavaModLoadingContext context) {
            super();
            Config.initClientConfig(
                    spec -> context.registerConfig(ModConfig.Type.CLIENT, spec,
                            ModernUI.NAME_CPT + "/client.toml")
            );
            Config.initTextConfig(
                    spec -> context.registerConfig(ModConfig.Type.CLIENT, spec,
                            ModernUI.NAME_CPT + "/text.toml")
            );
            FontResourceManager.getInstance();
            if (ModernUIMod.isTextEngineEnabled()) {
                ModernUIText.init(context);
                LOGGER.info(MARKER, "Initialized Modern UI text engine");
            }
            context.getModEventBus().addListener(
                    (Consumer<ModConfigEvent>) event -> Config.reloadAnyClient(event.getConfig())
            );
            context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (mc, modsScreen) -> {
                                var args = new DataSet();
                                args.putBoolean("navigateToPreferences", true);
                                var fragment = new CenterFragment2();
                                fragment.setArguments(args);
                                return MuiForgeApi.get().createScreen(fragment, null, modsScreen);
                            }
                    ));
            /*if (ModernUIMod.sDevelopment) {
                context.getModEventBus().register(Registration.ModClientDev.class);
            }*/
            LOGGER.info(MARKER, "Initialized Modern UI client");
        }

        /*@Override
        protected void checkFirstLoadTypeface() {
            if (RenderSystem.isOnRenderThread() || Minecraft.getInstance().isSameThread()) {
                LOGGER.error(MARKER,
                        "Loading typeface on the render thread, but it should be on a worker thread.\n"
                                + "Don't report to Modern UI, but to other mods as displayed in stack trace.",
                        new Exception("Loading typeface at the wrong mod loading stage")
                                .fillInStackTrace());
            }
        }*/

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
