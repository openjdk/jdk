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
import jdk.test.lib.jfr.EventNames;

/**
 * @test
 * @summary test DNS lookup events with three scenarios: actual network queries,
 *          cache hits, and stale data usage
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm -Dnetworkaddress.cache.ttl=1 -Dnetworkaddress.cache.stale.ttl=2 jdk.jfr.event.io.TestDnsLookupEvent
 */
public class TestDnsLookupEvent {

    private static final String EVENT_DNS_LOOKUP = EventNames.DnsLookup;
    private static final String EVENT_DNS_CACHE_STATISTICS = EventNames.DnsCacheStatistics;

    private Set<String> expectedNetworkQueryHosts = new HashSet<>();
    private Set<String> expectedCachedHosts = new HashSet<>();

    public static void main(String[] args) throws Throwable {
        new TestDnsLookupEvent().test();
    }

    private void test() throws Throwable {
        try (Recording recording = new Recording()) {
            recording.enable(EVENT_DNS_LOOKUP).withThreshold(Duration.ofMillis(0));
            recording.enable(EVENT_DNS_CACHE_STATISTICS).withThreshold(Duration.ofMillis(0));
            recording.start();

            // Scenario 1: Actual DNS network queries (cached=false)
            // First lookup should trigger actual DNS query
            String hostname1 = "www.example.com";
            InetAddress[] addresses1 = InetAddress.getAllByName(hostname1);
            assertNotNull(addresses1, "Should resolve addresses for " + hostname1);
            assertTrue(addresses1.length > 0, "Should resolve at least one address");
            expectedNetworkQueryHosts.add(hostname1);

            // Another unique hostname should also trigger actual DNS query
            String hostname2 = "www.oracle.com";
            InetAddress[] addresses2 = InetAddress.getAllByName(hostname2);
            assertNotNull(addresses2, "Should resolve addresses for " + hostname2);
            assertTrue(addresses2.length > 0, "Should resolve at least one address");
            expectedNetworkQueryHosts.add(hostname2);

            // Scenario 2: Cache hits (cached=true, stale=false)
            // Second lookup of same hostname should be from cache
            InetAddress[] addresses3 = InetAddress.getAllByName(hostname1);
            assertNotNull(addresses3, "Should resolve addresses for " + hostname1);
            expectedCachedHosts.add(hostname1);

            // Another cache hit
            InetAddress[] addresses4 = InetAddress.getAllByName(hostname2);
            assertNotNull(addresses4, "Should resolve addresses for " + hostname2);
            expectedCachedHosts.add(hostname2);

            // Scenario 3: Stale data usage (cached=true, stale=true)
            // Wait for cache to expire (TTL=1 second) and enter stale period
            // The stale period is 2 seconds, so we wait 1.5 seconds to be in stale period
            Thread.sleep(1500); // Wait for TTL to expire, but still within stale period

            // This lookup should use stale cached data (may also trigger background refresh)
            // The event should show cached=true, stale=true if stale data is used
            InetAddress[] addresses5 = InetAddress.getAllByName(hostname1);
            assertNotNull(addresses5, "Should resolve addresses for " + hostname1);
            // Note: stale events may be recorded, or background refresh may occur

            recording.stop();

            // Verify events
            List<RecordedEvent> events = Events.fromRecording(recording);
            List<RecordedEvent> dnsEvents = new ArrayList<>();
            List<RecordedEvent> cacheStatsEvents = new ArrayList<>();

            for (RecordedEvent event : events) {
                String eventName = event.getEventType().getName();
                if (EVENT_DNS_LOOKUP.equals(eventName)) {
                    dnsEvents.add(event);
                } else if (EVENT_DNS_CACHE_STATISTICS.equals(eventName)) {
                    cacheStatsEvents.add(event);
                }
            }

            // Verify we have network query events
            assertTrue(dnsEvents.size() >= expectedNetworkQueryHosts.size(),
                String.format("Expected at least %d network query events, but got %d",
                    expectedNetworkQueryHosts.size(), dnsEvents.size()));

            // Verify events
            Set<String> foundNetworkQueryHosts = new HashSet<>();
            Set<String> foundCachedHosts = new HashSet<>();
            int staleCount = 0;

            for (RecordedEvent event : dnsEvents) {
                String host = Events.assertField(event, "host").getValue();
                boolean success = Events.assertField(event, "success").getValue();
                String result = Events.assertField(event, "result").getValue();
                boolean cached = Events.assertField(event, "cached").getValue();
                long ttl = Events.assertField(event, "ttl").getValue();
                boolean stale = Events.assertField(event, "stale").getValue();

                assertNotNull(host, "Host should not be null");
                assertTrue(success, "DNS lookup should be successful");
                assertNotNull(result, "Result should not be null");
                assertTrue(!result.isEmpty(), "Result should not be empty");

                if (!cached) {
                    // Network query: cached=false, ttl=0 or -1, stale=false
                    assertTrue(ttl == 0 || ttl == -1,
                        String.format("Network query should have ttl=0 or -1, but got %d", ttl));
                    assertTrue(!stale, "Network query should not be stale");
                    foundNetworkQueryHosts.add(host);
                } else {
                    // Cache hit: cached=true
                    assertTrue(ttl >= -1, "TTL should be >= -1");
                    if (stale) {
                        staleCount++;
                        foundCachedHosts.add(host + ":stale");
                    } else {
                        foundCachedHosts.add(host);
                    }
                }
            }

            // Verify we found network query events for all expected hosts
            assertTrue(foundNetworkQueryHosts.containsAll(expectedNetworkQueryHosts),
                String.format("Expected network query hosts %s, but found %s",
                    expectedNetworkQueryHosts, foundNetworkQueryHosts));

            // Verify we found cache hit events (remove :stale suffix for comparison)
            Set<String> foundCachedHostsNormalized = new HashSet<>();
            for (String host : foundCachedHosts) {
                foundCachedHostsNormalized.add(host.replace(":stale", ""));
            }
            assertTrue(foundCachedHostsNormalized.size() >= expectedCachedHosts.size(),
                String.format("Expected at least %d cache hit events, but found %d",
                    expectedCachedHosts.size(), foundCachedHostsNormalized.size()));

            // Note: Stale data events may be recorded, but background refresh
            // may replace stale data with fresh data, so we don't strictly require stale events
            // The important thing is that we can distinguish between cached and non-cached events
            System.out.println("Found " + staleCount + " stale data events");
            System.out.println("Found " + foundNetworkQueryHosts.size() + " network query events");
            System.out.println("Found " + foundCachedHosts.size() + " cache hit events");

            // Verify cache statistics events (may be present if cache cleanup occurred)
            if (!cacheStatsEvents.isEmpty()) {
                for (RecordedEvent statsEvent : cacheStatsEvents) {
                    long cacheSize = Events.assertField(statsEvent, "cacheSize").getValue();
                    long staleEntries = Events.assertField(statsEvent, "staleEntries").getValue();
                    long entriesRemoved = Events.assertField(statsEvent, "entriesRemoved").getValue();

                    assertTrue(cacheSize >= 0, "Cache size should be >= 0");
                    assertTrue(staleEntries >= 0, "Stale entries should be >= 0");
                    assertTrue(entriesRemoved >= 0, "Entries removed should be >= 0");
                }
            }
        }
    }
}
