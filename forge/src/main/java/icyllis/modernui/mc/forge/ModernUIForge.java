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

import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.modernui.ModernUI;
import icyllis.modernui.graphics.text.*;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.view.WindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.util.*;

import static icyllis.modernui.ModernUI.*;

/**
 * Mod class. INTERNAL.
 *
 * @author BloCamLimb
 */
@Mod(ModernUI.ID)
public final class ModernUIForge {

    // false to disable extensions
    public static final String BOOTSTRAP_DISABLE_TEXT_ENGINE = "modernui_mc_disableTextEngine";
    public static final String BOOTSTRAP_DISABLE_SMOOTH_SCROLLING = "modernui_mc_disableSmoothScrolling";
    //public static final int BOOTSTRAP_ENABLE_DEBUG_INJECTORS = 0x4;

    private static final Path BOOTSTRAP_PATH = FMLPaths.getOrCreateGameRelativePath(
            FMLPaths.CONFIGDIR.get().resolve(NAME_CPT)).resolve("bootstrap.properties");

    private static boolean sOptiFineLoaded;

    //static volatile boolean sInterceptTipTheScales;

    static volatile boolean sDevelopment;
    static volatile boolean sDeveloperMode;

    public static boolean sInventoryScreenPausesGame;
    //public static boolean sRemoveMessageSignature;
    public static boolean sRemoveTelemetrySession;
    //public static boolean sSecureProfilePublicKey;
    public static float sFontScale = 1;

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
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static final Map<String, IEventBus> sModEventBuses = new HashMap<>();

    // mod-loading thread
    public ModernUIForge() {
        // get '/run' parent
        Path path = FMLPaths.GAMEDIR.get().getParent();
        // the root directory of your project
        File dir = path.toFile();
        String[] r = dir.list((file, name) -> name.equals("build.gradle"));
        if (r != null && r.length > 0 && dir.getName().equals(NAME_CPT)) {
            sDevelopment = true;
            LOGGER.debug(MARKER, "Auto detected in development environment");
        } else if (!FMLEnvironment.production) {
            sDevelopment = true;
            LOGGER.debug(MARKER, "Auto detected in FML development environment");
        } else if (ModernUI.class.getSigners() == null) {
            LOGGER.warn(MARKER, "Signature is missing");
        }

        // TipTheScales doesn't work with OptiFine
        if (ModList.get().isLoaded("tipthescales") && !sOptiFineLoaded) {
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

        Config.init();
        LocalStorage.init();

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> Loader::init);

        /*if ((getBootstrapLevel() & BOOTSTRAP_ENABLE_DEBUG_INJECTORS) != 0) {
            MinecraftForge.EVENT_BUS.register(EventHandler.ClientDebug.class);
            LOGGER.debug(MARKER, "Enable Modern UI debug injectors");
        }*/

        if (sDevelopment) {
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

    // INTERNAL HOOK
    public static void dispatchOnScroll(double scrollX, double scrollY) {
        for (var l : MuiForgeApi.sOnScrollListeners) {
            l.onScroll(scrollX, scrollY);
        }
    }

    // INTERNAL HOOK
    public static void dispatchOnScreenChange(@Nullable Screen oldScreen, @Nullable Screen newScreen) {
        for (var l : MuiForgeApi.sOnScreenChangeListeners) {
            l.onScreenChange(oldScreen, newScreen);
        }
    }

    // INTERNAL HOOK
    public static void dispatchOnWindowResize(int width, int height, int guiScale, int oldGuiScale) {
        for (var l : MuiForgeApi.sOnWindowResizeListeners) {
            l.onWindowResize(width, height, guiScale, oldGuiScale);
        }
    }

    // INTERNAL HOOK
    public static void dispatchOnDebugDump(@Nonnull PrintWriter writer) {
        for (var l : MuiForgeApi.sOnDebugDumpListeners) {
            l.onDebugDump(writer);
        }
    }

    public static boolean enablesTextEngine() {
        return !Boolean.parseBoolean(
                getBootstrapProperty(BOOTSTRAP_DISABLE_TEXT_ENGINE)
        );
    }

    // INTERNAL
    public static String getBootstrapProperty(String key) {
        Properties props = DistExecutor.safeCallWhenOn(Dist.CLIENT,
                () -> Loader::getBootstrapProperties);
        if (props != null) {
            return props.getProperty(key);
        }
        return null;
    }

    public static void setBootstrapProperty(String key, String value) {
        Properties props = DistExecutor.safeCallWhenOn(Dist.CLIENT,
                () -> Loader::getBootstrapProperties);
        if (props != null) {
            props.setProperty(key, value);
            try {
                props.store(Files.newOutputStream(BOOTSTRAP_PATH,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING),
                        "Modern UI bootstrap file");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Nonnull
    public static ResourceLocation location(String path) {
        return new ResourceLocation(ID, path);
    }

    /*public static void warnSetup(String key, Object... args) {
        ModLoader.get().addWarning(new ModLoadingWarning(null, ModLoadingStage.SIDED_SETUP, key, args));
    }*/

    private static void loadFonts(@Nonnull Collection<String> configs, @Nonnull Set<FontFamily> selected) {
        boolean hasFail = false;
        for (String cfg : configs) {
            if (StringUtils.isEmpty(cfg)) {
                continue;
            }
            try {
                try (InputStream inputStream = Minecraft.getInstance().getResourceManager()
                        .open(new ResourceLocation(cfg))) {
                    FontFamily f = FontFamily.createFamily(inputStream, true);
                    selected.add(f);
                    LOGGER.debug(MARKER, "Font '{}' was loaded with config value '{}' as RESOURCE PACK",
                            f.getFamilyName(), cfg);
                    continue;
                }
            } catch (Exception ignored) {
            }
            try {
                FontFamily f = FontFamily.createFamily(new File(
                        cfg.replaceAll("\\\\", "/")), true);
                selected.add(f);
                LOGGER.debug(MARKER, "Font '{}' was loaded with config value '{}' as LOCAL FILE",
                        f.getFamilyName(), cfg);
                continue;
            } catch (Exception ignored) {
            }
            FontFamily family = FontFamily.getSystemFontWithAlias(cfg);
            if (family == null) {
                Optional<FontFamily> optional = FontFamily.getSystemFontMap().values().stream()
                        .filter(f -> f.getFamilyName().equalsIgnoreCase(cfg))
                        .findFirst();
                if (optional.isPresent()) {
                    family = optional.get();
                }
            }
            if (family != null) {
                selected.add(family);
                LOGGER.debug(MARKER, "Font '{}' was loaded with config value '{}' as SYSTEM FONT",
                        family.getFamilyName(), cfg);
                continue;
            }
            hasFail = true;
            LOGGER.info(MARKER, "Font '{}' failed to load or invalid", cfg);
        }
        if (hasFail && isDeveloperMode()) {
            LOGGER.debug(MARKER, "Available system font families: {}",
                    String.join(",", FontFamily.getSystemFontMap().keySet()));
        }
    }

    public static boolean isDeveloperMode() {
        return sDeveloperMode || sDevelopment;
    }

    public static boolean isOptiFineLoaded() {
        return sOptiFineLoaded;
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
        public static void init() {
            new Client();
        }

        private static volatile boolean sBootstrap;

        public static Properties getBootstrapProperties() {
            if (!sBootstrap) {
                synchronized (ModernUIForge.class) {
                    if (!sBootstrap) {
                        try {
                            if (Files.exists(BOOTSTRAP_PATH)) {
                                Client.props.load(
                                        Files.newInputStream(BOOTSTRAP_PATH, StandardOpenOption.READ)
                                );
                            } else {
                                Files.createFile(BOOTSTRAP_PATH);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        sBootstrap = true;
                    }
                }
            }
            return Client.props;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Client extends ModernUI {

        private static volatile Client sInstance;

        static {
            assert FMLEnvironment.dist.isClient();
        }

        private volatile Typeface mTypeface;

        private Client() {
            super();
            sInstance = this;
            if (!Boolean.parseBoolean(
                    Loader.getBootstrapProperties().getProperty(BOOTSTRAP_DISABLE_TEXT_ENGINE)
            )) {
                ModernUIText.init();
                LOGGER.info(MARKER, "Initialized Modern UI text engine");
            }
            ModernUIText.initConfig();
            if (sDevelopment) {
                FMLJavaModLoadingContext.get().getModEventBus().register(Registration.ModClientDev.class);
            }
            LOGGER.info(MARKER, "Initialized Modern UI client");
        }

        public static Client getInstance() {
            return sInstance;
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

        @Nonnull
        @Override
        protected Typeface onGetSelectedTypeface() {
            if (mTypeface != null) {
                return mTypeface;
            }
            synchronized (this) {
                // should be a worker thread
                if (mTypeface == null) {
                    if (RenderSystem.isOnRenderThread() || Minecraft.getInstance().isSameThread()) {
                        LOGGER.error(MARKER,
                                "Loading typeface on the render thread, but it should be on a worker thread.\n"
                                        + "Don't report to Modern UI, but to other mods as displayed in stack trace.",
                                new Exception("Loading typeface at the wrong mod loading stage")
                                        .fillInStackTrace());
                    }
                    Set<FontFamily> set = new LinkedHashSet<>();
                    List<? extends String> configs = Config.CLIENT.mFontFamily.get();
                    if (configs != null) {
                        loadFonts(new LinkedHashSet<>(configs), set);
                    }
                    mTypeface = Typeface.createTypeface(set.toArray(new FontFamily[0]));
                    // do some warm-up, but do not block ourselves
                    var paint = new FontPaint();
                    paint.setFont(mTypeface);
                    paint.setLocale(Locale.ROOT);
                    paint.setFontSize(12);
                    Minecraft.getInstance().tell(() -> LayoutCache.getOrCreate(new char[]{'M'},
                            0, 1, 0, 1, false, paint, 0));
                    LOGGER.info(MARKER, "Loaded typeface: {}", mTypeface);
                }
            }
            return mTypeface;
        }

        @Nonnull
        @Override
        public InputStream getResourceStream(@Nonnull String res, @Nonnull String path) throws IOException {
            return Minecraft.getInstance().getResourceManager().open(new ResourceLocation(res, path));
        }

        @Nonnull
        @Override
        public ReadableByteChannel getResourceChannel(@Nonnull String res, @Nonnull String path) throws IOException {
            return Channels.newChannel(getResourceStream(res, path));
        }

        @Override
        public WindowManager getWindowManager() {
            return UIManager.getInstance().getDecorView();
        }
    }
}
