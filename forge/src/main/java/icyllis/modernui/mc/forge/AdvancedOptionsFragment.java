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

package icyllis.modernui.mc.forge;

import icyllis.arc3d.opengl.GLCaps;
import icyllis.modernui.R;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.*;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.font.GlyphManager;
import icyllis.modernui.graphics.text.LayoutCache;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.mc.ui.ThemeControl;
import icyllis.modernui.text.InputFilter;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.commons.io.output.StringBuilderWriter;

import java.io.PrintWriter;
import java.lang.reflect.Field;

import static icyllis.modernui.mc.forge.PreferencesFragment.*;
import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

/**
 * Developer options.
 */
public class AdvancedOptionsFragment extends Fragment {

    private static final Field OPTION_VALUE = ObfuscationReflectionHelper.findField(OptionInstance.class, "f_231481_");

    ViewGroup mContent;
    TextView mUIManagerDump;
    TextView mMainGPUResourceDump;
    TextView mUIGPUResourceDump;
    TextView mPSOStatsDump;
    TextView mGPUStatsDump;

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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var context = requireContext();
        var sv = new ScrollView(context);
        sv.addView(createPage(context), MATCH_PARENT, WRAP_CONTENT);

        sv.setEdgeEffectColor(ThemeControl.THEME_COLOR);

        var params = new FrameLayout.LayoutParams(sv.dp(720), MATCH_PARENT, Gravity.CENTER);
        var dp6 = sv.dp(6);
        params.setMargins(dp6, dp6, dp6, dp6);
        sv.setLayoutParams(params);

        return sv;
    }

    LinearLayout createPage(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        mContent = content;

        var dp6 = content.dp(6);
        {
            var category = createCategoryList(context, "Developer");

            if (ModernUIMod.isDeveloperMode()) {
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

                category.addView(createBooleanOption(context, "Remove telemetry session",
                        Config.CLIENT.mRemoveTelemetry, Config.CLIENT::saveAndReloadAsync));

                /*category.addView(createBooleanOption(context, "Secure Profile Public Key",
                        Config.CLIENT.mSecurePublicKey, Config.CLIENT::saveAndReloadAsync));*/
            }
            {
                var layout = createSwitchLayout(context, "Show Layout Bounds");
                var button = layout.<SwitchButton>requireViewById(R.id.button1);
                button.setChecked(UIManager.getInstance().isShowingLayoutBounds());
                button.setOnCheckedChangeListener((__, checked) ->
                        UIManager.getInstance().setShowingLayoutBounds(checked));
                category.addView(layout);
            }
            {
                var button = createDebugButton(context, "Take UI screenshot (Y)");
                button.setOnClickListener((__) ->
                        Core.executeOnMainThread(() -> UIManager.getInstance().takeScreenshot()));
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Dump UI manager (P)");
                button.setOnClickListener((__) ->
                        Core.executeOnMainThread(() -> UIManager.getInstance().dump()));
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Dump font atlases (G)");
                button.setOnClickListener((__) ->
                        Core.executeOnMainThread(() -> GlyphManager.getInstance().debug()));
                category.addView(button);
            }
            if (ModernUIMod.isTextEngineEnabled()) {
                {
                    var button = createDebugButton(context, "Dump bitmap fonts (V)");
                    button.setOnClickListener((__) ->
                            Core.executeOnMainThread(() -> {
                                //TextLayoutEngine.getInstance().dumpEmojiAtlas();
                                TextLayoutEngine.getInstance().dumpBitmapFonts();
                            }));
                    category.addView(button);
                }
                {
                    var button = createDebugButton(context, "Reload text layout (MC)");
                    button.setOnClickListener((__) ->
                            Core.executeOnMainThread(() -> TextLayoutEngine.getInstance().reload()));
                    category.addView(button);
                }
            }
            {
                var button = createDebugButton(context, "Reload glyph manager");
                button.setOnClickListener((__) ->
                        Core.executeOnMainThread(() -> GlyphManager.getInstance().reload()));
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Reset layout cache");
                button.setOnClickListener((__) ->
                        Core.executeOnMainThread(LayoutCache::clear));
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Reload full text engine");
                button.setOnClickListener((__) ->
                        ModernUIClient.getInstance().reloadFontStrike());
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "GC (F)");
                button.setOnClickListener((__) -> System.gc());
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Copy this page to clipboard");
                button.setOnClickListener((__) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < content.getChildCount(); i++) {
                        if (content.getChildAt(i) instanceof TextView tv) {
                            sb.append(tv.getText());
                            sb.append('\n');
                        }
                    }
                    if (!sb.isEmpty()) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    Core.executeOnMainThread(() -> Clipboard.setText(sb));
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                });
                category.addView(button);
            }
            content.addView(category);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            mUIManagerDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            tv.setText("Rendering pipeline: Arc 3D OpenGL");
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            tv.setText("Rectangle packing algorithm: Skyline (silhouette)");
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            mMainGPUResourceDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            mUIGPUResourceDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            mPSOStatsDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            mGPUStatsDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            var caps = (GLCaps) Core.requireUiRecordingContext().getCaps();
            var s = caps.toString(/*includeFormatTable*/false);
            tv.setText(s);
            content.addView(tv);
        }

        refreshPage();

        return content;
    }

    void refreshPage() {
        if (mUIManagerDump != null) {
            Core.executeOnMainThread(() -> {
                StringBuilder builder = new StringBuilder();
                try (var w = new PrintWriter(new StringBuilderWriter(builder))) {
                    UIManager.getInstance().dump(w, false);
                }
                String s = builder.toString();
                mUIManagerDump.post(() -> mUIManagerDump.setText(s));
            });
        }
        if (mMainGPUResourceDump != null) {
            Core.executeOnMainThread(() -> {
                var content = Core.requireImmediateContext();
                var s = "GPU Resource Cache (Immediate Context):\n" +
                        String.format("Current budgeted resource bytes: %s (%s bytes)",
                                TextUtils.binaryCompact(content.getCurrentBudgetedBytes()),
                                content.getCurrentBudgetedBytes()) +
                        "\n" +
                        String.format("Current purgeable resource bytes: %s (%s bytes)",
                                TextUtils.binaryCompact(content.getCurrentPurgeableBytes()),
                                content.getCurrentPurgeableBytes()) +
                        "\n" +
                        String.format("Max budgeted resource bytes: %s (%s bytes)",
                                TextUtils.binaryCompact(content.getMaxBudgetedBytes()),
                                content.getMaxBudgetedBytes());
                mMainGPUResourceDump.post(() -> mMainGPUResourceDump.setText(s));
            });
        }
        if (mUIGPUResourceDump != null) {
            /*Core.executeOnRenderThread(() -> {
                var rc = Core.requireDirectContext().getResourceCache();
                var s = "GPU Resource Cache:\n" +
                        String.format("Resource bytes: %s (%s bytes)",
                                TextUtils.binaryCompact(rc.getResourceBytes()),
                                rc.getResourceBytes()) +
                        "\n" +
                        String.format("Budgeted resource bytes: %s (%s bytes)",
                                TextUtils.binaryCompact(rc.getBudgetedResourceBytes()),
                                rc.getBudgetedResourceBytes()) +
                        "\n" +
                        String.format("Resource count: %s",
                                rc.getResourceCount()) +
                        "\n" +
                        String.format("Budgeted resource count: %s",
                                rc.getBudgetedResourceCount()) +
                        "\n" +
                        String.format("Free resource bytes: %s (%s bytes)",
                                TextUtils.binaryCompact(rc.getFreeResourceBytes()),
                                rc.getFreeResourceBytes()) +
                        "\n" +
                        String.format("Max resource bytes: %s (%s bytes)",
                                TextUtils.binaryCompact(rc.getMaxResourceBytes()),
                                rc.getMaxResourceBytes());
                mGPUResourceDump.post(() -> mGPUResourceDump.setText(s));
            });*/
            var content = Core.requireUiRecordingContext();
            var s = "GPU Resource Cache (UI Recording Context):\n" +
                    String.format("Current budgeted resource bytes: %s (%s bytes)",
                            TextUtils.binaryCompact(content.getCurrentBudgetedBytes()),
                            content.getCurrentBudgetedBytes()) +
                    "\n" +
                    String.format("Current purgeable resource bytes: %s (%s bytes)",
                            TextUtils.binaryCompact(content.getCurrentPurgeableBytes()),
                            content.getCurrentPurgeableBytes()) +
                    "\n" +
                    String.format("Max budgeted resource bytes: %s (%s bytes)",
                            TextUtils.binaryCompact(content.getMaxBudgetedBytes()),
                            content.getMaxBudgetedBytes());
            mUIGPUResourceDump.setText(s);
        }
        /*if (mPSOStatsDump != null) {
            mPSOStatsDump.setText(
                    Core.requireUiRecordingContext()
                            .getPipelineStateCache()
                            .getStats()
                            .toString()
            );
        }
        if (mGPUStatsDump != null) {
            Core.executeOnRenderThread(() -> {
                var s = Core.requireDirectContext().getDevice().getStats().toString();
                mGPUStatsDump.post(() -> mGPUStatsDump.setText(s));
            });
        }*/
        if (mContent != null) {
            mContent.postDelayed(this::refreshPage, 5_000);
        }
    }
}
