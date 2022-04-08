/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper class for testing virtual threads.
 */

class TestHelper {

    static final int NO_THREAD_LOCALS = 1 << 1;
    static final int NO_INHERIT_THREAD_LOCALS = 1 << 2;

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void run(String name, int characteristics, ThrowingRunnable task)
        throws Exception
    {
        AtomicReference<Exception> exc = new AtomicReference<>();
        Runnable target =  () -> {
            try {
                task.run();
            } catch (Error e) {
                exc.set(new RuntimeException(e));
            } catch (Exception e) {
                exc.set(e);
            }
        };

        Thread.Builder builder = Thread.ofVirtual();
        if (name != null)
            builder.name(name);
        boolean allow = ((characteristics & NO_THREAD_LOCALS)  == 0);
        builder.allowSetThreadLocals(allow);
        boolean inherit = ((characteristics & NO_INHERIT_THREAD_LOCALS) == 0);
        builder.inheritInheritableThreadLocals(inherit);
        Thread thread = builder.start(target);

        // wait for thread to terminate
        while (thread.join(Duration.ofSeconds(10)) == false) {
            System.out.println("-- " + thread + " --");
            for (StackTraceElement e : thread.getStackTrace()) {
                System.out.println("  " + e);
            }
        }

        Exception e = exc.get();
        if (e != null) {
            throw e;
        }
    }


    /**
     * Run a task in a virutal thread and wait for it to terminate.
     * @param name the thread name
     * @param characteristics thread characteristics
     * @param task the task to run
     */
    static void runInVirtualThread(String name, int characteristics, ThrowingRunnable task)
        throws Exception
    {
        run(name, characteristics, task);
    }

    /**
     * Run a task in a virutal thread and wait for it to terminate.
     * @param characteristics thread characteristics
     * @param task the task to run
     */
    static void runInVirtualThread(int characteristics, ThrowingRunnable task)
        throws Exception
    {
        run(null, characteristics, task);
    }

    /**
     * Run a task in a virutal thread and wait for it to terminate.
     * @param name the thread name
     * @param task the task to run
     */
    static void runInVirtualThread(String name, ThrowingRunnable task) throws Exception {
        run(name, 0, task);
    }

    /**
     * Run a task in a virutal thread and wait for it to terminate.
     * @param task the task to run
     */
    static void runInVirtualThread(ThrowingRunnable task) throws Exception {
        run(null, 0, task);
    }

    /**
     * Returns a builder to create virtual threads that use the given scheduler.
     */
    static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual();
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            Field field = clazz.getDeclaredField("scheduler");
            field.setAccessible(true);
            field.set(builder, scheduler);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return builder;
    }

    /**
     * Schedule a thread to be interrupted after a delay.
     */
    static void scheduleInterrupt(Thread thread, long delay) {
        Interrupter task  = new Interrupter(thread, delay);
        new Thread(task).start();
    }

    private static class Interrupter implements Runnable {
        final Thread thread;
        final long delay;

        Interrupter(Thread thread, long delay) {
            this.thread = thread;
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                thread.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
