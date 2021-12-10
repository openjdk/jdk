/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.DataProvider;

/** TestNG DataProvider for SourceDate values */
public class SourceDateDataProvider {

    private static final Object[][] validSourceDates = {
                                 {"1980-01-01T00:00:02+00:00"},
                                 {"1986-06-24T01:02:03+00:00"},
                                 {"2022-03-15T00:00:00+00:00"},
                                 {"2022-03-15T00:00:00+06:00"},
                                 {"2021-12-25T09:30:00-08:00[America/Los_Angeles]"},
                                 {"2021-12-31T23:59:59Z"},
                                 {"2024-06-08T14:24Z"},
                                 {"2026-09-24T16:26-05:00"},
                                 {"2038-11-26T06:06:06+00:00"},
                                 {"2098-02-18T00:00:00-08:00"},
                                 {"2099-12-31T23:59:59+00:00"}
                               };

    private static final Object[][] invalidSourceDates = {
                                 {"1976-06-24T01:02:03+00:00"},
                                 {"1980-01-01T00:00:01+00:00"},
                                 {"2100-01-01T00:00:00+00:00"},
                                 {"2138-02-18T00:00:00-11:00"},
                                 {"2006-04-06T12:38:00"},
                                 {"2012-08-24T16"}
                               };

    @DataProvider(name = "SourceDateData.valid")
    public static Object[][] makeValidSourceDataData() {
        return validSourceDates;
    }

    @DataProvider(name = "SourceDateData.invalid")
    public static Object[][] makeInvalidSourceDataData() {
        return invalidSourceDates;
    }

}

