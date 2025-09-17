/*
 * Modern UI.
 * Copyright (C) 2021-2025 BloCamLimb. All rights reserved.
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

package icyllis.modernui.mc.text.mixin;

import icyllis.modernui.mc.text.ModernTextRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ContextualBarRenderer.class)
public interface MixinContextualBar {

    @Redirect(
            method = "renderExperienceLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString" +
                    "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V")
    )
    private static void drawExperience(GuiGraphics gr, Font font, Component text, int x, int y, int color,
                                       boolean dropShadow) {
        if (!ModernTextRenderer.sTweakExperienceText) {
            gr.drawString(font, text, x, y, color, dropShadow);
            return;
        }
        // the first four drawString() are black, and the last one is green
        if ((color & 0xFFFFFF) != 0) {
            // our engine will extract fractional translation from matrix
            float offset = ModernTextRenderer.sOutlineOffset;
            Matrix3x2fStack pose = gr.pose();
            pose.pushMatrix()
                    .translate(offset, 0);
            gr.drawString(font, text, x, y, 0xFF000000, dropShadow);
            pose.popMatrix();
            pose.pushMatrix()
                    .translate(-offset, 0);
            gr.drawString(font, text, x, y, 0xFF000000, dropShadow);
            pose.popMatrix();
            pose.pushMatrix()
                    .translate(0, offset);
            gr.drawString(font, text, x, y, 0xFF000000, dropShadow);
            pose.popMatrix();
            pose.pushMatrix()
                    .translate(0, -offset);
            gr.drawString(font, text, x, y, 0xFF000000, dropShadow);
            pose.popMatrix();
            gr.drawString(font, text, x, y, 0xFF000000 | color, dropShadow);
        }
    }
}
