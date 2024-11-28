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

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.*;
import icyllis.arc3d.granite.*;
import icyllis.arc3d.opengl.*;
import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.*;
import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.core.*;
import icyllis.modernui.fragment.*;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.text.LayoutCache;
import icyllis.modernui.lifecycle.*;
import icyllis.modernui.mc.text.GlyphManager;
import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.text.*;
import icyllis.modernui.view.*;
import icyllis.modernui.view.menu.ContextMenuBuilder;
import icyllis.modernui.view.menu.MenuHelper;
import icyllis.modernui.widget.EditText;
import net.minecraft.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.*;

import static icyllis.modernui.ModernUI.LOGGER;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Manage UI thread and connect Minecraft to Modern UI view system at most bottom level.
 * This class is public only for some hooking methods.
 */
@ApiStatus.Internal
public abstract class UIManager implements LifecycleOwner {

    // the logger marker
    protected static final Marker MARKER = MarkerManager.getMarker("UIManager");

    // configs
    public static volatile boolean sDingEnabled;
    public static volatile boolean sZoomEnabled;

    // the global instance, lazily init
    protected static volatile UIManager sInstance;

    protected static final int fragment_container = 0x01020007;

    private static final Field SURFACE_DEVICE;

    static {
        try {
            SURFACE_DEVICE = GraniteSurface.class.getDeclaredField("mDevice");
            SURFACE_DEVICE.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    // minecraft
    protected final Minecraft minecraft = Minecraft.getInstance();

    // minecraft window
    protected final Window mWindow = minecraft.getWindow();

    private final PoseStack mEmptyPoseStack = new PoseStack();

    // the UI thread
    private final Thread mUiThread;
    private volatile Looper mLooper;
    private volatile boolean mRunning;

    // the view root impl
    protected volatile ViewRootImpl mRoot;

    // the top-level view of the window
    protected WindowGroup mDecor;
    private FragmentContainerView mFragmentContainerView;

    private volatile boolean mDebugLayout = false;

    protected static final boolean DEBUG = false;

    private boolean mTestChars = false;
    private int mTestCodepoint = 0x4E00;


    /// Task Handling \\\

    // elapsed time from a screen open in milliseconds, Render thread
    protected long mElapsedTimeMillis;

    // time for drawing, Render thread
    protected long mFrameTimeNanos;


    /// Rendering \\\

    private final Matrix4 mProjectionMatrix = new Matrix4();
    protected boolean mNoRender = false;
    protected boolean mClearNextMainTarget = false;
    protected boolean mAlwaysClearMainTarget = false;
    private long mLastPurgeNanos;

    protected final TooltipRenderer mTooltipRenderer = new TooltipRenderer();


    /// User Interface \\\

    // indicates the current Modern UI screen, updated on main thread
    @Nullable
    protected volatile MuiScreen mScreen;

    //protected boolean mFirstScreenOpened = false;
    protected boolean mZoomMode = false;
    protected boolean mZoomSmoothCamera;


    /// Lifecycle \\\

    protected LifecycleRegistry mFragmentLifecycleRegistry;
    private final OnBackPressedDispatcher mOnBackPressedDispatcher =
            new OnBackPressedDispatcher(() -> minecraft.schedule(this::onBackPressed));

    private ViewModelStore mViewModelStore;
    protected volatile FragmentController mFragmentController;


    /// Input Event \\\

    protected int mButtonState;

    private final StringBuilder mCharInputBuffer = new StringBuilder();
    private final Runnable mCommitCharInput = this::commitCharInput;

    protected UIManager() {
        //MuiModApi.addOnScrollListener(this::onScroll);
        MuiModApi.addOnScreenChangeListener(this::onScreenChange);
        MuiModApi.addOnWindowResizeListener((width, height, guiScale, oldGuiScale) -> resize(width, height));
        MuiModApi.addOnPreKeyInputListener((window, keyCode, scanCode, action, mods) -> {
            if (window == minecraft.getWindow().getWindow()) {
                onPreKeyInput(keyCode, scanCode, action, mods);
            }
        });

        mUiThread = new Thread(this::run, "UI thread");
        mUiThread.start();
        // integrated with Minecraft
        AudioManager.getInstance().initialize(/*integrated*/ true);

        mRunning = true;
    }

    @RenderThread
    public static void initializeRenderer() {
        Core.checkRenderThread();
        if (ModernUIMod.isDeveloperMode()) {
            Core.glSetupDebugCallback();
        }
        Objects.requireNonNull(sInstance);
        Core.requireImmediateContext();
        BufferUploader.invalidate();
        LOGGER.info(MARKER, "UI renderer initialized");
    }

    @Nonnull
    public static UIManager getInstance() {
        // Do not push into stack, since it's lazily init
        if (sInstance == null)
            throw new IllegalStateException("UI manager was never initialized. " +
                    "Please check whether mod loader threw an exception before.");
        return sInstance;
    }

    @UiThread
    private void run() {
        try {
            init();
        } catch (Throwable e) {
            LOGGER.fatal(MARKER, "UI manager failed to initialize", e);
            return;
        }
        while (mRunning) {
            try {
                Looper.loop();
            } catch (Throwable e) {
                LOGGER.error(MARKER, "An error occurred on UI thread", e);
                // dev can add breakpoints
                if (mRunning && ModernUIMod.isDeveloperMode()) {
                    continue;
                } else {
                    minecraft.schedule(this::dump);
                    minecraft.delayCrashRaw(CrashReport.forThrowable(e, "Exception on UI thread"));
                }
            }
            break;
        }
        mRoot.mSurface = RefCnt.move(mRoot.mSurface);
        Core.requireUiRecordingContext().unref();
        LOGGER.debug(MARKER, "Quited UI thread");
    }

    /**
     * Schedule UI and create views.
     *
     * @param fragment the main fragment
     */
    @MainThread
    protected abstract void open(@Nonnull Fragment fragment);

    @MainThread
    void onBackPressed() {
        final MuiScreen screen = mScreen;
        if (screen == null)
            return;
        if (screen.getCallback() != null && !screen.getCallback().shouldClose()) {
            return;
        }
        if (screen.isMenuScreen()) {
            if (minecraft.player != null) {
                minecraft.player.closeContainer();
            }
        } else {
            minecraft.setScreen(screen.getPreviousScreen());
        }
    }

    /**
     * Get elapsed time in UI, update every frame. Internal use only.
     *
     * @return drawing time in milliseconds
     */
    static long getElapsedTime() {
        if (sInstance == null) {
            return Core.timeMillis();
        }
        return sInstance.mElapsedTimeMillis;
    }

    /**
     * Get synced frame time, update every frame
     *
     * @return frame time in nanoseconds
     */
    static long getFrameTimeNanos() {
        if (sInstance == null) {
            return Core.timeNanos();
        }
        return sInstance.mFrameTimeNanos;
    }

    public WindowGroup getDecorView() {
        return mDecor;
    }

    public FragmentController getFragmentController() {
        return mFragmentController;
    }

    public void setShowingLayoutBounds(boolean debugLayout) {
        mDebugLayout = debugLayout;
        mRoot.loadSystemProperties(() -> mDebugLayout);
    }

    public boolean isShowingLayoutBounds() {
        return mDebugLayout;
    }

    public OnBackPressedDispatcher getOnBackPressedDispatcher() {
        return mOnBackPressedDispatcher;
    }

    @Nonnull
    @Override
    public Lifecycle getLifecycle() {
        // STRONG reference "this"
        return mFragmentLifecycleRegistry;
    }

    // Called when open a screen from Modern UI, or back to the screen
    @MainThread
    public void initScreen(@Nonnull MuiScreen screen) {
        if (mScreen != screen) {
            if (mScreen != null) {
                LOGGER.warn(MARKER, "You cannot set multiple screens.");
                return;
            }
            mRoot.mHandler.post(this::suppressLayoutTransition);
            mFragmentController.getFragmentManager().beginTransaction()
                    .add(fragment_container, screen.getFragment(), "main")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .setReorderingAllowed(true)
                    .commit();
            mRoot.mHandler.post(this::restoreLayoutTransition);
        }
        mScreen = screen;
        // ensure it's resized
        resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
    }

    @UiThread
    void suppressLayoutTransition() {
        LayoutTransition transition = mDecor.getLayoutTransition();
        transition.disableTransitionType(LayoutTransition.APPEARING);
        transition.disableTransitionType(LayoutTransition.DISAPPEARING);
    }

    @UiThread
    void restoreLayoutTransition() {
        LayoutTransition transition = mDecor.getLayoutTransition();
        transition.enableTransitionType(LayoutTransition.APPEARING);
        transition.enableTransitionType(LayoutTransition.DISAPPEARING);
    }

    protected void onScreenChange(@Nullable Screen oldScreen, @Nullable Screen newScreen) {
        BlurHandler.INSTANCE.blur(newScreen);
        /*if (newScreen == null) {
            removed();
        }*/
    }

    @UiThread
    private void init() {
        long startTime = System.nanoTime();
        mLooper = Core.initUiThread();

        mRoot = this.new ViewRootImpl();

        mDecor = new WindowGroup(ModernUI.getInstance());
        mDecor.setWillNotDraw(true);
        mDecor.setId(R.id.content);
        updateLayoutDir(false);

        mFragmentContainerView = new FragmentContainerView(ModernUI.getInstance());
        mFragmentContainerView.setLayoutParams(new WindowManager.LayoutParams());
        mFragmentContainerView.setWillNotDraw(true);
        mFragmentContainerView.setId(fragment_container);
        mDecor.addView(mFragmentContainerView);

        mDecor.setLayoutTransition(new LayoutTransition());

        mRoot.setView(mDecor);
        resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());

        mDecor.getViewTreeObserver().addOnScrollChangedListener(() -> onHoverMove(false));

        mFragmentLifecycleRegistry = new LifecycleRegistry(this);
        mViewModelStore = new ViewModelStore();
        mFragmentController = FragmentController.createController(this.new HostCallbacks());

        mFragmentController.attachHost(null);

        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mFragmentController.dispatchCreate();

        mFragmentController.dispatchActivityCreated();
        mFragmentController.execPendingActions();

        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        mFragmentController.dispatchStart();

        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        mFragmentController.dispatchResume();

        LOGGER.info(MARKER, "UI thread initialized in {}ms", (System.nanoTime() - startTime) / 1000000);

        // test stuff
        /*Paint paint = Paint.take();
        paint.setStrokeWidth(6);
        int c = (int) mElapsedTimeMillis / 300;
        c = Math.min(c, 8);
        float[] pts = new float[c * 2 + 2];
        pts[0] = 90;
        pts[1] = 30;
        for (int i = 0; i < c; i++) {
            pts[2 + i * 2] = Math.min((i + 2) * 60, mElapsedTimeMillis / 5) + 30;
            if ((i & 1) == 0) {
                if (mElapsedTimeMillis >= (i + 2) * 300) {
                    pts[3 + i * 2] = 90;
                } else {
                    pts[3 + i * 2] = 30 + (mElapsedTimeMillis % 300) / 5f;
                }
            } else {
                if (mElapsedTimeMillis >= (i + 2) * 300) {
                    pts[3 + i * 2] = 30;
                } else {
                    pts[3 + i * 2] = 90 - (mElapsedTimeMillis % 300) / 5f;
                }
            }
        }
        mCanvas.drawStripLines(pts, paint);

        paint.setRGBA(255, 180, 100, 255);
        mCanvas.drawCircle(90, 30, 6, paint);
        mCanvas.drawCircle(150, 90, 6, paint);
        mCanvas.drawCircle(210, 30, 6, paint);
        mCanvas.drawCircle(270, 90, 6, paint);
        mCanvas.drawCircle(330, 30, 6, paint);
        mCanvas.drawCircle(390, 90, 6, paint);
        mCanvas.drawCircle(450, 30, 6, paint);
        mCanvas.drawCircle(510, 90, 6, paint);
        mCanvas.drawCircle(570, 30, 6, paint);*/
    }

    @UiThread
    private void finish() {
        LOGGER.debug(MARKER, "Quiting UI thread");

        mFragmentController.dispatchStop();
        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);

        mFragmentController.dispatchDestroy();
        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        // must delay, some messages are not enqueued
        // currently it is a bit longer than a game tick
        mRoot.mHandler.postDelayed(mLooper::quitSafely, 60);
    }

    /**
     * From screen
     *
     * @param natural natural or synthetic
     * @see org.lwjgl.glfw.GLFWCursorPosCallbackI
     * @see net.minecraft.client.MouseHandler
     * @see MuiScreen
     */
    @MainThread
    public void onHoverMove(boolean natural) {
        final long now = Core.timeNanos();
        float x = (float) (minecraft.mouseHandler.xpos() *
                mWindow.getWidth() / mWindow.getScreenWidth());
        float y = (float) (minecraft.mouseHandler.ypos() *
                mWindow.getHeight() / mWindow.getScreenHeight());
        MotionEvent event = MotionEvent.obtain(now, MotionEvent.ACTION_HOVER_MOVE,
                x, y, 0);
        mRoot.enqueueInputEvent(event);
        //mPendingRepostCursorEvent = false;
        if (natural && mButtonState > 0) {
            event = MotionEvent.obtain(now, MotionEvent.ACTION_MOVE, 0, x, y, 0, mButtonState, 0);
            mRoot.enqueueInputEvent(event);
        }
    }

    // Hook method, DO NOT CALL
    public void onScroll(double scrollX, double scrollY) {
        if (mScreen != null) {
            final long now = Core.timeNanos();
            final Window window = mWindow;
            final MouseHandler mouseHandler = minecraft.mouseHandler;
            float x = (float) (mouseHandler.xpos() *
                    window.getWidth() / window.getScreenWidth());
            float y = (float) (mouseHandler.ypos() *
                    window.getHeight() / window.getScreenHeight());
            int mods = 0;
            if (Screen.hasControlDown()) {
                mods |= KeyEvent.META_CTRL_ON;
            }
            if (Screen.hasShiftDown()) {
                mods |= KeyEvent.META_SHIFT_ON;
            }
            MotionEvent event = MotionEvent.obtain(now, MotionEvent.ACTION_SCROLL,
                    x, y, mods);
            event.setAxisValue(MotionEvent.AXIS_HSCROLL, (float) scrollX);
            event.setAxisValue(MotionEvent.AXIS_VSCROLL, (float) scrollY);
            mRoot.enqueueInputEvent(event);
        }
    }

    public void onPostMouseInput(int button, int action, int mods) {
        // We should ensure (overlay == null && screen != null)
        // and the screen must be a mui screen
        if (minecraft.getOverlay() == null && mScreen != null) {
            //ModernUI.LOGGER.info(MARKER, "Button: {} {} {}", event.getButton(), event.getAction(), event.getMods());
            final long now = Core.timeNanos();
            float x = (float) (minecraft.mouseHandler.xpos() *
                    mWindow.getWidth() / mWindow.getScreenWidth());
            float y = (float) (minecraft.mouseHandler.ypos() *
                    mWindow.getHeight() / mWindow.getScreenHeight());
            int buttonState = 0;
            for (int i = 0; i < 5; i++) {
                if (glfwGetMouseButton(mWindow.getWindow(), i) == GLFW_PRESS) {
                    buttonState |= 1 << i;
                }
            }
            mButtonState = buttonState;
            int hoverAction = action == GLFW_PRESS ?
                    MotionEvent.ACTION_BUTTON_PRESS : MotionEvent.ACTION_BUTTON_RELEASE;
            int touchAction = action == GLFW_PRESS ?
                    MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
            int actionButton = 1 << button;
            MotionEvent ev = MotionEvent.obtain(now, hoverAction, actionButton,
                    x, y, mods, buttonState, 0);
            mRoot.enqueueInputEvent(ev);
            if ((touchAction == MotionEvent.ACTION_DOWN && (buttonState ^ actionButton) == 0)
                    || (touchAction == MotionEvent.ACTION_UP && buttonState == 0)) {
                ev = MotionEvent.obtain(now, touchAction, actionButton,
                        x, y, mods, buttonState, 0);
                mRoot.enqueueInputEvent(ev);
                //LOGGER.info("Enqueue mouse event: {}", ev);
            }
        }
    }

    public void onKeyPress(int keyCode, int scanCode, int mods) {
        KeyEvent keyEvent = KeyEvent.obtain(Core.timeNanos(), KeyEvent.ACTION_DOWN, keyCode, 0,
                mods, scanCode, 0);
        mRoot.enqueueInputEvent(keyEvent);
    }

    public void onKeyRelease(int keyCode, int scanCode, int mods) {
        KeyEvent keyEvent = KeyEvent.obtain(Core.timeNanos(), KeyEvent.ACTION_UP, keyCode, 0,
                mods, scanCode, 0);
        mRoot.enqueueInputEvent(keyEvent);
    }

    protected void onPreKeyInput(int keyCode, int scanCode, int action, int mods) {
        if (TooltipRenderer.sTooltip) {
            if (mods == 0 && action != GLFW_RELEASE) {
                switch (keyCode) {
                    case GLFW_KEY_UP -> mTooltipRenderer.updateArrowMovement(-1);
                    case GLFW_KEY_DOWN -> mTooltipRenderer.updateArrowMovement(1);
                }
            }
        }
        if (!Screen.hasControlDown() || !Screen.hasShiftDown() || !ModernUIMod.isDeveloperMode()) {
            return;
        }
        if (action == GLFW_PRESS) {
            switch (keyCode) {
                case GLFW_KEY_Y -> takeScreenshot();
                //case GLFW_KEY_H -> open(new TestFragment());
                //case GLFW_KEY_J -> open(new TestPauseFragment());
                case GLFW_KEY_U -> {
                    mClearNextMainTarget = true;
                }
                case GLFW_KEY_I -> {
                    mTestChars ^= true;
                }
                case GLFW_KEY_N -> mDecor.postInvalidate();
                case GLFW_KEY_P -> dump();
                case GLFW_KEY_M -> changeRadialBlur();
                case GLFW_KEY_T -> {
                    /*String text = "\u09b9\u09cd\u09af\u09be\n\u09b2\u09cb" + ChatFormatting.RED + "\uD83E\uDD14" +
                            ChatFormatting.BOLD + "\uD83E\uDD14\uD83E\uDD14";
                    for (int i = 1; i <= 10; i++) {
                        float width = i * 5;
                        int index = ModernStringSplitter.breakText(text, width, Style.EMPTY, true);
                        LOGGER.info("Break forwards: width {} index:{}", width, index);
                        index = ModernStringSplitter.breakText(text, width, Style.EMPTY, false);
                        LOGGER.info("Break backwards: width {} index:{}", width, index);
                    }
                    LOGGER.info(TextLayoutEngine.getInstance().lookupVanillaLayout(text));*/
                    if (ModernUIMod.isTextEngineEnabled()) {
                        TextLayoutEngine.getInstance().dumpLayoutCache();
                    }
                }
                case GLFW_KEY_G ->
                /*if (minecraft.screen == null && minecraft.isLocalServer() &&
                        minecraft.getSingleplayerServer() != null && !minecraft.getSingleplayerServer().isPublished()) {
                    start(new TestPauseUI());
                }*/
                /*minecraft.getLanguageManager().getLanguages().forEach(l ->
                        ModernUI.LOGGER.info(MARKER, "Locale {} RTL {}", l.getCode(), ULocale.forLocale(l
                        .getJavaLocale()).isRightToLeft()));*/
                        GlyphManager.getInstance().debug();
                case GLFW_KEY_V -> {
                    if (ModernUIMod.isTextEngineEnabled()) {
                        //TextLayoutEngine.getInstance().dumpEmojiAtlas();
                        TextLayoutEngine.getInstance().dumpBitmapFonts();
                    }
                }
                case GLFW_KEY_O -> mNoRender = !mNoRender;
                case GLFW_KEY_F -> System.gc();
            }
        }
    }

    public void onGameLoadFinished() {
        if (sDingEnabled) {
            glfwRequestWindowAttention(minecraft.getWindow().getWindow());
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f)
            );
        }
        if (ModernUIMod.isOptiFineLoaded() &&
                ModernUIMod.isTextEngineEnabled()) {
            OptiFineIntegration.setFastRender(false);
            LOGGER.info(MARKER, "Disabled OptiFine Fast Render");
        }
    }

    @SuppressWarnings("resource")
    public void takeScreenshot() {
        @SharedPtr
        Surface surface = mRoot.getSurface();
        if (surface == null) {
            return;
        }
        @RawPtr
        GLTexture layer;
        try {
            @RawPtr
            GraniteDevice device = (GraniteDevice) SURFACE_DEVICE.get(surface);
            layer = (GLTexture) device.getReadView().getImage();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        final int width = layer.getWidth();
        final int height = layer.getHeight();
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Format.RGBA_8888);
        bitmap.setPremultiplied(true);
        GL33C.glPixelStorei(GL33C.GL_PACK_ROW_LENGTH, 0);
        GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_ROWS, 0);
        GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_PIXELS, 0);
        GL33C.glPixelStorei(GL33C.GL_PACK_ALIGNMENT, 1);
        // SYNC GPU TODO (use transfer buffer?)
        GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
        int boundTexture = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, layer.getHandle());
        GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE,
                bitmap.getAddress());
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, boundTexture);
        surface.unref();
        Util.ioPool().execute(() -> {
            Bitmap converted = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Format.RGBA_8888);
            converted.setPremultiplied(false);
            try (bitmap) {
                // unpremul and flip
                PixelUtils.convertPixels(bitmap.getPixmap(), converted.getPixmap(), true);
            }
            try (converted) {
                converted.saveDialog(Bitmap.SaveFormat.PNG, 0, null);
            } catch (IOException e) {
                LOGGER.warn(MARKER, "Failed to save UI screenshot", e);
            }
        });
    }

    /*@SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    static void unpremulAlpha(Bitmap bitmap) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int rowStride = bitmap.getRowStride();
        long addr = bitmap.getAddress();
        final boolean big = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                long base = addr + (j << 2);
                int col = MemoryUtil.memGetInt(base);
                if (big) {
                    col = Integer.reverseBytes(col);
                }
                int alpha = col >>> 24;
                if (alpha != 0) {
                    float a = alpha / 255.0f;
                    int r = MathUtil.clamp((int) ((col & 0xFF) / a + 0.5f), 0, 0xFF);
                    int g = MathUtil.clamp((int) (((col >> 8) & 0xFF) / a + 0.5f), 0, 0xFF);
                    int b = MathUtil.clamp((int) (((col >> 16) & 0xFF) / a + 0.5f), 0, 0xFF);
                    col = (r) | (g << 8) | (b << 16) | (col & 0xFF000000);
                    if (big) {
                        col = Integer.reverseBytes(col);
                    }
                    MemoryUtil.memPutInt(base, col);
                }
            }
            addr += rowStride;
        }
    }*/

    protected void changeRadialBlur() {
        if (minecraft.gameRenderer.currentPostEffect() == null) {
            LOGGER.info(MARKER, "Load post-processing effect");
            final ResourceLocation effect;
            if (InputConstants.isKeyDown(mWindow.getWindow(), GLFW_KEY_RIGHT_SHIFT)) {
                effect = ModernUIMod.location("grayscale");
            } else {
                effect = ModernUIMod.location("radial_blur");
            }
            MuiModApi.get().loadEffect(minecraft.gameRenderer, effect);
        } else {
            LOGGER.info(MARKER, "Stop post-processing effect");
            minecraft.gameRenderer.clearPostEffect();
        }
    }

    public void dump() {
        StringBuilder builder = new StringBuilder();
        try (var w = new PrintWriter(new StringBuilderWriter(builder))) {
            dump(w, true);
        }
        String str = builder.toString();
        if (minecraft.level != null) {
            /*try {
                SEND_TO_CHAT.invoke(minecraft.gui.getChat(), ,
                        0xCBD366, minecraft.gui.getGuiTicks(), false);

            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }*/
            minecraft.gui.getChat().addMessage(Component.literal(str).withStyle(ChatFormatting.GRAY));
        }
        LOGGER.info(MARKER, str);
    }

    public void dump(@Nonnull PrintWriter pw, boolean fragments) {
        pw.println(">>> Modern UI dump data <<<");

        pw.print("Container Menu: ");
        LocalPlayer player = minecraft.player;
        AbstractContainerMenu menu = null;
        if (player != null) {
            menu = player.containerMenu;
        }
        if (menu != null) {
            pw.println(menu.getClass().getSimpleName());
            try {
                ResourceLocation name = BuiltInRegistries.MENU.getKey(menu.getType());
                pw.print("  Registry Name: ");
                pw.println(name);
            } catch (Exception ignored) {
            }
        } else {
            pw.println((Object) null);
        }

        Screen screen = minecraft.screen;
        if (screen != null) {
            pw.print("Screen: ");
            pw.println(screen.getClass());
        }

        if (fragments && mFragmentController != null) {
            mFragmentController.getFragmentManager().dump("", null, pw);
        }

        {
            int coreN = LayoutCache.getSize();
            int coreMem = LayoutCache.getMemoryUsage();
            pw.printf("LayoutCore: Count=%d, Size=%s (%d bytes)\n",
                    coreN, TextUtils.binaryCompact(coreMem), coreMem);
        }

        GlyphManager.getInstance().dumpInfo(pw);

        MuiModApi.dispatchOnDebugDump(pw);
    }

    @MainThread
    public boolean onCharTyped(char ch) {
        /*if (popup != null) {
            return popup.charTyped(codePoint, modifiers);
        }*/
        /*if (mKeyboard != null) {
            return mKeyboard.onCharTyped(codePoint, modifiers);
        }*/
        // block NUL and DEL character
        if (ch == '\0' || ch == '\u007F') {
            return false;
        }
        mCharInputBuffer.append(ch);
        Core.postOnMainThread(mCommitCharInput);
        return true;//root.charTyped(codePoint, modifiers);
    }

    private void commitCharInput() {
        if (mCharInputBuffer.isEmpty()) {
            return;
        }
        final String input = mCharInputBuffer.toString();
        mCharInputBuffer.setLength(0);
        Message msg = Message.obtain(mRoot.mHandler, () -> {
            if (mDecor.findFocus() instanceof EditText text) {
                final Editable content = text.getText();
                int selStart = text.getSelectionStart();
                int selEnd = text.getSelectionEnd();
                if (selStart >= 0 && selEnd >= 0) {
                    Selection.setSelection(content, Math.max(selStart, selEnd));
                    content.replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd), input);
                }
            }
        });
        msg.setAsynchronous(true);
        msg.sendToTarget();
    }

    @RenderThread
    public void render(@Nullable GuiGraphics gr, int mouseX, int mouseY, float deltaTick) {
        if (mNoRender) {
            /*if (mScreen != null) {
                String error = Language.getInstance().getOrDefault("error.modernui.gl_caps");
                int x = (mWindow.getGuiScaledWidth() - minecraft.font.width(error)) / 2;
                int y = (mWindow.getGuiScaledHeight() - 8) / 2;
                minecraft.font.draw(mEmptyPoseStack, error, x, y, 0xFFFF0000);
            }*/
            return;
        }

        int oldVertexArray = 0;
        int oldProgram = 0;


        @RawPtr
        ImmediateContext context = Core.requireImmediateContext();

        var frameTask = mRoot.swapFrameTask();
        @SharedPtr
        RootTask rootTask = frameTask.getLeft();
        @SharedPtr
        Surface surface = frameTask.getRight();

        if (rootTask != null) {
            oldVertexArray = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING);
            oldProgram = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
            boolean added = context.addTask(rootTask);
            rootTask.unref();
            if (!added) {
                LOGGER.error("Failed to add draw commands");
            }
        }

        ((GLDevice) context.getDevice()).flushRenderCalls();

        if (rootTask != null) {
            context.submit();
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, minecraft.getMainRenderTarget().frameBufferId);
            GL33C.glBindVertexArray(oldVertexArray);
            GL33C.glUseProgram(oldProgram);
        } else {
            context.checkForFinishedWork();
        }

        BufferUploader.invalidate();

        // force changing Blaze3D state
        for (int i = 0; i <= 3; i++) {
            GL33C.glBindSampler(i, 0);
        }
        GL33C.glDisable(GL33C.GL_STENCIL_TEST);
        RenderSystem.disableScissor();
        GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
        RenderSystem.defaultBlendFunc();
        GL33C.glBlendFuncSeparate(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA, GL33C.GL_ONE, GL33C.GL_ZERO);
        RenderSystem.enableBlend();
        GL33C.glEnable(GL33C.GL_BLEND);
        RenderSystem.blendEquation(GL33C.GL_FUNC_ADD);
        GL33C.glBlendEquation(GL33C.GL_FUNC_ADD);
        RenderSystem.disableDepthTest();
        GL33C.glDisable(GL33C.GL_DEPTH_TEST);
        RenderSystem.depthFunc(GL33C.GL_LEQUAL);
        GL33C.glDepthFunc(GL33C.GL_LEQUAL);
        RenderSystem.depthMask(true);
        GL33C.glDepthMask(true);
        for (int i = 3; i >= 0; i--) {
            RenderSystem.activeTexture(GL33C.GL_TEXTURE0 + i);
            RenderSystem.bindTexture(0);
        }
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
        RenderSystem.disableCull();
        RenderSystem.lineWidth(1);

        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();

        RenderSystem.viewport(0, 0, width, height);

        if (surface != null) {
            @RawPtr
            GLTexture layer;
            try {
                @RawPtr
                GraniteDevice device = (GraniteDevice) SURFACE_DEVICE.get(surface);
                layer = (GLTexture) device.getReadView().getImage();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            if (layer != null) {
                // draw off-screen target to Minecraft mainTarget (not the default framebuffer)
                CompiledShaderProgram blitShader = RenderSystem.setShader(CoreShaders.BLIT_SCREEN);
                Objects.requireNonNull(blitShader, "Blit shader not loaded");
                blitShader.bindSampler("InSampler", layer.getHandle());
                // using the nearest sampler is performant
                @SharedPtr
                GLSampler sampler = (GLSampler) context.getResourceProvider()
                        .findOrCreateCompatibleSampler(SamplerDesc.NEAREST);
                if (sampler != null) {
                    // XXX: we assume the binding unit is 0 since 'InSampler' is the only sampler
                    GL33C.glBindSampler(0, sampler.getHandle());
                }
                // override blend with src over
                RenderSystem.blendFuncSeparate(GL33C.GL_ONE, GL33C.GL_ONE_MINUS_SRC_ALPHA,
                        GL33C.GL_ONE, GL33C.GL_ONE_MINUS_SRC_ALPHA);
                BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator()
                        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
                bufferBuilder.addVertex(0.0F, 0.0F, 0.0F);
                bufferBuilder.addVertex(1.0F, 0.0F, 0.0F);
                bufferBuilder.addVertex(1.0F, 1.0F, 0.0F);
                bufferBuilder.addVertex(0.0F, 1.0F, 0.0F);
                BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
                if (sampler != null) {
                    GL33C.glBindSampler(0, 0);
                    sampler.unref();
                }
            }
        }
        RenderSystem.defaultBlendFunc();
        RefCnt.move(surface);

        if (gr != null) {
            for (var handler : mRoot.mRawDrawHandlers) {
                handler.render(gr, mouseX, mouseY, deltaTick, mWindow);
            }
        }
    }

    /**
     * Called when game window size changed, used to re-layout the window.
     */
    @MainThread
    void resize(int width, int height) {
        if (mRoot != null) {
            mRoot.mHandler.post(() -> mRoot.setFrame(width, height));
        }
    }

    @UiThread
    public void updateLayoutDir(boolean forceRTL) {
        if (mDecor == null) {
            return;
        }
        boolean layoutRtl = forceRTL ||
                TextUtils.getLayoutDirectionFromLocale(ModernUI.getSelectedLocale()) == View.LAYOUT_DIRECTION_RTL;
        mDecor.setLayoutDirection(layoutRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LOCALE);
        mDecor.requestLayout();
        mTooltipRenderer.mLayoutRTL = layoutRtl;
    }

    @MainThread
    public void removed(@Nonnull Screen target) {
        MuiScreen screen = mScreen;
        if (target != screen) {
            LOGGER.warn(MARKER, "No screen to remove, try to remove {}, but have {}", target, screen);
            return;
        }
        mRoot.mHandler.post(this::suppressLayoutTransition);
        mFragmentController.getFragmentManager().beginTransaction()
                .remove(screen.getFragment())
                .setReorderingAllowed(true)
                .commit();
        mRoot.mHandler.post(this::restoreLayoutTransition);
        mRoot.mRawDrawHandlers.clear();
        mScreen = null;
        glfwSetCursor(mWindow.getWindow(), MemoryUtil.NULL);
    }

    public void drawExtTooltip(ItemStack itemStack,
                               GuiGraphics graphics,
                               List<ClientTooltipComponent> components,
                               int x, int y, Font font,
                               int screenWidth, int screenHeight,
                               ClientTooltipPositioner positioner,
                               ResourceLocation tooltipStyle) {
        // screen coordinates to pixels for rendering
        final Window window = mWindow;
        final MouseHandler mouseHandler = minecraft.mouseHandler;
        final double cursorX = mouseHandler.xpos() *
                (double) window.getGuiScaledWidth() / (double) window.getScreenWidth();
        final double cursorY = mouseHandler.ypos() *
                (double) window.getGuiScaledHeight() / (double) window.getScreenHeight();
        final int mouseX = (int) cursorX;
        final int mouseY = (int) cursorY;
        final float partialX;
        final float partialY;
        if (TooltipRenderer.sExactPositioning &&
                positioner instanceof DefaultTooltipPositioner) {
            positioner = null;
        }
        if (x == mouseX && y == mouseY && positioner == null &&
                isIdentity(graphics.pose().last().pose())) {
            // use our exact pixel positioning
            partialX = (float) (cursorX - mouseX);
            partialY = (float) (cursorY - mouseY);
        } else {
            partialX = 0;
            partialY = 0;
        }

        mTooltipRenderer.drawTooltip(itemStack, graphics,
                components,
                x, y, font,
                screenWidth, screenHeight,
                partialX, partialY, positioner, tooltipStyle);
    }

    private static boolean isIdentity(Matrix4f ctm) {
        if ((ctm.properties() & Matrix4f.PROPERTY_IDENTITY) != 0) {
            return true;
        }
        return MathUtil.isApproxEqual(ctm.m00(), 1) &&
                MathUtil.isApproxZero(ctm.m01()) &&
                MathUtil.isApproxZero(ctm.m02()) &&
                MathUtil.isApproxZero(ctm.m03()) &&
                MathUtil.isApproxZero(ctm.m10()) &&
                MathUtil.isApproxEqual(ctm.m11(), 1) &&
                MathUtil.isApproxZero(ctm.m12()) &&
                MathUtil.isApproxZero(ctm.m13()) &&
                MathUtil.isApproxZero(ctm.m20()) &&
                MathUtil.isApproxZero(ctm.m21()) &&
                MathUtil.isApproxEqual(ctm.m22(), 1) &&
                MathUtil.isApproxZero(ctm.m23()) &&
                MathUtil.isApproxZero(ctm.m30()) &&
                MathUtil.isApproxZero(ctm.m31()) &&
                MathUtil.isApproxZero(ctm.m32()) &&
                MathUtil.isApproxEqual(ctm.m33(), 1);
    }

    protected void onRenderTick(boolean isEnd) {
        if (!isEnd) { // phase=start
            final long lastFrameTime = mFrameTimeNanos;
            mFrameTimeNanos = Core.timeNanos();
            final long deltaMillis = (mFrameTimeNanos - lastFrameTime) / 1000000;
            mElapsedTimeMillis += deltaMillis;
            // coordinates UI thread
            if (mRunning) {
                // update extension animations
                BlurHandler.INSTANCE.onRenderTick(mElapsedTimeMillis);
                if (TooltipRenderer.sTooltip) {
                    mTooltipRenderer.update(deltaMillis, mFrameTimeNanos / 1000000);
                }
                /*if (mClearNextMainTarget || mAlwaysClearMainTarget) {
                    int boundFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING);
                    glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    int error = glGetError();
                    LOGGER.info("Clear main target, boundFramebuffer: {}, mainTarget: {}, error: {}",
                            boundFramebuffer, minecraft.getMainRenderTarget().frameBufferId,
                            Integer.toHexString(error));
                    mClearNextMainTarget = false;
                }*/
            }
        } else {
            // phase=end
            // main thread
            var context = Core.requireImmediateContext();
            if (mFrameTimeNanos - mLastPurgeNanos >= 20_000_000_000L) {
                mLastPurgeNanos = mFrameTimeNanos;
                context.performDeferredCleanup(120_000);
                GlyphManager.getInstance().compact();
            }
            if (!minecraft.isRunning() && mRunning) {
                mRunning = false;
                mRoot.mHandler.post(this::finish);
                // later destroy() will be called
            } else if (minecraft.isRunning() && mRunning &&
                    mScreen == null && minecraft.getOverlay() == null) {
                // Render the UI above everything
                render(null, 0, 0, 0);
            }
        }
        /* else {
            // layout after updating animations and before drawing
            if (mLayoutRequested) {
                // fixed at 40Hz
                if (mElapsedTimeMillis - mLastLayoutTime >= 25) {
                    mLastLayoutTime = mElapsedTimeMillis;
                    doLayout();
                }
            }
        }*/
    }

    protected void onClientTick(boolean isEnd) {
        if (isEnd) {
            BlurHandler.INSTANCE.onClientTick();
            if (DEBUG && mTestChars) {
                int cp = mTestCodepoint;
                if (cp > 0x9FFF)
                    cp = 0x4E00;
                int end = cp + 16;
                StringBuilder sb = new StringBuilder(16);
                while (cp < end) {
                    sb.appendCodePoint(cp++);
                }
                mTestCodepoint = end;
                minecraft.gui.getChat().addMessage(Component.literal(sb.toString()));
            }
        }
    }

    public static void destroy() {
        // see onRenderTick() above
        LOGGER.debug(MARKER, "Quiting Modern UI");
        //BlurHandler.INSTANCE.closeEffect();
        FontResourceManager.getInstance().close();
        ImageStore.getInstance().clear();
        System.gc();
        Core.requireImmediateContext().unref();
        if (sInstance != null) {
            AudioManager.getInstance().close();
            try {
                // in case of GLFW is terminated too early
                sInstance.mUiThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        LOGGER.debug(MARKER, "Quited Modern UI");
    }

    @UiThread
    protected class ViewRootImpl extends ViewRoot {

        private final Rect mGlobalRect = new Rect();

        ContextMenuBuilder mContextMenu;
        MenuHelper mContextMenuHelper;

        Surface mSurface;
        RootTask mLastFrameTask;

        private long mLastPurgeNanos;

        ArrayList<MinecraftDrawHandler.Operation> mPendingRawDrawHandlerOperations = new ArrayList<>();
        ArrayList<MinecraftDrawHandler> mRawDrawHandlers = new ArrayList<>();

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            if (mScreen != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                View v = mDecor.findFocus();
                if (v instanceof EditText) {
                    v.getGlobalVisibleRect(mGlobalRect);
                    if (!mGlobalRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                        v.clearFocus();
                    }
                }
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        protected void onKeyEvent(KeyEvent event) {
            final MuiScreen screen = mScreen;
            if (screen != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                final boolean back;
                if (screen.getCallback() != null) {
                    back = screen.getCallback().isBackKey(event.getKeyCode(), event);
                } else if (screen.isMenuScreen()) {
                    if (event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
                        back = true;
                    } else {
                        InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
                        back = MuiModApi.get().isKeyBindingMatches(minecraft.options.keyInventory, key);
                    }
                } else {
                    back = event.getKeyCode() == KeyEvent.KEY_ESCAPE;
                }
                if (back) {
                    View v = mDecor.findFocus();
                    if (v instanceof EditText) {
                        if (event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
                            v.clearFocus();
                        }
                    } else {
                        mOnBackPressedDispatcher.onBackPressed();
                    }
                }
            }
        }

        @Override
        protected Canvas beginDrawLocked(int width, int height) {
            synchronized (mRenderLock) {
                if (mSurface == null ||
                        mSurface.getWidth() != width ||
                        mSurface.getHeight() != height) {
                    if (width > 0 && height > 0) {
                        mSurface = RefCnt.move(mSurface, GraniteSurface.makeRenderTarget(
                                Core.requireUiRecordingContext(),
                                ImageInfo.make(width, height,
                                        ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL,
                                        ColorSpace.get(ColorSpace.Named.SRGB)),
                                false,
                                Engine.SurfaceOrigin.kLowerLeft,
                                null
                        ));
                    }
                }
                if (mSurface != null && width > 0 && height > 0) {
                    mSurface.getCanvas().clear(0);
                    return new ArcCanvas(mSurface.getCanvas());
                }
                return null;
            }
        }

        @Override
        protected void endDrawLocked(@Nonnull Canvas canvas) {
            canvas.restoreToCount(1);
            RootTask task = Core.requireUiRecordingContext().snap();
            synchronized (mRenderLock) {
                mLastFrameTask = RefCnt.move(mLastFrameTask, task);
                try {
                    mRenderLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                mLastFrameTask = RefCnt.move(mLastFrameTask);
            }
            var context = Core.requireUiRecordingContext();
            if (System.nanoTime() - mLastPurgeNanos >= 20_000_000_000L) {
                mLastPurgeNanos = System.nanoTime();
                context.performDeferredCleanup(120_000);
            }
        }

        @SharedPtr
        private Surface getSurface() {
            synchronized (mRenderLock) {
                return RefCnt.create(mSurface);
            }
        }

        @RenderThread
        private Pair<@SharedPtr RootTask, @SharedPtr Surface> swapFrameTask() {
            @SharedPtr
            RootTask task;
            @SharedPtr
            Surface surface;
            synchronized (mRenderLock) {
                surface = RefCnt.create(mSurface);
                task = mLastFrameTask;
                mLastFrameTask = null;
                for (var operation : mPendingRawDrawHandlerOperations) {
                    switch (operation.mOp) {
                        case MinecraftDrawHandler.Operation.OP_ADD -> mRawDrawHandlers.add(operation.mTarget);
                        case MinecraftDrawHandler.Operation.OP_REMOVE -> mRawDrawHandlers.remove(operation.mTarget);
                        case MinecraftDrawHandler.Operation.OP_UPDATE -> operation.mTarget.syncProperties();
                    }
                }
                mPendingRawDrawHandlerOperations.clear();
                mRenderLock.notifyAll();
            }
            return Pair.of(task, surface);
            /*// wait UI thread, if slow
            synchronized (mRenderLock) {

                if (mPendingDraw) {
                    glEnable(GL_STENCIL_TEST);
                    try {
                        mBlit = canvas.executeRenderPass(surface);
                    } catch (Throwable t) {
                        LOGGER.fatal(MARKER,
                                "Failed to invoke rendering callbacks, please report the issue to related mods", t);
                        dump();
                        throw t;
                    } finally {
                        glDisable(GL_STENCIL_TEST);
                        mPendingDraw = false;
                        mRenderLock.notifyAll();
                    }
                }

                if (mBlit && surface.getBackingWidth() > 0) {
                    GLTexture layer = surface.getAttachedTexture(GL_COLOR_ATTACHMENT0);

                    // draw off-screen target to Minecraft mainTarget (not the default framebuffer)
                    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, minecraft.getMainRenderTarget().frameBufferId);

                    // do alpha fade in
                    //float alpha = (int) Math.min(300, mElapsedTimeMillis) / 300f;
                    canvas.drawLayer(layer, width, height, 1, true);
                    canvas.executeRenderPass(null);
                }
            }*/
        }

        @Override
        public void playSoundEffect(int effectId) {
            /*if (effectId == SoundEffectConstants.CLICK) {
                minecraft.tell(() -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(MuiRegistries
                .BUTTON_CLICK_1, 1.0f)));
            }*/
        }

        @Override
        public boolean performHapticFeedback(int effectId, boolean always) {
            return false;
        }

        @MainThread
        protected void applyPointerIcon(int pointerType) {
            minecraft.schedule(() -> glfwSetCursor(mWindow.getWindow(),
                    PointerIcon.getSystemIcon(pointerType).getHandle()));
        }

        @Override
        public boolean showContextMenuForChild(View originalView, float x, float y) {
            if (mContextMenuHelper != null) {
                mContextMenuHelper.dismiss();
                mContextMenuHelper = null;
            }

            if (mContextMenu == null) {
                mContextMenu = new ContextMenuBuilder(ModernUI.getInstance());
                //mContextMenu.setCallback(callback);
            } else {
                mContextMenu.clearAll();
            }

            final MenuHelper helper;
            final boolean isPopup = !Float.isNaN(x) && !Float.isNaN(y);
            if (isPopup) {
                helper = mContextMenu.showPopup(ModernUI.getInstance(), originalView, x, y);
            } else {
                helper = mContextMenu.showPopup(ModernUI.getInstance(), originalView, 0, 0);
            }

            /*if (helper != null) {
                helper.setPresenterCallback(callback);
            }*/

            mContextMenuHelper = helper;
            return helper != null;
        }
    }

    @UiThread
    protected class HostCallbacks extends FragmentHostCallback<Object> implements
            ViewModelStoreOwner,
            OnBackPressedDispatcherOwner {
        HostCallbacks() {
            super(ModernUI.getInstance(), new Handler(Looper.myLooper()));
            assert Core.isOnUiThread();
        }

        @Nullable
        @Override
        public Object onGetHost() {
            // intentionally null
            return null;
        }

        @Nullable
        @Override
        public View onFindViewById(int id) {
            return mDecor.findViewById(id);
        }

        @Nonnull
        @Override
        public ViewModelStore getViewModelStore() {
            return mViewModelStore;
        }

        @Nonnull
        @Override
        public OnBackPressedDispatcher getOnBackPressedDispatcher() {
            return mOnBackPressedDispatcher;
        }

        @Nonnull
        @Override
        public Lifecycle getLifecycle() {
            return mFragmentLifecycleRegistry;
        }
    }
}
