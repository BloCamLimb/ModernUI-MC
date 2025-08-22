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

import icyllis.arc3d.opengl.GLCaps;
import icyllis.modernui.R;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.*;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.text.LayoutCache;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.text.GlyphManager;
import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.text.*;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.apache.commons.io.output.StringBuilderWriter;

import java.io.PrintWriter;
import java.lang.reflect.Field;

import static icyllis.modernui.mc.ui.PreferencesFragment.*;
import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

/**
 * Developer options.
 */
public class AdvancedOptionsFragment extends Fragment {

    private static final Field OPTION_VALUE = getOptionValue();

    private static Field getOptionValue() {
        for (String can : new String[]{"value", "f_231481_"}) {
            try {
                Field f = OptionInstance.class.getDeclaredField(can);
                f.setAccessible(true);
                return f;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    ViewGroup mContent;
    TextView mUIManagerDump;
    TextView mMainGPUResourceDump;
    TextView mUIGPUResourceDump;
    TextView mPSOStatsDump;
    TextView mGPUStatsDump;

    public static Button createDebugButton(Context context, String text) {
        var button = new Button(context, null, R.attr.buttonOutlinedStyle);
        button.setText(text);

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        var margin = button.dp(6);
        params.setMargins(margin, margin, margin, margin);
        button.setLayoutParams(params);
        return button;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var context = requireContext();
        var sv = new ClampingScrollView(context);
        final int dp20 = sv.dp(20);
        final int maxWidth = sv.dp(800) + dp20 + dp20;
        sv.setMaxWidth(maxWidth);
        {
            ViewGroup layout = createPage(context);
            layout.setPadding(dp20, 0, dp20, 0);
            var params = new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
            sv.addView(layout, params);
        }

        //sv.setEdgeEffectColor(ThemeControl.THEME_COLOR);
        //sv.setTopEdgeEffectBlendMode(BlendMode.SRC_OVER);
        //sv.setBottomEdgeEffectBlendMode(BlendMode.SRC_OVER);

        return sv;
    }

    LinearLayout createPage(Context context) {
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        mContent = content;

        var dp6 = content.dp(6);
        {
            var category = createCategoryList(content, "Developer");

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
                            if (OPTION_VALUE != null) {
                                try {
                                    // no listener
                                    OPTION_VALUE.set(Minecraft.getInstance().options.gamma(), gamma);
                                    return;
                                } catch (Exception ignored) {
                                }
                            }
                            Minecraft.getInstance().options.gamma().set(gamma);
                        }
                    });
                    category.addView(option);
                }

                /*category.addView(createBooleanOption(context, "Remove Message Signature",
                        Config.CLIENT.mRemoveSignature, Config.CLIENT::saveAndReloadAsync));*/

                new PreferencesFragment.BooleanOption(context, "Remove telemetry session",
                        Config.CLIENT.mRemoveTelemetry, () -> {
                            Config.CLIENT.reload();
                            MuiPlatform.get().saveConfig(Config.TYPE_CLIENT);
                        })
                        .create(category);

                /*category.addView(createBooleanOption(context, "Secure Profile Public Key",
                        Config.CLIENT.mSecurePublicKey, Config.CLIENT::saveAndReloadAsync));*/
            }
            {
                var layout = createSwitchLayout(context, "Show layout bounds");
                var button = layout.<Switch>requireViewById(R.id.button1);
                button.setChecked(UIManager.getInstance().isShowingLayoutBounds());
                button.setOnCheckedChangeListener((__, checked) ->
                        UIManager.getInstance().setShowingLayoutBounds(checked));
                category.addView(layout);
            }
            new BooleanOption(context, "Use staging buffers in OpenGL",
                    () -> Boolean.parseBoolean(
                            ModernUIClient.getBootstrapProperty(ModernUIClient.BOOTSTRAP_USE_STAGING_BUFFERS_IN_OPENGL)
                    ),
                    (value) -> ModernUIClient.setBootstrapProperty(
                            ModernUIClient.BOOTSTRAP_USE_STAGING_BUFFERS_IN_OPENGL,
                            Boolean.toString(value)
                    ))
                    .setDefaultValue(false)
                    .setNeedsRestart()
                    .create(category);
            new BooleanOption(context, "Allow SPIR-V in OpenGL",
                    () -> Boolean.parseBoolean(
                            ModernUIClient.getBootstrapProperty(ModernUIClient.BOOTSTRAP_ALLOW_SPIRV_IN_OPENGL)
                    ),
                    (value) -> ModernUIClient.setBootstrapProperty(
                            ModernUIClient.BOOTSTRAP_ALLOW_SPIRV_IN_OPENGL,
                            Boolean.toString(value)
                    ))
                    .setDefaultValue(false)
                    .setNeedsRestart()
                    .create(category);
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
            if (ModernUIMod.isTextEngineEnabled()) {
                {
                    var button = createDebugButton(context, "Dump font atlases (G)");
                    button.setOnClickListener((__) ->
                            Core.executeOnMainThread(() -> GlyphManager.getInstance().debug()));
                    category.addView(button);
                }
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
                    var button = createDebugButton(context, "Dump text layout cache");
                    button.setOnClickListener((__) -> {
                        Core.executeOnMainThread(() -> {
                            TextLayoutEngine.getInstance().dumpLayoutCache();
                        });
                    });
                    category.addView(button);
                }
                {
                    var button = createDebugButton(context, "Reload glyph manager");
                    button.setOnClickListener((__) ->
                            Core.executeOnMainThread(() -> GlyphManager.getInstance().reload()));
                    category.addView(button);
                }
                {
                    var button = createDebugButton(context, "Reload text layout (MC)");
                    button.setOnClickListener((__) ->
                            Core.executeOnMainThread(() -> TextLayoutEngine.getInstance().reload()));
                    category.addView(button);
                }
                {
                    var button = createDebugButton(context, "Reload full text engine");
                    button.setOnClickListener((__) ->
                            ModernUIClient.getInstance().reloadFontStrike());
                    category.addView(button);
                }
            }
            {
                var button = createDebugButton(context, "Reset layout cache");
                button.setOnClickListener((__) ->
                        Core.executeOnMainThread(LayoutCache::clear));
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Reset changelogs");
                button.setOnClickListener((__) -> DashboardFragment.sChangelogs = null);
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Purge GPU resources on immediate context");
                button.setOnClickListener((__) ->
                        Core.executeOnMainThread(() ->
                                Core.requireImmediateContext().freeGpuResources()));
                category.addView(button);
            }
            {
                var button = createDebugButton(context, "Purge GPU resources on UI recording context");
                button.setOnClickListener((__) ->
                        Core.requireUiRecordingContext().freeGpuResources());
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

        Typeface monoFont = Typeface.getSystemFont("JetBrains Mono Medium");
        if (monoFont == Typeface.SANS_SERIF) {
            monoFont = null;
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            if (monoFont != null) {
                tv.setTypeface(monoFont);
            }
            mUIManagerDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            if (monoFont != null) {
                tv.setTypeface(monoFont);
            }
            tv.setText("Rendering pipeline: Arc3D Granite (OpenGL)");
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            if (monoFont != null) {
                tv.setTypeface(monoFont);
            }
            tv.setText("Rectangle packing algorithm: Skyline (silhouette)");
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            if (monoFont != null) {
                tv.setTypeface(monoFont);
            }
            mMainGPUResourceDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            if (monoFont != null) {
                tv.setTypeface(monoFont);
            }
            mUIGPUResourceDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            if (monoFont != null) {
                tv.setTypeface(monoFont);
            }
            mPSOStatsDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            if (monoFont != null) {
                tv.setTypeface(monoFont);
            }
            mGPUStatsDump = tv;
            content.addView(tv);
        }

        {
            var tv = new TextView(context);
            tv.setTextSize(12);
            tv.setPadding(dp6, dp6, dp6, dp6);
            if (monoFont != null) {
                tv.setTypeface(monoFont);
            }
            var caps = (GLCaps) Core.requireUiRecordingContext().getCaps();
            StringBuilder sb = new StringBuilder("GL Capabilities:\n");
            caps.dump(sb, /*includeFormatTable*/false);
            tv.setText(sb);
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
