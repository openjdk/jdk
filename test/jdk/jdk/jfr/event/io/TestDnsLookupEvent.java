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
 * accompanies this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jfr.event.io;

import static jdk.test.lib.Asserts.assertNotNull;
import static jdk.test.lib.Asserts.assertTrue;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary test DNS lookup events
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestDnsLookupEvent
 */
public class TestDnsLookupEvent {

    private static final String EVENT_DNS_LOOKUP = "jdk.DnsLookup";

    private Set<String> expectedHosts = new HashSet<>();

    public static void main(String[] args) throws Throwable {
        new TestDnsLookupEvent().test();
    }

    private void test() throws Throwable {
        try (Recording recording = new Recording()) {
            recording.enable(EVENT_DNS_LOOKUP).withThreshold(Duration.ofMillis(0));
            recording.start();

            // Perform DNS lookups that should trigger actual DNS queries
            String hostname1 = "www.example.com";
            InetAddress[] addresses1 = InetAddress.getAllByName(hostname1);
            assertNotNull(addresses1, "Should resolve addresses for " + hostname1);
            assertTrue(addresses1.length > 0, "Should resolve at least one address");
            expectedHosts.add(hostname1);

            // Perform another lookup with different hostname
            String hostname2 = "www.oracle.com";
            InetAddress[] addresses2 = InetAddress.getAllByName(hostname2);
            assertNotNull(addresses2, "Should resolve addresses for " + hostname2);
            assertTrue(addresses2.length > 0, "Should resolve at least one address");
            expectedHosts.add(hostname2);

            // Another lookup of same hostname should be from cache (no event)
            InetAddress[] addresses3 = InetAddress.getAllByName(hostname1);
            assertNotNull(addresses3, "Should resolve addresses for " + hostname1);

            recording.stop();

            // Verify events
            List<RecordedEvent> events = Events.fromRecording(recording);
            List<RecordedEvent> dnsEvents = new ArrayList<>();
            for (RecordedEvent event : events) {
                if (EVENT_DNS_LOOKUP.equals(event.getEventType().getName())) {
                    dnsEvents.add(event);
                }
            }

            // Should have at least 2 DNS lookup events (one for each unique hostname)
            // The second lookup of hostname1 should be from cache, so no additional event
            assertTrue(dnsEvents.size() >= expectedHosts.size(),
                String.format("Expected at least %d DNS lookup events, but got %d",
                    expectedHosts.size(), dnsEvents.size()));

            // Verify each event
            Set<String> foundHosts = new HashSet<>();
            for (RecordedEvent event : dnsEvents) {
                String host = Events.assertField(event, "host").getValue();
                boolean success = Events.assertField(event, "success").getValue();
                String result = Events.assertField(event, "result").getValue();

                assertNotNull(host, "Host should not be null");
                assertTrue(success, "DNS lookup should be successful");
                assertNotNull(result, "Result should not be null");
                assertTrue(!result.isEmpty(), "Result should not be empty");

                foundHosts.add(host);
            }

            // Verify we found events for all expected hosts
            assertTrue(foundHosts.containsAll(expectedHosts),
                String.format("Expected hosts %s, but found %s", expectedHosts, foundHosts));
        }
    }
}

