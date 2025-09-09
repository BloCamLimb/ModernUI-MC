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

import icyllis.modernui.annotation.FloatRange;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewTreeObserver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;
import java.lang.ref.WeakReference;

/**
 * A MinecraftSurfaceView redirects raw rendering calls to Minecraft main render target.
 * <p>
 * A MinecraftSurfaceView represents a logical surface in Minecraft rendering pipeline.
 *
 * @since 3.11
 */
public class MinecraftSurfaceView extends View {

    private final WeakReference<MinecraftSurfaceView> mThisWeakRef =
            new WeakReference<>(this);

    volatile Renderer mRenderer;

    private MinecraftDrawHandler mDrawHandler;

    private final ViewTreeObserver.OnScrollChangedListener mScrollChangedListener =
            this::updateSurface;

    private final ViewTreeObserver.OnPreDrawListener mDrawListener = () -> {
        // reposition ourselves where the surface is
        mHaveFrame = getWidth() > 0 && getHeight() > 0;
        updateSurface();
        return true;
    };

    private boolean mGlobalListenersAdded;
    private boolean mAttachedToWindow;

    final int[] mLocation = new int[2];

    boolean mRequestedVisible = false;
    boolean mViewVisibility = false;

    float mAlpha = 1f;

    boolean mHaveFrame = false;

    boolean mVisible = false;
    int mWindowSpaceLeft = -1;
    int mWindowSpaceTop = -1;
    int mSurfaceWidth = -1;
    int mSurfaceHeight = -1;

    public MinecraftSurfaceView(Context context) {
        super(context);
        setWillNotDraw(true);
    }

    /**
     * A generic renderer interface.
     * <p>
     * The renderer is responsible for making Minecraft calls to render objects.
     */
    @RenderThread
    public interface Renderer {

        /**
         * Called when the logical surface changed size.
         *
         * @param width  new width in pixels
         * @param height new height in pixels
         */
        void onSurfaceChanged(int width, int height);

        /**
         * Called to draw the current frame.
         * <p>
         * The current pose matrix is translated to view's position in GUI scaled
         * coordinates.
         * <p>
         * The draw's local coordinates are defined in Minecraft GUI scaled coordinates
         * instead of screen coordinates in pixels, the left top point is (0, 0);
         * <p>
         * <var>alpha</var> is synchronized with view's alpha value. Because there is
         * no real surface, the implementer needs to handle alpha on their own.
         * <p>
         * On Minecraft 1.21.6 and above, when this method is called, scissor stack
         * is unaffected by surface bounds.
         *
         * @param gr        a GUI graphics to render objects
         * @param mouseX    mouse x-position in GUI scaled coordinates
         * @param mouseY    mouse y-position in GUI scaled coordinates
         * @param deltaTick tick change between frames
         * @param guiScale  current GUI scale
         * @param alpha     requested alpha for rendering objects
         */
        void onDraw(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick,
                    double guiScale, @FloatRange(from = 0.0, to = 1.0) float alpha);
    }

    /**
     * Set the renderer associated with this view.
     *
     * @param renderer the renderer callback
     */
    public void setRenderer(Renderer renderer) {
        mRenderer = renderer;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mViewVisibility = getVisibility() == VISIBLE;
        mRequestedVisible = mViewVisibility;

        mAttachedToWindow = true;
        if (!mGlobalListenersAdded) {
            ViewTreeObserver observer = getViewTreeObserver();
            observer.addOnScrollChangedListener(mScrollChangedListener);
            observer.addOnPreDrawListener(mDrawListener);
            mGlobalListenersAdded = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttachedToWindow = false;
        if (mGlobalListenersAdded) {
            ViewTreeObserver observer = getViewTreeObserver();
            observer.removeOnScrollChangedListener(mScrollChangedListener);
            observer.removeOnPreDrawListener(mDrawListener);
            mGlobalListenersAdded = false;
        }

        mRequestedVisible = false;

        updateSurface();
        mAlpha = 1f;
        if (mDrawHandler != null) {
            if (getViewRoot() instanceof UIManager.ViewRootImpl viewRoot) {
                viewRoot.addRawDrawHandlerOperation(
                        new MinecraftDrawHandler.Operation(
                                MinecraftDrawHandler.Operation.OP_REMOVE,
                                mDrawHandler
                        )
                );
            }
            mDrawHandler = null;
        }

        mHaveFrame = false;
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        mViewVisibility = visibility == VISIBLE;
        boolean newRequestedVisible = mViewVisibility;
        if (newRequestedVisible != mRequestedVisible) {
            requestLayout();
        }
        mRequestedVisible = newRequestedVisible;
        updateSurface();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateSurface();
    }

    protected void updateSurface() {
        if (!mHaveFrame) {
            return;
        }
        if (!(getViewRoot() instanceof UIManager.ViewRootImpl viewRoot)) {
            return;
        }

        int myWidth = getWidth();
        int myHeight = getHeight();

        final float alpha = getAlpha();
        final boolean visibleChanged = mVisible != mRequestedVisible;
        final boolean alphaChanged = mAlpha != alpha;
        final boolean creating = mDrawHandler == null && mAttachedToWindow;
        final boolean layoutSizeChanged = mSurfaceWidth != myWidth || mSurfaceHeight != myHeight;
        getLocationInWindow(mLocation);
        final boolean positionChanged = mWindowSpaceLeft != mLocation[0]
                || mWindowSpaceTop != mLocation[1];

        if (creating || visibleChanged || alphaChanged ||
                layoutSizeChanged || positionChanged ||
                !mAttachedToWindow) {

            mVisible = mRequestedVisible;
            mWindowSpaceLeft = mLocation[0];
            mWindowSpaceTop = mLocation[1];
            mSurfaceWidth = myWidth;
            mSurfaceHeight = myHeight;
            mAlpha = alpha;

            if (creating) {
                mDrawHandler = new MinecraftDrawHandler(mThisWeakRef);
                viewRoot.addRawDrawHandlerOperation(
                        new MinecraftDrawHandler.Operation(
                                MinecraftDrawHandler.Operation.OP_ADD,
                                mDrawHandler
                        )
                );
            } else if (mDrawHandler == null) {
                return;
            }

            MinecraftDrawHandler.Properties p = mDrawHandler.mStagingProperties;
            p.mHidden = !mVisible;
            p.mAlpha = mAlpha;
            p.mPositionLeft = mWindowSpaceLeft;
            p.mPositionTop = mWindowSpaceTop;
            p.mSurfaceWidth = mSurfaceWidth;
            p.mSurfaceHeight = mSurfaceHeight;

            viewRoot.addRawDrawHandlerOperation(
                    new MinecraftDrawHandler.Operation(
                            MinecraftDrawHandler.Operation.OP_UPDATE,
                            mDrawHandler
                    )
            );
        }
    }
}
