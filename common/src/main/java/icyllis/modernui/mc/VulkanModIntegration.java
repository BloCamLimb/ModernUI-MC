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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.engine.Swizzle;
import icyllis.arc3d.vulkan.VKUtil;
import icyllis.arc3d.vulkan.VulkanBackendContext;
import icyllis.arc3d.vulkan.VulkanImage;
import icyllis.arc3d.vulkan.VulkanImageView;
import icyllis.arc3d.vulkan.VulkanMemoryAllocator;
import icyllis.modernui.core.VulkanManager;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;

import java.lang.reflect.Field;
import java.nio.LongBuffer;
import java.util.Objects;

@ApiStatus.Internal
public final class VulkanModIntegration {

    private static Field MAIN_IMAGE_VIEW;

    static {
        try {
            var f = net.vulkanmod.vulkan.texture.VulkanImage.class.getDeclaredField("mainImageView");
            f.setAccessible(true);
            MAIN_IMAGE_VIEW = f;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static VulkanBackendContext wrapContext() {
        VulkanBackendContext backendContext = new VulkanBackendContext();
        VulkanManager vulkanManager = VulkanManager.get();
        backendContext.mInstance = DeviceManager.physicalDevice.getInstance();
        backendContext.mPhysicalDevice = DeviceManager.physicalDevice;
        backendContext.mDevice = DeviceManager.vkDevice;
        var indices = net.vulkanmod.vulkan.queue.Queue.getQueueFamilies();
        backendContext.mGraphicsQueueIndex = indices.graphicsFamily;
        vulkanManager.setPhysicalDeviceFeatures2(VkPhysicalDeviceFeatures2.calloc());
        backendContext.mDeviceFeatures2 = vulkanManager.getPhysicalDeviceFeatures2();
        vulkanManager.setMemoryAllocator(VulkanMemoryAllocator.make(
                backendContext.mInstance, backendContext.mPhysicalDevice,
                backendContext.mDevice, DeviceManager.deviceProperties.apiVersion(),
                0
        ));
        backendContext.mMemoryAllocator = vulkanManager.getMemoryAllocator();
        backendContext.mQueue = DeviceManager.getGraphicsQueue().vkQueue();

        return backendContext;
    }

    public static void replaceMainImageViewWithSwizzle(GpuTextureView textureView, short swizzle) {
        var vulkanVulkanImage = ((net.vulkanmod.render.engine.VkTextureView) textureView).texture().getVulkanImage();
        VK10.vkDestroyImageView(Vulkan.getVkDevice(), vulkanVulkanImage.getImageView(), null);

        long newView;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo pCreateInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .image(vulkanVulkanImage.getId())
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(vulkanVulkanImage.format);
            pCreateInfo.components().set(
                    VKUtil.toVkComponentSwizzle(Swizzle.getR(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getG(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getB(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getA(swizzle))
            );

            pCreateInfo.subresourceRange()
                    .aspectMask(vulkanVulkanImage.aspect)
                    .baseMipLevel(0)
                    .levelCount(vulkanVulkanImage.mipLevels)
                    .baseArrayLayer(0)
                    .layerCount(vulkanVulkanImage.arrayLayers);
            LongBuffer pView = stack.mallocLong(1);
            int result = VK10.vkCreateImageView(Vulkan.getVkDevice(), pCreateInfo, null, pView);
            VKUtil._CHECK_(result);
            newView = pView.get(0);
        }

        try {
            MAIN_IMAGE_VIEW.setLong(vulkanVulkanImage, newView);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update Vulkan image view", e);
        }
    }

    // caller must track Arc3D CommandBuffer usage and Client usage ref
    // caller must NOT close the returned object
    public static GpuTexture wrapTextureImageFromArc3D(@RawPtr VulkanImage arc3dVulkanImage) {
        VkGpuDevice device = (VkGpuDevice) RenderSystem.getDevice().backend;
        var vulkanImageDesc = arc3dVulkanImage.getVulkanDesc();
        var vulkanVulkanImage = new net.vulkanmod.vulkan.texture.VulkanImage(
                arc3dVulkanImage.getLabel(), arc3dVulkanImage.vkImage(),
                vulkanImageDesc.mVkFormat, vulkanImageDesc.getMipLevelCount(),
                vulkanImageDesc.getWidth(), vulkanImageDesc.getHeight(),
                VKUtil.vkFormatBytesPerBlock(vulkanImageDesc.mVkFormat),
                vulkanImageDesc.mImageUsageFlags,
                Objects.requireNonNull(arc3dVulkanImage.findOrCreateTextureView(Swizzle.RGBA)).vkImageView()
        );
        return device.gpuTextureFromVulkanImage(vulkanVulkanImage);
    }

    public static void syncImageLayoutFromArc3D(GpuTexture vulkanTexture, @RawPtr VulkanImage arc3dVulkanImage) {
        var vulkanVulkanImage = ((net.vulkanmod.render.engine.VkGpuTexture) vulkanTexture).getVulkanImage();
        vulkanVulkanImage.setCurrentLayout(arc3dVulkanImage.getVulkanMutableState().getImageLayout());
    }

    public static void syncImageLayoutFromVulkan(GpuTexture vulkanTexture, @RawPtr VulkanImage arc3dVulkanImage) {
        var vulkanVulkanImage = ((net.vulkanmod.render.engine.VkGpuTexture) vulkanTexture).getVulkanImage();
        arc3dVulkanImage.getVulkanMutableState().setImageLayout(vulkanVulkanImage.getCurrentLayout());
    }

    public static boolean sameImage(GpuTexture vulkanTexture, @RawPtr VulkanImage arc3dVulkanImage) {
        var vulkanVulkanImage = ((net.vulkanmod.render.engine.VkGpuTexture) vulkanTexture).getVulkanImage();
        // If not same, the previous one should be still in-flight, so there shouldn't be ABA issue
        return vulkanVulkanImage.getId() == arc3dVulkanImage.vkImage();
    }

    public static void addFrameOp(Runnable runnable) {
        MemoryManager.getInstance().addFrameOp(runnable);
    }
}
