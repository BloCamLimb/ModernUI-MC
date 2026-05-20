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

package icyllis.modernui.mc.neoforge;

import icyllis.modernui.ModernUI;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.ui.CenterFragment2;
import icyllis.modernui.util.DataSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import javax.annotation.Nonnull;
import java.util.Locale;
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

    // mod-loading thread
    public ModernUIForge(IEventBus modEventBus, ModContainer modContainer) {
        if (!FMLEnvironment.isProduction()) {
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

        modContainer.registerConfig(ModConfig.Type.COMMON, ConfigImpl.COMMON_SPEC,
                ModernUI.NAME_CPT + "/common.toml");
        modEventBus.addListener(
                (Consumer<ModConfigEvent>) event -> ConfigImpl.reloadCommon(event.getConfig())
        );

        /*if (FMLEnvironment.dist.isClient()) {
            Loader.init(modEventBus);
        }*/

        /*if ((getBootstrapLevel() & BOOTSTRAP_ENABLE_DEBUG_INJECTORS) != 0) {
            MinecraftForge.EVENT_BUS.register(EventHandler.ClientDebug.class);
            LOGGER.debug(MARKER, "Enable Modern UI debug injectors");
        }*/

        LOGGER.info(MARKER, "Initialized Modern UI");
    }

    public static void warnSetup(String key, Object... args) {
        ModLoader.addLoadingIssue(ModLoadingIssue.warning(key, args));
    }

    /*private static class Loader {

        @SuppressWarnings("resource")
        public static void init(IEventBus modEventBus) {
            new Client(modEventBus);
        }
    }*/

    @Mod(value = ModernUI.ID, dist = {Dist.CLIENT})
    public static class Client extends ModernUIClient {

        static {
            assert FMLEnvironment.getDist().isClient();
        }

        // mod-loading thread
        public Client(IEventBus modEventBus, ModContainer modContainer) {
            super();
            modContainer.registerConfig(ModConfig.Type.CLIENT, ConfigImpl.CLIENT_SPEC,
                    ModernUI.NAME_CPT + "/client.toml");
            modContainer.registerConfig(ModConfig.Type.CLIENT, ConfigImpl.TEXT_SPEC,
                    ModernUI.NAME_CPT + "/text.toml");
            FontResourceManager.getInstance();
            if (ModernUIMod.isTextEngineEnabled()) {
                ModernUIText.init(modEventBus);
                LOGGER.info(MARKER, "Initialized Modern UI text engine");
            }
            modEventBus.addListener(
                    (Consumer<ModConfigEvent>) event -> ConfigImpl.reloadAnyClient(event.getConfig())
            );
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (mc, modsScreen) -> {
                        var args = new DataSet();
                        args.putBoolean("navigateToPreferences", true);
                        var fragment = new CenterFragment2();
                        fragment.setArguments(args);
                        return MuiForgeApi.get().createScreen(fragment, null, modsScreen);
                    }
            );
            /*if (ModernUIMod.sDevelopment) {
                modEventBus.register(Registration.ModClientDev.class);
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
