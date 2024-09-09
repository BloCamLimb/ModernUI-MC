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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import icyllis.modernui.ModernUI;
import icyllis.modernui.animation.ColorEvaluator;
import icyllis.modernui.mc.mixin.AccessPostChain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.apache.commons.lang3.StringUtils;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Handling the blur effect of screen background. Client only.
 */
public enum BlurHandler {
    INSTANCE;

    // minecraft namespace
    private static final ResourceLocation GAUSSIAN_BLUR =
            new ResourceLocation("shaders/post/modern_gaussian_blur.json");

    /**
     * Config values
     */
    public static volatile boolean sBlurEffect;
    public static volatile boolean sBlurWithBackground;
    public static volatile int sBlurRadius;
    public static volatile int sBackgroundDuration; // milliseconds
    public static volatile int[] sBackgroundColor = new int[4];

    public static volatile int sFramerateInactive;
    public static volatile int sFramerateMinimized;
    public static volatile float sMasterVolumeInactive = 1;
    public static volatile float sMasterVolumeMinimized = 1;

    private final Minecraft minecraft = Minecraft.getInstance();

    private volatile ArrayList<Class<? extends Screen>> mBlacklist = new ArrayList<>();

    private final int[] mBackgroundColor = new int[4];

    /**
     * If it is playing animation
     */
    private boolean mFadingIn;

    /**
     * If blur is running
     */
    private boolean mBlurring;

    /**
     * If blur post-processing shader is activated
     */
    private boolean mBlurLoaded;

    private float mBlurRadius;

    /**
     * If a screen excluded, the other screens that opened after this screen won't be blurred, unless current screen
     * closed
     */
    private boolean mHasScreen;

    @Nullable
    private PostChain mBlurEffect;

    /**
     * True if blur post-processing shader is loaded, though it may fail.
     */
    private boolean mBlurEffectLoaded;

    private float mVolumeMultiplier = 1;

    /**
     * Use blur shader in game renderer post-processing.
     */
    public void blur(@Nullable Screen nextScreen) {
        if (minecraft.level == null) {
            return;
        }
        boolean hasScreen = nextScreen != null;

        boolean blocked = false;
        if (hasScreen && sBlurEffect) {
            if (nextScreen instanceof MuiScreen screen) {
                ScreenCallback callback = screen.getCallback();
                if (callback != null) {
                    blocked = !callback.shouldBlurBackground();
                }
            } else {
                final Class<?> t = nextScreen.getClass();
                for (Class<?> c : mBlacklist) {
                    if (c.isAssignableFrom(t)) {
                        blocked = true;
                        break;
                    }
                }
            }
        }

        if (blocked && mBlurring) {
            if (mBlurLoaded) {
                minecraft.gameRenderer.shutdownEffect();
            }
            mBlurring = false;
            mBlurLoaded = false;
        }

        GameRenderer gr = minecraft.gameRenderer;
        if (hasScreen && !mHasScreen) {
            if (!blocked && sBlurEffect && !mBlurring && sBlurRadius >= 1) {
                if (sBlurWithBackground) {
                    if (!mBlurEffectLoaded) {
                        loadEffect();
                        assert mBlurEffectLoaded;
                    }
                    if (mBlurEffect != null) {
                        mBlurring = true;
                    }
                } else if (gr.currentEffect() == null) {
                    mBlurring = true;
                    mBlurLoaded = true;
                    MuiModApi.get().loadEffect(minecraft.gameRenderer, GAUSSIAN_BLUR);
                }
                if (mBlurring) {
                    if (sBackgroundDuration > 0) {
                        updateRadius(1);
                    } else {
                        updateRadius(sBlurRadius);
                    }
                }
            }
            if (sBackgroundDuration > 0) {
                mFadingIn = true;
                Arrays.fill(mBackgroundColor, 0);
            } else {
                mFadingIn = false;
                System.arraycopy(sBackgroundColor, 0, mBackgroundColor, 0, 4);
            }
        } else if (!hasScreen) {
            if (mBlurring) {
                if (mBlurLoaded) {
                    gr.shutdownEffect();
                }
                mBlurring = false;
                mBlurLoaded = false;
            }
            mFadingIn = false;
        }
        mHasScreen = hasScreen;
    }

    public void closeEffect() {
        if (mBlurEffect != null) {
            mBlurEffect.close();
        }
        mBlurEffect = null;
    }

    public void loadEffect() {
        closeEffect();
        if (sBlurEffect && sBlurWithBackground) {
            mBlurEffectLoaded = true;
            try {
                mBlurEffect = new PostChain(minecraft.getTextureManager(),
                        minecraft.getResourceManager(), minecraft.getMainRenderTarget(), GAUSSIAN_BLUR);
                mBlurEffect.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
            } catch (Exception e) {
                ModernUI.LOGGER.warn(ModernUI.MARKER, "Failed to load blur effect", e);
            }
            if (mBlurEffect == null) {
                mBlurring = false;
            }
        }
    }

    public void resize(int width, int height) {
        if (mBlurEffect != null) {
            mBlurEffect.resize(width, height);
        }
    }

    @SuppressWarnings("unchecked")
    public void loadBlacklist(@Nullable List<? extends String> names) {
        ArrayList<Class<? extends Screen>> blacklist = new ArrayList<>();
        if (names != null) {
            for (String s : names) {
                if (StringUtils.isEmpty(s)) {
                    continue;
                }
                try {
                    Class<?> clazz = Class.forName(s, false, ModernUIMod.class.getClassLoader());
                    blacklist.add((Class<? extends Screen>) clazz);
                } catch (ClassNotFoundException e) {
                    ModernUI.LOGGER.warn(ModernUI.MARKER,
                            "Failed to add blur blacklist {}: make sure class name exists", s, e);
                } catch (ClassCastException e) {
                    ModernUI.LOGGER.warn(ModernUI.MARKER,
                            "Failed to add blur blacklist {}: make sure class is a valid subclass of Screen", s, e);
                }
            }
            blacklist.trimToSize();
        }
        mBlacklist = blacklist;
    }

    /**
     * Render tick, should called before rendering things
     */
    public void onRenderTick(long elapsedTimeMillis) {
        if (mFadingIn) {
            float p = Math.min((float) elapsedTimeMillis / sBackgroundDuration, 1.0f);
            if (mBlurring) {
                updateRadius(Math.max(p * sBlurRadius, 1.0f));
            }
            for (int i = 0; i < 4; i++) {
                mBackgroundColor[i] = ColorEvaluator.evaluate(p, 0, sBackgroundColor[i]);
            }
            if (p == 1.0f) {
                mFadingIn = false;
            }
        }
    }

    private void updateRadius(float radius) {
        mBlurRadius = radius;
        PostChain effect;
        if (sBlurWithBackground) {
            return;
        } else {
            effect = minecraft.gameRenderer.currentEffect();
        }
        if (effect == null)
            return;
        updateRadius(effect, radius);
    }

    private void updateRadius(@Nonnull PostChain effect, float radius) {
        List<PostPass> passes = ((AccessPostChain) effect).getPasses();
        for (PostPass s : passes) {
            s.getEffect().safeGetUniform("Progress").set(radius);
        }
    }

    public void onClientTick() {
        float targetVolumeMultiplier;
        if (minecraft.isWindowActive()) {
            targetVolumeMultiplier = 1;
        } else if (sMasterVolumeMinimized < sMasterVolumeInactive &&
                GLFW.glfwGetWindowAttrib(minecraft.getWindow().getWindow(), GLFW.GLFW_ICONIFIED) != 0) {
            targetVolumeMultiplier = sMasterVolumeMinimized;
        } else {
            targetVolumeMultiplier = sMasterVolumeInactive;
        }
        if (mVolumeMultiplier != targetVolumeMultiplier) {
            // fade down is slower, 1 second = 20 ticks
            if (mVolumeMultiplier < targetVolumeMultiplier) {
                mVolumeMultiplier = Math.min(
                        mVolumeMultiplier + 0.5f,
                        targetVolumeMultiplier
                );
            } else {
                mVolumeMultiplier = Math.max(
                        mVolumeMultiplier - 0.05f,
                        targetVolumeMultiplier
                );
            }
            float volume = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
            minecraft.getSoundManager().updateSourceVolume(SoundSource.MASTER, volume * mVolumeMultiplier);
        }
    }

    // INTERNAL HOOK
    public void drawScreenBackground(@Nonnull GuiGraphics gr, int x1, int y1, int x2, int y2) {
        VertexConsumer consumer = gr.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = gr.pose().last().pose();
        int z = 0;
        if (minecraft.level == null) {
            consumer.vertex(pose, x2, y1, z)
                    .color(30, 31, 34, 255).endVertex();
            consumer.vertex(pose, x1, y1, z)
                    .color(30, 31, 34, 255).endVertex();
            consumer.vertex(pose, x1, y2, z)
                    .color(30, 31, 34, 255).endVertex();
            consumer.vertex(pose, x2, y2, z)
                    .color(30, 31, 34, 255).endVertex();
        } else {
            if (mBlurring && sBlurWithBackground && mBlurEffect != null) {
                updateRadius(mBlurEffect, mBlurRadius);
                // depth test needs to be disabled for Minecraft <1.20
                // and for Forge <1.20.6, because GuiFarPlane changed
                //RenderSystem.disableDepthTest();
                // we don't use time, pass deltaTicks = 0
                mBlurEffect.process(0);
                minecraft.getMainRenderTarget().bindWrite(false);
            }
            int color = mBackgroundColor[1];
            consumer.vertex(pose, x2, y1, z)
                    .color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24).endVertex();
            color = mBackgroundColor[0];
            consumer.vertex(pose, x1, y1, z)
                    .color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24).endVertex();
            color = mBackgroundColor[3];
            consumer.vertex(pose, x1, y2, z)
                    .color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24).endVertex();
            color = mBackgroundColor[2];
            consumer.vertex(pose, x2, y2, z)
                    .color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24).endVertex();
        }
        gr.flush();
    }
}
