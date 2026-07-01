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
 * @bug 8370344
 * @library /test/lib
 * @run junit/native TestSharedCloseJvmti
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TestSharedCloseJvmti {

    private static final String JVMTI_AGENT_LIB = Path.of(Utils.TEST_NATIVE_PATH, System.mapLibraryName("SharedCloseAgent"))
            .toAbsolutePath().toString();

    @Test
    void eventDuringScopedAccess() throws Throwable {
        List<String> command = new ArrayList<>(List.of(
                "-agentpath:" + JVMTI_AGENT_LIB,
                "-Xcheck:jni",
                EventDuringScopedAccessRunner.class.getName()
        ));

        try {
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);
            Process process = ProcessTools.startProcess("fork", pb, null, null, 1L, TimeUnit.MINUTES);
            OutputAnalyzer output = new OutputAnalyzer(process);
            output.shouldHaveExitValue(0);
            output.stderrShouldContain("Exception in thread \"Trigger\" jdk.internal.misc.ScopedMemoryAccess$ScopedAccessError: Invalid memory access");
        } catch (TimeoutException e) {
            throw new RuntimeException("Timeout while waiting for forked process");
        }
    }

    public static class EventDuringScopedAccessRunner {
        static final int ADDED_FRAMES = 10;

        static final CountDownLatch MAIN_LATCH = new CountDownLatch(1);
        static final CountDownLatch TARGET_LATCH = new CountDownLatch(1);

        static volatile int SINK;

        public static void main(String[] args) throws Throwable {
            try (Arena arena = Arena.ofShared()) {
                MemorySegment segment = arena.allocate(4);
                // run in separate thread so that waiting on
                // latch doesn't block main thread
                Thread.ofPlatform().name("Trigger").start(() -> {
                    SINK = segment.get(ValueLayout.JAVA_INT, 0); // should throw
                    System.err.println("No exception thrown during outer memory access");
                    System.exit(1);
                });
                // wait until trigger thread is in JVMTI event callback
                MAIN_LATCH.await();
            }
            // Notify trigger thread that arena was closed
            TARGET_LATCH.countDown();
        }

        static boolean reentrant = false;

        // called by jvmti agent
        // we get here after checking arena liveness
        private static void target() {
            String callerName = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames ->
                    frames.skip(2).findFirst().orElseThrow().getClassName());
            if (!callerName.equals("jdk.internal.misc.ScopedMemoryAccess")) {
                return;
            }

            if (reentrant) {
                // put some frames on the stack, so stack walk does not see @Scoped method
                addFrames(0);
            } else {
                reentrant = true;
                try (Arena arena = Arena.ofConfined()) {
                    SINK = arena.allocate(4).get(ValueLayout.JAVA_INT, 0); // should throw
                    System.err.println("No exception thrown during reentrant memory access");
                    System.exit(1);
                }
                reentrant = false;
            }
        }

        private static void addFrames(int depth) {
            if (depth >= ADDED_FRAMES) {
                // notify main thread to close the arena
                MAIN_LATCH.countDown();
                try {
                    // wait here until main thread has closed arena
                    TARGET_LATCH.await();
                } catch (InterruptedException ex) {
                    throw new RuntimeException("Unexpected interruption");
                }
                return;
            }
            addFrames(depth + 1);
        }
    }
}
