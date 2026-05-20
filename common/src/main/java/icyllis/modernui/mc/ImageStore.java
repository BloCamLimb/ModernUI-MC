/*
 * Modern UI.
 * Copyright (C) 2023-2026 BloCamLimb. All rights reserved.
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

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.function.BiFunction;

/**
 * Used to obtain an {@link Image} object from mod resources.
 * The old {@link Image#create(String, String)} is redirected to here,
 * but the old method can only access the "textures" subdirectory.
 *
 * @since 3.13.0
 */
public class ImageStore implements BiFunction<String, String, Image> {

    private static final ImageStore INSTANCE = new ImageStore();

    private final Object mLock = new Object();
    private HashMap<Identifier, WeakReference<Image>> mImages = new HashMap<>();

    private ImageStore() {
    }

    /**
     * @return the global texture manager instance
     */
    public static ImageStore getInstance() {
        return INSTANCE;
    }

    @ApiStatus.Internal
    public void clear() {
        synchronized (mLock) {
            for (var entry : mImages.values()) {
                var image = entry.get();
                if (image != null) {
                    image.close();
                }
            }
            mImages = new HashMap<>();
        }
    }

    /**
     * Get or create a texture image from the given resource.
     * <p>
     * This method should be called only from the UI thread; and the returned object
     * should be used only on the UI thread (unless you clone it).
     *
     * @param location the identifier to the resource
     * @return texture image, null if failed
     */
    @Nullable
    public Image getOrCreate(@NonNull Identifier location) {
        synchronized (mLock) {
            var imageRef = mImages.get(location);
            Image image;
            if (imageRef != null && (image = imageRef.get()) != null && !image.isClosed()) {
                return image;
            }
        }
        try (var stream = Minecraft.getInstance().getResourceManager().open(location);
             var bitmap = BitmapFactory.decodeStream(stream)) {
            var newImage = Image.createTextureFromBitmap(bitmap);
            synchronized (mLock) {
                var imageRef = mImages.get(location);
                Image image;
                if (imageRef != null && (image = imageRef.get()) != null && !image.isClosed()) {
                    // race
                    if (newImage != null) {
                        newImage.close();
                    }
                    return image;
                }
                if (newImage != null) {
                    mImages.put(location, new WeakReference<>(newImage));
                    return newImage;
                }
            }
        } catch (Exception e) {
            ModernUIMod.LOGGER.error(ModernUIMod.MARKER, "Failed to load image {}", location, e);
        }
        return null;
    }

    /**
     * Same as {@link #getOrCreate(Identifier)}.
     */
    @Nullable
    @Override
    public Image apply(@NonNull String namespace, @NonNull String path) {
        var location = Identifier.tryBuild(namespace, path);
        if (location != null) {
            return getOrCreate(location);
        }
        return null;
    }
}
