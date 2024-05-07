/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8210443
 * @summary Check values() and valueOf(String name) of Locale.FilteringMode.
 * @run junit FilteringModeTest
 */

import java.util.Arrays;
import java.util.List;
import java.util.Locale.FilteringMode;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FilteringModeTest {

    private static final List<String> expectedModeNames = List.of(
            "AUTOSELECT_FILTERING",
            "EXTENDED_FILTERING",
            "IGNORE_EXTENDED_RANGES",
            "MAP_EXTENDED_RANGES",
            "REJECT_EXTENDED_RANGES"
    );

    // Ensure valueOf() exceptions are thrown
    @Test
    public void valueOfExceptionsTest() {
        assertThrows(IllegalArgumentException.class,
                () -> FilteringMode.valueOf("").name());
        assertThrows(NullPointerException.class,
                () -> FilteringMode.valueOf(null).name());
    }

    // Ensure valueOf() returns expected results
    @ParameterizedTest
    @MethodSource("modes")
    public void valueOfTest(String expectedName) {
        String name = FilteringMode.valueOf(expectedName).name();
        assertEquals(expectedName, name);
    }

    private static Stream<String> modes() {
        return expectedModeNames.stream();
    }

    // Ensure values() returns expected results
    @Test
    public void valuesTest() {
        FilteringMode[] modeArray = FilteringMode.values();
        List<String> actualNames = Arrays.stream(modeArray)
                .map(mode -> mode.name())
                .collect(Collectors.toList());
        assertEquals(expectedModeNames, actualNames);
    }
}
