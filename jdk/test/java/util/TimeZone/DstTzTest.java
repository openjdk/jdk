/*
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyright (c) 2012 IBM Corporation
 */

/* @test
 * @bug 7094176
 * @summary Incorrect TimeZone display name when DST not applicable and
 * disabled
 * @run main DstTzTest
 */

import java.util.TimeZone;

/**
 * Manaul steps:
 * 1. In the Windows Date and Time Properties dialog, set the time zone to one that uses DST (e.g. Greenwich Mean Time).
 * 2. Disable the 'Automatically adjust clock for Daylight Saving Changes' option.
 * 3. Change the time zone to one that does not use DST (e.g. India Standard Time - (GMT+5:30) Chennai,Kolkata,Mumbai,New Delhi)
 * 4. Compile and run the testcase
 */
public class DstTzTest {
    public static void main(String[] args) throws Exception {
        String expectedName = "India Standard Time";
        String tzName = TimeZone.getDefault().getDisplayName();
        System.out.println(tzName);

        if (!expectedName.equals(tzName)) {
            throw new Exception("Expected time zone name is " + expectedName + ", output is " + tzName);
        }
    }
}
