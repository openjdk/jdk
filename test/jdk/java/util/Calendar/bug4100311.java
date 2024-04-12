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
 * @bug 4100311
 * @summary Ensure set(DAY_OF_YEAR, 1) works.
 * @run junit bug4100311
 */

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Date;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class bug4100311 {

    // GregorianCalendar should be able to date to january 1st properly
    @SuppressWarnings("deprecation")
    @Test
    public void dayOfYearIsOneTest() {
        GregorianCalendar cal = new GregorianCalendar();
        cal.set(Calendar.YEAR, 1997);
        cal.set(Calendar.DAY_OF_YEAR, 1);
        Date d = cal.getTime();
        assertEquals(0, d.getMonth(), "Date: "+d+" isn't January 1st");
        assertEquals(1, d.getDate(),"Date: "+d+" isn't January 1st");
    }
}
