/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 7071819 8221431 8239383 8273430
 * @summary tests Unicode Extended Grapheme support
 * @library /lib/testlibrary/java/lang
 * @build java.base/java.util.regex.GraphemeTestAccessor
 * @run testng GraphemeTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.annotations.Test;
import static org.testng.Assert.fail;
import static org.testng.Assert.assertFalse;
import java.util.regex.GraphemeTestAccessor;

public class GraphemeTest {

    @Test
    public static void testGraphemeBreakProperty() throws Throwable {
        testProps(UCDFiles.GRAPHEME_BREAK_PROPERTY);
    }

    @Test
    public static void testEmojiData() throws Throwable {
        testProps(UCDFiles.EMOJI_DATA);
    }

    @Test
    public static void testExcludedSpacingMarks() {
        assertFalse(GraphemeTestAccessor.isExcludedSpacingMark(0x1065));
        assertFalse(GraphemeTestAccessor.isExcludedSpacingMark(0x1066));
    }

    private static void testProps(Path path) throws IOException {
        Files.lines(path)
                .map(ln -> ln.replaceFirst("#.*", ""))
                .filter(ln -> ln.length() != 0)
                .forEach(ln -> {
                    String[] strs = ln.split("\\s+");
                    int off = strs[0].indexOf("..");
                    int cp0, cp1;
                    String expected = strs[2];
                    if (off != -1) {
                        cp0 = Integer.parseInt(strs[0], 0, off, 16);
                        cp1 = Integer.parseInt(strs[0], off + 2, strs[0].length(), 16);
                    } else {
                        cp0 = cp1 = Integer.parseInt(strs[0], 16);
                    }
                    for (int cp = cp0; cp <= cp1; cp++) {
                        // Ignore Emoji* for now (only interested in Extended_Pictographic)
                        if (expected.startsWith("Emoji")) {
                            continue;
                        }

                        // NOTE:
                        // #tr29 "plus a few General_Category = Spacing_Mark needed for
                        // canonical equivalence."
                        // For "extended grapheme clusters" support, there is no
                        // need actually to diff "extend" and "spackmark" given GB9, GB9a.
                        if (!expected.equals(types[GraphemeTestAccessor.getType(cp)])) {
                            if ("Extend".equals(expected) &&
                                    "SpacingMark".equals(types[GraphemeTestAccessor.getType(cp)]))
                                System.out.printf("[%x]  [%s][%d] -> [%s]%n",
                                        cp, expected, Character.getType(cp), types[GraphemeTestAccessor.getType(cp)]);
                            else
                                fail(String.format(
                                        "cp=[%x], expected:[%s] result:[%s]%n",
                                        cp, expected, types[GraphemeTestAccessor.getType(cp)]));
                        }
                    }
                });
    }

    private static final String[] types = {
            "Other", "CR", "LF", "Control", "Extend", "ZWJ", "Regional_Indicator",
            "Prepend", "SpacingMark",
            "L", "V", "T", "LV", "LVT",
            "Extended_Pictographic"};
}

