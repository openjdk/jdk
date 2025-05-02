/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4028518
 * @summary Ensure cloned GregorianCalendar is unchanged when modifying its original.
 * @run junit bug4028518
 */

import java.util.GregorianCalendar;

import static java.util.Calendar.DAY_OF_MONTH;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class bug4028518 {

    /*
     * Ensure modifying the original GregorianCalendar does not
     * modify the cloned one as well
     */
    @Test
    public void clonedShouldNotChangeOriginalTest() {
        GregorianCalendar cal1 = new GregorianCalendar() ;
        GregorianCalendar cal2 = (GregorianCalendar) cal1.clone() ;
        cal1.add(DAY_OF_MONTH, 1) ;
        assertNotEquals(cal1.get(DAY_OF_MONTH), cal2.get(DAY_OF_MONTH),
                "Cloned calendar should not have same value as original");
    }
}
