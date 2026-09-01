/*
 * Modern UI.
 * Copyright (C) 2019-2026 BloCamLimb. All rights reserved.
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
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import icyllis.arc3d.engine.Swizzle;
import icyllis.arc3d.vulkan.VKUtil;
import icyllis.arc3d.vulkan.VulkanBackendContext;
import icyllis.arc3d.vulkan.VulkanImage;
import icyllis.arc3d.vulkan.VulkanMemoryAllocator;
import icyllis.modernui.core.VulkanManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import javax.annotation.Nonnull;
import java.nio.LongBuffer;

public final class VanillaVulkanIntegration {

    private static final java.util.List<Runnable[]> sPendingFrameOps = new java.util.ArrayList<>();

    private VanillaVulkanIntegration() {
    }

    public static VulkanBackendContext wrapContext() {
        var device = (VulkanDevice) RenderSystem.getDevice().backend;
        var vkInstance = device.instance().vkInstance();
        var vkDevice = device.vkDevice();
        var physicalDevice = vkDevice.getPhysicalDevice();
        var graphicsQueue = device.graphicsQueue();

        final int apiVersion;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
            apiVersion = properties.apiVersion();
        }

        VulkanManager vulkanManager = VulkanManager.get();
        // vanilla does not keep the feature set it enabled at device creation either, and
        // querying the physical device would report features that were never enabled, so
        // leave the struct zeroed - the VulkanMod path does the same
        vulkanManager.setPhysicalDeviceFeatures2(VkPhysicalDeviceFeatures2.calloc().sType$Default());
        // vanilla has its own VMA instance behind VulkanDevice.vma(), but it is a raw
        // handle and sharing it would tie the two allocation lifetimes together
        vulkanManager.setMemoryAllocator(VulkanMemoryAllocator.make(
                vkInstance, physicalDevice, vkDevice, apiVersion, 0
        ));

        VulkanBackendContext backendContext = new VulkanBackendContext();
        backendContext.mInstance = vkInstance;
        backendContext.mPhysicalDevice = physicalDevice;
        backendContext.mDevice = vkDevice;
        backendContext.mQueue = graphicsQueue.vkQueue();
        backendContext.mGraphicsQueueIndex = graphicsQueue.queueFamilyIndex();
        backendContext.mDeviceFeatures2 = vulkanManager.getPhysicalDeviceFeatures2();
        backendContext.mMemoryAllocator = vulkanManager.getMemoryAllocator();

        return backendContext;
    }

    /**
     * Run {@code op} once the frame it belongs to has retired, the counterpart of
     * VulkanMod's frame op queue.
     * <p>
     * Blaze3D keeps several frames in flight, so handing it a resource and submitting is
     * not the end of the story: its command buffers still reference that resource well
     * after the submit returns. This is how we hold on to one for exactly that long.
     */
    public static void addFrameOp(@Nonnull Runnable op) {
        // Keep our own handle on it as well. Blaze3D drains this queue as its frames retire,
        // but it tears the device down without waiting for it, and anything still queued
        // then is simply leaked, see flushFrameOps().
        final Runnable[] holder = {op};
        synchronized (sPendingFrameOps) {
            sPendingFrameOps.add(holder);
        }
        ((VulkanDevice) RenderSystem.getDevice().backend)
                .createCommandEncoder()
                .queueForDestroy(() -> runFrameOp(holder));
    }

    private static void runFrameOp(@Nonnull Runnable[] holder) {
        Runnable op;
        synchronized (sPendingFrameOps) {
            sPendingFrameOps.remove(holder);
            op = holder[0];
            holder[0] = null;
        }
        if (op != null) {
            op.run();
        }
    }

    /**
     * Run every frame op Blaze3D has not got round to. Called on the way out, before the
     * device goes away, so that we do not leave child objects behind it.
     */
    public static void flushFrameOps() {
        Object[] pending;
        synchronized (sPendingFrameOps) {
            pending = sPendingFrameOps.toArray();
        }
        for (Object o : pending) {
            runFrameOp((Runnable[]) o);
        }
    }

    /**
     * Move the Arc3D layer image into the layout Blaze3D assumes for everything it samples,
     * and record that in Arc3D's own tracking so it does not transition from a stale value.
     * <p>
     * This is the vanilla counterpart of VulkanMod's syncImageLayout pair, but it has to do
     * more than bookkeeping. VulkanMod tracks image layouts and emits its own barriers once
     * it is told the truth; Blaze3D tracks nothing and assumes VK_IMAGE_LAYOUT_GENERAL
     * everywhere, so the transition has to actually happen here. Arc3D leaves the layer in
     * VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL after drawing into it, and sampling it as
     * GENERAL is VUID-vkCmdDraw-None-09600.
     */
    public static void syncImageLayoutToVanilla(@Nonnull VulkanImage image) {
        var state = image.getVulkanMutableState();
        final int oldLayout = state.getImageLayout();
        if (oldLayout == VK10.VK_IMAGE_LAYOUT_GENERAL) {
            return;
        }
        var encoder = ((VulkanDevice) RenderSystem.getDevice().backend).createCommandEncoder();
        var commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .srcAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(oldLayout)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image.vkImage());
            barrier.subresourceRange()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(image.getMipLevelCount())
                    .baseArrayLayer(0)
                    .layerCount(1);
            VK10.vkCmdPipelineBarrier(commandBuffer,
                    VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0, null, null, barrier);
        }
        // execute() ends the encoder's own buffer, not the transient one it is handed
        VKUtil._CHECK_(VK10.vkEndCommandBuffer(commandBuffer));
        encoder.execute(commandBuffer);
        state.setImageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
    }

    /**
     * Give an existing texture view a component swizzle, which Vulkan can only express at
     * image view creation. Used for the single channel glyph atlas, where the coverage
     * has to arrive in the alpha channel; the OpenGL backend does the same thing with
     * {@code GL_TEXTURE_SWIZZLE_RGBA}.
     * <p>
     * Vanilla has no entry point for this, so the view is rebuilt the way
     * {@code VulkanGpuTextureView} builds its own and written back over the handle. The
     * view remains owned by vanilla and is destroyed by it as usual.
     */
    public static void replaceImageViewWithSwizzle(@Nonnull GpuTextureView textureView, short swizzle) {
        var view = (VulkanGpuTextureView) textureView;
        var texture = view.texture();
        var format = texture.getFormat();
        var vkDevice = ((VulkanDevice) RenderSystem.getDevice().backend).vkDevice();

        VK10.vkDestroyImageView(vkDevice, view.vkImageView, null);

        final long newImageView;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo pCreateInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .image(texture.vkImage())
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(VulkanConst.toVk(format));
            pCreateInfo.components().set(
                    VKUtil.toVkComponentSwizzle(Swizzle.getR(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getG(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getB(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getA(swizzle))
            );
            pCreateInfo.subresourceRange()
                    .aspectMask(format.hasColorAspect()
                            ? VK10.VK_IMAGE_ASPECT_COLOR_BIT
                            : VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .baseMipLevel(view.baseMipLevel())
                    .levelCount(view.mipLevels())
                    .baseArrayLayer(0)
                    .layerCount(1);
            LongBuffer pView = stack.mallocLong(1);
            VKUtil._CHECK_(VK10.vkCreateImageView(vkDevice, pCreateInfo, null, pView));
            newImageView = pView.get(0);
        }

        view.vkImageView = newImageView;
    }
}
