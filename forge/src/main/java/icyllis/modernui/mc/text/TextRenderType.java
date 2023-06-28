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
import icyllis.arc3d.engine.SamplerState;
import icyllis.arc3d.opengl.*;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.RefCnt;
import icyllis.modernui.graphics.SharedPtr;
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
import java.util.Objects;
import java.util.Optional;

import static icyllis.modernui.ModernUI.*;

/**
 * Fast and modern text render type.
 */
public class TextRenderType extends RenderType {

    public static final int MODE_NORMAL = 0;
    public static final int MODE_SDF_FILL = 1;
    public static final int MODE_SDF_STROKE = 2;
    public static final int MODE_SEE_THROUGH = 3;

    private static volatile ShaderInstance sShaderNormal;
    private static volatile ShaderInstance sShaderSDFFill;
    private static volatile ShaderInstance sShaderSDFStroke;
    private static volatile ShaderInstance sShaderSeeThrough;

    static final ShaderStateShard
            RENDERTYPE_MODERN_TEXT_NORMAL = new ShaderStateShard(TextRenderType::getShaderNormal),
            RENDERTYPE_MODERN_TEXT_SDF_FILL = new ShaderStateShard(TextRenderType::getShaderSDFFill),
            RENDERTYPE_MODERN_TEXT_SDF_STROKE = new ShaderStateShard(TextRenderType::getShaderSDFStroke),
            RENDERTYPE_MODERN_TEXT_SEE_THROUGH = new ShaderStateShard(TextRenderType::getShaderSeeThrough);

    /**
     * Only the texture id is different, the rest state are same
     */
    private static final ImmutableList<RenderStateShard> NORMAL_STATES;
    private static final ImmutableList<RenderStateShard> SDF_FILL_STATES;
    private static final ImmutableList<RenderStateShard> SDF_STROKE_STATES;
    private static final ImmutableList<RenderStateShard> SEE_THROUGH_STATES;

    /**
     * Texture id to render type map
     */
    private static final Int2ObjectMap<TextRenderType> sNormalTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sSDFFillTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sSDFStrokeTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sSeeThroughTypes = new Int2ObjectOpenHashMap<>();

    private static TextRenderType sFirstSDFFillType;
    private static final BufferBuilder sFirstSDFFillBuffer = new BufferBuilder(131072);

    private static TextRenderType sFirstSDFStrokeType;
    private static final BufferBuilder sFirstSDFStrokeBuffer = new BufferBuilder(131072);

    // SDF requires bilinear sampling
    @SharedPtr
    private static GLSampler sLinearFontSampler;

    static {
        NORMAL_STATES = ImmutableList.of(
                RENDERTYPE_MODERN_TEXT_NORMAL,
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
        SDF_FILL_STATES = ImmutableList.of(
                RENDERTYPE_MODERN_TEXT_SDF_FILL,
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
        SDF_STROKE_STATES = ImmutableList.of(
                RENDERTYPE_MODERN_TEXT_SDF_STROKE,
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
    }

    private TextRenderType(String name, int bufferSize, Runnable setupState, Runnable clearState) {
        super(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS,
                bufferSize, false, true, setupState, clearState);
    }

    @Nonnull
    public static TextRenderType getOrCreate(int texture, int mode) {
        return switch (mode) {
            default -> sNormalTypes.computeIfAbsent(texture, TextRenderType::makeNormalType);
            case MODE_SDF_FILL -> sSDFFillTypes.computeIfAbsent(texture, TextRenderType::makeSDFFillType);
            case MODE_SDF_STROKE -> sSDFStrokeTypes.computeIfAbsent(texture, TextRenderType::makeSDFStrokeType);
            case MODE_SEE_THROUGH -> sSeeThroughTypes.computeIfAbsent(texture, TextRenderType::makeSeeThroughType);
        };
    }

    @Nonnull
    private static TextRenderType makeNormalType(int texture) {
        return new TextRenderType("modern_text_normal", 256, () -> {
            NORMAL_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, texture);
        }, () -> NORMAL_STATES.forEach(RenderStateShard::clearRenderState));
    }

    private static void ensureLinearFontSampler() {
        if (sLinearFontSampler == null) {
            GLEngine engine = (GLEngine) Core.requireDirectContext().getEngine();
            // default state is bilinear
            sLinearFontSampler = engine.getResourceProvider().findOrCreateCompatibleSampler(
                    SamplerState.DEFAULT);
            Objects.requireNonNull(sLinearFontSampler, "Failed to create sampler object");
        }
    }

    @Nonnull
    private static TextRenderType makeSDFFillType(int texture) {
        ensureLinearFontSampler();
        TextRenderType renderType = new TextRenderType("modern_text_sdf_fill", 256, () -> {
            SDF_FILL_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, texture);
            GLCore.glBindSampler(0, sLinearFontSampler.getHandle());
        }, () -> {
            SDF_FILL_STATES.forEach(RenderStateShard::clearRenderState);
            GLCore.glBindSampler(0, 0);
        });
        if (sFirstSDFFillType == null) {
            assert (sSDFFillTypes.isEmpty());
            sFirstSDFFillType = renderType;
            ((AccessRenderBuffers) Minecraft.getInstance().renderBuffers()).getFixedBuffers()
                    .put(renderType, sFirstSDFFillBuffer);
        }
        return renderType;
    }

    @Nonnull
    private static TextRenderType makeSDFStrokeType(int texture) {
        ensureLinearFontSampler();
        TextRenderType renderType = new TextRenderType("modern_text_sdf_stroke", 256, () -> {
            SDF_STROKE_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, texture);
            GLCore.glBindSampler(0, sLinearFontSampler.getHandle());
        }, () -> {
            SDF_STROKE_STATES.forEach(RenderStateShard::clearRenderState);
            GLCore.glBindSampler(0, 0);
        });
        if (sFirstSDFStrokeType == null) {
            assert (sSDFStrokeTypes.isEmpty());
            sFirstSDFStrokeType = renderType;
            ((AccessRenderBuffers) Minecraft.getInstance().renderBuffers()).getFixedBuffers()
                    .put(renderType, sFirstSDFStrokeBuffer);
        }
        return renderType;
    }

    @Nonnull
    private static TextRenderType makeSeeThroughType(int texture) {
        return new TextRenderType("modern_text_see_through", 256, () -> {
            SEE_THROUGH_STATES.forEach(RenderStateShard::setupRenderState);
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
    public static TextRenderType getFirstSDFFillType() {
        return sFirstSDFFillType;
    }

    /**
     * Similarly, but for outline.
     *
     * @see #getFirstSDFFillType()
     */
    @Nullable
    public static TextRenderType getFirstSDFStrokeType() {
        return sFirstSDFStrokeType;
    }

    public static void clear() {
        if (sFirstSDFFillType != null) {
            assert (!sSDFFillTypes.isEmpty());
            var access = (AccessRenderBuffers) Minecraft.getInstance().renderBuffers();
            if (!access.getFixedBuffers().remove(sFirstSDFFillType, sFirstSDFFillBuffer)) {
                throw new IllegalStateException();
            }
            sFirstSDFFillType = null;
        }
        if (sFirstSDFStrokeType != null) {
            assert (!sSDFStrokeTypes.isEmpty());
            var access = (AccessRenderBuffers) Minecraft.getInstance().renderBuffers();
            if (!access.getFixedBuffers().remove(sFirstSDFStrokeType, sFirstSDFStrokeBuffer)) {
                throw new IllegalStateException();
            }
            sFirstSDFStrokeType = null;
        }
        sNormalTypes.clear();
        sSDFFillTypes.clear();
        sSDFStrokeTypes.clear();
        sSeeThroughTypes.clear();
        sFirstSDFFillBuffer.clear();
        sFirstSDFStrokeBuffer.clear();
        sLinearFontSampler = RefCnt.move(sLinearFontSampler);
    }

    public static ShaderInstance getShaderNormal() {
        return sShaderNormal;
    }

    public static ShaderInstance getShaderSDFFill() {
        return sShaderSDFFill;
    }

    public static ShaderInstance getShaderSDFStroke() {
        return sShaderSDFStroke;
    }

    public static ShaderInstance getShaderSeeThrough() {
        return sShaderSeeThrough;
    }

    /**
     * Preload Modern UI text shaders for early text rendering. These shaders are loaded only once
     * and cannot be overridden by other resource packs or reloaded.
     */
    public static synchronized void preloadShaders() {
        if (sShaderNormal != null) {
            return;
        }
        final var source = Minecraft.getInstance().getVanillaPackResources();
        final var fallback = source.asProvider();
        final var provider = (ResourceProvider) location -> {
            // don't worry, ShaderInstance ctor will close it
            @SuppressWarnings("resource") final var stream = ModernUIText.class
                    .getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath());
            if (stream == null) {
                // fallback to vanilla
                return fallback.getResource(location);
            }
            return Optional.of(new Resource(source, () -> stream));
        };
        try {
            sShaderNormal = new ShaderInstance(provider,
                    ModernUIForge.location("rendertype_modern_text_normal"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            sShaderSDFFill = new ShaderInstance(provider,
                    ModernUIForge.location("rendertype_modern_text_sdf_fill"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            sShaderSDFStroke = new ShaderInstance(provider,
                    ModernUIForge.location("rendertype_modern_text_sdf_stroke"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            sShaderSeeThrough = new ShaderInstance(provider,
                    ModernUIForge.location("rendertype_modern_text_see_through"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        } catch (IOException e) {
            throw new IllegalStateException("Bad text shaders", e);
        }
        LOGGER.info(MARKER, "Preloaded modern text shaders");
    }
}
