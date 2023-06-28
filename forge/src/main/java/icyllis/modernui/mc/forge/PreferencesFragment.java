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

package icyllis.modernui.mc.forge;

import icyllis.modernui.R;
import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.font.GlyphManager;
import icyllis.modernui.mc.forge.ui.DividerDrawable;
import icyllis.modernui.mc.forge.ui.ThemeControl;
import icyllis.modernui.mc.text.ModernUIText;
import icyllis.modernui.text.InputFilter;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.viewpager.widget.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class PreferencesFragment extends Fragment {

    private static final Field OPTION_VALUE = ObfuscationReflectionHelper.findField(OptionInstance.class, "f_231481_");

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var pager = new ViewPager(getContext());

        pager.setAdapter(new TheAdapter());
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

    private static class TheAdapter extends PagerAdapter {

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
                    4, Config.CLIENT.mFontScale, Config.CLIENT::saveAndReloadAsync));

            {
                var option = createInputBox(context, "modernui.center.system.globalAnimationScale");
                var input = option.<EditText>requireViewById(R.id.input);
                input.setText(Float.toString(ValueAnimator.sDurationScale));
                input.setFilters(DigitsInputFilter.getInstance(input.getTextLocale(), false, true),
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
            var list = createCategoryList(context, "modernui.center.category.font");

            {
                var option = new LinearLayout(context);
                option.setOrientation(LinearLayout.HORIZONTAL);
                option.setHorizontalGravity(Gravity.START);

                final int dp3 = content.dp(3);
                {
                    var title = new TextView(context);
                    title.setText(I18n.get("modernui.center.font.fontFamily"));
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

                    input.setText(String.join("\n", Config.CLIENT.mFontFamily.get()));
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
                            v.setText(String.join("\n", result));
                            if (!Config.CLIENT.mFontFamily.get().equals(result)) {
                                Config.CLIENT.mFontFamily.set(result);
                                Config.CLIENT.saveAsync();
                                Toast.makeText(v.getContext(),
                                                I18n.get("gui.modernui.restart_to_work"),
                                                Toast.LENGTH_SHORT)
                                        .show();
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
                params.setMargins(content.dp(6), 0, content.dp(6), 0);
                option.setLayoutParams(params);

                list.addView(option);
            }

            list.addView(createBooleanOption(context, "modernui.center.font.vanillaFont",
                    ModernUIText.CONFIG.mUseVanillaFont,
                    ModernUIText.CONFIG::saveAndReloadAsync));

            list.addView(createBooleanOption(context, "modernui.center.font.antiAliasing",
                    Config.CLIENT.mAntiAliasing, Config.CLIENT::saveAndReloadAsync));

            list.addView(createBooleanOption(context, "modernui.center.font.autoHinting",
                    Config.CLIENT.mAutoHinting, Config.CLIENT::saveAndReloadAsync));

            content.addView(list);
        }

        if (ModernUIForge.isDeveloperMode()) {
            var category = createCategoryList(context, "Developer");
            {
                var option = createInputBox(context, "Gamma");
                var input = option.<EditText>requireViewById(R.id.input);
                input.setText(Minecraft.getInstance().options.gamma().get().toString());
                input.setFilters(DigitsInputFilter.getInstance(input.getTextLocale(), false, true),
                        new InputFilter.LengthFilter(6));
                input.setOnFocusChangeListener((view, hasFocus) -> {
                    if (!hasFocus) {
                        EditText v = (EditText) view;
                        double gamma = Double.parseDouble(v.getText().toString());
                        v.setText(Double.toString(gamma));
                        // no sync, but safe
                        try {
                            // no listener
                            OPTION_VALUE.set(Minecraft.getInstance().options.gamma(), gamma);
                        } catch (Exception e) {
                            Minecraft.getInstance().options.gamma().set(gamma);
                        }
                    }
                });
                category.addView(option);
            }

            /*category.addView(createBooleanOption(context, "Remove Message Signature",
                    Config.CLIENT.mRemoveSignature, Config.CLIENT::saveAndReloadAsync));*/

            category.addView(createBooleanOption(context, "Remove Telemetry Session",
                    Config.CLIENT.mRemoveTelemetry, Config.CLIENT::saveAndReloadAsync));

            /*category.addView(createBooleanOption(context, "Secure Profile Public Key",
                    Config.CLIENT.mSecurePublicKey, Config.CLIENT::saveAndReloadAsync));*/

            {
                var button = createDebugButton(context, "Take UI Screenshot (Y)");
                button.setOnClickListener((__) ->
                        Core.postOnRenderThread(() -> UIManager.getInstance().takeScreenshot()));
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Dump UI Manager (P)");
                button.setOnClickListener((__) ->
                        Core.postOnMainThread(() -> UIManager.getInstance().dump()));
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Debug Glyph Manager (G)");
                button.setOnClickListener((__) ->
                        Core.postOnMainThread(() -> GlyphManager.getInstance().debug()));
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "GC (F)");
                button.setOnClickListener((__) -> System.gc());
                category.addView(button);
            }
            content.addView(category);
        }

        content.setDividerDrawable(new DividerDrawable(content));
        content.setDividerPadding(content.dp(8));
        content.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        return content;
    }

    public static Button createDebugButton(Context context, String text) {
        var button = new Button(context);
        button.setText(text);
        button.setTextSize(14);
        button.setGravity(Gravity.START);

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(button.dp(6), 0, button.dp(6), 0);
        button.setLayoutParams(params);
        return button;
    }

    public static LinearLayout createFirstPage(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        var transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        content.setLayoutTransition(transition);

        Runnable saveFn = Config.CLIENT::saveAndReloadAsync;

        // Screen
        {
            var list = createCategoryList(context, "modernui.center.category.screen");

            list.addView(createIntegerOption(context, "modernui.center.screen.backgroundDuration",
                    Config.Client.ANIM_DURATION_MIN, Config.Client.ANIM_DURATION_MAX,
                    3, 50, Config.CLIENT.mBackgroundDuration, saveFn));

            list.addView(createBooleanOption(context, "modernui.center.screen.blurEffect",
                    Config.CLIENT.mBlurEffect, saveFn));

            list.addView(createIntegerOption(context, "modernui.center.screen.blurRadius",
                    Config.Client.BLUR_RADIUS_MIN, Config.Client.BLUR_RADIUS_MAX,
                    2, 1, Config.CLIENT.mBlurRadius, saveFn));

            list.addView(createSpinnerOption(context, "modernui.center.screen.windowMode",
                    Config.Client.WindowMode.values(), Config.CLIENT.mWindowMode, saveFn));

            list.addView(createBooleanOption(context, "modernui.center.screen.inventoryPause",
                    Config.CLIENT.mInventoryPause, saveFn));

            content.addView(list);
        }

        {
            var list = createCategoryList(context, "modernui.center.category.extension");

            list.addView(createBooleanOption(context, "modernui.center.extension.ding",
                    Config.CLIENT.mDing, saveFn));

            {
                var option = createSwitchLayout(context, "modernui.center.extension.smoothScrolling");
                var button = option.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked((ModernUIForge.getBootstrapLevel() & ModernUIForge.BOOTSTRAP_DISABLE_SMOOTH_SCROLLING) == 0);
                button.setOnCheckedChangeListener((__, checked) -> {
                    int level = ModernUIForge.getBootstrapLevel();
                    if (checked) {
                        level &= ~ModernUIForge.BOOTSTRAP_DISABLE_SMOOTH_SCROLLING;
                    } else {
                        level |= ModernUIForge.BOOTSTRAP_DISABLE_SMOOTH_SCROLLING;
                    }
                    ModernUIForge.setBootstrapLevel(level);
                    Toast.makeText(__.getContext(),
                                    I18n.get("gui.modernui.restart_to_work"),
                                    Toast.LENGTH_SHORT)
                            .show();
                });
                list.addView(option);
            }

            list.addView(createBooleanOption(context, "modernui.center.extension.tooltip",
                    Config.CLIENT.mTooltip, saveFn));

            /*list.addView(createIntegerOption(context, "modernui.center.extension.tooltipDuration",
                    Config.Client.ANIM_DURATION_MIN, Config.Client.ANIM_DURATION_MAX,
                    3, 50, Config.CLIENT.mTooltipDuration, saveFn));*/

            {
                var layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);

                final int dp6 = layout.dp(6);
                final Button title;
                {
                    title = new Button(context);
                    title.setText(I18n.get("modernui.center.extension.tooltipBorderColor"));
                    title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    title.setTextSize(14);

                    var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                    layout.addView(title, params);
                }
                final FourColorPicker picker;
                {
                    picker = new FourColorPicker(context,
                            Config.CLIENT.mTooltipStroke,
                            saveFn);
                    picker.setVisibility(View.GONE);

                    var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                    params.setMargins(0, dp6, 0, 0);
                    layout.addView(picker, params);
                }

                title.setOnClickListener((__) ->
                        picker.setVisibility(picker.getVisibility() == View.GONE
                                ? View.VISIBLE
                                : View.GONE));

                ThemeControl.addBackground(title);

                var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                params.gravity = Gravity.CENTER;
                params.setMargins(dp6, layout.dp(3), dp6, 0);
                list.addView(layout, params);
            }

            content.addView(list);
        }

        saveFn = ModernUIText.CONFIG::saveAndReloadAsync;

        {
            var category = createCategoryList(context, "modernui.center.category.text");

            var option = createSwitchLayout(context, "modernui.center.text.textEngine");
            var button = option.<SwitchButton>requireViewById(R.id.button1);
            button.setChecked((ModernUIForge.getBootstrapLevel() & ModernUIForge.BOOTSTRAP_DISABLE_TEXT_ENGINE) == 0);
            button.setOnCheckedChangeListener((__, checked) -> {
                int level = ModernUIForge.getBootstrapLevel();
                if (checked) {
                    level &= ~ModernUIForge.BOOTSTRAP_DISABLE_TEXT_ENGINE;
                } else {
                    level |= ModernUIForge.BOOTSTRAP_DISABLE_TEXT_ENGINE;
                }
                ModernUIForge.setBootstrapLevel(level);
                Toast.makeText(__.getContext(),
                                I18n.get("gui.modernui.restart_to_work"),
                                Toast.LENGTH_SHORT)
                        .show();
            });
            category.addView(option);

            category.addView(createBooleanOption(context, "modernui.center.text.colorEmoji",
                    ModernUIText.CONFIG.mColorEmoji, saveFn));

            category.addView(createBooleanOption(context, "modernui.center.text.emojiShortcodes",
                    ModernUIText.CONFIG.mEmojiShortcodes, saveFn));

            category.addView(createSpinnerOption(context, "modernui.center.text.bidiHeuristicAlgo",
                    ModernUIText.Config.TextDirection.values(),
                    ModernUIText.CONFIG.mTextDirection,
                    saveFn));

            category.addView(createBooleanOption(context, "modernui.center.text.allowShadow",
                    ModernUIText.CONFIG.mAllowShadow, saveFn));

            category.addView(createBooleanOption(context, "modernui.center.text.fixedResolution",
                    ModernUIText.CONFIG.mFixedResolution, saveFn));

            category.addView(createFloatOption(context, "modernui.center.text.baseFontSize",
                    ModernUIText.Config.BASE_FONT_SIZE_MIN, ModernUIText.Config.BASE_FONT_SIZE_MAX,
                    5, ModernUIText.CONFIG.mBaseFontSize, saveFn));

            category.addView(createFloatOption(context, "modernui.center.text.baselineShift",
                    ModernUIText.Config.BASELINE_MIN, ModernUIText.Config.BASELINE_MAX,
                    5, ModernUIText.CONFIG.mBaselineShift, saveFn));

            category.addView(createFloatOption(context, "modernui.center.text.shadowOffset",
                    ModernUIText.Config.SHADOW_OFFSET_MIN, ModernUIText.Config.SHADOW_OFFSET_MAX,
                    5, ModernUIText.CONFIG.mShadowOffset, saveFn));

            category.addView(createFloatOption(context, "modernui.center.text.outlineOffset",
                    ModernUIText.Config.OUTLINE_OFFSET_MIN, ModernUIText.Config.OUTLINE_OFFSET_MAX,
                    5, ModernUIText.CONFIG.mOutlineOffset, saveFn));

            content.addView(category);
        }

        content.setDividerDrawable(new DividerDrawable(content));
        content.setDividerPadding(content.dp(8));
        content.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        return content;
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

    @Nonnull
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
        var layout = createInputBoxWithSlider(context, name);
        var slider = layout.<SeekBar>requireViewById(R.id.button2);
        var input = layout.<EditText>requireViewById(R.id.input);
        input.setFilters(DigitsInputFilter.getInstance(input.getTextLocale()),
                new InputFilter.LengthFilter(maxLength));
        int curValue = config.get();
        input.setText(Integer.toString(curValue));
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                EditText v = (EditText) view;
                int newValue = MathUtil.clamp(Integer.parseInt(v.getText().toString()),
                        minValue, maxValue);
                v.setText(Integer.toString(newValue));
                if (newValue != config.get()) {
                    config.set(newValue);
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
                input.setText(Integer.toString(newValue));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int newValue = seekBar.getProgress() * stepSize + minValue;
                if (newValue != config.get()) {
                    config.set(newValue);
                    input.setText(Integer.toString(newValue));
                    saveFn.run();
                }
            }
        });
        return layout;
    }

    public static LinearLayout createFloatOption(Context context, String name,
                                                 float minValue, float maxValue, int maxLength,
                                                 ForgeConfigSpec.DoubleValue config,
                                                 Runnable saveFn) {
        var layout = createInputBoxWithSlider(context, name);
        var slider = layout.<SeekBar>requireViewById(R.id.button2);
        var input = layout.<EditText>requireViewById(R.id.input);
        input.setFilters(DigitsInputFilter.getInstance(input.getTextLocale(), minValue < 0, true),
                new InputFilter.LengthFilter(maxLength));
        float curValue = config.get().floatValue();
        input.setText(Float.toString(curValue));
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                EditText v = (EditText) view;
                float newValue = MathUtil.clamp(Float.parseFloat(v.getText().toString()),
                        minValue, maxValue);
                v.setText(Float.toString(newValue));
                if (newValue != config.get()) {
                    config.set((double) newValue);
                    int curProgress = (int) Math.round((newValue - minValue) * 10.0);
                    slider.setProgress(curProgress, true);
                    saveFn.run();
                }
            }
        });
        input.setMinWidth(slider.dp(50));
        int steps = (int) Math.round((maxValue - minValue) * 10.0);
        slider.setMax(steps);
        int curProgress = (int) Math.round((curValue - minValue) * 10.0);
        slider.setProgress(curProgress);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double newValue = seekBar.getProgress() / 10.0 + minValue;
                input.setText(Float.toString((float) newValue));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                double newValue = seekBar.getProgress() / 10.0 + minValue;
                if (newValue != config.get()) {
                    config.set((double) (float) newValue);
                    input.setText(Float.toString((float) newValue));
                    saveFn.run();
                }
            }
        });
        return layout;
    }

    public static class FourColorPicker extends RelativeLayout {

        private EditText mULColorField;
        private EditText mURColorField;
        private EditText mLRColorField;
        private EditText mLLColorField;

        private int mULColor = ~0;
        private int mURColor = ~0;
        private int mLRColor = ~0;
        private int mLLColor = ~0;

        private final Rect mPreviewBox = new Rect();

        private final View.OnFocusChangeListener mOnFieldFocusChange;

        public FourColorPicker(Context context,
                               ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                               Runnable saveFn) {
            super(context);

            mOnFieldFocusChange = (v, hasFocus) -> {
                EditText input = (EditText) v;
                if (!hasFocus) {
                    try {
                        var string = input.getText().toString();
                        int color = Color.parseColor(string);
                        int idx = -1;
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
                        if (idx != -1) {
                            invalidate();
                            var newList = new ArrayList<String>(config.get());
                            if (newList.isEmpty()) {
                                newList.add("#FFFFFFFF");
                            }
                            while (newList.size() < 4) {
                                newList.add(newList.get(newList.size() - 1));
                            }
                            newList.set(idx, string);
                            config.set(newList);
                            saveFn.run();
                        }
                        input.setTextColor(0xFF000000 | color);
                    } catch (Exception e) {
                        input.setTextColor(0xFFFF0000);
                    }
                }
            };

            var colors = config.get();

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

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            var paint = Paint.obtain();
            paint.setStyle(Paint.STROKE);
            paint.setStrokeWidth(4);
            canvas.drawRoundRectGradient(mPreviewBox.left, mPreviewBox.top, mPreviewBox.right, mPreviewBox.bottom,
                    mULColor, mURColor, mLRColor, mLLColor, 8, paint);
            paint.recycle();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            mPreviewBox.set(
                    Math.max(mULColorField.getRight(), mLLColorField.getRight()) + 8,
                    getPaddingTop() + 8,
                    Math.min(mURColorField.getLeft(), mLRColorField.getLeft()) - 8,
                    getHeight() - getPaddingBottom() - 8
            );
            invalidate();
        }
    }
}
