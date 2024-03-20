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

import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;

@Deprecated
@Mixin(ProfileKeyPairManager.class)
public class MixinProfileKeyPairManager {

    /*@Inject(method = "signer", at = @At("HEAD"), cancellable = true)
    private void onSigner(CallbackInfoReturnable<Optional<Signer>> info) {
        if (ModernUIForge.sSecureProfilePublicKey) {
            info.setReturnValue(null);
        }
    }

    @Inject(method = "parsePublicKey", at = @At("HEAD"))
    private static void onParsePublicKey(CallbackInfoReturnable<Optional<ProfilePublicKey.Data>> info) throws
    CryptException {
        if (ModernUIForge.sSecureProfilePublicKey) {
            throw new CryptException(new InsecurePublicKeyException.MissingException());
        }
    }

    @Inject(method = "profilePublicKey", at = @At("HEAD"), cancellable = true)
    private void onProfilePublicKey(CallbackInfoReturnable<Optional<ProfilePublicKey>> info) {
        if (ModernUIForge.sSecureProfilePublicKey) {
            info.setReturnValue(Optional.empty());
        }
    }*/
}
