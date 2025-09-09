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

package icyllis.modernui.mc.text.mixin;

import icyllis.modernui.mc.mixin.AccessGuiGraphics;
import icyllis.modernui.mc.text.ModernTextRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class MixinIngameGui {

    /*@Redirect(
            method = "renderExperienceLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString" +
                    "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I")
    )
    private int drawExperience(GuiGraphics gr, Font font, String text, int x, int y, int color, boolean dropShadow) {
        if (!ModernTextRenderer.sTweakExperienceText) {
            return gr.drawString(font, text, x, y, color, dropShadow);
        }
        // the first four drawString() are black, and the last one is green
        if ((color & 0xFFFFFF) != 0) {
            float offset = ModernTextRenderer.sOutlineOffset;
            Matrix4f pose = gr.pose().last().pose();
            MultiBufferSource.BufferSource source = ((AccessGuiGraphics) gr).getBufferSource();
            font.drawInBatch(text, x + offset, y, 0xFF000000, dropShadow,
                    pose, source, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            font.drawInBatch(text, x - offset, y, 0xFF000000, dropShadow,
                    pose, source, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            font.drawInBatch(text, x, y + offset, 0xFF000000, dropShadow,
                    pose, source, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            font.drawInBatch(text, x, y - offset, 0xFF000000, dropShadow,
                    pose, source, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            gr.flush();
            font.drawInBatch(text, x, y, 0xFF000000 | color, dropShadow,
                    pose, source, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            gr.flush();
        }
        return x;
    }*/
}
