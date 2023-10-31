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

import com.ibm.icu.text.BreakIterator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import icyllis.modernui.core.UndoManager;
import icyllis.modernui.core.UndoOwner;
import icyllis.modernui.mc.text.*;
import icyllis.modernui.text.method.WordIterator;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

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
 * <li>Add Unicode cursor movement (by grapheme and by word).</li>
 * <li>Add undo/redo manager.</li>
 * <li>Reset cursor blink timer when cursor changed.</li>
 * <li>Adjust cursor blink cycle to from 0.6 seconds to 1 second.</li>
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
    private int frame;

    @Shadow
    private boolean bordered;

    @Shadow
    @Nullable
    private String suggestion;

    @Shadow
    private BiFunction<String, Integer, FormattedCharSequence> formatter;

    @Unique
    private WordIterator modernUI_MC$wordIterator;

    @Unique
    private long modernUI_MC$lastInsertTextNanos;

    @Unique
    private final UndoManager modernUI_MC$undoManager = new UndoManager();
    @Unique
    private final UndoOwner modernUI_MC$undoOwner =
            modernUI_MC$undoManager.getOwner("EditBox", this);

    public MixinEditBox(int x, int y, int w, int h, Component msg) {
        super(x, y, w, h, msg);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/gui/Font;IIIILnet/minecraft/client/gui/components/EditBox;" +
            "Lnet/minecraft/network/chat/Component;)V",
            at = @At("RETURN"))
    public void EditBox(Font font, int x, int y, int w, int h, @Nullable EditBox src, Component msg,
                        CallbackInfo ci) {
        // fast path
        formatter = (s, i) -> new VanillaTextWrapper(s);
    }

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
        final boolean cursorVisible = isFocused() && ((frame / 10) & 1) == 0 && cursorInRange;

        final int baseX = bordered ? getX() + 4 : getX();
        final int baseY = bordered ? getY() + (height - 8) / 2 : getY();
        float hori = baseX;

        final Matrix4f matrix = gr.pose().last().pose();
        final MultiBufferSource.BufferSource bufferSource = gr.bufferSource();

        final boolean separate;
        if (!viewText.isEmpty()) {
            String subText = cursorInRange ? viewText.substring(0, viewCursorPos) : viewText;
            FormattedCharSequence subSequence = formatter.apply(subText, displayPos);
            if (subSequence != null &&
                    !(subSequence instanceof VanillaTextWrapper)) {
                separate = true;
                hori = engine.getTextRenderer().drawText(subSequence, hori, baseY, color, true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            } else {
                separate = false;
                hori = engine.getTextRenderer().drawText(viewText, hori, baseY, color, true,
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
            if (subSequence != null &&
                    !(subSequence instanceof VanillaTextWrapper)) {
                engine.getTextRenderer().drawText(subSequence, hori, baseY, color, true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            } else {
                engine.getTextRenderer().drawText(subText, hori, baseY, color, true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
        }

        if (!cursorNotAtEnd && suggestion != null) {
            engine.getTextRenderer().drawText(suggestion, cursorX, baseY, 0xFF808080, true,
                    matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }

        if (viewCursorPos != clampedViewHighlightPos) {
            gr.flush();

            TextLayout layout = TextLayoutEngine.getInstance().lookupVanillaLayout(viewText,
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

            VertexConsumer consumer = gr.bufferSource().getBuffer(RenderType.guiOverlay());
            consumer.vertex(matrix, startX, baseY + 10, 0)
                    .color(51, 181, 229, 56).endVertex();
            consumer.vertex(matrix, endX, baseY + 10, 0)
                    .color(51, 181, 229, 56).endVertex();
            consumer.vertex(matrix, endX, baseY - 1, 0)
                    .color(51, 181, 229, 56).endVertex();
            consumer.vertex(matrix, startX, baseY - 1, 0)
                    .color(51, 181, 229, 56).endVertex();
            gr.flush();
        } else if (cursorVisible) {
            if (cursorNotAtEnd) {
                gr.flush();

                VertexConsumer consumer = gr.bufferSource().getBuffer(RenderType.guiOverlay());
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
                engine.getTextRenderer().drawText(CURSOR_APPEND_CHARACTER, cursorX, baseY, color, true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

                gr.flush();
            }
        } else {
            gr.flush();
        }
        // unconditional
        ci.cancel();
    }

    /**
     * Reset blink.
     */
    @Inject(method = "setCursorPosition", at = @At("RETURN"))
    public void onSetCursorPosition(int pos, CallbackInfo ci) {
        frame = 0;
    }

    @Inject(method = "getCursorPos", at = @At("HEAD"), cancellable = true)
    public void onGetCursorPosition(int dir,
                                    CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ModernStringSplitter.offsetByGrapheme(value, cursorPos, dir));
    }

    @Inject(method = "getWordPosition(IIZ)I", at = @At("HEAD"), cancellable = true)
    public void onGetWordPosition(int dir, int cursor, boolean withEndSpace,
                                  CallbackInfoReturnable<Integer> cir) {
        if (dir == -1 || dir == 1) {
            WordIterator wordIterator = modernUI_MC$wordIterator;
            if (wordIterator == null) {
                modernUI_MC$wordIterator = wordIterator = new WordIterator();
            }
            wordIterator.setCharSequence(value, cursor, cursor);
            int offset;
            if (dir == -1) {
                offset = wordIterator.preceding(cursor);
            } else {
                offset = wordIterator.following(cursor);
            }
            if (offset != BreakIterator.DONE) {
                cir.setReturnValue(offset);
            } else {
                cir.setReturnValue(cursor);
            }
        }
    }

    @Inject(
            method = "setValue",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/EditBox;value:Ljava/lang/String;",
                    opcode = Opcodes.PUTFIELD))
    public void onSetValue(String string, CallbackInfo ci) {
        if (modernUI_MC$undoManager.isInUndo()) {
            return;
        }
        if (value.isEmpty() && string.isEmpty()) {
            return;
        }
        // we see this operation as Replace
        EditBoxEditAction edit = new EditBoxEditAction(
                modernUI_MC$undoOwner,
                cursorPos,
                /*oldText*/ value,
                0,
                /*newText*/ string
        );
        modernUI_MC$addEdit(edit, false);
    }

    @Inject(
            method = "insertText",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/EditBox;value:Ljava/lang/String;",
                    opcode = Opcodes.PUTFIELD),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void onInsertText(String string, CallbackInfo ci,
                             int i, int j, int k, String string2, int l, String string3) {
        if (modernUI_MC$undoManager.isInUndo()) {
            return;
        }
        String oldText = value.substring(i, j);
        if (oldText.isEmpty() && string2.isEmpty()) {
            return;
        }
        EditBoxEditAction edit = new EditBoxEditAction(
                modernUI_MC$undoOwner,
                cursorPos,
                oldText,
                i,
                /*newText*/ string2
        );
        final long nanos = Util.getNanos();
        final boolean mergeInsert;
        // Minecraft split IME batch commit and even a single code point into code units,
        // if two charTyped() occur at the same time (or 1ms difference at most), try to
        // merge (concat) them.
        if (modernUI_MC$lastInsertTextNanos >= nanos - 1_000_000) {
            mergeInsert = true;
        } else {
            modernUI_MC$lastInsertTextNanos = nanos;
            mergeInsert = false;
        }
        modernUI_MC$addEdit(edit, mergeInsert);
    }

    @Inject(
            method = "deleteChars",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/EditBox;value:Ljava/lang/String;",
                    opcode = Opcodes.PUTFIELD),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void onDeleteChars(int i, CallbackInfo ci,
                              int j, int k, int l, String string) {
        if (modernUI_MC$undoManager.isInUndo()) {
            return;
        }
        String oldText = value.substring(k, l);
        if (oldText.isEmpty()) {
            return;
        }
        EditBoxEditAction edit = new EditBoxEditAction(
                modernUI_MC$undoOwner,
                /*cursorPos*/ j,
                oldText,
                k,
                ""
        );
        modernUI_MC$addEdit(edit, false);
    }

    @Unique
    public void modernUI_MC$addEdit(EditBoxEditAction edit, boolean mergeInsert) {
        final UndoManager mgr = modernUI_MC$undoManager;
        mgr.beginUpdate("addEdit");
        EditBoxEditAction lastEdit = mgr.getLastOperation(
                EditBoxEditAction.class,
                modernUI_MC$undoOwner,
                UndoManager.MERGE_MODE_UNIQUE
        );
        if (lastEdit == null) {
            mgr.addOperation(edit, UndoManager.MERGE_MODE_NONE);
        } else if (!mergeInsert || !lastEdit.mergeInsertWith(edit)) {
            mgr.commitState(modernUI_MC$undoOwner);
            mgr.addOperation(edit, UndoManager.MERGE_MODE_NONE);
        }
        mgr.endUpdate();
    }

    @Inject(method = "keyPressed", at = @At("TAIL"), cancellable = true)
    public void onKeyPressed(int i, int j, int k, CallbackInfoReturnable<Boolean> cir) {
        if (i == GLFW.GLFW_KEY_Z || i == GLFW.GLFW_KEY_Y) {
            if (Screen.hasControlDown() && !Screen.hasAltDown()) {
                if (!Screen.hasShiftDown()) {
                    UndoOwner[] owners = {modernUI_MC$undoOwner};
                    if (i == GLFW.GLFW_KEY_Z) {
                        // CTRL+Z
                        if (modernUI_MC$undoManager.countUndos(owners) > 0) {
                            modernUI_MC$undoManager.undo(owners, 1);
                            cir.setReturnValue(true);
                        }
                    } else if (modernUI_MC$tryRedo(owners)) {
                        // CTRL+Y
                        cir.setReturnValue(true);
                    }
                } else if (i == GLFW.GLFW_KEY_Z) {
                    UndoOwner[] owners = {modernUI_MC$undoOwner};
                    if (modernUI_MC$tryRedo(owners)) {
                        // CTRL+SHIFT+Z
                        cir.setReturnValue(true);
                    }
                }
            }
        }
    }

    @Unique
    private boolean modernUI_MC$tryRedo(UndoOwner[] owners) {
        if (modernUI_MC$undoManager.countRedos(owners) > 0) {
            modernUI_MC$undoManager.redo(owners, 1);
            return true;
        }
        return false;
    }
}
