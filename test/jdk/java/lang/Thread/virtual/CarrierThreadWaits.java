/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284161 8286788
 * @summary Test that a carrier thread waits on a virtual thread
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @run junit CarrierThreadWaits
 */

/**
 * @test
 * @requires vm.continuations & vm.debug
 * @modules java.base/java.lang:+open
 * @run junit/othervm -XX:LockingMode=0 CarrierThreadWaits
 */

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarrierThreadWaits {

    @Test
    void testCarrierThreadWaiting() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            var carrierRef = new AtomicReference<Thread>();
            var vthreadRef = new AtomicReference<Thread>();

            Executor scheduler = task -> {
                pool.submit(() -> {
                    Thread carrier = Thread.currentThread();
                    carrierRef.set(carrier);
                    Thread vthread = vthreadRef.get();

                    System.err.format("%s run task (%s) ...%n", carrier, vthread);
                    task.run();
                    System.err.format("%s task done (%s)%n", carrier, vthread);
                });
            };

            // start a virtual thread that spins and remains mounted until "done"
            var started = new AtomicBoolean();
            var done = new AtomicBoolean();
            Thread.Builder builder = ThreadBuilders.virtualThreadBuilder(scheduler);
            Thread vthread = builder.unstarted(() -> {
                started.set(true);
                while (!done.get()) {
                    Thread.onSpinWait();
                }
            });
            vthreadRef.set(vthread);
            vthread.start();

            try {
                // wait for virtual thread to start
                while (!started.get()) {
                    Thread.sleep(10);
                }

                Thread carrier = carrierRef.get();

                long carrierId = carrier.threadId();
                long vthreadId = vthread.threadId();

                // carrier thread should be on WAITING on virtual thread
                ThreadInfo ti = ManagementFactory.getThreadMXBean().getThreadInfo(carrierId);
                Thread.State state = ti.getThreadState();
                LockInfo lockInfo = ti.getLockInfo();
                assertEquals(Thread.State.WAITING, state);
                assertNotNull(lockInfo);
                assertEquals(vthread.getClass().getName(), lockInfo.getClassName());
                assertEquals(System.identityHashCode(vthread), lockInfo.getIdentityHashCode());
                assertEquals(vthreadId, ti.getLockOwnerId());
            } finally {
                done.set(true);
            }
        }
    }

}
