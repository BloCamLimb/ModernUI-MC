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

package icyllis.modernui.mc.neoforge;

import icyllis.modernui.mc.Config;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.MuiPlatform;
import icyllis.modernui.mc.text.TextLayout;
import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.mc.text.TextLayoutProcessor;
import icyllis.modernui.view.ViewConfiguration;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.Range;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static icyllis.modernui.mc.ModernUIMod.*;

@ApiStatus.Internal
public final class ConfigImpl {

    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    public static final Text TEXT;
    public static final ModConfigSpec TEXT_SPEC;

    /*static final Server SERVER;
    private static final ModConfigSpec SERVER_SPEC;*/

    private static void init(boolean isClient,
                             BiConsumer<ModConfig.Type, ModConfigSpec> registerConfig) {
        /*builder = new ModConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();*/

        /*container.addConfig(new ModConfig(ModConfig.Type.SERVER, SERVER_SPEC, container,
                    ModernUI.NAME_CPT + "/server.toml")); // sync to client (network)*/
    }

    static {
        MuiPlatform service = MuiPlatform.get();
        if (service.isClient()) {
            {
                ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
                CLIENT = new Client(builder);
                CLIENT_SPEC = builder.build();
            }
            ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
            TEXT = new Text(builder);
            TEXT_SPEC = builder.build();
        } else {
            CLIENT = null;
            CLIENT_SPEC = null;
            TEXT = null;
            TEXT_SPEC = null;
        }
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    /*public static void reload(@Nonnull ModConfig config) {
        final IConfigSpec<?> spec = config.getSpec();
        *//* else if (spec == SERVER_SPEC) {
            SERVER.reload();
            LOGGER.debug(MARKER, "Server config reloaded with {}", event.getClass().getSimpleName());
        }*//*
    }*/

    public static void reloadCommon(@Nonnull ModConfig config) {
        final IConfigSpec spec = config.getSpec();
        if (spec == COMMON_SPEC) {
            COMMON.reload();
            LOGGER.debug(MARKER, "Modern UI common config loaded/reloaded");
        }
    }

    public static void reloadAnyClient(@Nonnull ModConfig config) {
        final IConfigSpec spec = config.getSpec();
        if (spec == CLIENT_SPEC) {
            CLIENT.reload();
            LOGGER.debug(MARKER, "Modern UI client config loaded/reloaded");
        } else if (spec == TEXT_SPEC) {
            TEXT.reload();
            LOGGER.debug(MARKER, "Modern UI text config loaded/reloaded");
        }
    }

    static class ConfigItemImpl<T> extends Config.ConfigItem<T> {

        private final ModConfigSpec.ConfigValue<T> value;
        private final ModConfigSpec.ValueSpec spec;

        ConfigItemImpl(ModConfigSpec.ConfigValue<T> value,
                       ModConfigSpec.ValueSpec spec) {
            this.value = value;
            this.spec = spec;
        }

        @Override
        public T get() {
            return value.get();
        }

        @Override
        public void set(T value) {
            this.value.set(value);
        }

        @Override
        public T getDefault() {
            return value.getDefault();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Range<T> getRange() {
            ModConfigSpec.Range<Comparable<Object>> r = spec.getRange();
            if (r != null) {
                return (Range<T>) Range.of(r.getMin(), r.getMax());
            }
            return null;
        }
    }

    @Nullable
    static Map<String, Config.ConfigItem<?>> getConfigMap(int type) {
        final Object config;
        final ModConfigSpec configSpec;
        switch (type) {
            case Config.TYPE_CLIENT -> {
                config = CLIENT;
                configSpec = CLIENT_SPEC;
            }
            case Config.TYPE_COMMON -> {
                config = COMMON;
                configSpec = COMMON_SPEC;
            }
            case Config.TYPE_TEXT -> {
                config = TEXT;
                configSpec = TEXT_SPEC;
            }
            default -> {
                return null;
            }
        }
        Map<String, Config.ConfigItem<?>> map = new HashMap<>();
        for (var f : config.getClass().getDeclaredFields()) {
            try {
                if (f.get(config) instanceof ModConfigSpec.ConfigValue<?> value &&
                        configSpec.getSpec().get(value.getPath()) instanceof ModConfigSpec.ValueSpec spec) {
                    map.put(f.getName(), new ConfigItemImpl<>(value, spec));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return map;
    }

    /*private static class C extends ModConfig {

        private static final Toml _TOML = new Toml();

        public C(Type type, ModConfigSpec spec, ModContainer container, String name) {
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
    }*/

    public static class Client {

        public static final int ANIM_DURATION_MIN = 0;
        public static final int ANIM_DURATION_MAX = 800;
        public static final int BLUR_RADIUS_MIN = 0;
        public static final int BLUR_RADIUS_MAX = 18;
        public static final float FONT_SCALE_MIN = 0.5f;
        public static final float FONT_SCALE_MAX = 2.0f;
        public static final int TOOLTIP_BORDER_COLOR_ANIM_MIN = 0;
        public static final int TOOLTIP_BORDER_COLOR_ANIM_MAX = 5000;
        public static final float TOOLTIP_BORDER_WIDTH_MIN = 0.5f;
        public static final float TOOLTIP_BORDER_WIDTH_MAX = 2.5f;
        public static final float TOOLTIP_CORNER_RADIUS_MIN = 0;
        public static final float TOOLTIP_CORNER_RADIUS_MAX = 8;
        public static final float TOOLTIP_SHADOW_RADIUS_MIN = 0;
        public static final float TOOLTIP_SHADOW_RADIUS_MAX = 32;
        public static final int TOOLTIP_ARROW_SCROLL_FACTOR_MIN = 0;
        public static final int TOOLTIP_ARROW_SCROLL_FACTOR_MAX = 320;

        public final ModConfigSpec.BooleanValue mBlurEffect;
        //public final ModConfigSpec.BooleanValue mBlurWithBackground;
        public final ModConfigSpec.BooleanValue mOverrideVanillaBlur;
        public final ModConfigSpec.IntValue mBackgroundDuration;
        public final ModConfigSpec.IntValue mBlurRadius;
        public final ModConfigSpec.ConfigValue<List<? extends String>> mBackgroundColor;
        public final ModConfigSpec.BooleanValue mInventoryPause;
        public final ModConfigSpec.BooleanValue mTooltip;
        public final ModConfigSpec.BooleanValue mRoundedTooltip;
        public final ModConfigSpec.BooleanValue mCenterTooltipTitle;
        public final ModConfigSpec.BooleanValue mTooltipTitleBreak;
        public final ModConfigSpec.BooleanValue mExactTooltipPositioning;
        public final ModConfigSpec.ConfigValue<List<? extends String>> mTooltipFill;
        public final ModConfigSpec.ConfigValue<List<? extends String>> mTooltipStroke;
        public final ModConfigSpec.IntValue mTooltipCycle;
        public final ModConfigSpec.DoubleValue mTooltipWidth;
        public final ModConfigSpec.DoubleValue mTooltipRadius;
        public final ModConfigSpec.DoubleValue mTooltipShadowRadius;
        public final ModConfigSpec.DoubleValue mTooltipShadowAlpha;
        public final ModConfigSpec.BooleanValue mAdaptiveTooltipColors;
        public final ModConfigSpec.IntValue mTooltipArrowScrollFactor;
        //public final ModConfigSpec.BooleanValue mTooltipLineWrapping;
        //public final ModConfigSpec.IntValue mTooltipDuration;
        public final ModConfigSpec.BooleanValue mDing;
        public final ModConfigSpec.BooleanValue mZoom;
        //private final ModConfigSpec.BooleanValue hudBars;
        public final ModConfigSpec.BooleanValue mForceRtl;
        public final ModConfigSpec.DoubleValue mFontScale;
        public final ModConfigSpec.EnumValue<Config.Client.WindowMode> mWindowMode;
        public final ModConfigSpec.BooleanValue mUseNewGuiScale;
        //public final ModConfigSpec.BooleanValue mRemoveSignature;
        public final ModConfigSpec.BooleanValue mRemoveTelemetry;
        //public final ModConfigSpec.BooleanValue mSecurePublicKey;
        public final ModConfigSpec.IntValue mFramerateInactive;
        //public final ModConfigSpec.IntValue mFramerateMinimized;
        public final ModConfigSpec.DoubleValue mMasterVolumeInactive;
        public final ModConfigSpec.DoubleValue mMasterVolumeMinimized;

        public final ModConfigSpec.IntValue mScrollbarSize;
        public final ModConfigSpec.IntValue mTouchSlop;
        public final ModConfigSpec.IntValue mMinScrollbarTouchTarget;
        public final ModConfigSpec.IntValue mMinimumFlingVelocity;
        public final ModConfigSpec.IntValue mMaximumFlingVelocity;
        public final ModConfigSpec.IntValue mOverscrollDistance;
        public final ModConfigSpec.IntValue mOverflingDistance;
        public final ModConfigSpec.DoubleValue mVerticalScrollFactor;
        public final ModConfigSpec.DoubleValue mHorizontalScrollFactor;

        private final ModConfigSpec.ConfigValue<List<? extends String>> mBlurBlacklist;

        public final ModConfigSpec.ConfigValue<String> mFirstFontFamily;
        public final ModConfigSpec.ConfigValue<List<? extends String>> mFallbackFontFamilyList;
        public final ModConfigSpec.ConfigValue<List<? extends String>> mFontRegistrationList;
        public final ModConfigSpec.BooleanValue mUseColorEmoji;
        public final ModConfigSpec.BooleanValue mEmojiShortcodes;

        /*public final ModConfigSpec.BooleanValue mSkipGLCapsError;
        public final ModConfigSpec.BooleanValue mShowGLCapsError;*/

        private Client(@Nonnull ModConfigSpec.Builder builder) {
            builder.comment("Screen Config")
                    .push("screen");

            mBackgroundDuration = builder.comment(
                            "The duration of GUI background color and blur radius animation in milliseconds. (0 = OFF)")
                    .defineInRange("animationDuration", 200, ANIM_DURATION_MIN, ANIM_DURATION_MAX);
            mBackgroundColor = builder.comment(
                            "The GUI background color in #RRGGBB or #AARRGGBB format. Default value: #99000000",
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
                            "Add Gaussian blur effect to GUI background when opened.",
                            "Disable this if you run into a problem or are on low-end PCs")
                    .define("blurEffect", !ModernUIMod.isOptiFineLoaded());
            // OK, this doesn't work well with OptiFine
            /*mBlurWithBackground = builder.comment(
                            "This option means that blur effect only applies to GUI screens with a background.",
                            "Similar to Minecraft 1.21. Enable this for better optimization & compatibility.")
                    .define("blurWithBackground", true);*/
            mOverrideVanillaBlur = builder.comment(
                            "Whether to replace Vanilla 3-pass box blur with Modern UI Gaussian blur.",
                            "This gives you better quality and performance, recommend setting this to true.")
                    .define("overrideVanillaBlur", !ModernUIMod.isOptiFineLoaded());
            // OK, this doesn't work well with OptiFine
            mBlurRadius = builder.comment(
                            "The kernel radius for gaussian convolution blur effect, 0 = disable.",
                            "samples per pixel = ((radius * 2) + 1) * 2, sigma = radius / 2.")
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
                            "Framerate limit on window inactive (out of focus), 0 = no change.")
                    .defineInRange("framerateInactive", 60, 0, 250);
            /*mFramerateMinimized = builder.comment(
                            "Framerate limit on window minimized, 0 = same as framerate inactive.",
                            "This value will be no greater than framerate inactive.")
                    .defineInRange("framerateMinimized", 0, 0, 255);*/
            mMasterVolumeInactive = builder.comment(
                            "Master volume multiplier on window inactive (out of focus or minimized), 1 = no change.")
                    .defineInRange("masterVolumeInactive", 0.5, 0, 1);
            mMasterVolumeMinimized = builder.comment(
                            "Master volume multiplier on window minimized, 1 = same as master volume inactive.",
                            "This value will be no greater than master volume inactive.")
                    .defineInRange("masterVolumeMinimized", 0.25, 0, 1);

            builder.pop();

            builder.comment("Tooltip Config")
                    .push("tooltip");

            mTooltip = builder.comment(
                            "Whether to enable Modern UI enhanced tooltip, or back to vanilla default.")
                    .define("enable", !ModernUIMod.isLegendaryTooltipsLoaded());
            mRoundedTooltip = builder.comment(
                            "Whether to use rounded tooltip shapes, or to use vanilla style.")
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
                            "The tooltip background color in #RRGGBB or #AARRGGBB format. Default: #E6000000",
                            "Can be one to four values representing top left, top right, bottom right and bottom left" +
                                    " color.",
                            "Multiple values produce a gradient effect, whereas one value produces a solid color.",
                            "If less than 4 are provided, repeat the last value.")
                    .defineList("colorFill", () -> {
                        List<String> list = new ArrayList<>();
                        list.add("#E6000000");
                        return list;
                    }, $ -> true);
            mTooltipStroke = builder.comment(
                            "The tooltip border color in #RRGGBB or #AARRGGBB format. Default: #F0AADCF0, #F0DAD0F4, " +
                                    "#F0FFC3F7 and #F0DAD0F4",
                            "Can be one to four values representing top left, top right, bottom right and bottom left" +
                                    " color.",
                            "Multiple values produce a gradient effect, whereas one value produces a solid color.",
                            "If less than 4 are provided, repeat the last value.")
                    .defineList("colorStroke", () -> {
                        List<String> list = new ArrayList<>();
                        list.add("#FFC2D0D6");
                        list.add("#FFE7DAE5");
                        list.add("#FFCCDAC8");
                        list.add("#FFC8B9AC");
                        return list;
                    }, $ -> true);
            mTooltipCycle = builder.comment(
                            "The cycle time of tooltip border color in milliseconds. (0 = OFF)")
                    .defineInRange("borderCycleTime", 1000, TOOLTIP_BORDER_COLOR_ANIM_MIN,
                            TOOLTIP_BORDER_COLOR_ANIM_MAX);
            mTooltipWidth = builder.comment(
                            "The width of tooltip border, if rounded, in GUI Scale Independent Pixels.")
                    .defineInRange("borderWidth", 4 / 3d, TOOLTIP_BORDER_WIDTH_MIN, TOOLTIP_BORDER_WIDTH_MAX);
            mTooltipRadius = builder.comment(
                            "The corner radius of tooltip border, if rounded, in GUI Scale Independent Pixels.")
                    .defineInRange("cornerRadius", 4d, TOOLTIP_CORNER_RADIUS_MIN, TOOLTIP_CORNER_RADIUS_MAX);
            /*mTooltipDuration = builder.comment(
                            "The duration of tooltip alpha animation in milliseconds. (0 = OFF)")
                    .defineInRange("animationDuration", 0, ANIM_DURATION_MIN, ANIM_DURATION_MAX);*/
            mTooltipShadowRadius = builder.comment(
                            "The shadow radius of tooltip, if rounded, in GUI Scale Independent Pixels.",
                            "No impact on performance.")
                    .defineInRange("shadowRadius", 10.0, TOOLTIP_SHADOW_RADIUS_MIN, TOOLTIP_SHADOW_RADIUS_MAX);
            mTooltipShadowAlpha = builder.comment(
                            "The shadow opacity of tooltip, if rounded. No impact on performance.")
                    .defineInRange("shadowOpacity", 0.25, 0d, 1d);
            mAdaptiveTooltipColors = builder.comment(
                            "When true, tooltip border colors adapt to item's name and rarity.")
                    .define("adaptiveColors", true);
            mTooltipArrowScrollFactor = builder.comment(
                            "Amount to scroll the tooltip in response to a arrow key pressed event.")
                    .defineInRange("arrowScrollFactor", 60, TOOLTIP_ARROW_SCROLL_FACTOR_MIN,
                            TOOLTIP_ARROW_SCROLL_FACTOR_MAX);
            /*mTooltipLineWrapping = builder.comment(
                            "Provide line wrapping and optimization for tooltip components.")
                    .define("lineWrapping", true);*/

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
                    .defineEnum("windowMode", Config.Client.WindowMode.NORMAL);
            mUseNewGuiScale = builder.comment("Whether to replace vanilla GUI scale button to slider with tips.")
                    .define("useNewGuiScale", true);

            /*mSkipGLCapsError = builder.comment("UI renderer is disabled when the OpenGL capability test fails.",
                            "Sometimes the driver reports wrong values, you can enable this to ignore it.")
                    .define("skipGLCapsError", false);
            mShowGLCapsError = builder.comment("A dialog popup is displayed when the OpenGL capability test fails.",
                            "Set to false to not show it. This is ignored when skipGLCapsError=true")
                    .define("showGLCapsError", true);*/

            /*mRemoveSignature = builder.comment("Remove signature of chat messages and commands.")
                    .define("removeSignature", false);*/
            mRemoveTelemetry = builder.comment("Remove telemetry event of client behaviors.")
                    .define("removeTelemetry", false);
            /*mSecurePublicKey = builder.comment("Don't report profile's public key to server.")
                    .define("securePublicKey", false);*/
            mEmojiShortcodes = builder.comment(
                            "Allow Slack or Discord shortcodes to replace Unicode Emoji Sequences in chat.")
                    .define("emojiShortcodes", true);

            builder.pop();

            builder.comment("View system config, currently not working.")
                    .push("view");

            mForceRtl = builder.comment("Force layout direction to RTL, otherwise, the current Locale setting.")
                    .define("forceRtl", false);
            mFontScale = builder.comment("The global font scale used with sp units.")
                    .defineInRange("fontScale", 1.0d, FONT_SCALE_MIN, FONT_SCALE_MAX);
            mScrollbarSize = builder.comment("Default scrollbar size in dips.")
                    .defineInRange("scrollbarSize", ViewConfiguration.SCROLL_BAR_SIZE, 0, 1024);
            mTouchSlop = builder.comment("Distance a touch can wander before we think the user is scrolling in dips.")
                    .defineInRange("touchSlop", ViewConfiguration.TOUCH_SLOP, 0, 1024);
            mMinScrollbarTouchTarget = builder.comment("Minimum size of the touch target for a scrollbar in dips.")
                    .defineInRange("minScrollbarTouchTarget", ViewConfiguration.MIN_SCROLLBAR_TOUCH_TARGET, 0, 1024);
            mMinimumFlingVelocity = builder.comment("Minimum velocity to initiate a fling in dips per second.")
                    .defineInRange("minimumFlingVelocity", ViewConfiguration.MINIMUM_FLING_VELOCITY, 0, 32767);
            mMaximumFlingVelocity = builder.comment("Maximum velocity to initiate a fling in dips per second.")
                    .defineInRange("maximumFlingVelocity", ViewConfiguration.MAXIMUM_FLING_VELOCITY, 0, 32767);
            mOverscrollDistance = builder.comment("Max distance in dips to overscroll for edge effects.")
                    .defineInRange("overscrollDistance", ViewConfiguration.OVERSCROLL_DISTANCE, 0, 1024);
            mOverflingDistance = builder.comment("Max distance in dips to overfling for edge effects.")
                    .defineInRange("overflingDistance", ViewConfiguration.OVERFLING_DISTANCE, 0, 1024);
            mVerticalScrollFactor = builder.comment(
                            "Amount to scroll in response to a vertical scroll event, in dips per axis value.")
                    .defineInRange("verticalScrollFactor", (double) ViewConfiguration.VERTICAL_SCROLL_FACTOR,
                            0, 1024);
            mHorizontalScrollFactor = builder.comment(
                            "Amount to scroll in response to a horizontal scroll event, in dips per axis value.")
                    .defineInRange("horizontalScrollFactor", (double) ViewConfiguration.HORIZONTAL_SCROLL_FACTOR,
                            0, 1024);

            builder.pop();


            builder.comment("Font Config")
                    .push("font");

            // Segoe UI, Source Han Sans CN Medium, Noto Sans, Open Sans, San Francisco, Calibri,
            // Microsoft YaHei UI, STHeiti, SimHei, SansSerif
            mFirstFontFamily = builder.comment(
                            "The first font family to use. See fallbackFontFamilyList")
                    .define("firstFontFamily", "Source Han Sans CN Medium");
            mFallbackFontFamilyList = builder.comment(
                            "A set of fallback font families to determine the typeface to use.",
                            "The order is first > fallbacks. TrueType & OpenType are supported.",
                            "Each element can be one of the following two cases:",
                            "1) Name of registered font family, for instance: Segoe UI",
                            "2) Path of font files on your PC, for instance: /usr/shared/fonts/x.otf",
                            "Registered font families include:",
                            "1) OS builtin fonts.",
                            "2) Font files in fontRegistrationList.",
                            "3) Font files in '/resourcepacks' directory.",
                            "4) Font files under 'modernui:font' in resource packs.",
                            "Note that for TTC/OTC font, you should register it and select one of font families.",
                            "Otherwise, only the first font family from the TrueType/OpenType Collection will be used.",
                            "This is only read once when the game is loaded, you can reload via in-game GUI.")
                    .defineList("fallbackFontFamilyList", () -> {
                        List<String> list = new ArrayList<>();
                        list.add("Noto Sans");
                        list.add("Segoe UI Variable");
                        list.add("Segoe UI");
                        list.add("San Francisco");
                        list.add("Open Sans");
                        list.add("SimHei");
                        list.add("STHeiti");
                        list.add("Segoe UI Symbol");
                        list.add("mui-i18n-compat");
                        return list;
                    }, s -> true);
            mFontRegistrationList = builder.comment(
                            "A set of additional font files (or directories) to register.",
                            "For TrueType/OpenType Collections, all contained font families will be registered.",
                            "Registered fonts can be referenced in Modern UI and Minecraft (Modern Text Engine).",
                            "For example, \"E:/Fonts\" means all font files in that directory will be registered.",
                            "System requires random access to these files, you should not remove them while running.",
                            "This is only read once when the game is loaded, i.e. registration.")
                    .defineList("fontRegistrationList", ArrayList::new, s -> true);
            mUseColorEmoji = builder.comment(
                            "Whether to use Google Noto Color Emoji, otherwise grayscale emoji (faster).",
                            "See Unicode 15.0 specification for details on how this affects text layout.")
                    .define("useColorEmoji", true);

            builder.pop();
        }

        public void saveAsync() {
            Util.ioPool().execute(() -> CLIENT_SPEC.save());
        }

        public void saveAndReloadAsync() {
            Util.ioPool().execute(() -> CLIENT_SPEC.save());
            reload();
        }

        private void reload() {
            Config.CLIENT.reload();

            // scan and preload typeface in background thread
            // only on Forge, config is loaded when reloading resources
            // on NeoForge 1.21 and Fabric, config is preloaded and loadTypeface() is trigger by FontResourceManager
            //ModernUIClient.getInstance().loadTypeface();
        }
    }

    /**
     * Common config exists on physical client and physical server once game loaded.
     * They are independent and do not sync with each other.
     */
    public static class Common {

        public final ModConfigSpec.BooleanValue developerMode;
        public final ModConfigSpec.IntValue oneTimeEvents;

        public final ModConfigSpec.BooleanValue autoShutdown;

        public final ModConfigSpec.ConfigValue<List<? extends String>> shutdownTimes;

        private Common(@Nonnull ModConfigSpec.Builder builder) {
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

        public void saveAndReloadAsync() {
            Util.ioPool().execute(() -> COMMON_SPEC.save());
            reload();
        }

        private void reload() {
            ModernUIMod.sDeveloperMode = developerMode.get();
            ServerHandler.INSTANCE.determineShutdownTime();
        }
    }

    public static class Text {

        public static final float BASE_FONT_SIZE_MIN = 6.5f;
        public static final float BASE_FONT_SIZE_MAX = 9.5f;
        public static final float BASELINE_MIN = 4;
        public static final float BASELINE_MAX = 10;
        public static final float SHADOW_OFFSET_MIN = 0.2f;
        public static final float SHADOW_OFFSET_MAX = 2;
        public static final float OUTLINE_OFFSET_MIN = 0.2f;
        public static final float OUTLINE_OFFSET_MAX = 2;
        public static final int LIFESPAN_MIN = 2;
        public static final int LIFESPAN_MAX = 15;
        /*public static final int REHASH_MIN = 0;
        public static final int REHASH_MAX = 2000;*/

        //final ModConfigSpec.BooleanValue globalRenderer;
        public final ModConfigSpec.BooleanValue mAllowShadow;
        public final ModConfigSpec.BooleanValue mFixedResolution;
        public final ModConfigSpec.DoubleValue mBaseFontSize;
        public final ModConfigSpec.DoubleValue mBaselineShift;
        public final ModConfigSpec.DoubleValue mShadowOffset;
        public final ModConfigSpec.DoubleValue mOutlineOffset;
        //public final ModConfigSpec.BooleanValue mSuperSampling;
        //public final ModConfigSpec.BooleanValue mAlignPixels;
        public final ModConfigSpec.IntValue mCacheLifespan;
        //public final ModConfigSpec.IntValue mRehashThreshold;
        public final ModConfigSpec.EnumValue<Config.Text.TextDirection> mTextDirection;
        //public final ModConfigSpec.BooleanValue mBitmapReplacement;
        //public final ModConfigSpec.BooleanValue mUseDistanceField;
        //public final ModConfigSpec.BooleanValue mUseVanillaFont;
        public final ModConfigSpec.BooleanValue mUseTextShadersInWorld;
        public final ModConfigSpec.EnumValue<Config.Text.DefaultFontBehavior> mDefaultFontBehavior;
        public final ModConfigSpec.ConfigValue<List<? extends String>> mDefaultFontRuleSet;
        public final ModConfigSpec.BooleanValue mUseComponentCache;
        public final ModConfigSpec.BooleanValue mAllowAsyncLayout;
        public final ModConfigSpec.EnumValue<Config.Text.LineBreakStyle> mLineBreakStyle;
        public final ModConfigSpec.EnumValue<Config.Text.LineBreakWordStyle> mLineBreakWordStyle;
        public final ModConfigSpec.BooleanValue mSmartSDFShaders;
        public final ModConfigSpec.BooleanValue mComputeDeviceFontSize;
        public final ModConfigSpec.BooleanValue mAllowSDFTextIn2D;
        public final ModConfigSpec.BooleanValue mTweakExperienceText;

        public final ModConfigSpec.BooleanValue mAntiAliasing;
        public final ModConfigSpec.BooleanValue mLinearMetrics;
        public final ModConfigSpec.IntValue mMinPixelDensityForSDF;
        public final ModConfigSpec.BooleanValue mLinearSamplingA8Atlas;
        //public final ModConfigSpec.BooleanValue mLinearSampling;

        //private final ModConfigSpec.BooleanValue antiAliasing;
        //private final ModConfigSpec.BooleanValue highPrecision;
        //private final ModConfigSpec.BooleanValue enableMipmap;
        //private final ModConfigSpec.IntValue mipmapLevel;
        //private final ModConfigSpec.IntValue resolutionLevel;
        //private final ModConfigSpec.IntValue defaultFontSize;

        private Text(@Nonnull ModConfigSpec.Builder builder) {
            builder.comment("Text Engine Config")
                    .push("text");

            /*globalRenderer = builder.comment(
                    "Apply Modern UI font renderer (including text layouts) to the entire game rather than only " +
                            "Modern UI itself.")
                    .define("globalRenderer", true);*/
            mAllowShadow = builder.comment(
                            "Allow text renderer to drop shadow, setting to false can improve performance.")
                    .define("allowShadow", true);
            mFixedResolution = builder.comment(
                            "Fix resolution level at 2. When the GUI scale increases, the resolution level remains.",
                            "Then GUI scale should be even numbers (2, 4, 6...), based on Minecraft GUI system.",
                            "If your fonts are not bitmap fonts, then you should keep this setting false.")
                    .define("fixedResolution", false);
            mBaseFontSize = builder.comment(
                            "Control base font size, in GUI scaled pixels. The default and vanilla value is 8.",
                            "For bitmap fonts, 8 represents a glyph size of 8x or 16x if fixed resolution.",
                            "This option only applies to TrueType fonts.")
                    .defineInRange("baseFontSize", (double) TextLayoutProcessor.DEFAULT_BASE_FONT_SIZE,
                            BASE_FONT_SIZE_MIN, BASE_FONT_SIZE_MAX);
            mBaselineShift = builder.comment(
                            "Control vertical baseline for vanilla text layout, in GUI scaled pixels.",
                            "The vanilla default value is 7.")
                    .defineInRange("baselineShift", (double) TextLayout.STANDARD_BASELINE_OFFSET,
                            BASELINE_MIN, BASELINE_MAX);
            mShadowOffset = builder.comment(
                            "Control the text shadow offset for vanilla text rendering, in GUI scaled pixels.")
                    .defineInRange("shadowOffset", 0.5, SHADOW_OFFSET_MIN, SHADOW_OFFSET_MAX);
            mOutlineOffset = builder.comment(
                            "Control the text outline offset for vanilla text rendering, in GUI scaled pixels.")
                    .defineInRange("outlineOffset", 0.5, OUTLINE_OFFSET_MIN, OUTLINE_OFFSET_MAX);
            /*mSuperSampling = builder.comment(
                            "Super sampling can make the text more smooth with large font size or in the 3D world.",
                            "But it makes the glyph edge too blurry and difficult to read.")
                    .define("superSampling", false);*/
            /*mAlignPixels = builder.comment(
                            "Enable to make each glyph pixel-aligned in text layout in screen-space.",
                            "Text rendering may be better with bitmap fonts / fixed resolution / linear sampling.")
                    .define("alignPixels", false);*/
            mCacheLifespan = builder.comment(
                            "Set the recycle time of layout cache in seconds, using least recently used algorithm.")
                    .defineInRange("cacheLifespan", 6, LIFESPAN_MIN, LIFESPAN_MAX);
            /*mRehashThreshold = builder.comment("Set the rehash threshold of layout cache")
                    .defineInRange("rehashThreshold", 100, REHASH_MIN, REHASH_MAX);*/
            mTextDirection = builder.comment(
                            "The bidirectional text heuristic algorithm. The default is FirstStrong (Locale).",
                            "This will affect which BiDi algorithm to use during text layout.")
                    .defineEnum("textDirection", Config.Text.TextDirection.FIRST_STRONG);
            /*mBitmapReplacement = builder.comment(
                            "Whether to use bitmap replacement for non-Emoji character sequences. Restart is required.")
                    .define("bitmapReplacement", false);*/
            /*mUseVanillaFont = builder.comment(
                            "Whether to use Minecraft default bitmap font for basic Latin letters.")
                    .define("useVanillaFont", false);*/
            mUseTextShadersInWorld = builder.comment(
                            "Whether to use Modern UI text rendering pipeline in 3D world.",
                            "Disabling this means that SDF text and rendering optimization are no longer effective.",
                            "But text rendering can be compatible with OptiFine Shaders and Iris Shaders.",
                            "This does not affect text rendering in GUI.",
                            "This option only applies to TrueType fonts.")
                    .define("useTextShadersInWorld", true);
            /*mUseDistanceField = builder.comment(
                            "Enable to use distance field for text rendering in 3D world.",
                            "It improves performance with deferred rendering and sharpens when doing 3D transform.")
                    .define("useDistanceField", true);*/
            mDefaultFontBehavior = builder.comment(
                            "For \"minecraft:default\" font, should we keep some glyph providers of them?",
                            "Ignore All: Only use Modern UI typeface list.",
                            "Keep ASCII: Include minecraft:font/ascii.png, minecraft:font/accented.png, " +
                                    "minecraft:font/nonlatin_european.png",
                            "Keep Other: Include providers other than ASCII and Unicode font.",
                            "Keep All: Include all except Unicode font.",
                            "Only Include: Only include providers that specified by defaultFontRuleSet.",
                            "Only Exclude: Only exclude providers that specified by defaultFontRuleSet.")
                    .defineEnum("defaultFontBehavior", Config.Text.DefaultFontBehavior.ONLY_EXCLUDE);
            mDefaultFontRuleSet = builder.comment(
                            "Used when defaultFontBehavior is either ONLY_INCLUDE or ONLY_EXCLUDE.",
                            "This specifies a set of regular expressions to match the glyph provider name.",
                            "For bitmap providers, this is the texture path without 'textures/'.",
                            "For TTF providers, this is the TTF file path without 'font/'.",
                            "For space providers, this is \"font_name / minecraft:space\",",
                            "where font_name is font definition path without 'font/'.")
                    .defineList("defaultFontRuleSet", () -> {
                        List<String> rules = new ArrayList<>();
                        // three vanilla fonts
                        rules.add("^minecraft:font\\/(nonlatin_european|accented|ascii|" +
                                // four added by CFPA Minecraft-Mod-Language-Package
                                "element_ideographs|cjk_punctuations|ellipsis|2em_dash)\\.png$");
                        // the vanilla space
                        rules.add("^minecraft:include\\/space \\/ minecraft:space$");
                        // CozyUI by 05
                        rules.add("^minecraft:font\\/(mcsans|emoji)_05_00\\d.png$");
                        return rules;
                    }, s -> true);
            mUseComponentCache = builder.comment(
                            "Whether to use text component object as hash key to lookup in layout cache.",
                            "If you find that Modern UI text rendering is not compatible with some mods,",
                            "you can disable this option for compatibility, but this will decrease performance a bit.",
                            "Modern UI will use another cache strategy if this is disabled.")
                    .define("useComponentCache", !ModernUIMod.isUntranslatedItemsLoaded());
            mAllowAsyncLayout = builder.comment(
                            "Allow text layout to be computed from background threads (not cached).",
                            "Otherwise, block the current thread and wait for main thread.")
                    .define("allowAsyncLayout", true);
            mLineBreakStyle = builder.comment(
                            "See CSS line-break property, https://developer.mozilla.org/en-US/docs/Web/CSS/line-break")
                    .defineEnum("lineBreakStyle", Config.Text.LineBreakStyle.AUTO);
            mLineBreakWordStyle = builder
                    .defineEnum("lineBreakWordStyle", Config.Text.LineBreakWordStyle.AUTO);
            mSmartSDFShaders = builder.comment(
                            "When enabled, Modern UI will compute texel density in device-space to determine whether " +
                                    "to use SDF text or bilinear sampling.",
                            "This feature requires GLSL 400 or has no effect.",
                            "This generally decreases performance but provides better rendering quality.",
                            "This option only applies to TrueType fonts. May not be compatible with OptiFine.")
                    .define("smartSDFShaders", true);
            mComputeDeviceFontSize = builder.comment(
                            "When rendering in 2D, this option allows Modern UI to exactly compute font size in " +
                                    "device-space from the current coordinate transform matrix.",
                            "This provides perfect text rendering for scaling-down texts in vanilla, but may increase" +
                                    " GPU memory usage.",
                            "When disabled, Modern UI will use SDF text rendering if appropriate.",
                            "This option only applies to TrueType fonts.")
                    .define("computeDeviceFontSize", true);
            mAllowSDFTextIn2D = builder.comment(
                            "When enabled, Modern UI will use SDF text rendering if appropriate.",
                            "Otherwise, it uses nearest-neighbor or bilinear sampling based on texel density.",
                            "This option only applies to TrueType fonts.")
                    .define("allowSDFTextIn2D", true);
            mTweakExperienceText = builder.comment(
                            "When enabled, the outline of the experience level text will be tweaked.")
                    .define("tweakExperienceText", true);
            mAntiAliasing = builder.comment(
                            "Control the anti-aliasing of raw glyph rasterization.")
                    .define("antiAliasing", true);
            mLinearMetrics = builder.comment(
                            "Control the FreeType linear metrics and font hinting of raw glyph metrics.",
                            "Disable if on low-res monitor; enable for linear text.")
                    .define("linearMetrics", true);
            mMinPixelDensityForSDF = builder.comment(
                            "Control the minimum pixel density for SDF text and text in 3D world rendering.",
                            "This value will be no less than current GUI scale.",
                            "Recommend setting a higher value on high-res monitor and powerful PC hardware.")
                    .defineInRange("minPixelDensityForSDF", TextLayoutEngine.DEFAULT_MIN_PIXEL_DENSITY_FOR_SDF,
                            4, 10);
            mLinearSamplingA8Atlas = builder.comment(
                            "Enable linear sampling for A8 font atlases with mipmaps, mag filter will be always",
                            "NEAREST. We prefer computeDeviceFontSize and allowSDFTextIn2D, then setting this to",
                            "false can improve performance. If either of the above two is false or Shaders are active,",
                            "then setting this to true can improve readability.")
                    .define("linearSamplingA8Atlas", false);
            /*mLinearSampling = builder.comment(
                            "Enable linear sampling for font atlases with mipmaps, mag filter will be always NEAREST.",
                            "If your fonts are not bitmap fonts, then you should keep this setting true.")
                    .define("linearSampling", true);*/
            /*antiAliasing = builder.comment(
                    "Enable font anti-aliasing.")
                    .define("antiAliasing", true);
            highPrecision = builder.comment(
                    "Enable high precision rendering, this is very useful especially when the font is very small.")
                    .define("highPrecision", true);
            enableMipmap = builder.comment(
                    "Enable mipmap for font textures, this makes font will not be blurred when scaling down.")
                    .define("enableMipmap", true);
            mipmapLevel = builder.comment(
                    "The mipmap level for font textures.")
                    .defineInRange("mipmapLevel", 4, 0, 4);*/
            /*resolutionLevel = builder.comment(
                    "The resolution level of font, higher levels would better work with high resolution monitors.",
                    "Reference: 1 (Standard, 1.5K Fullscreen), 2 (High, 2K~3K Fullscreen), 3 (Ultra, 4K Fullscreen)",
                    "This should match your GUI scale. Scale -> Level: [1,2] -> 1; [3,4] -> 2; [5,) -> 3")
                    .defineInRange("resolutionLevel", 2, 1, 3);*/
            /*defaultFontSize = builder.comment(
                    "The default font size for texts with no size specified. (deprecated, to be removed)")
                    .defineInRange("defaultFontSize", 16, 12, 20);*/

            builder.pop();
        }

        public void saveAsync() {
            Util.ioPool().execute(() -> TEXT_SPEC.save());
        }

        public void saveAndReloadAsync() {
            Util.ioPool().execute(() -> TEXT_SPEC.save());
            reload();
        }

        void reload() {
            Config.TEXT.reload();
        }
    }

    // server config is available when integrated server or dedicated server started
    // if on dedicated server, all config data will sync to remote client via network
    /*public static class Server {

        private Server(@Nonnull ModConfigSpec.Builder builder) {

        }

        private void reload() {

        }
    }*/
}
