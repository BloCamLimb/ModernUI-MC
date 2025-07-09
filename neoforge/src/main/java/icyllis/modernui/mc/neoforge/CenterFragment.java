/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
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

package icyllis.modernui.mc.neoforge;

import icyllis.modernui.R;
import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.FragmentTransaction;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.mc.ui.RectangleDrawable;
import icyllis.modernui.text.InputFilter;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.*;
import icyllis.modernui.view.View.OnLayoutChangeListener;
import icyllis.modernui.widget.*;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static icyllis.modernui.mc.ui.ThemeControl.*;
import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

@Deprecated
public class CenterFragment extends Fragment implements ScreenCallback {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var base = new LinearLayout(getContext());
        base.setId(R.id.content);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setBackground(new Background(base));

        final int dp6 = base.dp(6);
        {
            var title = new TextView(getContext());
            title.setId(R.id.title);
            title.setText(I18n.get("modernui.center.title"));
            title.setTextSize(22);
            title.setTextStyle(TextPaint.BOLD);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            params.setMargins(0, base.dp(12), 0, base.dp(12));
            base.addView(title, params);
        }

        {
            var content = new LinearLayout(getContext());
            content.setClipChildren(true);
            content.setOrientation(LinearLayout.HORIZONTAL);

            boolean xor1 = Math.random() < 0.5;
            boolean xor2 = Math.random() < 0.5;

            {
                ScrollView left = new ScrollView(getContext());
                left.addView(createLeftPanel(), MATCH_PARENT, WRAP_CONTENT);
                var params = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1);
                params.setMargins(dp6, dp6, dp6, dp6);
                content.addView(left, params);

                ObjectAnimator animator = ObjectAnimator.ofFloat(left,
                        xor2 ? View.ROTATION_Y : View.ROTATION_X,
                        !xor1 && xor2 ? -80 : 80, 0);
                animator.setInterpolator(TimeInterpolator.DECELERATE);
                left.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                                               int oldTop, int oldRight, int oldBottom) {
                        animator.start();
                        v.removeOnLayoutChangeListener(this);
                    }
                });
                left.setEdgeEffectColor(THEME_COLOR);
            }

            {
                ScrollView right = new ScrollView(getContext());
                right.addView(createRightPanel(), MATCH_PARENT, WRAP_CONTENT);
                var params = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1);
                params.setMargins(dp6, dp6, dp6, dp6);
                content.addView(right, params);

                ObjectAnimator animator = ObjectAnimator.ofFloat(right,
                        xor2 ? View.ROTATION_X : View.ROTATION_Y,
                        xor1 && !xor2 ? -80 : 80, 0);
                animator.setInterpolator(TimeInterpolator.DECELERATE);
                right.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                                               int oldTop, int oldRight, int oldBottom) {
                        animator.start();
                        v.removeOnLayoutChangeListener(this);
                    }
                });
                right.setEdgeEffectColor(THEME_COLOR);
            }

            var params = new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            base.addView(content, params);
        }

        var params = new FrameLayout.LayoutParams(base.dp(720), base.dp(450));
        params.gravity = Gravity.CENTER;
        base.setLayoutParams(params);
        return base;
    }

    @Nonnull
    private LinearLayout createCategory(String titleKey) {
        var layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        final int dp6 = layout.dp(6);
        final int dp12 = layout.dp(12);
        {
            var title = new TextView(getContext());
            title.setId(R.id.title);
            title.setText(I18n.get(titleKey));
            title.setTextSize(16);
            title.setTextColor(THEME_COLOR);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.START;
            params.setMargins(dp6, dp6, dp6, dp6);
            layout.addView(title, params);
        }

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp12, dp12, dp12, layout.dp(18));
        layout.setLayoutParams(params);

        return layout;
    }

    @Nonnull
    private LinearLayout createInputOption(String titleKey) {
        var layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.START);

        final int dp3 = layout.dp(3);
        final int dp6 = layout.dp(6);
        {
            var title = new TextView(getContext());
            title.setText(I18n.get(titleKey));
            title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            title.setTextSize(14);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
            params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            layout.addView(title, params);
        }
        {
            var input = new EditText(getContext());
            input.setId(R.id.input);
            input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            input.setTextSize(14);
            input.setPadding(dp3, 0, dp3, 0);

            StateListDrawable background = new StateListDrawable();
            background.addState(StateSet.get(StateSet.VIEW_STATE_HOVERED), new RectangleDrawable());
            background.setEnterFadeDuration(300);
            background.setExitFadeDuration(300);
            input.setBackground(background);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(input, params);
        }

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp6, 0, dp6, 0);
        layout.setLayoutParams(params);

        return layout;
    }

    @Nonnull
    private LinearLayout createButtonOption(String titleKey) {
        var layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.START);

        final int dp3 = layout.dp(3);
        final int dp6 = layout.dp(6);
        {
            var title = new TextView(getContext());
            title.setText(I18n.get(titleKey));
            title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            title.setTextSize(14);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(title, params);
        }
        {
            var button = new SwitchButton(getContext());
            button.setId(R.id.button1);
            button.setCheckedColor(THEME_COLOR);

            var params = new LinearLayout.LayoutParams(layout.dp(36), layout.dp(16));
            params.gravity = Gravity.CENTER_VERTICAL;
            params.setMargins(0, dp3, 0, dp3);
            layout.addView(button, params);
        }

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp6, 0, dp6, 0);
        layout.setLayoutParams(params);

        return layout;
    }

    // Minecraft
    @Nonnull
    private View createLeftPanel() {
        var panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);

        {
            // Screen
            var category = createCategory("modernui.center.category.screen");
            panel.addView(category);
        }

        {
            var category = createCategory("modernui.center.category.extension");
            panel.addView(category);
        }

        {
            // Text Engine
            var category = createCategory("modernui.center.category.text");
            /*{
                var option = createButtonOption("modernui.center.text.bitmapRepl");
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(ModernUITextMC.CONFIG.mBitmapReplacement.get());
                button.setOnCheckedChangeListener((__, checked) -> {
                    ModernUITextMC.CONFIG.mBitmapReplacement.set(checked);
                    ModernUITextMC.CONFIG.saveOnly();
                    Toast.makeText(I18n.get("gui.modernui.restart_to_work"), Toast.LENGTH_SHORT)
                            .show();
                });
                category.addView(option);
            }*/
            {
                var option = createButtonOption("modernui.center.text.distanceField");
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(true);
                category.addView(option);
            }
            {
                var option = createButtonOption("modernui.center.text.superSampling");
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(true);
                category.addView(option);
            }
            /*{
                var option = createButtonOption("modernui.center.text.alignPixels");
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(ModernUITextMC.CONFIG.mAlignPixels.get());
                button.setOnCheckedChangeListener((__, checked) -> {
                    ModernUITextMC.CONFIG.mAlignPixels.set(checked);
                    ModernUITextMC.CONFIG.saveAndReloadAsync();
                });
                category.addView(option);
            }*/
            {
                var option = createButtonOption("modernui.center.text.textShaping");
                option.<SwitchButton>requireViewById(R.id.button1).setChecked(true);
                category.addView(option);
            }
            {
                var option = createButtonOption("modernui.center.text.fixSurrogate");
                option.<SwitchButton>requireViewById(R.id.button1).setChecked(true);
                category.addView(option);
            }
            {
                var option = createButtonOption("modernui.center.text.fastDigitRepl");
                option.<SwitchButton>requireViewById(R.id.button1).setChecked(true);
                category.addView(option);
            }
            {
                var option = createButtonOption("modernui.center.text.fastStreamingAlgo");
                option.<SwitchButton>requireViewById(R.id.button1).setChecked(true);
                category.addView(option);
            }
            {
                var option = createButtonOption("modernui.center.text.graphemeAlgo");
                option.<SwitchButton>requireViewById(R.id.button1).setChecked(true);
                category.addView(option);
            }
            {
                var option = createButtonOption("modernui.center.text.lineBreakingAlgo");
                option.<SwitchButton>requireViewById(R.id.button1).setChecked(true);
                category.addView(option);
            }
            {
                var option = createButtonOption("modernui.center.text.substringAlgo");
                option.<SwitchButton>requireViewById(R.id.button1).setChecked(true);
                category.addView(option);
            }
            /*{
                var option = createInputOption("modernui.center.text.cacheLifespan");
                var input = option.<EditText>requireViewById(R.id.input);
                input.setText(ModernUIText.CONFIG.mCacheLifespan.get().toString());
                input.setFilters(DigitsInputFilter.getInstance(input.getTextLocale()), new InputFilter.LengthFilter(2));
                input.setOnFocusChangeListener((view, hasFocus) -> {
                    if (!hasFocus) {
                        EditText v = (EditText) view;
                        int value = MathUtil.clamp(Integer.parseInt(v.getText().toString()),
                                ModernUIText.Config.LIFESPAN_MIN, ModernUIText.Config.LIFESPAN_MAX);
                        v.setText(Integer.toString(value));
                        if (value != ModernUIText.CONFIG.mCacheLifespan.get()) {
                            ModernUIText.CONFIG.mCacheLifespan.set(value);
                            ModernUIText.CONFIG.saveAndReloadAsync();
                        }
                    }
                });
                category.addView(option);
            }
            {
                var option = createInputOption("modernui.center.text.rehashThreshold");
                var input = option.<EditText>requireViewById(R.id.input);
                input.setText(ModernUIText.CONFIG.mRehashThreshold.get().toString());
                input.setFilters(DigitsInputFilter.getInstance(input.getTextLocale()), new InputFilter.LengthFilter(4));
                input.setOnFocusChangeListener((view, hasFocus) -> {
                    if (!hasFocus) {
                        EditText v = (EditText) view;
                        int value = MathUtil.clamp(Integer.parseInt(v.getText().toString()),
                                ModernUIText.Config.REHASH_MIN, ModernUIText.Config.REHASH_MAX);
                        v.setText(Integer.toString(value));
                        if (value != ModernUIText.CONFIG.mRehashThreshold.get()) {
                            ModernUIText.CONFIG.mRehashThreshold.set(value);
                            ModernUIText.CONFIG.saveAndReloadAsync();
                        }
                    }
                });
                category.addView(option);
            }*/
            panel.addView(category);
        }

        panel.setDividerDrawable(new Divider(panel));
        panel.setDividerPadding(panel.dp(8));
        panel.setShowDividers(LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END);

        return panel;
    }

    @Nonnull
    private View createRightPanel() {
        var panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);

        final int dp6 = panel.dp(6);
        {
            var category = createCategory("modernui.center.category.system");
            panel.addView(category);
        }

        {
            var category = createCategory("modernui.center.category.font");
            /*{
                var option = createButtonOption("modernui.center.font.fractionalMetrics");
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(Config.CLIENT.mFractionalMetrics.get());
                button.setOnCheckedChangeListener((__, checked) -> {
                    Config.CLIENT.mFractionalMetrics.set(checked);
                    Config.CLIENT.saveAndReloadAsync();
                });
                category.addView(option);
            }
            {
                var option = createButtonOption("modernui.center.font.linearSampling");
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(Config.CLIENT.mLinearSampling.get());
                button.setOnCheckedChangeListener((__, checked) -> {
                    Config.CLIENT.mLinearSampling.set(checked);
                    Config.CLIENT.saveAndReloadAsync();
                });
                category.addView(option);
            }*/
            panel.addView(category);
        }

        {
            var group = new RelativeLayout(getContext());

            {
                var title = new TextView(getContext());
                title.setId(18);
                title.setText("View");
                title.setTextSize(16);
                title.setTextColor(THEME_COLOR);

                var params = new RelativeLayout.LayoutParams(WRAP_CONTENT,
                        WRAP_CONTENT);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.addRule(RelativeLayout.ALIGN_PARENT_START);
                params.setMargins(dp6, dp6, dp6, dp6);
                group.addView(title, params);
            }

            addSystemSetting(20, "Scrollbar size", group, 1024, ConfigImpl.CLIENT.mScrollbarSize);
            addSystemSetting(22, "Touch slop", group, 1024, ConfigImpl.CLIENT.mTouchSlop);
            addSystemSetting(24, "Min scrollbar touch target", group, 1024, ConfigImpl.CLIENT.mMinScrollbarTouchTarget);
            addSystemSetting(26, "Minimum fling velocity", group, 32767, ConfigImpl.CLIENT.mMinimumFlingVelocity);
            addSystemSetting(28, "Maximum fling velocity", group, 32767, ConfigImpl.CLIENT.mMaximumFlingVelocity);
            addSystemSetting(30, "Overscroll distance", group, 1024, ConfigImpl.CLIENT.mOverscrollDistance);
            addSystemSetting(32, "Overfling distance", group, 1024, ConfigImpl.CLIENT.mOverflingDistance);

            {
                var view = new TextView(getContext());
                view.setId(34);
                view.setText("Vertical scroll factor");
                view.setTextSize(14);

                var params = new RelativeLayout.LayoutParams(WRAP_CONTENT,
                        WRAP_CONTENT);
                params.addRule(RelativeLayout.BELOW, 32);
                params.addRule(RelativeLayout.ALIGN_START, 32);
                group.addView(view, params);

                var input = new EditText(getContext());
                input.setText(ConfigImpl.CLIENT.mVerticalScrollFactor.get().toString());
                input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                input.setTextSize(14);
                input.setFilters(DigitsInputFilter.getInstance(input.getTextLocale(), false, true),
                        new InputFilter.LengthFilter(6));
                input.setPadding(panel.dp(4), 0, panel.dp(4), 0);
                input.setOnFocusChangeListener((__, hasFocus) -> {
                    if (!hasFocus) {
                        double radius = Double.parseDouble(input.getText().toString());
                        radius = Math.max(Math.min(radius, 1024), 0);
                        input.setText(Double.toString(radius));
                        if (radius != ConfigImpl.CLIENT.mVerticalScrollFactor.get()) {
                            ConfigImpl.CLIENT.mVerticalScrollFactor.set(radius);
                            ConfigImpl.CLIENT.saveAndReloadAsync();
                        }
                    }
                });
                StateListDrawable drawable = new StateListDrawable();
                drawable.addState(StateSet.get(StateSet.VIEW_STATE_HOVERED), new RectangleDrawable());
                drawable.setEnterFadeDuration(300);
                drawable.setExitFadeDuration(300);
                input.setBackground(drawable);

                params = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                params.addRule(RelativeLayout.ALIGN_BASELINE, 34);
                params.addRule(RelativeLayout.ALIGN_PARENT_END);
                params.setMargins(dp6, dp6, dp6, dp6);
                group.addView(input, params);
            }

            {
                var view = new TextView(getContext());
                view.setId(36);
                view.setText("Horizontal scroll factor");
                view.setTextSize(14);

                var params = new RelativeLayout.LayoutParams(WRAP_CONTENT,
                        WRAP_CONTENT);
                params.addRule(RelativeLayout.BELOW, 34);
                params.addRule(RelativeLayout.ALIGN_START, 34);
                group.addView(view, params);

                var input = new EditText(getContext());
                input.setText(ConfigImpl.CLIENT.mHorizontalScrollFactor.get().toString());
                input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                input.setTextSize(14);
                input.setFilters(DigitsInputFilter.getInstance(input.getTextLocale(), false, true),
                        new InputFilter.LengthFilter(6));
                input.setPadding(panel.dp(4), 0, panel.dp(4), 0);
                input.setOnFocusChangeListener((__, hasFocus) -> {
                    if (!hasFocus) {
                        double radius = Double.parseDouble(input.getText().toString());
                        radius = Math.max(Math.min(radius, 1024), 0);
                        input.setText(Double.toString(radius));
                        if (radius != ConfigImpl.CLIENT.mHorizontalScrollFactor.get()) {
                            ConfigImpl.CLIENT.mHorizontalScrollFactor.set(radius);
                            ConfigImpl.CLIENT.saveAndReloadAsync();
                        }
                    }
                });
                StateListDrawable drawable = new StateListDrawable();
                drawable.addState(StateSet.get(StateSet.VIEW_STATE_HOVERED), new RectangleDrawable());
                drawable.setEnterFadeDuration(300);
                drawable.setExitFadeDuration(300);
                input.setBackground(drawable);

                params = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                params.addRule(RelativeLayout.ALIGN_BASELINE, 36);
                params.addRule(RelativeLayout.ALIGN_PARENT_END);
                params.setMargins(dp6, dp6, dp6, dp6);
                group.addView(input, params);
            }

            var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            params.setMargins(panel.dp(12), panel.dp(12), panel.dp(12), panel.dp(18));
            panel.addView(group, params);
        }

        panel.setDividerDrawable(new Divider(panel));
        panel.setDividerPadding(panel.dp(8));
        panel.setShowDividers(LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END);

        return panel;
    }

    private void addSystemSetting(int id, String title, @Nonnull ViewGroup container, int max,
                                  @Nonnull ModConfigSpec.IntValue config) {
        var view = new TextView(getContext());
        view.setId(id);
        view.setText(title);
        view.setTextSize(14);

        var params = new RelativeLayout.LayoutParams(WRAP_CONTENT,
                WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, id - 2);
        params.addRule(RelativeLayout.ALIGN_START, id - 2);
        container.addView(view, params);

        var input = new EditText(getContext());
        input.setText(config.get().toString());
        input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        input.setTextSize(14);
        input.setFilters(DigitsInputFilter.getInstance(input.getTextLocale()), new InputFilter.LengthFilter(5));
        input.setPadding(view.dp(4), 0, view.dp(4), 0);
        input.setOnFocusChangeListener((__, hasFocus) -> {
            if (!hasFocus) {
                int val = Integer.parseInt(input.getText().toString());
                val = MathUtil.clamp(val, 0, max);
                input.setText(Integer.toString(val));
                if (val != config.get()) {
                    config.set(val);
                    ConfigImpl.CLIENT.saveAndReloadAsync();
                }
            }
        });
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(StateSet.get(StateSet.VIEW_STATE_HOVERED), new RectangleDrawable());
        drawable.setEnterFadeDuration(300);
        drawable.setExitFadeDuration(300);
        input.setBackground(drawable);

        params = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_BASELINE, id);
        params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.setMargins(view.dp(6), view.dp(6), view.dp(6), view.dp(6));
        container.addView(input, params);
    }

    @Nullable
    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        if (enter && transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
            Keyframe kfStart = Keyframe.ofFloat(0, 0.75f);
            Keyframe kfEnd = Keyframe.ofFloat(1, 1);
            kfEnd.setInterpolator(TimeInterpolator.OVERSHOOT);
            PropertyValuesHolder scaleX = PropertyValuesHolder.ofKeyframe(View.SCALE_X, kfStart, kfEnd);
            PropertyValuesHolder scaleY = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, kfStart.copy(), kfEnd.copy());
            kfStart = Keyframe.ofFloat(0, 0);
            kfEnd = Keyframe.ofFloat(1, 1);
            kfEnd.setInterpolator(TimeInterpolator.DECELERATE_CUBIC);
            PropertyValuesHolder alpha = PropertyValuesHolder.ofKeyframe(View.ALPHA, kfStart, kfEnd);
            final Animator animator = ObjectAnimator.ofPropertyValuesHolder(null, scaleX, scaleY, alpha);
            animator.setDuration(400);
            // we use keyframe-specified interpolators
            animator.setInterpolator(null);
            return animator;
        }
        return super.onCreateAnimator(transit, enter, nextAnim);
    }

    private static class Background extends Drawable {

        private final float mRadius;
        private final float mStrokeWidth;

        private Background(View view) {
            mRadius = view.dp(8);
            mStrokeWidth = view.dp(4);
        }

        @Override
        public void draw(@Nonnull Canvas canvas) {
            Paint paint = Paint.obtain();
            Rect bounds = getBounds();
            paint.setStyle(Paint.FILL);
            paint.setColor(BACKGROUND_COLOR);
            float inner = mStrokeWidth * 0.5f;
            canvas.drawRoundRect(bounds.left + inner, bounds.top + inner, bounds.right - inner,
                    bounds.bottom - inner, mRadius, paint);
            /*((GLSurfaceCanvas) canvas).drawGlowWave(bounds.left + inner * 1.5f, bounds.top + inner * 1.5f,
                    bounds.right - inner, bounds.bottom - inner);*/
            paint.setStyle(Paint.STROKE);
            paint.setColor(THEME_COLOR);
            paint.setStrokeWidth(mStrokeWidth);
            //paint.setSmoothRadius(inner);
            canvas.drawRoundRect(bounds.left + inner, bounds.top + inner, bounds.right - inner,
                    bounds.bottom - inner, mRadius, paint);
            paint.recycle();
            invalidateSelf();
        }

        @Override
        public boolean getPadding(@Nonnull Rect padding) {
            int inner = (int) Math.ceil(mStrokeWidth * 0.5f);
            padding.set(inner, inner, inner, inner);
            return true;
        }
    }

    private static class Divider extends Drawable {

        private final int mSize;

        public Divider(View view) {
            mSize = view.dp(2);
        }

        @Override
        public void draw(@Nonnull Canvas canvas) {
            Paint paint = Paint.obtain();
            paint.setColor(0xc0606060);
            canvas.drawRect(getBounds(), paint);
            paint.recycle();
        }

        @Override
        public int getIntrinsicHeight() {
            return mSize;
        }
    }

}
