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
import icyllis.modernui.TestFragment;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.FragmentContainerView;
import icyllis.modernui.fragment.FragmentTransaction;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.DisplayMetrics;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RadioButton;
import icyllis.modernui.widget.RadioGroup;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class CenterFragment2 extends Fragment {

    private static final int id_tab_container = 0x2002;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        getParentFragmentManager().beginTransaction()
                .setPrimaryNavigationFragment(this)
                .commit();
    }

    @Override
    public void onCreate(@Nullable DataSet savedInstanceState) {
        super.onCreate(savedInstanceState);
        var ft = getChildFragmentManager().beginTransaction();
        var args = getArguments();
        if (args != null && args.getBoolean("navigateToPreferences")) {
            ft.replace(id_tab_container, PreferencesFragment.class, null, "preferences");
        } else {
            ft.replace(id_tab_container, DashboardFragment.class, null, "dashboard");
        }
        ft
                .setReorderingAllowed(true)
                .commit();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var value = new TypedValue();
        var theme = requireContext().getTheme();

        var base = new LinearLayout(getContext());
        base.setOrientation(LinearLayout.HORIZONTAL);
        base.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        base.setDividerDrawable(ThemeControl.makeDivider(base, true));

        // TITLE
        if (false) {
            var title = new TextView(getContext());
            title.setId(R.id.title);
            title.setText(I18n.get("modernui.center.title"));
            title.setTextSize(22);
            title.setTextStyle(Typeface.BOLD);
            title.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int height = bottom - top;
                int oldHeight = oldBottom - oldTop;
                if (height != oldHeight) {
                    var tv = (TextView) v;
                    tv.getPaint().setShader(new LinearGradient(0, 0, height * 2, height,
                            // Minato Aqua
                            new int[]{
                                    0xFFB8DFF4,
                                    0xFFF8C5CE,
                                    0xFFFEFDF0
                            },
                            null,
                            Shader.TileMode.MIRROR,
                            null));
                }
            });

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            params.setMarginsRelative(0, base.dp(12), 0, base.dp(12));
            base.addView(title, params);
        }

        // NAV BUTTONS
        {
            var buttonGroup = new RadioGroup(getContext());
            theme.resolveAttribute(R.ns, R.attr.colorSurfaceContainer, value, true);
            buttonGroup.setBackground(new ColorDrawable(value.data));
            buttonGroup.setOrientation(LinearLayout.VERTICAL);
            buttonGroup.setGravity(Gravity.CENTER);

            var icons = Image.create(ModernUI.ID, "gui/new_icon.png");
            if (icons != null) {
                // icons are designed for xxhigh
                icons.setDensity(DisplayMetrics.DENSITY_DEFAULT * 3);
            }
            int colorOnSecondaryContainer;
            ColorStateList itemTextColor;
            {
                int[] colors = new int[2];
                theme.resolveAttribute(R.ns, R.attr.colorSecondary, value, true);
                colors[0] = value.data; // selected
                theme.resolveAttribute(R.ns, R.attr.colorOnSurfaceVariant, value, true);
                colors[1] = value.data; // other
                itemTextColor = new ColorStateList(
                        new int[][]{
                                new int[]{R.attr.state_checked},
                                StateSet.WILD_CARD
                        },
                        colors
                );
            }
            ColorStateList itemIconTint;
            {
                int[] colors = new int[2];
                theme.resolveAttribute(R.ns, R.attr.colorOnSecondaryContainer, value, true);
                colors[0] = colorOnSecondaryContainer = value.data; // selected
                theme.resolveAttribute(R.ns, R.attr.colorOnSurfaceVariant, value, true);
                colors[1] = value.data; // other
                itemIconTint = new ColorStateList(
                        new int[][]{
                                new int[]{R.attr.state_checked},
                                StateSet.WILD_CARD
                        },
                        colors
                );
            }
            ColorStateList itemRippleColor = new ColorStateList(
                    new int[][]{
                            new int[]{R.attr.state_pressed},
                            new int[]{R.attr.state_focused},
                            new int[]{R.attr.state_hovered},
                            StateSet.WILD_CARD
                    },
                    new int[]{
                            ColorStateList.modulateColor(colorOnSecondaryContainer, 0.1f),
                            ColorStateList.modulateColor(colorOnSecondaryContainer, 0.1f),
                            ColorStateList.modulateColor(colorOnSecondaryContainer, 0.08f),
                            ColorStateList.modulateColor(colorOnSecondaryContainer, 0.08f)
                    }
            );
            ColorStateList activeIndicatorColor;
            {
                int[] colors = new int[2];
                theme.resolveAttribute(R.ns, R.attr.colorSecondaryContainer, value, true);
                colors[0] = value.data; // selected
                activeIndicatorColor = new ColorStateList(
                        new int[][]{
                                new int[]{R.attr.state_checked},
                                StateSet.WILD_CARD
                        },
                        colors
                );
            }

            buttonGroup.addView(createNavButton(1001, "modernui.center.tab.dashboard",
                    itemTextColor, icons, 2, 0,
                    itemIconTint, itemRippleColor, activeIndicatorColor));
            buttonGroup.addView(createNavButton(1002, "modernui.center.tab.preferences",
                    itemTextColor, icons, 0, 0,
                    itemIconTint, itemRippleColor, activeIndicatorColor));
            if (ModernUIMod.isDeveloperMode()) {
                buttonGroup.addView(createNavButton(1003, "modernui.center.tab.developerOptions",
                        itemTextColor, icons, 0, 6,
                        itemIconTint, itemRippleColor, activeIndicatorColor));
            }
            buttonGroup.addView(createNavButton(1004, "soundCategory.music",
                    itemTextColor, icons, 0, 6,
                    itemIconTint, itemRippleColor, activeIndicatorColor));
            if (ModernUIMod.isDeveloperMode()) {
                buttonGroup.addView(createNavButton(1005, "Dev",
                        itemTextColor, icons, 0, 6,
                        itemIconTint, itemRippleColor, activeIndicatorColor));
            }
            buttonGroup.addView(createNavButton(1006, "Markdown",
                    itemTextColor, icons, 0, 6,
                    itemIconTint, itemRippleColor, activeIndicatorColor));

            var args = getArguments();
            buttonGroup.check(args != null && args.getBoolean("navigateToPreferences") ? 1002 : 1001);

            buttonGroup.setOnCheckedChangeListener((group, checkedId) -> {
                var fm = getChildFragmentManager();
                FragmentTransaction ft = null;
                switch (checkedId) {
                    case 1001 -> {
                        ft = fm.beginTransaction()
                                .replace(id_tab_container, DashboardFragment.class, null, "dashboard");
                    }
                    case 1002 -> {
                        ft = fm.beginTransaction()
                                .replace(id_tab_container, PreferencesFragment.class, null, "preferences");
                    }
                    case 1003 -> {
                        ft = fm.beginTransaction()
                                .replace(id_tab_container, AdvancedOptionsFragment.class, null, "developerOptions");
                    }
                    case 1004 -> {
                        ft = fm.beginTransaction()
                                .replace(id_tab_container, MusicFragment.class, null, "music");
                    }
                    case 1005 -> {
                        ft = fm.beginTransaction()
                                .replace(id_tab_container, TestFragment.class, null, "dev");
                    }
                    case 1006 -> {
                        ft = fm.beginTransaction()
                                .replace(id_tab_container, MarkdownFragment.class, null, "markdown");
                    }
                }
                if (ft != null) {
                    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                            .setReorderingAllowed(true)
                            .commit();
                }
            });

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
            params.gravity = Gravity.CENTER;
            base.addView(buttonGroup, params);
        }

        // TAB CONTAINER
        {
            var tabContainer = new FragmentContainerView(getContext());
            tabContainer.setId(id_tab_container);
            var params = new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            base.addView(tabContainer, params);
        }

        var params = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        base.setLayoutParams(params);
        return base;
    }

    private RadioButton createNavButton(int id, String text,
                                        ColorStateList itemTextColor,
                                        Image icons, int row, int col,
                                        ColorStateList itemIconTint,
                                        ColorStateList itemRippleColor,
                                        ColorStateList activeIndicatorColor) {
        var button = new RadioButton(getContext(), null, null, null);
        button.setFocusable(true);
        button.setClickable(true);
        button.setId(id);
        button.setText(I18n.get(text));
        button.setTextSize(12);
        button.setTextColor(itemTextColor);
        button.setGravity(Gravity.CENTER);
        final int dp8 = button.dp(8);
        button.setPadding(0, dp8, 0, dp8);

        {
            ShapeDrawable indicator = new ShapeDrawable();
            indicator.setSize(button.dp(56), button.dp(32));
            indicator.setColor(activeIndicatorColor);
            indicator.setCornerRadius(1000);
            RippleDrawable ripple = new RippleDrawable(itemRippleColor,
                    indicator, null);
            if (icons != null) {
                ImageDrawable icon = new ImageDrawable(requireContext().getResources(), icons);
                icon.setSrcRect(col * 72, row * 72, (col + 1) * 72, (row + 1) * 72);
                icon.setTintList(itemIconTint);
                icon.setGravity(Gravity.CENTER);
                ripple.addLayer(icon);
            }
            button.setCompoundDrawablesWithIntrinsicBounds(null,
                    ripple, null, null);
            button.setCompoundDrawablePadding(button.dp(4));
        }

        var params = new LinearLayout.LayoutParams(button.dp(88), WRAP_CONTENT);
        button.setLayoutParams(params);

        return button;
    }
}
