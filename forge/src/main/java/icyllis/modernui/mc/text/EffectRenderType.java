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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.opengl.GLBackendFormat;
import icyllis.arc3d.opengl.GLTexture;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Core;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.Objects;

import static icyllis.arc3d.opengl.GLCore.GL_RGBA8;

public class EffectRenderType extends RenderType {

    private static GLTexture WHITE;

    private static final ImmutableList<RenderStateShard> STATES;
    private static final ImmutableList<RenderStateShard> SEE_THROUGH_STATES;

    private static final EffectRenderType TYPE;
    private static final EffectRenderType SEE_THROUGH_TYPE;

    static {
        STATES = ImmutableList.of(
                TextRenderType.RENDERTYPE_MODERN_TEXT_NORMAL,
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
        TYPE = new EffectRenderType("modern_text_effect", 256, () -> {
            STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, WHITE.getHandle());
        }, () -> STATES.forEach(RenderStateShard::clearRenderState));
        SEE_THROUGH_TYPE = new EffectRenderType("modern_text_effect_see_through", 256, () -> {
            SEE_THROUGH_STATES.forEach(RenderStateShard::setupRenderState);
            RenderSystem.setShaderTexture(0, WHITE.getHandle());
        }, () -> SEE_THROUGH_STATES.forEach(RenderStateShard::clearRenderState));
    }

    private EffectRenderType(String name, int bufferSize, Runnable setupState, Runnable clearState) {
        super(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS,
                bufferSize, false, true, setupState, clearState);
    }

    @RenderThread
    @Nonnull
    public static EffectRenderType getRenderType(boolean seeThrough) {
        if (WHITE == null)
            makeWhiteTexture();
        return seeThrough ? SEE_THROUGH_TYPE : TYPE;
    }

    @RenderThread
    @Nonnull
    public static EffectRenderType getRenderType(Font.DisplayMode mode) {
        throw new IllegalStateException();
    }

    private static void makeWhiteTexture() {
        var dContext = Core.requireDirectContext();
        var format = GLBackendFormat.make(GL_RGBA8);
        int width = 2, height = 2;
        WHITE = (GLTexture) dContext
                .getResourceProvider()
                .createTexture(
                        width, height,
                        format,
                        1,
                        0,
                        "MCTextEffect"
                );
        Objects.requireNonNull(WHITE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int colorType = ImageInfo.CT_RGBA_8888;
            int bpp = ImageInfo.bytesPerPixel(colorType);
            ByteBuffer pixels = stack.malloc(width * height * bpp);
            while (pixels.hasRemaining()) {
                pixels.put((byte) 0xff);
            }
            pixels.flip();
            dContext.getDevice().writePixels(
                    WHITE, 0, 0, width, height,
                    colorType, colorType,
                    width * bpp,
                    MemoryUtil.memAddress(pixels)
            );
        }
    }
}
