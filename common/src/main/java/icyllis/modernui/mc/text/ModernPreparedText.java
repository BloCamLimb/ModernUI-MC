/*
 * Modern UI.
 * Copyright (C) 2025 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.joml.Matrix3x2f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * When this object is created, all glyphs are added to the font atlas.
 */
public class ModernPreparedText implements Font.PreparedText {

    public static final ModernPreparedText EMPTY = new ModernPreparedText(
            1, 0, false, 0, 0,
            0, 0, null,
            new ArrayList<>(), false,
            null, null, null
    );

    private final float density;
    private final float shadowOffset;
    private final boolean dropShadow;
    private final int color;
    private final int backgroundColor;
    private final float x;
    private final float top;
    private final ScreenRectangle bounds;
    private final ArrayList<TextRun> runs;
    private final boolean hasEffect;
    private final GLBakedGlyph[] glyphs;
    private final float[] positions;
    private final int[] flags;

    ModernPreparedText(float density, float shadowOffset, boolean dropShadow, int color,
                       int backgroundColor, float x, float top, ScreenRectangle bounds,
                       ArrayList<TextRun> runs, boolean hasEffect,
                       GLBakedGlyph[] glyphs, float[] positions, int[] flags) {
        this.density = density;
        this.shadowOffset = shadowOffset;
        this.dropShadow = dropShadow;
        this.color = color;
        this.backgroundColor = backgroundColor;
        this.x = x;
        this.top = top;
        this.bounds = bounds;
        this.runs = runs;
        this.hasEffect = hasEffect;
        this.glyphs = glyphs;
        this.positions = positions;
        this.flags = flags;
    }

    @Override
    public void visit(@Nonnull Font.GlyphVisitor glyphVisitor) {
        throw new UnsupportedOperationException("Modern Text Engine");
    }

    @Nullable
    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void submitRuns(GuiRenderState renderState, Matrix3x2f pose,
                           @Nullable ScreenRectangle scissor) {
        // For-index is 2x faster than enhanced-for
        for (int i = 0; i < runs.size(); i++) {
            var run = runs.get(i);
            renderState.submitGlyphToCurrentLayer(
                    new TextRunRenderState(pose, run.pipeline,
                            TextureSetup.singleTextureWithLightmap(run.textureView),
                            scissor,
                            x, top, color, dropShadow,
                            glyphs, positions, flags,
                            run.glyphStart, run.glyphEnd,
                            run.isColorEmoji, run.isDirectMask,
                            density, shadowOffset)
            );
        }
        //TODO submit background and effect pass
    }

    /**
     * GPU-baked text sub run.
     */
    static class TextRun {

        public final RenderPipeline pipeline;
        public final GpuTextureView textureView;
        public final int glyphStart;
        public int glyphEnd;
        public final boolean isColorEmoji;
        public final boolean isDirectMask;

        public TextRun(RenderPipeline pipeline, GpuTextureView textureView, int glyphStart,
                       boolean isColorEmoji, boolean isDirectMask) {
            this.pipeline = pipeline;
            this.textureView = textureView;
            this.glyphStart = glyphStart;
            this.isColorEmoji = isColorEmoji;
            this.isDirectMask = isDirectMask;
        }
    }
}
