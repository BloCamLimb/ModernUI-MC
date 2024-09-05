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
import icyllis.modernui.TestFragment;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.*;
import icyllis.modernui.graphics.*;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.ui.ThemeControl;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.*;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class CenterFragment2 extends Fragment {

    private static final int id_tab_container = 0x2002;

    private static final ColorStateList NAV_BUTTON_COLOR = new ColorStateList(
            new int[][]{
                    new int[]{R.attr.state_checked},
                    StateSet.get(StateSet.VIEW_STATE_HOVERED),
                    StateSet.WILD_CARD},
            new int[]{
                    0xFFFFFFFF, // selected
                    0xFFE0E0E0, // hovered
                    0xFFB4B4B4} // other
    );

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
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .setReorderingAllowed(true)
                .commit();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var base = new LinearLayout(getContext());
        base.setOrientation(LinearLayout.VERTICAL);
        base.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        base.setDividerDrawable(ThemeControl.makeDivider(base));
        base.setDividerPadding(base.dp(8));

        // TITLE
        {
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
            buttonGroup.setOrientation(LinearLayout.HORIZONTAL);
            buttonGroup.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);

            buttonGroup.addView(createNavButton(1001, "modernui.center.tab.dashboard"));
            buttonGroup.addView(createNavButton(1002, "modernui.center.tab.preferences"));
            if (ModernUIMod.isDeveloperMode()) {
                buttonGroup.addView(createNavButton(1003, "modernui.center.tab.developerOptions"));
            }
            buttonGroup.addView(createNavButton(1004, "soundCategory.music"));
            if (ModernUIMod.isDeveloperMode()) {
                buttonGroup.addView(createNavButton(1005, "Dev"));
            }
            buttonGroup.addView(createNavButton(1006, "Markdown"));

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

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
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

    private RadioButton createNavButton(int id, String text) {
        var button = new RadioButton(getContext());
        button.setId(id);
        button.setText(I18n.get(text));
        button.setTextSize(16);
        button.setTextColor(NAV_BUTTON_COLOR);
        final int dp6 = button.dp(6);
        button.setPadding(dp6, 0, dp6, 0);
        ThemeControl.addBackground(button);

        var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMarginsRelative(dp6 * 3, dp6, dp6 * 3, dp6);
        button.setLayoutParams(params);

        return button;
    }
}
