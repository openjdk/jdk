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

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.testng.Assert;
import org.testng.annotations.Test;

import impl.SimpleResolverProviderImpl;


/*
 * @test
 * @summary Test that InetAddress caching security properties work as expected
 *  when a custom resolver is installed.
 * @library lib providers/simple
 * @build test.library/testlib.ResolutionRegistry
 *  simple.provider/impl.SimpleResolverProviderImpl AddressesCachingTest
 * @run testng/othervm -Djava.security.properties=${test.src}/NeverCache.props
 *  -Dtest.cachingDisabled=true AddressesCachingTest
 * @run testng/othervm -Djava.security.properties=${test.src}/ForeverCache.props
 *  -Dtest.cachingDisabled=false AddressesCachingTest
 */
public class AddressesCachingTest {

    @Test
    public void testPositiveCaching() {
        boolean observedTwoLookups = performLookups(false);
        if (CACHING_DISABLED) {
            Assert.assertTrue(observedTwoLookups,
                    "Two positive lookups are expected with caching disabled");
        } else {
            Assert.assertFalse(observedTwoLookups,
                    "Only one positive lookup is expected with caching enabled");
        }
    }

    @Test
    public void testNegativeCaching() {
        boolean observedTwoLookups = performLookups(true);
        if (CACHING_DISABLED) {
            Assert.assertTrue(observedTwoLookups,
                    "Two negative lookups are expected with caching disabled");
        } else {
            Assert.assertFalse(observedTwoLookups,
                    "Only one negative lookup is expected with caching enabled");
        }
    }

    /*
     * Performs two subsequent positive or negative lookups.
     * Returns true if the timestamp of this lookups differs,
     * false otherwise.
     */
    private static boolean performLookups(boolean performNegativeLookup) {
        doLookup(performNegativeLookup);
        long firstTimestamp = SimpleResolverProviderImpl.getLastLookupTimestamp();
        doLookup(performNegativeLookup);
        long secondTimestamp = SimpleResolverProviderImpl.getLastLookupTimestamp();
        return firstTimestamp != secondTimestamp;
    }

    // Performs negative or positive lookup.
    // It is a test error if UnknownHostException is thrown during positive lookup.
    // It is a test error if UnknownHostException is NOT thrown during negative lookup.
    private static void doLookup(boolean performNegativeLookup) {
        String hostName = performNegativeLookup ? "notKnowHost.org" : "javaTest.org";
        try {
            InetAddress.getByName(hostName);
            if (performNegativeLookup) {
                Assert.fail("Host name is expected to get unresolved");
            }
        } catch (UnknownHostException uhe) {
            if (!performNegativeLookup) {
                Assert.fail("Host name is expected to get resolved");
            }
        }
    }

    // Helper system property that signals to the test if both negative and positive
    // caches are disabled.
    private static final boolean CACHING_DISABLED = Boolean.getBoolean("test.cachingDisabled");
}
