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

package icyllis.modernui.mc.fabric;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.graphics.ImageStore;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.mixin.AccessOptions;
import icyllis.modernui.mc.text.MuiTextCommand;
import icyllis.modernui.mc.text.TextLayoutEngine;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.config.ModConfig;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

@Environment(EnvType.CLIENT)
public class ModernUIFabricClient extends ModernUIClient implements ClientModInitializer {

    public static final Event<Runnable> START_RENDER_TICK = EventFactory.createArrayBacked(Runnable.class,
            callbacks -> () -> {
                for (Runnable runnable : callbacks) {
                    runnable.run();
                }
            });

    public static final Event<Runnable> END_RENDER_TICK = EventFactory.createArrayBacked(Runnable.class,
            callbacks -> () -> {
                for (Runnable runnable : callbacks) {
                    runnable.run();
                }
            });

    private String mSelectedLanguageCode;
    private Locale mSelectedJavaLocale;

    public ModernUIFabricClient() {
        super();
    }

    @Override
    protected void checkFirstLoadTypeface() {
        // No-op, on Fabric, this can only be loaded on main thread...
        LOGGER.info(MARKER,
                "Loading typeface, printing the stacktrace for debugging purposes.",
                new Exception("Loading typeface")
                        .fillInStackTrace());
    }

    @Override
    public void onInitializeClient() {
        START_RENDER_TICK.register(EventHandler.Client::onRenderTick);
        END_RENDER_TICK.register(EventHandler.Client::onRenderTick);

        KeyBindingHelper.registerKeyBinding(UIManagerFabric.OPEN_CENTER_KEY);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ModernUIMod.location("client");
            }

            @Override
            public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
                ImageStore.getInstance().clear();
                Handler handler = Core.getUiHandlerAsync();
                // FML may throw ex, so it can be null
                if (handler != null) {
                    // Call in lambda, not in creating the lambda
                    handler.post(() -> UIManager.getInstance().updateLayoutDir(Config.CLIENT.mForceRtl.get()));
                }
                BlurHandler.INSTANCE.loadEffect();
            }
        });

        CoreShaderRegistrationCallback.EVENT.register(context -> {
            try {
                context.register(
                        ModernUIMod.location("rendertype_modern_tooltip"),
                        DefaultVertexFormat.POSITION,
                        TooltipRenderType::setShaderTooltip);
            } catch (IOException e) {
                LOGGER.error(MARKER, "Bad tooltip shader", e);
            }
        });

        ModConfigEvents.loading(ID).register(Config::reloadAnyClient);
        ModConfigEvents.reloading(ID).register(Config::reloadAnyClient);

        ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> {
            UIManagerFabric.initializeRenderer();
            // ensure it's applied and positioned
            Config.CLIENT.mLastWindowMode.apply();

            if (Config.CLIENT.mUseNewGuiScale.get()) {
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
                    Minecraft.getInstance().tell(() -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        if ((int) minecraft.getWindow().getGuiScale() !=
                                minecraft.getWindow().calculateScale(value, false)) {
                            minecraft.resizeDisplay();
                        }
                    });
                });
                Options options = mc.options;
                newGuiScale.set(options.guiScale().get());
                ((AccessOptions) options).setGuiScale(newGuiScale);
                if (ModernUIMod.isOptiFineLoaded()) {
                    OptiFineIntegration.setGuiScale(newGuiScale);
                    LOGGER.debug(MARKER, "Override OptiFine Gui Scale");
                }
            }
        });

        Config.initClientConfig(
                spec -> ForgeConfigRegistry.INSTANCE.register(ID, ModConfig.Type.CLIENT, spec,
                        ModernUI.NAME_CPT + "/client.toml")
        );
        Config.initTextConfig(
                spec -> ForgeConfigRegistry.INSTANCE.register(ID, ModConfig.Type.CLIENT, spec,
                        ModernUI.NAME_CPT + "/text.toml")
        );

        FontResourceManager.getInstance();
        if (ModernUIMod.isTextEngineEnabled()) {
            ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> {
                MuiModApi.addOnWindowResizeListener(TextLayoutEngine.getInstance());
            });

            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                MuiTextCommand.register(dispatcher);
            });

            MuiModApi.addOnDebugDumpListener(TextLayoutEngine.getInstance());

            ClientTickEvents.END_CLIENT_TICK.register((mc) -> TextLayoutEngine.getInstance().onEndClientTick());

            LOGGER.info(MARKER, "Initialized Modern UI text engine");
        } else {
            // see MixinFontManager in another case
            ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() {
                    return ModernUIMod.location("font");
                }

                @Nonnull
                @Override
                public CompletableFuture<Void> reload(@Nonnull PreparationBarrier preparationBarrier,
                                                      @Nonnull ResourceManager resourceManager,
                                                      @Nonnull ProfilerFiller preparationProfiler,
                                                      @Nonnull ProfilerFiller reloadProfiler,
                                                      @Nonnull Executor preparationExecutor,
                                                      @Nonnull Executor reloadExecutor) {
                    return FontResourceManager.getInstance().reload(
                            preparationBarrier,
                            resourceManager,
                            preparationProfiler,
                            reloadProfiler,
                            preparationExecutor,
                            reloadExecutor
                    );
                }
            });
        }
        LOGGER.info(MARKER, "Initialized Modern UI client");
    }

    @SuppressWarnings("ConstantValue")
    @Nonnull
    @Override
    protected Locale onGetSelectedLocale() {
        // Minecraft can be null if we're running DataGen
        // LanguageManager can be null if this method is being called too early
        Minecraft minecraft;
        LanguageManager languageManager;
        if ((minecraft = Minecraft.getInstance()) != null &&
                (languageManager = minecraft.getLanguageManager()) != null) {
            String languageCode = languageManager.getSelected();
            if (!languageCode.equals(mSelectedLanguageCode)) {
                mSelectedLanguageCode = languageCode;
                String[] langSplit = languageCode.split("_", 2);
                mSelectedJavaLocale = langSplit.length == 1
                        ? new Locale(langSplit[0])
                        : new Locale(langSplit[0], langSplit[1]);
            }
            return mSelectedJavaLocale;
        }
        return super.onGetSelectedLocale();
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
            return ExtraCodecs.validate(Codec.INT, value -> {
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
}
