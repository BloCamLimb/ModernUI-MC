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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.jspecify.annotations.NonNull;

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
public class MenuScreen<T extends @NonNull AbstractContainerMenu>
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
    public void extractBackground(@Nonnull GuiGraphicsExtractor gr, int mouseX, int mouseY, float deltaTick) {
        ScreenCallback callback = getCallback();
        if (callback == null || callback.hasDefaultBackground()) {
            super.extractBackground(gr, mouseX, mouseY, deltaTick);
        }
    }

    @Override
    public void extractRenderState(@Nonnull GuiGraphicsExtractor gr, int mouseX, int mouseY, float deltaTick) {
        mHost.render(gr, mouseX, mouseY, deltaTick);
        super.extractRenderState(gr, mouseX, mouseY, deltaTick);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        super.mouseClicked(event, doubleClick);
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        super.mouseReleased(event);
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        super.mouseDragged(event, dx, dy);
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
    public boolean keyPressed(KeyEvent event) {
        if (getFocused() != null && getFocused().keyPressed(event)) {
            return true;
        }
        if (checkHotbarKeyPressed(event)) {
            return true;
        }
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            if (minecraft.options.keyPickItem.matches(event)) {
                slotClicked(hoveredSlot, hoveredSlot.index, 0, ContainerInput.CLONE);
                return true;
            } else if (minecraft.options.keyDrop.matches(event)) {
                slotClicked(hoveredSlot, hoveredSlot.index, event.hasControlDown() ? 1 : 0, ContainerInput.THROW);
                return true;
            }
        }

        mHost.onKeyPress(event.key(), event.scancode(), event.modifiers());
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (getFocused() != null && getFocused().keyReleased(event)) {
            return true;
        }
        mHost.onKeyRelease(event.key(), event.scancode(), event.modifiers());
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (getFocused() != null && getFocused().charTyped(event)) {
            return true;
        }
        return mHost.onCharTyped(event.codepoint());
    }
}
