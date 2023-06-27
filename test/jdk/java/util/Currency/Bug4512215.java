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
 * @bug 4512215 4818420 4819436
 * @summary Updated currency data.
 * @run junit Bug4512215
 */

import java.util.Currency;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Bug4512215 {

    /**
     * Tests that the given country has the expected currency code from
     * calling getCurrencyCode().
     */
    @ParameterizedTest
    @MethodSource("twoDigitDecimals")
    public void currencyCountryTest(String currencyCode, int digits, String country) {
        // digits parameter is not used, exists so that data provider can
        // be shared among multiple tests
        Currency currency = Currency.getInstance(Locale.of("", country));
        assertEquals(currency.getCurrencyCode(), currencyCode, String.format(
                "[%s] expected %s; got: %s", country, currencyCode, currency.getCurrencyCode()));
    }

    /**
     * Tests that the given currencyCode has the expected number of
     * decimal digits from calling getDefaultFractionDigits().
     */
    @ParameterizedTest
    @MethodSource({"twoDigitDecimals", "nonTwoDigitDecimals"})
    public void currencyDefinedTest(String currencyCode, int digits) {
        Currency currency = Currency.getInstance(currencyCode);
        assertEquals(currency.getDefaultFractionDigits(), digits, String.format(
                "[%s] expected: %s; got: %s", currencyCode, digits, currency.getDefaultFractionDigits()));
    }

    private static Stream<Arguments> twoDigitDecimals() {
        return Stream.of(
                Arguments.of("TJS", 2, "TJ"),
                Arguments.of("DKK", 2, "FO"),
                Arguments.of("FKP", 2, "FK"),
                Arguments.of("AFN", 2, "AF"), // changed from "AFA"
                // Newsletter V-5 on ISO 3166-1 (2002-05-20)
                Arguments.of("USD", 2, "TL"), // successor to TP/TPE
                // Newsletter V-8 on ISO 3166-1 (2003-07-23)
                Arguments.of("CSD", 2, "CS") // successor to YU/YUM
        );
    }

    private static Stream<Arguments> nonTwoDigitDecimals() {
        return Stream.of(
                Arguments.of("XBD", -1),
                Arguments.of("XAG", -1),
                Arguments.of("XAU", -1),
                Arguments.of("XBA", -1),
                Arguments.of("XBB", -1)
        );
    }
}
