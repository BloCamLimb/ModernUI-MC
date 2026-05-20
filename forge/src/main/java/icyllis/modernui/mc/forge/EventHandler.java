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
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.mc.FontResourceManager;
import icyllis.modernui.mc.ImageStore;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.ResourcesStore;
import icyllis.modernui.mc.StillAlive;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.mc.testforge.TestContainerMenu;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import javax.annotation.Nonnull;

import static icyllis.modernui.mc.ModernUIMod.*;

/**
 * Handles game server or client events from Forge event bus
 */
@Mod.EventBusSubscriber(modid = ModernUI.ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
final class EventHandler {

    @SubscribeEvent
    static void onRightClickItem(@Nonnull PlayerInteractEvent.RightClickItem event) {
        if (ModernUIMod.sDevelopment) {
            final boolean diamond;
            if (event.getSide().isServer() && ((diamond = event.getItemStack().is(Items.DIAMOND))
                    || event.getItemStack().is(Items.EMERALD))) {
                if (event.getEntity().isShiftKeyDown()) {
                    ((ServerPlayer) event.getEntity()).openMenu(new MenuProvider() {
                        @Nonnull
                        @Override
                        public Component getDisplayName() {
                            return CommonComponents.EMPTY;
                        }

                        @Override
                        public AbstractContainerMenu createMenu(int containerId,
                                                                @Nonnull Inventory inventory,
                                                                @Nonnull Player player) {
                            return new TestContainerMenu(containerId, inventory, player);
                        }
                    }, buf -> buf.writeBoolean(diamond));
                }
            }
        }
    }

    /*@SubscribeEvent
    static void onRightClickBlock(@Nonnull PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isServer() && event.getHand() == InteractionHand.MAIN_HAND &&
                event.getPlayer().isShiftKeyDown() &&
                event.getWorld().getBlockState(event.getPos()).getBlock() == Blocks.GRASS_BLOCK) {
            event.getPlayer().addItem(new ItemStack(Blocks.STONE));
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }*/

    /*@SubscribeEvent
    static void onContainerClosed(PlayerContainerEvent.Close event) {

    }*/

    /**
     * Handles game client events from Forge event bus
     */
    @Mod.EventBusSubscriber(modid = ModernUI.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    static class Client {

        static {
            assert (FMLEnvironment.dist.isClient());
        }

        private Client() {
        }

        //static OptionInstance<Integer> sNewGuiScale;

        /*@Nullable
        private static Screen sCapturedVideoSettingsScreen;*/

        /*@SubscribeEvent
        static void onPlayerLogin(@Nonnull ClientPlayerNetworkEvent.LoggedInEvent event) {
            if (ModernUIForge.isDeveloperMode()) {
                LocalPlayer player = event.getPlayer();
                if (player != null && RenderCore.glCapabilitiesErrors > 0) {
                    player.sendMessage(new TextComponent("[Modern UI] There are " + RenderCore.glCapabilitiesErrors +
                            " GL capabilities that are not supported by your GPU, see debug.log for detailed info")
                            .withStyle(ChatFormatting.RED), Util.NIL_UUID);
                }
            }
        }*/

        /*@SubscribeEvent(priority = EventPriority.HIGH)
        static void onGuiOpenH(@Nonnull ScreenEvent.Opening event) {
            // TipTheScales is not good, and it also not compatible with OptiFine
            if (ModernUIForge.sInterceptTipTheScales) {
                if (event.getNewScreen() instanceof VideoSettingsScreen) {
                    sCapturedVideoSettingsScreen = event.getNewScreen();
                }
            }
        }*/

        /*@SubscribeEvent(priority = EventPriority.LOW)
        static void onGuiOpenL(@Nonnull ScreenEvent.Opening event) {
            // This event should not be cancelled
            if (sCapturedVideoSettingsScreen != null) {
                event.setNewScreen(sCapturedVideoSettingsScreen);
                sCapturedVideoSettingsScreen = null;
            }
        }*/

        /*@SubscribeEvent
        static void onGuiInit(@Nonnull ScreenEvent.Init event) {
            if (event.getScreen() instanceof VideoSettingsScreen && sNewGuiScale != null) {
                sNewGuiScale.setMaxValue(MuiForgeApi.calcGuiScales() & 0xf);
            }
        }*/

        @SubscribeEvent
        static void onRenderTick(@Nonnull TickEvent.RenderTickEvent.Pre event) {
            Core.flushMainCalls();
            StillAlive.tick();
        }

        @SubscribeEvent
        static void onRenderTick(@Nonnull TickEvent.RenderTickEvent.Post event) {
            Core.flushMainCalls();
            StillAlive.tick();
        }

        @SubscribeEvent
        static void registerResourceListener(@Nonnull RegisterClientReloadListenersEvent event) {
            // this event fired after LOAD_REGISTRIES and before COMMON_SETUP on client main thread (render thread)
            // this event fired after ParticleFactoryRegisterEvent
            Image.setLegacyFactory(ImageStore.getInstance());
            event.registerReloadListener(ResourcesStore.getInstance());
            event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                Handler handler = Core.getUiHandlerAsync();
                // FML may throw ex, so it can be null
                if (handler != null) {
                    // Call in lambda, not in creating the lambda
                    handler.post(() -> {
                        ImageStore.getInstance().clear();
                        UIManager.getInstance().updateLayoutDir(ConfigImpl.CLIENT.mForceRtl.get());
                    });
                }
                //BlurHandler.INSTANCE.loadEffect();
            });
            if (!ModernUIMod.isTextEngineEnabled()) {
                event.registerReloadListener(FontResourceManager.getInstance());
            }
            // else injected by MixinFontManager

            LOGGER.debug(MARKER, "Registered resource reload listener");
        }

        @SubscribeEvent
        static void registerKeyMapping(@Nonnull RegisterKeyMappingsEvent event) {
            UIManagerForge.KEYBIND_CATEGORY = KeyMapping.Category.register(
                    ModernUIMod.location("keybind"));
            UIManagerForge.OPEN_CENTER_KEY = new KeyMapping(
                    "key.modernui.openCenter", KeyConflictContext.UNIVERSAL, KeyModifier.CONTROL,
                    InputConstants.Type.KEYSYM, InputConstants.KEY_K, UIManagerForge.KEYBIND_CATEGORY, 0);
            UIManagerForge.ZOOM_KEY = new KeyMapping(
                    "key.modernui.zoom", KeyConflictContext.IN_GAME, KeyModifier.NONE,
                    InputConstants.Type.KEYSYM, InputConstants.KEY_C, UIManagerForge.KEYBIND_CATEGORY, 0);

            event.register(UIManagerForge.OPEN_CENTER_KEY);
            event.register(UIManagerForge.ZOOM_KEY);
        }

        /*@SubscribeEvent(receiveCanceled = true)
        static void onGuiOpen(@Nonnull GuiOpenEvent event) {

        }

        @SubscribeEvent
        static void onRenderTick(@Nonnull TickEvent.RenderTickEvent event) {

        }

        @SubscribeEvent
        static void onClientTick(@Nonnull TickEvent.ClientTickEvent event) {

        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.KeyInputEvent event) {

        }

        @SubscribeEvent
        static void onGuiInit(GuiScreenEvent.InitGuiEvent event) {

        }

        @SubscribeEvent
        public static void onScreenStartMouseClicked(@Nonnull GuiScreenEvent.MouseClickedEvent.Pre event) {

        }

        @SubscribeEvent
        public static void onScreenStartMouseReleased(@Nonnull GuiScreenEvent.MouseReleasedEvent.Pre event) {

        }

        @SubscribeEvent
        public static void onScreenStartMouseDragged(@Nonnull GuiScreenEvent.MouseDragEvent.Pre event) {

        }*/
    }

    /*@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventHandler {

        @SubscribeEvent
        public static void setupCommon(FMLCommonSetupEvent event) {

        }

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void setupClient(FMLClientSetupEvent event) {

        }

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void registerSounds(@Nonnull RegistryEvent.Register<SoundEvent> event) {

        }

        @SubscribeEvent
        public static void registerContainers(@Nonnull RegistryEvent.Register<ContainerType<?>> event) {

        }

        @SubscribeEvent
        public static void onConfigChange(@Nonnull ModConfig.ModConfigEvent event) {

        }

        @SubscribeEvent
        public static void onLoadComplete(FMLLoadCompleteEvent event) {

        }
    }*/

    static class ClientDebug {

        static {
            assert (FMLEnvironment.dist.isClient());
        }

        private ClientDebug() {
        }

        /*@SubscribeEvent
        static void onRenderLevelLast(@Nonnull RenderLevelLastEvent event) {
            if (Screen.hasAltDown() &&
                    InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_KP_7)) {
                LOGGER.info("Capture from RenderLevelLastEvent");
                LOGGER.info("PoseStack.last().pose(): {}", event.getPoseStack().last().pose());
                LOGGER.info("ProjectionMatrix: {}", event.getProjectionMatrix());
            }
        }*/
    }
}
