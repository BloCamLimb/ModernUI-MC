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

package icyllis.modernui.mc;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.Matrix4;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.opengl.GLDevice;
import icyllis.arc3d.opengl.GLTexture;
import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.*;
import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.core.*;
import icyllis.modernui.fragment.*;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.font.GlyphManager;
import icyllis.modernui.graphics.text.LayoutCache;
import icyllis.modernui.lifecycle.*;
import icyllis.modernui.mc.mixin.AccessGameRenderer;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

import static icyllis.arc3d.opengl.GLCore.*;
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
    protected static volatile boolean sDingEnabled;
    protected static volatile boolean sZoomEnabled;

    // the global instance, lazily init
    protected static volatile UIManager sInstance;

    protected static final int fragment_container = 0x01020007;

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


    /// Task Handling \\\

    // elapsed time from a screen open in milliseconds, Render thread
    protected long mElapsedTimeMillis;

    // time for drawing, Render thread
    protected long mFrameTimeNanos;


    /// Rendering \\\

    // the UI framebuffer
    private GLSurface mSurface;
    protected GLSurfaceCanvas mCanvas;
    protected GLDevice mDevice;
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

    protected boolean mFirstScreenOpened = false;
    protected boolean mZoomMode = false;
    protected boolean mZoomSmoothCamera;


    /// Lifecycle \\\

    protected LifecycleRegistry mFragmentLifecycleRegistry;
    private final OnBackPressedDispatcher mOnBackPressedDispatcher =
            new OnBackPressedDispatcher(() -> minecraft.tell(this::onBackPressed));

    private ViewModelStore mViewModelStore;
    protected volatile FragmentController mFragmentController;


    /// Input Event \\\

    protected int mButtonState;

    private final StringBuilder mCharInputBuffer = new StringBuilder();
    private final Runnable mCommitCharInput = this::commitCharInput;

    protected UIManager() {
        //MuiModApi.addOnScrollListener(this::onScroll);
        MuiModApi.addOnScreenChangeListener(this::onScreenChange);
        MuiModApi.addOnWindowResizeListener((width, height, guiScale, oldGuiScale) -> resize());
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
        sInstance.mCanvas = GLSurfaceCanvas.initialize();
        sInstance.mDevice = (GLDevice) Core.requireDirectContext().getDevice();
        sInstance.mDevice.getContext().getResourceCache().setCacheLimit(1 << 28); // 256MB
        sInstance.mSurface = new GLSurface();
        BufferUploader.invalidate();
        LOGGER.info(MARKER, "UI renderer initialized");
    }

    @Nonnull
    public static UIManager getInstance() {
        // Do not push into stack, since it's lazily init
        if (sInstance == null)
            throw new IllegalStateException("UI manager was never initialized. " +
                    "Please check whether FML threw an exception before.");
        return sInstance;
    }

    @UiThread
    private void run() {
        try {
            init();
        } catch (Throwable e) {
            LOGGER.fatal(MARKER, "UI manager failed to initialize");
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
                    minecraft.tell(this::dump);
                    minecraft.tell(() -> Minecraft.crash(
                            CrashReport.forThrowable(e, "Exception on UI thread")));
                }
            }
            break;
        }
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
            minecraft.setScreen(null);
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
                removed();
            }
            mRoot.mHandler.post(this::suppressLayoutTransition);
            mFragmentController.getFragmentManager().beginTransaction()
                    .add(fragment_container, screen.getFragment(), "main")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
            mRoot.mHandler.post(this::restoreLayoutTransition);
        }
        mScreen = screen;
        // ensure it's resized
        resize();
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
        if (newScreen == null) {
            removed();
        }
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
        resize();

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
            MotionEvent event = MotionEvent.obtain(now, MotionEvent.ACTION_SCROLL,
                    x, y, 0);
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
                    if (ModernUIClient.isTextEngineEnabled()) {
                        //TextLayoutEngine.getInstance().dumpEmojiAtlas();
                        TextLayoutEngine.getInstance().dumpBitmapFonts();
                    }
                }
                case GLFW_KEY_O -> mNoRender = !mNoRender;
                case GLFW_KEY_F -> System.gc();
            }
        }
    }

    @SuppressWarnings("resource")
    protected void takeScreenshot() {
        mSurface.bindRead();
        final int width = mSurface.getBackingWidth();
        final int height = mSurface.getBackingHeight();
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Format.RGBA_8888);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        // SYNC GPU TODO (use transfer buffer?)
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, bitmap.getAddress());
        Util.ioPool().execute(() -> {
            try (bitmap) {
                Bitmap.flipVertically(bitmap);
                unpremulAlpha(bitmap);
                bitmap.saveDialog(Bitmap.SaveFormat.PNG, 0, null);
            } catch (IOException e) {
                LOGGER.warn(MARKER, "Failed to save UI screenshot", e);
            }
        });
    }

    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    static void unpremulAlpha(Bitmap bitmap) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int rowStride = bitmap.getRowBytes();
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
    }

    protected void changeRadialBlur() {
        if (minecraft.gameRenderer.currentEffect() == null) {
            LOGGER.info(MARKER, "Load post-processing effect");
            final ResourceLocation effect;
            if (InputConstants.isKeyDown(mWindow.getWindow(), GLFW_KEY_RIGHT_SHIFT)) {
                effect = new ResourceLocation("shaders/post/grayscale.json");
            } else {
                effect = new ResourceLocation("shaders/post/radial_blur.json");
            }
            ((AccessGameRenderer) minecraft.gameRenderer).callLoadEffect(effect);
        } else {
            LOGGER.info(MARKER, "Stop post-processing effect");
            minecraft.gameRenderer.shutdownEffect();
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
    public void render() {
        if (mCanvas == null || mNoRender) {
            /*if (mScreen != null) {
                String error = Language.getInstance().getOrDefault("error.modernui.gl_caps");
                int x = (mWindow.getGuiScaledWidth() - minecraft.font.width(error)) / 2;
                int y = (mWindow.getGuiScaledHeight() - 8) / 2;
                minecraft.font.draw(mEmptyPoseStack, error, x, y, 0xFFFF0000);
            }*/
            return;
        }
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.activeTexture(GL_TEXTURE0);
        RenderSystem.disableDepthTest();
        //glDisable(GL_DEPTH_TEST);

        // Minecraft.mainRenderTarget has no transparency (=== 1)
        // UI layer has a transparent background, with premultiplied alpha
        RenderSystem.blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        final int oldVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);

        int width = mWindow.getWidth();
        int height = mWindow.getHeight();

        mDevice.markContextDirty(Engine.GLBackendState.kPixelStore);
        // TODO need multiple canvas instances, tooltip shares this now, but different thread; remove Z transform
        mCanvas.setProjection(mProjectionMatrix.setOrthographic(
                width, height, 0, icyllis.modernui.core.Window.LAST_SYSTEM_WINDOW * 2 + 1,
                true));
        mRoot.flushDrawCommands(mCanvas, mSurface, width, height);

        var resourceCache = mDevice.getContext().getResourceCache();
        resourceCache.cleanup();
        // 2 min
        if (mFrameTimeNanos - mLastPurgeNanos >= 120_000_000_000L) {
            mLastPurgeNanos = mFrameTimeNanos;
            resourceCache.purgeFreeResourcesOlderThan(
                    System.currentTimeMillis() - 120_000,
                    /*scratch only*/ true
            );
            GlyphManager.getInstance().compact();
        }

        glBindVertexArray(oldVertexArray);
        glUseProgram(oldProgram);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        // force changing Blaze3D state
        RenderSystem.bindTexture(DEFAULT_TEXTURE);
    }

    private final Runnable mResizeRunnable = () -> mRoot.setFrame(mWindow.getWidth(), mWindow.getHeight());

    /**
     * Called when game window size changed, used to re-layout the window.
     */
    @MainThread
    void resize() {
        if (mRoot != null) {
            mRoot.mHandler.post(mResizeRunnable);
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
    public void removed() {
        MuiScreen screen = mScreen;
        if (screen == null) {
            return;
        }
        mRoot.mHandler.post(this::suppressLayoutTransition);
        mFragmentController.getFragmentManager().beginTransaction()
                .remove(screen.getFragment())
                .runOnCommit(() -> mFragmentContainerView.removeAllViews())
                .commit();
        mRoot.mHandler.post(this::restoreLayoutTransition);
        mScreen = null;
        glfwSetCursor(mWindow.getWindow(), MemoryUtil.NULL);
    }

    public void drawExtTooltip(ItemStack itemStack,
                               GuiGraphics graphics,
                               List<ClientTooltipComponent> components,
                               int x, int y, Font font,
                               int screenWidth, int screenHeight,
                               ClientTooltipPositioner positioner) {
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
        if (x == mouseX && y == mouseY && positioner == null) {
            // use our exact pixel positioning
            partialX = (float) (cursorX - mouseX);
            partialY = (float) (cursorY - mouseY);
        } else {
            partialX = 0;
            partialY = 0;
        }

        mRoot.drawExtTooltipLocked(itemStack, graphics,
                components,
                x, y, font,
                screenWidth, screenHeight,
                partialX, partialY, positioner); // need a lock
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
            if (!minecraft.isRunning() && mRunning) {
                mRunning = false;
                mRoot.mHandler.post(this::finish);
                // later destroy() will be called
            } else if (minecraft.isRunning() && mRunning &&
                    mScreen == null && minecraft.getOverlay() == null) {
                // Render the UI above everything
                render();
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
        }
    }

    public static void destroy() {
        // see onRenderTick() above
        LOGGER.debug(MARKER, "Quiting Modern UI");
        if (sInstance != null) {
            if (sInstance.mCanvas != null) {
                sInstance.mCanvas.destroy();
            }
        }
        FontResourceManager.getInstance().close();
        ImageStore.getInstance().clear();
        Core.requireDirectContext().unref();
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

        private volatile boolean mPendingDraw = false;
        private boolean mBlit;

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
                        back = minecraft.options.keyInventory.matches(event.getKeyCode(), event.getScanCode());
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
            if (mCanvas != null) {
                mCanvas.reset(width, height);
            }
            return mCanvas;
        }

        @Override
        protected void endDrawLocked(@Nonnull Canvas canvas) {
            mPendingDraw = true;
            try {
                mRenderLock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @RenderThread
        private void flushDrawCommands(GLSurfaceCanvas canvas, GLSurface surface,
                                       int width, int height) {
            // wait UI thread, if slow
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
            }
        }

        public void drawExtTooltipLocked(@Nonnull ItemStack itemStack, @Nonnull GuiGraphics gr,
                                         @Nonnull List<ClientTooltipComponent> list, int mouseX, int mouseY,
                                         @Nonnull Font font, int screenWidth, int screenHeight,
                                         float partialX, float partialY,
                                         @Nullable ClientTooltipPositioner positioner) {
            if (mCanvas == null) {
                return;
            }
            synchronized (mRenderLock) {
                if (!mPendingDraw) {
                    mTooltipRenderer.drawTooltip(mCanvas, mWindow,
                            itemStack, gr, list,
                            mouseX, mouseY, font,
                            screenWidth, screenHeight,
                            partialX, partialY, positioner);
                }
            }
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
            minecraft.tell(() -> glfwSetCursor(mWindow.getWindow(),
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
