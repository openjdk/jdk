/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.time.ZoneId;
import java.util.TimeZone;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.assertEquals;

/**
 * @test
 * @bug 8285844
 * @summary Checks round-trips between TimeZone and ZoneOffset are consistent
 * @run testng ZoneOffsetRoundTripTest
 */
@Test
public class ZoneOffsetRoundTripTest {

    @DataProvider
    private Object[][] testZoneOffsets() {
        return new Object[][] {
                {ZoneId.of("Z"), 0},
                {ZoneId.of("+00:01"), 60},
                {ZoneId.of("-00:01"), -60},
                {ZoneId.of("+00:00:01"), 1},
                {ZoneId.of("-00:00:01"), -1},
        };
    }

    @Test(dataProvider="testZoneOffsets")
    public void test_ZoneOffsetRoundTrip(ZoneId zid, int offset) {
        var tz = TimeZone.getTimeZone(zid);
        assertEquals(tz.getRawOffset(), offset);
        assertEquals(tz.toZoneId().normalized(), zid);
    }
}

