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

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import icyllis.modernui.mc.FontResourceManager;
import icyllis.modernui.mc.ModernUIClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Provide emoji shortcode suggestions
 */
@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions {

    @Shadow
    @Final
    EditBox input;

    @Shadow
    @Final
    Minecraft minecraft;

    @Shadow
    @Final
    private boolean commandsOnly;

    @Shadow
    @Nullable
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private static int getLastWordIndex(String s) {
        return 0;
    }

    @Shadow
    public abstract void showSuggestions(boolean b);

    @Inject(method = "updateCommandInfo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientSuggestionProvider;" +
                    "getCustomTabSugggestions()Ljava/util/Collection;"),
            cancellable = true)
    private void onUpdateCommandInfo(CallbackInfo ci) {
        if (ModernUIClient.sEmojiShortcodes) {
            String inputValue = input.getValue();

            if (!commandsOnly && !inputValue.startsWith("/")) {
                String candidate = inputValue.substring(0, input.getCursorPosition());
                int startPos = getLastWordIndex(candidate);

                if (candidate.startsWith(":", startPos) && candidate.length() - startPos >= 2) {
                    Collection<String> suggestions = FontResourceManager.getInstance().getEmojiShortcodes(
                            candidate.charAt(startPos + 1)
                    );
                    if (!suggestions.isEmpty()) {
                        pendingSuggestions = SharedSuggestionProvider.suggest(suggestions,
                                new SuggestionsBuilder(candidate, startPos));
                        pendingSuggestions.thenRun(() -> {
                            if (!pendingSuggestions.isDone()) {
                                return;
                            }
                            if (minecraft.options.autoSuggestions().get()) {
                                showSuggestions(false);
                            }
                        });
                        ci.cancel();
                    }
                }
            }
        }
    }
}
