/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4372743
 * @summary test that checks transitions of ERA and YEAR which are caused by add(MONTH).
 * @run junit bug4372743
 */

import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.stream.Stream;

import static java.util.GregorianCalendar.AD;
import static java.util.GregorianCalendar.APRIL;
import static java.util.GregorianCalendar.AUGUST;
import static java.util.GregorianCalendar.BC;
import static java.util.GregorianCalendar.DECEMBER;
import static java.util.GregorianCalendar.ERA;
import static java.util.GregorianCalendar.FEBRUARY;
import static java.util.GregorianCalendar.JANUARY;
import static java.util.GregorianCalendar.JULY;
import static java.util.GregorianCalendar.JUNE;
import static java.util.GregorianCalendar.MARCH;
import static java.util.GregorianCalendar.MAY;
import static java.util.GregorianCalendar.MONTH;
import static java.util.GregorianCalendar.NOVEMBER;
import static java.util.GregorianCalendar.OCTOBER;
import static java.util.GregorianCalendar.SEPTEMBER;
import static java.util.GregorianCalendar.YEAR;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class bug4372743 {

    // Save JVM default timezone
    private static final TimeZone savedTz = TimeZone.getDefault();

    // Set custom JVM default timezone
    @BeforeAll
    static void initAll() {
        TimeZone.setDefault(TimeZone.getTimeZone("PST"));
    }

    // Restore JVM default timezone
    @AfterAll
    static void tearDownAll() {
        TimeZone.setDefault(savedTz);
    }

    /*
     * Set GregorianCalendar to (March 3, A.D. 2) and test adding
     * to the month field. Ensure that the added field is as expected.
     */
    @ParameterizedTest
    @MethodSource("A_D_Values")
    public void A_D_Test(GregorianCalendar gc, int monthValue) {
        for (int i = 0; i < tableSize; i+=(-monthValue)) {
            check(gc, i);
            gc.add(MONTH, monthValue);
        }
    }

    // Given in format: (A.D.) GregorianCalendar, amount to add
    private static Stream<Arguments> A_D_Values() {
        return Stream.of(
                Arguments.of(new GregorianCalendar(2, MARCH, 3), -1),
                Arguments.of(new GregorianCalendar(2, MARCH, 3), -7));
    }

    /*
     * Set GregorianCalendar to (March 10, 2 B.C.) and test adding
     * to the month field. Ensure that the added field is as expected.
     */
    @ParameterizedTest
    @MethodSource("B_C_Values")
    public void B_C_Test(GregorianCalendar gc, int monthValue) {
            gc.add(YEAR, -3);
            for (int i = tableSize - 1; i >= 0; i-=monthValue) {
                check(gc, i);
                gc.add(MONTH, monthValue);
            }
    }

    // Given in format: (B.C.) GregorianCalendar, amount to add
    private static Stream<Arguments> B_C_Values() {
        return Stream.of(
                Arguments.of(new GregorianCalendar(2, OCTOBER, 10), 1),
                Arguments.of(new GregorianCalendar(2, OCTOBER, 10), 8));
    }

    // Check golden data array with actual value
    private void check(GregorianCalendar gc, int index) {
        assertEquals(data[index][ERA], gc.get(ERA), "Invalid era");
        assertEquals(data[index][YEAR], gc.get(YEAR), "Invalid year");
        assertEquals(data[index][MONTH], gc.get(MONTH), "Invalid month");
    }

    // Expected ERA, YEAR, and MONTH combinations
    private final int[][] data = {
        {AD, 2, MARCH},
        {AD, 2, FEBRUARY},
        {AD, 2, JANUARY},
        {AD, 1, DECEMBER},
        {AD, 1, NOVEMBER},
        {AD, 1, OCTOBER},
        {AD, 1, SEPTEMBER},
        {AD, 1, AUGUST},
        {AD, 1, JULY},
        {AD, 1, JUNE},
        {AD, 1, MAY},
        {AD, 1, APRIL},
        {AD, 1, MARCH},
        {AD, 1, FEBRUARY},
        {AD, 1, JANUARY},
        {BC, 1, DECEMBER},
        {BC, 1, NOVEMBER},
        {BC, 1, OCTOBER},
        {BC, 1, SEPTEMBER},
        {BC, 1, AUGUST},
        {BC, 1, JULY},
        {BC, 1, JUNE},
        {BC, 1, MAY},
        {BC, 1, APRIL},
        {BC, 1, MARCH},
        {BC, 1, FEBRUARY},
        {BC, 1, JANUARY},
        {BC, 2, DECEMBER},
        {BC, 2, NOVEMBER},
        {BC, 2, OCTOBER}};
    private final int tableSize = data.length;
}
