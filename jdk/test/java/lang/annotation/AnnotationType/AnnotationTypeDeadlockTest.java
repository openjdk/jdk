/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7122142
 * @summary Test deadlock situation when recursive annotations are parsed
 */

import java.lang.annotation.Retention;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class AnnotationTypeDeadlockTest {

    @Retention(RUNTIME)
    @AnnB
    public @interface AnnA {
    }

    @Retention(RUNTIME)
    @AnnA
    public @interface AnnB {
    }

    static class Task extends Thread {
        final CountDownLatch prepareLatch;
        final AtomicInteger goLatch;
        final Class<?> clazz;

        Task(CountDownLatch prepareLatch, AtomicInteger goLatch, Class<?> clazz) {
            super(clazz.getSimpleName());
            setDaemon(true); // in case it deadlocks
            this.prepareLatch = prepareLatch;
            this.goLatch = goLatch;
            this.clazz = clazz;
        }

        @Override
        public void run() {
            prepareLatch.countDown();  // notify we are prepared
            while (goLatch.get() > 0); // spin-wait before go
            clazz.getDeclaredAnnotations();
        }
    }

    static void dumpState(Task task) {
        System.err.println(
            "Task[" + task.getName() + "].state: " +
            task.getState() + " ..."
        );
        for (StackTraceElement ste : task.getStackTrace()) {
            System.err.println("\tat " + ste);
        }
        System.err.println();
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch prepareLatch = new CountDownLatch(2);
        AtomicInteger goLatch = new AtomicInteger(1);
        Task taskA = new Task(prepareLatch, goLatch, AnnA.class);
        Task taskB = new Task(prepareLatch, goLatch, AnnB.class);
        taskA.start();
        taskB.start();
        // wait until both threads start-up
        prepareLatch.await();
        // let them go
        goLatch.set(0);
        // attempt to join them
        taskA.join(5000L);
        taskB.join(5000L);

        if (taskA.isAlive() || taskB.isAlive()) {
            dumpState(taskA);
            dumpState(taskB);
            throw new IllegalStateException(
                taskA.getState() == Thread.State.BLOCKED &&
                taskB.getState() == Thread.State.BLOCKED
                ? "deadlock detected"
                : "unexpected condition");
        }
    }
}
