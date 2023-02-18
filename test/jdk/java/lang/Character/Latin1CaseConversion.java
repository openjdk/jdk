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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * @test
 * @summary Provides exchaustive verification of Character.toUpperCase and Character.toLowerCase
 * for all code points in the latin1 range 0-255.
 * @run testng Latin1CaseConversion
 */
public class Latin1CaseConversion {

    @Test
    public void shouldUpperCaseAndLowerCaseLatin1() {
        for (int c = 0; c < 256; c++) {
            int upper = Character.toUpperCase(c);
            int lower = Character.toLowerCase(c);

            if (c < 0x41) { // Before A
                assertUnchanged(upper, lower, c);
            } else if (c <= 0x5A) { // A-Z
                assertEquals(upper, c);
                assertEquals(lower, c + 32);
            } else if (c < 0x61) { // Between Z and a
                assertUnchanged(upper, lower, c);
            } else if (c <= 0x7A) { // a-z
                assertEquals(upper, c - 32);
                assertEquals(lower, c);
            } else if (c < 0xB5) { // Between z and Micro Sign
                assertUnchanged(upper, lower, c);
            } else if (c == 0xB5) { // Special case for Micro Sign
                assertEquals(upper, 0x39C);
                assertEquals(lower, c);
            } else if (c < 0xC0) { // Between my and A-grave
                assertUnchanged(upper, lower, c);
            } else if (c < 0xD7) { // A-grave - O with Diaeresis
                assertEquals(upper, c);
                assertEquals(lower, c + 32);
            } else if (c == 0xD7) { // Multiplication
                assertUnchanged(upper, lower, c);
            } else if (c <= 0xDE) { // O with slash - Thorn
                assertEquals(upper, c);
                assertEquals(lower, c + 32);
            } else if (c == 0xDF) { // Sharp s
                assertUnchanged(upper, lower, c);
            } else if (c < 0xF7) { // a-grave - divsion
                assertEquals(upper, c - 32);
                assertEquals(lower, c);
            } else if (c == 0xF7) { // Division
                assertUnchanged(upper, lower, c);
            } else if (c < 0xFF) { // o with slash - thorn
                assertEquals(upper, c - 32);
                assertEquals(lower, c);
            } else if (c == 0XFF) { // Special case for y with Diaeresis
                assertEquals(upper, 0x178);
                assertEquals(lower, c);
            } else {
                fail("Uncovered code point: " + Integer.toHexString(c));
            }
        }
    }

    private static void assertUnchanged(int upper, int lower, int c) {
        assertEquals(upper, c);
        assertEquals(lower, c);
    }
}
