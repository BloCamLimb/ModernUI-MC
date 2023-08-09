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

package icyllis.modernui.mc.testforge;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TestListFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        ListView listView = new ListView(getContext());
        listView.setAdapter(new ArrayAdapter<>(getContext(), new String[]{
                "Apple",
                "Banana",
                "Cherry",
                "Grapes",
                "Mango",
                "Orange",
                "Strawberry",
                "Watermelon"
        }));
        listView.setDivider(new Drawable() {
            @Override
            public void draw(@Nonnull Canvas canvas) {
                Paint paint = Paint.obtain();
                paint.setRGBA(192, 192, 192, 128);
                canvas.drawRect(getBounds(), paint);
                paint.recycle();
            }

            @Override
            public int getIntrinsicHeight() {
                return 2;
            }
        });
        listView.setLayoutParams(new FrameLayout.LayoutParams(listView.dp(400), listView.dp(300), Gravity.CENTER));
        return listView;
    }
}
