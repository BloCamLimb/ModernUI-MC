/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.*;
import org.jetbrains.annotations.ApiStatus;

/**
 * Modern tooltip.
 */
@ApiStatus.Internal
public class TooltipRenderType extends RenderType {

    public static final ShaderProgram SHADER_TOOLTIP = new ShaderProgram(
            ModernUIMod.location("core/rendertype_modern_tooltip"),
            DefaultVertexFormat.POSITION,
            ShaderDefines.EMPTY);

    static final ShaderStateShard
            RENDERTYPE_MODERN_TOOLTIP = new ShaderStateShard(SHADER_TOOLTIP);

    private static final ImmutableList<RenderStateShard> STATES = ImmutableList.of(
            RENDERTYPE_MODERN_TOOLTIP,
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
            DEFAULT_LINE
    );

    static final RenderType
            TOOLTIP = new TooltipRenderType("modern_tooltip", 1536,
            () -> STATES.forEach(RenderStateShard::setupRenderState),
            () -> STATES.forEach(RenderStateShard::clearRenderState));

    private TooltipRenderType(String name, int bufferSize, Runnable setupState, Runnable clearState) {
        super(name, DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS,
                bufferSize, false, false, setupState, clearState);
    }

    public static RenderType tooltip() {
        return TOOLTIP;
    }
}
