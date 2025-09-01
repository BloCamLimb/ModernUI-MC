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

package icyllis.modernui.mc.forge;

import icyllis.modernui.mc.ConfigItem;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.Range;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

public class ForgeConfigItem<T> extends ConfigItem<T> {

    private static final Method RANGE_MIN;
    private static final Method RANGE_MAX;

    static {
        try {
            var clz = Class.forName("net.minecraftforge.common.ForgeConfigSpec$Range");
            var min = clz.getDeclaredMethod("getMin", (Class<?>[]) null);
            min.setAccessible(true);
            RANGE_MIN = min;
            var max = clz.getDeclaredMethod("getMax", (Class<?>[]) null);
            max.setAccessible(true);
            RANGE_MAX = max;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final ForgeConfigSpec.ConfigValue<T> value;
    private final ForgeConfigSpec.ValueSpec spec;

    public ForgeConfigItem(ForgeConfigSpec.ConfigValue<T> value,
                           ForgeConfigSpec.ValueSpec spec) {
        this.value = value;
        this.spec = spec;
    }

    @Override
    public T get() {
        return value.get();
    }

    @Override
    public List<String> getPath() {
        return value.getPath();
    }

    @Override
    public void set(T value) {
        this.value.set(value);
    }

    @Override
    public T getDefault() {
        return value.getDefault();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Range<T> getRange() {
        Predicate<Object> r = spec.getRange();
        if (r != null) {
            try {
                Object min = RANGE_MIN.invoke(r, (Object[]) null);
                Object max = RANGE_MAX.invoke(r, (Object[]) null);
                return (Range<T>) Range.between(min, max, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
