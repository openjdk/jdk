/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4401223
 * @summary Make sure that GregorianCalendar doesn't cause
 *          IllegalArgumentException at some special situations which are
 *          related to the Leap Year.
 * @run junit bug4401223
 */

import java.util.Date;
import java.util.GregorianCalendar;

import static java.util.GregorianCalendar.DATE;
import static java.util.GregorianCalendar.DAY_OF_YEAR;
import static java.util.GregorianCalendar.DECEMBER;
import static java.util.GregorianCalendar.FEBRUARY;
import static java.util.GregorianCalendar.MONTH;
import static java.util.GregorianCalendar.YEAR;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class bug4401223 {

    // Ensure IAE not thrown for date: 12-29-00
    @SuppressWarnings("deprecation")
    @Test
    public void checkExceptionTest() {
        Date date = new Date(2000 - 1900, FEBRUARY, 29);
        GregorianCalendar gc = new GregorianCalendar();
        assertDoesNotThrow(() -> {
            gc.setTime(date);
            gc.setLenient(false);
            gc.set(YEAR, 2001);
        }, "Exception occurred for 2/29/00 & set(YEAR,2001)");
    }

    // Ensure IAE not thrown for date: 12-31-00. Validate expected values.
    @SuppressWarnings("deprecation")
    @Test
    public void checkExceptionAndValuesTest() {
        Date date = new Date(2000 - 1900, DECEMBER, 31);
        GregorianCalendar gc = new GregorianCalendar();
        assertDoesNotThrow(() -> {
            gc.setTime(date);
            gc.setLenient(false);
            gc.set(YEAR, 2001);
        }, "Exception occurred for 12/31/00 & set(YEAR,2001)");

        String errMsg = "Wrong date,  got: " + gc.getTime();

        assertEquals(2001, gc.get(YEAR), errMsg);
        assertEquals(DECEMBER, gc.get(MONTH), errMsg);
        assertEquals(31, gc.get(DATE), errMsg);
        assertEquals(365, gc.get(DAY_OF_YEAR), errMsg);
    }
}
