/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, NTT DATA.
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

package jdk.jfr.event.oldobject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingFile;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
* @test
* @bug 8364090
* @summary Tests Dump reason and OldObjectSample events at OOME.
* @requires vm.flagless
* @requires vm.hasJFR
* @library /test/lib
* @run main/othervm jdk.jfr.event.oldobject.TestEmergencyDumpAtOOM
*/
public class TestEmergencyDumpAtOOM {

    public static List<String> DEFAULT_LEAKER_ARGS = List.of(
        "-Xmx64m",
        "-XX:TLABSize=2k",
        "-XX:StartFlightRecording:dumponexit=true,filename=oom.jfr",
        Leaker.class.getName()
    );

    public static class Leaker {
        public static void main(String... args) {
            List<byte[]> list = new ArrayList<>();
            while (true) {
                list.add(new byte[1024]);
            }
        }
    }

    private static void test(boolean shouldCrash) throws Exception {
        List<String> args = new ArrayList<>(DEFAULT_LEAKER_ARGS);
        if (shouldCrash) {
            args.add(0, "-XX:+CrashOnOutOfMemoryError");
        }

        while (true) {
            Process p = ProcessTools.createTestJavaProcessBuilder(args).start();
            p.waitFor();
            OutputAnalyzer output = new OutputAnalyzer(p);
            if (!output.contains("java.lang.OutOfMemoryError")) {
                throw new RuntimeException("OutOfMemoryError did not happen.");
            }

            // Check recording file
            String jfrFileName = shouldCrash ? String.format("hs_err_pid%d.jfr", p.pid()) : "oom.jfr";
            Path jfrPath = Path.of(jfrFileName);
            Asserts.assertTrue(Files.exists(jfrPath), "No jfr recording file " + jfrFileName + " exists");

            // Check events
            AtomicLong oldObjects = new AtomicLong();
            AtomicReference<String> shutdownReason = new AtomicReference<>();
            AtomicReference<String> dumpReason = new AtomicReference<>();
            try (EventStream stream = EventStream.openFile(jfrPath)) {
                stream.onEvent("jdk.OldObjectSample", e -> oldObjects.incrementAndGet());
                stream.onEvent("jdk.Shutdown", e -> shutdownReason.set(e.getString("reason")));
                stream.onEvent("jdk.DumpReason", e -> dumpReason.set(e.getString("reason")));
                stream.start();
            }

            // Check OldObjectSample events
            if (oldObjects.get() > 0L) {
                if (shouldCrash) {
                    Asserts.assertEquals("VM Error", shutdownReason.get());
                    Asserts.assertEquals("Out of Memory", dumpReason.get());
                } else {
                    Asserts.assertEquals("No remaining non-daemon Java threads", shutdownReason.get());
                }
                // Passed this test
                return;
            }

            System.out.println("Could not find OldObjectSample events. Retrying.");
        }
    }

    public static void main(String... args) throws Exception {
        test(true);
        test(false);
    }
}
