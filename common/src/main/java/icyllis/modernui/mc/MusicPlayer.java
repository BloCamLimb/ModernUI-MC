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

import icyllis.modernui.ModernUI;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.audio.*;
import icyllis.modernui.core.Core;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MusicPlayer {

    private static volatile MusicPlayer sInstance;

    private Track mCurrentTrack;
    private FFT mFFT;
    private float mGain = 1.0f;

    private String mName;

    private Consumer<Track> mOnTrackLoadCallback;

    public static MusicPlayer getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
        synchronized (MusicPlayer.class) {
            if (sInstance == null) {
                sInstance = new MusicPlayer();
            }
        }
        return sInstance;
    }

    private MusicPlayer() {
    }

    public static String openDialogGet() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            stack.nUTF8("*.ogg", true);
            filters.put(stack.getPointerAddress());
            filters.rewind();
            return TinyFileDialogs.tinyfd_openFileDialog(null, null,
                    filters, "Ogg Vorbis (*.ogg)", false);
        }
    }

    public void clearTrack() {
        if (mCurrentTrack != null) {
            mCurrentTrack.close();
            mCurrentTrack = null;
        }
        mName = null;
    }

    public void replaceTrack(Path path) {
        clearTrack();
        CompletableFuture.supplyAsync(() -> {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                ByteBuffer nativeEncodedData = Core.readIntoNativeBuffer(channel).flip();
                VorbisPullDecoder decoder = new VorbisPullDecoder(nativeEncodedData);
                return new Track(decoder);
            } catch (IOException e) {
                ModernUI.LOGGER.error("Failed to open Ogg Vorbis, {}", path, e);
                return null;
            }
        }).whenCompleteAsync((track, ex) -> {
            mCurrentTrack = track;
            if (track != null) {
                track.setGain(mGain);
                mName = path.getFileName().toString();
            }
            if (mOnTrackLoadCallback != null) {
                mOnTrackLoadCallback.accept(track);
            }
        }, Core.getUiThreadExecutor());
    }

    public void setOnTrackLoadCallback(Consumer<Track> onTrackLoadCallback) {
        mOnTrackLoadCallback = onTrackLoadCallback;
    }

    @Nullable
    public String getTrackName() {
        return mName;
    }

    public boolean hasTrack() {
        return mCurrentTrack != null;
    }

    public float getTrackTime() {
        if (mCurrentTrack != null) {
            return mCurrentTrack.getTime();
        }
        return 0;
    }

    public float getTrackLength() {
        if (mCurrentTrack != null) {
            return mCurrentTrack.getLength();
        }
        return 0;
    }

    public void play() {
        if (mCurrentTrack != null) {
            mCurrentTrack.play();
        }
    }

    public void pause() {
        if (mCurrentTrack != null) {
            mCurrentTrack.pause();
        }
    }

    public boolean isPlaying() {
        if (mCurrentTrack != null) {
            return mCurrentTrack.isPlaying();
        }
        return false;
    }

    public boolean seek(float fraction) {
        if (mCurrentTrack != null) {
            return mCurrentTrack.seekToSeconds(fraction * mCurrentTrack.getLength());
        }
        return true;
    }

    public void setGain(float gain) {
        if (mGain != gain) {
            mGain = gain;
            if (mCurrentTrack != null) {
                mCurrentTrack.setGain(gain);
            }
        }
    }

    public float getGain() {
        return mGain;
    }

    public void setAnalyzerCallback(Consumer<FFT> setup, Consumer<FFT> callback) {
        if (mCurrentTrack == null) {
            return;
        }
        if (setup == null && callback == null) {
            mCurrentTrack.setAnalyzer(null, null);
        } else {
            if (mFFT == null || mFFT.getSampleRate() != mCurrentTrack.getSampleRate()) {
                mFFT = FFT.create(1024, mCurrentTrack.getSampleRate());
            }
            if (setup != null) {
                setup.accept(mFFT);
            }
            mCurrentTrack.setAnalyzer(mFFT, callback);
        }
    }
}
