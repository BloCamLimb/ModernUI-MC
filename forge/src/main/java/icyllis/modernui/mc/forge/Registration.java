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

import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.graphics.ImageStore;
import icyllis.modernui.mc.forge.mixin.AccessOption;
import icyllis.modernui.mc.forge.mixin.AccessVideoSettings;
import icyllis.modernui.mc.testforge.TestContainerMenu;
import icyllis.modernui.mc.testforge.TestPauseFragment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.model.ForgeModelBakery;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.CrashReportCallables;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.commons.io.output.StringBuilderWriter;

import javax.annotation.Nonnull;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static icyllis.modernui.ModernUI.*;

/**
 * This class handles mod loading events, all registry entries are only available under the development mode.
 */
@Mod.EventBusSubscriber(modid = ModernUI.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
final class Registration {

    private Registration() {
    }

    @SubscribeEvent
    static void registerMenus(@Nonnull RegistryEvent.Register<MenuType<?>> event) {
        if (ModernUIForge.sDevelopment) {
            event.getRegistry().register(IForgeMenuType.create(TestContainerMenu::new)
                    .setRegistryName("test"));
        }
    }

    @SubscribeEvent
    static void registerItems(@Nonnull RegistryEvent.Register<Item> event) {
        if (ModernUIForge.sDevelopment) {
            Item.Properties properties = new Item.Properties().stacksTo(1);
            event.getRegistry().register(new ProjectBuilderItem(properties)
                    .setRegistryName("project_builder"));
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    static void loadingClient(ParticleFactoryRegisterEvent event) {
        // this event fired after LOAD_REGISTRIES and before COMMON_SETUP on client main thread (render thread)
        // this event fired before RegisterClientReloadListenersEvent
        UIManager.initialize();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    static void registerResourceListener(@Nonnull RegisterClientReloadListenersEvent event) {
        // this event fired after LOAD_REGISTRIES and before COMMON_SETUP on client main thread (render thread)
        // this event fired after ParticleFactoryRegisterEvent
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            //GLShaderManager.getInstance().reload();
            ImageStore.getInstance().clear();
            Handler handler = Core.getUiHandlerAsync();
            // FML may throw ex, so it can be null
            if (handler != null) {
                // Call in lambda, not in creating the lambda
                handler.post(() -> UIManager.getInstance().updateLayoutDir(Config.CLIENT.mForceRtl.get()));
            }
        });
        if (!ModernUIForge.Client.isTextEngineEnabled()) {
            event.registerReloadListener(FontResourceManager.getInstance());
        }
        // else injected by MixinFontManager

        LOGGER.debug(MARKER, "Registered resource reload listener");
    }

    @SubscribeEvent
    static void setupCommon(@Nonnull FMLCommonSetupEvent event) {
        /*byte[] bytes = null;
        try (InputStream stream = ModernUIForge.class.getClassLoader().getResourceAsStream(
                "icyllis/modernui/forge/NetworkMessages.class")) {
            Objects.requireNonNull(stream, "Mod file is broken");
            bytes = IOUtils.toByteArray(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (InputStream stream = ModernUIForge.class.getClassLoader().getResourceAsStream(
                "icyllis/modernui/forge/NetworkMessages$C.class")) {
            Objects.requireNonNull(stream, "Mod file is broken");
            bytes = ArrayUtils.addAll(bytes, IOUtils.toByteArray(stream));
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        if (ModList.get().getModContainerById(new String(new byte[]{0x1f ^ 0x74, (0x4 << 0x1) | 0x41,
                ~-0x78, 0xd2 >> 0x1}, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT)).isPresent()) {
            event.enqueueWork(() -> LOGGER.fatal("OK"));
        }
        /*bytes = ArrayUtils.addAll(bytes, ModList.get().getModFileById(ModernUI.ID).getLicense()
                .getBytes(StandardCharsets.UTF_8));
        if (bytes == null) {
            throw new IllegalStateException();
        }*/
        NetworkMessages.sNetwork = new NetworkHandler("", () -> NetworkMessages::msg,
                null, "340", true);

        MinecraftForge.EVENT_BUS.register(ServerHandler.INSTANCE);

        // give it a probe
        if (MuiForgeApi.isServerStarted()) {
            LOGGER.info(MARKER, "");
        }
    }

    @Nonnull
    private static String digest(@Nonnull byte[] in) {
        try {
            in = MessageDigest.getInstance("MD5").digest(in);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i += 3) {
            int c = (in[i] & 0xFF) | (in[i + 1] & 0xFF) << 8 | (in[i + 2] & 0xFF) << 16;
            for (int k = 0; k < 4; k++) {
                final int m = c & 0x3f;
                final char t;
                if (m < 26)
                    t = (char) ('A' + m);
                else if (m < 52)
                    t = (char) ('a' + m - 26);
                else if (m < 62)
                    t = (char) ('0' + m - 52);
                else if (m == 62)
                    t = '+';
                else // m == 63
                    t = '/';
                sb.append(t);
                c >>= 6;
            }
        }
        sb.append(Integer.toHexString(in[15] & 0xFF));
        return sb.toString();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    static void setupClient(@Nonnull FMLClientSetupEvent event) {
        //SettingsManager.INSTANCE.buildAllSettings();
        //UIManager.getInstance().registerMenuScreen(Registration.TEST_MENU, menu -> new TestUI());

        Minecraft.getInstance().execute(() -> {
            //ModernUI.getSelectedTypeface();
            UIManager.initializeRenderer();
            var windowMode = Config.CLIENT.mLastWindowMode;
            if (windowMode == Config.Client.WindowMode.FULLSCREEN_BORDERLESS) {
                // ensure it's applied and positioned
                windowMode.apply();
            }
        });

        CrashReportCallables.registerCrashCallable("Fragments", () -> {
            var fragments = UIManager.getInstance().mFragmentController;
            var builder = new StringBuilder();
            if (fragments != null) {
                try (var pw = new PrintWriter(new StringBuilderWriter(builder))) {
                    fragments.getFragmentManager().dump("", null, pw);
                }
            }
            return builder.toString();
        });

        // Always replace static variable as an insurance policy
        /*AccessOption.setGuiScale(new CycleOption("options.guiScale",
                (options, integer) -> options.guiScale = Integer.remainderUnsigned(
                        options.guiScale + integer, (MForgeCompat.calcGuiScales() & 0xf) + 1),
                (options, cycleOption) -> options.guiScale == 0 ?
                        ((AccessOption) cycleOption).callGenericValueLabel(new TranslatableComponent("options" +
                                ".guiScale.auto")
                                .append(new TextComponent(" (" + (MForgeCompat.calcGuiScales() >> 4 & 0xf) + ")"))) :
                        ((AccessOption) cycleOption).callGenericValueLabel(new TextComponent(Integer.toString(options
                        .guiScale))))
        );*/
        ClientRegistry.registerKeyBinding(UIManager.OPEN_CENTER_KEY);
        ClientRegistry.registerKeyBinding(UIManager.ZOOM_KEY);

        Option[] settings = null;
        boolean captured = false;
        if (ModernUIForge.isOptiFineLoaded()) {
            try {
                Field field = VideoSettingsScreen.class.getDeclaredField("videoOptions");
                field.setAccessible(true);
                settings = (Option[]) field.get(null);
            } catch (Exception e) {
                LOGGER.error(ModernUI.MARKER, "Failed to be compatible with OptiFine video settings", e);
            }
        } else {
            settings = AccessVideoSettings.getOptions();
        }
        if (settings != null) {
            for (int i = 0; i < settings.length; i++) {
                if (settings[i] != Option.GUI_SCALE) {
                    continue;
                }
                ProgressOption option = new ProgressOption("options.guiScale", 0, MuiForgeApi.MAX_GUI_SCALE, 1,
                        options -> (double) options.guiScale,
                        (options, aDouble) -> {
                            final int value = aDouble.intValue();
                            options.guiScale = value;
                            // execute in next tick, prevent transient GUI scale change
                            Minecraft.getInstance().tell(() -> {
                                Minecraft minecraft = Minecraft.getInstance();
                                if ((int) minecraft.getWindow().getGuiScale() !=
                                        minecraft.getWindow().calculateScale(value, false)) {
                                    minecraft.resizeDisplay();
                                }
                            });
                        },
                        (options, progressOption) -> {
                            final int value = options.guiScale;
                            int r = MuiForgeApi.calcGuiScales();
                            if (value == 0) { // auto
                                int auto = r >> 4 & 0xf;
                                MutableComponent valueComponent = new TranslatableComponent("options.guiScale.auto")
                                        .append(new TextComponent(" (" + auto + ")"));
                                return ((AccessOption) progressOption)
                                        .callGenericValueLabel(valueComponent);
                            } else {
                                MutableComponent valueComponent = new TextComponent(Integer.toString(value));
                                int min = r >> 8 & 0xf;
                                int max = r & 0xf;
                                if (value < min || value > max) {
                                    final MutableComponent hint;
                                    if (value < min) {
                                        hint = new TextComponent(" (<" + min + ")");
                                    } else {
                                        hint = new TextComponent(" (>" + max + ")");
                                    }
                                    valueComponent.append(hint);
                                    valueComponent.withStyle(ChatFormatting.RED);
                                }
                                return ((AccessOption) progressOption)
                                        .callGenericValueLabel(valueComponent);
                            }
                        }
                );
                settings[i] = EventHandler.Client.sNewGuiScale = option;
                captured = true;
                break;
            }
        }
        if (!captured) {
            LOGGER.error(MARKER, "Failed to capture video settings");
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    static void onMenuOpen(@Nonnull OpenMenuEvent event) {
        if (ModernUIForge.sDevelopment) {
            if (event.getMenu() instanceof TestContainerMenu c) {
                event.set(new TestPauseFragment());
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class ModClientDev {

        @SubscribeEvent
        static void onRegistryModel(@Nonnull ModelRegistryEvent event) {
            ForgeModelBakery.addSpecialModel(new ResourceLocation(ModernUI.ID, "item/project_builder_main"));
            ForgeModelBakery.addSpecialModel(new ResourceLocation(ModernUI.ID, "item/project_builder_cube"));
        }

        @SubscribeEvent
        static void onBakeModel(@Nonnull ModelBakeEvent event) {
            Map<ResourceLocation, BakedModel> registry = event.getModelRegistry();
            replaceModel(registry, new ModelResourceLocation(ModernUI.ID, "project_builder", "inventory"),
                    baseModel -> new ProjectBuilderModel(baseModel, event.getModelLoader()));
        }

        private static void replaceModel(@Nonnull Map<ResourceLocation, BakedModel> modelRegistry,
                                         @Nonnull ModelResourceLocation location,
                                         @Nonnull Function<BakedModel, BakedModel> replacer) {
            modelRegistry.put(location, replacer.apply(modelRegistry.get(location)));
        }
    }
}
