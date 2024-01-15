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

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.markdown.Markdown;
import icyllis.modernui.markdown.core.CorePlugin;
import icyllis.modernui.mc.ui.ThemeControl;
import icyllis.modernui.text.method.LinkMovementMethod;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

import javax.annotation.Nonnull;

public class DashboardFragment extends Fragment {

    public static final String CREDIT_TEXT = """
            Modern UI 3.10
            by
            BloCamLimb
            (Icyllis Milica)
            _(:з」∠)_""";

    private ViewGroup mLayout;
    private TextView mSideBox;
    private TextView mInfoBox;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        if (mLayout != null) {
            return mLayout;
        }
        var layout = new FrameLayout(requireContext());

        {
            TextView tv;
            if (mSideBox == null) {
                tv = new Button(getContext());
                tv.setText("Still Alive");
                tv.setTextSize(16);
                tv.setTextColor(0xFFDCAE32);
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                tv.setOnClickListener(this::play);
                ThemeControl.addBackground(tv);
                mSideBox = tv;
            } else {
                tv = mSideBox;
            }

            var params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(tv.dp(120));
            params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            layout.addView(tv, params);
        }

        {
            TextView tv;
            if (mInfoBox == null) {
                tv = new TextView(getContext());
                tv.setTextSize(16);
                // leading margin is based on para dir, not view dir
                tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                tv.setMovementMethod(LinkMovementMethod.getInstance());
                Markdown.builder(requireContext())
                        .usePlugin(CorePlugin.create())
                        .setBufferType(TextView.BufferType.SPANNABLE)
                        .build()
                        .setMarkdown(tv, """
                                What's New in Modern UI 3.10
                                ----
                                * Enhanced Text Layout
                                * Better Mod Compatibility
                                * Rendering Optimization
                                * Basic Markdown Support
                                * Unicode 15.1 Emoji List
                                * New UI Components
                                * And More…
                                                                
                                > Author: BloCamLimb \s
                                  Source Code: [Modern UI](https://github.com/BloCamLimb/ModernUI) \s
                                  Source Code: [Modern UI (MC)](https://github.com/BloCamLimb/ModernUI-MC)""");
                mInfoBox = tv;
            } else {
                tv = mInfoBox;
            }

            var params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(tv.dp(120));
            params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            layout.addView(tv, params);
        }

        {
            var tv = new TextView(getContext());
            tv.setTextSize(12);
            tv.setText("Copyright © 2019-2023 BloCamLimb. All rights reserved.");
            var params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            layout.addView(tv, params);
        }

        layout.setLayoutTransition(new LayoutTransition());
        return mLayout = layout;
    }

    final Runnable mUpdateText = this::updateText;

    private void play(View button) {
        var tv = (TextView) button;
        tv.setText("", TextView.BufferType.EDITABLE);
        tv.setClickable(false);
        if (mInfoBox != null) {
            mInfoBox.setVisibility(View.GONE);
        }

        StillAlive.getInstance().start();

        mLayout.postDelayed(() -> {
            if (mLayout.isAttachedToWindow()) {
                var view = new View(getContext());
                view.setBackground(new Background(view));

                var params = new FrameLayout.LayoutParams(view.dp(480), view.dp(270));
                params.setMarginStart(view.dp(60));
                params.setMarginEnd(view.dp(30));
                params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;

                mLayout.addView(view, params);
            }
        }, 18000);

        mLayout.postDelayed(mUpdateText, 2000);
    }

    private void updateText() {
        if (!mSideBox.isAttachedToWindow()) {
            return;
        }
        var editable = mSideBox.getEditableText();
        if (editable.length() < CREDIT_TEXT.length()) {
            editable.append(CREDIT_TEXT.charAt(editable.length()));
            if (editable.length() < CREDIT_TEXT.length()) {
                mSideBox.postDelayed(mUpdateText, 250);
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        StillAlive.getInstance().stop();
    }

    private static class Background extends Drawable {

        private final float mStrokeWidth;

        private Background(View view) {
            mStrokeWidth = view.dp(2);
        }

        @Override
        public void draw(@Nonnull Canvas canvas) {
            if (canvas instanceof GLSurfaceCanvas) {
                var bounds = getBounds();
                var inner = mStrokeWidth * 0.5f;
                ((GLSurfaceCanvas) canvas).drawGlowWave(bounds.left + inner * 1.5f, bounds.top + inner * 1.5f,
                        bounds.right - inner, bounds.bottom - inner);
                var paint = Paint.obtain();
                paint.setStyle(Paint.STROKE);
                paint.setColor(ThemeControl.THEME_COLOR);
                paint.setStrokeWidth(mStrokeWidth);
                canvas.drawRoundRect(bounds.left + inner, bounds.top + inner, bounds.right - inner,
                        bounds.bottom - inner, mStrokeWidth * 2, paint);
                paint.recycle();
                invalidateSelf();
            }
        }

        @Override
        public boolean getPadding(@Nonnull Rect padding) {
            int inner = (int) Math.ceil(mStrokeWidth * 0.5f);
            padding.set(inner, inner, inner, inner);
            return true;
        }
    }
}
