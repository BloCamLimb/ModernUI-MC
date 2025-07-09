/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
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

package icyllis.modernui.mc.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.graphics.ImageStore;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.mixin.AccessOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.apache.commons.io.output.StringBuilderWriter;

import javax.annotation.Nonnull;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;

import static icyllis.modernui.mc.ModernUIMod.*;

/**
 * This class handles mod loading events, all registry entries are only available under the development mode.
 */
@EventBusSubscriber(modid = ModernUI.ID, bus = EventBusSubscriber.Bus.MOD)
final class Registration {

    private Registration() {
    }

    @SubscribeEvent
    static void register(@Nonnull RegisterEvent event) {
        if (ModernUIMod.sDevelopment) {
            event.register(Registries.MENU, Registration::registerMenus);
            event.register(Registries.ITEM, Registration::registerItems);
        }
    }

    static void registerMenus(@Nonnull RegisterEvent.RegisterHelper<MenuType<?>> helper) {
        helper.register(MuiRegistries.TEST_MENU_KEY, IMenuTypeExtension.create(TestContainerMenu::new));
    }

    static void registerItems(@Nonnull RegisterEvent.RegisterHelper<Item> helper) {
        Item.Properties properties = new Item.Properties().stacksTo(1);
        properties.setId(ResourceKey.create(Registries.ITEM, MuiRegistries.PROJECT_BUILDER_ITEM_KEY));
        helper.register(MuiRegistries.PROJECT_BUILDER_ITEM_KEY, new ProjectBuilderItem(properties));
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

        NeoForge.EVENT_BUS.register(ServerHandler.INSTANCE);
    }

    /*@Nonnull
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
    }*/

    @EventBusSubscriber(modid = ModernUI.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ModClient {

        static {
            assert (FMLEnvironment.dist.isClient());
        }

        private ModClient() {
        }

        @SubscribeEvent
        static void loadingClient(RegisterParticleProvidersEvent event) {
            // this event fired after LOAD_REGISTRIES and before COMMON_SETUP on client main thread (render thread)
            // this event fired before AddClientReloadListenersEvent
            UIManagerForge.initialize();
        }

        @SubscribeEvent
        static void registerResourceListener(@Nonnull AddClientReloadListenersEvent event) {
            // this event fired after LOAD_REGISTRIES and before COMMON_SETUP on client main thread (render thread)
            // this event fired after ParticleFactoryRegisterEvent
            event.addListener(ModernUIMod.location("client"), (ResourceManagerReloadListener) manager -> {
                ImageStore.getInstance().clear();
                Handler handler = Core.getUiHandlerAsync();
                // FML may throw ex, so it can be null
                if (handler != null) {
                    // Call in lambda, not in creating the lambda
                    handler.post(() -> UIManager.getInstance().updateLayoutDir(ConfigImpl.CLIENT.mForceRtl.get()));
                }
                //BlurHandler.INSTANCE.loadEffect();
            });
            if (!ModernUIMod.isTextEngineEnabled()) {
                event.addListener(ModernUIMod.location("font"), FontResourceManager.getInstance());
            }
            // else injected by MixinFontManager

            LOGGER.debug(MARKER, "Registered resource reload listener");
        }

        @SubscribeEvent
        static void registerKeyMapping(@Nonnull RegisterKeyMappingsEvent event) {
            event.register(UIManagerForge.OPEN_CENTER_KEY);
            event.register(UIManagerForge.ZOOM_KEY);
        }

        /*@SubscribeEvent
        static void registerCapabilities(@Nonnull RegisterCapabilitiesEvent event) {
            event.register(ScreenCallback.class);
        }*/

        @SubscribeEvent
        static void setupClient(@Nonnull FMLClientSetupEvent event) {
            //SettingsManager.INSTANCE.buildAllSettings();
            //UIManager.getInstance().registerMenuScreen(Registration.TEST_MENU, menu -> new TestUI());

            event.enqueueWork(() -> {
                //ModernUI.getSelectedTypeface();
                UIManagerForge.initializeRenderer();
                // ensure it's applied and positioned
                Config.CLIENT.mLastWindowMode.apply();
            });

            CrashReportCallables.registerCrashCallable("Fragments", () -> {
                var fragments = UIManager.getInstance().getFragmentController();
                var builder = new StringBuilder();
                if (fragments != null) {
                    try (var pw = new PrintWriter(new StringBuilderWriter(builder))) {
                        fragments.getFragmentManager().dump("", null, pw);
                    }
                }
                return builder.toString();
            }, () -> UIManager.getInstance().getFragmentController() != null);

            // Always replace static variable as an insurance policy
            /*AccessOption.setGuiScale(new CycleOption("options.guiScale",
                    (options, integer) -> options.guiScale = Integer.remainderUnsigned(
                            options.guiScale + integer, (MForgeCompat.calcGuiScales() & 0xf) + 1),
                    (options, cycleOption) -> options.guiScale == 0 ?
                            ((AccessOption) cycleOption).callGenericValueLabel(new TranslatableComponent("options" +
                                    ".guiScale.auto")
                                    .append(new TextComponent(" (" + (MForgeCompat.calcGuiScales() >> 4 & 0xf) + ")")
                                    )) :
                            ((AccessOption) cycleOption).callGenericValueLabel(new TextComponent(Integer.toString
                            (options
                            .guiScale))))
            );*/

            if (ConfigImpl.CLIENT.mUseNewGuiScale.get()) {
                final OptionInstance<Integer> newGuiScale = new OptionInstance<>(
                        /*caption*/ "options.guiScale",
                        /*tooltip*/ OptionInstance.noTooltip(),
                        /*toString*/ (caption, value) -> {
                    int r = MuiModApi.calcGuiScales();
                    if (value == 0) { // auto
                        int auto = r >> 4 & 0xf;
                        return Options.genericValueLabel(caption,
                                Component.translatable("options.guiScale.auto")
                                        .append(Component.literal(" (" + auto + ")")));
                    } else {
                        MutableComponent valueComponent = Component.literal(value.toString());
                        int min = r >> 8 & 0xf;
                        int max = r & 0xf;
                        if (value < min || value > max) {
                            final MutableComponent hint;
                            if (value < min) {
                                hint = Component.literal(" (<" + min + ")");
                            } else {
                                hint = Component.literal(" (>" + max + ")");
                            }
                            valueComponent.append(hint);
                            valueComponent.withStyle(ChatFormatting.RED);
                        }
                        return Options.genericValueLabel(caption, valueComponent);
                    }
                },
                        /*values*/ new GuiScaleValueSet(),
                        /*initialValue*/ 0,
                        /*onValueUpdate*/ value -> {
                    // execute in next tick, prevent transient GUI scale change
                    Minecraft.getInstance().schedule(() -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        if ((int) minecraft.getWindow().getGuiScale() !=
                                minecraft.getWindow().calculateScale(value, false)) {
                            minecraft.resizeDisplay();
                        }
                    });
                });
                // no barrier
                Options options = Minecraft.getInstance().options;
                newGuiScale.set(options.guiScale().get());
                ((AccessOptions) options).setGuiScale(newGuiScale);
                if (ModernUIMod.isOptiFineLoaded()) {
                    OptiFineIntegration.setGuiScale(newGuiScale);
                    LOGGER.debug(MARKER, "Override OptiFine Gui Scale");
                }
            }

            /*Option[] settings = null;
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
                    ProgressOption option = new ProgressOption("options.guiScale", 0, 2, 1,
                            options -> (double) options.guiScale,
                            (options, aDouble) -> {
                                if (options.guiScale != aDouble.intValue()) {
                                    options.guiScale = aDouble.intValue();
                                    Minecraft.getInstance().resizeDisplay();
                                }
                            },
                            (options, progressOption) -> options.guiScale == 0 ?
                                    ((AccessOptions) progressOption)
                                            .callGenericValueLabel(new TranslatableComponent("options.guiScale.auto")
                                                    .append(new TextComponent(" (" + (MuiForgeApi.calcGuiScales() >> 4 &
                                                    0xf) + ")"))) :
                                    ((AccessOptions) progressOption)
                                            .callGenericValueLabel(new TextComponent(Integer.toString(options
                                            .guiScale)))
                    );
                    settings[i] = EventHandler.Client.sNewGuiScale = option;
                    captured = true;
                    break;
                }
            }
            if (!captured) {
                LOGGER.error(MARKER, "Failed to capture video settings");
            }*/
        }

        static class GuiScaleValueSet implements OptionInstance.IntRangeBase,
                OptionInstance.SliderableOrCyclableValueSet<Integer> {

            @Override
            public int minInclusive() {
                return 0;
            }

            @Override
            public int maxInclusive() {
                return MuiModApi.MAX_GUI_SCALE;
            }

            @Nonnull
            @Override
            public Integer fromSliderValue(double progress) {
                return Math.toIntExact(Math.round(Mth.map(progress, 0.0, 1.0, minInclusive(), maxInclusive())));
            }

            @Nonnull
            @Override
            public Optional<Integer> validateValue(@Nonnull Integer value) {
                return Optional.of(Mth.clamp(value, minInclusive(), maxInclusive()));
            }

            @Nonnull
            @Override
            public Codec<Integer> codec() {
                return Codec.INT.validate(value -> {
                    int max = maxInclusive() + 1;
                    if (value.compareTo(minInclusive()) >= 0 && value.compareTo(max) <= 0) {
                        return DataResult.success(value);
                    }
                    return DataResult.error(() ->
                            "Value " + value + " outside of range [" + minInclusive() + ":" + max + "]", value);
                });
            }

            @Nonnull
            @Override
            public CycleButton.ValueListSupplier<Integer> valueListSupplier() {
                return CycleButton.ValueListSupplier.create(IntStream.range(minInclusive(), maxInclusive() + 1).boxed().toList());
            }

            @Override
            public boolean createCycleButton() {
                return false;
            }
        }

        /*@SubscribeEvent
        static void onMenuOpen(@Nonnull OpenMenuEvent event) {
            if (ModernUIMod.sDevelopment) {
                if (event.getMenu() instanceof TestContainerMenu c) {
                    event.set(new TestPauseFragment());
                }
            }
        }*/

        @SubscribeEvent
        static void onRegisterMenuScreens(@Nonnull RegisterMenuScreensEvent event) {
            if (ModernUIMod.sDevelopment) {
                event.register(MuiRegistries.TEST_MENU.get(), MenuScreenFactory.create(menu ->
                        new TestPauseFragment()));
            }
        }

        // tooltip is not a required shader, let it lazy init
        /*@SubscribeEvent
        static void onRegisterShaders(@Nonnull RegisterShadersEvent event) {
            try {
                event.registerShader(
                        new ShaderInstance(event.getResourceProvider(),
                                ModernUIMod.location("rendertype_modern_tooltip"),
                                DefaultVertexFormat.POSITION),
                        TooltipRenderType::setShaderTooltip);
            } catch (IOException e) {
                LOGGER.error(MARKER, "Bad tooltip shader", e);
            }
        }*/

        /*@SubscribeEvent
        static void onRegisterClientExtensions(@Nonnull RegisterClientExtensionsEvent event) {
            if (ModernUIMod.sDevelopment) {
                event.registerItem(new IClientItemExtensions() {
                    @Override
                    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return new ProjectBuilderRenderer();
                    }
                }, MuiRegistries.PROJECT_BUILDER_ITEM);
            }
        }*/
    }

    /*static class ModClientDev {

        static {
            assert (FMLEnvironment.dist.isClient());
        }

        private ModClientDev() {
        }

        @SubscribeEvent
        static void onRegistryModel(@Nonnull ModelEvent.RegisterAdditional event) {
            event.register(ModelResourceLocation.standalone(ModernUIMod.location("item/project_builder_main")));
            event.register(ModelResourceLocation.standalone(ModernUIMod.location("item/project_builder_cube")));
        }

        @SubscribeEvent
        static void onBakeModel(@Nonnull ModelEvent.ModifyBakingResult event) {
            Map<ModelResourceLocation, BakedModel> registry = event.getModels();
            replaceModel(registry, ModelResourceLocation.inventory(ModernUIMod.location("project_builder")),
                    baseModel -> new ProjectBuilderModel(baseModel, event.getModels()));
        }

        private static void replaceModel(@Nonnull Map<ModelResourceLocation, BakedModel> modelRegistry,
                                         @Nonnull ModelResourceLocation location,
                                         @Nonnull Function<BakedModel, BakedModel> replacer) {
            modelRegistry.put(location, replacer.apply(modelRegistry.get(location)));
        }
    }*/
}
