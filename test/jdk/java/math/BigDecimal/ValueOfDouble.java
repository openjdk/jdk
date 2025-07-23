/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8356709
 * @summary Test Double.toString(double)
 * @run junit ValueOfDouble
 */


import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class ValueOfDouble {
    private static final String DIGITS = "1234567000003456789"; // Enough digits to fill a long

    @Test
    public void testValueOfDouble() {
        checkValue(0.0);
        checkValue(-0.0);
        checkValue(Math.PI);
        checkValue(-Math.PI);
        checkValue(Double.MAX_VALUE);
        checkValue(Double.MIN_VALUE);
        checkValue(1e-44); // Lots of digits with lots of 9s

        for (int prec = 1; prec < DIGITS.length(); prec++) {
            String prefix = DIGITS.substring(0, prec);
            for (int exp = -30; exp < 30; exp++) {
                double value = Double.parseDouble(prefix + "e" + exp);
                checkValue(value);
                checkValue(-value);
            }
        }
    }

    private static void checkValue(double value) {
        BigDecimal expected = new BigDecimal(Double.toString(value));
        assertEquals(expected, BigDecimal.valueOf(value));
    }

    @Test
    public void testExceptions() {
        assertThrows(NumberFormatException.class, () -> BigDecimal.valueOf(Double.NaN));
        assertThrows(NumberFormatException.class, () -> BigDecimal.valueOf(Double.POSITIVE_INFINITY));
        assertThrows(NumberFormatException.class, () -> BigDecimal.valueOf(Double.NEGATIVE_INFINITY));
    }
}
