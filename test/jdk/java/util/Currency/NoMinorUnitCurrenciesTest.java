/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4512215 4818420 4819436 8310923
 * @summary Test currencies without minor units.
 * @run junit NoMinorUnitCurrenciesTest
 */

import java.util.Currency;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NoMinorUnitCurrenciesTest {

    /**
     * Spot check some minor undefined currencies and ensure their default fraction
     * digits are not 2.
     */
    @ParameterizedTest
    @MethodSource("minorUndefined")
    public void checkFractionDigits(String currencyCode, int digits) {
        Currency currency = Currency.getInstance(currencyCode);
        assertEquals(currency.getCurrencyCode(), currencyCode);
        assertEquals(currency.getDefaultFractionDigits(), digits, String.format(
                "[%s] expected: %s; got: %s", currencyCode, digits, currency.getDefaultFractionDigits()));
    }

    // Currencies from the minorUndefined key of CurrencyData.properties
    // (These are currencies without minor units)
    private static Stream<Arguments> minorUndefined() {
        return Stream.of(
                Arguments.of("XBD", -1),
                Arguments.of("XAG", -1),
                Arguments.of("XAU", -1),
                Arguments.of("XBA", -1),
                Arguments.of("XBB", -1)
        );
    }
}
