/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit DropLookupModeTest
 * @summary Basic unit tests Lookup::dropLookupMode
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.Lookup.*;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class DropLookupModeTest {

    /**
     * Basic test of dropLookupMode
     */
    @Test
    public void testBasic() {
        final Lookup fullPowerLookup = MethodHandles.lookup();
        final Class<?> lc = fullPowerLookup.lookupClass();
        assertEquals(PUBLIC | MODULE | PACKAGE | PROTECTED | PRIVATE | ORIGINAL, fullPowerLookup.lookupModes());

        Lookup lookup = fullPowerLookup.dropLookupMode(PRIVATE);
        assertSame(lc, lookup.lookupClass());
        assertEquals(PUBLIC | MODULE | PACKAGE, lookup.lookupModes());

        lookup = fullPowerLookup.dropLookupMode(PROTECTED);
        assertSame(lc, lookup.lookupClass());
        assertEquals(PUBLIC | MODULE | PACKAGE | PRIVATE, lookup.lookupModes());

        lookup = fullPowerLookup.dropLookupMode(PACKAGE);
        assertSame(lc, lookup.lookupClass());
        assertEquals(PUBLIC | MODULE, lookup.lookupModes());

        lookup = fullPowerLookup.dropLookupMode(MODULE);
        assertSame(lc, lookup.lookupClass());
        assertEquals(PUBLIC, lookup.lookupModes());

        lookup = fullPowerLookup.dropLookupMode(PUBLIC);
        assertSame(lc, lookup.lookupClass());
        assertEquals(0, lookup.lookupModes());

        lookup = fullPowerLookup.dropLookupMode(UNCONDITIONAL);
        assertSame(lc, lookup.lookupClass());
        assertEquals(PUBLIC | MODULE | PACKAGE | PRIVATE, lookup.lookupModes());
    }

    /**
     * Starting with a full power Lookup, use dropLookupMode to create new Lookups
     * with reduced access.
     */
    @Test
    public void testReducingAccess() {
        Lookup lookup = MethodHandles.lookup();
        final Class<?> lc = lookup.lookupClass();
        assertEquals(PUBLIC | MODULE | PACKAGE | PROTECTED | PRIVATE | ORIGINAL, lookup.lookupModes());

        lookup = lookup.dropLookupMode(PROTECTED);
        assertSame(lc, lookup.lookupClass());
        assertEquals(PUBLIC | MODULE | PACKAGE | PRIVATE, lookup.lookupModes());

        lookup = lookup.dropLookupMode(PRIVATE);
        assertSame(lc, lookup.lookupClass());
        assertEquals(PUBLIC | MODULE | PACKAGE, lookup.lookupModes());

        lookup = lookup.dropLookupMode(PACKAGE);
        assertSame(lc, lookup.lookupClass());
        assertEquals(PUBLIC | MODULE, lookup.lookupModes());

        lookup = lookup.dropLookupMode(MODULE);
        assertSame(lc, lookup.lookupClass());
        assertEquals(PUBLIC, lookup.lookupModes());

        lookup = lookup.dropLookupMode(PUBLIC);
        assertSame(lc, lookup.lookupClass());
        assertEquals(0, lookup.lookupModes());

        // repeat with lookup has no access
        lookup = lookup.dropLookupMode(PUBLIC);
        assertSame(lc, lookup.lookupClass());
        assertEquals(0, lookup.lookupModes());
    }

    public static Object[][] unconditionals() {
        Lookup publicLookup = MethodHandles.publicLookup();
        return new Object[][] {
            { publicLookup, Object.class },
            { publicLookup.in(String.class), String.class },
            { publicLookup.in(DropLookupModeTest.class), DropLookupModeTest.class },
        };
    }

    /**
     * Test dropLookupMode on the lookup with public lookup
     * and UNCONDITIONAL
     */
    @ParameterizedTest
    @MethodSource("unconditionals")
    public void testUnconditionalLookup(Lookup unconditionalLookup, Class<?> expected) {
        assertEquals(UNCONDITIONAL, unconditionalLookup.lookupModes());

        assertPublicLookup(unconditionalLookup.dropLookupMode(PRIVATE), expected);
        assertPublicLookup(unconditionalLookup.dropLookupMode(PROTECTED), expected);
        assertPublicLookup(unconditionalLookup.dropLookupMode(PACKAGE), expected);
        assertPublicLookup(unconditionalLookup.dropLookupMode(MODULE), expected);
        assertPublicLookup(unconditionalLookup.dropLookupMode(PUBLIC), expected);

        // drop all access
        Lookup lookup = unconditionalLookup.dropLookupMode(UNCONDITIONAL);
        assertSame(expected, lookup.lookupClass());
        assertEquals(0, lookup.lookupModes());
    }

    private void assertPublicLookup(Lookup lookup, Class<?> expected) {
        assertSame(expected, lookup.lookupClass());
        assertEquals(UNCONDITIONAL, lookup.lookupModes());
    }

    /**
     * Check that IllegalArgumentException is thrown for bad input
     */
    @ParameterizedTest
    @ValueSource(ints = {
            0,
            (PACKAGE|PRIVATE),    // two modes
            Integer.MAX_VALUE,
            Integer.MIN_VALUE,
    })
    public void testBadInput(int modeToDrop) {
        assertThrows(IllegalArgumentException.class, () -> MethodHandles.lookup().dropLookupMode(modeToDrop));
    }

}
