/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR & vm.continuations
 * @library /test/lib
 * @compile TestVirtualThreadStartEvent.java LatchedThread.java
 * @run main/othervm jdk.jfr.event.runtime.TestVirtualThreadStartEvent
 */
public class TestVirtualThreadStartEvent {
    private final static String EVENT_NAME_THREAD_START = EventNames.VirtualThreadStart;

    public static void main(String[] args) throws Throwable {
        try (Recording recording = new Recording()) {
            recording.enable(EVENT_NAME_THREAD_START);

            // Start a thread before recording
            LatchedThread beforeThread = new LatchedThread("Before Thread", true);
            beforeThread.start();
            beforeThread.awaitStarted();
            recording.start();

            // Start and end a thread during recording
            LatchedThread duringThread = new LatchedThread("During Thread", true);
            duringThread.start();
            duringThread.stopAndJoin();

            // Start a thread and end it after the recording has stopped
            LatchedThread afterThread = new LatchedThread("After Thread", true);
            afterThread.start();
            afterThread.awaitStarted();

            recording.stop();
            afterThread.stopAndJoin();

            List<RecordedEvent> events = Events.fromRecording(recording);
            for (RecordedEvent e : events) {
                System.out.println(e);
            }
            assertEvent(events, duringThread);
            assertEvent(events, afterThread);
            Asserts.assertNull(findEventByThreadName(events, beforeThread.getName()));
        }
    }

    private static void assertEvent(List<RecordedEvent> events, LatchedThread thread) throws Exception {
        RecordedEvent event = findEventByThreadName(events, thread.getName());
        System.out.println(event);
        RecordedThread t = event.getThread();
        Asserts.assertEquals(event.getLong("javaThreadId"), thread.getId());
        Asserts.assertTrue(t.isVirtual());
        Asserts.assertEquals(t.getThreadGroup().getName(), "VirtualThreads");
    }

    private static RecordedEvent findEventByThreadName(List<RecordedEvent> events, String name) {
        for (RecordedEvent e : events) {
            if (e.getThread().getJavaName().equals(name)) {
                return e;
            }
        }
        return null;
    }
}
