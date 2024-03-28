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

import icyllis.modernui.annotation.UiThread;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.OnBackPressedDispatcher;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Common interface between MenuScreen and SimpleScreen
 */
public interface MuiScreen {

    /**
     * @return this as screen
     */
    @Nonnull
    Screen self();

    /**
     * @return the main fragment
     */
    @Nonnull
    Fragment getFragment();

    /**
     * @return a callback describes the screen properties
     */
    @Nullable
    ScreenCallback getCallback();

    /**
     * Returns the previous screen associated with this screen.
     * If non-null, then {@link #onBackPressed()} will return back to that screen.
     * This is always null for menu screen.
     *
     * @see #isMenuScreen()
     */
    @Nullable
    Screen getPreviousScreen();

    /**
     * Returns whether this is a container menu screen. If true, then this screen
     * is guaranteed to have {@link net.minecraft.client.gui.screens.inventory.MenuAccess}.
     *
     * @return true for MenuScreen, false for SimpleScreen
     */
    boolean isMenuScreen();

    /**
     * Call {@link OnBackPressedDispatcher#onBackPressed()} programmatically.
     * <p>
     * Typically, if the back stack is not empty, pop the back stack.
     * Otherwise, close this screen or back to previous screen.
     */
    //TODO not thread-safe, since the final onBackPressed executed on main thread,
    // calling this method when this screen it not current should be disallowed,
    // but we can't see it immediately on UI thread
    @UiThread
    void onBackPressed();
}
