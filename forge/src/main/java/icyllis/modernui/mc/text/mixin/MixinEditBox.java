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

import com.mojang.blaze3d.vertex.VertexConsumer;
import icyllis.modernui.mc.text.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * This mostly fixes text advance shift and decreases dynamic layout overhead,
 * but it cannot be truly internationalized due to Minecraft design defects.
 */
@Mixin(EditBox.class)
public abstract class MixinEditBox extends AbstractWidget {

    @Shadow
    @Final
    private static String CURSOR_APPEND_CHARACTER;

    @Shadow
    @Final
    private static int BORDER_COLOR_FOCUSED;

    @Shadow
    @Final
    private static int BORDER_COLOR;

    @Shadow
    @Final
    private static int BACKGROUND_COLOR;

    @Shadow
    private boolean isEditable;

    @Shadow
    private int textColor;

    @Shadow
    private int textColorUneditable;

    @Shadow
    private int cursorPos;

    @Shadow
    private int displayPos;

    @Shadow
    private int highlightPos;

    @Shadow
    private String value;

    @Shadow
    private int frame;

    @Shadow
    private boolean bordered;

    @Shadow
    @Nullable
    private String suggestion;

    @Shadow
    private BiFunction<String, Integer, FormattedCharSequence> formatter;

    public MixinEditBox(int x, int y, int w, int h, Component msg) {
        super(x, y, w, h, msg);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/gui/Font;IIIILnet/minecraft/client/gui/components/EditBox;" +
            "Lnet/minecraft/network/chat/Component;)V",
            at = @At("RETURN"))
    public void EditBox(Font font, int x, int y, int w, int h, @Nullable EditBox src, Component msg,
                        CallbackInfo ci) {
        // fast path
        formatter = (s, i) -> null;
    }

    @Shadow
    public abstract boolean isVisible();

    @Shadow
    public abstract int getInnerWidth();

    @Shadow
    protected abstract int getMaxLength();

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Override
    @Overwrite
    public void renderWidget(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTicks) {
        if (!isVisible()) {
            return;
        }
        if (bordered) {
            int color = isFocused() ? BORDER_COLOR_FOCUSED : BORDER_COLOR;
            gr.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, color);
            gr.fill(getX(), getY(), getX() + width, getY() + height, BACKGROUND_COLOR);
        }
        final int color = isEditable ? textColor : textColorUneditable;

        final String viewText =
                ModernStringSplitter.headByWidth(value.substring(displayPos), getInnerWidth(), Style.EMPTY);
        final int viewCursorPos = cursorPos - displayPos;
        final int clampedViewHighlightPos = Mth.clamp(highlightPos - displayPos, 0, viewText.length());

        final boolean cursorInRange = viewCursorPos >= 0 && viewCursorPos <= viewText.length();
        final boolean cursorVisible = isFocused() && ((frame / 10) & 1) == 0 && cursorInRange;

        final int baseX = bordered ? getX() + 4 : getX();
        final int baseY = bordered ? getY() + (height - 8) / 2 : getY();
        float seqX = baseX;

        final Matrix4f matrix = gr.pose().last().pose();
        final MultiBufferSource.BufferSource bufferSource = gr.bufferSource();

        final boolean separate;
        if (!viewText.isEmpty()) {
            String subText = cursorInRange ? viewText.substring(0, viewCursorPos) : viewText;
            FormattedCharSequence subSequence = formatter.apply(subText, displayPos);
            if (subSequence != null) {
                separate = true;
                seqX = ModernTextRenderer.drawText(subSequence, seqX, baseY, color, true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            } else {
                separate = false;
                seqX = ModernTextRenderer.drawText(viewText, seqX, baseY, color, true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
        } else {
            separate = false;
        }

        final boolean cursorNotAtEnd = cursorPos < value.length() || value.length() >= getMaxLength();

        // XXX: BiDi is not supported here
        final float cursorX;
        if (cursorInRange) {
            if (!separate && !viewText.isEmpty()) {
                TextLayout layout = TextLayoutEngine.getInstance().lookupVanillaLayout(viewText);
                float accAdv = 0;
                int seekIndex = 0;
                for (int i = 0; i < viewCursorPos; i++) {
                    if (viewText.charAt(i) == ChatFormatting.PREFIX_CODE) {
                        i++;
                        continue;
                    }
                    accAdv += layout.getAdvances()[seekIndex++];
                }
                cursorX = baseX + accAdv;
            } else {
                cursorX = seqX;
            }
        } else {
            cursorX = viewCursorPos > 0 ? baseX + width : baseX;
        }

        if (!viewText.isEmpty() && cursorInRange && viewCursorPos < viewText.length() && separate) {
            String subText = viewText.substring(viewCursorPos);
            FormattedCharSequence subSequence = formatter.apply(subText, cursorPos);
            if (subSequence != null) {
                ModernTextRenderer.drawText(subSequence, seqX, baseY, color, true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            } else {
                ModernTextRenderer.drawText(subText, seqX, baseY, color, true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
        }

        if (!cursorNotAtEnd && suggestion != null) {
            ModernTextRenderer.drawText(suggestion, cursorX, baseY, 0xFF808080, true,
                    matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }

        if (viewCursorPos != clampedViewHighlightPos) {
            gr.flush();

            TextLayout layout = TextLayoutEngine.getInstance().lookupVanillaLayout(viewText);
            float startX = baseX;
            float endX = cursorX;
            int seekIndex = 0;
            for (int i = 0; i < clampedViewHighlightPos; i++) {
                if (viewText.charAt(i) == ChatFormatting.PREFIX_CODE) {
                    i++;
                    continue;
                }
                startX += layout.getAdvances()[seekIndex++];
            }

            if (endX < startX) {
                float temp = startX;
                startX = endX;
                endX = temp;
            }
            if (startX > getX() + width) {
                startX = getX() + width;
            }
            if (endX > getX() + width) {
                endX = getX() + width;
            }

            VertexConsumer consumer = gr.bufferSource().getBuffer(RenderType.gui());
            consumer.vertex(matrix, startX, baseY + 10, 0)
                    .color(51, 181, 229, 102).endVertex();
            consumer.vertex(matrix, endX, baseY + 10, 0)
                    .color(51, 181, 229, 102).endVertex();
            consumer.vertex(matrix, endX, baseY - 1, 0)
                    .color(51, 181, 229, 102).endVertex();
            consumer.vertex(matrix, startX, baseY - 1, 0)
                    .color(51, 181, 229, 102).endVertex();
            gr.flush();
        } else if (cursorVisible) {
            if (cursorNotAtEnd) {
                gr.flush();

                VertexConsumer consumer = gr.bufferSource().getBuffer(RenderType.gui());
                consumer.vertex(matrix, cursorX - 0.5f, baseY + 10, 0)
                        .color(208, 208, 208, 255).endVertex();
                consumer.vertex(matrix, cursorX + 0.5f, baseY + 10, 0)
                        .color(208, 208, 208, 255).endVertex();
                consumer.vertex(matrix, cursorX + 0.5f, baseY - 1, 0)
                        .color(208, 208, 208, 255).endVertex();
                consumer.vertex(matrix, cursorX - 0.5f, baseY - 1, 0)
                        .color(208, 208, 208, 255).endVertex();
                gr.flush();
            } else {
                ModernTextRenderer.drawText(CURSOR_APPEND_CHARACTER, cursorX, baseY, color, true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

                gr.flush();
            }
        } else {
            gr.flush();
        }
    }

    /**
     * Reset blink.
     */
    @Inject(method = "setCursorPosition", at = @At("RETURN"))
    public void onSetCursorPosition(int pos, CallbackInfo ci) {
        frame = 0;
    }
}
