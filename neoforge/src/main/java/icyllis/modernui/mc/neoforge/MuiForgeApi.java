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

package icyllis.modernui.mc.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexFormat;
import icyllis.modernui.ModernUI;
import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;

@OnlyIn(Dist.CLIENT)
public final class MuiForgeApi extends MuiModApi {

    public MuiForgeApi() {
        ModernUI.LOGGER.info(ModernUI.MARKER, "Created MuiForgeAPI");
    }

    @Override
    public boolean isGLVersionPromoted() {
        try {
            String version = net.neoforged.fml.loading.ImmediateWindowHandler.getGLVersion();
            if (!"3.2".equals(version)) {
                ModernUI.LOGGER.info(ModernUI.MARKER, "Detected OpenGL {} Core Profile from FML Early Window",
                        version);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void loadEffect(GameRenderer gr, ResourceLocation effect) {
        gr.loadEffect(effect);
    }

    @Override
    public ShaderInstance makeShaderInstance(ResourceProvider resourceProvider,
                                             ResourceLocation resourceLocation,
                                             VertexFormat vertexFormat) throws IOException {
        return new ShaderInstance(resourceProvider, resourceLocation, vertexFormat);
    }

    @Override
    public boolean isKeyBindingMatches(KeyMapping keyMapping, InputConstants.Key key) {
        return keyMapping.isActiveAndMatches(key);
    }
}
