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

import icyllis.modernui.mc.text.*;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nonnull;

@Mixin(Font.class)
public abstract class MixinFontRenderer {

    @Unique
    private final ModernTextRenderer modernUI_MC$textRenderer =
            TextLayoutEngine.getInstance().getTextRenderer();

    @Redirect(method = "<init>", at = @At(value = "NEW",
            target = "(Lnet/minecraft/client/StringSplitter$WidthProvider;)Lnet/minecraft/client/StringSplitter;"))
    private StringSplitter onNewSplitter(StringSplitter.WidthProvider widthProvider) {
        return new ModernStringSplitter(TextLayoutEngine.getInstance(), widthProvider);
    }

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    public int drawInBatch(@Nonnull String text, float x, float y, int color, boolean dropShadow,
                           @Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, Font.DisplayMode displayMode,
                           int colorBackground, int packedLight) {
        return (int) modernUI_MC$textRenderer.drawText(text, x, y, color, dropShadow, matrix, source,
                displayMode, colorBackground, packedLight) + (dropShadow ? 1 : 0);
    }

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    public int drawInBatch(@Nonnull Component text, float x, float y, int color, boolean dropShadow,
                           @Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, Font.DisplayMode displayMode,
                           int colorBackground, int packedLight) {
        return (int) modernUI_MC$textRenderer.drawText(text, x, y, color, dropShadow, matrix, source,
                displayMode, colorBackground, packedLight) + (dropShadow ? 1 : 0);
    }

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    public int drawInBatch(@Nonnull Component text, float x, float y, int color, boolean dropShadow,
                           @Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, Font.DisplayMode displayMode,
                           int colorBackground, int packedLight, boolean inverseDepth) {
        //TODO make use of inverseDepth for background and effect
        return (int) modernUI_MC$textRenderer.drawText(text, x, y, color, dropShadow, matrix, source,
                displayMode, colorBackground, packedLight) + (dropShadow ? 1 : 0);
    }

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    public int drawInBatch(@Nonnull FormattedCharSequence text, float x, float y, int color, boolean dropShadow,
                           @Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, Font.DisplayMode displayMode,
                           int colorBackground, int packedLight) {
        /*if (text instanceof FormattedTextWrapper)
            // Handle Enchantment Table
            if (((FormattedTextWrapper) text).mText.visit((style, string) -> style.getFont().equals(Minecraft
            .ALT_FONT) ?
                    FormattedText.STOP_ITERATION : Optional.empty(), Style.EMPTY).isPresent())
                return callDrawInternal(text, x, y, color, dropShadow, matrix, source, seeThrough, colorBackground,
                        packedLight);*/
        return (int) modernUI_MC$textRenderer.drawText(text, x, y, color, dropShadow, matrix, source,
                displayMode, colorBackground, packedLight) + (dropShadow ? 1 : 0);
    }

    /*@Invoker
    abstract int callDrawInternal(@Nonnull FormattedCharSequence text, float x, float y, int color, boolean dropShadow,
                                  @Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, boolean seeThrough,
                                  int colorBackground, int packedLight);*/

    /**
     * Bidi and shaping always works no matter what language is in.
     * So we should analyze the original string without reordering.
     * Do not reorder, we have our layout engine.
     *
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    public String bidirectionalShaping(String text) {
        return text;
    }

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    public void drawInBatch8xOutline(@Nonnull FormattedCharSequence text, float x, float y, int color, int outlineColor,
                                     @Nonnull Matrix4f matrix, @Nonnull MultiBufferSource source, int packedLight) {
        modernUI_MC$textRenderer.drawText8xOutline(text, x, y, color, outlineColor, matrix, source, packedLight);
    }
}
