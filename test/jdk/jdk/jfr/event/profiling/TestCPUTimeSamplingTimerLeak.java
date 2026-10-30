/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package jdk.jfr.event.profiling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jdk.jfr.Recording;
import jdk.test.lib.Asserts;
import jtreg.SkippedException;

import static jdk.test.lib.jfr.EventNames.CPUTimeSample;

/*
 * @test
 * @bug 8396148
 * @summary Verify that CPU-time sampler does not leak timers
 * @requires vm.hasJFR & vm.flagless & os.family == "linux"
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.profiling.TestCPUTimeSamplingTimerLeak
 */
public class TestCPUTimeSamplingTimerLeak {

    private static final Path PROC_TIMERS = Path.of("/proc/self/timers");
    private static final int IDLE_THREADS = 10;

    public static void main(String[] args) throws Exception {
        if (!Files.exists(PROC_TIMERS)) {
            throw new SkippedException(PROC_TIMERS + " is not available on this system");
        }

        var pool = new ThreadPoolExecutor(IDLE_THREADS, IDLE_THREADS,
                10, TimeUnit.SECONDS, new SynchronousQueue<>());

        try (Recording r = new Recording()) {
            r.start();

            // First iteration is to initialize CPU-time sampling machinery
            r.enable(CPUTimeSample).with("throttle", "1ms");
            r.disable(CPUTimeSample);

            // Start several idle threads while CPU-time profiler is disenrolled
            pool.prestartAllCoreThreads();

            r.enable(CPUTimeSample).with("throttle", "1ms");
            r.disable(CPUTimeSample);

            // No stray timers should remain after the second iteration ends
            String timers = Files.readString(PROC_TIMERS);
            Asserts.assertEquals("", timers, "Unexpected leftover timers");

            r.stop();
        } finally {
            pool.shutdown();
        }
    }
}
