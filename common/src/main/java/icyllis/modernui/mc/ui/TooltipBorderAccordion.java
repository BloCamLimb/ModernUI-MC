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

import icyllis.modernui.R;
import icyllis.modernui.mc.Config;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.Switch;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class TooltipBorderAccordion implements View.OnClickListener {

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
    public TooltipBorderAccordion(ViewGroup parent, Runnable saveFn) {
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
            var option = PreferencesFragment.createSwitchLayout(mParent.getContext(), "modernui.center.tooltip" +
                    ".roundedShapes");
            var button = option.<Switch>requireViewById(R.id.button1);
            button.setChecked(rounded = Config.CLIENT.mRoundedTooltip.get());
            button.setOnCheckedChangeListener((__, checked) -> {
                Config.CLIENT.mRoundedTooltip.set(checked);
                PreferencesFragment.setViewAndChildrenEnabled(mBorderWidth, checked);
                PreferencesFragment.setViewAndChildrenEnabled(mCornerRadius, checked);
                PreferencesFragment.setViewAndChildrenEnabled(mShadowRadius, checked);
                PreferencesFragment.setViewAndChildrenEnabled(mShadowAlpha, checked);
                mSaveFn.run();
            });
            mContent.addView(option);
        }
        {
            var option = PreferencesFragment.createFloatOption(mParent.getContext(), "modernui.center.tooltip" +
                            ".borderWidth",
                    4, Config.CLIENT.mTooltipWidth, (width) -> {
                        Config.CLIENT.mTooltipWidth.set(width);
                        if (mColorPicker != null) {
                            mColorPicker.setBorderWidth(width.floatValue());
                        }
                    }, 100, mSaveFn);
            if (!rounded) {
                PreferencesFragment.setViewAndChildrenEnabled(option, false);
            }
            mBorderWidth = option;
            mContent.addView(option);
        }
        {
            var option = PreferencesFragment.createFloatOption(mParent.getContext(), "modernui.center.tooltip" +
                            ".cornerRadius",
                    3, Config.CLIENT.mTooltipRadius, (radius) -> {
                        Config.CLIENT.mTooltipRadius.set(radius);
                        if (mColorPicker != null) {
                            mColorPicker.setBorderRadius(radius.floatValue());
                        }
                    }, 10, mSaveFn);
            if (!rounded) {
                PreferencesFragment.setViewAndChildrenEnabled(option, false);
            }
            mCornerRadius = option;
            mContent.addView(option);
        }
        {
            var option = PreferencesFragment.createFloatOption(mParent.getContext(), "modernui.center.tooltip" +
                            ".shadowRadius",
                    4, Config.CLIENT.mTooltipShadowRadius, 10, mSaveFn);
            if (!rounded) {
                PreferencesFragment.setViewAndChildrenEnabled(option, false);
            }
            mShadowRadius = option;
            mContent.addView(option);
        }
        {
            var option = PreferencesFragment.createFloatOption(mParent.getContext(), "modernui.center.tooltip" +
                            ".shadowOpacity",
                    4, Config.CLIENT.mTooltipShadowAlpha, 100, mSaveFn);
            if (!rounded) {
                PreferencesFragment.setViewAndChildrenEnabled(option, false);
            }
            mShadowAlpha = option;
            mContent.addView(option);
        }
        mContent.addView(PreferencesFragment.createBooleanOption(mParent.getContext(), "modernui.center.tooltip" +
                        ".adaptiveColors",
                Config.CLIENT.mAdaptiveTooltipColors, mSaveFn));
        mContent.addView(PreferencesFragment.createIntegerOption(mParent.getContext(), "modernui.center.tooltip" +
                        ".borderCycle",
                4, 100, Config.CLIENT.mTooltipCycle, mSaveFn));
        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, mContent.dp(6), 0, 0);
        int dp4 = mContent.dp(4);
        {
            var buttonGroup = new LinearLayout(mParent.getContext());
            buttonGroup.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 4; i++) {
                var button = new Button(mParent.getContext(), null, R.attr.buttonOutlinedStyle);
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
                Config.CLIENT.mTooltipStroke, Config.CLIENT.mTooltipStroke,
                mSaveFn), new LinearLayout.LayoutParams(params));
        mColorPicker.setBorderRadius(Config.CLIENT.mTooltipRadius.get().floatValue());
        mColorPicker.setBorderWidth(Config.CLIENT.mTooltipWidth.get().floatValue());
        mParent.addView(mContent, params);
    }
}
