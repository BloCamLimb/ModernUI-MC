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

import icyllis.arc3d.core.*;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.*;
import icyllis.modernui.view.View;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * ContainerMenuView can be used to display and interact with a {@link AbstractContainerMenu ContainerMenu}
 * in the view system.
 */
//TODO WIP
public class ContainerMenuView extends View implements CustomDrawable {

    private AbstractContainerMenu mContainerMenu;
    private final int mItemSize;

    public ContainerMenuView(Context context) {
        super(context);
        mItemSize = dp(32);
    }

    public void setContainerMenu(AbstractContainerMenu containerMenu) {
        mContainerMenu = containerMenu;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(@Nonnull Canvas canvas) {
        var menu = mContainerMenu;
        if (menu == null) {
            return;
        }
        for (int i = 0; i < menu.slots.size(); ++i) {
            Slot slot = menu.slots.get(i);
            if (slot.isActive()) {
                drawSlot(canvas, slot);
            }
        }
    }

    protected void drawSlot(@Nonnull Canvas canvas, @Nonnull Slot slot) {
        ItemStack item = slot.getItem();
        if (item.isEmpty()) {
            return;
        }
        int x = dp(slot.x * 2);
        int y = dp(slot.y * 2);
        ContainerDrawHelper.drawItem(canvas, item, x, y, 0, mItemSize, x + y * getWidth());
    }

    @Override
    public DrawHandler snapDrawHandler(int backendApi,
                                       Matrix4 viewMatrix,
                                       Rect2i clipBounds,
                                       ImageInfo targetInfo) {
        return null;
    }

    @Override
    public RectF getBounds() {
        return null;
    }
}
