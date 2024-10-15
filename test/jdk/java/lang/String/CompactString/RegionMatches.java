/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;
import static org.testng.Assert.assertEquals;

/*
 * @test
 * @bug 8077559 8248655 8264544
 * @summary Tests Compact String. This one is for String.regionMatches.
 * @run testng/othervm -XX:+CompactStrings RegionMatches
 * @run testng/othervm -XX:-CompactStrings RegionMatches
 */

public class RegionMatches extends CompactString {

    @DataProvider
    public Object[][] provider() {
        return new Object[][] {

                new Object[] { STRING_EMPTY, true, 0, "", 0, 0, true },
                new Object[] { STRING_EMPTY, true, 0, "", 0, 1, false },
                new Object[] { STRING_EMPTY, true, 0, "A", 0, 0, true },
                new Object[] { STRING_EMPTY, true, 0, "", 0, 0, true },
                new Object[] { STRING_EMPTY, true, 0, "", 0, 1, false },
                new Object[] { STRING_L1, false, 0, "a", 0, 1, false },
                new Object[] { STRING_L1, false, 0, "BA", 1, 1, true },
                new Object[] { STRING_L1, false, 0, "Ba", 1, 1, false },
                new Object[] { STRING_L1, true, 0, "a", 0, 1, true },
                new Object[] { STRING_L1, true, 0, "BA", 1, 1, true },
                new Object[] { STRING_L1, true, 0, "Ba", 1, 1, true },
                new Object[] { STRING_L2, true, 1, "b", 0, 1, true },
                new Object[] { STRING_L2, true, 1, "B", 0, 1, true },
                new Object[] { STRING_L2, true, 0, "xaBc", 1, 2, true },
                new Object[] { STRING_L2, false, 0, "AB", 0, 2, true },
                new Object[] { STRING_L2, false, 0, "Ab", 0, 2, false },
                new Object[] { STRING_L2, false, 1, "BAB", 2, 1, true },
                new Object[] { STRING_LLONG, true, 1, "bCdEF", 0, 5, true },
                new Object[] { STRING_LLONG, false, 2, "CDEFG", 0, 5, true },
                new Object[] { STRING_LLONG, true, 2, "CDEFg", 0, 5, true },
                new Object[] { STRING_U1, true, 0, "\uFF41", 0, 1, true },
                new Object[] { STRING_U1, false, 0, "\uFF41", 0, 1, false },
                new Object[] { STRING_MDUPLICATE1, true, 0, "\uFF41a\uFF41", 0,
                        3, true },
                new Object[] { STRING_MDUPLICATE1, false, 0, "\uFF21a\uFF21",
                        0, 3, false },
                new Object[] { STRING_SUPPLEMENTARY, true, 0, "\uD801\uDC28\uD801\uDC29",
                        0, 4, true },
                new Object[] { STRING_SUPPLEMENTARY, true, 1, "\uDC00\uD801",
                        0, 2, true },
                new Object[] { STRING_SUPPLEMENTARY, true, 1, "\uDC28",
                        0, 1, false },
                new Object[] { STRING_SUPPLEMENTARY, true, 4, "\uFF21", 0, 1,
                        true },
                new Object[] { STRING_SUPPLEMENTARY, true, 5, "A", 0, 1, true },
                new Object[] { STRING_SUPPLEMENTARY, true, 0, "\uD802\uDC00\uD801\uDC01\uFF21A", 0, 2, false },
                new Object[] { STRING_SUPPLEMENTARY_LOWERCASE, false, 0,
                        "\uD801\uDC28\uD801\uDC29", 0, 4, true },
                new Object[] { STRING_SUPPLEMENTARY_LOWERCASE, true, 0,
                        "\uD801\uDC00\uD801\uDC01", 0, 4, true },
                new Object[] { STRING_SUPPLEMENTARY_LOWERCASE, true, 1,
                        "\uDC28\uD801", 0, 2, true },
                new Object[] { STRING_SUPPLEMENTARY_LOWERCASE, true, 1,
                        "\uDC00", 0, 1, false },
                new Object[] { STRING_SUPPLEMENTARY_LOWERCASE, true, 1,
                        "\uDC00\uD801", 0, 2, false },
                new Object[] { STRING_SUPPLEMENTARY_LOWERCASE, true, 4,
                        "\uFF21", 0, 1, true },
                new Object[] { STRING_SUPPLEMENTARY_LOWERCASE, false, 4,
                        "\uFF21", 0, 1, false },
                new Object[] { STRING_SUPPLEMENTARY_LOWERCASE, false, 4,
                        "\uFF41", 0, 1, true },
                // Turkish I with dot
                new Object[] { "\u0130", false, 0, "i", 0, 1, false },
                new Object[] { "\u0130", true,  0, "i", 0, 1, true },
                new Object[] { "\u0130", false, 0, "I", 0, 1, false },
                new Object[] { "\u0130", true,  0, "I", 0, 1, true },
                // i without dot
                new Object[] { "\u0131", false, 0, "i", 0, 1, false },
                new Object[] { "\u0131", true,  0, "i", 0, 1, true },
                new Object[] { "\u0131", false, 0, "I", 0, 1, false },
                new Object[] { "\u0131", true,  0, "I", 0, 1, true },
                // Exhaustive list of 1-char latin1 strings that match
                // case-insensitively:
                // for (char c1 = 0; c1 < 255; c1++) {
                //   for (char c2 = c; c2 < 255; c2++) {
                //     if (c1 == c2) continue;
                //     if (String.valueOf(c1).regionMatches(true, 0, String.valueOf(c2), 0, 1)) {
                //       System.out.println("                new Object[] { \"\\u00"
                //                          + Integer.toHexString((int)c) + "\", true, 0, \"\\u00"
                //                          + Integer.toHexString((int)c2) + "\", 0, 1, true },");
                // }}}
                new Object[] { "\u0041", true, 0, "\u0061", 0, 1, true },
                new Object[] { "\u0042", true, 0, "\u0062", 0, 1, true },
                new Object[] { "\u0043", true, 0, "\u0063", 0, 1, true },
                new Object[] { "\u0044", true, 0, "\u0064", 0, 1, true },
                new Object[] { "\u0045", true, 0, "\u0065", 0, 1, true },
                new Object[] { "\u0046", true, 0, "\u0066", 0, 1, true },
                new Object[] { "\u0047", true, 0, "\u0067", 0, 1, true },
                new Object[] { "\u0048", true, 0, "\u0068", 0, 1, true },
                new Object[] { "\u0049", true, 0, "\u0069", 0, 1, true },
                new Object[] { "\u004a", true, 0, "\u006a", 0, 1, true },
                new Object[] { "\u004b", true, 0, "\u006b", 0, 1, true },
                new Object[] { "\u004c", true, 0, "\u006c", 0, 1, true },
                new Object[] { "\u004d", true, 0, "\u006d", 0, 1, true },
                new Object[] { "\u004e", true, 0, "\u006e", 0, 1, true },
                new Object[] { "\u004f", true, 0, "\u006f", 0, 1, true },
                new Object[] { "\u0050", true, 0, "\u0070", 0, 1, true },
                new Object[] { "\u0051", true, 0, "\u0071", 0, 1, true },
                new Object[] { "\u0052", true, 0, "\u0072", 0, 1, true },
                new Object[] { "\u0053", true, 0, "\u0073", 0, 1, true },
                new Object[] { "\u0054", true, 0, "\u0074", 0, 1, true },
                new Object[] { "\u0055", true, 0, "\u0075", 0, 1, true },
                new Object[] { "\u0056", true, 0, "\u0076", 0, 1, true },
                new Object[] { "\u0057", true, 0, "\u0077", 0, 1, true },
                new Object[] { "\u0058", true, 0, "\u0078", 0, 1, true },
                new Object[] { "\u0059", true, 0, "\u0079", 0, 1, true },
                new Object[] { "\u005a", true, 0, "\u007a", 0, 1, true },
                new Object[] { "\u00c0", true, 0, "\u00e0", 0, 1, true },
                new Object[] { "\u00c1", true, 0, "\u00e1", 0, 1, true },
                new Object[] { "\u00c2", true, 0, "\u00e2", 0, 1, true },
                new Object[] { "\u00c3", true, 0, "\u00e3", 0, 1, true },
                new Object[] { "\u00c4", true, 0, "\u00e4", 0, 1, true },
                new Object[] { "\u00c5", true, 0, "\u00e5", 0, 1, true },
                new Object[] { "\u00c6", true, 0, "\u00e6", 0, 1, true },
                new Object[] { "\u00c7", true, 0, "\u00e7", 0, 1, true },
                new Object[] { "\u00c8", true, 0, "\u00e8", 0, 1, true },
                new Object[] { "\u00c9", true, 0, "\u00e9", 0, 1, true },
                new Object[] { "\u00ca", true, 0, "\u00ea", 0, 1, true },
                new Object[] { "\u00cb", true, 0, "\u00eb", 0, 1, true },
                new Object[] { "\u00cc", true, 0, "\u00ec", 0, 1, true },
                new Object[] { "\u00cd", true, 0, "\u00ed", 0, 1, true },
                new Object[] { "\u00ce", true, 0, "\u00ee", 0, 1, true },
                new Object[] { "\u00cf", true, 0, "\u00ef", 0, 1, true },
                new Object[] { "\u00d0", true, 0, "\u00f0", 0, 1, true },
                new Object[] { "\u00d1", true, 0, "\u00f1", 0, 1, true },
                new Object[] { "\u00d2", true, 0, "\u00f2", 0, 1, true },
                new Object[] { "\u00d3", true, 0, "\u00f3", 0, 1, true },
                new Object[] { "\u00d4", true, 0, "\u00f4", 0, 1, true },
                new Object[] { "\u00d5", true, 0, "\u00f5", 0, 1, true },
                new Object[] { "\u00d6", true, 0, "\u00f6", 0, 1, true },
                new Object[] { "\u00d8", true, 0, "\u00f8", 0, 1, true },
                new Object[] { "\u00d9", true, 0, "\u00f9", 0, 1, true },
                new Object[] { "\u00da", true, 0, "\u00fa", 0, 1, true },
                new Object[] { "\u00db", true, 0, "\u00fb", 0, 1, true },
                new Object[] { "\u00dc", true, 0, "\u00fc", 0, 1, true },
                new Object[] { "\u00dd", true, 0, "\u00fd", 0, 1, true },
                new Object[] { "\u00de", true, 0, "\u00fe", 0, 1, true },
        };
    }

    @Test(dataProvider = "provider")
    public void testRegionMatches(String str, boolean ignoreCase, int toffset,
            String other, int ooffset, int len, boolean expected) {
        map.getOrDefault(str, Map.of(str, str)) // Allow data provider to test adhoc strings
                .forEach(
                        (source, data) -> {
                            assertEquals(
                                    data.regionMatches(ignoreCase, toffset,
                                            other, ooffset, len),
                                    expected,
                                    String.format(
                                            "testing String(%s).regionMatches(%b, %d, %s, %d, %d), source : %s, ",
                                            escapeNonASCIIs(data), ignoreCase,
                                            toffset, escapeNonASCIIs(other),
                                            ooffset, len, source));
                        });
    }
}
