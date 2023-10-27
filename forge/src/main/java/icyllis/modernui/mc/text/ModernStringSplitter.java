/*
 * Modern UI.
 * Copyright (C) 2019-2021 BloCamLimb. All rights reserved.
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

package icyllis.modernui.mc.text;

import icyllis.modernui.ModernUI;
import icyllis.modernui.graphics.text.GraphemeBreak;
import icyllis.modernui.graphics.text.LineBreaker;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import org.apache.commons.lang3.mutable.MutableObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Provides text measurement, truncation, Unicode grapheme cluster breaking,
 * Unicode line breaking and so on.
 */
public final class ModernStringSplitter extends StringSplitter {

    private final TextLayoutEngine mEngine;

    //private final MutableFloat v = new MutableFloat();

    /**
     * Constructor
     */
    public ModernStringSplitter(TextLayoutEngine engine,
                                StringSplitter.WidthProvider widthProvider) {
        super(widthProvider);
        mEngine = engine;
    }

    // move a grapheme cluster at least
    public static int offsetByGrapheme(String value, int cursor, int dir) {
        int op;
        if (dir < 0) {
            op = GraphemeBreak.BEFORE;
        } else if (dir == 0) {
            op = GraphemeBreak.AT_OR_BEFORE;
        } else {
            op = GraphemeBreak.AFTER;
        }
        int offset = Util.offsetByCodepoints(value, cursor, dir);
        cursor = GraphemeBreak.getTextRunCursor(
                value, ModernUI.getSelectedLocale(),
                0, value.length(), cursor, op
        );
        if (dir > 0) {
            return Math.max(offset, cursor);
        } else {
            return Math.min(offset, cursor);
        }
    }

    /**
     * Modern Text Engine
     */
    public float stringWidth(@Nullable String text) {
        return measureText(text);
    }

    /**
     * Modern Text Engine
     */
    public float stringWidth(@Nonnull FormattedText text) {
        return measureText(text);
    }

    /**
     * Modern Text Engine
     */
    public float stringWidth(@Nonnull FormattedCharSequence text) {
        return measureText(text);
    }

    /**
     * Measure the text and get the text advance.
     * The text can contain formatting codes.
     *
     * @param text the text to measure
     * @return text advance in GUI scaled pixels
     */
    public float measureText(@Nullable String text) {
        if (text == null) {
            return 0;
        }
        return mEngine.lookupVanillaLayout(text).getTotalAdvance();
    }

    /**
     * Measure the text and get the text advance.
     * The text can contain formatting codes.
     *
     * @param text the text to measure
     * @return text advance in GUI scaled pixels
     */
    public float measureText(@Nonnull FormattedText text) {
        return mEngine.lookupFormattedLayout(text).getTotalAdvance();
    }

    /**
     * Measure the text and get the text advance.
     *
     * @param text the text to measure
     * @return text advance in GUI scaled pixels
     */
    public float measureText(@Nonnull FormattedCharSequence text) {
        return mEngine.lookupFormattedLayout(text).getTotalAdvance();
    }

    /**
     * Modern Text Engine
     */
    public int plainIndexAtWidth(@Nonnull String text, int width, @Nonnull Style style) {
        return indexByWidth(text, (float) width, style);
    }

    /**
     * Modern Text Engine
     */
    @Nonnull
    public String plainHeadByWidth(@Nonnull String text, int width, @Nonnull Style style) {
        return headByWidth(text, (float) width, style);
    }

    /**
     * Modern Text Engine
     */
    @Nonnull
    public String plainTailByWidth(@Nonnull String text, int width, @Nonnull Style style) {
        return tailByWidth(text, (float) width, style);
    }

    /**
     * Modern Text Engine
     */
    public int formattedIndexByWidth(@Nonnull String text, int width, @Nonnull Style style) {
        return indexByWidth(text, (float) width, style);
    }

    /**
     * Modern Text Engine
     */
    @Nullable
    public Style componentStyleAtWidth(@Nonnull FormattedText text, int width) {
        return styleAtWidth(text, (float) width);
    }

    /**
     * Modern Text Engine
     */
    @Nullable
    public Style componentStyleAtWidth(@Nonnull FormattedCharSequence text, int width) {
        return styleAtWidth(text, (float) width);
    }

    /**
     * Modern Text Engine
     */
    @Nonnull
    public String formattedHeadByWidth(@Nonnull String text, int width, @Nonnull Style style) {
        return headByWidth(text, (float) width, style);
    }

    /**
     * Modern Text Engine
     */
    @Nonnull
    public FormattedText headByWidth(@Nonnull FormattedText text, int width, @Nonnull Style style) {
        // Handle Enchantment Table
        /*if (text instanceof Component component &&
                component.getSiblings().isEmpty() &&
                component.getStyle().getFont().equals(Minecraft.ALT_FONT) &&
                component.getContents() instanceof LiteralContents literal) {
            final MutableFloat maxWidth = new MutableFloat(width);
            final MutableInt position = new MutableInt();
            if (!StringDecomposer.iterate(literal.text(), component.getStyle(),
                    (index, sty, codePoint) -> {
                        if (maxWidth.addAndGet(-widthProvider.getWidth(codePoint, sty)) >= 0) {
                            position.setValue(index + Character.charCount(codePoint));
                            return true;
                        } else {
                            return false;
                        }
                    })) {
                String substring = literal.text().substring(0, position.intValue());
                if (!substring.isEmpty()) {
                    return FormattedText.of(substring, component.getStyle());
                }
            } else {
                if (!literal.text().isEmpty()) {
                    return FormattedText.of(literal.text(), component.getStyle());
                }
            }
            return FormattedText.EMPTY;
        }*/
        return headByWidth(text, (float) width, style);
    }

    /**
     * Measure the text and perform Unicode GCB (grapheme cluster break).
     * Returns the maximum index that the accumulated width not exceeds the width.
     * <p>
     * If forwards=false, returns the minimum index from the end instead (but still
     * indexing from the start).
     *
     * @param layout   the measured text to break
     * @param forwards the leading position
     * @param width    the max width in GUI scaled pixels
     * @return break index (without formatting codes)
     */
    public static int breakText(@Nonnull TextLayout layout, boolean forwards, float width) {
        final int limit = layout.getCharCount();
        if (forwards) {
            // TruncateAt.END
            int i = 0;
            while (i < limit) {
                width -= layout.getAdvances()[i];
                if (width < 0.0f) break;
                i++;
            }
            while (i > 0 && layout.getTextBuf()[i - 1] == ' ') i--;
            return i;
        } else {
            // TruncateAt.START
            int i = limit - 1;
            while (i >= 0) {
                width -= layout.getAdvances()[i];
                if (width < 0.0f) break;
                i--;
            }
            while (i < limit - 1 && (layout.getTextBuf()[i + 1] == ' ' || layout.getAdvances()[i + 1] == 0.0f)) {
                i++;
            }
            return i + 1;
        }
    }

    /**
     * Measure the text and perform Unicode GCB (grapheme cluster break).
     * Returns the maximum index that the accumulated width not exceeds the width.
     * <p>
     * If forwards=false, returns the minimum index from the end instead (but still
     * indexing from the start).
     * <p>
     * The text can contain formatting codes.
     *
     * @param text     the text to break
     * @param width    the max width in GUI scaled pixels
     * @param style    the base style for the text
     * @param forwards the leading position
     * @return break index
     */
    public int breakText(@Nonnull String text, float width, @Nonnull Style style, boolean forwards) {
        if (text.isEmpty() || width < 0) {
            return 0;
        }

        final TextLayout layout = mEngine.lookupVanillaLayout(text, style,
                TextLayoutEngine.COMPUTE_ADVANCES);
        if (width >= layout.getTotalAdvance()) {
            return forwards ? text.length() : 0;
        }

        int breakIndex = breakText(layout, forwards, width);

        // We assume that formatting codes belong to the next grapheme cluster in logical order
        final int length = text.length();
        for (int j = 0; j < length; j++) {
            if (j == breakIndex) {
                break;
            }
            if (text.charAt(j) == ChatFormatting.PREFIX_CODE) {
                j++;
                breakIndex += 2;
            }
        }
        return breakIndex;
    }

    /**
     * Get trimmed length / size to width.
     * <p>
     * Return the number of characters in a text that will completely fit inside
     * the specified width when rendered.
     *
     * @param text  the text to break
     * @param width the max width in GUI scaled pixels
     * @param style the base style for the text
     * @return the length of the text when it is trimmed to be at most /
     * the number of characters from text that will fit inside width
     */
    public int indexByWidth(@Nonnull String text, float width, @Nonnull Style style) {
        return breakText(text, width, style, true);
    }

    /**
     * Break a text forwards so that it fits in the specified width when rendered.
     * The text can contain formatting codes.
     *
     * @param text  the text to break
     * @param width the max width in GUI scaled pixels
     * @param style the base style for the text
     * @return the trimmed text
     */
    @Nonnull
    public String headByWidth(@Nonnull String text, float width, @Nonnull Style style) {
        return text.substring(0, indexByWidth(text, width, style));
    }

    /**
     * Break a text backwards so that it fits in the specified width when rendered.
     * The text can contain formatting codes.
     *
     * @param text  the text to break
     * @param width the max width in GUI scaled pixels
     * @param style the base style for the text
     * @return the trimmed text
     */
    @Nonnull
    public String tailByWidth(@Nonnull String text, float width, @Nonnull Style style) {
        return text.substring(breakText(text, width, style, false));
    }

    /**
     * Break a text forwards to find the text style at the given width to handle
     * its click event or hover event.
     *
     * @param text  the text to break
     * @param width the max width in GUI scaled pixels
     * @return the text style or null
     */
    @Nullable
    public Style styleAtWidth(@Nonnull FormattedText text, float width) {
        if (text == CommonComponents.EMPTY || text == FormattedText.EMPTY || width < 0) {
            return null;
        }

        final TextLayout layout = mEngine.lookupFormattedLayout(text,
                Style.EMPTY, TextLayoutEngine.COMPUTE_ADVANCES);
        if (width >= layout.getTotalAdvance()) {
            return null;
        }

        final int breakIndex = breakText(layout, true, width);

        return text.visit(new FormattedText.StyledContentConsumer<Style>() {
            private int mStripIndex;

            @Nonnull
            @Override
            public Optional<Style> accept(@Nonnull Style style, @Nonnull String string) {
                final int length = string.length();
                for (int i = 0; i < length; i++) {
                    if (string.charAt(i) == ChatFormatting.PREFIX_CODE) {
                        i++;
                        continue;
                    }
                    if (++mStripIndex > breakIndex) {
                        return Optional.of(style); // stop iteration
                    }
                }
                return Optional.empty(); // continue
            }
        }, Style.EMPTY).orElse(null); // null should not happen
    }

    /**
     * Break a text forwards to find the text style at the given width to handle
     * its click event or hover event.
     *
     * @param text  the text to break
     * @param width the max width in GUI scaled pixels
     * @return the text style or null
     */
    @Nullable
    public Style styleAtWidth(@Nonnull FormattedCharSequence text, float width) {
        if (text == FormattedCharSequence.EMPTY || width < 0) {
            return null;
        }

        if (text instanceof FormattedTextWrapper) {
            // This is more accurate that do not shift control codes
            return styleAtWidth(((FormattedTextWrapper) text).mText, width);
        }

        final TextLayout layout = mEngine.lookupFormattedLayout(text,
                TextLayoutEngine.COMPUTE_ADVANCES);
        if (width >= layout.getTotalAdvance()) {
            return null;
        }

        final int breakIndex = breakText(layout, true, width);

        final MutableObject<Style> result = new MutableObject<>();
        text.accept(new FormattedCharSink() {
            private int mStripIndex;

            @Override
            public boolean accept(int index, @Nonnull Style style, int codePoint) {
                if ((mStripIndex += Character.charCount(codePoint)) > breakIndex) {
                    result.setValue(style);
                    return false; // stop iteration
                }
                return true; // continue
            }
        });
        return result.getValue();
    }

    /**
     * Break a text backwards so that it fits in the specified width when rendered.
     * The text can contain formatting codes.
     *
     * @param text  the text to break
     * @param width the max width in GUI scaled pixels
     * @param style the base style
     * @return the trimmed text or the original text
     */
    @Nonnull
    public FormattedText headByWidth(@Nonnull FormattedText text, float width, @Nonnull Style style) {
        if (text == CommonComponents.EMPTY || text == FormattedText.EMPTY || width < 0) {
            return FormattedText.EMPTY;
        }

        final TextLayout layout = mEngine.lookupFormattedLayout(text, style,
                TextLayoutEngine.COMPUTE_ADVANCES);
        if (width >= layout.getTotalAdvance()) {
            return text;
        }

        final int breakIndex = breakText(layout, true, width);

        return text.visit(new FormattedText.StyledContentConsumer<FormattedText>() {
            private final ComponentCollector mCollector = new ComponentCollector();
            private int mSegmentIndex;

            @Nonnull
            @Override
            public Optional<FormattedText> accept(@Nonnull Style sty, @Nonnull String string) {
                final int length = string.length();
                int stripIndex = 0;
                for (int i = 0; i < length; i++) {
                    if (string.charAt(i) == ChatFormatting.PREFIX_CODE) {
                        i++;
                        continue;
                    }
                    if (mSegmentIndex + ++stripIndex > breakIndex) {
                        String substring = string.substring(0, stripIndex);
                        if (!substring.isEmpty()) {
                            mCollector.append(FormattedText.of(substring, sty));
                        }
                        return Optional.of(mCollector.getResultOrEmpty()); // stop iteration
                    }
                }
                if (length > 0) {
                    mCollector.append(FormattedText.of(string, sty));
                }
                mSegmentIndex += stripIndex;
                return Optional.empty(); // continue
            }
        }, style).orElse(text); // else the original text
    }

    /**
     * Modern Text Engine
     */
    public void splitLines(@Nonnull String text, int width, @Nonnull Style style, @Deprecated boolean withEndSpace,
                           @Nonnull StringSplitter.LinePosConsumer linePos) {
        computeLineBreaks(text, (float) width, style, linePos);
    }

    /**
     * Modern Text Engine
     */
    public void splitLines(@Nonnull FormattedText text, int width, @Nonnull Style style,
                           @Nonnull BiConsumer<FormattedText, Boolean> consumer) {
        computeLineBreaks(text, (float) width, style, consumer);
    }

    private static final int NOWHERE = 0xFFFFFFFF;

    /**
     * Compute Unicode line breaking boundaries. If none, compute grapheme cluster boundaries.
     * Returns the maximum index that the accumulated width not exceeds the width.
     * <p>
     * The text can contain formatting codes.
     *
     * @param text     the text to break line
     * @param width    the width limit of the line
     * @param base     the base style
     * @param consumer accept each line result, params lineBaseStyle, startIndex (inclusive), endIndex (exclusive)
     */
    public void computeLineBreaks(@Nonnull String text, float width, @Nonnull Style base,
                                  @Nonnull StringSplitter.LinePosConsumer consumer) {
        if (text.isEmpty() || width < 0) {
            return;
        }

        final TextLayout layout = mEngine.lookupVanillaLayout(text, base,
                TextLayoutEngine.COMPUTE_ADVANCES | TextLayoutEngine.COMPUTE_LINE_BOUNDARIES);
        final char[] buf = layout.getTextBuf();
        if (width >= layout.getTotalAdvance()) {
            boolean hasLineFeed = false;
            for (int i = 0, e = layout.getCharCount(); i < e; i++) {
                if (buf[i] == '\n') {
                    hasLineFeed = true;
                    break;
                }
            }
            if (!hasLineFeed) {
                consumer.accept(base, 0, text.length());
                return;
            }
        }

        // ignore styles generated from formatting codes
        final LineProcessor lineBreaker = new LineProcessor(width);
        final int end = layout.getCharCount();

        int nextBoundaryIndex = 0;
        int paraEnd;
        for (int paraStart = 0; paraStart < end; paraStart = paraEnd) {
            paraEnd = -1;
            for (int i = paraStart; i < end; i++)
                if (buf[i] == '\n') {
                    paraEnd = i;
                    break;
                }
            if (paraEnd < 0) {
                // No LINE_FEED(U+000A) character found. Use end of the text as the paragraph
                // end.
                paraEnd = end;
            } else {
                paraEnd++;  // Includes LINE_FEED(U+000A) to the prev paragraph.
            }

            nextBoundaryIndex = lineBreaker.process(layout, buf, paraStart, paraEnd, nextBoundaryIndex);
        }

        final IntList result = lineBreaker.mBreakPoints;

        int mStripIndex = 0;

        int mBreakOffsetIndex = 0;
        int mBreakPointOffset = result.getInt(mBreakOffsetIndex++);

        Style currStyle = base;
        Style lastStyle = base;
        int lastSubPos = 0;
        for (int i = 0, e = text.length(); i < e; i++) {
            char c = text.charAt(i);
            if (c == ChatFormatting.PREFIX_CODE) {
                i++;
                ChatFormatting formatting = TextLayoutEngine.getFormattingByCode(text.charAt(i));
                if (formatting != null) {
                    currStyle = formatting == ChatFormatting.RESET ? base :
                            currStyle.applyLegacyFormat(formatting);
                }
                continue;
            }
            // End index is exclusive, so ++index not index++
            if (++mStripIndex >= mBreakPointOffset) {
                consumer.accept(lastStyle, lastSubPos, i + 1);
                lastSubPos = i + 1;
                lastStyle = currStyle;
                if (mBreakOffsetIndex >= result.size()) {
                    break;
                }
                mBreakPointOffset = result.getInt(mBreakOffsetIndex++);
            }
        }
    }

    /**
     * Compute Unicode line breaking boundaries. If none, compute grapheme cluster boundaries.
     * Returns the maximum index that the accumulated width not exceeds the width.
     * <p>
     * The text can contain formatting codes.
     *
     * @param text     the text to break line
     * @param width    the width limit of the line
     * @param base     the base style
     * @param consumer the line consumer, second boolean false meaning it's the first line of a paragraph
     */
    public void computeLineBreaks(@Nonnull FormattedText text, float width, @Nonnull Style base,
                                  @Nonnull BiConsumer<FormattedText, Boolean> consumer) {
        if (text == CommonComponents.EMPTY || text == FormattedText.EMPTY || width < 0) {
            return;
        }

        final TextLayout layout = mEngine.lookupFormattedLayout(text, base,
                TextLayoutEngine.COMPUTE_ADVANCES | TextLayoutEngine.COMPUTE_LINE_BOUNDARIES);
        final char[] buf = layout.getTextBuf();
        if (width >= layout.getTotalAdvance()) {
            boolean hasLineFeed = false;
            for (int i = 0, e = layout.getCharCount(); i < e; i++) {
                if (buf[i] == '\n') {
                    hasLineFeed = true;
                    break;
                }
            }
            if (!hasLineFeed) {
                consumer.accept(text, Boolean.FALSE);
                return;
            }
        }

        // ignore styles generated from formatting codes
        final LineProcessor lineBreaker = new LineProcessor(width);
        final int end = layout.getCharCount();

        int nextBoundaryIndex = 0;
        int paraEnd;
        for (int paraStart = 0; paraStart < end; paraStart = paraEnd) {
            paraEnd = -1;
            for (int i = paraStart; i < end; i++)
                if (buf[i] == '\n') {
                    paraEnd = i;
                    break;
                }
            if (paraEnd < 0) {
                // No LINE_FEED(U+000A) character found. Use end of the text as the paragraph
                // end.
                paraEnd = end;
            } else {
                paraEnd++;  // Includes LINE_FEED(U+000A) to the prev paragraph.
            }

            nextBoundaryIndex = lineBreaker.process(layout, buf, paraStart, paraEnd, nextBoundaryIndex);
        }

        final IntList result = lineBreaker.mBreakPoints;

        class LineBreakVisitor implements FormattedText.StyledContentConsumer<Unit> {
            private ComponentCollector mCollector = new ComponentCollector();
            private int mStripIndex = 0;

            private int mBreakOffsetIndex = 0;
            private int mBreakPointOffset = result.getInt(mBreakOffsetIndex++);

            private boolean mNonNewPara = false;

            @Nonnull
            @Override
            public Optional<Unit> accept(@Nonnull Style aStyle, @Nonnull String aText) {
                Style currStyle = aStyle;
                Style lastStyle = aStyle;
                int lastSubPos = 0;
                for (int i = 0, e = aText.length(); i < e; i++) {
                    char c = aText.charAt(i);
                    if (c == ChatFormatting.PREFIX_CODE) {
                        i++;
                        ChatFormatting formatting = TextLayoutEngine.getFormattingByCode(aText.charAt(i));
                        if (formatting != null) {
                            currStyle = formatting == ChatFormatting.RESET ? aStyle :
                                    currStyle.applyLegacyFormat(formatting);
                        }
                        continue;
                    }
                    // End index is exclusive, so ++index not index++
                    if (++mStripIndex >= mBreakPointOffset) {
                        String substring = aText.substring(lastSubPos, i + 1);
                        if (!substring.isEmpty()) {
                            mCollector.append(FormattedText.of(substring, lastStyle));
                        }
                        consumer.accept(mCollector.getResultOrEmpty(), mNonNewPara);
                        if (mBreakOffsetIndex >= result.size()) {
                            return FormattedText.STOP_ITERATION;
                        }
                        lastSubPos = i + 1;
                        lastStyle = currStyle;
                        mCollector = new ComponentCollector();
                        mBreakPointOffset = result.getInt(mBreakOffsetIndex++);
                        mNonNewPara = c != '\n';
                    }
                }
                String substring = aText.substring(lastSubPos);
                if (!substring.isEmpty()) {
                    mCollector.append(FormattedText.of(substring, lastStyle));
                }
                return Optional.empty(); // continue
            }
        }
        text.visit(new LineBreakVisitor(), base);
    }

    public static class LineProcessor {

        private float mLineWidth;
        private float mCharsAdvance;
        private final float mLineWidthLimit;

        private int mPrevBoundaryOffset;
        private float mCharsAdvanceAtPrevBoundary;

        private final IntList mBreakPoints = new IntArrayList();

        public LineProcessor(float lineWidthLimit) {
            mLineWidthLimit = lineWidthLimit;
        }

        public int process(@Nonnull TextLayout layout, @Nonnull char[] buf,
                           int start, int end, int nextBoundaryIndex) {
            mLineWidth = 0;
            mCharsAdvance = 0;
            mPrevBoundaryOffset = NOWHERE;
            mCharsAdvanceAtPrevBoundary = 0;

            final float[] advances = layout.getAdvances();
            final int[] lineBoundaries = layout.getLineBoundaries();
            int nextLineBoundary = lineBoundaries[nextBoundaryIndex++];

            for (int i = start; i < end; i++) {
                updateLineWidth(buf[i], advances[i]);

                if (i + 1 == nextLineBoundary) {
                    processLineBreak(advances, i + 1);

                    if (nextLineBoundary < end) {
                        nextLineBoundary = lineBoundaries[nextBoundaryIndex++];
                    }
                    if (nextLineBoundary > end) {
                        nextLineBoundary = end;
                    }
                }
            }

            if (getPrevLineBreakOffset() != end && mPrevBoundaryOffset != NOWHERE) {
                // The remaining words in the last line.
                breakLineAt(mPrevBoundaryOffset, 0, 0);
            }

            return nextBoundaryIndex;
        }

        private void processLineBreak(float[] advances, int offset) {
            while (mLineWidth > mLineWidthLimit) {
                int start = getPrevLineBreakOffset();
                // The word in the new line may still be too long for the line limit.
                // Try general line break first, otherwise try grapheme boundary or out of the line width
                if (!tryLineBreak() && doLineBreakWithGraphemeBounds(advances, start, offset)) {
                    return;
                }
            }

            mPrevBoundaryOffset = offset;
            mCharsAdvanceAtPrevBoundary = mCharsAdvance;
        }

        // general line break, use ICU line break iterator, not word breaker
        private boolean tryLineBreak() {
            if (mPrevBoundaryOffset == NOWHERE) {
                return false;
            }

            breakLineAt(mPrevBoundaryOffset,
                    mLineWidth - mCharsAdvanceAtPrevBoundary,
                    mCharsAdvance - mCharsAdvanceAtPrevBoundary);
            return true;
        }

        private boolean doLineBreakWithGraphemeBounds(float[] advances, int start, int end) {
            float width = advances[start];

            // Starting from + 1 since at least one character needs to be assigned to a line.
            for (int i = start + 1; i < end; i++) {
                final float w = advances[i];
                if (w == 0) {
                    // w == 0 means here is not a grapheme bounds. Don't break here.
                    continue;
                }
                if (width + w > mLineWidthLimit) {
                    // Okay, here is the longest position.
                    breakLineAt(i, mLineWidth - width, mCharsAdvance - width);
                    // This method only breaks at the first longest offset, since we may want to hyphenate
                    // the rest of the word.
                    return false;
                } else {
                    width += w;
                }
            }

            // Reaching here means even one character (or cluster) doesn't fit the line.
            // Give up and break at the end of this range.
            breakLineAt(end, 0, 0);
            return true;
        }

        // Add a break point
        private void breakLineAt(int offset, float remainingNextLineWidth, float remainingNextCharsAdvance) {
            mBreakPoints.add(offset);

            mLineWidth = remainingNextLineWidth;
            mCharsAdvance = remainingNextCharsAdvance;
            mPrevBoundaryOffset = NOWHERE;
            mCharsAdvanceAtPrevBoundary = 0;
        }

        private void updateLineWidth(char c, float adv) {
            mCharsAdvance += adv;
            if (!LineBreaker.isLineEndSpace(c)) {
                mLineWidth = mCharsAdvance;
            }
        }

        private int getPrevLineBreakOffset() {
            return mBreakPoints.isEmpty() ? 0 : mBreakPoints.getInt(mBreakPoints.size() - 1);
        }
    }

    public record LineComponent(String text, Style style) implements FormattedText {

        @Nonnull
        @Override
        public <T> Optional<T> visit(@Nonnull FormattedText.ContentConsumer<T> consumer) {
            return consumer.accept(text);
        }

        @Nonnull
        @Override
        public <T> Optional<T> visit(@Nonnull FormattedText.StyledContentConsumer<T> consumer, @Nonnull Style base) {
            return consumer.accept(style.applyTo(base), this.text);
        }
    }
}
