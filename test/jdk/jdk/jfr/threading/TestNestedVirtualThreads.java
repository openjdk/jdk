/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.threading;

import java.util.List;
import java.util.concurrent.ThreadFactory;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests committing an event in a virtual thread created by a virtual
 *          thread
 * @key jfr
 * @requires vm.hasJFR & vm.continuations
 * @library /test/lib /test/jdk
 * @modules jdk.jfr/jdk.jfr.internal
 * @compile --enable-preview -source ${jdk.version} TestNestedVirtualThreads.java
 * @run main/othervm --enable-preview jdk.jfr.threading.TestNestedVirtualThreads
 */
public class TestNestedVirtualThreads {
    @Name("test.Nested")
    private static class NestedEvent extends Event {
    }

    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.start();
            ThreadFactory factory1 = Thread.ofVirtual().factory();
            Thread vt1 = factory1.newThread(() -> {
                ThreadFactory factory2 = Thread.ofVirtual().factory();
                Thread vt2 = factory2.newThread(() -> {
                    NestedEvent event = new NestedEvent();
                    event.commit();
                });
                vt2.start();
                try {
                    vt2.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            vt1.start();
            vt1.join();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            Events.hasEvents(events);
            System.out.println(events.get(0));
            RecordedEvent e = events.get(0);
            RecordedThread t = e.getThread();
            Asserts.assertTrue(t.isVirtual());
            Asserts.assertEquals(t.getJavaName(), ""); // vthreads default name is the empty string.
            Asserts.assertEquals(t.getOSName(), "");
            Asserts.assertEquals(t.getThreadGroup().getName(), "VirtualThreads");
            Asserts.assertGreaterThan(t.getJavaThreadId(), 0L);
            Asserts.assertEquals(t.getOSThreadId(), 0L);
        }
    }
}
