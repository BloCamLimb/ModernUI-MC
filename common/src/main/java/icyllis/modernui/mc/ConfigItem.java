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

import org.apache.commons.lang3.Range;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Abstraction of combination of ConfigValue and ValueSpec.
 *
 * @param <T> value type
 */
public abstract class ConfigItem<T> implements Supplier<T>, Consumer<T> {

    /**
     * Returns the value's path, each element of the list is a different part of the path.
     */
    public abstract List<String> getPath();

    /**
     * Delegate to {@link #set(Object)}.
     */
    @Override
    public final void accept(T t) {
        set(t);
    }

    /**
     * Sets the raw value, without reload events or saving to disk.
     */
    public abstract void set(T value);

    public abstract T getDefault();

    @Nullable
    public abstract Range<T> getRange();
}
