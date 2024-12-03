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

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.*;
import icyllis.modernui.text.InputFilter;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class FourColorPicker extends RelativeLayout {

    private EditText mULColorField;
    private EditText mURColorField;
    private EditText mLRColorField;
    private EditText mLLColorField;

    private int mULColor = ~0;
    private int mURColor = ~0;
    private int mLRColor = ~0;
    private int mLLColor = ~0;

    private final Rect mPreviewBox = new Rect();
    private final int mBorderRadius;

    private float mThicknessFactor = 4f / 9f;

    private final OnFocusChangeListener mOnFieldFocusChange;

    public FourColorPicker(Context context,
                           Supplier<List<? extends String>> getter,
                           Consumer<List<? extends String>> setter,
                           Runnable saveFn) {
        super(context);
        mBorderRadius = dp(6);

        mOnFieldFocusChange = (v, hasFocus) -> {
            EditText input = (EditText) v;
            if (!hasFocus) {
                try {
                    var string = input.getText().toString();
                    int color = 0xFFFFFFFF;
                    int idx = -1;
                    try {
                        color = Color.parseColor(string);
                        if (input == mULColorField) {
                            if (mULColor != color) {
                                mULColor = color;
                                idx = 0;
                            }
                        } else if (input == mURColorField) {
                            if (mURColor != color) {
                                mURColor = color;
                                idx = 1;
                            }
                        } else if (input == mLRColorField) {
                            if (mLRColor != color) {
                                mLRColor = color;
                                idx = 2;
                            }
                        } else if (input == mLLColorField) {
                            if (mLLColor != color) {
                                mLLColor = color;
                                idx = 3;
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                    if (idx != -1) {
                        invalidate();
                        var oldList = getter.get();
                        var newList = new ArrayList<String>(oldList);
                        if (newList.isEmpty()) {
                            newList.add("#FFFFFFFF");
                        }
                        while (newList.size() < 4) {
                            newList.add(newList.get(newList.size() - 1));
                        }
                        newList.set(idx, string);
                        if (!newList.equals(oldList)) {
                            setter.accept(newList);
                            saveFn.run();
                        }
                    }
                    input.setTextColor(0xFF000000 | color);
                } catch (Exception e) {
                    input.setTextColor(0xFFFF0000);
                }
            }
        };

        var colors = getter.get();

        int dp4 = dp(4);
        mULColorField = createField(0, colors);
        {
            var params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.setMargins(dp4, dp4, dp4, dp4);
            mULColorField.setId(601);
            addView(mULColorField, params);
        }
        mURColorField = createField(1, colors);
        {
            var params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.setMargins(dp4, dp4, dp4, dp4);
            mURColorField.setId(602);
            addView(mURColorField, params);
        }
        mLRColorField = createField(2, colors);
        {
            var params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.addRule(RelativeLayout.BELOW, 602);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.setMargins(dp4, dp4, dp4, dp4);
            addView(mLRColorField, params);
        }
        mLLColorField = createField(3, colors);
        {
            var params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.addRule(RelativeLayout.BELOW, 601);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.setMargins(dp4, dp4, dp4, dp4);
            addView(mLLColorField, params);
        }
        mOnFieldFocusChange.onFocusChange(mULColorField, false);
        mOnFieldFocusChange.onFocusChange(mURColorField, false);
        mOnFieldFocusChange.onFocusChange(mLRColorField, false);
        mOnFieldFocusChange.onFocusChange(mLLColorField, false);
    }

    @NonNull
    private EditText createField(int idx, List<? extends String> colors) {
        var field = new EditText(getContext());
        field.setSingleLine();
        field.setText(colors.isEmpty()
                ? "#FFFFFFFF"
                : colors.get(Math.min(idx, colors.size() - 1)));
        field.setFilters(new InputFilter.LengthFilter(10));
        field.setTextSize(16);
        field.setOnFocusChangeListener(mOnFieldFocusChange);
        return field;
    }

    public void setColors(String[] colors) {
        mULColorField.setText(colors[0]);
        mURColorField.setText(colors[1]);
        mLRColorField.setText(colors[2]);
        mLLColorField.setText(colors[3]);
        mOnFieldFocusChange.onFocusChange(mULColorField, false);
        mOnFieldFocusChange.onFocusChange(mURColorField, false);
        mOnFieldFocusChange.onFocusChange(mLRColorField, false);
        mOnFieldFocusChange.onFocusChange(mLLColorField, false);
    }

    public void setThicknessFactor(float thicknessFactor) {
        if (mThicknessFactor != thicknessFactor) {
            mThicknessFactor = thicknessFactor;
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        var paint = Paint.obtain();
        paint.setStyle(Paint.STROKE);
        // TooltipRenderer: rad = 3f, width = 4/3f
        paint.setStrokeWidth(mBorderRadius * mThicknessFactor);
        canvas.drawRoundRectGradient(mPreviewBox.left, mPreviewBox.top, mPreviewBox.right, mPreviewBox.bottom,
                mULColor, mURColor, mLRColor, mLLColor, mBorderRadius, paint);
        paint.recycle();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        mPreviewBox.set(
                Math.max(mULColorField.getRight(), mLLColorField.getRight()),
                getPaddingTop(),
                Math.min(mURColorField.getLeft(), mLRColorField.getLeft()),
                getHeight() - getPaddingBottom()
        );
        int inset = (int) (mBorderRadius * 1.33f + 0.5f);
        mPreviewBox.inset(inset, inset);
        invalidate();
    }
}
