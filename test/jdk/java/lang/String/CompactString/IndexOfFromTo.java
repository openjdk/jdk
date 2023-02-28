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
 * @bug 8302590
 * @summary This one is for String.indexOf(int,int,int).
 * @run testng/othervm -XX:+CompactStrings IndexOfFromTo
 * @run testng/othervm -XX:-CompactStrings IndexOfFromTo
 */

public class IndexOfFromTo extends CompactString {
    private static final int MIN = Integer.MIN_VALUE;
    private static final int MAX = Integer.MAX_VALUE;

    @DataProvider
    public Object[][] provider() {
        return new Object[][] {

                new Object[] { STRING_EMPTY, (int) 'Z', MIN, MAX, -1 },

                new Object[] { STRING_L1, (int) 'A', MIN, MAX, 0 },
                new Object[] { STRING_L1, (int) 'A', 1, MAX, -1 },
                new Object[] { STRING_L1, (int) 'A', MIN, 1, 0 },
                new Object[] { STRING_L1, (int) 'Z', MIN, MAX, -1 },

                new Object[] { STRING_L2, (int) 'A', MIN, MAX, 0 },
                new Object[] { STRING_L2, (int) 'A', 0, 1, 0 },
                new Object[] { STRING_L2, (int) 'A', 1, 2, -1 },
                new Object[] { STRING_L2, (int) 'A', 1, MAX, -1 },
                new Object[] { STRING_L2, (int) 'B', MIN, MAX, 1 },
                new Object[] { STRING_L2, (int) 'B', 0, 1, -1 },
                new Object[] { STRING_L2, (int) 'B', 1, 2, 1 },
                new Object[] { STRING_L2, (int) 'B', 2, MAX, -1 },
                new Object[] { STRING_L2, (int) 'Z', MIN, MAX, -1 },

                new Object[] { STRING_L4, (int) 'A', MIN, MAX, 0 },
                new Object[] { STRING_L4, (int) 'A', 0, 1, 0 },
                new Object[] { STRING_L4, (int) 'A', 1, 4, -1 },
                new Object[] { STRING_L4, (int) 'A', 1, MAX, -1 },
                new Object[] { STRING_L4, (int) 'D', MIN, MAX, 3 },
                new Object[] { STRING_L4, (int) 'D', 0, 3, -1 },
                new Object[] { STRING_L4, (int) 'D', 3, 4, 3 },
                new Object[] { STRING_L4, (int) 'D', 4, MAX, -1 },
                new Object[] { STRING_L4, (int) 'Z', MIN, MAX, -1 },

                new Object[] { STRING_LLONG, (int) 'A', MIN, MAX, 0 },
                new Object[] { STRING_LLONG, (int) 'A', 0, 1, 0 },
                new Object[] { STRING_LLONG, (int) 'A', 1, 8, -1 },
                new Object[] { STRING_LLONG, (int) 'A', 1, MAX, -1 },
                new Object[] { STRING_LLONG, (int) 'H', MIN, MAX, 7 },
                new Object[] { STRING_LLONG, (int) 'H', 0, 7, -1 },
                new Object[] { STRING_LLONG, (int) 'H', 7, 8, 7 },
                new Object[] { STRING_LLONG, (int) 'H', 8, MAX, -1 },
                new Object[] { STRING_LLONG, (int) 'Z', MIN, MAX, -1 },

                new Object[] { STRING_U1, (int) '\uFF21', MIN, MAX, 0 },
                new Object[] { STRING_U1, (int) '\uFF21', 1, MAX, -1 },
                new Object[] { STRING_U1, (int) '\uFF21', MIN, 1, 0 },
                new Object[] { STRING_U1, (int) 'A', MIN, MAX, -1 },

                new Object[] { STRING_U2, (int) '\uFF21', MIN, MAX, 0 },
                new Object[] { STRING_U2, (int) '\uFF21', 0, 1, 0 },
                new Object[] { STRING_U2, (int) '\uFF21', 1, 2, -1 },
                new Object[] { STRING_U2, (int) '\uFF21', 1, MAX, -1 },
                new Object[] { STRING_U2, (int) '\uFF22', MIN, MAX, 1 },
                new Object[] { STRING_U2, (int) '\uFF22', 0, 1, -1 },
                new Object[] { STRING_U2, (int) '\uFF22', 1, 2, 1 },
                new Object[] { STRING_U2, (int) '\uFF22', 2, MAX, -1 },
                new Object[] { STRING_U2, (int) '\uFF3A', MIN, MAX, -1 },

                new Object[] { STRING_LDUPLICATE, (int) 'A', MIN, MAX, 0 },
                new Object[] { STRING_LDUPLICATE, (int) 'A', 1, 3, 2 },
                new Object[] { STRING_LDUPLICATE, (int) 'A', 3, 5, 4 },
                new Object[] { STRING_LDUPLICATE, (int) 'B', MIN, MAX, 1 },
                new Object[] { STRING_LDUPLICATE, (int) 'B', 2, 4, 3 },
                new Object[] { STRING_LDUPLICATE, (int) 'B', 4, 6, 5 },

                new Object[] { STRING_M11, (int) 'A', MIN, MAX, 0 },
                new Object[] { STRING_M11, (int) 'A', 0, 1, 0 },
                new Object[] { STRING_M11, (int) 'A', 1, 2, -1 },
                new Object[] { STRING_M11, (int) 'A', 2, MAX, -1 },
                new Object[] { STRING_M11, (int) '\uFF21', MIN, MAX, 1 },
                new Object[] { STRING_M11, (int) '\uFF21', 0, 1, -1 },
                new Object[] { STRING_M11, (int) '\uFF21', 1, 2, 1 },
                new Object[] { STRING_M11, (int) '\uFF21', 2, MAX, -1 },
                new Object[] { STRING_M11, (int) '\uFF3A', MIN, MAX, -1 },

                new Object[] { STRING_M12, (int) '\uFF21', MIN, MAX, 0 },
                new Object[] { STRING_M12, (int) '\uFF21', 0, 1, 0 },
                new Object[] { STRING_M12, (int) '\uFF21', 1, 2, -1 },
                new Object[] { STRING_M12, (int) '\uFF21', 2, MAX, -1 },
                new Object[] { STRING_M12, (int) 'A', MIN, MAX, 1 },
                new Object[] { STRING_M12, (int) 'A', 0, 1, -1 },
                new Object[] { STRING_M12, (int) 'A', 1, 2, 1 },
                new Object[] { STRING_M12, (int) 'A', 2, MAX, -1 },
                new Object[] { STRING_M12, (int) '\uFF3A', MIN, MAX, -1 },

                new Object[] { STRING_UDUPLICATE, (int) '\uFF21', MIN, MAX, 0 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF21', 1, 3, 2 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF21', 3, 5, 4 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF22', MIN, MAX, 1 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF22', 2, 4, 3 },
                new Object[] { STRING_UDUPLICATE, (int) '\uFF22', 4, 6, 5 },

                new Object[] { STRING_SUPPLEMENTARY, 'A', MIN, MAX, 5 },
                new Object[] { STRING_SUPPLEMENTARY, 'A', 2, 6, 5 },
                new Object[] { STRING_SUPPLEMENTARY, 'A', 2, 4, -1 },
                new Object[] { STRING_SUPPLEMENTARY, '\uFF21', MIN, MAX, 4 },
                new Object[] { STRING_SUPPLEMENTARY, '\uFF21', 2, 6, 4 },
                new Object[] { STRING_SUPPLEMENTARY, '\uFF21', 2, 4, -1 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC00'), MIN, MAX, 0 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC00'), 0, 3, 0 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC00'), 0, 1, -1 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC00'), 1, 4, -1 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC01'), MIN, MAX, 2 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC01'), 2, 5, 2 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC01'), 2, 3, -1 },
                new Object[] { STRING_SUPPLEMENTARY,
                        Character.toCodePoint('\uD801', '\uDC01'), 3, 6, -1 },

                new Object[] { STRING_LDUPLICATE, 'A', 1, 0, -1},
                new Object[] { STRING_UDUPLICATE, 'A', 1, 0, -1},
                new Object[] { STRING_MDUPLICATE1, 'A', 1, 0, -1},
                new Object[] { STRING_MDUPLICATE2, 'A', 1, 0, -1},
        };
    }

    @Test(dataProvider = "provider")
    public void testIndexOf(String str, int ch, int from, int to, int expected) {
        map.get(str).forEach(
                (source, data) -> {
                    assertEquals(data.indexOf(ch, from, to), expected, String.format(
                            "testing String(%s).indexOf(%d,%d,%d), source : %s, ",
                            escapeNonASCIIs(data), ch, from, to, source));
                });
    }

    @Test(dataProvider = "provider")
    public void testCheckedIndexOf(String str, int ch, int from, int to, int expected) {
        map.get(str).forEach(
                (source, data) -> {
                    if (0 <= from && from <= to && to <= data.length()) {
                        assertEquals(data.checkedIndexOf(ch, from, to), expected,
                                String.format("testing String(%s).checkedIndexOf(%d,%d,%d), source : %s, ",
                                        escapeNonASCIIs(data), ch, from, to, source));
                    } else {
                        assertThrows(StringIndexOutOfBoundsException.class,
                                () -> data.checkedIndexOf(ch, from, to));
                    }
                });
    }

}
