/*
 * Modern UI.
 * Copyright (C) 2019-2021 BloCamLimb. All rights reserved.
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
import icyllis.arc3d.engine.DriverBugWorkarounds;
import icyllis.modernui.ModernUI;
import icyllis.modernui.graphics.text.*;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.view.WindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

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
    public static final String BOOTSTRAP_DISABLE_ENHANCED_TEXT_FIELD = "modernui_mc_disableEnhancedTextField";
    //public static final int BOOTSTRAP_ENABLE_DEBUG_INJECTORS = 0x4;

    private static final Path BOOTSTRAP_PATH = FMLPaths.getOrCreateGameRelativePath(
            FMLPaths.CONFIGDIR.get().resolve(NAME_CPT), NAME_CPT).resolve("bootstrap.properties");

    private static boolean sOptiFineLoaded;
    private static boolean sIrisApiLoaded;

    static volatile boolean sInterceptTipTheScales;

    static volatile boolean sDevelopment;
    static volatile boolean sDeveloperMode;

    public static boolean sInventoryScreenPausesGame;
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
        } catch (Exception ignored) {
        }
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            sIrisApiLoaded = true;
            LOGGER.info(MARKER, "Iris API installed...");
        } catch (Exception ignored) {
        }
    }

    private static final Map<String, IEventBus> sModEventBuses = new HashMap<>();

    // mod-loading thread
    public ModernUIForge() {
        if (!FMLEnvironment.production) {
            sDevelopment = true;
            LOGGER.debug(MARKER, "Auto detected in FML development environment");
        } else if (ModernUI.class.getSigners() == null) {
            LOGGER.warn(MARKER, "Signature is missing");
        }

        // TipTheScales doesn't work with OptiFine
        if (ModList.get().isLoaded("tipthescales") && !sOptiFineLoaded) {
            sInterceptTipTheScales = true;
            LOGGER.info(MARKER, "Disabled TipTheScales");
        }

        Config.initCommonConfig(
                spec -> ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, spec,
                        ModernUI.NAME_CPT + "/common.toml")
        );
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (Consumer<ModConfigEvent>) event -> Config.reloadCommon(event.getConfig())
        );
        LocalStorage.init();

        // the 'new' method is in another class, so it's class-loading-safe
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> Loader::init);

        /*if ((getBootstrapLevel() & BOOTSTRAP_ENABLE_DEBUG_INJECTORS) != 0) {
            MinecraftForge.EVENT_BUS.register(EventHandler.ClientDebug.class);
        }*/

        ModList.get().forEachModContainer((modid, container) -> {
            if (container instanceof FMLModContainer) {
                final String namespace = container.getNamespace();
                if (!namespace.equals("forge")) {
                    sModEventBuses.put(namespace, ((FMLModContainer) container).getEventBus());
                }
            }
        });

        LOGGER.info(MARKER, "Initialized Modern UI");
    }

    // INTERNAL HOOK
    @OnlyIn(Dist.CLIENT)
    public static void dispatchOnWindowResize(int width, int height, int guiScale, int oldGuiScale) {
        for (var l : MuiForgeApi.sOnWindowResizeListeners) {
            l.onWindowResize(width, height, guiScale, oldGuiScale);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void dispatchOnDebugDump(@Nonnull PrintWriter writer) {
        for (var l : MuiForgeApi.sOnDebugDumpListeners) {
            l.onDebugDump(writer);
        }
    }

    @Nonnull
    public static ResourceLocation location(String path) {
        return new ResourceLocation(ID, path);
    }

    /*public static void warnSetup(String key, Object... args) {
        ModLoader.get().addWarning(new ModLoadingWarning(null, ModLoadingStage.SIDED_SETUP, key, args));
    }*/

    public static boolean isDeveloperMode() {
        return sDeveloperMode || sDevelopment;
    }

    public static boolean isOptiFineLoaded() {
        return sOptiFineLoaded;
    }

    public static boolean isIrisApiLoaded() {
        return sIrisApiLoaded;
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
    }

    @OnlyIn(Dist.CLIENT)
    public static class Client extends ModernUI {

        private static volatile Client sInstance;

        private static volatile boolean sBootstrap;

        private static Properties getBootstrapProperties() {
            if (!sBootstrap) {
                synchronized (Client.class) {
                    if (!sBootstrap) {
                        if (Files.exists(BOOTSTRAP_PATH)) {
                            try (var is = Files.newInputStream(BOOTSTRAP_PATH, StandardOpenOption.READ)) {
                                props.load(is);
                            } catch (IOException e) {
                                LOGGER.error(MARKER, "Failed to load bootstrap file", e);
                            }
                        } else {
                            try {
                                Files.createFile(BOOTSTRAP_PATH);
                            } catch (IOException e) {
                                LOGGER.error(MARKER, "Failed to create bootstrap file", e);
                            }
                        }
                        sBootstrap = true;
                    }
                }
            }
            return props;
        }

        private volatile Typeface mTypeface;
        private volatile FontFamily mFirstFontFamily;

        private Client() {
            super();
            sInstance = this;
            Config.initClientConfig(
                    spec -> ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, spec,
                            ModernUI.NAME_CPT + "/client.toml")
            );
            Config.initTextConfig(
                    spec -> ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, spec,
                            ModernUI.NAME_CPT + "/text.toml")
            );
            FontResourceManager.getInstance();
            if (isTextEngineEnabled()) {
                ModernUIText.init();
                LOGGER.info(MARKER, "Initialized Modern UI text engine");
            }
            FMLJavaModLoadingContext.get().getModEventBus().addListener(
                    (Consumer<ModConfigEvent>) event -> Config.reloadAnyClient(event.getConfig())
            );
            if (sDevelopment) {
                FMLJavaModLoadingContext.get().getModEventBus().register(Registration.ModClientDev.class);
            }
            LOGGER.info(MARKER, "Initialized Modern UI client");
        }

        public static Client getInstance() {
            return sInstance;
        }

        public static boolean isTextEngineEnabled() {
            return !Boolean.parseBoolean(
                    getBootstrapProperty(BOOTSTRAP_DISABLE_TEXT_ENGINE)
            );
        }

        public static boolean areShadersEnabled() {
            if (isOptiFineLoaded()) {
                if (OptiFineIntegration.isShaderPackLoaded()) {
                    return true;
                }
            }
            if (isIrisApiLoaded()) {
                return IrisApiIntegration.isShaderPackInUse();
            }
            return false;
        }

        public static String getBootstrapProperty(String key) {
            Properties props = getBootstrapProperties();
            if (props != null) {
                return props.getProperty(key);
            }
            return null;
        }

        public static void setBootstrapProperty(String key, String value) {
            Properties props = getBootstrapProperties();
            if (props != null) {
                props.setProperty(key, value);
                try (var os = Files.newOutputStream(BOOTSTRAP_PATH,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    props.store(os, "Modern UI bootstrap file");
                } catch (IOException e) {
                    LOGGER.error(MARKER, "Failed to write bootstrap file", e);
                }
            }
        }

        @Nullable
        public static DriverBugWorkarounds getGpuDriverBugWorkarounds() {
            Properties props = getBootstrapProperties();
            if (props != null) {
                Map<String, Boolean> map = new HashMap<>();
                props.forEach((k, v) -> {
                    if (k instanceof String key && v instanceof String value) {
                        Boolean state;
                        if ("true".equalsIgnoreCase(value)) {
                            state = Boolean.TRUE;
                        } else if ("false".equalsIgnoreCase(value)) {
                            state = Boolean.FALSE;
                        } else {
                            return;
                        }
                        if (key.startsWith("arc3d_driverBugWorkarounds_")) { // <- length == 27
                            map.put(key.substring(27), state);
                        }
                    }
                });
                if (!map.isEmpty()) {
                    return new DriverBugWorkarounds(map);
                }
            }
            return null;
        }

        public static void loadFonts(String first,
                                     @Nonnull Collection<String> fallbacks,
                                     @Nonnull Set<FontFamily> selected,
                                     @Nonnull Consumer<FontFamily> firstSetter,
                                     boolean firstLoad) {
            if (firstLoad) {
                var resources = Minecraft.getInstance().getResourceManager();
                for (var location : resources.listResources("font",
                        res -> res.endsWith(".ttf") ||
                                res.endsWith(".otf") ||
                                res.endsWith(".ttc") ||
                                res.endsWith(".otc"))) {
                    if (!location.getNamespace().equals(ModernUI.ID)) {
                        continue;
                    }
                    try {
                        for (var resource : resources.getResources(location)) {
                            try (var inputStream = resource.getInputStream()) {
                                FontFamily f = FontFamily.createFamily(inputStream, /*register*/true);
                                LOGGER.debug(MARKER, "Registered font '{}', location '{}'",
                                        f.getFamilyName(), location);
                            } catch (Exception e) {
                                LOGGER.error(MARKER, "Failed to load font '{}'",
                                        location);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error(MARKER, "Failed to load font '{}",
                                location);
                    }
                }
            }
            boolean hasFail = loadSingleFont(first, selected, firstSetter);
            for (String fallback : fallbacks) {
                hasFail |= loadSingleFont(fallback, selected, null);
            }
            if (hasFail && isDeveloperMode()) {
                LOGGER.debug(MARKER, "Available system font families: {}",
                        String.join(",", FontFamily.getSystemFontMap().keySet()));
            }
        }

        private static boolean loadSingleFont(String value,
                                              @Nonnull Set<FontFamily> selected,
                                              @Nullable Consumer<FontFamily> firstSetter) {
            if (StringUtils.isEmpty(value)) {
                return true;
            }
            if (firstSetter != null) {
                try {
                    FontFamily f = FontFamily.createFamily(new File(
                            value.replaceAll("\\\\", "/")), /*register*/false);
                    selected.add(f);
                    LOGGER.debug(MARKER, "Font '{}' was loaded with config value '{}' as LOCAL FILE",
                            f.getFamilyName(), value);
                    firstSetter.accept(f);
                    return true;
                } catch (Exception ignored) {
                }
            }
            FontFamily family = FontFamily.getSystemFontWithAlias(value);
            if (family == null) {
                Optional<FontFamily> optional = FontFamily.getSystemFontMap().values().stream()
                        .filter(f -> f.getFamilyName().equalsIgnoreCase(value))
                        .findFirst();
                if (optional.isPresent()) {
                    family = optional.get();
                }
            }
            if (family != null) {
                selected.add(family);
                LOGGER.debug(MARKER, "Font '{}' was loaded with config value '{}' as SYSTEM FONT",
                        family.getFamilyName(), value);
                if (firstSetter != null) {
                    firstSetter.accept(family);
                }
                return true;
            }
            LOGGER.info(MARKER, "Font '{}' failed to load or invalid", value);
            return false;
        }

        @Nullable
        public FontFamily getFirstFontFamily() {
            return mFirstFontFamily;
        }

        private void setFirstFontFamily(FontFamily firstFontFamily) {
            mFirstFontFamily = firstFontFamily;
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
                return languageManager.getSelected().getJavaLocale();
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
                    mTypeface = loadTypefaceInternal(this::setFirstFontFamily, true);
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

        // reload just Typeface on main thread, called after loaded
        public void reloadTypeface() {
            synchronized (this) {
                boolean firstLoad = mTypeface == null;
                mFirstFontFamily = null;
                mTypeface = loadTypefaceInternal(this::setFirstFontFamily, firstLoad);
                LOGGER.info(MARKER, "{} typeface: {}", firstLoad ? "Loaded" : "Reloaded", mTypeface);
            }
        }

        public void reloadFontStrike() {
            Minecraft.getInstance().submit(
                    () -> FontResourceManager.getInstance().reloadAll());
        }

        @Nonnull
        private static Typeface loadTypefaceInternal(
                @Nonnull Consumer<FontFamily> firstSetter,
                boolean firstLoad) {
            Set<FontFamily> families = new LinkedHashSet<>();
            if (Config.CLIENT.mUseColorEmoji.get()) {
                var emojiFont = FontResourceManager.getInstance().getEmojiFont();
                if (emojiFont != null) {
                    var colorEmojiFamily = new FontFamily(
                            emojiFont
                    );
                    families.add(colorEmojiFamily);
                }
            }
            String first = Config.CLIENT.mFirstFontFamily.get();
            List<? extends String> configs = Config.CLIENT.mFallbackFontFamilyList.get();
            if (first != null || configs != null) {
                var fallbacks = new LinkedHashSet<String>();
                if (configs != null) {
                    fallbacks.addAll(configs);
                }
                if (first != null) {
                    fallbacks.remove(first);
                }
                loadFonts(first, fallbacks, families,
                        firstSetter, firstLoad);
            }
            return Typeface.createTypeface(families.toArray(new FontFamily[0]));
        }

        @Nonnull
        @Override
        public InputStream getResourceStream(@Nonnull String namespace, @Nonnull String path) throws IOException {
            return Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(namespace, path)).getInputStream();
        }

        @Nonnull
        @Override
        public ReadableByteChannel getResourceChannel(@Nonnull String namespace, @Nonnull String path) throws IOException {
            return Channels.newChannel(getResourceStream(namespace, path));
        }

        @Override
        public WindowManager getWindowManager() {
            return UIManager.getInstance().getDecorView();
        }
    }
}
