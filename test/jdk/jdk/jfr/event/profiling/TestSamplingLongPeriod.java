/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main jdk.jfr.event.profiling.TestSamplingLongPeriod
 */
public class TestSamplingLongPeriod {

    final static String SAMPLE_EVENT = EventNames.ExecutionSample;

    // The period is set to 1100 ms to provoke the 1000 ms
    // threshold in the JVM for os::naked_short_sleep().
    public static void main(String[] args) throws Exception {
        RecurseThread t = new RecurseThread(50);
        t.setDaemon(true);
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(SAMPLE_EVENT).withPeriod(Duration.ofMillis(1100));
            rs.onEvent(SAMPLE_EVENT, e -> {
                t.quit();
                rs.close();
            });
            t.start();
            rs.start();
        }
    }
}
