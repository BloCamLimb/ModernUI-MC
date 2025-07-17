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
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.text.FontFamily;
import icyllis.modernui.mc.Config;
import icyllis.modernui.mc.ModernUIClient;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class PreferredFontAccordion implements View.OnClickListener {

    final ViewGroup mParent;
    final Runnable mSaveFn;
    final Runnable mOnFontChanged;

    // lazy-init
    LinearLayout mContent;
    EditText mInput;
    Spinner mSpinner;

    // this callback is registered on a child view of 'parent'
    // so no weak ref
    public PreferredFontAccordion(ViewGroup parent, Runnable saveFn, Runnable onFontChanged) {
        mParent = parent;
        mSaveFn = saveFn;
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
        mContent = new LinearLayout(mParent.getContext());
        mContent.setOrientation(LinearLayout.VERTICAL);
        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, mContent.dp(6), 0, 0);
        {
            var layout = PreferencesFragment.createInputBox(mParent.getContext(), "gui.modernui.configValue");
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
                mSpinner.setOnItemSelectedListener((parent, view, position, id) -> {
                    if (position == 0) {
                        return;
                    }
                    String newValue = values.get(position).rootName;
                    boolean changed = applyNewValue(view.getContext(), newValue);
                    if (changed) {
                        mInput.setText(newValue);
                    }
                });
                FontFamily first = ModernUIClient.getInstance().getFirstFontFamily();
                if (first != null) {
                    String firstName = first.getFamilyName();
                    for (int i = 1; i < values.size(); i++) {
                        var candidate = values.get(i);
                        if (candidate.rootName.equalsIgnoreCase(firstName)) {
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
            mSaveFn.run();
            PreferencesFragment.reloadDefaultTypeface(context, mOnFontChanged);
            return true;
        }
        return false;
    }
}
