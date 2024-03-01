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

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.Permission;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import jdk.internal.access.JavaUtilConcurrentFJPAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.SharedThreadContainer;

/**
 * An {@link ExecutorService} for running {@link ForkJoinTask}s.
 * A {@code ForkJoinPool} provides the entry point for submissions
 * from non-{@code ForkJoinTask} clients, as well as management and
 * monitoring operations.
 *
 * <p>A {@code ForkJoinPool} differs from other kinds of {@link
 * ExecutorService} mainly by virtue of employing
 * <em>work-stealing</em>: all threads in the pool attempt to find and
 * execute tasks submitted to the pool and/or created by other active
 * tasks (eventually blocking waiting for work if none exist). This
 * enables efficient processing when most tasks spawn other subtasks
 * (as do most {@code ForkJoinTask}s), as well as when many small
 * tasks are submitted to the pool from external clients.  Especially
 * when setting <em>asyncMode</em> to true in constructors, {@code
 * ForkJoinPool}s may also be appropriate for use with event-style
 * tasks that are never joined. All worker threads are initialized
 * with {@link Thread#isDaemon} set {@code true}.
 *
 * <p>A static {@link #commonPool()} is available and appropriate for
 * most applications. The common pool is used by any ForkJoinTask that
 * is not explicitly submitted to a specified pool. Using the common
 * pool normally reduces resource usage (its threads are slowly
 * reclaimed during periods of non-use, and reinstated upon subsequent
 * use).
 *
 * <p>For applications that require separate or custom pools, a {@code
 * ForkJoinPool} may be constructed with a given target parallelism
 * level; by default, equal to the number of available processors.
 * The pool attempts to maintain enough active (or available) threads
 * by dynamically adding, suspending, or resuming internal worker
 * threads, even if some tasks are stalled waiting to join others.
 * However, no such adjustments are guaranteed in the face of blocked
 * I/O or other unmanaged synchronization. The nested {@link
 * ManagedBlocker} interface enables extension of the kinds of
 * synchronization accommodated. The default policies may be
 * overridden using a constructor with parameters corresponding to
 * those documented in class {@link ThreadPoolExecutor}.
 *
 * <p>In addition to execution and lifecycle control methods, this
 * class provides status check methods (for example
 * {@link #getStealCount}) that are intended to aid in developing,
 * tuning, and monitoring fork/join applications. Also, method
 * {@link #toString} returns indications of pool state in a
 * convenient form for informal monitoring.
 *
 * <p>As is the case with other ExecutorServices, there are three
 * main task execution methods summarized in the following table.
 * These are designed to be used primarily by clients not already
 * engaged in fork/join computations in the current pool.  The main
 * forms of these methods accept instances of {@code ForkJoinTask},
 * but overloaded forms also allow mixed execution of plain {@code
 * Runnable}- or {@code Callable}- based activities as well.  However,
 * tasks that are already executing in a pool should normally instead
 * use the within-computation forms listed in the table unless using
 * async event-style tasks that are not usually joined, in which case
 * there is little difference among choice of methods.
 *
 * <table class="plain">
 * <caption>Summary of task execution methods</caption>
 *  <tr>
 *    <td></td>
 *    <th scope="col"> Call from non-fork/join clients</th>
 *    <th scope="col"> Call from within fork/join computations</th>
 *  </tr>
 *  <tr>
 *    <th scope="row" style="text-align:left"> Arrange async execution</th>
 *    <td> {@link #execute(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork}</td>
 *  </tr>
 *  <tr>
 *    <th scope="row" style="text-align:left"> Await and obtain result</th>
 *    <td> {@link #invoke(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#invoke}</td>
 *  </tr>
 *  <tr>
 *    <th scope="row" style="text-align:left"> Arrange exec and obtain Future</th>
 *    <td> {@link #submit(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork} (ForkJoinTasks <em>are</em> Futures)</td>
 *  </tr>
 * </table>
 *
 * <p>The parameters used to construct the common pool may be controlled by
 * setting the following {@linkplain System#getProperty system properties}:
 * <ul>
 * <li>{@systemProperty java.util.concurrent.ForkJoinPool.common.parallelism}
 * - the parallelism level, a non-negative integer
 * <li>{@systemProperty java.util.concurrent.ForkJoinPool.common.threadFactory}
 * - the class name of a {@link ForkJoinWorkerThreadFactory}.
 * The {@linkplain ClassLoader#getSystemClassLoader() system class loader}
 * is used to load this class.
 * <li>{@systemProperty java.util.concurrent.ForkJoinPool.common.exceptionHandler}
 * - the class name of a {@link UncaughtExceptionHandler}.
 * The {@linkplain ClassLoader#getSystemClassLoader() system class loader}
 * is used to load this class.
 * <li>{@systemProperty java.util.concurrent.ForkJoinPool.common.maximumSpares}
 * - the maximum number of allowed extra threads to maintain target
 * parallelism (default 256).
 * </ul>
 * If no thread factory is supplied via a system property, then the
 * common pool uses a factory that uses the system class loader as the
 * {@linkplain Thread#getContextClassLoader() thread context class loader}.
 * In addition, if a {@link SecurityManager} is present, then
 * the common pool uses a factory supplying threads that have no
 * {@link Permissions} enabled, and are not guaranteed to preserve
 * the values of {@link java.lang.ThreadLocal} variables across tasks.
 *
 * Upon any error in establishing these settings, default parameters
 * are used. It is possible to disable or limit the use of threads in
 * the common pool by setting the parallelism property to zero, and/or
 * using a factory that may return {@code null}. However doing so may
 * cause unjoined tasks to never be executed.
 *
 * @implNote This implementation restricts the maximum number of
 * running threads to 32767. Attempts to create pools with greater
 * than the maximum number result in {@code
 * IllegalArgumentException}. Also, this implementation rejects
 * submitted tasks (that is, by throwing {@link
 * RejectedExecutionException}) only when the pool is shut down or
 * internal resources have been exhausted.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class ForkJoinPool extends AbstractExecutorService {

    /*
     * Implementation Overview -- omitted until stable
     *
     */

    // static configuration constants

    /**
     * Default idle timeout value (in milliseconds) for idle threads
     * to park waiting for new work before terminating.
     */
    static final long DEFAULT_KEEPALIVE = 60_000L;

    /**
     * Undershoot tolerance for idle timeouts
     */
    static final long TIMEOUT_SLOP = 20L;

    /**
     * The default value for common pool maxSpares.  Overridable using
     * the "java.util.concurrent.ForkJoinPool.common.maximumSpares"
     * system property.  The default value is far in excess of normal
     * requirements, but also far short of maximum capacity and typical OS
     * thread limits, so allows JVMs to catch misuse/abuse before
     * running out of resources needed to do so.
     */
    static final int DEFAULT_COMMON_MAX_SPARES = 256;

    /**
     * Initial capacity of work-stealing queue array.  Must be a power
     * of two, at least 2. See above.
     */
    static final int INITIAL_QUEUE_CAPACITY = 1 << 6;

    // conversions among short, int, long
    static final int  SMASK           = 0xffff;      // (unsigned) short bits
    static final long LMASK           = 0xffffffffL; // lower 32 bits of long
    static final long UMASK           = ~LMASK;      // upper 32 bits

    // masks and sentinels for queue indices
    static final int MAX_CAP          = 0x7fff;   // max # workers
    static final int EXTERNAL_ID_MASK = 0x3ffe;   // max external queue id
    static final int INVALID_ID       = 0x4000;   // unused external queue id

    // pool.runState bits
    static final int STOP             = 1 <<  0;   // terminating
    static final int SHUTDOWN         = 1 <<  1;   // terminate when quiescent
    static final int TERMINATED       = 1 <<  2;   // only set if STOP also set
    static final int RS_LOCK          = 1 <<  3;   // lowest seqlock bit

    // spin/sleep limits for runState locking and elsewhere
    static final int SPIN_WAITS       = 1 <<  7;   // max calls to onSpinWait
    static final int MIN_SLEEP        = 1 << 10;   // approx 1 usec as nanos
    static final int MAX_SLEEP        = 1 << 20;   // approx 1 sec  as nanos

    // {pool, workQueue} config bits
    static final int FIFO             = 1 << 0;   // fifo queue or access mode
    static final int CLEAR_TLS        = 1 << 1;   // set for Innocuous workers
    static final int PRESET_SIZE      = 1 << 2;   // size was set by property

    // others
    static final int DEREGISTERED     = 1 << 31;  // worker terminating
    static final int UNCOMPENSATE     = 1 << 16;  // tryCompensate return
    static final int IDLE             = 1 << 16;  // phase seqlock/version count

    /*
     * Bits and masks for ctl and bounds are packed with 4 16 bit subfields:
     * RC: Number of released (unqueued) workers
     * TC: Number of total workers
     * SS: version count and status of top waiting thread
     * ID: poolIndex of top of Treiber stack of waiters
     *
     * When convenient, we can extract the lower 32 stack top bits
     * (including version bits) as sp=(int)ctl. When sp is non-zero,
     * there are waiting workers.  Count fields may be transiently
     * negative during termination because of out-of-order updates.
     * To deal with this, we use casts in and out of "short" and/or
     * signed shifts to maintain signedness. Because it occupies
     * uppermost bits, we can add one release count using getAndAdd of
     * RC_UNIT, rather than CAS, when returning from a blocked join.
     * Other updates of multiple subfields require CAS.
     */

    // Release counts
    static final int  RC_SHIFT = 48;
    static final long RC_UNIT  = 0x0001L << RC_SHIFT;
    static final long RC_MASK  = 0xffffL << RC_SHIFT;
    // Total counts
    static final int  TC_SHIFT = 32;
    static final long TC_UNIT  = 0x0001L << TC_SHIFT;
    static final long TC_MASK  = 0xffffL << TC_SHIFT;

    /*
     * All atomic operations on task arrays (queues) use Unsafe
     * operations that take array offsets versus indices, based on
     * array base and shift constants established during static
     * initialization.
     */
    static final long ABASE;
    static final int  ASHIFT;

    // Static utilities

    /**
     * Returns the array offset corresponding to the given index for
     * Unsafe task queue operations
     */
    static long slotOffset(int index) {
        return ((long)index << ASHIFT) + ABASE;
    }

    /**
     * If there is a security manager, makes sure caller has
     * permission to modify threads.
     */
    @SuppressWarnings("removal")
    private static void checkPermission() {
        SecurityManager security; RuntimePermission perm;
        if ((security = System.getSecurityManager()) != null) {
            if ((perm = modifyThreadPermission) == null)
                modifyThreadPermission = perm = // races OK
                    new RuntimePermission("modifyThread");
            security.checkPermission(perm);
        }
    }

    // Nested classes

    /**
     * Factory for creating new {@link ForkJoinWorkerThread}s.
     * A {@code ForkJoinWorkerThreadFactory} must be defined and used
     * for {@code ForkJoinWorkerThread} subclasses that extend base
     * functionality or initialize threads with different contexts.
     */
    public static interface ForkJoinWorkerThreadFactory {
        /**
         * Returns a new worker thread operating in the given pool.
         * Returning null or throwing an exception may result in tasks
         * never being executed.  If this method throws an exception,
         * it is relayed to the caller of the method (for example
         * {@code execute}) causing attempted thread creation. If this
         * method returns null or throws an exception, it is not
         * retried until the next attempted creation (for example
         * another call to {@code execute}).
         *
         * @param pool the pool this thread works in
         * @return the new worker thread, or {@code null} if the request
         *         to create a thread is rejected
         * @throws NullPointerException if the pool is null
         */
        public ForkJoinWorkerThread newThread(ForkJoinPool pool);
    }

    /**
     * Default ForkJoinWorkerThreadFactory implementation; creates a
     * new ForkJoinWorkerThread using the system class loader as the
     * thread context class loader.
     */
    static final class DefaultForkJoinWorkerThreadFactory
        implements ForkJoinWorkerThreadFactory {
        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            boolean isCommon = (pool.workerNamePrefix == null);
            @SuppressWarnings("removal")
            SecurityManager sm = System.getSecurityManager();
            if (sm == null)
                return new ForkJoinWorkerThread(null, pool, true, false);
            else if (isCommon)
                return newCommonWithACC(pool);
            else
                return newRegularWithACC(pool);
        }

        /*
         * Create and use static AccessControlContexts only if there
         * is a SecurityManager. (These can be removed if/when
         * SecurityManagers are removed from platform.) The ACCs are
         * immutable and equivalent even when racily initialized, so
         * they don't require locking, although with the chance of
         * needlessly duplicate construction.
         */
        @SuppressWarnings("removal")
        static volatile AccessControlContext regularACC, commonACC;

        @SuppressWarnings("removal")
        static ForkJoinWorkerThread newRegularWithACC(ForkJoinPool pool) {
            AccessControlContext acc = regularACC;
            if (acc == null) {
                Permissions ps = new Permissions();
                ps.add(new RuntimePermission("getClassLoader"));
                ps.add(new RuntimePermission("setContextClassLoader"));
                regularACC = acc =
                    new AccessControlContext(new ProtectionDomain[] {
                            new ProtectionDomain(null, ps) });
            }
            return AccessController.doPrivileged(
                new PrivilegedAction<>() {
                    public ForkJoinWorkerThread run() {
                        return new ForkJoinWorkerThread(null, pool, true, false);
                    }}, acc);
        }

        @SuppressWarnings("removal")
        static ForkJoinWorkerThread newCommonWithACC(ForkJoinPool pool) {
            AccessControlContext acc = commonACC;
            if (acc == null) {
                Permissions ps = new Permissions();
                ps.add(new RuntimePermission("getClassLoader"));
                ps.add(new RuntimePermission("setContextClassLoader"));
                ps.add(new RuntimePermission("modifyThread"));
                ps.add(new RuntimePermission("enableContextClassLoaderOverride"));
                ps.add(new RuntimePermission("modifyThreadGroup"));
                commonACC = acc =
                    new AccessControlContext(new ProtectionDomain[] {
                            new ProtectionDomain(null, ps) });
            }
            return AccessController.doPrivileged(
                new PrivilegedAction<>() {
                    public ForkJoinWorkerThread run() {
                        return new ForkJoinWorkerThread.
                            InnocuousForkJoinWorkerThread(pool);
                    }}, acc);
        }
    }

    /**
     * Queues supporting work-stealing as well as external task
     * submission. See above for descriptions and algorithms.
     */
    static final class WorkQueue {
        // fields declared in order of their likely layout on most VMs
        final ForkJoinWorkerThread owner; // null if shared
        ForkJoinTask<?>[] array;   // the queued tasks; power of 2 size
        int base;                  // index of next slot for poll
        final int config;          // mode bits

        // fields otherwise causing more unnecessary false-sharing cache misses
        @jdk.internal.vm.annotation.Contended("w")
        int top;                   // index of next slot for push
        @jdk.internal.vm.annotation.Contended("w")
        volatile int phase;        // versioned active status
        @jdk.internal.vm.annotation.Contended("w")
        int stackPred;             // pool stack (ctl) predecessor link
        @jdk.internal.vm.annotation.Contended("w")
        volatile int source;       // source queue id (or DEREGISTERED)
        @jdk.internal.vm.annotation.Contended("w")
        int nsteals;               // number of steals from other queues
        @jdk.internal.vm.annotation.Contended("w")
        volatile int parking;      // nonzero if parked in awaitWork

        // Support for atomic operations
        private static final Unsafe U;
        private static final long PHASE;
        private static final long BASE;
        private static final long TOP;
        private static final long SOURCE;
        private static final long ARRAY;

        final void updateBase(int v) {
            U.putIntVolatile(this, BASE, v);
        }
        final void updateTop(int v) {
            U.putIntOpaque(this, TOP, v);
        }
        final void setSource(int v) {
            U.getAndSetInt(this, SOURCE, v);
        }
        final void updateArray(ForkJoinTask<?>[] a) {
            U.getAndSetReference(this, ARRAY, a);
        }
        final void unlockPhase() {
            U.getAndAddInt(this, PHASE, IDLE);
        }
        final boolean tryLockPhase() {    // seqlock acquire
            int p;
            return (((p = phase) & IDLE) != 0 &&
                    U.compareAndSetInt(this, PHASE, p, p + IDLE));
        }

        /**
         * Constructor. For internal queues, most fields are initialized
         * upon thread start in pool.registerWorker.
         */
        WorkQueue(ForkJoinWorkerThread owner, int id, int cfg,
                  boolean clearThreadLocals) {
            if (clearThreadLocals)
                cfg |= CLEAR_TLS;
            this.config = cfg;
            top = base = 1;
            this.phase = id;
            this.owner = owner;
        }

        /**
         * Returns an exportable index (used by ForkJoinWorkerThread).
         */
        final int getPoolIndex() {
            return (phase & 0xffff) >>> 1; // ignore odd/even tag bit
        }

        /**
         * Returns the approximate number of tasks in the queue.
         */
        final int queueSize() {
            int unused = phase;             // for ordering effect
            return Math.max(top - base, 0); // ignore transient negative
        }

        /**
         * Pushes a task. Called only by owner or if already locked
         *
         * @param task the task. Caller must ensure non-null.
         * @param pool the pool to signal if was previously empty, else null
         * @param internal if caller owns this queue
         * @throws RejectedExecutionException if array could not be resized
         */
        final void push(ForkJoinTask<?> task, ForkJoinPool pool,
                        boolean internal) {
            int s = top, b = base, cap, m, p, room, newCap; ForkJoinTask<?>[] a;
            if ((a = array) == null || (cap = a.length) <= 0 ||
                (room = (m = cap - 1) - (s - b)) < 0) { // could not resize
                if (!internal)
                    unlockPhase();
                throw new RejectedExecutionException("Queue capacity exceeded");
            }
            top = s + 1;
            long pos = slotOffset(p = m & s);
            if (!internal)
                U.putReference(a, pos, task);         // inside lock
            else
                U.getAndSetReference(a, pos, task);   // fully fenced
            if (room == 0 && (newCap = cap << 1) > 0) {
                ForkJoinTask<?>[] newArray = null;
                try {                                 // resize for next time
                    newArray = new ForkJoinTask<?>[newCap];
                } catch (OutOfMemoryError ex) {
                }
                if (newArray != null) {               // else throw on next push
                    int newMask = newCap - 1;         // poll old, push to new
                    for (int k = s, j = cap; j > 0; --j, --k) {
                        ForkJoinTask<?> u;
                        if ((u = (ForkJoinTask<?>)U.getAndSetReference(
                                 a, slotOffset(k & m), null)) == null)
                            break;                    // lost to pollers
                        newArray[k & newMask] = u;
                    }
                    updateArray(a = newArray);        // fully fenced
                }
            }
            if (!internal)
                unlockPhase();
            if (room != 0 && a[m & (s - 1)] == null && pool != null)
                pool.signalWork(false, a, p, null);
        }

        /**
         * Takes next task, if one exists, in order specified by mode,
         * so acts as either local-pop or local-poll. Called only by owner.
         * @param fifo nonzero if FIFO mode
         */
        private ForkJoinTask<?> nextLocalTask(int fifo) {
            ForkJoinTask<?> t = null;
            ForkJoinTask<?>[] a = array;
            int b = base, p = top, cap;
            if (p - b > 0 && a != null && (cap = a.length) > 0) {
                for (int m = cap - 1, s, nb;;) {
                    if (fifo == 0 || (nb = b + 1) == p) {
                        if ((t = (ForkJoinTask<?>)U.getAndSetReference(
                                 a, slotOffset(m & (s = p - 1)), null)) != null)
                            updateTop(s);       // else lost race for only task
                        break;
                    }
                    if ((t = (ForkJoinTask<?>)U.getAndSetReference(
                             a, slotOffset(m & b), null)) != null) {
                        updateBase(nb);
                        break;
                    }
                    while (b == (b = base)) {
                        U.loadFence();
                        Thread.onSpinWait();    // spin to reduce memory traffic
                    }
                    if (p - b <= 0)
                        break;
                }
            }
            return t;
        }

        /**
         * Takes next task, if one exists, using configured mode.
         * (Always internal, never called for Common pool.)
         */
        final ForkJoinTask<?> nextLocalTask() {
            return nextLocalTask(config & FIFO);
        }

        /**
         * Pops the given task only if it is at the current top.
         * @param task the task. Caller must ensure non-null.
         * @param internal if caller owns this queue
         */
        final boolean tryUnpush(ForkJoinTask<?> task, boolean internal) {
            boolean taken = false;
            ForkJoinTask<?>[] a = array;
            int p = top, s = p - 1, cap, k;
            if (a != null && (cap = a.length) > 0 &&
                a[k = (cap - 1) & s] == task &&
                (internal || tryLockPhase())) {
                if (top == p &&
                    U.compareAndSetReference(a, slotOffset(k), task, null)) {
                    taken = true;
                    updateTop(s);
                }
                if (!internal)
                    unlockPhase();
            }
            return taken;
        }

        /**
         * Returns next task, if one exists, in order specified by mode.
         */
        final ForkJoinTask<?> peek() {
            ForkJoinTask<?>[] a = array;
            int b = base, cfg = config, p = top, cap;
            if (p != b && a != null && (cap = a.length) > 0) {
                if ((cfg & FIFO) == 0)
                    return a[(cap - 1) & (p - 1)];
                else { // skip over in-progress removals
                    ForkJoinTask<?> t;
                    for ( ; p - b > 0; ++b) {
                        if ((t = a[(cap - 1) & b]) != null)
                            return t;
                    }
                }
            }
            return null;
        }

        /**
         * Polls for a task. Used only by non-owners.
         *
         */
        final ForkJoinTask<?> poll() {
            for (;;) {
                ForkJoinTask<?>[] a = array;
                int b = base, cap, k;
                if (a == null || (cap = a.length) <= 0)
                    break;
                ForkJoinTask<?> t = a[k = b & (cap - 1)];
                U.loadFence();
                if (base == b) {
                    Object o;
                    int nb = b + 1, nk = nb & (cap - 1);
                    if (t == null)
                        o = a[k];
                    else if (t == (o = U.compareAndExchangeReference(
                                       a, slotOffset(k), t, null))) {
                        updateBase(nb);
                        return t;
                    }
                    if (o == null && a[nk] == null && array == a &&
                        (phase & (IDLE | 1)) != 0 && top - base <= 0)
                        break;                    // empty
                }
            }
            return null;
        }

        // specialized execution methods

        /**
         * Runs the given task, as well as remaining local tasks, plus
         * those from src queue that can be taken without interference.
         */
        final void topLevelExec(ForkJoinTask<?> task, WorkQueue src,
                                int srcBase, int cfg) {
            if (task != null && src != null) {
                int fifo = cfg & FIFO, nstolen = 1;
                for (;;) {
                    task.doExec();
                    if ((task = nextLocalTask(fifo)) == null) {
                        int k, cap; ForkJoinTask<?>[] a;
                        if (src.base != srcBase ||
                            (a = src.array) == null || (cap = a.length) <= 0 ||
                            (task = a[k = srcBase & (cap - 1)]) == null)
                            break;
                        U.loadFence();
                        if (src.base != srcBase || !U.compareAndSetReference(
                                a, slotOffset(k), task, null))
                            break;
                        src.updateBase(++srcBase);
                        ++nstolen;
                    }
                }
                nsteals += nstolen;
                if ((cfg & CLEAR_TLS) != 0)
                    ThreadLocalRandom.eraseThreadLocals(Thread.currentThread());
            }
        }

        /**
         * Deep form of tryUnpush: Traverses from top and removes and
         * runs task if present.
         */
        final void tryRemoveAndExec(ForkJoinTask<?> task, boolean internal) {
            ForkJoinTask<?>[] a = array;
            int b = base, p = top, s = p - 1, d = p - b, cap;
            if (a != null && (cap = a.length) > 0) {
                for (int m = cap - 1, i = s; d > 0; --i, --d) {
                    ForkJoinTask<?> t; int k; boolean taken;
                    if ((t = a[k = i & m]) == null)
                        break;
                    if (t == task) {
                        long pos = slotOffset(k);
                        if (!internal && !tryLockPhase())
                            break;                  // fail if locked
                        if (taken =
                            (top == p &&
                             U.compareAndSetReference(a, pos, task, null))) {
                            if (i == s)             // act as pop
                                updateTop(s);
                            else if (i == base)     // act as poll
                                updateBase(i + 1);
                            else {                  // swap with top
                                U.putReferenceVolatile(
                                    a, pos, (ForkJoinTask<?>)
                                    U.getAndSetReference(
                                        a, slotOffset(s & m), null));
                                updateTop(s);
                            }
                        }
                        if (!internal)
                            unlockPhase();
                        if (taken)
                            task.doExec();
                        break;
                    }
                }
            }
        }

        /**
         * Tries to pop and run tasks within the target's computation
         * until done, not found, or limit exceeded.
         *
         * @param task root of computation
         * @param limit max runs, or zero for no limit
         * @return task status if known to be done
         */
        final int helpComplete(ForkJoinTask<?> task, boolean internal, int limit) {
            int status = 0;
            if (task != null) {
                outer: for (;;) {
                    ForkJoinTask<?>[] a; ForkJoinTask<?> t; boolean taken;
                    int stat, p, s, cap, k;
                    if ((stat = task.status) < 0) {
                        status = stat;
                        break;
                    }
                    if ((a = array) == null || (cap = a.length) <= 0)
                        break;
                    if ((t = a[k = (cap - 1) & (s = (p = top) - 1)]) == null)
                        break;
                    if (!(t instanceof CountedCompleter))
                        break;
                    CountedCompleter<?> f = (CountedCompleter<?>)t;
                    for (int steps = cap;;) {       // bound path
                        if (f == task)
                            break;
                        if ((f = f.completer) == null || --steps == 0)
                            break outer;
                    }
                    if (!internal && !tryLockPhase())
                        break;
                    if (taken =
                        (top == p &&
                         U.compareAndSetReference(a, slotOffset(k), t, null)))
                        updateTop(s);
                    if (!internal)
                        unlockPhase();
                    if (!taken)
                        break;
                    t.doExec();
                    if (limit != 0 && --limit == 0)
                        break;
                }
            }
            return status;
        }

        /**
         * Tries to poll and run AsynchronousCompletionTasks until
         * none found or blocker is released
         *
         * @param blocker the blocker
         */
        final void helpAsyncBlocker(ManagedBlocker blocker) {
            for (;;) {
                ForkJoinTask<?>[] a; int b, cap, k;
                if ((a = array) == null || (cap = a.length) <= 0)
                    break;
                ForkJoinTask<?> t = a[k = (b = base) & (cap - 1)];
                U.loadFence();
                if (t == null) {
                    if (top - b <= 0)
                        break;
                }
                else if (!(t instanceof CompletableFuture
                           .AsynchronousCompletionTask))
                    break;
                if (blocker != null && blocker.isReleasable())
                    break;
                if (base == b && t != null &&
                    U.compareAndSetReference(a, slotOffset(k), t, null)) {
                    updateBase(b + 1);
                    t.doExec();
                }
            }
        }

        // misc

        /**
         * Returns true if internal and not known to be blocked.
         */
        final boolean isApparentlyUnblocked() {
            Thread wt; Thread.State s;
            return ((wt = owner) != null && (phase & IDLE) != 0 &&
                    (s = wt.getState()) != Thread.State.BLOCKED &&
                    s != Thread.State.WAITING &&
                    s != Thread.State.TIMED_WAITING);
        }

        static {
            U = Unsafe.getUnsafe();
            Class<WorkQueue> klass = WorkQueue.class;
            PHASE = U.objectFieldOffset(klass, "phase");
            BASE = U.objectFieldOffset(klass, "base");
            TOP = U.objectFieldOffset(klass, "top");
            SOURCE = U.objectFieldOffset(klass, "source");
            ARRAY = U.objectFieldOffset(klass, "array");
        }
    }

    // static fields (initialized in static initializer below)

    /**
     * Creates a new ForkJoinWorkerThread. This factory is used unless
     * overridden in ForkJoinPool constructors.
     */
    public static final ForkJoinWorkerThreadFactory
        defaultForkJoinWorkerThreadFactory;

    /**
     * Common (static) pool. Non-null for public use unless a static
     * construction exception, but internal usages null-check on use
     * to paranoically avoid potential initialization circularities
     * as well as to simplify generated code.
     */
    static final ForkJoinPool common;

    /**
     * Sequence number for creating worker names
     */
    private static volatile int poolIds;

    /**
     * Permission required for callers of methods that may start or
     * kill threads. Lazily constructed.
     */
    static volatile RuntimePermission modifyThreadPermission;

    // fields declared in order of their likely layout on most VMs
    volatile CountDownLatch termination; // lazily constructed
    final Predicate<? super ForkJoinPool> saturate;
    final ForkJoinWorkerThreadFactory factory;
    final UncaughtExceptionHandler ueh;  // per-worker UEH
    final SharedThreadContainer container;
    final String workerNamePrefix;       // null for common pool
    WorkQueue[] queues;                  // main registry
    final long keepAlive;                // milliseconds before dropping if idle
    final long config;                   // static configuration bits
    volatile long stealCount;            // collects worker nsteals
    volatile long threadIds;             // for worker thread names
    volatile int runState;               // versioned, lockable
    @jdk.internal.vm.annotation.Contended("fjpctl") // segregate
    volatile long ctl;                   // main pool control
    @jdk.internal.vm.annotation.Contended("fjpctl") // colocate
    int parallelism;                     // target number of workers

    // Support for atomic operations
    private static final Unsafe U;
    private static final long CTL;
    private static final long RUNSTATE;
    private static final long PARALLELISM;
    private static final long THREADIDS;
    private static final long TERMINATION;
    private static final Object POOLIDS_BASE;
    private static final long POOLIDS;

    private boolean compareAndSetCtl(long c, long v) {
        return U.compareAndSetLong(this, CTL, c, v);
    }
    private long compareAndExchangeCtl(long c, long v) {
        return U.compareAndExchangeLong(this, CTL, c, v);
    }
    private long getAndAddCtl(long v) {
        return U.getAndAddLong(this, CTL, v);
    }
    private long incrementThreadIds() {
        return U.getAndAddLong(this, THREADIDS, 1L);
    }
    private static int getAndAddPoolIds(int x) {
        return U.getAndAddInt(POOLIDS_BASE, POOLIDS, x);
    }
    private int getAndSetParallelism(int v) {
        return U.getAndSetInt(this, PARALLELISM, v);
    }
    private int getParallelismOpaque() {
        return U.getIntOpaque(this, PARALLELISM);
    }
    private CountDownLatch cmpExTerminationSignal(CountDownLatch x) {
        return (CountDownLatch)
            U.compareAndExchangeReference(this, TERMINATION, null, x);
    }

    // runState operations

    private int getAndBitwiseOrRunState(int v) { // for status bits
        return U.getAndBitwiseOrInt(this, RUNSTATE, v);
    }
    private boolean casRunState(int c, int v) {
        return U.compareAndSetInt(this, RUNSTATE, c, v);
    }
    private void unlockRunState() {              // increment lock bit
        U.getAndAddInt(this, RUNSTATE, RS_LOCK);
    }
    private int lockRunState() {                // lock and return current state
        int s, u;                               // locked when RS_LOCK set
        if (((s = runState) & RS_LOCK) == 0 && casRunState(s, u = s + RS_LOCK))
            return u;
        else
            return spinLockRunState();
    }
    private int spinLockRunState() {            // spin/sleep
        for (int waits = 0, s, u;;) {
            if (((s = runState) & RS_LOCK) == 0) {
                if (casRunState(s, u = s + RS_LOCK))
                    return u;
                waits = 0;
            } else if (waits < SPIN_WAITS) {
                ++waits;
                Thread.onSpinWait();
            } else {
                if (waits < MIN_SLEEP)
                    waits = MIN_SLEEP;
                LockSupport.parkNanos(this, (long)waits);
                if (waits < MAX_SLEEP)
                    waits <<= 1;
            }
        }
    }

    static boolean poolIsStopping(ForkJoinPool p) { // Used by ForkJoinTask
        return p != null && (p.runState & STOP) != 0;
    }

    // Creating, registering, and deregistering workers

    /**
     * Tries to construct and start one worker. Assumes that total
     * count has already been incremented as a reservation.  Invokes
     * deregisterWorker on any failure.
     *
     * @return true if successful
     */
    private boolean createWorker() {
        ForkJoinWorkerThreadFactory fac = factory;
        SharedThreadContainer ctr = container;
        Throwable ex = null;
        ForkJoinWorkerThread wt = null;
        try {
            if ((runState & STOP) == 0 &&  // avoid construction if terminating
                fac != null && (wt = fac.newThread(this)) != null) {
                if (ctr != null)
                    ctr.start(wt);
                else
                    wt.start();
                return true;
            }
        } catch (Throwable rex) {
            ex = rex;
        }
        deregisterWorker(wt, ex);
        return false;
    }

    /**
     * Provides a name for ForkJoinWorkerThread constructor.
     */
    final String nextWorkerThreadName() {
        String prefix = workerNamePrefix;
        long tid = incrementThreadIds() + 1L;
        if (prefix == null) // commonPool has no prefix
            prefix = "ForkJoinPool.commonPool-worker-";
        return prefix.concat(Long.toString(tid));
    }

    /**
     * Finishes initializing and records internal queue.
     *
     * @param w caller's WorkQueue
     */
    final void registerWorker(WorkQueue w) {
        if (w != null) {
            w.array = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];
            ThreadLocalRandom.localInit();
            int seed = w.stackPred = ThreadLocalRandom.getProbe();
            int phaseSeq = seed & ~((IDLE << 1) - 1); // initial phase tag
            int id = ((seed << 1) | 1) & SMASK; // base of linear-probe-like scan
            int stop = lockRunState() & STOP;
            try {
                WorkQueue[] qs; int n;
                if (stop == 0 && (qs = queues) != null && (n = qs.length) > 0) {
                    for (int k = n, m = n - 1;  ; id += 2) {
                        if (qs[id &= m] == null)
                            break;
                        if ((k -= 2) <= 0) {
                            id |= n;
                            break;
                        }
                    }
                    w.phase = id | phaseSeq;    // now publishable
                    if (id < n)
                        qs[id] = w;
                    else {                      // expand
                        int an = n << 1, am = an - 1;
                        WorkQueue[] as = new WorkQueue[an];
                        as[id & am] = w;
                        for (int j = 1; j < n; j += 2)
                            as[j] = qs[j];
                        for (int j = 0; j < n; j += 2) {
                            WorkQueue q;        // shared queues may move
                            if ((q = qs[j]) != null)
                                as[q.phase & EXTERNAL_ID_MASK & am] = q;
                        }
                        U.storeFence();         // fill before publish
                        queues = as;
                    }
                }
            } finally {
                unlockRunState();
            }
        }
    }

    /**
     * Final callback from terminating worker, as well as upon failure
     * to construct or start a worker.  Removes record of worker from
     * array, and adjusts counts. If pool is shutting down, tries to
     * complete termination.
     *
     * @param wt the worker thread, or null if construction failed
     * @param ex the exception causing failure, or null if none
     */
    final void deregisterWorker(ForkJoinWorkerThread wt, Throwable ex) {
        WorkQueue w = null;
        int src = 0, phase = 0;
        boolean replaceable = false;
        if (wt != null && (w = wt.workQueue) != null) {
            phase = w.phase;
            if ((src = w.source) != DEREGISTERED) { // else trimmed on timeout
                w.source = DEREGISTERED;
                if (phase != 0) {         // else failed to start
                    replaceable = true;
                    if ((phase & IDLE) != 0)
                        releaseAll();     // pool stopped before released
                }
            }
        }
        if (src != DEREGISTERED) {        // decrement counts
            long c = ctl;
            do {} while (c != (c = compareAndExchangeCtl(
                                   c, ((RC_MASK & (c - RC_UNIT)) |
                                       (TC_MASK & (c - TC_UNIT)) |
                                       (LMASK & c)))));
            if (w != null) {              // cancel remaining tasks
                for (ForkJoinTask<?> t; (t = w.nextLocalTask()) != null; ) {
                    try {
                        t.cancel(false);
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
        if ((tryTerminate(false, false) & STOP) == 0 && w != null) {
            WorkQueue[] qs; int n, i;     // remove index unless terminating
            long ns = w.nsteals & 0xffffffffL;
            if ((lockRunState() & STOP) != 0)
                replaceable = false;
            else if ((qs = queues) != null && (n = qs.length) > 0 &&
                     qs[i = phase & SMASK & (n - 1)] == w) {
                qs[i] = null;
                stealCount += ns;         // accumulate steals
            }
            unlockRunState();
            if (replaceable)
                signalWork(false, null, 0, null);
        }
        if (ex != null)
            ForkJoinTask.rethrow(ex);
    }

    /**
     * Releases an idle worker, or creates one if not enough exist,
     * returning on contention if a signal task is already taken.
     *
     * @param propagating true if  propagating signal
     * @param a if nonnull, a task array holding task signalled
     * @param k index of task in array
     * @param task the signalled task or null if just checking presence
     */
    final void signalWork(boolean propagating, ForkJoinTask<?>[] a, int k,
                          ForkJoinTask<?> task) {
        int pc = parallelism;
        for (long c = ctl;;) {
            ForkJoinTask<?> t;
            WorkQueue[] qs = queues;
            if (a != null && a.length > k && k >= 0 &&
                ((t = a[k]) == null || (task != null && t != task)))
                break;
            long ac = (c + RC_UNIT) & RC_MASK, nc;
            int sp = (int)c, i = sp & SMASK;
            if ((short)(c >>> RC_SHIFT) >= pc)
                break;
            if (qs == null || qs.length <= i)
                break;
            WorkQueue w = qs[i], v = null;
            if (sp == 0) {
                if ((short)(c >>> TC_SHIFT) >= pc)
                    break;
                nc = ac | ((c + TC_UNIT) & TC_MASK);
            }
            else if ((v = w) == null)
                break;
            else
                nc = ac | (c & TC_MASK) | (v.stackPred & LMASK);
            if (c == (c = ctl) &&
                c == (c = compareAndExchangeCtl(c, nc))) {
                if (v == null)
                    createWorker();
                else {
                    v.phase = sp;
                    if (v.parking != 0)
                        U.unpark(v.owner);
                }
                break;
            }
            if (propagating && (c & RC_MASK) >= (nc & RC_MASK))
                break;
        }
    }

    /**
     * Reactivates the given worker, and possibly others if not top of
     * ctl stack. Called only during shutdown to ensure release on
     * termination.
     */
    private void releaseAll() {
        for (long c = ctl;;) {
            WorkQueue[] qs; WorkQueue v; int sp, i;
            if ((sp = (int)c) == 0 || (qs = queues) == null ||
                qs.length <= (i = sp & SMASK) || (v = qs[i]) == null)
                break;
            if (c == (c = compareAndExchangeCtl(
                          c, ((UMASK & (c + RC_UNIT)) | (c & TC_MASK) |
                              (v.stackPred & LMASK))))) {
                v.phase = sp;
                if (v.parking != 0)
                    U.unpark(v.owner);
            }
        }
    }

    /**
     * Internal version of isQuiescent and related functionality.
     * @return positive if stopping, nonnegative if terminating or all
     * workers are inactive and submission queues are empty and
     * unlocked; if so, setting STOP if shutdown is enabled
     */
    private int quiescent() {
        outer: for (;;) {
            long phaseSum = 0L;
            boolean swept = false;
            for (int e, prevRunState = 0; ; prevRunState = e) {
                long c = ctl;
                if (((e = runState) & STOP) != 0)
                    return 1;                         // terminating
                else if ((c & RC_MASK) > 0L)
                    return -1;                        // at least one active
                else if (!swept || e != prevRunState || (e & RS_LOCK) != 0) {
                    long sum = c;
                    WorkQueue[] qs = queues; WorkQueue q;
                    int n = (qs == null) ? 0 : qs.length;
                    for (int i = 0; i < n; ++i) {         // scan queues
                        if ((q = qs[i]) != null) {
                            int p = q.phase, s = q.top, b = q.base;
                            sum += (p & 0xffffffffL) | ((long)b << 32);
                            if ((p & IDLE) == 0 || s - b > 0) {
                                if ((i & 1) == 0 && compareAndSetCtl(c, c))
                                    signalWork(false, q.array, q.base, null);
                                return -1;
                            }
                        }
                    }
                    swept = (phaseSum == (phaseSum = sum));
                }
                else if ((e & SHUTDOWN) == 0)
                    return 0;
                else if (compareAndSetCtl(c, c) && casRunState(e, e | STOP)) {
                    releaseAll();                         // confirmed
                    return 1;                             // enable termination
                }
                else
                    break;                                // restart
            }
        }
    }

    /**
     * Top-level runloop for workers, called by ForkJoinWorkerThread.run.
     * See above for explanation.
     *
     * @param w caller's WorkQueue (may be null on failed initialization)
     */
    final void runWorker(WorkQueue w) {
        if (w != null) {
            int cfg = w.config & (FIFO|CLEAR_TLS);
            long stat = (-1L << 32) | (w.stackPred & LMASK);
            while ((stat = scan(w, stat, cfg)) >= 0L ||
                   (quiescent() <= 0 && awaitWork(w) == 0));
        }
    }

    /**
     * Scans for and if found executes top-level task
     *
     * @param w caller's WorkQueue
     * @param stat random seed and src
     * @param cfg config bits
     * @return src and seed for next use, negative if no tasks found
     */
    private long scan(WorkQueue w, long stat, int cfg) {
        if (w == null)                              // currently impossible
            return stat;
        int r = (int)stat;                          // random seed
        int src = (int)(stat >>> 32);               // last steal or -1
        long polls = 0L;                            // total # slots scanned
        int phase = w.phase;                        // IDLE set when deactivated
        while ((runState & STOP) == 0) {
            WorkQueue[] qs = queues;
            int n = (qs == null) ? 0 : qs.length;
            long pctl = ctl;                        // for signal check
            int i = r, step = (r >>> 16) | 1;       // for random permutation
            r ^= r << 13; r ^= r >>> 17; r ^= r << 5; // advance xorshift
            int prevPhase = phase;
            boolean rescan = false;
            for (int l = n; l > 0; --l, i += step) {
                int j; WorkQueue q;
                if ((q = qs[j = i & SMASK & (n - 1)]) != null) {
                    for (;;) {
                        ++polls;
                        int b = q.base, cap, k, nb, nk; ForkJoinTask<?>[] a;
                        if ((a = q.array) == null || (cap = a.length) <= 0)
                            break;
                        long kp = slotOffset(k = b & (cap - 1));
                        ForkJoinTask<?> t = a[k];
                        U.loadFence();              // re-read
                        if (q.base != b || a[k] != t) {
                            if (rescan)             // reduce contention
                                break;
                            else
                                continue;           // inconsistent
                        }
                        ForkJoinTask<?> nt = a[nk = (nb = b + 1) & (cap - 1)];
                        ForkJoinTask<?> st = a[(nb + 1) & (cap - 1)];
                        if (t == null) {
                            if (!rescan && (nt != null || st != null ||
                                            q.top - nb > 0))
                                rescan = true;      // probe before reading top
                            break;
                        }
                        if ((phase & IDLE) != 0) {  // recheck or reactivate
                            long sp = w.stackPred & LMASK, c; int nextp;
                            if (((phase = w.phase) & IDLE) != 0) {
                                if (rescan ||
                                    (nextp = phase + 1) != (int)(c = ctl))
                                    break;          // ineligible
                                long nc = sp | ((c + RC_UNIT) & UMASK);
                                if (a[k] != t || !compareAndSetCtl(c, nc))
                                    continue;
                                w.phase = nextp;    // self-signalled
                            }
                            rescan = true;
                        }
                        if (U.compareAndSetReference(a, kp, t, null)) {
                            q.base = nb;
                            w.setSource(j);         // fully fenced
                            long taken = ((long)j << 32) | (r & LMASK);
                            if (src != j)           // propagate signal
                                signalWork(true, a, nk, nt);
                            w.topLevelExec(t, q, nb, cfg);
                            return taken;
                        }
                    }
                }
            }
            phase = w.phase;
            long sc = ctl, ac = sc & RC_MASK, dc = ac - (pctl & RC_MASK);
            if ((phase & IDLE) != 0) {
                if (ac <= 0L ||
                    (!rescan && polls >= SPIN_WAITS && (sc == pctl || dc < 0L)))
                    break;                          // block or terminate
            }
            else if (!rescan && (phase == prevPhase || dc <= 0L)) {
                int idle = phase | IDLE;            // try to deactivate
                long qc = ((idle + IDLE) & LMASK) | ((sc - RC_UNIT) & UMASK);
                w.stackPred = (int)sc;              // set ctl stack link
                w.phase = idle;
                if (!compareAndSetCtl(sc, qc))
                    w.phase = phase;                // back out
                else {
                    phase = idle;
                    polls = 0L;
                    src = -1;
                }
            }
        }
        return (-1L << 32) | (r & LMASK);
    }

    /**
     * Awaits signal or termination.
     *
     * @param w the WorkQueue (may be null if already terminated)
     * @return nonzero for exit
     */
    private int awaitWork(WorkQueue w) {
        long c = ctl;
        int p = IDLE, phase;
        if (w != null && (p = (phase = w.phase) & IDLE) != 0) {
            LockSupport.setCurrentBlocker(this);
            int nextPhase = phase + IDLE;
            int tc = (short)(c >>> TC_SHIFT), ac = (short)(c >>> RC_SHIFT);
            long deadline = 0L;            // set if all idle and w is ctl top
            if (ac <= 0 && (int)c == nextPhase) {
                int np = parallelism, nt = tc;
                long delay = keepAlive;    // scale if not fully populated
                if (nt != (nt = Math.max(nt, np)) && nt > 0)
                    delay = Math.max(TIMEOUT_SLOP, delay / nt);
                long d = delay + System.currentTimeMillis();
                deadline = (d == 0L) ? 1L : d;
            }
            w.parking = 1;             // enable unpark
            for (;;) {                 // emulate LockSupport.park
                if ((runState & STOP) != 0)
                    break;
                if ((p = w.phase & IDLE) == 0)
                    break;
                U.park(deadline != 0L, deadline);
                if ((p = w.phase & IDLE) == 0)
                    break;
                if ((runState & STOP) != 0)
                    break;
                Thread.interrupted();  // clear for next park
                if (deadline != 0L &&  // try to trim
                    deadline - System.currentTimeMillis() < TIMEOUT_SLOP) {
                    long sp = w.stackPred & LMASK, dc = ctl;
                    long nc = sp | (UMASK & (dc - TC_UNIT));
                    if ((int)dc == nextPhase && compareAndSetCtl(dc, nc)) {
                        WorkQueue[] qs; WorkQueue v; int vp, i;
                        w.source = DEREGISTERED;
                        w.phase = nextPhase; // try to wake up next waiter
                        if ((vp = (int)nc) != 0 && (qs = queues) != null &&
                            qs.length > (i = vp & SMASK) &&
                            (v = qs[i]) != null &&
                            compareAndSetCtl(nc, ((UMASK & (nc + RC_UNIT)) |
                                                  (nc & TC_MASK) |
                                                  (v.stackPred & LMASK)))) {
                            v.phase = vp;
                            U.unpark(v.owner);
                        }
                        break;
                    }
                    deadline = 0L;     // no longer trimmable
                }
            }
            w.parking = 0;             // disable unpark
            LockSupport.setCurrentBlocker(null);
        }
        return p;
    }

    /**
     * Scans for and returns a polled task, if available.  Used only
     * for untracked polls. Begins scan at a random index to avoid
     * systematic unfairness.
     *
     * @param submissionsOnly if true, only scan submission queues
     */
    private ForkJoinTask<?> pollScan(boolean submissionsOnly) {
        if ((runState & STOP) == 0) {
            WorkQueue[] qs; int n; WorkQueue q; ForkJoinTask<?> t;
            int r = ThreadLocalRandom.nextSecondarySeed();
            if (submissionsOnly)                 // even indices only
                r &= ~1;
            int step = (submissionsOnly) ? 2 : 1;
            if ((qs = queues) != null && (n = qs.length) > 0) {
                for (int i = n; i > 0; i -= step, r += step) {
                    if ((q = qs[r & (n - 1)]) != null &&
                        (t = q.poll()) != null)
                        return t;
                }
            }
        }
        return null;
    }

    /**
     * Tries to decrement counts (sometimes implicitly) and possibly
     * arrange for a compensating worker in preparation for
     * blocking. May fail due to interference, in which case -1 is
     * returned so caller may retry. A zero return value indicates
     * that the caller doesn't need to re-adjust counts when later
     * unblocked.
     *
     * @param c incoming ctl value
     * @return UNCOMPENSATE: block then adjust, 0: block, -1 : retry
     */
    private int tryCompensate(long c) {
        Predicate<? super ForkJoinPool> sat;
        long b = config;
        int pc        = parallelism,                    // unpack fields
            minActive = (short)(b >>> RC_SHIFT),
            maxTotal  = (short)(b >>> TC_SHIFT) + pc,
            active    = (short)(c >>> RC_SHIFT),
            total     = (short)(c >>> TC_SHIFT),
            sp        = (int)c,
            stat      = -1;                             // default retry return
        if (sp != 0 && active <= pc) {                  // activate idle worker
            WorkQueue[] qs; WorkQueue v; int i;
            if ((qs = queues) != null && qs.length > (i = sp & SMASK) &&
                (v = qs[i]) != null &&
                compareAndSetCtl(c, (c & UMASK) | (v.stackPred & LMASK))) {
                v.phase = sp;
                if (v.parking != 0)
                    U.unpark(v.owner);
                stat = UNCOMPENSATE;
            }
        }
        else if (active > minActive && total >= pc) {   // reduce active workers
            if (compareAndSetCtl(c, ((c - RC_UNIT) & RC_MASK) | (c & ~RC_MASK)))
                stat = UNCOMPENSATE;
        }
        else if (total < maxTotal && total < MAX_CAP) { // try to expand pool
            long nc = ((c + TC_UNIT) & TC_MASK) | (c & ~TC_MASK);
            if ((runState & STOP) != 0)                 // terminating
                stat = 0;
            else if (compareAndSetCtl(c, nc))
                stat = createWorker() ? UNCOMPENSATE : 0;
        }
        else if (!compareAndSetCtl(c, c))               // validate
            ;
        else if ((sat = saturate) != null && sat.test(this))
            stat = 0;
        else
            throw new RejectedExecutionException(
                "Thread limit exceeded replacing blocked worker");
        return stat;
    }

    /**
     * Readjusts RC count; called from ForkJoinTask after blocking.
     */
    final void uncompensate() {
        getAndAddCtl(RC_UNIT);
    }

    /**
     * Helps if possible until the given task is done.  Processes
     * compatible local tasks and scans other queues for task produced
     * by w's stealers; returning compensated blocking sentinel if
     * none are found.
     *
     * @param task the task
     * @param w caller's WorkQueue
     * @param internal true if w is owned by a ForkJoinWorkerThread
     * @return task status on exit, or UNCOMPENSATE for compensated blocking
     */

    final int helpJoin(ForkJoinTask<?> task, WorkQueue w, boolean internal) {
        if (w != null)
            w.tryRemoveAndExec(task, internal);
        int s = 0;
        if (task != null && (s = task.status) >= 0 && internal && w != null) {
            int wid = w.phase & SMASK, r = wid + 2, wsrc = w.source;
            long sctl = 0L;                             // track stability
            outer: for (boolean rescan = true;;) {
                if ((s = task.status) < 0)
                    break;
                if (!rescan) {
                    if ((runState & STOP) != 0)
                        break;
                    if (sctl == (sctl = ctl) && (s = tryCompensate(sctl)) >= 0)
                        break;
                }
                rescan = false;
                WorkQueue[] qs = queues;
                int n = (qs == null) ? 0 : qs.length;
                scan: for (int l = n >>> 1; l > 0; --l, r += 2) {
                    int j; WorkQueue q;
                    if ((q = qs[j = r & SMASK & (n - 1)]) != null) {
                        for (;;) {
                            int sq = q.source, b, cap, k; ForkJoinTask<?>[] a;
                            if ((a = q.array) == null || (cap = a.length) <= 0)
                                break;
                            ForkJoinTask<?> t = a[k = (b = q.base) & (cap - 1)];
                            U.loadFence();
                            boolean eligible = false;
                            if (t == task)
                                eligible = true;
                            else if (t != null) {       // check steal chain
                                for (int v = sq, d = cap;;) {
                                    WorkQueue p;
                                    if (v == wid) {
                                        eligible = true;
                                        break;
                                    }
                                    if ((v & 1) == 0 || // external or none
                                        --d < 0 ||      // bound depth
                                        (p = qs[v & (n - 1)]) == null)
                                        break;
                                    v = p.source;
                                }
                            }
                            if ((s = task.status) < 0)
                                break outer;            // validate
                            if (q.source == sq && q.base == b && a[k] == t) {
                                int nb = b + 1, nk = nb & (cap - 1);
                                if (!eligible) {        // revisit if nonempty
                                    if (!rescan && t == null &&
                                        (a[nk] != null || q.top - b > 0))
                                        rescan = true;
                                    break;
                                }
                                if (U.compareAndSetReference(
                                        a, slotOffset(k), t, null)) {
                                    q.updateBase(nb);
                                    w.source = j;
                                    t.doExec();
                                    w.source = wsrc;
                                    rescan = true;   // restart at index r
                                    break scan;
                                }
                            }
                        }
                    }
                }
            }
        }
        return s;
    }

    /**
     * Version of helpJoin for CountedCompleters.
     *
     * @param task root of computation (only called when a CountedCompleter)
     * @param w caller's WorkQueue
     * @param internal true if w is owned by a ForkJoinWorkerThread
     * @return task status on exit, or UNCOMPENSATE for compensated blocking
     */
    final int helpComplete(ForkJoinTask<?> task, WorkQueue w, boolean internal) {
        int s = 0;
        if (task != null && (s = task.status) >= 0 && w != null) {
            int r = w.phase + 1;                          // for indexing
            long sctl = 0L;                               // track stability
            outer: for (boolean rescan = true, locals = true;;) {
                if (locals && (s = w.helpComplete(task, internal, 0)) < 0)
                    break;
                if ((s = task.status) < 0)
                    break;
                if (!rescan) {
                    if ((runState & STOP) != 0)
                        break;
                    if (sctl == (sctl = ctl) &&
                        (!internal || (s = tryCompensate(sctl)) >= 0))
                        break;
                }
                rescan = locals = false;
                WorkQueue[] qs = queues;
                int n = (qs == null) ? 0 : qs.length;
                scan: for (int l = n; l > 0; --l, ++r) {
                    int j; WorkQueue q;
                    if ((q = qs[j = r & SMASK & (n - 1)]) != null) {
                        for (;;) {
                            ForkJoinTask<?>[] a; int b, cap, k;
                            if ((a = q.array) == null || (cap = a.length) <= 0)
                                break;
                            ForkJoinTask<?> t = a[k = (b = q.base) & (cap - 1)];
                            U.loadFence();
                            boolean eligible = false;
                            if (t instanceof CountedCompleter) {
                                CountedCompleter<?> f = (CountedCompleter<?>)t;
                                for (int steps = cap; steps > 0; --steps) {
                                    if (f == task) {
                                        eligible = true;
                                        break;
                                    }
                                    if ((f = f.completer) == null)
                                        break;
                                }
                            }
                            if ((s = task.status) < 0)    // validate
                                break outer;
                            if (q.base == b) {
                                int nb = b + 1, nk = nb & (cap - 1);
                                if (eligible) {
                                    if (U.compareAndSetReference(
                                            a, slotOffset(k), t, null)) {
                                        q.updateBase(nb);
                                        t.doExec();
                                        locals = rescan = true;
                                        break scan;
                                    }
                                }
                                else if (a[k] == t) {
                                    if (!rescan && t == null &&
                                        (a[nk] != null || q.top - b > 0))
                                        rescan = true;    // revisit
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        return s;
     }

    /**
     * Runs tasks until all workers are inactive and no tasks are
     * found. Rather than blocking when tasks cannot be found, rescans
     * until all others cannot find tasks either.
     *
     * @param nanos max wait time (Long.MAX_VALUE if effectively untimed)
     * @param interruptible true if return on interrupt
     * @return positive if quiescent, negative if interrupted, else 0
     */
    private int helpQuiesce(WorkQueue w, long nanos, boolean interruptible) {
        int phase; // w.phase inactive bit set when temporarily quiescent
        if (w == null || ((phase = w.phase) & IDLE) != 0)
            return 0;
        int wsrc = w.source;
        long startTime = System.nanoTime();
        long maxSleep = Math.min(nanos >>> 8, MAX_SLEEP); // approx 1% nanos
        long prevSum = 0L;
        int activePhase = phase, inactivePhase = phase + IDLE;
        int r = phase + 1, waits = 0, returnStatus = 1;
        boolean locals = true;
        for (int e = runState;;) {
            if ((e & STOP) != 0)
                break;                      // terminating
            if (interruptible && Thread.interrupted()) {
                returnStatus = -1;
                break;
            }
            if (locals) {                   // run local tasks before (re)polling
                locals = false;
                for (ForkJoinTask<?> u; (u = w.nextLocalTask()) != null;)
                    u.doExec();
            }
            WorkQueue[] qs = queues;
            int n = (qs == null) ? 0 : qs.length;
            long phaseSum = 0L;
            boolean rescan = false, busy = false;
            scan: for (int l = n; l > 0; --l, ++r) {
                int j; WorkQueue q;
                if ((q = qs[j = r & SMASK & (n - 1)]) != null && q != w) {
                    for (;;) {
                        ForkJoinTask<?>[] a; int b, cap, k;
                        if ((a = q.array) == null || (cap = a.length) <= 0)
                            break;
                        ForkJoinTask<?> t = a[k = (b = q.base) & (cap - 1)];
                        if (t != null && phase == inactivePhase) // reactivate
                            w.phase = phase = activePhase;
                        U.loadFence();
                        if (q.base == b && a[k] == t) {
                            int nb = b + 1;
                            if (t == null) {
                                if (!rescan) {
                                    int qp = q.phase, mq = qp & (IDLE | 1);
                                    phaseSum += qp;
                                    if (mq == 0 || q.top - b > 0)
                                        rescan = true;
                                    else if (mq == 1)
                                        busy = true;
                                }
                                break;
                            }
                            if (U.compareAndSetReference(
                                    a, slotOffset(k), t, null)) {
                                q.updateBase(nb);
                                w.source = j;
                                t.doExec();
                                w.source = wsrc;
                                rescan = locals = true;
                                break scan;
                            }
                        }
                    }
                }
            }
            if (e != (e = runState) || prevSum != (prevSum = phaseSum) ||
                rescan || (e & RS_LOCK) != 0)
                ;                   // inconsistent
            else if (!busy)
                break;
            else if (phase == activePhase) {
                waits = 0;          // recheck, then sleep
                w.phase = phase = inactivePhase;
            }
            else if (System.nanoTime() - startTime > nanos) {
                returnStatus = 0;   // timed out
                break;
            }
            else if (waits == 0)   // same as spinLockRunState except
                waits = MIN_SLEEP; //   with rescan instead of onSpinWait
            else {
                LockSupport.parkNanos(this, (long)waits);
                if (waits < maxSleep)
                    waits <<= 1;
            }
        }
        w.phase = activePhase;
        return returnStatus;
    }

    /**
     * Helps quiesce from external caller until done, interrupted, or timeout
     *
     * @param nanos max wait time (Long.MAX_VALUE if effectively untimed)
     * @param interruptible true if return on interrupt
     * @return positive if quiescent, negative if interrupted, else 0
     */
    private int externalHelpQuiesce(long nanos, boolean interruptible) {
        if (quiescent() < 0) {
            long startTime = System.nanoTime();
            long maxSleep = Math.min(nanos >>> 8, MAX_SLEEP);
            for (int waits = 0;;) {
                ForkJoinTask<?> t;
                if (interruptible && Thread.interrupted())
                    return -1;
                else if ((t = pollScan(false)) != null) {
                    waits = 0;
                    t.doExec();
                }
                else if (quiescent() >= 0)
                    break;
                else if (System.nanoTime() - startTime > nanos)
                    return 0;
                else if (waits == 0)
                    waits = MIN_SLEEP;
                else {
                    LockSupport.parkNanos(this, (long)waits);
                    if (waits < maxSleep)
                        waits <<= 1;
                }
            }
        }
        return 1;
    }

    /**
     * Helps quiesce from either internal or external caller
     *
     * @param pool the pool to use, or null if any
     * @param nanos max wait time (Long.MAX_VALUE if effectively untimed)
     * @param interruptible true if return on interrupt
     * @return positive if quiescent, negative if interrupted, else 0
     */
    static final int helpQuiescePool(ForkJoinPool pool, long nanos,
                                     boolean interruptible) {
        Thread t; ForkJoinPool p; ForkJoinWorkerThread wt;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread &&
            (p = (wt = (ForkJoinWorkerThread)t).pool) != null &&
            (p == pool || pool == null))
            return p.helpQuiesce(wt.workQueue, nanos, interruptible);
        else if ((p = pool) != null || (p = common) != null)
            return p.externalHelpQuiesce(nanos, interruptible);
        else
            return 0;
    }

    /**
     * Gets and removes a local or stolen task for the given worker.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> nextTaskFor(WorkQueue w) {
        ForkJoinTask<?> t;
        if (w == null || (t = w.nextLocalTask()) == null)
            t = pollScan(false);
        return t;
    }

    // External operations

    /**
     * Finds and locks a WorkQueue for an external submitter, or
     * throws RejectedExecutionException if shutdown or terminating.
     * @param r current ThreadLocalRandom.getProbe() value
     * @param isSubmit false if this is for a common pool fork
     */
    private WorkQueue submissionQueue(int r) {
        if (r == 0) {
            ThreadLocalRandom.localInit();           // initialize caller's probe
            r = ThreadLocalRandom.getProbe();
        }
        for (;;) {
            int n, i, id; WorkQueue[] qs; WorkQueue q;
            if ((qs = queues) == null)
                break;
            if ((n = qs.length) <= 0)
                break;
            if ((q = qs[i = (id = r & EXTERNAL_ID_MASK) & (n - 1)]) == null) {
                WorkQueue w = new WorkQueue(null, id, 0, false);
                w.array = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];
                int stop = lockRunState() & STOP;
                if (stop == 0 && queues == qs && qs[i] == null)
                    q = qs[i] = w;                   // else discard; retry
                unlockRunState();
                if (q != null)
                    return q;
                if (stop != 0)
                    break;
            }
            else if (!q.tryLockPhase())              // move index
                r = ThreadLocalRandom.advanceProbe(r);
            else if ((runState & SHUTDOWN) != 0) {
                q.unlockPhase();                     // check while q lock held
                break;
            }
            else
                return q;
        }
        tryTerminate(false, false);
        throw new RejectedExecutionException();
    }

    private void poolSubmit(boolean signalIfEmpty, ForkJoinTask<?> task) {
        Thread t; ForkJoinWorkerThread wt; WorkQueue q; boolean internal;
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) &&
            (wt = (ForkJoinWorkerThread)t).pool == this) {
            internal = true;
            q = wt.workQueue;
        }
        else {                     // find and lock queue
            internal = false;
            q = submissionQueue(ThreadLocalRandom.getProbe());
        }
        q.push(task, signalIfEmpty ? this : null, internal);
    }

    /**
     * Returns queue for an external submission, bypassing call to
     * submissionQueue if already established and unlocked.
     */
    final WorkQueue externalSubmissionQueue() {
        WorkQueue[] qs; WorkQueue q; int n;
        int r = ThreadLocalRandom.getProbe();
        return (((qs = queues) != null && (n = qs.length) > 0 &&
                 (q = qs[r & EXTERNAL_ID_MASK & (n - 1)]) != null && r != 0 &&
                 q.tryLockPhase()) ? q : submissionQueue(r));
    }

    /**
     * Returns queue for an external thread, if one exists that has
     * possibly ever submitted to the given pool (nonzero probe), or
     * null if none.
     */
    static WorkQueue externalQueue(ForkJoinPool p) {
        WorkQueue[] qs; int n;
        int r = ThreadLocalRandom.getProbe();
        return (p != null && (qs = p.queues) != null &&
                (n = qs.length) > 0 && r != 0) ?
            qs[r & EXTERNAL_ID_MASK & (n - 1)] : null;
    }

    /**
     * Returns external queue for common pool.
     */
    static WorkQueue commonQueue() {
        return externalQueue(common);
    }

    /**
     * If the given executor is a ForkJoinPool, poll and execute
     * AsynchronousCompletionTasks from worker's queue until none are
     * available or blocker is released.
     */
    static void helpAsyncBlocker(Executor e, ManagedBlocker blocker) {
        WorkQueue w = null; Thread t; ForkJoinWorkerThread wt;
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) &&
            (wt = (ForkJoinWorkerThread)t).pool == e)
            w = wt.workQueue;
        else if (e instanceof ForkJoinPool)
            w = externalQueue((ForkJoinPool)e);
        if (w != null)
            w.helpAsyncBlocker(blocker);
    }

    /**
     * Returns a cheap heuristic guide for task partitioning when
     * programmers, frameworks, tools, or languages have little or no
     * idea about task granularity.  In essence, by offering this
     * method, we ask users only about tradeoffs in overhead vs
     * expected throughput and its variance, rather than how finely to
     * partition tasks.
     *
     * In a steady state strict (tree-structured) computation, each
     * thread makes available for stealing enough tasks for other
     * threads to remain active. Inductively, if all threads play by
     * the same rules, each thread should make available only a
     * constant number of tasks.
     *
     * The minimum useful constant is just 1. But using a value of 1
     * would require immediate replenishment upon each steal to
     * maintain enough tasks, which is infeasible.  Further,
     * partitionings/granularities of offered tasks should minimize
     * steal rates, which in general means that threads nearer the top
     * of computation tree should generate more than those nearer the
     * bottom. In perfect steady state, each thread is at
     * approximately the same level of computation tree. However,
     * producing extra tasks amortizes the uncertainty of progress and
     * diffusion assumptions.
     *
     * So, users will want to use values larger (but not much larger)
     * than 1 to both smooth over transient shortages and hedge
     * against uneven progress; as traded off against the cost of
     * extra task overhead. We leave the user to pick a threshold
     * value to compare with the results of this call to guide
     * decisions, but recommend values such as 3.
     *
     * When all threads are active, it is on average OK to estimate
     * surplus strictly locally. In steady-state, if one thread is
     * maintaining say 2 surplus tasks, then so are others. So we can
     * just use estimated queue length.  However, this strategy alone
     * leads to serious mis-estimates in some non-steady-state
     * conditions (ramp-up, ramp-down, other stalls). We can detect
     * many of these by further considering the number of "idle"
     * threads, that are known to have zero queued tasks, so
     * compensate by a factor of (#idle/#active) threads.
     */
    static int getSurplusQueuedTaskCount() {
        Thread t; ForkJoinWorkerThread wt; ForkJoinPool pool; WorkQueue q;
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) &&
            (pool = (wt = (ForkJoinWorkerThread)t).pool) != null &&
            (q = wt.workQueue) != null) {
            int n = q.top - q.base;
            int p = pool.parallelism;
            int a = (short)(pool.ctl >>> RC_SHIFT);
            return n - (a > (p >>>= 1) ? 0 :
                        a > (p >>>= 1) ? 1 :
                        a > (p >>>= 1) ? 2 :
                        a > (p >>>= 1) ? 4 :
                        8);
        }
        return 0;
    }

    // Termination

    /**
     * Possibly initiates and/or completes pool termination.
     *
     * @param now if true, unconditionally terminate, else only
     * if no work and no active workers
     * @param enable if true, terminate when next possible
     * @return runState on exit
     */
    private int tryTerminate(boolean now, boolean enable) {
        int e = runState, isShutdown;
        if ((e & STOP) == 0) {
            if (now) {
                runState = e = (lockRunState() + RS_LOCK) | STOP | SHUTDOWN;
                releaseAll();
            }
            else if ((isShutdown = (e & SHUTDOWN)) != 0 || enable) {
                if (isShutdown == 0)
                    getAndBitwiseOrRunState(SHUTDOWN);
                if (quiescent() > 0)
                    e = runState;
            }
        }
        if ((e & (STOP | TERMINATED)) == STOP) {
            if ((ctl & RC_MASK) > 0L) {         // avoid if quiescent shutdown
                helpTerminate(now);
                e = runState;
            }
            if ((e & TERMINATED) == 0 && ctl == 0L) {
                e |= TERMINATED;
                if ((getAndBitwiseOrRunState(TERMINATED) & TERMINATED) == 0) {
                    CountDownLatch done; SharedThreadContainer ctr;
                    if ((done = termination) != null)
                        done.countDown();
                    if ((ctr = container) != null)
                        ctr.close();
                }
            }
        }
        return e;
    }

    /**
     * Cancels tasks and interrupts workers
     */
    private void helpTerminate(boolean now) {
        Thread current = Thread.currentThread();
        int r = (int)current.threadId();   // stagger traversals
        WorkQueue[] qs = queues;
        int n = (qs == null) ? 0 : qs.length;
        for (int l = n; l > 0; --l, ++r) {
            WorkQueue q; ForkJoinTask<?> t; Thread o;
            int j = r & SMASK & (n - 1);
            if ((q = qs[j]) != null && q.source != DEREGISTERED) {
                while ((t = q.poll()) != null) {
                    try {
                        t.cancel(false);
                    } catch (Throwable ignore) {
                    }
                }
                if ((r & 1) != 0 && (o = q.owner) != null &&
                    o != current && q.source != DEREGISTERED &&
                    (now || !o.isInterrupted())) {
                    try {
                        o.interrupt();
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
    }

    /**
     * Returns termination signal, constructing if necessary
     */
    private CountDownLatch terminationSignal() {
        CountDownLatch signal, s, u;
        if ((signal = termination) == null)
            signal = ((u = cmpExTerminationSignal(
                           s = new CountDownLatch(1))) == null) ? s : u;
        return signal;
    }

    // Exported methods

    // Constructors

    /**
     * Creates a {@code ForkJoinPool} with parallelism equal to {@link
     * java.lang.Runtime#availableProcessors}, using defaults for all
     * other parameters (see {@link #ForkJoinPool(int,
     * ForkJoinWorkerThreadFactory, UncaughtExceptionHandler, boolean,
     * int, int, int, Predicate, long, TimeUnit)}).
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool() {
        this(Math.min(MAX_CAP, Runtime.getRuntime().availableProcessors()),
             defaultForkJoinWorkerThreadFactory, null, false,
             0, MAX_CAP, 1, null, DEFAULT_KEEPALIVE, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a {@code ForkJoinPool} with the indicated parallelism
     * level, using defaults for all other parameters (see {@link
     * #ForkJoinPool(int, ForkJoinWorkerThreadFactory,
     * UncaughtExceptionHandler, boolean, int, int, int, Predicate,
     * long, TimeUnit)}).
     *
     * @param parallelism the parallelism level
     * @throws IllegalArgumentException if parallelism less than or
     *         equal to zero, or greater than implementation limit
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool(int parallelism) {
        this(parallelism, defaultForkJoinWorkerThreadFactory, null, false,
             0, MAX_CAP, 1, null, DEFAULT_KEEPALIVE, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parameters (using
     * defaults for others -- see {@link #ForkJoinPool(int,
     * ForkJoinWorkerThreadFactory, UncaughtExceptionHandler, boolean,
     * int, int, int, Predicate, long, TimeUnit)}).
     *
     * @param parallelism the parallelism level. For default value,
     * use {@link java.lang.Runtime#availableProcessors}.
     * @param factory the factory for creating new threads. For default value,
     * use {@link #defaultForkJoinWorkerThreadFactory}.
     * @param handler the handler for internal worker threads that
     * terminate due to unrecoverable errors encountered while executing
     * tasks. For default value, use {@code null}.
     * @param asyncMode if true,
     * establishes local first-in-first-out scheduling mode for forked
     * tasks that are never joined. This mode may be more appropriate
     * than default locally stack-based mode in applications in which
     * worker threads only process event-style asynchronous tasks.
     * For default value, use {@code false}.
     * @throws IllegalArgumentException if parallelism less than or
     *         equal to zero, or greater than implementation limit
     * @throws NullPointerException if the factory is null
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool(int parallelism,
                        ForkJoinWorkerThreadFactory factory,
                        UncaughtExceptionHandler handler,
                        boolean asyncMode) {
        this(parallelism, factory, handler, asyncMode,
             0, MAX_CAP, 1, null, DEFAULT_KEEPALIVE, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parameters.
     *
     * @param parallelism the parallelism level. For default value,
     * use {@link java.lang.Runtime#availableProcessors}.
     *
     * @param factory the factory for creating new threads. For
     * default value, use {@link #defaultForkJoinWorkerThreadFactory}.
     *
     * @param handler the handler for internal worker threads that
     * terminate due to unrecoverable errors encountered while
     * executing tasks. For default value, use {@code null}.
     *
     * @param asyncMode if true, establishes local first-in-first-out
     * scheduling mode for forked tasks that are never joined. This
     * mode may be more appropriate than default locally stack-based
     * mode in applications in which worker threads only process
     * event-style asynchronous tasks.  For default value, use {@code
     * false}.
     *
     * @param corePoolSize the number of threads to keep in the pool
     * (unless timed out after an elapsed keep-alive). Normally (and
     * by default) this is the same value as the parallelism level,
     * but may be set to a larger value to reduce dynamic overhead if
     * tasks regularly block. Using a smaller value (for example
     * {@code 0}) has the same effect as the default.
     *
     * @param maximumPoolSize the maximum number of threads allowed.
     * When the maximum is reached, attempts to replace blocked
     * threads fail.  (However, because creation and termination of
     * different threads may overlap, and may be managed by the given
     * thread factory, this value may be transiently exceeded.)  To
     * arrange the same value as is used by default for the common
     * pool, use {@code 256} plus the {@code parallelism} level. (By
     * default, the common pool allows a maximum of 256 spare
     * threads.)  Using a value (for example {@code
     * Integer.MAX_VALUE}) larger than the implementation's total
     * thread limit has the same effect as using this limit (which is
     * the default).
     *
     * @param minimumRunnable the minimum allowed number of core
     * threads not blocked by a join or {@link ManagedBlocker}.  To
     * ensure progress, when too few unblocked threads exist and
     * unexecuted tasks may exist, new threads are constructed, up to
     * the given maximumPoolSize.  For the default value, use {@code
     * 1}, that ensures liveness.  A larger value might improve
     * throughput in the presence of blocked activities, but might
     * not, due to increased overhead.  A value of zero may be
     * acceptable when submitted tasks cannot have dependencies
     * requiring additional threads.
     *
     * @param saturate if non-null, a predicate invoked upon attempts
     * to create more than the maximum total allowed threads.  By
     * default, when a thread is about to block on a join or {@link
     * ManagedBlocker}, but cannot be replaced because the
     * maximumPoolSize would be exceeded, a {@link
     * RejectedExecutionException} is thrown.  But if this predicate
     * returns {@code true}, then no exception is thrown, so the pool
     * continues to operate with fewer than the target number of
     * runnable threads, which might not ensure progress.
     *
     * @param keepAliveTime the elapsed time since last use before
     * a thread is terminated (and then later replaced if needed).
     * For the default value, use {@code 60, TimeUnit.SECONDS}.
     *
     * @param unit the time unit for the {@code keepAliveTime} argument
     *
     * @throws IllegalArgumentException if parallelism is less than or
     *         equal to zero, or is greater than implementation limit,
     *         or if maximumPoolSize is less than parallelism,
     *         of if the keepAliveTime is less than or equal to zero.
     * @throws NullPointerException if the factory is null
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     * @since 9
     */
    public ForkJoinPool(int parallelism,
                        ForkJoinWorkerThreadFactory factory,
                        UncaughtExceptionHandler handler,
                        boolean asyncMode,
                        int corePoolSize,
                        int maximumPoolSize,
                        int minimumRunnable,
                        Predicate<? super ForkJoinPool> saturate,
                        long keepAliveTime,
                        TimeUnit unit) {
        checkPermission();
        int p = parallelism;
        if (p <= 0 || p > MAX_CAP || p > maximumPoolSize || keepAliveTime <= 0L)
            throw new IllegalArgumentException();
        if (factory == null || unit == null)
            throw new NullPointerException();
        int size = 1 << (33 - Integer.numberOfLeadingZeros(p - 1));
        this.parallelism = p;
        this.factory = factory;
        this.ueh = handler;
        this.saturate = saturate;
        this.keepAlive = Math.max(unit.toMillis(keepAliveTime), TIMEOUT_SLOP);
        int maxSpares = Math.clamp(maximumPoolSize - p, 0, MAX_CAP);
        int minAvail = Math.clamp(minimumRunnable, 0, MAX_CAP);
        this.config = (((asyncMode ? FIFO : 0) & LMASK) |
                       (((long)maxSpares) << TC_SHIFT) |
                       (((long)minAvail)  << RC_SHIFT));
        this.queues = new WorkQueue[size];
        String pid = Integer.toString(getAndAddPoolIds(1) + 1);
        String name = "ForkJoinPool-" + pid;
        this.workerNamePrefix = name + "-worker-";
        this.container = SharedThreadContainer.create(name);
    }

    /**
     * Constructor for common pool using parameters possibly
     * overridden by system properties
     */
    private ForkJoinPool(byte forCommonPoolOnly) {
        ForkJoinWorkerThreadFactory fac = defaultForkJoinWorkerThreadFactory;
        UncaughtExceptionHandler handler = null;
        int maxSpares = DEFAULT_COMMON_MAX_SPARES;
        int pc = 0, preset = 0; // nonzero if size set as property
        try {  // ignore exceptions in accessing/parsing properties
            String pp = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.parallelism");
            if (pp != null) {
                pc = Math.max(0, Integer.parseInt(pp));
                preset = PRESET_SIZE;
            }
            String ms = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.maximumSpares");
            if (ms != null)
                maxSpares = Math.clamp(Integer.parseInt(ms), 0, MAX_CAP);
            String sf = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.threadFactory");
            String sh = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.exceptionHandler");
            if (sf != null || sh != null) {
                ClassLoader ldr = ClassLoader.getSystemClassLoader();
                if (sf != null)
                    fac = (ForkJoinWorkerThreadFactory)
                        ldr.loadClass(sf).getConstructor().newInstance();
                if (sh != null)
                    handler = (UncaughtExceptionHandler)
                        ldr.loadClass(sh).getConstructor().newInstance();
            }
        } catch (Exception ignore) {
        }
        if (preset == 0)
            pc = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        int p = Math.min(pc, MAX_CAP);
        int size = (p == 0) ? 1 : 1 << (33 - Integer.numberOfLeadingZeros(p-1));
        this.parallelism = p;
        this.config = ((preset & LMASK) | (((long)maxSpares) << TC_SHIFT) |
                       (1L << RC_SHIFT));
        this.factory = fac;
        this.ueh = handler;
        this.keepAlive = DEFAULT_KEEPALIVE;
        this.saturate = null;
        this.workerNamePrefix = null;
        this.queues = new WorkQueue[size];
        this.container = SharedThreadContainer.create("ForkJoinPool.commonPool");
    }

    /**
     * Returns the common pool instance. This pool is statically
     * constructed; its run state is unaffected by attempts to {@link
     * #shutdown} or {@link #shutdownNow}. However this pool and any
     * ongoing processing are automatically terminated upon program
     * {@link System#exit}.  Any program that relies on asynchronous
     * task processing to complete before program termination should
     * invoke {@code commonPool().}{@link #awaitQuiescence awaitQuiescence},
     * before exit.
     *
     * @return the common pool instance
     * @since 1.8
     */
    public static ForkJoinPool commonPool() {
        // assert common != null : "static init error";
        return common;
    }

    // Execution methods

    /**
     * Performs the given task, returning its result upon completion.
     * If the computation encounters an unchecked Exception or Error,
     * it is rethrown as the outcome of this invocation.  Rethrown
     * exceptions behave in the same way as regular exceptions, but,
     * when possible, contain stack traces (as displayed for example
     * using {@code ex.printStackTrace()}) of both the current thread
     * as well as the thread actually encountering the exception;
     * minimally only the latter.
     *
     * @param task the task
     * @param <T> the type of the task's result
     * @return the task's result
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> T invoke(ForkJoinTask<T> task) {
        Objects.requireNonNull(task);
        poolSubmit(true, task);
        try {
            return task.join();
        } catch (RuntimeException | Error unchecked) {
            throw unchecked;
        } catch (Exception checked) {
            throw new RuntimeException(checked);
        }
    }

    /**
     * Arranges for (asynchronous) execution of the given task.
     *
     * @param task the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public void execute(ForkJoinTask<?> task) {
        Objects.requireNonNull(task);
        poolSubmit(true, task);
    }

    // AbstractExecutorService methods

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    @SuppressWarnings("unchecked")
    public void execute(Runnable task) {
        poolSubmit(true, (task instanceof ForkJoinTask<?>)
                   ? (ForkJoinTask<Void>) task // avoid re-wrap
                   : new ForkJoinTask.RunnableExecuteAction(task));
    }

    /**
     * Submits a ForkJoinTask for execution.
     *
     * @implSpec
     * This method is equivalent to {@link #externalSubmit(ForkJoinTask)}
     * when called from a thread that is not in this pool.
     *
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @return the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        Objects.requireNonNull(task);
        poolSubmit(true, task);
        return task;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        ForkJoinTask<T> t =
            (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            new ForkJoinTask.AdaptedCallable<T>(task) :
            new ForkJoinTask.AdaptedInterruptibleCallable<T>(task);
        poolSubmit(true, t);
        return t;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        ForkJoinTask<T> t =
            (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            new ForkJoinTask.AdaptedRunnable<T>(task, result) :
            new ForkJoinTask.AdaptedInterruptibleRunnable<T>(task, result);
        poolSubmit(true, t);
        return t;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    @SuppressWarnings("unchecked")
    public ForkJoinTask<?> submit(Runnable task) {
        ForkJoinTask<?> f = (task instanceof ForkJoinTask<?>) ?
            (ForkJoinTask<Void>) task : // avoid re-wrap
            ((Thread.currentThread() instanceof ForkJoinWorkerThread) ?
             new ForkJoinTask.AdaptedRunnable<Void>(task, null) :
             new ForkJoinTask.AdaptedInterruptibleRunnable<Void>(task, null));
        poolSubmit(true, f);
        return f;
    }

    /**
     * Submits the given task as if submitted from a non-{@code ForkJoinTask}
     * client. The task is added to a scheduling queue for submissions to the
     * pool even when called from a thread in the pool.
     *
     * @implSpec
     * This method is equivalent to {@link #submit(ForkJoinTask)} when called
     * from a thread that is not in this pool.
     *
     * @return the task
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @since 20
     */
    public <T> ForkJoinTask<T> externalSubmit(ForkJoinTask<T> task) {
        Objects.requireNonNull(task);
        externalSubmissionQueue().push(task, this, false);
        return task;
    }

    /**
     * Submits the given task without guaranteeing that it will
     * eventually execute in the absence of available active threads.
     * In some contexts, this method may reduce contention and
     * overhead by relying on context-specific knowledge that existing
     * threads (possibly including the calling thread if operating in
     * this pool) will eventually be available to execute the task.
     *
     * @param task the task
     * @param <T> the type of the task's result
     * @return the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @since 19
     */
    public <T> ForkJoinTask<T> lazySubmit(ForkJoinTask<T> task) {
        Objects.requireNonNull(task);
        poolSubmit(false, task);
        return task;
    }

    /**
     * Changes the target parallelism of this pool, controlling the
     * future creation, use, and termination of worker threads.
     * Applications include contexts in which the number of available
     * processors changes over time.
     *
     * @implNote This implementation restricts the maximum number of
     * running threads to 32767
     *
     * @param size the target parallelism level
     * @return the previous parallelism level.
     * @throws IllegalArgumentException if size is less than 1 or
     *         greater than the maximum supported by this pool.
     * @throws UnsupportedOperationException this is the{@link
     *         #commonPool()} and parallelism level was set by System
     *         property {@systemProperty
     *         java.util.concurrent.ForkJoinPool.common.parallelism}.
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     * @since 19
     */
    public int setParallelism(int size) {
        if (size < 1 || size > MAX_CAP)
            throw new IllegalArgumentException();
        if ((config & PRESET_SIZE) != 0)
            throw new UnsupportedOperationException("Cannot override System property");
        checkPermission();
        return getAndSetParallelism(size);
    }

    /**
     * Uninterrupible version of {@code invokeAll}. Executes the given
     * tasks, returning a list of Futures holding their status and
     * results when all complete, ignoring interrupts.  {@link
     * Future#isDone} is {@code true} for each element of the returned
     * list.  Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.  The
     * results of this method are undefined if the given collection is
     * modified while this operation is in progress.
     *
     * @apiNote This method supports usages that previously relied on an
     * incompatible override of
     * {@link ExecutorService#invokeAll(java.util.Collection)}.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same
     *         sequential order as produced by the iterator for the
     *         given task list, each of which has completed
     * @throws NullPointerException if tasks or any of its elements are {@code null}
     * @throws RejectedExecutionException if any task cannot be
     *         scheduled for execution
     * @since 22
     */
    public <T> List<Future<T>> invokeAllUninterruptibly(Collection<? extends Callable<T>> tasks) {
        ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f = ForkJoinTask.adapt(t);
                futures.add(f);
                poolSubmit(true, f);
            }
            for (int i = futures.size() - 1; i >= 0; --i)
                ((ForkJoinTask<?>)futures.get(i)).quietlyJoin();
            return futures;
        } catch (Throwable t) {
            for (Future<T> e : futures)
                e.cancel(true);
            throw t;
        }
    }

    /**
     * Common support for timed and untimed invokeAll
     */
    private  <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                           long deadline)
        throws InterruptedException {
        ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f = ForkJoinTask.adaptInterruptible(t);
                futures.add(f);
                poolSubmit(true, f);
            }
            for (int i = futures.size() - 1; i >= 0; --i)
                ((ForkJoinTask<?>)futures.get(i))
                    .quietlyJoinPoolInvokeAllTask(deadline);
            return futures;
        } catch (Throwable t) {
            for (Future<T> e : futures)
                e.cancel(true);
            throw t;
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
        return invokeAll(tasks, 0L);
    }
    // for jdk version < 22, replace with
    // /**
    //  * @throws NullPointerException       {@inheritDoc}
    //  * @throws RejectedExecutionException {@inheritDoc}
    //  */
    // @Override
    // public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
    //     return invokeAllUninterruptibly(tasks);
    // }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
        throws InterruptedException {
        return invokeAll(tasks, (System.nanoTime() + unit.toNanos(timeout)) | 1L);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
        try {
            return new ForkJoinTask.InvokeAnyRoot<T>()
                .invokeAny(tasks, this, false, 0L);
        } catch (TimeoutException cannotHappen) {
            assert false;
            return null;
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return new ForkJoinTask.InvokeAnyRoot<T>()
            .invokeAny(tasks, this, true, unit.toNanos(timeout));
    }

    /**
     * Returns the factory used for constructing new workers.
     *
     * @return the factory used for constructing new workers
     */
    public ForkJoinWorkerThreadFactory getFactory() {
        return factory;
    }

    /**
     * Returns the handler for internal worker threads that terminate
     * due to unrecoverable errors encountered while executing tasks.
     *
     * @return the handler, or {@code null} if none
     */
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return ueh;
    }

    /**
     * Returns the targeted parallelism level of this pool.
     *
     * @return the targeted parallelism level of this pool
     */
    public int getParallelism() {
        return Math.max(getParallelismOpaque(), 1);
    }

    /**
     * Returns the targeted parallelism level of the common pool.
     *
     * @return the targeted parallelism level of the common pool
     * @since 1.8
     */
    public static int getCommonPoolParallelism() {
        return common.getParallelism();
    }

    /**
     * Returns the number of worker threads that have started but not
     * yet terminated.  The result returned by this method may differ
     * from {@link #getParallelism} when threads are created to
     * maintain parallelism when others are cooperatively blocked.
     *
     * @return the number of worker threads
     */
    public int getPoolSize() {
        return (short)(ctl >>> TC_SHIFT);
    }

    /**
     * Returns {@code true} if this pool uses local first-in-first-out
     * scheduling mode for forked tasks that are never joined.
     *
     * @return {@code true} if this pool uses async mode
     */
    public boolean getAsyncMode() {
        return (config & FIFO) != 0;
    }

    /**
     * Returns an estimate of the number of worker threads that are
     * not blocked waiting to join tasks or for other managed
     * synchronization. This method may overestimate the
     * number of running threads.
     *
     * @return the number of worker threads
     */
    public int getRunningThreadCount() {
        WorkQueue[] qs; WorkQueue q;
        int rc = 0;
        if ((runState & TERMINATED) == 0 && (qs = queues) != null) {
            for (int i = 1; i < qs.length; i += 2) {
                if ((q = qs[i]) != null && q.isApparentlyUnblocked())
                    ++rc;
            }
        }
        return rc;
    }

    /**
     * Returns an estimate of the number of threads that are currently
     * stealing or executing tasks. This method may overestimate the
     * number of active threads.
     *
     * @return the number of active threads
     */
    public int getActiveThreadCount() {
        return Math.max((short)(ctl >>> RC_SHIFT), 0);
    }

    /**
     * Returns {@code true} if all worker threads are currently idle.
     * An idle worker is one that cannot obtain a task to execute
     * because none are available to steal from other threads, and
     * there are no pending submissions to the pool. This method is
     * conservative; it might not return {@code true} immediately upon
     * idleness of all threads, but will eventually become true if
     * threads remain inactive.
     *
     * @return {@code true} if all threads are currently idle
     */
    public boolean isQuiescent() {
        return quiescent() >= 0;
    }

    /**
     * Returns an estimate of the total number of completed tasks that
     * were executed by a thread other than their submitter. The
     * reported value underestimates the actual total number of steals
     * when the pool is not quiescent. This value may be useful for
     * monitoring and tuning fork/join programs: in general, steal
     * counts should be high enough to keep threads busy, but low
     * enough to avoid overhead and contention across threads.
     *
     * @return the number of steals
     */
    public long getStealCount() {
        long count = stealCount;
        WorkQueue[] qs; WorkQueue q;
        if ((qs = queues) != null) {
            for (int i = 1; i < qs.length; i += 2) {
                if ((q = qs[i]) != null)
                     count += (long)q.nsteals & 0xffffffffL;
            }
        }
        return count;
    }

    /**
     * Returns an estimate of the total number of tasks currently held
     * in queues by worker threads (but not including tasks submitted
     * to the pool that have not begun executing). This value is only
     * an approximation, obtained by iterating across all threads in
     * the pool. This method may be useful for tuning task
     * granularities.
     *
     * @return the number of queued tasks
     * @see ForkJoinWorkerThread#getQueuedTaskCount()
     */
    public long getQueuedTaskCount() {
        WorkQueue[] qs; WorkQueue q;
        int count = 0;
        if ((runState & TERMINATED) == 0 && (qs = queues) != null) {
            for (int i = 1; i < qs.length; i += 2) {
                if ((q = qs[i]) != null)
                    count += q.queueSize();
            }
        }
        return count;
    }

    /**
     * Returns an estimate of the number of tasks submitted to this
     * pool that have not yet begun executing.  This method may take
     * time proportional to the number of submissions.
     *
     * @return the number of queued submissions
     */
    public int getQueuedSubmissionCount() {
        WorkQueue[] qs; WorkQueue q;
        int count = 0;
        if ((runState & TERMINATED) == 0 && (qs = queues) != null) {
            for (int i = 0; i < qs.length; i += 2) {
                if ((q = qs[i]) != null)
                    count += q.queueSize();
            }
        }
        return count;
    }

    /**
     * Returns {@code true} if there are any tasks submitted to this
     * pool that have not yet begun executing.
     *
     * @return {@code true} if there are any queued submissions
     */
    public boolean hasQueuedSubmissions() {
        WorkQueue[] qs; WorkQueue q;
        if ((runState & STOP) == 0 && (qs = queues) != null) {
            for (int i = 0; i < qs.length; i += 2) {
                if ((q = qs[i]) != null && q.queueSize() > 0)
                    return true;
            }
        }
        return false;
    }

    /**
     * Removes and returns the next unexecuted submission if one is
     * available.  This method may be useful in extensions to this
     * class that re-assign work in systems with multiple pools.
     *
     * @return the next submission, or {@code null} if none
     */
    protected ForkJoinTask<?> pollSubmission() {
        return pollScan(true);
    }

    /**
     * Removes all available unexecuted submitted and forked tasks
     * from scheduling queues and adds them to the given collection,
     * without altering their execution status. These may include
     * artificially generated or wrapped tasks. This method is
     * designed to be invoked only when the pool is known to be
     * quiescent. Invocations at other times may not remove all
     * tasks. A failure encountered while attempting to add elements
     * to collection {@code c} may result in elements being in
     * neither, either or both collections when the associated
     * exception is thrown.  The behavior of this operation is
     * undefined if the specified collection is modified while the
     * operation is in progress.
     *
     * @param c the collection to transfer elements into
     * @return the number of elements transferred
     */
    protected int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
        int count = 0;
        for (ForkJoinTask<?> t; (t = pollScan(false)) != null; ) {
            c.add(t);
            ++count;
        }
        return count;
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state, parallelism level, and
     * worker and task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        // Use a single pass through queues to collect counts
        int e = runState;
        long st = stealCount;
        long qt = 0L, ss = 0L; int rc = 0;
        WorkQueue[] qs; WorkQueue q;
        if ((qs = queues) != null) {
            for (int i = 0; i < qs.length; ++i) {
                if ((q = qs[i]) != null) {
                    int size = q.queueSize();
                    if ((i & 1) == 0)
                        ss += size;
                    else {
                        qt += size;
                        st += (long)q.nsteals & 0xffffffffL;
                        if (q.isApparentlyUnblocked())
                            ++rc;
                    }
                }
            }
        }

        int pc = parallelism;
        long c = ctl;
        int tc = (short)(c >>> TC_SHIFT);
        int ac = (short)(c >>> RC_SHIFT);
        if (ac < 0) // ignore transient negative
            ac = 0;
        String level = ((e & TERMINATED) != 0 ? "Terminated" :
                        (e & STOP)       != 0 ? "Terminating" :
                        (e & SHUTDOWN)   != 0 ? "Shutting down" :
                        "Running");
        return super.toString() +
            "[" + level +
            ", parallelism = " + pc +
            ", size = " + tc +
            ", active = " + ac +
            ", running = " + rc +
            ", steals = " + st +
            ", tasks = " + qt +
            ", submissions = " + ss +
            "]";
    }

    /**
     * Possibly initiates an orderly shutdown in which previously
     * submitted tasks are executed, but no new tasks will be
     * accepted. Invocation has no effect on execution state if this
     * is the {@link #commonPool()}, and no additional effect if
     * already shut down.  Tasks that are in the process of being
     * submitted concurrently during the course of this method may or
     * may not be rejected.
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public void shutdown() {
        checkPermission();
        if (workerNamePrefix != null) // not common pool
            tryTerminate(false, true);
    }

    /**
     * Possibly attempts to cancel and/or stop all tasks, and reject
     * all subsequently submitted tasks.  Invocation has no effect on
     * execution state if this is the {@link #commonPool()}, and no
     * additional effect if already shut down. Otherwise, tasks that
     * are in the process of being submitted or executed concurrently
     * during the course of this method may or may not be
     * rejected. This method cancels both existing and unexecuted
     * tasks, in order to permit termination in the presence of task
     * dependencies. So the method always returns an empty list
     * (unlike the case for some other Executors).
     *
     * @return an empty list
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public List<Runnable> shutdownNow() {
        checkPermission();
        if (workerNamePrefix != null) // not common pool
            tryTerminate(true, true);
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    public boolean isTerminated() {
        return (tryTerminate(false, false) & TERMINATED) != 0;
    }

    /**
     * Returns {@code true} if the process of termination has
     * commenced but not yet completed.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, or are waiting for I/O,
     * causing this executor not to properly terminate. (See the
     * advisory notes for class {@link ForkJoinTask} stating that
     * tasks should not normally entail blocking operations.  But if
     * they do, they must abort them on interrupt.)
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        return (tryTerminate(false, false) & (STOP | TERMINATED)) == STOP;
    }

    /**
     * Returns {@code true} if this pool has been shut down.
     *
     * @return {@code true} if this pool has been shut down
     */
    public boolean isShutdown() {
        return (runState & SHUTDOWN) != 0;
    }

    /**
     * Blocks until all tasks have completed execution after a
     * shutdown request, or the timeout occurs, or the current thread
     * is interrupted, whichever happens first. Because the {@link
     * #commonPool()} never terminates until program shutdown, when
     * applied to the common pool, this method is equivalent to {@link
     * #awaitQuiescence(long, TimeUnit)} but always returns {@code false}.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this executor terminated and
     *         {@code false} if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        CountDownLatch done;
        if (workerNamePrefix == null) {    // is common pool
            if (helpQuiescePool(this, nanos, true) < 0)
                throw new InterruptedException();
            return false;
        }
        else if ((tryTerminate(false, false) & TERMINATED) != 0 ||
                 (done = terminationSignal()) == null ||
                 (runState & TERMINATED) != 0)
            return true;
        else
            return done.await(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * If called by a ForkJoinTask operating in this pool, equivalent
     * in effect to {@link ForkJoinTask#helpQuiesce}. Otherwise,
     * waits and/or attempts to assist performing tasks until this
     * pool {@link #isQuiescent} or the indicated timeout elapses.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if quiescent; {@code false} if the
     * timeout elapsed.
     */
    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        return (helpQuiescePool(this, unit.toNanos(timeout), false) > 0);
    }

    /**
     * Unless this is the {@link #commonPool()}, initiates an orderly
     * shutdown in which previously submitted tasks are executed, but
     * no new tasks will be accepted, and waits until all tasks have
     * completed execution and the executor has terminated.
     *
     * <p> If already terminated, or this is the {@link
     * #commonPool()}, this method has no effect on execution, and
     * does not wait. Otherwise, if interrupted while waiting, this
     * method stops all executing tasks as if by invoking {@link
     * #shutdownNow()}. It then continues to wait until all actively
     * executing tasks have completed. Tasks that were awaiting
     * execution are not executed. The interrupt status will be
     * re-asserted before this method returns.
     *
     * @throws SecurityException if a security manager exists and
     *         shutting down this ExecutorService may manipulate
     *         threads that the caller is not permitted to modify
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")},
     *         or the security manager's {@code checkAccess} method
     *         denies access.
     * @since 19
     */
    @Override
    public void close() {
        if (workerNamePrefix != null) {
            checkPermission();
            CountDownLatch done = null;
            boolean interrupted = false;
            while ((tryTerminate(interrupted, true) & TERMINATED) == 0) {
                if (done == null)
                    done = terminationSignal();
                else {
                    try {
                        done.await();
                        break;
                    } catch (InterruptedException ex) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }

    /**
     * Interface for extending managed parallelism for tasks running
     * in {@link ForkJoinPool}s.
     *
     * <p>A {@code ManagedBlocker} provides two methods.  Method
     * {@link #isReleasable} must return {@code true} if blocking is
     * not necessary. Method {@link #block} blocks the current thread
     * if necessary (perhaps internally invoking {@code isReleasable}
     * before actually blocking). These actions are performed by any
     * thread invoking {@link
     * ForkJoinPool#managedBlock(ManagedBlocker)}.  The unusual
     * methods in this API accommodate synchronizers that may, but
     * don't usually, block for long periods. Similarly, they allow
     * more efficient internal handling of cases in which additional
     * workers may be, but usually are not, needed to ensure
     * sufficient parallelism.  Toward this end, implementations of
     * method {@code isReleasable} must be amenable to repeated
     * invocation. Neither method is invoked after a prior invocation
     * of {@code isReleasable} or {@code block} returns {@code true}.
     *
     * <p>For example, here is a ManagedBlocker based on a
     * ReentrantLock:
     * <pre> {@code
     * class ManagedLocker implements ManagedBlocker {
     *   final ReentrantLock lock;
     *   boolean hasLock = false;
     *   ManagedLocker(ReentrantLock lock) { this.lock = lock; }
     *   public boolean block() {
     *     if (!hasLock)
     *       lock.lock();
     *     return true;
     *   }
     *   public boolean isReleasable() {
     *     return hasLock || (hasLock = lock.tryLock());
     *   }
     * }}</pre>
     *
     * <p>Here is a class that possibly blocks waiting for an
     * item on a given queue:
     * <pre> {@code
     * class QueueTaker<E> implements ManagedBlocker {
     *   final BlockingQueue<E> queue;
     *   volatile E item = null;
     *   QueueTaker(BlockingQueue<E> q) { this.queue = q; }
     *   public boolean block() throws InterruptedException {
     *     if (item == null)
     *       item = queue.take();
     *     return true;
     *   }
     *   public boolean isReleasable() {
     *     return item != null || (item = queue.poll()) != null;
     *   }
     *   public E getItem() { // call after pool.managedBlock completes
     *     return item;
     *   }
     * }}</pre>
     */
    public static interface ManagedBlocker {
        /**
         * Possibly blocks the current thread, for example waiting for
         * a lock or condition.
         *
         * @return {@code true} if no additional blocking is necessary
         * (i.e., if isReleasable would return true)
         * @throws InterruptedException if interrupted while waiting
         * (the method is not required to do so, but is allowed to)
         */
        boolean block() throws InterruptedException;

        /**
         * Returns {@code true} if blocking is unnecessary.
         * @return {@code true} if blocking is unnecessary
         */
        boolean isReleasable();
    }

    /**
     * Runs the given possibly blocking task.  When {@linkplain
     * ForkJoinTask#inForkJoinPool() running in a ForkJoinPool}, this
     * method possibly arranges for a spare thread to be activated if
     * necessary to ensure sufficient parallelism while the current
     * thread is blocked in {@link ManagedBlocker#block blocker.block()}.
     *
     * <p>This method repeatedly calls {@code blocker.isReleasable()} and
     * {@code blocker.block()} until either method returns {@code true}.
     * Every call to {@code blocker.block()} is preceded by a call to
     * {@code blocker.isReleasable()} that returned {@code false}.
     *
     * <p>If not running in a ForkJoinPool, this method is
     * behaviorally equivalent to
     * <pre> {@code
     * while (!blocker.isReleasable())
     *   if (blocker.block())
     *     break;}</pre>
     *
     * If running in a ForkJoinPool, the pool may first be expanded to
     * ensure sufficient parallelism available during the call to
     * {@code blocker.block()}.
     *
     * @param blocker the blocker task
     * @throws InterruptedException if {@code blocker.block()} did so
     */
    public static void managedBlock(ManagedBlocker blocker)
        throws InterruptedException {
        Thread t; ForkJoinPool p;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread &&
            (p = ((ForkJoinWorkerThread)t).pool) != null)
            p.compensatedBlock(blocker);
        else
            unmanagedBlock(blocker);
    }

    /** ManagedBlock for ForkJoinWorkerThreads */
    private void compensatedBlock(ManagedBlocker blocker)
        throws InterruptedException {
        Objects.requireNonNull(blocker);
        for (;;) {
            int comp; boolean done;
            long c = ctl;
            if (blocker.isReleasable())
                break;
            if ((runState & STOP) != 0)
                throw new InterruptedException();
            if ((comp = tryCompensate(c)) >= 0) {
                try {
                    done = blocker.block();
                } finally {
                    if (comp > 0)
                        getAndAddCtl(RC_UNIT);
                }
                if (done)
                    break;
            }
        }
    }

    /**
     * Invokes tryCompensate to create or re-activate a spare thread to
     * compensate for a thread that performs a blocking operation. When the
     * blocking operation is done then endCompensatedBlock must be invoked
     * with the value returned by this method to re-adjust the parallelism.
     */
    final long beginCompensatedBlock() {
        int c;
        do {} while ((c = tryCompensate(ctl)) < 0);
        return (c == 0) ? 0L : RC_UNIT;
    }

    /**
     * Re-adjusts parallelism after a blocking operation completes.
     */
    void endCompensatedBlock(long post) {
        if (post > 0L) {
            getAndAddCtl(post);
        }
    }

    /** ManagedBlock for external threads */
    private static void unmanagedBlock(ManagedBlocker blocker)
        throws InterruptedException {
        Objects.requireNonNull(blocker);
        do {} while (!blocker.isReleasable() && !blocker.block());
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            new ForkJoinTask.AdaptedRunnable<T>(runnable, value) :
            new ForkJoinTask.AdaptedInterruptibleRunnable<T>(runnable, value);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            new ForkJoinTask.AdaptedCallable<T>(callable) :
            new ForkJoinTask.AdaptedInterruptibleCallable<T>(callable);
    }

    static {
        U = Unsafe.getUnsafe();
        Class<ForkJoinPool> klass = ForkJoinPool.class;
        try {
            Field poolIdsField = klass.getDeclaredField("poolIds");
            POOLIDS_BASE = U.staticFieldBase(poolIdsField);
            POOLIDS = U.staticFieldOffset(poolIdsField);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
        CTL = U.objectFieldOffset(klass, "ctl");
        RUNSTATE = U.objectFieldOffset(klass, "runState");
        PARALLELISM =  U.objectFieldOffset(klass, "parallelism");
        THREADIDS = U.objectFieldOffset(klass, "threadIds");
        TERMINATION = U.objectFieldOffset(klass, "termination");
        Class<ForkJoinTask[]> aklass = ForkJoinTask[].class;
        ABASE = U.arrayBaseOffset(aklass);
        int scale = U.arrayIndexScale(aklass);
        ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        if ((scale & (scale - 1)) != 0)
            throw new Error("array index scale not a power of two");

        defaultForkJoinWorkerThreadFactory =
            new DefaultForkJoinWorkerThreadFactory();
        @SuppressWarnings("removal")
        ForkJoinPool p = common = (System.getSecurityManager() == null) ?
            new ForkJoinPool((byte)0) :
            AccessController.doPrivileged(new PrivilegedAction<>() {
                    public ForkJoinPool run() {
                        return new ForkJoinPool((byte)0); }});
        // allow access to non-public methods
        SharedSecrets.setJavaUtilConcurrentFJPAccess(
            new JavaUtilConcurrentFJPAccess() {
                @Override
                public long beginCompensatedBlock(ForkJoinPool pool) {
                    return pool.beginCompensatedBlock();
                }
                public void endCompensatedBlock(ForkJoinPool pool, long post) {
                    pool.endCompensatedBlock(post);
                }
            });
        Class<?> dep = LockSupport.class; // ensure loaded
    }
}
