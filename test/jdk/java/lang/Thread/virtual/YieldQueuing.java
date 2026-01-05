/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Thread.yield submits the virtual thread task to the expected queue
 * @requires vm.continuations
 * @run junit/othervm -Djdk.virtualThreadScheduler.maxPoolSize=1 YieldQueuing
 */

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class YieldQueuing {

    @BeforeAll
    static void setup() throws Exception {
        // waiting for LockSupport to be initialized can change the scheduling
        MethodHandles.lookup().ensureInitialized(LockSupport.class);
    }

    /**
     * Test Thread.yield submits the task for the current virtual thread to a scheduler
     * submission queue when there are no tasks in the local queue.
     */
    @Test
    void testYieldWithEmptyLocalQueue() throws Exception {
        var list = new CopyOnWriteArrayList<String>();

        var threadsStarted = new AtomicBoolean();

        var threadA = Thread.ofVirtual().unstarted(() -> {
            // pin thread until task for B is in submission queue
            while (!threadsStarted.get()) {
                Thread.onSpinWait();
            }

            list.add("A");
            Thread.yield();      // push task for A to submission queue, B should run
            list.add("A");
        });

        var threadB = Thread.ofVirtual().unstarted(() -> {
            list.add("B");
        });

        // push tasks for A and B to submission queue
        threadA.start();
        threadB.start();

        // release A
        threadsStarted.set(true);

        // wait for result
        threadA.join();
        threadB.join();
        assertEquals(list, List.of("A", "B", "A"));
    }

    /**
     * Test Thread.yield submits the task for the current virtual thread to the local
     * queue when there are tasks in the local queue.
     */
    @Test
    void testYieldWithNonEmptyLocalQueue() throws Exception {
        var list = new CopyOnWriteArrayList<String>();

        var threadsStarted = new AtomicBoolean();

        var threadA = Thread.ofVirtual().unstarted(() -> {
            // pin thread until tasks for B and C are in submission queue
            while (!threadsStarted.get()) {
                Thread.onSpinWait();
            }

            list.add("A");
            LockSupport.park();   // B should run
            list.add("A");
        });

        var threadB = Thread.ofVirtual().unstarted(() -> {
            list.add("B");
            LockSupport.unpark(threadA);  // push task for A to local queue
            Thread.yield();               // push task for B to local queue, A should run
            list.add("B");
        });

        var threadC = Thread.ofVirtual().unstarted(() -> {
            list.add("C");
        });

        // push tasks for A, B and C to submission queue
        threadA.start();
        threadB.start();
        threadC.start();

        // release A
        threadsStarted.set(true);

        // wait for result
        threadA.join();
        threadB.join();
        threadC.join();
        assertEquals(list, List.of("A", "B", "A", "B", "C"));
    }
}
