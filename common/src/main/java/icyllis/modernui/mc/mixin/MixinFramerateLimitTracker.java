/*
 * Modern UI.
 * Copyright (C) 2024 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import icyllis.modernui.mc.BlurHandler;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Add framerate limit when window lost focus.
 */
@Mixin(FramerateLimitTracker.class)
public class MixinFramerateLimitTracker {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int framerateLimit;

    @Redirect(method = "getFramerateLimit", at = @At(value = "FIELD",
            target = "Lcom/mojang/blaze3d/platform/FramerateLimitTracker;framerateLimit:I",
            opcode = Opcodes.GETFIELD))
    public int onFramerateLimit(FramerateLimitTracker instance) {
        int framerateInactive = BlurHandler.sFramerateInactive;
        if (framerateInactive != 0 && !minecraft.isWindowActive()) {
            return Math.min(framerateInactive, framerateLimit);
        }
        return framerateLimit;
    }
}
