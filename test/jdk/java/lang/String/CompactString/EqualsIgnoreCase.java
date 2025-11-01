/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @bug 8077559 8248655 8302871
 * @summary Tests Compact String. This one is for String.equalsIgnoreCase.
 * @run testng/othervm EqualsIgnoreCase
 */

public class EqualsIgnoreCase extends CompactString {

    @DataProvider
    public Object[][] provider() {
        return new Object[][] {

        new Object[] { STRING_EMPTY, "", true },
                new Object[] { STRING_L1, "a", true },
                new Object[] { STRING_L2, "aB", true },
                new Object[] { STRING_L4, "AbCd", true },
                new Object[] { STRING_LLONG, "aBcDeFgH", true },
                new Object[] { STRING_U1, "\uFF41", true },
                new Object[] { STRING_U1, "\uFF21", true },
                new Object[] { STRING_U2, "\uFF41\uFF42", true },
                new Object[] { STRING_U2, "\uFF41\uFF22", true },
                new Object[] { STRING_U2, "\uFF21\uFF42", true },
                new Object[] { STRING_M12, "\uFF41a", true },
                new Object[] { STRING_M12, "\uFF21A", true },
                new Object[] { STRING_M11, "a\uFF41", true },
                new Object[] { STRING_M11, "A\uFF21", true },
                new Object[] { STRING_SUPPLEMENTARY, STRING_SUPPLEMENTARY_LOWERCASE, true },

        };
    }

    @Test(dataProvider = "provider")
    public void testEqualsIgnoreCase(String str, String anotherString,
            boolean expected) {
        map.get(str)
                .forEach(
                        (source, data) -> {
                            assertEquals(
                                    data.equalsIgnoreCase(anotherString),
                                    expected,
                                    String.format(
                                            "testing String(%s).equalsIgnoreCase(%s), source : %s, ",
                                            escapeNonASCIIs(data),
                                            escapeNonASCIIs(anotherString),
                                            source));
                        });
    }

    /**
     * Exhaustively check that all 256x256 latin1 code point pairs are equalsIgnoreCased
     * in a manner consistent with Character.toLowerCase(Character.toUpperCase(c));
     */
    @Test
    public void checkConsistencyWithCharacterUppercaseLowerCase() {
        for (char a = 0; a < 256; a++) {
            for (char b = 0; b < 256; b++) {

                int caseFoldA = Character.toLowerCase(Character.toUpperCase(a));
                int caseFoldB = Character.toLowerCase(Character.toUpperCase(b));

                String astr = Character.toString(a);
                String bstr = Character.toString(b);

                // If characters fold to the same lowercase, their strings should equalsIgnoreCase:
                if (caseFoldA == caseFoldB) {
                    assertTrue(astr.equalsIgnoreCase(bstr),
                            "Expected %s to equalsIgnoreCase %s".formatted(astr, bstr));
                } else {
                    assertFalse(astr.equalsIgnoreCase(bstr),
                            "Expected %s to not equalsIgnoreCase %s".formatted(astr, bstr));
                }
            }
        }
    }
}
