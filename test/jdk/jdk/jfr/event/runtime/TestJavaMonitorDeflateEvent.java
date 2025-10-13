/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.runtime;

import static jdk.test.lib.Asserts.assertFalse;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.thread.TestThread;
import jdk.test.lib.thread.XRun;

/**
 * @test TestJavaMonitorDeflateEvent
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedAsyncDeflationInterval=100 jdk.jfr.event.runtime.TestJavaMonitorDeflateEvent
 */
public class TestJavaMonitorDeflateEvent {

    private static final String FIELD_KLASS_NAME = "monitorClass.name";
    private static final String FIELD_ADDRESS    = "address";

    private static final String EVENT_NAME = EventNames.JavaMonitorDeflate;

    static class Lock {
    }

    // Make sure the object stays reachable.
    // This guarantees the fields are fully set up on deflation.
    static final Lock lock = new Lock();

    public static void main(String[] args) throws Exception {
        final String lockClassName = lock.getClass().getName().replace('.', '/');

        List<RecordedEvent> events = new CopyOnWriteArrayList<>();
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(EVENT_NAME).withoutThreshold();
            rs.onEvent(EVENT_NAME, e -> {
                Object clazz = e.getValue(FIELD_KLASS_NAME);
                if (clazz.equals(lockClassName)) {
                    events.add(e);
                    rs.close();
                }
            });
            rs.startAsync();

            synchronized (lock) {
                // Causes lock inflation.
                lock.wait(1);
            }

            // Wait for deflater thread to act.
            rs.awaitTermination();

            System.out.println(events);
            assertFalse(events.isEmpty());
            for (RecordedEvent ev : events) {
                Events.assertField(ev, FIELD_ADDRESS).notEqual(0L);
            }
        }
    }
}
