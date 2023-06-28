/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8154295
 * @summary Check getNumericCodeAsString() method which returns numeric code as a 3 digit String.
 * @run junit NumCodeAsStringTest
 */

import java.util.Currency;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NumCodeAsStringTest {

    /**
     * Ensure getNumericCodeAsString() returns the correct 3-digit numeric code
     * for the associated currency Code.
     */
    @ParameterizedTest
    @MethodSource("codeProvider")
    public void checkNumCodeTest(String currCode, String expectedNumCode) {
        String actualNumCode = Currency.getInstance(currCode).getNumericCodeAsString();
        assertEquals(expectedNumCode, actualNumCode, String.format(
                "Expected: %s, but got: %s, for %s", expectedNumCode, actualNumCode, currCode));
    }

    private static Stream<Arguments> codeProvider() {
        return Stream.of(
                Arguments.of("AFA", "004"),
                Arguments.of("AUD", "036"),
                Arguments.of("USD", "840")
        );
    }
}
