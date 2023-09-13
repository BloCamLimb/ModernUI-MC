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

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.blaze3d.platform.*;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.font.GlyphManager;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.util.DisplayMetrics;
import icyllis.modernui.view.ViewConfiguration;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.*;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static icyllis.modernui.ModernUI.*;

@ApiStatus.Internal
final class Config {

    static Client CLIENT;
    private static ForgeConfigSpec CLIENT_SPEC;

    static final Common COMMON;
    private static final ForgeConfigSpec COMMON_SPEC;

    static final Server SERVER;
    private static final ForgeConfigSpec SERVER_SPEC;

    static {
        ForgeConfigSpec.Builder builder;

        if (FMLEnvironment.dist.isClient()) {
            builder = new ForgeConfigSpec.Builder();
            CLIENT = new Client(builder);
            CLIENT_SPEC = builder.build();
        }

        builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();

        builder = new ForgeConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    static void init() {
        FMLPaths.getOrCreateGameRelativePath(FMLPaths.CONFIGDIR.get().resolve(ModernUI.NAME_CPT), ModernUI.NAME_CPT);
        ModContainer mod = ModLoadingContext.get().getActiveContainer();
        if (FMLEnvironment.dist.isClient()) {
            mod.addConfig(new C(ModConfig.Type.CLIENT, CLIENT_SPEC, mod, "client")); // client only
            mod.addConfig(new C(ModConfig.Type.COMMON, COMMON_SPEC, mod, "common")); // client only, but server logic
            mod.addConfig(new C(ModConfig.Type.SERVER, SERVER_SPEC, mod, "server")); // sync to client (local)
        } else {
            mod.addConfig(new C(ModConfig.Type.COMMON, COMMON_SPEC, mod, "common")); // dedicated server only
            mod.addConfig(new C(ModConfig.Type.SERVER, SERVER_SPEC, mod, "server")); // sync to client (network)
        }
        FMLJavaModLoadingContext.get().getModEventBus().addListener(Config::reload);
    }

    static void reload(@Nonnull ModConfigEvent event) {
        final IConfigSpec<?> spec = event.getConfig().getSpec();
        if (spec == CLIENT_SPEC) {
            /*try {
                ((com.electronwill.nightconfig.core.Config) ObfuscationReflectionHelper.findField(ForgeConfigSpec
                .class, "childConfig").get(CLIENT_SPEC)).set(Lists.newArrayList("tooltip", "frameColor"), "0xE8B4DF");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            CLIENT_SPEC.save();*/
            CLIENT.reload();
            LOGGER.debug(MARKER, "Client config reloaded with {}", event.getClass().getSimpleName());
        } else if (spec == COMMON_SPEC) {
            COMMON.reload();
            LOGGER.debug(MARKER, "Common config reloaded with {}", event.getClass().getSimpleName());
        } else if (spec == SERVER_SPEC) {
            SERVER.reload();
            LOGGER.debug(MARKER, "Server config reloaded with {}", event.getClass().getSimpleName());
        }
    }

    private static class C extends ModConfig {

        private static final Toml _TOML = new Toml();

        public C(Type type, ForgeConfigSpec spec, ModContainer container, String name) {
            super(type, spec, container, ModernUI.NAME_CPT + "/" + name + ".toml");
        }

        @Override
        public ConfigFileTypeHandler getHandler() {
            return _TOML;
        }
    }

    private static class Toml extends ConfigFileTypeHandler {

        private Toml() {
        }

        // reroute it to the global config directory
        // see ServerLifecycleHooks, ModConfig.Type.SERVER
        private static Path reroute(@Nonnull Path configBasePath) {
            //noinspection SpellCheckingInspection
            if (configBasePath.endsWith("serverconfig")) {
                return FMLPaths.CONFIGDIR.get();
            }
            return configBasePath;
        }

        @Override
        public Function<ModConfig, CommentedFileConfig> reader(Path configBasePath) {
            return super.reader(reroute(configBasePath));
        }

        @Override
        public void unload(Path configBasePath, ModConfig config) {
            super.unload(reroute(configBasePath), config);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Client {

        public static final int ANIM_DURATION_MIN = 0;
        public static final int ANIM_DURATION_MAX = 800;
        public static final int BLUR_RADIUS_MIN = 2;
        public static final int BLUR_RADIUS_MAX = 18;
        public static final float FONT_SCALE_MIN = 0.5f;
        public static final float FONT_SCALE_MAX = 2.0f;
        public static final int TOOLTIP_BORDER_COLOR_ANIM_MIN = 0;
        public static final int TOOLTIP_BORDER_COLOR_ANIM_MAX = 5000;

        public final ForgeConfigSpec.BooleanValue mBlurEffect;
        public final ForgeConfigSpec.IntValue mBackgroundDuration;
        public final ForgeConfigSpec.IntValue mBlurRadius;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mBackgroundColor;
        public final ForgeConfigSpec.BooleanValue mInventoryPause;
        public final ForgeConfigSpec.BooleanValue mTooltip;
        public final ForgeConfigSpec.BooleanValue mRoundedTooltip;
        public final ForgeConfigSpec.BooleanValue mCenterTooltipTitle;
        public final ForgeConfigSpec.BooleanValue mTooltipTitleBreak;
        public final ForgeConfigSpec.BooleanValue mExactTooltipPositioning;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mTooltipFill;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mTooltipStroke;
        public final ForgeConfigSpec.IntValue mTooltipCycle;
        public final ForgeConfigSpec.IntValue mTooltipDuration;
        public final ForgeConfigSpec.BooleanValue mDing;
        public final ForgeConfigSpec.BooleanValue mZoom;
        //private final ForgeConfigSpec.BooleanValue hudBars;
        public final ForgeConfigSpec.BooleanValue mForceRtl;
        public final ForgeConfigSpec.DoubleValue mFontScale;
        public final ForgeConfigSpec.EnumValue<WindowMode> mWindowMode;
        public final ForgeConfigSpec.IntValue mFramerateInactive;
        public final ForgeConfigSpec.IntValue mFramerateMinimized;
        public final ForgeConfigSpec.DoubleValue mMasterVolumeInactive;
        public final ForgeConfigSpec.DoubleValue mMasterVolumeMinimized;

        final ForgeConfigSpec.IntValue scrollbarSize;
        final ForgeConfigSpec.IntValue touchSlop;
        final ForgeConfigSpec.IntValue minScrollbarTouchTarget;
        final ForgeConfigSpec.IntValue minimumFlingVelocity;
        final ForgeConfigSpec.IntValue maximumFlingVelocity;
        final ForgeConfigSpec.IntValue overscrollDistance;
        final ForgeConfigSpec.IntValue overflingDistance;
        final ForgeConfigSpec.DoubleValue verticalScrollFactor;
        final ForgeConfigSpec.DoubleValue horizontalScrollFactor;

        private final ForgeConfigSpec.ConfigValue<List<? extends String>> mBlurBlacklist;

        final ForgeConfigSpec.BooleanValue mAntiAliasing;
        public final ForgeConfigSpec.BooleanValue mAutoHinting;
        public final ForgeConfigSpec.ConfigValue<String> mFirstFontFamily;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mFallbackFontFamilyList;

        /*final ForgeConfigSpec.BooleanValue skipGLCapsError;
        final ForgeConfigSpec.BooleanValue showGLCapsError;*/

        public WindowMode mLastWindowMode;

        private Client(@Nonnull ForgeConfigSpec.Builder builder) {
            builder.comment("Screen Config")
                    .push("screen");

            mBackgroundDuration = builder.comment(
                            "The duration of GUI background color and blur radius animation in milliseconds. (0 = OFF)")
                    .defineInRange("animationDuration", 200, ANIM_DURATION_MIN, ANIM_DURATION_MAX);
            mBackgroundColor = builder.comment(
                            "The GUI background color in #RRGGBB or #AARRGGBB format. Default value: #66000000",
                            "Can be one to four values representing top left, top right, bottom right and bottom left" +
                                    " color.",
                            "Multiple values produce a gradient effect, whereas one value produce a solid color.",
                            "When values is less than 4, the rest of the corner color will be replaced by the last " +
                                    "value.")
                    .defineList("backgroundColor", () -> {
                        List<String> list = new ArrayList<>();
                        list.add("#99000000");
                        return list;
                    }, o -> true);

            mBlurEffect = builder.comment(
                            "Add blur effect to GUI background when opened, it is incompatible with OptiFine's FXAA " +
                                    "shader and some mods.",
                            "Disable this if you run into a problem or are on low-end PCs")
                    .define("blurEffect", true);
            mBlurRadius = builder.comment(
                            "The strength for two-pass gaussian convolution blur effect.",
                            "samples/pixel = ((radius * 2) + 1) * 2, sigma = radius / 2.")
                    .defineInRange("blurRadius", 7, BLUR_RADIUS_MIN, BLUR_RADIUS_MAX);
            mBlurBlacklist = builder.comment(
                            "A list of GUI screen superclasses that won't activate blur effect when opened.")
                    .defineList("blurBlacklist", () -> {
                        List<String> list = new ArrayList<>();
                        list.add(ChatScreen.class.getName());
                        return list;
                    }, o -> true);
            mInventoryPause = builder.comment(
                            "(Beta) Pause the game when inventory (also includes creative mode) opened.")
                    .define("inventoryPause", false);
            mFramerateInactive = builder.comment(
                            "Framerate limit on window inactive (out of focus or minimized), 0 = no change.")
                    .defineInRange("framerateInactive", 60, 0, 255);
            mFramerateMinimized = builder.comment(
                            "Framerate limit on window minimized, 0 = same as framerate inactive.",
                            "This value will be no greater than framerate inactive.")
                    .defineInRange("framerateMinimized", 0, 0, 255);
            mMasterVolumeInactive = builder.comment(
                            "Master volume multiplier on window inactive (out of focus or minimized), 1 = no change.")
                    .defineInRange("masterVolumeInactive", 1.0, 0, 1);
            mMasterVolumeMinimized = builder.comment(
                            "Master volume multiplier on window minimized, 1 = same as master volume inactive.",
                            "This value will be no greater than master volume inactive.")
                    .defineInRange("masterVolumeMinimized", 1.0, 0, 1);

            builder.pop();

            builder.comment("Tooltip Config")
                    .push("tooltip");

            mTooltip = builder.comment(
                            "Whether to enable Modern UI rounded tooltip style, or back to vanilla style.")
                    .define("enable", true);
            mRoundedTooltip = builder.comment(
                            "Whether to use rounded tooltip shapes, or to use rectangular shapes.")
                    .define("roundedShape", true);
            mCenterTooltipTitle = builder.comment(
                            "True to center the tooltip title if rendering an item's tooltip.",
                            "Following lines are not affected by this option.")
                    .define("centerTitle", true);
            mTooltipTitleBreak = builder.comment(
                            "True to add a title break below the tooltip title line.",
                            "TitleBreak and CenterTitle will work/appear at the same time.")
                    .define("titleBreak", true);
            mExactTooltipPositioning = builder.comment(
                            "True to exactly position tooltip to pixel grid, smoother movement.")
                    .define("exactPositioning", true);
            mTooltipFill = builder.comment(
                            "The tooltip FILL color in #RRGGBB or #AARRGGBB format. Default: #D4000000",
                            "Can be one to four values representing top left, top right, bottom right and bottom left" +
                                    " color.",
                            "Multiple values produce a gradient effect, whereas one value produces a solid color.")
                    .defineList("colorFill", () -> {
                        List<String> list = new ArrayList<>();
                        list.add("#D4000000");
                        return list;
                    }, $ -> true);
            mTooltipStroke = builder.comment(
                            "The tooltip STROKE color in #RRGGBB or #AARRGGBB format. Default: #F0AADCF0, #F0DAD0F4, " +
                                    "#F0FFC3F7 and #F0DAD0F4",
                            "Can be one to four values representing top left, top right, bottom right and bottom left" +
                                    " color.",
                            "Multiple values produce a gradient effect, whereas one value produces a solid color.",
                            "If less than 4 are provided, repeat the last value.")
                    .defineList("colorStroke", () -> {
                        List<String> list = new ArrayList<>();
                        list.add("#F0AADCF0");
                        list.add("#F0FFC3F7");
                        list.add("#F0BFF2B2");
                        list.add("#F0D27F3D");
                        return list;
                    }, $ -> true);
            mTooltipCycle = builder.comment(
                            "The cycle time of tooltip border color in milliseconds. (0 = OFF)")
                    .defineInRange("borderCycleTime", 1000, TOOLTIP_BORDER_COLOR_ANIM_MIN,
                            TOOLTIP_BORDER_COLOR_ANIM_MAX);
            mTooltipDuration = builder.comment(
                            "The duration of tooltip alpha animation in milliseconds. (0 = OFF)")
                    .defineInRange("animationDuration", 0, ANIM_DURATION_MIN, ANIM_DURATION_MAX);

            builder.pop();

            builder.comment("General Config")
                    .push("general");

            mDing = builder.comment("Play a sound effect when the game is loaded.")
                    .define("ding", true);
            mZoom = builder.comment(
                            "Press 'C' key (by default) to zoom 4x, the same as OptiFine's.",
                            "This is auto disabled when OptiFine is installed.")
                    .define("zoom", true);

            /*hudBars = builder.comment(
                    "Show additional HUD bars added by ModernUI on the bottom-left of the screen.")
                    .define("hudBars", false);*/

            mWindowMode = builder.comment("Control the window mode, normal mode does nothing.")
                    .defineEnum("windowMode", WindowMode.NORMAL);

            /*skipGLCapsError = builder.comment("UI renderer is disabled when the OpenGL capability test fails.",
                            "Sometimes the driver reports wrong values, you can enable this to ignore it.")
                    .define("skipGLCapsError", false);
            showGLCapsError = builder.comment("A dialog popup is displayed when the OpenGL capability test fails.",
                            "Set to false to not show it. This is ignored when skipGLCapsError=true")
                    .define("showGLCapsError", true);*/

            builder.pop();

            builder.comment("View system config, currently not working.")
                    .push("view");

            mForceRtl = builder.comment("Force layout direction to RTL, otherwise, the current Locale setting.")
                    .define("forceRtl", false);
            mFontScale = builder.comment("The global font scale used with sp units.")
                    .defineInRange("fontScale", 1.0f, FONT_SCALE_MIN, FONT_SCALE_MAX);
            scrollbarSize = builder.comment("Default scrollbar size in dips.")
                    .defineInRange("scrollbarSize", ViewConfiguration.SCROLL_BAR_SIZE, 0, 1024);
            touchSlop = builder.comment("Distance a touch can wander before we think the user is scrolling in dips.")
                    .defineInRange("touchSlop", ViewConfiguration.TOUCH_SLOP, 0, 1024);
            minScrollbarTouchTarget = builder.comment("Minimum size of the touch target for a scrollbar in dips.")
                    .defineInRange("minScrollbarTouchTarget", ViewConfiguration.MIN_SCROLLBAR_TOUCH_TARGET, 0, 1024);
            minimumFlingVelocity = builder.comment("Minimum velocity to initiate a fling in dips per second.")
                    .defineInRange("minimumFlingVelocity", ViewConfiguration.MINIMUM_FLING_VELOCITY, 0, 32767);
            maximumFlingVelocity = builder.comment("Maximum velocity to initiate a fling in dips per second.")
                    .defineInRange("maximumFlingVelocity", ViewConfiguration.MAXIMUM_FLING_VELOCITY, 0, 32767);
            overscrollDistance = builder.comment("Max distance in dips to overscroll for edge effects.")
                    .defineInRange("overscrollDistance", ViewConfiguration.OVERSCROLL_DISTANCE, 0, 1024);
            overflingDistance = builder.comment("Max distance in dips to overfling for edge effects.")
                    .defineInRange("overflingDistance", ViewConfiguration.OVERFLING_DISTANCE, 0, 1024);
            verticalScrollFactor = builder.comment("Amount to scroll in response to a vertical scroll event, in dips " +
                            "per axis value.")
                    .defineInRange("verticalScrollFactor", ViewConfiguration.VERTICAL_SCROLL_FACTOR, 0, 1024);
            horizontalScrollFactor = builder.comment("Amount to scroll in response to a horizontal scroll event, in " +
                            "dips per axis value.")
                    .defineInRange("horizontalScrollFactor", ViewConfiguration.HORIZONTAL_SCROLL_FACTOR, 0, 1024);

            builder.pop();


            builder.comment("Font Config")
                    .push("font");

            mAntiAliasing = builder.comment(
                            "Control the anti-aliasing of raw glyph rasterization.")
                    .define("antiAliasing", true);
            mAutoHinting = builder.comment(
                            "Control the FreeType font hinting of raw glyph metrics.",
                            "Enable if on low-res monitor; disable for smooth fonts.")
                    .define("autoHinting", true);
            /*mLinearSampling = builder.comment(
                            "Enable linear sampling for font atlases with mipmaps, mag filter will be always NEAREST.",
                            "If your fonts are not bitmap fonts, then you should keep this setting true.")
                    .define("linearSampling", true);*/
            // Segoe UI, Source Han Sans CN Medium, Noto Sans, Open Sans, San Francisco, Calibri,
            // Microsoft YaHei UI, STHeiti, SimHei, SansSerif
            mFirstFontFamily = builder.comment(
                            "The first font family to use. See fallbackFontFamilyList")
                    .define("firstFontFamily", "Source Han Sans CN Medium");
            mFallbackFontFamilyList = builder.comment(
                            "A set of fallback font families to determine the typeface to use.",
                            "TrueType & OpenType are supported. Each element can be one of the following two cases.",
                            "1) Font family English name that registered to Modern UI, for instance: Segoe UI",
                            "2) File path for external fonts on your PC, for instance: /usr/shared/fonts/x.otf",
                            "Fonts under 'modernui:font' in resource packs and OS builtin fonts will be registered.",
                            "Using bitmap fonts should consider other text settings, default glyph size should be 16x.",
                            "This list is only read once when the game is loaded. A game restart is required to reload")
                    .defineList("fallbackFontFamilyList", () -> {
                        List<String> list = new ArrayList<>();
                        list.add("Noto Sans");
                        list.add("Segoe UI");
                        list.add("San Francisco");
                        list.add("Open Sans");
                        list.add("SimHei");
                        list.add("STHeiti");
                        list.add("Segoe UI Variable");
                        list.add("mui-i18n-compat");
                        return list;
                    }, s -> true);

            builder.pop();
        }

        public void saveAsync() {
            Util.ioPool().execute(() -> CLIENT_SPEC.save());
        }

        public void saveAndReloadAsync() {
            Util.ioPool().execute(() -> {
                CLIENT_SPEC.save();
                reload();
            });
        }

        private void reload() {
            BlurHandler.sBlurEffect = mBlurEffect.get();
            BlurHandler.sBackgroundDuration = mBackgroundDuration.get();
            BlurHandler.sBlurRadius = mBlurRadius.get();

            BlurHandler.sFramerateInactive = mFramerateInactive.get();
            BlurHandler.sFramerateMinimized = Math.min(
                    mFramerateMinimized.get(),
                    BlurHandler.sFramerateInactive
            );
            BlurHandler.sMasterVolumeInactive = mMasterVolumeInactive.get().floatValue();
            BlurHandler.sMasterVolumeMinimized = Math.min(
                    mMasterVolumeMinimized.get().floatValue(),
                    BlurHandler.sMasterVolumeInactive
            );

            List<? extends String> inColors = mBackgroundColor.get();
            int[] resultColors = new int[4];
            int color = 0x99000000;
            for (int i = 0; i < 4; i++) {
                if (inColors != null && i < inColors.size()) {
                    String s = inColors.get(i);
                    try {
                        color = Color.parseColor(s);
                    } catch (Exception e) {
                        LOGGER.error(MARKER, "Wrong color format for screen background, index: {}", i, e);
                    }
                }
                resultColors[i] = color;
            }
            BlurHandler.sBackgroundColor = resultColors;

            BlurHandler.INSTANCE.loadBlacklist(mBlurBlacklist.get());

            ModernUIForge.sInventoryScreenPausesGame = mInventoryPause.get();

            TooltipRenderer.sTooltip = mTooltip.get();

            inColors = mTooltipFill.get();
            color = 0xFFFFFFFF;
            for (int i = 0; i < 4; i++) {
                if (inColors != null && i < inColors.size()) {
                    String s = inColors.get(i);
                    try {
                        color = Color.parseColor(s);
                    } catch (Exception e) {
                        LOGGER.error(MARKER, "Wrong color format for tooltip background, index: {}", i, e);
                    }
                }
                TooltipRenderer.sFillColor[i] = color;
            }
            inColors = mTooltipStroke.get();
            color = 0xFFFFFFFF;
            for (int i = 0; i < 4; i++) {
                if (inColors != null && i < inColors.size()) {
                    String s = inColors.get(i);
                    try {
                        color = Color.parseColor(s);
                    } catch (Exception e) {
                        LOGGER.error(MARKER, "Wrong color format for tooltip border, index: {}", i, e);
                    }
                }
                TooltipRenderer.sStrokeColor[i] = color;
            }
            TooltipRenderer.sAnimationDuration = mTooltipDuration.get();
            TooltipRenderer.sBorderColorCycle = mTooltipCycle.get();
            TooltipRenderer.sExactPositioning = mExactTooltipPositioning.get();
            TooltipRenderer.sRoundedShapes = mRoundedTooltip.get();
            TooltipRenderer.sCenterTitle = mCenterTooltipTitle.get();
            TooltipRenderer.sTitleBreak = mTooltipTitleBreak.get();

            UIManager.sDingEnabled = mDing.get();
            UIManager.sZoomEnabled = mZoom.get() && !ModernUIForge.isOptiFineLoaded();

            WindowMode windowMode = mWindowMode.get();
            if (mLastWindowMode != windowMode) {
                mLastWindowMode = windowMode;
                if (windowMode != WindowMode.NORMAL) {
                    Minecraft.getInstance().tell(windowMode::apply);
                }
            }

            //TestHUD.sBars = hudBars.get();
            Handler handler = Core.getUiHandlerAsync();
            if (handler != null) {
                handler.post(() -> {
                    UIManager.getInstance().updateLayoutDir(mForceRtl.get());
                    ModernUIForge.sFontScale = (mFontScale.get().floatValue());
                    var ctx = ModernUI.getInstance();
                    if (ctx != null) {
                        Resources res = ctx.getResources();
                        DisplayMetrics metrics = new DisplayMetrics();
                        metrics.setTo(res.getDisplayMetrics());
                        metrics.scaledDensity = ModernUIForge.sFontScale * metrics.density;
                        res.updateMetrics(metrics);
                    }
                });
            }

            boolean reloadStrike = false;
            if (GlyphManager.sAntiAliasing != mAntiAliasing.get()) {
                GlyphManager.sAntiAliasing = mAntiAliasing.get();
                reloadStrike = true;
            }
            if (GlyphManager.sFractionalMetrics == mAutoHinting.get()) {
                GlyphManager.sFractionalMetrics = !mAutoHinting.get();
                reloadStrike = true;
            }
            /*if (GLFontAtlas.sLinearSampling != mLinearSampling.get()) {
                GLFontAtlas.sLinearSampling = mLinearSampling.get();
                reload = true;
            }*/
            if (reloadStrike) {
                ModernUIForge.Client.getInstance().reloadFontStrike();
            }

            ModernUI.getSelectedTypeface();
        }

        public enum WindowMode {
            NORMAL,
            FULLSCREEN,
            FULLSCREEN_BORDERLESS,
            MAXIMIZED,
            MAXIMIZED_BORDERLESS,
            WINDOWED,
            WINDOWED_BORDERLESS;

            public void apply() {
                Window window = Minecraft.getInstance().getWindow();
                switch (this) {
                    case FULLSCREEN -> {
                        if (!window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                    }
                    case FULLSCREEN_BORDERLESS -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwRestoreWindow(window.getWindow());
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                        Monitor monitor = window.findBestMonitor();
                        if (monitor != null) {
                            VideoMode videoMode = monitor.getCurrentMode();
                            int x = monitor.getX();
                            int y = monitor.getY();
                            int width = videoMode.getWidth();
                            int height = videoMode.getHeight();
                            GLFW.glfwSetWindowMonitor(window.getWindow(), MemoryUtil.NULL,
                                    x, y, width, height, GLFW.GLFW_DONT_CARE);
                        } else {
                            GLFW.glfwMaximizeWindow(window.getWindow());
                        }
                    }
                    case MAXIMIZED -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwRestoreWindow(window.getWindow());
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
                        GLFW.glfwMaximizeWindow(window.getWindow());
                    }
                    case MAXIMIZED_BORDERLESS -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwRestoreWindow(window.getWindow());
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                        GLFW.glfwMaximizeWindow(window.getWindow());
                    }
                    case WINDOWED -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
                        GLFW.glfwRestoreWindow(window.getWindow());
                    }
                    case WINDOWED_BORDERLESS -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                        GLFW.glfwRestoreWindow(window.getWindow());
                    }
                }
            }

            @Nonnull
            @Override
            public String toString() {
                return I18n.get("modernui.windowMode." + name().toLowerCase(Locale.ROOT));
            }
        }
    }

    // common config exists on physical client and physical server once game loaded
    // they are independent and do not sync with each other
    public static class Common {

        private final ForgeConfigSpec.BooleanValue developerMode;
        final ForgeConfigSpec.IntValue oneTimeEvents;

        final ForgeConfigSpec.BooleanValue autoShutdown;

        final ForgeConfigSpec.ConfigValue<List<? extends String>> shutdownTimes;

        private Common(@Nonnull ForgeConfigSpec.Builder builder) {
            builder.comment("Developer Config")
                    .push("developer");

            developerMode = builder.comment("Whether to enable developer mode.")
                    .define("enableDeveloperMode", false);
            oneTimeEvents = builder
                    .defineInRange("oneTimeEvents", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

            builder.pop();

            builder.comment("Auto Shutdown Config")
                    .push("autoShutdown");

            autoShutdown = builder.comment(
                            "Enable auto-shutdown for server.")
                    .define("enable", false);
            shutdownTimes = builder.comment(
                            "The time points of when server will auto-shutdown. Format: HH:mm.")
                    .defineList("times", () -> {
                        List<String> list = new ArrayList<>();
                        list.add("04:00");
                        list.add("16:00");
                        return list;
                    }, s -> true);

            builder.pop();
        }

        private void reload() {
            ModernUIForge.sDeveloperMode = developerMode.get();
            ServerHandler.INSTANCE.determineShutdownTime();
        }
    }

    // server config is available when integrated server or dedicated server started
    // if on dedicated server, all config data will sync to remote client via network
    public static class Server {

        private Server(@Nonnull ForgeConfigSpec.Builder builder) {

        }

        private void reload() {

        }
    }
}
