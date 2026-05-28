/*
 * Modern UI.
 * Copyright (C) 2026 BloCamLimb. All rights reserved.
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

import icyllis.modernui.fragment.Fragment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Base screen that provides ModernUI.
 *
 * @see MenuScreen
 * @since 3.13
 */
public class SimpleScreen extends Screen implements MuiScreen {

    private final UIManager mHost;
    @Nullable
    private final Screen mPrevious;
    private final Fragment mFragment;
    @Nullable
    private final ScreenCallback mCallback;

    public SimpleScreen(@Nonnull Fragment fragment, @Nullable ScreenCallback callback,
                        @Nullable Screen previous, @Nonnull Component title) {
        super(title);
        mHost = UIManager.getInstance();
        mPrevious = previous;
        mFragment = Objects.requireNonNull(fragment);
        mCallback = callback != null ? callback :
                fragment instanceof ScreenCallback cbk ? cbk : null;
    }

    @Override
    protected void init() {
        super.init();
        mHost.initScreen(this);
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick) {
        ScreenCallback callback = getCallback();
        if (callback == null || callback.hasDefaultBackground()) {
            if (minecraft != null && minecraft.level == null) {
                super.renderBackground(gr, mouseX, mouseY, deltaTick);
            } else {
                BlurHandler.INSTANCE.drawScreenBackground(gr, 0, 0, this.width, this.height);
            }
        }
        mHost.render(gr, mouseX, mouseY, deltaTick);
    }

    @Override
    public void render(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick) {
        super.render(gr, mouseX, mouseY, deltaTick);
    }

    @Override
    public void removed() {
        super.removed();
        mHost.removed(this);
    }

    @Override
    public boolean isPauseScreen() {
        ScreenCallback callback = getCallback();
        return callback == null || callback.isPauseScreen();
    }

    @Nonnull
    @Override
    public Screen self() {
        return this;
    }

    @Nonnull
    @Override
    public Fragment getFragment() {
        return mFragment;
    }

    @Nullable
    @Override
    public ScreenCallback getCallback() {
        return mCallback;
    }

    @Nullable
    @Override
    public Screen getPreviousScreen() {
        return mPrevious;
    }

    @Override
    public boolean isMenuScreen() {
        return false;
    }

    @Override
    public void onBackPressed() {
        mHost.getOnBackPressedDispatcher().onBackPressed();
    }

    // IMPL - GuiEventListener

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        mHost.onHoverMove(true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        super.mouseReleased(mouseX, mouseY, mouseButton);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double deltaX, double deltaY) {
        super.mouseDragged(mouseX, mouseY, mouseButton, deltaX, deltaY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (super.mouseScrolled(mouseX, mouseY, deltaX, deltaY)) {
            return true;
        }
        mHost.onScroll(deltaX, deltaY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        mHost.onKeyPress(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (getFocused() != null && getFocused().keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        mHost.onKeyRelease(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (getFocused() != null && getFocused().charTyped(ch, modifiers)) {
            return true;
        }
        return mHost.onCharTyped(ch);
    }
}
