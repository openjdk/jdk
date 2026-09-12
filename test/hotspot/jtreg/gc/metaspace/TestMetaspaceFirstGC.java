/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestMetaspaceFirstGC
 * @bug 8208250
 * @summary Verify that the first metaspace GC happens near the MetaspaceSize threshold
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -Xms200m TestMetaspaceFirstGC
 * @run main/othervm -Xms200m -XX:MetaspaceSize=10m TestMetaspaceFirstGC 10m
 * @run main/othervm -Xms200m -XX:MetaspaceSize=50m TestMetaspaceFirstGC 50m
 * @run main/othervm -Xms200m -XX:MetaspaceSize=99m TestMetaspaceFirstGC 99m
 */

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

public class TestMetaspaceFirstGC {

    private static int classCounter = 0;

    public interface Dummy {}

    static class DummyHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        long expectedSize = -1;
        if (args.length > 0) {
            expectedSize = parseSize(args[0]);
        }

        try (Recording recording = new Recording()) {
            recording.enable(EventNames.GarbageCollection);
            recording.enable(EventNames.MetaspaceSummary).withThreshold(Duration.ofMillis(0));
            recording.start();

            // Load classes until a metaspace-triggered GC happens
            loadClassesUntilGC(50000);

            recording.stop();

            List<RecordedEvent> events = Events.fromRecordingOrdered(recording);

            // Find first GarbageCollection with cause "Metadata GC Threshold"
            RecordedEvent gcEvent = null;
            for (RecordedEvent event : events) {
                if (event.getEventType().getName().equals(EventNames.GarbageCollection)) {
                    String cause = event.getString("cause");
                    if ("Metadata GC Threshold".equals(cause)) {
                        gcEvent = event;
                        break;
                    }
                }
            }

            if (gcEvent == null) {
                throw new RuntimeException("No GC with cause 'Metadata GC Threshold' found");
            }

            int gcId = gcEvent.getInt("gcId");
            System.out.println("Found Metadata GC Threshold GC, gcId=" + gcId);

            // Find matching MetaspaceSummary with same gcId and when="Before GC"
            RecordedEvent msEvent = null;
            for (RecordedEvent event : events) {
                if (event.getEventType().getName().equals(EventNames.MetaspaceSummary)) {
                    if (event.getInt("gcId") == gcId && "Before GC".equals(event.getString("when"))) {
                        msEvent = event;
                        break;
                    }
                }
            }

            if (msEvent == null) {
                throw new RuntimeException("No MetaspaceSummary 'Before GC' found for gcId=" + gcId);
            }

            long committed = msEvent.getLong("metaspace.committed");
            long gcThreshold = msEvent.getLong("gcThreshold");
            System.out.println("MetaspaceSummary: committed=" + committed + " gcThreshold=" + gcThreshold);

            // committed should be reasonably close to gcThreshold
            long tolerance = 5 * 1024 * 1024; // 5MB tolerance
            Asserts.assertLessThanOrEqual(Math.abs(committed - gcThreshold), tolerance,
                "committed (" + committed + ") should be close to gcThreshold (" + gcThreshold + ")");

            // If explicit MetaspaceSize given, gcThreshold should match it
            if (expectedSize > 0) {
                Asserts.assertLessThanOrEqual(Math.abs(gcThreshold - expectedSize), tolerance,
                    "gcThreshold (" + gcThreshold + ") should be close to MetaspaceSize (" + expectedSize + ")");
                System.out.println("gcThreshold matches expected MetaspaceSize=" + expectedSize);
            } else {
                // No explicit MetaspaceSize — check default range (~12MB to ~20MB per tuning guide)
                Asserts.assertGreaterThan(gcThreshold, 11_500_000L,
                    "default gcThreshold (" + gcThreshold + ") too small");
                Asserts.assertLessThan(gcThreshold, 22_500_000L,
                    "default gcThreshold (" + gcThreshold + ") too large");
                System.out.println("gcThreshold in expected default range");
            }

            System.out.println("PASSED");
        }
    }

    private static void loadClassesUntilGC(int maxIterations) {
        long prevUsed = getMetaspaceUsed();
        for (int i = 0; i < maxIterations; i++) {
            loadOneClass();
            long used = getMetaspaceUsed();
            if (used < prevUsed) {
                System.out.println("GC detected at iteration " + i +
                    ", used dropped from " + prevUsed + " to " + used);
                return;
            }
            prevUsed = used;
        }
        throw new RuntimeException("No metaspace GC after " + maxIterations + " class loads");
    }

    private static void loadOneClass() {
        try {
            String jarUrl = "file:" + (classCounter++) + ".jar";
            URLClassLoader cl = new URLClassLoader(new URL[]{new URL(jarUrl)});
            Proxy.newProxyInstance(cl, new Class[]{Dummy.class}, new DummyHandler());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long getMetaspaceUsed() {
        return java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getName().equals("Metaspace"))
            .mapToLong(p -> p.getUsage().getUsed())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Metaspace pool not found"));
    }

    private static long parseSize(String size) {
        size = size.toLowerCase();
        long multiplier = 1;
        if (size.endsWith("m")) {
            multiplier = 1024 * 1024;
            size = size.substring(0, size.length() - 1);
        } else if (size.endsWith("k")) {
            multiplier = 1024;
            size = size.substring(0, size.length() - 1);
        } else if (size.endsWith("g")) {
            multiplier = 1024 * 1024 * 1024;
            size = size.substring(0, size.length() - 1);
        }
        return Long.parseLong(size) * multiplier;
    }
}
