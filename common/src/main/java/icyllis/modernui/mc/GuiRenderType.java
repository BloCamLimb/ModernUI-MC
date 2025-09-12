/*
 * Modern UI.
 * Copyright (C) 2024-2025 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.ApiStatus;

/**
 * Modern GUI.
 */
@ApiStatus.Internal
public abstract class GuiRenderType {

    public static final RenderPipeline PIPELINE_TOOLTIP = RenderPipeline.builder()
            .withLocation(ModernUIMod.location("pipeline/modern_tooltip"))
            .withVertexShader(ModernUIMod.location("core/rendertype_modern_tooltip"))
            .withFragmentShader(ModernUIMod.location("core/rendertype_modern_tooltip"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("ModernTooltip", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    /*public static final ShaderProgram SHADER_TOOLTIP = new ShaderProgram(
            ModernUIMod.location("core/rendertype_modern_tooltip"),
            DefaultVertexFormat.POSITION,
            ShaderDefines.EMPTY);
    public static final ShaderProgram SHADER_ROUND_RECT = new ShaderProgram(
            ModernUIMod.location("core/rendertype_round_rect"),
            DefaultVertexFormat.POSITION_COLOR,
            ShaderDefines.EMPTY
    );

    static final ShaderStateShard
            RENDERTYPE_TOOLTIP = new ShaderStateShard(SHADER_TOOLTIP);
    static final ShaderStateShard
            RENDERTYPE_ROUND_RECT = new ShaderStateShard(SHADER_ROUND_RECT);

    static final ImmutableList<RenderStateShard> TOOLTIP_STATES = ImmutableList.of(
            RENDERTYPE_TOOLTIP,
            NO_TEXTURE,
            TRANSLUCENT_TRANSPARENCY,
            LEQUAL_DEPTH_TEST,
            CULL,
            LIGHTMAP,
            NO_OVERLAY,
            NO_LAYERING,
            MAIN_TARGET,
            DEFAULT_TEXTURING,
            COLOR_DEPTH_WRITE,
            DEFAULT_LINE,
            NO_COLOR_LOGIC
    );
    static final ImmutableList<RenderStateShard> ROUND_RECT_STATES = ImmutableList.of(
            RENDERTYPE_ROUND_RECT,
            NO_TEXTURE,
            TRANSLUCENT_TRANSPARENCY,
            LEQUAL_DEPTH_TEST,
            CULL,
            LIGHTMAP,
            NO_OVERLAY,
            NO_LAYERING,
            MAIN_TARGET,
            DEFAULT_TEXTURING,
            COLOR_DEPTH_WRITE,
            DEFAULT_LINE,
            NO_COLOR_LOGIC
    );

    static final RenderType
            TOOLTIP = new GuiRenderType("modern_tooltip", DefaultVertexFormat.POSITION, 1536,
            () -> TOOLTIP_STATES.forEach(RenderStateShard::setupRenderState),
            () -> TOOLTIP_STATES.forEach(RenderStateShard::clearRenderState));
    static final RenderType
            ROUND_RECT = new GuiRenderType("modern_round_rect", DefaultVertexFormat.POSITION_COLOR, 1536,
            () -> ROUND_RECT_STATES.forEach(RenderStateShard::setupRenderState),
            () -> ROUND_RECT_STATES.forEach(RenderStateShard::clearRenderState));*/

    /*private GuiRenderType(String name, VertexFormat vertexFormat, int bufferSize,
                          Runnable setupState, Runnable clearState) {
        super(name, bufferSize, false, false, setupState, clearState);
    }

    public static RenderType tooltip() {
        return null;//TOOLTIP;
    }

    public static RenderType roundRect() {
        return null;//ROUND_RECT;
    }*/
}
