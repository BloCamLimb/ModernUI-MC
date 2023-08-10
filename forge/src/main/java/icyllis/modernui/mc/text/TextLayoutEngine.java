/*
 * Modern UI.
 * Copyright (C) 2019-2022 BloCamLimb. All rights reserved.
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

package icyllis.modernui.mc.text;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.arc3d.engine.Engine;
import icyllis.modernui.ModernUI;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.font.*;
import icyllis.modernui.graphics.text.*;
import icyllis.modernui.mc.forge.*;
import icyllis.modernui.mc.text.mixin.AccessFontManager;
import icyllis.modernui.mc.text.mixin.MixinClientLanguage;
import icyllis.modernui.text.*;
import icyllis.modernui.util.Pools;
import icyllis.modernui.view.View;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.font.GlyphVector;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static icyllis.modernui.ModernUI.*;

/**
 * Modern UI text engine for Minecraft. This class performs Unicode text layout (and measurement),
 * and manage several caches for the layout results. They also provide rendering information and
 * additional information to support several Unicode text algorithms for Minecraft text system.
 * This class also handles font and emoji resources used in Minecraft.
 * <p>
 * The engine is designed only for Minecraft, for its special GUI scaled coordinates,
 * tree representation of styled texts, and rendering systems.
 *
 * @author BloCamLimb
 * @since 2.0
 */
public class TextLayoutEngine implements PreparableReloadListener {

    /**
     * Instance on main/render thread
     */
    private static volatile TextLayoutEngine sInstance;

    static {
        assert FMLEnvironment.dist.isClient();
    }

    /**
     * Config values
     */
    //public static int sDefaultFontSize;
    public static volatile boolean sFixedResolution = false;
    //public static volatile boolean sSuperSampling = false;
    public static volatile int sTextDirection = View.TEXT_DIRECTION_FIRST_STRONG;
    /*
     * Time in seconds to recycle a render node in the cache.
     */
    //public static volatile int sCacheLifespan = 12;
    //public static volatile int sRehashThreshold = 100;
    /*
     * Config value to use distance field text in 3D world.
     */
    //public static volatile boolean sCanUseDistanceField;

    /**
     * Dynamic value controlling whether to use distance field at current stage.
     * Distance field benefits in 3D world, but it looks bad in 2D UI,
     * unless the text is scaled and it is large. SDF use font size 4x base size.
     *
     * @see icyllis.modernui.mc.text.mixin.MixinGameRenderer
     */
    public static boolean sCurrentInWorldRendering;

    /**
     * Whether to use our rendering pipeline in 3D world?
     * False for compatibility with OptiFine shaders.
     */
    public static volatile boolean sUseTextShadersInWorld = true;

    public static volatile boolean sUseColorEmoji = true;

    /**
     * Matches Slack emoji shortcode.
     */
    public static final Pattern EMOJI_SHORTCODE_PATTERN =
            Pattern.compile("(:(\\w|\\+|-)+:)(?=|[!.?]|$)");

    /**
     * Maps ASCII to ChatFormatting, including all cases.
     */
    private static final ChatFormatting[] FORMATTING_TABLE = new ChatFormatting[128];

    static {
        for (ChatFormatting f : ChatFormatting.values()) {
            FORMATTING_TABLE[f.getChar()] = f;
            FORMATTING_TABLE[Character.toUpperCase(f.getChar())] = f;
        }
    }

    /*
     * Draw and cache all glyphs of all fonts needed
     * Lazy-loading because we are waiting for render system to initialize
     */
    //private GlyphManagerForge glyphManager;

    /*
     * A cache of recently seen strings to their fully laid-out state, complete with color changes and texture
     * coordinates of
     * all pre-rendered glyph images needed to display this string. The weakRefCache holds strong references to the Key
     * objects used in this map.
     */
    //private WeakHashMap<Key, Entry> stringCache = new WeakHashMap<>();

    /*
     * Every String passed to the public renderString() function is added to this WeakHashMap. As long as As long as
     * Minecraft
     * continues to hold a strong reference to the String object (i.e. from TileEntitySign and ChatLine) passed here,
     *  the
     * weakRefCache map will continue to hold a strong reference to the Key object that said strings all map to
     * (multiple strings
     * in weakRefCache can map to a single Key if those strings only differ by their ASCII digits).
     */
    //private WeakHashMap<String, Key> weakRefCache = new WeakHashMap<>();

    /*private final Cache<VanillaTextKey, TextRenderNode> stringCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.SECONDS)
            .build();*/

    /**
     * Also computes per-cluster advances.
     *
     * @see TextLayoutProcessor
     */
    public static final int COMPUTE_ADVANCES = 0x1;

    /**
     * Also computes Unicode line boundaries.
     *
     * @see TextLayoutProcessor
     */
    public static final int COMPUTE_LINE_BOUNDARIES = 0x4;


    /**
     * Use {@link ModernUI#getSelectedTypeface()} only.
     */
    public static final int DEFAULT_FONT_BEHAVIOR_IGNORE_ALL = 0;
    /**
     * Include
     * minecraft:font/ascii.png
     * minecraft:font/accented.png
     * minecraft:font/nonlatin_european.png
     */
    public static final int DEFAULT_FONT_BEHAVIOR_KEEP_ASCII = 0x1;
    /**
     * Include providers in minecraft:font/default.json other than
     * DEFAULT_FONT_BEHAVIOR_KEEP_ASCII and Unicode font.
     */
    public static final int DEFAULT_FONT_BEHAVIOR_KEEP_OTHER = 0x2;
    /**
     * Include all except Unicode font.
     */
    public static final int DEFAULT_FONT_BEHAVIOR_KEEP_ALL = 0x3;

    /**
     * For {@link net.minecraft.client.Minecraft#DEFAULT_FONT} and
     * {@link net.minecraft.client.Minecraft#UNIFORM_FONT},
     * should we keep some bitmap providers of them?
     *
     * @see StandardFontSet
     */
    public static volatile int sDefaultFontBehavior =
            DEFAULT_FONT_BEHAVIOR_KEEP_OTHER; // <- bit mask


    public static volatile boolean sUseComponentCache = true;


    private final GlyphManager mGlyphManager;
    /**
     * Temporary Key object re-used for lookups with stringCache.get(). Using a temporary object like this avoids the
     * overhead of allocating new objects in the critical rendering path. Of course, new Key objects are always created
     * when adding a mapping to stringCache.
     */
    private final VanillaLayoutKey mVanillaLookupKey = new VanillaLayoutKey();
    private Map<VanillaLayoutKey, TextLayout> mVanillaCache = new HashMap<>();

    /**
     * For styled texts.
     *
     * @see #sUseComponentCache
     */
    private Map<BaseComponent, TextLayout> mComponentCache = new HashMap<>();

    /**
     * For deeply-processed texts.
     */
    private final FormattedLayoutKey.Lookup mFormattedLayoutKey = new FormattedLayoutKey.Lookup();
    private Map<FormattedLayoutKey, TextLayout> mFormattedCache = new HashMap<>();

    /**
     * Render thread layout proc.
     */
    private final TextLayoutProcessor mProcessor = new TextLayoutProcessor(this);

    /**
     * Background thread layout proc.
     */
    private final Pools.Pool<TextLayoutProcessor> mProcessorPool = Pools.newSynchronizedPool(3);

    private record FontStrikeDesc(Font font, int resLevel) {
    }

    /**
     * For fast digit replacement and obfuscated char rendering.
     * Map from 'derived font' to 'ASCII 33(!) to 126(~) characters with their standard advances and relative advances'.
     */
    private final Map<FontStrikeDesc, FastCharSet> mFastCharMap = new HashMap<>();
    // it's necessary to cache the lambda
    private final Function<FontStrikeDesc, FastCharSet> mCacheFastChars = this::cacheFastChars;

    /**
     * All the fonts to use. Maps typeface name to FontCollection.
     */
    private final HashMap<ResourceLocation, FontCollection> mFontCollections = new HashMap<>();

    private FontCollection mDefaultFontCollection;

    private EmojiFont mEmojiFont;

    /**
     * Gui scale = 4.
     */
    public static final int BITMAP_SCALE = 4;
    public static final int EMOJI_BASE_SIZE = 9;
    public static final int EMOJI_SIZE = EMOJI_BASE_SIZE * BITMAP_SCALE;
    public static final int EMOJI_SIZE_LARGE = EMOJI_SIZE * 2;

    /**
     * Emoji sequence to sprite index (used as glyph code in emoji atlas).
     */
    private final ArrayList<String> mEmojiFiles = new ArrayList<>();
    /**
     * Shortcodes to Emoji char sequences.
     */
    private final HashMap<String, String> mEmojiShortcodes = new HashMap<>();

    /**
     * The emoji texture atlas.
     */
    private GLFontAtlas mEmojiAtlas;
    private ByteBuffer mEmojiBuffer;

    /**
     * Determine font size. Integer.
     */
    private volatile int mResLevel;
    /**
     * Text direction.
     */
    private TextDirectionHeuristic mTextDirectionHeuristic = TextDirectionHeuristics.FIRSTSTRONG_LTR;

    // vanilla's font manager, used only for compatibility
    private FontManager mVanillaFontManager;

    private boolean mColorEmojiUsed = false;

    private final ModernTextRenderer mTextRenderer;
    private final ModernStringSplitter mStringSplitter;

    /*
     * Remove all formatting code even though it's invalid {@link #getFormattingByCode(char)} == null
     */
    //private static final Pattern FORMATTING_REMOVE_PATTERN = Pattern.compile("\u00a7.");

    /*
     * True if digitGlyphs[] has been assigned and cacheString() can begin replacing all digits with '0' in the string.
     */
    //private boolean digitGlyphsReady = false;

    private int mTimer;

    private TextLayoutEngine() {
        /* StringCache is created by the main game thread; remember it for later thread safety checks */
        //mainThread = Thread.currentThread();

        /* Pre-cache the ASCII digits to allow for fast glyph substitution */
        //cacheDigitGlyphs();

        // init first
        mGlyphManager = GlyphManager.getInstance();
        // When OpenGL texture ID changed (atlas resized), we want to use the new first atlas
        // for deferred rendering, we need to clear any existing TextRenderType instances
        mGlyphManager.addAtlasInvalidationCallback(TextRenderType::clear);
        // init
        reload();
        // events
        MinecraftForge.EVENT_BUS.register(this);

        mTextRenderer = new ModernTextRenderer(this);
        mStringSplitter = new ModernStringSplitter(this);
    }

    /**
     * Get the global instance, each call will return the same instance.
     *
     * @return the instance
     */
    @Nonnull
    public static TextLayoutEngine getInstance() {
        if (sInstance == null) {
            synchronized (TextLayoutEngine.class) {
                if (sInstance == null) {
                    sInstance = new TextLayoutEngine();
                }
            }
        }
        return sInstance;
    }

    /**
     * @return the glyph manager
     */
    @Nonnull
    public GlyphManager getGlyphManager() {
        return mGlyphManager;
    }

    @Nonnull
    public ModernTextRenderer getTextRenderer() {
        return mTextRenderer;
    }

    @Nonnull
    public ModernStringSplitter getStringSplitter() {
        return mStringSplitter;
    }

    /**
     * Cleanup layout cache.
     */
    public void clear() {
        int count = getCacheCount();
        mVanillaCache.clear();
        mComponentCache.clear();
        mFormattedCache.clear();
        // Create new HashMap so that the internal hashtable of old maps are released as well
        mVanillaCache = new HashMap<>();
        mComponentCache = new HashMap<>();
        mFormattedCache = new HashMap<>();
        // Metrics change with resolution level
        mFastCharMap.clear();
        // Just clear TextRenderType instances, font textures are remained
        TextRenderType.clear();
        if (count > 0) {
            LOGGER.debug(MARKER, "Cleanup {} text layout entries", count);
        }
    }

    /**
     * Reload layout engine.
     * Called when resolution level or language changed. This will call {@link #clear()}.
     */
    @RenderThread
    public void reload() {
        clear();

        var ctx = ModernUI.getInstance();
        final int scale;
        if (ctx != null) {
            scale = Math.round(ctx.getResources()
                    .getDisplayMetrics().density * 2);
        } else {
            scale = 2;
        }
        final int oldLevel = mResLevel;
        if (sFixedResolution) {
            // make font size to 16 (8 * 2)
            mResLevel = 2;
        } else {
            // Note max font size is 96, actual font size will be (baseFontSize * resLevel) in Minecraft
            /*if (!sSuperSampling || !GLFontAtlas.sLinearSampling) {
                mResLevel = Math.min(scale, 9);
            } else if (scale > 2) {
                mResLevel = Math.min((int) Math.ceil(scale * 4 / 3f), 12);
            } else {
                mResLevel = scale;
            }*/
            mResLevel = Math.min(scale, MuiForgeApi.MAX_GUI_SCALE);
        }

        Locale locale = ModernUI.getSelectedLocale();
        boolean layoutRtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
        mTextDirectionHeuristic = switch (sTextDirection) {
            case View.TEXT_DIRECTION_ANY_RTL -> TextDirectionHeuristics.ANYRTL_LTR;
            case View.TEXT_DIRECTION_LTR -> TextDirectionHeuristics.LTR;
            case View.TEXT_DIRECTION_RTL -> TextDirectionHeuristics.RTL;
            case View.TEXT_DIRECTION_LOCALE -> TextDirectionHeuristics.LOCALE;
            case View.TEXT_DIRECTION_FIRST_STRONG_LTR -> TextDirectionHeuristics.FIRSTSTRONG_LTR;
            case View.TEXT_DIRECTION_FIRST_STRONG_RTL -> TextDirectionHeuristics.FIRSTSTRONG_RTL;
            default -> layoutRtl ? TextDirectionHeuristics.FIRSTSTRONG_RTL :
                    TextDirectionHeuristics.FIRSTSTRONG_LTR;
        };

        if (oldLevel != 0) {
            // inject after first load
            injectAdditionalFonts(/*force*/false);

            if (mVanillaFontManager != null) {
                var fontSets = ((AccessFontManager) mVanillaFontManager).getFontSets();
                for (var fontSet : fontSets.values()) {
                    if (fontSet instanceof StandardFontSet standardFontSet) {
                        standardFontSet.invalidateCache(mResLevel);
                    }
                }
            }
        }

        if (oldLevel == 0) {
            LOGGER.info(MARKER, "Loaded text layout engine, res level: {}, locale: {}, layout RTL: {}",
                    mResLevel, locale, layoutRtl);
        } else {
            LOGGER.info(MARKER, "Reloaded text layout engine, res level: {} to {}, locale: {}, layout RTL: {}",
                    oldLevel, mResLevel, locale, layoutRtl);
        }
    }

    /**
     * Reload both glyph manager and layout engine.
     * Called when any resource changed. This will call {@link #reload()}.
     */
    public void reloadAll() {
        mGlyphManager.reload();
        LOGGER.info(MARKER, "Reloaded glyph manager");
        if (mEmojiAtlas != null) {
            mEmojiAtlas.close();
            mEmojiAtlas = null;
            LOGGER.info(MARKER, "Reloaded emoji atlas");
        }
        LayoutCache.clear();
        injectAdditionalFonts(/*force*/true); // we force it here, so normal reload() won't inject again
        reload();
    }


    //// START Resource Reloading

    // we redirect vanilla's Latin and Unicode font,
    // but we still bake their bitmap glyphs for some cases,
    // like Display Board from Create mod
    @Nonnull
    public TextLayoutEngine injectFontManager(@Nonnull FontManager manager) {
        mVanillaFontManager = manager;
        return this;
    }

    @RenderThread
    private void injectAdditionalFonts(boolean force) {
        if (mColorEmojiUsed != sUseColorEmoji || force) {
            LinkedHashSet<FontFamily> defaultFonts = new LinkedHashSet<>();
            if (!sUseColorEmoji) {
                if (sDefaultFontBehavior == DEFAULT_FONT_BEHAVIOR_IGNORE_ALL) {
                    mFontCollections.remove(Minecraft.DEFAULT_FONT);
                } else {
                    populateDefaultFonts(defaultFonts, sDefaultFontBehavior);
                    defaultFonts.addAll(ModernUI.getSelectedTypeface().getFamilies());
                    mFontCollections.put(Minecraft.DEFAULT_FONT,
                            new FontCollection(defaultFonts.toArray(new FontFamily[0])));
                }
                mFontCollections.remove(Minecraft.UNIFORM_FONT);
                mColorEmojiUsed = false;
            } else if (sUseColorEmoji && mEmojiFont != null) {
                LinkedHashSet<FontFamily> unicodeFonts = new LinkedHashSet<>();

                FontFamily colorEmojiFamily = new FontFamily(mEmojiFont);

                defaultFonts.add(colorEmojiFamily);
                populateDefaultFonts(defaultFonts, sDefaultFontBehavior);
                defaultFonts.addAll(ModernUI.getSelectedTypeface().getFamilies());

                unicodeFonts.add(colorEmojiFamily);
                unicodeFonts.addAll(ModernUI.getSelectedTypeface().getFamilies());

                mFontCollections.put(Minecraft.DEFAULT_FONT,
                        new FontCollection(defaultFonts.toArray(new FontFamily[0])));
                mFontCollections.put(Minecraft.UNIFORM_FONT,
                        new FontCollection(unicodeFonts.toArray(new FontFamily[0])));
                mColorEmojiUsed = true;
            } else {
                if (sDefaultFontBehavior == DEFAULT_FONT_BEHAVIOR_IGNORE_ALL) {
                    mFontCollections.remove(Minecraft.DEFAULT_FONT);
                } else {
                    populateDefaultFonts(defaultFonts, sDefaultFontBehavior);
                    defaultFonts.addAll(ModernUI.getSelectedTypeface().getFamilies());
                    mFontCollections.put(Minecraft.DEFAULT_FONT,
                            new FontCollection(defaultFonts.toArray(new FontFamily[0])));
                }
                mFontCollections.remove(Minecraft.UNIFORM_FONT);
                mColorEmojiUsed = false;
            }
            if (force && mVanillaFontManager != null) {
                var fontSets = ((AccessFontManager) mVanillaFontManager).getFontSets();
                if (fontSets.get(Minecraft.DEFAULT_FONT) instanceof StandardFontSet sfs) {
                    sfs.reload(getFontCollection(Minecraft.DEFAULT_FONT), mResLevel);
                }
                if (fontSets.get(Minecraft.UNIFORM_FONT) instanceof StandardFontSet sfs) {
                    sfs.reload(getFontCollection(Minecraft.UNIFORM_FONT), mResLevel);
                }
            }
        }
    }

    private void populateDefaultFonts(Set<FontFamily> set, int behavior) {
        if (behavior == DEFAULT_FONT_BEHAVIOR_IGNORE_ALL) {
            return;
        }
        for (FontFamily family : mDefaultFontCollection.getFamilies()) {
            switch (family.getFamilyName()) {
                case "minecraft:font/nonlatin_european.png",
                        "minecraft:font/accented.png",
                        "minecraft:font/ascii.png",
                        "minecraft:default / minecraft:space" -> {
                    if ((behavior & DEFAULT_FONT_BEHAVIOR_KEEP_ASCII) != 0) {
                        set.add(family);
                    }
                }
                default -> {
                    if ((behavior & DEFAULT_FONT_BEHAVIOR_KEEP_OTHER) != 0) {
                        set.add(family);
                    }
                }
            }
        }
    }

    /**
     * Called when resources reloaded.
     */
    @Nonnull
    @Override
    public CompletableFuture<Void> reload(@Nonnull PreparationBarrier preparationBarrier,
                                          @Nonnull ResourceManager resourceManager,
                                          @Nonnull ProfilerFiller preparationProfiler,
                                          @Nonnull ProfilerFiller reloadProfiler,
                                          @Nonnull Executor preparationExecutor,
                                          @Nonnull Executor reloadExecutor) {
        preparationProfiler.startTick();
        preparationProfiler.endTick();
        return prepareResources(resourceManager, preparationExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(results -> applyResources(results, reloadProfiler), reloadExecutor);
    }

    private static final class LoadResults {
        volatile Map<ResourceLocation, FontCollection> mFontCollections;
        volatile EmojiFont mEmojiFont;
        volatile List<String> mEmojiFiles;
        volatile Map<String, String> mEmojiShortcodes;
    }

    // ASYNC
    @Nonnull
    private CompletableFuture<LoadResults> prepareResources(@Nonnull ResourceManager resourceManager,
                                                            @Nonnull Executor preparationExecutor) {
        final var results = new LoadResults();
        final var loadFonts = CompletableFuture.runAsync(() ->
                        loadFonts(resourceManager, results),
                preparationExecutor);
        final var loadEmojis = CompletableFuture.runAsync(() -> {
            loadEmojis(resourceManager, results);
            loadShortcodes(resourceManager, results);
        }, preparationExecutor);
        return CompletableFuture.allOf(loadFonts, loadEmojis)
                .thenApply(__ -> results);
    }

    // SYNC
    private void applyResources(@Nonnull LoadResults results, @Nonnull ProfilerFiller reloadProfiler) {
        reloadProfiler.startTick();
        reloadProfiler.push("reload");
        // close bitmaps if never baked
        for (var fontCollection : mFontCollections.values()) {
            for (var family : fontCollection.getFamilies()) {
                if (family.getClosestMatch(FontPaint.NORMAL) instanceof BitmapFont bitmapFont) {
                    bitmapFont.close();
                }
            }
        }
        // reload fonts
        mFontCollections.clear();
        mFontCollections.putAll(results.mFontCollections);
        mDefaultFontCollection = mFontCollections.get(Minecraft.DEFAULT_FONT);
        if (mDefaultFontCollection == null) {
            throw new IllegalStateException("Default font failed to load");
        }
        // vanilla compatibility
        if (mVanillaFontManager != null) {
            var fontSets = ((AccessFontManager) mVanillaFontManager).getFontSets();
            fontSets.values().forEach(FontSet::close);
            fontSets.clear();
            var textureManager = Minecraft.getInstance().textureManager;
            mFontCollections.forEach((fontName, fontCollection) -> {
                var fontSet = new StandardFontSet(textureManager, fontName);
                fontSet.reload(fontCollection, mResLevel);
                fontSets.put(fontName, fontSet);
            });
        } else {
            LOGGER.warn(MARKER, "Where is font manager?");
        }
        // reload emojis
        mEmojiFiles.clear();
        mEmojiShortcodes.clear();
        mEmojiFiles.addAll(results.mEmojiFiles);
        mEmojiShortcodes.putAll(results.mEmojiShortcodes);
        mEmojiFont = results.mEmojiFont;
        // reload the whole engine
        reloadAll();
        reloadProfiler.pop();
        reloadProfiler.endTick();
    }

    private static boolean isUnicodeFont(@Nonnull ResourceLocation name) {
        if (name.equals(Minecraft.UNIFORM_FONT)) {
            return true;
        }
        if (name.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            return name.getPath().equals("include/unifont");
        }
        return false;
    }

    // ASYNC
    private void loadFonts(@Nonnull ResourceManager resources, @Nonnull LoadResults results) {
        final var gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        final var map = new HashMap<ResourceLocation, Set<FontFamily>>();
        for (var location : resources.listResources("font",
                res -> res.endsWith(".json"))) {
            var path = location.getPath();
            // XXX: remove prefix 'font/' and suffix '.json' to get the font name
            var name = new ResourceLocation(location.getNamespace(),
                    path.substring(5, path.length() - 5));
            if (isUnicodeFont(name)) {
                continue;
            }
            var set = map.computeIfAbsent(name, n -> new LinkedHashSet<>());
            try {
                for (var resource : resources.getResources(location)) {
                    try (var reader = new BufferedReader(
                            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                        var providers = GsonHelper.getAsJsonArray(Objects.requireNonNull(
                                GsonHelper.fromJson(gson, reader, JsonObject.class)), "providers");
                        for (int i = 0; i < providers.size(); i++) {
                            var metadata = GsonHelper.convertToJsonObject(
                                    providers.get(i), "providers[" + i + "]");
                            var type = GsonHelper.getAsString(metadata, "type");
                            switch (type) {
                                case "bitmap" -> {
                                    var bitmapFont = BitmapFont.create(metadata, resources);
                                    set.add(
                                            new FontFamily(bitmapFont)
                                    );
                                }
                                case "ttf" -> {
                                    if (metadata.has("shift")) {
                                        LOGGER.info(MARKER, "Ignore 'shift' of providers[{}] in font '{}'",
                                                i, name);
                                    }
                                    if (metadata.has("skip")) {
                                        LOGGER.info(MARKER, "Ignore 'skip' of providers[{}] in font '{}'",
                                                i, name);
                                    }
                                    set.add(createTTF(metadata, resources));
                                }
                                default -> LOGGER.info(MARKER, "Unknown provider type '{}' in font '{}'",
                                        type, name);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn(MARKER, "Failed to load font '{}'", name, e);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn(MARKER, "Failed to load font '{}'", name, e);
            }
            LOGGER.info(MARKER, "Loaded font resource: '{}', font set: [{}]", location,
                    set.stream().map(FontFamily::getFamilyName).collect(Collectors.joining(",")));
        }
        results.mFontCollections = map.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new FontCollection(e.getValue().toArray(new FontFamily[0])))
                );
    }

    @Nonnull
    private static FontFamily createTTF(JsonObject metadata, ResourceManager resources) {
        var file = new ResourceLocation(GsonHelper.getAsString(metadata, "file"));
        var location = new ResourceLocation(file.getNamespace(), "font/" + file.getPath());
        try (var resource = resources.getResource(location)) {
            return FontFamily.createFamily(resource.getInputStream(), false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ASYNC
    private void loadEmojis(@Nonnull ResourceManager resources,
                            @Nonnull LoadResults results) {
        final var map = new Object2IntOpenHashMap<CharSequence>();
        final var files = new ArrayList<String>();
        CYCLE:
        for (var image : resources.listResources("emoji",
                res -> res.endsWith(".png"))) {
            var path = image.getPath().split("/");
            if (path.length == 0) {
                continue;
            }
            var fileName = path[path.length - 1];
            var codes = fileName.substring(0, fileName.length() - 4).split("_");
            int length = codes.length;
            if (length == 0) {
                continue;
            }
            // double the size, we may add vs16
            var cps = new int[length << 1];
            int n = 0;
            for (int i = 0; i < length; i++) {
                try {
                    int c = Integer.parseInt(codes[i], 16);
                    if (!Character.isValidCodePoint(c)) {
                        continue CYCLE;
                    }
                    boolean ec = Emoji.isEmoji(c);
                    boolean ecc = isEmoji_Unicode15_workaround(c);
                    if (i == 0 && !ec && !ecc) {
                        continue CYCLE;
                    }
                    cps[n++] = c;
                    // require vs16 for color emoji, otherwise grayscale
                    if (ec && !Emoji.isEmojiPresentation(c)) {
                        cps[n++] = Emoji.VARIATION_SELECTOR_16;
                    }
                } catch (NumberFormatException e) {
                    continue CYCLE;
                }
            }
            var sequence = new String(cps, 0, n);
            if (!map.containsKey(sequence)) {
                map.put(sequence, map.size() + 1);
                files.add(fileName);
            }
        } // CYCLE end
        LOGGER.info(MARKER, "Scanned emoji map size: {}",
                map.size());
        results.mEmojiFiles = files;
        if (!files.isEmpty()) {
            IntOpenHashSet coverage = new IntOpenHashSet();
            _populateEmojiFontCoverage_DONT_COMPILE_(coverage);
            results.mEmojiFont = new EmojiFont("Google Noto Color Emoji",
                    coverage, EMOJI_BASE_SIZE * BITMAP_SCALE,
                    TextLayout.STANDARD_BASELINE_OFFSET * BITMAP_SCALE,
                    (int) (0.5 * BITMAP_SCALE + 0.5),
                    TextLayoutProcessor.DEFAULT_BASE_FONT_SIZE * BITMAP_SCALE, map);
        } else {
            LOGGER.info(MARKER, "No Emoji font was found");
        }
    }

    //FIXME Minecraft 1.20.1 still uses ICU-71.1, but Unicode 15 CLDR was added in ICU-72
    // remove once Minecraft's ICU updated
    static boolean isEmoji_Unicode15_workaround(int codePoint) {
        return codePoint == 0x1f6dc ||
                (0x1fa75 <= codePoint && codePoint <= 0x1fa77) ||
                codePoint == 0x1fa87 || codePoint == 0x1fa88 ||
                (0x1faad <= codePoint && codePoint <= 0x1faaf) ||
                (0x1fabb <= codePoint && codePoint <= 0x1fabd) ||
                codePoint == 0x1fabf ||
                codePoint == 0x1face || codePoint == 0x1facf ||
                codePoint == 0x1fada || codePoint == 0x1fadb ||
                codePoint == 0x1fae8 ||
                codePoint == 0x1faf7 || codePoint == 0x1faf8;
    }

    // ASYNC
    private void loadShortcodes(@Nonnull ResourceManager resources,
                                @Nonnull LoadResults results) {
        final var map = new HashMap<String, String>();
        try (var resource = resources.getResource(ModernUIForge.location("emoji_data.json"));
             var reader = new BufferedReader(
                     new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            for (var entry : new Gson().fromJson(reader, JsonArray.class)) {
                var row = entry.getAsJsonArray();
                var sequence = row.get(0).getAsString();
                // map shortcodes -> emoji sequence
                var shortcodes = row.get(2).getAsJsonArray();
                if (!shortcodes.isEmpty()) {
                    map.put(shortcodes.get(0).getAsString(), sequence);
                    for (int i = 1; i < shortcodes.size(); i++) {
                        map.putIfAbsent(shortcodes.get(i).getAsString(), sequence);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.info(MARKER, "Failed to load emoji data", e);
        }
        LOGGER.info(MARKER, "Scanned emoji shortcodes: {}",
                map.size());
        results.mEmojiShortcodes = map;
    }

    ////// END Resource Reloading


    public static int getResLevelForSDF(int resLevel) {
        return Math.max(resLevel, 4);
    }


    ////// START Cache Retrieval

    /**
     * Find or create a full text layout for the given text.
     *
     * @param text the source text, may contain formatting codes
     * @return the full layout for the text
     */
    @Nonnull
    public TextLayout lookupVanillaLayout(@Nonnull String text) {
        return lookupVanillaLayout(text, Style.EMPTY, 0);
    }

    /**
     * Find or create a full text layout for the given text.
     *
     * @param text  the source text, may contain formatting codes
     * @param style the base style
     * @return the full layout for the text
     */
    @Nonnull
    public TextLayout lookupVanillaLayout(@Nonnull String text, @Nonnull Style style) {
        return lookupVanillaLayout(text, style, 0);
    }

    /**
     * Find or create a full text layout for the given text.
     *
     * @param text  the source text, may contain formatting codes
     * @param style the base style
     * @return the full layout for the text
     */
    @Nonnull
    public TextLayout lookupVanillaLayout(@Nonnull String text, @Nonnull Style style,
                                          int computeFlags) {
        if (text.isEmpty()) {
            return TextLayout.EMPTY;
        }
        if (!RenderSystem.isOnRenderThread()) {
            TextLayoutProcessor proc = mProcessorPool.acquire();
            if (proc == null) {
                proc = new TextLayoutProcessor(this);
            }
            TextLayout layout = proc.createVanillaLayout(text, style, mResLevel, computeFlags);
            mProcessorPool.release(proc);
            return layout;
        }
        TextLayout layout = mVanillaCache.get(mVanillaLookupKey.update(text, style, mResLevel));
        int nowFlags = 0;
        if (layout == null ||
                ((nowFlags = layout.mComputedFlags) & computeFlags) != computeFlags) {
            layout = mProcessor.createVanillaLayout(text, style, mResLevel,
                    nowFlags | computeFlags);
            mVanillaCache.put(mVanillaLookupKey.copy(), layout);
            return layout;
        }
        return layout.get();
    }

    /**
     * Find or create a full text layout for the given formatted text, fast digit replacement
     * is not applicable. To perform bidi analysis, we must have the full text of all contents.
     *
     * @param text the text ancestor
     * @return the full layout for the text
     * @see FormattedTextWrapper
     */
    @Nonnull
    public TextLayout lookupFormattedLayout(@Nonnull FormattedText text) {
        return lookupFormattedLayout(text, Style.EMPTY, 0);
    }

    /**
     * Find or create a full text layout for the given formatted text, fast digit replacement
     * is not applicable. To perform bidi analysis, we must have the full text of all contents.
     *
     * @param text  the text ancestor
     * @param style the base style
     * @return the full layout for the text
     * @see FormattedTextWrapper
     */
    @Nonnull
    public TextLayout lookupFormattedLayout(@Nonnull FormattedText text, @Nonnull Style style) {
        return lookupFormattedLayout(text, style, 0);
    }

    /**
     * Find or create a full text layout for the given formatted text, fast digit replacement
     * is not applicable. To perform bidi analysis, we must have the full text of all contents.
     *
     * @param text  the text ancestor
     * @param style the base style
     * @return the full layout for the text
     * @see FormattedTextWrapper
     */
    @Nonnull
    public TextLayout lookupFormattedLayout(@Nonnull FormattedText text, @Nonnull Style style,
                                            int computeFlags) {
        if (text == TextComponent.EMPTY || text == FormattedText.EMPTY) {
            return TextLayout.EMPTY;
        }
        if (!RenderSystem.isOnRenderThread()) {
            TextLayoutProcessor proc = mProcessorPool.acquire();
            if (proc == null) {
                proc = new TextLayoutProcessor(this);
            }
            TextLayout layout = proc.createTextLayout(text, style, mResLevel, computeFlags);
            mProcessorPool.release(proc);
            return layout;
        }
        TextLayout layout;
        int nowFlags = 0;
        if (style.isEmpty() && sUseComponentCache &&
                text instanceof BaseComponent component) {
            layout = mComponentCache.get(component);
            if (layout == null ||
                    ((nowFlags = layout.mComputedFlags) & computeFlags) != computeFlags) {
                layout = mProcessor.createTextLayout(text, Style.EMPTY, mResLevel,
                        nowFlags | computeFlags);
                mComponentCache.put(component, layout);
                return layout;
            }
        } else {
            // the more complex case (multi-component)
            layout = mFormattedCache.get(mFormattedLayoutKey.update(text, style, mResLevel));
            if (layout == null ||
                    ((nowFlags = layout.mComputedFlags) & computeFlags) != computeFlags) {
                layout = mProcessor.createTextLayout(text, style, mResLevel,
                        nowFlags | computeFlags);
                mFormattedCache.put(mFormattedLayoutKey.copy(), layout);
                return layout;
            }
        }
        return layout.get();
    }

    /**
     * Find or create a full text layout for the given formatted text, fast digit replacement
     * is not applicable. To perform bidi analysis, we must have the full text of all contents.
     * Note: Modern UI removes Minecraft vanilla's BiDi reordering.
     * <p>
     * This method should only be used when the text is not originated from FormattedText.
     *
     * @param sequence the deeply-processed sequence
     * @return the full layout
     * @see FormattedTextWrapper
     */
    @Nonnull
    public TextLayout lookupFormattedLayout(@Nonnull FormattedCharSequence sequence) {
        return lookupFormattedLayout(sequence, 0);
    }

    /**
     * Find or create a full text layout for the given formatted text, fast digit replacement
     * is not applicable. To perform bidi analysis, we must have the full text of all contents.
     * Note: Modern UI removes Minecraft vanilla's BiDi reordering.
     * <p>
     * This method should only be used when the text is not originated from FormattedText.
     *
     * @param sequence the deeply-processed sequence
     * @return the full layout
     * @see FormattedTextWrapper
     */
    @Nonnull
    public TextLayout lookupFormattedLayout(@Nonnull FormattedCharSequence sequence,
                                            int computeFlags) {
        if (sequence == FormattedCharSequence.EMPTY) {
            return TextLayout.EMPTY;
        }
        if (!RenderSystem.isOnRenderThread()) {
            TextLayoutProcessor proc = mProcessorPool.acquire();
            if (proc == null) {
                proc = new TextLayoutProcessor(this);
            }
            TextLayout layout = proc.createSequenceLayout(sequence, mResLevel, computeFlags);
            mProcessorPool.release(proc);
            return layout;
        }
        int nowFlags = 0;
        // check if it's intercepted by Language.getVisualOrder()
        if (sequence instanceof FormattedTextWrapper) {
            FormattedText text = ((FormattedTextWrapper) sequence).mText;
            if (text == TextComponent.EMPTY || text == FormattedText.EMPTY) {
                return TextLayout.EMPTY;
            }
            TextLayout layout;
            if (sUseComponentCache &&
                    text instanceof BaseComponent component) {
                layout = mComponentCache.get(component);
                if (layout == null ||
                        ((nowFlags = layout.mComputedFlags) & computeFlags) != computeFlags) {
                    layout = mProcessor.createTextLayout(text, Style.EMPTY, mResLevel,
                            nowFlags | computeFlags);
                    mComponentCache.put(component, layout);
                    return layout;
                }
            } else {
                // the more complex case (multi-component)
                layout = mFormattedCache.get(mFormattedLayoutKey.update(text, Style.EMPTY, mResLevel));
                if (layout == null ||
                        ((nowFlags = layout.mComputedFlags) & computeFlags) != computeFlags) {
                    layout = mProcessor.createTextLayout(text, Style.EMPTY, mResLevel,
                            nowFlags | computeFlags);
                    mFormattedCache.put(mFormattedLayoutKey.copy(), layout);
                    return layout;
                }
            }
            return layout.get();
        } else {
            // the most complex case (multi-component)
            TextLayout layout = mFormattedCache.get(mFormattedLayoutKey.update(sequence, mResLevel));
            if (layout == null ||
                    ((nowFlags = layout.mComputedFlags) & computeFlags) != computeFlags) {
                layout = mProcessor.createSequenceLayout(sequence, mResLevel,
                        nowFlags | computeFlags);
                mFormattedCache.put(mFormattedLayoutKey.copy(), layout);
                return layout;
            }
            return layout.get();
        }
    }

    ////// END Cache Retrieval

    /**
     * Given a font name, returns the loaded font collection.
     * <p>
     * Cache will not be invalidated until resource reloading.
     *
     * @param fontName a font name
     * @return the font collection
     */
    @Nonnull
    public FontCollection getFontCollection(@Nonnull ResourceLocation fontName) {
        FontCollection fontCollection;
        return (fontCollection = mFontCollections.get(fontName)) != null
                ? fontCollection
                : ModernUI.getSelectedTypeface();
    }

    public void dumpBitmapFonts() {
        String basePath = Bitmap.saveDialogGet(
                Bitmap.SaveFormat.PNG, null, "BitmapFont");
        if (basePath != null) {
            // XXX: remove extension name
            basePath = basePath.substring(0, basePath.length() - 4);
        }
        int index = 0;
        for (var fc : mFontCollections.values()) {
            for (var family : fc.getFamilies()) {
                var font = family.getClosestMatch(FontPaint.NORMAL);
                if (font instanceof BitmapFont bmf) {
                    if (basePath != null) {
                        bmf.dumpAtlas(basePath + "_" + index + ".png");
                        index++;
                    } else {
                        bmf.dumpAtlas(null);
                    }
                }
            }
        }
    }

    @Nullable
    public BakedGlyph lookupGlyph(Font font, int fontSize, int glyphId) {
        if (font instanceof StandardFont standardFont) {
            java.awt.Font awtFont = standardFont.chooseFont(fontSize);
            return mGlyphManager.lookupGlyph(awtFont, glyphId);
        } else if (font == mEmojiFont) {
            if (glyphId == 0) {
                return null;
            }
            if (mEmojiAtlas == null) {
                mEmojiAtlas = new GLFontAtlas(Engine.MASK_FORMAT_ARGB);
                int size = (EMOJI_SIZE + GlyphManager.GLYPH_BORDER * 2);
                // RGBA, 4 bytes per pixel
                mEmojiBuffer = MemoryUtil.memCalloc(1, size * size * 4);
            }
            BakedGlyph glyph = mEmojiAtlas.getGlyph(glyphId);
            if (glyph != null && glyph.x == Short.MIN_VALUE) {
                return cacheEmoji(
                        glyphId,
                        mEmojiFiles.get(glyphId - 1),
                        mEmojiAtlas,
                        glyph
                );
            }
            return glyph;
        } else if (font instanceof BitmapFont bitmapFont) {
            // auto bake
            return bitmapFont.getGlyph(glyphId);
        }
        return null;
    }

    public int getCurrentTexture(Font font) {
        if (font == mEmojiFont) {
            return mEmojiAtlas.mTexture.get();
        }
        if (font instanceof BitmapFont bitmapFont) {
            return bitmapFont.getCurrentTexture();
        }
        return getStandardTexture();
    }

    public int getStandardTexture() {
        return mGlyphManager.getCurrentTexture(Engine.MASK_FORMAT_A8);
    }

    /**
     * Given a grapheme cluster, locate the color emoji's pre-rendered image in the emoji atlas and
     * return its cache entry. The entry stores the texture with the pre-rendered emoji image,
     * as well as the position and size of that image within the texture.
     *
     * @param buf   the text buffer
     * @param start the cluster start index (inclusive)
     * @param end   the cluster end index (exclusive)
     * @return the cached emoji sprite or null
     */
    @Deprecated
    @Nullable
    private BakedGlyph lookupEmoji(@Nonnull char[] buf, int start, int end) {
        return null;
    }

    /**
     * Lookup Emoji char sequence from shortcode.
     *
     * @param shortcode the shortcode, e.g. cheese
     * @return the Emoji sequence
     */
    @Nullable
    public String lookupEmojiShortcode(@Nonnull String shortcode) {
        return mEmojiShortcodes.get(shortcode);
    }

    public void dumpEmojiAtlas() {
        if (mEmojiAtlas != null) {
            String basePath = Bitmap.saveDialogGet(
                    Bitmap.SaveFormat.PNG, null, "EmojiAtlas");
            mEmojiAtlas.debug(basePath);
        }
    }

    public int getEmojiAtlasMemorySize() {
        if (mEmojiAtlas != null) {
            return mEmojiAtlas.getMemorySize();
        }
        return 0;
    }

    // bake emoji sprites
    @Nullable
    private BakedGlyph cacheEmoji(int id, @Nonnull String fileName,
                                  @Nonnull GLFontAtlas atlas, @Nonnull BakedGlyph glyph) {
        var location = new ResourceLocation(ModernUI.ID, "emoji/" + fileName);
        try (var resource = Minecraft.getInstance().getResourceManager().getResource(location);
             NativeImage image = NativeImage.read(resource.getInputStream())) {
            if ((image.getWidth() == EMOJI_SIZE && image.getHeight() == EMOJI_SIZE) ||
                    (image.getWidth() == EMOJI_SIZE_LARGE && image.getHeight() == EMOJI_SIZE_LARGE)) {
                long dst = MemoryUtil.memAddress(mEmojiBuffer);
                NativeImage subImage = null;
                if (image.getWidth() == EMOJI_SIZE_LARGE) {
                    // Down-sampling
                    subImage = MipmapGenerator.generateMipLevels(image, 1)[1];
                }
                long src = UIManager.IMAGE_PIXELS.getLong(subImage != null ? subImage : image);
                // Add 1 pixel transparent border to prevent texture bleeding
                // RGBA is 4 bytes per pixel
                long dstOff = (EMOJI_SIZE + GlyphManager.GLYPH_BORDER * 2 + GlyphManager.GLYPH_BORDER) * 4;
                for (int i = 0; i < EMOJI_SIZE; i++) {
                    long srcOff = (i * EMOJI_SIZE * 4);
                    MemoryUtil.memCopy(src + srcOff, dst + dstOff, EMOJI_SIZE * 4);
                    dstOff += (EMOJI_SIZE + GlyphManager.GLYPH_BORDER * 2) * 4;
                }
                if (subImage != null) {
                    subImage.close();
                }
                glyph.x = 0;
                glyph.y = -TextLayout.STANDARD_BASELINE_OFFSET * BITMAP_SCALE;
                glyph.width = EMOJI_SIZE;
                glyph.height = EMOJI_SIZE;
                atlas.stitch(glyph, dst);
                return glyph;
            } else {
                atlas.setNoPixels(id);
                LOGGER.warn(MARKER, "Emoji is not {}x or {}x: {}", EMOJI_SIZE, EMOJI_SIZE_LARGE, location);
                return null;
            }
        } catch (Exception e) {
            atlas.setNoPixels(id);
            LOGGER.warn(MARKER, "Failed to load emoji: {}", location, e);
            return null;
        }
    }

    /**
     * Ticks the caches and clear unused entries.
     */
    @SubscribeEvent
    void onTick(@Nonnull TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (mTimer == 0) {
                //int oldCount = getCacheCount();
                mVanillaCache.values().removeIf(TextLayout::tick);
                mComponentCache.values().removeIf(TextLayout::tick);
                mFormattedCache.values().removeIf(TextLayout::tick);
                /*if (oldCount >= sRehashThreshold) {
                    int newCount = getCacheCount();
                    if (newCount < sRehashThreshold) {
                        mVanillaCache = new HashMap<>(mVanillaCache);
                        mComponentCache = new HashMap<>(mComponentCache);
                        mFormattedCache = new HashMap<>(mFormattedCache);
                    }
                }*/
            }
            // convert ticks to seconds
            mTimer = (mTimer + 1) % 20;
        }
    }

    /**
     * @return the number of layout entries
     */
    public int getCacheCount() {
        return mVanillaCache.size() + mComponentCache.size() + mFormattedCache.size();
    }

    /**
     * @return measurable cache size in bytes
     */
    public int getCacheMemorySize() {
        int size = 0;
        for (var n : mVanillaCache.values()) {
            // key is a view, memory-less
            size += n.getMemorySize();
        }
        for (var n : mComponentCache.values()) {
            // key is a view, memory-less
            size += n.getMemorySize();
        }
        for (var e : mFormattedCache.entrySet()) {
            // key is backed ourselves
            size += e.getKey().getMemorySize();
            size += e.getValue().getMemorySize();
        }
        return size;
    }

    /**
     * Get the {@link ChatFormatting} by the given formatting code. Vanilla's method is
     * overwritten by this, see {@link icyllis.modernui.mc.text.mixin.MixinChatFormatting}.
     * <p>
     * Vanilla would create a new String from the char, call String.toLowerCase() and
     * String.charAt(0), search this char with a clone of ChatFormatting values. However,
     * it is unnecessary to consider non-ASCII compatibility, so we simplify it to a LUT.
     *
     * @param code c, case-insensitive
     * @return chat formatting, {@code null} if nothing
     * @see ChatFormatting#getByCode(char)
     */
    @Nullable
    public static ChatFormatting getFormattingByCode(char code) {
        return code < 128 ? FORMATTING_TABLE[code] : null;
    }

    /**
     * Returns current text direction algorithm.
     *
     * @return text dir
     */
    @Nonnull
    public TextDirectionHeuristic getTextDirectionHeuristic() {
        return mTextDirectionHeuristic;
    }

    /**
     * Lookup fast char glyph with given font.
     * The pair right is the offsetX to standard '0' advance alignment (already scaled by GUI factor).
     * Because we assume FAST digit glyphs are monospaced, no matter whether it's a monospaced font.
     *
     * @param font     derived font including style
     * @param resLevel resolution level
     * @return array of all fast char glyphs, and others, or null if not supported
     */
    @Nullable
    public FastCharSet lookupFastChars(@Nonnull Font font, int resLevel) {
        if (font == mEmojiFont) {
            // Emojis are supported for obfuscated rendering
            return null;
        }
        if (font instanceof BitmapFont) {
            resLevel = 1;
        }
        return mFastCharMap.computeIfAbsent(
                new FontStrikeDesc(font, resLevel),
                mCacheFastChars
        );
    }

    @Nullable
    private FastCharSet cacheFastChars(@Nonnull FontStrikeDesc desc) {
        java.awt.Font awtFont = null;
        BitmapFont bitmapFont = null;
        if (desc.font instanceof StandardFont) {
            int fontSize = TextLayoutProcessor.computeFontSize(desc.resLevel);
            awtFont = ((StandardFont) desc.font).chooseFont(fontSize);
        } else if (desc.font instanceof BitmapFont) {
            bitmapFont = (BitmapFont) desc.font;
        } else {
            return null;
        }

        // initial table
        BakedGlyph[] glyphs = new BakedGlyph[94]; // 126 - 33 + 1
        // normalized offsets
        float[] offsets = new float[glyphs.length];

        char[] chars = new char[1];
        int n = 0;

        // 48 to 57, always cache all digits for fast digit replacement
        for (int i = 0; i < 10; i++) {
            chars[0] = (char) ('0' + i);
            float advance;
            BakedGlyph glyph;
            // no text shaping
            if (awtFont != null) {
                GlyphVector vector = mGlyphManager.createGlyphVector(awtFont, chars);
                advance = (float) vector.getGlyphPosition(1).getX() / desc.resLevel;
                glyph = mGlyphManager.lookupGlyph(awtFont, vector.getGlyphCode(0));
                if (glyph == null && i == 0) {
                    LOGGER.warn(MARKER, awtFont + " does not support ASCII digits");
                    return null;
                }
                if (glyph == null) {
                    continue;
                }
            } else {
                var gl = bitmapFont.getGlyph(chars[0]);
                if (gl == null && i == 0) {
                    LOGGER.warn(MARKER, bitmapFont + " does not support ASCII digits");
                    return null;
                }
                if (gl == null) {
                    continue;
                }
                advance = gl.advance;
                glyph = gl;
            }
            glyphs[i] = glyph;
            // '0' is standard, because it's wider than other digits in general
            if (i == 0) {
                // 0 is standard advance
                offsets[n] = advance;
            } else {
                // relative offset to standard advance, to center the glyph
                offsets[n] = (offsets[0] - advance) / 2f;
            }
            n++;
        }

        // 33 to 47, cache only narrow chars
        for (int i = 0; i < 15; i++) {
            chars[0] = (char) (33 + i);
            float advance;
            BakedGlyph glyph;
            // no text shaping
            if (awtFont != null) {
                GlyphVector vector = mGlyphManager.createGlyphVector(awtFont, chars);
                advance = (float) vector.getGlyphPosition(1).getX() / desc.resLevel;
                // too wide
                if (advance + 1f > offsets[0]) {
                    continue;
                }
                glyph = mGlyphManager.lookupGlyph(awtFont, vector.getGlyphCode(0));
            } else {
                var gl = bitmapFont.getGlyph(chars[0]);
                // allow empty
                if (gl == null) {
                    continue;
                }
                advance = gl.advance;
                // too wide
                if (advance + 1f > offsets[0]) {
                    continue;
                }
                glyph = gl;
            }
            // allow empty
            if (glyph != null) {
                glyphs[n] = glyph;
                offsets[n] = (offsets[0] - advance) / 2f;
                n++;
            }
        }

        // 58 to 126, cache only narrow chars
        for (int i = 0; i < 69; i++) {
            chars[0] = (char) (58 + i);
            float advance;
            BakedGlyph glyph;
            // no text shaping
            if (awtFont != null) {
                GlyphVector vector = mGlyphManager.createGlyphVector(awtFont, chars);
                advance = (float) vector.getGlyphPosition(1).getX() / desc.resLevel;
                // too wide
                if (advance + 1 > offsets[0]) {
                    continue;
                }
                glyph = mGlyphManager.lookupGlyph(awtFont, vector.getGlyphCode(0));
            } else {
                var gl = bitmapFont.getGlyph(chars[0]);
                // allow empty
                if (gl == null) {
                    continue;
                }
                advance = gl.advance;
                // too wide
                if (advance + 1 > offsets[0]) {
                    continue;
                }
                glyph = gl;
            }
            // allow empty
            if (glyph != null) {
                glyphs[n] = glyph;
                offsets[n] = (offsets[0] - advance) / 2f;
                n++;
            }
        }
        if (n < glyphs.length) {
            glyphs = Arrays.copyOf(glyphs, n);
            offsets = Arrays.copyOf(offsets, n);
        }
        return new FastCharSet(glyphs, offsets);
    }

    /**
     * FastCharSet have uniform advances. Offset[0] is the advance for all glyphs.
     * Other offsets is the relative X offset to center the glyph. Normalized to
     * Minecraft GUI system.
     * <p>
     * This is used to render fast digits and obfuscated chars.
     */
    public static class FastCharSet extends BakedGlyph {

        public final BakedGlyph[] glyphs;
        public final float[] offsets;

        public FastCharSet(BakedGlyph[] glyphs, float[] offsets) {
            this.glyphs = glyphs;
            this.offsets = offsets;
        }
    }

    // We believe that JIT is smart enough not to compile this method
    static void _populateEmojiFontCoverage_DONT_COMPILE_(IntSet coverage) {
        coverage.add(0x9);
        coverage.add(0xa);
        coverage.add(0xd);
        coverage.add(0x20);
        coverage.add(0x23);
        coverage.add(0x2a);
        coverage.add(0x30);
        coverage.add(0x31);
        coverage.add(0x32);
        coverage.add(0x33);
        coverage.add(0x34);
        coverage.add(0x35);
        coverage.add(0x36);
        coverage.add(0x37);
        coverage.add(0x38);
        coverage.add(0x39);
        coverage.add(0xa9);
        coverage.add(0xae);
        coverage.add(0x200c);
        coverage.add(0x200d);
        coverage.add(0x200e);
        coverage.add(0x200f);
        coverage.add(0x2028);
        coverage.add(0x2029);
        coverage.add(0x202a);
        coverage.add(0x202b);
        coverage.add(0x202c);
        coverage.add(0x202d);
        coverage.add(0x202e);
        coverage.add(0x203c);
        coverage.add(0x2049);
        coverage.add(0x206a);
        coverage.add(0x206b);
        coverage.add(0x206c);
        coverage.add(0x206d);
        coverage.add(0x206e);
        coverage.add(0x206f);
        coverage.add(0x20e3);
        coverage.add(0x2122);
        coverage.add(0x2139);
        coverage.add(0x2194);
        coverage.add(0x2195);
        coverage.add(0x2196);
        coverage.add(0x2197);
        coverage.add(0x2198);
        coverage.add(0x2199);
        coverage.add(0x21a9);
        coverage.add(0x21aa);
        coverage.add(0x231a);
        coverage.add(0x231b);
        coverage.add(0x2328);
        coverage.add(0x23cf);
        coverage.add(0x23e9);
        coverage.add(0x23ea);
        coverage.add(0x23eb);
        coverage.add(0x23ec);
        coverage.add(0x23ed);
        coverage.add(0x23ee);
        coverage.add(0x23ef);
        coverage.add(0x23f0);
        coverage.add(0x23f1);
        coverage.add(0x23f2);
        coverage.add(0x23f3);
        coverage.add(0x23f8);
        coverage.add(0x23f9);
        coverage.add(0x23fa);
        coverage.add(0x24c2);
        coverage.add(0x25aa);
        coverage.add(0x25ab);
        coverage.add(0x25b6);
        coverage.add(0x25c0);
        coverage.add(0x25fb);
        coverage.add(0x25fc);
        coverage.add(0x25fd);
        coverage.add(0x25fe);
        coverage.add(0x2600);
        coverage.add(0x2601);
        coverage.add(0x2602);
        coverage.add(0x2603);
        coverage.add(0x2604);
        coverage.add(0x260e);
        coverage.add(0x2611);
        coverage.add(0x2614);
        coverage.add(0x2615);
        coverage.add(0x2618);
        coverage.add(0x261d);
        coverage.add(0x2620);
        coverage.add(0x2622);
        coverage.add(0x2623);
        coverage.add(0x2626);
        coverage.add(0x262a);
        coverage.add(0x262e);
        coverage.add(0x262f);
        coverage.add(0x2638);
        coverage.add(0x2639);
        coverage.add(0x263a);
        coverage.add(0x2640);
        coverage.add(0x2642);
        coverage.add(0x2648);
        coverage.add(0x2649);
        coverage.add(0x264a);
        coverage.add(0x264b);
        coverage.add(0x264c);
        coverage.add(0x264d);
        coverage.add(0x264e);
        coverage.add(0x264f);
        coverage.add(0x2650);
        coverage.add(0x2651);
        coverage.add(0x2652);
        coverage.add(0x2653);
        coverage.add(0x265f);
        coverage.add(0x2660);
        coverage.add(0x2663);
        coverage.add(0x2665);
        coverage.add(0x2666);
        coverage.add(0x2668);
        coverage.add(0x267b);
        coverage.add(0x267e);
        coverage.add(0x267f);
        coverage.add(0x2692);
        coverage.add(0x2693);
        coverage.add(0x2694);
        coverage.add(0x2695);
        coverage.add(0x2696);
        coverage.add(0x2697);
        coverage.add(0x2699);
        coverage.add(0x269b);
        coverage.add(0x269c);
        coverage.add(0x26a0);
        coverage.add(0x26a1);
        coverage.add(0x26a7);
        coverage.add(0x26aa);
        coverage.add(0x26ab);
        coverage.add(0x26b0);
        coverage.add(0x26b1);
        coverage.add(0x26bd);
        coverage.add(0x26be);
        coverage.add(0x26c4);
        coverage.add(0x26c5);
        coverage.add(0x26c8);
        coverage.add(0x26ce);
        coverage.add(0x26cf);
        coverage.add(0x26d1);
        coverage.add(0x26d3);
        coverage.add(0x26d4);
        coverage.add(0x26e9);
        coverage.add(0x26ea);
        coverage.add(0x26f0);
        coverage.add(0x26f1);
        coverage.add(0x26f2);
        coverage.add(0x26f3);
        coverage.add(0x26f4);
        coverage.add(0x26f5);
        coverage.add(0x26f7);
        coverage.add(0x26f8);
        coverage.add(0x26f9);
        coverage.add(0x26fa);
        coverage.add(0x26fd);
        coverage.add(0x2702);
        coverage.add(0x2705);
        coverage.add(0x2708);
        coverage.add(0x2709);
        coverage.add(0x270a);
        coverage.add(0x270b);
        coverage.add(0x270c);
        coverage.add(0x270d);
        coverage.add(0x270f);
        coverage.add(0x2712);
        coverage.add(0x2714);
        coverage.add(0x2716);
        coverage.add(0x271d);
        coverage.add(0x2721);
        coverage.add(0x2728);
        coverage.add(0x2733);
        coverage.add(0x2734);
        coverage.add(0x2744);
        coverage.add(0x2747);
        coverage.add(0x274c);
        coverage.add(0x274e);
        coverage.add(0x2753);
        coverage.add(0x2754);
        coverage.add(0x2755);
        coverage.add(0x2757);
        coverage.add(0x2763);
        coverage.add(0x2764);
        coverage.add(0x2795);
        coverage.add(0x2796);
        coverage.add(0x2797);
        coverage.add(0x27a1);
        coverage.add(0x27b0);
        coverage.add(0x27bf);
        coverage.add(0x2934);
        coverage.add(0x2935);
        coverage.add(0x2b05);
        coverage.add(0x2b06);
        coverage.add(0x2b07);
        coverage.add(0x2b1b);
        coverage.add(0x2b1c);
        coverage.add(0x2b50);
        coverage.add(0x2b55);
        coverage.add(0x3030);
        coverage.add(0x303d);
        coverage.add(0x3297);
        coverage.add(0x3299);
        coverage.add(0x1f004);
        coverage.add(0x1f0cf);
        coverage.add(0x1f170);
        coverage.add(0x1f171);
        coverage.add(0x1f17e);
        coverage.add(0x1f17f);
        coverage.add(0x1f18e);
        coverage.add(0x1f191);
        coverage.add(0x1f192);
        coverage.add(0x1f193);
        coverage.add(0x1f194);
        coverage.add(0x1f195);
        coverage.add(0x1f196);
        coverage.add(0x1f197);
        coverage.add(0x1f198);
        coverage.add(0x1f199);
        coverage.add(0x1f19a);
        coverage.add(0x1f201);
        coverage.add(0x1f202);
        coverage.add(0x1f21a);
        coverage.add(0x1f22f);
        coverage.add(0x1f232);
        coverage.add(0x1f233);
        coverage.add(0x1f234);
        coverage.add(0x1f235);
        coverage.add(0x1f236);
        coverage.add(0x1f237);
        coverage.add(0x1f238);
        coverage.add(0x1f239);
        coverage.add(0x1f23a);
        coverage.add(0x1f250);
        coverage.add(0x1f251);
        coverage.add(0x1f300);
        coverage.add(0x1f301);
        coverage.add(0x1f302);
        coverage.add(0x1f303);
        coverage.add(0x1f304);
        coverage.add(0x1f305);
        coverage.add(0x1f306);
        coverage.add(0x1f307);
        coverage.add(0x1f308);
        coverage.add(0x1f309);
        coverage.add(0x1f30a);
        coverage.add(0x1f30b);
        coverage.add(0x1f30c);
        coverage.add(0x1f30d);
        coverage.add(0x1f30e);
        coverage.add(0x1f30f);
        coverage.add(0x1f310);
        coverage.add(0x1f311);
        coverage.add(0x1f312);
        coverage.add(0x1f313);
        coverage.add(0x1f314);
        coverage.add(0x1f315);
        coverage.add(0x1f316);
        coverage.add(0x1f317);
        coverage.add(0x1f318);
        coverage.add(0x1f319);
        coverage.add(0x1f31a);
        coverage.add(0x1f31b);
        coverage.add(0x1f31c);
        coverage.add(0x1f31d);
        coverage.add(0x1f31e);
        coverage.add(0x1f31f);
        coverage.add(0x1f320);
        coverage.add(0x1f321);
        coverage.add(0x1f324);
        coverage.add(0x1f325);
        coverage.add(0x1f326);
        coverage.add(0x1f327);
        coverage.add(0x1f328);
        coverage.add(0x1f329);
        coverage.add(0x1f32a);
        coverage.add(0x1f32b);
        coverage.add(0x1f32c);
        coverage.add(0x1f32d);
        coverage.add(0x1f32e);
        coverage.add(0x1f32f);
        coverage.add(0x1f330);
        coverage.add(0x1f331);
        coverage.add(0x1f332);
        coverage.add(0x1f333);
        coverage.add(0x1f334);
        coverage.add(0x1f335);
        coverage.add(0x1f336);
        coverage.add(0x1f337);
        coverage.add(0x1f338);
        coverage.add(0x1f339);
        coverage.add(0x1f33a);
        coverage.add(0x1f33b);
        coverage.add(0x1f33c);
        coverage.add(0x1f33d);
        coverage.add(0x1f33e);
        coverage.add(0x1f33f);
        coverage.add(0x1f340);
        coverage.add(0x1f341);
        coverage.add(0x1f342);
        coverage.add(0x1f343);
        coverage.add(0x1f344);
        coverage.add(0x1f345);
        coverage.add(0x1f346);
        coverage.add(0x1f347);
        coverage.add(0x1f348);
        coverage.add(0x1f349);
        coverage.add(0x1f34a);
        coverage.add(0x1f34b);
        coverage.add(0x1f34c);
        coverage.add(0x1f34d);
        coverage.add(0x1f34e);
        coverage.add(0x1f34f);
        coverage.add(0x1f350);
        coverage.add(0x1f351);
        coverage.add(0x1f352);
        coverage.add(0x1f353);
        coverage.add(0x1f354);
        coverage.add(0x1f355);
        coverage.add(0x1f356);
        coverage.add(0x1f357);
        coverage.add(0x1f358);
        coverage.add(0x1f359);
        coverage.add(0x1f35a);
        coverage.add(0x1f35b);
        coverage.add(0x1f35c);
        coverage.add(0x1f35d);
        coverage.add(0x1f35e);
        coverage.add(0x1f35f);
        coverage.add(0x1f360);
        coverage.add(0x1f361);
        coverage.add(0x1f362);
        coverage.add(0x1f363);
        coverage.add(0x1f364);
        coverage.add(0x1f365);
        coverage.add(0x1f366);
        coverage.add(0x1f367);
        coverage.add(0x1f368);
        coverage.add(0x1f369);
        coverage.add(0x1f36a);
        coverage.add(0x1f36b);
        coverage.add(0x1f36c);
        coverage.add(0x1f36d);
        coverage.add(0x1f36e);
        coverage.add(0x1f36f);
        coverage.add(0x1f370);
        coverage.add(0x1f371);
        coverage.add(0x1f372);
        coverage.add(0x1f373);
        coverage.add(0x1f374);
        coverage.add(0x1f375);
        coverage.add(0x1f376);
        coverage.add(0x1f377);
        coverage.add(0x1f378);
        coverage.add(0x1f379);
        coverage.add(0x1f37a);
        coverage.add(0x1f37b);
        coverage.add(0x1f37c);
        coverage.add(0x1f37d);
        coverage.add(0x1f37e);
        coverage.add(0x1f37f);
        coverage.add(0x1f380);
        coverage.add(0x1f381);
        coverage.add(0x1f382);
        coverage.add(0x1f383);
        coverage.add(0x1f384);
        coverage.add(0x1f385);
        coverage.add(0x1f386);
        coverage.add(0x1f387);
        coverage.add(0x1f388);
        coverage.add(0x1f389);
        coverage.add(0x1f38a);
        coverage.add(0x1f38b);
        coverage.add(0x1f38c);
        coverage.add(0x1f38d);
        coverage.add(0x1f38e);
        coverage.add(0x1f38f);
        coverage.add(0x1f390);
        coverage.add(0x1f391);
        coverage.add(0x1f392);
        coverage.add(0x1f393);
        coverage.add(0x1f396);
        coverage.add(0x1f397);
        coverage.add(0x1f399);
        coverage.add(0x1f39a);
        coverage.add(0x1f39b);
        coverage.add(0x1f39e);
        coverage.add(0x1f39f);
        coverage.add(0x1f3a0);
        coverage.add(0x1f3a1);
        coverage.add(0x1f3a2);
        coverage.add(0x1f3a3);
        coverage.add(0x1f3a4);
        coverage.add(0x1f3a5);
        coverage.add(0x1f3a6);
        coverage.add(0x1f3a7);
        coverage.add(0x1f3a8);
        coverage.add(0x1f3a9);
        coverage.add(0x1f3aa);
        coverage.add(0x1f3ab);
        coverage.add(0x1f3ac);
        coverage.add(0x1f3ad);
        coverage.add(0x1f3ae);
        coverage.add(0x1f3af);
        coverage.add(0x1f3b0);
        coverage.add(0x1f3b1);
        coverage.add(0x1f3b2);
        coverage.add(0x1f3b3);
        coverage.add(0x1f3b4);
        coverage.add(0x1f3b5);
        coverage.add(0x1f3b6);
        coverage.add(0x1f3b7);
        coverage.add(0x1f3b8);
        coverage.add(0x1f3b9);
        coverage.add(0x1f3ba);
        coverage.add(0x1f3bb);
        coverage.add(0x1f3bc);
        coverage.add(0x1f3bd);
        coverage.add(0x1f3be);
        coverage.add(0x1f3bf);
        coverage.add(0x1f3c0);
        coverage.add(0x1f3c1);
        coverage.add(0x1f3c2);
        coverage.add(0x1f3c3);
        coverage.add(0x1f3c4);
        coverage.add(0x1f3c5);
        coverage.add(0x1f3c6);
        coverage.add(0x1f3c7);
        coverage.add(0x1f3c8);
        coverage.add(0x1f3c9);
        coverage.add(0x1f3ca);
        coverage.add(0x1f3cb);
        coverage.add(0x1f3cc);
        coverage.add(0x1f3cd);
        coverage.add(0x1f3ce);
        coverage.add(0x1f3cf);
        coverage.add(0x1f3d0);
        coverage.add(0x1f3d1);
        coverage.add(0x1f3d2);
        coverage.add(0x1f3d3);
        coverage.add(0x1f3d4);
        coverage.add(0x1f3d5);
        coverage.add(0x1f3d6);
        coverage.add(0x1f3d7);
        coverage.add(0x1f3d8);
        coverage.add(0x1f3d9);
        coverage.add(0x1f3da);
        coverage.add(0x1f3db);
        coverage.add(0x1f3dc);
        coverage.add(0x1f3dd);
        coverage.add(0x1f3de);
        coverage.add(0x1f3df);
        coverage.add(0x1f3e0);
        coverage.add(0x1f3e1);
        coverage.add(0x1f3e2);
        coverage.add(0x1f3e3);
        coverage.add(0x1f3e4);
        coverage.add(0x1f3e5);
        coverage.add(0x1f3e6);
        coverage.add(0x1f3e7);
        coverage.add(0x1f3e8);
        coverage.add(0x1f3e9);
        coverage.add(0x1f3ea);
        coverage.add(0x1f3eb);
        coverage.add(0x1f3ec);
        coverage.add(0x1f3ed);
        coverage.add(0x1f3ee);
        coverage.add(0x1f3ef);
        coverage.add(0x1f3f0);
        coverage.add(0x1f3f3);
        coverage.add(0x1f3f4);
        coverage.add(0x1f3f5);
        coverage.add(0x1f3f7);
        coverage.add(0x1f3f8);
        coverage.add(0x1f3f9);
        coverage.add(0x1f3fa);
        coverage.add(0x1f3fb);
        coverage.add(0x1f3fc);
        coverage.add(0x1f3fd);
        coverage.add(0x1f3fe);
        coverage.add(0x1f3ff);
        coverage.add(0x1f400);
        coverage.add(0x1f401);
        coverage.add(0x1f402);
        coverage.add(0x1f403);
        coverage.add(0x1f404);
        coverage.add(0x1f405);
        coverage.add(0x1f406);
        coverage.add(0x1f407);
        coverage.add(0x1f408);
        coverage.add(0x1f409);
        coverage.add(0x1f40a);
        coverage.add(0x1f40b);
        coverage.add(0x1f40c);
        coverage.add(0x1f40d);
        coverage.add(0x1f40e);
        coverage.add(0x1f40f);
        coverage.add(0x1f410);
        coverage.add(0x1f411);
        coverage.add(0x1f412);
        coverage.add(0x1f413);
        coverage.add(0x1f414);
        coverage.add(0x1f415);
        coverage.add(0x1f416);
        coverage.add(0x1f417);
        coverage.add(0x1f418);
        coverage.add(0x1f419);
        coverage.add(0x1f41a);
        coverage.add(0x1f41b);
        coverage.add(0x1f41c);
        coverage.add(0x1f41d);
        coverage.add(0x1f41e);
        coverage.add(0x1f41f);
        coverage.add(0x1f420);
        coverage.add(0x1f421);
        coverage.add(0x1f422);
        coverage.add(0x1f423);
        coverage.add(0x1f424);
        coverage.add(0x1f425);
        coverage.add(0x1f426);
        coverage.add(0x1f427);
        coverage.add(0x1f428);
        coverage.add(0x1f429);
        coverage.add(0x1f42a);
        coverage.add(0x1f42b);
        coverage.add(0x1f42c);
        coverage.add(0x1f42d);
        coverage.add(0x1f42e);
        coverage.add(0x1f42f);
        coverage.add(0x1f430);
        coverage.add(0x1f431);
        coverage.add(0x1f432);
        coverage.add(0x1f433);
        coverage.add(0x1f434);
        coverage.add(0x1f435);
        coverage.add(0x1f436);
        coverage.add(0x1f437);
        coverage.add(0x1f438);
        coverage.add(0x1f439);
        coverage.add(0x1f43a);
        coverage.add(0x1f43b);
        coverage.add(0x1f43c);
        coverage.add(0x1f43d);
        coverage.add(0x1f43e);
        coverage.add(0x1f43f);
        coverage.add(0x1f440);
        coverage.add(0x1f441);
        coverage.add(0x1f442);
        coverage.add(0x1f443);
        coverage.add(0x1f444);
        coverage.add(0x1f445);
        coverage.add(0x1f446);
        coverage.add(0x1f447);
        coverage.add(0x1f448);
        coverage.add(0x1f449);
        coverage.add(0x1f44a);
        coverage.add(0x1f44b);
        coverage.add(0x1f44c);
        coverage.add(0x1f44d);
        coverage.add(0x1f44e);
        coverage.add(0x1f44f);
        coverage.add(0x1f450);
        coverage.add(0x1f451);
        coverage.add(0x1f452);
        coverage.add(0x1f453);
        coverage.add(0x1f454);
        coverage.add(0x1f455);
        coverage.add(0x1f456);
        coverage.add(0x1f457);
        coverage.add(0x1f458);
        coverage.add(0x1f459);
        coverage.add(0x1f45a);
        coverage.add(0x1f45b);
        coverage.add(0x1f45c);
        coverage.add(0x1f45d);
        coverage.add(0x1f45e);
        coverage.add(0x1f45f);
        coverage.add(0x1f460);
        coverage.add(0x1f461);
        coverage.add(0x1f462);
        coverage.add(0x1f463);
        coverage.add(0x1f464);
        coverage.add(0x1f465);
        coverage.add(0x1f466);
        coverage.add(0x1f467);
        coverage.add(0x1f468);
        coverage.add(0x1f469);
        coverage.add(0x1f46a);
        coverage.add(0x1f46b);
        coverage.add(0x1f46c);
        coverage.add(0x1f46d);
        coverage.add(0x1f46e);
        coverage.add(0x1f46f);
        coverage.add(0x1f470);
        coverage.add(0x1f471);
        coverage.add(0x1f472);
        coverage.add(0x1f473);
        coverage.add(0x1f474);
        coverage.add(0x1f475);
        coverage.add(0x1f476);
        coverage.add(0x1f477);
        coverage.add(0x1f478);
        coverage.add(0x1f479);
        coverage.add(0x1f47a);
        coverage.add(0x1f47b);
        coverage.add(0x1f47c);
        coverage.add(0x1f47d);
        coverage.add(0x1f47e);
        coverage.add(0x1f47f);
        coverage.add(0x1f480);
        coverage.add(0x1f481);
        coverage.add(0x1f482);
        coverage.add(0x1f483);
        coverage.add(0x1f484);
        coverage.add(0x1f485);
        coverage.add(0x1f486);
        coverage.add(0x1f487);
        coverage.add(0x1f488);
        coverage.add(0x1f489);
        coverage.add(0x1f48a);
        coverage.add(0x1f48b);
        coverage.add(0x1f48c);
        coverage.add(0x1f48d);
        coverage.add(0x1f48e);
        coverage.add(0x1f48f);
        coverage.add(0x1f490);
        coverage.add(0x1f491);
        coverage.add(0x1f492);
        coverage.add(0x1f493);
        coverage.add(0x1f494);
        coverage.add(0x1f495);
        coverage.add(0x1f496);
        coverage.add(0x1f497);
        coverage.add(0x1f498);
        coverage.add(0x1f499);
        coverage.add(0x1f49a);
        coverage.add(0x1f49b);
        coverage.add(0x1f49c);
        coverage.add(0x1f49d);
        coverage.add(0x1f49e);
        coverage.add(0x1f49f);
        coverage.add(0x1f4a0);
        coverage.add(0x1f4a1);
        coverage.add(0x1f4a2);
        coverage.add(0x1f4a3);
        coverage.add(0x1f4a4);
        coverage.add(0x1f4a5);
        coverage.add(0x1f4a6);
        coverage.add(0x1f4a7);
        coverage.add(0x1f4a8);
        coverage.add(0x1f4a9);
        coverage.add(0x1f4aa);
        coverage.add(0x1f4ab);
        coverage.add(0x1f4ac);
        coverage.add(0x1f4ad);
        coverage.add(0x1f4ae);
        coverage.add(0x1f4af);
        coverage.add(0x1f4b0);
        coverage.add(0x1f4b1);
        coverage.add(0x1f4b2);
        coverage.add(0x1f4b3);
        coverage.add(0x1f4b4);
        coverage.add(0x1f4b5);
        coverage.add(0x1f4b6);
        coverage.add(0x1f4b7);
        coverage.add(0x1f4b8);
        coverage.add(0x1f4b9);
        coverage.add(0x1f4ba);
        coverage.add(0x1f4bb);
        coverage.add(0x1f4bc);
        coverage.add(0x1f4bd);
        coverage.add(0x1f4be);
        coverage.add(0x1f4bf);
        coverage.add(0x1f4c0);
        coverage.add(0x1f4c1);
        coverage.add(0x1f4c2);
        coverage.add(0x1f4c3);
        coverage.add(0x1f4c4);
        coverage.add(0x1f4c5);
        coverage.add(0x1f4c6);
        coverage.add(0x1f4c7);
        coverage.add(0x1f4c8);
        coverage.add(0x1f4c9);
        coverage.add(0x1f4ca);
        coverage.add(0x1f4cb);
        coverage.add(0x1f4cc);
        coverage.add(0x1f4cd);
        coverage.add(0x1f4ce);
        coverage.add(0x1f4cf);
        coverage.add(0x1f4d0);
        coverage.add(0x1f4d1);
        coverage.add(0x1f4d2);
        coverage.add(0x1f4d3);
        coverage.add(0x1f4d4);
        coverage.add(0x1f4d5);
        coverage.add(0x1f4d6);
        coverage.add(0x1f4d7);
        coverage.add(0x1f4d8);
        coverage.add(0x1f4d9);
        coverage.add(0x1f4da);
        coverage.add(0x1f4db);
        coverage.add(0x1f4dc);
        coverage.add(0x1f4dd);
        coverage.add(0x1f4de);
        coverage.add(0x1f4df);
        coverage.add(0x1f4e0);
        coverage.add(0x1f4e1);
        coverage.add(0x1f4e2);
        coverage.add(0x1f4e3);
        coverage.add(0x1f4e4);
        coverage.add(0x1f4e5);
        coverage.add(0x1f4e6);
        coverage.add(0x1f4e7);
        coverage.add(0x1f4e8);
        coverage.add(0x1f4e9);
        coverage.add(0x1f4ea);
        coverage.add(0x1f4eb);
        coverage.add(0x1f4ec);
        coverage.add(0x1f4ed);
        coverage.add(0x1f4ee);
        coverage.add(0x1f4ef);
        coverage.add(0x1f4f0);
        coverage.add(0x1f4f1);
        coverage.add(0x1f4f2);
        coverage.add(0x1f4f3);
        coverage.add(0x1f4f4);
        coverage.add(0x1f4f5);
        coverage.add(0x1f4f6);
        coverage.add(0x1f4f7);
        coverage.add(0x1f4f8);
        coverage.add(0x1f4f9);
        coverage.add(0x1f4fa);
        coverage.add(0x1f4fb);
        coverage.add(0x1f4fc);
        coverage.add(0x1f4fd);
        coverage.add(0x1f4ff);
        coverage.add(0x1f500);
        coverage.add(0x1f501);
        coverage.add(0x1f502);
        coverage.add(0x1f503);
        coverage.add(0x1f504);
        coverage.add(0x1f505);
        coverage.add(0x1f506);
        coverage.add(0x1f507);
        coverage.add(0x1f508);
        coverage.add(0x1f509);
        coverage.add(0x1f50a);
        coverage.add(0x1f50b);
        coverage.add(0x1f50c);
        coverage.add(0x1f50d);
        coverage.add(0x1f50e);
        coverage.add(0x1f50f);
        coverage.add(0x1f510);
        coverage.add(0x1f511);
        coverage.add(0x1f512);
        coverage.add(0x1f513);
        coverage.add(0x1f514);
        coverage.add(0x1f515);
        coverage.add(0x1f516);
        coverage.add(0x1f517);
        coverage.add(0x1f518);
        coverage.add(0x1f519);
        coverage.add(0x1f51a);
        coverage.add(0x1f51b);
        coverage.add(0x1f51c);
        coverage.add(0x1f51d);
        coverage.add(0x1f51e);
        coverage.add(0x1f51f);
        coverage.add(0x1f520);
        coverage.add(0x1f521);
        coverage.add(0x1f522);
        coverage.add(0x1f523);
        coverage.add(0x1f524);
        coverage.add(0x1f525);
        coverage.add(0x1f526);
        coverage.add(0x1f527);
        coverage.add(0x1f528);
        coverage.add(0x1f529);
        coverage.add(0x1f52a);
        coverage.add(0x1f52b);
        coverage.add(0x1f52c);
        coverage.add(0x1f52d);
        coverage.add(0x1f52e);
        coverage.add(0x1f52f);
        coverage.add(0x1f530);
        coverage.add(0x1f531);
        coverage.add(0x1f532);
        coverage.add(0x1f533);
        coverage.add(0x1f534);
        coverage.add(0x1f535);
        coverage.add(0x1f536);
        coverage.add(0x1f537);
        coverage.add(0x1f538);
        coverage.add(0x1f539);
        coverage.add(0x1f53a);
        coverage.add(0x1f53b);
        coverage.add(0x1f53c);
        coverage.add(0x1f53d);
        coverage.add(0x1f549);
        coverage.add(0x1f54a);
        coverage.add(0x1f54b);
        coverage.add(0x1f54c);
        coverage.add(0x1f54d);
        coverage.add(0x1f54e);
        coverage.add(0x1f550);
        coverage.add(0x1f551);
        coverage.add(0x1f552);
        coverage.add(0x1f553);
        coverage.add(0x1f554);
        coverage.add(0x1f555);
        coverage.add(0x1f556);
        coverage.add(0x1f557);
        coverage.add(0x1f558);
        coverage.add(0x1f559);
        coverage.add(0x1f55a);
        coverage.add(0x1f55b);
        coverage.add(0x1f55c);
        coverage.add(0x1f55d);
        coverage.add(0x1f55e);
        coverage.add(0x1f55f);
        coverage.add(0x1f560);
        coverage.add(0x1f561);
        coverage.add(0x1f562);
        coverage.add(0x1f563);
        coverage.add(0x1f564);
        coverage.add(0x1f565);
        coverage.add(0x1f566);
        coverage.add(0x1f567);
        coverage.add(0x1f56f);
        coverage.add(0x1f570);
        coverage.add(0x1f573);
        coverage.add(0x1f574);
        coverage.add(0x1f575);
        coverage.add(0x1f576);
        coverage.add(0x1f577);
        coverage.add(0x1f578);
        coverage.add(0x1f579);
        coverage.add(0x1f57a);
        coverage.add(0x1f587);
        coverage.add(0x1f58a);
        coverage.add(0x1f58b);
        coverage.add(0x1f58c);
        coverage.add(0x1f58d);
        coverage.add(0x1f590);
        coverage.add(0x1f595);
        coverage.add(0x1f596);
        coverage.add(0x1f5a4);
        coverage.add(0x1f5a5);
        coverage.add(0x1f5a8);
        coverage.add(0x1f5b1);
        coverage.add(0x1f5b2);
        coverage.add(0x1f5bc);
        coverage.add(0x1f5c2);
        coverage.add(0x1f5c3);
        coverage.add(0x1f5c4);
        coverage.add(0x1f5d1);
        coverage.add(0x1f5d2);
        coverage.add(0x1f5d3);
        coverage.add(0x1f5dc);
        coverage.add(0x1f5dd);
        coverage.add(0x1f5de);
        coverage.add(0x1f5e1);
        coverage.add(0x1f5e3);
        coverage.add(0x1f5e8);
        coverage.add(0x1f5ef);
        coverage.add(0x1f5f3);
        coverage.add(0x1f5fa);
        coverage.add(0x1f5fb);
        coverage.add(0x1f5fc);
        coverage.add(0x1f5fd);
        coverage.add(0x1f5fe);
        coverage.add(0x1f5ff);
        coverage.add(0x1f600);
        coverage.add(0x1f601);
        coverage.add(0x1f602);
        coverage.add(0x1f603);
        coverage.add(0x1f604);
        coverage.add(0x1f605);
        coverage.add(0x1f606);
        coverage.add(0x1f607);
        coverage.add(0x1f608);
        coverage.add(0x1f609);
        coverage.add(0x1f60a);
        coverage.add(0x1f60b);
        coverage.add(0x1f60c);
        coverage.add(0x1f60d);
        coverage.add(0x1f60e);
        coverage.add(0x1f60f);
        coverage.add(0x1f610);
        coverage.add(0x1f611);
        coverage.add(0x1f612);
        coverage.add(0x1f613);
        coverage.add(0x1f614);
        coverage.add(0x1f615);
        coverage.add(0x1f616);
        coverage.add(0x1f617);
        coverage.add(0x1f618);
        coverage.add(0x1f619);
        coverage.add(0x1f61a);
        coverage.add(0x1f61b);
        coverage.add(0x1f61c);
        coverage.add(0x1f61d);
        coverage.add(0x1f61e);
        coverage.add(0x1f61f);
        coverage.add(0x1f620);
        coverage.add(0x1f621);
        coverage.add(0x1f622);
        coverage.add(0x1f623);
        coverage.add(0x1f624);
        coverage.add(0x1f625);
        coverage.add(0x1f626);
        coverage.add(0x1f627);
        coverage.add(0x1f628);
        coverage.add(0x1f629);
        coverage.add(0x1f62a);
        coverage.add(0x1f62b);
        coverage.add(0x1f62c);
        coverage.add(0x1f62d);
        coverage.add(0x1f62e);
        coverage.add(0x1f62f);
        coverage.add(0x1f630);
        coverage.add(0x1f631);
        coverage.add(0x1f632);
        coverage.add(0x1f633);
        coverage.add(0x1f634);
        coverage.add(0x1f635);
        coverage.add(0x1f636);
        coverage.add(0x1f637);
        coverage.add(0x1f638);
        coverage.add(0x1f639);
        coverage.add(0x1f63a);
        coverage.add(0x1f63b);
        coverage.add(0x1f63c);
        coverage.add(0x1f63d);
        coverage.add(0x1f63e);
        coverage.add(0x1f63f);
        coverage.add(0x1f640);
        coverage.add(0x1f641);
        coverage.add(0x1f642);
        coverage.add(0x1f643);
        coverage.add(0x1f644);
        coverage.add(0x1f645);
        coverage.add(0x1f646);
        coverage.add(0x1f647);
        coverage.add(0x1f648);
        coverage.add(0x1f649);
        coverage.add(0x1f64a);
        coverage.add(0x1f64b);
        coverage.add(0x1f64c);
        coverage.add(0x1f64d);
        coverage.add(0x1f64e);
        coverage.add(0x1f64f);
        coverage.add(0x1f680);
        coverage.add(0x1f681);
        coverage.add(0x1f682);
        coverage.add(0x1f683);
        coverage.add(0x1f684);
        coverage.add(0x1f685);
        coverage.add(0x1f686);
        coverage.add(0x1f687);
        coverage.add(0x1f688);
        coverage.add(0x1f689);
        coverage.add(0x1f68a);
        coverage.add(0x1f68b);
        coverage.add(0x1f68c);
        coverage.add(0x1f68d);
        coverage.add(0x1f68e);
        coverage.add(0x1f68f);
        coverage.add(0x1f690);
        coverage.add(0x1f691);
        coverage.add(0x1f692);
        coverage.add(0x1f693);
        coverage.add(0x1f694);
        coverage.add(0x1f695);
        coverage.add(0x1f696);
        coverage.add(0x1f697);
        coverage.add(0x1f698);
        coverage.add(0x1f699);
        coverage.add(0x1f69a);
        coverage.add(0x1f69b);
        coverage.add(0x1f69c);
        coverage.add(0x1f69d);
        coverage.add(0x1f69e);
        coverage.add(0x1f69f);
        coverage.add(0x1f6a0);
        coverage.add(0x1f6a1);
        coverage.add(0x1f6a2);
        coverage.add(0x1f6a3);
        coverage.add(0x1f6a4);
        coverage.add(0x1f6a5);
        coverage.add(0x1f6a6);
        coverage.add(0x1f6a7);
        coverage.add(0x1f6a8);
        coverage.add(0x1f6a9);
        coverage.add(0x1f6aa);
        coverage.add(0x1f6ab);
        coverage.add(0x1f6ac);
        coverage.add(0x1f6ad);
        coverage.add(0x1f6ae);
        coverage.add(0x1f6af);
        coverage.add(0x1f6b0);
        coverage.add(0x1f6b1);
        coverage.add(0x1f6b2);
        coverage.add(0x1f6b3);
        coverage.add(0x1f6b4);
        coverage.add(0x1f6b5);
        coverage.add(0x1f6b6);
        coverage.add(0x1f6b7);
        coverage.add(0x1f6b8);
        coverage.add(0x1f6b9);
        coverage.add(0x1f6ba);
        coverage.add(0x1f6bb);
        coverage.add(0x1f6bc);
        coverage.add(0x1f6bd);
        coverage.add(0x1f6be);
        coverage.add(0x1f6bf);
        coverage.add(0x1f6c0);
        coverage.add(0x1f6c1);
        coverage.add(0x1f6c2);
        coverage.add(0x1f6c3);
        coverage.add(0x1f6c4);
        coverage.add(0x1f6c5);
        coverage.add(0x1f6cb);
        coverage.add(0x1f6cc);
        coverage.add(0x1f6cd);
        coverage.add(0x1f6ce);
        coverage.add(0x1f6cf);
        coverage.add(0x1f6d0);
        coverage.add(0x1f6d1);
        coverage.add(0x1f6d2);
        coverage.add(0x1f6d5);
        coverage.add(0x1f6d6);
        coverage.add(0x1f6d7);
        coverage.add(0x1f6dc);
        coverage.add(0x1f6dd);
        coverage.add(0x1f6de);
        coverage.add(0x1f6df);
        coverage.add(0x1f6e0);
        coverage.add(0x1f6e1);
        coverage.add(0x1f6e2);
        coverage.add(0x1f6e3);
        coverage.add(0x1f6e4);
        coverage.add(0x1f6e5);
        coverage.add(0x1f6e9);
        coverage.add(0x1f6eb);
        coverage.add(0x1f6ec);
        coverage.add(0x1f6f0);
        coverage.add(0x1f6f3);
        coverage.add(0x1f6f4);
        coverage.add(0x1f6f5);
        coverage.add(0x1f6f6);
        coverage.add(0x1f6f7);
        coverage.add(0x1f6f8);
        coverage.add(0x1f6f9);
        coverage.add(0x1f6fa);
        coverage.add(0x1f6fb);
        coverage.add(0x1f6fc);
        coverage.add(0x1f7e0);
        coverage.add(0x1f7e1);
        coverage.add(0x1f7e2);
        coverage.add(0x1f7e3);
        coverage.add(0x1f7e4);
        coverage.add(0x1f7e5);
        coverage.add(0x1f7e6);
        coverage.add(0x1f7e7);
        coverage.add(0x1f7e8);
        coverage.add(0x1f7e9);
        coverage.add(0x1f7ea);
        coverage.add(0x1f7eb);
        coverage.add(0x1f7f0);
        coverage.add(0x1f90c);
        coverage.add(0x1f90d);
        coverage.add(0x1f90e);
        coverage.add(0x1f90f);
        coverage.add(0x1f910);
        coverage.add(0x1f911);
        coverage.add(0x1f912);
        coverage.add(0x1f913);
        coverage.add(0x1f914);
        coverage.add(0x1f915);
        coverage.add(0x1f916);
        coverage.add(0x1f917);
        coverage.add(0x1f918);
        coverage.add(0x1f919);
        coverage.add(0x1f91a);
        coverage.add(0x1f91b);
        coverage.add(0x1f91c);
        coverage.add(0x1f91d);
        coverage.add(0x1f91e);
        coverage.add(0x1f91f);
        coverage.add(0x1f920);
        coverage.add(0x1f921);
        coverage.add(0x1f922);
        coverage.add(0x1f923);
        coverage.add(0x1f924);
        coverage.add(0x1f925);
        coverage.add(0x1f926);
        coverage.add(0x1f927);
        coverage.add(0x1f928);
        coverage.add(0x1f929);
        coverage.add(0x1f92a);
        coverage.add(0x1f92b);
        coverage.add(0x1f92c);
        coverage.add(0x1f92d);
        coverage.add(0x1f92e);
        coverage.add(0x1f92f);
        coverage.add(0x1f930);
        coverage.add(0x1f931);
        coverage.add(0x1f932);
        coverage.add(0x1f933);
        coverage.add(0x1f934);
        coverage.add(0x1f935);
        coverage.add(0x1f936);
        coverage.add(0x1f937);
        coverage.add(0x1f938);
        coverage.add(0x1f939);
        coverage.add(0x1f93a);
        coverage.add(0x1f93c);
        coverage.add(0x1f93d);
        coverage.add(0x1f93e);
        coverage.add(0x1f93f);
        coverage.add(0x1f940);
        coverage.add(0x1f941);
        coverage.add(0x1f942);
        coverage.add(0x1f943);
        coverage.add(0x1f944);
        coverage.add(0x1f945);
        coverage.add(0x1f947);
        coverage.add(0x1f948);
        coverage.add(0x1f949);
        coverage.add(0x1f94a);
        coverage.add(0x1f94b);
        coverage.add(0x1f94c);
        coverage.add(0x1f94d);
        coverage.add(0x1f94e);
        coverage.add(0x1f94f);
        coverage.add(0x1f950);
        coverage.add(0x1f951);
        coverage.add(0x1f952);
        coverage.add(0x1f953);
        coverage.add(0x1f954);
        coverage.add(0x1f955);
        coverage.add(0x1f956);
        coverage.add(0x1f957);
        coverage.add(0x1f958);
        coverage.add(0x1f959);
        coverage.add(0x1f95a);
        coverage.add(0x1f95b);
        coverage.add(0x1f95c);
        coverage.add(0x1f95d);
        coverage.add(0x1f95e);
        coverage.add(0x1f95f);
        coverage.add(0x1f960);
        coverage.add(0x1f961);
        coverage.add(0x1f962);
        coverage.add(0x1f963);
        coverage.add(0x1f964);
        coverage.add(0x1f965);
        coverage.add(0x1f966);
        coverage.add(0x1f967);
        coverage.add(0x1f968);
        coverage.add(0x1f969);
        coverage.add(0x1f96a);
        coverage.add(0x1f96b);
        coverage.add(0x1f96c);
        coverage.add(0x1f96d);
        coverage.add(0x1f96e);
        coverage.add(0x1f96f);
        coverage.add(0x1f970);
        coverage.add(0x1f971);
        coverage.add(0x1f972);
        coverage.add(0x1f973);
        coverage.add(0x1f974);
        coverage.add(0x1f975);
        coverage.add(0x1f976);
        coverage.add(0x1f977);
        coverage.add(0x1f978);
        coverage.add(0x1f979);
        coverage.add(0x1f97a);
        coverage.add(0x1f97b);
        coverage.add(0x1f97c);
        coverage.add(0x1f97d);
        coverage.add(0x1f97e);
        coverage.add(0x1f97f);
        coverage.add(0x1f980);
        coverage.add(0x1f981);
        coverage.add(0x1f982);
        coverage.add(0x1f983);
        coverage.add(0x1f984);
        coverage.add(0x1f985);
        coverage.add(0x1f986);
        coverage.add(0x1f987);
        coverage.add(0x1f988);
        coverage.add(0x1f989);
        coverage.add(0x1f98a);
        coverage.add(0x1f98b);
        coverage.add(0x1f98c);
        coverage.add(0x1f98d);
        coverage.add(0x1f98e);
        coverage.add(0x1f98f);
        coverage.add(0x1f990);
        coverage.add(0x1f991);
        coverage.add(0x1f992);
        coverage.add(0x1f993);
        coverage.add(0x1f994);
        coverage.add(0x1f995);
        coverage.add(0x1f996);
        coverage.add(0x1f997);
        coverage.add(0x1f998);
        coverage.add(0x1f999);
        coverage.add(0x1f99a);
        coverage.add(0x1f99b);
        coverage.add(0x1f99c);
        coverage.add(0x1f99d);
        coverage.add(0x1f99e);
        coverage.add(0x1f99f);
        coverage.add(0x1f9a0);
        coverage.add(0x1f9a1);
        coverage.add(0x1f9a2);
        coverage.add(0x1f9a3);
        coverage.add(0x1f9a4);
        coverage.add(0x1f9a5);
        coverage.add(0x1f9a6);
        coverage.add(0x1f9a7);
        coverage.add(0x1f9a8);
        coverage.add(0x1f9a9);
        coverage.add(0x1f9aa);
        coverage.add(0x1f9ab);
        coverage.add(0x1f9ac);
        coverage.add(0x1f9ad);
        coverage.add(0x1f9ae);
        coverage.add(0x1f9af);
        coverage.add(0x1f9b0);
        coverage.add(0x1f9b1);
        coverage.add(0x1f9b2);
        coverage.add(0x1f9b3);
        coverage.add(0x1f9b4);
        coverage.add(0x1f9b5);
        coverage.add(0x1f9b6);
        coverage.add(0x1f9b7);
        coverage.add(0x1f9b8);
        coverage.add(0x1f9b9);
        coverage.add(0x1f9ba);
        coverage.add(0x1f9bb);
        coverage.add(0x1f9bc);
        coverage.add(0x1f9bd);
        coverage.add(0x1f9be);
        coverage.add(0x1f9bf);
        coverage.add(0x1f9c0);
        coverage.add(0x1f9c1);
        coverage.add(0x1f9c2);
        coverage.add(0x1f9c3);
        coverage.add(0x1f9c4);
        coverage.add(0x1f9c5);
        coverage.add(0x1f9c6);
        coverage.add(0x1f9c7);
        coverage.add(0x1f9c8);
        coverage.add(0x1f9c9);
        coverage.add(0x1f9ca);
        coverage.add(0x1f9cb);
        coverage.add(0x1f9cc);
        coverage.add(0x1f9cd);
        coverage.add(0x1f9ce);
        coverage.add(0x1f9cf);
        coverage.add(0x1f9d0);
        coverage.add(0x1f9d1);
        coverage.add(0x1f9d2);
        coverage.add(0x1f9d3);
        coverage.add(0x1f9d4);
        coverage.add(0x1f9d5);
        coverage.add(0x1f9d6);
        coverage.add(0x1f9d7);
        coverage.add(0x1f9d8);
        coverage.add(0x1f9d9);
        coverage.add(0x1f9da);
        coverage.add(0x1f9db);
        coverage.add(0x1f9dc);
        coverage.add(0x1f9dd);
        coverage.add(0x1f9de);
        coverage.add(0x1f9df);
        coverage.add(0x1f9e0);
        coverage.add(0x1f9e1);
        coverage.add(0x1f9e2);
        coverage.add(0x1f9e3);
        coverage.add(0x1f9e4);
        coverage.add(0x1f9e5);
        coverage.add(0x1f9e6);
        coverage.add(0x1f9e7);
        coverage.add(0x1f9e8);
        coverage.add(0x1f9e9);
        coverage.add(0x1f9ea);
        coverage.add(0x1f9eb);
        coverage.add(0x1f9ec);
        coverage.add(0x1f9ed);
        coverage.add(0x1f9ee);
        coverage.add(0x1f9ef);
        coverage.add(0x1f9f0);
        coverage.add(0x1f9f1);
        coverage.add(0x1f9f2);
        coverage.add(0x1f9f3);
        coverage.add(0x1f9f4);
        coverage.add(0x1f9f5);
        coverage.add(0x1f9f6);
        coverage.add(0x1f9f7);
        coverage.add(0x1f9f8);
        coverage.add(0x1f9f9);
        coverage.add(0x1f9fa);
        coverage.add(0x1f9fb);
        coverage.add(0x1f9fc);
        coverage.add(0x1f9fd);
        coverage.add(0x1f9fe);
        coverage.add(0x1f9ff);
        coverage.add(0x1fa70);
        coverage.add(0x1fa71);
        coverage.add(0x1fa72);
        coverage.add(0x1fa73);
        coverage.add(0x1fa74);
        coverage.add(0x1fa75);
        coverage.add(0x1fa76);
        coverage.add(0x1fa77);
        coverage.add(0x1fa78);
        coverage.add(0x1fa79);
        coverage.add(0x1fa7a);
        coverage.add(0x1fa7b);
        coverage.add(0x1fa7c);
        coverage.add(0x1fa80);
        coverage.add(0x1fa81);
        coverage.add(0x1fa82);
        coverage.add(0x1fa83);
        coverage.add(0x1fa84);
        coverage.add(0x1fa85);
        coverage.add(0x1fa86);
        coverage.add(0x1fa87);
        coverage.add(0x1fa88);
        coverage.add(0x1fa90);
        coverage.add(0x1fa91);
        coverage.add(0x1fa92);
        coverage.add(0x1fa93);
        coverage.add(0x1fa94);
        coverage.add(0x1fa95);
        coverage.add(0x1fa96);
        coverage.add(0x1fa97);
        coverage.add(0x1fa98);
        coverage.add(0x1fa99);
        coverage.add(0x1fa9a);
        coverage.add(0x1fa9b);
        coverage.add(0x1fa9c);
        coverage.add(0x1fa9d);
        coverage.add(0x1fa9e);
        coverage.add(0x1fa9f);
        coverage.add(0x1faa0);
        coverage.add(0x1faa1);
        coverage.add(0x1faa2);
        coverage.add(0x1faa3);
        coverage.add(0x1faa4);
        coverage.add(0x1faa5);
        coverage.add(0x1faa6);
        coverage.add(0x1faa7);
        coverage.add(0x1faa8);
        coverage.add(0x1faa9);
        coverage.add(0x1faaa);
        coverage.add(0x1faab);
        coverage.add(0x1faac);
        coverage.add(0x1faad);
        coverage.add(0x1faae);
        coverage.add(0x1faaf);
        coverage.add(0x1fab0);
        coverage.add(0x1fab1);
        coverage.add(0x1fab2);
        coverage.add(0x1fab3);
        coverage.add(0x1fab4);
        coverage.add(0x1fab5);
        coverage.add(0x1fab6);
        coverage.add(0x1fab7);
        coverage.add(0x1fab8);
        coverage.add(0x1fab9);
        coverage.add(0x1faba);
        coverage.add(0x1fabb);
        coverage.add(0x1fabc);
        coverage.add(0x1fabd);
        coverage.add(0x1fabf);
        coverage.add(0x1fac0);
        coverage.add(0x1fac1);
        coverage.add(0x1fac2);
        coverage.add(0x1fac3);
        coverage.add(0x1fac4);
        coverage.add(0x1fac5);
        coverage.add(0x1face);
        coverage.add(0x1facf);
        coverage.add(0x1fad0);
        coverage.add(0x1fad1);
        coverage.add(0x1fad2);
        coverage.add(0x1fad3);
        coverage.add(0x1fad4);
        coverage.add(0x1fad5);
        coverage.add(0x1fad6);
        coverage.add(0x1fad7);
        coverage.add(0x1fad8);
        coverage.add(0x1fad9);
        coverage.add(0x1fada);
        coverage.add(0x1fadb);
        coverage.add(0x1fae0);
        coverage.add(0x1fae1);
        coverage.add(0x1fae2);
        coverage.add(0x1fae3);
        coverage.add(0x1fae4);
        coverage.add(0x1fae5);
        coverage.add(0x1fae6);
        coverage.add(0x1fae7);
        coverage.add(0x1fae8);
        coverage.add(0x1faf0);
        coverage.add(0x1faf1);
        coverage.add(0x1faf2);
        coverage.add(0x1faf3);
        coverage.add(0x1faf4);
        coverage.add(0x1faf5);
        coverage.add(0x1faf6);
        coverage.add(0x1faf7);
        coverage.add(0x1faf8);
        coverage.add(0xe0030);
        coverage.add(0xe0031);
        coverage.add(0xe0032);
        coverage.add(0xe0033);
        coverage.add(0xe0034);
        coverage.add(0xe0035);
        coverage.add(0xe0036);
        coverage.add(0xe0037);
        coverage.add(0xe0038);
        coverage.add(0xe0039);
        coverage.add(0xe0061);
        coverage.add(0xe0062);
        coverage.add(0xe0063);
        coverage.add(0xe0064);
        coverage.add(0xe0065);
        coverage.add(0xe0066);
        coverage.add(0xe0067);
        coverage.add(0xe0068);
        coverage.add(0xe0069);
        coverage.add(0xe006a);
        coverage.add(0xe006b);
        coverage.add(0xe006c);
        coverage.add(0xe006d);
        coverage.add(0xe006e);
        coverage.add(0xe006f);
        coverage.add(0xe0070);
        coverage.add(0xe0071);
        coverage.add(0xe0072);
        coverage.add(0xe0073);
        coverage.add(0xe0074);
        coverage.add(0xe0075);
        coverage.add(0xe0076);
        coverage.add(0xe0077);
        coverage.add(0xe0078);
        coverage.add(0xe0079);
        coverage.add(0xe007a);
        coverage.add(0xe007f);
        coverage.add(0xfe4e5);
        coverage.add(0xfe4e6);
        coverage.add(0xfe4e7);
        coverage.add(0xfe4e8);
        coverage.add(0xfe4e9);
        coverage.add(0xfe4ea);
        coverage.add(0xfe4eb);
        coverage.add(0xfe4ec);
        coverage.add(0xfe4ed);
        coverage.add(0xfe4ee);
        coverage.add(0xfe82c);
        coverage.add(0xfe82e);
        coverage.add(0xfe82f);
        coverage.add(0xfe830);
        coverage.add(0xfe831);
        coverage.add(0xfe832);
        coverage.add(0xfe833);
        coverage.add(0xfe834);
        coverage.add(0xfe835);
        coverage.add(0xfe836);
        coverage.add(0xfe837);
    }

    /**
     * Pre-cache the ASCII digits to allow for fast glyph substitution. Called once from the constructor and called any
     * time the font selection
     * changes at runtime via setDefaultFont().
     * <p>
     * Pre-cached glyphs for the ASCII digits 0-9 (in that order). Used by renderString() to substitute digit glyphs on
     * the fly
     * as a performance boost. The speed up is most noticeable on the F3 screen which rapidly displays lots of changing
     * numbers.
     * The 4 element array is index by the font style (combination of Font.PLAIN, Font.BOLD, and Font.ITALIC), and each
     * of the
     * nested elements is index by the digit value 0-9.
     *
     * @deprecated {@link GlyphManagerForge#lookupDigits(Font)}
     */
    @Deprecated
    private void cacheDigitGlyphs() {
        /* Need to cache each font style combination; the digitGlyphsReady = false disabled the normal glyph
        substitution mechanism */
        //digitGlyphsReady = false;
        /*digitGlyphs[FormattingCode.PLAIN] = getOrCacheString("0123456789").glyphs;
        digitGlyphs[FormattingCode.BOLD] = getOrCacheString("\u00a7l0123456789").glyphs;
        digitGlyphs[FormattingCode.ITALIC] = getOrCacheString("\u00a7o0123456789").glyphs;
        digitGlyphs[FormattingCode.BOLD | FormattingCode.ITALIC] = getOrCacheString("\u00a7l\u00a7o0123456789")
        .glyphs;*/
        //digitGlyphsReady = true;
    }
}
