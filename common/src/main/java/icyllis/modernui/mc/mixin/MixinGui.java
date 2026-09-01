/*
 * Modern UI.
 * Copyright (C) 2019-2026 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Gui.class)
public abstract class MixinGui {

    @Shadow
    @Nullable
    private Screen screen;

    @Unique
    @Nullable
    private Screen modernUI_MC$previousScreen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreenHead(Screen guiScreen, CallbackInfo ci) {
        modernUI_MC$previousScreen = screen;
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void onSetScreenTail(Screen guiScreen, CallbackInfo ci) {
        MuiModApi.dispatchOnScreenChange(modernUI_MC$previousScreen, guiScreen);
        modernUI_MC$previousScreen = null;
    }
}
