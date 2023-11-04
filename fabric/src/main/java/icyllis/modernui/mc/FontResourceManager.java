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

package icyllis.modernui.mc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import icyllis.modernui.graphics.font.GlyphManager;
import icyllis.modernui.graphics.text.*;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static icyllis.modernui.ModernUI.LOGGER;

/**
 * Load extra font resources for Modern UI.
 * <p>
 * Subclass by {@link icyllis.modernui.mc.text.TextLayoutEngine}.
 *
 * @see ModernUIClient
 */
public class FontResourceManager implements PreparableReloadListener {

    /**
     * Global instance
     */
    private static volatile FontResourceManager sInstance;

    /**
     * Gui scale = 8.
     * <p>
     * This is because our emoji is 72px. Let emoji be 72px / 8(scale) = 9px,
     * which is close to Minecraft base font size 8px.
     *
     * @see GlyphManager
     */
    public static final int BITMAP_SCALE = 8;

    protected final GlyphManager mGlyphManager;

    protected EmojiFont mEmojiFont;

    /**
     * Shortcodes to Emoji char sequences.
     */
    protected final HashMap<String, String> mEmojiShortcodes = new HashMap<>();

    public FontResourceManager() {
        synchronized (FontResourceManager.class) {
            if (sInstance == null) {
                sInstance = this;
            } else {
                throw new RuntimeException("Multiple instances");
            }
        }
        // init first
        mGlyphManager = GlyphManager.getInstance();
    }

    /**
     * Get the global instance, each call will return the same instance.
     *
     * @return the instance
     */
    public static FontResourceManager getInstance() {
        return sInstance;
    }

    public void reloadAll() {
        mGlyphManager.reload();
        LOGGER.info(GlyphManager.MARKER, "Reloaded glyph manager");
        LayoutCache.clear();
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
        CompletableFuture<LoadResults> preparation;
        {
            final var results = new LoadResults();
            final var loadEmojis = CompletableFuture.runAsync(() ->
                            loadEmojis(resourceManager, results),
                    preparationExecutor);
            final var loadShortcodes = CompletableFuture.runAsync(() ->
                            loadShortcodes(resourceManager, results),
                    preparationExecutor);
            preparation = CompletableFuture.allOf(loadEmojis, loadShortcodes)
                    .thenApply(__ -> results);
        }
        preparationProfiler.endTick();
        return preparation
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(results -> {
                    reloadProfiler.startTick();
                    reloadProfiler.push("reload");
                    applyResources(results);
                    reloadProfiler.pop();
                    reloadProfiler.endTick();
                }, reloadExecutor);
    }

    public static class LoadResults {
        public volatile EmojiFont mEmojiFont;
        public volatile Map<String, String> mEmojiShortcodes;
    }

    // SYNC
    protected void applyResources(@Nonnull LoadResults results) {
        // reload emojis
        mEmojiFont = results.mEmojiFont;
        mEmojiShortcodes.clear();
        mEmojiShortcodes.putAll(results.mEmojiShortcodes);
        // reload the whole engine
        ModernUIClient.getInstance().reloadTypeface();
        reloadAll();
    }

    // SYNC, close native resources
    public void close() {
    }

    // ASYNC
    protected static void loadEmojis(@Nonnull ResourceManager resources,
                                     @Nonnull LoadResults results) {
        final var map = new Object2IntOpenHashMap<CharSequence>();
        final var files = new ArrayList<String>();
        CYCLE:
        for (var image : resources.listResources("emoji",
                res -> res.getPath().endsWith(".png")).keySet()) {
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
                // 1-based as glyph ID, see also GlyphManager.cacheEmoji()
                map.put(sequence, map.size() + 1);
                files.add(fileName);
            }
        } // CYCLE end
        LOGGER.info(GlyphManager.MARKER, "Scanned emoji map size: {}",
                map.size());
        if (!files.isEmpty()) {
            var coverage = new IntOpenHashSet(1478);
            EmojiData._populateEmojiFontCoverage_(coverage);
            results.mEmojiFont = new EmojiFont("Google Noto Color Emoji",
                    coverage,
                    GlyphManager.EMOJI_SIZE,
                    GlyphManager.EMOJI_ASCENT,
                    GlyphManager.EMOJI_SPACING,
                    GlyphManager.EMOJI_BASE,
                    map, files);
        } else {
            LOGGER.info(GlyphManager.MARKER, "No Emoji font was found");
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

    /**
     * @see EmojiDataGen
     */
    // ASYNC
    protected static void loadShortcodes(@Nonnull ResourceManager resources,
                                         @Nonnull LoadResults results) {
        final var map = new HashMap<String, String>();
        try (var reader = resources.openAsReader(ModernUIMod.location("emoji_data.json"))) {
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
            LOGGER.info(GlyphManager.MARKER, "Failed to load emoji data", e);
        }
        LOGGER.info(GlyphManager.MARKER, "Scanned emoji shortcodes: {}",
                map.size());
        results.mEmojiShortcodes = map;
    }

    @Nullable
    public EmojiFont getEmojiFont() {
        return mEmojiFont;
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

    static class EmojiData {
        static void _populateEmojiFontCoverage_(IntSet c) {
            c.add(0x9);
            c.add(0xa);
            c.add(0xd);
            c.add(0x20);
            c.add(0x23);
            c.add(0x2a);
            c.add(0x30);
            c.add(0x31);
            c.add(0x32);
            c.add(0x33);
            c.add(0x34);
            c.add(0x35);
            c.add(0x36);
            c.add(0x37);
            c.add(0x38);
            c.add(0x39);
            c.add(0xa9);
            c.add(0xae);
            c.add(0x200c);
            c.add(0x200d);
            c.add(0x200e);
            c.add(0x200f);
            c.add(0x2028);
            c.add(0x2029);
            c.add(0x202a);
            c.add(0x202b);
            c.add(0x202c);
            c.add(0x202d);
            c.add(0x202e);
            c.add(0x203c);
            c.add(0x2049);
            c.add(0x206a);
            c.add(0x206b);
            c.add(0x206c);
            c.add(0x206d);
            c.add(0x206e);
            c.add(0x206f);
            c.add(0x20e3);
            c.add(0x2122);
            c.add(0x2139);
            c.add(0x2194);
            c.add(0x2195);
            c.add(0x2196);
            c.add(0x2197);
            c.add(0x2198);
            c.add(0x2199);
            c.add(0x21a9);
            c.add(0x21aa);
            c.add(0x231a);
            c.add(0x231b);
            c.add(0x2328);
            c.add(0x23cf);
            c.add(0x23e9);
            c.add(0x23ea);
            c.add(0x23eb);
            c.add(0x23ec);
            c.add(0x23ed);
            c.add(0x23ee);
            c.add(0x23ef);
            c.add(0x23f0);
            c.add(0x23f1);
            c.add(0x23f2);
            c.add(0x23f3);
            c.add(0x23f8);
            c.add(0x23f9);
            c.add(0x23fa);
            c.add(0x24c2);
            c.add(0x25aa);
            c.add(0x25ab);
            c.add(0x25b6);
            c.add(0x25c0);
            c.add(0x25fb);
            c.add(0x25fc);
            c.add(0x25fd);
            c.add(0x25fe);
            c.add(0x2600);
            c.add(0x2601);
            c.add(0x2602);
            c.add(0x2603);
            c.add(0x2604);
            c.add(0x260e);
            c.add(0x2611);
            c.add(0x2614);
            c.add(0x2615);
            c.add(0x2618);
            c.add(0x261d);
            c.add(0x2620);
            c.add(0x2622);
            c.add(0x2623);
            c.add(0x2626);
            c.add(0x262a);
            c.add(0x262e);
            c.add(0x262f);
            c.add(0x2638);
            c.add(0x2639);
            c.add(0x263a);
            c.add(0x2640);
            c.add(0x2642);
            c.add(0x2648);
            c.add(0x2649);
            c.add(0x264a);
            c.add(0x264b);
            c.add(0x264c);
            c.add(0x264d);
            c.add(0x264e);
            c.add(0x264f);
            c.add(0x2650);
            c.add(0x2651);
            c.add(0x2652);
            c.add(0x2653);
            c.add(0x265f);
            c.add(0x2660);
            c.add(0x2663);
            c.add(0x2665);
            c.add(0x2666);
            c.add(0x2668);
            c.add(0x267b);
            c.add(0x267e);
            c.add(0x267f);
            c.add(0x2692);
            c.add(0x2693);
            c.add(0x2694);
            c.add(0x2695);
            c.add(0x2696);
            c.add(0x2697);
            c.add(0x2699);
            c.add(0x269b);
            c.add(0x269c);
            c.add(0x26a0);
            c.add(0x26a1);
            c.add(0x26a7);
            c.add(0x26aa);
            c.add(0x26ab);
            c.add(0x26b0);
            c.add(0x26b1);
            c.add(0x26bd);
            c.add(0x26be);
            c.add(0x26c4);
            c.add(0x26c5);
            c.add(0x26c8);
            c.add(0x26ce);
            c.add(0x26cf);
            c.add(0x26d1);
            c.add(0x26d3);
            c.add(0x26d4);
            c.add(0x26e9);
            c.add(0x26ea);
            c.add(0x26f0);
            c.add(0x26f1);
            c.add(0x26f2);
            c.add(0x26f3);
            c.add(0x26f4);
            c.add(0x26f5);
            c.add(0x26f7);
            c.add(0x26f8);
            c.add(0x26f9);
            c.add(0x26fa);
            c.add(0x26fd);
            c.add(0x2702);
            c.add(0x2705);
            c.add(0x2708);
            c.add(0x2709);
            c.add(0x270a);
            c.add(0x270b);
            c.add(0x270c);
            c.add(0x270d);
            c.add(0x270f);
            c.add(0x2712);
            c.add(0x2714);
            c.add(0x2716);
            c.add(0x271d);
            c.add(0x2721);
            c.add(0x2728);
            c.add(0x2733);
            c.add(0x2734);
            c.add(0x2744);
            c.add(0x2747);
            c.add(0x274c);
            c.add(0x274e);
            c.add(0x2753);
            c.add(0x2754);
            c.add(0x2755);
            c.add(0x2757);
            c.add(0x2763);
            c.add(0x2764);
            c.add(0x2795);
            c.add(0x2796);
            c.add(0x2797);
            c.add(0x27a1);
            c.add(0x27b0);
            c.add(0x27bf);
            c.add(0x2934);
            c.add(0x2935);
            c.add(0x2b05);
            c.add(0x2b06);
            c.add(0x2b07);
            c.add(0x2b1b);
            c.add(0x2b1c);
            c.add(0x2b50);
            c.add(0x2b55);
            c.add(0x3030);
            c.add(0x303d);
            c.add(0x3297);
            c.add(0x3299);
            c.add(0x1f004);
            c.add(0x1f0cf);
            c.add(0x1f170);
            c.add(0x1f171);
            c.add(0x1f17e);
            c.add(0x1f17f);
            c.add(0x1f18e);
            c.add(0x1f191);
            c.add(0x1f192);
            c.add(0x1f193);
            c.add(0x1f194);
            c.add(0x1f195);
            c.add(0x1f196);
            c.add(0x1f197);
            c.add(0x1f198);
            c.add(0x1f199);
            c.add(0x1f19a);
            c.add(0x1f201);
            c.add(0x1f202);
            c.add(0x1f21a);
            c.add(0x1f22f);
            c.add(0x1f232);
            c.add(0x1f233);
            c.add(0x1f234);
            c.add(0x1f235);
            c.add(0x1f236);
            c.add(0x1f237);
            c.add(0x1f238);
            c.add(0x1f239);
            c.add(0x1f23a);
            c.add(0x1f250);
            c.add(0x1f251);
            c.add(0x1f300);
            c.add(0x1f301);
            c.add(0x1f302);
            c.add(0x1f303);
            c.add(0x1f304);
            c.add(0x1f305);
            c.add(0x1f306);
            c.add(0x1f307);
            c.add(0x1f308);
            c.add(0x1f309);
            c.add(0x1f30a);
            c.add(0x1f30b);
            c.add(0x1f30c);
            c.add(0x1f30d);
            c.add(0x1f30e);
            c.add(0x1f30f);
            c.add(0x1f310);
            c.add(0x1f311);
            c.add(0x1f312);
            c.add(0x1f313);
            c.add(0x1f314);
            c.add(0x1f315);
            c.add(0x1f316);
            c.add(0x1f317);
            c.add(0x1f318);
            c.add(0x1f319);
            c.add(0x1f31a);
            c.add(0x1f31b);
            c.add(0x1f31c);
            c.add(0x1f31d);
            c.add(0x1f31e);
            c.add(0x1f31f);
            c.add(0x1f320);
            c.add(0x1f321);
            c.add(0x1f324);
            c.add(0x1f325);
            c.add(0x1f326);
            c.add(0x1f327);
            c.add(0x1f328);
            c.add(0x1f329);
            c.add(0x1f32a);
            c.add(0x1f32b);
            c.add(0x1f32c);
            c.add(0x1f32d);
            c.add(0x1f32e);
            c.add(0x1f32f);
            c.add(0x1f330);
            c.add(0x1f331);
            c.add(0x1f332);
            c.add(0x1f333);
            c.add(0x1f334);
            c.add(0x1f335);
            c.add(0x1f336);
            c.add(0x1f337);
            c.add(0x1f338);
            c.add(0x1f339);
            c.add(0x1f33a);
            c.add(0x1f33b);
            c.add(0x1f33c);
            c.add(0x1f33d);
            c.add(0x1f33e);
            c.add(0x1f33f);
            c.add(0x1f340);
            c.add(0x1f341);
            c.add(0x1f342);
            c.add(0x1f343);
            c.add(0x1f344);
            c.add(0x1f345);
            c.add(0x1f346);
            c.add(0x1f347);
            c.add(0x1f348);
            c.add(0x1f349);
            c.add(0x1f34a);
            c.add(0x1f34b);
            c.add(0x1f34c);
            c.add(0x1f34d);
            c.add(0x1f34e);
            c.add(0x1f34f);
            c.add(0x1f350);
            c.add(0x1f351);
            c.add(0x1f352);
            c.add(0x1f353);
            c.add(0x1f354);
            c.add(0x1f355);
            c.add(0x1f356);
            c.add(0x1f357);
            c.add(0x1f358);
            c.add(0x1f359);
            c.add(0x1f35a);
            c.add(0x1f35b);
            c.add(0x1f35c);
            c.add(0x1f35d);
            c.add(0x1f35e);
            c.add(0x1f35f);
            c.add(0x1f360);
            c.add(0x1f361);
            c.add(0x1f362);
            c.add(0x1f363);
            c.add(0x1f364);
            c.add(0x1f365);
            c.add(0x1f366);
            c.add(0x1f367);
            c.add(0x1f368);
            c.add(0x1f369);
            c.add(0x1f36a);
            c.add(0x1f36b);
            c.add(0x1f36c);
            c.add(0x1f36d);
            c.add(0x1f36e);
            c.add(0x1f36f);
            c.add(0x1f370);
            c.add(0x1f371);
            c.add(0x1f372);
            c.add(0x1f373);
            c.add(0x1f374);
            c.add(0x1f375);
            c.add(0x1f376);
            c.add(0x1f377);
            c.add(0x1f378);
            c.add(0x1f379);
            c.add(0x1f37a);
            c.add(0x1f37b);
            c.add(0x1f37c);
            c.add(0x1f37d);
            c.add(0x1f37e);
            c.add(0x1f37f);
            c.add(0x1f380);
            c.add(0x1f381);
            c.add(0x1f382);
            c.add(0x1f383);
            c.add(0x1f384);
            c.add(0x1f385);
            c.add(0x1f386);
            c.add(0x1f387);
            c.add(0x1f388);
            c.add(0x1f389);
            c.add(0x1f38a);
            c.add(0x1f38b);
            c.add(0x1f38c);
            c.add(0x1f38d);
            c.add(0x1f38e);
            c.add(0x1f38f);
            c.add(0x1f390);
            c.add(0x1f391);
            c.add(0x1f392);
            c.add(0x1f393);
            c.add(0x1f396);
            c.add(0x1f397);
            c.add(0x1f399);
            c.add(0x1f39a);
            c.add(0x1f39b);
            c.add(0x1f39e);
            c.add(0x1f39f);
            c.add(0x1f3a0);
            c.add(0x1f3a1);
            c.add(0x1f3a2);
            c.add(0x1f3a3);
            c.add(0x1f3a4);
            c.add(0x1f3a5);
            c.add(0x1f3a6);
            c.add(0x1f3a7);
            c.add(0x1f3a8);
            c.add(0x1f3a9);
            c.add(0x1f3aa);
            c.add(0x1f3ab);
            c.add(0x1f3ac);
            c.add(0x1f3ad);
            c.add(0x1f3ae);
            c.add(0x1f3af);
            c.add(0x1f3b0);
            c.add(0x1f3b1);
            c.add(0x1f3b2);
            c.add(0x1f3b3);
            c.add(0x1f3b4);
            c.add(0x1f3b5);
            c.add(0x1f3b6);
            c.add(0x1f3b7);
            c.add(0x1f3b8);
            c.add(0x1f3b9);
            c.add(0x1f3ba);
            c.add(0x1f3bb);
            c.add(0x1f3bc);
            c.add(0x1f3bd);
            c.add(0x1f3be);
            c.add(0x1f3bf);
            c.add(0x1f3c0);
            c.add(0x1f3c1);
            c.add(0x1f3c2);
            c.add(0x1f3c3);
            c.add(0x1f3c4);
            c.add(0x1f3c5);
            c.add(0x1f3c6);
            c.add(0x1f3c7);
            c.add(0x1f3c8);
            c.add(0x1f3c9);
            c.add(0x1f3ca);
            c.add(0x1f3cb);
            c.add(0x1f3cc);
            c.add(0x1f3cd);
            c.add(0x1f3ce);
            c.add(0x1f3cf);
            c.add(0x1f3d0);
            c.add(0x1f3d1);
            c.add(0x1f3d2);
            c.add(0x1f3d3);
            c.add(0x1f3d4);
            c.add(0x1f3d5);
            c.add(0x1f3d6);
            c.add(0x1f3d7);
            c.add(0x1f3d8);
            c.add(0x1f3d9);
            c.add(0x1f3da);
            c.add(0x1f3db);
            c.add(0x1f3dc);
            c.add(0x1f3dd);
            c.add(0x1f3de);
            c.add(0x1f3df);
            c.add(0x1f3e0);
            c.add(0x1f3e1);
            c.add(0x1f3e2);
            c.add(0x1f3e3);
            c.add(0x1f3e4);
            c.add(0x1f3e5);
            c.add(0x1f3e6);
            c.add(0x1f3e7);
            c.add(0x1f3e8);
            c.add(0x1f3e9);
            c.add(0x1f3ea);
            c.add(0x1f3eb);
            c.add(0x1f3ec);
            c.add(0x1f3ed);
            c.add(0x1f3ee);
            c.add(0x1f3ef);
            c.add(0x1f3f0);
            c.add(0x1f3f3);
            c.add(0x1f3f4);
            c.add(0x1f3f5);
            c.add(0x1f3f7);
            c.add(0x1f3f8);
            c.add(0x1f3f9);
            c.add(0x1f3fa);
            c.add(0x1f3fb);
            c.add(0x1f3fc);
            c.add(0x1f3fd);
            c.add(0x1f3fe);
            c.add(0x1f3ff);
            c.add(0x1f400);
            c.add(0x1f401);
            c.add(0x1f402);
            c.add(0x1f403);
            c.add(0x1f404);
            c.add(0x1f405);
            c.add(0x1f406);
            c.add(0x1f407);
            c.add(0x1f408);
            c.add(0x1f409);
            c.add(0x1f40a);
            c.add(0x1f40b);
            c.add(0x1f40c);
            c.add(0x1f40d);
            c.add(0x1f40e);
            c.add(0x1f40f);
            c.add(0x1f410);
            c.add(0x1f411);
            c.add(0x1f412);
            c.add(0x1f413);
            c.add(0x1f414);
            c.add(0x1f415);
            c.add(0x1f416);
            c.add(0x1f417);
            c.add(0x1f418);
            c.add(0x1f419);
            c.add(0x1f41a);
            c.add(0x1f41b);
            c.add(0x1f41c);
            c.add(0x1f41d);
            c.add(0x1f41e);
            c.add(0x1f41f);
            c.add(0x1f420);
            c.add(0x1f421);
            c.add(0x1f422);
            c.add(0x1f423);
            c.add(0x1f424);
            c.add(0x1f425);
            c.add(0x1f426);
            c.add(0x1f427);
            c.add(0x1f428);
            c.add(0x1f429);
            c.add(0x1f42a);
            c.add(0x1f42b);
            c.add(0x1f42c);
            c.add(0x1f42d);
            c.add(0x1f42e);
            c.add(0x1f42f);
            c.add(0x1f430);
            c.add(0x1f431);
            c.add(0x1f432);
            c.add(0x1f433);
            c.add(0x1f434);
            c.add(0x1f435);
            c.add(0x1f436);
            c.add(0x1f437);
            c.add(0x1f438);
            c.add(0x1f439);
            c.add(0x1f43a);
            c.add(0x1f43b);
            c.add(0x1f43c);
            c.add(0x1f43d);
            c.add(0x1f43e);
            c.add(0x1f43f);
            c.add(0x1f440);
            c.add(0x1f441);
            c.add(0x1f442);
            c.add(0x1f443);
            c.add(0x1f444);
            c.add(0x1f445);
            c.add(0x1f446);
            c.add(0x1f447);
            c.add(0x1f448);
            c.add(0x1f449);
            c.add(0x1f44a);
            c.add(0x1f44b);
            c.add(0x1f44c);
            c.add(0x1f44d);
            c.add(0x1f44e);
            c.add(0x1f44f);
            c.add(0x1f450);
            c.add(0x1f451);
            c.add(0x1f452);
            c.add(0x1f453);
            c.add(0x1f454);
            c.add(0x1f455);
            c.add(0x1f456);
            c.add(0x1f457);
            c.add(0x1f458);
            c.add(0x1f459);
            c.add(0x1f45a);
            c.add(0x1f45b);
            c.add(0x1f45c);
            c.add(0x1f45d);
            c.add(0x1f45e);
            c.add(0x1f45f);
            c.add(0x1f460);
            c.add(0x1f461);
            c.add(0x1f462);
            c.add(0x1f463);
            c.add(0x1f464);
            c.add(0x1f465);
            c.add(0x1f466);
            c.add(0x1f467);
            c.add(0x1f468);
            c.add(0x1f469);
            c.add(0x1f46a);
            c.add(0x1f46b);
            c.add(0x1f46c);
            c.add(0x1f46d);
            c.add(0x1f46e);
            c.add(0x1f46f);
            c.add(0x1f470);
            c.add(0x1f471);
            c.add(0x1f472);
            c.add(0x1f473);
            c.add(0x1f474);
            c.add(0x1f475);
            c.add(0x1f476);
            c.add(0x1f477);
            c.add(0x1f478);
            c.add(0x1f479);
            c.add(0x1f47a);
            c.add(0x1f47b);
            c.add(0x1f47c);
            c.add(0x1f47d);
            c.add(0x1f47e);
            c.add(0x1f47f);
            c.add(0x1f480);
            c.add(0x1f481);
            c.add(0x1f482);
            c.add(0x1f483);
            c.add(0x1f484);
            c.add(0x1f485);
            c.add(0x1f486);
            c.add(0x1f487);
            c.add(0x1f488);
            c.add(0x1f489);
            c.add(0x1f48a);
            c.add(0x1f48b);
            c.add(0x1f48c);
            c.add(0x1f48d);
            c.add(0x1f48e);
            c.add(0x1f48f);
            c.add(0x1f490);
            c.add(0x1f491);
            c.add(0x1f492);
            c.add(0x1f493);
            c.add(0x1f494);
            c.add(0x1f495);
            c.add(0x1f496);
            c.add(0x1f497);
            c.add(0x1f498);
            c.add(0x1f499);
            c.add(0x1f49a);
            c.add(0x1f49b);
            c.add(0x1f49c);
            c.add(0x1f49d);
            c.add(0x1f49e);
            c.add(0x1f49f);
            c.add(0x1f4a0);
            c.add(0x1f4a1);
            c.add(0x1f4a2);
            c.add(0x1f4a3);
            c.add(0x1f4a4);
            c.add(0x1f4a5);
            c.add(0x1f4a6);
            c.add(0x1f4a7);
            c.add(0x1f4a8);
            c.add(0x1f4a9);
            c.add(0x1f4aa);
            c.add(0x1f4ab);
            c.add(0x1f4ac);
            c.add(0x1f4ad);
            c.add(0x1f4ae);
            c.add(0x1f4af);
            c.add(0x1f4b0);
            c.add(0x1f4b1);
            c.add(0x1f4b2);
            c.add(0x1f4b3);
            c.add(0x1f4b4);
            c.add(0x1f4b5);
            c.add(0x1f4b6);
            c.add(0x1f4b7);
            c.add(0x1f4b8);
            c.add(0x1f4b9);
            c.add(0x1f4ba);
            c.add(0x1f4bb);
            c.add(0x1f4bc);
            c.add(0x1f4bd);
            c.add(0x1f4be);
            c.add(0x1f4bf);
            c.add(0x1f4c0);
            c.add(0x1f4c1);
            c.add(0x1f4c2);
            c.add(0x1f4c3);
            c.add(0x1f4c4);
            c.add(0x1f4c5);
            c.add(0x1f4c6);
            c.add(0x1f4c7);
            c.add(0x1f4c8);
            c.add(0x1f4c9);
            c.add(0x1f4ca);
            c.add(0x1f4cb);
            c.add(0x1f4cc);
            c.add(0x1f4cd);
            c.add(0x1f4ce);
            c.add(0x1f4cf);
            c.add(0x1f4d0);
            c.add(0x1f4d1);
            c.add(0x1f4d2);
            c.add(0x1f4d3);
            c.add(0x1f4d4);
            c.add(0x1f4d5);
            c.add(0x1f4d6);
            c.add(0x1f4d7);
            c.add(0x1f4d8);
            c.add(0x1f4d9);
            c.add(0x1f4da);
            c.add(0x1f4db);
            c.add(0x1f4dc);
            c.add(0x1f4dd);
            c.add(0x1f4de);
            c.add(0x1f4df);
            c.add(0x1f4e0);
            c.add(0x1f4e1);
            c.add(0x1f4e2);
            c.add(0x1f4e3);
            c.add(0x1f4e4);
            c.add(0x1f4e5);
            c.add(0x1f4e6);
            c.add(0x1f4e7);
            c.add(0x1f4e8);
            c.add(0x1f4e9);
            c.add(0x1f4ea);
            c.add(0x1f4eb);
            c.add(0x1f4ec);
            c.add(0x1f4ed);
            c.add(0x1f4ee);
            c.add(0x1f4ef);
            c.add(0x1f4f0);
            c.add(0x1f4f1);
            c.add(0x1f4f2);
            c.add(0x1f4f3);
            c.add(0x1f4f4);
            c.add(0x1f4f5);
            c.add(0x1f4f6);
            c.add(0x1f4f7);
            c.add(0x1f4f8);
            c.add(0x1f4f9);
            c.add(0x1f4fa);
            c.add(0x1f4fb);
            c.add(0x1f4fc);
            c.add(0x1f4fd);
            c.add(0x1f4ff);
            c.add(0x1f500);
            c.add(0x1f501);
            c.add(0x1f502);
            c.add(0x1f503);
            c.add(0x1f504);
            c.add(0x1f505);
            c.add(0x1f506);
            c.add(0x1f507);
            c.add(0x1f508);
            c.add(0x1f509);
            c.add(0x1f50a);
            c.add(0x1f50b);
            c.add(0x1f50c);
            c.add(0x1f50d);
            c.add(0x1f50e);
            c.add(0x1f50f);
            c.add(0x1f510);
            c.add(0x1f511);
            c.add(0x1f512);
            c.add(0x1f513);
            c.add(0x1f514);
            c.add(0x1f515);
            c.add(0x1f516);
            c.add(0x1f517);
            c.add(0x1f518);
            c.add(0x1f519);
            c.add(0x1f51a);
            c.add(0x1f51b);
            c.add(0x1f51c);
            c.add(0x1f51d);
            c.add(0x1f51e);
            c.add(0x1f51f);
            c.add(0x1f520);
            c.add(0x1f521);
            c.add(0x1f522);
            c.add(0x1f523);
            c.add(0x1f524);
            c.add(0x1f525);
            c.add(0x1f526);
            c.add(0x1f527);
            c.add(0x1f528);
            c.add(0x1f529);
            c.add(0x1f52a);
            c.add(0x1f52b);
            c.add(0x1f52c);
            c.add(0x1f52d);
            c.add(0x1f52e);
            c.add(0x1f52f);
            c.add(0x1f530);
            c.add(0x1f531);
            c.add(0x1f532);
            c.add(0x1f533);
            c.add(0x1f534);
            c.add(0x1f535);
            c.add(0x1f536);
            c.add(0x1f537);
            c.add(0x1f538);
            c.add(0x1f539);
            c.add(0x1f53a);
            c.add(0x1f53b);
            c.add(0x1f53c);
            c.add(0x1f53d);
            c.add(0x1f549);
            c.add(0x1f54a);
            c.add(0x1f54b);
            c.add(0x1f54c);
            c.add(0x1f54d);
            c.add(0x1f54e);
            c.add(0x1f550);
            c.add(0x1f551);
            c.add(0x1f552);
            c.add(0x1f553);
            c.add(0x1f554);
            c.add(0x1f555);
            c.add(0x1f556);
            c.add(0x1f557);
            c.add(0x1f558);
            c.add(0x1f559);
            c.add(0x1f55a);
            c.add(0x1f55b);
            c.add(0x1f55c);
            c.add(0x1f55d);
            c.add(0x1f55e);
            c.add(0x1f55f);
            c.add(0x1f560);
            c.add(0x1f561);
            c.add(0x1f562);
            c.add(0x1f563);
            c.add(0x1f564);
            c.add(0x1f565);
            c.add(0x1f566);
            c.add(0x1f567);
            c.add(0x1f56f);
            c.add(0x1f570);
            c.add(0x1f573);
            c.add(0x1f574);
            c.add(0x1f575);
            c.add(0x1f576);
            c.add(0x1f577);
            c.add(0x1f578);
            c.add(0x1f579);
            c.add(0x1f57a);
            c.add(0x1f587);
            c.add(0x1f58a);
            c.add(0x1f58b);
            c.add(0x1f58c);
            c.add(0x1f58d);
            c.add(0x1f590);
            c.add(0x1f595);
            c.add(0x1f596);
            c.add(0x1f5a4);
            c.add(0x1f5a5);
            c.add(0x1f5a8);
            c.add(0x1f5b1);
            c.add(0x1f5b2);
            c.add(0x1f5bc);
            c.add(0x1f5c2);
            c.add(0x1f5c3);
            c.add(0x1f5c4);
            c.add(0x1f5d1);
            c.add(0x1f5d2);
            c.add(0x1f5d3);
            c.add(0x1f5dc);
            c.add(0x1f5dd);
            c.add(0x1f5de);
            c.add(0x1f5e1);
            c.add(0x1f5e3);
            c.add(0x1f5e8);
            c.add(0x1f5ef);
            c.add(0x1f5f3);
            c.add(0x1f5fa);
            c.add(0x1f5fb);
            c.add(0x1f5fc);
            c.add(0x1f5fd);
            c.add(0x1f5fe);
            c.add(0x1f5ff);
            c.add(0x1f600);
            c.add(0x1f601);
            c.add(0x1f602);
            c.add(0x1f603);
            c.add(0x1f604);
            c.add(0x1f605);
            c.add(0x1f606);
            c.add(0x1f607);
            c.add(0x1f608);
            c.add(0x1f609);
            c.add(0x1f60a);
            c.add(0x1f60b);
            c.add(0x1f60c);
            c.add(0x1f60d);
            c.add(0x1f60e);
            c.add(0x1f60f);
            c.add(0x1f610);
            c.add(0x1f611);
            c.add(0x1f612);
            c.add(0x1f613);
            c.add(0x1f614);
            c.add(0x1f615);
            c.add(0x1f616);
            c.add(0x1f617);
            c.add(0x1f618);
            c.add(0x1f619);
            c.add(0x1f61a);
            c.add(0x1f61b);
            c.add(0x1f61c);
            c.add(0x1f61d);
            c.add(0x1f61e);
            c.add(0x1f61f);
            c.add(0x1f620);
            c.add(0x1f621);
            c.add(0x1f622);
            c.add(0x1f623);
            c.add(0x1f624);
            c.add(0x1f625);
            c.add(0x1f626);
            c.add(0x1f627);
            c.add(0x1f628);
            c.add(0x1f629);
            c.add(0x1f62a);
            c.add(0x1f62b);
            c.add(0x1f62c);
            c.add(0x1f62d);
            c.add(0x1f62e);
            c.add(0x1f62f);
            c.add(0x1f630);
            c.add(0x1f631);
            c.add(0x1f632);
            c.add(0x1f633);
            c.add(0x1f634);
            c.add(0x1f635);
            c.add(0x1f636);
            c.add(0x1f637);
            c.add(0x1f638);
            c.add(0x1f639);
            c.add(0x1f63a);
            c.add(0x1f63b);
            c.add(0x1f63c);
            c.add(0x1f63d);
            c.add(0x1f63e);
            c.add(0x1f63f);
            c.add(0x1f640);
            c.add(0x1f641);
            c.add(0x1f642);
            c.add(0x1f643);
            c.add(0x1f644);
            c.add(0x1f645);
            c.add(0x1f646);
            c.add(0x1f647);
            c.add(0x1f648);
            c.add(0x1f649);
            c.add(0x1f64a);
            c.add(0x1f64b);
            c.add(0x1f64c);
            c.add(0x1f64d);
            c.add(0x1f64e);
            c.add(0x1f64f);
            c.add(0x1f680);
            c.add(0x1f681);
            c.add(0x1f682);
            c.add(0x1f683);
            c.add(0x1f684);
            c.add(0x1f685);
            c.add(0x1f686);
            c.add(0x1f687);
            c.add(0x1f688);
            c.add(0x1f689);
            c.add(0x1f68a);
            c.add(0x1f68b);
            c.add(0x1f68c);
            c.add(0x1f68d);
            c.add(0x1f68e);
            c.add(0x1f68f);
            c.add(0x1f690);
            c.add(0x1f691);
            c.add(0x1f692);
            c.add(0x1f693);
            c.add(0x1f694);
            c.add(0x1f695);
            c.add(0x1f696);
            c.add(0x1f697);
            c.add(0x1f698);
            c.add(0x1f699);
            c.add(0x1f69a);
            c.add(0x1f69b);
            c.add(0x1f69c);
            c.add(0x1f69d);
            c.add(0x1f69e);
            c.add(0x1f69f);
            c.add(0x1f6a0);
            c.add(0x1f6a1);
            c.add(0x1f6a2);
            c.add(0x1f6a3);
            c.add(0x1f6a4);
            c.add(0x1f6a5);
            c.add(0x1f6a6);
            c.add(0x1f6a7);
            c.add(0x1f6a8);
            c.add(0x1f6a9);
            c.add(0x1f6aa);
            c.add(0x1f6ab);
            c.add(0x1f6ac);
            c.add(0x1f6ad);
            c.add(0x1f6ae);
            c.add(0x1f6af);
            c.add(0x1f6b0);
            c.add(0x1f6b1);
            c.add(0x1f6b2);
            c.add(0x1f6b3);
            c.add(0x1f6b4);
            c.add(0x1f6b5);
            c.add(0x1f6b6);
            c.add(0x1f6b7);
            c.add(0x1f6b8);
            c.add(0x1f6b9);
            c.add(0x1f6ba);
            c.add(0x1f6bb);
            c.add(0x1f6bc);
            c.add(0x1f6bd);
            c.add(0x1f6be);
            c.add(0x1f6bf);
            c.add(0x1f6c0);
            c.add(0x1f6c1);
            c.add(0x1f6c2);
            c.add(0x1f6c3);
            c.add(0x1f6c4);
            c.add(0x1f6c5);
            c.add(0x1f6cb);
            c.add(0x1f6cc);
            c.add(0x1f6cd);
            c.add(0x1f6ce);
            c.add(0x1f6cf);
            c.add(0x1f6d0);
            c.add(0x1f6d1);
            c.add(0x1f6d2);
            c.add(0x1f6d5);
            c.add(0x1f6d6);
            c.add(0x1f6d7);
            c.add(0x1f6dc);
            c.add(0x1f6dd);
            c.add(0x1f6de);
            c.add(0x1f6df);
            c.add(0x1f6e0);
            c.add(0x1f6e1);
            c.add(0x1f6e2);
            c.add(0x1f6e3);
            c.add(0x1f6e4);
            c.add(0x1f6e5);
            c.add(0x1f6e9);
            c.add(0x1f6eb);
            c.add(0x1f6ec);
            c.add(0x1f6f0);
            c.add(0x1f6f3);
            c.add(0x1f6f4);
            c.add(0x1f6f5);
            c.add(0x1f6f6);
            c.add(0x1f6f7);
            c.add(0x1f6f8);
            c.add(0x1f6f9);
            c.add(0x1f6fa);
            c.add(0x1f6fb);
            c.add(0x1f6fc);
            c.add(0x1f7e0);
            c.add(0x1f7e1);
            c.add(0x1f7e2);
            c.add(0x1f7e3);
            c.add(0x1f7e4);
            c.add(0x1f7e5);
            c.add(0x1f7e6);
            c.add(0x1f7e7);
            c.add(0x1f7e8);
            c.add(0x1f7e9);
            c.add(0x1f7ea);
            c.add(0x1f7eb);
            c.add(0x1f7f0);
            c.add(0x1f90c);
            c.add(0x1f90d);
            c.add(0x1f90e);
            c.add(0x1f90f);
            c.add(0x1f910);
            c.add(0x1f911);
            c.add(0x1f912);
            c.add(0x1f913);
            c.add(0x1f914);
            c.add(0x1f915);
            c.add(0x1f916);
            c.add(0x1f917);
            c.add(0x1f918);
            c.add(0x1f919);
            c.add(0x1f91a);
            c.add(0x1f91b);
            c.add(0x1f91c);
            c.add(0x1f91d);
            c.add(0x1f91e);
            c.add(0x1f91f);
            c.add(0x1f920);
            c.add(0x1f921);
            c.add(0x1f922);
            c.add(0x1f923);
            c.add(0x1f924);
            c.add(0x1f925);
            c.add(0x1f926);
            c.add(0x1f927);
            c.add(0x1f928);
            c.add(0x1f929);
            c.add(0x1f92a);
            c.add(0x1f92b);
            c.add(0x1f92c);
            c.add(0x1f92d);
            c.add(0x1f92e);
            c.add(0x1f92f);
            c.add(0x1f930);
            c.add(0x1f931);
            c.add(0x1f932);
            c.add(0x1f933);
            c.add(0x1f934);
            c.add(0x1f935);
            c.add(0x1f936);
            c.add(0x1f937);
            c.add(0x1f938);
            c.add(0x1f939);
            c.add(0x1f93a);
            c.add(0x1f93c);
            c.add(0x1f93d);
            c.add(0x1f93e);
            c.add(0x1f93f);
            c.add(0x1f940);
            c.add(0x1f941);
            c.add(0x1f942);
            c.add(0x1f943);
            c.add(0x1f944);
            c.add(0x1f945);
            c.add(0x1f947);
            c.add(0x1f948);
            c.add(0x1f949);
            c.add(0x1f94a);
            c.add(0x1f94b);
            c.add(0x1f94c);
            c.add(0x1f94d);
            c.add(0x1f94e);
            c.add(0x1f94f);
            c.add(0x1f950);
            c.add(0x1f951);
            c.add(0x1f952);
            c.add(0x1f953);
            c.add(0x1f954);
            c.add(0x1f955);
            c.add(0x1f956);
            c.add(0x1f957);
            c.add(0x1f958);
            c.add(0x1f959);
            c.add(0x1f95a);
            c.add(0x1f95b);
            c.add(0x1f95c);
            c.add(0x1f95d);
            c.add(0x1f95e);
            c.add(0x1f95f);
            c.add(0x1f960);
            c.add(0x1f961);
            c.add(0x1f962);
            c.add(0x1f963);
            c.add(0x1f964);
            c.add(0x1f965);
            c.add(0x1f966);
            c.add(0x1f967);
            c.add(0x1f968);
            c.add(0x1f969);
            c.add(0x1f96a);
            c.add(0x1f96b);
            c.add(0x1f96c);
            c.add(0x1f96d);
            c.add(0x1f96e);
            c.add(0x1f96f);
            c.add(0x1f970);
            c.add(0x1f971);
            c.add(0x1f972);
            c.add(0x1f973);
            c.add(0x1f974);
            c.add(0x1f975);
            c.add(0x1f976);
            c.add(0x1f977);
            c.add(0x1f978);
            c.add(0x1f979);
            c.add(0x1f97a);
            c.add(0x1f97b);
            c.add(0x1f97c);
            c.add(0x1f97d);
            c.add(0x1f97e);
            c.add(0x1f97f);
            c.add(0x1f980);
            c.add(0x1f981);
            c.add(0x1f982);
            c.add(0x1f983);
            c.add(0x1f984);
            c.add(0x1f985);
            c.add(0x1f986);
            c.add(0x1f987);
            c.add(0x1f988);
            c.add(0x1f989);
            c.add(0x1f98a);
            c.add(0x1f98b);
            c.add(0x1f98c);
            c.add(0x1f98d);
            c.add(0x1f98e);
            c.add(0x1f98f);
            c.add(0x1f990);
            c.add(0x1f991);
            c.add(0x1f992);
            c.add(0x1f993);
            c.add(0x1f994);
            c.add(0x1f995);
            c.add(0x1f996);
            c.add(0x1f997);
            c.add(0x1f998);
            c.add(0x1f999);
            c.add(0x1f99a);
            c.add(0x1f99b);
            c.add(0x1f99c);
            c.add(0x1f99d);
            c.add(0x1f99e);
            c.add(0x1f99f);
            c.add(0x1f9a0);
            c.add(0x1f9a1);
            c.add(0x1f9a2);
            c.add(0x1f9a3);
            c.add(0x1f9a4);
            c.add(0x1f9a5);
            c.add(0x1f9a6);
            c.add(0x1f9a7);
            c.add(0x1f9a8);
            c.add(0x1f9a9);
            c.add(0x1f9aa);
            c.add(0x1f9ab);
            c.add(0x1f9ac);
            c.add(0x1f9ad);
            c.add(0x1f9ae);
            c.add(0x1f9af);
            c.add(0x1f9b0);
            c.add(0x1f9b1);
            c.add(0x1f9b2);
            c.add(0x1f9b3);
            c.add(0x1f9b4);
            c.add(0x1f9b5);
            c.add(0x1f9b6);
            c.add(0x1f9b7);
            c.add(0x1f9b8);
            c.add(0x1f9b9);
            c.add(0x1f9ba);
            c.add(0x1f9bb);
            c.add(0x1f9bc);
            c.add(0x1f9bd);
            c.add(0x1f9be);
            c.add(0x1f9bf);
            c.add(0x1f9c0);
            c.add(0x1f9c1);
            c.add(0x1f9c2);
            c.add(0x1f9c3);
            c.add(0x1f9c4);
            c.add(0x1f9c5);
            c.add(0x1f9c6);
            c.add(0x1f9c7);
            c.add(0x1f9c8);
            c.add(0x1f9c9);
            c.add(0x1f9ca);
            c.add(0x1f9cb);
            c.add(0x1f9cc);
            c.add(0x1f9cd);
            c.add(0x1f9ce);
            c.add(0x1f9cf);
            c.add(0x1f9d0);
            c.add(0x1f9d1);
            c.add(0x1f9d2);
            c.add(0x1f9d3);
            c.add(0x1f9d4);
            c.add(0x1f9d5);
            c.add(0x1f9d6);
            c.add(0x1f9d7);
            c.add(0x1f9d8);
            c.add(0x1f9d9);
            c.add(0x1f9da);
            c.add(0x1f9db);
            c.add(0x1f9dc);
            c.add(0x1f9dd);
            c.add(0x1f9de);
            c.add(0x1f9df);
            c.add(0x1f9e0);
            c.add(0x1f9e1);
            c.add(0x1f9e2);
            c.add(0x1f9e3);
            c.add(0x1f9e4);
            c.add(0x1f9e5);
            c.add(0x1f9e6);
            c.add(0x1f9e7);
            c.add(0x1f9e8);
            c.add(0x1f9e9);
            c.add(0x1f9ea);
            c.add(0x1f9eb);
            c.add(0x1f9ec);
            c.add(0x1f9ed);
            c.add(0x1f9ee);
            c.add(0x1f9ef);
            c.add(0x1f9f0);
            c.add(0x1f9f1);
            c.add(0x1f9f2);
            c.add(0x1f9f3);
            c.add(0x1f9f4);
            c.add(0x1f9f5);
            c.add(0x1f9f6);
            c.add(0x1f9f7);
            c.add(0x1f9f8);
            c.add(0x1f9f9);
            c.add(0x1f9fa);
            c.add(0x1f9fb);
            c.add(0x1f9fc);
            c.add(0x1f9fd);
            c.add(0x1f9fe);
            c.add(0x1f9ff);
            c.add(0x1fa70);
            c.add(0x1fa71);
            c.add(0x1fa72);
            c.add(0x1fa73);
            c.add(0x1fa74);
            c.add(0x1fa75);
            c.add(0x1fa76);
            c.add(0x1fa77);
            c.add(0x1fa78);
            c.add(0x1fa79);
            c.add(0x1fa7a);
            c.add(0x1fa7b);
            c.add(0x1fa7c);
            c.add(0x1fa80);
            c.add(0x1fa81);
            c.add(0x1fa82);
            c.add(0x1fa83);
            c.add(0x1fa84);
            c.add(0x1fa85);
            c.add(0x1fa86);
            c.add(0x1fa87);
            c.add(0x1fa88);
            c.add(0x1fa90);
            c.add(0x1fa91);
            c.add(0x1fa92);
            c.add(0x1fa93);
            c.add(0x1fa94);
            c.add(0x1fa95);
            c.add(0x1fa96);
            c.add(0x1fa97);
            c.add(0x1fa98);
            c.add(0x1fa99);
            c.add(0x1fa9a);
            c.add(0x1fa9b);
            c.add(0x1fa9c);
            c.add(0x1fa9d);
            c.add(0x1fa9e);
            c.add(0x1fa9f);
            c.add(0x1faa0);
            c.add(0x1faa1);
            c.add(0x1faa2);
            c.add(0x1faa3);
            c.add(0x1faa4);
            c.add(0x1faa5);
            c.add(0x1faa6);
            c.add(0x1faa7);
            c.add(0x1faa8);
            c.add(0x1faa9);
            c.add(0x1faaa);
            c.add(0x1faab);
            c.add(0x1faac);
            c.add(0x1faad);
            c.add(0x1faae);
            c.add(0x1faaf);
            c.add(0x1fab0);
            c.add(0x1fab1);
            c.add(0x1fab2);
            c.add(0x1fab3);
            c.add(0x1fab4);
            c.add(0x1fab5);
            c.add(0x1fab6);
            c.add(0x1fab7);
            c.add(0x1fab8);
            c.add(0x1fab9);
            c.add(0x1faba);
            c.add(0x1fabb);
            c.add(0x1fabc);
            c.add(0x1fabd);
            c.add(0x1fabf);
            c.add(0x1fac0);
            c.add(0x1fac1);
            c.add(0x1fac2);
            c.add(0x1fac3);
            c.add(0x1fac4);
            c.add(0x1fac5);
            c.add(0x1face);
            c.add(0x1facf);
            c.add(0x1fad0);
            c.add(0x1fad1);
            c.add(0x1fad2);
            c.add(0x1fad3);
            c.add(0x1fad4);
            c.add(0x1fad5);
            c.add(0x1fad6);
            c.add(0x1fad7);
            c.add(0x1fad8);
            c.add(0x1fad9);
            c.add(0x1fada);
            c.add(0x1fadb);
            c.add(0x1fae0);
            c.add(0x1fae1);
            c.add(0x1fae2);
            c.add(0x1fae3);
            c.add(0x1fae4);
            c.add(0x1fae5);
            c.add(0x1fae6);
            c.add(0x1fae7);
            c.add(0x1fae8);
            c.add(0x1faf0);
            c.add(0x1faf1);
            c.add(0x1faf2);
            c.add(0x1faf3);
            c.add(0x1faf4);
            c.add(0x1faf5);
            c.add(0x1faf6);
            c.add(0x1faf7);
            c.add(0x1faf8);
            c.add(0xe0030);
            c.add(0xe0031);
            c.add(0xe0032);
            c.add(0xe0033);
            c.add(0xe0034);
            c.add(0xe0035);
            c.add(0xe0036);
            c.add(0xe0037);
            c.add(0xe0038);
            c.add(0xe0039);
            c.add(0xe0061);
            c.add(0xe0062);
            c.add(0xe0063);
            c.add(0xe0064);
            c.add(0xe0065);
            c.add(0xe0066);
            c.add(0xe0067);
            c.add(0xe0068);
            c.add(0xe0069);
            c.add(0xe006a);
            c.add(0xe006b);
            c.add(0xe006c);
            c.add(0xe006d);
            c.add(0xe006e);
            c.add(0xe006f);
            c.add(0xe0070);
            c.add(0xe0071);
            c.add(0xe0072);
            c.add(0xe0073);
            c.add(0xe0074);
            c.add(0xe0075);
            c.add(0xe0076);
            c.add(0xe0077);
            c.add(0xe0078);
            c.add(0xe0079);
            c.add(0xe007a);
            c.add(0xe007f);
            c.add(0xfe4e5);
            c.add(0xfe4e6);
            c.add(0xfe4e7);
            c.add(0xfe4e8);
            c.add(0xfe4e9);
            c.add(0xfe4ea);
            c.add(0xfe4eb);
            c.add(0xfe4ec);
            c.add(0xfe4ed);
            c.add(0xfe4ee);
            c.add(0xfe82c);
            c.add(0xfe82e);
            c.add(0xfe82f);
            c.add(0xfe830);
            c.add(0xfe831);
            c.add(0xfe832);
            c.add(0xfe833);
            c.add(0xfe834);
            c.add(0xfe835);
            c.add(0xfe836);
            c.add(0xfe837);
        }
    }
}
