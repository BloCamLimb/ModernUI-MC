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

package icyllis.modernui.mc.fabric;

import icyllis.modernui.mc.ConfigItem;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.Range;

import java.util.List;

public class ForgeConfigItem<T> extends ConfigItem<T> {

    private final ModConfigSpec.ConfigValue<T> value;
    private final ModConfigSpec.ValueSpec spec;

    public ForgeConfigItem(ModConfigSpec.ConfigValue<T> value,
                           ModConfigSpec.ValueSpec spec) {
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
        ModConfigSpec.Range<Comparable<Object>> r = spec.getRange();
        if (r != null) {
            return (Range<T>) Range.of(r.getMin(), r.getMax());
        }
        return null;
    }
}
