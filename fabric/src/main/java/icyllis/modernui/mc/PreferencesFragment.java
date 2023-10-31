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

import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.graphics.text.FontFamily;
import icyllis.modernui.mc.ui.FourColorPicker;
import icyllis.modernui.mc.ui.ThemeControl;
import icyllis.modernui.text.*;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.common.ForgeConfigSpec;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class PreferencesFragment extends Fragment {

    LinearLayout mTooltipCategory;
    LinearLayout mTextEngineCategory;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var pager = new ViewPager(getContext());

        pager.setAdapter(this.new ThePagerAdapter());
        pager.setFocusableInTouchMode(true);
        pager.setKeyboardNavigationCluster(true);

        pager.setEdgeEffectColor(ThemeControl.THEME_COLOR);

        {
            var indicator = new LinearPagerIndicator(getContext());
            indicator.setPager(pager);
            indicator.setLineWidth(pager.dp(4));
            indicator.setLineColor(ThemeControl.THEME_COLOR);
            var lp = new ViewPager.LayoutParams();
            lp.height = pager.dp(30);
            lp.isDecor = true;
            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            pager.addView(indicator, lp);
        }

        var lp = new FrameLayout.LayoutParams(pager.dp(720), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        pager.setLayoutParams(lp);

        return pager;
    }

    private class ThePagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return 2;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            var context = container.getContext();
            var sv = new ScrollView(context);
            if (position == 1) {
                sv.addView(createSecondPage(context), MATCH_PARENT, WRAP_CONTENT);
            } else {
                sv.addView(createFirstPage(context), MATCH_PARENT, WRAP_CONTENT);

                var animator = ObjectAnimator.ofFloat(sv,
                        View.ROTATION_Y, container.isLayoutRtl() ? -45 : 45, 0);
                animator.setInterpolator(TimeInterpolator.DECELERATE);
                sv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                                               int oldTop, int oldRight, int oldBottom) {
                        animator.start();
                        v.removeOnLayoutChangeListener(this);
                    }
                });
            }
            sv.setEdgeEffectColor(ThemeControl.THEME_COLOR);

            var params = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1);
            var dp6 = sv.dp(6);
            params.setMargins(dp6, dp6, dp6, dp6);
            container.addView(sv, params);

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
    }

    public static LinearLayout createSecondPage(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        {
            var list = createCategoryList(context, "modernui.center.category.system");

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

            content.addView(list);
        }

        {
            var category = createCategoryList(context, "modernui.center.category.font");
            var transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            category.setLayoutTransition(transition);

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
                ThemeControl.addBackground(firstLine);

                layout.addView(firstLine);

                var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                params.gravity = Gravity.CENTER;
                params.setMargins(dp6, layout.dp(3), dp6, layout.dp(3));

                category.addView(layout, params);
            }

            {
                var option = new LinearLayout(context);
                option.setOrientation(LinearLayout.HORIZONTAL);
                option.setHorizontalGravity(Gravity.START);

                final int dp3 = content.dp(3);
                {
                    var title = new TextView(context);
                    title.setText(I18n.get("modernui.center.font.fallbackFonts"));
                    title.setTooltipText(I18n.get("modernui.center.font.fallbackFonts_desc"));
                    title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    title.setTextSize(14);
                    title.setMinWidth(content.dp(60));

                    var params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 2);
                    params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                    option.addView(title, params);
                }
                {
                    var input = new EditText(context);
                    input.setId(R.id.input);
                    input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                    input.setTextSize(14);
                    input.setPadding(dp3, 0, dp3, 0);

                    input.setText(String.join("\n", Config.CLIENT.mFallbackFontFamilyList.get()));
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
                            if (!Config.CLIENT.mFallbackFontFamilyList.get().equals(result)) {
                                Config.CLIENT.mFallbackFontFamilyList.set(result);
                                Config.CLIENT.saveAsync();
                                reloadDefaultTypeface()
                                        .whenCompleteAsync((oldTypeface, throwable) -> {
                                            if (throwable == null) {
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
                        }
                    });

                    ThemeControl.addBackground(input);

                    var params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 5);
                    params.gravity = Gravity.CENTER_VERTICAL;
                    option.addView(input, params);
                }

                var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                params.gravity = Gravity.CENTER;
                params.setMargins(content.dp(6), dp3, content.dp(6), dp3);
                option.setLayoutParams(params);

                category.addView(option);
            }

            /*list.addView(createBooleanOption(context, "modernui.center.font.vanillaFont",
                    ModernUIText.CONFIG.mUseVanillaFont,
                    ModernUIText.CONFIG::saveAndReloadAsync));*/

            category.addView(createBooleanOption(context, "modernui.center.font.antiAliasing",
                    Config.CLIENT.mAntiAliasing, Config.CLIENT::saveAndReloadAsync));

            category.addView(createBooleanOption(context, "modernui.center.font.autoHinting",
                    Config.CLIENT.mAutoHinting, Config.CLIENT::saveAndReloadAsync));

            content.addView(category);
        }

        content.setDividerDrawable(ThemeControl.makeDivider(content));
        content.setDividerPadding(content.dp(8));
        content.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        return content;
    }

    public LinearLayout createFirstPage(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        var transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        content.setLayoutTransition(transition);

        Runnable saveFn = Config.CLIENT::saveAndReloadAsync;

        // Screen
        {
            var list = createCategoryList(context, "modernui.center.category.screen");

            list.addView(createGuiScaleOption(context));

            list.addView(createColorOpacityOption(context, "modernui.center.screen.backgroundOpacity",
                    Config.CLIENT.mBackgroundColor, saveFn));

            list.addView(createIntegerOption(context, "modernui.center.screen.backgroundDuration",
                    Config.Client.ANIM_DURATION_MIN, Config.Client.ANIM_DURATION_MAX,
                    3, 50, Config.CLIENT.mBackgroundDuration, saveFn));
            {
                var option = createBooleanOption(context, "modernui.center.screen.blurEffect",
                        Config.CLIENT.mBlurEffect, saveFn);
                option.setTooltipText(I18n.get("modernui.center.screen.blurEffect_desc"));
                list.addView(option);
            }

            list.addView(createIntegerOption(context, "modernui.center.screen.blurRadius",
                    Config.Client.BLUR_RADIUS_MIN, Config.Client.BLUR_RADIUS_MAX,
                    2, 1, Config.CLIENT.mBlurRadius, saveFn));

            list.addView(createSpinnerOption(context, "modernui.center.screen.windowMode",
                    Config.Client.WindowMode.values(), Config.CLIENT.mWindowMode, saveFn));

            list.addView(createIntegerOption(context, "modernui.center.screen.framerateInactive",
                    0, 255, 3, 5,
                    Config.CLIENT.mFramerateInactive, saveFn));

            list.addView(createIntegerOption(context, "modernui.center.screen.framerateMinimized",
                    0, 255, 3, 5,
                    Config.CLIENT.mFramerateMinimized, saveFn));

            list.addView(createFloatOption(context, "modernui.center.screen.masterVolumeInactive",
                    0, 1, 4,
                    Config.CLIENT.mMasterVolumeInactive, 100, saveFn));

            list.addView(createFloatOption(context, "modernui.center.screen.masterVolumeMinimized",
                    0, 1, 4,
                    Config.CLIENT.mMasterVolumeMinimized, 100, saveFn));

            {
                var option = createBooleanOption(context, "modernui.center.screen.inventoryPause",
                        Config.CLIENT.mInventoryPause, saveFn);
                option.setTooltipText(I18n.get("modernui.center.screen.inventoryPause_desc"));
                list.addView(option);
            }

            content.addView(list);
        }

        {
            var list = createCategoryList(context, "modernui.center.category.extension");

            {
                var option = createBooleanOption(context, "modernui.center.extension.ding",
                        Config.CLIENT.mDing, saveFn);
                option.setTooltipText(I18n.get("modernui.center.extension.ding_desc"));
                list.addView(option);
            }

            /*{
                var option = createBooleanOption(context, "key.modernui.zoom",
                        Config.CLIENT.mZoom, saveFn);
                option.setTooltipText(I18n.get("key.modernui.zoom_desc"));
                list.addView(option);
            }*/

            {
                var option = createSwitchLayout(context, "modernui.center.extension.smoothScrolling");
                option.setTooltipText(I18n.get("modernui.center.extension.smoothScrolling_desc"));
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(!Boolean.parseBoolean(
                        ModernUIClient.getBootstrapProperty(ModernUIMod.BOOTSTRAP_DISABLE_SMOOTH_SCROLLING)
                ));
                button.setOnCheckedChangeListener((__, checked) -> {
                    ModernUIClient.setBootstrapProperty(
                            ModernUIMod.BOOTSTRAP_DISABLE_SMOOTH_SCROLLING,
                            Boolean.toString(!checked)
                    );
                    Toast.makeText(__.getContext(),
                                    I18n.get("gui.modernui.restart_to_work"),
                                    Toast.LENGTH_SHORT)
                            .show();
                });
                list.addView(option);
            }

            {
                var option = createSwitchLayout(context, "modernui.center.extension.tooltip");
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(Config.CLIENT.mTooltip.get());
                button.setOnCheckedChangeListener((view, checked) -> {
                    Config.CLIENT.mTooltip.set(checked);
                    saveFn.run();
                    if (checked) {
                        if (mTooltipCategory == null) {
                            mTooltipCategory = createTooltipCategory(view.getContext());
                            content.addView(mTooltipCategory, 2);
                        } else {
                            mTooltipCategory.setVisibility(View.VISIBLE);
                        }
                    } else if (mTooltipCategory != null) {
                        mTooltipCategory.setVisibility(View.GONE);
                    }
                });
                list.addView(option);
            }

            {
                var option = createSwitchLayout(context, "modernui.center.text.textEngine");
                option.setTooltipText(I18n.get("modernui.center.text.textEngine_desc"));
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(ModernUIClient.isTextEngineEnabled());
                button.setOnCheckedChangeListener((view, checked) -> {
                    ModernUIClient.setBootstrapProperty(
                            ModernUIMod.BOOTSTRAP_DISABLE_TEXT_ENGINE,
                            Boolean.toString(!checked)
                    );
                    Toast.makeText(view.getContext(),
                                    I18n.get("gui.modernui.restart_to_work"),
                                    Toast.LENGTH_SHORT)
                            .show();
                    if (checked) {
                        if (mTextEngineCategory == null) {
                            mTextEngineCategory = createTextEngineCategory(view.getContext());
                            content.addView(mTextEngineCategory);
                        } else {
                            mTextEngineCategory.setVisibility(View.VISIBLE);
                        }
                    } else if (mTextEngineCategory != null) {
                        mTextEngineCategory.setVisibility(View.GONE);
                    }
                });
                list.addView(option);
            }

            /*list.addView(createIntegerOption(context, "modernui.center.extension.tooltipDuration",
                    Config.Client.ANIM_DURATION_MIN, Config.Client.ANIM_DURATION_MAX,
                    3, 50, Config.CLIENT.mTooltipDuration, saveFn));*/

            content.addView(list);
        }

        if (Config.CLIENT.mTooltip.get()) {
            mTooltipCategory = createTooltipCategory(context);
            content.addView(mTooltipCategory);
        }

        if (ModernUIClient.isTextEngineEnabled()) {
            mTextEngineCategory = createTextEngineCategory(context);
            content.addView(mTextEngineCategory);
        }

        content.setDividerDrawable(ThemeControl.makeDivider(content));
        content.setDividerPadding(content.dp(8));
        content.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        return content;
    }

    public static LinearLayout createTooltipCategory(Context context) {
        var category = createCategoryList(context, "modernui.center.category.tooltip");

        Runnable saveFn = Config.CLIENT::saveAndReloadAsync;

        category.addView(createBooleanOption(context, "modernui.center.tooltip.roundedShapes",
                Config.CLIENT.mRoundedTooltip, saveFn));

        /*category.addView(createBooleanOption(context, "modernui.center.tooltip.centerTitle",
                Config.CLIENT.mCenterTooltipTitle, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.tooltip.titleBreak",
                Config.CLIENT.mTooltipTitleBreak, saveFn));*/

        category.addView(createBooleanOption(context, "modernui.center.tooltip.exactPositioning",
                Config.CLIENT.mExactTooltipPositioning, saveFn));

        category.addView(createColorOpacityOption(context, "modernui.center.tooltip.backgroundOpacity",
                Config.CLIENT.mTooltipFill, saveFn));

        {
            var layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);

            final int dp6 = layout.dp(6);
            final Button title;
            {
                title = new Button(context);
                title.setText(I18n.get("modernui.center.tooltip.borderStyle"));
                title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                title.setTextSize(14);

                var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                layout.addView(title, params);
            }

            title.setOnClickListener(new TooltipBorderCollapsed(layout, saveFn));

            ThemeControl.addBackground(title);

            var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            params.setMargins(dp6, layout.dp(3), dp6, 0);
            category.addView(layout, params);
        }

        return category;
    }

    public static LinearLayout createTextEngineCategory(Context context) {
        var category = createCategoryList(context, "modernui.center.category.text");

        Runnable saveFn = Config.TEXT::saveAndReloadAsync;

        {
            var option = createBooleanOption(context, "modernui.center.text.textShadersInWorld",
                    Config.TEXT.mUseTextShadersInWorld, saveFn);
            option.setTooltipText(I18n.get("modernui.center.text.textShadersInWorld_desc"));
            category.addView(option);
        }

        {
            var option = createBooleanOption(context, "modernui.center.text.allowSDFTextIn2D",
                    Config.TEXT.mAllowSDFTextIn2D, saveFn);
            option.setTooltipText(I18n.get("modernui.center.text.allowSDFTextIn2D_desc"));
            category.addView(option);
        }

        {
            var option = createBooleanOption(context, "modernui.center.text.smartSDFShaders",
                    Config.TEXT.mSmartSDFShaders, saveFn);
            option.setTooltipText(I18n.get("modernui.center.text.smartSDFShaders_desc"));
            category.addView(option);
        }

        {
            var option = createBooleanOption(context, "modernui.center.text.computeDeviceFontSize",
                    Config.TEXT.mComputeDeviceFontSize, saveFn);
            option.setTooltipText(I18n.get("modernui.center.text.computeDeviceFontSize_desc"));
            category.addView(option);
        }

        {
            var option = createSpinnerOption(context, "modernui.center.text.defaultFontBehavior",
                    Config.Text.DefaultFontBehavior.values(),
                    Config.TEXT.mDefaultFontBehavior, saveFn);
            option.getChildAt(0)
                    .setTooltipText(I18n.get("modernui.center.text.defaultFontBehavior_desc"));
            category.addView(option);
        }

        {
            var option = createBooleanOption(context, "modernui.center.text.colorEmoji",
                    Config.TEXT.mUseColorEmoji, saveFn);
            option.setTooltipText(I18n.get("modernui.center.text.colorEmoji_desc"));
            category.addView(option);
        }

        {
            var option = createBooleanOption(context, "modernui.center.text.emojiShortcodes",
                    Config.TEXT.mEmojiShortcodes, saveFn);
            option.setTooltipText(I18n.get("modernui.center.text.emojiShortcodes_desc"));
            category.addView(option);
        }

        category.addView(createSpinnerOption(context, "modernui.center.text.bidiHeuristicAlgo",
                Config.Text.TextDirection.values(),
                Config.TEXT.mTextDirection,
                saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.allowShadow",
                Config.TEXT.mAllowShadow, saveFn));

        category.addView(createFloatOption(context, "modernui.center.text.shadowOffset",
                Config.Text.SHADOW_OFFSET_MIN, Config.Text.SHADOW_OFFSET_MAX,
                5, Config.TEXT.mShadowOffset, 10, saveFn));

        category.addView(createBooleanOption(context, "modernui.center.text.allowAsyncLayout",
                Config.TEXT.mAllowAsyncLayout, saveFn));

        {
            var option = createSpinnerOption(context, "modernui.center.text.lineBreakStyle",
                    Config.Text.LineBreakStyle.values(),
                    Config.TEXT.mLineBreakStyle, saveFn);
            option.getChildAt(0)
                    .setTooltipText(I18n.get("modernui.center.text.lineBreakStyle_desc"));
            category.addView(option);
        }

        category.addView(createSpinnerOption(context, "modernui.center.text.lineBreakWordStyle",
                Config.Text.LineBreakWordStyle.values(),
                Config.TEXT.mLineBreakWordStyle, saveFn));

        {
            var option = createBooleanOption(context, "modernui.center.text.useComponentCache",
                    Config.TEXT.mUseComponentCache, saveFn);
            option.setTooltipText(I18n.get("modernui.center.text.useComponentCache_desc"));
            category.addView(option);
        }

        category.addView(createBooleanOption(context, "modernui.center.text.fixedResolution",
                Config.TEXT.mFixedResolution, saveFn));

        category.addView(createFloatOption(context, "modernui.center.text.baseFontSize",
                Config.Text.BASE_FONT_SIZE_MIN, Config.Text.BASE_FONT_SIZE_MAX,
                5, Config.TEXT.mBaseFontSize, 10, saveFn));

        category.addView(createFloatOption(context, "modernui.center.text.baselineShift",
                Config.Text.BASELINE_MIN, Config.Text.BASELINE_MAX,
                5, Config.TEXT.mBaselineShift, 10, saveFn));

        category.addView(createFloatOption(context, "modernui.center.text.outlineOffset",
                Config.Text.OUTLINE_OFFSET_MIN, Config.Text.OUTLINE_OFFSET_MAX,
                5, Config.TEXT.mOutlineOffset, 10, saveFn));

        category.addView(createIntegerOption(context, "modernui.center.text.cacheLifespan",
                Config.Text.LIFESPAN_MIN, Config.Text.LIFESPAN_MAX,
                2, 1,
                Config.TEXT.mCacheLifespan, saveFn));

        return category;
    }

    @NonNull
    public static LinearLayout createCategoryList(Context context,
                                                  String name) {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        final int dp6 = layout.dp(6);
        final int dp12 = layout.dp(12);
        final int dp18 = layout.dp(18);
        {
            var title = new TextView(context);
            title.setId(R.id.title);
            title.setText(I18n.get(name));
            title.setTextSize(16);
            title.setTextColor(ThemeControl.THEME_COLOR);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.START;
            params.setMargins(dp6, dp6, dp6, dp6);
            layout.addView(title, params);
        }

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp12, dp12, dp12, dp18);
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
            var button = new SwitchButton(context);
            button.setId(R.id.button1);
            button.setCheckedColor(ThemeControl.THEME_COLOR);

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

    public static LinearLayout createBooleanOption(
            Context context,
            String name,
            ForgeConfigSpec.BooleanValue config,
            Runnable saveFn) {
        var layout = createSwitchLayout(context, name);
        var button = layout.<SwitchButton>requireViewById(R.id.button1);
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
            ForgeConfigSpec.EnumValue<E> config,
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

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
            params.gravity = Gravity.CENTER_VERTICAL;
            option.addView(title, params);
        }
        {
            var spinner = new Spinner(context);
            spinner.setGravity(Gravity.END);
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
            return "(" + auto + ")";
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

        final int dp3 = layout.dp(3);
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
            var input = new EditText(context);
            input.setId(R.id.input);
            input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            input.setTextSize(14);
            input.setPadding(dp3, 0, dp3, 0);

            ThemeControl.addBackground(input);

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

    public static LinearLayout createInputBoxWithSlider(Context context, String name) {
        var layout = createInputBox(context, name);
        var slider = new SeekBar(context);
        slider.setId(R.id.button2);
        slider.setClickable(true);
        var params = new LinearLayout.LayoutParams(slider.dp(200), WRAP_CONTENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(slider, 1, params);
        return layout;
    }

    public static LinearLayout createIntegerOption(Context context, String name,
                                                   int minValue, int maxValue, int maxLength, int stepSize,
                                                   ForgeConfigSpec.IntValue config,
                                                   Runnable saveFn) {
        return createIntegerOption(context, name,
                minValue, maxValue, maxLength, stepSize,
                config, config::set, saveFn);
    }

    public static LinearLayout createIntegerOption(Context context, String name,
                                                   int minValue, int maxValue, int maxLength, int stepSize,
                                                   Supplier<Integer> getter, Consumer<Integer> setter,
                                                   Runnable saveFn) {
        var layout = createInputBoxWithSlider(context, name);
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
        input.setMinWidth(slider.dp(50));
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
            ForgeConfigSpec.ConfigValue<List<? extends String>> config,
            Runnable saveFn) {
        Supplier<Double> getter = () -> {
            List<? extends String> colors = config.get();
            if (colors != null && !colors.isEmpty()) {
                try {
                    int color = Color.parseColor(colors.get(0));
                    return (color >>> 24) / 255.0;
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
            return 1.0;
        };
        Consumer<Double> setter = (d) -> {
            int alpha = (int) (d * 255.0 + 0.5);
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
                                                 ForgeConfigSpec.DoubleValue config,
                                                 float denominator,
                                                 Runnable saveFn) {
        return createFloatOption(context, name, minValue, maxValue, maxLength,
                config, config::set, denominator, saveFn);
    }

    public static LinearLayout createFloatOption(Context context, String name,
                                                 float minValue, float maxValue, int maxLength,
                                                 Supplier<Double> getter, Consumer<Double> setter,
                                                 float denominator, // 10 means step=0.1, 100 means step=0.01
                                                 Runnable saveFn) {
        var layout = createInputBoxWithSlider(context, name);
        var slider = layout.<SeekBar>requireViewById(R.id.button2);
        var input = layout.<EditText>requireViewById(R.id.input);
        input.setFilters(DigitsInputFilter.getInstance(null, minValue < 0, true),
                new InputFilter.LengthFilter(maxLength));
        float curValue = getter.get().floatValue();
        input.setText(Float.toString(curValue));
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                EditText v = (EditText) view;
                float newValue = MathUtil.clamp(Float.parseFloat(v.getText().toString()),
                        minValue, maxValue);
                replaceText(v, Float.toString(newValue));
                if (newValue != getter.get()) {
                    setter.accept((double) newValue);
                    int curProgress = Math.round((newValue - minValue) * denominator);
                    slider.setProgress(curProgress, true);
                    saveFn.run();
                }
            }
        });
        input.setMinWidth(slider.dp(50));
        int steps = Math.round((maxValue - minValue) * denominator);
        slider.setMax(steps);
        int curProgress = Math.round((curValue - minValue) * denominator);
        slider.setProgress(curProgress);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double newValue = seekBar.getProgress() / denominator + minValue;
                replaceText(input, Float.toString((float) newValue));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                double newValue = seekBar.getProgress() / denominator + minValue;
                if (newValue != getter.get()) {
                    setter.accept((double) (float) newValue);
                    replaceText(input, Float.toString((float) newValue));
                    saveFn.run();
                }
            }
        });
        return layout;
    }

    public static class TooltipBorderCollapsed implements View.OnClickListener {

        public static final String[][] PRESET_COLORS = {
                {"#F0AADCF0", "#F0FFC3F7", "#F0BFF2B2", "#F0D27F3D"},
                {"#F0AADCF0", "#F0DAD0F4", "#F0FFC3F7", "#F0DAD0F4"},
                {"#F028007F", "#F028007F", "#F014003F", "#F014003F"},
                {"#F0606060", "#F0101010", "#F0FFFFFF", "#F0B0B0B0"}
        };

        final ViewGroup mParent;
        final Runnable mSaveFn;

        // lazy-init
        LinearLayout mContent;
        FourColorPicker mColorPicker;

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
            {
                var option = createFloatOption(mParent.getContext(), "modernui.center.tooltip.borderWidth",
                        Config.Client.TOOLTIP_BORDER_WIDTH_MIN, Config.Client.TOOLTIP_BORDER_WIDTH_MAX,
                        4, Config.CLIENT.mTooltipWidth, (thickness) -> {
                            Config.CLIENT.mTooltipWidth.set(thickness);
                            if (mColorPicker != null) {
                                mColorPicker.setThicknessFactor(thickness.floatValue() / 3f);
                            }
                        }, 100, mSaveFn);
                mContent.addView(option);
            }
            {
                var option = createIntegerOption(mParent.getContext(), "modernui.center.tooltip.borderCycle",
                        Config.Client.TOOLTIP_BORDER_COLOR_ANIM_MIN, Config.Client.TOOLTIP_BORDER_COLOR_ANIM_MAX,
                        4, 100, Config.CLIENT.mTooltipCycle, mSaveFn);
                option.setTooltipText(I18n.get("modernui.center.tooltip.borderCycle_desc"));
                mContent.addView(option);
            }
            var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.setMargins(0, mContent.dp(6), 0, 0);
            {
                var buttonGroup = new LinearLayout(mParent.getContext());
                buttonGroup.setOrientation(LinearLayout.HORIZONTAL);
                for (int i = 0; i < 4; i++) {
                    var button = new Button(mParent.getContext());
                    button.setText(I18n.get("gui.modernui.preset_s", (i + 1)));
                    final int idx = i;
                    button.setOnClickListener((__) -> mColorPicker.setColors(
                            PRESET_COLORS[idx])
                    );
                    var p = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
                    buttonGroup.addView(button, p);
                }
                mContent.addView(buttonGroup, new LinearLayout.LayoutParams(params));
            }
            mContent.addView(mColorPicker = new FourColorPicker(mParent.getContext(),
                    Config.CLIENT.mTooltipStroke,
                    mSaveFn), new LinearLayout.LayoutParams(params));
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
                var values = FontFamily.getSystemFontMap()
                        .values()
                        .stream()
                        .map(family -> new FontFamilyItem(family.getFamilyName(),
                                family.getFamilyName(ModernUI.getSelectedLocale())))
                        .sorted()
                        .collect(Collectors.toList());
                String chooseFont = I18n.get("modernui.center.font.chooseFont");
                values.add(0, new FontFamilyItem(chooseFont, chooseFont));
                var spinner = mSpinner = new Spinner(mParent.getContext());
                spinner.setAdapter(new FontFamilyAdapter(mParent.getContext(), values));
                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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
                            spinner.setSelection(i);
                            break;
                        }
                    }
                }

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
                Config.CLIENT.saveAsync();
                reloadDefaultTypeface()
                        .whenCompleteAsync((oldTypeface, throwable) -> {
                            if (throwable == null) {
                                mOnFontChanged.run();
                                refreshViewTypeface(
                                        UIManager.getInstance().getDecorView(),
                                        oldTypeface
                                );
                                Toast.makeText(context,
                                        I18n.get("gui.modernui.font_reloaded"),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }, Core.getUiThreadExecutor());
                return true;
            }
            return false;
        }
    }

    private static void replaceText(EditText editText, CharSequence newText) {
        Editable text = editText.getText();
        text.replace(0, text.length(), newText);
    }

    private static CompletableFuture<Typeface> reloadDefaultTypeface() {
        return Minecraft.getInstance().submit(() -> {
            var oldTypeface = ModernUI.getSelectedTypeface();
            var client = ModernUIClient.getInstance();
            client.reloadTypeface();
            client.reloadFontStrike();
            return oldTypeface;
        });
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
}
