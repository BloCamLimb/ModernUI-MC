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

import icyllis.modernui.mc.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;

/**
 * Transform emoji shortcodes.
 */
@Mixin(ChatScreen.class)
public class MixinChatScreen {

    @Shadow
    protected EditBox input;

    @Unique
    private boolean modernUI_MC$broadcasting;

    @Inject(method = "onEdited", at = @At("HEAD"))
    private void _onEdited(String s, CallbackInfo ci) {
        if (!modernUI_MC$broadcasting &&
                ModernUIClient.sEmojiShortcodes &&
                !input.getValue().startsWith("/") &&
                (!(input instanceof IModernEditBox) ||
                        !((IModernEditBox) input).modernUI_MC$getUndoManager().isInUndo())) {
            final FontResourceManager manager = FontResourceManager.getInstance();
            CYCLE:
            for (;;) {
                final Matcher matcher = MuiModApi.EMOJI_SHORTCODE_PATTERN.matcher(input.getValue());
                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    if (end - start > 2) {
                        String replacement = manager.lookupEmojiShortcode(
                                input.getValue().substring(start, end)
                        );
                        if (replacement != null) {
                            modernUI_MC$broadcasting = true;
                            input.setHighlightPos(start);
                            input.setCursorPosition(end);
                            input.insertText(replacement);
                            modernUI_MC$broadcasting = false;
                            continue CYCLE;
                        }
                    }
                }
                break;
            }
        }
    }
}
