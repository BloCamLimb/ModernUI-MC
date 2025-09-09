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

package icyllis.modernui.mc;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nonnull;
import java.lang.ref.WeakReference;

/**
 * Redirect raw Minecraft draw calls to Minecraft main render target.
 */
@ApiStatus.Internal
public class MinecraftDrawHandler {

    private final WeakReference<MinecraftSurfaceView> mViewWeakRef;

    final Properties mStagingProperties = new Properties();
    private final Properties mProperties = new Properties();

    MuiScreen mOwnerScreen;

    public MinecraftDrawHandler(WeakReference<MinecraftSurfaceView> viewWeakRef) {
        mViewWeakRef = viewWeakRef;
    }

    public void render(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick,
                       Window window) {
        MinecraftSurfaceView view = mViewWeakRef.get();
        if (view == null) {
            return;
        }
        Properties p = mProperties;
        if (p.mHidden) {
            return;
        }
        MinecraftSurfaceView.Renderer renderer = view.mRenderer;
        if (renderer == null) {
            return;
        }
        if (p.mAlpha < 0.01f) {
            return;
        }
        gr.nextStratum();
        float guiScale = window.getGuiScale();
        gr.pose().pushMatrix()
                .translate(p.mPositionLeft / guiScale, p.mPositionTop / guiScale);
        try {
            renderer.onDraw(gr, mouseX, mouseY, deltaTick, guiScale, p.mAlpha);
        } finally {
            gr.pose().popMatrix();
        }
    }

    public void syncProperties() {
        MinecraftSurfaceView view = mViewWeakRef.get();
        if (view == null) {
            return;
        }
        MinecraftSurfaceView.Renderer renderer = view.mRenderer;
        if (renderer == null) {
            return;
        }
        boolean sizeChanged = mProperties.set(mStagingProperties);
        if (sizeChanged) {
            renderer.onSurfaceChanged(mProperties.mSurfaceWidth, mProperties.mSurfaceHeight);
        }
    }

    public static class Properties {
        public boolean mHidden;
        public float mAlpha;
        public int mPositionLeft;
        public int mPositionTop;
        public int mSurfaceWidth;
        public int mSurfaceHeight;

        public boolean set(Properties p) {
            mHidden = p.mHidden;
            mAlpha = p.mAlpha;
            mPositionLeft = p.mPositionLeft;
            mPositionTop = p.mPositionTop;
            boolean sizeChanged = mSurfaceWidth != p.mSurfaceWidth ||
                    mSurfaceHeight != p.mSurfaceHeight;
            mSurfaceWidth = p.mSurfaceWidth;
            mSurfaceHeight = p.mSurfaceHeight;
            return sizeChanged;
        }
    }

    public static class Operation {
        public static final int OP_ADD = 0;
        public static final int OP_REMOVE = 1;
        public static final int OP_UPDATE = 2;

        public final int mOp;
        public final MinecraftDrawHandler mTarget;

        public Operation(int op, MinecraftDrawHandler target) {
            mOp = op;
            mTarget = target;
        }
    }
}
