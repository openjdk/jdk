/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.localedata
 * @bug 8303440 8317979 8322647 8174269
 * @summary Test parsing "UTC-XX:XX" text works correctly
 */
package test.java.time.format;

import java.time.ZoneId;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.TemporalQueries;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class TestUTCParse {

    @DataProvider
    public Object[][] utcZoneIdStrings() {
        return new Object[][] {
            {"UTC"},
            {"UTC+01:30"},
            {"UTC-01:30"},
        };
    }

    @Test(dataProvider = "utcZoneIdStrings")
    public void testUTCOffsetRoundTrip(String zidString) {
        var fmt = new DateTimeFormatterBuilder()
                .appendZoneText(TextStyle.NARROW)
                .toFormatter();
        var zid = ZoneId.of(zidString);
        assertEquals(fmt.parse(zidString).query(TemporalQueries.zoneId()), zid);
    }
}
