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
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordingStream;

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

    public static void main(String... arg) throws Exception {
        Set<Instant> threadDumps = Collections.synchronizedSet(new LinkedHashSet<>());
        Set<Instant> classLoaderStatistics = Collections.synchronizedSet(new LinkedHashSet<>());
        Set<Instant> physicalMemory = Collections.synchronizedSet(new LinkedHashSet<>());

        Configuration configuration = Configuration.getConfiguration("default");
        try (RecordingStream r1 = new RecordingStream(configuration)) {
            r1.setMaxSize(Long.MAX_VALUE);
            r1.onEvent("jdk.ThreadDump", e -> threadDumps.add(e.getStartTime()));
            r1.onEvent("jdk.ClassLoaderStatistics", e -> {
                RecordedClassLoader cl = e.getValue("classLoader");
                if (cl != null) {
                    if (cl.getType().getName().contains("PlatformClassLoader")) {
                        classLoaderStatistics.add(e.getStartTime());
                    }
                }
            });
            r1.onEvent("jdk.PhysicalMemory", e -> physicalMemory.add(e.getStartTime()));
            // Start chunk 1
            r1.startAsync();
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
            long chunkFiles = filesInRepository();
            System.out.println("Number of chunk files: " + chunkFiles);
            // When jdk.ClassLoaderStatistics and jdk.ThreadThreadDump are expected to be
            // emitted:
            // Chunk 1: begin, end
            // Chunk 2: begin, end
            // Chunk 3: end
            // Chunk 4: end
            assertCount("jdk.ThreadDump", threadDumps, 2 + 2 + (chunkFiles - 2));
            assertCount("jdk.ClassLoaderStatistics", classLoaderStatistics, 2 + 2 + (chunkFiles - 2));
            // When jdk.PhysicalMemory is expected to be emitted:
            // Chunk 1: begin, end
            // Chunk 2: begin, end
            // Chunk 3: begin, end
            // Chunk 4: begin, end
            assertCount("jdk.PhysicalMemory", physicalMemory, 2 * chunkFiles);
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
