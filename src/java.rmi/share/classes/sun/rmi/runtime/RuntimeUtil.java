/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.rmi.runtime;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * RMI runtime implementation utilities.
 *
 * There is a single instance of this class, which can be obtained
 * with a getInstance() call.
 *
 * This class also contains a couple static methods for creating
 * threads. The methods allow the choice of the Runnable for the
 * new thread to execute, the name of the new thread (which will
 * be prefixed with "RMI "), and whether or not it will be a daemon
 * thread.
 *
 * The new thread may be created in the system thread group (the root
 * of the thread group tree) or an internally created non-system
 * thread group (the "user" thread group).
 *
 * The new thread will have the system class loader as its initial
 * context class loader (that is, its context class loader will NOT be
 * inherited from the current thread).
 *
 * @author      Peter Jones
 **/
public final class RuntimeUtil {

    /**
     * Cached reference to the system (root) thread group.
     */
    private static final ThreadGroup systemThreadGroup;
    static {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        ThreadGroup parent;
        while ((parent = group.getParent()) != null) {
            group = parent;
        }
        systemThreadGroup = group;
    }

    /**
     * Special child of the system thread group for running tasks that
     * may execute user code. The need for a separate thread group may
     * be a vestige of it having had a different security policy from
     * the system thread group, so this might no longer be necessary.
     */
    private static final ThreadGroup userThreadGroup =
        new ThreadGroup(systemThreadGroup, "RMI Runtime");

    /** runtime package log */
    private static final Log runtimeLog =
        Log.getLog("sun.rmi.runtime", null, false);

    /** number of scheduler threads */
    private static final int schedulerThreads =         // default 1
        Integer.getInteger("sun.rmi.runtime.schedulerThreads", 1);

    /** the singleton instance of this class */
    private static final RuntimeUtil instance = new RuntimeUtil();

    /** thread pool for scheduling delayed tasks */
    private final ScheduledThreadPoolExecutor scheduler;

    /**
     * Creates the single instance of RuntimeUtil. Note that this is called
     * from a static initializer, and it has a ThreadFactory that calls
     * static methods on this class, possibly from other threads. This
     * should be ok, as the ScheduledThreadPoolExecutor constructor
     * returns immediately without blocking on the creation of threads
     * by the factory.
     */
    private RuntimeUtil() {
        scheduler = new ScheduledThreadPoolExecutor(
            schedulerThreads,
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger();
                public Thread newThread(Runnable runnable) {
                    try {
                        return newSystemThread(
                            runnable,
                            "Scheduler(" + count.getAndIncrement() + ")",
                            true);
                    } catch (Throwable t) {
                        runtimeLog.log(Level.WARNING,
                                       "scheduler thread factory throws", t);
                        return null;
                    }
                }
            });
        /*
         * We would like to allow the scheduler's threads to terminate
         * if possible, but a bug in DelayQueue.poll can cause code
         * like this to result in a busy loop:
         */
        // stpe.setKeepAliveTime(10, TimeUnit.MINUTES);
        // stpe.allowCoreThreadTimeOut(true);
    }

    public static RuntimeUtil getInstance() {
        return instance;
    }

    /**
     * Returns the shared thread pool for scheduling delayed tasks.
     *
     * Note that the returned pool has limited concurrency, so
     * submitted tasks should be short-lived and should not block.
     **/
    public ScheduledThreadPoolExecutor getScheduler() {
        return scheduler;
    }

    // Thread creation methods.

    /**
     * Internal method to create a new thread with the given settings.
     *
     * @param group the thread group, should be systemThreadGroup or userThreadGroup
     * @param runnable the thread's task
     * @param name the thread's name, which will be prefixed with "RMI "
     * @param daemon whether the thread should be a daemon
     * @return the newly created thread
     */
    private static Thread newThread(ThreadGroup group, Runnable runnable, String name, boolean daemon) {
        Thread t = new Thread(group, runnable, "RMI " + name);
        t.setContextClassLoader(ClassLoader.getSystemClassLoader());
        t.setDaemon(daemon);
        return t;
    }

    /**
     * Creates and returns, but does not start, a new thread with the given settings.
     * The thread will be in the system ("root") thread group.
     *
     * @param runnable the thread's task
     * @param name the thread's name, which will be prefixed with "RMI "
     * @param daemon whether the thread should be a daemon
     * @return the newly created thread
     */
    public static Thread newSystemThread(Runnable runnable, String name, boolean daemon) {
        return newThread(systemThreadGroup, runnable, name, daemon);
    }

    /**
     * Creates and returns, but does not start, a new thread with the given settings.
     * The thread will be in the RMI user thread group.
     *
     * @param runnable the thread's task
     * @param name the thread's name, which will be prefixed with "RMI "
     * @param daemon whether the thread should be a daemon
     * @return the newly created thread
     */
    public static Thread newUserThread(Runnable runnable, String name, boolean daemon) {
        return newThread(userThreadGroup, runnable, name, daemon);
    }
}
