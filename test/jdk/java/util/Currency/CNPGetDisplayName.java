/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6807534
 * @summary check whether the default implementation of
 *    CurrencyNameProvider.getDisplayName(String, Locale) throws appropriate
 *    exceptions when necessary.
 * @run junit CNPGetDisplayName
 */

import java.util.Locale;
import java.util.spi.CurrencyNameProvider;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CNPGetDisplayName {

    static final CurrencyNameProvider cnp = new CurrencyNameProviderImpl();

    /**
     * Tests that the currency name provider throws a NullPointerException
     * under the expected circumstances.
     */
    @ParameterizedTest
    @MethodSource("nullArgProvider")
    public void NPETest(String currencyCode, Locale locale, String err) {
        assertThrows(NullPointerException.class,
                () -> cnp.getDisplayName(currencyCode, locale), err);
    }

    /**
     * Tests that the currency name provider throws a IllegalArgumentException
     * under the expected circumstances.
     */
    @ParameterizedTest
    @MethodSource("illegalArgProvider")
    public void IAETest(String currencyCode, Locale locale, String err) {
        assertThrows(IllegalArgumentException.class,
                () -> cnp.getDisplayName(currencyCode, locale), err);
    }

    private static Stream<Arguments> nullArgProvider() {
        return Stream.of(
                Arguments.of(null, Locale.US,
                        "NPE was not thrown with null currencyCode"),
                Arguments.of("USD", null,
                        "NPE was not thrown with null locale")
        );
    }

    private static Stream<Arguments> illegalArgProvider() {
        return Stream.of(
                Arguments.of("INVALID", Locale.US,
                        "IAE was not thrown with invalid currency code"),
                Arguments.of("inv", Locale.US,
                        "IAE was not thrown with invalid currency code"),
                Arguments.of("USD", Locale.JAPAN,
                        "IllegalArgumentException was not thrown with non-supported locale")
        );
    }

    static class CurrencyNameProviderImpl extends CurrencyNameProvider {
        // dummy implementation
        public String getSymbol(String currencyCode, Locale locale) {
            return "";
        }

        public Locale[] getAvailableLocales() {
            Locale[] avail = new Locale[1];
            avail[0] = Locale.US;
            return avail;
        }
    }
}
