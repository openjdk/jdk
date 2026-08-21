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
 */

import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Duration;

public class GetMultipleWaiters {

    public static void main(String[] args) throws Exception {
        // get() with timeout must time out
        test(false);
        // get() must be interruptible
        test(true);
    }

    static void test(boolean interrupt) throws Exception {
        ForkJoinTask<?> task = ForkJoinTask.adapt(() -> {});
        Throwable[] thrown = {null};

        Thread a = new Thread(() -> {
            try {
                if (interrupt)
                    task.get();
                else
                    task.get(1, TimeUnit.SECONDS);
            } catch (Throwable t) {
                thrown[0] = t;
            }
        }, "waiter-A");
        Thread b = new Thread(() -> {
            try {
                task.get();
            } catch (Throwable ignore) {}
        }, "waiter-B");
        // Do not rely on a timeout to terminate the test if it fails
        a.setDaemon(true);
        b.setDaemon(true);

        a.start();
        while (a.getState() != (interrupt ? Thread.State.WAITING : Thread.State.TIMED_WAITING))
            Thread.sleep(10);
        b.start();
        while (b.getState() != Thread.State.WAITING)
            Thread.sleep(10);
        if (interrupt)
            a.interrupt();

        if (!a.join(Duration.ofSeconds(10))) {
            System.out.println("waiter-A state=" + a.getState());
            for (StackTraceElement e : a.getStackTrace())
                System.out.println("    at " + e);
            throw new RuntimeException("get() did not return (interrupt=" + interrupt + ")");
        }
        Class<?> expected = interrupt ? InterruptedException.class : TimeoutException.class;
        if (!expected.isInstance(thrown[0]))
            throw new RuntimeException("expected " + expected.getSimpleName() + ", got " + thrown[0]);
    }
}
