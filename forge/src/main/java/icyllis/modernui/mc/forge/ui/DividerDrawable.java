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

package icyllis.modernui.mc.forge.ui;

import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.view.View;

import javax.annotation.Nonnull;

public class DividerDrawable extends Drawable {

    private final int mThickness;

    public DividerDrawable(View view) {
        mThickness = view.dp(2);
    }

    @Override
    public void draw(@Nonnull Canvas canvas) {
        Paint paint = Paint.obtain();
        paint.setColor(ThemeControl.THEME_COLOR);
        Rect r = getBounds();
        float half = mThickness / 2f;
        canvas.drawLine(r.left + half, r.top + half, r.right - half, r.bottom - half, mThickness, paint);
        paint.recycle();
    }

    @Override
    public int getIntrinsicWidth() {
        return mThickness;
    }

    @Override
    public int getIntrinsicHeight() {
        return mThickness;
    }
}
