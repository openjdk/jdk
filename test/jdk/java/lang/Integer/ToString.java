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

/*
 * @test
 * @bug 8136500 8310929
 * @summary Test Integer.toString method for both compact and non-compact strings
 * @run junit/othervm ToString
 */

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToString {

    @Test
    public void testBase10() {
        test("-2147483648", Integer.MIN_VALUE);
        test("2147483647",  Integer.MAX_VALUE);
        test("0", 0);

        // Wiggle around the exponentially increasing base.
        final int LIMIT = (1 << 15);
        int base = 10000;
        while (base < Integer.MAX_VALUE / 10) {
            for (int d = -LIMIT; d < LIMIT; d++) {
                int c = base + d;
                if (c > 0) {
                    buildAndTest(c);
                }
            }
            base *= 10;
        }

        for (int c = 1; c < LIMIT; c++) {
            buildAndTest(Integer.MAX_VALUE - LIMIT + c);
        }
    }

    private static void buildAndTest(int c) {
        if (c <= 0) {
            throw new IllegalArgumentException("Test bug: can only handle positives, " + c);
        }

        StringBuilder sbN = new StringBuilder();
        StringBuilder sbP = new StringBuilder();

        int t = c;
        while (t > 0) {
            char digit = (char) ('0' + (t % 10));
            sbN.append(digit);
            sbP.append(digit);
            t = t / 10;
        }

        sbN.append("-");
        sbN.reverse();
        sbP.reverse();

        test(sbN.toString(), -c);
        test(sbP.toString(), c);
    }

    private static void test(String expected, int value) {
        assertEquals(expected, Integer.toString(value));
    }
}
