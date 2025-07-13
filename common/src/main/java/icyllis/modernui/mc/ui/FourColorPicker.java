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
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Size;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.text.InputFilter;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.RelativeLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class FourColorPicker extends RelativeLayout {

    // upper left, upper right, lower right, lower left
    private final EditText[] mColorFields = new EditText[4];

    private final int[] mColors = {~0, ~0, ~0, ~0};

    private final Rect mPreviewBox = new Rect();

    private float mBorderRadius;
    private float mBorderWidth = 1;

    private final int mBorderColor;

    private final OnFocusChangeListener mOnFieldFocusChange;

    public FourColorPicker(Context context,
                           Supplier<List<? extends String>> getter,
                           Consumer<List<? extends String>> setter,
                           Runnable saveFn) {
        super(context);

        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(R.ns, R.attr.colorOutline, value, true);
        mBorderColor = value.data;

        mOnFieldFocusChange = (v, hasFocus) -> {
            EditText input = (EditText) v;
            if (hasFocus) {
                return;
            }
            var string = input.getText().toString();
            int color = 0xFFFFFFFF;
            int index = -1;
            for (int i = 0; i < mColorFields.length; i++) {
                if (mColorFields[i] == input) {
                    index = i;
                    break;
                }
            }
            try {
                color = Color.parseColor(string);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            if (mColors[index] != color) {
                mColors[index] = color;
                invalidate();
                var oldList = getter.get();
                var newList = new ArrayList<String>(oldList);
                if (newList.isEmpty()) {
                    newList.add("#FFFFFFFF");
                }
                while (newList.size() < 4) {
                    newList.add(newList.get(newList.size() - 1));
                }
                newList.set(index, string);
                if (!newList.equals(oldList)) {
                    setter.accept(newList);
                    saveFn.run();
                }
            }
        };

        var colors = getter.get();

        int dp4 = dp(4);
        mColorFields[0] = createField(0, colors);
        {
            var params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.setMargins(dp4, dp4, dp4, dp4);
            mColorFields[0].setId(601);
            addView(mColorFields[0], params);
        }
        mColorFields[1] = createField(1, colors);
        {
            var params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.setMargins(dp4, dp4, dp4, dp4);
            mColorFields[1].setId(602);
            addView(mColorFields[1], params);
        }
        mColorFields[2] = createField(2, colors);
        {
            var params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.addRule(RelativeLayout.BELOW, 602);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.setMargins(dp4, dp4, dp4, dp4);
            addView(mColorFields[2], params);
        }
        mColorFields[3] = createField(3, colors);
        {
            var params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.addRule(RelativeLayout.BELOW, 601);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.setMargins(dp4, dp4, dp4, dp4);
            addView(mColorFields[3], params);
        }
        // init colors
        for (var f : mColorFields) {
            mOnFieldFocusChange.onFocusChange(f, false);
        }
        setWillNotDraw(false);
    }

    @NonNull
    private EditText createField(int index, @NotNull List<? extends String> colors) {
        var field = new EditText(getContext(), null, R.attr.editTextOutlinedStyle);
        Typeface monoFont = Typeface.getSystemFont("JetBrains Mono Medium");
        if (monoFont != Typeface.SANS_SERIF) {
            field.setTypeface(monoFont);
        }
        field.setSingleLine();
        field.setText(colors.isEmpty()
                ? "#FFFFFFFF"
                : colors.get(Math.min(index, colors.size() - 1)));
        field.setFilters(new InputFilter.LengthFilter(10));
        field.setOnFocusChangeListener(mOnFieldFocusChange);
        return field;
    }

    public void setColors(@NonNull @Size(4) String[] colors) {
        for (int i = 0; i < mColorFields.length; i++) {
            mColorFields[i].setText(colors[i]);
        }
        for (var f : mColorFields) {
            mOnFieldFocusChange.onFocusChange(f, false);
        }
    }

    public void setBorderRadius(float borderRadius) {
        borderRadius = getContext().getResources().getDisplayMetrics().density * borderRadius * 2.0f;
        if (mBorderRadius != borderRadius) {
            mBorderRadius = borderRadius;
            invalidate();
        }
    }

    public void setBorderWidth(float borderWidth) {
        borderWidth = getContext().getResources().getDisplayMetrics().density * borderWidth * 2.0f;
        if (mBorderWidth != borderWidth) {
            mBorderWidth = borderWidth;
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        var paint = Paint.obtain();

        paint.setColor(mColors[0]);
        canvas.drawRoundRect(
                mPreviewBox.left + mBorderWidth, mPreviewBox.top + mBorderWidth, mPreviewBox.centerX() - 1,
                mPreviewBox.centerY() - 1,
                mBorderRadius, paint
        );
        paint.setColor(mColors[1]);
        canvas.drawRoundRect(
                mPreviewBox.centerX() + 1, mPreviewBox.top + mBorderWidth, mPreviewBox.right - mBorderWidth,
                mPreviewBox.centerY() - 1,
                mBorderRadius, paint
        );
        paint.setColor(mColors[2]);
        canvas.drawRoundRect(
                mPreviewBox.centerX() + 1, mPreviewBox.centerY() + 1, mPreviewBox.right - mBorderWidth,
                mPreviewBox.bottom - mBorderWidth,
                mBorderRadius, paint
        );
        paint.setColor(mColors[3]);
        canvas.drawRoundRect(
                mPreviewBox.left + mBorderWidth, mPreviewBox.centerY() + 1, mPreviewBox.centerX() - 1,
                mPreviewBox.bottom - mBorderWidth,
                mBorderRadius, paint
        );

        paint.setColor(mBorderColor);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(mBorderWidth);
        canvas.drawRoundRect(mPreviewBox.left, mPreviewBox.top, mPreviewBox.right, mPreviewBox.bottom,
                mBorderRadius, paint);
        paint.recycle();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        mPreviewBox.set(
                Math.max(mColorFields[0].getRight(), mColorFields[3].getRight()),
                getPaddingTop(),
                Math.min(mColorFields[1].getLeft(), mColorFields[2].getLeft()),
                getHeight() - getPaddingBottom()
        );
        int inset = dp(8);
        mPreviewBox.inset(inset, inset);
    }
}
