/*
 * Modern UI.
 * Copyright (C) 2019-2025 BloCamLimb. All rights reserved.
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
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.markflow.Markflow;
import icyllis.modernui.markflow.MarkflowPlugin;
import icyllis.modernui.markflow.MarkflowTheme;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.StillAlive;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.text.Spannable;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.method.LinkMovementMethod;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.resources.language.I18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

public class DashboardFragment extends Fragment {

    public static final String CREDIT_TEXT = """
            Modern UI 3.11.1
            by
            BloCamLimb
            (Icyllis Milica)
            Ciallo～(∠・ω< )⌒☆""";

    public static Changelogs sChangelogs;

    private ViewGroup mLayout;
    private TextView mSideBox;
    private TextView mInfoBox;
    private LinearLayout mChangelogList;
    private Markflow mMarkflow;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (sChangelogs == null) {
            sChangelogs = new Changelogs(Core.getUiThreadExecutor());
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var context = requireContext();
        var layout = new FrameLayout(context);
        var value = new TypedValue();
        Markflow markflow; {
            var builder = Markflow.builder(context);
            Typeface monoFont = Typeface.getSystemFont("JetBrains Mono Medium");
            if (monoFont != Typeface.SANS_SERIF) {
                builder.usePlugin(new MarkflowPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkflowTheme.Builder builder) {
                        builder.codeTypeface(monoFont);
                    }
                });
            }
            markflow = builder.build();
        }
        mMarkflow = markflow;

        {
            // two panel layout
            var content = new WrappingLinearLayout(context);
            content.setWrapWidth(content.dp(864));
            content.setMaxWidth(content.dp(746));
            int dp4 = content.dp(4);

            {
                var panel = new LinearLayout(context);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setClipToPadding(false);
                panel.setGravity(Gravity.CENTER_VERTICAL);
                // TITLE
                {
                    var title = new TextView(context);
                    title.setText(I18n.get("modernui.center.title"));
                    title.setTextSize(32);
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
                    params.bottomMargin = content.dp(40);
                    panel.addView(title, params);
                }
                {
                    var info = new TextView(context);
                    info.setMovementMethod(LinkMovementMethod.getInstance());
                    markflow.setMarkdown(info, """
                            Source Code: [ModernUI](https://github.com/BloCamLimb/ModernUI) [ModernUI-MC](https://github.com/BloCamLimb/ModernUI-MC) \s
                            Mod Releases: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/modern-ui) [Modrinth](https://modrinth.com/mod/modern-ui) \s
                            Community: [Discord](https://discord.gg/kmyGKt2)""");
                    var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                    params.bottomMargin = content.dp(40);
                    panel.addView(info, params);
                }

                ThemeControl.makeElevatedCard(context, panel, value);
                var params = new LinearLayout.LayoutParams(MATCH_PARENT, content.dp(420), 1);
                params.setMargins(dp4, dp4, dp4, dp4);
                content.addView(panel, params);
            }

            {
                var panel = new NestedScrollView(context);
                panel.setClipToPadding(false);
                panel.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);

                {
                    var inner = new LinearLayout(context);
                    inner.setOrientation(LinearLayout.VERTICAL);
                    mChangelogList = inner;

                    {
                        var tv = new TextView(context);
                        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                        tv.setMovementMethod(LinkMovementMethod.getInstance());
                        markflow.setMarkdown(tv, """
                                What's New in Modern UI 3.12.0
                                ----
                                * Brand-New Graphics Engine
                                * Better Text Rendering
                                * Better Mod Compatibility
                                * Emoji 16.0 Support
                                * Rendering Optimizations""");
                        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                        params.bottomMargin = content.dp(20);
                        inner.addView(tv, params);
                    }

                    if (sChangelogs.isDone()) {
                        addChangelogs(this, sChangelogs.getResult());
                    } else {
                        var progressBar = new ProgressBar(context);
                        progressBar.setIndeterminate(true);
                        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                        params.gravity = Gravity.CENTER;
                        inner.addView(progressBar, params);

                        final WeakReference<DashboardFragment> weakThis = new WeakReference<>(this);
                        sChangelogs.addCallback(s -> addChangelogs(weakThis.get(), s));
                    }

                    panel.addView(inner, MATCH_PARENT, WRAP_CONTENT);
                }

                ThemeControl.makeElevatedCard(context, panel, value);
                var params = new LinearLayout.LayoutParams(MATCH_PARENT, content.dp(420), 1);
                params.setMargins(dp4, dp4, dp4, dp4);
                content.addView(panel, params);
            }

            var sv = new ScrollView(context);
            var params = new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
            sv.addView(content, params);
            layout.addView(sv);
        }

        if (false){
            TextView tv;
            if (mSideBox == null) {
                tv = new Button(getContext());
                tv.setText("Still Alive");
                tv.setTextSize(16);
                tv.setTextColor(0xFFDCAE32);
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                tv.setOnClickListener(this::play);
                mSideBox = tv;
            } else {
                tv = mSideBox;
            }

            var params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(tv.dp(120));
            params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            layout.addView(tv, params);
        }

        if (false){
            TextView tv;
            if (mInfoBox == null) {
                tv = new TextView(getContext());
                tv.setTextSize(16);
                // leading margin is based on para dir, not view dir
                tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                tv.setMovementMethod(LinkMovementMethod.getInstance());
                tv.setSpannableFactory(Spannable.NO_COPY_FACTORY);
                markflow
                        .setMarkdown(tv, """
                                What's New in Modern UI 3.11.1
                                ----
                                * Brand-New Graphics Engine
                                * Better Text Rendering
                                * Better Mod Compatibility
                                * Emoji 16.0 Support
                                * Rendering Optimizations
                                * [Full Changelog…](https://github.com/BloCamLimb/ModernUI/blob/master/changelogs.md)
                                * [Full Changelog…](https://github.com/BloCamLimb/ModernUI-MC/blob/master/changelogs.md)
                                \s
                                > Author: BloCamLimb \s
                                  Source Code: [Modern UI](https://github.com/BloCamLimb/ModernUI) \s
                                  Source Code: [Modern UI (MC)](https://github.com/BloCamLimb/ModernUI-MC)""");
                mInfoBox = tv;
            } else {
                tv = mInfoBox;
            }

            var params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(tv.dp(120));
            params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            layout.addView(tv, params);
        }

        {
            var tv = new TextView(getContext());
            tv.setTextSize(12);
            tv.setText("Copyright © 2019-2025 BloCamLimb. All rights reserved.");
            var params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            layout.addView(tv, params);
        }

        var transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        layout.setLayoutTransition(transition);
        return mLayout = layout;
    }

    private static void addChangelogs(@Nullable DashboardFragment f, @Nullable String result) {
        if (f == null || f.mChangelogList == null) {
            // was destroyed
            return;
        }
        ViewGroup list = f.mChangelogList;
        if (list.getChildCount() > 1) {
            // remove the progress bar
            list.removeViewAt(1);
        }
        TextView tv = new TextView(list.getContext());
        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        f.mMarkflow.setMarkdown(tv, result != null ? result :
                """
                [Full Changelog…](https://github.com/BloCamLimb/ModernUI-MC/blob/master/changelogs.md)""");
        list.addView(tv);
    }

    final Runnable mUpdateText = this::updateText;

    private void play(View button) {
        var tv = (TextView) button;
        tv.setText("", TextView.BufferType.EDITABLE);
        tv.setClickable(false);
        if (mInfoBox != null) {
            mInfoBox.setVisibility(View.GONE);
        }

        StillAlive.getInstance().start();

        mLayout.postDelayed(() -> {
            if (mLayout.isAttachedToWindow()) {
                var view = new View(getContext());
                //view.setBackground(new Background(view));

                var params = new FrameLayout.LayoutParams(view.dp(480), view.dp(270));
                params.setMarginStart(view.dp(60));
                params.setMarginEnd(view.dp(30));
                params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;

                mLayout.addView(view, params);
            }
        }, 18000);

        mLayout.postDelayed(mUpdateText, 2000);
    }

    private void updateText() {
        if (!mSideBox.isAttachedToWindow()) {
            return;
        }
        var editable = mSideBox.getEditableText();
        if (editable.length() < CREDIT_TEXT.length()) {
            editable.append(CREDIT_TEXT.charAt(editable.length()));
            if (editable.length() < CREDIT_TEXT.length()) {
                mSideBox.postDelayed(mUpdateText, 250);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLayout = null;
        mChangelogList = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        StillAlive.getInstance().stop();
    }

    static class WrappingLinearLayout extends LinearLayout {

        private int mWrapWidth;
        private int mMaxWidth;

        public WrappingLinearLayout(Context context) {
            super(context);
        }

        public void setWrapWidth(int wrapWidth) {
            mWrapWidth = wrapWidth;
        }

        public int getWrapWidth() {
            return mWrapWidth;
        }

        public void setMaxWidth(int maxWidth) {
            mMaxWidth = maxWidth;
        }

        public int getMaxWidth() {
            return mMaxWidth;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            var orientation = width >= mWrapWidth
                    ? HORIZONTAL : VERTICAL;
            setOrientation(orientation);
            if (mMaxWidth > 0) {
                int limit = orientation == HORIZONTAL
                        ? getChildCount() * mMaxWidth : mMaxWidth;
                if (width > limit) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.EXACTLY);
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    // UI thread only
    public static class Changelogs {

        private CompletableFuture<String> future;
        private String result;
        private ArrayList<Consumer<String>> callbacks;

        public Changelogs(Executor uiExecutor) {
            future = CompletableFuture.supplyAsync(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL("https://raw.githubusercontent.com/BloCamLimb/ModernUI-MC/refs/heads/master/changelogs.md");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(60_000); // 1min
                    connection.setReadTimeout(180_000); // 3min
                    connection.connect();
                    var sb = new StringBuilder();
                    try (var br = new BufferedReader(new InputStreamReader(
                            connection.getInputStream(), StandardCharsets.UTF_8))) {
                        // read at least 300 lines
                        for (int i = 0; i < 300; i++) {
                            String line = br.readLine();
                            if (line == null) {
                                break;
                            }
                            if (line.startsWith("===")) {
                                // drop the first headline
                                sb.setLength(0);
                            } else {
                                sb.append(line).append('\n');
                            }
                        }
                        // truncate at next heading
                        for (;;) {
                            String line = br.readLine();
                            if (line == null || line.startsWith("### ")) {
                                break;
                            }
                            sb.append(line).append('\n');
                        }
                    }
                    sb.append("[Full Changelog…](https://github.com/BloCamLimb/ModernUI-MC/blob/master/changelogs.md)");
                    return sb.toString();
                } catch (IOException e) {
                    throw new CompletionException(e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }).whenCompleteAsync((res, ex) -> {
                if (ex != null) {
                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    ModernUIMod.LOGGER.warn(CenterFragment2.MARKER,
                            "Failed to request changelogs", cause);
                } else {
                    result = res;
                }
                future = null;
                if (callbacks != null) {
                    callbacks.forEach(c -> c.accept(result));
                }
                callbacks = null;
            }, uiExecutor);
        }

        public boolean isDone() {
            return future == null;
        }

        public void addCallback(@NonNull Consumer<String> cb) {
            assert !isDone();
            if (callbacks == null) {
                callbacks = new ArrayList<>();
            }
            callbacks.add(cb);
        }

        @Nullable
        public String getResult() {
            return result;
        }
    }
}
