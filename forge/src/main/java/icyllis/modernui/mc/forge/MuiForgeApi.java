/*
 * Modern UI.
 * Copyright (C) 2019-2021 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexFormat;
import icyllis.modernui.ModernUI;
import icyllis.modernui.annotation.MainThread;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Public APIs for Minecraft Forge mods to Modern UI.
 *
 * @since 3.3
 */
@OnlyIn(Dist.CLIENT)
public final class MuiForgeApi extends MuiModApi {

    public MuiForgeApi() {
        ModernUI.LOGGER.info(ModernUI.MARKER, "Created MuiForgeAPI");
    }

    /**
     * Start the lifecycle of user interface with the fragment and create views.
     * This method must be called from client side main thread.
     * <p>
     * This is served as a local interaction model, the server will not intersect with this before.
     * Otherwise, initiate this with a network model via
     * {@link ServerPlayer#openMenu(MenuProvider, Consumer)}.
     * <p>
     * Specially, the main {@link Fragment} subclass can implement {@link ICapabilityProvider}
     * to provide capabilities, some of which may be internally handled by the framework.
     * For example, {@link ScreenCallback} to describe the screen properties.
     *
     * @param fragment the main fragment
     */
    @MainThread
    public static void openScreen(@Nonnull Fragment fragment) {
        MuiModApi.openScreen(fragment);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T extends Screen & MuiScreen> T createScreen(@Nonnull Fragment fragment,
                                                         @Nullable ScreenCallback callback,
                                                         @Nullable Screen previousScreen,
                                                         @Nullable CharSequence title) {
        return (T) new SimpleScreen(UIManager.getInstance(),
                fragment, callback, previousScreen, title);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T extends AbstractContainerMenu, U extends Screen & MenuAccess<T> & MuiScreen>
    U createMenuScreen(@Nonnull Fragment fragment,
                       @Nullable ScreenCallback callback,
                       @Nonnull T menu,
                       @Nonnull Inventory inventory,
                       @Nonnull Component title) {
        return (U) new MenuScreen<>(UIManager.getInstance(),
                fragment, callback, menu, inventory, title);
    }

    /**
     * Get the elapsed time since the current screen is set, updated every frame on Render thread.
     * Ignoring game paused.
     *
     * @return elapsed time in milliseconds
     */
    @RenderThread
    public static long getElapsedTime() {
        return MuiModApi.getElapsedTime();
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
        return MuiModApi.getFrameTimeNanos();
    }

    /**
     * Post a runnable to be executed asynchronously (no barrier) on UI thread.
     * This method is equivalent to calling {@link Core#getUiHandlerAsync()},
     * but {@link Core} is not a public API.
     *
     * @param r the Runnable that will be executed
     */
    public static void postToUiThread(@Nonnull Runnable r) {
        MuiModApi.postToUiThread(r);
    }

    @Override
    public boolean isGLVersionPromoted() {
        try {
            String version = net.minecraftforge.fml.loading.ImmediateWindowHandler.getGLVersion();
            if (!"3.2".equals(version)) {
                ModernUI.LOGGER.info(ModernUI.MARKER, "Detected OpenGL {} Core Profile from FML Early Window",
                        version);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void loadEffect(GameRenderer gr, ResourceLocation effect) {
        gr.loadEffect(effect);
    }

    @Override
    public ShaderInstance makeShaderInstance(ResourceProvider resourceProvider,
                                             ResourceLocation resourceLocation,
                                             VertexFormat vertexFormat) throws IOException {
        return new ShaderInstance(resourceProvider, resourceLocation, vertexFormat);
    }

    @Override
    public boolean isKeyBindingMatches(KeyMapping keyMapping, InputConstants.Key key) {
        return keyMapping.isActiveAndMatches(key);
    }

    @Override
    public Style applyRarityTo(Rarity rarity, Style baseStyle) {
        return rarity.getStyleModifier().apply(baseStyle);
    }

    /**
     * Create a container menu on server-side with the given {@link MenuProvider}, generate a
     * container id represents the next screen. Then send a packet to the player to request to
     * open a GUI on the client. This method must be called from server thread,
     * {@link net.minecraftforge.network.IContainerFactory#create(int, Inventory, FriendlyByteBuf)}
     * and {@link MenuScreenFactory#createFragment(AbstractContainerMenu)} will be called on client.
     * <p>
     * This is served as a client/server interaction model, there must be a running server.
     * <p>
     * Do not use this, use {@link ServerPlayer#openMenu(MenuProvider, Consumer)}
     * because Modern UI is client-only.
     *
     * @param player   the server player to open the screen for
     * @param provider a provider to create a menu on server side
     * @see #openMenu(Player, MenuProvider, Consumer)
     * @see net.minecraftforge.common.extensions.IForgeMenuType#create(net.minecraftforge.network.IContainerFactory)
     */
    @ApiStatus.Internal
    public static void openMenu(@Nonnull Player player, @Nonnull MenuProvider provider) {
        openMenu(player, provider, (Consumer<FriendlyByteBuf>) null);
    }

    /**
     * Create a container menu on server-side with the given {@link MenuProvider}, generate a
     * container id represents the next screen. Then send a packet to the player to request to
     * open a GUI on the client. This method must be called from server thread,
     * {@link net.minecraftforge.network.IContainerFactory#create(int, Inventory, FriendlyByteBuf)}
     * and {@link MenuScreenFactory#createFragment(AbstractContainerMenu)} will be called on client.
     * <p>
     * This is served as a client/server interaction model, there must be a running server.
     * <p>
     * Do not use this, use {@link ServerPlayer#openMenu(MenuProvider, Consumer)}
     * because Modern UI is client-only.
     *
     * @param player   the server player to open the screen for
     * @param provider a provider to create a menu on server side
     * @param pos      a block pos to send to client, this will be passed to
     *                 the menu supplier that registered on client
     * @see #openMenu(Player, MenuProvider, Consumer)
     * @see net.minecraftforge.common.extensions.IForgeMenuType#create(net.minecraftforge.network.IContainerFactory)
     */
    @ApiStatus.Internal
    public static void openMenu(@Nonnull Player player, @Nonnull MenuProvider provider, @Nonnull BlockPos pos) {
        openMenu(player, provider, buf -> buf.writeBlockPos(pos));
    }

    /**
     * Create a container menu on server-side with the given {@link MenuProvider}, generate a
     * container id represents the next screen. Then send a packet to the player to request to
     * open a GUI on the client. This method must be called from server thread,
     * {@link net.minecraftforge.network.IContainerFactory#create(int, Inventory, FriendlyByteBuf)}
     * and {@link MenuScreenFactory#createFragment(AbstractContainerMenu)} will be called on client.
     * <p>
     * This is served as a client/server interaction model, there must be a running server.
     * <p>
     * Do not use this, use {@link ServerPlayer#openMenu(MenuProvider, Consumer)}
     * because Modern UI is client-only.
     *
     * @param player   the server player to open the screen for
     * @param provider a provider to create a menu on server side
     * @param writer   a data writer to send additional data to client, this will be passed
     *                 to the menu supplier (IContainerFactory) that registered on client
     * @see net.minecraftforge.common.extensions.IForgeMenuType#create(net.minecraftforge.network.IContainerFactory)
     */
    @ApiStatus.Internal
    public static void openMenu(@Nonnull Player player, @Nonnull MenuProvider provider,
                                @Nullable Consumer<FriendlyByteBuf> writer) {
        if (ModernUIMod.isDeveloperMode()) {
            openMenu0(player, provider, writer);
        } else {
            if (!(player instanceof ServerPlayer p)) {
                ModernUI.LOGGER.warn(ModernUI.MARKER, "openMenu() is not called from logical server",
                        new Exception().fillInStackTrace());
                return;
            }
            p.openMenu(provider, writer);
        }
    }

    @ApiStatus.Internal
    static void openMenu0(@Nonnull Player player, @Nonnull MenuConstructor provider,
                          @Nullable Consumer<FriendlyByteBuf> writer) {
        if (!(player instanceof ServerPlayer p)) {
            ModernUI.LOGGER.warn(ModernUI.MARKER, "openMenu() is not called from logical server",
                    new Exception().fillInStackTrace());
            return;
        }
        // do the same thing as ServerPlayer.openMenu()
        if (p.containerMenu != p.inventoryMenu) {
            p.closeContainer();
        }
        p.nextContainerCounter();
        AbstractContainerMenu menu = provider.createMenu(p.containerCounter, p.getInventory(), p);
        if (menu == null) {
            return;
        }
        NetworkMessages.openMenu(menu, writer).sendToPlayer(p);
        p.initMenu(menu);
        p.containerMenu = menu;
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(p, menu));
    }

    /* Screen */
    /*public static int getScreenBackgroundColor() {
        return (int) (BlurHandler.INSTANCE.getBackgroundAlpha() * 255.0f) << 24;
    }*/

    /* Minecraft */
    /*public static void displayInGameMenu(boolean usePauseScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.currentScreen == null) {
            // If press F3 + Esc and is single player and not open LAN world
            if (usePauseScreen && minecraft.isIntegratedServerRunning() && minecraft.getIntegratedServer() != null &&
             !minecraft.getIntegratedServer().getPublic()) {
                minecraft.displayGuiScreen(new IngameMenuScreen(false));
                minecraft.getSoundHandler().pause();
            } else {
                //UIManager.INSTANCE.openGuiScreen(new TranslationTextComponent("menu.game"), IngameMenuHome::new);
                minecraft.displayGuiScreen(new IngameMenuScreen(true));
            }
        }
    }*/
}
