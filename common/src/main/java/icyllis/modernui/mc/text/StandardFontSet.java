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

import com.mojang.blaze3d.font.GlyphInfo;
import icyllis.modernui.graphics.text.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.*;
import net.minecraft.client.gui.font.glyphs.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Unmodifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.IntFunction;

/**
 * This class is used only for <b>compatibility</b>.
 * <p>
 * Some mods may manually call {@link net.minecraft.client.gui.Font#prepareText},
 * we have to provide per-code-point glyph info. Minecraft vanilla maps code points
 * to glyphs without text shaping (no international support). It also ignores
 * resolution level (GUI scale), we assume it's current and round it up. Vanilla
 * doesn't support FreeType embolden as well, we ignore it.
 * <p>
 * This class is similar to {@link FontCollection} but no font itemization.
 * <p>
 * We use our own font atlas and rectangle packing algorithm.
 *
 * @author BloCamLimb
 * @since 3.8
 */
public class StandardFontSet extends FontSet {

    @Unmodifiable
    private List<FontFamily> mFamilies = Collections.emptyList();

    private CodepointMap<BakedGlyph> mGlyphs;

    private final IntFunction<BakedGlyph> mCacheGlyph = this::cacheGlyph;

    private float mResLevel = 2;
    private final FontPaint mStandardPaint = new FontPaint();

    private final GlyphSource mGlyphSource = new GlyphSource() {
        @Nonnull
        @Override
        public BakedGlyph getGlyph(int codepoint) {
            return StandardFontSet.this.getGlyph(codepoint);
        }

        @Nonnull
        @Override
        public BakedGlyph getRandomGlyph(@Nonnull RandomSource random, int width) {
            return StandardFontSet.this.getRandomGlyph(random, width);
        }
    };

    public StandardFontSet(@Nonnull TextureManager texMgr,
                           @Nonnull Identifier fontName) {
        super(new GlyphStitcher(texMgr, fontName));

        mStandardPaint.setFontStyle(FontPaint.NORMAL);
        mStandardPaint.setLocale(Locale.ROOT);
    }

    public void reload(@Nonnull FontCollection fontCollection, int newResLevel) {
        super.reload(Collections.emptyList(), Collections.emptySet());
        mFamilies = fontCollection.getFamilies();
        invalidateCache(newResLevel);
    }

    public void invalidateCache(int newResLevel) {
        if (mGlyphs != null) {
            mGlyphs.clear();
        }
        int fontSize = TextLayoutProcessor.computeFontSize(newResLevel);
        mStandardPaint.setFontSize(fontSize);
        mStandardPaint.setAntiAlias(GlyphManager.sAntiAliasing);
        mStandardPaint.setLinearMetrics(GlyphManager.sFractionalMetrics);
        mResLevel = newResLevel;
    }

    @Nonnull
    private BakedGlyph cacheGlyph(int codePoint) {
        for (FontFamily family : mFamilies) {
            if (!family.hasGlyph(codePoint)) {
                continue;
            }
            Font font = family.getClosestMatch(FontPaint.NORMAL);
            // we MUST check BitmapFont first,
            // because codePoint may be an invalid Unicode code point
            // but vanilla doesn't validate that
            if (font instanceof BitmapFont bitmapFont) {
                var info = bitmapFont.getGlyph(codePoint);
                if (info == null) {
                    continue;
                }
                // bake glyph ourselves
                var glyph = GlyphManager.getInstance().lookupGlyph(
                        bitmapFont,
                        (int) mStandardPaint.getFontSize(),
                        codePoint
                );
                if (glyph != null) {
                    // convert to Minecraft, see SheetGlyphInfo
                    float up = TextLayout.STANDARD_BASELINE_OFFSET +
                            (float) glyph.y / TextLayoutEngine.BITMAP_SCALE;
                    float left = (float) glyph.x / TextLayoutEngine.BITMAP_SCALE;
                    float right = left + (float) glyph.width / TextLayoutEngine.BITMAP_SCALE;
                    float down = up + (float) glyph.height / TextLayoutEngine.BITMAP_SCALE;
                    Identifier textureName = bitmapFont.getCurrentTextureName();
                    return new BakedSheetGlyph(
                            info,
                            new GlyphRenderTypes(
                                    TextRenderType.getOrCreate(textureName, net.minecraft.client.gui.Font.DisplayMode.NORMAL, true),
                                    TextRenderType.getOrCreate(textureName, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, true),
                                    TextRenderType.getOrCreate(textureName, net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, true),
                                    TextRenderType.getPipelineForGui(TextRenderType.MODE_NORMAL, true)
                            ),
                            GlyphManager.getInstance().getCurrentTexture(bitmapFont).getTextureView(),
                            glyph.u1,
                            glyph.u2,
                            glyph.v1,
                            glyph.v2,
                            left,
                            right,
                            up,
                            down
                    );
                }
                // no pixels
                return new EmptyBakedGlyph(info);
            } else if (font instanceof SpaceFont spaceFont) {
                float adv = spaceFont.getAdvance(codePoint);
                if (!Float.isNaN(adv)) {
                    return new EmptyBakedGlyph(GlyphInfo.simple(adv));
                }
            } else if (font instanceof OutlineFont outlineFont) {
                // no variation selector
                if (!outlineFont.hasGlyph(codePoint, 0)) {
                    continue;
                }
                char[] chars = Character.toChars(codePoint);
                IntArrayList glyphs = new IntArrayList(1);
                float adv = outlineFont.doSimpleLayout(
                        chars,
                        0, chars.length,
                        mStandardPaint, glyphs, null,
                        0, 0
                );
                var info = new StandardGlyphInfo((adv / mResLevel));
                if (glyphs.size() == 1 &&
                        glyphs.getInt(0) != 0) { // 0 is the missing glyph for TTF
                    // bake glyph ourselves
                    var glyph = GlyphManager.getInstance().lookupGlyph(
                            outlineFont,
                            (int) mStandardPaint.getFontSize(),
                            glyphs.getInt(0)
                    );
                    if (glyph != null) {
                        // convert to Minecraft, see SheetGlyphInfo
                        float up = TextLayout.STANDARD_BASELINE_OFFSET +
                                (float) glyph.y / mResLevel;
                        float left = (float) glyph.x / mResLevel;
                        float right = left + (float) glyph.width / mResLevel;
                        float down = up + (float) glyph.height / mResLevel;
                        return new BakedSheetGlyph(
                                info,
                                new GlyphRenderTypes(
                                        TextRenderType.getOrCreate(GlyphManager.FONT_SHEET, net.minecraft.client.gui.Font.DisplayMode.NORMAL, true),
                                        TextRenderType.getOrCreate(GlyphManager.FONT_SHEET, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, true),
                                        TextRenderType.getOrCreate(GlyphManager.FONT_SHEET, net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, true),
                                        TextRenderType.getPipelineForGui(TextRenderType.MODE_NORMAL, true)
                                ),
                                GlyphManager.getInstance().getFontTexture().getTextureView(),
                                glyph.u1,
                                glyph.u2,
                                glyph.v1,
                                glyph.v2,
                                left,
                                right,
                                up,
                                down
                        );
                    }
                }
                if (adv > 0) {
                    // no pixels, e.g. space
                    return new EmptyBakedGlyph(info);
                }
            }
            // color emoji requires complex layout, so no support
        }
        return super.source(false).getGlyph(codePoint); // missing
    }

    @Nonnull
    public BakedGlyph getGlyph(int codePoint) {
        if (mGlyphs == null) {
            mGlyphs = new CodepointMap<>(BakedGlyph[]::new, BakedGlyph[][]::new);
        }
        return mGlyphs.computeIfAbsent(codePoint, mCacheGlyph);
    }

    @Nonnull
    @Override
    public GlyphSource source(boolean nonFishyOnly) {
        return mGlyphSource;
    }

    // no obfuscated support

    public static class StandardGlyphInfo implements GlyphInfo {

        private final float mAdvance;

        public StandardGlyphInfo(float advance) {
            mAdvance = advance;
        }

        @Override
        public float getAdvance() {
            return mAdvance;
        }

        @Override
        public float getBoldOffset() {
            return 0.5f;
        }

        @Override
        public float getShadowOffset() {
            return ModernTextRenderer.sShadowOffset;
        }
    }

    public static class EmptyBakedGlyph implements BakedGlyph {

        private final GlyphInfo info;

        public EmptyBakedGlyph(@Nonnull GlyphInfo info) {
            this.info = info;
        }

        @Nonnull
        @Override
        public GlyphInfo info() {
            return info;
        }

        @Nullable
        @Override
        public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor,
                                                 @Nonnull Style style, float boldOffset, float shadowOffset) {
            return null;
        }
    }
}
