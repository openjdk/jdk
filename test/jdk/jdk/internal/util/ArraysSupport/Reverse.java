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

/*
 * @test
 * @bug 8266571
 * @modules java.base/jdk.internal.util
 * @run testng Reverse
 * @summary Tests for ArraysSupport.reverse
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.Arrays;
import jdk.internal.util.ArraysSupport;

public class Reverse {
    @DataProvider(name = "data")
    public Object[][] data() {
        return new Object[][][] {
            // pairs of actual, expected
            {
                { },
                { }
            }, {
                { "a" },
                { "a" }
            }, {
                { "a", "b" },
                { "b", "a" }
            }, {
                { "a", "b", "c" },
                { "c", "b", "a" }
            }, {
                { "a", "b", "c", "d" },
                { "d", "c", "b", "a" }
            }, {
                { "a", "b", "c", "d", "e" },
                { "e", "d", "c", "b", "a" }
            }
        };
    }

    @Test(dataProvider = "data")
    public void testReverse(Object[] actual, Object[] expected) {
        Object[] r = ArraysSupport.reverse(actual);
        assertSame(r, actual);
        assertEquals(actual.length, expected.length);
        for (int i = 0; i < actual.length; i++) {
            assertEquals(actual[i], expected[i],
                "mismatch: actual=" + Arrays.asList(actual) +
                " expected=" + Arrays.asList(expected) + " at index " + i);
        }
    }
}
