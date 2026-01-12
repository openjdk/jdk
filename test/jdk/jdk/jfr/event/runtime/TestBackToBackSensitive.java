/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.event.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test
 * @summary The test verifies that jdk.ClassLoaderStatistics and
 *          jdk.ThreadThreadDump are not emitted at the beginning of a chunk
 *          when the period is everyChunk, as is the case in default.jfc
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.runtime.TestBackToBackSensitive
 */
public class TestBackToBackSensitive {
    @StackTrace(false)
    static class FillEvent extends Event {
        String message;
    }
    public static Object OBJECT;

    public static void main(String... arg) throws Exception {
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.loadClass(TestBackToBackSensitive.class.getName());
        String classLoaderName = loader.getClass().getName();
        OBJECT = clazz.getDeclaredConstructor().newInstance();
        Configuration configuration = Configuration.getConfiguration("default");
        try (Recording r1 = new Recording(configuration)) {
            // Start chunk 1
            r1.start();
            try (Recording r2 = new Recording()) {
                // Start chunk 2
                r2.start();
                // Starts chunk 3
                r2.stop();
            }
            // Start chunk 4 by filling up chunk 3
            for (int i = 0; i < 1_500_000; i++) {
                FillEvent f = new FillEvent();
                f.commit();
            }
            r1.stop();
            Path file = Path.of("file.jfr");
            r1.dump(file);
            Set<Instant> threadDumps = new LinkedHashSet<>();
            Set<Instant> classLoaderStatistics = new LinkedHashSet<>();
            Set<Instant> physicalMemory = new LinkedHashSet<>();
            try (EventStream es = EventStream.openFile(file)) {
                es.onEvent("jdk.ThreadDump", e -> threadDumps.add(e.getStartTime()));
                es.onEvent("jdk.ClassLoaderStatistics", e -> {
                    RecordedClassLoader cl = e.getValue("classLoader");
                    if (cl != null) {
                        if (cl.getType().getName().equals(classLoaderName)) {
                            classLoaderStatistics.add(e.getStartTime());
                            System.out.println("Class loader" + e);
                        }
                    }
                });
                es.onEvent("jdk.PhysicalMemory", e -> physicalMemory.add(e.getStartTime()));
                es.start();
            }
            long chunkFiles = filesInRepository();
            System.out.println("Number of chunk files: " + chunkFiles);
            // When jdk.PhysicalMemory is expected to be emitted:
            // Chunk 1: begin, end
            // Chunk 2: begin, end
            // Chunk 3: begin, end
            // Chunk 4: begin, end
            assertCount("jdk.PhysicalMemory", physicalMemory, 2 * chunkFiles);
            // When jdk.ClassLoaderStatistics and jdk.ThreadThreadDump are expected to be
            // emitted:
            // Chunk 1: begin, end
            // Chunk 2: begin, end
            // Chunk 3: end
            // Chunk 4: end
            assertCount("jdk.ThreadDump", threadDumps, 2 + 2 + (chunkFiles - 2));
            assertCount("jdk.ClassLoaderStatistics", classLoaderStatistics, 2 + 2 + (chunkFiles - 2));
        }
    }

    private static long filesInRepository() throws IOException {
        Path repository = Path.of(System.getProperty("jdk.jfr.repository"));
        return Files.list(repository).filter(p -> p.toString().endsWith(".jfr")).count();
    }

    private static void assertCount(String eventName, Set<Instant> timestamps, long expected) throws Exception {
        System.out.println("Timestamps for " + eventName + ":");
        for (Instant timestamp : timestamps) {
            System.out.println(timestamp);
        }
        int count = timestamps.size();
        if (count != expected) {
            throw new Exception("Expected " + expected + "  timestamps for event " + eventName + ", but got " + count);
        }
    }
}
