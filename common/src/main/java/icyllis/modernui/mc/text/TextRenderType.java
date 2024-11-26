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
import icyllis.arc3d.engine.ImmediateContext;
import icyllis.arc3d.engine.SamplerDesc;
import icyllis.arc3d.opengl.GLCaps;
import icyllis.arc3d.opengl.GLSampler;
import icyllis.modernui.core.Core;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.text.mixin.AccessBufferSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import org.lwjgl.opengl.GL33C;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

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

    public static final ShaderProgram SHADER_NORMAL = new ShaderProgram(
            ModernUIMod.location("core/rendertype_modern_text_normal"),
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            ShaderDefines.EMPTY
    );

    public static final ShaderProgram SHADER_SDF_FILL = new ShaderProgram(
            ModernUIMod.location("core/rendertype_modern_text_sdf_fill"),
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            ShaderDefines.EMPTY
    );
    public static final ShaderProgram SHADER_SDF_STROKE = new ShaderProgram(
            ModernUIMod.location("core/rendertype_modern_text_sdf_stroke"),
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            ShaderDefines.EMPTY
    );

    private static volatile ShaderProgram sCurrentShaderSDFFill = SHADER_SDF_FILL;
    private static volatile ShaderProgram sCurrentShaderSDFStroke = SHADER_SDF_STROKE;

    private static final ShaderProgram SHADER_SDF_FILL_SMART = new ShaderProgram(
            ModernUIMod.location("core/rendertype_modern_text_sdf_fill_400"),
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            ShaderDefines.EMPTY
    );
    private static final ShaderProgram SHADER_SDF_STROKE_SMART = new ShaderProgram(
            ModernUIMod.location("core/rendertype_modern_text_sdf_stroke_400"),
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            ShaderDefines.EMPTY
    );

    /*static final ShaderStateShard
            RENDERTYPE_MODERN_TEXT_NORMAL = new ShaderStateShard(TextRenderType::getShaderNormal),
            RENDERTYPE_MODERN_TEXT_SDF_FILL = new ShaderStateShard(TextRenderType::getShaderSDFFill),
            RENDERTYPE_MODERN_TEXT_SDF_STROKE = new ShaderStateShard(TextRenderType::getShaderSDFStroke);*/

    /**
     * Only the texture id is different, the rest state are same
     */
    private static final ImmutableList<RenderStateShard> NORMAL_STATES;
    private static final ImmutableList<RenderStateShard> SDF_FILL_STATES;
    private static final ImmutableList<RenderStateShard> SDF_STROKE_STATES;
    static final ImmutableList<RenderStateShard> VANILLA_STATES;
    static final ImmutableList<RenderStateShard> SEE_THROUGH_STATES;
    static final ImmutableList<RenderStateShard> POLYGON_OFFSET_STATES;

    /**
     * Texture id to render type map
     */
    private static final Int2ObjectOpenHashMap<TextRenderType> sNormalTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<TextRenderType> sSDFFillTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<TextRenderType> sSDFStrokeTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<TextRenderType> sVanillaTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<TextRenderType> sSeeThroughTypes = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<TextRenderType> sPolygonOffsetTypes = new Int2ObjectOpenHashMap<>();

    private static TextRenderType sFirstSDFFillType;
    private static final ByteBufferBuilder sFirstSDFFillBuffer = new ByteBufferBuilder(131072);

    private static TextRenderType sFirstSDFStrokeType;
    private static final ByteBufferBuilder sFirstSDFStrokeBuffer = new ByteBufferBuilder(131072);

    // SDF requires bilinear sampling
    @SharedPtr
    private static GLSampler sLinearFontSampler;

    static {
        NORMAL_STATES = ImmutableList.of(
                //RENDERTYPE_MODERN_TEXT_NORMAL,
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
                //RENDERTYPE_MODERN_TEXT_SDF_FILL,
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
                //RENDERTYPE_MODERN_TEXT_SDF_STROKE,
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
        VANILLA_STATES = ImmutableList.of(
                RENDERTYPE_TEXT_SHADER,
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
            case MODE_SDF_FILL -> sSDFFillTypes.computeIfAbsent(texture, TextRenderType::makeSDFFillType);
            case MODE_SDF_STROKE -> sSDFStrokeTypes.computeIfAbsent(texture, TextRenderType::makeSDFStrokeType);
            case MODE_SEE_THROUGH -> sSeeThroughTypes.computeIfAbsent(texture, TextRenderType::makeSeeThroughType);
            default -> sNormalTypes.computeIfAbsent(texture, TextRenderType::makeNormalType);
        };
    }

    // compatibility
    @Nonnull
    public static TextRenderType getOrCreate(int texture, Font.DisplayMode mode, boolean isBitmapFont) {
        return switch (mode) {
            case SEE_THROUGH -> sSeeThroughTypes.computeIfAbsent(texture, TextRenderType::makeSeeThroughType);
            case POLYGON_OFFSET -> sPolygonOffsetTypes.computeIfAbsent(texture, TextRenderType::makePolygonOffsetType);
            default -> isBitmapFont
                    ? sVanillaTypes.computeIfAbsent(texture, TextRenderType::makeVanillaType)
                    : sNormalTypes.computeIfAbsent(texture, TextRenderType::makeNormalType);
        };
    }

    @Nonnull
    private static TextRenderType makeNormalType(int texture) {
        return new TextRenderType("modern_text_normal", 256, () -> {
            RenderSystem.setShader(getShaderNormal());
            NORMAL_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, texture);
        }, () -> NORMAL_STATES.forEach(RenderStateShard::clearRenderState));
    }

    private static void ensureLinearFontSampler() {
        if (sLinearFontSampler == null) {
            ImmediateContext context = Core.requireImmediateContext();
            // default state is bilinear
            sLinearFontSampler = (GLSampler) context.getResourceProvider().findOrCreateCompatibleSampler(
                    SamplerDesc.make(SamplerDesc.FILTER_LINEAR, SamplerDesc.MIPMAP_MODE_LINEAR));
            Objects.requireNonNull(sLinearFontSampler, "Failed to create sampler object");
        }
    }

    @Nonnull
    private static TextRenderType makeSDFFillType(int texture) {
        ensureLinearFontSampler();
        TextRenderType renderType = new TextRenderType("modern_text_sdf_fill", 256, () -> {
            RenderSystem.setShader(getShaderSDFFill());
            SDF_FILL_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, texture);
            if (!TextLayoutEngine.sCurrentInWorldRendering || TextLayoutEngine.sUseTextShadersInWorld) {
                GL33C.glBindSampler(0, sLinearFontSampler.getHandle());
            }
        }, () -> {
            SDF_FILL_STATES.forEach(RenderStateShard::clearRenderState);
            if (!TextLayoutEngine.sCurrentInWorldRendering || TextLayoutEngine.sUseTextShadersInWorld) {
                GL33C.glBindSampler(0, 0);
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
            RenderSystem.setShader(getShaderSDFStroke());
            SDF_STROKE_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, texture);
            if (!TextLayoutEngine.sCurrentInWorldRendering || TextLayoutEngine.sUseTextShadersInWorld) {
                GL33C.glBindSampler(0, sLinearFontSampler.getHandle());
            }
        }, () -> {
            SDF_STROKE_STATES.forEach(RenderStateShard::clearRenderState);
            if (!TextLayoutEngine.sCurrentInWorldRendering || TextLayoutEngine.sUseTextShadersInWorld) {
                GL33C.glBindSampler(0, 0);
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
    private static TextRenderType makeVanillaType(int texture) {
        return new TextRenderType("modern_text_vanilla", 256, () -> {
            VANILLA_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, texture);
        }, () -> VANILLA_STATES.forEach(RenderStateShard::clearRenderState));
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

    public static synchronized void clear(boolean cleanup) {
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
        sVanillaTypes.clear();
        sSeeThroughTypes.clear();
        sPolygonOffsetTypes.clear();
        sFirstSDFFillBuffer.clear();
        sFirstSDFStrokeBuffer.clear();
        if (cleanup) {
            sLinearFontSampler = RefCnt.move(sLinearFontSampler);
            sCurrentShaderSDFFill = null;
            sCurrentShaderSDFStroke = null;
        }
    }

    public static ShaderProgram getShaderNormal() {
        if (TextLayoutEngine.sCurrentInWorldRendering && !TextLayoutEngine.sUseTextShadersInWorld) {
            return CoreShaders.RENDERTYPE_TEXT;
        }
        return SHADER_NORMAL;
    }

    public static ShaderProgram getShaderSDFFill() {
        if (TextLayoutEngine.sCurrentInWorldRendering && !TextLayoutEngine.sUseTextShadersInWorld) {
            return CoreShaders.RENDERTYPE_TEXT;
        }
        return sCurrentShaderSDFFill;
    }

    public static ShaderProgram getShaderSDFStroke() {
        return sCurrentShaderSDFStroke;
    }

    // RT only
    public static synchronized void toggleSDFShaders(boolean smart) {
        if (smart) {
            if (((GLCaps) Core.requireImmediateContext()
                    .getCaps()).getGLSLVersion() >= 400) {
                sCurrentShaderSDFFill = SHADER_SDF_FILL_SMART;
                sCurrentShaderSDFStroke = SHADER_SDF_STROKE_SMART;
                return;
            } else {
                LOGGER.info(MARKER, "No GLSL 400, smart SDF text shaders disabled");
            }
        }
        sCurrentShaderSDFFill = SHADER_SDF_FILL;
        sCurrentShaderSDFStroke = SHADER_SDF_STROKE;
    }

    /*
     * Preload Modern UI text shaders for early text rendering.
     */
    /*public static synchronized void preloadShaders() {
        var provider = obtainResourceProvider();
        try {
            Minecraft.getInstance().getShaderManager().preloadForStartup(
                    provider, SHADER_NORMAL, SHADER_SDF_FILL, SHADER_SDF_STROKE
            );
        } catch (IOException | ShaderManager.CompilationException e) {
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
            // don't worry, ShaderManager will close it
            @SuppressWarnings("resource") final var stream = TextRenderType.class
                    .getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath());
            if (stream == null) {
                // fallback to vanilla
                return fallback.getResource(location);
            }
            return Optional.of(new Resource(source, () -> stream));
        };
    }*/
}
