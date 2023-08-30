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

package icyllis.modernui.mc.forge.mixin;

import com.mojang.blaze3d.platform.Window;
import icyllis.modernui.mc.forge.BlurHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    @Final
    private Window window;

    @Shadow
    public abstract boolean isWindowActive();

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void onGetFramerateLimit(CallbackInfoReturnable<Integer> info) {
        if ((BlurHandler.sFramerateInactive != 0 ||
                BlurHandler.sFramerateMinimized != 0) &&
                !isWindowActive()) {
            if (BlurHandler.sFramerateMinimized != 0 &&
                    BlurHandler.sFramerateMinimized < BlurHandler.sFramerateInactive &&
                    GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_ICONIFIED) != 0) {
                info.setReturnValue(Math.min(
                        BlurHandler.sFramerateMinimized,
                        window.getFramerateLimit()
                ));
            } else if (BlurHandler.sFramerateInactive != 0) {
                info.setReturnValue(Math.min(
                        BlurHandler.sFramerateInactive,
                        window.getFramerateLimit()
                ));
            }
        }
    }
}
