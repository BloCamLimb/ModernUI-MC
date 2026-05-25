/*
 * Modern UI.
 * Copyright (C) 2026 BloCamLimb. All rights reserved.
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
import icyllis.modernui.mc.text.TextLayoutEngine;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.ActiveArea;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Style;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.function.Consumer;

@Mixin(ActiveTextCollector.class)
public interface MixinActiveTextCollector {

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    static void findElementUnderCursor(GuiTextRenderState text, float testX, float testY, Consumer<Style> consumer) {
        ScreenRectangle bounds = text.bounds();
        if (bounds != null && bounds.containsPoint((int) testX, (int) testY)) {
            Vector2f localPos = text.pose.invert(new Matrix3x2f())
                    .transformPosition(new Vector2f(testX, testY));
            float localX = localPos.x;
            float localY = localPos.y;
            Font.PreparedText preparedText = text.ensurePrepared();
            if (preparedText instanceof ModernPreparedText) {
                ScreenRectangle localBounds = preparedText.bounds();
                if (localBounds != null && localBounds.containsPoint((int) localX, (int) localY)) {
                    Style style = TextLayoutEngine.getInstance().getStringSplitter()
                            .styleAtWidth(text.text, localX - ((ModernPreparedText) preparedText).x);
                    if (style != null) {
                        consumer.accept(style);
                    }
                }
            } else {
                // some mods subclass GuiTextRenderState to return a custom PreparedText,
                // fallback to vanilla logic
                preparedText.visit(new Font.GlyphVisitor() {
                    @Override
                    public void acceptGlyph(TextRenderable.Styled glyph) {
                        acceptActiveArea(glyph);
                    }

                    @Override
                    public void acceptEmptyArea(EmptyArea area) {
                        acceptActiveArea(area);
                    }

                    private void acceptActiveArea(ActiveArea area) {
                        if (ActiveTextCollector.isPointInRectangle(localX, localY,
                                area.activeLeft(), area.activeTop(), area.activeRight(), area.activeBottom())) {
                            consumer.accept(area.style());
                        }
                    }
                });
            }
        }
    }
}
