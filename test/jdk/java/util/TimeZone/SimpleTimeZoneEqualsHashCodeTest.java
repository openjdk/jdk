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
 * @bug 8369184
 * @summary Checks if equals()/hashCode() of SimpleTimeZone works correctly
 * @run junit SimpleTimeZoneEqualsHashCodeTest
 */

import java.util.SimpleTimeZone;
import static java.util.Calendar.MARCH;
import static java.util.Calendar.NOVEMBER;
import static java.util.Calendar.SUNDAY;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleTimeZoneEqualsHashCodeTest {
    private static final SimpleTimeZone STZ_WITH_DST =
        new SimpleTimeZone(-288_000_000, "America/Los_Angeles",
            MARCH, 8, -SUNDAY, 7_200_000,
            NOVEMBER, 1, -SUNDAY, 7_200_000);
    private static final SimpleTimeZone STZ_WITHOUT_DST =
        new SimpleTimeZone(0, "foo");

    @Test
    void withDSTTest() {
        var stz = (SimpleTimeZone)STZ_WITH_DST.clone();
        assertEquals(STZ_WITH_DST, stz);
        assertEquals(STZ_WITH_DST.hashCode(), stz.hashCode());

        stz.setEndRule(NOVEMBER, 8, -SUNDAY, 7_200_000);
        assertNotEquals(STZ_WITH_DST, stz);
        // From the contract point, hash codes may be the same.
        // This tests the implementation which considers DST
        // related fields for calculating the hash code.
        assertNotEquals(STZ_WITH_DST.hashCode(), stz.hashCode());
    }

    @Test
    void withoutDSTTest() {
        var stz = (SimpleTimeZone)STZ_WITHOUT_DST.clone();

        // Only setting start rule. Still considered non-DST zone
        stz.setStartRule(MARCH, 8, -SUNDAY, 7_200_000);
        assertTrue(!stz.useDaylightTime());
        assertEquals(STZ_WITHOUT_DST, stz);
        assertEquals(STZ_WITHOUT_DST.hashCode(), stz.hashCode());

        // Setting end rule as well. Now it is considered DST zone
        stz.setEndRule(NOVEMBER, 8, -SUNDAY, 7_200_000);
        assertTrue(stz.useDaylightTime());
        assertNotEquals(STZ_WITHOUT_DST, stz);
        // From the contract point, hash codes may be the same.
        // This tests the implementation which considers DST
        // related fields for calculating the hash code.
        assertNotEquals(STZ_WITHOUT_DST.hashCode(), stz.hashCode());
    }
}
