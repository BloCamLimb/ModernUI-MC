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

import icyllis.modernui.animation.AnimationUtils;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.audio.FFT;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

import java.nio.file.Path;
import java.util.*;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicFragment extends Fragment {

    private MusicController mMusicController;
    private SpectrumDrawable mSpectrumDrawable;

    private Button mTitleButton;
    private Button mPlayButton;
    private SeekLayout mSeekLayout;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mMusicController = MusicController.getInstance();
        mMusicController.setOnTrackLoadCallback(track -> {
            if (track != null) {
                mMusicController.setAnalyzerCallback(
                        fft -> {
                            fft.setLogAverages(250, 14);
                            fft.setWindowFunc(FFT.NONE);
                        },
                        mSpectrumDrawable::updateAmplitudes
                );
                track.play();
                mPlayButton.setText("\u23F8");
            } else {
                mPlayButton.setText("\u23F5");
                Toast.makeText(requireContext(),
                        "Failed to open Ogg Vorbis file", Toast.LENGTH_SHORT).show();
            }
            var trackName = mMusicController.getTrackName();
            mTitleButton.setText(Objects.requireNonNullElse(trackName, "Play A Music!"));
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mMusicController.setAnalyzerCallback(null, null);
        if (!mMusicController.isPlaying()) {
            mMusicController.clearTrack();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        mSpectrumDrawable = new SpectrumDrawable(
                content.dp(14),
                content.dp(2),
                content.dp(640)
        );
        content.setBackground(mSpectrumDrawable);
        content.setGravity(Gravity.CENTER);

        mMusicController.setAnalyzerCallback(null, mSpectrumDrawable::updateAmplitudes);

        {
            var button = new Button(requireContext());
            button.setOnClickListener(v -> {
                String path = MusicController.openDialogGet();
                if (path != null) {
                    mMusicController.replaceTrack(Path.of(path));
                }
            });
            var trackName = mMusicController.getTrackName();
            button.setText(Objects.requireNonNullElse(trackName, "Play A Music!"));
            button.setTextSize(16f);
            button.setTextColor(0xFF28A3F3);
            button.setPadding(0, content.dp(4), 0, content.dp(4));
            button.setMinWidth(button.dp(200));
            mTitleButton = button;

            content.addView(button, WRAP_CONTENT, WRAP_CONTENT);
        }

        {
            var button = new Button(requireContext());
            button.setOnClickListener(v -> {
                var btn = (Button) v;
                if (mMusicController.isPlaying()) {
                    mMusicController.pause();
                    btn.setText("\u23F5");
                } else {
                    mMusicController.play();
                    btn.setText("\u23F8");
                }
            });
            if (mMusicController.isPlaying()) {
                button.setText("\u23F8");
            } else {
                button.setText("\u23F5");
            }
            button.setTextSize(24f);
            button.setMinWidth(button.dp(200));
            mPlayButton = button;

            content.addView(button, WRAP_CONTENT, WRAP_CONTENT);
        }

        {
            var seekLayout = new SeekLayout(requireContext());
            mSeekLayout = seekLayout;
            content.addView(seekLayout);

            seekLayout.post(this::updateProgress);
            seekLayout.mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    mMusicController.pause();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    mMusicController.seek(seekBar.getProgress() / 10000f);
                    mMusicController.play();
                }
            });
        }

        return content;
    }

    private void updateProgress() {
        if (mMusicController.isPlaying()) {
            float time = mMusicController.getTrackTime();
            float length = mMusicController.getTrackLength();
            mSeekLayout.mCurTime.setText(formatTime((int) time));
            mSeekLayout.mSeekBar.setProgress((int) (time / length * 10000));
            mSeekLayout.mMaxTime.setText(formatTime((int) length));
        }
        mSeekLayout.postDelayed(this::updateProgress, 200);
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        seconds -= minutes * 60;
        int hours = minutes / 60;
        minutes -= hours * 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    public static class SeekLayout extends LinearLayout {

        TextView mCurTime;
        SeekBar mSeekBar;
        TextView mMaxTime;

        public SeekLayout(Context context) {
            super(context);
            setOrientation(HORIZONTAL);

            mCurTime = new TextView(context);
            mCurTime.setTextSize(12);
            mCurTime.setMinWidth(dp(60));
            mCurTime.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);

            addView(mCurTime);

            mSeekBar = new SeekBar(context);
            mSeekBar.setMax(10000);
            var lp = new LayoutParams(LayoutParams.MATCH_PARENT, WRAP_CONTENT);
            lp.weight = 1;
            mSeekBar.setLayoutParams(lp);

            addView(mSeekBar);

            mMaxTime = new TextView(context);
            mMaxTime.setTextSize(12);
            mMaxTime.setMinWidth(dp(60));
            mMaxTime.setTextAlignment(TEXT_ALIGNMENT_VIEW_END);

            addView(mMaxTime);

            setGravity(Gravity.CENTER);
        }
    }

    public static class SpectrumDrawable extends Drawable {

        private static final Random RANDOM = new Random();

        private static final int AMPLITUDE_LENGTH = 60;

        private final float[] mAmplitudes = new float[AMPLITUDE_LENGTH];

        private volatile int mActualAmplitudeLength;

        private long mLastAnimationTime;

        private final Runnable mAnimationRunnable = this::invalidateSelf;

        private final int mBandWidth;
        private final int mBandGap;
        private final int mBandHeight;

        private float mLastBassAmplitude;

        private static class Particle {
            float x;
            float y;
            float xVel;
            float yVel;

            Particle(float x, float y, float xVel, float yVel) {
                this.x = x;
                this.y = y;
                this.xVel = xVel;
                this.yVel = yVel;
            }
        }

        private static final int MAX_PARTICLES = 60;

        private final List<Particle> mParticleList = new ArrayList<>(MAX_PARTICLES);

        public SpectrumDrawable(int bandWidth, int bandGap, int bandHeight) {
            mBandWidth = bandWidth;
            mBandGap = bandGap;
            mBandHeight = bandHeight;
        }

        // called from audio thread
        public void updateAmplitudes(FFT fft) {
            final float[] amplitudes = mAmplitudes;
            final int len = Math.min(fft.getAverageSize() - 5, AMPLITUDE_LENGTH);

            for (int i = 0; i < len; i++) {
                float value = fft.getAverage((i % len) + 5) / fft.getBandSize();
                amplitudes[i] = Math.max(amplitudes[i], value);
            }
            mActualAmplitudeLength = len;

            final long now = Core.timeMillis();
            scheduleSelf(mAnimationRunnable, now);
        }

        private void computeParticles(float multiplier, long deltaMillis) {
            float delta = deltaMillis / 1000f;

            for (var it = mParticleList.listIterator(); it.hasNext(); ) {
                var p = it.next();
                float newX = p.x + p.xVel * delta;
                float newY = p.y + p.yVel * delta;
                if (newY < -0.1) {
                    it.remove();
                    continue;
                }
                if (newX < 0 || newX > 1) {
                    float d = Math.max(0 - newX, newX - 1);
                    if (newX < 0) {
                        newX = d;
                    } else {
                        newX = 1 - d;
                    }
                    p.xVel = -p.xVel * 0.8f;
                }
                p.x = newX;
                p.y = newY;
                float velSq = p.xVel * p.xVel + p.yVel * p.yVel;
                float vel = (float) Math.sqrt(velSq);
                float xVelSign = Math.signum(p.xVel);
                float yVelSign = Math.signum(p.yVel);
                if (vel > 0.00001f) {
                    p.xVel = Math.max(0, Math.abs(p.xVel) - (Math.abs(p.xVel) / vel) * velSq * 0.003f) * xVelSign;
                }
                {
                    float y1 = 0;
                    if (vel > 0.00001f) {
                        y1 = Math.max(0, Math.abs(p.yVel) - (Math.abs(p.yVel) / vel) * velSq * 0.003f) * yVelSign;
                    }
                    float y2 = p.yVel - 0.5f * delta;
                    p.yVel = Math.min(y1, y2);
                }
            }

            if ((mLastBassAmplitude >= 0.024f && multiplier >= 1.25f) ||
                    (mLastBassAmplitude >= 0.016f && multiplier >= 2.5f) ||
                    (mLastBassAmplitude >= 0.008f && multiplier >= 3.75f)) {
                int count = 6;
                if (mLastBassAmplitude >= 0.024f) {
                    count = (int) Math.min(multiplier * 3, count);
                } else if (mLastBassAmplitude >= 0.016f) {
                    count = (int) Math.min(multiplier * 2, count);
                } else {
                    count = (int) Math.min(multiplier, count);
                }
                count = Math.min(count, MAX_PARTICLES - mParticleList.size());
                while (count-- != 0) {
                    boolean leftSide = RANDOM.nextBoolean();
                    float x = leftSide ? 0 : 1;
                    float y = RANDOM.nextFloat() * 0.6f + 0.25f;
                    float xVel = RANDOM.nextFloat() * 0.2f + 0.1f;
                    if (!leftSide) {
                        xVel = -xVel;
                    }
                    float vel = Math.min(0.08f * multiplier, 0.3f);
                    float yVel = (float) Math.sqrt(vel - xVel * xVel);
                    mParticleList.add(new Particle(x, y, xVel, yVel));
                }
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            var b = getBounds();

            int contentCenter = (mBandWidth * AMPLITUDE_LENGTH + mBandGap * (AMPLITUDE_LENGTH - 1)) / 2;
            float x = b.centerX() - contentCenter;
            float bottom = b.bottom - mBandWidth;

            var paint = Paint.obtain();

            long time = AnimationUtils.currentAnimationTimeMillis();
            long delta = time - mLastAnimationTime;
            mLastAnimationTime = time;

            final float[] amplitudes = mAmplitudes;
            final int len = mActualAmplitudeLength;

            boolean invalidate = false;
            for (int i = 0; i < len; i++) {
                if (amplitudes[i] > 0) {
                    invalidate = true;
                    break;
                }
            }

            for (int i = 0; i < len; i++) {
                // 2.5e-5f * BPM
                amplitudes[i] = Math.max(0, amplitudes[i] - delta * 2.5e-5f * 180f * (amplitudes[i] + 0.03f));
            }

            int bassLen = len / 5;
            float bassAmplitude = 0;
            for (int i = 0; i < bassLen; i++) {
                bassAmplitude += amplitudes[i];
            }
            bassAmplitude /= bassLen;
            float multiplier = bassAmplitude / mLastBassAmplitude;
            computeParticles(multiplier, delta);
            mLastBassAmplitude = bassAmplitude;
            if (!mParticleList.isEmpty()) {
                invalidate = true;
            }

            float alphaMult = 1.5f + MathUtil.sin(time / 600f) / 2;
            paint.setRGBA(160, 155, 230, (int) (64 * alphaMult));
            float radius = mBandHeight * 0.05f;
            paint.setSmoothWidth(radius * 2.2f);
            for (Particle p : mParticleList) {
                canvas.drawCircle(
                        b.x() + p.x * b.width(),
                        b.y() + (1 - p.y) * b.height(),
                        radius,
                        paint
                );
            }
            paint.setSmoothWidth(0);

            for (int i = 0; i < AMPLITUDE_LENGTH; i++) {
                paint.setRGBA(100 + i * 2, 220 - i * 2, 240 - i * 4, 255);
                canvas.drawRect(x, bottom - amplitudes[i] * mBandHeight, x + mBandWidth, bottom, paint);
                x += mBandWidth + mBandGap;
            }
            paint.recycle();

            if (invalidate) {
                invalidateSelf();
            }
        }

        @Override
        public int getIntrinsicWidth() {
            return mBandWidth * AMPLITUDE_LENGTH + mBandGap * (AMPLITUDE_LENGTH - 1);
        }

        @Override
        public int getIntrinsicHeight() {
            return mBandHeight;
        }

        @Override
        public boolean getPadding(@NonNull Rect padding) {
            int pad = mBandWidth;
            padding.set(pad, pad, pad, pad);
            return true;
        }
    }
}
