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

import icyllis.modernui.graphics.drawable.*;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.View;

import javax.annotation.Nonnull;

public class ThemeControl {

    public static final int BACKGROUND_COLOR = 0xc0292a2c;
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
    }

    public static Drawable makeDivider(@Nonnull View view) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.HLINE);
        drawable.setColor(THEME_COLOR);
        drawable.setSize(-1, view.dp(2));
        return drawable;
    }
}
