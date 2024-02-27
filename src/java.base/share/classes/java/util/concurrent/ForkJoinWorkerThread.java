/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

/**
 * A thread managed by a {@link ForkJoinPool}, which executes
 * {@link ForkJoinTask}s.
 * This class is subclassable solely for the sake of adding
 * functionality -- there are no overridable methods dealing with
 * scheduling or execution.  However, you can override initialization
 * and termination methods surrounding the main task processing loop.
 * If you do create such a subclass, you will also need to supply a
 * custom {@link ForkJoinPool.ForkJoinWorkerThreadFactory} to
 * {@linkplain ForkJoinPool#ForkJoinPool(int, ForkJoinWorkerThreadFactory,
 * UncaughtExceptionHandler, boolean, int, int, int, Predicate, long, TimeUnit)
 * use it} in a {@code ForkJoinPool}.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class ForkJoinWorkerThread extends Thread {
    /*
     * ForkJoinWorkerThreads are managed by ForkJoinPools and perform
     * ForkJoinTasks. For explanation, see the internal documentation
     * of class ForkJoinPool.
     *
     * This class just maintains links to its pool and WorkQueue.
     */

    final ForkJoinPool pool;                // the pool this thread works in
    final ForkJoinPool.WorkQueue workQueue; // work-stealing mechanics

    /**
     * Full nonpublic constructor.
     */
    ForkJoinWorkerThread(ThreadGroup group, ForkJoinPool pool,
                         boolean useSystemClassLoader,
                         boolean clearThreadLocals) {
        super(group, null, pool.nextWorkerThreadName(), 0L, !clearThreadLocals);
        UncaughtExceptionHandler handler = (this.pool = pool).ueh;
        this.workQueue = new ForkJoinPool.WorkQueue(this, 0, (int)pool.config,
                                                    clearThreadLocals);
        super.setDaemon(true);
        if (handler != null)
            super.setUncaughtExceptionHandler(handler);
        if (useSystemClassLoader)
            super.setContextClassLoader(ClassLoader.getSystemClassLoader());
    }

    /**
     * Creates a ForkJoinWorkerThread operating in the given thread group and
     * pool, and with the given policy for preserving ThreadLocals.
     *
     * @param group if non-null, the thread group for this
     * thread. Otherwise, the thread group is chosen by the security
     * manager if present, else set to the current thread's thread
     * group.
     * @param pool the pool this thread works in
     * @param preserveThreadLocals if true, always preserve the values of
     * ThreadLocal variables across tasks; otherwise they may be cleared.
     * @throws NullPointerException if pool is null
     * @since 19
     */
    @SuppressWarnings("this-escape")
    protected ForkJoinWorkerThread(ThreadGroup group, ForkJoinPool pool,
                                   boolean preserveThreadLocals) {
        this(group, pool, false, !preserveThreadLocals);
    }

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     *
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null
     */
    @SuppressWarnings("this-escape")
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        this(null, pool, false, false);
    }

    /**
     * Returns the pool hosting this thread.
     *
     * @return the pool
     */
    public ForkJoinPool getPool() {
        return pool;
    }

    /**
     * Returns the unique index number of this thread in its pool.
     * The returned value ranges from zero to the maximum number of
     * threads (minus one) that may exist in the pool, and does not
     * change during the lifetime of the thread.  This method may be
     * useful for applications that track status or collect results
     * per-worker-thread rather than per-task.
     *
     * @return the index number
     */
    public int getPoolIndex() {
        return workQueue.getPoolIndex();
    }

    /**
     * {@return a (non-negative) estimate of the number of tasks in the
     * thread's queue}
     *
     * @since 20
     * @see ForkJoinPool#getQueuedTaskCount()
     */
    public int getQueuedTaskCount() {
        return workQueue.queueSize();
    }

    /**
     * Initializes internal state after construction but before
     * processing any tasks. If you override this method, you must
     * invoke {@code super.onStart()} at the beginning of the method.
     * Initialization requires care: Most fields must have legal
     * default values, to ensure that attempted accesses from other
     * threads work correctly even before this thread starts
     * processing tasks.
     */
    protected void onStart() {
    }

    /**
     * Performs cleanup associated with termination of this worker
     * thread.  If you override this method, you must invoke
     * {@code super.onTermination} at the end of the overridden method.
     *
     * @param exception the exception causing this thread to abort due
     * to an unrecoverable error, or {@code null} if completed normally
     */
    protected void onTermination(Throwable exception) {
    }

    /**
     * This method is required to be public, but should never be
     * called explicitly. It performs the main run loop to execute
     * {@link ForkJoinTask}s.
     */
    public void run() {
        Throwable exception = null;
        ForkJoinPool p = pool;
        ForkJoinPool.WorkQueue w = workQueue;
        if (p != null && w != null) {   // skip on failed initialization
            try {
                p.registerWorker(w);
                onStart();
                p.runWorker(w);
            } catch (Throwable ex) {
                exception = ex;
            } finally {
                try {
                    onTermination(exception);
                } catch (Throwable ex) {
                    if (exception == null)
                        exception = ex;
                } finally {
                    p.deregisterWorker(this, exception);
                }
            }
        }
    }

    /**
     * A worker thread that has no permissions, is not a member of any
     * user-defined ThreadGroup, uses the system class loader as
     * thread context class loader, and clears all ThreadLocals after
     * running each top-level task.
     */
    static final class InnocuousForkJoinWorkerThread extends ForkJoinWorkerThread {
        /** The ThreadGroup for all InnocuousForkJoinWorkerThreads */
        private static final ThreadGroup innocuousThreadGroup;
        @SuppressWarnings("removal")
        private static final AccessControlContext innocuousACC;
        InnocuousForkJoinWorkerThread(ForkJoinPool pool) {
            super(innocuousThreadGroup, pool, true, true);
        }

        @Override @SuppressWarnings("removal")
        protected void onStart() {
            Thread t = Thread.currentThread();
            ThreadLocalRandom.setInheritedAccessControlContext(t, innocuousACC);
        }

        @Override // to silently fail
        public void setUncaughtExceptionHandler(UncaughtExceptionHandler x) { }

        @Override // paranoically
        public void setContextClassLoader(ClassLoader cl) {
            if (cl != null && ClassLoader.getSystemClassLoader() != cl)
                throw new SecurityException("setContextClassLoader");
        }

        @SuppressWarnings("removal")
        static AccessControlContext createACC() {
            return new AccessControlContext(
                new ProtectionDomain[] { new ProtectionDomain(null, null) });
        }
        static ThreadGroup createGroup() {
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            for (ThreadGroup p; (p = group.getParent()) != null; )
                group = p;
            return new ThreadGroup(group, "InnocuousForkJoinWorkerThreadGroup");
        }
        static {
            @SuppressWarnings("removal")
            SecurityManager sm = System.getSecurityManager();
            @SuppressWarnings("removal")
            ThreadGroup g = innocuousThreadGroup =
                (sm == null) ? createGroup() :
                AccessController.doPrivileged(new PrivilegedAction<>() {
                        public ThreadGroup run() {
                            return createGroup(); }});
            @SuppressWarnings("removal")
            AccessControlContext a = innocuousACC =
                (sm == null) ? createACC() :
                AccessController.doPrivileged(new PrivilegedAction<>() {
                        public AccessControlContext run() {
                            return createACC(); }});
        }
    }
}
