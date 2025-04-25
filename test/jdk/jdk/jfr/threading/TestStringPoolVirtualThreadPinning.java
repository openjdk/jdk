/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8338417
 * @summary Tests pinning of virtual threads when the JFR string pool monitor is contended.
 * @requires vm.flagless
 * @requires vm.hasJFR & vm.continuations
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.threading.TestStringPoolVirtualThreadPinning
 */
public class TestStringPoolVirtualThreadPinning {

    private static final int VIRTUAL_THREAD_COUNT = 100_000;
    private static final int STARTER_THREADS = 10;

    @Name("test.Tester")
    private static class TestEvent extends Event {
        private String eventString = Thread.currentThread().getName();
    }

    /*
     * During event commit, the thread is in a critical section because it has loaded a carrier thread local event writer object.
     * For virtual threads, a contended monitor, such as a synchronized block, is a point where a thread could become unmounted.
     * A monitor guards the JFR string pool, but because of the event writer, remounting a virtual thread onto another carrier is impossible.
     *
     * The test provokes JFR string pool monitor contention to exercise explicit pin constructs to ensure the pinning of virtual threads.
    */
    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.start();

            ThreadFactory factory = Thread.ofVirtual().factory();
            CompletableFuture<?>[] c = new CompletableFuture[STARTER_THREADS];
            for (int j = 0; j < STARTER_THREADS; j++) {
                c[j] = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < VIRTUAL_THREAD_COUNT / STARTER_THREADS; i++) {
                        try {
                            Thread vt = factory.newThread(TestStringPoolVirtualThreadPinning::emitEvent);
                            // For an event field string to be placed in the JFR string pool, it must exceed 16 characters.
                            // We use the virtual thread name as the event field string so we can verify the result as a 1-1 mapping.
                            vt.setName("VirtualTestThread-" + i);
                            vt.start();
                            vt.join();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                });
            }
            for (int j = 0; j < STARTER_THREADS; j++) {
                c[j].get();
            }

            r.stop();
            Path p = Utils.createTempFile("test", ".jfr");
            r.dump(p);
            List<RecordedEvent> events = RecordingFile.readAllEvents(p);
            Asserts.assertEquals(events.size(), VIRTUAL_THREAD_COUNT, "Expected " + VIRTUAL_THREAD_COUNT + " events");
            for (RecordedEvent e : events) {
                RecordedThread t = e.getThread();
                Asserts.assertNotNull(t);
                Asserts.assertTrue(t.isVirtual());
                Asserts.assertEquals(e.getString("eventString"), t.getJavaName());
            }
        }
    }

    private static void emitEvent() {
        TestEvent t = new TestEvent();
        t.commit();
    }
}
