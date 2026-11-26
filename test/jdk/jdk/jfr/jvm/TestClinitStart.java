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

package jdk.jfr.jvm;

import java.util.concurrent.CountDownLatch;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test TestClinitStart
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.jvm.TestClinitStart
 */
public class TestClinitStart {
    private static final String EVENT_NAME = "ClinitStart";
    private static final CountDownLatch registration = new CountDownLatch(1);
    private static final CountDownLatch recordingStart = new CountDownLatch(1);

    @Name(EVENT_NAME)
    private static class ClinitEvent extends Event {
        static {
            // FlightRecorder.register(ClinitEvent.class) instrumentation
            // is added here by the JVM
            registration.countDown();
            try {
                recordingStart.await();
            } catch (InterruptedException e) {
                throw new InternalError(e);
            }
        }
    }

    public static void main(String... args) throws Exception {
        Thread.ofPlatform().start(() -> new ClinitEvent().commit());
        registration.await();
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(EVENT_NAME);
            rs.onEvent(EVENT_NAME, e -> rs.close());
            rs.startAsync();
            recordingStart.countDown();
            rs.awaitTermination();
        }
    }
}
