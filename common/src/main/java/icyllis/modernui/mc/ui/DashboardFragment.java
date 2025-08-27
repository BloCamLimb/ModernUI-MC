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
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.markflow.Markflow;
import icyllis.modernui.markflow.MarkflowPlugin;
import icyllis.modernui.markflow.MarkflowTheme;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.StillAlive;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.method.LinkMovementMethod;
import icyllis.modernui.util.ColorStateList;
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

    public static Changelogs sChangelogs;

    private FrameLayout mLayout;
    private LinearLayout mChangelogList;
    private Markflow mMarkflow;
    private int mClickCount;
    private TextView mLyricView;
    private TextView mCreditView;
    private TextView mArtView;

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
                    context.getTheme().resolveAttribute(R.ns, R.attr.colorControlHighlight, value, true);
                    title.setBackground(new RippleDrawable(ColorStateList.valueOf(value.data), null,
                            new ColorDrawable(~0)));
                    title.setOnClickListener(this::prepare);
                    mClickCount = 0;

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
        layout.setLayoutTransition(transition);
        return mLayout = layout;
    }

    private static void addChangelogs(@Nullable DashboardFragment f, @Nullable String result) {
        ViewGroup list;
        if (f == null || (list = f.mChangelogList) == null) {
            // was destroyed
            return;
        }
        if (list.getChildCount() > 1) {
            // remove the progress bar
            list.removeViewAt(1);
        }
        TextView tv = new TextView(list.getContext());
        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        f.mMarkflow.setMarkdown(tv, result != null ? result :
                "[Full Changelog…](https://github.com/BloCamLimb/ModernUI-MC/blob/master/changelogs.md)");
        list.addView(tv);
    }

    private Runnable mUpdateText;

    // all events
    private StillAlive.Event[] mEvents;
    private int mEventIndex;
    private long mEventStartTime;
    // current line
    private String mLyricLine;
    private int mLyricIndex;
    private int mLyricStartTime;
    private int mLyricInterval;
    private boolean mLyricNeedsWrap;

    private int mCreditIndex;
    private int mCreditStartTime;
    private int mCreditInterval;

    private void prepare(View button) {
        if (++mClickCount == 10) {
            mLayout.removeAllViews();
            mChangelogList = null;

            mLayout.postDelayed(this::play, 2000);
        }
    }

    private void play() {
        Context context = requireContext();
        GridLayout gridLayout = new GridLayout(context);
        gridLayout.setRowCount(2);
        gridLayout.setColumnCount(2);
        gridLayout.setUseDefaultMargins(true);

        Typeface monoFont = Typeface.getSystemFont("JetBrains Mono Medium");
        if (monoFont == Typeface.SANS_SERIF) {
            monoFont = Typeface.MONOSPACED;
        }
        TypedValue value = new TypedValue();
        {
            var tv = new TextView(context);
            tv.setTypeface(monoFont);
            tv.setTextSize(14);
            tv.setLineSpacing(0, 1.5f / 1.1f);
            tv.setText("", TextView.BufferType.EDITABLE);
            tv.setTextDirection(View.TEXT_DIRECTION_LTR);
            ThemeControl.makeOutlinedCard(context, tv, value);

            var params = new GridLayout.LayoutParams();
            params.rowSpec = GridLayout.spec(0, 2, 1F);
            params.columnSpec = GridLayout.spec(0, 1, 1F);
            params.width = 0;
            params.height = 0;
            gridLayout.addView(tv, params);
            mLyricView = tv;
        }

        {
            var tv = new TextView(context);
            tv.setTypeface(monoFont);
            tv.setTextSize(14);
            tv.setLineSpacing(0, 1.5f / 1.1f);
            tv.setText("", TextView.BufferType.EDITABLE);
            tv.setTextDirection(View.TEXT_DIRECTION_LTR);
            tv.setGravity(Gravity.BOTTOM);
            ThemeControl.makeOutlinedCard(context, tv, value);

            var params = new GridLayout.LayoutParams();
            params.rowSpec = GridLayout.spec(0, 1F);
            params.columnSpec = GridLayout.spec(1, 1F);
            params.width = 0;
            params.height = 0;
            gridLayout.addView(tv, params);
            mCreditView = tv;
        }

        {
            var tv = new TextView(context);
            tv.setTypeface(monoFont);
            tv.setTextSize(14);
            tv.setLineSpacing(0, 1f / 1.1f);
            tv.setTextDirection(View.TEXT_DIRECTION_LTR);
            tv.setGravity(Gravity.CENTER);
            ThemeControl.makeOutlinedCard(context, tv, value);

            var params = new GridLayout.LayoutParams();
            params.rowSpec = GridLayout.spec(1, 1.1F);
            params.columnSpec = GridLayout.spec(1, 1F);
            params.width = 0;
            params.height = 0;
            gridLayout.addView(tv, params);
            mArtView = tv;
        }

        mLayout.addView(gridLayout);

        mEvents = StillAlive.Event.getEvents();
        mEventIndex = 0;
        mEventStartTime = System.nanoTime();
        mCreditInterval = 0;

        mUpdateText = this::tick;
        tick();
    }

    private void tick() {
        if (mLayout == null || !mLayout.isAttachedToWindow()) {
            return;
        }
        int time = (int) ((System.nanoTime() - mEventStartTime) / 1000000L);
        while (mEventIndex < mEvents.length) {
            var e = mEvents[mEventIndex];
            if (time < e.time()) {
                break;
            }
            tickLyric(time);
            switch (e.kind()) {
                case StillAlive.Event.WORDS_WRAP, StillAlive.Event.WORDS_NOWRAP -> {
                    mLyricLine = e.payload();
                    mLyricIndex = 0;
                    mLyricStartTime = e.time();
                    int interval = e.arg();
                    if (interval < 0) {
                        mLyricInterval = mEvents[mEventIndex + 1].time() - mLyricStartTime;
                    } else {
                        mLyricInterval = interval;
                    }
                    mLyricNeedsWrap = e.kind() == StillAlive.Event.WORDS_WRAP;
                }
                case StillAlive.Event.ASCII_ART -> mArtView.setText(StillAlive.ASCII_ARTS[e.arg()]);
                case StillAlive.Event.CLEAR_SCREEN -> mLyricView.getEditableText().clear();
                case StillAlive.Event.PLAY_MUSIC -> StillAlive.getInstance().start();
                case StillAlive.Event.SHOW_CREDITS -> {
                    mCreditIndex = 0;
                    mCreditStartTime = e.time();
                    mCreditInterval = mEvents[mEvents.length - 1].time() - mCreditStartTime;
                }
            }
            mEventIndex++;
        }
        if (mEventIndex == mEvents.length) {
            mEvents = null;
            mLyricLine = null;
            return;
        }
        tickLyric(time);
        mLayout.postDelayed(mUpdateText, 50);
    }

    private void tickLyric(int time) {
        if (mLyricLine == null) {
            return;
        }
        int count = mLyricLine.length();
        if (mLyricIndex < count) {
            int end = Math.min((time - mLyricStartTime) * count / mLyricInterval + 1, count);
            if (mLyricIndex < end) {
                mLyricView.getEditableText().append(mLyricLine, mLyricIndex, end);
                mLyricIndex = end;
            }
        }
        if (mLyricNeedsWrap && mLyricIndex == count) {
            mLyricView.getEditableText().append('\n');
            mLyricNeedsWrap = false;
        }
        if (mCreditInterval > 0) {
            count = StillAlive.CREDITS.length();
            int end = Math.min((time - mCreditStartTime) * count / mCreditInterval + 1, count);
            if (mCreditIndex < end) {
                mCreditView.getEditableText().append(StillAlive.CREDITS, mCreditIndex, end);
                mCreditIndex = end;
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLayout = null;
        mChangelogList = null;
        StillAlive.stop();
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
