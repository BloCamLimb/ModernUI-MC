/*
 * Modern UI.
 * Copyright (C) 2025 BloCamLimb. All rights reserved.
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

import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.util.AttributeSet;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.ScrollView;

/**
 * Constrain the max width of the direct child, which should use MATCH_PARENT layout_width.
 */
public class ClampingScrollView extends ScrollView {

    private int mMaxWidth;

    public ClampingScrollView(Context context) {
        super(context);
    }

    public ClampingScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClampingScrollView(Context context, AttributeSet attrs, ResourceId defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ClampingScrollView(Context context, AttributeSet attrs, ResourceId defStyleAttr, ResourceId defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setMaxWidth(int maxWidth) {
        mMaxWidth = maxWidth;
    }

    public int getMaxWidth() {
        return mMaxWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (getChildCount() > 0 && mMaxWidth > 0) {
            // constrain the direct child
            final View child = getChildAt(0);

            int childWidth = child.getMeasuredWidth();
            int clampedWidth = Math.min(childWidth, mMaxWidth);

            if (childWidth != clampedWidth) {
                var newWidthSpec = MeasureSpec.makeMeasureSpec(clampedWidth, MeasureSpec.EXACTLY);
                var newHeightSpec = MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY);
                child.measure(newWidthSpec, newHeightSpec);
            }
        }
    }
}
