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

import com.ibm.icu.text.BreakIterator;
import icyllis.modernui.ModernUI;
import icyllis.modernui.text.method.WordIterator;
import net.minecraft.client.StringSplitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.text.StringCharacterIterator;

@Mixin(StringSplitter.class)
public class MixinStringSplitter {

    @Inject(method = "getWordPosition", at = @At("HEAD"), cancellable = true)
    private static void getWordPosition(String value, int dir, int cursor, boolean withEndSpace,
                                        CallbackInfoReturnable<Integer> cir) {
        if (dir == -1 || dir == 1) {
            int offset;
            if (withEndSpace) {
                WordIterator wordIterator = new WordIterator();
                wordIterator.setCharSequence(value, cursor, cursor);
                if (dir == -1) {
                    offset = wordIterator.preceding(cursor);
                } else {
                    offset = wordIterator.following(cursor);
                }
            } else {
                BreakIterator wordIterator = BreakIterator.getWordInstance(
                        ModernUI.getSelectedLocale()
                );
                wordIterator.setText(new StringCharacterIterator(value, cursor));
                if (dir == -1) {
                    offset = wordIterator.preceding(cursor);
                } else {
                    offset = wordIterator.following(cursor);
                }
            }
            if (offset != BreakIterator.DONE) {
                cir.setReturnValue(offset);
            } else {
                cir.setReturnValue(cursor);
            }
        }
    }
}
