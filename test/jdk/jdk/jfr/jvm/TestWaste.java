/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jvm;

import jdk.jfr.Recording;
import jdk.jfr.Event;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.test.WhiteBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import jdk.jfr.Configuration;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @run main/othervm -XX:TLABSize=2k jdk.jfr.jvm.TestWaste
 */
public class TestWaste {
    static List<Object> list = new LinkedList<>();
    static Random random = new Random();

    public static void main(String... args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        Configuration c = Configuration.getConfiguration("profile");
        Path file = Path.of("recording.jfr");
        Path scrubbed = Path.of("scrubbed.jfr");
        try (Recording r = new Recording(c)) {
            // Old objects that are cleared out should not create waste
            r.enable("jdk.OldObjectSample")
             .with("cutoff", "infinity")
             .withStackTrace();
            // No stack trace waste from allocation sample
            r.enable("jdk.ObjectAllocationSample")
             .with("throttle", "1000/s")
             .withoutStackTrace();
            // Unused threads should not create unreasonable amount of waste
            r.disable("jdk.ThreadStart");
            r.disable("jdk.ThreadStop");
            // jdk.GCPhaseParallel can often, but not always, take up a very
            // large part of the recording. Disable to make test more stable
            r.disable("jdk.GCPhaseParallel");
            r.start();
            // Generate data
            for (int i = 0; i < 5_000_000; i++) {
                foo(50);
                if (i % 3_000_000 == 0) {
                    System.gc();
                }
                if (i % 10_000 == 0) {
                    Thread t = new Thread();
                    t.start();
                }
            }
            r.stop();
            r.dump(file);
            final Map<String, Long> histogram = new HashMap<>();
            try (RecordingFile rf = new RecordingFile(file)) {
                rf.write(scrubbed, event -> {
                    String key = event.getEventType().getName();
                    histogram.merge(key, 1L, (x, y) -> x + y);
                    return true;
                });
            }
            for (var entry : histogram.entrySet()) {
                System.out.println(entry.getKey() + " " + entry.getValue());
            }
            float fileSize = Files.size(file);
            System.out.printf("File size: %.2f MB\n", fileSize / (1024 * 1024));
            float scrubbedSize = Files.size(scrubbed);
            System.out.printf("Scrubbed size: %.2f MB\n", scrubbedSize / (1024 * 1024));
            float waste = 1 - scrubbedSize / fileSize;
            System.out.printf("Waste: %.2f%%\n", 100 * waste);
            if (waste > 0.10) {
                throw new AssertionError("Found more than 10% waste");
            }
        }
    }

    static void foo(int depth) {
        bar(depth - 1);
    }

    static void bar(int depth) {
        if (depth > 1) {
            if (random.nextBoolean()) {
                foo(depth);
            } else {
                bar(depth - 1);
            }
        } else {
            list.add(new String("hello"));
        }
    }
}
