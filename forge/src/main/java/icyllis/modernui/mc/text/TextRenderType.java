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

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import icyllis.modernui.ModernUI;
import icyllis.modernui.graphics.opengl.GLCore;
import icyllis.modernui.mc.forge.ModernUIForge;
import icyllis.modernui.mc.text.mixin.AccessRenderBuffers;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;

import static icyllis.modernui.ModernUI.*;

/**
 * Fast and modern text render type.
 */
public class TextRenderType extends RenderType {

    private static volatile ShaderInstance sShader;
    private static volatile ShaderInstance sShaderDfFill;
    private static volatile ShaderInstance sShaderDfStroke;
    private static volatile ShaderInstance sShaderSeeThrough;

    static final ShaderStateShard
            RENDERTYPE_MODERN_TEXT = new ShaderStateShard(TextRenderType::getShader),
            RENDERTYPE_MODERN_TEXT_DF_FILL = new ShaderStateShard(TextRenderType::getShaderDfFill),
            RENDERTYPE_MODERN_TEXT_DF_STROKE = new ShaderStateShard(TextRenderType::getShaderDfStroke),
            RENDERTYPE_MODERN_TEXT_SEE_THROUGH = new ShaderStateShard(TextRenderType::getShaderSeeThrough);

    /**
     * Only the texture id is different, the rest state are same
     */
    private static final ImmutableList<RenderStateShard> STATES;
    private static final ImmutableList<RenderStateShard> DF_FILL_STATES;
    private static final ImmutableList<RenderStateShard> DF_STROKE_STATES;
    private static final ImmutableList<RenderStateShard> SEE_THROUGH_STATES;

    /**
     * Texture id to render type map
     */
    private static final Int2ObjectMap<TextRenderType> sTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sDfFillTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sDfStrokeTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sSeeThroughTypes = new Int2ObjectOpenHashMap<>();

    private static TextRenderType sFirstDfFillType;
    private static final BufferBuilder sFirstDfFillBuffer = new BufferBuilder(131072);

    private static TextRenderType sFirstDfStrokeType;
    private static final BufferBuilder sFirstDfStrokeBuffer = new BufferBuilder(131072);

    /**
     * Dynamic value controlling whether to use distance field at current stage.
     * Distance field only benefits in 3D world, it looks bad in 2D UI.
     *
     * @see icyllis.modernui.mc.text.mixin.MixinGameRenderer
     */
    public static boolean sCurrentUseDistanceField;
    // DF requires bilinear sampling
    private static int sLinearFontSampler;

    static {
        STATES = ImmutableList.of(
                RENDERTYPE_MODERN_TEXT,
                TRANSLUCENT_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                LIGHTMAP,
                NO_OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE
        );
        DF_FILL_STATES = ImmutableList.of(
                RENDERTYPE_MODERN_TEXT_DF_FILL,
                TRANSLUCENT_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                LIGHTMAP,
                NO_OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE
        );
        DF_STROKE_STATES = ImmutableList.of(
                RENDERTYPE_MODERN_TEXT_DF_STROKE,
                TRANSLUCENT_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                LIGHTMAP,
                NO_OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE
        );
        SEE_THROUGH_STATES = ImmutableList.of(
                RENDERTYPE_MODERN_TEXT_SEE_THROUGH,
                TRANSLUCENT_TRANSPARENCY,
                NO_DEPTH_TEST,
                CULL,
                LIGHTMAP,
                NO_OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_WRITE,
                DEFAULT_LINE
        );
        /*POLYGON_OFFSET_STATES = ImmutableList.of(
                RENDERTYPE_MODERN_TEXT,
                TRANSLUCENT_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                LIGHTMAP,
                NO_OVERLAY,
                POLYGON_OFFSET_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE
        );*/
    }

    private TextRenderType(String name, int bufferSize, Runnable setupState, Runnable clearState) {
        super(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS,
                bufferSize, false, true, setupState, clearState);
    }

    @Nonnull
    public static TextRenderType getOrCreate(int texture, boolean seeThrough, boolean isBitmap) {
        if (seeThrough)
            return sSeeThroughTypes.computeIfAbsent(texture, TextRenderType::makeSeeThroughType);
        else if (sCurrentUseDistanceField && !isBitmap)
            return sDfFillTypes.computeIfAbsent(texture, TextRenderType::makeDfFillType);
        else
            return sTypes.computeIfAbsent(texture, TextRenderType::makeType);
    }

    @Nonnull
    public static TextRenderType getOrCreateDfStroke(int texture) { // seeThrough=false isBitmap=false
        return sDfStrokeTypes.computeIfAbsent(texture, TextRenderType::makeDfStrokeType);
    }

    @Nonnull
    private static TextRenderType makeType(int texture) {
        return new TextRenderType("modern_text", 256, () -> {
            STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.enableTexture();
            RenderSystem.setShaderTexture(0, texture);
        }, () -> STATES.forEach(RenderStateShard::clearRenderState));
    }

    @Nonnull
    private static TextRenderType makeDfFillType(int texture) {
        TextRenderType renderType = new TextRenderType("modern_text_df_fill", 256, () -> {
            DF_FILL_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.enableTexture();
            RenderSystem.setShaderTexture(0, texture);
            GLCore.glBindSampler(0, sLinearFontSampler);
        }, () -> {
            DF_FILL_STATES.forEach(RenderStateShard::clearRenderState);
            GLCore.glBindSampler(0, 0);
        });
        if (sFirstDfFillType == null) {
            assert (sDfFillTypes.isEmpty());
            sFirstDfFillType = renderType;
            ((AccessRenderBuffers) Minecraft.getInstance().renderBuffers()).getFixedBuffers()
                    .put(renderType, sFirstDfFillBuffer);
        }
        return renderType;
    }

    @Nonnull
    private static TextRenderType makeDfStrokeType(int texture) {
        TextRenderType renderType = new TextRenderType("modern_text_df_stroke", 256, () -> {
            DF_STROKE_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.enableTexture();
            RenderSystem.setShaderTexture(0, texture);
            GLCore.glBindSampler(0, sLinearFontSampler);
        }, () -> {
            DF_STROKE_STATES.forEach(RenderStateShard::clearRenderState);
            GLCore.glBindSampler(0, 0);
        });
        if (sFirstDfStrokeType == null) {
            assert (sDfStrokeTypes.isEmpty());
            sFirstDfStrokeType = renderType;
            ((AccessRenderBuffers) Minecraft.getInstance().renderBuffers()).getFixedBuffers()
                    .put(renderType, sFirstDfStrokeBuffer);
        }
        return renderType;
    }

    @Nonnull
    private static TextRenderType makeSeeThroughType(int texture) {
        return new TextRenderType("modern_text_see_through", 256, () -> {
            SEE_THROUGH_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.enableTexture();
            RenderSystem.setShaderTexture(0, texture);
        }, () -> SEE_THROUGH_STATES.forEach(RenderStateShard::clearRenderState));
    }

    @Nonnull
    public static TextRenderType getOrCreate(int texture, Font.DisplayMode mode) {
        throw new IllegalStateException();
    }

    /**
     * Deferred rendering.
     * <p>
     * We use a universal atlas for deferred rendering to improve performance.
     */
    @Nullable
    public static TextRenderType firstDfFillType() {
        return sFirstDfFillType;
    }

    /**
     * Similarly, but for outline.
     *
     * @see #firstDfFillType()
     */
    @Nullable
    public static TextRenderType firstDfStrokeType() {
        return sFirstDfStrokeType;
    }

    public static void clear() {
        if (sFirstDfFillType != null) {
            assert (!sDfFillTypes.isEmpty());
            var access = (AccessRenderBuffers) Minecraft.getInstance().renderBuffers();
            if (!access.getFixedBuffers().remove(sFirstDfFillType, sFirstDfFillBuffer)) {
                throw new IllegalStateException();
            }
            sFirstDfFillType = null;
        }
        if (sFirstDfStrokeType != null) {
            assert (!sDfStrokeTypes.isEmpty());
            var access = (AccessRenderBuffers) Minecraft.getInstance().renderBuffers();
            if (!access.getFixedBuffers().remove(sFirstDfStrokeType, sFirstDfStrokeBuffer)) {
                throw new IllegalStateException();
            }
            sFirstDfStrokeType = null;
        }
        sTypes.clear();
        sDfFillTypes.clear();
        sDfStrokeTypes.clear();
        sSeeThroughTypes.clear();
        sFirstDfFillBuffer.clear();
        sFirstDfStrokeBuffer.clear();
    }

    public static ShaderInstance getShader() {
        return sShader;
    }

    public static ShaderInstance getShaderDfFill() {
        return sShaderDfFill;
    }

    public static ShaderInstance getShaderDfStroke() {
        return sShaderDfStroke;
    }

    public static ShaderInstance getShaderSeeThrough() {
        return sShaderSeeThrough;
    }

    /**
     * Preload Modern UI text shaders for early text rendering. These shaders are loaded only once
     * and cannot be overridden by other resource packs or reloaded.
     */
    public static synchronized void preloadShaders() {
        if (sShader != null) {
            return;
        }
        final var fallback = Minecraft.getInstance().getClientPackSource().getVanillaPack().asProvider();
        final var provider = (ResourceProvider) location -> {
            // don't worry, ShaderInstance ctor will close it
            @SuppressWarnings("resource") final var stream = ModernUITextMC.class
                    .getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath());
            if (stream == null) {
                // fallback to vanilla
                return fallback.getResource(location);
            }
            return Optional.of(new Resource(ModernUI.ID, () -> stream));
        };
        try {
            sShader = new ShaderInstance(provider,
                    ModernUIForge.location("rendertype_modern_text"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            sShaderDfFill = new ShaderInstance(provider,
                    ModernUIForge.location("rendertype_modern_text_df_fill"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            sShaderDfStroke = new ShaderInstance(provider,
                    ModernUIForge.location("rendertype_modern_text_df_stroke"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            sShaderSeeThrough = new ShaderInstance(provider,
                    ModernUIForge.location("rendertype_modern_text_see_through"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            sLinearFontSampler = GLCore.glGenSamplers();
            GLCore.glSamplerParameteri(sLinearFontSampler, GLCore.GL_TEXTURE_MIN_FILTER,
                    GLCore.GL_LINEAR_MIPMAP_LINEAR);
            GLCore.glSamplerParameteri(sLinearFontSampler, GLCore.GL_TEXTURE_MAG_FILTER, GLCore.GL_LINEAR);
            GLCore.glSamplerParameteri(sLinearFontSampler, GLCore.GL_TEXTURE_MIN_LOD, 0);
            GLCore.glSamplerParameteri(sLinearFontSampler, GLCore.GL_TEXTURE_MAX_LOD, 0);
        } catch (IOException e) {
            throw new IllegalStateException("Bad text shaders", e);
        }
        LOGGER.info(MARKER, "Preloaded modern text shaders");
    }
}
