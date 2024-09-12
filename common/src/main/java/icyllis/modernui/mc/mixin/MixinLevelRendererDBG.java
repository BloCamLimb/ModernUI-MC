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

package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static icyllis.modernui.ModernUI.LOGGER;

/**
 * Debug Minecraft-Transit-Railway
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRendererDBG {

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=blockentities", ordinal = 0))
    private void afterEntities(DeltaTracker deltaTracker, boolean renderBlockOutline,
                               Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                               Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (Screen.hasAltDown() &&
                InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_KP_7)) {
            LOGGER.info("Capture from MixinLevelRendererDBG.afterEntities()");
            LOGGER.info("Param ModelViewMatrix: {}", modelView);
            LOGGER.info("Param Camera.getPosition(): {}, pitch: {}, yaw: {}, rot: {}, detached: {}",
                    camera.getPosition(), camera.getXRot(), camera.getYRot(), camera.rotation(), camera.isDetached());
            LOGGER.info("Param ProjectionMatrix: {}", projection);
            LOGGER.info("RenderSystem.getModelViewStack(): {}",
                    RenderSystem.getModelViewStack());
            LOGGER.info("RenderSystem.getModelViewMatrix(): {}", RenderSystem.getModelViewMatrix());
            LOGGER.info("GameRenderer.getMainCamera().getPosition(): {}, pitch: {}, yaw: {}, rot: {}, detached: {}",
                    Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(),
                    camera.getXRot(), camera.getYRot(), camera.rotation(), camera.isDetached());
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                LOGGER.info("LocalPlayer: yaw: {}, yawHead: {}, eyePos: {}",
                        player.getYRot(), player.getYHeadRot(),
                        player.getEyePosition(deltaTracker.getGameTimeDeltaPartialTick(false)));
            }
            Entity cameraEntity = Minecraft.getInstance().cameraEntity;
            if (cameraEntity != null) {
                LOGGER.info("CameraEntity position: {}", cameraEntity.position());
            }
        }
    }
}
