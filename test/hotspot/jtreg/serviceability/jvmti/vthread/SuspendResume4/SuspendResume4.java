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

/**
 * @test
 * @bug 8376621
 * @summary Suspend virtual thread while it's inside disableSuspendAndPreempt region
 * @requires vm.continuations
 * @requires vm.jvmti
 * @library /test/lib /test/hotspot/jtreg/testlibrary
 * @run main/othervm SuspendResume4
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;

import jvmti.JVMTIUtils;

public class SuspendResume4 {
    native static void suspendThread(Thread thread);
    native static void resumeThread(Thread thread);

    public static void main(String[] args) throws Exception {
        // Run test in child VM where Locale won't be initialized already by jtreg
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
            "-agentpath:" + Utils.TEST_NATIVE_PATH + File.separator + System.mapLibraryName("SuspendResume4"),
            "SuspendResume4$Test");
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        System.out.println(output.getStdout());
        output.shouldHaveExitValue(0);
    }

    static class Test{
        static String targetName;

        private void runTest() throws Exception {
            // start target vthread
            Thread target = Thread.ofVirtual().name("target").start(() -> {
                // Give time for reader to get suspended in
                // disableSuspendAndPreempt region.
                spinWaitMillis(100);
                // Force unmounting. If reader was suspended inside
                // disableSuspendAndPreempt region this will block
                // in VirtualThread.unmount.
                Thread.yield();
            });

            // start clinit contender
            Thread contender = Thread.ofPlatform().name("contender").start(() -> {
                "JAVA".toLowerCase(java.util.Locale.ROOT);
            });

            // start vthread that reads target's state
            Thread reader = Thread.ofVirtual().name("reader").start(() -> {
                targetName = "name: " + target;
            });

            // start suspend/resumer
            Thread suspender = Thread.ofPlatform().name("suspender").start(() -> {
                SuspendResume4.suspendThread(reader);
                // Give target time for Thread.yield
                spinWaitMillis(100);
                SuspendResume4.resumeThread(reader);
            });

            target.join();
            contender.join();
            suspender.join();
            reader.join();
        }

        public static void main(String[] args) throws Exception {
            Test obj = new Test();
            obj.runTest();
        }

        static void spinWaitMillis(long millis) {
            long durationNanos = millis * 1_000_000L;
            long start = System.nanoTime();
            while (System.nanoTime() - start < durationNanos) {
                Thread.onSpinWait();
            }
        }
    }
}
