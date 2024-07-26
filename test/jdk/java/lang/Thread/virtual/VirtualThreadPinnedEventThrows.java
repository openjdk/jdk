/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test parking when pinned and emitting the JFR VirtualThreadPinnedEvent throws
 * @modules java.base/java.lang:+open java.base/jdk.internal.event
 * @library /test/lib
 * @compile/module=java.base jdk/internal/event/VirtualThreadPinnedEvent.java
 * @run junit/othervm --enable-native-access=ALL-UNNAMED VirtualThreadPinnedEventThrows
 */

import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import jdk.internal.event.VirtualThreadPinnedEvent;

import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.thread.VThreadPinner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class VirtualThreadPinnedEventThrows {

    @BeforeAll
    static void setup() {
        // need >=2 carriers for testing pinning when main thread is a virtual thread
        if (Thread.currentThread().isVirtual()) {
            VThreadRunner.ensureParallelism(2);
        }
    }

    /**
     * Test parking when pinned and creating the VirtualThreadPinnedEvent fails with OOME.
     */
    @Test
    void testVirtualThreadPinnedEventCreateThrows() throws Exception {
        VirtualThreadPinnedEvent.setCreateThrows(true);
        try {
            testParkWhenPinned();
        } finally {
            VirtualThreadPinnedEvent.setCreateThrows(false);
        }
    }

    /**
     * Test parking when pinned and VirtualThreadPinnedEvent.begin fails with OOME.
     */
    @Test
    void testVirtualThreadPinnedEventBeginThrows() throws Exception {
        VirtualThreadPinnedEvent.setBeginThrows(true);
        try {
            testParkWhenPinned();
        } finally {
            VirtualThreadPinnedEvent.setBeginThrows(false);
        }
    }

    /**
     * Test parking when pinned and VirtualThreadPinnedEvent.commit fails with OOME.
     */
    @Test
    void testVirtualThreadPinnedEventCommitThrows() throws Exception {
        VirtualThreadPinnedEvent.setCommitThrows(true);
        try {
            testParkWhenPinned();
        } finally {
            VirtualThreadPinnedEvent.setCommitThrows(false);
        }
    }

    /**
     * Test parking a virtual thread when pinned.
     */
    private void testParkWhenPinned() throws Exception {
        var exception = new AtomicReference<Throwable>();
        var done = new AtomicBoolean();
        Thread thread = Thread.startVirtualThread(() -> {
            try {
                VThreadPinner.runPinned(() -> {
                    while (!done.get()) {
                        LockSupport.park();
                    }
                });
            } catch (Throwable e) {
                exception.set(e);
            }
        });
        try {
            // wait for thread to park
            Thread.State state;
            while ((state = thread.getState()) != Thread.State.WAITING) {
                assertTrue(state != Thread.State.TERMINATED);
                Thread.sleep(10);
            }
        } finally {
            done.set(true);
            LockSupport.unpark(thread);
            thread.join();
        }
        assertNull(exception.get());
    }
}
