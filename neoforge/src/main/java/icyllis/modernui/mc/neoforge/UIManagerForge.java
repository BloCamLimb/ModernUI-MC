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

package icyllis.modernui.mc.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import icyllis.arc3d.opengl.GLDevice;
import icyllis.modernui.annotation.MainThread;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.lifecycle.LifecycleOwner;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.mixin.AccessNativeImage;
import icyllis.modernui.text.TextUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.*;
import org.lwjgl.opengl.GL45C;

import javax.annotation.Nonnull;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Map;

import static icyllis.modernui.ModernUI.LOGGER;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Manage UI thread and connect Minecraft to Modern UI view system at most bottom level.
 * This class is public only for some hooking methods.
 */
@ApiStatus.Internal
public final class UIManagerForge extends UIManager implements LifecycleOwner {

    @SuppressWarnings("NoTranslation")
    public static final KeyMapping OPEN_CENTER_KEY = new KeyMapping(
            "key.modernui.openCenter", KeyConflictContext.UNIVERSAL, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW_KEY_K, "Modern UI");
    @SuppressWarnings("NoTranslation")
    public static final KeyMapping ZOOM_KEY = new KeyMapping(
            "key.modernui.zoom", KeyConflictContext.IN_GAME, KeyModifier.NONE,
            InputConstants.Type.KEYSYM, GLFW_KEY_C, "Modern UI");

    /*public static final Method SEND_TO_CHAT =
            ObfuscationReflectionHelper.findMethod(ChatComponent.class, "m_93790_",
                    Component.class, int.class, int.class, boolean.class);*/
    public static final Field BY_PATH =
            ObfuscationReflectionHelper.findField(TextureManager.class, "byPath");
    public static final Field TEXTURES_BY_NAME =
            ObfuscationReflectionHelper.findField(TextureAtlas.class, "texturesByName");
    /*public static final Field MAIN_IMAGE =
            ObfuscationReflectionHelper.findField(TextureAtlasSprite.class, "f_118342_");
    public static final Field IMAGE_PIXELS =
            ObfuscationReflectionHelper.findField(NativeImage.class, "f_84964_");*/
    public static final Field TEXTURE_ID =
            ObfuscationReflectionHelper.findField(AbstractTexture.class, "id");

    private UIManagerForge() {
        super();
        // events
        NeoForge.EVENT_BUS.register(this);
    }

    @RenderThread
    static void initialize() {
        Core.checkRenderThread();
        assert sInstance == null;
        sInstance = new UIManagerForge();
        LOGGER.info(MARKER, "UI manager initialized");
    }

    /**
     * Schedule UI and create views.
     *
     * @param fragment the main fragment
     */
    @MainThread
    protected void open(@Nonnull Fragment fragment) {
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("Not called from main thread");
        }
        minecraft.setScreen(new SimpleScreen(this, fragment, null, null, null));
    }

    @Override
    protected void onScreenChange(@Nullable Screen oldScreen, @Nullable Screen newScreen) {
        if (newScreen != null) {
            if (mScreen != newScreen && newScreen instanceof MuiScreen) {
                //mTicks = 0;
                mElapsedTimeMillis = 0;
            }
            if (mScreen != newScreen && mScreen != null) {
                onHoverMove(false);
            }
            // for non-mui screens
            if (mScreen == null && minecraft.screen == null) {
                //mTicks = 0;
                mElapsedTimeMillis = 0;
            }
        }
        super.onScreenChange(oldScreen, newScreen);
    }

    /**
     * @see org.lwjgl.glfw.GLFWMouseButtonCallbackI
     * @see net.minecraft.client.MouseHandler
     * @see net.neoforged.neoforge.client.event.InputEvent
     */
    @SubscribeEvent
    void onPostMouseInput(@Nonnull InputEvent.MouseButton.Post event) {
        super.onPostMouseInput(event.getButton(), event.getAction(), event.getModifiers());
    }

    @Override
    protected void onPreKeyInput(int keyCode, int scanCode, int action, int mods) {
        if (action == GLFW_PRESS) {
            if (minecraft.screen == null ||
                    minecraft.screen.shouldCloseOnEsc() ||
                    minecraft.screen instanceof TitleScreen) {
                InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
                if (OPEN_CENTER_KEY.isActiveAndMatches(key)) {
                    open(new CenterFragment2());
                    return;
                }
            }
        }
        super.onPreKeyInput(keyCode, scanCode, action, mods);
    }

    @Override
    public void onGameLoadFinished() {
        super.onGameLoadFinished();
        // ensure it's applied and positioned
        Config.CLIENT.mLastWindowMode.apply();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void dump(@NotNull PrintWriter pw, boolean fragments) {
        super.dump(pw, fragments);
        Map<ResourceLocation, AbstractTexture> textureMap = null;
        try {
            textureMap = (Map<ResourceLocation, AbstractTexture>) BY_PATH.get(minecraft.getTextureManager());
        } catch (Exception ignored) {
        }
        GLDevice device = (GLDevice) Core.requireImmediateContext().getDevice();
        if (textureMap != null && device.getCaps().hasDSASupport()) {
            long gpuSize = 0;
            long cpuSize = 0;
            int dynamicTextures = 0;
            int textureAtlases = 0;
            int atlasSprites = 0;
            for (var texture : textureMap.values()) {
                try {
                    int tex = TEXTURE_ID.getInt(texture);
                    if (GL45C.glIsTexture(tex)) {
                        int internalFormat = GL45C.glGetTextureLevelParameteri(tex, 0, GL45C.GL_TEXTURE_INTERNAL_FORMAT);
                        long width = GL45C.glGetTextureLevelParameteri(tex, 0, GL45C.GL_TEXTURE_WIDTH);
                        long height = GL45C.glGetTextureLevelParameteri(tex, 0, GL45C.GL_TEXTURE_HEIGHT);
                        int maxLevel = GL45C.glGetTextureParameteri(tex, GL45C.GL_TEXTURE_MAX_LEVEL);
                        int bpp = switch (internalFormat) {
                            case GL45C.GL_R8, GL45C.GL_RED -> 1;
                            case GL45C.GL_RG8, GL45C.GL_RG -> 2;
                            case GL45C.GL_RGB8, GL45C.GL_RGBA8, GL45C.GL_RGB, GL45C.GL_RGBA -> 4;
                            default -> 0;
                        };
                        long size = width * height * bpp;
                        if (maxLevel > 0) {
                            size = ((size - (size >> ((maxLevel + 1) << 1))) << 2) / 3;
                        }
                        gpuSize += size;
                    }
                } catch (Exception ignored) {
                }

                if (texture instanceof DynamicTexture dynamicTexture) {
                    var image = dynamicTexture.getPixels();
                    try {
                        //noinspection ConstantValue,DataFlowIssue
                        if (image != null && ((AccessNativeImage) (Object) image).getPixels() != 0) {
                            cpuSize += (long) image.getWidth() * image.getHeight() * image.format().components();
                        }
                    } catch (Exception ignored) {
                    }
                    dynamicTextures++;
                }
                if (texture instanceof TextureAtlas textureAtlas) {
                    try {
                        Map<ResourceLocation, TextureAtlasSprite> textures =
                                (Map<ResourceLocation, TextureAtlasSprite>) TEXTURES_BY_NAME.get(textureAtlas);
                        /*for (var sprite : textures.values()) {
                            for (var image : (com.mojang.blaze3d.platform.NativeImage[]) MAIN_IMAGE.get(sprite)) {
                                if (image != null && IMAGE_PIXELS.getLong(image) != 0) {
                                    cpuSize += (long) image.getWidth() * image.getHeight() * image.format()
                                    .components();
                                }
                            }
                            atlasSprites++;
                        }*/
                        atlasSprites += textures.size();
                    } catch (Exception ignored) {
                    }
                    textureAtlases++;
                }
            }
            pw.print("Minecraft's TextureManager: ");
            pw.print("Textures=" + textureMap.size());
            pw.print(", DynamicTextures=" + dynamicTextures);
            pw.print(", Atlases=" + textureAtlases);
            pw.print(", Sprites=" + atlasSprites);
            pw.print(", GPUMemory=" + TextUtils.binaryCompact(gpuSize) + " (" + gpuSize + " bytes)");
            pw.println(", CPUMemory=" + TextUtils.binaryCompact(cpuSize) + " (" + cpuSize + " bytes)");
        }
    }

    @SubscribeEvent
    void onRenderGameOverlayLayer(@Nonnull RenderGuiLayerEvent.Pre event) {
        /*switch (event.getType()) {
            case CROSSHAIRS:
                event.setCanceled(mScreen != null);
                break;
            case ALL:
                // hotfix 1.16 vanilla, using shader makes TEXTURE_2D disabled
                RenderSystem.enableTexture();
                break;
            *//*case HEALTH:
                if (TestHUD.sBars)
                    TestHUD.sInstance.drawBars(mFCanvas);
                break;*//*
        }*/
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            if (mScreen != null) {
                event.setCanceled(true);
            }
            /*minecraft.font.draw(event.getMatrixStack(),
                    ChatFormatting.DARK_RED + "Fuck you " + ChatFormatting.UNDERLINE + "OK " + mElapsedTimeMillis +
                     " " + ChatFormatting.OBFUSCATED + "66" + ChatFormatting.RESET + " Fine", 20, 20, 0xFF0000);
            minecraft.font.draw(event.getMatrixStack(),
                    new TextComponent("Yes " + ChatFormatting.DARK_RED + "Fuck " + ChatFormatting.RESET + "That"),
                    20, 60, 0x00FFFF);*/
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    void onRenderTooltipH(@Nonnull RenderTooltipEvent.Pre event) {
        if (TooltipRenderer.sTooltip) {
            /*if (!(minecraft.font instanceof ModernFontRenderer)) {
                ModernUI.LOGGER.fatal(MARKER, "Failed to hook FontRenderer, tooltip disabled");
                TestHUD.sTooltip = false;
                return;
            }*/

            drawExtTooltip(event.getItemStack(), event.getGraphics(),
                    event.getComponents(),
                    event.getX(), event.getY(), event.getFont(),
                    event.getScreenWidth(), event.getScreenHeight(),
                    event.getTooltipPositioner());

            // our tooltip is translucent, need transparency sorting
            // we will cancel this event later, see below
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    void onRenderTooltipL(@Nonnull RenderTooltipEvent.Pre event) {
        if (TooltipRenderer.sTooltip) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    void onRenderFramePre(@Nonnull RenderFrameEvent.Pre event) {
        super.onRenderTick(/*isEnd*/ false);
    }

    @SubscribeEvent
    void onRenderFramePost(@Nonnull RenderFrameEvent.Post event) {
        super.onRenderTick(/*isEnd*/ true);
    }

    @SubscribeEvent
    void onClientTickPre(@Nonnull ClientTickEvent.Pre event) {
        super.onClientTick(/*isEnd*/ false);
    }

    @SubscribeEvent
    void onClientTickPost(@Nonnull ClientTickEvent.Post event) {
        super.onClientTick(/*isEnd*/ true);
    }

    @SubscribeEvent
    void onChangeFov(@Nonnull ViewportEvent.ComputeFov event) {
        boolean zoomActive = false;
        if (sZoomEnabled && minecraft.screen == null) {
            zoomActive = ZOOM_KEY.isDown();
        }
        if (zoomActive) {
            if (!mZoomMode) {
                mZoomMode = true;
                mZoomSmoothCamera = minecraft.options.smoothCamera;
                minecraft.options.smoothCamera = true;
                minecraft.levelRenderer.needsUpdate();
            }
            event.setFOV(
                    event.getFOV() * 0.25f
            );
        } else if (mZoomMode) {
            mZoomMode = false;
            minecraft.options.smoothCamera = mZoomSmoothCamera;
            minecraft.levelRenderer.needsUpdate();
        }
    }

    //boolean mPendingRepostCursorEvent = false;

    // main fragment of a UI
    //private Fragment fragment;

    // main UI view that created from main fragment
    //private View view;

    //private IModule popup;

    //boolean mLayoutRequested = false;
    //private long mLastLayoutTime = 0;

    // elapsed ticks from a gui open, update every tick, 20 = 1 second
    //private int mTicks = 0;

    // registered menu screens
    //private final Map<ContainerType<?>, Function<? extends Container, ApplicationUI>> mScreenRegistry = new
    // HashMap<>();

    // the most child hovered view, render at the top of other hovered ancestor views
    /*@Nullable
    private View mHovered;*/

    // focused view
    /*@Nullable
    private View mDragging;
    @Nullable
    private View mKeyboard;*/

    /*public int getScreenWidth() {
        return mWidth;
    }

    public int getScreenHeight() {
        return mHeight;
    }

    public double getCursorX() {
        return mCursorX;
    }

    public double getCursorY() {
        return mCursorY;
    }

    public double getViewMouseX(@Nonnull View view) {
        ViewParent parent = view.getParent();
        double mouseX = mCursorX;

        while (parent != null) {
            mouseX += parent.getScrollX();
            parent = parent.getParent();
        }

        return mouseX;
    }

    public double getViewMouseY(@Nonnull View view) {
        ViewParent parent = view.getParent();
        double mouseY = mCursorY;

        while (parent != null) {
            mouseY += parent.getScrollY();
            parent = parent.getParent();
        }

        return mouseY;
    }*/

    /*void setHovered(@Nullable View view) {
        mHovered = view;
    }

    @Nullable
    public View getHovered() {
        return mHovered;
    }

    public void setDragging(@Nullable View view) {
        if (mDragging != view) {
            if (mDragging != null) {
                mDragging.onStopDragging();
            }
            mDragging = view;
            if (mDragging != null) {
                mDragging.onStartDragging();
            }
        }
    }

    @Nullable
    public View getDragging() {
        return mDragging;
    }

    public void setKeyboard(@Nullable View view) {
        if (mKeyboard != view) {
            minecraft.keyboardHandler.setSendRepeatsToGui(view != null);
            if (mKeyboard != null) {
                mKeyboard.onStopKeyboard();
            }
            mKeyboard = view;
            if (mKeyboard != null) {
                mKeyboard.onStartKeyboard();
            }
        }
    }

    @Nullable
    public View getKeyboard() {
        return mKeyboard;
    }*/

    /*@Nonnull
    private <T extends Container> ScreenManager.IScreenFactory<T, MuiMenuScreen<T>> getFactory(
            @Nonnull Function<T, Fragment> factory) {
        return (container, inventory, title) -> {
            this.fragment = factory.apply(container);
            return new MuiMenuScreen<>(container, inventory, title, this);
        };
    }*/

    /*
     * Get elapsed ticks from a gui open, update every tick, 20 = 1 second
     *
     * @return elapsed ticks
     */
    /*public int getElapsedTicks() {
        return mTicks;
    }*/

    /*@Deprecated
    public void openPopup(IModule popup, boolean refresh) {
        throw new UnsupportedOperationException();
        *//*if (root == null) {
            ModernUI.LOGGER.fatal(MARKER, "#openPopup() shouldn't be called when there's NO gui open");
            return;
        }*//*
     *//*if (this.popup != null) {
            ModernUI.LOGGER.warn(MARKER, "#openPopup() shouldn't be called when there's already a popup, the previous
             one has been overwritten");
        }
        if (refresh) {
            this.screenMouseMove(-1, -1);
        }
        this.popup = popup;
        this.popup.resize(width, height);*//*
    }*/

    /*@Deprecated
    public void closePopup() {
        throw new UnsupportedOperationException();
        *//*if (popup != null) {
            popup = null;
        }*//*
    }*/

    /*boolean screenKeyDown(int keyCode, int scanCode, int modifiers) {
     *//*if (popup != null) {
            return popup.keyPressed(keyCode, scanCode, modifiers);
        }*//*
        ModernUI.LOGGER.debug(MARKER, "KeyDown{keyCode:{}, scanCode:{}, mods:{}}", keyCode, scanCode, modifiers);
        *//*if (mKeyboard != null) {
            return mKeyboard.onKeyPressed(keyCode, scanCode, modifiers);
        }*//*
        return false;
    }*/

    /*boolean screenKeyUp(int keyCode, int scanCode, int modifiers) {
     *//*if (popup != null) {
            return popup.keyReleased(keyCode, scanCode, modifiers);
        }*//*
     *//*if (mKeyboard != null) {
            return mKeyboard.onKeyReleased(keyCode, scanCode, modifiers);
        }*//*
        return false;//root.keyReleased(keyCode, scanCode, modifiers);
    }*/

    /*boolean sChangeKeyboard(boolean searchNext) {
        return false;
    }

    boolean onBackPressed() {
        *//*if (popup != null) {
            closePopup();
            return true;
        }*//*
        return false;//root.onBack();
    }*/

    /*
     * Get current open screen differently from Minecraft's,
     * which will only return Modern UI's screen or null
     *
     * @return open modern screen
     * @see Minecraft#currentScreen
     */
    /*@Nullable
    public Screen getModernScreen() {
        return mMuiScreen;
    }*/

    /*public boolean hasOpenGUI() {
        return mScreen != null;
    }*/

    /*public void repostCursorEvent() {
        mPendingRepostCursorEvent = true;
    }*/
    
    /*@Deprecated
    boolean screenMouseDown(double mouseX, double mouseY, int mouseButton) {
        setMousePos(mouseX, mouseY);
        MotionEvent event = null;
        event.button = mouseButton;
        //List<ViewRootImpl> windows = this.windows;
        boolean handled = false;
        if (mouseButton == GLFW_MOUSE_BUTTON_LEFT && lastLmTick >= 0 && ticks - lastLmTick < 6) {
            //event.action = MotionEvent.ACTION_DOUBLE_CLICK;
            *//*for (int i = windows.size() - 1; i >= 0; i--) {
                if (windows.get(i).onMouseEvent(event)) {
                    return true;
                }
            }*//*
            if (lastLmView != null && lastLmView.isMouseHovered() && lastLmView.onGenericMotionEvent(event)) {
                handled = true;
            }
        }
        lastLmView = null;
        if (handled) {
            return true;
        }
        //event.action = MotionEvent.ACTION_PRESS;
        return mAppWindow.onMouseEvent(event);
        *//*for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onMouseEvent(event)) {
                return true;
            }
        }*//*
     *//*if (popup != null) {
            return popup.mouseClicked(mouseX, mouseY, mouseButton);
        }*//*
     *//*if (mHovered != null) {
            IViewParent parent;
            View view;
            double viewMX = getViewMouseX(mHovered);
            double viewMY = getViewMouseY(mHovered);
            if (mouseButton == 0) {
                int delta = ticks - lastDClickTick;
                if (delta < 10) {
                    lastDClickTick = Integer.MIN_VALUE;
                    if (mHovered.onMouseDoubleClicked(viewMX, viewMY)) {
                        return true;
                    }
                    parent = mHovered.getParent();
                    double viewMX2 = viewMX;
                    double viewMY2 = viewMY;
                    while (parent instanceof View) {
                        view = (View) parent;
                        viewMX2 -= parent.getScrollX();
                        viewMY2 -= parent.getScrollY();
                        if (view.onMouseDoubleClicked(viewMX2, viewMY2)) {
                            return true;
                        }
                        parent = parent.getParent();
                    }
                } else {
                    lastDClickTick = ticks;
                }
            }
            *//**//*if (mHovered.mouseClicked(viewMX, viewMY, mouseButton)) {
                return true;
            }*//**//*
            parent = mHovered.getParent();
            while (parent instanceof View) {
                view = (View) parent;
                viewMX -= parent.getScrollX();
                viewMY -= parent.getScrollY();
                *//**//*if (view.mouseClicked(viewMX, viewMY, mouseButton)) {
                    return true;
                }*//**//*
                parent = parent.getParent();
            }
        }*//*
    }

    @Deprecated
    boolean screenMouseUp(double mouseX, double mouseY, int mouseButton) {
        setMousePos(mouseX, mouseY);
        MotionEvent event = motionEvent;
        //event.action = MotionEvent.ACTION_RELEASE;
        event.button = mouseButton;
        boolean dCheck = false;
        if (mouseButton == GLFW_MOUSE_BUTTON_LEFT && lastLmTick < 0) {
            dCheck = event.pressMap.get(mouseButton) != null;
        } else {
            lastLmTick = Integer.MIN_VALUE;
        }
        //List<ViewRootImpl> windows = this.windows;
        boolean handled = false;
        *//*for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onMouseEvent(event)) {
                handled = true;
                break;
            }
        }*//*
        if (mAppWindow.onMouseEvent(event)) {
            handled = true;
        }
        if (dCheck && event.clicked != null) {
            lastLmTick = ticks;
        } else {
            lastLmTick = Integer.MIN_VALUE;
        }
        lastLmView = event.clicked;
        event.clicked = null;
        if (handled) {
            return true;
        }
        *//*if (popup != null) {
            return popup.mouseReleased(mouseX, mouseY, mouseButton);
        }*//*
        if (mDragging != null) {
            setDragging(null);
            return true;
        }
        *//*if (mHovered != null) {
            double viewMX = getViewMouseX(mHovered);
            double viewMY = getViewMouseY(mHovered);
            if (mHovered.onMouseReleased(viewMX, viewMY, mouseButton)) {
                return true;
            }
            IViewParent parent = mHovered.getParent();
            View view;
            while (parent instanceof View) {
                view = (View) parent;
                viewMX -= parent.getScrollX();
                viewMY -= parent.getScrollY();
                if (view.onMouseReleased(viewMX, viewMY, mouseButton)) {
                    return true;
                }
                parent = parent.getParent();
            }
        }*//*
        return false;//root.mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Deprecated
    boolean screenMouseDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        setMousePos(mouseX, mouseY);
        *//*if (popup != null) {
            return popup.mouseDragged(mouseX, mouseY, deltaX, deltaY);
        }*//*
        if (mDragging != null) {
            return mDragging.onMouseDragged(getViewMouseX(mDragging), getViewMouseY(mDragging), deltaX, deltaY);
        }
        return false;
    }

    @Deprecated
    boolean screenMouseScroll(double mouseX, double mouseY, double amount) {
        setMousePos(mouseX, mouseY);
        MotionEvent event = motionEvent;
        //event.action = MotionEvent.ACTION_SCROLL;
        event.scrollDelta = amount;
        *//*List<ViewRootImpl> windows = this.windows;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onMouseEvent(event)) {
                return true;
            }
        }*//*
        return mAppWindow.onMouseEvent(event);
        *//*if (popup != null) {
            return popup.mouseScrolled(mouseX, mouseY, amount);
        }*//*
     *//*if (mHovered != null) {
            double viewMX = getViewMouseX(mHovered);
            double viewMY = getViewMouseY(mHovered);
            if (mHovered.onMouseScrolled(viewMX, getViewMouseY(mHovered), amount)) {
                return true;
            }
            IViewParent parent = mHovered.getParent();
            View view;
            while (parent != null) {
                view = (View) parent;
                viewMX -= parent.getScrollX();
                viewMY -= parent.getScrollY();
                if (view.onMouseScrolled(mouseX, mouseY, amount)) {
                    return true;
                }
                parent = parent.getParent();
            }
        }*//*
    }*/

    /*private void setMousePos(double mouseX, double mouseY) {
     *//*this.mouseX = mouseEvent.x = mouseX;
        this.mouseY = mouseEvent.y = mouseY;*//*
    }

    @Deprecated
    void screenMouseMove(double mouseX, double mouseY) {
        setMousePos(mouseX, mouseY);
        MotionEvent event = null;
        //event.action = MotionEvent.ACTION_MOVE;
        *//*List<ViewRootImpl> windows = this.windows;
        boolean anyHovered = false;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (!anyHovered && windows.get(i).onMouseEvent(event)) {
                anyHovered = true;
            } else {
                windows.get(i).ensureMouseHoverExit();
            }
        }*//*
        mAppWindow.onMouseEvent(event);
        cursorRefreshRequested = false;
    }*/

    /*private void mouseMoved() {
        if (view != null && !view.updateMouseHover(mouseX, mouseY)) {
            setHovered(null);
        }
    }*/
}
