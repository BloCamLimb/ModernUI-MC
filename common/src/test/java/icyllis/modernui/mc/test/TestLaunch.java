/*
 * Modern UI.
 * Copyright (C) 2026 BloCamLimb. All rights reserved.
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

package icyllis.modernui.mc.test;

import icyllis.modernui.ModernUI;
import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.mc.ui.MusicFragment;

public class TestLaunch {

    public static void main(String[] args) {
        AudioManager.getInstance().initialize();
        try (var app = new ModernUI()) {
            app.run(new MusicFragment());
        }
        AudioManager.getInstance().close();
    }
}
