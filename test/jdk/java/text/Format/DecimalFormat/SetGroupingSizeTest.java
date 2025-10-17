/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8212749
 * @summary test whether input value check for
 *          DecimalFormat.setGroupingSize(int) works correctly.
 * @run junit/othervm SetGroupingSizeTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.DecimalFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SetGroupingSizeTest {

    static Object[][] validGroupingSizes() {
        return new Object[][] {
            { 0 },
            { Byte.MAX_VALUE },
        };
    }

    static Object[][] invalidGroupingSizes() {
        return new Object[][] {
            { Byte.MIN_VALUE - 1 },
            { Byte.MIN_VALUE },
            { -1 },
            { Byte.MAX_VALUE + 1 },
            { Integer.MIN_VALUE },
            { Integer.MAX_VALUE },
        };
    }

    @ParameterizedTest
    @MethodSource("validGroupingSizes")
    void test_validGroupingSize(int newVal) {
        DecimalFormat df = new DecimalFormat();
        df.setGroupingSize(newVal);
        assertEquals(newVal, df.getGroupingSize());
    }

    @ParameterizedTest
    @MethodSource("invalidGroupingSizes")
    void test_invalidGroupingSize(int newVal) {
        assertThrows(IllegalArgumentException.class, () -> {
            DecimalFormat df = new DecimalFormat();
            df.setGroupingSize(newVal);
        });
    }
}
