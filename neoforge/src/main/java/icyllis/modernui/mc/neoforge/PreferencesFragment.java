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
import icyllis.modernui.mc.ModernUIClient;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.mc.ui.FourColorPicker;
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
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.OneShotPreDrawListener;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class PreferencesFragment extends Fragment {

    boolean mClientConfigChanged;
    boolean mTextConfigChanged;

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
        if (mClientConfigChanged) {
            Config.CLIENT.saveAsync();
            mClientConfigChanged = false;
        }
        if (mTextConfigChanged) {
            Config.TEXT.saveAsync();
            mTextConfigChanged = false;
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
            var context = container.getContext();
            var sv = new ScrollView(context);
            sv.setTag(position);
            ViewGroup layout = switch (position) {
                case 0 -> createPage1(context);
                case 1 -> createPage2(context);
                case 2 -> createPage3(context);
                case 3 -> createPage4(context);
                default -> createPage5(context);
            };
            layout.setMinimumWidth(layout.dp(720));
            var params = new ScrollView.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
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

            list.addView(createIntegerOption(context, "modernui.center.screen.backgroundDuration",
                    Config.Client.ANIM_DURATION_MIN, Config.Client.ANIM_DURATION_MAX,
                    3, 50, Config.CLIENT.mBackgroundDuration, saveFn));

            list.addView(createBooleanOption(context, "modernui.center.screen.blurEffect",
                    Config.CLIENT.mBlurEffect, saveFn));

            list.addView(createBooleanOption(context, "modernui.center.screen.overrideVanillaBlur",
                    Config.CLIENT.mOverrideVanillaBlur, saveFn));

            list.addView(createIntegerOption(context, "modernui.center.screen.blurRadius",
                    Config.Client.BLUR_RADIUS_MIN, Config.Client.BLUR_RADIUS_MAX,
                    2, 1, Config.CLIENT.mBlurRadius, saveFn));

            list.addView(createSpinnerOption(context, "modernui.center.screen.windowMode",
                    Config.Client.WindowMode.values(), Config.CLIENT.mWindowMode, saveFn));

            list.addView(createIntegerOption(context, "modernui.center.screen.framerateInactive",
                    0, 250, 3, 10,
                    Config.CLIENT.mFramerateInactive, saveFn));

            /*list.addView(createIntegerOption(context, "modernui.center.screen.framerateMinimized",
                    0, 255, 3, 5,
                    Config.CLIENT.mFramerateMinimized, saveFn));*/

            list.addView(createFloatOption(context, "modernui.center.screen.masterVolumeInactive",
                    0, 1, 4,
                    Config.CLIENT.mMasterVolumeInactive, 100, saveFn));

            list.addView(createFloatOption(context, "modernui.center.screen.masterVolumeMinimized",
                    0, 1, 4,
                    Config.CLIENT.mMasterVolumeMinimized, 100, saveFn));

            list.addView(createBooleanOption(context, "modernui.center.screen.inventoryPause",
                    Config.CLIENT.mInventoryPause, saveFn));

            content.addView(list);
        }

        return content;
    }

    public LinearLayout createPage2(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        {
            var list = createCategoryList(content, "modernui.center.category.system");

            list.addView(createBooleanOption(context, "modernui.center.system.forceRtlLayout",
                    Config.CLIENT.mForceRtl, Config.CLIENT::saveAndReloadAsync));

            list.addView(createFloatOption(context, "modernui.center.system.globalFontScale",
                    Config.Client.FONT_SCALE_MIN, Config.Client.FONT_SCALE_MAX,
                    4, Config.CLIENT.mFontScale, 10, Config.CLIENT::saveAndReloadAsync));

            {
                var option = createInputBox(context, "modernui.center.system.globalAnimationScale");
                var input = option.<EditText>requireViewById(R.id.input);
                input.setText(Float.toString(ValueAnimator.sDurationScale));
                input.setFilters(DigitsInputFilter.getInstance(null, false, true),
                        new InputFilter.LengthFilter(4));
                input.setOnFocusChangeListener((view, hasFocus) -> {
                    if (!hasFocus) {
                        EditText v = (EditText) view;
                        double scale = Math.max(Math.min(Double.parseDouble(v.getText().toString()), 10), 0.1);
                        v.setText(Double.toString(scale));
                        if (scale != ValueAnimator.sDurationScale) {
                            ValueAnimator.sDurationScale = (float) scale;
                        }
                    }
                });
                list.addView(option);
            }

            list.addView(createBooleanOption(context, "modernui.center.system.developerMode",
                    Config.COMMON.developerMode, Config.COMMON::saveAndReloadAsync));

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
                        new PreferredFontCollapsed(
                                layout,
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
                            Config.CLIENT.saveAndReloadAsync();
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
                        Config.CLIENT.saveAndReloadAsync();
                        reloadDefaultTypeface(context, () -> {
                        });
                    }));

            category.addView(createStringListOption(context, "modernui.center.font.fontRegistrationList",
                    Config.CLIENT.mFontRegistrationList, () -> {
                        Config.CLIENT.saveAsync();
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

            list.addView(createBooleanOption(context, "key.modernui.zoom",
                    Config.CLIENT.mZoom, saveFn));

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

        category.addView(createIntegerOption(context, "modernui.center.tooltip.arrowScrollFactor",
                Config.Client.TOOLTIP_ARROW_SCROLL_FACTOR_MIN, Config.Client.TOOLTIP_ARROW_SCROLL_FACTOR_MAX,
                3, 1, Config.CLIENT.mTooltipArrowScrollFactor, saveFn));

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
            title = new ToggleButton(context, null, null, R.style.Widget_Material3_Button_TextButton);
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
            title.setOnClickListener(new TooltipBorderCollapsed(category, mOnClientConfigChanged));

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

        category.addView(createFloatOption(context, "modernui.center.text.baseFontSize",
                Config.Text.BASE_FONT_SIZE_MIN, Config.Text.BASE_FONT_SIZE_MAX,
                5, Config.TEXT.mBaseFontSize, 10, saveFn));

        category.addView(createIntegerOption(context, "modernui.center.text.cacheLifespan",
                Config.Text.LIFESPAN_MIN, Config.Text.LIFESPAN_MAX,
                2, 1,
                Config.TEXT.mCacheLifespan, saveFn));

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

        category.addView(createIntegerOption(context, "modernui.center.text.minPixelDensityForSDF",
                4, 10,
                2, 1, Config.TEXT.mMinPixelDensityForSDF, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.font.linearSampling",
                Config.TEXT.mLinearSamplingA8Atlas, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.allowShadow",
                Config.TEXT.mAllowShadow, saveFn));

        category.addView(createFloatOption(context, "modernui.center.text.shadowOffset",
                Config.Text.SHADOW_OFFSET_MIN, Config.Text.SHADOW_OFFSET_MAX,
                5, Config.TEXT.mShadowOffset, 100, saveFn));

        category.addView(createFloatOption(context, "modernui.center.text.baselineShift",
                Config.Text.BASELINE_MIN, Config.Text.BASELINE_MAX,
                5, Config.TEXT.mBaselineShift, 10, saveFn));

        category.addView(createFloatOption(context, "modernui.center.text.outlineOffset",
                Config.Text.OUTLINE_OFFSET_MIN, Config.Text.OUTLINE_OFFSET_MAX,
                5, Config.TEXT.mOutlineOffset, 100, saveFn));

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
            ModConfigSpec.BooleanValue config,
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
            ModConfigSpec.EnumValue<E> config,
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
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    E newValue = values[position];
                    if (config.get() != newValue) {
                        config.set(newValue);
                        saveFn.run();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
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
            var input = new EditText(context, null, null,
                    R.style.Widget_Material3_EditText_OutlinedBox);
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

    public static LinearLayout createInputBoxWithSlider(Context context, String name,
                                                        boolean discrete) {
        var layout = createInputBox(context, name);
        var slider = new SeekBar(context, null, null,
                discrete ? R.style.Widget_Material3_SeekBar_Discrete_Slider : R.style.Widget_Material3_SeekBar_Slider);
        slider.setId(R.id.button2);
        slider.setClickable(true);
        slider.setUserAnimationEnabled(true);
        var params = new LinearLayout.LayoutParams(slider.dp(210), WRAP_CONTENT);
        params.setMarginEnd(slider.dp(8));
        params.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(slider, 1, params);
        return layout;
    }

    public static LinearLayout createIntegerOption(Context context, String name,
                                                   int minValue, int maxValue, int maxLength, int stepSize,
                                                   ModConfigSpec.IntValue config,
                                                   Runnable saveFn) {
        return createIntegerOption(context, name,
                minValue, maxValue, maxLength, stepSize,
                config, config::set, saveFn);
    }

    public static LinearLayout createIntegerOption(Context context, String name,
                                                   int minValue, int maxValue, int maxLength, int stepSize,
                                                   Supplier<Integer> getter, Consumer<Integer> setter,
                                                   Runnable saveFn) {
        var layout = createInputBoxWithSlider(context, name, (maxValue - minValue) / stepSize <= 10);
        var slider = layout.<SeekBar>requireViewById(R.id.button2);
        var input = layout.<EditText>requireViewById(R.id.input);
        input.setFilters(DigitsInputFilter.getInstance((Locale) null),
                new InputFilter.LengthFilter(maxLength));
        int curValue = getter.get();
        input.setText(Integer.toString(curValue));
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                EditText v = (EditText) view;
                int newValue = MathUtil.clamp(Integer.parseInt(v.getText().toString()),
                        minValue, maxValue);
                replaceText(v, Integer.toString(newValue));
                if (newValue != getter.get()) {
                    setter.accept(newValue);
                    int curProgress = (newValue - minValue) / stepSize;
                    slider.setProgress(curProgress, true);
                    saveFn.run();
                }
            }
        });
        input.setMinWidth(slider.dp(60));
        int steps = (maxValue - minValue) / stepSize;
        slider.setMax(steps);
        slider.setProgress((curValue - minValue) / stepSize);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
                    saveFn.run();
                }
            }
        });
        return layout;
    }

    public static LinearLayout createColorOpacityOption(
            Context context, String name,
            ModConfigSpec.ConfigValue<List<? extends String>> config,
            Runnable saveFn) {
        Supplier<Double> getter = () -> {
            List<? extends String> colors = config.get();
            if (colors != null && !colors.isEmpty()) {
                try {
                    int color = Color.parseColor(colors.get(0));
                    return (double) ((color >>> 24) / 255.0f);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
            return 1.0;
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
                int color = Color.parseColor(it.next());
                color = (color & 0xFFFFFF) | (alpha << 24);
                it.set(String.format(Locale.ROOT, "#%08X", color));
            }
            config.set(newList);
        };
        return createFloatOption(context, name, 0, 1, 4,
                getter, setter, 100, saveFn);
    }

    public static LinearLayout createFloatOption(Context context, String name,
                                                 float minValue, float maxValue, int maxLength,
                                                 ModConfigSpec.DoubleValue config,
                                                 float denominator,
                                                 Runnable saveFn) {
        return createFloatOption(context, name, minValue, maxValue, maxLength,
                config, config::set, denominator, saveFn);
    }

    private static String floatValueToString(float value, float denominator) {
        return Float.toString(Math.round(value * denominator) / denominator);
    }

    public static LinearLayout createFloatOption(Context context, String name,
                                                 float minValue, float maxValue, int maxLength,
                                                 Supplier<Double> getter, Consumer<Double> setter,
                                                 float denominator, // 10 means step=0.1, 100 means step=0.01
                                                 Runnable saveFn) {
        var layout = createInputBoxWithSlider(context, name, (maxValue - minValue) * denominator <= 10);
        var slider = layout.<SeekBar>requireViewById(R.id.button2);
        var input = layout.<EditText>requireViewById(R.id.input);
        input.setFilters(DigitsInputFilter.getInstance(null, minValue < 0, true),
                new InputFilter.LengthFilter(maxLength));
        float curValue = getter.get().floatValue();
        input.setText(floatValueToString(curValue, denominator));
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                EditText v = (EditText) view;
                float newValue = MathUtil.clamp(Float.parseFloat(v.getText().toString()),
                        minValue, maxValue);
                replaceText(v, floatValueToString(newValue, denominator));
                if (newValue != getter.get().floatValue()) {
                    setter.accept((double) newValue);
                    int curProgress = Math.round((newValue - minValue) * denominator);
                    slider.setProgress(curProgress, true);
                    saveFn.run();
                }
            }
        });
        input.setMinWidth(slider.dp(60));
        int steps = Math.round((maxValue - minValue) * denominator);
        slider.setMax(steps);
        int curProgress = Math.round((curValue - minValue) * denominator);
        slider.setProgress(curProgress);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float newValue = seekBar.getProgress() / denominator + minValue;
                replaceText(input, floatValueToString(newValue, denominator));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float newValue = seekBar.getProgress() / denominator + minValue;
                if (newValue != getter.get().floatValue()) {
                    setter.accept((double) newValue);
                    replaceText(input, floatValueToString(newValue, denominator));
                    saveFn.run();
                }
            }
        });
        return layout;
    }

    public static LinearLayout createStringListOption(Context context,
                                                      String name,
                                                      ModConfigSpec.ConfigValue<List<? extends String>> config,
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
            var input = new EditText(context, null, null,
                    R.style.Widget_Material3_EditText_OutlinedBox);
            input.setId(R.id.input);
            input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            input.setTextSize(14);
            input.setPadding(dp3, 0, dp3, 0);

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

    public static class TooltipBorderCollapsed implements View.OnClickListener {

        public static final String[][] PRESET_COLORS = {
                {"#F0AADCF0", "#F0FFC3F7", "#F0BFF2B2", "#F0D27F3D"},
                {"#F0AADCF0", "#F0DAD0F4", "#F0FFC3F7", "#F0DAD0F4"},
                {"#F028007F", "#F028007F", "#F014003F", "#F014003F"},
                {"#F0E0E0E0", "#F0B0B0B0", "#F0FFFFFF", "#F0B0B0B0"}
        };

        final ViewGroup mParent;
        final Runnable mSaveFn;

        // lazy-init
        LinearLayout mContent;
        FourColorPicker mColorPicker;

        ViewGroup mBorderWidth;
        ViewGroup mCornerRadius;
        ViewGroup mShadowRadius;
        ViewGroup mShadowAlpha;

        // this callback is registered on a child view of 'parent'
        // so no weak ref
        public TooltipBorderCollapsed(ViewGroup parent, Runnable saveFn) {
            mParent = parent;
            mSaveFn = saveFn;
        }

        @Override
        public void onClick(View v) {
            if (mContent != null) {
                // toggle
                mContent.setVisibility(mContent.getVisibility() == View.GONE
                        ? View.VISIBLE
                        : View.GONE);
                return;
            }
            mContent = new LinearLayout(mParent.getContext());
            mContent.setOrientation(LinearLayout.VERTICAL);
            final boolean rounded;
            {
                var option = createSwitchLayout(mParent.getContext(), "modernui.center.tooltip.roundedShapes");
                var button = option.<Switch>requireViewById(R.id.button1);
                button.setChecked(rounded = Config.CLIENT.mRoundedTooltip.get());
                button.setOnCheckedChangeListener((__, checked) -> {
                    Config.CLIENT.mRoundedTooltip.set(checked);
                    setViewAndChildrenEnabled(mBorderWidth, checked);
                    setViewAndChildrenEnabled(mCornerRadius, checked);
                    setViewAndChildrenEnabled(mShadowRadius, checked);
                    setViewAndChildrenEnabled(mShadowAlpha, checked);
                    mSaveFn.run();
                });
                mContent.addView(option);
            }
            {
                var option = createFloatOption(mParent.getContext(), "modernui.center.tooltip.borderWidth",
                        Config.Client.TOOLTIP_BORDER_WIDTH_MIN, Config.Client.TOOLTIP_BORDER_WIDTH_MAX,
                        4, Config.CLIENT.mTooltipWidth, (width) -> {
                            Config.CLIENT.mTooltipWidth.set(width);
                            if (mColorPicker != null) {
                                mColorPicker.setBorderWidth(width.floatValue());
                            }
                        }, 100, mSaveFn);
                if (!rounded) {
                    setViewAndChildrenEnabled(option, false);
                }
                mBorderWidth = option;
                mContent.addView(option);
            }
            {
                var option = createFloatOption(mParent.getContext(), "modernui.center.tooltip.cornerRadius",
                        Config.Client.TOOLTIP_CORNER_RADIUS_MIN, Config.Client.TOOLTIP_CORNER_RADIUS_MAX,
                        3, Config.CLIENT.mTooltipRadius, (radius) -> {
                            Config.CLIENT.mTooltipRadius.set(radius);
                            if (mColorPicker != null) {
                                mColorPicker.setBorderRadius(radius.floatValue());
                            }
                        }, 10, mSaveFn);
                if (!rounded) {
                    setViewAndChildrenEnabled(option, false);
                }
                mCornerRadius = option;
                mContent.addView(option);
            }
            {
                var option = createFloatOption(mParent.getContext(), "modernui.center.tooltip.shadowRadius",
                        Config.Client.TOOLTIP_SHADOW_RADIUS_MIN, Config.Client.TOOLTIP_SHADOW_RADIUS_MAX,
                        4, Config.CLIENT.mTooltipShadowRadius, 10, mSaveFn);
                if (!rounded) {
                    setViewAndChildrenEnabled(option, false);
                }
                mShadowRadius = option;
                mContent.addView(option);
            }
            {
                var option = createFloatOption(mParent.getContext(), "modernui.center.tooltip.shadowOpacity",
                        0F, 1F,
                        4, Config.CLIENT.mTooltipShadowAlpha, 100, mSaveFn);
                if (!rounded) {
                    setViewAndChildrenEnabled(option, false);
                }
                mShadowAlpha = option;
                mContent.addView(option);
            }
            mContent.addView(createBooleanOption(mParent.getContext(), "modernui.center.tooltip.adaptiveColors",
                    Config.CLIENT.mAdaptiveTooltipColors, mSaveFn));
            mContent.addView(createIntegerOption(mParent.getContext(), "modernui.center.tooltip.borderCycle",
                    Config.Client.TOOLTIP_BORDER_COLOR_ANIM_MIN, Config.Client.TOOLTIP_BORDER_COLOR_ANIM_MAX,
                    4, 100, Config.CLIENT.mTooltipCycle, mSaveFn));
            var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.setMargins(0, mContent.dp(6), 0, 0);
            int dp4 = mContent.dp(4);
            {
                var buttonGroup = new LinearLayout(mParent.getContext());
                buttonGroup.setOrientation(LinearLayout.HORIZONTAL);
                for (int i = 0; i < 4; i++) {
                    var button = new Button(mParent.getContext(), null, null,
                            R.style.Widget_Material3_Button_OutlinedButton);
                    button.setText(I18n.get("gui.modernui.preset_s", (i + 1)));
                    final int idx = i;
                    button.setOnClickListener((__) -> mColorPicker.setColors(
                            PRESET_COLORS[idx])
                    );
                    var p = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
                    p.setMargins(dp4, 0, dp4, 0);
                    buttonGroup.addView(button, p);
                }
                mContent.addView(buttonGroup, new LinearLayout.LayoutParams(params));
            }
            mContent.addView(mColorPicker = new FourColorPicker(mParent.getContext(),
                    Config.CLIENT.mTooltipStroke, Config.CLIENT.mTooltipStroke::set,
                    mSaveFn), new LinearLayout.LayoutParams(params));
            mColorPicker.setBorderRadius(Config.CLIENT.mTooltipRadius.get().floatValue());
            mColorPicker.setBorderWidth(Config.CLIENT.mTooltipWidth.get().floatValue());
            mParent.addView(mContent, params);
        }
    }

    public static class PreferredFontCollapsed implements View.OnClickListener {

        final ViewGroup mParent;
        final Runnable mOnFontChanged;

        // lazy-init
        LinearLayout mContent;
        EditText mInput;
        Spinner mSpinner;

        public PreferredFontCollapsed(ViewGroup parent, Runnable onFontChanged) {
            mParent = parent;
            mOnFontChanged = onFontChanged;
        }

        @Override
        public void onClick(View v) {
            if (mContent != null) {
                // toggle
                mContent.setVisibility(mContent.getVisibility() == View.GONE
                        ? View.VISIBLE
                        : View.GONE);
                return;
            }
            mContent = new LinearLayout(mParent.getContext());
            mContent.setOrientation(LinearLayout.VERTICAL);
            var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.setMargins(0, mContent.dp(6), 0, 0);
            {
                var layout = createInputBox(mParent.getContext(), "gui.modernui.configValue");
                var input = mInput = layout.requireViewById(R.id.input);
                input.setText(Config.CLIENT.mFirstFontFamily.get());
                input.setOnFocusChangeListener((view, hasFocus) -> {
                    if (!hasFocus) {
                        EditText v1 = (EditText) view;
                        String newValue = v1.getText().toString().strip();
                        applyNewValue(v1.getContext(), newValue);
                    }
                });
                mContent.addView(layout);
            }
            {
                var spinner = mSpinner = new Spinner(mParent.getContext());
                CompletableFuture.supplyAsync(() -> {
                    var values = FontFamily.getSystemFontMap()
                            .values()
                            .stream()
                            .map(family -> new FontFamilyItem(family.getFamilyName(),
                                    family.getFamilyName(ModernUI.getSelectedLocale())))
                            .sorted()
                            .collect(Collectors.toList());
                    String chooseFont = I18n.get("modernui.center.font.chooseFont");
                    values.add(0, new FontFamilyItem(chooseFont, chooseFont));
                    return values;
                }).thenAcceptAsync(values -> {
                    mSpinner.setAdapter(new FontFamilyAdapter(mParent.getContext(), values));
                    mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position == 0) {
                                return;
                            }
                            String newValue = values.get(position).rootName;
                            boolean changed = applyNewValue(view.getContext(), newValue);
                            if (changed) {
                                mInput.setText(newValue);
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    });
                    FontFamily first = ModernUIClient.getInstance().getFirstFontFamily();
                    if (first != null) {
                        for (int i = 1; i < values.size(); i++) {
                            var candidate = values.get(i);
                            if (candidate.rootName
                                    .equalsIgnoreCase(
                                            first.getFamilyName()
                                    )) {
                                mSpinner.setSelection(i);
                                break;
                            }
                        }
                    }
                }, Core.getUiThreadExecutor());

                mContent.addView(spinner, new LinearLayout.LayoutParams(params));
            }
            {
                Button openFile = new Button(mParent.getContext());
                openFile.setText(I18n.get("modernui.center.font.openFontFile"));
                openFile.setTextSize(14);
                openFile.setOnClickListener(v1 -> CompletableFuture.runAsync(() -> {
                    String path;
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        PointerBuffer filters = stack.mallocPointer(4);
                        stack.nUTF8("*.ttf", true);
                        filters.put(stack.getPointerAddress());
                        stack.nUTF8("*.otf", true);
                        filters.put(stack.getPointerAddress());
                        stack.nUTF8("*.ttc", true);
                        filters.put(stack.getPointerAddress());
                        stack.nUTF8("*.otc", true);
                        filters.put(stack.getPointerAddress());
                        filters.rewind();
                        path = TinyFileDialogs.tinyfd_openFileDialog(null, null,
                                filters, "TrueType/OpenType Fonts (*.ttf;*.otf;*.ttc;*.otc)", false);
                    }
                    if (path != null) {
                        v1.post(() -> {
                            boolean changed = applyNewValue(v1.getContext(), path);
                            if (changed) {
                                mInput.setText(path);
                            }
                            mSpinner.setSelection(0);
                        });
                    }
                }));
                mContent.addView(openFile, new LinearLayout.LayoutParams(params));
            }
            mParent.addView(mContent, params);
        }

        public record FontFamilyItem(String rootName, String localeName)
                implements Comparable<FontFamilyItem> {

            @Override
            public String toString() {
                return localeName;
            }

            @Override
            public int compareTo(@NonNull FontFamilyItem o) {
                return localeName.compareTo(o.localeName);
            }
        }

        private static class FontFamilyAdapter extends ArrayAdapter<FontFamilyItem> {

            private final Context mContext;

            public FontFamilyAdapter(Context context, @NonNull List<FontFamilyItem> objects) {
                super(context, objects);
                mContext = context;
            }

            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView,
                                @NonNull ViewGroup parent) {
                final TextView tv;

                if (convertView == null) {
                    tv = new TextView(mContext);
                } else {
                    tv = (TextView) convertView;
                }

                final FontFamilyItem item = getItem(position);
                tv.setText(item.localeName);

                tv.setTextSize(14);
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                final int dp4 = tv.dp(4);
                tv.setPadding(dp4, dp4, dp4, dp4);

                return tv;
            }

            @NonNull
            @Override
            public View getDropDownView(int position, @Nullable View convertView,
                                        @NonNull ViewGroup parent) {
                return getView(position, convertView, parent);
            }
        }

        private boolean applyNewValue(Context context, @NonNull String newValue) {
            if (!newValue.equals(Config.CLIENT.mFirstFontFamily.get())) {
                Config.CLIENT.mFirstFontFamily.set(newValue);
                Config.CLIENT.saveAndReloadAsync();
                reloadDefaultTypeface(context, mOnFontChanged);
                return true;
            }
            return false;
        }
    }

    private static void replaceText(@NonNull EditText editText, @NonNull CharSequence newText) {
        Editable text = editText.getText();
        text.replace(0, text.length(), newText);
    }

    private static void reloadDefaultTypeface(@NonNull Context context, @NonNull Runnable onFontChanged) {
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

    private static void setViewAndChildrenEnabled(View view, boolean enabled) {
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
