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
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.font.GlyphManager;
import icyllis.modernui.graphics.text.LineBreakConfig;
import icyllis.modernui.mc.text.*;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.View;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.ParallelDispatchEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nonnull;
import java.util.Locale;

import static icyllis.modernui.ModernUI.*;

/**
 * Modern UI Text for Minecraft can bootstrap independently.
 */
public final class ModernUIText {

    static {
        assert FMLEnvironment.dist.isClient();
    }

    public static Config CONFIG;
    private static ForgeConfigSpec CONFIG_SPEC;

    private ModernUIText() {
    }

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().register(ModernUIText.class);
    }

    public static void initConfig() {
        FMLPaths.getOrCreateGameRelativePath(FMLPaths.CONFIGDIR.get().resolve(ModernUI.NAME_CPT));
        ModContainer mod = ModLoadingContext.get().getActiveContainer();

        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CONFIG = new Config(builder);
        CONFIG_SPEC = builder.build();
        mod.addConfig(new ModConfig(ModConfig.Type.CLIENT, CONFIG_SPEC, mod, ModernUI.NAME_CPT + "/text.toml"));

        FMLJavaModLoadingContext.get().getModEventBus().addListener(CONFIG::onReload);
    }

    /*@SubscribeEvent
    static void registerResourceListener(@Nonnull RegisterClientReloadListenersEvent event) {
        // language may reload, cause TranslatableComponent changed, so clear layout cache
        event.registerReloadListener(TextLayoutEngine.getInstance()::reload);
        LOGGER.debug(MARKER, "Registered language reload listener");
    }*/

    @SubscribeEvent
    static void setupClient(@Nonnull FMLClientSetupEvent event) {
        // preload text engine, note that this event is fired after client config first load
        // so that the typeface config is valid
        Minecraft.getInstance().execute(ModernUI::getSelectedTypeface);
        MuiForgeApi.addOnWindowResizeListener((width, height, newScale, oldScale) -> {
            if (Core.getRenderThread() != null && newScale != oldScale) {
                TextLayoutEngine.getInstance().reload();
            }
        });
        MuiForgeApi.addOnDebugDumpListener(pw -> {
            pw.print("TextLayoutEngine: ");
            pw.print("CacheCount=" + TextLayoutEngine.getInstance().getCacheCount());
            int memorySize = TextLayoutEngine.getInstance().getCacheMemorySize();
            pw.print(", CacheSize=" + TextUtils.binaryCompact(memorySize) + " (" + memorySize + " bytes)");
            memorySize = TextLayoutEngine.getInstance().getEmojiAtlasMemorySize();
            pw.println(", EmojiAtlasSize=" + TextUtils.binaryCompact(memorySize) + " (" + memorySize + " bytes)");
            GlyphManager.getInstance().dumpInfo(pw);
        });
        MinecraftForge.EVENT_BUS.register(EventHandler.class);
        LOGGER.info(MARKER, "Loaded modern text engine");
    }

    @SubscribeEvent
    static void onParallelDispatch(@Nonnull ParallelDispatchEvent event) {
        // since Forge EVENT_BUS is not started yet, we should manually maintain that
        // in case of some mods render texts before entering main menu
        event.enqueueWork(() -> TextLayoutEngine.getInstance().clear());
    }

    /*@OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    static void registerShaders(@Nonnull RegisterShadersEvent event) {
        ResourceProvider provider = event.getResourceManager();
        try {
            event.registerShader(new ShaderInstance(provider, TextRenderType.SHADER_RL,
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), TextRenderType::setShader);
            event.registerShader(new ShaderInstance(provider, TextRenderType.SHADER_SEE_THROUGH_RL,
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), TextRenderType::setShaderSeeThrough);
        } catch (IOException e) {
            throw new RuntimeException("Bad shaders", e);
        }
    }*/

    static class EventHandler {

        /*@SubscribeEvent
        static void onClientChat(@Nonnull ClientChatEvent event) {
            final String msg = event.getMessage();
            if (CONFIG.mEmojiShortcodes.get() && !msg.startsWith("/")) {
                final TextLayoutEngine engine = TextLayoutEngine.getInstance();
                final Matcher matcher = TextLayoutEngine.EMOJI_SHORTCODE_PATTERN.matcher(msg);

                StringBuilder builder = null;
                int lastEnd = 0;
                while (matcher.find()) {
                    if (builder == null) {
                        builder = new StringBuilder();
                    }
                    int st = matcher.start();
                    int en = matcher.end();
                    String emoji = null;
                    if (en - st > 2) {
                        emoji = engine.lookupEmojiShortcode(msg.substring(st + 1, en - 1));
                    }
                    if (emoji != null) {
                        builder.append(msg, lastEnd, st);
                        builder.append(emoji);
                    } else {
                        builder.append(msg, lastEnd, en);
                    }
                    lastEnd = en;
                }
                if (builder != null) {
                    builder.append(msg, lastEnd, msg.length());
                    //event.setMessage(builder.toString());
                }
            }
        }*/

        @SubscribeEvent
        static void onClientTick(@Nonnull TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                TextLayoutEngine.getInstance().onEndClientTick();
            }
        }
    }

    public static class Config {

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

        //final ForgeConfigSpec.BooleanValue globalRenderer;
        public final ForgeConfigSpec.BooleanValue mAllowShadow;
        public final ForgeConfigSpec.BooleanValue mFixedResolution;
        public final ForgeConfigSpec.DoubleValue mBaseFontSize;
        public final ForgeConfigSpec.DoubleValue mBaselineShift;
        public final ForgeConfigSpec.DoubleValue mShadowOffset;
        public final ForgeConfigSpec.DoubleValue mOutlineOffset;
        //public final ForgeConfigSpec.BooleanValue mSuperSampling;
        //public final ForgeConfigSpec.BooleanValue mAlignPixels;
        public final ForgeConfigSpec.IntValue mCacheLifespan;
        //public final ForgeConfigSpec.IntValue mRehashThreshold;
        public final ForgeConfigSpec.EnumValue<TextDirection> mTextDirection;
        public final ForgeConfigSpec.BooleanValue mUseColorEmoji;
        //public final ForgeConfigSpec.BooleanValue mBitmapReplacement;
        public final ForgeConfigSpec.BooleanValue mEmojiShortcodes;
        //public final ForgeConfigSpec.BooleanValue mUseDistanceField;
        //public final ForgeConfigSpec.BooleanValue mUseVanillaFont;
        public final ForgeConfigSpec.BooleanValue mUseTextShadersInWorld;
        public final ForgeConfigSpec.EnumValue<DefaultFontBehavior> mDefaultFontBehavior;
        public final ForgeConfigSpec.BooleanValue mUseComponentCache;
        public final ForgeConfigSpec.BooleanValue mAllowAsyncLayout;
        public final ForgeConfigSpec.EnumValue<LineBreakStyle> mLineBreakStyle;
        public final ForgeConfigSpec.EnumValue<LineBreakWordStyle> mLineBreakWordStyle;
        public final ForgeConfigSpec.BooleanValue mSmartSDFShaders;
        public final ForgeConfigSpec.BooleanValue mComputeDeviceFontSize;
        public final ForgeConfigSpec.BooleanValue mAllowSDFTextIn2D;

        //private final ForgeConfigSpec.BooleanValue antiAliasing;
        //private final ForgeConfigSpec.BooleanValue highPrecision;
        //private final ForgeConfigSpec.BooleanValue enableMipmap;
        //private final ForgeConfigSpec.IntValue mipmapLevel;
        //private final ForgeConfigSpec.IntValue resolutionLevel;
        //private final ForgeConfigSpec.IntValue defaultFontSize;

        private Config(@Nonnull ForgeConfigSpec.Builder builder) {
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
                    .defineInRange("baseFontSize", TextLayoutProcessor.DEFAULT_BASE_FONT_SIZE,
                            BASE_FONT_SIZE_MIN, BASE_FONT_SIZE_MAX);
            mBaselineShift = builder.comment(
                            "Control vertical baseline for vanilla text layout, in GUI scaled pixels.",
                            "The vanilla default value is 7.")
                    .defineInRange("baselineShift", TextLayout.STANDARD_BASELINE_OFFSET,
                            BASELINE_MIN, BASELINE_MAX);
            mShadowOffset = builder.comment(
                            "Control the text shadow offset for vanilla text rendering, in GUI scaled pixels.")
                    .defineInRange("shadowOffset", 0.8, SHADOW_OFFSET_MIN, SHADOW_OFFSET_MAX);
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
                            "The bidirectional text heuristic algorithm.",
                            "This will affect which BiDi algorithm to use during text layout.")
                    .defineEnum("textDirection", TextDirection.FIRST_STRONG);
            mUseColorEmoji = builder.comment(
                            "Whether to use Google Noto Color Emoji, otherwise grayscale emoji (faster).",
                            "See Unicode 15.0 specification for details on how this affects text layout.")
                    .define("useColorEmoji", true);
            /*mBitmapReplacement = builder.comment(
                            "Whether to use bitmap replacement for non-Emoji character sequences. Restart is required.")
                    .define("bitmapReplacement", false);*/
            mEmojiShortcodes = builder.comment(
                            "Allow Slack or Discord shortcodes to replace Unicode Emoji Sequences in chat.")
                    .define("emojiShortcodes", true);
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
                            "For DEFAULT_FONT and UNIFORM_FONT, should we keep some bitmap providers of them?",
                            "Ignore All: Use selectedTypeface only.",
                            "Keep ASCII: Include minecraft:font/ascii.png, minecraft:font/accented.png, " +
                                    "minecraft:font/nonlatin_european.png",
                            "Keep Other: Include providers in minecraft:font/default.json other than Keep ASCII and " +
                                    "Unicode font.",
                            "Keep All: Include all except Unicode font.")
                    .defineEnum("defaultFontBehavior", DefaultFontBehavior.KEEP_OTHER);
            mUseComponentCache = builder.comment(
                            "Whether to use text component object as hash key to lookup in layout cache.",
                            "If you find that Modern UI text rendering is not compatible with some mods,",
                            "you can disable this option for compatibility, but this will decrease performance a bit.",
                            "Modern UI will use another cache strategy if this is disabled.")
                    .define("useComponentCache", true);
            mAllowAsyncLayout = builder.comment(
                            "Allow text layout to be computed from non-main threads.",
                            "Otherwise, block on current thread.")
                    .define("allowAsyncLayout", true);
            mLineBreakStyle = builder.comment(
                            "See CSS line-break property, https://developer.mozilla.org/en-US/docs/Web/CSS/line-break")
                    .defineEnum("lineBreakStyle", LineBreakStyle.AUTO);
            mLineBreakWordStyle = builder
                    .defineEnum("lineBreakWordStyle", LineBreakWordStyle.AUTO);
            mSmartSDFShaders = builder.comment(
                            "When enabled, Modern UI will compute texel density in device-space to determine whether " +
                                    "to use SDF text or bilinear sampling.",
                            "This feature requires GLSL 400 or has no effect.",
                            "This generally decreases performance but provides better rendering quality.",
                            "This option only applies to TrueType fonts. May not be compatible with OptiFine.")
                    .define("smartSDFShaders", !ModernUIForge.isOptiFineLoaded());
            // OK, this doesn't work well with OptiFine
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
            Util.ioPool().execute(() -> CONFIG_SPEC.save());
        }

        public void saveAndReloadAsync() {
            Util.ioPool().execute(() -> {
                CONFIG_SPEC.save();
                reload();
            });
        }

        void onReload(@Nonnull ModConfigEvent event) {
            final IConfigSpec<?> spec = event.getConfig().getSpec();
            if (spec == CONFIG_SPEC) {
                reload();
                LOGGER.debug(MARKER, "Text config reloaded with {}", event.getClass().getSimpleName());
            }
        }

        void reload() {
            boolean reload = false;
            boolean reloadAll = false;
            ModernTextRenderer.sAllowShadow = mAllowShadow.get();
            if (TextLayoutEngine.sFixedResolution != mFixedResolution.get()) {
                TextLayoutEngine.sFixedResolution = mFixedResolution.get();
                reload = true;
            }
            if (TextLayoutProcessor.sBaseFontSize != mBaseFontSize.get()) {
                TextLayoutProcessor.sBaseFontSize = mBaseFontSize.get().floatValue();
                reloadAll = true;
            }
            TextLayout.sBaselineOffset = mBaselineShift.get().floatValue();
            ModernTextRenderer.sShadowOffset = mShadowOffset.get().floatValue();
            ModernTextRenderer.sOutlineOffset = mOutlineOffset.get().floatValue();
            /*if (TextLayoutProcessor.sAlignPixels != mAlignPixels.get()) {
                TextLayoutProcessor.sAlignPixels = mAlignPixels.get();
                reload = true;
            }*/
            TextLayoutEngine.sCacheLifespan = mCacheLifespan.get();
            /*TextLayoutEngine.sRehashThreshold = mRehashThreshold.get();*/
            if (TextLayoutEngine.sTextDirection != mTextDirection.get().key) {
                TextLayoutEngine.sTextDirection = mTextDirection.get().key;
                reload = true;
            }
            if (TextLayoutEngine.sUseColorEmoji != mUseColorEmoji.get()) {
                TextLayoutEngine.sUseColorEmoji = mUseColorEmoji.get();
                reload = true;
            }
            TextLayoutEngine.sUseEmojiShortcodes = mEmojiShortcodes.get();
            if (TextLayoutEngine.sUseTextShadersInWorld != mUseTextShadersInWorld.get()) {
                TextLayoutEngine.sUseTextShadersInWorld = mUseTextShadersInWorld.get();
                reload = true;
            }
            if (TextLayoutEngine.sDefaultFontBehavior != mDefaultFontBehavior.get().key) {
                TextLayoutEngine.sDefaultFontBehavior = mDefaultFontBehavior.get().key;
                reloadAll = true;
            }
            TextLayoutEngine.sAllowAsyncLayout = mAllowAsyncLayout.get();
            if (TextLayoutProcessor.sLbStyle != mLineBreakStyle.get().key) {
                TextLayoutProcessor.sLbStyle = mLineBreakStyle.get().key;
                reload = true;
            }
            if (TextLayoutProcessor.sLbWordStyle != mLineBreakWordStyle.get().key) {
                TextLayoutProcessor.sLbWordStyle = mLineBreakWordStyle.get().key;
                reload = true;
            }

            final boolean smartShaders = mSmartSDFShaders.get();
            Minecraft.getInstance().submit(() -> TextRenderType.toggleSDFShaders(smartShaders));

            ModernTextRenderer.sComputeDeviceFontSize = mComputeDeviceFontSize.get();
            ModernTextRenderer.sAllowSDFTextIn2D = mAllowSDFTextIn2D.get();

            if (reloadAll) {
                Minecraft.getInstance().submit(() -> TextLayoutEngine.getInstance().reloadAll());
            } else if (reload) {
                Minecraft.getInstance().submit(() -> TextLayoutEngine.getInstance().reload());
            }
            /*GlyphManagerForge.sPreferredFont = preferredFont.get();
            GlyphManagerForge.sAntiAliasing = antiAliasing.get();
            GlyphManagerForge.sHighPrecision = highPrecision.get();
            GlyphManagerForge.sEnableMipmap = enableMipmap.get();
            GlyphManagerForge.sMipmapLevel = mipmapLevel.get();*/
            //GlyphManager.sResolutionLevel = resolutionLevel.get();
            //TextLayoutEngine.sDefaultFontSize = defaultFontSize.get();
        }

        public enum TextDirection {
            FIRST_STRONG(View.TEXT_DIRECTION_FIRST_STRONG, "FirstStrong"),
            ANY_RTL(View.TEXT_DIRECTION_ANY_RTL, "AnyRTL-LTR"),
            LTR(View.TEXT_DIRECTION_LTR, "LTR"),
            RTL(View.TEXT_DIRECTION_RTL, "RTL"),
            LOCALE(View.TEXT_DIRECTION_LOCALE, "Locale"),
            FIRST_STRONG_LTR(View.TEXT_DIRECTION_FIRST_STRONG_LTR, "FirstStrong-LTR"),
            FIRST_STRONG_RTL(View.TEXT_DIRECTION_FIRST_STRONG_RTL, "FirstStrong-RTL");

            private final int key;
            private final String text;

            TextDirection(int key, String text) {
                this.key = key;
                this.text = text;
            }

            @Override
            public String toString() {
                return text;
            }
        }

        public enum DefaultFontBehavior {
            IGNORE_ALL(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_IGNORE_ALL),
            KEEP_ASCII(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_KEEP_ASCII),
            KEEP_OTHER(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_KEEP_OTHER),
            KEEP_ALL(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_KEEP_ALL);

            private final int key;

            DefaultFontBehavior(int key) {
                this.key = key;
            }

            @Nonnull
            @Override
            public String toString() {
                return I18n.get("modernui.defaultFontBehavior." + name().toLowerCase(Locale.ROOT));
            }
        }

        public enum LineBreakStyle {
            AUTO(LineBreakConfig.LINE_BREAK_STYLE_NONE, "Auto"),
            LOOSE(LineBreakConfig.LINE_BREAK_STYLE_LOOSE, "Loose"),
            NORMAL(LineBreakConfig.LINE_BREAK_STYLE_NORMAL, "Normal"),
            STRICT(LineBreakConfig.LINE_BREAK_STYLE_STRICT, "Strict");

            private final int key;
            private final String text;

            LineBreakStyle(int key, String text) {
                this.key = key;
                this.text = text;
            }

            @Override
            public String toString() {
                return text;
            }
        }

        public enum LineBreakWordStyle {
            AUTO(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE, "Auto"),
            PHRASE(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE, "Phrase-based");

            private final int key;
            private final String text;

            LineBreakWordStyle(int key, String text) {
                this.key = key;
                this.text = text;
            }

            @Override
            public String toString() {
                return text;
            }
        }
    }
}
