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

package icyllis.modernui.mc.text.mixin;

import icyllis.modernui.mc.text.ModernTextRenderer;
import icyllis.modernui.mc.text.TextLayout;
import icyllis.modernui.mc.text.TextLayoutEngine;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(NameTagFeatureRenderer.class)
public abstract class MixinNameTagFeatureRenderer {

    @Inject(method = "buildGroup(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;" +
            "Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
    private void onBuildGroup(FeatureFrameContext context,
                              List<NameTagFeatureRenderer.Submit> submits,
                              CallbackInfo ci) {
        ci.cancel();

        final TextLayout.BufferSource source =
                ((AccessRenderTypeFeatureRenderer) this)::modernUI_MC$getVertexBuilder;

        final boolean wasInWorldRendering = TextLayoutEngine.sCurrentInWorldRendering;
        TextLayoutEngine.sCurrentInWorldRendering = true;
        try {
            for (int i = 0; i < submits.size(); i++) {
                var submit = submits.get(i);
                ModernTextRenderer.drawWorldText(submit.text().getVisualOrderText(),
                        submit.x(), submit.y(), submit.color(), false,
                        submit.pose(), source, submit.displayMode(),
                        submit.backgroundColor(), submit.lightCoords());
            }
        } finally {
            TextLayoutEngine.sCurrentInWorldRendering = wasInWorldRendering;
        }
    }
}
