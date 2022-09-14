/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper class for running tasks in a virtual thread.
 */
public class VThreadRunner {
    private VThreadRunner() { }

    /**
     * Characteristic value signifying that the thread cannot set values for its
     * copy of thread-locals.
     */
    public static final int NO_THREAD_LOCALS = 1 << 1;

    /**
     * Characteristic value signifying that initial values for inheritable
     * thread locals are not inherited from the constructing thread.
     */
    public static final int NO_INHERIT_THREAD_LOCALS = 1 << 2;

    /**
     * Represents a task that does not return a result but may throw
     * an exception.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        /**
         * Runs this operation.
         */
        void run() throws Exception;
    }

    /**
     * Run a task in a virtual thread and wait for it to terminate.
     * If the task completes with an exception then it is thrown by this method.
     * If the task throws an Error then it is wrapped in an RuntimeException.
     *
     * @param name thread name, can be null
     * @param characteristics thread characteristics
     * @param task the task to run
     * @throws Exception the exception thrown by the task
     */
    public static void run(String name,
                           int characteristics,
                           ThrowingRunnable task) throws Exception {
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
        if ((characteristics & NO_THREAD_LOCALS) != 0)
            builder.allowSetThreadLocals(false);
        if ((characteristics & NO_INHERIT_THREAD_LOCALS) != 0)
            builder.inheritInheritableThreadLocals(false);
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
     * Run a task in a virtual thread and wait for it to terminate.
     * If the task completes with an exception then it is thrown by this method.
     * If the task throws an Error then it is wrapped in an RuntimeException.
     *
     * @param name thread name, can be null
     * @param task the task to run
     * @throws Exception the exception thrown by the task
     */
    public static void run(String name, ThrowingRunnable task) throws Exception {
        run(name, 0, task);
    }

    /**
     * Run a task in a virtual thread and wait for it to terminate.
     * If the task completes with an exception then it is thrown by this method.
     * If the task throws an Error then it is wrapped in an RuntimeException.
     *
     * @param characteristics thread characteristics
     * @param task the task to run
     * @throws Exception the exception thrown by the task
     */
    public static void run(int characteristics, ThrowingRunnable task) throws Exception {
        run(null, characteristics, task);
    }

    /**
     * Run a task in a virtual thread and wait for it to terminate.
     * If the task completes with an exception then it is thrown by this method.
     * If the task throws an Error then it is wrapped in an RuntimeException.
     *
     * @param task the task to run
     * @throws Exception the exception thrown by the task
     */
    public static void run(ThrowingRunnable task) throws Exception {
        run(null, 0, task);
    }
}
