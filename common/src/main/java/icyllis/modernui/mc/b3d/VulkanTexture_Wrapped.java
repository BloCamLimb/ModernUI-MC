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

package icyllis.modernui.mc.b3d;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.vulkan.VulkanImage;
import icyllis.modernui.mc.VanillaVulkanIntegration;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.vulkan.VK10;

import javax.annotation.Nonnull;

// Wrap Arc3D Vulkan image in Blaze3D Vulkan backend as a unique owner.
public class VulkanTexture_Wrapped extends VulkanGpuTexture {

    // usage ref count is managed by caller
    public VulkanImage source;

    public VulkanTexture_Wrapped(@Nonnull @SharedPtr VulkanImage source) {
        super((VulkanDevice) RenderSystem.getDevice().backend,
                USAGE_COPY_SRC | USAGE_TEXTURE_BINDING |
                        (source.isRenderable() ? USAGE_RENDER_ATTACHMENT : 0),
                source.getLabel(),
                toGpuFormat(source),
                source.getWidth(), source.getHeight(),
                /*depthOrLayers*/ 1, source.getMipLevelCount());
        assert source.getDepth() == 1;
        assert source.getArraySize() == 1;
        assert source.getSampleCount() == 1;

        var device = (VulkanDevice) RenderSystem.getDevice().backend;
        // The super constructor allocated an image of its own, we only wanted the
        // GpuTexture bookkeeping around it. Do not free that image here: it carries the
        // label we passed and Blaze3D has already touched it on the command buffer that is
        // currently recording, so destroying it inline invalidates that command buffer.
        // Hand it to the frame op queue like any other resource Blaze3D still holds.
        final long ownImage = vkImage;
        final long ownAllocation = vmaAllocation;
        vkImage = source.vkImage();
        vmaAllocation = VK10.VK_NULL_HANDLE;
        VanillaVulkanIntegration.addFrameOp(
                () -> Vma.vmaDestroyImage(device.vma(), ownImage, ownAllocation));

        this.source = source; // move
    }

    @Nonnull
    private static GpuFormat toGpuFormat(@Nonnull VulkanImage source) {
        int vkFormat = source.getVulkanDesc().mVkFormat;
        return switch (vkFormat) {
            case VK10.VK_FORMAT_R8G8B8A8_UNORM -> GpuFormat.RGBA8_UNORM;
            case VK10.VK_FORMAT_R8_UNORM -> GpuFormat.R8_UNORM;
            default -> throw new IllegalArgumentException("Unsupported VkFormat " + vkFormat);
        };
    }

    /**
     * The image is owned by Arc3D, there is nothing of ours left to free.
     */
    @Override
    public void destroy() {
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            source.unref();
        }
    }

    // we can increment usage cnt if not reached to zero,
    // to use the same wrapper object.
    // otherwise cause an assertion error
    public void touch() {
        if (closed) {
            closed = false;
            source.ref();
        }
    }

    @Override
    public void addViews() {
    }

    @Override
    public void removeViews() {
    }
}
