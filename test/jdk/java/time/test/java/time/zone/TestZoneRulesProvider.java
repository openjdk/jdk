/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package test.java.time.zone;

import java.time.ZoneId;
import java.time.zone.ZoneRules;
import java.time.zone.ZoneRulesException;
import java.time.zone.ZoneRulesProvider;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.NavigableMap;
import java.util.Set;

import org.testng.annotations.Test;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @summary Tests for ZoneRulesProvider class.
 * @bug 8299571
 * @bug 8302983
 */
@Test
public class TestZoneRulesProvider {
    private static final Set<String> MY_ZONE_IDS =
        new LinkedHashSet(Arrays.asList(new String[] {"MyID_1", "MyID_2", "CET", "MyID_3"}));

    /**
     * Tests whether partially registered zones are cleaned on a provider registration
     * failure, in case a duplicated zone is detected.
     */
    @Test
    public void test_registerDuplicatedZone() {
        try {
            ZoneRulesProvider.registerProvider(new ZoneRulesProvider() {
                @Override
                protected Set<String> provideZoneIds() {
                    return MY_ZONE_IDS;
                }

                @Override
                protected ZoneRules provideRules(String zoneId, boolean forCaching) {
                    return null;
                }

                @Override
                protected NavigableMap<String, ZoneRules> provideVersions(String zoneId) {
                    return null;
                }
            });
            throw new RuntimeException("Registering a provider that duplicates a zone should throw an exception");
        } catch (ZoneRulesException e) {
            // Ignore. Failure on registration is expected.
        }

        MY_ZONE_IDS.stream().forEach(id -> {
            var isCET = id.equals("CET");

            // availability check
            var available = ZoneId.getAvailableZoneIds().contains(id);
            if (available ^ isCET) {
                throw new RuntimeException("Unexpected availability for " + id + ", availability: " + available);
            }

            // instantiation check
            try {
                ZoneId.of(id);
                assertTrue(isCET, "ZoneId.of() for the custom id %s should throw ZoneRulesException.".formatted(id));
            } catch (ZoneRulesException e) {
                assertFalse(isCET, "Not possible to obtain a ZoneId for \"CET\".");
            }
        });
    }

    /**
     * Tests whether registering a provider twice will still leave it registered.
     */
    @Test
    public void test_registerTwice() {
        String zone = "MyID";
        var provider = new ZoneRulesProvider() {
            @Override
            protected Set<String> provideZoneIds() {
                return Set.of(zone);
            }

            @Override
            protected ZoneRules provideRules(String zoneId, boolean forCaching) {
                return null;
            }

            @Override
            protected NavigableMap<String, ZoneRules> provideVersions(String zoneId) {
                return null;
            }
        }
        assertFalse(ZoneId.getAvailableZoneIds().contains(zone), "Unexpected availability for " + zone);
        ZoneRulesProvider.registerProvider(provider);
        assertTrue(ZoneId.getAvailableZoneIds().contains(zone), "Unexpected non-availability for " + zone);
        assertNotNull(ZoneId.of(zone), "ZoneId instance for " + zone + " should be obtainable");

        try {
            ZoneRulesProvider.registerProvider(provider);
            throw new RuntimeException("Registering an already registered provider should throw an exception");
        } catch (ZoneRulesException e) {
            // Ignore. Failure on duplicate registration is expected.
        }

        // availability check
        assertTrue(ZoneId.getAvailableZoneIds().contains(zone), "Unexpected non-availability for " + id);
        // instantiation check
        try {
            assertNotNull(ZoneId.of(zone), "ZoneId instance for " + zone + " should still be obtainable");
        } catch (ZoneRulesException e) {
            fail("ZoneId instance for " + zone + " should still be obtainable", e);
        }
    }
}
