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
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.SamplerState;
import icyllis.arc3d.opengl.*;
import icyllis.modernui.core.Core;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.text.mixin.AccessBufferSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.impl.client.rendering.FabricShaderProgram;
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

import static icyllis.modernui.ModernUI.LOGGER;
import static icyllis.modernui.mc.text.TextLayoutEngine.MARKER;

/**
 * Fast and modern text render type.
 */
public class TextRenderType extends RenderType {

    public static final int MODE_NORMAL = 0; // <- must be zero
    public static final int MODE_SDF_FILL = 1;
    public static final int MODE_SDF_STROKE = 2;
    public static final int MODE_SEE_THROUGH = 3;
    /**
     * Used in 2D rendering, render as {@link #MODE_NORMAL},
     * but we compute font size in device space from CTM.
     *
     * @since 3.8.1
     */
    public static final int MODE_UNIFORM_SCALE = 4; // <- must be power of 2

    private static volatile ShaderInstance sShaderNormal;

    private static volatile ShaderInstance sCurrentShaderSDFFill;
    private static volatile ShaderInstance sCurrentShaderSDFStroke;

    private static volatile ShaderInstance sShaderSDFFill;
    private static volatile ShaderInstance sShaderSDFStroke;

    @Nullable
    private static volatile ShaderInstance sShaderSDFFillSmart;
    @Nullable
    private static volatile ShaderInstance sShaderSDFStrokeSmart;

    private static boolean sSmartShadersLoaded = false;

    static final ShaderStateShard
            RENDERTYPE_MODERN_TEXT_NORMAL = new ShaderStateShard(TextRenderType::getShaderNormal),
            RENDERTYPE_MODERN_TEXT_SDF_FILL = new ShaderStateShard(TextRenderType::getShaderSDFFill),
            RENDERTYPE_MODERN_TEXT_SDF_STROKE = new ShaderStateShard(TextRenderType::getShaderSDFStroke);

    /**
     * Only the texture id is different, the rest state are same
     */
    private static final ImmutableList<RenderStateShard> NORMAL_STATES;
    private static final ImmutableList<RenderStateShard> SDF_FILL_STATES;
    private static final ImmutableList<RenderStateShard> SDF_STROKE_STATES;
    private static final ImmutableList<RenderStateShard> SEE_THROUGH_STATES;
    private static final ImmutableList<RenderStateShard> POLYGON_OFFSET_STATES;

    /**
     * Texture id to render type map
     */
    private static final Int2ObjectMap<TextRenderType> sNormalTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sSDFFillTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sSDFStrokeTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sSeeThroughTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<TextRenderType> sPolygonOffsetTypes = new Int2ObjectOpenHashMap<>();

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
                POLYGON_OFFSET_LAYERING,
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
                POLYGON_OFFSET_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE
        );
        SEE_THROUGH_STATES = ImmutableList.of(
                RENDERTYPE_TEXT_SEE_THROUGH_SHADER,
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
        POLYGON_OFFSET_STATES = ImmutableList.of(
                RENDERTYPE_TEXT_SHADER,
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

    // compatibility
    @Nonnull
    public static TextRenderType getOrCreate(int texture, Font.DisplayMode mode) {
        return switch (mode) {
            default -> sNormalTypes.computeIfAbsent(texture, TextRenderType::makeNormalType);
            case SEE_THROUGH -> sSeeThroughTypes.computeIfAbsent(texture, TextRenderType::makeSeeThroughType);
            case POLYGON_OFFSET -> sPolygonOffsetTypes.computeIfAbsent(texture, TextRenderType::makePolygonOffsetType);
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
            GLDevice device = (GLDevice) Core.requireDirectContext().getDevice();
            // default state is bilinear
            sLinearFontSampler = device.getResourceProvider().findOrCreateCompatibleSampler(
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
            if (!TextLayoutEngine.sCurrentInWorldRendering || TextLayoutEngine.sUseTextShadersInWorld) {
                GLCore.glBindSampler(0, sLinearFontSampler.getHandle());
            }
        }, () -> {
            SDF_FILL_STATES.forEach(RenderStateShard::clearRenderState);
            if (!TextLayoutEngine.sCurrentInWorldRendering || TextLayoutEngine.sUseTextShadersInWorld) {
                GLCore.glBindSampler(0, 0);
            }
        });
        if (sFirstSDFFillType == null) {
            assert (sSDFFillTypes.isEmpty());
            sFirstSDFFillType = renderType;
            if (TextLayoutEngine.sUseTextShadersInWorld) {
                try {
                    ((AccessBufferSource) Minecraft.getInstance().renderBuffers().bufferSource()).getFixedBuffers()
                            .put(renderType, sFirstSDFFillBuffer);
                } catch (Exception e) {
                    LOGGER.warn(MARKER, "Failed to add SDF fill to fixed buffers", e);
                }
            }
        }
        return renderType;
    }

    @Nonnull
    private static TextRenderType makeSDFStrokeType(int texture) {
        ensureLinearFontSampler();
        TextRenderType renderType = new TextRenderType("modern_text_sdf_stroke", 256, () -> {
            SDF_STROKE_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, texture);
            if (!TextLayoutEngine.sCurrentInWorldRendering || TextLayoutEngine.sUseTextShadersInWorld) {
                GLCore.glBindSampler(0, sLinearFontSampler.getHandle());
            }
        }, () -> {
            SDF_STROKE_STATES.forEach(RenderStateShard::clearRenderState);
            if (!TextLayoutEngine.sCurrentInWorldRendering || TextLayoutEngine.sUseTextShadersInWorld) {
                GLCore.glBindSampler(0, 0);
            }
        });
        if (sFirstSDFStrokeType == null) {
            assert (sSDFStrokeTypes.isEmpty());
            sFirstSDFStrokeType = renderType;
            if (TextLayoutEngine.sUseTextShadersInWorld) {
                try {
                    ((AccessBufferSource) Minecraft.getInstance().renderBuffers().bufferSource()).getFixedBuffers()
                            .put(renderType, sFirstSDFStrokeBuffer);
                } catch (Exception e) {
                    LOGGER.warn(MARKER, "Failed to add SDF stroke to fixed buffers", e);
                }
            }
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
    private static TextRenderType makePolygonOffsetType(int texture) {
        return new TextRenderType("modern_text_polygon_offset", 256, () -> {
            POLYGON_OFFSET_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, texture);
        }, () -> POLYGON_OFFSET_STATES.forEach(RenderStateShard::clearRenderState));
    }

    /**
     * Batch rendering and custom ordering.
     * <p>
     * We use a single atlas for batch rendering to improve performance.
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

    public static void clear(boolean cleanup) {
        if (sFirstSDFFillType != null) {
            assert (!sSDFFillTypes.isEmpty());
            var access = (AccessBufferSource) Minecraft.getInstance().renderBuffers().bufferSource();
            try {
                access.getFixedBuffers().remove(sFirstSDFFillType, sFirstSDFFillBuffer);
            } catch (Exception ignored) {
            }
            sFirstSDFFillType = null;
        }
        if (sFirstSDFStrokeType != null) {
            assert (!sSDFStrokeTypes.isEmpty());
            var access = (AccessBufferSource) Minecraft.getInstance().renderBuffers().bufferSource();
            try {
                access.getFixedBuffers().remove(sFirstSDFStrokeType, sFirstSDFStrokeBuffer);
            } catch (Exception ignored) {
            }
            sFirstSDFStrokeType = null;
        }
        sNormalTypes.clear();
        sSDFFillTypes.clear();
        sSDFStrokeTypes.clear();
        sSeeThroughTypes.clear();
        sFirstSDFFillBuffer.clear();
        sFirstSDFStrokeBuffer.clear();
        if (cleanup) {
            sLinearFontSampler = RefCnt.move(sLinearFontSampler);
        }
    }

    public static ShaderInstance getShaderNormal() {
        if (TextLayoutEngine.sCurrentInWorldRendering && !TextLayoutEngine.sUseTextShadersInWorld) {
            return GameRenderer.getRendertypeTextShader();
        }
        return sShaderNormal;
    }

    public static ShaderInstance getShaderSDFFill() {
        if (TextLayoutEngine.sCurrentInWorldRendering && !TextLayoutEngine.sUseTextShadersInWorld) {
            return GameRenderer.getRendertypeTextShader();
        }
        return sCurrentShaderSDFFill;
    }

    public static ShaderInstance getShaderSDFStroke() {
        return sCurrentShaderSDFStroke;
    }

    // RT only
    public static synchronized void toggleSDFShaders(boolean smart) {
        if (smart) {
            if (!sSmartShadersLoaded) {
                sSmartShadersLoaded = true;
                if (((GLCaps) Core.requireDirectContext()
                        .getCaps()).getGLSLVersion() >= 400) {
                    var provider = obtainResourceProvider();
                    try {
                        sShaderSDFFillSmart = new FabricShaderProgram(provider,
                                ModernUIMod.location("rendertype_modern_text_sdf_fill_400"),
                                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
                        sShaderSDFStrokeSmart = new FabricShaderProgram(provider,
                                ModernUIMod.location("rendertype_modern_text_sdf_stroke_400"),
                                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
                        LOGGER.info(MARKER, "Loaded smart SDF text shaders");
                    } catch (IOException e) {
                        LOGGER.error(MARKER, "Failed to load smart SDF text shaders", e);
                    }
                } else {
                    LOGGER.info(MARKER, "No GLSL 400, smart SDF text shaders disabled");
                }
            }
            if (sShaderSDFStrokeSmart != null) {
                sCurrentShaderSDFFill = sShaderSDFFillSmart;
                sCurrentShaderSDFStroke = sShaderSDFStrokeSmart;
                return;
            }
        }
        sCurrentShaderSDFFill = sShaderSDFFill;
        sCurrentShaderSDFStroke = sShaderSDFStroke;
    }

    /**
     * Preload Modern UI text shaders for early text rendering. These shaders are loaded only once
     * and cannot be overridden by other resource packs or reloaded.
     * <p>
     * Note that Minecraft vanilla will delete OpenGL shader objects when reloading resources, but
     * since we do not delete OpenGL program object (ShaderInstance), they will remain valid.
     */
    public static synchronized void preloadShaders() {
        if (sShaderNormal != null) {
            return;
        }
        var provider = obtainResourceProvider();
        try {
            sShaderNormal = new FabricShaderProgram(provider,
                    ModernUIMod.location("rendertype_modern_text_normal"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            sShaderSDFFill = new FabricShaderProgram(provider,
                    ModernUIMod.location("rendertype_modern_text_sdf_fill"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            sShaderSDFStroke = new FabricShaderProgram(provider,
                    ModernUIMod.location("rendertype_modern_text_sdf_stroke"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        } catch (IOException e) {
            throw new IllegalStateException("Bad text shaders", e);
        }
        toggleSDFShaders(false);
        LOGGER.info(MARKER, "Preloaded modern text shaders");
    }

    @Nonnull
    private static ResourceProvider obtainResourceProvider() {
        final var source = Minecraft.getInstance().getVanillaPackResources();
        final var fallback = source.asProvider();
        return location -> {
            // don't worry, ShaderInstance ctor will close it
            @SuppressWarnings("resource") final var stream = TextRenderType.class
                    .getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath());
            if (stream == null) {
                // fallback to vanilla
                return fallback.getResource(location);
            }
            return Optional.of(new Resource(source, () -> stream));
        };
    }
}
