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
        final String msg;
        if (!modernUI_MC$broadcasting &&
                !(msg = input.getValue()).startsWith("/") &&
                Config.CLIENT.mEmojiShortcodes.get() &&
                msg.contains(":")) {
            final FontResourceManager mgr = FontResourceManager.getInstance();
            final Matcher matcher = MuiModApi.EMOJI_SHORTCODE_PATTERN.matcher(msg);

            StringBuilder builder = null;
            int lastEnd = 0;
            boolean replaced = false;
            while (matcher.find()) {
                if (builder == null) {
                    builder = new StringBuilder();
                }
                int st = matcher.start();
                int en = matcher.end();
                String emojiSequence = null;
                if (en - st > 2) {
                    emojiSequence = mgr.lookupEmojiShortcode(msg.substring(st + 1, en - 1));
                }
                if (emojiSequence != null) {
                    builder.append(msg, lastEnd, st);
                    builder.append(emojiSequence);
                    replaced = true;
                } else {
                    builder.append(msg, lastEnd, en);
                }
                lastEnd = en;
            }
            if (replaced) {
                builder.append(msg, lastEnd, msg.length());
                modernUI_MC$broadcasting = true;
                input.setValue(builder.toString());
                input.setCursorPosition(builder.length() - (msg.length() - lastEnd));
                input.setHighlightPos(input.getCursorPosition());
                modernUI_MC$broadcasting = false;
            }
        }
    }
}
