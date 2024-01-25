/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/*
 * @test
 * @bug 8302590 8303648
 * @summary This one is for String.indexOf([int|String],int,int).
 * @run testng IndexOfBeginEnd
 */

public class IndexOfBeginEnd {

    private static final String STRING_EMPTY = "";
    private static final String STRING_L1 = "A";
    private static final String STRING_L2 = "AB";
    private static final String STRING_L4 = "ABCD";
    private static final String STRING_LLONG = "ABCDEFGH";
    private static final String STRING_U1 = "\uFF21";
    private static final String STRING_U2 = "\uFF21\uFF22";
    private static final String STRING_LDUPLICATE = "ABABABABAB";
    private static final String STRING_M11 = "A\uFF21";
    private static final String STRING_M12 = "\uFF21A";
    private static final String STRING_UDUPLICATE = "\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22";
    private static final String STRING_SUPPLEMENTARY = "\uD801\uDC00\uD801\uDC01\uFF21A";
    private static final String STRING_MDUPLICATE1 = "\uFF21A\uFF21A\uFF21A\uFF21A\uFF21A";
    private static final String STRING_MDUPLICATE2 = "A\uFF21A\uFF21A\uFF21A\uFF21A\uFF21";

    @DataProvider
    public Object[][] results() {
        return new Object[][] {

                new Object[] { STRING_EMPTY, (int) 'Z', 0, 0, -1 },

                new Object[] { STRING_L1, (int) 'A', 0, 1, 0 },
                new Object[] { STRING_L1, (int) 'A', 1, 1, -1 },
                new Object[] { STRING_L1, (int) 'Z', 0, 1, -1 },

                new Object[] { STRING_L2, (int) 'A', 0, 2, 0 },
                new Object[] { STRING_L2, (int) 'A', 0, 1, 0 },
                new Object[] { STRING_L2, (int) 'A', 1, 1, -1 },
                new Object[] { STRING_L2, (int) 'A', 1, 2, -1 },
                new Object[] { STRING_L2, (int) 'B', 0, 2, 1 },
                new Object[] { STRING_L2, (int) 'B', 0, 1, -1 },
                new Object[] { STRING_L2, (int) 'B', 1, 1, -1 },
                new Object[] { STRING_L2, (int) 'B', 1, 2, 1 },
                new Object[] { STRING_L2, (int) 'B', 2, 2, -1 },
                new Object[] { STRING_L2, (int) 'Z', 0, 2, -1 },

                new Object[] { STRING_L4, (int) 'A', 0, 4, 0 },
                new Object[] { STRING_L4, (int) 'A', 0, 1, 0 },
                new Object[] { STRING_L4, (int) 'A', 1, 4, -1 },
                new Object[] { STRING_L4, (int) 'D', 0, 4, 3 },
                new Object[] { STRING_L4, (int) 'D', 0, 3, -1 },
                new Object[] { STRING_L4, (int) 'D', 3, 4, 3 },
                new Object[] { STRING_L4, (int) 'D', 4, 4, -1 },
                new Object[] { STRING_L4, (int) 'Z', 0, 4, -1 },

                new Object[] { STRING_LLONG, (int) 'A', 0, 8, 0 },
                new Object[] { STRING_LLONG, (int) 'A', 0, 1, 0 },
                new Object[] { STRING_LLONG, (int) 'A', 1, 1, -1 },
                new Object[] { STRING_LLONG, (int) 'A', 1, 8, -1 },
                new Object[] { STRING_LLONG, (int) 'H', 0, 8, 7 },
                new Object[] { STRING_LLONG, (int) 'H', 0, 7, -1 },
                new Object[] { STRING_LLONG, (int) 'H', 7, 8, 7 },
                new Object[] { STRING_LLONG, (int) 'H', 8, 8, -1 },
                new Object[] { STRING_LLONG, (int) 'Z', 0, 8, -1 },

                new Object[] { STRING_U1, (int) '\uFF21', 0, 1, 0 },
                new Object[] { STRING_U1, (int) '\uFF21', 0, 0, -1 },
                new Object[] { STRING_U1, (int) '\uFF21', 1, 1, -1 },
                new Object[] { STRING_U1, (int) 'A', 0, 1, -1 },

                new Object[] { STRING_U2, (int) '\uFF21', 0, 2, 0 },
                new Object[] { STRING_U2, (int) '\uFF21', 0, 1, 0 },
                new Object[] { STRING_U2, (int) '\uFF21', 1, 2, -1 },
                new Object[] { STRING_U2, (int) '\uFF22', 0, 2, 1 },
                new Object[] { STRING_U2, (int) '\uFF22', 0, 1, -1 },
                new Object[] { STRING_U2, (int) '\uFF22', 1, 2, 1 },
                new Object[] { STRING_U2, (int) '\uFF22', 2, 2, -1 },
                new Object[] { STRING_U2, (int) '\uFF3A', 0, 2, -1 },

                new Object[] { STRING_LDUPLICATE, (int) 'A', 0, 10, 0 },
                new Object[] { STRING_LDUPLICATE, (int) 'A', 1, 3, 2 },
                new Object[] { STRING_LDUPLICATE, (int) 'A', 3, 3, -1 },
                new Object[] { STRING_LDUPLICATE, (int) 'A', 3, 5, 4 },
                new Object[] { STRING_LDUPLICATE, (int) 'B', 0, 10, 1 },
                new Object[] { STRING_LDUPLICATE, (int) 'B', 2, 4, 3 },
                new Object[] { STRING_LDUPLICATE, (int) 'B', 4, 6, 5 },

                new Object[] { STRING_M11, (int) 'A', 0, 2, 0 },
                new Object[] { STRING_M11, (int) 'A', 0, 1, 0 },
                new Object[] { STRING_M11, (int) 'A', 1, 2, -1 },
                new Object[] { STRING_M11, (int) 'A', 2, 2, -1 },
                new Object[] { STRING_M11, (int) '\uFF21', 0, 2, 1 },
                new Object[] { STRING_M11, (int) '\uFF21', 0, 1, -1 },
                new Object[] { STRING_M11, (int) '\uFF21', 1, 2, 1 },
                new Object[] { STRING_M11, (int) '\uFF21', 2, 2, -1 },
                new Object[] { STRING_M11, (int) '\uFF3A', 0, 2, -1 },

                new Object[] { STRING_M12, (int) '\uFF21', 0, 2, 0 },
                new Object[] { STRING_M12, (int) '\uFF21', 0, 1, 0 },
                new Object[] { STRING_M12, (int) '\uFF21', 1, 2, -1 },
                new Object[] { STRING_M12, (int) '\uFF21', 2, 2, -1 },
                new Object[] { STRING_M12, (int) 'A', 0, 2, 1 },
                new Object[] { STRING_M12, (int) 'A', 0, 1, -1 },
                new Object[] { STRING_M12, (int) 'A', 1, 2, 1 },
                new Object[] { STRING_M12, (int) 'A', 2, 2, -1 },
                new Object[] { STRING_M12, (int) '\uFF3A', 0, 2, -1 },

                new Object[] { STRING_UDUPLICATE, (int) '\uFF21', 0, 10, 0 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF21', 1, 3, 2 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF21', 3, 3, -1 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF21', 3, 5, 4 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF22', 0, 10, 1 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF22', 2, 4, 3 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF22', 4, 6, 5 },

                new Object[] { STRING_SUPPLEMENTARY, 'A', 0, 6, 5 },
                new Object[] { STRING_SUPPLEMENTARY, 'A', 2, 6, 5 },
                new Object[] { STRING_SUPPLEMENTARY, 'A', 2, 4, -1 },
                new Object[] { STRING_SUPPLEMENTARY, 'A', 4, 4, -1 },
                new Object[] { STRING_SUPPLEMENTARY, '\uFF21', 0, 6, 4 },
                new Object[] { STRING_SUPPLEMENTARY, '\uFF21', 2, 2, -1 },
                new Object[] { STRING_SUPPLEMENTARY, '\uFF21', 2, 6, 4 },
                new Object[] { STRING_SUPPLEMENTARY, '\uFF21', 2, 4, -1 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC00'), 0, 6, 0 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC00'), 0, 3, 0 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC00'), 0, 1, -1 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC00'), 1, 4, -1 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC01'), 0, 6, 2 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC01'), 2, 2, -1 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC01'), 2, 5, 2 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC01'), 2, 3, -1 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC01'), 3, 6, -1 },
        };
    }

    @DataProvider
    public Object[][] exceptions() {
        return new Object[][]{
                new Object[]{STRING_LDUPLICATE, 'A', -1, 0},
                new Object[]{STRING_LDUPLICATE, 'A', 0, 100},
                new Object[]{STRING_LDUPLICATE, 'A', -1, 100},
                new Object[]{STRING_LDUPLICATE, 'A', 3, 1},

                new Object[]{STRING_UDUPLICATE, 'A', -1, 0},
                new Object[]{STRING_UDUPLICATE, 'A', 0, 100},
                new Object[]{STRING_UDUPLICATE, 'A', -1, 100},
                new Object[]{STRING_UDUPLICATE, 'A', 3, 1},

                new Object[]{STRING_MDUPLICATE1, 'A', -1, 0},
                new Object[]{STRING_MDUPLICATE1, 'A', 0, 100},
                new Object[]{STRING_MDUPLICATE1, 'A', -1, 100},
                new Object[]{STRING_MDUPLICATE1, 'A', 3, 1},

                new Object[]{STRING_MDUPLICATE2, 'A', -1, 0},
                new Object[]{STRING_MDUPLICATE2, 'A', 0, 100},
                new Object[]{STRING_MDUPLICATE2, 'A', -1, 100},
                new Object[]{STRING_MDUPLICATE2, 'A', 3, 1},
        };
    }

    @DataProvider
    public Object[][] resultsStr() {
        return new Object[][] {

                new Object[] { STRING_EMPTY, "A", 0, 0, -1 },
                new Object[] { STRING_EMPTY, "", 0, 0, 0 },

                new Object[] { STRING_L1, "A", 0, 1, 0 },
                new Object[] { STRING_L1, "A", 1, 1, -1 },
                new Object[] { STRING_L1, "AB", 0, 1, -1 },
                new Object[] { STRING_L1, "", 0, 0, 0 },
                new Object[] { STRING_L1, "", 0, 1, 0 },
                new Object[] { STRING_L1, "", 1, 1, 1 },

                new Object[] { STRING_L2, "A", 0, 2, 0 },
                new Object[] { STRING_L2, "A", 0, 1, 0 },
                new Object[] { STRING_L2, "A", 1, 2, -1 },
                new Object[] { STRING_L2, "B", 0, 2, 1 },
                new Object[] { STRING_L2, "B", 1, 2, 1 },
                new Object[] { STRING_L2, "B", 0, 0, -1 },
                new Object[] { STRING_L2, "AB", 0, 2, 0 },
                new Object[] { STRING_L2, "AB", 1, 2, -1 },
                new Object[] { STRING_L2, "AB", 0, 1, -1 },
                new Object[] { STRING_L2, "", 0, 2, 0 },
                new Object[] { STRING_L2, "", 1, 2, 1 },
                new Object[] { STRING_L2, "", 2, 2, 2 },

                new Object[] { STRING_L4, "ABCD", 0, 4, 0 },
                new Object[] { STRING_L4, "ABCD", 0, 3, -1 },
                new Object[] { STRING_L4, "ABCD", 1, 4, -1 },
                new Object[] { STRING_L4, "BC", 0, 4, 1 },
                new Object[] { STRING_L4, "BC", 0, 3, 1 },
                new Object[] { STRING_L4, "BC", 1, 4, 1 },
                new Object[] { STRING_L4, "BC", 1, 2, -1 },
                new Object[] { STRING_L4, "BC", 2, 4, -1 },
                new Object[] { STRING_L4, "A", 0, 4, 0 },
                new Object[] { STRING_L4, "A", 1, 4, -1 },
                new Object[] { STRING_L4, "CD", 0, 4, 2 },
                new Object[] { STRING_L4, "CD", 2, 4, 2 },
                new Object[] { STRING_L4, "CD", 1, 4, 2 },
                new Object[] { STRING_L4, "CD", 0, 3, -1 },
                new Object[] { STRING_L4, "A", 2, 4, -1 },
                new Object[] { STRING_L4, "A", 2, 2, -1 },
                new Object[] { STRING_L4, "A", 4, 4, -1 },
                new Object[] { STRING_L4, "ABCDE", 0, 4, -1 },

                new Object[] { STRING_LLONG, "ABCDEFGH", 0, 8, 0 },
                new Object[] { STRING_LLONG, "ABCDEFGH", 1, 8, -1 },
                new Object[] { STRING_LLONG, "ABCDEFGH", 0, 7, -1 },
                new Object[] { STRING_LLONG, "DEFGH", 0, 8, 3 },
                new Object[] { STRING_LLONG, "DEFGH", 3, 8, 3 },
                new Object[] { STRING_LLONG, "DEFGH", 4, 8, -1 },
                new Object[] { STRING_LLONG, "DEFGH", 0, 7, -1 },
                new Object[] { STRING_LLONG, "A", 0, 8, 0 },
                new Object[] { STRING_LLONG, "A", 1, 8, -1 },
                new Object[] { STRING_LLONG, "A", 0, 0, -1 },
                new Object[] { STRING_LLONG, "GHI", 0, 8, -1 },
                new Object[] { STRING_LLONG, "GHI", 8, 8, -1 },
                new Object[] { STRING_LLONG, "", 4, 4, 4 },
                new Object[] { STRING_LLONG, "", 4, 8, 4 },
                new Object[] { STRING_LLONG, "", 8, 8, 8 },

                new Object[] { STRING_U1, "\uFF21", 0, 1, 0 },
                new Object[] { STRING_U1, "\uFF21", 0, 0, -1 },
                new Object[] { STRING_U1, "\uFF21", 1, 1, -1 },
                new Object[] { STRING_U1, "\uFF21A", 0, 1, -1 },

                new Object[] { STRING_U2, "\uFF21\uFF22", 0, 2, 0 },
                new Object[] { STRING_U2, "\uFF21\uFF22", 1, 2, -1 },
                new Object[] { STRING_U2, "\uFF22", 0, 2, 1 },
                new Object[] { STRING_U2, "\uFF22", 0, 1, -1 },
                new Object[] { STRING_U2, "\uFF22", 1, 2, 1 },
                new Object[] { STRING_U2, "\uFF21", 1, 2, -1 },
                new Object[] { STRING_U2, "\uFF21", 0, 1, 0 },
                new Object[] { STRING_U2, "", 0, 1, 0 },
                new Object[] { STRING_U2, "", 1, 1, 1 },
                new Object[] { STRING_U2, "", 2, 2, 2 },

                new Object[] { STRING_M12, "\uFF21A", 0, 2, 0 },
                new Object[] { STRING_M12, "\uFF21A", 0, 1, -1 },
                new Object[] { STRING_M12, "\uFF21A", 1, 2, -1 },
                new Object[] { STRING_M12, "A", 1, 2, 1 },
                new Object[] { STRING_M12, "A", 0, 2, 1 },
                new Object[] { STRING_M12, "A", 0, 1, -1 },
                new Object[] { STRING_M12, "\uFF21", 0, 2, 0 },
                new Object[] { STRING_M12, "\uFF21", 0, 1, 0 },
                new Object[] { STRING_M12, "\uFF21", 1, 2, -1 },

                new Object[] { STRING_M11, "A\uFF21", 0, 2, 0 },
                new Object[] { STRING_M11, "\uFF21", 1, 2, 1 },
                new Object[] { STRING_M11, "A\uFF21", 1, 2, -1 },
                new Object[] { STRING_M11, "A\uFF21A", 0, 2, -1 },

                new Object[] {
                        STRING_UDUPLICATE,
                        "\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22",
                        0, 10, 0 },
                new Object[] {
                        STRING_UDUPLICATE,
                        "\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22",
                        0, 9, -1 },
                new Object[] {
                        STRING_UDUPLICATE,
                        "\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22",
                        1, 10, -1 },
                new Object[] {
                        STRING_UDUPLICATE,
                        "\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22",
                        1, 10, 1 },
                new Object[] {
                        STRING_UDUPLICATE,
                        "\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22",
                        0, 10, 1 },
                new Object[] {
                        STRING_UDUPLICATE,
                        "\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22\uFF21\uFF22",
                        0, 9, -1 },
                new Object[] { STRING_UDUPLICATE, "\uFF21\uFF22\uFF21\uFF22",
                        4, 10, 4 },
                new Object[] { STRING_UDUPLICATE, "\uFF21\uFF22\uFF21\uFF22",
                        3, 8, 4 },
                new Object[] { STRING_UDUPLICATE, "\uFF21\uFF22\uFF21\uFF22",
                        2, 7, 2 },
                new Object[] { STRING_UDUPLICATE, "\uFF21\uFF22\uFF21\uFF22",
                        7, 10, -1 },
                new Object[] { STRING_UDUPLICATE, "",
                        7, 10, 7 },
                new Object[] { STRING_UDUPLICATE, "",
                        10, 10, 10 },
        };
    }

    @DataProvider
    public Object[][] exceptionsStr() {
        return new Object[][]{
                new Object[]{STRING_LDUPLICATE, "", -1, 0},
                new Object[]{STRING_LDUPLICATE, "", 0, 100},
                new Object[]{STRING_LDUPLICATE, "", -1, 100},
                new Object[]{STRING_LDUPLICATE, "", 3, 1},

                new Object[]{STRING_UDUPLICATE, "", -1, 0},
                new Object[]{STRING_UDUPLICATE, "", 0, 100},
                new Object[]{STRING_UDUPLICATE, "", -1, 100},
                new Object[]{STRING_UDUPLICATE, "", 3, 1},

                new Object[]{STRING_MDUPLICATE1, "", -1, 0},
                new Object[]{STRING_MDUPLICATE1, "", 0, 100},
                new Object[]{STRING_MDUPLICATE1, "", -1, 100},
                new Object[]{STRING_MDUPLICATE1, "", 3, 1},

                new Object[]{STRING_MDUPLICATE2, "", -1, 0},
                new Object[]{STRING_MDUPLICATE2, "", 0, 100},
                new Object[]{STRING_MDUPLICATE2, "", -1, 100},
                new Object[]{STRING_MDUPLICATE2, "", 3, 1},

                new Object[]{STRING_LDUPLICATE, "A", -1, 0},
                new Object[]{STRING_LDUPLICATE, "A", 0, 100},
                new Object[]{STRING_LDUPLICATE, "A", -1, 100},
                new Object[]{STRING_LDUPLICATE, "A", 3, 1},

                new Object[]{STRING_UDUPLICATE, "A", -1, 0},
                new Object[]{STRING_UDUPLICATE, "A", 0, 100},
                new Object[]{STRING_UDUPLICATE, "A", -1, 100},
                new Object[]{STRING_UDUPLICATE, "A", 3, 1},

                new Object[]{STRING_MDUPLICATE1, "A", -1, 0},
                new Object[]{STRING_MDUPLICATE1, "A", 0, 100},
                new Object[]{STRING_MDUPLICATE1, "A", -1, 100},
                new Object[]{STRING_MDUPLICATE1, "A", 3, 1},

                new Object[]{STRING_MDUPLICATE2, "A", -1, 0},
                new Object[]{STRING_MDUPLICATE2, "A", 0, 100},
                new Object[]{STRING_MDUPLICATE2, "A", -1, 100},
                new Object[]{STRING_MDUPLICATE2, "A", 3, 1},
        };
    }

    @Test(dataProvider = "results")
    public void testIndexOf(String str, int ch, int from, int to, int expected) {
        assertEquals(str.indexOf(ch, from, to), expected,
                String.format("testing String(%s).indexOf(%d,%d,%d)",
                        escapeNonASCIIs(str), ch, from, to));
    }

    @Test(dataProvider = "exceptions")
    public void testIndexOf(String str, int ch, int from, int to) {
        assertThrows(StringIndexOutOfBoundsException.class,
                () -> str.indexOf(ch, from, to));
    }

    @Test(dataProvider = "resultsStr")
    public void testIndexOf(String str, String sub, int from, int to, int expected) {
        assertEquals(str.indexOf(sub, from, to), expected,
                String.format("testing String(%s).indexOf(%s,%d,%d)",
                        escapeNonASCIIs(str), escapeNonASCIIs(sub), from, to));
    }

    @Test(dataProvider = "exceptionsStr")
    public void testIndexOf(String str, String sub, int from, int to) {
        assertThrows(StringIndexOutOfBoundsException.class,
                () -> str.indexOf(sub, from, to));
    }

    private static String escapeNonASCIIs(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c >= 0x100) {
                sb.append("\\u").append(Integer.toHexString(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

}
