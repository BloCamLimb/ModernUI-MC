/*
 * Modern UI.
 * Copyright (C) 2025 BloCamLimb. All rights reserved.
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

import icyllis.modernui.mc.text.ModernPreparedText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiRenderer.class)
public class MixinGuiRenderer {

    @Shadow
    @Final
    GuiRenderState renderState;

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    private void prepareText() {
        renderState.forEachText(guiTextRenderState -> {
            Matrix3x2fc pose = guiTextRenderState.pose;
            ScreenRectangle scissor = guiTextRenderState.scissor;
            Font.PreparedText preparedText = guiTextRenderState.ensurePrepared();
            if (preparedText instanceof ModernPreparedText) {
                ((ModernPreparedText) preparedText).submitRuns(renderState, pose, scissor);
            } else {
                // some mods subclass GuiTextRenderState to return a custom PreparedText,
                // fallback to vanilla logic
                preparedText.visit(new Font.GlyphVisitor() {
                    @Override
                    public void acceptGlyph(TextRenderable.Styled glyph) {
                        renderState.addGlyphToCurrentLayer(new GlyphRenderState(pose, glyph, scissor));
                    }

                    @Override
                    public void acceptEffect(TextRenderable glyph) {
                        renderState.addGlyphToCurrentLayer(new GlyphRenderState(pose, glyph, scissor));
                    }
                });
            }
        });
    }
}
