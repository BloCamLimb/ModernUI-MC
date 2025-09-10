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

package icyllis.modernui.mc.testforge;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.SimpleDateFormat;
import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ContainerDrawHelper;
import icyllis.modernui.mc.ExtendedGuiGraphics;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.text.*;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.util.*;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Date;
import java.util.Locale;

public class TestPauseFragment extends Fragment {

    public static final int NETWORK_COLOR = 0xFF295E8A;

    private Image mButtonIcon;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        var navigation = new LinearLayout(getContext());
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
        navigation.setLayoutTransition(new LayoutTransition());

        if (mButtonIcon == null) {
            mButtonIcon = Image.create(ModernUI.ID, "gui/gui_icon.png");
        }
        if (mButtonIcon != null) {
            mButtonIcon.setDensity(DisplayMetrics.DENSITY_DEFAULT / 24 * 64);
        }

        for (int i = 0; i < 8; i++) {
            var button = new ImageButton(getContext(), null,
                    R.attr.iconButtonStyle);
            var icon = new ImageDrawable(getContext().getResources(), mButtonIcon);
            icon.setSrcRect(i * 32, 352, i * 32 + 32, 384);
            button.setImageDrawable(icon);
            var params = new LinearLayout.LayoutParams(navigation.dp(32), navigation.dp(32));
            button.setClickable(true);
            params.setMarginsRelative(i == 7 ? 26 : 2, 2, 2, 6);
            if (i == 0 || i == 7) {
                navigation.addView(button, params);
            } else {
                int index = i;
                content.postDelayed(() -> navigation.addView(button, index, params), i * 50);
            }
            if (i == 2) {
                button.setOnClickListener(__ -> {
                    DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.FULL, Locale.CHINA);
                    Toast.makeText(__.getContext(), "Hello, Toast! " + dateFormat.format(new Date()),
                            Toast.LENGTH_LONG).show();
                });
            }
            if (i == 3) {
                button.setOnClickListener(__ -> {
                    String s = "Your request was rejected by the server.";
                    SpannableString text = new SpannableString(s);
                    text.setSpan(new ForegroundColorSpan(0xFFCF1515), 0, s.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    Toast.makeText(__.getContext(), text, Toast.LENGTH_SHORT).show();
                });
            }
        }

        content.addView(navigation,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        var tab = new LinearLayout(getContext());
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setLayoutTransition(new LayoutTransition());
        var tabBackground = new ShapeDrawable();
        tabBackground.setCornerRadius(tab.dp(16));
        tabBackground.setColor(0xB4000000);
        tabBackground.setStroke(tab.dp(4), NETWORK_COLOR);
        tab.setBackground(tabBackground);

        for (int i = 0; i < 3; i++) {
            var v = new EditText(getContext());
            v.setText(switch (i) {
                case 0:
                    yield "Flux Point";
                case 1:
                    yield "0";
                default:
                    yield "800000";
            });
            v.setHint(switch (i) {
                case 0:
                    yield "Flux Point";
                case 1:
                    yield "Priority";
                default:
                    yield "Transfer Limit";
            });
            v.setSingleLine();
            int dp3 = v.dp(3);
            ShapeDrawable background = new ShapeDrawable();
            background.setCornerRadius(dp3);
            background.setStroke(dp3, NETWORK_COLOR);
            v.setBackground(background);
            v.setPadding(dp3, 1, dp3, 1);
            v.setTextSize(16);
            var icon = new ImageDrawable(getContext().getResources(), mButtonIcon);
            int srcLeft = (((i + 1) % 3) + 1) * 64;
            icon.setSrcRect(srcLeft, 192, srcLeft + 64, 256);
            v.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    icon, null, null, null);
            v.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);

            var params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(navigation.dp(20), navigation.dp(i == 0 ? 50 : 2),
                    navigation.dp(20), navigation.dp(2));

            content.postDelayed(() -> tab.addView(v, params), (i + 1) * 100);
        }

        {
            var v = new ConnectorView(getContext(), mButtonIcon);
            var params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            params.setMargins(navigation.dp(8), navigation.dp(2),
                    navigation.dp(8), navigation.dp(8));
            content.postDelayed(() -> tab.addView(v, params), 400);
        }

        int tabSize = navigation.dp(340);
        content.addView(tab, new LinearLayout.LayoutParams(tabSize, tabSize));

        content.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return content;
    }

    /*private static class TabBackground extends Drawable {

        private final float mRadius;
        private final TextPaint mTextPaint;

        public TabBackground(View view) {
            mRadius = view.dp(16);
            mTextPaint = new TextPaint();
            mTextPaint.setTextSize(view.sp(16));
        }

        @Override
        public void draw(@Nonnull Canvas canvas) {
            Rect b = getBounds();
            float stroke = mRadius * 0.25f;
            float start = stroke * 0.5f;

            Paint paint = Paint.obtain();
            paint.setRGBA(0, 0, 0, 180);
            canvas.drawRoundRect(b.left + start, b.top + start, b.right - start, b.bottom - start, mRadius, paint);
            paint.setStyle(Paint.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(NETWORK_COLOR);
            canvas.drawRoundRect(b.left + start, b.top + start, b.right - start, b.bottom - start, mRadius, paint);

            *//*canvas.drawText("BloCamLimb's Network", 0, 20, b.exactCenterX(), b.top + mRadius * 1.8f,
                    mTextPaint);*//*
            paint.recycle();
        }
    }*/

    /*private static class TextFieldStart extends Drawable {

        private final Image mImage;
        private final int mSrcLeft;
        private final int mSize;

        public TextFieldStart(View view, Image image, int srcLeft) {
            mImage = image;
            mSrcLeft = srcLeft;
            mSize = view.dp(24);
        }

        @Override
        public void draw(@Nonnull Canvas canvas) {
            Rect b = getBounds();
            canvas.drawImage(mImage, mSrcLeft, 192, mSrcLeft + 64, 256, b.left, b.top, b.right, b.bottom, null);
        }

        @Override
        public int getIntrinsicWidth() {
            return mSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return mSize;
        }

        @Override
        public boolean getPadding(@Nonnull Rect padding) {
            int h = Math.round(mSize / 4f);
            int v = Math.round(mSize / 6f);
            padding.set(h, v, h, v);
            return true;
        }
    }*/

    /*private static class TextFieldBackground extends Drawable {

        private final float mRadius;

        public TextFieldBackground(View view) {
            mRadius = view.dp(3);
        }

        @Override
        public void draw(@Nonnull Canvas canvas) {
            Rect b = getBounds();
            float start = mRadius * 0.5f;

            Paint paint = Paint.obtain();
            paint.setStyle(Paint.STROKE);
            paint.setStrokeWidth(mRadius);
            paint.setColor(NETWORK_COLOR);
            canvas.drawRoundRect(b.left + start, b.top + start, b.right - start, b.bottom - start, mRadius, paint);
            paint.recycle();
        }

        @Override
        public boolean getPadding(@Nonnull Rect padding) {
            int h = Math.round(mRadius);
            int v = Math.round(mRadius * 0.5f);
            padding.set(h, v, h, v);
            return true;
        }
    }*/

    /*private static class NavigationButton extends View {

        private final Image mImage;
        private final int mSrcLeft;

        public NavigationButton(Context context,
                                Image image, int srcLeft) {
            super(context);
            mImage = image;
            mSrcLeft = srcLeft;
        }

        @Override
        protected void onDraw(@Nonnull Canvas canvas) {
            Paint paint = Paint.obtain();
            if (!isHovered())
                paint.setRGBA(192, 192, 192, 255);
            canvas.drawImage(mImage, mSrcLeft, 352, mSrcLeft + 32, 384, 0, 0, getWidth(), getHeight(), paint);
            paint.recycle();
        }

        @Override
        public void onHoverChanged(boolean hovered) {
            super.onHoverChanged(hovered);
            invalidate();
        }
    }*/

    private static class ConnectorView extends FrameLayout {

        private final Image mImage;
        private final int mSize;
        private float mRodLength;
        private final Paint mBoxPaint = new Paint();

        private final ObjectAnimator mRodAnimator;
        private final ObjectAnimator mBoxAnimator;

        private final MinecraftSurfaceView mItemView;

        private final ItemStack mItem = Items.DIAMOND_BLOCK.getDefaultInstance();

        public ConnectorView(Context context, Image image) {
            super(context);
            mImage = image;
            mSize = dp(32);
            mRodAnimator = ObjectAnimator.ofFloat(this, new FloatProperty<>("rodLength") {
                @Override
                public void setValue(@Nonnull ConnectorView object, float value) {
                    object.mRodLength = value;
                    invalidate();
                }

                @Override
                public Float get(@Nonnull ConnectorView object) {
                    return object.mRodLength;
                }
            }, 0, dp(32));
            mRodAnimator.setInterpolator(TimeInterpolator.DECELERATE);
            mRodAnimator.setDuration(400);
            mRodAnimator.addListener(new AnimatorListener() {
                @Override
                public void onAnimationEnd(@Nonnull Animator animation) {
                    mBoxAnimator.start();
                }
            });
            mBoxAnimator = ObjectAnimator.ofInt(mBoxPaint, new IntProperty<>("boxAlpha") {
                @Override
                public void setValue(@Nonnull Paint object, int value) {
                    object.setAlpha(value);
                    if (value > 0) {
                        mItemView.setVisibility(View.VISIBLE);
                    }
                    invalidate();
                }

                @Override
                public Integer get(@Nonnull Paint object) {
                    return object.getAlpha();
                }
            }, 0, 128);
            mRodAnimator.setInterpolator(TimeInterpolator.LINEAR);
            mBoxAnimator.setDuration(400);
            mBoxPaint.setRGBA(64, 64, 64, 0);
            {
                mItemView = new MinecraftSurfaceView(context);
                mItemView.setVisibility(INVISIBLE);
                mItemView.setRenderer(new MinecraftSurfaceView.Renderer() {
                    int mSurfaceWidth;
                    int mSurfaceHeight;

                    @Override
                    public void onSurfaceChanged(int width, int height) {
                        mSurfaceWidth = width;
                        mSurfaceHeight = height;
                    }

                    @Override
                    public void onDraw(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick,
                                       double guiScale, float alpha) {
                        int guiScaledWidth = (int) (mSurfaceWidth / guiScale);
                        int guiScaledHeight = (int) (mSurfaceHeight / guiScale);
                        int itemX = guiScaledWidth / 2 - 8;
                        int itemY = Math.round(guiScaledHeight * 0.120625F) - 8;

                        var exGr = new ExtendedGuiGraphics(gr);
                        exGr.setGradient(ExtendedGuiGraphics.Orientation.TL_BR,
                                Color.argb(128, 45, 212, 191),
                                Color.argb(255, 14, 165, 233));
                        exGr.fillRoundRect(
                                itemX - 1, itemY - 1, itemX + 17, itemY + 17,
                                3
                        );
                        // the text should not be clipped
                        gr.drawString(Minecraft.getInstance().font,
                                "A", itemX, itemY, ~0);

                        gr.renderItem(mItem, itemX, itemY);
                    }
                });
                LayoutParams params = new LayoutParams(mSize * 4, mSize * 5, Gravity.CENTER);
                addView(mItemView, params);
            }
            setWillNotDraw(false);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            mRodAnimator.start();
        }

        @Override
        protected void onDraw(@Nonnull Canvas canvas) {
            Paint paint = Paint.obtain();
            paint.setColor(NETWORK_COLOR);
            paint.setAlpha(192);

            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;

            int boxAlpha = mBoxPaint.getAlpha();

            float px1l = centerX - (15 / 64f) * mSize;
            float py1 = centerY + (8 / 64f) * mSize;
            canvas.save();
            canvas.rotate(22.5f, px1l, py1);
            canvas.drawLine(px1l, py1, px1l - mRodLength * 2, py1, mSize / 8f, paint);
            canvas.restore();

            if (boxAlpha > 0) {
                canvas.drawRect(px1l - mSize * 2.9f, py1 - mSize * 1.1f,
                        px1l - mSize * 1.9f, py1 - mSize * 0.1f, mBoxPaint);
            }

            float px1r = centerX + (15 / 64f) * mSize;
            canvas.save();
            canvas.rotate(-22.5f, px1r, py1);
            canvas.drawLine(px1r, py1, px1r + mRodLength * 2, py1, mSize / 8f, paint);
            canvas.restore();

            if (boxAlpha > 0) {
                canvas.drawRect(px1r + mSize * 1.9f, py1 - mSize * 1.1f,
                        px1r + mSize * 2.9f, py1 - mSize * 0.1f, mBoxPaint);
            }

            float py2 = centerY + (19 / 64f) * mSize;
            canvas.drawLine(centerX, py2, centerX, py2 + mRodLength, mSize / 8f, paint);

            if (boxAlpha > 0) {
                canvas.drawRect(centerX - mSize * .5f, py2 + mSize * 1.1f,
                        centerX + mSize * .5f, py2 + mSize * 2.1f, mBoxPaint);
            }

            float offset = mSize / 2f;
            canvas.drawImage(mImage, 0, 192, 64, 256,
                    centerX - offset, centerY - offset,
                    centerX + offset, centerY + offset, null);

            canvas.save();
            canvas.rotate(-22.5f, px1l, py1);
            canvas.drawLine(px1l, py1, px1l - mRodLength * 2, py1, mSize / 8f, paint);
            canvas.restore();

            if (boxAlpha > 0) {
                canvas.drawRect(px1l - mSize * 2.9f, py1 + mSize * 0.1f,
                        px1l - mSize * 1.9f, py1 + mSize * 1.1f, mBoxPaint);
            }

            canvas.save();
            canvas.rotate(22.5f, px1r, py1);
            canvas.drawLine(px1r, py1, px1r + mRodLength * 2, py1, mSize / 8f, paint);
            canvas.restore();

            if (boxAlpha > 0) {
                canvas.drawRect(px1r + mSize * 1.9f, py1 + mSize * 0.1f,
                        px1r + mSize * 2.9f, py1 + mSize * 1.1f, mBoxPaint);
            }

            py2 = centerY - (19 / 64f) * mSize;
            canvas.drawLine(centerX, py2, centerX, py2 - mRodLength, mSize / 8f, paint);

            if (boxAlpha > 0) {
                canvas.drawRect(centerX - mSize * .5f, py2 - mSize * 2.1f,
                        centerX + mSize * .5f, py2 - mSize * 1.1f, mBoxPaint);
            }
            paint.recycle();
        }
    }
}
