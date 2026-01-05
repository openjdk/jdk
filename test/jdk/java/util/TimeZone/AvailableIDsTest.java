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
 * @bug 8347955
 * @summary Ensure underlying element equality of available tz ID methods
 * @run junit AvailableIDsTest
 */

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class AvailableIDsTest {

    // Validate the equality of the array and stream of available IDs
    @Test
    public void streamEqualsArrayTest() {
        String[] tzs = TimeZone.getAvailableIDs();
        assertArrayEquals(tzs, TimeZone.availableIDs().toArray(String[]::new),
                "availableIDs() and getAvailableIDs() do not have the same elements");
    }

    // Validate the equality of the array and stream of available IDs
    // when passed an offset. Tests various offsets.
    @ParameterizedTest
    @ValueSource(ints = {21600000, 25200000, 28800000}) // 6:00, 7:00, 8:00
    public void streamEqualsArrayWithOffsetTest(int offset) {
        String[] tzs = TimeZone.getAvailableIDs(offset);
        assertArrayEquals(tzs, TimeZone.availableIDs(offset).toArray(String[]::new),
                "availableIDs(int) and getAvailableIDs(int) do not have the same elements");
    }
}
