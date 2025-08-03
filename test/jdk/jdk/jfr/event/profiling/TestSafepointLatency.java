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

package jdk.jfr.event.profiling;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.Random;

import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.EventNames;

/*
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main jdk.jfr.event.profiling.TestSafepointLatency
 */
public class TestSafepointLatency {
    // The SafepointLatency event is parasitic on the ExecutionSample mechanism.
    final static String EXECUTION_SAMPLE_EVENT = EventNames.ExecutionSample;
    final static String SAFEPOINT_LATENCY_EVENT = EventNames.SafepointLatency;
    final static CountDownLatch latch = new CountDownLatch(10);
    final static Random random = new Random();
    public static int publicizedValue = 4711;

    public static void main(String[] args) throws Exception {
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(EXECUTION_SAMPLE_EVENT).withPeriod(Duration.ofMillis(1));
            rs.enable(SAFEPOINT_LATENCY_EVENT);
            rs.onEvent(SAFEPOINT_LATENCY_EVENT, e -> latch.countDown());
            rs.startAsync();
            Thread t = new Thread(TestSafepointLatency::callMethods);
            t.setDaemon(true);
            t.start();
            latch.await();
        }
    }

    public static void callMethods() {
        while (latch.getCount() > 0) {
            publicizedValue += bar(publicizedValue);
        }
    }

    private static int bar(int value) {
        return baz(value);
    }

    private static int baz(int value) {
        return qux(value);
    }

    private static int qux(int value) {
        return (value << 4) * random.nextInt();
    }
}
