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
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.text.FontFamily;
import icyllis.modernui.mc.Config;
import icyllis.modernui.mc.ModernUIClient;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.AdapterView;
import icyllis.modernui.widget.ArrayAdapter;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.Spinner;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class PreferredFontAccordion implements View.OnClickListener,
        View.OnFocusChangeListener, AdapterView.OnItemSelectedListener {

    final ViewGroup mParent;
    final Runnable mOnChanged;
    final Runnable mOnFontChanged;

    // lazy-init
    LinearLayout mContent;
    EditText mInput;
    Spinner mSpinner;
    ArrayAdapter<FontFamilyItem> mAdapter;

    // this callback is registered on a child view of 'parent'
    // so no weak ref
    public PreferredFontAccordion(ViewGroup parent, Runnable onChanged, Runnable onFontChanged) {
        mParent = parent;
        mOnChanged = onChanged;
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
        addContent();
    }

    private void addContent() {
        var context = mParent.getContext();
        mContent = new LinearLayout(context);
        mContent.setOrientation(LinearLayout.VERTICAL);
        {
            var layout = createRowLayout(context, "gui.modernui.configValue");
            var input = mInput = new EditText(context, null, R.attr.editTextOutlinedStyle);
            input.setText(Config.CLIENT.mFirstFontFamily.get());
            input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            input.setMinimumWidth(input.dp(100));
            input.setOnFocusChangeListener(this);
            input.setSingleLine();

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(input, params);
            mContent.addView(layout);
        }
        {
            var layout = createRowLayout(context, "modernui.center.font.chooseFont");
            var spinner = mSpinner = new Spinner(context);
            spinner.setMinimumWidth(spinner.dp(300));
            CompletableFuture.supplyAsync(() -> {
                var values = FontFamily.getSystemFontMap()
                        .values()
                        .stream()
                        .map(family -> new FontFamilyItem(family.getFamilyName(),
                                family.getFamilyName(ModernUI.getSelectedLocale())))
                        .sorted()
                        .collect(Collectors.toList());
                values.add(0, new FontFamilyItem("\u2026", "\u2026"));
                return values;
            }).thenAcceptAsync(values -> {
                mSpinner.setAdapter(mAdapter = new ArrayAdapter<>(mParent.getContext(), values));
                mSpinner.setOnItemSelectedListener(this);
                updateSpinnerSelection();
            }, Core.getUiThreadExecutor());

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(spinner, params);
            mContent.addView(layout);
        }
        {
            var layout = createRowLayout(context, "modernui.center.font.openFontFile");
            var openFile = new Button(context, null, R.attr.buttonOutlinedStyle);
            openFile.setText(I18n.get("gui.modernui.browseFiles"));
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
                        boolean changed = applyNewValue(v1.getContext(), path, mOnFontChanged);
                        if (changed) {
                            mInput.setText(path);
                        }
                        mSpinner.setSelection(0);
                    });
                }
            }));
            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(openFile, params);
            mContent.addView(layout);
        }
        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, mContent.dp(6), 0, 0);
        mParent.addView(mContent, params);
    }

    private static LinearLayout createRowLayout(Context context, String name) {
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

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp6, 0, dp6, 0);
        layout.setLayoutParams(params);

        layout.setMinimumHeight(layout.dp(44));

        return layout;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v == mInput) {
            if (!hasFocus) {
                EditText v1 = (EditText) v;
                String newValue = v1.getText().toString().strip();
                applyNewValue(v1.getContext(), newValue, () -> {
                    mOnFontChanged.run();
                    updateSpinnerSelection();
                });
            }
        }
    }

    @Override
    public void onItemSelected(@NonNull AdapterView<?> parent, View view, int position, long id) {
        if (position == 0) {
            return;
        }
        String newValue = mAdapter.getItem(position).rootName;
        boolean changed = applyNewValue(view.getContext(), newValue, mOnFontChanged);
        if (changed) {
            mInput.setText(newValue);
        }
    }

    private void updateSpinnerSelection() {
        FontFamily first = ModernUIClient.getInstance().getFirstFontFamily();
        if (first != null) {
            String firstName = first.getFamilyName();
            for (int i = 1; i < mAdapter.getCount(); i++) {
                var candidate = mAdapter.getItem(i);
                if (candidate.rootName.equalsIgnoreCase(firstName)) {
                    mSpinner.setSelection(i);
                    return;
                }
            }
        }
        mSpinner.setSelection(0);
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

    private boolean applyNewValue(Context context, @NonNull String newValue,
                                  Runnable onFontChanged) {
        if (!newValue.equals(Config.CLIENT.mFirstFontFamily.get())) {
            Config.CLIENT.mFirstFontFamily.set(newValue);
            mOnChanged.run();
            PreferencesFragment.reloadDefaultTypeface(context, onFontChanged);
            return true;
        }
        return false;
    }
}
