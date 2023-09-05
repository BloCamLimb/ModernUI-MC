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

package icyllis.modernui.mc.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import icyllis.modernui.annotation.MainThread;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.*;
import icyllis.modernui.view.KeyEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

import static icyllis.modernui.ModernUI.LOGGER;
import static org.lwjgl.glfw.GLFW.*;

@ApiStatus.Internal
public final class UIManagerFabric extends UIManager {

    @SuppressWarnings("NoTranslation")
    public static final KeyMapping OPEN_CENTER_KEY = new KeyMapping(
            "key.modernui.openCenter",
            InputConstants.Type.KEYSYM, GLFW_KEY_K, "Modern UI");

    private UIManagerFabric() {
        super();

        ModernUIFabric.Client.START_RENDER_TICK.register(() -> super.onRenderTick(false));
        ModernUIFabric.Client.END_RENDER_TICK.register(() -> super.onRenderTick(true));

        ClientTickEvents.START_CLIENT_TICK.register((mc) -> super.onClientTick(false));
        ClientTickEvents.END_CLIENT_TICK.register((mc) -> super.onClientTick(true));
    }

    @RenderThread
    public static void initialize() {
        Core.checkRenderThread();
        assert sInstance == null;
        sInstance = new UIManagerFabric();
        LOGGER.info(MARKER, "UI manager initialized");
    }

    /**
     * Schedule UI and create views.
     *
     * @param fragment the main fragment
     */
    @MainThread
    protected void open(@Nonnull Fragment fragment) {
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("Not called from main thread");
        }
        minecraft.setScreen(new SimpleScreen(this, fragment));
    }

    @Override
    protected void onScreenChange(@Nullable Screen oldScreen, @Nullable Screen newScreen) {
        if (newScreen != null) {
            if (!mFirstScreenOpened) {
                if (sDingEnabled) {
                    minecraft.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f)
                    );
                }
                if (ModernUIMod.isOptiFineLoaded() &&
                        ModernUIClient.isTextEngineEnabled()) {
                    OptiFineIntegration.setFastRender(false);
                    LOGGER.info(MARKER, "Disabled OptiFine Fast Render");
                }
                mFirstScreenOpened = true;
            }

            if (mScreen != newScreen && newScreen instanceof MuiScreen) {
                //mTicks = 0;
                mElapsedTimeMillis = 0;
            }
            if (mScreen != newScreen && mScreen != null) {
                onHoverMove(false);
            }
            // for non-mui screens
            if (mScreen == null && minecraft.screen == null) {
                //mTicks = 0;
                mElapsedTimeMillis = 0;
            }
        }
        super.onScreenChange(oldScreen, newScreen);
    }

    @Override
    public void onPostKeyInput(int key, int scanCode, int action, int mods) {
        if (mScreen != null) {
            int ac = action == GLFW_RELEASE ? KeyEvent.ACTION_UP : KeyEvent.ACTION_DOWN;
            KeyEvent keyEvent = KeyEvent.obtain(Core.timeNanos(), ac, key, 0,
                    mods, scanCode, 0);
            mRoot.enqueueInputEvent(keyEvent);
        }
        if (action == GLFW_PRESS) {
            if (OPEN_CENTER_KEY.matches(key, scanCode)) {
                open(new CenterFragment2());
                return;
            }
        }
        super.onPostKeyInput(key, scanCode, action, mods);
    }
}
