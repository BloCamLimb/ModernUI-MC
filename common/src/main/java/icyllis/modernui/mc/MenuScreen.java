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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Menu screen holds a container menu for item stack interaction.
 *
 * @param <T> the type of container menu
 * @since 3.13
 * @see SimpleScreen
 */
public class MenuScreen<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T>
        implements MuiScreen {

    private final UIManager mHost;
    private final Fragment mFragment;
    @Nullable
    private final ScreenCallback mCallback;

    public MenuScreen(@Nonnull Fragment fragment, @Nullable ScreenCallback callback,
                      @Nonnull T menu, @Nonnull Inventory inventory, @Nonnull Component title) {
        super(menu, inventory, title);
        mHost = UIManager.getInstance();
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
            super.renderBackground(gr, mouseX, mouseY, deltaTick);
        }
        mHost.render(gr, mouseX, mouseY, deltaTick);
    }

    @Override
    public void render(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick) {
        super.render(gr, mouseX, mouseY, deltaTick);
        renderExtraContents(gr, mouseX, mouseY, deltaTick);
        renderTooltip(gr, mouseX, mouseY);
    }

    /**
     * Override to render additional contents above other contents and widgets, but below the tooltip.
     */
    @ApiStatus.OverrideOnly
    protected void renderExtraContents(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick) {
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics gr, float deltaTick, int x, int y) {
    }

    @Override
    public void removed() {
        super.removed();
        mHost.removed(this);
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
        return null;
    }

    @Override
    public boolean isMenuScreen() {
        return true;
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
        if (checkHotbarKeyPressed(keyCode, scanCode)) {
            return true;
        }
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            if (minecraft.options.keyPickItem.matches(keyCode, scanCode)) {
                slotClicked(hoveredSlot, hoveredSlot.index, 0, ClickType.CLONE);
                return true;
            } else if (minecraft.options.keyDrop.matches(keyCode, scanCode)) {
                slotClicked(hoveredSlot, hoveredSlot.index, hasControlDown() ? 1 : 0, ClickType.THROW);
                return true;
            }
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
