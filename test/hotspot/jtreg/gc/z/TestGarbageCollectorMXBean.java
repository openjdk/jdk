/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test TestGarbageCollectorMXBean
 * @requires vm.gc.Z & !vm.graal.enabled
 * @summary Test ZGC garbage collector MXBean
 * @modules java.management
 * @run main/othervm -XX:+UseZGC -Xms256M -Xmx512M -Xlog:gc TestGarbageCollectorMXBean 256 512
 * @run main/othervm -XX:+UseZGC -Xms512M -Xmx512M -Xlog:gc TestGarbageCollectorMXBean 512 512
 */

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;

public class TestGarbageCollectorMXBean {
    public static void main(String[] args) throws Exception {
        final long M = 1024 * 1024;
        final long initialCapacity = Long.parseLong(args[0]) * M;
        final long maxCapacity = Long.parseLong(args[1]) * M;
        final AtomicInteger cycles = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();

        final NotificationListener listener = (Notification notification, Object ignored) -> {
            final var type = notification.getType();
            if (!type.equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                // Ignore
                return;
            }

            final var data = (CompositeData)notification.getUserData();
            final var info = GarbageCollectionNotificationInfo.from(data);
            final var name = info.getGcName();
            final var id = info.getGcInfo().getId();
            final var action = info.getGcAction();
            final var cause = info.getGcCause();
            final var startTime = info.getGcInfo().getStartTime();
            final var endTime = info.getGcInfo().getEndTime();
            final var duration = info.getGcInfo().getDuration();
            final var memoryUsageBeforeGC = info.getGcInfo().getMemoryUsageBeforeGc().get("ZHeap");
            final var memoryUsageAfterGC = info.getGcInfo().getMemoryUsageAfterGc().get("ZHeap");

            System.out.println(name + " (" + type + ")");
            System.out.println("                  Id: " + id);
            System.out.println("              Action: " + action);
            System.out.println("               Cause: " + cause);
            System.out.println("           StartTime: " + startTime);
            System.out.println("             EndTime: " + endTime);
            System.out.println("            Duration: " + duration);
            System.out.println(" MemoryUsageBeforeGC: " + memoryUsageBeforeGC);
            System.out.println("  MemoryUsageAfterGC: " + memoryUsageAfterGC);
            System.out.println();

            if (name.equals("ZGC")) {
                cycles.incrementAndGet();
            } else {
                System.out.println("ERROR: Name");
                errors.incrementAndGet();
            }

            if (!action.equals("end of major GC")) {
                System.out.println("ERROR: Action");
                errors.incrementAndGet();
            }

            if (memoryUsageBeforeGC.getInit() != initialCapacity) {
                System.out.println("ERROR: MemoryUsageBeforeGC.init");
                errors.incrementAndGet();
            }

            if (memoryUsageBeforeGC.getUsed() > initialCapacity) {
                System.out.println("ERROR: MemoryUsageBeforeGC.used");
                errors.incrementAndGet();
            }

            if (memoryUsageBeforeGC.getCommitted() != initialCapacity) {
                System.out.println("ERROR: MemoryUsageBeforeGC.committed");
                errors.incrementAndGet();
            }

            if (memoryUsageBeforeGC.getMax() != maxCapacity) {
                System.out.println("ERROR: MemoryUsageBeforeGC.max");
                errors.incrementAndGet();
            }

            if (!cause.equals("System.gc()")) {
                System.out.println("ERROR: Cause");
                errors.incrementAndGet();
            }

            if (startTime > endTime) {
                System.out.println("ERROR: StartTime");
                errors.incrementAndGet();
            }

            if (endTime - startTime != duration) {
                System.out.println("ERROR: Duration");
                errors.incrementAndGet();
            }
        };

        // Collect garbage created at startup
        System.gc();

        // Register GC event listener
        for (final var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            final NotificationBroadcaster broadcaster = (NotificationBroadcaster)collector;
            broadcaster.addNotificationListener(listener, null, null);
        }

        final int minCycles = 5;

        // Run GCs
        for (int i = 0; i < minCycles; i++) {
            System.gc();
        }

        // Wait at most 60 seconds
        for (int i = 0; i < 60; i++) {
            Thread.sleep(1000);
            if (cycles.get() >= minCycles) {
                // All events received
                break;
            }
        }

        final int actualCycles = cycles.get();
        final int actualErrors = errors.get();

        System.out.println("   minCycles: " + minCycles);
        System.out.println("actualCycles: " + actualCycles);
        System.out.println("actualErrors: " + actualErrors);

        // Verify number of cycle events
        if (cycles.get() < minCycles) {
            throw new Exception("Unexpected cycles");
        }

        // Verify number of errors
        if (actualErrors != 0) {
            throw new Exception("Unexpected errors");
        }
    }
}
