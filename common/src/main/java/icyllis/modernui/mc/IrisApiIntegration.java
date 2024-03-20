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

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Method;

@ApiStatus.Internal
public final class IrisApiIntegration {

    private static Object irisApiInstance;
    private static Method isShaderPackInUse;

    static {
        try {
            Class<?> clazz = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApiInstance = clazz.getMethod("getInstance").invoke(null);
            isShaderPackInUse = clazz.getMethod("isShaderPackInUse");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private IrisApiIntegration() {
    }

    public static boolean isShaderPackInUse() {
        if (isShaderPackInUse != null) {
            try {
                return (boolean) isShaderPackInUse.invoke(irisApiInstance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
