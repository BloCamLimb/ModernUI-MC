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

import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.animation.MotionEasingUtils;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.graphics.drawable.BuiltinIconDrawable;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.graphics.text.FontFamily;
import icyllis.modernui.mc.Config;
import icyllis.modernui.mc.ModernUIClient;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.MuiPlatform;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.InputFilter;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.ContextMenu;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.Menu;
import icyllis.modernui.view.MenuItem;
import icyllis.modernui.view.OneShotPreDrawListener;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class PreferencesFragment extends Fragment {

    boolean mCommonConfigChanged;
    boolean mClientConfigChanged;
    boolean mTextConfigChanged;

    final Runnable mOnCommonConfigChanged = () -> {
        mCommonConfigChanged = true;
        Config.COMMON.reload();
    };
    final Runnable mOnClientConfigChanged = () -> {
        mClientConfigChanged = true;
        Config.CLIENT.reload();
    };
    final Runnable mOnTextConfigChanged = () -> {
        mTextConfigChanged = true;
        Config.TEXT.reload();
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var context = requireContext();
        var base = new FrameLayout(context);
        var pager = new ViewPager(context);
        {
            pager.setAdapter(this.new PreferencesAdapter());
            pager.setFocusableInTouchMode(true);
            pager.setKeyboardNavigationCluster(true);

            OneShotPreDrawListener.add(pager, () -> {
                var animator = ObjectAnimator.ofFloat(pager,
                        View.ROTATION_Y, pager.isLayoutRtl() ? -45 : 45, 0);
                animator.setInterpolator(MotionEasingUtils.MOTION_EASING_EMPHASIZED);
                animator.start();
            });

            var lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = base.dp(48);
            base.addView(pager, lp);
        }

        {
            var tabLayout = new TabLayout(context);
            {
                var value = new TypedValue();
                context.getTheme().resolveAttribute(R.ns, R.attr.colorSurfaceContainerLow, value, true);
                tabLayout.setBackground(new ColorDrawable(value.data));
            }
            tabLayout.setElevation(base.dp(3));
            tabLayout.setTabMode(TabLayout.MODE_AUTO);
            tabLayout.setTabGravity(TabLayout.GRAVITY_CENTER);
            tabLayout.setupWithViewPager(pager);

            var lp = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            base.addView(tabLayout, lp);
        }

        return base;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        int count = (mClientConfigChanged ? 1 : 0) +
                (mCommonConfigChanged ? 1 : 0) +
                (mTextConfigChanged ? 1 : 0);
        if (count != 0) {
            int[] configs = new int[count];
            count = 0;
            if (mClientConfigChanged) {
                configs[count++] = Config.TYPE_CLIENT;
                mClientConfigChanged = false;
            }
            if (mCommonConfigChanged) {
                configs[count++] = Config.TYPE_COMMON;
                mCommonConfigChanged = false;
            }
            if (mTextConfigChanged) {
                configs[count++] = Config.TYPE_TEXT;
                mTextConfigChanged = false;
            }
            assert count == configs.length;
            Util.ioPool().execute(() -> {
                var service = MuiPlatform.get();
                for (int type : configs) {
                    service.saveConfig(type);
                }
            });
        }
    }

    private class PreferencesAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return 5;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            final int dp20 = container.dp(20);
            final int maxWidth = container.dp(800) + dp20 + dp20;
            var context = container.getContext();
            //noinspection ExtractMethodRecommender
            var sv = new ScrollView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                    if (getChildCount() > 0) {
                        // constrain the direct child
                        final View child = getChildAt(0);

                        int childWidth = child.getMeasuredWidth();
                        int clampedWidth = Math.min(childWidth, maxWidth);

                        if (childWidth != clampedWidth) {
                            var newWidthSpec = MeasureSpec.makeMeasureSpec(clampedWidth, MeasureSpec.EXACTLY);
                            var newHeightSpec = MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY);
                            child.measure(newWidthSpec, newHeightSpec);
                        }
                    }
                }
            };
            sv.setTag(position);
            ViewGroup layout = switch (position) {
                case 0 -> createPage1(context);
                case 1 -> createPage2(context);
                case 2 -> createPage3(context);
                case 3 -> createPage4(context);
                default -> createPage5(context);
            };
            layout.setPadding(dp20, 0, dp20, 0);
            var params = new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
            sv.addView(layout, params);
            //sv.setEdgeEffectColor(ThemeControl.THEME_COLOR);
            //sv.setTopEdgeEffectBlendMode(BlendMode.SRC_OVER);
            //sv.setBottomEdgeEffectBlendMode(BlendMode.SRC_OVER);

            container.addView(sv);

            return sv;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            if (object instanceof View) {
                Object tag = ((View) object).getTag();
                if (tag != null) {
                    return (int) tag;
                }
            }
            return POSITION_NONE;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return switch (position) {
                case 0 -> I18n.get("modernui.center.category.screen");
                case 1 -> "View";
                case 2 -> I18n.get("modernui.center.category.font");
                case 3 -> I18n.get("modernui.center.category.extension");
                default -> "Text (MC)";
            };
        }
    }

    public LinearLayout createPage1(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        Runnable saveFn = mOnClientConfigChanged;

        {
            var list = createCategoryList(content, null);

            list.addView(createGuiScaleOption(context));

            list.addView(createColorOpacityOption(context, "modernui.center.screen.backgroundOpacity",
                    Config.CLIENT.mBackgroundColor, saveFn));

            new IntegerOption(context, "modernui.center.screen.backgroundDuration",
                    50, Config.CLIENT.mBackgroundDuration, saveFn)
                    .create(list, 3);

            list.addView(createBooleanOption(context, "modernui.center.screen.blurEffect",
                    Config.CLIENT.mBlurEffect, saveFn));

            list.addView(createBooleanOption(context, "modernui.center.screen.overrideVanillaBlur",
                    Config.CLIENT.mOverrideVanillaBlur, saveFn));

            new IntegerOption(context, "modernui.center.screen.blurRadius",
                    1, Config.CLIENT.mBlurRadius, saveFn)
                    .create(list, 2);

            list.addView(createSpinnerOption(context, "modernui.center.screen.windowMode",
                    Config.Client.WindowMode.values(), Config.CLIENT.mWindowMode, saveFn));

            new IntegerOption(context, "modernui.center.screen.framerateInactive",
                    10, Config.CLIENT.mFramerateInactive, saveFn)
                    .create(list, 3);

            /*list.addView(createIntegerOption(context, "modernui.center.screen.framerateMinimized",
                    0, 255, 3, 5,
                    Config.CLIENT.mFramerateMinimized, saveFn));*/

            new FloatOption(context, "modernui.center.screen.masterVolumeInactive",
                    Config.CLIENT.mMasterVolumeInactive, 100, saveFn)
                    .create(list, 4);
            new FloatOption(context, "modernui.center.screen.masterVolumeInactive",
                    Config.CLIENT.mMasterVolumeInactive, 100, saveFn)
                    .create(list, 4);

            new FloatOption(context, "modernui.center.screen.masterVolumeMinimized",
                    Config.CLIENT.mMasterVolumeMinimized, 100, saveFn)
                    .create(list, 4);

            list.addView(createBooleanOption(context, "modernui.center.screen.inventoryPause",
                    Config.CLIENT.mInventoryPause, saveFn));

            content.addView(list);
        }

        return content;
    }

    public LinearLayout createPage2(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        Runnable saveFn = mOnClientConfigChanged;

        {
            var list = createCategoryList(content, null);

            new IntegerOption(context, "Scrollbar Size",
                    1, Config.CLIENT.mScrollbarSize, saveFn)
                    .create(list, 4);
            new IntegerOption(context, "Touch Slop",
                     1, Config.CLIENT.mTouchSlop, saveFn)
                    .create(list, 4);
            new IntegerOption(context, "Hover Slop",
                     1, Config.CLIENT.mHoverSlop, saveFn)
                    .create(list, 4);
            new IntegerOption(context, "Minimum Scrollbar Touch Target",
                     1, Config.CLIENT.mMinScrollbarTouchTarget, saveFn)
                    .create(list, 4);
            new IntegerOption(context, "Minimum Fling Velocity",
                     1, Config.CLIENT.mMinimumFlingVelocity, saveFn)
                    .create(list, 4);
            new IntegerOption(context, "Maximum Fling Velocity",
                     1, Config.CLIENT.mMaximumFlingVelocity, saveFn)
                    .create(list, 4);
            new FloatOption(context, "Scroll Friction",
                    Config.CLIENT.mScrollFriction, 1000, saveFn)
                    .create(list, 6);
            new IntegerOption(context, "Overscroll Distance",
                     1, Config.CLIENT.mOverscrollDistance, saveFn)
                    .create(list, 4);
            new IntegerOption(context, "Overfling Distance",
                     1, Config.CLIENT.mOverflingDistance, saveFn)
                    .create(list, 4);
            new FloatOption(context, "Horizontal Scroll Factor",
                    Config.CLIENT.mHorizontalScrollFactor, 10, saveFn)
                    .create(list, 6);
            new FloatOption(context, "Vertical Scroll Factor",
                    Config.CLIENT.mVerticalScrollFactor, 10, saveFn)
                    .create(list, 6);
            new IntegerOption(context, "Hover Tooltip Show Timeout",
                     1, Config.CLIENT.mHoverTooltipShowTimeout, saveFn)
                    .create(list, 4);
            new IntegerOption(context, "Hover Tooltip Hide Timeout",
                     1, Config.CLIENT.mHoverTooltipHideTimeout, saveFn)
                    .create(list, 6);

            content.addView(list);
        }

        {
            var list = createCategoryList(content, "modernui.center.category.system");

            list.addView(createBooleanOption(context, "modernui.center.system.forceRtlLayout",
                    Config.CLIENT.mForceRtl, saveFn));

            new FloatOption(context, "modernui.center.system.globalFontScale",
                    Config.CLIENT.mFontScale, 100, saveFn)
                    .create(list, 4);

            new FloatOption(context, "modernui.center.system.globalAnimationScale",
                    () -> (double) ValueAnimator.sDurationScale,
                    (scale) -> ValueAnimator.sDurationScale = scale.floatValue())
                    .setRange(0.1, 10.0, 100)
                    .setDefaultValue(1.0)
                    .create(list, 4);

            list.addView(createBooleanOption(context, "modernui.center.system.developerMode",
                    Config.COMMON.developerMode, mOnCommonConfigChanged));

            content.addView(list);
        }

        return content;
    }

    public LinearLayout createPage3(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        {
            var category = createCategoryList(content, null);

            {
                var layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);

                final int dp6 = layout.dp(6);
                final LinearLayout firstLine = new LinearLayout(context);
                firstLine.setOrientation(LinearLayout.HORIZONTAL);
                {
                    TextView title = new TextView(context);
                    title.setText(I18n.get("modernui.center.font.firstFont"));
                    title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    title.setTextSize(14);

                    var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
                    firstLine.addView(title, params);
                }
                Runnable onFontChanged;
                {
                    TextView value = new TextView(context);
                    onFontChanged = () -> {
                        FontFamily first = ModernUIClient.getInstance().getFirstFontFamily();
                        if (first != null) {
                            value.setText(first.getFamilyName(value.getTextLocale()));
                        } else {
                            value.setText("NONE");
                        }
                    };
                    onFontChanged.run();
                    value.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                    value.setTextSize(14);

                    var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                    firstLine.addView(value, params);
                }

                firstLine.setOnClickListener(
                        new PreferredFontAccordion(
                                layout,
                                mOnClientConfigChanged,
                                onFontChanged
                        )
                );
                TypedValue value = new TypedValue();
                context.getTheme().resolveAttribute(R.ns, R.attr.colorControlHighlight, value, true);
                firstLine.setBackground(new RippleDrawable(ColorStateList.valueOf(value.data), null,
                        new ColorDrawable(~0)));

                layout.addView(firstLine);

                var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                params.gravity = Gravity.CENTER;
                params.setMargins(dp6, layout.dp(3), dp6, layout.dp(3));

                category.addView(layout, params);
            }

            {
                var option = createStringListOption(context, "modernui.center.font.fallbackFonts",
                        Config.CLIENT.mFallbackFontFamilyList,
                        () -> {
                            mOnClientConfigChanged.run();
                            reloadDefaultTypeface(context, () -> {
                            });
                        });
                category.addView(option);
            }

            /*list.addView(createBooleanOption(context, "modernui.center.font.vanillaFont",
                    ModernUIText.CONFIG.mUseVanillaFont,
                    ModernUIText.CONFIG::saveAndReloadAsync));*/

            category.addView(createBooleanOption(context, "modernui.center.font.colorEmoji",
                    Config.CLIENT.mUseColorEmoji, () -> {
                        mOnClientConfigChanged.run();
                        reloadDefaultTypeface(context, () -> {
                        });
                    }));

            category.addView(createStringListOption(context, "modernui.center.font.fontRegistrationList",
                    Config.CLIENT.mFontRegistrationList, () -> {
                        mClientConfigChanged = true;
                        Toast.makeText(context,
                                        I18n.get("gui.modernui.restart_to_work"),
                                        Toast.LENGTH_SHORT)
                                .show();
                    }));

            content.addView(category);
        }

        return content;
    }

    public LinearLayout createPage4(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        Runnable saveFn = mOnClientConfigChanged;

        boolean curTooltipEnabled = Config.CLIENT.mTooltip.get();

        final View[] tooltipCategories = new View[2];

        {
            var list = createCategoryList(content, null);

            list.addView(createBooleanOption(context, "modernui.center.extension.ding",
                    Config.CLIENT.mDing, saveFn));

            if (Config.CLIENT.mZoom != null) {
                list.addView(createBooleanOption(context, "key.modernui.zoom",
                        Config.CLIENT.mZoom, saveFn));
            }

            list.addView(createBooleanOption(context, "modernui.center.text.emojiShortcodes",
                    Config.CLIENT.mEmojiShortcodes, saveFn));

            {
                var option = createSwitchLayout(context, "modernui.center.extension.smoothScrolling");
                var button = option.<Switch>requireViewById(R.id.button1);
                button.setChecked(!Boolean.parseBoolean(
                        ModernUIClient.getBootstrapProperty(ModernUIMod.BOOTSTRAP_DISABLE_SMOOTH_SCROLLING)
                ));
                button.setOnCheckedChangeListener((view, checked) -> {
                    ModernUIClient.setBootstrapProperty(
                            ModernUIMod.BOOTSTRAP_DISABLE_SMOOTH_SCROLLING,
                            Boolean.toString(!checked)
                    );
                    Toast.makeText(
                            view.getContext(),
                            I18n.get("gui.modernui.restart_to_work"),
                            Toast.LENGTH_SHORT
                    ).show();
                });
                list.addView(option);
            }

            {
                var option = createSwitchLayout(context, "modernui.center.text.enhancedTextField");
                var button = option.<Switch>requireViewById(R.id.button1);
                button.setChecked(!Boolean.parseBoolean(
                        ModernUIClient.getBootstrapProperty(ModernUIMod.BOOTSTRAP_DISABLE_ENHANCED_TEXT_FIELD)
                ));
                button.setOnCheckedChangeListener((view, checked) -> {
                    ModernUIClient.setBootstrapProperty(
                            ModernUIMod.BOOTSTRAP_DISABLE_ENHANCED_TEXT_FIELD,
                            Boolean.toString(!checked)
                    );
                    Toast.makeText(
                            view.getContext(),
                            I18n.get("gui.modernui.restart_to_work"),
                            Toast.LENGTH_SHORT
                    ).show();
                });
                list.addView(option);
            }

            {
                var option = createSwitchLayout(context, "modernui.center.extension.tooltip");
                var button = option.<Switch>requireViewById(R.id.button1);
                button.setChecked(curTooltipEnabled);
                button.setOnCheckedChangeListener((view, checked) -> {
                    Config.CLIENT.mTooltip.set(checked);
                    saveFn.run();
                    for (var v : tooltipCategories) {
                        setViewAndChildrenEnabled(v, checked);
                    }
                });
                list.addView(option);
            }

            /*list.addView(createIntegerOption(context, "modernui.center.extension.tooltipDuration",
                    Config.Client.ANIM_DURATION_MIN, Config.Client.ANIM_DURATION_MAX,
                    3, 50, Config.CLIENT.mTooltipDuration, saveFn));*/

            content.addView(list);
        }

        content.addView(tooltipCategories[0] = createTooltipCategory(content));
        content.addView(tooltipCategories[1] = createTooltipBorderCategory(content));
        for (var v : tooltipCategories) {
            setViewAndChildrenEnabled(v, curTooltipEnabled);
        }

        return content;
    }

    public LinearLayout createPage5(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        boolean curTextEngineEnabled = !Boolean.parseBoolean(
                ModernUIClient.getBootstrapProperty(ModernUIMod.BOOTSTRAP_DISABLE_TEXT_ENGINE));

        final View[] categories = new View[2];

        {
            var list = createCategoryList(content, null);
            {
                var option = createSwitchLayout(context, "modernui.center.text.textEngine");
                var button = option.<Switch>requireViewById(R.id.button1);
                button.setChecked(curTextEngineEnabled);
                button.setOnCheckedChangeListener((view, checked) -> {
                    ModernUIClient.setBootstrapProperty(
                            ModernUIMod.BOOTSTRAP_DISABLE_TEXT_ENGINE,
                            Boolean.toString(!checked)
                    );
                    Toast.makeText(
                            view.getContext(),
                            I18n.get("gui.modernui.restart_to_work"),
                            Toast.LENGTH_SHORT
                    ).show();
                    for (var v : categories) {
                        setViewAndChildrenEnabled(v, checked);
                    }
                });
                list.addView(option);
            }
            content.addView(list);
        }

        content.addView(categories[0] = createTextLayoutCategory(content));
        content.addView(categories[1] = createTextRenderingCategory(content));
        for (var v : categories) {
            setViewAndChildrenEnabled(v, curTextEngineEnabled);
        }

        return content;
    }

    public LinearLayout createTooltipCategory(@NonNull ViewGroup page) {
        var context = page.getContext();
        var category = createCategoryList(page, "modernui.center.category.tooltip");

        Runnable saveFn = mOnClientConfigChanged;

        category.addView(createBooleanOption(context, "modernui.center.tooltip.centerTitle",
                Config.CLIENT.mCenterTooltipTitle, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.tooltip.titleBreak",
                Config.CLIENT.mTooltipTitleBreak, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.tooltip.exactPositioning",
                Config.CLIENT.mExactTooltipPositioning, saveFn));

        new IntegerOption(context, "modernui.center.tooltip.arrowScrollFactor",
                2, Config.CLIENT.mTooltipArrowScrollFactor, saveFn)
                .create(category, 3);

        category.addView(createColorOpacityOption(context, "modernui.center.tooltip.backgroundOpacity",
                Config.CLIENT.mTooltipFill, saveFn));

        return category;
    }

    public LinearLayout createTooltipBorderCategory(@NonNull ViewGroup page) {
        var context = page.getContext();
        var category = createCategoryList(page, null);
        var transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        category.setLayoutTransition(transition);

        {
            final Button title;
            title = new ToggleButton(context, null, R.attr.borderlessButtonStyle);
            title.setText(I18n.get("modernui.center.tooltip.borderStyle"));
            title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            {
                var icon = new StateListDrawable();
                icon.addState(new int[]{R.attr.state_checked}, new BuiltinIconDrawable(
                        context.getResources(), BuiltinIconDrawable.KEYBOARD_ARROW_UP
                ));
                icon.addState(StateSet.WILD_CARD, new BuiltinIconDrawable(
                        context.getResources(), BuiltinIconDrawable.KEYBOARD_ARROW_DOWN
                ));
                icon.setTintList(title.getTextColors());
                title.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        null, null, icon, null
                );
            }
            title.setOnClickListener(new TooltipBorderAccordion(category, mOnClientConfigChanged));

            var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            category.addView(title, params);
        }

        return category;
    }

    public LinearLayout createTextLayoutCategory(@NonNull ViewGroup page) {
        var context = page.getContext();
        var category = createCategoryList(page, "modernui.center.category.textLayout");

        Runnable saveFn = mOnTextConfigChanged;

        category.addView(createSpinnerOption(context, "modernui.center.text.defaultFontBehavior",
                Config.Text.DefaultFontBehavior.values(),
                Config.TEXT.mDefaultFontBehavior, saveFn));

        category.addView(createStringListOption(context, "modernui.center.text.defaultFontRuleSet",
                Config.TEXT.mDefaultFontRuleSet, saveFn));

        category.addView(createSpinnerOption(context, "modernui.center.text.bidiHeuristicAlgo",
                Config.Text.TextDirection.values(),
                Config.TEXT.mTextDirection,
                saveFn));

        category.addView(createSpinnerOption(context, "modernui.center.text.lineBreakStyle",
                Config.Text.LineBreakStyle.values(),
                Config.TEXT.mLineBreakStyle, saveFn));

        category.addView(createSpinnerOption(context, "modernui.center.text.lineBreakWordStyle",
                Config.Text.LineBreakWordStyle.values(),
                Config.TEXT.mLineBreakWordStyle, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.allowAsyncLayout",
                Config.TEXT.mAllowAsyncLayout, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.useComponentCache",
                Config.TEXT.mUseComponentCache, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.fixedResolution",
                Config.TEXT.mFixedResolution, saveFn));

        new FloatOption(context, "modernui.center.text.baseFontSize",
                Config.TEXT.mBaseFontSize, 10, saveFn)
                .create(category, 4);

        new IntegerOption(context, "modernui.center.text.cacheLifespan",
                1, Config.TEXT.mCacheLifespan, saveFn)
                .create(category, 2);

        return category;
    }

    public LinearLayout createTextRenderingCategory(@NonNull ViewGroup page) {
        var context = page.getContext();
        var category = createCategoryList(page, "modernui.center.category.textRendering");

        Runnable saveFn = mOnTextConfigChanged;

        category.addView(createBooleanOption(context, "modernui.center.font.antiAliasing",
                Config.TEXT.mAntiAliasing, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.font.linearMetrics",
                Config.TEXT.mLinearMetrics, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.textShadersInWorld",
                Config.TEXT.mUseTextShadersInWorld, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.allowSDFTextIn2D",
                Config.TEXT.mAllowSDFTextIn2D, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.smartSDFShaders",
                Config.TEXT.mSmartSDFShaders, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.computeDeviceFontSize",
                Config.TEXT.mComputeDeviceFontSize, saveFn));

        new IntegerOption(context, "modernui.center.text.minPixelDensityForSDF",
                1, Config.TEXT.mMinPixelDensityForSDF, saveFn)
                .create(category, 2);

        category.addView(createBooleanOption(context, "modernui.center.font.linearSampling",
                Config.TEXT.mLinearSamplingA8Atlas, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.allowShadow",
                Config.TEXT.mAllowShadow, saveFn));

        new FloatOption(context, "modernui.center.text.shadowOffset",
                Config.TEXT.mShadowOffset, 100, saveFn)
                .create(category, 4);

        new FloatOption(context, "modernui.center.text.baselineShift",
                Config.TEXT.mBaselineShift, 10, saveFn)
                .create(category, 4);

        new FloatOption(context, "modernui.center.text.outlineOffset",
                Config.TEXT.mOutlineOffset, 100, saveFn)
                .create(category, 4);

        return category;
    }

    @NonNull
    public static LinearLayout createCategoryList(@NonNull ViewGroup page,
                                                  @Nullable String name) {
        var context = page.getContext();
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        // no need to clip to padding, disabling it may be faster
        layout.setClipToPadding(false);

        final int dp6 = layout.dp(6);
        final int dp12 = layout.dp(12);
        TypedValue value = new TypedValue();
        if (name != null) {
            var title = new TextView(context);
            title.setId(R.id.title);
            title.setText(I18n.get(name));
            title.setTextSize(16);
            context.getTheme().resolveAttribute(R.ns, R.attr.colorPrimary, value, true);
            title.setTextColor(value.data);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.START;
            params.setMargins(0, page.getChildCount() == 0 ? dp6 : layout.dp(18), 0, 0);
            page.addView(title, params);
        }
        // card
        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp12);
        context.getTheme().resolveAttribute(R.ns, R.attr.colorSurface, value, true);
        bg.setColor(value.data);
        int[] strokeColors = new int[2];
        context.getTheme().resolveAttribute(R.ns, R.attr.colorOutlineVariant, value, true);
        strokeColors[0] = value.data;
        context.getTheme().resolveAttribute(R.ns, R.attr.colorOutline, value, true);
        strokeColors[1] = ColorStateList.modulateColor(value.data, 0.12f);
        bg.setStroke(layout.dp(1), new ColorStateList(
                new int[][]{
                        StateSet.get(StateSet.VIEW_STATE_ENABLED),
                        StateSet.WILD_CARD
                },
                strokeColors
        ));
        layout.setBackground(bg);
        layout.setPadding(dp12, dp12, dp12, dp12);

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, dp6, 0, dp6);
        layout.setLayoutParams(params);

        return layout;
    }

    public static LinearLayout createSwitchLayout(Context context, String name) {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.START);

        final int dp3 = layout.dp(3);
        final int dp6 = layout.dp(6);
        {
            var title = new TextView(context);
            title.setText(I18n.get(name));
            title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            title.setTextSize(14);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(title, params);
        }
        {
            var button = new Switch(context);
            button.setId(R.id.button1);
            //button.setCheckedColor(ThemeControl.THEME_COLOR);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            params.setMargins(0, dp3, 0, dp3);
            layout.addView(button, params);
        }

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp6, 0, dp6, 0);
        layout.setLayoutParams(params);

        String tooltip = name + "_desc";
        if (I18n.exists(tooltip)) {
            layout.setTooltipText(I18n.get(tooltip));
        }
        layout.setMinimumHeight(layout.dp(44));

        return layout;
    }

    public static LinearLayout createBooleanOption(
            Context context,
            String name,
            Config.ConfigItem<Boolean> config,
            Runnable saveFn) {
        var layout = createSwitchLayout(context, name);
        var button = layout.<Switch>requireViewById(R.id.button1);
        button.setChecked(config.get());
        button.setOnCheckedChangeListener((__, checked) -> {
            config.set(checked);
            saveFn.run();
        });
        return layout;
    }

    public static <E extends Enum<E>> LinearLayout createSpinnerOption(
            Context context,
            String name,
            E[] values,
            Config.ConfigItem<E> config,
            Runnable saveFn) {
        var option = new LinearLayout(context);
        option.setOrientation(LinearLayout.HORIZONTAL);
        option.setHorizontalGravity(Gravity.START);

        final int dp6 = option.dp(6);
        {
            var title = new TextView(context);
            title.setText(I18n.get(name));
            title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            title.setTextSize(14);

            String tooltip = name + "_desc";
            if (I18n.exists(tooltip)) {
                title.setTooltipText(I18n.get(tooltip));
            }

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
            params.gravity = Gravity.CENTER_VERTICAL;
            option.addView(title, params);
        }
        {
            var spinner = new Spinner(context);
            //spinner.setGravity(Gravity.END);
            spinner.setAdapter(new ArrayAdapter<>(context, values));
            spinner.setSelection(config.get().ordinal());
            spinner.setOnItemSelectedListener((parent, view, position, id) -> {
                E newValue = values[position];
                if (config.get() != newValue) {
                    config.set(newValue);
                    saveFn.run();
                }
            });

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            option.addView(spinner, params);
        }

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp6, 0, dp6, 0);
        option.setMinimumHeight(option.dp(44));
        option.setLayoutParams(params);

        return option;
    }

    private static LinearLayout createGuiScaleOption(Context context) {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.START);

        final int dp3 = layout.dp(3);
        final int dp6 = layout.dp(6);
        {
            var title = new TextView(context);
            title.setText(I18n.get("options.guiScale"));
            title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            title.setTextSize(14);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
            params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            layout.addView(title, params);
        }

        var slider = new SeekBar(context);
        {
            slider.setClickable(true);
            var params = new LinearLayout.LayoutParams(slider.dp(200), WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(slider, params);
        }

        var tv = new TextView(context);
        {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            tv.setTextSize(14);
            tv.setPadding(dp3, 0, dp3, 0);
            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(tv, params);
        }

        int curValue = Minecraft.getInstance().options.guiScale().get();
        tv.setText(guiScaleToString(curValue));
        tv.setMinWidth(slider.dp(50));

        slider.setMax(MuiModApi.MAX_GUI_SCALE);
        slider.setProgress(curValue);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int newValue = seekBar.getProgress();
                tv.setText(guiScaleToString(newValue));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int newValue = seekBar.getProgress();
                Core.executeOnMainThread(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.options.guiScale().set(newValue);
                    // ensure it's applied
                    if ((int) minecraft.getWindow().getGuiScale() !=
                            minecraft.getWindow().calculateScale(newValue, false)) {
                        minecraft.resizeDisplay();
                    }
                    minecraft.options.save();
                });
                tv.setText(guiScaleToString(newValue));
            }
        });

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp6, 0, dp6, 0);
        layout.setLayoutParams(params);

        return layout;
    }

    private static CharSequence guiScaleToString(int value) {
        int r = MuiModApi.calcGuiScales();
        if (value == 0) { // auto
            int auto = r >> 4 & 0xf;
            return "A (" + auto + ")";
        } else {
            String valueString = Integer.toString(value);
            int min = r >> 8 & 0xf;
            int max = r & 0xf;
            if (value < min || value > max) {
                final String hint;
                if (value < min) {
                    hint = (" (" + min + ")");
                } else {
                    hint = (" (" + max + ")");
                }
                var spannableString = new SpannableString(valueString + hint);
                spannableString.setSpan(
                        new ForegroundColorSpan(0xFFFF5555),
                        0, spannableString.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                return spannableString;
            }
            return valueString;
        }
    }

    @NonNull
    public static LinearLayout createInputBox(Context context, String name) {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.START);

        final int dp6 = layout.dp(6);
        {
            var title = new TextView(context);
            title.setText(I18n.get(name));
            title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            title.setTextSize(14);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
            params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            layout.addView(title, params);
        }
        {
            var input = new EditText(context, null, R.attr.editTextOutlinedStyle);
            input.setId(R.id.input);
            input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(input, params);
        }

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp6, 0, dp6, 0);
        layout.setLayoutParams(params);

        String tooltip = name + "_desc";
        if (I18n.exists(tooltip)) {
            layout.setTooltipText(I18n.get(tooltip));
        }
        layout.setMinimumHeight(layout.dp(44));

        return layout;
    }

    @NonNull
    private static SeekBar addSliderToLayout(@NonNull LinearLayout layout,
                                             boolean discrete) {
        var slider = new SeekBar(layout.getContext(), null, null,
                discrete ? R.style.Widget_Material3_SeekBar_Discrete_Slider : R.style.Widget_Material3_SeekBar_Slider);
        slider.setId(R.id.button2);
        slider.setUserAnimationEnabled(true);
        var params = new LinearLayout.LayoutParams(slider.dp(210), WRAP_CONTENT);
        int dp8 = slider.dp(8);
        params.setMargins(dp8, 0, dp8, 0);
        params.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(slider, 1, params);
        return slider;
    }

    public static final int ID_RESET_TO_DEFAULT = 0x7f000002;

    public static class IntegerOption implements
            View.OnFocusChangeListener,
            SeekBar.OnSeekBarChangeListener,
            View.OnCreateContextMenuListener,
            MenuItem.OnMenuItemClickListener {

        protected final LinearLayout layout;
        protected final EditText input;

        protected final Supplier<Integer> getter;
        protected final Consumer<Integer> setter;
        @Nullable
        protected Runnable onChanged;

        @Nullable
        protected SeekBar slider;
        protected int minValue = Integer.MIN_VALUE;
        protected int maxValue = Integer.MAX_VALUE;
        protected int stepSize = 1;

        protected boolean hasDefaultValue = false;
        protected int defaultValue;

        public IntegerOption(Context context, String name,
                             Supplier<Integer> getter,
                             Consumer<Integer> setter) {
            layout = createInputBox(context, name);
            input = layout.<EditText>requireViewById(R.id.input);
            this.getter = getter;
            this.setter = setter;
        }

        public IntegerOption(Context context, String name,
                             Config.ConfigItem<Integer> config,
                             Consumer<Integer> setter,
                             @Nullable Runnable onChanged) {
            this(context, name, config, setter);
            var range = config.getRange();
            assert range != null;
            minValue = range.getMinimum();
            maxValue = range.getMaximum();
            hasDefaultValue = true;
            defaultValue = config.getDefault();
            this.onChanged = onChanged;
        }

        public IntegerOption(Context context, String name,
                             int stepSize,
                             Config.ConfigItem<Integer> config,
                             Consumer<Integer> setter,
                             @Nullable Runnable onChanged) {
            this(context, name, config, setter, onChanged);
            this.stepSize = stepSize;
        }

        public IntegerOption(Context context, String name,
                             int stepSize,
                             Config.ConfigItem<Integer> config,
                             @Nullable Runnable onChanged) {
            this(context, name, stepSize, config, config, onChanged);
        }

        public IntegerOption setRange(int minValue, int maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            return this;
        }

        public IntegerOption setRange(int minValue, int maxValue,
                                      int stepSize) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.stepSize = stepSize;
            return this;
        }

        public IntegerOption setDefaultValue(int defaultValue) {
            this.hasDefaultValue = true;
            this.defaultValue = defaultValue;
            return this;
        }

        public IntegerOption setOnChanged(Runnable onChanged) {
            this.onChanged = onChanged;
            return this;
        }

        @NonNull
        public LinearLayout create(@Nullable ViewGroup parent) {
            return create(parent, -1);
        }

        @NonNull
        public LinearLayout create(@Nullable ViewGroup parent, int maxLength) {
            int curValue = getter.get();
            int steps = (maxValue - minValue) / stepSize;
            if (steps <= 200) {
                slider = addSliderToLayout(layout, steps <= 10);
                slider.setMax(steps);
                slider.setProgress((curValue - minValue) / stepSize);
                slider.setOnSeekBarChangeListener(this);
            }
            InputFilter digitsFilter = DigitsInputFilter.getInstance((Locale) null);
            if (maxLength > 0) {
                input.setFilters(digitsFilter,
                        new InputFilter.LengthFilter(maxLength));
            } else {
                input.setFilters(digitsFilter);
            }
            input.setText(Integer.toString(curValue));
            input.setOnFocusChangeListener(this);
            input.setMinWidth(slider != null ? slider.dp(60) : input.dp(80));
            if (hasDefaultValue) {
                defaultValue = (defaultValue / stepSize) * stepSize;
                layout.setOnCreateContextMenuListener(this);
            }
            if (parent != null) {
                parent.addView(layout);
            }
            return layout;
        }

        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            assert view == input;
            if (!hasFocus) {
                EditText v = (EditText) view;
                int newValue;
                try {
                    newValue = MathUtil.clamp(Integer.parseInt(v.getText().toString()),
                            minValue, maxValue);
                } catch (NumberFormatException e) {
                    newValue = hasDefaultValue ? defaultValue : minValue;
                }
                replaceText(v, Integer.toString(newValue));
                if (newValue != getter.get()) {
                    setter.accept(newValue);
                    if (slider != null) {
                        int curProgress = (newValue - minValue) / stepSize;
                        slider.setProgress(curProgress, true);
                    }
                    if (onChanged != null) {
                        onChanged.run();
                    }
                }
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            int newValue = seekBar.getProgress() * stepSize + minValue;
            replaceText(input, Integer.toString(newValue));
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int newValue = seekBar.getProgress() * stepSize + minValue;
            if (newValue != getter.get()) {
                setter.accept(newValue);
                replaceText(input, Integer.toString(newValue));
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo) {
            boolean canReset;
            if (slider != null) {
                int curValue = slider.getProgress() * stepSize + minValue;
                canReset = curValue != defaultValue;
            } else {
                try {
                    int curValue = Integer.parseInt(input.getText().toString());
                    canReset = curValue != defaultValue;
                } catch (NumberFormatException e) {
                    canReset = true;
                }
            }
            if (canReset) {
                menu.add(Menu.NONE, ID_RESET_TO_DEFAULT, Menu.CATEGORY_ALTERNATIVE | 0, "Reset to Default")
                        .setOnMenuItemClickListener(this);
            }
        }

        @Override
        public boolean onMenuItemClick(@NonNull MenuItem item) {
            if (item.getItemId() == ID_RESET_TO_DEFAULT) {
                replaceText(input, Integer.toString(defaultValue));
                setter.accept(defaultValue);
                if (slider != null) {
                    int newProgress = (defaultValue - minValue) / stepSize;
                    slider.setProgress(newProgress, true);
                }
                if (onChanged != null) {
                    onChanged.run();
                }
                return true;
            }
            return false;
        }
    }

    public static double getFirstAlphaValue(List<? extends String> colors) {
        if (colors != null && !colors.isEmpty()) {
            try {
                int color = Color.parseColor(colors.get(0));
                return (color >>> 24) / 255.0;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return 1.0;
    }

    public static LinearLayout createColorOpacityOption(
            Context context, String name,
            Config.ConfigItem<List<? extends String>> config,
            Runnable saveFn) {
        Supplier<Double> getter = () -> {
            List<? extends String> colors = config.get();
            return getFirstAlphaValue(colors);
        };
        Consumer<Double> setter = (d) -> {
            int alpha = (int) (d.floatValue() * 255.0f + 0.5f);
            var newList = new ArrayList<String>(config.get());
            if (newList.isEmpty()) {
                newList.add("#FF000000");
            }
            for (var it = newList.listIterator();
                 it.hasNext();
            ) {
                try {
                    int color = Color.parseColor(it.next());
                    color = (color & 0xFFFFFF) | (alpha << 24);
                    it.set(String.format(Locale.ROOT, "#%08X", color));
                } catch (IllegalArgumentException ignored) {
                }
            }
            config.set(newList);
        };
        double defaultValue = getFirstAlphaValue(config.getDefault());
        return new FloatOption(context, name, getter, setter)
                .setRange(0.0, 1.0, 100)
                .setDefaultValue(defaultValue)
                .setOnChanged(saveFn)
                .create(null, 4);
    }

    public static class FloatOption implements
            View.OnFocusChangeListener,
            SeekBar.OnSeekBarChangeListener,
            View.OnCreateContextMenuListener,
            MenuItem.OnMenuItemClickListener {

        protected final LinearLayout layout;
        protected final EditText input;

        protected final Supplier<Double> getter;
        protected final Consumer<Double> setter;
        @Nullable
        protected Runnable onChanged;

        // there is a slider when denominator is not NaN
        @Nullable
        protected SeekBar slider;
        protected double minValue = Double.NEGATIVE_INFINITY;
        protected double maxValue = Double.POSITIVE_INFINITY;
        // 10 means step=0.1, 100 means step=0.01
        protected double denominator = Double.NaN;

        protected double defaultValue = Double.NaN;

        public FloatOption(Context context, String name,
                           Supplier<Double> getter,
                           Consumer<Double> setter) {
            layout = createInputBox(context, name);
            input = layout.<EditText>requireViewById(R.id.input);
            this.getter = getter;
            this.setter = setter;
        }

        public FloatOption(Context context, String name,
                           Config.ConfigItem<Double> config,
                           Consumer<Double> setter,
                           @Nullable Runnable onChanged) {
            this(context, name, config, setter);
            var range = config.getRange();
            assert range != null;
            minValue = range.getMinimum();
            maxValue = range.getMaximum();
            defaultValue = config.getDefault();
            this.onChanged = onChanged;
        }

        public FloatOption(Context context, String name,
                           Config.ConfigItem<Double> config,
                           Consumer<Double> setter,
                           double denominator,
                           @Nullable Runnable onChanged) {
            this(context, name, config, setter, onChanged);
            this.denominator = denominator;
        }

        public FloatOption(Context context, String name,
                           Config.ConfigItem<Double> config,
                           double denominator,
                           @Nullable Runnable onChanged) {
            this(context, name, config, config, denominator, onChanged);
        }

        public FloatOption setRange(double minValue, double maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            return this;
        }

        public FloatOption setRange(double minValue, double maxValue,
                             double denominator) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.denominator = denominator;
            return this;
        }

        public FloatOption setDefaultValue(double defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public FloatOption setOnChanged(Runnable onChanged) {
            this.onChanged = onChanged;
            return this;
        }

        @NonNull
        public LinearLayout create(@Nullable ViewGroup parent) {
            return create(parent, -1);
        }

        @NonNull
        public LinearLayout create(@Nullable ViewGroup parent, int maxLength) {
            double curValue = getter.get();
            if (!Double.isNaN(denominator)) {
                int steps = (int) Math.round((maxValue - minValue) * denominator);
                if (steps <= 200) {
                    slider = addSliderToLayout(layout, steps <= 10);
                    slider.setMax(steps);
                    int curProgress = (int) Math.round((curValue - minValue) * denominator);
                    slider.setProgress(curProgress);
                    slider.setOnSeekBarChangeListener(this);
                }
            }
            InputFilter digitsFilter = DigitsInputFilter.getInstance(
                    null, minValue < 0, true);
            if (maxLength > 0) {
                input.setFilters(digitsFilter,
                        new InputFilter.LengthFilter(maxLength));
            } else {
                input.setFilters(digitsFilter);
            }
            input.setText(floatValueToString(curValue, denominator));
            input.setOnFocusChangeListener(this);
            input.setMinWidth(slider != null ? slider.dp(60) : input.dp(80));
            if (!Double.isNaN(defaultValue)) {
                if (!Double.isNaN(denominator)) {
                    defaultValue = Math.round(defaultValue * denominator) / denominator;
                }
                layout.setOnCreateContextMenuListener(this);
            }
            if (parent != null) {
                parent.addView(layout);
            }
            return layout;
        }

        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            assert view == input;
            if (!hasFocus) {
                EditText v = (EditText) view;
                double newValue;
                try {
                    newValue = MathUtil.clamp(Double.parseDouble(v.getText().toString()),
                            minValue, maxValue);
                } catch (NumberFormatException e) {
                    newValue = defaultValue;
                }
                if (Double.isNaN(newValue)) {
                    newValue = minValue;
                }
                replaceText(v, floatValueToString(newValue, denominator));
                if (newValue != getter.get()) {
                    setter.accept(newValue);
                    if (slider != null) {
                        int curProgress = (int) Math.round((newValue - minValue) * denominator);
                        slider.setProgress(curProgress, true);
                    }
                    if (onChanged != null) {
                        onChanged.run();
                    }
                }
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            double newValue = seekBar.getProgress() / denominator + minValue;
            replaceText(input, floatValueToString(newValue, denominator));
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            double newValue = seekBar.getProgress() / denominator + minValue;
            if (newValue != getter.get()) {
                setter.accept(newValue);
                replaceText(input, floatValueToString(newValue, denominator));
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo) {
            boolean canReset;
            if (slider != null) {
                double curValue = slider.getProgress() / denominator + minValue;
                canReset = curValue != defaultValue;
            } else {
                try {
                    double curValue = Double.parseDouble(input.getText().toString());
                    canReset = curValue != defaultValue;
                } catch (NumberFormatException e) {
                    canReset = true;
                }
            }
            if (canReset) {
                menu.add(Menu.NONE, ID_RESET_TO_DEFAULT, Menu.CATEGORY_ALTERNATIVE | 0, "Reset to Default")
                        .setOnMenuItemClickListener(this);
            }
        }

        @Override
        public boolean onMenuItemClick(@NonNull MenuItem item) {
            if (item.getItemId() == ID_RESET_TO_DEFAULT) {
                replaceText(input, floatValueToString(defaultValue, denominator));
                setter.accept(defaultValue);
                if (slider != null) {
                    int newProgress = (int) Math.round((defaultValue - minValue) * denominator);
                    slider.setProgress(newProgress, true);
                }
                if (onChanged != null) {
                    onChanged.run();
                }
                return true;
            }
            return false;
        }

        private static String floatValueToString(double value, double denominator) {
            if (Double.isNaN(denominator))
                return Double.toString(value);
            return Double.toString(Math.round(value * denominator) / denominator);
        }
    }

    public static LinearLayout createStringListOption(Context context,
                                                      String name,
                                                      Config.ConfigItem<List<? extends String>> config,
                                                      Runnable saveFn) {
        var option = new LinearLayout(context);
        option.setOrientation(LinearLayout.HORIZONTAL);
        option.setHorizontalGravity(Gravity.START);

        final int dp3 = option.dp(3);
        {
            var title = new TextView(context);
            title.setText(I18n.get(name));
            title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            title.setTextSize(14);
            title.setMinWidth(option.dp(60));

            String tooltip = name + "_desc";
            if (I18n.exists(tooltip)) {
                title.setTooltipText(I18n.get(tooltip));
            }

            var params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 2);
            params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            option.addView(title, params);
        }
        {
            var input = new EditText(context, null, R.attr.editTextOutlinedStyle);
            input.setId(R.id.input);
            input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);

            input.setText(String.join("\n", config.get()));
            input.setOnFocusChangeListener((view, hasFocus) -> {
                if (!hasFocus) {
                    EditText v = (EditText) view;
                    ArrayList<String> result = new ArrayList<>();
                    for (String s : v.getText().toString().split("\n")) {
                        if (!s.isBlank()) {
                            String strip = s.strip();
                            if (!strip.isEmpty() && !result.contains(strip)) {
                                result.add(strip);
                            }
                        }
                    }
                    replaceText(v, String.join("\n", result));
                    if (!Objects.equals(config.get(), result)) {
                        config.set(result);
                        saveFn.run();
                    }
                }
            });

            var params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 5);
            params.gravity = Gravity.CENTER_VERTICAL;
            option.addView(input, params);
        }

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(option.dp(6), dp3, option.dp(6), dp3);
        option.setLayoutParams(params);

        return option;
    }

    private static void replaceText(@NonNull EditText editText, @NonNull CharSequence newText) {
        Editable text = editText.getText();
        text.replace(0, text.length(), newText);
    }

    static void reloadDefaultTypeface(@NonNull Context context, @NonNull Runnable onFontChanged) {
        var future = Minecraft.getInstance().submit(() -> {
            var oldTypeface = ModernUI.getSelectedTypeface();
            var client = ModernUIClient.getInstance();
            client.reloadTypeface();
            client.reloadFontStrike();
            return oldTypeface;
        });
        future.whenCompleteAsync((oldTypeface, throwable) -> {
            if (throwable == null) {
                onFontChanged.run();
                refreshViewTypeface(
                        UIManager.getInstance().getDecorView(),
                        oldTypeface
                );
                Toast.makeText(context,
                        I18n.get("gui.modernui.font_reloaded"),
                        Toast.LENGTH_SHORT).show();
            }
        }, Core.getUiThreadExecutor());
    }

    private static void refreshViewTypeface(@NonNull ViewGroup vg,
                                            Typeface oldTypeface) {
        int cc = vg.getChildCount();
        for (int i = 0; i < cc; i++) {
            View v = vg.getChildAt(i);
            if (v instanceof ViewGroup) {
                refreshViewTypeface((ViewGroup) v, oldTypeface);
            } else if (v instanceof TextView tv) {
                if (tv.getTypeface() == oldTypeface) {
                    tv.setTypeface(ModernUI.getSelectedTypeface());
                }
            }
        }
    }

    static void setViewAndChildrenEnabled(View view, boolean enabled) {
        if (view != null) {
            view.setEnabled(enabled);
        }
        if (view instanceof ViewGroup vg) {
            int cc = vg.getChildCount();
            for (int i = 0; i < cc; i++) {
                View v = vg.getChildAt(i);
                setViewAndChildrenEnabled(v, enabled);
            }
        }
    }
}
