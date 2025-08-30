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

package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.arc3d.engine.ContextOptions;
import icyllis.modernui.core.Core;
import icyllis.modernui.mc.ModernUIClient;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.forge.UIManagerForge;
import net.minecraft.util.TimeSource;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Configuration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {

    @Inject(method = "initBackendSystem", at = @At("HEAD"), remap = false)
    private static void onInitBackendSystem(CallbackInfoReturnable<TimeSource.NanoTimeSource> ci) {
        String name = Configuration.OPENGL_LIBRARY_NAME.get();
        if (name != null) {
            // non-system library should load before window creation
            ModernUIMod.LOGGER.info(ModernUIMod.MARKER, "OpenGL library: {}", name);
            Objects.requireNonNull(GL.getFunctionProvider(), "Implicit OpenGL loading is required");
        }
    }

    @Inject(method = "initRenderer", at = @At("TAIL"), remap = false)
    private static void onInitRenderer(int debugLevel, boolean debugSync, CallbackInfo ci) {
        Core.initialize();
        ContextOptions options = new ContextOptions();
        String value = ModernUIClient.getBootstrapProperty(ModernUIClient.BOOTSTRAP_USE_STAGING_BUFFERS_IN_OPENGL);
        if (value != null) {
            options.mUseStagingBuffers = Boolean.parseBoolean(value);
        }
        value = ModernUIClient.getBootstrapProperty(ModernUIClient.BOOTSTRAP_ALLOW_SPIRV_IN_OPENGL);
        if (value != null) {
            options.mAllowGLSPIRV = Boolean.parseBoolean(value);
        }
        options.mDriverBugWorkarounds = ModernUIClient.getGpuDriverBugWorkarounds();
        if (!Core.initOpenGL(options)) {
            Core.glShowCapsErrorDialog();
        }
        UIManagerForge.initialize();
    }

    /**
     * @author BloCamLimb
     * @reason Disable runtime checks
     */
    @Overwrite(remap = false)
    public static void assertOnRenderThread() {
    }
}
