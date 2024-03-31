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

public class ThemeControl {

    public static final int BACKGROUND_COLOR = 0xc0292a2c;
    public static final int THEME_COLOR = 0xffcda398;
    public static final int THEME_COLOR_2 = 0xffcd98a3;

    public static void addBackground(View view) {
        StateListDrawable background = new StateListDrawable();
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(0x80a0a0a0);
        drawable.setCornerRadius(view.dp(2));
        background.addState(StateSet.get(StateSet.VIEW_STATE_HOVERED), drawable);
        background.setEnterFadeDuration(250);
        background.setExitFadeDuration(250);
        view.setBackground(background);
    }

    public static Drawable makeDivider(View view) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.HLINE);
        drawable.setColor(THEME_COLOR);
        drawable.setSize(-1, view.dp(2));
        return drawable;
    }
}
