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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.arc3d.engine.Engine;
import icyllis.modernui.ModernUI;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.font.BakedGlyph;
import icyllis.modernui.graphics.font.GlyphManager;
import icyllis.modernui.graphics.text.*;
import icyllis.modernui.mc.forge.*;
import icyllis.modernui.mc.text.mixin.AccessFontManager;
import icyllis.modernui.text.*;
import icyllis.modernui.util.Pools;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.font.GlyphVector;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static icyllis.modernui.ModernUI.LOGGER;

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
public class TextLayoutEngine extends FontResourceManager
        implements MuiForgeApi.OnWindowResizeListener, MuiForgeApi.OnDebugDumpListener {

    public static final Marker MARKER = MarkerManager.getMarker("TextLayout");

    /**
     * Config values
     */
    //public static int sDefaultFontSize;
    public static volatile boolean sFixedResolution = false;
    //public static volatile boolean sSuperSampling = false;
    public static volatile int sTextDirection = View.TEXT_DIRECTION_FIRST_STRONG;
    /**
     * Time in seconds to recycle a render node in the cache.
     */
    public static volatile int sCacheLifespan = 6;
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

    public static final ResourceLocation SANS_SERIF = ModernUIForge.location("sans-serif");
    public static final ResourceLocation SERIF = ModernUIForge.location("serif");
    public static final ResourceLocation MONOSPACED = ModernUIForge.location("monospace"); // no -d

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


    /**
     * Whether to use text component object as hash key to lookup in layout cache.
     *
     * @see BaseComponent#hashCode()
     */
    public static volatile boolean sUseComponentCache = true;

    /**
     * Allow text layout to be computed from non-main threads.
     */
    public static volatile boolean sAllowAsyncLayout = true;


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
     * Background thread layout procs.
     *
     * @see #sAllowAsyncLayout
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

    public static final int MIN_PIXEL_DENSITY_FOR_SDF = 4;

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

    private Boolean mForceUnicodeFont;

    /*
     * Remove all formatting code even though it's invalid {@link #getFormattingByCode(char)} == null
     */
    //private static final Pattern FORMATTING_REMOVE_PATTERN = Pattern.compile("\u00a7.");

    /*
     * True if digitGlyphs[] has been assigned and cacheString() can begin replacing all digits with '0' in the string.
     */
    //private boolean digitGlyphsReady = false;

    private int mTimer;

    public TextLayoutEngine() {
        /* StringCache is created by the main game thread; remember it for later thread safety checks */
        //mainThread = Thread.currentThread();

        /* Pre-cache the ASCII digits to allow for fast glyph substitution */
        //cacheDigitGlyphs();

        mGlyphManager.addAtlasInvalidationCallback(invalidationInfo -> {
            if (invalidationInfo.resize()) {
                // When OpenGL texture ID changed (atlas resized), we want to use the new first atlas
                // for batch rendering, we need to clear any existing TextRenderType instances
                TextRenderType.clear();
            } else {
                // called by compact(), need to lookupGlyph() and cacheGlyph() again
                reload();
            }
        });
        // init
        reload();

        mTextRenderer = new ModernTextRenderer(this);
        mStringSplitter = new ModernStringSplitter(this, (int ch, Style style) -> {
            throw new UnsupportedOperationException("Modern Text Engine");
        });
    }

    /**
     * Get the global instance, each call will return the same instance.
     *
     * @return the instance
     */
    @Nonnull
    public static TextLayoutEngine getInstance() {
        return (TextLayoutEngine) FontResourceManager.getInstance();
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

        if (oldLevel == 0) {
            LOGGER.info(MARKER, "Loaded text layout engine, res level: {}, locale: {}, layout RTL: {}",
                    mResLevel, locale, layoutRtl);
        } else {
            // register logical fonts
            mFontCollections.putIfAbsent(SANS_SERIF,
                    Typeface.SANS_SERIF);
            mFontCollections.putIfAbsent(SERIF,
                    Typeface.SERIF);
            mFontCollections.putIfAbsent(MONOSPACED,
                    Typeface.MONOSPACED);
            // register logical fonts in default namespace
            mFontCollections.putIfAbsent(new ResourceLocation(SANS_SERIF.getPath()),
                    Typeface.SANS_SERIF);
            mFontCollections.putIfAbsent(new ResourceLocation(SERIF.getPath()),
                    Typeface.SERIF);
            mFontCollections.putIfAbsent(new ResourceLocation(MONOSPACED.getPath()),
                    Typeface.MONOSPACED);

            if (sDefaultFontBehavior == DEFAULT_FONT_BEHAVIOR_IGNORE_ALL) {
                mFontCollections.put(Minecraft.DEFAULT_FONT, ModernUI.getSelectedTypeface());
            } else {
                LinkedHashSet<FontFamily> defaultFonts = new LinkedHashSet<>();
                populateDefaultFonts(defaultFonts, sDefaultFontBehavior);
                defaultFonts.addAll(ModernUI.getSelectedTypeface().getFamilies());
                mFontCollections.put(Minecraft.DEFAULT_FONT,
                        new FontCollection(defaultFonts.toArray(new FontFamily[0])));
            }

            if (mVanillaFontManager != null) {
                var fontSets = ((AccessFontManager) mVanillaFontManager).getFontSets();
                if (fontSets.get(Minecraft.DEFAULT_FONT) instanceof StandardFontSet standardFontSet) {
                    standardFontSet.reload(mFontCollections.get(Minecraft.DEFAULT_FONT), mResLevel);
                }
                for (var e : fontSets.entrySet()) {
                    if (e.getKey().equals(Minecraft.DEFAULT_FONT)) {
                        continue;
                    }
                    if (e.getValue() instanceof StandardFontSet standardFontSet) {
                        standardFontSet.invalidateCache(mResLevel);
                    }
                }
            }

            LOGGER.info(MARKER, "Reloaded text layout engine, res level: {} to {}, locale: {}, layout RTL: {}",
                    oldLevel, mResLevel, locale, layoutRtl);
        }
    }

    /**
     * Reload both glyph manager and layout engine.
     * Called when any resource changed. This will call {@link #reload()}.
     */
    public void reloadAll() {
        super.reloadAll();
        reload();
    }

    @Override
    public void onWindowResize(int width, int height, int newScale, int oldScale) {
        Boolean forceUnicodeFont = Minecraft.getInstance().options.forceUnicodeFont;
        if (Core.getRenderThread() != null &&
                (newScale != oldScale || !Objects.equals(mForceUnicodeFont, forceUnicodeFont))) {
            reload();
            mForceUnicodeFont = forceUnicodeFont;
        }
    }

    @Override
    public void onDebugDump(@Nonnull PrintWriter pw) {
        pw.print("TextLayoutEngine: ");
        pw.print("CacheCount=" + getCacheCount());
        long memorySize = getCacheMemorySize();
        pw.println(", CacheSize=" + TextUtils.binaryCompact(memorySize) + " (" + memorySize + " bytes)");
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

    private void populateDefaultFonts(Set<FontFamily> set, int behavior) {
        if (mDefaultFontCollection == null) {
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
                .thenAcceptAsync(results -> {
                    reloadProfiler.startTick();
                    reloadProfiler.push("reload");
                    applyResources(results);
                    reloadProfiler.pop();
                    reloadProfiler.endTick();
                }, reloadExecutor);
    }

    private static final class LoadResults extends FontResourceManager.LoadResults {
        volatile Map<ResourceLocation, FontCollection> mFontCollections;
    }

    // ASYNC
    @Nonnull
    private CompletableFuture<LoadResults> prepareResources(@Nonnull ResourceManager resourceManager,
                                                            @Nonnull Executor preparationExecutor) {
        final var results = new LoadResults();
        final var loadFonts = CompletableFuture.runAsync(() ->
                        loadFonts(resourceManager, results),
                preparationExecutor);
        final var loadEmojis = CompletableFuture.runAsync(() ->
                        loadEmojis(resourceManager, results),
                preparationExecutor);
        final var loadShortcodes = CompletableFuture.runAsync(() ->
                        loadShortcodes(resourceManager, results),
                preparationExecutor);
        return CompletableFuture.allOf(loadFonts, loadEmojis, loadShortcodes)
                .thenApply(__ -> results);
    }

    // SYNC
    private void applyResources(@Nonnull LoadResults results) {
        close();
        // reload fonts
        mFontCollections.clear();
        mFontCollections.putAll(results.mFontCollections);
        mDefaultFontCollection = mFontCollections.get(Minecraft.DEFAULT_FONT);
        // vanilla compatibility
        if (mVanillaFontManager != null) {
            var fontSets = ((AccessFontManager) mVanillaFontManager).getFontSets();
            fontSets.values().forEach(FontSet::close);
            fontSets.clear();
            var textureManager = Minecraft.getInstance().getTextureManager();
            mFontCollections.forEach((fontName, fontCollection) -> {
                var fontSet = new StandardFontSet(textureManager, fontName);
                fontSet.reload(fontCollection, mResLevel);
                fontSets.put(fontName, fontSet);
            });
            {
                var fontSet = new StandardFontSet(textureManager, Minecraft.UNIFORM_FONT);
                fontSet.reload(ModernUI.getSelectedTypeface(), mResLevel);
                fontSets.put(Minecraft.UNIFORM_FONT, fontSet);
            }
        } else {
            LOGGER.warn(MARKER, "Where is font manager?");
        }
        if (mDefaultFontCollection == null) {
            throw new IllegalStateException("Default font failed to load");
        }
        super.applyResources(results);
    }

    @Override
    public void close() {
        // close bitmaps if never baked
        for (var fontCollection : mFontCollections.values()) {
            for (var family : fontCollection.getFamilies()) {
                if (family.getClosestMatch(FontPaint.NORMAL) instanceof BitmapFont bitmapFont) {
                    bitmapFont.close();
                }
            }
        }
        if (mDefaultFontCollection != null) {
            for (var family : mDefaultFontCollection.getFamilies()) {
                if (family.getClosestMatch(FontPaint.NORMAL) instanceof BitmapFont bitmapFont) {
                    bitmapFont.close();
                }
            }
        }
        TextRenderType.clear();
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
    private static void loadFonts(@Nonnull ResourceManager resources, @Nonnull LoadResults results) {
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
                                    if (metadata.has("size")) {
                                        LOGGER.info(MARKER, "Ignore 'size' of providers[{}] in font '{}'",
                                                i, name);
                                    }
                                    if (metadata.has("oversample")) {
                                        LOGGER.info(MARKER, "Ignore 'oversample' of providers[{}] in font '{}'",
                                                i, name);
                                    }
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

    ////// END Resource Reloading


    /**
     * Vanilla font size is 8, but SDF text requires at least 32px to be clear enough,
     * then resolution level is adjusted to 4.
     */
    public static int adjustPixelDensityForSDF(int resLevel) {
        return Math.max(resLevel, MIN_PIXEL_DENSITY_FOR_SDF);
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
            if (sAllowAsyncLayout) {
                TextLayoutProcessor proc = mProcessorPool.acquire();
                if (proc == null) {
                    proc = new TextLayoutProcessor(this);
                }
                TextLayout layout = proc.createVanillaLayout(text, style, mResLevel, computeFlags);
                mProcessorPool.release(proc);
                return layout;
            } else {
                return Minecraft.getInstance().submit(
                                () -> lookupVanillaLayout(text, style, computeFlags)
                        )
                        .join();
            }
        }
        TextLayout layout = mVanillaCache.get(mVanillaLookupKey.update(text, style));
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
            if (sAllowAsyncLayout) {
                TextLayoutProcessor proc = mProcessorPool.acquire();
                if (proc == null) {
                    proc = new TextLayoutProcessor(this);
                }
                TextLayout layout = proc.createTextLayout(text, style, mResLevel, computeFlags);
                mProcessorPool.release(proc);
                return layout;
            } else {
                return Minecraft.getInstance().submit(
                                () -> lookupFormattedLayout(text, style, computeFlags)
                        )
                        .join();
            }
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
            layout = mFormattedCache.get(mFormattedLayoutKey.update(text, style));
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
            if (sAllowAsyncLayout) {
                TextLayoutProcessor proc = mProcessorPool.acquire();
                if (proc == null) {
                    proc = new TextLayoutProcessor(this);
                }
                TextLayout layout = proc.createSequenceLayout(sequence, mResLevel, computeFlags);
                mProcessorPool.release(proc);
                return layout;
            } else {
                return Minecraft.getInstance().submit(
                                () -> lookupFormattedLayout(sequence, computeFlags)
                        )
                        .join();
            }
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
                layout = mFormattedCache.get(mFormattedLayoutKey.update(text, Style.EMPTY));
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
            TextLayout layout = mFormattedCache.get(mFormattedLayoutKey.update(sequence));
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
        if (mForceUnicodeFont == Boolean.TRUE &&
                fontName.equals(Minecraft.DEFAULT_FONT)) {
            fontName = Minecraft.UNIFORM_FONT;
        }
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
    public BakedGlyph lookupGlyph(Font font, int devSize, int glyphId) {
        if (font instanceof BitmapFont bitmapFont) {
            // auto bake
            return bitmapFont.getGlyph(glyphId);
        }
        return mGlyphManager.lookupGlyph(font, devSize, glyphId);
    }

    public int getEmojiTexture() {
        return mGlyphManager.getCurrentTexture(Engine.MASK_FORMAT_ARGB);
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

    /*public void dumpEmojiAtlas() {
        if (mEmojiAtlas != null) {
            String basePath = Bitmap.saveDialogGet(
                    Bitmap.SaveFormat.PNG, null, "EmojiAtlas");
            mEmojiAtlas.debug(basePath);
        }
    }*/

    /*public long getEmojiAtlasMemorySize() {
        if (mEmojiAtlas != null) {
            return mEmojiAtlas.getMemorySize();
        }
        return 0;
    }*/

    /**
     * Ticks the caches and clear unused entries.
     */
    public void onEndClientTick() {
        if (mTimer == 0) {
            //int oldCount = getCacheCount();
            final int lifespan = sCacheLifespan;
            final Predicate<TextLayout> ticker = layout -> layout.tick(lifespan);
            mVanillaCache.values().removeIf(ticker);
            mComponentCache.values().removeIf(ticker);
            mFormattedCache.values().removeIf(ticker);
            /*if (oldCount >= sRehashThreshold) {
                int newCount = getCacheCount();
                if (newCount < sRehashThreshold) {
                    mVanillaCache = new HashMap<>(mVanillaCache);
                    mComponentCache = new HashMap<>(mComponentCache);
                    mFormattedCache = new HashMap<>(mFormattedCache);
                }
            }*/
            boolean useTextShadersEffective = Config.TEXT.mUseTextShadersInWorld.get()
                    && !ModernUIForge.Client.areShadersEnabled();
            if (sUseTextShadersInWorld != useTextShadersEffective) {
                reload();
                sUseTextShadersInWorld = useTextShadersEffective;
            }
        }
        // convert ticks to seconds
        mTimer = (mTimer + 1) % 20;
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

    public int getResLevel() {
        return mResLevel;
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
        int deviceFontSize = 1;
        if (desc.font instanceof OutlineFont) {
            deviceFontSize = TextLayoutProcessor.computeFontSize(desc.resLevel);
            awtFont = ((OutlineFont) desc.font).chooseFont(deviceFontSize);
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
                glyph = mGlyphManager.lookupGlyph(desc.font, deviceFontSize, vector.getGlyphCode(0));
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
                glyph = mGlyphManager.lookupGlyph(desc.font, deviceFontSize, vector.getGlyphCode(0));
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
                glyph = mGlyphManager.lookupGlyph(desc.font, deviceFontSize, vector.getGlyphCode(0));
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
