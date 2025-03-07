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
 * @bug 8347949
 * @summary Ensure underlying element equality of available currency methods
 * @run junit AvailableCurrenciesTest
 */

import java.util.Currency;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AvailableCurrenciesTest {

    // Validate the equality of the set and stream of available currencies
    @Test
    public void streamEqualsSetTest() {
        var currencies = Currency.getAvailableCurrencies();
        assertEquals(currencies, Currency.availableCurrencies().collect(Collectors.toSet()),
                "availableCurrencies() and getAvailableCurrencies() do not have the same elements");
    }

    // Ensure there are no duplicates in the available currencies
    @Test
    public void noDuplicatesTest() {
        assertEquals(Currency.getAvailableCurrencies().size(),
                Currency.availableCurrencies().distinct().count(),
                "Duplicate currencies returned by availableCurrencies()");
    }
}
