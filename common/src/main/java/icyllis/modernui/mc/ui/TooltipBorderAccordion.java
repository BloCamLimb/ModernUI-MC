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
    final Runnable mOnChanged;

    // lazy-init
    LinearLayout mContent;
    FourColorPicker mColorPicker;

    final View[] mRoundedDependants = new View[4];

    // this callback is registered on a child view of 'parent'
    // so no weak ref
    public TooltipBorderAccordion(ViewGroup parent, Runnable onChanged) {
        mParent = parent;
        mOnChanged = onChanged;
    }

    @Override
    public void onClick(View __) {
        if (mContent != null) {
            // toggle
            mContent.setVisibility(mContent.getVisibility() == View.GONE
                    ? View.VISIBLE
                    : View.GONE);
            return;
        }
        addContent();
    }

    private void addContent() {
        mContent = new LinearLayout(mParent.getContext());
        mContent.setOrientation(LinearLayout.VERTICAL);
        int ri = 0;
        new PreferencesFragment.BooleanOption(mParent.getContext(), "modernui.center.tooltip.roundedShapes",
                Config.CLIENT.mRoundedTooltip,
                (value) -> {
                    Config.CLIENT.mRoundedTooltip.set(value);
                    for (var v : mRoundedDependants) {
                        ThemeControl.setViewAndChildrenEnabled(v, value);
                    }
                }, mOnChanged)
                .create(mContent);
        mRoundedDependants[ri++] = new PreferencesFragment.FloatOption(mParent.getContext(),
                "modernui.center.tooltip.borderWidth",
                Config.CLIENT.mTooltipWidth, (width) -> {
            Config.CLIENT.mTooltipWidth.set(width);
            if (mColorPicker != null) {
                mColorPicker.setBorderWidth(width.floatValue());
            }
        }, 100, mOnChanged)
                .create(mContent, 4);
        mRoundedDependants[ri++] = new PreferencesFragment.FloatOption(mParent.getContext(),
                "modernui.center.tooltip.cornerRadius",
                Config.CLIENT.mTooltipRadius, (radius) -> {
            Config.CLIENT.mTooltipRadius.set(radius);
            if (mColorPicker != null) {
                mColorPicker.setBorderRadius(radius.floatValue());
            }
        }, 10, mOnChanged)
                .create(mContent, 3);
        mRoundedDependants[ri++] = new PreferencesFragment.FloatOption(mParent.getContext(),
                "modernui.center.tooltip.shadowRadius",
                Config.CLIENT.mTooltipShadowRadius, 5, mOnChanged)
                .create(mContent, 4);
        mRoundedDependants[ri++] = new PreferencesFragment.FloatOption(mParent.getContext(),
                "modernui.center.tooltip.shadowOpacity",
                Config.CLIENT.mTooltipShadowAlpha, 100, mOnChanged)
                .create(mContent, 4);
        assert ri == mRoundedDependants.length;
        if (!Config.CLIENT.mRoundedTooltip.get()) {
            for (var v : mRoundedDependants) {
                ThemeControl.setViewAndChildrenEnabled(v, false);
            }
        }
        new PreferencesFragment.BooleanOption(mParent.getContext(), "modernui.center.tooltip.adaptiveColors",
                Config.CLIENT.mAdaptiveTooltipColors, mOnChanged)
                .create(mContent);
        new PreferencesFragment.IntegerOption(mParent.getContext(), "modernui.center.tooltip.borderCycle",
                100, Config.CLIENT.mTooltipCycle, mOnChanged)
                .create(mContent, 4);
        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, mContent.dp(6), 0, 0);
        int dp4 = mContent.dp(4);
        {
            var buttonGroup = new LinearLayout(mParent.getContext());
            buttonGroup.setOrientation(LinearLayout.HORIZONTAL);
            View.OnClickListener buttonOnClick = (btn) -> mColorPicker.setColors(PRESET_COLORS[btn.getId() - 1]);
            for (int i = 0; i < 4; i++) {
                var button = new Button(mParent.getContext(), null, R.attr.buttonOutlinedStyle);
                button.setId((i + 1));
                button.setText(I18n.get("gui.modernui.preset_s", (i + 1)));
                button.setOnClickListener(buttonOnClick);
                var p = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
                p.setMargins(dp4, 0, dp4, 0);
                buttonGroup.addView(button, p);
            }
            mContent.addView(buttonGroup, new LinearLayout.LayoutParams(params));
        }
        mContent.addView(mColorPicker = new FourColorPicker(mParent.getContext(),
                Config.CLIENT.mTooltipStroke, mOnChanged),
                new LinearLayout.LayoutParams(params));
        mColorPicker.setBorderRadius(Config.CLIENT.mTooltipRadius.get().floatValue());
        mColorPicker.setBorderWidth(Config.CLIENT.mTooltipWidth.get().floatValue());
        mParent.addView(mContent, params);
    }
}
