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

package icyllis.modernui.mc.text;

import com.google.gson.*;
import com.ibm.icu.text.Bidi;
import com.mojang.blaze3d.font.SpaceProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.JsonOps;
import icyllis.arc3d.engine.Engine;
import icyllis.modernui.ModernUI;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.font.BakedGlyph;
import icyllis.modernui.graphics.font.GlyphManager;
import icyllis.modernui.graphics.text.*;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.text.mixin.AccessFontManager;
import icyllis.modernui.mc.text.mixin.MixinClientLanguage;
import icyllis.modernui.text.*;
import icyllis.modernui.util.Pools;
import icyllis.modernui.view.View;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.*;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.font.GlyphVector;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.*;
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
        implements MuiModApi.OnWindowResizeListener, MuiModApi.OnDebugDumpListener {

    public static final Marker MARKER = MarkerManager.getMarker("TextLayout");

    /**
     * Config values
     */
    //public static int sDefaultFontSize;
    public static volatile boolean sFixedResolution = false;
    //public static volatile boolean sSuperSampling = false;
    public static volatile int sTextDirection = View.TEXT_DIRECTION_FIRST_STRONG;
    /**
     * Time in seconds to recycle a {@link TextLayout} entry in the cache.
     * <p>
     * We have an internal layout cache, so entries in this cache can be evicted quickly.
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

    /**
     * Logical font names, CSS convention.
     */
    public static final ResourceLocation SANS_SERIF = ModernUIMod.location("sans-serif");
    public static final ResourceLocation SERIF = ModernUIMod.location("serif");
    public static final ResourceLocation MONOSPACED = ModernUIMod.location("monospace"); // no -d

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
     * @see MutableComponent#hashCode()
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
    private Map<MutableComponent, TextLayout> mComponentCache = new HashMap<>();

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

    private final HashMap<ResourceLocation, FontCollection> mRegisteredFonts = new HashMap<>();

    public static final int MIN_PIXEL_DENSITY_FOR_SDF = 4;

    /*
     * Emoji sequence to sprite index (used as glyph code in emoji atlas).
     */
    //private final ArrayList<String> mEmojiFiles = new ArrayList<>();

    /*
     * The emoji texture atlas.
     */
    /*private GLFontAtlas mEmojiAtlas;
    private ByteBuffer mEmojiBuffer;*/

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
            mResLevel = Math.min(scale, MuiModApi.MAX_GUI_SCALE);
        }
        var opts = Minecraft.getInstance().options;
        //noinspection ConstantValue
        if (opts != null) { // this can be null on Fabric, because this class loads too early
            mForceUnicodeFont = opts.forceUnicodeFont().get();
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
    @RenderThread
    public void reloadAll() {
        super.reloadAll();
        reload();
    }

    @Override
    public void onWindowResize(int width, int height, int newScale, int oldScale) {
        if (Core.getRenderThread() != null) {
            boolean reload = (newScale != oldScale);
            if (!reload) {
                Boolean forceUnicodeFont = Minecraft.getInstance().options.forceUnicodeFont().get();
                reload = !Objects.equals(mForceUnicodeFont, forceUnicodeFont);
            }
            if (reload) {
                reload();
            }
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
                        "minecraft:include/space / minecraft:space" -> {
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
        mFontCollections.putAll(mRegisteredFonts);
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

    @Override
    public void onFontRegistered(FontFamily f) {
        super.onFontRegistered(f);
        String name = f.getFamilyName();
        try {
            String newName = name.toLowerCase(Locale.ROOT)
                    .replaceAll(" ", "-");
            var fc = new FontCollection(f);
            var location = ModernUIMod.location(newName);
            mRegisteredFonts.putIfAbsent(location, fc);
            LOGGER.info(MARKER, "Redirect registered font '{}' to '{}'", name, location);
            // also register in minecraft namespace
            mRegisteredFonts.putIfAbsent(new ResourceLocation(newName), fc);
        } catch (Exception e) {
            LOGGER.warn(MARKER, "Failed to redirect registered font '{}'", name);
        }
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

    private static final class RawFontBundle
            implements DependencySorter.Entry<ResourceLocation> {
        final ResourceLocation name;
        /**
         * We load font families other than {@link #isUnicodeFont(ResourceLocation)}.
         * <p>
         * Either FontFamily or ResourceLocation (reference).
         */
        Set<Object> families = new LinkedHashSet<>();
        /**
         * References to other fonts.
         */
        Set<ResourceLocation> dependencies = new HashSet<>();

        RawFontBundle(ResourceLocation name) {
            this.name = name;
        }

        @Override
        public void visitRequiredDependencies(@Nonnull Consumer<ResourceLocation> visitor) {
            dependencies.forEach(visitor);
        }

        @Override
        public void visitOptionalDependencies(@Nonnull Consumer<ResourceLocation> visitor) {
        }
    }

    // ASYNC
    private static void loadFonts(@Nonnull ResourceManager resources, @Nonnull LoadResults results) {
        final var gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        final var bundles = new ArrayList<RawFontBundle>();
        for (var entry : resources.listResourceStacks("font",
                res -> res.getPath().endsWith(".json")).entrySet()) {
            var location = entry.getKey();
            var path = location.getPath();
            // remove prefix 'font/' and extension '.json' to get the font name
            var name = location.withPath(path.substring(5, path.length() - 5));
            if (isUnicodeFont(name)) {
                continue;
            }
            var bundle = new RawFontBundle(name);
            bundles.add(bundle);
            for (var resource : entry.getValue()) {
                try (var reader = resource.openAsReader()) {
                    var providers = GsonHelper.getAsJsonArray(Objects.requireNonNull(
                            GsonHelper.fromJson(gson, reader, JsonObject.class)), "providers");
                    for (int i = 0; i < providers.size(); i++) {
                        var metadata = GsonHelper.convertToJsonObject(
                                providers.get(i), "providers[" + i + "]");
                        var definition = Util.getOrThrow(
                                GlyphProviderDefinition.CODEC.parse(
                                        JsonOps.INSTANCE,
                                        metadata
                                ),
                                JsonParseException::new
                        );
                        loadSingleFont(resources, name, bundle,
                                resource.sourcePackId(), i, metadata, definition);
                    }
                } catch (Exception e) {
                    LOGGER.warn(MARKER, "Failed to load font '{}' in pack: '{}'",
                            name, resource.sourcePackId(), e);
                }
            }
            LOGGER.debug(MARKER, "Loaded raw font resource: '{}', font set: [{}]", location,
                    bundle.families.stream().map(object -> {
                                if (object instanceof FontFamily family) {
                                    return family.getFamilyName();
                                }
                                return object.toString();
                            })
                            .collect(Collectors.joining(",")));
        }
        final var sorter = new DependencySorter<ResourceLocation, RawFontBundle>();
        for (var bundle : bundles) {
            sorter.addEntry(bundle.name, bundle);
        }
        final var map = new HashMap<ResourceLocation, FontCollection>();
        sorter.orderByDependencies((name, bundle) -> {
            if (isUnicodeFont(name)) {
                return;
            }
            var set = new LinkedHashSet<FontFamily>();
            for (var object : bundle.families) {
                if (object instanceof FontFamily family) {
                    set.add(family);
                } else {
                    var reference = (ResourceLocation) object;
                    FontCollection resolved = map.get(reference);
                    if (resolved != null) {
                        set.addAll(resolved.getFamilies());
                    } else {
                        LOGGER.warn(MARKER, "Failed to resolve font: {}", reference);
                    }
                }
            }
            if (!set.isEmpty()) {
                map.put(name, new FontCollection(set.toArray(new FontFamily[0])));
                LOGGER.info(MARKER, "Loaded font: '{}', font set: [{}]", name,
                        set.stream().map(FontFamily::getFamilyName).collect(Collectors.joining(",")));
            } else {
                LOGGER.warn(MARKER, "Ignore font: '{}', because it's empty", name);
            }
        });
        results.mFontCollections = map;
    }

    private static void loadSingleFont(@Nonnull ResourceManager resources,
                                       ResourceLocation name,
                                       RawFontBundle bundle,
                                       String sourcePackId, int index,
                                       JsonObject metadata,
                                       @Nonnull GlyphProviderDefinition definition) {
        switch (definition.type()) {
            case BITMAP -> {
                var bitmapFont = BitmapFont.create((BitmapProvider.Definition) definition, resources);
                bundle.families.add(
                        new FontFamily(bitmapFont)
                );
            }
            case TTF -> {
                var ttf = (TrueTypeGlyphProviderDefinition) definition;
                if (metadata.has("size")) {
                    LOGGER.info(MARKER, "Ignore 'size={}' of providers[{}] in font '{}' in pack: '{}'",
                            ttf.size(), index, name, sourcePackId);
                }
                if (metadata.has("oversample")) {
                    LOGGER.info(MARKER, "Ignore 'oversample={}' of providers[{}] in font '{}' in pack: '{}'",
                            ttf.oversample(), index, name, sourcePackId);
                }
                if (metadata.has("shift")) {
                    LOGGER.info(MARKER, "Ignore 'shift={}' of providers[{}] in font '{}' in pack: '{}'",
                            ttf.shift(), index, name, sourcePackId);
                }
                if (metadata.has("skip")) {
                    LOGGER.info(MARKER, "Ignore 'skip={}' of providers[{}] in font '{}' in pack: '{}'",
                            ttf.skip(), index, name, sourcePackId);
                }
                bundle.families.add(
                        createTTF(ttf.location(), resources)
                );
            }
            case SPACE -> {
                var spaceFont = SpaceFont.create(name, (SpaceProvider.Definition) definition);
                bundle.families.add(
                        new FontFamily(spaceFont)
                );
            }
            case REFERENCE -> {
                ResourceLocation reference = ((ProviderReferenceDefinition) definition).id();
                if (!isUnicodeFont(reference)) {
                    bundle.families.add(reference);
                    bundle.dependencies.add(reference);
                }
            }
            default -> LOGGER.info(MARKER, "Unknown provider type '{}' in font '{}' in pack: '{}'",
                    definition.type(), name, sourcePackId);
        }
    }

    @Nonnull
    private static FontFamily createTTF(@Nonnull ResourceLocation file, ResourceManager resources) {
        var location = file.withPrefix("font/");
        try (var stream = resources.open(location)) {
            return FontFamily.createFamily(stream, false);
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
        if (text == CommonComponents.EMPTY || text == FormattedText.EMPTY) {
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
                text instanceof MutableComponent component) {
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
            if (text == CommonComponents.EMPTY || text == FormattedText.EMPTY) {
                return TextLayout.EMPTY;
            }
            TextLayout layout;
            if (sUseComponentCache &&
                    text instanceof MutableComponent component) {
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
     * Minecraft gives us a deeply processed sequence, so we have to make
     * it not a reordered text, see {@link MixinClientLanguage}.
     * So actually it's a copy of original text, then we can use our layout engine later
     *
     * @param sequence a char sequence copied from the original string
     * @param consumer what to do with a part of styled char sequence
     * @return {@code false} if action stopped on the way, {@code true} if the whole text was handled
     */
    @Deprecated
    public boolean handleSequence(FormattedCharSequence sequence, ReorderTextHandler.IConsumer consumer) {
        throw new UnsupportedOperationException();
    }

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
                    && !ModernUIClient.areShadersEnabled();
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

    @Nullable
    @Deprecated
    private TextLayout generateAndCache(VanillaLayoutKey key, @Nonnull CharSequence string,
                                        @Nonnull final Style style) {
        /*final int length = string.length();
        final TextProcessRegister register = this.register;

        register.beginProcess(style);

        int codePoint;
        TexturedGlyph glyph;
        for (int stringIndex = 0, glyphIndex = 0; stringIndex < length; stringIndex++) {
            char c1 = string.charAt(stringIndex);

            if (stringIndex + 1 < length) {
                if (c1 == '\u00a7') {
                    TextFormatting formatting = fromFormattingCode(string.charAt(++stringIndex));
                    if (formatting != null) {
                        register.applyFormatting(formatting, glyphIndex);
                    }*/
        /*switch (code) {
         *//* Obfuscated *//*
                        case 16:
                            if (state.setObfuscated(true)) {
                                if (!state.isDigitMode()) {
                                    state.setDigitGlyphs(glyphManager.lookupDigits(state.getFontStyle(),
                                    sDefaultFontSize));
                                }
                                if (state.isDigitMode() && state.hasDigit()) {
                                    strings.add(StringRenderInfo.ofDigit(state.getDigitGlyphs(),
                                            state.getColor(), state.toDigitIndexArray()));
                                } else if (!glyphs.isEmpty()) {
                                    strings.add(StringRenderInfo.ofText(glyphs.toArray(new TexturedGlyph[0]),
                                            state.getColor()));
                                }
                            }
                            break;

                        *//* Bold *//*
                        case 17:
                            if (state.setBold(true)) {
                                if (state.getObfuscatedCount() > 0) {
                                    strings.add(StringRenderInfo.ofObfuscated(state.getDigitGlyphs(),
                                            state.getColor(), state.getObfuscatedCount()));
                                } else if (state.isDigitMode() && state.hasDigit()) {
                                    strings.add(StringRenderInfo.ofDigit(state.getDigitGlyphs(),
                                            state.getColor(), state.toDigitIndexArray()));
                                }
                                if (state.isDigitMode() || state.isObfuscated()) {
                                    state.setDigitGlyphs(glyphManager.lookupDigits(state.getFontStyle(),
                                    sDefaultFontSize));
                                }
                            }
                            break;

                        case 18:
                            state.setStrikethrough(true);
                            break;

                        case 19:
                            state.setUnderline(true);
                            break;

                        case 20:
                            if (state.setItalic(true)) {
                                if (state.getObfuscatedCount() > 0) {
                                    strings.add(StringRenderInfo.ofObfuscated(state.getDigitGlyphs(),
                                            state.getColor(), state.getObfuscatedCount()));
                                } else if (state.isDigitMode() && state.hasDigit()) {
                                    strings.add(StringRenderInfo.ofDigit(state.getDigitGlyphs(),
                                            state.getColor(), state.toDigitIndexArray()));
                                }
                                if (state.isDigitMode() || state.isObfuscated()) {
                                    state.setDigitGlyphs(glyphManager.lookupDigits(state.getFontStyle(),
                                    sDefaultFontSize));
                                }
                            }
                            break;

                        *//* Reset *//*
                        case 21: {
                            int pColor = state.getColor();
                            if (state.setDefaultColor()) {
                                if (state.getObfuscatedCount() > 0) {
                                    strings.add(StringRenderInfo.ofObfuscated(state.getDigitGlyphs(),
                                            pColor, state.getObfuscatedCount()));
                                } else if (state.isDigitMode() && state.hasDigit()) {
                                    strings.add(StringRenderInfo.ofDigit(state.getDigitGlyphs(),
                                            pColor, state.toDigitIndexArray()));
                                } else if (!glyphs.isEmpty()) {
                                    strings.add(StringRenderInfo.ofText(glyphs.toArray(new TexturedGlyph[0]),
                                            pColor));
                                }
                                if (state.isUnderline()) {
                                    effects.add(EffectRenderInfo.ofUnderline(state.getUnderlineStart(), state
                                    .getAdvance(), pColor));
                                }
                                if (state.isStrikethrough()) {
                                    effects.add(EffectRenderInfo.ofStrikethrough(state.getUnderlineStart(), state
                                    .getAdvance(), pColor));
                                }
                                state.setDefaultFontStyle();
                                state.setDefaultObfuscated();
                                state.setDefaultStrikethrough();
                                state.setDefaultUnderline();
                            } else {
                                if (state.isObfuscated() && state.setDefaultObfuscated() && state.getObfuscatedCount
                                () > 0) {
                                    strings.add(StringRenderInfo.ofObfuscated(state.getDigitGlyphs(),
                                            pColor, state.getObfuscatedCount()));
                                }
                            }
                        }
                            *//*fontStyle = defStyle;
                            color = defColor;
                        {
                            boolean p = strikethrough;
                            strikethrough = defStrikethrough;
                            if (!strikethrough && p) {
                                effects.add(EffectRenderInfo.ofStrikethrough(strikethroughX, advance, color));
                            }
                        }
                        {
                            boolean p = underline;
                            underline = defUnderline;
                            if (!underline && p) {
                                effects.add(EffectRenderInfo.ofUnderline(underlineX, advance, color));
                            }
                        }
                        {
                            boolean p = obfuscated;
                            obfuscated = defObfuscated;
                            if (!obfuscated && p) {
                                if (!glyphs.isEmpty()) {
                                    strings.add(new StringRenderInfo(glyphs.toArray(new TexturedGlyph[0]), color,
                                    true));
                                    glyphs.clear();
                                }
                            }
                        }*//*
                        break;

                        default:
                            if (code != -1) {
                                int c = Color3i.fromFormattingCode(code).getColor();
                                //processState.setColor(c, this::addStringAndEffectInfo);
                                *//*if (color != c) {
                                    if (!glyphs.isEmpty()) {
                                        strings.add(new StringRenderInfo(glyphs.toArray(new TexturedGlyph[0]), color,
                                         obfuscated));
                                        color = c;
                                        glyphs.clear();
                                    }
                                    if (strikethrough) {
                                        effects.add(new EffectRenderInfo(strikethroughX, advance, color,
                                        EffectRenderInfo.STRIKETHROUGH));
                                        strikethroughX = advance;
                                    }
                                    if (underline) {
                                        effects.add(new EffectRenderInfo(underlineX, advance, color, EffectRenderInfo
                                        .UNDERLINE));
                                        underlineX = advance;
                                    }
                                }*//*
                            }
                            break;
                    }*/
                    /*continue;

                } else if (Character.isHighSurrogate(c1)) {
                    char c2 = string.charAt(stringIndex + 1);
                    if (Character.isLowSurrogate(c2)) {
                        codePoint = Character.toCodePoint(c1, c2);
                        ++stringIndex;
                    } else {
                        codePoint = c1;
                    }
                } else {
                    codePoint = c1;
                }
            } else {
                codePoint = c1;
            }*/

            /*if (codePoint >= 48 && codePoint <= 57) {
                TexturedGlyph[] digits = glyphManager.lookupDigits(processRegister.getFontStyle(), sDefaultFontSize);
                //processState.nowDigit(i, digits, this::addStringInfo);
                processRegister.addAdvance(digits[0].advance);
            } else {
                //processState.nowText(this::addStringInfo);
                glyph = glyphManager.lookupGlyph(codePoint, processRegister.getFontStyle(), sDefaultFontSize);
                processRegister.addAdvance(glyph.advance);
                glyphs.add(glyph);
            }*/

            /*if (register.peekFontStyle()) {
                register.setDigitGlyphs(glyphManager.lookupDigits(
                        register.getFontStyle(), sDefaultFontSize));
            }*/
            /*if (register.isObfuscated() || (codePoint <= 57 && codePoint >= 48)) {
                register.depositDigit(stringIndex, glyphManager.lookupDigits(register.getFontStyle(),
                sDefaultFontSize));
            } else {
                register.depositGlyph(glyphManager.lookupGlyph(codePoint, register.getFontStyle(), sDefaultFontSize));
            }
            glyphIndex++;
        }*/

        /*if (strikethrough) {
            effects.add(new EffectRenderInfo(strikethroughX, advance, color, EffectRenderInfo.STRIKETHROUGH));
        }

        if (underline) {
            effects.add(new EffectRenderInfo(underlineX, advance, color, EffectRenderInfo.UNDERLINE));
        }

        if (!glyphs.isEmpty()) {
            strings.add(new StringRenderInfo(glyphs.toArray(new TexturedGlyph[0]), color, obfuscated));
            glyphs.clear();
        }*/
        //addStringAndEffectInfo(processState);

        //register.finishProcess();

        // Async work, waiting for render thread
        if (!RenderSystem.isOnRenderThread()) {
            // The game thread is equal to render thread now
            /*synchronized (lock) {
                Minecraft.getInstance()
                        .submit(() -> generateVanillaNode(key, string, style))
                        .whenComplete((n, t) -> {
                            atomicNode.set(n);
                            synchronized (lock) {
                                lock.notify();
                            }
                        });
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TextRenderNode node = atomicNode.get();
                atomicNode.set(null);
                return node;
            }*/
            return Minecraft.getInstance()
                    .submit(() -> generateAndCache(key, string, style))
                    .join();
        }

        /*final TextLayoutProcessor data = this.mProcessor;

         *//* Step 1 *//*
        char[] text = resolveFormattingCodes(data, string, style);

        final TextRenderNode node;

        if (text.length > 0) {
            *//* Step 2-5 *//*
            startBidiAnalysis(data, text);

            if (data.mAllList.isEmpty()) {
                *//* Sometimes naive, too young too simple *//*
                node = TextRenderNode.EMPTY;
            } else {
                *//* Step 6 *//*
                adjustGlyphIndex(data);

                *//* Step 7 *//*
                insertColorState(data);

                *//* Step 8 *//*
                GlyphRender[] glyphs = data.wrapGlyphs();

                *//* Step 9 *//*
                node = new TextRenderNode(glyphs, data.mAdvance, data.mHasEffect);
            }
        }

        *//* Sometimes naive, too young too simple *//*
        else {
            node = TextRenderNode.EMPTY;
        }
        data.release();

        stringCache.put(key, node);*/

        return null;
    }

    /**
     * Add a string to the string cache by perform full layout on it, remembering its glyph positions, and making sure
     * that
     * every font glyph used by the string is pre-rendering. If this string has already been cached, then simply return
     * its
     * existing Entry from the cache. Note that for caching purposes, this method considers two strings to be identical
     * if they
     * only differ in their ASCII digits; the renderString() method performs fast glyph substitution based on the actual
     * digits
     * in the string at the time.
     *
     * @param str this String will be laid out and added to the cache (or looked up, if already cached)
     * @return the string's cache entry containing all the glyph positions
     */
    @Nonnull
    @Deprecated
    private Entry getOrCacheString(@Nonnull String str) {
        /*
         * New Key object allocated only if the string was not found in the StringCache using lookupKey. This
         * variable must
         * be outside the (entry == null) code block to have a temporary strong reference between the time when the
         * Key is
         * added to stringCache and added to weakRefCache.
         */
        Key key;

        /* Either a newly created Entry object for the string, or the cached Entry if the string is already in the
        cache */
        Entry entry;

        /* Don't perform a cache lookup from other threads because the stringCache is not synchronized */
        RenderSystem.assertOnRenderThread();
        //if () {
        /* Re-use existing lookupKey to avoid allocation overhead on the critical rendering path */
        //lookupKey.str = str;

        /* If this string is already in the cache, simply return the cached Entry object */
        //entry = stringCache.getIfPresent(lookupKey);
        //}
        //ModernUI.LOGGER.info("cache size {}", stringCache.size());
        /* If string is not cached (or not on main thread) then layout the string */
        //if (false) {
        //ModernUI.LOGGER.info("new entry for {}", str);
        /* layoutGlyphVector() requires a char[] so create it here and pass it around to avoid duplication later on */
        char[] text = str.toCharArray();

        /* First extract all formatting codes from the string */
        entry = new Entry();
        int length = extractFormattingCodes(entry, str, text); // return total string length except formatting codes

        /* Layout the entire string, splitting it up by formatting codes and the Unicode bidirectional algorithm */
        List<Glyph> glyphList = new ArrayList<>();

        entry.advance = layoutBidiString(glyphList, text, 0, length, entry.codes);

        /* Convert the accumulated Glyph list to an array for efficient storage */
        entry.glyphs = new Glyph[glyphList.size()];
        entry.glyphs = glyphList.toArray(entry.glyphs);

        /*
         * Sort Glyph array by stringIndex so it can be compared during rendering to the already sorted ColorCode array.
         * This will apply color codes in the string's logical character order and not the visual order on screen.
         */
        Arrays.sort(entry.glyphs);

        /* Do some post-processing on each Glyph object */
        int colorIndex = 0, shift = 0;
        for (int glyphIndex = 0; glyphIndex < entry.glyphs.length; glyphIndex++) {
            Glyph glyph = entry.glyphs[glyphIndex];

            /*
             * Adjust the string index for each glyph to point into the original string with unstripped color codes.
             * The while
             * loop is necessary to handle multiple consecutive color codes with no visible glyphs between them.
             * These new adjusted
             * stringIndex can now be compared against the color stringIndex during rendering. It also allows lookups
             *  of ASCII
             * digits in the original string for fast glyph replacement during rendering.
             */
            //while (colorIndex < entry.codes.length && glyph.stringIndex + shift >= entry.codes[colorIndex]
            // .stringIndex) {
            //    shift += 2;
            //    colorIndex++;
            //}
            //glyph.stringIndex += shift;
        }

        /*
         * Do not actually cache the string when called from other threads because GlyphCache.cacheGlyphs() will not
         * have been called
         * and the cache entry does not contain any texture data needed for rendering.
         */
        //if (mainThread == Thread.currentThread()) {
        /* Wrap the string in a Key object (to change how ASCII digits are compared) and cache it along with the
        newly generated Entry */
        key = new Key();

        /* Make a copy of the original String to avoid creating a strong reference to it */
        key.str = str;
        //entry.keyRef = new WeakReference<>(key);
        //stringCache.put(key, entry);
        //ModernUI.LOGGER.debug("cache string {}", key.str);
        //}
        //lookupKey.str = null;
        //}

        /* Do not access weakRefCache from other threads since it is unsynchronized, and for a newly created entry,
        the keyRef is null */
        /*if (mainThread == Thread.currentThread()) {
         *//*
         * Add the String passed into this method to the stringWeakMap so it keeps the Key reference live as long as
         the String is in use.
         * If an existing Entry was already found in the stringCache, it's possible that its Key has already been
         garbage collected. The
         * code below checks for this to avoid adding (str, null) entries into weakRefCache. Note that if a new Key
         object was created, it
         * will still be live because of the strong reference created by the "key" variable.
         *//*
            Key oldKey = entry.keyRef.get();
            if (oldKey != null) {
                //weakRefCache.put(str, oldKey);
            }
            lookupKey.str = null;
        }*/

        /* Return either the existing or the newly created entry so it can be accessed immediately */
        return entry;
    }

    /**
     * Finally, we got a piece of text with same layout direction, font style and whether to be obfuscated.
     *
     * @param data   an object to store the results
     * @param text   the plain text (without formatting codes) to analyze
     * @param start  start index (inclusive) of the text
     * @param limit  end index (exclusive) of the text
     * @param flag   layout direction
     * @param font   the derived font with fontStyle and fontSize
     * @param random whether to layout obfuscated characters or not
     * @param effect text render effect
     */
    @Deprecated
    private void layoutFont(TextLayoutProcessor data, char[] text, int start, int limit, int flag, Font font,
                            boolean random,
                            byte effect) {
        /*if (random) {
         *//* Random is not worthy to layout *//*
            layoutRandom(data, text, start, limit, flag, font, effect);
        } else {
            *//* The glyphCode matched to the same codePoint is specified in the font, they are different in different
            font *//*
            GlyphVector vector = glyphManager.layoutGlyphVector(font, text, start, limit, flag);
            int num = vector.getNumGlyphs();

            final GlyphManagerForge.VanillaGlyph[] digits = glyphManager.lookupDigits(font);
            final float factor = glyphManager.getResolutionFactor();

            for (int i = 0; i < num; i++) {
                *//* Back compatibility for Java 8, since LayoutGlyphVector should not have non-standard glyphs
         * HarfBuzz is introduced in Java 11 or higher
         *//*
         *//*if (vector.getGlyphMetrics(i).getAdvanceX() == 0 &&
                        vector.getGlyphMetrics(i).getBounds2D().getWidth() == 0) {
                    continue;
                }*//*

                int stripIndex = vector.getGlyphCharIndex(i) + start;
                Point2D point = vector.getGlyphPosition(i);

                float offset = (float) (point.getX() / factor);

                *//*if (flag == Font.LAYOUT_RIGHT_TO_LEFT) {
                    offset += data.mLayoutRight;
                } else {
                    offset += data.mAdvance;
                }*//*

                char o = text[stripIndex];
                *//* Digits are not on SMP *//*
                if (o == '0') {
                    //data.mStyleList.add(new DigitGlyphRender(digits, effect, stripIndex, offset));
                    continue;
                }

                int glyphCode = vector.getGlyphCode(i);
                GlyphManagerForge.VanillaGlyph glyph = glyphManager.lookupGlyph(font, glyphCode);

                //data.mStyleList.add(new StandardGlyphRender(glyph, effect, stripIndex, offset));
            }

            float totalAdvance = (float) (vector.getGlyphPosition(num).getX() / factor);
            *//*data.mAdvance += totalAdvance;

            if (flag == Font.LAYOUT_RIGHT_TO_LEFT) {
                data.finishStyleRun(-totalAdvance);
                data.mLayoutRight -= totalAdvance;
            } else {
                data.finishStyleRun(0);
            }*//*
        }*/
    }

    /*private void layoutEmoji(TextProcessData data, int codePoint, int start, int flag) {
        float offset;
        if (flag == Font.LAYOUT_RIGHT_TO_LEFT) {
            offset = data.layoutRight;
        } else {
            offset = data.advance;
        }

        data.minimalList.add(new StandardGlyphRender(glyphManager.lookupEmoji(codePoint), TextRenderEffect.NO_EFFECT,
         start, offset));

        offset += 12;

        data.advance += offset;

        if (flag == Font.LAYOUT_RIGHT_TO_LEFT) {
            data.finishFontLayout(-offset);
            data.layoutRight -= offset;
        } else {
            data.finishFontLayout(0);
        }
    }*/

    /**
     * Simple layout for random digits
     *
     * @param data   an object to store the results
     * @param text   the plain text (without formatting codes) to analyze
     * @param start  start index (inclusive) of the text
     * @param limit  end index (exclusive) of the text
     * @param flag   layout direction
     * @param font   the derived font with fontStyle and fontSize
     * @param effect text render effect
     */
    @Deprecated
    private void layoutRandom(TextLayoutProcessor data, char[] text, int start, int limit, int flag, Font font,
                              byte effect) {
        /*final GlyphManagerForge.VanillaGlyph[] digits = glyphManager.lookupDigits(font);
        final float stdAdv = digits[0].getAdvance();

        float offset;
        if (flag == Font.LAYOUT_RIGHT_TO_LEFT) {
            offset = data.mLayoutRight;
        } else {
            offset = data.mAdvance;
        }

        *//* Process code point *//*
        for (int i = start; i < limit; i++) {
            data.mStyleList.add(new RandomGlyphRender(digits, effect, start + i, offset));

            offset += stdAdv;

            char c1 = text[i];
            if (i + 1 < limit && Character.isHighSurrogate(c1)) {
                char c2 = text[i + 1];
                if (Character.isLowSurrogate(c2)) {
                    ++i;
                }
            }
        }

        data.mAdvance += offset;

        if (flag == Font.LAYOUT_RIGHT_TO_LEFT) {
            data.finishStyleRun(-offset);
            data.mLayoutRight -= offset;
        } else {
            data.finishStyleRun(0);
        }*/
    }

    @Deprecated
    private void insertColorState(@Nonnull TextLayoutProcessor data) {
        /* Sometimes naive */
        /*else {
            if (underline) {
                if (strikethrough) {
                    glyphs.forEach(e -> e.effect = TextRenderEffect.UNDERLINE_STRIKETHROUGH);
                } else {
                    glyphs.forEach(e -> e.effect = TextRenderEffect.UNDERLINE);
                }
                data.hasEffect = true;
            } else if (strikethrough) {
                glyphs.forEach(e -> e.effect = TextRenderEffect.STRIKETHROUGH);
                data.hasEffect = true;
            }
        }*/

        /*float start1 = 0;
        float start2 = 0;

        int glyphIndex = 0;
        for (int codeIndex = 1; codeIndex < data.codes.size(); codeIndex++) {
            FormattingStyle code = data.codes.get(codeIndex);

            while (glyphIndex < data.allList.size() - 1 &&
                    (pg = data.allList.get(glyphIndex)).stringIndex < code.stringIndex) {
                //data.advance += pg.getAdvance();
                glyphIndex++;
            }

            if (color != code.getColor()) {
                colors.add(new ColorStateInfo(glyphIndex, color = code.getColor()));

                boolean b = code.isUnderline();

                if (underline) {
                    //effects.add(EffectRenderInfo.underline(start1, data.advance, color));
                }
                if (b) {
                    start1 = data.advance;
                }
                underline = b;

                b = code.isStrikethrough();
                if (strikethrough) {
                    //effects.add(EffectRenderInfo.strikethrough(start2, data.advance, color));
                }
                if (b) {
                    start2 = data.advance;
                }
                strikethrough = b;

            } else {
                boolean b = code.isUnderline();

                if (underline != b) {
                    if (!b) {
                        //effects.add(EffectRenderInfo.underline(start1, data.advance, color));
                    } else {
                        start1 = data.advance;
                    }
                    underline = b;
                }

                b = code.isStrikethrough();
                if (strikethrough != b) {
                    if (!b) {
                        //effects.add(EffectRenderInfo.strikethrough(start2, data.advance, color));
                    } else {
                        start2 = data.advance;
                    }
                    strikethrough = b;
                }
            }
        }

        while (glyphIndex < data.allList.size()) {
            //data.advance += data.list.get(glyphIndex).getAdvance();
            glyphIndex++;
        }*/

        /*if (underline) {
            //effects.add(EffectRenderInfo.underline(start1, data.advance, color));
            data.mergeUnderline(color);
        }
        if (strikethrough) {
            //effects.add(EffectRenderInfo.strikethrough(start2, data.advance, color));
            data.mergeStrikethrough(color);
        }*/
    }

    /**
     * Remove all color codes from the string by shifting data in the text[] array over so it overwrites them. The value
     * of each
     * color code and its position (relative to the new stripped text[]) is also recorded in a separate array. The color
     * codes must
     * be removed for a font's context sensitive glyph substitution to work (like Arabic letter middle form).
     *
     * @param cacheEntry each color change in the string will add a new ColorCode object to this list
     * @param str        the string from which color codes will be stripped
     * @param text       on input it should be an identical copy of str; on output it will be string with all color
     *                   codes removed
     * @return the length of the new stripped string in text[]; actual text.length will not change because the array is
     * not reallocated
     */
    @Deprecated
    private int extractFormattingCodes(Entry cacheEntry, @Nonnull String str, char[] text) {
        List<FormattingCode> codeList = new ArrayList<>();
        int start = 0, shift = 0, next;

        byte fontStyle = FontPaint.NORMAL;
        byte renderStyle = 0;
        byte colorCode = -1;

        /* Search for section mark characters indicating the start of a color code (but only if followed by at least
        one character) */
        while ((next = str.indexOf('\u00A7', start)) != -1 && next + 1 < str.length()) {
            /*
             * Remove the two char color code from text[] by shifting the remaining data in the array over on top of it.
             * The "start" and "next" variables all contain offsets into the original unmodified "str" string. The
             * "shift"
             * variable keeps track of how many characters have been stripped so far, and it's used to compute
             * offsets into
             * the text[] array based on the start/next offsets in the original string.
             */
            System.arraycopy(text, next - shift + 2, text, next - shift, text.length - next - 2);

            /* Decode escape code used in the string and change current font style / color based on it */
            int code = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(str.charAt(next + 1)));
            switch (code) {
                /* Random style */
                case 16:
                    break;

                /* Bold style */
                case 17:
                    fontStyle |= 1;
                    break;

                /* Strikethrough style */
                case 18:
                    renderStyle |= FormattingCode.STRIKETHROUGH;
                    cacheEntry.needExtraRender = true;
                    break;

                /* Underline style */
                case 19:
                    renderStyle |= FormattingCode.UNDERLINE;
                    cacheEntry.needExtraRender = true;
                    break;

                /* Italic style */
                case 20:
                    fontStyle |= 2;
                    break;

                /* Reset style */
                case 21:
                    fontStyle = 0;
                    renderStyle = 0;
                    colorCode = -1; // we need to back default color
                    break;

                /* Otherwise, must be a color code or some other unsupported code */
                default:
                    if (code >= 0) {
                        colorCode = (byte) code;
                        //fontStyle = Font.PLAIN; // This may be a bug in Minecraft's original FontRenderer
                        //renderStyle = 0; // This may be a bug in Minecraft's original FontRenderer
                    }
                    break;
            }

            /* Create a new ColorCode object that tracks the position of the code in the original string */
            FormattingCode formatting = new FormattingCode();
            formatting.stringIndex = next;
            formatting.stripIndex = next - shift;
            formatting.color = Color3i.fromFormattingCode(colorCode);
            formatting.fontStyle = fontStyle;
            formatting.renderEffect = renderStyle;
            codeList.add(formatting);

            /* Resume search for section marks after skipping this one */
            start = next + 2;
            shift += 2;
        }

        /* Convert the accumulated ColorCode list to an array for efficient storage */
        //cacheEntry.codes = new ColorCode[codeList.size()];
        cacheEntry.codes = codeList.toArray(new FormattingCode[0]);

        /* Return the new length of the string after all color codes were removed */
        /* This should be equal to current text char[] length */
        return text.length - shift;
    }

    /**
     * Split a string into contiguous LTR or RTL sections by applying the Unicode Bidirectional Algorithm. Calls
     * layoutString()
     * for each contiguous run to perform further analysis.
     *
     * @param glyphList will hold all new Glyph objects allocated by layoutFont()
     * @param text      the string to lay out
     * @param start     the offset into text at which to start the layout
     * @param limit     the (offset + length) at which to stop performing the layout
     * @return the total advance (horizontal distance) of this string
     */
    @Deprecated
    private float layoutBidiString(List<Glyph> glyphList, char[] text, int start, int limit, FormattingCode[] codes) {
        float advance = 0;

        /* Avoid performing full bidirectional analysis if text has no "strong" right-to-left characters */
        if (Bidi.requiresBidi(text, start, limit)) {
            /* Note that while requiresBidi() uses start/limit the Bidi constructor uses start/length */
            Bidi bidi = new Bidi(text, start, null, 0, limit - start, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);

            /* If text is entirely right-to-left, then insert an EntryText node for the entire string */
            if (bidi.isRightToLeft()) {
                return layoutStyle(glyphList, text, start, limit, 0/*Font.LAYOUT_RIGHT_TO_LEFT*/, advance, codes);
            }

            /* Otherwise text has a mixture of LTR and RLT, and it requires full bidirectional analysis */
            else {
                int runCount = bidi.getRunCount();
                byte[] levels = new byte[runCount];
                Integer[] ranges = new Integer[runCount];

                /* Reorder contiguous runs of text into their display order from left to right */
                for (int index = 0; index < runCount; index++) {
                    levels[index] = (byte) bidi.getRunLevel(index);
                    ranges[index] = index;
                }
                Bidi.reorderVisually(levels, 0, ranges, 0, runCount);

                /*
                 * Every GlyphVector must be created on a contiguous run of left-to-right or right-to-left text. Keep
                 *  track of
                 * the horizontal advance between each run of text, so that the glyphs in each run can be assigned a
                 * position relative
                 * to the start of the entire string and not just relative to that run.
                 */
                for (int visualIndex = 0; visualIndex < runCount; visualIndex++) {
                    int logicalIndex = ranges[visualIndex];

                    /* An odd numbered level indicates right-to-left ordering */
                    int layoutFlag = 0;/*(bidi.getRunLevel(logicalIndex) & 1) == 1 ? Font.LAYOUT_RIGHT_TO_LEFT :
                            Font.LAYOUT_LEFT_TO_RIGHT;*/
                    advance = layoutStyle(glyphList, text, start + bidi.getRunStart(logicalIndex),
                            start + bidi.getRunLimit(logicalIndex),
                            layoutFlag, advance, codes);
                }
            }

            return advance;
        }

        /* If text is entirely left-to-right, then insert an EntryText node for the entire string */
        else {
            return layoutStyle(glyphList, text, start, limit, 0/*Font.LAYOUT_LEFT_TO_RIGHT*/, advance, codes);
        }
    }

    @Deprecated
    private float layoutStyle(List<Glyph> glyphList, char[] text, int start, int limit, int layoutFlags,
                              float advance, FormattingCode[] codes) {
        int currentFontStyle = FontPaint.NORMAL;

        /* Find FormattingCode object with stripIndex <= start; that will have the font style in effect at the
        beginning of this text run */
        //int codeIndex = Arrays.binarySearch(codes, start);

        /*
         * If no exact match is found, Arrays.binarySearch() returns (-(insertion point) - 1) where the insertion
         * point is the index
         * of the first FormattingCode with a stripIndex > start. In that case, colorIndex is adjusted to select the
         * immediately preceding
         * FormattingCode whose stripIndex < start.
         */
        /*if (codeIndex < 0) {
            codeIndex = -codeIndex - 2;
        }*/

        /* Break up the string into segments, where each segment has the same font style in use */
        //while (start < limit) {
        //int next = limit;

        /* In case of multiple consecutive color codes with the same stripIndex, select the last one which will have
        active font style */
            /*while (codeIndex >= 0 && codeIndex < (codes.length - 1) && codes[codeIndex].stripIndex ==
            codes[codeIndex + 1].stripIndex) {
                codeIndex++;
            }*/

        /* If an actual FormattingCode object was found (colorIndex within the array), use its fontStyle for layout
        and render */
            /*if (codeIndex >= 0 && codeIndex < codes.length) {
                currentFontStyle = codes[codeIndex].fontStyle;
            }*/

        /*
         * Search for the next FormattingCode that uses a different fontStyle than the current one. If found, the
         * stripIndex of that
         * new code is the split point where the string must be split into a separately styled segment.
         */
            /*while (++codeIndex < codes.length) {
                if (codes[codeIndex].fontStyle != currentFontStyle) {
                    next = codes[codeIndex].stripIndex;
                    break;
                }
            }*/

        /* Layout the string segment with the style currently selected by the last color code */
        //advance = layoutString(glyphList, text, start, next, layoutFlags, advance, currentFontStyle);
        //start = next;
        //}

        return advance;
    }

    /**
     * Given a string that runs contiguously LTR or RTL, break it up into individual segments based on which fonts can
     * render
     * which characters in the string. Calls layoutFont() for each portion of the string that can be layed out with a
     * single
     * font.
     *
     * @param glyphList   will hold all new Glyph objects allocated by layoutFont()
     * @param text        the string to lay out
     * @param start       the offset into text at which to start the layout
     * @param limit       the (offset + length) at which to stop performing the layout
     * @param layoutFlags either Font.LAYOUT_RIGHT_TO_LEFT or Font.LAYOUT_LEFT_TO_RIGHT
     * @param advance     the horizontal advance (i.e. X position) returned by previous call to layoutString()
     * @param style       combination of PLAIN, BOLD, and ITALIC to select a fonts with some specific style
     * @return the advance (horizontal distance) of this string plus the advance passed in as an argument
     */
    @Deprecated
    private float layoutString(List<Glyph> glyphList, char[] text, int start, int limit, int layoutFlags,
                               float advance, int style) {
        /*
         * Convert all digits in the string to a '0' before layout to ensure that any glyphs replaced on the fly will
         *  all have
         * the same positions. Under Windows, Java's "SansSerif" logical font uses the "Arial" font for digits, in
         * which the "1"
         * digit is slightly narrower than all other digits. Checking the digitGlyphsReady flag prevents a
         * chicken-and-egg
         * problem where the digit glyphs have to be initially cached and the digitGlyphs[] array initialized without
         *  replacing
         * every digit with '0'.
         */
        /*if (digitGlyphsReady) {
            for (int index = start; index < limit; index++) {
                if (text[index] >= '0' && text[index] <= '9') {
                    text[index] = '0';
                }
            }
        }*/

        /* Break the string up into segments, where each segment can be displayed using a single font */
        while (start < limit) {
            //Font font = glyphManager.lookupFont(text, start, limit, style);
            int next = 0;//font.canDisplayUpTo(text, start, limit);

            /* canDisplayUpTo returns -1 if the entire string range is supported by this font */
            //if (next == -1) {
            //    next = limit;
            //}

            /*
             * canDisplayUpTo() returns start if the starting character is not supported at all. In that case, draw
             * just the
             * one unsupported character (which will use the font's "missing glyph code"), then retry the lookup
             * again at the
             * next character after that.
             */
            if (next == start) {
                next++;
            }

            //advance = layoutFont(glyphList, text, start, next, layoutFlags, advance, font);
            start = next;
        }

        return advance;
    }

    /**
     * Allocate new Glyph objects and add them to the glyph list. This sequence of Glyphs represents a portion of the
     * string where all glyphs run contiguously in either LTR or RTL and come from the same physical/logical font.
     *
     * @param glyphList   all newly created Glyph objects are added to this list
     * @param text        the string to layout
     * @param start       the offset into text at which to start the layout
     * @param limit       the (offset + length) at which to stop performing the layout
     * @param layoutFlags either Font.LAYOUT_RIGHT_TO_LEFT or Font.LAYOUT_LEFT_TO_RIGHT
     * @param advance     the horizontal advance (i.e. X position) returned by previous call to layoutString()
     * @param font        the Font used to layout a GlyphVector for the string
     * @return the advance (horizontal distance) of this string plus the advance passed in as an argument
     */
    @Deprecated
    private float layoutFont(List<Glyph> glyphList, char[] text, int start, int limit, int layoutFlags, float advance
            , Font font) {
        /*
         * Ensure that all glyphs used by the string are pre-rendered and cached in the texture. Only safe to do so
         * from the
         * main thread because cacheGlyphs() can crash LWJGL if it makes OpenGL calls from any other thread. In this
         * case,
         * cacheString() will also not insert the entry into the stringCache since it may be incomplete if lookupGlyph()
         * returns null for any glyphs not yet stored in the glyph cache.
         */
        //if (mainThread == Thread.currentThread()) { // already checked
        //glyphManager.cacheGlyphs(font, text, start, limit, layoutFlags);
        //}

        /* Creating a GlyphVector takes care of all language specific OpenType glyph substitutions and positionings */
        GlyphVector vector = null;//glyphManager.layoutGlyphVector(font, text, start, limit, layoutFlags);

        /*
         * Extract all needed information for each glyph from the GlyphVector so it won't be needed for actual
         * rendering.
         * Note that initially, glyph.start holds the character index into the stripped text array. But after the entire
         * string is layed out, this field will be adjusted on every Glyph object to correctly index the original
         * unstripped
         * string.
         */
        Glyph glyph = null;
        int numGlyphs = 1;//vector.getNumGlyphs();
        //for (int index = 0; index < numGlyphs; index++) {
        //Point position = vector.getGlyphPixelBounds(index, null, advance, 0).getLocation();

        /* Compute horizontal advance for the previous glyph based on this glyph's position */
            /*if (glyph != null) {
                glyph.advance = position.x - glyph.x;
            }*/

        /*
         * Allocate a new glyph object and add to the glyphList. The glyph.stringIndex here is really like stripIndex
         *  but
         * it will be corrected later to account for the color codes that have been stripped out.
         */
            /*glyph = new GlyphInfo();
            glyph.stringIndex = start + vector.getGlyphCharIndex(index);
            glyph.texture = glyphManager.lookupGlyph(font, vector.getGlyphCode(index));
            glyph.x = position.x;
            glyph.y = position.y;
            glyphList.add(glyph);*/
        //}

        /* Compute the advance position of the last glyph (or only glyph) since it can't be done by the above loop */
        /*advance += vector.getGlyphPosition(numGlyphs).getX();
        if (glyph != null) {
            glyph.advance = advance - glyph.x;
        }*/

        /* Return the overall horizontal advance in pixels from the start of string */
        return advance;
    }

    /**
     * This entry holds the laid out glyph positions for the cached string along with some relevant metadata.
     */
    @Deprecated
    private static class Entry {

        /**
         * A weak reference back to the Key object in stringCache that maps to this Entry.
         */
        public WeakReference<Key> keyRef; // We do not use this anymore

        /**
         * The total horizontal advance (i.e. width) for this string in pixels.
         */
        public float advance;

        /**
         * Array of fully layed out glyphs for the string. Sorted by logical order of characters (i.e.
         * glyph.stringIndex)
         */
        public Glyph[] glyphs;

        /**
         * Array of color code locations from the original string
         */
        public FormattingCode[] codes;

        /**
         * True if the string uses strikethrough or underlines anywhere and needs an extra pass in renderString()
         */
        public boolean needExtraRender;
    }

    /**
     * Identifies the location and value of a single color code in the original string
     */
    @Deprecated
    private static class FormattingCode implements Comparable<Integer> {

        /**
         * Bit flag used with renderStyle to request the underline style
         */
        public static final byte UNDERLINE = 1;

        /**
         * Bit flag used with renderStyle to request the strikethrough style
         */
        public static final byte STRIKETHROUGH = 2;

        /**
         * The index into the original string (i.e. with color codes) for the location of this color code.
         */
        public int stringIndex;

        /**
         * The index into the stripped string (i.e. with no color codes) of where this color code would have appeared
         */
        public int stripIndex;

        /**
         * Combination of PLAIN, BOLD, and ITALIC specifying font specific styles
         */
        public byte fontStyle;

        /**
         * The numeric color code (i.e. index into the colorCode[] array); -1 to reset default (original parameter)
         * color
         */
        @Nullable
        public Color3i color;

        /**
         * Combination of UNDERLINE and STRIKETHROUGH flags specifying effects performed by renderString()
         */
        public byte renderEffect;

        /**
         * Performs numeric comparison on stripIndex. Allows binary search on ColorCode arrays in layoutStyle.
         *
         * @param i the Integer object being compared
         * @return either -1, 0, or 1 if this < other, this == other, or this > other
         */
        @Override
        public int compareTo(@Nonnull Integer i) {
            return Integer.compare(stringIndex, i);
        }
    }

    @Deprecated
    private static class Glyph implements Comparable<Glyph> {

        /**
         * The index into the original string (i.e. with color codes) for the character that generated this glyph.
         */
        int stringIndex;

        /**
         * Texture ID and position/size of the glyph's pre-rendered image within the cache texture.
         */
        int texture;

        /**
         * Glyph's horizontal position (in pixels) relative to the entire string's baseline
         */
        int x;

        /**
         * Glyph's vertical position (in pixels) relative to the entire string's baseline
         */
        int y;

        /**
         * Glyph's horizontal advance (in pixels) used for strikethrough and underline effects
         */
        float advance;

        /**
         * Allows arrays of Glyph objects to be sorted. Performs numeric comparison on stringIndex.
         *
         * @param o the other Glyph object being compared with this one
         * @return either -1, 0, or 1 if this < other, this == other, or this > other
         */
        @Override
        public int compareTo(Glyph o) {
            return Integer.compare(stringIndex, o.stringIndex);
        }
    }

    /**
     * Wraps a String and acts as the key into stringCache. The hashCode() and equals() methods consider all ASCII
     * digits
     * to be equal when hashing and comparing Key objects together. Therefore, Strings which only differ in their digits
     * will
     * be all hashed together into the same entry. The renderString() method will then substitute the correct digit
     * glyph on
     * the fly. This special digit handling gives a significant speedup on the F3 debug screen.
     */
    @Deprecated
    private static class Key {

        /**
         * A copy of the String which this Key is indexing. A copy is used to avoid creating a strong reference to the
         * original
         * passed into renderString(). When the original String is no longer needed by Minecraft, it will be garbage
         * collected
         * and the WeakHashMaps in StringCache will allow this Key object and its associated Entry object to be garbage
         * collected as well.
         */
        public String str;

        /**
         * Computes a hash code on str in the same manner as the String class, except all ASCII digits hash as '0'
         *
         * @return the augmented hash code on str
         */
        @Override
        public int hashCode() {
            int code = 0, length = str.length();

            /*
             * True if a section mark character was last seen. In this case, if the next character is a digit, it must
             * not be considered equal to any other digit. This forces any string that differs in color codes only to
             * have a separate entry in the StringCache.
             */
            boolean colorCode = false;

            for (int index = 0; index < length; index++) {
                char c = str.charAt(index);
                if (c >= '0' && c <= '9' && !colorCode) {
                    c = '0';
                }
                code = (code * 31) + c;
                colorCode = (c == '\u00A7');
            }

            return code;
        }

        /**
         * Compare str against another object (specifically, the object's string representation as returned by
         * toString).
         * All ASCII digits are considered equal by this method, as long as they are at the same index within the
         * string.
         *
         * @return true if the strings are the identical, or only differ in their ASCII digits
         */
        @Override
        public boolean equals(Object o) {
            /*
             * There seems to be a timing window inside WeakHashMap itself where a null object can be passed to this
             * equals() method. Presumably it happens between computing a hash code for the weakly referenced Key object
             * while it still exists and calling its equals() method after it was garbage collected.
             */
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            /* Calling toString on a String object simply returns itself so no new object allocation is performed */
            String other = o.toString();
            int length = str.length();

            if (length != other.length()) {
                return false;
            }

            /*
             * True if a section mark character was last seen. In this case, if the next character is a digit, it must
             * not be considered equal to any other digit. This forces any string that differs in color codes only to
             * have a separate entry in the StringCache.
             */
            boolean colorCode = false;

            for (int index = 0; index < length; index++) {
                char c1 = str.charAt(index);
                char c2 = other.charAt(index);

                if (c1 != c2 && (c1 < '0' || c1 > '9' || c2 < '0' || c2 > '9' || colorCode)) {
                    return false;
                }
                colorCode = (c1 == '\u00A7');
            }

            return true;
        }

        /**
         * Returns the contained String object within this Key.
         *
         * @return the str object
         */
        @Override
        public String toString() {
            return str;
        }
    }
}
