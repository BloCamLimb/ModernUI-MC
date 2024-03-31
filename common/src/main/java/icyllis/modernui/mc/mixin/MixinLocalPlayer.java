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

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Deprecated
@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {

    /*@Inject(method = "signMessage", at = @At("HEAD"), cancellable = true)
    private void onSignMessage(MessageSigner signer,
                               ChatMessageContent content,
                               LastSeenMessages messages,
                               CallbackInfoReturnable<MessageSignature> ci) {
        if (ModernUIForge.sRemoveMessageSignature) {
            ci.setReturnValue(MessageSignature.EMPTY);
        }
    }

    @Inject(method = "signCommandArguments", at = @At("HEAD"), cancellable = true)
    private void onSignCommandArguments(MessageSigner signer,
                                        ParseResults<SharedSuggestionProvider> content,
                                        @Nullable Component decorated,
                                        LastSeenMessages messages,
                                        CallbackInfoReturnable<ArgumentSignatures> ci) {
        if (ModernUIForge.sRemoveMessageSignature) {
            ci.setReturnValue(ArgumentSignatures.EMPTY);
        }
    }*/
}
