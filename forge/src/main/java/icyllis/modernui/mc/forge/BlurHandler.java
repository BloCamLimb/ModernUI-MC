/*
 * Modern UI.
 * Copyright (C) 2019-2022 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import icyllis.modernui.ModernUI;
import icyllis.modernui.animation.ColorEvaluator;
import icyllis.modernui.mc.forge.mixin.AccessPostChain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.apache.commons.lang3.StringUtils;
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
    private static final ResourceLocation BLUR_POST_EFFECT = new ResourceLocation("shaders/post/blur_fast.json");

    /**
     * Config values
     */
    public static volatile boolean sBlurEffect;
    public static volatile int sBlurRadius;
    public static volatile int sBackgroundDuration; // milliseconds
    public static volatile int[] sBackgroundColor = new int[4];

    public static volatile int sFramerateInactive;
    public static volatile int sFramerateMinimized;
    public static volatile float sMasterVolumeInactive = 1;
    public static volatile float sMasterVolumeMinimized = 1;

    private final Minecraft minecraft = Minecraft.getInstance();

    private final ArrayList<Class<? extends Screen>> mBlacklist = new ArrayList<>();

    private final int[] mBackgroundColor = new int[4];

    /**
     * If it is playing animation
     */
    private boolean mFadingIn;

    /**
     * If blur post-processing shader is activated
     */
    private boolean mBlurring;

    /**
     * If a screen excluded, the other screens that opened after this screen won't be blurred, unless current screen
     * closed
     */
    private boolean mHasScreen;

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
            minecraft.gameRenderer.shutdownEffect();
            mFadingIn = false;
            mBlurring = false;
        }

        GameRenderer gr = minecraft.gameRenderer;
        if (hasScreen && !mHasScreen) {
            if (!blocked && sBlurEffect && !mBlurring && gr.currentEffect() == null && sBlurRadius >= 1) {
                gr.loadEffect(BLUR_POST_EFFECT);
                mBlurring = true;
                if (sBackgroundDuration > 0) {
                    updateRadius(1);
                } else {
                    updateRadius(sBlurRadius);
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
                gr.shutdownEffect();
                mBlurring = false;
            }
            mFadingIn = false;
        }
        mHasScreen = hasScreen;
    }

    /**
     * Internal method, to re-blur after resources (including shaders) reloaded in the pause menu.
     */
    public void forceBlur() {
        // no need to check if is excluded, this method is only called by opened ModernUI Screen
        if (!sBlurEffect) {
            return;
        }
        if (minecraft.level != null && mBlurring) {
            GameRenderer gr = minecraft.gameRenderer;
            if (gr.currentEffect() == null) {
                gr.loadEffect(BLUR_POST_EFFECT);
                mFadingIn = true;
                mBlurring = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void loadBlacklist(@Nullable List<? extends String> names) {
        mBlacklist.clear();
        if (names == null) {
            return;
        }
        for (String s : names) {
            if (StringUtils.isEmpty(s)) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(s, false, ModernUIForge.class.getClassLoader());
                mBlacklist.add((Class<? extends Screen>) clazz);
            } catch (ClassNotFoundException e) {
                ModernUI.LOGGER.warn(ModernUI.MARKER,
                        "Failed to add blur blacklist {}: make sure class name exists", s, e);
            } catch (ClassCastException e) {
                ModernUI.LOGGER.warn(ModernUI.MARKER,
                        "Failed to add blur blacklist {}: make sure class is a valid subclass of Screen", s, e);
            }
        }
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
        PostChain effect = minecraft.gameRenderer.currentEffect();
        if (effect == null)
            return;
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
    public void drawScreenBackground(@Nonnull Screen screen, @Nonnull PoseStack stack, int x1, int y1, int x2, int y2) {
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = stack.last().pose();
        int z = screen.getBlitOffset();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (minecraft.level == null) {
            builder.vertex(matrix, x2, y1, z)
                    .color(25, 25, 25, 255).endVertex();
            builder.vertex(matrix, x1, y1, z)
                    .color(25, 25, 25, 255).endVertex();
            builder.vertex(matrix, x1, y2, z)
                    .color(25, 25, 25, 255).endVertex();
            builder.vertex(matrix, x2, y2, z)
                    .color(25, 25, 25, 255).endVertex();
        } else {
            int color = mBackgroundColor[1];
            builder.vertex(matrix, x2, y1, z)
                    .color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24).endVertex();
            color = mBackgroundColor[0];
            builder.vertex(matrix, x1, y1, z)
                    .color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24).endVertex();
            color = mBackgroundColor[3];
            builder.vertex(matrix, x1, y2, z)
                    .color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24).endVertex();
            color = mBackgroundColor[2];
            builder.vertex(matrix, x2, y2, z)
                    .color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());

        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
    }
}