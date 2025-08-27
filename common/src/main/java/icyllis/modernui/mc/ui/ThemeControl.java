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

package icyllis.modernui.mc.ui;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.text.CharSequenceBuilder;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

import javax.annotation.Nonnull;

public class ThemeControl {

    /*public static final int BACKGROUND_COLOR = 0xc0292a2c;
    public static final int THEME_COLOR = 0xffcda398;
    public static final int THEME_COLOR_2 = 0xffcd98a3;

    private static Drawable.ConstantState sBackgroundState;
    private static int sBackgroundDensity;

    public static synchronized void addBackground(@Nonnull View view) {
        createBackground(view);
        if (sBackgroundState != null) {
            view.setBackground(sBackgroundState.newDrawable());
        }
    }

    private static synchronized void createBackground(@Nonnull View view) {
        int density = view.getContext().getResources().getDisplayMetrics().densityDpi;
        if (sBackgroundState == null || density != sBackgroundDensity) {
            sBackgroundDensity = density;
            StateListDrawable background = new StateListDrawable();
            ShapeDrawable drawable = new ShapeDrawable();
            drawable.setShape(ShapeDrawable.RECTANGLE);
            drawable.setColor(0x60A0A0A0);
            drawable.setCornerRadius(view.dp(3));
            int dp1 = view.dp(1);
            drawable.setStroke(dp1, 0xFFE6E6E6);
            background.addState(StateSet.get(StateSet.VIEW_STATE_HOVERED), drawable);
            background.setEnterFadeDuration(200);
            background.setExitFadeDuration(200);
            sBackgroundState = background.getConstantState();
        }
    }*/

    @Nonnull
    public static Drawable makeDivider(@Nonnull View view) {
        return makeDivider(view, false);
    }

    @Nonnull
    public static Drawable makeDivider(@Nonnull View view, boolean vertical) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(vertical ? ShapeDrawable.VLINE : ShapeDrawable.HLINE);
        TypedValue value = new TypedValue();
        view.getContext().getTheme().resolveAttribute(R.ns, R.attr.colorOutlineVariant, value, true);
        drawable.setColor(value.data);
        drawable.setSize(view.dp(1), view.dp(1));
        return drawable;
    }

    @Nonnull
    public static String stripFormattingCodes(@Nonnull String str) {
        if (str.indexOf(167) >= 0) {
            var csb = new CharSequenceBuilder();
            boolean res = StringDecomposer.iterateFormatted(str, Style.EMPTY, (index, style, codePoint) -> {
                csb.addCodePoint(codePoint);
                return true;
            });
            assert res;
            return csb.toString();
        }
        return str;
    }

    public static void setViewAndChildrenEnabled(View view, boolean enabled) {
        if (view != null) {
            view.setEnabled(enabled);
        }
        if (view instanceof ViewGroup vg) {
            int cc = vg.getChildCount();
            for (int i = 0; i < cc; i++) {
                View v = vg.getChildAt(i);
                setViewAndChildrenEnabled(v, enabled);
            }
        }
    }

    public static void makeOutlinedCard(@Nonnull Context context, @Nonnull View layout,
                                        @Nonnull TypedValue value) {
        final int dp12 = layout.dp(12);
        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp12);
        context.getTheme().resolveAttribute(R.ns, R.attr.colorSurface, value, true);
        bg.setColor(value.data);
        int[] strokeColors = new int[2];
        context.getTheme().resolveAttribute(R.ns, R.attr.colorOutlineVariant, value, true);
        strokeColors[0] = value.data;
        context.getTheme().resolveAttribute(R.ns, R.attr.colorOutline, value, true);
        strokeColors[1] = ColorStateList.modulateColor(value.data, 0.12f);
        bg.setStroke(layout.dp(1), new ColorStateList(
                new int[][]{
                        StateSet.get(StateSet.VIEW_STATE_ENABLED),
                        StateSet.WILD_CARD
                },
                strokeColors
        ));
        layout.setBackground(bg);
        layout.setPadding(dp12, dp12, dp12, dp12);
    }

    public static void makeFilledCard(@Nonnull Context context, @Nonnull View layout,
                                      @Nonnull TypedValue value) {
        final int dp12 = layout.dp(12);
        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp12);
        context.getTheme().resolveAttribute(R.ns, R.attr.colorSurfaceContainerHighest, value, true);
        bg.setColor(value.data);
        layout.setBackground(bg);
        layout.setPadding(dp12, dp12, dp12, dp12);
    }

    public static void makeElevatedCard(@Nonnull Context context, @Nonnull View layout,
                                        @Nonnull TypedValue value) {
        final int dp12 = layout.dp(12);
        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp12);
        context.getTheme().resolveAttribute(R.ns, R.attr.colorSurfaceContainerLow, value, true);
        bg.setColor(value.data);
        layout.setBackground(bg);
        layout.setElevation(layout.dp(1));
        layout.setPadding(dp12, dp12, dp12, dp12);
    }
}
