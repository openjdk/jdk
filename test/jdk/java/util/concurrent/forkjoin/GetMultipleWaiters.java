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

/*
 * @test
 * @bug 8390870
 * @summary ForkJoinTask.get must honor its timeout and interrupts even
 *          when another thread is waiting on the same task.
 * @run junit/othervm/timeout=20 GetMultipleWaiters
 */

import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GetMultipleWaiters {

    /**
     * get() must be interruptible while another thread is waiting on the
     * same task.
     */
    @Test
    void testGet() throws Exception {
        var task = ForkJoinTask.adapt(() -> {});
        var thrown = new Throwable[1];

        var a = startThreadAndAwaitState(() -> {
            try {
                task.get();
            } catch (Throwable t) {
                thrown[0] = t;
            }
        }, "Get-waiter-A", Thread.State.WAITING);
        var b = startThreadAndAwaitState(() -> {
            try {
                task.get();
            } catch (Throwable ignore) {
            }
        }, "Get-waiter-B", Thread.State.WAITING);

        try {
            a.interrupt();
            a.join();

            assertInstanceOf(InterruptedException.class, thrown[0]);
        } finally {
            task.complete(null);
            b.join();
        }
    }

    /**
     * get(long, TimeUnit) must time out while another thread is waiting
     * on the same task.
     */
    @Test
    void testTimedGet() throws Exception {
        var task = ForkJoinTask.adapt(() -> {});
        var thrown = new Throwable[1];

        var a = startThreadAndAwaitState(() -> {
            try {
                task.get(1, TimeUnit.SECONDS);
            } catch (Throwable t) {
                thrown[0] = t;
            }
        }, "TimedGet-waiter-A", Thread.State.TIMED_WAITING);
        var b = startThreadAndAwaitState(() -> {
            try {
                task.get();
            } catch (Throwable ignore) {
            }
        }, "TimedGet-waiter-B", Thread.State.WAITING);

        try {
            a.join();

            assertInstanceOf(TimeoutException.class, thrown[0]);
        } finally {
            task.complete(null);
            b.join();
        }
    }

    static Thread startThreadAndAwaitState(Runnable r, String name, Thread.State state) throws Exception {
        var t = new Thread(r, name);
        t.start();
        while (t.getState() != state)
            Thread.sleep(1);
        return t;
    }
}
