/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.platform.Window;
import icyllis.modernui.ModernUI;
import icyllis.modernui.annotation.MainThread;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.graphics.text.GraphemeBreak;
import icyllis.modernui.mc.mixin.MixinChatFormatting;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Public APIs for Minecraft mods that depend on Modern UI.
 *
 * @since 3.8.2
 */
public abstract class MuiModApi {

    /*@FunctionalInterface
    public interface OnScrollListener {

        *//*
     * Called when a scroll event polling from the main handler and responding to the main window.
     *
     * @param scrollX raw relative movement of the horizontal scroll wheel or touchpad gesture
     * @param scrollY raw relative movement of the vertical scroll wheel or touchpad gesture
     *//*
        void onScroll(double scrollX, double scrollY);
    }*/

    @FunctionalInterface
    public interface OnScreenChangeListener {

        /**
         * Called when {@link Minecraft#setScreen(Screen)} is called, and after Forge's
         * event is fired.
         *
         * @param oldScreen the old screen
         * @param newScreen the new screen
         */
        void onScreenChange(@Nullable Screen oldScreen, @Nullable Screen newScreen);
    }

    @FunctionalInterface
    public interface OnWindowResizeListener {

        /**
         * Invoked at the beginning of {@link Minecraft#resizeDisplay()}.
         * Gui scale algorithm is replaced by Modern UI, see {@link #calcGuiScales(Window)}.
         *
         * @param width       framebuffer width of the window in pixels
         * @param height      framebuffer height of the window in pixels
         * @param guiScale    the new gui scale will be applied to (not apply yet)
         * @param oldGuiScale the old gui scale, may be equal to the new gui scale
         */
        void onWindowResize(int width, int height, int guiScale, int oldGuiScale);
    }

    @FunctionalInterface
    public interface OnDebugDumpListener {

        /**
         * Called when Modern UI dumps its debug info to chat or console.
         *
         * @param writer the writer to add new lines
         */
        void onDebugDump(@Nonnull PrintWriter writer);
    }

    @FunctionalInterface
    public interface OnPreKeyInputListener {

        /**
         * Called when {@link org.lwjgl.glfw.GLFWKeyCallbackI} invoked.
         */
        void onPreKeyInput(long window, int keyCode, int scanCode, int action, int mods);
    }

    /*static final CopyOnWriteArrayList<OnScrollListener> sOnScrollListeners =
            new CopyOnWriteArrayList<>();*/
    static final CopyOnWriteArrayList<OnScreenChangeListener> sOnScreenChangeListeners =
            new CopyOnWriteArrayList<>();
    static final CopyOnWriteArrayList<OnWindowResizeListener> sOnWindowResizeListeners =
            new CopyOnWriteArrayList<>();
    static final CopyOnWriteArrayList<OnDebugDumpListener> sOnDebugDumpListeners =
            new CopyOnWriteArrayList<>();
    static final CopyOnWriteArrayList<OnPreKeyInputListener> sOnPreKeyInputListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Minecraft GUI scale is limited to 8, so that density is limited to 4.0.
     * This prevents rasterization of large vector graphics.
     */
    public static final int MAX_GUI_SCALE = 8;

    /**
     * Matches Slack emoji shortcode.
     */
    public static final Pattern EMOJI_SHORTCODE_PATTERN =
            Pattern.compile("(:(\\w|\\+|-)+:)(?=|[!.?]|$)");

    /**
     * Start the lifecycle of user interface with the fragment and create views.
     * This method must be called from client side main thread.
     * <p>
     * This is served as a local interaction model, the server will not intersect with this before.
     * Otherwise, initiate this with a network model via
     * {@link ServerPlayer#openMenu(MenuProvider)} and {@link ExtendedScreenHandlerFactory}.
     * <p>
     * Specially, the main {@link Fragment} subclass can implement {@link ScreenCallback} to
     * describe the screen properties.
     *
     * @param fragment the main fragment
     */
    @MainThread
    public static void openScreen(@Nonnull Fragment fragment) {
        UIManager.getInstance().open(fragment);
    }

    /**
     * Get the elapsed time since the current screen is set, updated every frame on Render thread.
     * Ignoring game paused.
     *
     * @return elapsed time in milliseconds
     */
    @RenderThread
    public static long getElapsedTime() {
        return UIManager.getElapsedTime();
    }

    /**
     * Get synced UI frame time, updated every frame on Render thread. Ignoring game paused.
     *
     * @return frame time in milliseconds
     */
    @RenderThread
    public static long getFrameTime() {
        return getFrameTimeNanos() / 1000000;
    }

    /**
     * Get synced UI frame time, updated every frame on Render thread. Ignoring game paused.
     *
     * @return frame time in nanoseconds
     */
    @RenderThread
    public static long getFrameTimeNanos() {
        return UIManager.getFrameTimeNanos();
    }

    /**
     * Post a runnable to be executed asynchronously (no barrier) on UI thread.
     * This method is equivalent to calling {@link Core#getUiHandlerAsync()},
     * but {@link Core} is not a public API.
     *
     * @param r the Runnable that will be executed
     */
    public static void postToUiThread(@Nonnull Runnable r) {
        Core.getUiHandlerAsync().post(r);
    }

    public static int calcGuiScales() {
        return calcGuiScales(Minecraft.getInstance().getWindow());
    }

    public static int calcGuiScales(@Nonnull Window window) {
        return calcGuiScales(window.getWidth(), window.getHeight());
    }

    // V4, this matches vanilla, screen size is 640x480 dp at least
    public static int calcGuiScales(int framebufferWidth, int framebufferHeight) {
        double w = framebufferWidth / 16.;
        double h = framebufferHeight / 9.;
        double base = Math.min(w, h);

        int min;
        int det = (int) (Math.min(framebufferWidth, h * 12) / 320);
        if (det >= 2) {
            min = MathUtil.clamp((int) (base / 64), 2, MAX_GUI_SCALE);
        } else {
            min = 2;
        }
        int max = MathUtil.clamp(det, 2, MAX_GUI_SCALE);

        int auto;
        if (min >= 2) {
            double step = base > 216 ? 42. : base > 120 ? 36. : 30.;
            int i = (int) (base / step);
            int j = (int) (Math.max(w, h) / step);
            double v1 = base / (i * 30.);
            if (v1 > 42 / 30. || j > i) {
                auto = MathUtil.clamp(i + 1, min, max);
            } else {
                auto = MathUtil.clamp(i, min, max);
            }
        } else {
            auto = 2;
        }
        assert min <= auto && auto <= max;
        return min << 8 | auto << 4 | max;
    }

    // move a grapheme cluster at least
    public static int offsetByGrapheme(String value, int cursor, int dir) {
        int op;
        if (dir < 0) {
            op = GraphemeBreak.BEFORE;
        } else if (dir == 0) {
            op = GraphemeBreak.AT_OR_BEFORE;
        } else {
            op = GraphemeBreak.AFTER;
        }
        int offset = Util.offsetByCodepoints(value, cursor, dir);
        cursor = GraphemeBreak.getTextRunCursor(
                value, ModernUI.getSelectedLocale(),
                0, value.length(), cursor, op
        );
        if (dir > 0) {
            return Math.max(offset, cursor);
        } else {
            return Math.min(offset, cursor);
        }
    }

    /**
     * Maps ASCII to ChatFormatting, including all cases.
     */
    private static final ChatFormatting[] FORMATTING_TABLE = new ChatFormatting[128];

    static {
        for (ChatFormatting f : ChatFormatting.values()) {
            FORMATTING_TABLE[f.getChar()] = f;
            FORMATTING_TABLE[Character.toUpperCase(f.getChar())] = f;
        }
    }

    /**
     * Get the {@link ChatFormatting} by the given formatting code. Vanilla's method is
     * overwritten by this, see {@link MixinChatFormatting}.
     * <p>
     * Vanilla would create a new String from the char, call String.toLowerCase() and
     * String.charAt(0), search this char with a clone of ChatFormatting values. However,
     * it is unnecessary to consider non-ASCII compatibility, so we simplify it to a LUT.
     *
     * @param code c, case-insensitive
     * @return chat formatting, {@code null} if nothing
     * @see ChatFormatting#getByCode(char)
     */
    @Nullable
    public static ChatFormatting getFormattingByCode(char code) {
        return code < 128 ? FORMATTING_TABLE[code] : null;
    }

    /*
     * Registers a callback to be called when {@link org.lwjgl.glfw.GLFWScrollCallback} is called.
     *
     * @param listener the listener to register
     * @see OnScrollListener
     */
    /*public static void addOnScrollListener(@Nonnull OnScrollListener listener) {
        sOnScrollListeners.addIfAbsent(listener);
    }*/

    /*
     * Remove a registered listener.
     *
     * @param listener the listener to unregister
     */
    /*public static void removeOnScrollListener(@Nonnull OnScrollListener listener) {
        sOnScrollListeners.remove(listener);
    }*/

    /**
     * Registers a callback to be called when {@link Minecraft#setScreen(Screen)} is called.
     *
     * @param listener the listener to register
     * @see OnScreenChangeListener
     */
    public static void addOnScreenChangeListener(@Nonnull OnScreenChangeListener listener) {
        sOnScreenChangeListeners.addIfAbsent(listener);
    }

    /**
     * Remove a registered listener.
     *
     * @param listener the listener to unregister
     */
    public static void removeOnScreenChangeListener(@Nonnull OnScreenChangeListener listener) {
        sOnScreenChangeListeners.remove(listener);
    }

    /**
     * Registers a callback to be invoked at the beginning of {@link Minecraft#resizeDisplay()}.
     *
     * @param listener the listener to register
     * @see OnWindowResizeListener
     */
    public static void addOnWindowResizeListener(@Nonnull OnWindowResizeListener listener) {
        sOnWindowResizeListeners.addIfAbsent(listener);
    }

    /**
     * Remove a registered listener.
     *
     * @param listener the listener to unregister
     */
    public static void removeOnWindowResizeListener(@Nonnull OnWindowResizeListener listener) {
        sOnWindowResizeListeners.remove(listener);
    }

    /**
     * Registers a callback to be called when Modern UI dumps its debug info to chat or console.
     *
     * @param listener the listener to register
     * @see OnDebugDumpListener
     */
    public static void addOnDebugDumpListener(@Nonnull OnDebugDumpListener listener) {
        sOnDebugDumpListeners.addIfAbsent(listener);
    }

    /**
     * Remove a registered OnDebugDumpListener.
     *
     * @param listener the listener to unregister
     */
    public static void removeOnDebugDumpListener(@Nonnull OnDebugDumpListener listener) {
        sOnDebugDumpListeners.remove(listener);
    }

    public static void addOnPreKeyInputListener(@Nonnull OnPreKeyInputListener listener) {
        sOnPreKeyInputListeners.addIfAbsent(listener);
    }

    public static void removeOnPreKeyInputListener(@Nonnull OnPreKeyInputListener listener) {
        sOnPreKeyInputListeners.remove(listener);
    }

    // INTERNAL HOOK
    /*public static void dispatchOnScroll(double scrollX, double scrollY) {
        for (var l : sOnScrollListeners) {
            l.onScroll(scrollX, scrollY);
        }
    }*/

    // INTERNAL HOOK
    public static void dispatchOnScreenChange(@Nullable Screen oldScreen, @Nullable Screen newScreen) {
        for (var l : sOnScreenChangeListeners) {
            l.onScreenChange(oldScreen, newScreen);
        }
    }

    // INTERNAL HOOK
    public static void dispatchOnWindowResize(int width, int height, int guiScale, int oldGuiScale) {
        for (var l : sOnWindowResizeListeners) {
            l.onWindowResize(width, height, guiScale, oldGuiScale);
        }
    }

    // INTERNAL HOOK
    public static void dispatchOnDebugDump(@Nonnull PrintWriter writer) {
        for (var l : sOnDebugDumpListeners) {
            l.onDebugDump(writer);
        }
    }

    // INTERNAL HOOK
    public static void dispatchOnPreKeyInput(long window, int keyCode, int scanCode, int action, int mods) {
        for (var l : sOnPreKeyInputListeners) {
            l.onPreKeyInput(window, keyCode, scanCode, action, mods);
        }
    }
}
