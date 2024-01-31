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

package jdk.test.lib.thread;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper class to support tests running tasks in a virtual thread.
 */
public class VThreadRunner {
    private VThreadRunner() { }

    /**
     * Characteristic value signifying that initial values for inheritable
     * thread locals are not inherited from the constructing thread.
     */
    public static final int NO_INHERIT_THREAD_LOCALS = 1 << 2;

    /**
     * Represents a task that does not return a result but may throw an exception.
     */
    @FunctionalInterface
    public interface ThrowingRunnable<X extends Throwable> {
        void run() throws X;
    }

    /**
     * Run a task in a virtual thread and wait for it to terminate.
     * If the task completes with an exception then it is thrown by this method.
     *
     * @param name thread name, can be null
     * @param characteristics thread characteristics
     * @param task the task to run
     * @throws X the exception thrown by the task
     */
    public static <X extends Throwable> void run(String name,
                                                 int characteristics,
                                                 ThrowingRunnable<X> task) throws X {
        var throwableRef = new AtomicReference<Throwable>();
        Runnable target = () -> {
            try {
                task.run();
            } catch (Throwable ex) {
                throwableRef.set(ex);
            }
        };

        Thread.Builder builder = Thread.ofVirtual();
        if (name != null)
            builder.name(name);
        if ((characteristics & NO_INHERIT_THREAD_LOCALS) != 0)
            builder.inheritInheritableThreadLocals(false);
        Thread thread = builder.start(target);

        // wait for thread to terminate
        try {
            while (thread.join(Duration.ofSeconds(10)) == false) {
                System.out.println("-- " + thread + " --");
                for (StackTraceElement e : thread.getStackTrace()) {
                    System.out.println("  " + e);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Throwable ex = throwableRef.get();
        if (ex != null) {
            if (ex instanceof RuntimeException e)
                throw e;
            if (ex instanceof Error e)
                throw e;
            throw (X) ex;
        }
    }

    /**
     * Run a task in a virtual thread and wait for it to terminate.
     * If the task completes with an exception then it is thrown by this method.
     *
     * @param name thread name, can be null
     * @param task the task to run
     * @throws X the exception thrown by the task
     */
    public static <X extends Throwable> void run(String name, ThrowingRunnable<X> task) throws X {
        run(name, 0, task);
    }

    /**
     * Run a task in a virtual thread and wait for it to terminate.
     * If the task completes with an exception then it is thrown by this method.
     *
     * @param characteristics thread characteristics
     * @param task the task to run
     * @throws X the exception thrown by the task
     */
    public static <X extends Throwable> void run(int characteristics, ThrowingRunnable<X> task) throws X {
        run(null, characteristics, task);
    }

    /**
     * Run a task in a virtual thread and wait for it to terminate.
     * If the task completes with an exception then it is thrown by this method.
     *
     * @param task the task to run
     * @throws X the exception thrown by the task
     */
    public static <X extends Throwable> void run(ThrowingRunnable<X> task) throws X {
        run(null, 0, task);
    }

    /**
     * Returns the virtual thread scheduler.
     */
    private static ForkJoinPool defaultScheduler() {
        try {
            var clazz = Class.forName("java.lang.VirtualThread");
            var field = clazz.getDeclaredField("DEFAULT_SCHEDULER");
            field.setAccessible(true);
            return (ForkJoinPool) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the virtual thread scheduler's target parallelism.
     * @return the previous parallelism level
     */
    public static int setParallelism(int size) {
        return defaultScheduler().setParallelism(size);
    }

    /**
     * Ensures that the virtual thread scheduler's target parallelism is at least
     * the given size. If the target parallelism is less than the given size then
     * it is changed to the given size.
     * @return the previous parallelism level
     */
    public static int ensureParallelism(int size) {
        ForkJoinPool pool = defaultScheduler();
        int parallelism = pool.getParallelism();
        if (size > parallelism) {
            pool.setParallelism(size);
        }
        return parallelism;
    }
}
