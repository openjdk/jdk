/*
 * Copyright (c) 2025 SAP SE. All rights reserved.
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

import java.time.Duration;

import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.RecurseThread;

/*
 * @test
 * @requires vm.hasJFR & os.family == "linux"
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main/timeout=30 jdk.jfr.event.profiling.TestCPUTimeAndExecutionSample
 */
public class TestCPUTimeAndExecutionSample {

    // The period is set to 1100 ms to provoke the 1000 ms
    // threshold in the JVM for os::naked_short_sleep().
    public static void main(String[] args) throws Exception {
        run(EventNames.CPUTimeSample, "throttle", "1000/s");
        run(EventNames.ExecutionSample, "period", "1100ms");
        run(EventNames.CPUTimeSample, "throttle", "1100ms");
        run(EventNames.ExecutionSample, "period", "1000ms");
    }

    private static void run(String eventType, String attribute, String value) {
        RecurseThread t = new RecurseThread(50);
        t.setDaemon(true);
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(eventType).with(attribute, value);
            rs.onEvent(eventType, e -> {
                t.quit();
                rs.close();
            });
            t.start();
            rs.start();
        }
    }
}
