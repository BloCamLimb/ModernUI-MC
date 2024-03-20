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

import icyllis.modernui.util.*;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * Contains {@link DataSet} and {@link CompoundTag} utilities.
 */
public final class BinaryDataUtils {

    private BinaryDataUtils() {
    }

    /**
     * Write the data set to the given byte buf.
     *
     * @param buf    the target byte buf
     * @param source the source data set
     * @return the byte buf as a convenience
     */
    @Nonnull
    public static FriendlyByteBuf writeDataSet(@Nonnull FriendlyByteBuf buf, @Nullable DataSet source) {
        try (var p = new IOStreamParcel(null, new ByteBufOutputStream(buf))) {
            p.writeDataSet(source);
        } catch (Exception e) {
            throw new EncoderException(e);
        }
        return buf;
    }

    /**
     * Read a data set from the given byte buf.
     *
     * @param buf the source byte buf
     * @return the decoded data set
     */
    @Nullable
    public static DataSet readDataSet(@Nonnull FriendlyByteBuf buf, @Nullable ClassLoader loader) {
        try (var p = new IOStreamParcel(new ByteBufInputStream(buf), null)) {
            return p.readDataSet(loader);
        } catch (Exception e) {
            throw new DecoderException(e);
        }
    }

    /*
     * Write only the string mapping of the given data set to the target compound tag.
     * ByteList, IntList and LongList will be converted to their ArrayTags. If one of
     * these lists nested in the parent list, then it will be silently ignored.
     *
     * @param source the source data set
     * @param dest   the target compound tag
     * @return the compound tag as a convenience
     */
    /*@Nonnull
    public static CompoundTag writeDataSet(@Nonnull DataSet source, @Nonnull CompoundTag dest) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            final Object v = entry.getValue();
            if (v instanceof Byte) {
                dest.putByte(entry.getKey(), (byte) v);
            } else if (v instanceof Short) {
                dest.putShort(entry.getKey(), (short) v);
            } else if (v instanceof Integer) {
                dest.putInt(entry.getKey(), (int) v);
            } else if (v instanceof Long) {
                dest.putLong(entry.getKey(), (long) v);
            } else if (v instanceof Float) {
                dest.putFloat(entry.getKey(), (float) v);
            } else if (v instanceof Double) {
                dest.putDouble(entry.getKey(), (double) v);
            } else if (v instanceof String) {
                dest.putString(entry.getKey(), (String) v);
            } else if (v instanceof UUID) {
                dest.putUUID(entry.getKey(), (UUID) v);
            } else if (v instanceof byte[]) {
                dest.putByteArray(entry.getKey(), (byte[]) v);
            } else if (v instanceof int[]) {
                dest.putIntArray(entry.getKey(), (int[]) v);
            } else if (v instanceof long[]) {
                dest.putLongArray(entry.getKey(), (long[]) v);
            } else if (v instanceof List) {
                dest.put(entry.getKey(), writeList((List<?>) v, new ListTag()));
            } else if (v instanceof DataSet) {
                dest.put(entry.getKey(), writeDataSet((DataSet) v, new CompoundTag()));
            }
        }
        return dest;
    }

    @Nonnull
    private static ListTag writeList(@Nonnull List<?> list, @Nonnull ListTag tag) {
        if (list.isEmpty()) {
            return tag;
        }
        if (list instanceof ShortArrayList) {
            for (short v : (ShortArrayList) list) {
                tag.add(ShortTag.valueOf(v));
            }
        } else if (list instanceof FloatArrayList) {
            for (float v : (FloatArrayList) list) {
                tag.add(FloatTag.valueOf(v));
            }
        } else if (list instanceof DoubleArrayList) {
            for (double v : (DoubleArrayList) list) {
                tag.add(DoubleTag.valueOf(v));
            }
        } else {
            final Object e = list.get(0);
            if (e instanceof String) {
                for (String s : (List<String>) list) {
                    tag.add(StringTag.valueOf(s));
                }
            } else if (e instanceof UUID) {
                for (UUID u : (List<UUID>) list) {
                    tag.add(NbtUtils.createUUID(u));
                }
            } else if (e instanceof List) {
                for (List<?> li : (List<List<?>>) list) {
                    tag.add(writeList(li, new ListTag()));
                }
            } else if (e instanceof DataSet) {
                for (DataSet set : (List<DataSet>) list) {
                    tag.add(writeDataSet(set, new CompoundTag()));
                }
            }
        }
        return tag;
    }*/
}
