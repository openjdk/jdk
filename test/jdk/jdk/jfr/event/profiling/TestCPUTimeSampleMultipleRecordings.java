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

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.internal.JVM;
import jdk.test.lib.jfr.EventNames;

/*
 * Tests that creating multiple recordings after another is possible.
 * @test
 * @requires vm.hasJFR & os.family == "linux"
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main jdk.jfr.event.profiling.TestCPUTimeSampleMultipleRecordings
 */
public class TestCPUTimeSampleMultipleRecordings {

    static String nativeEvent = EventNames.CPUTimeSample;

    static volatile boolean alive = true;

    public static void main(String[] args) throws Exception {
        Thread t = new Thread(TestCPUTimeSampleMultipleRecordings::nativeMethod);
        t.setDaemon(true);
        t.start();
        for (int i = 0; i < 2; i++) {
            try (RecordingStream rs = new RecordingStream()) {
                rs.enable(nativeEvent).with("throttle", "1ms");
                rs.onEvent(nativeEvent, e -> {
                    alive = false;
                    rs.close();
                });

                rs.start();
            }
        }
        alive = false;
    }

    public static void nativeMethod() {
        while (alive) {
            JVM.getPid();
        }
    }
}
