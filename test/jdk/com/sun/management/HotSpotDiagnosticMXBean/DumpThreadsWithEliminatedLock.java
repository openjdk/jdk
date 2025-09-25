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

/*
 * @test
 * @bug 8356870
 * @summary Test HotSpotDiagnosticMXBean.dumpThreads with a thread owning a monitor for
 *     an object that is scalar replaced
 * @requires vm.compMode != "Xcomp"
 * @requires (vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel == 4)
 * @modules jdk.management
 * @library /test/lib
 * @run main/othervm -XX:CompileCommand=inline,java/lang/String*.* DumpThreadsWithEliminatedLock plain platform
 * @run main/othervm -XX:CompileCommand=inline,java/lang/String*.* DumpThreadsWithEliminatedLock plain virtual
 * @run main/othervm -XX:CompileCommand=inline,java/lang/String*.* DumpThreadsWithEliminatedLock json platform
 * @run main/othervm -XX:CompileCommand=inline,java/lang/String*.* DumpThreadsWithEliminatedLock json virtual
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import com.sun.management.HotSpotDiagnosticMXBean;
import jdk.test.lib.threaddump.ThreadDump;
import jdk.test.lib.thread.VThreadRunner;

public class DumpThreadsWithEliminatedLock {

    public static void main(String[] args) throws Exception {
        boolean plain = switch (args[0]) {
            case "plain" -> true;
            case "json"  -> false;
            default      -> throw new RuntimeException("Unknown dump format");
        };

        ThreadFactory factory = switch (args[1]) {
            case "platform" -> Thread.ofPlatform().factory();
            case "virtual"  -> Thread.ofVirtual().factory();
            default         -> throw new RuntimeException("Unknown thread kind");
        };

        // need at least two carriers for JTREG_TEST_THREAD_FACTORY=Virtual
        if (Thread.currentThread().isVirtual()) {
            VThreadRunner.ensureParallelism(2);
        }

        // A thread that spins creating and adding to a StringBuffer. StringBuffer is
        // synchronized, assume object will be scalar replaced and the lock eliminated.
        var done = new AtomicBoolean();
        var ref = new AtomicReference<String>();
        Thread thread = factory.newThread(() -> {
            while (!done.get()) {
                StringBuffer sb = new StringBuffer();
                sb.append(System.currentTimeMillis());
                String s = sb.toString();
                ref.set(s);
            }
        });
        try {
            thread.start();
            if (plain) {
                testPlainFormat();
            } else {
                testJsonFormat(thread.threadId());
            }
        } finally {
            done.set(true);
            thread.join();
        }
    }

    /**
     * Invoke HotSpotDiagnosticMXBean.dumpThreads to generate a thread dump in plain text
     * format until "lock is eliminated" is found in the output.
     */
    private static void testPlainFormat() {
        try {
            Path file = genOutputPath(".txt");
            boolean found = false;
            int attempts = 0;
            while (!found) {
                attempts++;
                Files.deleteIfExists(file);
                ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                        .dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN);
                try (Stream<String> stream = Files.lines(file)) {
                    found = stream.map(String::trim)
                            .anyMatch(l -> l.contains("- lock is eliminated"));
                }
                System.out.format("%s Attempt %d, found: %b%n", Instant.now(), attempts, found);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Invoke HotSpotDiagnosticMXBean.dumpThreads to generate a thread dump in JSON format
     * until the monitorsOwned.locks array for the given thread has a null lock.
     */
    private static void testJsonFormat(long tid) {
        try {
            Path file = genOutputPath(".json");
            boolean found = false;
            int attempts = 0;
            while (!found) {
                attempts++;
                Files.deleteIfExists(file);
                ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                        .dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.JSON);

                // parse thread dump as JSON and find thread
                String jsonText = Files.readString(file);
                ThreadDump threadDump = ThreadDump.parse(jsonText);
                ThreadDump.ThreadInfo ti = threadDump.rootThreadContainer()
                        .findThread(tid)
                        .orElse(null);
                if (ti == null) {
                    throw new RuntimeException("Thread " + tid + " not found in thread dump");
                }

                // look for null element in ownedMonitors/locks array
                found = ti.ownedMonitors()
                        .values()
                        .stream()
                        .flatMap(List::stream)
                        .anyMatch(o -> o == null);
                System.out.format("%s Attempt %d, found: %b%n", Instant.now(), attempts, found);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Generate a file path with the given suffix to use as an output file.
     */
    private static Path genOutputPath(String suffix) throws IOException {
        Path dir = Path.of(".").toAbsolutePath();
        Path file = Files.createTempFile(dir, "dump", suffix);
        Files.delete(file);
        return file;
    }
}
