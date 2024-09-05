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

package icyllis.modernui.mc;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.markdown.*;
import icyllis.modernui.markdown.core.CorePlugin;
import icyllis.modernui.text.*;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

public class MarkdownFragment extends Fragment {

    private Markdown mMarkdown;

    private EditText mInput;
    private TextView mPreview;

    private final Runnable mRenderMarkdown = () -> mMarkdown.setMarkdown(mPreview, mInput.getText().toString());

    @Override
    public void onCreate(DataSet savedInstanceState) {
        super.onCreate(savedInstanceState);
        var builder = Markdown.builder(requireContext())
                .usePlugin(CorePlugin.create());
        Typeface monoFont = Typeface.getSystemFont("JetBrains Mono Medium");
        if (monoFont != Typeface.SANS_SERIF) {
            builder.usePlugin(new MarkdownPlugin() {
                @Override
                public void configureTheme(@NonNull MarkdownTheme.Builder builder) {
                    builder.setCodeTypeface(monoFont);
                }
            });
        }
        mMarkdown = builder
                .setBufferType(TextView.BufferType.EDITABLE)
                .build();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);

        {
            EditText input = mInput = new EditText(requireContext());
            int dp6 = input.dp(6);
            input.setPadding(dp6, dp6, dp6, dp6);
            input.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            input.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);

            var params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            layout.addView(input, params);
        }

        {
            TextView preview = mPreview = new TextView(requireContext());
            int dp6 = preview.dp(6);
            preview.setPadding(dp6, dp6, dp6, dp6);
            preview.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            preview.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            preview.setTextIsSelectable(true);

            var params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            layout.addView(preview, params);
        }

        mInput.setText("""
                Modern UI Markdown
                ---
                My **First** Line
                > My *Second* Line
                * One
                  * ```java
                    public static void main(String[] args) {
                        System.out.println("Hello, Modern UI!");
                    }
                    ```
                  * Three
                    * Four
                                       \s
                1. One
                2. Two
                3. Three
                # Heading 1
                ## Heading 2 👋
                ### Heading 3 🤔
                                       \s
                AAA AAA
                ******
                BBB BBB
                \s""");
        mRenderMarkdown.run();
        mInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mPreview.removeCallbacks(mRenderMarkdown);
                mPreview.postDelayed(mRenderMarkdown, 600);
            }
        });

        return layout;
    }
}
