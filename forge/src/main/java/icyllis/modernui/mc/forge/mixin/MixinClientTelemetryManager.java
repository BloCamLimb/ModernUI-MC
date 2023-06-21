/*
 * Modern UI.
 * Copyright (C) 2019-2022 BloCamLimb. All rights reserved.
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

import icyllis.modernui.mc.forge.ModernUIForge;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.client.telemetry.TelemetryEventSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientTelemetryManager.class)
public class MixinClientTelemetryManager {

    @Inject(method = "createEventSender", at = @At("HEAD"), cancellable = true)
    private void onCreateTelemetrySession(CallbackInfoReturnable<TelemetryEventSender> cir) {
        if (ModernUIForge.sRemoveTelemetrySession) {
            cir.setReturnValue(TelemetryEventSender.DISABLED);
        }
    }
}
