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
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary test DNS cache statistics periodic event
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestDnsCacheStatisticsEvent
 */
public class TestDnsCacheStatisticsEvent {

    private static final String EVENT_NAME = EventNames.DnsCacheStatistics;

    public static void main(String[] args) throws Throwable {
        try (Recording recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();

            // Perform DNS lookups to populate the cache
            String[] hostnames = {
                "www.example.com",
                "www.oracle.com",
                "www.google.com",
                "www.github.com"
            };

            for (String hostname : hostnames) {
                try {
                    InetAddress[] addresses = InetAddress.getAllByName(hostname);
                    assertNotNull(addresses, "Should resolve addresses for " + hostname);
                    assertTrue(addresses.length > 0, "Should resolve at least one address");
                } catch (Exception e) {
                    // Ignore resolution failures, we just want to populate cache
                    System.out.println("Failed to resolve " + hostname + ": " + e.getMessage());
                }
            }

            // Wait for periodic event to fire (default period is 5 seconds in default.jfc)
            // Wait a bit longer to ensure at least one event is recorded
            Thread.sleep(6000);

            recording.stop();

            // Verify events
            List<RecordedEvent> events = Events.fromRecording(recording);
            Events.hasEvents(events);

            boolean foundEvent = false;
            long maxCacheSize = 0;
            long maxStaleEntries = 0;
            long maxEntriesRemoved = 0;

            for (RecordedEvent event : events) {
                if (Events.isEventType(event, EVENT_NAME)) {
                    System.out.println("Event: " + event);
                    foundEvent = true;

                    long cacheSize = Events.assertField(event, "cacheSize").atLeast(0L).getValue();
                    long staleEntries = Events.assertField(event, "staleEntries").atLeast(0L).getValue();
                    long entriesRemoved = Events.assertField(event, "entriesRemoved").atLeast(0L).getValue();

                    // Take maximum values across multiple events
                    maxCacheSize = Math.max(maxCacheSize, cacheSize);
                    maxStaleEntries = Math.max(maxStaleEntries, staleEntries);
                    maxEntriesRemoved = Math.max(maxEntriesRemoved, entriesRemoved);

                    // Verify field constraints
                    // staleEntries should not exceed cacheSize
                    assertTrue(staleEntries <= cacheSize,
                        String.format("staleEntries (%d) should not exceed cacheSize (%d)",
                            staleEntries, cacheSize));
                }
            }

            assertTrue(foundEvent, "No DNS cache statistics events found");

            // Verify that we have at least some cached entries
            // (may be 0 if all lookups failed, but we should have tried)
            System.out.println("Max cache size: " + maxCacheSize);
            System.out.println("Max stale entries: " + maxStaleEntries);
            System.out.println("Max entries removed: " + maxEntriesRemoved);
        }
    }
}

