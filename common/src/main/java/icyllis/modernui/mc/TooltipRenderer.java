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

package icyllis.modernui.mc;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.arc3d.core.MathUtil;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.mc.mixin.AccessClientTextTooltip;
import icyllis.modernui.mc.mixin.AccessGuiGraphics;
import icyllis.modernui.mc.text.CharacterStyle;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.*;
import net.minecraft.world.item.*;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * An extension that replaces vanilla tooltip style.
 */
@ApiStatus.Internal
public final class TooltipRenderer implements ScrollController.IListener {

    // config value
    public static volatile boolean sTooltip = true;

    public static final int[] sFillColor = new int[4];
    public static final int[] sStrokeColor = new int[4];
    public static volatile float sBorderWidth = 4 / 3f;
    public static volatile float sCornerRadius = 4;
    public static volatile float sShadowRadius = 10;
    public static volatile float sShadowAlpha = 0.3f;
    public static volatile boolean sAdaptiveColors = true;

    // space between mouse and tooltip
    public static final int TOOLTIP_SPACE = 12;
    public static final int H_BORDER = 4;
    public static final int V_BORDER = 4;
    //public static final int LINE_HEIGHT = 10;
    // extra space after first line
    private static final int TITLE_GAP = 2;

    //private static final List<FormattedText> sTempTexts = new ArrayList<>();

    //private static final int[] sActiveFillColor = new int[4];
    private final int[] mWorkStrokeColor = new int[4];
    private final int[] mActiveStrokeColor = new int[4];
    //static volatile float sAnimationDuration; // milliseconds
    public static volatile int sBorderColorCycle = 1000; // milliseconds

    public static volatile boolean sExactPositioning = true;
    public static volatile boolean sRoundedShapes = true;
    public static volatile boolean sCenterTitle = true;
    public static volatile boolean sTitleBreak = true;
    public static volatile int sArrowScrollFactor = 60;

    public volatile boolean mLayoutRTL;

    private boolean mDraw;
    //public static float sAlpha = 1;

    private float mScroll;
    // 0 = off, 1 = down, -1 = up
    private int mMarqueeDir;
    // the time point when marquee is at top or bottom
    private long mMarqueeEndMillis;

    // arrow key movement
    private int mPendingArrowMove;
    private final ScrollController mScroller = new ScrollController(this);

    private static final long MARQUEE_DELAY_MILLIS = 1200;

    private boolean mFrameGap;
    private long mCurrTimeMillis;
    private long mCurrDeltaMillis;

    // no weak ref, clear on frame gap
    private ItemStack mLastSeenItem;

    // true to use spectrum colors
    private boolean mUseSpectrum;

    public TooltipRenderer() {
    }

    public void update(long deltaMillis, long timeMillis) {
        /*if (sAnimationDuration <= 0) {
            sAlpha = 1;
        } else if (sDraw) {
            if (sAlpha < 1) {
                sAlpha = Math.min(sAlpha + deltaMillis / sAnimationDuration, 1);
            }
            sDraw = false;
        } else if (sAlpha > 0) {
            sAlpha = Math.max(sAlpha - deltaMillis / sAnimationDuration, 0);
        }*/
        if (mDraw) {
            mDraw = false;
            if (mFrameGap) {
                mScroller.scrollTo(0);
                mScroller.abortAnimation();
                mMarqueeEndMillis = timeMillis;
                // default is auto scrolling
                mMarqueeDir = 1;
            }
            mFrameGap = false;
        } else {
            mFrameGap = true;
            mLastSeenItem = null;
            mPendingArrowMove = 0;
        }
        mCurrTimeMillis = timeMillis;
        mCurrDeltaMillis = deltaMillis;
    }

    public void updateArrowMovement(int move) {
        if (sArrowScrollFactor > 0) {
            mPendingArrowMove += move;
        }
    }

    @Override
    public void onScrollAmountUpdated(ScrollController controller, float amount) {
        // stop auto scrolling
        mMarqueeDir = 0;
        mScroll = amount;
    }

    // compute a gradient for the given item
    void computeWorkingColor(@Nonnull ItemStack item) {
        if (sAdaptiveColors && !item.isEmpty()) {
            if (sRoundedShapes && (item.is(Items.DRAGON_EGG) ||
                    item.is(Items.DEBUG_STICK))) {
                mUseSpectrum = true;
                return;
            }
            Style baseStyle = Style.EMPTY;
            Rarity rarity = item.getRarity();
            if (rarity != Rarity.COMMON) {
                baseStyle = MuiModApi.get().applyRarityTo(rarity, baseStyle);
            }
            IntOpenHashSet colors = new IntOpenHashSet(16);
            // consider formatting codes
            StringDecomposer.iterateFormatted(
                    item.getHoverName(),
                    baseStyle,
                    (i, style, ch) -> {
                        TextColor textColor = style.getColor();
                        if (textColor != null) {
                            return !(colors.add(textColor.getValue() & 0xFFFFFF) &&
                                    colors.size() >= 16);
                        }
                        return true;
                    }
            );
            if (!colors.isEmpty()) {
                ArrayList<float[]> hsvColors = new ArrayList<>(16);
                for (var it = colors.iterator(); it.hasNext(); ) {
                    int c = it.nextInt();
                    float[] hsv = new float[3];
                    Color.RGBToHSV(c, hsv);
                    // reduce saturation and brightness
                    hsv[1] = Math.min(hsv[1], 0.9f);
                    hsv[2] = MathUtil.clamp(hsv[2], 0.2f, 0.85f);
                    hsvColors.add(hsv);
                }
                if (!hsvColors.isEmpty()) {
                    int size = hsvColors.size();
                    if (size > 4) {
                        if (sRoundedShapes) {
                            mUseSpectrum = true;
                            return;
                        }
                        Collections.shuffle(hsvColors);
                    }
                    int c1 = Color.HSVToColor(hsvColors.get(0));
                    int c2, c3, c4;
                    if (size > 2) {
                        // we have 3 or 4 colors
                        c2 = Color.HSVToColor(hsvColors.get(1));
                        c3 = Color.HSVToColor(hsvColors.get(2));
                        if (size == 4) {
                            c4 = Color.HSVToColor(hsvColors.get(3));
                        } else {
                            // invert hue of c2
                            float[] hsv = hsvColors.get(1);
                            hsv[0] = (hsv[0] + 180f) % 360f;
                            c4 = Color.HSVToColor(hsv);
                        }
                    } else if (size == 2) {
                        // we have two colors, make diagonal
                        c3 = Color.HSVToColor(hsvColors.get(1));
                        c2 = lerpInLinearSpace(0.5f, c1, c3);
                        float[] hsv = new float[3];
                        Color.RGBToHSV(c2, hsv);
                        c4 = adjustColor(hsv, false, true, false, item.isEnchanted());
                    } else {
                        // we have one color...
                        float[] hsv = hsvColors.get(0);
                        boolean mag = item.isEnchanted();
                        c2 = adjustColor(hsv, false, true, false, mag);
                        c3 = adjustColor(hsv, true, true, true, mag);
                        c4 = adjustColor(hsv, true, false, true, mag);
                    }
                    mWorkStrokeColor[0] = (sStrokeColor[0] & 0xFF000000) | c1;
                    mWorkStrokeColor[1] = (sStrokeColor[1] & 0xFF000000) | c2;
                    mWorkStrokeColor[2] = (sStrokeColor[2] & 0xFF000000) | c3;
                    mWorkStrokeColor[3] = (sStrokeColor[3] & 0xFF000000) | c4;
                    mUseSpectrum = false;
                    return;
                }
            }
        }
        System.arraycopy(sStrokeColor, 0, mWorkStrokeColor, 0, 4);
        mUseSpectrum = false;
    }

    static int adjustColor(float[] hsv, boolean hue, boolean sat, boolean val, boolean magnified) {
        float h = hsv[0];
        float s = hsv[1];
        float v = hsv[2];
        if (hue) {
            if (h >= 60f && h <= 240f) {
                h += magnified ? 27f : 15f;
            } else {
                h -= magnified ? 18f : 10f;
            }
            h = (h + 360f) % 360f;
        }
        if (sat) {
            if (s < 0.6f) {
                s += magnified ? 0.18f : 0.12f;
            } else {
                s -= magnified ? 0.12f : 0.06f;
            }
        }
        if (val) {
            if (v < 0.6f) {
                v += magnified ? 0.12f : 0.08f;
            } else {
                v -= magnified ? 0.08f : 0.04f;
            }
        }
        return Color.HSVToColor(h, s, v);
    }

    void updateBorderColor() {
        float p = (mCurrTimeMillis % sBorderColorCycle) / (float) sBorderColorCycle;
        if (mLayoutRTL) {
            int pos = (int) ((mCurrTimeMillis / sBorderColorCycle) & 3);
            for (int i = 0; i < 4; i++) {
                mActiveStrokeColor[i] = lerpInLinearSpace(p,
                        mWorkStrokeColor[(i + pos) & 3],
                        mWorkStrokeColor[(i + pos + 1) & 3]);
            }
        } else {
            int pos = 3 - (int) ((mCurrTimeMillis / sBorderColorCycle) & 3);
            for (int i = 0; i < 4; i++) {
                mActiveStrokeColor[i] = lerpInLinearSpace(p,
                        mWorkStrokeColor[(i + pos) & 3],
                        mWorkStrokeColor[(i + pos + 3) & 3]);
            }
        }
    }

    static int lerpInLinearSpace(float fraction, int startValue, int endValue) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            float s = ((startValue >> (i << 3)) & 0xff) / 255.0f;
            float t = ((endValue >> (i << 3)) & 0xff) / 255.0f;
            float v = MathUtil.lerp(s, t, fraction);
            result |= Math.round(v * 255.0f) << (i << 3);
        }
        return result;
    }

    // return style if the line contains a single style, or it's too long to determine;
    // return null if the line is empty, or multi style
    @Nullable
    static Style findSingleStyle(@Nonnull ClientTextTooltip line) {
        FormattedCharSequence text = ((AccessClientTextTooltip) line).getText();
        class StyleFinder implements FormattedCharSink {
            Style style = null;
            int count = 0;

            @Override
            public boolean accept(int index, @Nonnull Style style, int codePoint) {
                if (this.style == null) {
                    this.style = style;
                } else if (!CharacterStyle.equalsForTextLayout(this.style, style)) {
                    this.style = null;
                    return false;
                }
                return ++count <= 50;
            }
        }
        var finder = new StyleFinder();
        text.accept(finder);
        return finder.style;
    }

    /*public static void drawTooltip(@Nonnull GLCanvas canvas, @Nonnull List<? extends FormattedText> texts,
                                   @Nonnull Font font, @Nonnull ItemStack stack, @Nonnull PoseStack poseStack,
                                   float mouseX, float mouseY, float preciseMouseX, float preciseMouseY,
                                   int maxTextWidth, float screenWidth, float screenHeight,
                                   int framebufferWidth, int framebufferHeight) {
        sDraw = true;
        final float partialX = (preciseMouseX - (int) preciseMouseX);
        final float partialY = (preciseMouseY - (int) preciseMouseY);

        // matrix transformation for x and y params, compatibility to MineColonies
        float tooltipX = mouseX + TOOLTIP_SPACE + partialX;
        float tooltipY = mouseY - TOOLTIP_SPACE + partialY;
        *//*if (mouseX != (int) mouseX || mouseY != (int) mouseY) {
            // ignore partial pixels
            tooltipX += mouseX - (int) mouseX;
            tooltipY += mouseY - (int) mouseY;
        }*//*
        int tooltipWidth = 0;
        int tooltipHeight = V_BORDER * 2;

        for (FormattedText text : texts) {
            tooltipWidth = Math.max(tooltipWidth, font.width(text));
        }

        boolean needWrap = false;
        if (tooltipX + tooltipWidth + H_BORDER + 1 > screenWidth) {
            tooltipX = mouseX - TOOLTIP_SPACE - H_BORDER - 1 - tooltipWidth + partialX;
            if (tooltipX < H_BORDER + 1) {
                if (mouseX > screenWidth / 2) {
                    tooltipWidth = (int) (mouseX - TOOLTIP_SPACE - H_BORDER * 2 - 2);
                } else {
                    tooltipWidth = (int) (screenWidth - TOOLTIP_SPACE - H_BORDER - 1 - mouseX);
                }
                needWrap = true;
            }
        }

        if (maxTextWidth > 0 && tooltipWidth > maxTextWidth) {
            tooltipWidth = maxTextWidth;
            needWrap = true;
        }

        int titleLinesCount = 1;
        if (needWrap) {
            int w = 0;
            final List<FormattedText> temp = sTempTexts;
            for (int i = 0; i < texts.size(); i++) {
                List<FormattedText> wrapped = font.getSplitter().splitLines(texts.get(i), tooltipWidth, Style.EMPTY);
                if (i == 0) {
                    titleLinesCount = wrapped.size();
                }
                for (FormattedText text : wrapped) {
                    w = Math.max(w, font.width(text));
                    temp.add(text);
                }
            }
            tooltipWidth = w;
            texts = temp;

            if (mouseX > screenWidth / 2) {
                tooltipX = mouseX - TOOLTIP_SPACE - H_BORDER - 1 - tooltipWidth + partialX;
            } else {
                tooltipX = mouseX + TOOLTIP_SPACE + partialX;
            }
        }

        if (texts.size() > 1) {
            tooltipHeight += (texts.size() - 1) * LINE_HEIGHT;
            if (texts.size() > titleLinesCount) {
                tooltipHeight += TITLE_GAP;
            }
        }

        tooltipY = MathUtil.clamp(tooltipY, V_BORDER + 1, screenHeight - tooltipHeight - V_BORDER - 1);

        // smoothing scaled pixels, keep the same partial value as mouse position since tooltipWidth and height are int
        final int tooltipLeft = (int) tooltipX;
        final int tooltipTop = (int) tooltipY;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        poseStack.pushPose();
        poseStack.translate(0, 0, 400); // because of the order of draw calls, we actually don't need z-shifting
        final Matrix4f mat = poseStack.last().pose();

        final int oldVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);

        // give some points to the original framebuffer, not gui scaled
        canvas.reset(framebufferWidth, framebufferHeight);

        // swap matrices
        RenderSystem.getProjectionMatrix().store(sMatBuf.rewind());
        sMyMat.set(sMatBuf.rewind());
        canvas.setProjection(sMyMat);

        canvas.save();
        RenderSystem.getModelViewMatrix().store(sMatBuf.rewind());
        sMyMat.set(sMatBuf.rewind());
        canvas.multiply(sMyMat);

        mat.store(sMatBuf.rewind()); // Sodium check the remaining
        sMyMat.set(sMatBuf.rewind());
        //myMat.translate(0, 0, -2000);
        canvas.multiply(sMyMat);

        Paint paint = Paint.take();

        paint.setSmoothRadius(0.5f);

        for (int i = 0; i < 4; i++) {
            int color = sFillColor[i];
            int alpha = (int) ((color >>> 24) * sAlpha);
            sColor[i] = (color & 0xFFFFFF) | (alpha << 24);
        }
        paint.setColors(sColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(tooltipX - H_BORDER, tooltipY - V_BORDER,
                tooltipX + tooltipWidth + H_BORDER,
                tooltipY + tooltipHeight + V_BORDER, 3, paint);

        for (int i = 0; i < 4; i++) {
            int color = sStrokeColor[i];
            int alpha = (int) ((color >>> 24) * sAlpha);
            sColor[i] = (color & 0xFFFFFF) | (alpha << 24);
        }
        paint.setColors(sColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        canvas.drawRoundRect(tooltipX - H_BORDER, tooltipY - V_BORDER,
                tooltipX + tooltipWidth + H_BORDER,
                tooltipY + tooltipHeight + V_BORDER, 3, paint);
        *//*canvas.drawRoundedFrameT1(tooltipX - H_BORDER, tooltipY - V_BORDER,
                tooltipX + tooltipWidth + H_BORDER, tooltipY + tooltipHeight + V_BORDER, 3);*//*

        canvas.restore();
        canvas.draw(null);

        glBindVertexArray(oldVertexArray);
        glUseProgram(oldProgram);

        final MultiBufferSource.BufferSource source =
                MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final int color = (Math.max((int) (sAlpha * 255), 1) << 24) | 0xFFFFFF;
        for (int i = 0; i < texts.size(); i++) {
            FormattedText text = texts.get(i);
            if (text != null)
                ModernFontRenderer.drawText(text, tooltipX, tooltipY, color, true, mat, source,
                        false, 0, LightTexture.FULL_BRIGHT);
            if (i + 1 == titleLinesCount) {
                tooltipY += TITLE_GAP;
            }
            tooltipY += LINE_HEIGHT;
        }
        source.endBatch();

        // because of the order of draw calls, we actually don't need z-shifting
        poseStack.translate(partialX, partialY, -400);
        // compatibility with Forge mods, like Quark
        *//*MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostText(stack, texts, poseStack, tooltipLeft,
        tooltipTop,
                font, tooltipWidth, tooltipHeight));*//*
        poseStack.popPose();

        RenderSystem.enableDepthTest();
        sTempTexts.clear();
    }*/

    int chooseBorderColor(int corner) {
        if (sBorderColorCycle > 0) {
            return mActiveStrokeColor[corner];
        } else {
            return mWorkStrokeColor[corner];
        }
    }

    void chooseBorderColor(int corner, AbstractUniform uniform) {
        int color = chooseBorderColor(corner);
        int a = (color >>> 24);
        int r = ((color >> 16) & 0xff);
        int g = ((color >> 8) & 0xff);
        int b = (color & 0xff);
        uniform.set(r / 255f, g / 255f, b / 255f, a / 255f);
    }

    public void drawTooltip(@Nonnull ItemStack itemStack, @Nonnull GuiGraphics gr,
                            @Nonnull List<ClientTooltipComponent> list, int mouseX, int mouseY,
                            @Nonnull Font font, int screenWidth, int screenHeight,
                            float partialX, float partialY, @Nullable ClientTooltipPositioner positioner,
                            @Nullable ResourceLocation tooltipStyle) {
        mDraw = true;

        if (itemStack != mLastSeenItem) {
            mLastSeenItem = itemStack;
            computeWorkingColor(itemStack);
        }

        int tooltipWidth;
        int tooltipHeight;
        boolean titleGap = false;
        int titleBreakHeight = 0;
        if (list.size() == 1) {
            ClientTooltipComponent component = list.get(0);
            tooltipWidth = component.getWidth(font);
            tooltipHeight = component.getHeight(font) - TITLE_GAP;
        } else {
            tooltipWidth = 0;
            tooltipHeight = 0;
            Style singleStyle = null;
            for (int i = 0; i < list.size(); i++) {
                ClientTooltipComponent component = list.get(i);
                tooltipWidth = Math.max(tooltipWidth, component.getWidth(font));
                int componentHeight = component.getHeight(font);
                tooltipHeight += componentHeight;
                if (i == 0) {
                    titleBreakHeight = componentHeight;
                    if (component instanceof ClientTextTooltip) {
                        if (!itemStack.isEmpty()) {
                            // item stack provided, always add title gap
                            titleGap = true;
                        } else {
                            singleStyle = findSingleStyle((ClientTextTooltip) component);
                            if (singleStyle == null) {
                                // multi-style, add title gap
                                titleGap = true;
                            }
                        }
                    }
                } else if (i <= 2 && !titleGap && component instanceof ClientTextTooltip) {
                    // check first three lines to see if title gap is needed
                    final Style lineStyle = findSingleStyle((ClientTextTooltip) component);
                    if (lineStyle == null) {
                        // multi-style, add title gap
                        titleGap = true;
                    } else if (singleStyle == null) {
                        singleStyle = lineStyle;
                    } else if (!CharacterStyle.equalsForTextLayout(singleStyle, lineStyle)) {
                        // multi-style, add title gap
                        titleGap = true;
                    }
                }
            }
            if (!titleGap) {
                tooltipHeight -= TITLE_GAP;
            }
        }

        float tooltipX;
        float tooltipY;
        final float maxScroll;
        if (positioner != null) {
            var pos = positioner.positionTooltip(screenWidth, screenHeight,
                    mouseX, mouseY,
                    tooltipWidth, tooltipHeight);
            tooltipX = pos.x();
            tooltipY = pos.y();
            maxScroll = 0;
        } else {
            if (mLayoutRTL) {
                tooltipX = mouseX + TOOLTIP_SPACE + partialX - 24 - tooltipWidth;
                if (tooltipX - partialX < 4) {
                    tooltipX += 24 + tooltipWidth;
                }
            } else {
                tooltipX = mouseX + TOOLTIP_SPACE + partialX;
                if (tooltipX - partialX + tooltipWidth + 4 > screenWidth) {
                    tooltipX -= 28 + tooltipWidth;
                }
            }
            partialX = (tooltipX - (int) tooltipX);

            tooltipY = mouseY - TOOLTIP_SPACE + partialY;
            if (tooltipY + tooltipHeight + 6 > screenHeight) {
                tooltipY = screenHeight - tooltipHeight - 6;
            }
            if (tooltipY < 6) {
                tooltipY = 6;
            }
            partialY = (tooltipY - (int) tooltipY);

            maxScroll = 6 + tooltipHeight + 6 - screenHeight;
        }

        if (maxScroll > 0) {
            mScroller.setMaxScroll(maxScroll);
            if (mPendingArrowMove != 0) {
                if (mMarqueeDir != 0) {
                    mScroller.scrollTo(mScroll);
                    mScroller.abortAnimation();
                }
                mScroller.scrollBy(mPendingArrowMove * sArrowScrollFactor);
                mPendingArrowMove = 0;
            }
            mScroller.update(MuiModApi.getElapsedTime());

            mScroll = MathUtil.clamp(mScroll, 0, maxScroll);

            if (mMarqueeDir != 0 && mCurrTimeMillis - mMarqueeEndMillis >= MARQUEE_DELAY_MILLIS) {
                float t = MathUtil.clamp(0.5f * tooltipWidth / screenWidth, 0.0f, 0.5f);
                float baseMultiplier = (1.5f - t) * 0.01f;
                mScroll += mMarqueeDir * mCurrDeltaMillis *
                        (baseMultiplier + baseMultiplier * Math.min(maxScroll / screenHeight, 1.5f));
                if (mMarqueeDir > 0) {
                    if (mScroll >= maxScroll) {
                        mMarqueeDir = -1;
                        mMarqueeEndMillis = mCurrTimeMillis;
                    }
                } else {
                    if (mScroll <= 0) {
                        mMarqueeDir = 1;
                        mMarqueeEndMillis = mCurrTimeMillis;
                    }
                }
            }
        } else {
            mScroll = 0;
            mPendingArrowMove = 0;
        }

        if (sBorderColorCycle > 0) {
            updateBorderColor();
        }

        gr.flush();
        gr.pose().pushPose();
        // because of the order of draw calls, we actually don't need z-shifting
        gr.pose().translate(0, -mScroll, 400);
        final Matrix4f pose = gr.pose().last().pose();

        if (tooltipStyle == null) {
            // we should disable depth test, because texts may be translucent
            // for compatibility reasons, we keep this enabled, and it doesn't seem to be a big problem
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (sRoundedShapes) {
                drawRoundedBackground(gr, pose,
                        tooltipX, tooltipY, tooltipWidth, tooltipHeight,
                        titleGap, titleBreakHeight);
            } else {
                drawVanillaBackground(gr, pose,
                        tooltipX, tooltipY, tooltipWidth, tooltipHeight,
                        titleGap, titleBreakHeight);
            }
        }

        final int drawX = (int) tooltipX;
        int drawY = (int) tooltipY;

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        final MultiBufferSource.BufferSource source = ((AccessGuiGraphics) gr).getBufferSource();
        // With rounded borders, we create a new matrix and do not perform matrix * vector
        // on the CPU side. There are floating-point errors, and we found that this can cause
        // text to be discarded by LEqual depth test on some GPUs, so lift it up by 0.1.
        gr.pose().translate(partialX, partialY, tooltipStyle == null && sRoundedShapes ? 0.1f : 0);
        if (tooltipStyle != null) {
            TooltipRenderUtil.renderTooltipBackground(gr, drawX, drawY,
                    tooltipWidth, tooltipHeight, 0, tooltipStyle);
        }
        for (int i = 0; i < list.size(); i++) {
            ClientTooltipComponent component = list.get(i);
            if (titleGap && i == 0 && sCenterTitle) {
                component.renderText(font, drawX + (tooltipWidth - component.getWidth(font)) / 2, drawY, pose, source);
            } else if (mLayoutRTL) {
                component.renderText(font, drawX + tooltipWidth - component.getWidth(font), drawY, pose, source);
            } else {
                component.renderText(font, drawX, drawY, pose, source);
            }
            if (titleGap && i == 0) {
                drawY += TITLE_GAP;
            }
            drawY += component.getHeight(font);
        }
        gr.flush();

        drawY = (int) tooltipY;

        for (int i = 0; i < list.size(); i++) {
            ClientTooltipComponent component = list.get(i);
            if (mLayoutRTL) {
                component.renderImage(font, drawX + tooltipWidth - component.getWidth(font), drawY, tooltipWidth, tooltipHeight, gr);
            } else {
                component.renderImage(font, drawX, drawY, tooltipWidth, tooltipHeight, gr);
            }
            if (titleGap && i == 0) {
                drawY += TITLE_GAP;
            }
            drawY += component.getHeight(font);
        }
        gr.pose().popPose();
    }

    private void drawRoundedBackground(@Nonnull GuiGraphics gr, Matrix4f pose,
                                       float tooltipX, float tooltipY,
                                       int tooltipWidth, int tooltipHeight,
                                       boolean titleGap, int titleBreakHeight) {
        float halfWidth = tooltipWidth / 2f;
        float halfHeight = tooltipHeight / 2f;
        float centerX = tooltipX + halfWidth;
        float centerY = tooltipY + halfHeight;
        float sizeX = halfWidth + H_BORDER;
        float sizeY = halfHeight + V_BORDER;
        float shadowRadius = Math.max(sShadowRadius, 0.00001f);

        CompiledShaderProgram shader = RenderSystem.setShader(GuiRenderType.SHADER_TOOLTIP);
        if (shader == null) {
            return;
        }
        shader.safeGetUniform("u_PushData0")
                .set(sizeX, sizeY, sCornerRadius, sBorderWidth / 2f);
        float rainbowOffset = 0;
        if (mUseSpectrum) {
            rainbowOffset = 1;
            if (sBorderColorCycle > 0) {
                long overallCycle = sBorderColorCycle * 4L;
                rainbowOffset += (float) (mCurrTimeMillis % overallCycle) / overallCycle;
            }
            if (!mLayoutRTL) {
                rainbowOffset = -rainbowOffset;
            }
        }
        shader.safeGetUniform("u_PushData1")
                .set(sShadowAlpha, 1.25f / shadowRadius, (sFillColor[0] >>> 24) / 255f, rainbowOffset);
        if (rainbowOffset == 0) {
            chooseBorderColor(0, shader.safeGetUniform("u_PushData2"));
            chooseBorderColor(1, shader.safeGetUniform("u_PushData3"));
            chooseBorderColor(3, shader.safeGetUniform("u_PushData4"));
            chooseBorderColor(2, shader.safeGetUniform("u_PushData5"));
        }

        var buffer = ((AccessGuiGraphics) gr).getBufferSource().getBuffer(GuiRenderType.tooltip());

        // we expect local coordinates, concat pose with model view
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().mul(pose);
        RenderSystem.getModelViewStack().translate(centerX, centerY, 0);
        // estimate the draw bounds, half stroke width + 0.5 AA bloat + shadow spread
        float extent = sBorderWidth / 2f + 0.5f + shadowRadius * 1.2f;
        float extentX = sizeX + extent;
        float extentY = sizeY + extent;
        buffer.addVertex(extentX, extentY, 0);
        buffer.addVertex(extentX, -extentY, 0);
        buffer.addVertex(-extentX, -extentY, 0);
        buffer.addVertex(-extentX, extentY, 0);

        gr.flush();
        RenderSystem.getModelViewStack().popMatrix();

        if (titleGap && sTitleBreak) {
            fillGrad(gr, pose,
                    tooltipX, tooltipY + titleBreakHeight - 0.5f,
                    tooltipX + tooltipWidth, tooltipY + titleBreakHeight + 0.5f,
                    0.08f, // lift it up by 0.08
                    0xE0C8C8C8, 0xE0C8C8C8, 0xE0C8C8C8, 0xE0C8C8C8);
        }
    }

    private void drawVanillaBackground(@Nonnull GuiGraphics gr, Matrix4f pose,
                                       float tooltipX, float tooltipY,
                                       int tooltipWidth, int tooltipHeight,
                                       boolean titleGap, int titleBreakHeight) {
        float left = tooltipX - H_BORDER;
        float top = tooltipY - V_BORDER;
        float right = tooltipX + tooltipWidth + H_BORDER;
        float bottom = tooltipY + tooltipHeight + V_BORDER;

        // top
        fillGrad(gr, pose, left, top - 1, right, top, 0,
                sFillColor[0], sFillColor[1], sFillColor[1], sFillColor[0]);
        // bottom
        fillGrad(gr, pose, left, bottom, right, bottom + 1, 0,
                sFillColor[3], sFillColor[2], sFillColor[2], sFillColor[3]);
        // center
        fillGrad(gr, pose, left, top, right, bottom, 0,
                sFillColor[0], sFillColor[1], sFillColor[2], sFillColor[3]);
        // left
        fillGrad(gr, pose, left - 1, top, left, bottom, 0,
                sFillColor[0], sFillColor[0], sFillColor[3], sFillColor[3]);
        // right
        fillGrad(gr, pose, right, top, right + 1, bottom, 0,
                sFillColor[1], sFillColor[1], sFillColor[2], sFillColor[2]);

        if (titleGap && sTitleBreak) {
            fillGrad(gr, pose,
                    tooltipX, tooltipY + titleBreakHeight - 0.5f,
                    tooltipX + tooltipWidth, tooltipY + titleBreakHeight + 0.5f, 0,
                    0xE0C8C8C8, 0xE0C8C8C8, 0xE0C8C8C8, 0xE0C8C8C8);
        }

        // top
        fillGrad(gr, pose,
                left, top, right, top + 1, 0,
                chooseBorderColor(0), chooseBorderColor(1),
                chooseBorderColor(1), chooseBorderColor(0));
        // right
        fillGrad(gr, pose,
                right - 1, top, right, bottom, 0,
                chooseBorderColor(1), chooseBorderColor(1),
                chooseBorderColor(2), chooseBorderColor(2));
        // bottom
        fillGrad(gr, pose,
                left, bottom - 1, right, bottom, 0,
                chooseBorderColor(3), chooseBorderColor(2),
                chooseBorderColor(2), chooseBorderColor(3));
        // left
        fillGrad(gr, pose, left, top, left + 1, bottom, 0,
                chooseBorderColor(0), chooseBorderColor(0),
                chooseBorderColor(3), chooseBorderColor(3));
    }

    private static void fillGrad(GuiGraphics gr, Matrix4f pose,
                                 float left, float top, float right, float bottom, float z,
                                 int colorUL, int colorUR, int colorLR, int colorLL) {
        var buffer = ((AccessGuiGraphics) gr).getBufferSource().getBuffer(RenderType.gui());

        // CCW
        int color = colorLR;
        buffer.addVertex(pose, right, bottom, z)
                .setColor(color);

        color = colorUR;
        buffer.addVertex(pose, right, top, z)
                .setColor(color);

        color = colorUL;
        buffer.addVertex(pose, left, top, z)
                .setColor(color);

        color = colorLL;
        buffer.addVertex(pose, left, bottom, z)
                .setColor(color);

        gr.flush();
    }
}
