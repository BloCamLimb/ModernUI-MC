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

import com.mojang.blaze3d.platform.*;
import icyllis.modernui.ModernUI;
import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.mc.ModernUIClient;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.util.DisplayMetrics;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(Window.class)
public abstract class MixinWindow {

    @Shadow
    private double guiScale;

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Shadow
    @Nullable
    public abstract Monitor findBestMonitor();

    /**
     * @author BloCamLimb
     * @reason Make GUI scale more suitable, and not limited to even numbers when forceUnicode = true
     */
    @Inject(method = "calculateScale", at = @At("HEAD"), cancellable = true)
    public void onCalculateScale(int guiScaleIn, boolean forceUnicode, CallbackInfoReturnable<Integer> ci) {
        int r = MuiModApi.calcGuiScales((Window) (Object) this);
        ci.setReturnValue(guiScaleIn > 0 ? MathUtil.clamp(guiScaleIn, r >> 8 & 0xf, r & 0xf) : r >> 4 & 0xf);
    }

    @Inject(method = "setGuiScale", at = @At("HEAD"))
    private void onSetGuiScale(double scaleFactor, CallbackInfo ci) {
        int oldScale = (int) guiScale;
        int newScale = (int) scaleFactor;
        if (newScale != scaleFactor) {
            ModernUI.LOGGER.warn(ModernUI.MARKER,
                    "Gui scale {} should be an integer, some mods break this", scaleFactor);
        }

        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setToDefaults();

        metrics.widthPixels = getWidth();
        metrics.heightPixels = getHeight();

        // the base scale is 2x, so divide by 2
        metrics.density = newScale * 0.5f;
        metrics.densityDpi = (int) (metrics.density * DisplayMetrics.DENSITY_DEFAULT);
        metrics.scaledDensity = ModernUIClient.sFontScale * metrics.density;

        Monitor monitor = findBestMonitor();
        if (monitor != null) {
            // physical DPI is usually not necessary...
            try {
                int[] w = {0}, h = {0};
                org.lwjgl.glfw.GLFW.glfwGetMonitorPhysicalSize(monitor.getMonitor(), w, h);
                VideoMode mode = monitor.getCurrentMode();
                metrics.xdpi = 25.4f * mode.getWidth() / w[0];
                metrics.ydpi = 25.4f * mode.getHeight() / h[0];
            } catch (NoSuchMethodError ignored) {
                // the method is missing in PojavLauncher-modified GLFW
            }
        }
        var ctx = ModernUI.getInstance();
        if (ctx != null) {
            ctx.getResources().updateMetrics(metrics);
        }

        MuiModApi.dispatchOnWindowResize(getWidth(), getHeight(), newScale, oldScale);
    }

    @Redirect(method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
                    ordinal = 5),
            remap = false
    )
    private void onInit(int x, int y) {
        if (MuiModApi.get().isGLVersionPromoted()) {
            return;
        }
        GLFWErrorCallback callback = GLFW.glfwSetErrorCallback(null);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        final int[][] versions = {{4, 6}, {4, 5}, {4, 1}, {3, 3}};
        long window = 0;
        try {
            for (int[] version : versions) {
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, version[0]);
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, version[1]);
                ModernUI.LOGGER.debug(ModernUI.MARKER, "Trying OpenGL {}.{}", version[0], version[1]);
                window = GLFW.glfwCreateWindow(640, 480, "System Testing", 0, 0);
                if (window != 0) {
                    ModernUI.LOGGER.info(ModernUI.MARKER, "Promoted to OpenGL {}.{} Core Profile",
                            version[0], version[1]);
                    return;
                }
            }
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            ModernUI.LOGGER.warn(ModernUI.MARKER, "Fallback to OpenGL 3.2 Core Profile");
        } catch (Exception e) {
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            ModernUI.LOGGER.warn(ModernUI.MARKER, "Fallback to OpenGL 3.2 Core Profile", e);
        } finally {
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
            GLFW.glfwSetErrorCallback(callback);
        }
    }
}
