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

package icyllis.modernui.mc.text;

import com.google.gson.*;
import icyllis.modernui.graphics.text.Emoji;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generate emoji_data.json, the code is dirty.
 *
 * @version Unicode 15.0
 */
public class EmojiDataGen {

    public static void main(String[] args) {
        // https://raw.githubusercontent.com/iamcal/emoji-data/master/emoji.json
        // https://raw.githubusercontent.com/joypixels/emoji-toolkit/master/emoji.json
        // https://raw.githubusercontent.com/googlefonts/emoji-metadata/main/emoji_15_0_ordering.json
        final String iam_cal, joy_pixels, google_fonts, output;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            stack.nUTF8("*.json", true);
            filters.put(stack.getPointerAddress());
            filters.rewind();
            iam_cal = TinyFileDialogs.tinyfd_openFileDialog("Open IamCal", null,
                    filters, "JSON File", false);
            joy_pixels = TinyFileDialogs.tinyfd_openFileDialog("Open JoyPixels", null,
                    filters, "JSON File", false);
            google_fonts = TinyFileDialogs.tinyfd_openFileDialog("Open GoogleFonts", null,
                    filters, "JSON File", false);
            output = TinyFileDialogs.tinyfd_saveFileDialog(null, "emoji_data.json",
                    filters, "JSON File");
        }
        if (iam_cal != null && joy_pixels != null && google_fonts != null && output != null) {
            var gson = new Gson();
            var iam_cal_data = read(gson, iam_cal, EmojiEntry[].class);
            var joy_pixels_data = read(gson, joy_pixels, JsonObject.class);
            var google_fonts_data = read(gson, google_fonts, JsonArray.class);
            var map = Arrays.stream(iam_cal_data)
                    .collect(Collectors.toMap(e -> e.unified.toLowerCase(Locale.ROOT), Function.identity(),
                            (x, y) -> x, LinkedHashMap::new));

            for (var e : joy_pixels_data.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                JsonObject ci = o.getAsJsonObject("code_points");
                var emoji = map.computeIfAbsent(
                        ci.get("fully_qualified").getAsString(),
                        EmojiEntry::new);
                emoji.short_names.add(stripColons(o.get("shortname").getAsString()));
                for (var s : o.get("shortname_alternates").getAsJsonArray()) {
                    emoji.short_names.add(stripColons(s.getAsString()));
                }
            }

            for (var e : google_fonts_data) {
                for (var e1 : e.getAsJsonObject().get("emoji").getAsJsonArray()) {
                    JsonObject o = e1.getAsJsonObject();
                    JsonArray ci = o.get("base").getAsJsonArray();
                    int[] cps = new int[ci.size()];
                    for (int i = 0; i < cps.length; i++) {
                        cps[i] = Integer.parseInt(ci.get(i).getAsString());
                    }
                    var emoji = map.computeIfAbsent(
                            Arrays.stream(cps).mapToObj(Integer::toHexString)
                                    .collect(Collectors.joining("-")),
                            EmojiEntry::new);
                    for (var s : o.get("shortcodes").getAsJsonArray()) {
                        emoji.short_names.add(stripColons(s.getAsString()));
                    }
                }
            }

            var output_data = map.values().stream()
                    .filter(EmojiEntry::validate)
                    .map(EmojiEntry::flatten)
                    .collect(Collectors.toList());

            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(output), StandardCharsets.UTF_8))) {
                gson.toJson(output_data, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static <T> T read(Gson gson, String file, Class<T> type) {
        try (Reader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            return gson.fromJson(reader, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String stripColons(String s) {
        return s.substring(1, s.length() - 1);
    }

    public static class EmojiEntry {

        public String unified;
        public String name;
        public Set<String> short_names;
        public String category;
        public String subcategory;
        public int sort_order;
        public String added_in;

        public EmojiEntry(String unified) {
            this.unified = unified;
            short_names = new LinkedHashSet<>();
        }

        public boolean validate() {
            return !Emoji.isRegionalIndicatorSymbol(Integer.parseInt(unified.split("-")[0], 16));
        }

        public Object[] flatten() {
            int[] cps = Arrays.stream(unified.split("-"))
                    .mapToInt(c -> Integer.parseInt(c, 16))
                    .toArray();
            Object[] row = new Object[7];
            row[0] = new String(cps, 0, cps.length);
            row[1] = name;
            row[2] = short_names;
            row[3] = category;
            row[4] = subcategory;
            row[5] = sort_order;
            row[6] = added_in;
            return row;
        }
    }
}
