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

import icyllis.modernui.mc.GradientRectangleRenderState;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.text.*;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * Changes:
 * <ul>
 * <li>Fixes some bidirectional text rendering bugs (not editing).</li>
 * <li>Fixes possible IndexOutOfBoundsException crash.</li>
 * <li>Use floating-point text advance precision.</li>
 * <li>Increases dynamic layout performance.</li>
 * <li>Adjust text highlight style.</li>
 * <li>Adjust text cursor rendering position.</li>
 * </ul>
 * <p>
 * This cannot be fully internationalized because of Minecraft bad implementation.
 */
@Mixin(EditBox.class)
public abstract class MixinEditBox extends AbstractWidget {

    @Shadow
    @Final
    private static String CURSOR_APPEND_CHARACTER;

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
    private long focusedTime;

    @Shadow
    @Nullable
    private String suggestion;

    @Shadow
    private BiFunction<String, Integer, FormattedCharSequence> formatter;

    @Shadow
    private int textX;

    @Shadow
    private int textY;

    @Shadow
    @Final
    private Font font;

    @Shadow
    @Nullable
    private Component hint;

    public MixinEditBox(int x, int y, int w, int h, Component msg) {
        super(x, y, w, h, msg);
    }

    /*@Inject(method = "<init>(Lnet/minecraft/client/gui/Font;IIIILnet/minecraft/client/gui/components/EditBox;" +
            "Lnet/minecraft/network/chat/Component;)V",
            at = @At("RETURN"))
    public void EditBox(Font font, int x, int y, int w, int h, @Nullable EditBox src, Component msg,
                        CallbackInfo ci) {
        // fast path
        formatter = (s, i) -> new VanillaTextWrapper(s);
    }*/

    @Shadow
    public abstract int getInnerWidth();

    @Shadow
    protected abstract int getMaxLength();

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Inject(
            method = "renderWidget",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/EditBox;isEditable:Z",
                    opcode = Opcodes.GETFIELD),
            cancellable = true)
    public void onRenderWidget(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTicks,
                               CallbackInfo ci) {
        final TextLayoutEngine engine = TextLayoutEngine.getInstance();

        final int color = isEditable ? textColor : textColorUneditable;

        final String viewText =
                engine.getStringSplitter().headByWidth(value.substring(displayPos), getInnerWidth(), Style.EMPTY);
        final int viewCursorPos = cursorPos - displayPos;
        final int clampedViewHighlightPos = Mth.clamp(highlightPos - displayPos, 0, viewText.length());

        final boolean cursorInRange = viewCursorPos >= 0 && viewCursorPos <= viewText.length();
        final boolean cursorVisible =
                isFocused() && (((Util.getMillis() - focusedTime) / 500) & 1) == 0 && cursorInRange;

        final int baseX = textX;
        final int baseY = textY;
        float hori = baseX;

        final boolean separate;
        if (!viewText.isEmpty()) {
            String subText = cursorInRange ? viewText.substring(0, viewCursorPos) : viewText;
            FormattedCharSequence subSequence = formatter.apply(subText, displayPos);
            if (!(subSequence instanceof VanillaTextWrapper)) {
                separate = true;
                gr.drawString(font, subSequence, baseX, baseY, color, true);
                hori += engine.getStringSplitter().measureText(subSequence);
            } else {
                separate = false;
                gr.drawString(font, new VanillaTextWrapper(viewText), baseX, baseY, color, true);
                hori += engine.getStringSplitter().measureText(viewText);
            }
        } else {
            separate = false;
        }

        final boolean cursorNotAtEnd = cursorPos < value.length() || value.length() >= getMaxLength();

        // XXX: BiDi is not supported here
        final float cursorX;
        if (cursorInRange) {
            if (!separate && !viewText.isEmpty()) {
                TextLayout layout = engine.lookupVanillaLayout(viewText,
                        Style.EMPTY, TextLayoutEngine.COMPUTE_ADVANCES);
                float curAdv = 0;
                int stripIndex = 0;
                for (int i = 0; i < viewCursorPos; i++) {
                    if (viewText.charAt(i) == ChatFormatting.PREFIX_CODE) {
                        i++;
                        continue;
                    }
                    curAdv += layout.getAdvances()[stripIndex++];
                }
                cursorX = baseX + curAdv;
            } else {
                cursorX = hori;
            }
        } else {
            cursorX = viewCursorPos > 0 ? baseX + width : baseX;
        }

        if (!viewText.isEmpty() && cursorInRange && viewCursorPos < viewText.length() && separate) {
            String subText = viewText.substring(viewCursorPos);
            FormattedCharSequence subSequence = formatter.apply(subText, cursorPos);
            gr.pose().pushMatrix();
            gr.pose().translate(hori - baseX, 0);
            gr.drawString(font, subSequence, baseX, baseY, color, true);
            gr.pose().popMatrix();
        }

        if (hint != null && viewText.isEmpty() && !isFocused()) {
            gr.drawString(font, hint, baseX, baseY, color);
        }

        if (!cursorNotAtEnd && suggestion != null) {
            gr.pose().pushMatrix();
            gr.pose().translate(cursorX - baseX, 0);
            gr.drawString(font, suggestion, baseX, baseY, 0xFF808080, true);
            gr.pose().popMatrix();
        }

        if (viewCursorPos != clampedViewHighlightPos) {
            TextLayout layout = engine.lookupVanillaLayout(viewText,
                    Style.EMPTY, TextLayoutEngine.COMPUTE_ADVANCES);
            float startX = baseX;
            float endX = cursorX;
            int stripIndex = 0;
            for (int i = 0; i < clampedViewHighlightPos; i++) {
                if (viewText.charAt(i) == ChatFormatting.PREFIX_CODE) {
                    i++;
                    continue;
                }
                startX += layout.getAdvances()[stripIndex++];
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

            ScreenRectangle scissorArea = MuiModApi.get().peekScissorStack(gr);
            MuiModApi.get().submitGuiElementRenderState(gr,
                    new GradientRectangleRenderState(
                            RenderPipelines.GUI,
                            TextureSetup.noTexture(),
                            new Matrix3x2f(gr.pose()),
                            startX, baseY - 1, endX, baseY + 10,
                            0x3833B5E5, 0x3833B5E5, 0x3833B5E5, 0x3833B5E5,
                            scissorArea
                    ));
        } else if (cursorVisible) {
            if (cursorNotAtEnd) {
                ScreenRectangle scissorArea = MuiModApi.get().peekScissorStack(gr);
                MuiModApi.get().submitGuiElementRenderState(gr,
                        new GradientRectangleRenderState(
                                RenderPipelines.GUI,
                                TextureSetup.noTexture(),
                                new Matrix3x2f(gr.pose()),
                                cursorX - 0.5f, baseY - 1, cursorX + 0.5f, baseY + 10,
                                0xFFD0D0D0, 0xFFD0D0D0, 0xFFD0D0D0, 0xFFD0D0D0,
                                scissorArea
                        ));
            } else {
                gr.pose().pushMatrix();
                gr.pose().translate(cursorX - baseX, 0);
                gr.drawString(font, CURSOR_APPEND_CHARACTER, baseX, baseY, color, true);
                gr.pose().popMatrix();
            }
        }
        // unconditional
        ci.cancel();
    }
}
