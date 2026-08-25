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
 * @run junit GetMultipleWaiters
 */

import java.time.Duration;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

class GetMultipleWaiters {

    /**
     * get() must be interruptible while another thread is waiting on the
     * same task.
     */
    @Test
    void testGet() throws Exception {
        var task = ForkJoinTask.adapt(() -> {});
        Throwable[] thrown = {null};

        var a = new Thread(() -> {
            try {
                task.get();
            } catch (Throwable t) {
                thrown[0] = t;
            }
        }, "waiter-A");
        var b = new Thread(() -> {
            try {
                task.get();
            } catch (Throwable ignore) {
            }
        }, "waiter-B");

        try {
            a.start();
            while (a.getState() != Thread.State.WAITING)
                Thread.sleep(1);
            b.start();
            while (b.getState() != Thread.State.WAITING)
                Thread.sleep(1);
            a.interrupt();

            assertJoins(a);
            assertInstanceOf(InterruptedException.class, thrown[0]);
        } finally {
            task.cancel(false);
            a.join();
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
        Throwable[] thrown = {null};

        var a = new Thread(() -> {
            try {
                task.get(1, TimeUnit.SECONDS);
            } catch (Throwable t) {
                thrown[0] = t;
            }
        }, "waiter-A");
        var b = new Thread(() -> {
            try {
                task.get();
            } catch (Throwable ignore) {
            }
        }, "waiter-B");

        try {
            a.start();
            while (a.getState() != Thread.State.TIMED_WAITING)
                Thread.sleep(1);
            b.start();
            while (b.getState() != Thread.State.WAITING)
                Thread.sleep(1);

            assertJoins(a);
            assertInstanceOf(TimeoutException.class, thrown[0]);
        } finally {
            task.cancel(false);
            a.join();
            b.join();
        }
    }

    /**
     * Fails with the thread's state and stack trace if it does not
     * terminate within 10 seconds.
     */
    private void assertJoins(Thread thread) throws InterruptedException {
        if (!thread.join(Duration.ofSeconds(10))) {
            var sb = new StringBuilder();
            sb.append(thread.getName())
              .append(" did not terminate, state=")
              .append(thread.getState());
            for (var e : thread.getStackTrace())
                sb.append(System.lineSeparator()).append("    at ").append(e);
            fail(sb.toString());
        }
    }
}
