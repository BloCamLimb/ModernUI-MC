/*
 * Modern UI.
 * Copyright (C) 2025 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.text.LayoutCache;
import icyllis.modernui.graphics.text.LineBreakConfig;
import icyllis.modernui.mc.text.BitmapFont;
import icyllis.modernui.mc.text.GLFontAtlas;
import icyllis.modernui.mc.text.GlyphManager;
import icyllis.modernui.mc.text.ModernTextRenderer;
import icyllis.modernui.mc.text.TextLayout;
import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.mc.text.TextLayoutProcessor;
import icyllis.modernui.mc.text.TextRenderType;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.util.DisplayMetrics;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static icyllis.modernui.mc.ModernUIMod.*;

@ApiStatus.Internal
public final class Config {

    public static final int TYPE_CLIENT = 1;
    public static final int TYPE_COMMON = 2;
    public static final int TYPE_TEXT = 3;

    public static final Client CLIENT;
    public static final Common COMMON;
    public static final Text TEXT;

    static {
        MuiPlatform service = MuiPlatform.get();
        if (service.isClient()) {
            CLIENT = new Client(service.getConfigMap(TYPE_CLIENT));
            TEXT = new Text(service.getConfigMap(TYPE_TEXT));
        } else {
            CLIENT = new Client(Collections.emptyMap());
            TEXT = new Text(Collections.emptyMap());
        }
        COMMON = new Common(service.getConfigMap(TYPE_COMMON));
    }

    public static void initialize() {
    }

    @SuppressWarnings("unchecked")
    private static <T> ConfigItem<T> get(Map<String, ConfigItem<?>> map, String name) {
        return (ConfigItem<T>) map.get(name);
    }

    public static class Client {

        public final ConfigItem<Boolean> mBlurEffect;
        public final ConfigItem<Boolean> mAdditionalBlurEffect;
        public final ConfigItem<Boolean> mOverrideVanillaBlur;
        public final ConfigItem<Integer> mBackgroundDuration;
        public final ConfigItem<Integer> mBlurRadius;
        public final ConfigItem<List<? extends String>> mBackgroundColor;
        public final ConfigItem<Boolean> mInventoryPause;
        public final ConfigItem<Boolean> mTooltip;
        public final ConfigItem<Boolean> mRoundedTooltip;
        public final ConfigItem<Boolean> mCenterTooltipTitle;
        public final ConfigItem<Boolean> mTooltipTitleBreak;
        public final ConfigItem<Boolean> mExactTooltipPositioning;
        public final ConfigItem<List<? extends String>> mTooltipFill;
        public final ConfigItem<List<? extends String>> mTooltipStroke;
        public final ConfigItem<Integer> mTooltipCycle;
        public final ConfigItem<Double> mTooltipWidth;
        public final ConfigItem<Double> mTooltipRadius;
        public final ConfigItem<Double> mTooltipShadowRadius;
        public final ConfigItem<Double> mTooltipShadowAlpha;
        public final ConfigItem<Boolean> mAdaptiveTooltipColors;
        public final ConfigItem<Integer> mTooltipArrowScrollFactor;
        public final ConfigItem<Boolean> mTooltipLineWrapping;
        public final ConfigItem<Boolean> mDing;
        public final ConfigItem<String> mDingSound;
        public final ConfigItem<Double> mDingVolume;
        public final ConfigItem<Boolean> mZoom;
        public final ConfigItem<Boolean> mForceRtl;
        public final ConfigItem<Double> mFontScale;
        public final ConfigItem<WindowMode> mWindowMode;
        public final ConfigItem<Boolean> mUseNewGuiScale;
        public final ConfigItem<Boolean> mRemoveTelemetry;
        public final ConfigItem<Integer> mFramerateInactive;
        public final ConfigItem<Double> mMasterVolumeInactive;
        public final ConfigItem<Double> mMasterVolumeMinimized;
        public final ConfigItem<Integer> mScrollbarSize;
        public final ConfigItem<Integer> mTouchSlop;
        public final ConfigItem<Integer> mHoverSlop;
        public final ConfigItem<Integer> mMinScrollbarTouchTarget;
        public final ConfigItem<Integer> mMinimumFlingVelocity;
        public final ConfigItem<Integer> mMaximumFlingVelocity;
        public final ConfigItem<Double> mScrollFriction;
        public final ConfigItem<Integer> mOverscrollDistance;
        public final ConfigItem<Integer> mOverflingDistance;
        public final ConfigItem<Double> mVerticalScrollFactor;
        public final ConfigItem<Double> mHorizontalScrollFactor;
        public final ConfigItem<Integer> mHoverTooltipShowTimeout;
        public final ConfigItem<Integer> mHoverTooltipHideTimeout;
        public final ConfigItem<List<? extends String>> mBlurBlacklist;
        public final ConfigItem<String> mFirstFontFamily;
        public final ConfigItem<List<? extends String>> mFallbackFontFamilyList;
        public final ConfigItem<List<? extends String>> mFontRegistrationList;
        public final ConfigItem<Boolean> mUseColorEmoji;
        public final ConfigItem<Boolean> mLinearMetrics;
        public final ConfigItem<Boolean> mEmojiShortcodes;

        public volatile WindowMode mLastWindowMode = WindowMode.NORMAL;

        // Used to test whether config values are loaded.
        // We can't rely on ConfigSpec.isLoaded because it's not thread-safe,
        // and when that method returns true, the cached values in ConfigValue may
        // not have been cleared. This field is set on the Load/Reload event is fired,
        // and sets up a barrier to load config values safely, especially under race condition.
        public volatile boolean mLoaded;
        // Set to true to allow to propagate changes to listeners.
        // If configs load too early, we will not trigger listeners until the game is loaded.
        public volatile boolean mPropagate;

        private Client(Map<String, ConfigItem<?>> map) {
            mBlurEffect = get(map, "mBlurEffect");
            mAdditionalBlurEffect = get(map, "mAdditionalBlurEffect");
            mOverrideVanillaBlur = get(map, "mOverrideVanillaBlur");
            mBackgroundDuration = get(map, "mBackgroundDuration");
            mBlurRadius = get(map, "mBlurRadius");
            mBackgroundColor = get(map, "mBackgroundColor");
            mInventoryPause = get(map, "mInventoryPause");
            mTooltip = get(map, "mTooltip");
            mRoundedTooltip = get(map, "mRoundedTooltip");
            mCenterTooltipTitle = get(map, "mCenterTooltipTitle");
            mTooltipTitleBreak = get(map, "mTooltipTitleBreak");
            mExactTooltipPositioning = get(map, "mExactTooltipPositioning");
            mTooltipFill = get(map, "mTooltipFill");
            mTooltipStroke = get(map, "mTooltipStroke");
            mTooltipCycle = get(map, "mTooltipCycle");
            mTooltipWidth = get(map, "mTooltipWidth");
            mTooltipRadius = get(map, "mTooltipRadius");
            mTooltipShadowRadius = get(map, "mTooltipShadowRadius");
            mTooltipShadowAlpha = get(map, "mTooltipShadowAlpha");
            mAdaptiveTooltipColors = get(map, "mAdaptiveTooltipColors");
            mTooltipArrowScrollFactor = get(map, "mTooltipArrowScrollFactor");
            mTooltipLineWrapping = get(map, "mTooltipLineWrapping");
            mDing = get(map, "mDing");
            mDingSound = get(map, "mDingSound");
            mDingVolume = get(map, "mDingVolume");
            mZoom = get(map, "mZoom");
            mForceRtl = get(map, "mForceRtl");
            mFontScale = get(map, "mFontScale");
            mWindowMode = get(map, "mWindowMode");
            mUseNewGuiScale = get(map, "mUseNewGuiScale");
            mRemoveTelemetry = get(map, "mRemoveTelemetry");
            mFramerateInactive = get(map, "mFramerateInactive");
            mMasterVolumeInactive = get(map, "mMasterVolumeInactive");
            mMasterVolumeMinimized = get(map, "mMasterVolumeMinimized");
            mScrollbarSize = get(map, "mScrollbarSize");
            mTouchSlop = get(map, "mTouchSlop");
            mHoverSlop = get(map, "mHoverSlop");
            mMinScrollbarTouchTarget = get(map, "mMinScrollbarTouchTarget");
            mMinimumFlingVelocity = get(map, "mMinimumFlingVelocity");
            mMaximumFlingVelocity = get(map, "mMaximumFlingVelocity");
            mScrollFriction = get(map, "mScrollFriction");
            mOverscrollDistance = get(map, "mOverscrollDistance");
            mOverflingDistance = get(map, "mOverflingDistance");
            mVerticalScrollFactor = get(map, "mVerticalScrollFactor");
            mHorizontalScrollFactor = get(map, "mHorizontalScrollFactor");
            mHoverTooltipShowTimeout = get(map, "mHoverTooltipShowTimeout");
            mHoverTooltipHideTimeout = get(map, "mHoverTooltipHideTimeout");
            mBlurBlacklist = get(map, "mBlurBlacklist");
            mFirstFontFamily = get(map, "mFirstFontFamily");
            mFallbackFontFamilyList = get(map, "mFallbackFontFamilyList");
            mFontRegistrationList = get(map, "mFontRegistrationList");
            mUseColorEmoji = get(map, "mUseColorEmoji");
            mLinearMetrics = get(map, "mLinearMetrics");
            mEmojiShortcodes = get(map, "mEmojiShortcodes");
        }

        public void reload() {
            mLoaded = true;


            ModernUIClient.sInventoryPause = mInventoryPause.get();
            //ModernUIForge.sRemoveMessageSignature = mRemoveSignature.get();
            ModernUIClient.sRemoveTelemetrySession = mRemoveTelemetry.get();
            //ModernUIForge.sSecureProfilePublicKey = mSecurePublicKey.get();


            ModernUIClient.sUseColorEmoji = mUseColorEmoji.get();
            ModernUIClient.sEmojiShortcodes = mEmojiShortcodes.get();
            ModernUIClient.sFirstFontFamily = mFirstFontFamily.get();
            ModernUIClient.sFallbackFontFamilyList = mFallbackFontFamilyList.get();
            ModernUIClient.sFontRegistrationList = mFontRegistrationList.get();

            if (mPropagate) {
                apply();
            }
        }

        public void apply() {
            mPropagate = true;

            BlurHandler.sBlurEffect = mBlurEffect.get();
            //BlurHandler.sBlurWithBackground = mBlurWithBackground.get();
            BlurHandler.sBlurForVanillaScreens = mAdditionalBlurEffect.get();
            BlurHandler.sOverrideVanillaBlur = mOverrideVanillaBlur.get();
            BlurHandler.sBackgroundDuration = mBackgroundDuration.get();
            BlurHandler.sBlurRadius = mBlurRadius.get();

            BlurHandler.sFramerateInactive = mFramerateInactive.get();
            /*BlurHandler.sFramerateMinimized = Math.min(
                    mFramerateMinimized.get(),
                    BlurHandler.sFramerateInactive
            );*/
            BlurHandler.sMasterVolumeInactive = mMasterVolumeInactive.get().floatValue();
            BlurHandler.sMasterVolumeMinimized = Math.min(
                    mMasterVolumeMinimized.get().floatValue(),
                    BlurHandler.sMasterVolumeInactive
            );

            List<? extends String> inColors = mBackgroundColor.get();
            int[] resultColors = new int[4];
            int color = 0x99000000;
            for (int i = 0; i < 4; i++) {
                if (inColors != null && i < inColors.size()) {
                    String s = inColors.get(i);
                    try {
                        color = Color.parseColor(s);
                    } catch (Exception e) {
                        LOGGER.error(MARKER, "Wrong color format for screen background, index: {}", i, e);
                    }
                }
                resultColors[i] = color;
            }
            BlurHandler.sBackgroundColor = resultColors;

            BlurHandler.INSTANCE.loadBlacklist(mBlurBlacklist.get());

            TooltipRenderer.sTooltip = mTooltip.get();

            inColors = mTooltipFill.get();
            color = 0xFFFFFFFF;
            for (int i = 0; i < 4; i++) {
                if (inColors != null && i < inColors.size()) {
                    String s = inColors.get(i);
                    try {
                        color = Color.parseColor(s);
                    } catch (Exception e) {
                        LOGGER.error(MARKER, "Wrong color format for tooltip background, index: {}", i, e);
                    }
                }
                TooltipRenderer.sFillColor[i] = color;
            }
            inColors = mTooltipStroke.get();
            color = 0xFFFFFFFF;
            for (int i = 0; i < 4; i++) {
                if (inColors != null && i < inColors.size()) {
                    String s = inColors.get(i);
                    try {
                        color = Color.parseColor(s);
                    } catch (Exception e) {
                        LOGGER.error(MARKER, "Wrong color format for tooltip border, index: {}", i, e);
                    }
                }
                TooltipRenderer.sStrokeColor[i] = color;
            }
            //TooltipRenderer.sAnimationDuration = mTooltipDuration.get();
            TooltipRenderer.sBorderColorCycle = mTooltipCycle.get();
            TooltipRenderer.sExactPositioning = mExactTooltipPositioning.get();
            TooltipRenderer.sRoundedShapes = mRoundedTooltip.get();
            TooltipRenderer.sCenterTitle = mCenterTooltipTitle.get();
            TooltipRenderer.sTitleBreak = mTooltipTitleBreak.get();
            TooltipRenderer.sBorderWidth = mTooltipWidth.get().floatValue();
            TooltipRenderer.sCornerRadius = mTooltipRadius.get().floatValue();
            TooltipRenderer.sShadowRadius = mTooltipShadowRadius.get().floatValue();
            TooltipRenderer.sShadowAlpha = mTooltipShadowAlpha.get().floatValue();
            TooltipRenderer.sAdaptiveColors = mAdaptiveTooltipColors.get();
            TooltipRenderer.sArrowScrollFactor = mTooltipArrowScrollFactor.get();
            if (mTooltipLineWrapping != null) {
                TooltipRenderer.sLineWrapping_FabricOnly = mTooltipLineWrapping.get();
            }

            UIManager.sDingEnabled = mDing.get();
            UIManager.sDingSound = mDingSound.get();
            UIManager.sDingVolume = mDingVolume.get().floatValue();
            if (mZoom != null) {
                UIManager.sZoomEnabled = mZoom.get() && !ModernUIMod.isOptiFineLoaded();
            }

            WindowMode windowMode = mWindowMode.get();
            if (mLastWindowMode != windowMode) {
                mLastWindowMode = windowMode;
                Minecraft.getInstance().schedule(() -> mLastWindowMode.apply());
            }

            //TestHUD.sBars = hudBars.get();
            Handler handler = Core.getUiHandlerAsync();
            if (handler != null) {
                handler.post(() -> {
                    UIManager.getInstance().updateLayoutDir(mForceRtl.get());
                    ModernUIClient.sFontScale = (mFontScale.get().floatValue());
                    var ctx = ModernUI.getInstance();
                    if (ctx != null) {
                        Resources res = ctx.getResources();
                        DisplayMetrics metrics = new DisplayMetrics();
                        metrics.setTo(res.getDisplayMetrics());
                        metrics.scaledDensity = ModernUIClient.sFontScale * metrics.density;
                        res.updateMetrics(metrics);
                    }
                    boolean resetConfigCache = false;
                    if (ViewConfiguration.sScrollBarSize != mScrollbarSize.get()) {
                        ViewConfiguration.sScrollBarSize = mScrollbarSize.get();
                        resetConfigCache = true;
                    }
                    if (ViewConfiguration.sTouchSlop != mTouchSlop.get()) {
                        ViewConfiguration.sTouchSlop = mTouchSlop.get();
                        resetConfigCache = true;
                    }
                    if (ViewConfiguration.sHoverSlop != mHoverSlop.get()) {
                        ViewConfiguration.sHoverSlop = mHoverSlop.get();
                        resetConfigCache = true;
                    }
                    if (ViewConfiguration.sMinScrollbarTouchTarget != mMinScrollbarTouchTarget.get()) {
                        ViewConfiguration.sMinScrollbarTouchTarget = mMinScrollbarTouchTarget.get();
                        resetConfigCache = true;
                    }
                    if (ViewConfiguration.sMinimumFlingVelocity != mMinimumFlingVelocity.get()) {
                        ViewConfiguration.sMinimumFlingVelocity = mMinimumFlingVelocity.get();
                        resetConfigCache = true;
                    }
                    if (ViewConfiguration.sMaximumFlingVelocity != mMaximumFlingVelocity.get()) {
                        ViewConfiguration.sMaximumFlingVelocity = mMaximumFlingVelocity.get();
                        resetConfigCache = true;
                    }
                    ViewConfiguration.sScrollFriction = mScrollFriction.get().floatValue();
                    if (ViewConfiguration.sOverscrollDistance != mOverscrollDistance.get()) {
                        ViewConfiguration.sOverscrollDistance = mOverscrollDistance.get();
                        resetConfigCache = true;
                    }
                    if (ViewConfiguration.sOverflingDistance != mOverflingDistance.get()) {
                        ViewConfiguration.sOverflingDistance = mOverflingDistance.get();
                        resetConfigCache = true;
                    }
                    if (ViewConfiguration.sHorizontalScrollFactor != mHorizontalScrollFactor.get()) {
                        ViewConfiguration.sHorizontalScrollFactor = mHorizontalScrollFactor.get().floatValue();
                        resetConfigCache = true;
                    }
                    if (ViewConfiguration.sVerticalScrollFactor != mVerticalScrollFactor.get()) {
                        ViewConfiguration.sVerticalScrollFactor = mVerticalScrollFactor.get().floatValue();
                        resetConfigCache = true;
                    }
                    ViewConfiguration.sHoverTooltipShowTimeout = mHoverTooltipShowTimeout.get();
                    ViewConfiguration.sHoverTooltipHideTimeout = mHoverTooltipHideTimeout.get();
                    if (resetConfigCache) {
                        ViewConfiguration.resetCache();
                    }
                    //TODO need some Paint constants to be public
                    /*if (mLinearMetrics.get()) {
                    }*/
                });
            }
        }

        public enum WindowMode {
            NORMAL,
            FULLSCREEN,
            FULLSCREEN_BORDERLESS,
            MAXIMIZED,
            MAXIMIZED_BORDERLESS,
            WINDOWED,
            WINDOWED_BORDERLESS;

            public void apply() {
                if (this == NORMAL) {
                    return;
                }
                Window window = Minecraft.getInstance().getWindow();
                switch (this) {
                    case FULLSCREEN -> {
                        if (!window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                    }
                    case FULLSCREEN_BORDERLESS -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwRestoreWindow(window.getWindow());
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                        Monitor monitor = window.findBestMonitor();
                        if (monitor != null) {
                            VideoMode videoMode = monitor.getCurrentMode();
                            int x = monitor.getX();
                            int y = monitor.getY();
                            int width = videoMode.getWidth();
                            int height = videoMode.getHeight();
                            GLFW.glfwSetWindowMonitor(window.getWindow(), MemoryUtil.NULL,
                                    x, y, width, height, GLFW.GLFW_DONT_CARE);
                        } else {
                            GLFW.glfwMaximizeWindow(window.getWindow());
                        }
                    }
                    case MAXIMIZED -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwRestoreWindow(window.getWindow());
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
                        GLFW.glfwMaximizeWindow(window.getWindow());
                    }
                    case MAXIMIZED_BORDERLESS -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwRestoreWindow(window.getWindow());
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                        GLFW.glfwMaximizeWindow(window.getWindow());
                    }
                    case WINDOWED -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
                        GLFW.glfwRestoreWindow(window.getWindow());
                    }
                    case WINDOWED_BORDERLESS -> {
                        if (window.isFullscreen()) {
                            window.toggleFullScreen();
                        }
                        GLFW.glfwSetWindowAttrib(window.getWindow(),
                                GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                        GLFW.glfwRestoreWindow(window.getWindow());
                    }
                }
            }

            @Nonnull
            @Override
            public String toString() {
                return I18n.get("modernui.windowMode." + name().toLowerCase(Locale.ROOT));
            }
        }
    }

    public static class Common {

        public final ConfigItem<Boolean> developerMode;

        private Common(Map<String, ConfigItem<?>> map) {
            developerMode = get(map, "developerMode");
        }

        public void reload() {
            ModernUIMod.sDeveloperMode = developerMode.get();
        }
    }

    public static class Text {

        public final ConfigItem<Boolean> mAllowShadow;
        public final ConfigItem<Boolean> mFixedResolution;
        public final ConfigItem<Double> mBaseFontSize;
        public final ConfigItem<Double> mBaselineShift;
        public final ConfigItem<Double> mShadowOffset;
        public final ConfigItem<Double> mOutlineOffset;
        public final ConfigItem<Double> mBitmapOffset;
        public final ConfigItem<Integer> mCacheLifespan;
        public final ConfigItem<TextDirection> mTextDirection;
        public final ConfigItem<Boolean> mUseTextShadersInWorld;
        public final ConfigItem<DefaultFontBehavior> mDefaultFontBehavior;
        public final ConfigItem<List<? extends String>> mDefaultFontRuleSet;
        public final ConfigItem<Boolean> mUseComponentCache;
        public final ConfigItem<Boolean> mAllowAsyncLayout;
        public final ConfigItem<LineBreakStyle> mLineBreakStyle;
        public final ConfigItem<LineBreakWordStyle> mLineBreakWordStyle;
        public final ConfigItem<Boolean> mSmartSDFShaders;
        public final ConfigItem<Boolean> mComputeDeviceFontSize;
        public final ConfigItem<Boolean> mAllowSDFTextIn2D;
        public final ConfigItem<Boolean> mTweakExperienceText;
        public final ConfigItem<Boolean> mAntiAliasing;
        public final ConfigItem<Boolean> mLinearMetrics;
        public final ConfigItem<Integer> mMinPixelDensityForSDF;
        public final ConfigItem<Boolean> mLinearSamplingA8Atlas;

        public volatile boolean mLoaded;
        public volatile boolean mPropagate;

        private Text(Map<String, ConfigItem<?>> map) {
            mAllowShadow = get(map, "mAllowShadow");
            mFixedResolution = get(map, "mFixedResolution");
            mBaseFontSize = get(map, "mBaseFontSize");
            mBaselineShift = get(map, "mBaselineShift");
            mShadowOffset = get(map, "mShadowOffset");
            mOutlineOffset = get(map, "mOutlineOffset");
            mBitmapOffset = get(map, "mBitmapOffset");
            mCacheLifespan = get(map, "mCacheLifespan");
            mTextDirection = get(map, "mTextDirection");
            mUseTextShadersInWorld = get(map, "mUseTextShadersInWorld");
            mDefaultFontBehavior = get(map, "mDefaultFontBehavior");
            mDefaultFontRuleSet = get(map, "mDefaultFontRuleSet");
            mUseComponentCache = get(map, "mUseComponentCache");
            mAllowAsyncLayout = get(map, "mAllowAsyncLayout");
            mLineBreakStyle = get(map, "mLineBreakStyle");
            mLineBreakWordStyle = get(map, "mLineBreakWordStyle");
            mSmartSDFShaders = get(map, "mSmartSDFShaders");
            mComputeDeviceFontSize = get(map, "mComputeDeviceFontSize");
            mAllowSDFTextIn2D = get(map, "mAllowSDFTextIn2D");
            mTweakExperienceText = get(map, "mTweakExperienceText");
            mAntiAliasing = get(map, "mAntiAliasing");
            mLinearMetrics = get(map, "mLinearMetrics");
            mMinPixelDensityForSDF = get(map, "mMinPixelDensityForSDF");
            mLinearSamplingA8Atlas = get(map, "mLinearSamplingA8Atlas");
        }

        public void reload() {
            mLoaded = true;

            if (mPropagate) {
                apply();
            }
        }

        public void apply() {
            mPropagate = true;

            boolean reload = false;
            boolean reloadStrike = false;
            ModernTextRenderer.sAllowShadow = mAllowShadow.get();
            if (TextLayoutEngine.sFixedResolution != mFixedResolution.get()) {
                TextLayoutEngine.sFixedResolution = mFixedResolution.get();
                reload = true;
            }
            if (TextLayoutProcessor.sBaseFontSize != mBaseFontSize.get().floatValue()) {
                TextLayoutProcessor.sBaseFontSize = mBaseFontSize.get().floatValue();
                reloadStrike = true;
            }
            TextLayout.sBaselineOffset = mBaselineShift.get().floatValue();
            ModernTextRenderer.sShadowOffset = mShadowOffset.get().floatValue();
            ModernTextRenderer.sOutlineOffset = mOutlineOffset.get().floatValue();
            if (BitmapFont.sBitmapOffset != mBitmapOffset.get().floatValue()) {
                BitmapFont.sBitmapOffset = mBitmapOffset.get().floatValue();
                reload = true;
                LayoutCache.clear();
            }
            /*if (TextLayoutProcessor.sAlignPixels != mAlignPixels.get()) {
                TextLayoutProcessor.sAlignPixels = mAlignPixels.get();
                reload = true;
            }*/
            TextLayoutEngine.sCacheLifespan = mCacheLifespan.get();
            /*TextLayoutEngine.sRehashThreshold = mRehashThreshold.get();*/
            if (TextLayoutEngine.sTextDirection != mTextDirection.get().key) {
                TextLayoutEngine.sTextDirection = mTextDirection.get().key;
                reload = true;
            }
            if (TextLayoutEngine.sDefaultFontBehavior != mDefaultFontBehavior.get().key) {
                TextLayoutEngine.sDefaultFontBehavior = mDefaultFontBehavior.get().key;
                reload = true;
            }
            List<? extends String> defaultFontRuleSet = mDefaultFontRuleSet.get();
            if (!Objects.equals(TextLayoutEngine.sDefaultFontRuleSet, defaultFontRuleSet)) {
                TextLayoutEngine.sDefaultFontRuleSet = defaultFontRuleSet;
                reload = true;
            }
            TextLayoutEngine.sRawUseTextShadersInWorld = mUseTextShadersInWorld.get();
            TextLayoutEngine.sUseComponentCache = mUseComponentCache.get();
            TextLayoutEngine.sAllowAsyncLayout = mAllowAsyncLayout.get();
            if (TextLayoutProcessor.sLbStyle != mLineBreakStyle.get().key) {
                TextLayoutProcessor.sLbStyle = mLineBreakStyle.get().key;
                reload = true;
            }
            if (TextLayoutProcessor.sLbWordStyle != mLineBreakWordStyle.get().key) {
                TextLayoutProcessor.sLbWordStyle = mLineBreakWordStyle.get().key;
                reload = true;
            }

            final boolean smartShaders = mSmartSDFShaders.get();
            reload |= TextRenderType.toggleSDFShaders(smartShaders);

            ModernTextRenderer.sComputeDeviceFontSize = mComputeDeviceFontSize.get();
            ModernTextRenderer.sAllowSDFTextIn2D = mAllowSDFTextIn2D.get();
            ModernTextRenderer.sTweakExperienceText = mTweakExperienceText.get();

            if (GlyphManager.sAntiAliasing != mAntiAliasing.get()) {
                GlyphManager.sAntiAliasing = mAntiAliasing.get();
                reloadStrike = true;
            }
            if (GlyphManager.sFractionalMetrics != mLinearMetrics.get()) {
                GlyphManager.sFractionalMetrics = mLinearMetrics.get();
                reloadStrike = true;
            }
            if (TextLayoutEngine.sMinPixelDensityForSDF != mMinPixelDensityForSDF.get()) {
                TextLayoutEngine.sMinPixelDensityForSDF = mMinPixelDensityForSDF.get();
                reload = true;
            }
            if (GLFontAtlas.sLinearSamplingA8Atlas != mLinearSamplingA8Atlas.get()) {
                GLFontAtlas.sLinearSamplingA8Atlas = mLinearSamplingA8Atlas.get();
                reloadStrike = true;
            }
            /*if (GLFontAtlas.sLinearSampling != mLinearSampling.get()) {
                GLFontAtlas.sLinearSampling = mLinearSampling.get();
                reload = true;
            }*/

            if (reloadStrike) {
                Minecraft.getInstance().submit(
                        () -> FontResourceManager.getInstance().reloadAll());
            } else if (reload && ModernUIMod.isTextEngineEnabled()) {
                Minecraft.getInstance().submit(
                        () -> {
                            try {
                                TextLayoutEngine.getInstance().reload();
                            } catch (Exception ignored) {
                            }
                        });
            }
            /*GlyphManagerForge.sPreferredFont = preferredFont.get();
            GlyphManagerForge.sAntiAliasing = antiAliasing.get();
            GlyphManagerForge.sHighPrecision = highPrecision.get();
            GlyphManagerForge.sEnableMipmap = enableMipmap.get();
            GlyphManagerForge.sMipmapLevel = mipmapLevel.get();*/
            //GlyphManager.sResolutionLevel = resolutionLevel.get();
            //TextLayoutEngine.sDefaultFontSize = defaultFontSize.get();
        }

        public enum TextDirection {
            FIRST_STRONG(View.TEXT_DIRECTION_FIRST_STRONG, "FirstStrong"),
            ANY_RTL(View.TEXT_DIRECTION_ANY_RTL, "AnyRTL-LTR"),
            LTR(View.TEXT_DIRECTION_LTR, "LTR"),
            RTL(View.TEXT_DIRECTION_RTL, "RTL"),
            LOCALE(View.TEXT_DIRECTION_LOCALE, "Locale"),
            FIRST_STRONG_LTR(View.TEXT_DIRECTION_FIRST_STRONG_LTR, "FirstStrong-LTR"),
            FIRST_STRONG_RTL(View.TEXT_DIRECTION_FIRST_STRONG_RTL, "FirstStrong-RTL");

            private final int key;
            private final String text;

            TextDirection(int key, String text) {
                this.key = key;
                this.text = text;
            }

            @Override
            public String toString() {
                return text;
            }
        }

        public enum DefaultFontBehavior {
            IGNORE_ALL(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_IGNORE_ALL),
            KEEP_ASCII(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_KEEP_ASCII),
            KEEP_OTHER(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_KEEP_OTHER),
            KEEP_ALL(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_KEEP_ALL),
            ONLY_INCLUDE(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_ONLY_INCLUDE),
            ONLY_EXCLUDE(TextLayoutEngine.DEFAULT_FONT_BEHAVIOR_ONLY_EXCLUDE);

            private final int key;

            DefaultFontBehavior(int key) {
                this.key = key;
            }

            @Nonnull
            @Override
            public String toString() {
                return I18n.get("modernui.defaultFontBehavior." + name().toLowerCase(Locale.ROOT));
            }
        }

        public enum LineBreakStyle {
            AUTO(LineBreakConfig.LINE_BREAK_STYLE_NONE, "Auto"),
            LOOSE(LineBreakConfig.LINE_BREAK_STYLE_LOOSE, "Loose"),
            NORMAL(LineBreakConfig.LINE_BREAK_STYLE_NORMAL, "Normal"),
            STRICT(LineBreakConfig.LINE_BREAK_STYLE_STRICT, "Strict");

            private final int key;
            private final String text;

            LineBreakStyle(int key, String text) {
                this.key = key;
                this.text = text;
            }

            @Override
            public String toString() {
                return text;
            }
        }

        public enum LineBreakWordStyle {
            AUTO(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE, "Auto"),
            PHRASE(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE, "Phrase-based");

            private final int key;
            private final String text;

            LineBreakWordStyle(int key, String text) {
                this.key = key;
                this.text = text;
            }

            @Override
            public String toString() {
                return text;
            }
        }
    }
}
