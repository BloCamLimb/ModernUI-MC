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

package icyllis.modernui.mc.text.mixin;

import icyllis.modernui.mc.text.TextLayoutEngine;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(FontManager.class)
public class MixinFontManager {

    /**
     * @author BloCamLimb
     * @reason Modern Text Engine
     */
    @Overwrite
    public CompletableFuture<Void> reload(@Nonnull PreparableReloadListener.PreparationBarrier preparationBarrier,
                                          @Nonnull ResourceManager resourceManager,
                                          @Nonnull Executor preparationExecutor,
                                          @Nonnull Executor reloadExecutor) {
        return TextLayoutEngine.getInstance().injectFontManager((FontManager) (Object) this)
                .reload(preparationBarrier,
                        resourceManager,
                        preparationExecutor,
                        reloadExecutor);
    }
}
