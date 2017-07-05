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
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An {@link ExecutorService} for running {@link ForkJoinTask}s.
 * A {@code ForkJoinPool} provides the entry point for submissions
 * from non-{@code ForkJoinTask}s, as well as management and
 * monitoring operations.
 *
 * <p>A {@code ForkJoinPool} differs from other kinds of {@link
 * ExecutorService} mainly by virtue of employing
 * <em>work-stealing</em>: all threads in the pool attempt to find and
 * execute subtasks created by other active tasks (eventually blocking
 * waiting for work if none exist). This enables efficient processing
 * when most tasks spawn other subtasks (as do most {@code
 * ForkJoinTask}s). A {@code ForkJoinPool} may also be used for mixed
 * execution of some plain {@code Runnable}- or {@code Callable}-
 * based activities along with {@code ForkJoinTask}s. When setting
 * {@linkplain #setAsyncMode async mode}, a {@code ForkJoinPool} may
 * also be appropriate for use with fine-grained tasks of any form
 * that are never joined. Otherwise, other {@code ExecutorService}
 * implementations are typically more appropriate choices.
 *
 * <p>A {@code ForkJoinPool} is constructed with a given target
 * parallelism level; by default, equal to the number of available
 * processors. Unless configured otherwise via {@link
 * #setMaintainsParallelism}, the pool attempts to maintain this
 * number of active (or available) threads by dynamically adding,
 * suspending, or resuming internal worker threads, even if some tasks
 * are stalled waiting to join others. However, no such adjustments
 * are performed in the face of blocked IO or other unmanaged
 * synchronization. The nested {@link ManagedBlocker} interface
 * enables extension of the kinds of synchronization accommodated.
 * The target parallelism level may also be changed dynamically
 * ({@link #setParallelism}). The total number of threads may be
 * limited using method {@link #setMaximumPoolSize}, in which case it
 * may become possible for the activities of a pool to stall due to
 * the lack of available threads to process new tasks.
 *
 * <p>In addition to execution and lifecycle control methods, this
 * class provides status check methods (for example
 * {@link #getStealCount}) that are intended to aid in developing,
 * tuning, and monitoring fork/join applications. Also, method
 * {@link #toString} returns indications of pool state in a
 * convenient form for informal monitoring.
 *
 * <p><b>Sample Usage.</b> Normally a single {@code ForkJoinPool} is
 * used for all parallel task execution in a program or subsystem.
 * Otherwise, use would not usually outweigh the construction and
 * bookkeeping overhead of creating a large set of threads. For
 * example, a common pool could be used for the {@code SortTasks}
 * illustrated in {@link RecursiveAction}. Because {@code
 * ForkJoinPool} uses threads in {@linkplain java.lang.Thread#isDaemon
 * daemon} mode, there is typically no need to explicitly {@link
 * #shutdown} such a pool upon program exit.
 *
 * <pre>
 * static final ForkJoinPool mainPool = new ForkJoinPool();
 * ...
 * public void sort(long[] array) {
 *   mainPool.invoke(new SortTask(array, 0, array.length));
 * }
 * </pre>
 *
 * <p><b>Implementation notes</b>: This implementation restricts the
 * maximum number of running threads to 32767. Attempts to create
 * pools with greater than the maximum number result in
 * {@code IllegalArgumentException}.
 *
 * <p>This implementation rejects submitted tasks (that is, by throwing
 * {@link RejectedExecutionException}) only when the pool is shut down.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class ForkJoinPool extends AbstractExecutorService {

    /*
     * See the extended comments interspersed below for design,
     * rationale, and walkthroughs.
     */

    /** Mask for packing and unpacking shorts */
    private static final int  shortMask = 0xffff;

    /** Max pool size -- must be a power of two minus 1 */
    private static final int MAX_THREADS =  0x7FFF;

    /**
     * Factory for creating new {@link ForkJoinWorkerThread}s.
     * A {@code ForkJoinWorkerThreadFactory} must be defined and used
     * for {@code ForkJoinWorkerThread} subclasses that extend base
     * functionality or initialize threads with different contexts.
     */
    public static interface ForkJoinWorkerThreadFactory {
        /**
         * Returns a new worker thread operating in the given pool.
         *
         * @param pool the pool this thread works in
         * @throws NullPointerException if the pool is null
         */
        public ForkJoinWorkerThread newThread(ForkJoinPool pool);
    }

    /**
     * Default ForkJoinWorkerThreadFactory implementation; creates a
     * new ForkJoinWorkerThread.
     */
    static class  DefaultForkJoinWorkerThreadFactory
        implements ForkJoinWorkerThreadFactory {
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            try {
                return new ForkJoinWorkerThread(pool);
            } catch (OutOfMemoryError oom)  {
                return null;
            }
        }
    }

    /**
     * Creates a new ForkJoinWorkerThread. This factory is used unless
     * overridden in ForkJoinPool constructors.
     */
    public static final ForkJoinWorkerThreadFactory
        defaultForkJoinWorkerThreadFactory =
        new DefaultForkJoinWorkerThreadFactory();

    /**
     * Permission required for callers of methods that may start or
     * kill threads.
     */
    private static final RuntimePermission modifyThreadPermission =
        new RuntimePermission("modifyThread");

    /**
     * If there is a security manager, makes sure caller has
     * permission to modify threads.
     */
    private static void checkPermission() {
        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkPermission(modifyThreadPermission);
    }

    /**
     * Generator for assigning sequence numbers as pool names.
     */
    private static final AtomicInteger poolNumberGenerator =
        new AtomicInteger();

    /**
     * Array holding all worker threads in the pool. Initialized upon
     * first use. Array size must be a power of two.  Updates and
     * replacements are protected by workerLock, but it is always kept
     * in a consistent enough state to be randomly accessed without
     * locking by workers performing work-stealing.
     */
    volatile ForkJoinWorkerThread[] workers;

    /**
     * Lock protecting access to workers.
     */
    private final ReentrantLock workerLock;

    /**
     * Condition for awaitTermination.
     */
    private final Condition termination;

    /**
     * The uncaught exception handler used when any worker
     * abruptly terminates
     */
    private Thread.UncaughtExceptionHandler ueh;

    /**
     * Creation factory for worker threads.
     */
    private final ForkJoinWorkerThreadFactory factory;

    /**
     * Head of stack of threads that were created to maintain
     * parallelism when other threads blocked, but have since
     * suspended when the parallelism level rose.
     */
    private volatile WaitQueueNode spareStack;

    /**
     * Sum of per-thread steal counts, updated only when threads are
     * idle or terminating.
     */
    private final AtomicLong stealCount;

    /**
     * Queue for external submissions.
     */
    private final LinkedTransferQueue<ForkJoinTask<?>> submissionQueue;

    /**
     * Head of Treiber stack for barrier sync. See below for explanation.
     */
    private volatile WaitQueueNode syncStack;

    /**
     * The count for event barrier
     */
    private volatile long eventCount;

    /**
     * Pool number, just for assigning useful names to worker threads
     */
    private final int poolNumber;

    /**
     * The maximum allowed pool size
     */
    private volatile int maxPoolSize;

    /**
     * The desired parallelism level, updated only under workerLock.
     */
    private volatile int parallelism;

    /**
     * True if use local fifo, not default lifo, for local polling
     */
    private volatile boolean locallyFifo;

    /**
     * Holds number of total (i.e., created and not yet terminated)
     * and running (i.e., not blocked on joins or other managed sync)
     * threads, packed into one int to ensure consistent snapshot when
     * making decisions about creating and suspending spare
     * threads. Updated only by CAS.  Note: CASes in
     * updateRunningCount and preJoin assume that running active count
     * is in low word, so need to be modified if this changes.
     */
    private volatile int workerCounts;

    private static int totalCountOf(int s)           { return s >>> 16;  }
    private static int runningCountOf(int s)         { return s & shortMask; }
    private static int workerCountsFor(int t, int r) { return (t << 16) + r; }

    /**
     * Adds delta (which may be negative) to running count.  This must
     * be called before (with negative arg) and after (with positive)
     * any managed synchronization (i.e., mainly, joins).
     *
     * @param delta the number to add
     */
    final void updateRunningCount(int delta) {
        int s;
        do {} while (!casWorkerCounts(s = workerCounts, s + delta));
    }

    /**
     * Adds delta (which may be negative) to both total and running
     * count.  This must be called upon creation and termination of
     * worker threads.
     *
     * @param delta the number to add
     */
    private void updateWorkerCount(int delta) {
        int d = delta + (delta << 16); // add to both lo and hi parts
        int s;
        do {} while (!casWorkerCounts(s = workerCounts, s + d));
    }

    /**
     * Lifecycle control. High word contains runState, low word
     * contains the number of workers that are (probably) executing
     * tasks. This value is atomically incremented before a worker
     * gets a task to run, and decremented when worker has no tasks
     * and cannot find any. These two fields are bundled together to
     * support correct termination triggering.  Note: activeCount
     * CAS'es cheat by assuming active count is in low word, so need
     * to be modified if this changes
     */
    private volatile int runControl;

    // RunState values. Order among values matters
    private static final int RUNNING     = 0;
    private static final int SHUTDOWN    = 1;
    private static final int TERMINATING = 2;
    private static final int TERMINATED  = 3;

    private static int runStateOf(int c)             { return c >>> 16; }
    private static int activeCountOf(int c)          { return c & shortMask; }
    private static int runControlFor(int r, int a)   { return (r << 16) + a; }

    /**
     * Tries incrementing active count; fails on contention.
     * Called by workers before/during executing tasks.
     *
     * @return true on success
     */
    final boolean tryIncrementActiveCount() {
        int c = runControl;
        return casRunControl(c, c+1);
    }

    /**
     * Tries decrementing active count; fails on contention.
     * Possibly triggers termination on success.
     * Called by workers when they can't find tasks.
     *
     * @return true on success
     */
    final boolean tryDecrementActiveCount() {
        int c = runControl;
        int nextc = c - 1;
        if (!casRunControl(c, nextc))
            return false;
        if (canTerminateOnShutdown(nextc))
            terminateOnShutdown();
        return true;
    }

    /**
     * Returns {@code true} if argument represents zero active count
     * and nonzero runstate, which is the triggering condition for
     * terminating on shutdown.
     */
    private static boolean canTerminateOnShutdown(int c) {
        // i.e. least bit is nonzero runState bit
        return ((c & -c) >>> 16) != 0;
    }

    /**
     * Transition run state to at least the given state. Return true
     * if not already at least given state.
     */
    private boolean transitionRunStateTo(int state) {
        for (;;) {
            int c = runControl;
            if (runStateOf(c) >= state)
                return false;
            if (casRunControl(c, runControlFor(state, activeCountOf(c))))
                return true;
        }
    }

    /**
     * Controls whether to add spares to maintain parallelism
     */
    private volatile boolean maintainsParallelism;

    // Constructors

    /**
     * Creates a {@code ForkJoinPool} with parallelism equal to {@link
     * java.lang.Runtime#availableProcessors}, and using the {@linkplain
     * #defaultForkJoinWorkerThreadFactory default thread factory}.
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool() {
        this(Runtime.getRuntime().availableProcessors(),
             defaultForkJoinWorkerThreadFactory);
    }

    /**
     * Creates a {@code ForkJoinPool} with the indicated parallelism
     * level and using the {@linkplain
     * #defaultForkJoinWorkerThreadFactory default thread factory}.
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
        this(parallelism, defaultForkJoinWorkerThreadFactory);
    }

    /**
     * Creates a {@code ForkJoinPool} with parallelism equal to {@link
     * java.lang.Runtime#availableProcessors}, and using the given
     * thread factory.
     *
     * @param factory the factory for creating new threads
     * @throws NullPointerException if the factory is null
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool(ForkJoinWorkerThreadFactory factory) {
        this(Runtime.getRuntime().availableProcessors(), factory);
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parallelism and
     * thread factory.
     *
     * @param parallelism the parallelism level
     * @param factory the factory for creating new threads
     * @throws IllegalArgumentException if parallelism less than or
     *         equal to zero, or greater than implementation limit
     * @throws NullPointerException if the factory is null
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory) {
        if (parallelism <= 0 || parallelism > MAX_THREADS)
            throw new IllegalArgumentException();
        if (factory == null)
            throw new NullPointerException();
        checkPermission();
        this.factory = factory;
        this.parallelism = parallelism;
        this.maxPoolSize = MAX_THREADS;
        this.maintainsParallelism = true;
        this.poolNumber = poolNumberGenerator.incrementAndGet();
        this.workerLock = new ReentrantLock();
        this.termination = workerLock.newCondition();
        this.stealCount = new AtomicLong();
        this.submissionQueue = new LinkedTransferQueue<ForkJoinTask<?>>();
        // worker array and workers are lazily constructed
    }

    /**
     * Creates a new worker thread using factory.
     *
     * @param index the index to assign worker
     * @return new worker, or null if factory failed
     */
    private ForkJoinWorkerThread createWorker(int index) {
        Thread.UncaughtExceptionHandler h = ueh;
        ForkJoinWorkerThread w = factory.newThread(this);
        if (w != null) {
            w.poolIndex = index;
            w.setDaemon(true);
            w.setAsyncMode(locallyFifo);
            w.setName("ForkJoinPool-" + poolNumber + "-worker-" + index);
            if (h != null)
                w.setUncaughtExceptionHandler(h);
        }
        return w;
    }

    /**
     * Returns a good size for worker array given pool size.
     * Currently requires size to be a power of two.
     */
    private static int arraySizeFor(int poolSize) {
        if (poolSize <= 1)
            return 1;
        // See Hackers Delight, sec 3.2
        int c = poolSize >= MAX_THREADS ? MAX_THREADS : (poolSize - 1);
        c |= c >>>  1;
        c |= c >>>  2;
        c |= c >>>  4;
        c |= c >>>  8;
        c |= c >>> 16;
        return c + 1;
    }

    /**
     * Creates or resizes array if necessary to hold newLength.
     * Call only under exclusion.
     *
     * @return the array
     */
    private ForkJoinWorkerThread[] ensureWorkerArrayCapacity(int newLength) {
        ForkJoinWorkerThread[] ws = workers;
        if (ws == null)
            return workers = new ForkJoinWorkerThread[arraySizeFor(newLength)];
        else if (newLength > ws.length)
            return workers = Arrays.copyOf(ws, arraySizeFor(newLength));
        else
            return ws;
    }

    /**
     * Tries to shrink workers into smaller array after one or more terminate.
     */
    private void tryShrinkWorkerArray() {
        ForkJoinWorkerThread[] ws = workers;
        if (ws != null) {
            int len = ws.length;
            int last = len - 1;
            while (last >= 0 && ws[last] == null)
                --last;
            int newLength = arraySizeFor(last+1);
            if (newLength < len)
                workers = Arrays.copyOf(ws, newLength);
        }
    }

    /**
     * Initializes workers if necessary.
     */
    final void ensureWorkerInitialization() {
        ForkJoinWorkerThread[] ws = workers;
        if (ws == null) {
            final ReentrantLock lock = this.workerLock;
            lock.lock();
            try {
                ws = workers;
                if (ws == null) {
                    int ps = parallelism;
                    ws = ensureWorkerArrayCapacity(ps);
                    for (int i = 0; i < ps; ++i) {
                        ForkJoinWorkerThread w = createWorker(i);
                        if (w != null) {
                            ws[i] = w;
                            w.start();
                            updateWorkerCount(1);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Worker creation and startup for threads added via setParallelism.
     */
    private void createAndStartAddedWorkers() {
        resumeAllSpares();  // Allow spares to convert to nonspare
        int ps = parallelism;
        ForkJoinWorkerThread[] ws = ensureWorkerArrayCapacity(ps);
        int len = ws.length;
        // Sweep through slots, to keep lowest indices most populated
        int k = 0;
        while (k < len) {
            if (ws[k] != null) {
                ++k;
                continue;
            }
            int s = workerCounts;
            int tc = totalCountOf(s);
            int rc = runningCountOf(s);
            if (rc >= ps || tc >= ps)
                break;
            if (casWorkerCounts (s, workerCountsFor(tc+1, rc+1))) {
                ForkJoinWorkerThread w = createWorker(k);
                if (w != null) {
                    ws[k++] = w;
                    w.start();
                }
                else {
                    updateWorkerCount(-1); // back out on failed creation
                    break;
                }
            }
        }
    }

    // Execution methods

    /**
     * Common code for execute, invoke and submit
     */
    private <T> void doSubmit(ForkJoinTask<T> task) {
        if (task == null)
            throw new NullPointerException();
        if (isShutdown())
            throw new RejectedExecutionException();
        if (workers == null)
            ensureWorkerInitialization();
        submissionQueue.offer(task);
        signalIdleWorkers();
    }

    /**
     * Performs the given task, returning its result upon completion.
     *
     * @param task the task
     * @return the task's result
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> T invoke(ForkJoinTask<T> task) {
        doSubmit(task);
        return task.join();
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
        doSubmit(task);
    }

    // AbstractExecutorService methods

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public void execute(Runnable task) {
        ForkJoinTask<?> job;
        if (task instanceof ForkJoinTask<?>) // avoid re-wrap
            job = (ForkJoinTask<?>) task;
        else
            job = ForkJoinTask.adapt(task, null);
        doSubmit(job);
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        ForkJoinTask<T> job = ForkJoinTask.adapt(task);
        doSubmit(job);
        return job;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        ForkJoinTask<T> job = ForkJoinTask.adapt(task, result);
        doSubmit(job);
        return job;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public ForkJoinTask<?> submit(Runnable task) {
        ForkJoinTask<?> job;
        if (task instanceof ForkJoinTask<?>) // avoid re-wrap
            job = (ForkJoinTask<?>) task;
        else
            job = ForkJoinTask.adapt(task, null);
        doSubmit(job);
        return job;
    }

    /**
     * Submits a ForkJoinTask for execution.
     *
     * @param task the task to submit
     * @return the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        doSubmit(task);
        return task;
    }


    /**
     * @throws NullPointerException       {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     */
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        ArrayList<ForkJoinTask<T>> forkJoinTasks =
            new ArrayList<ForkJoinTask<T>>(tasks.size());
        for (Callable<T> task : tasks)
            forkJoinTasks.add(ForkJoinTask.adapt(task));
        invoke(new InvokeAll<T>(forkJoinTasks));

        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Future<T>> futures = (List<Future<T>>) (List) forkJoinTasks;
        return futures;
    }

    static final class InvokeAll<T> extends RecursiveAction {
        final ArrayList<ForkJoinTask<T>> tasks;
        InvokeAll(ArrayList<ForkJoinTask<T>> tasks) { this.tasks = tasks; }
        public void compute() {
            try { invokeAll(tasks); }
            catch (Exception ignore) {}
        }
        private static final long serialVersionUID = -7914297376763021607L;
    }

    // Configuration and status settings and queries

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
    public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler h;
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            h = ueh;
        } finally {
            lock.unlock();
        }
        return h;
    }

    /**
     * Sets the handler for internal worker threads that terminate due
     * to unrecoverable errors encountered while executing tasks.
     * Unless set, the current default or ThreadGroup handler is used
     * as handler.
     *
     * @param h the new handler
     * @return the old handler, or {@code null} if none
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public Thread.UncaughtExceptionHandler
        setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler h) {
        checkPermission();
        Thread.UncaughtExceptionHandler old = null;
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            old = ueh;
            ueh = h;
            ForkJoinWorkerThread[] ws = workers;
            if (ws != null) {
                for (int i = 0; i < ws.length; ++i) {
                    ForkJoinWorkerThread w = ws[i];
                    if (w != null)
                        w.setUncaughtExceptionHandler(h);
                }
            }
        } finally {
            lock.unlock();
        }
        return old;
    }


    /**
     * Sets the target parallelism level of this pool.
     *
     * @param parallelism the target parallelism
     * @throws IllegalArgumentException if parallelism less than or
     * equal to zero or greater than maximum size bounds
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public void setParallelism(int parallelism) {
        checkPermission();
        if (parallelism <= 0 || parallelism > maxPoolSize)
            throw new IllegalArgumentException();
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            if (isProcessingTasks()) {
                int p = this.parallelism;
                this.parallelism = parallelism;
                if (parallelism > p)
                    createAndStartAddedWorkers();
                else
                    trimSpares();
            }
        } finally {
            lock.unlock();
        }
        signalIdleWorkers();
    }

    /**
     * Returns the targeted parallelism level of this pool.
     *
     * @return the targeted parallelism level of this pool
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Returns the number of worker threads that have started but not
     * yet terminated.  This result returned by this method may differ
     * from {@link #getParallelism} when threads are created to
     * maintain parallelism when others are cooperatively blocked.
     *
     * @return the number of worker threads
     */
    public int getPoolSize() {
        return totalCountOf(workerCounts);
    }

    /**
     * Returns the maximum number of threads allowed to exist in the
     * pool. Unless set using {@link #setMaximumPoolSize}, the
     * maximum is an implementation-defined value designed only to
     * prevent runaway growth.
     *
     * @return the maximum
     */
    public int getMaximumPoolSize() {
        return maxPoolSize;
    }

    /**
     * Sets the maximum number of threads allowed to exist in the
     * pool. The given value should normally be greater than or equal
     * to the {@link #getParallelism parallelism} level. Setting this
     * value has no effect on current pool size. It controls
     * construction of new threads.
     *
     * @throws IllegalArgumentException if negative or greater than
     * internal implementation limit
     */
    public void setMaximumPoolSize(int newMax) {
        if (newMax < 0 || newMax > MAX_THREADS)
            throw new IllegalArgumentException();
        maxPoolSize = newMax;
    }


    /**
     * Returns {@code true} if this pool dynamically maintains its
     * target parallelism level. If false, new threads are added only
     * to avoid possible starvation.  This setting is by default true.
     *
     * @return {@code true} if maintains parallelism
     */
    public boolean getMaintainsParallelism() {
        return maintainsParallelism;
    }

    /**
     * Sets whether this pool dynamically maintains its target
     * parallelism level. If false, new threads are added only to
     * avoid possible starvation.
     *
     * @param enable {@code true} to maintain parallelism
     */
    public void setMaintainsParallelism(boolean enable) {
        maintainsParallelism = enable;
    }

    /**
     * Establishes local first-in-first-out scheduling mode for forked
     * tasks that are never joined. This mode may be more appropriate
     * than default locally stack-based mode in applications in which
     * worker threads only process asynchronous tasks.  This method is
     * designed to be invoked only when the pool is quiescent, and
     * typically only before any tasks are submitted. The effects of
     * invocations at other times may be unpredictable.
     *
     * @param async if {@code true}, use locally FIFO scheduling
     * @return the previous mode
     * @see #getAsyncMode
     */
    public boolean setAsyncMode(boolean async) {
        boolean oldMode = locallyFifo;
        locallyFifo = async;
        ForkJoinWorkerThread[] ws = workers;
        if (ws != null) {
            for (int i = 0; i < ws.length; ++i) {
                ForkJoinWorkerThread t = ws[i];
                if (t != null)
                    t.setAsyncMode(async);
            }
        }
        return oldMode;
    }

    /**
     * Returns {@code true} if this pool uses local first-in-first-out
     * scheduling mode for forked tasks that are never joined.
     *
     * @return {@code true} if this pool uses async mode
     * @see #setAsyncMode
     */
    public boolean getAsyncMode() {
        return locallyFifo;
    }

    /**
     * Returns an estimate of the number of worker threads that are
     * not blocked waiting to join tasks or for other managed
     * synchronization.
     *
     * @return the number of worker threads
     */
    public int getRunningThreadCount() {
        return runningCountOf(workerCounts);
    }

    /**
     * Returns an estimate of the number of threads that are currently
     * stealing or executing tasks. This method may overestimate the
     * number of active threads.
     *
     * @return the number of active threads
     */
    public int getActiveThreadCount() {
        return activeCountOf(runControl);
    }

    /**
     * Returns an estimate of the number of threads that are currently
     * idle waiting for tasks. This method may underestimate the
     * number of idle threads.
     *
     * @return the number of idle threads
     */
    final int getIdleThreadCount() {
        int c = runningCountOf(workerCounts) - activeCountOf(runControl);
        return (c <= 0) ? 0 : c;
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
        return activeCountOf(runControl) == 0;
    }

    /**
     * Returns an estimate of the total number of tasks stolen from
     * one thread's work queue by another. The reported value
     * underestimates the actual total number of steals when the pool
     * is not quiescent. This value may be useful for monitoring and
     * tuning fork/join programs: in general, steal counts should be
     * high enough to keep threads busy, but low enough to avoid
     * overhead and contention across threads.
     *
     * @return the number of steals
     */
    public long getStealCount() {
        return stealCount.get();
    }

    /**
     * Accumulates steal count from a worker.
     * Call only when worker known to be idle.
     */
    private void updateStealCount(ForkJoinWorkerThread w) {
        int sc = w.getAndClearStealCount();
        if (sc != 0)
            stealCount.addAndGet(sc);
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
     */
    public long getQueuedTaskCount() {
        long count = 0;
        ForkJoinWorkerThread[] ws = workers;
        if (ws != null) {
            for (int i = 0; i < ws.length; ++i) {
                ForkJoinWorkerThread t = ws[i];
                if (t != null)
                    count += t.getQueueSize();
            }
        }
        return count;
    }

    /**
     * Returns an estimate of the number of tasks submitted to this
     * pool that have not yet begun executing.  This method takes time
     * proportional to the number of submissions.
     *
     * @return the number of queued submissions
     */
    public int getQueuedSubmissionCount() {
        return submissionQueue.size();
    }

    /**
     * Returns {@code true} if there are any tasks submitted to this
     * pool that have not yet begun executing.
     *
     * @return {@code true} if there are any queued submissions
     */
    public boolean hasQueuedSubmissions() {
        return !submissionQueue.isEmpty();
    }

    /**
     * Removes and returns the next unexecuted submission if one is
     * available.  This method may be useful in extensions to this
     * class that re-assign work in systems with multiple pools.
     *
     * @return the next submission, or {@code null} if none
     */
    protected ForkJoinTask<?> pollSubmission() {
        return submissionQueue.poll();
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
        int n = submissionQueue.drainTo(c);
        ForkJoinWorkerThread[] ws = workers;
        if (ws != null) {
            for (int i = 0; i < ws.length; ++i) {
                ForkJoinWorkerThread w = ws[i];
                if (w != null)
                    n += w.drainTasksTo(c);
            }
        }
        return n;
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state, parallelism level, and
     * worker and task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        int ps = parallelism;
        int wc = workerCounts;
        int rc = runControl;
        long st = getStealCount();
        long qt = getQueuedTaskCount();
        long qs = getQueuedSubmissionCount();
        return super.toString() +
            "[" + runStateToString(runStateOf(rc)) +
            ", parallelism = " + ps +
            ", size = " + totalCountOf(wc) +
            ", active = " + activeCountOf(rc) +
            ", running = " + runningCountOf(wc) +
            ", steals = " + st +
            ", tasks = " + qt +
            ", submissions = " + qs +
            "]";
    }

    private static String runStateToString(int rs) {
        switch(rs) {
        case RUNNING: return "Running";
        case SHUTDOWN: return "Shutting down";
        case TERMINATING: return "Terminating";
        case TERMINATED: return "Terminated";
        default: throw new Error("Unknown run state");
        }
    }

    // lifecycle control

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     * Tasks that are in the process of being submitted concurrently
     * during the course of this method may or may not be rejected.
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public void shutdown() {
        checkPermission();
        transitionRunStateTo(SHUTDOWN);
        if (canTerminateOnShutdown(runControl)) {
            if (workers == null) { // shutting down before workers created
                final ReentrantLock lock = this.workerLock;
                lock.lock();
                try {
                    if (workers == null) {
                        terminate();
                        transitionRunStateTo(TERMINATED);
                        termination.signalAll();
                    }
                } finally {
                    lock.unlock();
                }
            }
            terminateOnShutdown();
        }
    }

    /**
     * Attempts to cancel and/or stop all tasks, and reject all
     * subsequently submitted tasks.  Tasks that are in the process of
     * being submitted or executed concurrently during the course of
     * this method may or may not be rejected. This method cancels
     * both existing and unexecuted tasks, in order to permit
     * termination in the presence of task dependencies. So the method
     * always returns an empty list (unlike the case for some other
     * Executors).
     *
     * @return an empty list
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public List<Runnable> shutdownNow() {
        checkPermission();
        terminate();
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    public boolean isTerminated() {
        return runStateOf(runControl) == TERMINATED;
    }

    /**
     * Returns {@code true} if the process of termination has
     * commenced but not yet completed.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        return runStateOf(runControl) == TERMINATING;
    }

    /**
     * Returns {@code true} if this pool has been shut down.
     *
     * @return {@code true} if this pool has been shut down
     */
    public boolean isShutdown() {
        return runStateOf(runControl) >= SHUTDOWN;
    }

    /**
     * Returns true if pool is not terminating or terminated.
     * Used internally to suppress execution when terminating.
     */
    final boolean isProcessingTasks() {
        return runStateOf(runControl) < TERMINATING;
    }

    /**
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
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
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            for (;;) {
                if (isTerminated())
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
    }

    // Shutdown and termination support

    /**
     * Callback from terminating worker. Nulls out the corresponding
     * workers slot, and if terminating, tries to terminate; else
     * tries to shrink workers array.
     *
     * @param w the worker
     */
    final void workerTerminated(ForkJoinWorkerThread w) {
        updateStealCount(w);
        updateWorkerCount(-1);
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            if (ws != null) {
                int idx = w.poolIndex;
                if (idx >= 0 && idx < ws.length && ws[idx] == w)
                    ws[idx] = null;
                if (totalCountOf(workerCounts) == 0) {
                    terminate(); // no-op if already terminating
                    transitionRunStateTo(TERMINATED);
                    termination.signalAll();
                }
                else if (isProcessingTasks()) {
                    tryShrinkWorkerArray();
                    tryResumeSpare(true); // allow replacement
                }
            }
        } finally {
            lock.unlock();
        }
        signalIdleWorkers();
    }

    /**
     * Initiates termination.
     */
    private void terminate() {
        if (transitionRunStateTo(TERMINATING)) {
            stopAllWorkers();
            resumeAllSpares();
            signalIdleWorkers();
            cancelQueuedSubmissions();
            cancelQueuedWorkerTasks();
            interruptUnterminatedWorkers();
            signalIdleWorkers(); // resignal after interrupt
        }
    }

    /**
     * Possibly terminates when on shutdown state.
     */
    private void terminateOnShutdown() {
        if (!hasQueuedSubmissions() && canTerminateOnShutdown(runControl))
            terminate();
    }

    /**
     * Clears out and cancels submissions.
     */
    private void cancelQueuedSubmissions() {
        ForkJoinTask<?> task;
        while ((task = pollSubmission()) != null)
            task.cancel(false);
    }

    /**
     * Cleans out worker queues.
     */
    private void cancelQueuedWorkerTasks() {
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            if (ws != null) {
                for (int i = 0; i < ws.length; ++i) {
                    ForkJoinWorkerThread t = ws[i];
                    if (t != null)
                        t.cancelTasks();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets each worker's status to terminating. Requires lock to avoid
     * conflicts with add/remove.
     */
    private void stopAllWorkers() {
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            if (ws != null) {
                for (int i = 0; i < ws.length; ++i) {
                    ForkJoinWorkerThread t = ws[i];
                    if (t != null)
                        t.shutdownNow();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Interrupts all unterminated workers.  This is not required for
     * sake of internal control, but may help unstick user code during
     * shutdown.
     */
    private void interruptUnterminatedWorkers() {
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            if (ws != null) {
                for (int i = 0; i < ws.length; ++i) {
                    ForkJoinWorkerThread t = ws[i];
                    if (t != null && !t.isTerminated()) {
                        try {
                            t.interrupt();
                        } catch (SecurityException ignore) {
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }


    /*
     * Nodes for event barrier to manage idle threads.  Queue nodes
     * are basic Treiber stack nodes, also used for spare stack.
     *
     * The event barrier has an event count and a wait queue (actually
     * a Treiber stack).  Workers are enabled to look for work when
     * the eventCount is incremented. If they fail to find work, they
     * may wait for next count. Upon release, threads help others wake
     * up.
     *
     * Synchronization events occur only in enough contexts to
     * maintain overall liveness:
     *
     *   - Submission of a new task to the pool
     *   - Resizes or other changes to the workers array
     *   - pool termination
     *   - A worker pushing a task on an empty queue
     *
     * The case of pushing a task occurs often enough, and is heavy
     * enough compared to simple stack pushes, to require special
     * handling: Method signalWork returns without advancing count if
     * the queue appears to be empty.  This would ordinarily result in
     * races causing some queued waiters not to be woken up. To avoid
     * this, the first worker enqueued in method sync (see
     * syncIsReleasable) rescans for tasks after being enqueued, and
     * helps signal if any are found. This works well because the
     * worker has nothing better to do, and so might as well help
     * alleviate the overhead and contention on the threads actually
     * doing work.  Also, since event counts increments on task
     * availability exist to maintain liveness (rather than to force
     * refreshes etc), it is OK for callers to exit early if
     * contending with another signaller.
     */
    static final class WaitQueueNode {
        WaitQueueNode next; // only written before enqueued
        volatile ForkJoinWorkerThread thread; // nulled to cancel wait
        final long count; // unused for spare stack

        WaitQueueNode(long c, ForkJoinWorkerThread w) {
            count = c;
            thread = w;
        }

        /**
         * Wakes up waiter, returning false if known to already
         */
        boolean signal() {
            ForkJoinWorkerThread t = thread;
            if (t == null)
                return false;
            thread = null;
            LockSupport.unpark(t);
            return true;
        }

        /**
         * Awaits release on sync.
         */
        void awaitSyncRelease(ForkJoinPool p) {
            while (thread != null && !p.syncIsReleasable(this))
                LockSupport.park(this);
        }

        /**
         * Awaits resumption as spare.
         */
        void awaitSpareRelease() {
            while (thread != null) {
                if (!Thread.interrupted())
                    LockSupport.park(this);
            }
        }
    }

    /**
     * Ensures that no thread is waiting for count to advance from the
     * current value of eventCount read on entry to this method, by
     * releasing waiting threads if necessary.
     *
     * @return the count
     */
    final long ensureSync() {
        long c = eventCount;
        WaitQueueNode q;
        while ((q = syncStack) != null && q.count < c) {
            if (casBarrierStack(q, null)) {
                do {
                    q.signal();
                } while ((q = q.next) != null);
                break;
            }
        }
        return c;
    }

    /**
     * Increments event count and releases waiting threads.
     */
    private void signalIdleWorkers() {
        long c;
        do {} while (!casEventCount(c = eventCount, c+1));
        ensureSync();
    }

    /**
     * Signals threads waiting to poll a task. Because method sync
     * rechecks availability, it is OK to only proceed if queue
     * appears to be non-empty, and OK to skip under contention to
     * increment count (since some other thread succeeded).
     */
    final void signalWork() {
        long c;
        WaitQueueNode q;
        if (syncStack != null &&
            casEventCount(c = eventCount, c+1) &&
            (((q = syncStack) != null && q.count <= c) &&
             (!casBarrierStack(q, q.next) || !q.signal())))
            ensureSync();
    }

    /**
     * Waits until event count advances from last value held by
     * caller, or if excess threads, caller is resumed as spare, or
     * caller or pool is terminating. Updates caller's event on exit.
     *
     * @param w the calling worker thread
     */
    final void sync(ForkJoinWorkerThread w) {
        updateStealCount(w); // Transfer w's count while it is idle

        while (!w.isShutdown() && isProcessingTasks() && !suspendIfSpare(w)) {
            long prev = w.lastEventCount;
            WaitQueueNode node = null;
            WaitQueueNode h;
            while (eventCount == prev &&
                   ((h = syncStack) == null || h.count == prev)) {
                if (node == null)
                    node = new WaitQueueNode(prev, w);
                if (casBarrierStack(node.next = h, node)) {
                    node.awaitSyncRelease(this);
                    break;
                }
            }
            long ec = ensureSync();
            if (ec != prev) {
                w.lastEventCount = ec;
                break;
            }
        }
    }

    /**
     * Returns {@code true} if worker waiting on sync can proceed:
     *  - on signal (thread == null)
     *  - on event count advance (winning race to notify vs signaller)
     *  - on interrupt
     *  - if the first queued node, we find work available
     * If node was not signalled and event count not advanced on exit,
     * then we also help advance event count.
     *
     * @return {@code true} if node can be released
     */
    final boolean syncIsReleasable(WaitQueueNode node) {
        long prev = node.count;
        if (!Thread.interrupted() && node.thread != null &&
            (node.next != null ||
             !ForkJoinWorkerThread.hasQueuedTasks(workers)) &&
            eventCount == prev)
            return false;
        if (node.thread != null) {
            node.thread = null;
            long ec = eventCount;
            if (prev <= ec) // help signal
                casEventCount(ec, ec+1);
        }
        return true;
    }

    /**
     * Returns {@code true} if a new sync event occurred since last
     * call to sync or this method, if so, updating caller's count.
     */
    final boolean hasNewSyncEvent(ForkJoinWorkerThread w) {
        long lc = w.lastEventCount;
        long ec = ensureSync();
        if (ec == lc)
            return false;
        w.lastEventCount = ec;
        return true;
    }

    //  Parallelism maintenance

    /**
     * Decrements running count; if too low, adds spare.
     *
     * Conceptually, all we need to do here is add or resume a
     * spare thread when one is about to block (and remove or
     * suspend it later when unblocked -- see suspendIfSpare).
     * However, implementing this idea requires coping with
     * several problems: we have imperfect information about the
     * states of threads. Some count updates can and usually do
     * lag run state changes, despite arrangements to keep them
     * accurate (for example, when possible, updating counts
     * before signalling or resuming), especially when running on
     * dynamic JVMs that don't optimize the infrequent paths that
     * update counts. Generating too many threads can make these
     * problems become worse, because excess threads are more
     * likely to be context-switched with others, slowing them all
     * down, especially if there is no work available, so all are
     * busy scanning or idling.  Also, excess spare threads can
     * only be suspended or removed when they are idle, not
     * immediately when they aren't needed. So adding threads will
     * raise parallelism level for longer than necessary.  Also,
     * FJ applications often encounter highly transient peaks when
     * many threads are blocked joining, but for less time than it
     * takes to create or resume spares.
     *
     * @param joinMe if non-null, return early if done
     * @param maintainParallelism if true, try to stay within
     * target counts, else create only to avoid starvation
     * @return true if joinMe known to be done
     */
    final boolean preJoin(ForkJoinTask<?> joinMe,
                          boolean maintainParallelism) {
        maintainParallelism &= maintainsParallelism; // overrride
        boolean dec = false;  // true when running count decremented
        while (spareStack == null || !tryResumeSpare(dec)) {
            int counts = workerCounts;
            if (dec || (dec = casWorkerCounts(counts, --counts))) {
                if (!needSpare(counts, maintainParallelism))
                    break;
                if (joinMe.status < 0)
                    return true;
                if (tryAddSpare(counts))
                    break;
            }
        }
        return false;
    }

    /**
     * Same idea as preJoin
     */
    final boolean preBlock(ManagedBlocker blocker,
                           boolean maintainParallelism) {
        maintainParallelism &= maintainsParallelism;
        boolean dec = false;
        while (spareStack == null || !tryResumeSpare(dec)) {
            int counts = workerCounts;
            if (dec || (dec = casWorkerCounts(counts, --counts))) {
                if (!needSpare(counts, maintainParallelism))
                    break;
                if (blocker.isReleasable())
                    return true;
                if (tryAddSpare(counts))
                    break;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if a spare thread appears to be needed.
     * If maintaining parallelism, returns true when the deficit in
     * running threads is more than the surplus of total threads, and
     * there is apparently some work to do.  This self-limiting rule
     * means that the more threads that have already been added, the
     * less parallelism we will tolerate before adding another.
     *
     * @param counts current worker counts
     * @param maintainParallelism try to maintain parallelism
     */
    private boolean needSpare(int counts, boolean maintainParallelism) {
        int ps = parallelism;
        int rc = runningCountOf(counts);
        int tc = totalCountOf(counts);
        int runningDeficit = ps - rc;
        int totalSurplus = tc - ps;
        return (tc < maxPoolSize &&
                (rc == 0 || totalSurplus < 0 ||
                 (maintainParallelism &&
                  runningDeficit > totalSurplus &&
                  ForkJoinWorkerThread.hasQueuedTasks(workers))));
    }

    /**
     * Adds a spare worker if lock available and no more than the
     * expected numbers of threads exist.
     *
     * @return true if successful
     */
    private boolean tryAddSpare(int expectedCounts) {
        final ReentrantLock lock = this.workerLock;
        int expectedRunning = runningCountOf(expectedCounts);
        int expectedTotal = totalCountOf(expectedCounts);
        boolean success = false;
        boolean locked = false;
        // confirm counts while locking; CAS after obtaining lock
        try {
            for (;;) {
                int s = workerCounts;
                int tc = totalCountOf(s);
                int rc = runningCountOf(s);
                if (rc > expectedRunning || tc > expectedTotal)
                    break;
                if (!locked && !(locked = lock.tryLock()))
                    break;
                if (casWorkerCounts(s, workerCountsFor(tc+1, rc+1))) {
                    createAndStartSpare(tc);
                    success = true;
                    break;
                }
            }
        } finally {
            if (locked)
                lock.unlock();
        }
        return success;
    }

    /**
     * Adds the kth spare worker. On entry, pool counts are already
     * adjusted to reflect addition.
     */
    private void createAndStartSpare(int k) {
        ForkJoinWorkerThread w = null;
        ForkJoinWorkerThread[] ws = ensureWorkerArrayCapacity(k + 1);
        int len = ws.length;
        // Probably, we can place at slot k. If not, find empty slot
        if (k < len && ws[k] != null) {
            for (k = 0; k < len && ws[k] != null; ++k)
                ;
        }
        if (k < len && isProcessingTasks() && (w = createWorker(k)) != null) {
            ws[k] = w;
            w.start();
        }
        else
            updateWorkerCount(-1); // adjust on failure
        signalIdleWorkers();
    }

    /**
     * Suspends calling thread w if there are excess threads.  Called
     * only from sync.  Spares are enqueued in a Treiber stack using
     * the same WaitQueueNodes as barriers.  They are resumed mainly
     * in preJoin, but are also woken on pool events that require all
     * threads to check run state.
     *
     * @param w the caller
     */
    private boolean suspendIfSpare(ForkJoinWorkerThread w) {
        WaitQueueNode node = null;
        int s;
        while (parallelism < runningCountOf(s = workerCounts)) {
            if (node == null)
                node = new WaitQueueNode(0, w);
            if (casWorkerCounts(s, s-1)) { // representation-dependent
                // push onto stack
                do {} while (!casSpareStack(node.next = spareStack, node));
                // block until released by resumeSpare
                node.awaitSpareRelease();
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to pop and resume a spare thread.
     *
     * @param updateCount if true, increment running count on success
     * @return true if successful
     */
    private boolean tryResumeSpare(boolean updateCount) {
        WaitQueueNode q;
        while ((q = spareStack) != null) {
            if (casSpareStack(q, q.next)) {
                if (updateCount)
                    updateRunningCount(1);
                q.signal();
                return true;
            }
        }
        return false;
    }

    /**
     * Pops and resumes all spare threads. Same idea as ensureSync.
     *
     * @return true if any spares released
     */
    private boolean resumeAllSpares() {
        WaitQueueNode q;
        while ( (q = spareStack) != null) {
            if (casSpareStack(q, null)) {
                do {
                    updateRunningCount(1);
                    q.signal();
                } while ((q = q.next) != null);
                return true;
            }
        }
        return false;
    }

    /**
     * Pops and shuts down excessive spare threads. Call only while
     * holding lock. This is not guaranteed to eliminate all excess
     * threads, only those suspended as spares, which are the ones
     * unlikely to be needed in the future.
     */
    private void trimSpares() {
        int surplus = totalCountOf(workerCounts) - parallelism;
        WaitQueueNode q;
        while (surplus > 0 && (q = spareStack) != null) {
            if (casSpareStack(q, null)) {
                do {
                    updateRunningCount(1);
                    ForkJoinWorkerThread w = q.thread;
                    if (w != null && surplus > 0 &&
                        runningCountOf(workerCounts) > 0 && w.shutdown())
                        --surplus;
                    q.signal();
                } while ((q = q.next) != null);
            }
        }
    }

    /**
     * Interface for extending managed parallelism for tasks running
     * in {@link ForkJoinPool}s.
     *
     * <p>A {@code ManagedBlocker} provides two methods.
     * Method {@code isReleasable} must return {@code true} if
     * blocking is not necessary. Method {@code block} blocks the
     * current thread if necessary (perhaps internally invoking
     * {@code isReleasable} before actually blocking).
     *
     * <p>For example, here is a ManagedBlocker based on a
     * ReentrantLock:
     *  <pre> {@code
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
         */
        boolean isReleasable();
    }

    /**
     * Blocks in accord with the given blocker.  If the current thread
     * is a {@link ForkJoinWorkerThread}, this method possibly
     * arranges for a spare thread to be activated if necessary to
     * ensure parallelism while the current thread is blocked.
     *
     * <p>If {@code maintainParallelism} is {@code true} and the pool
     * supports it ({@link #getMaintainsParallelism}), this method
     * attempts to maintain the pool's nominal parallelism. Otherwise
     * it activates a thread only if necessary to avoid complete
     * starvation. This option may be preferable when blockages use
     * timeouts, or are almost always brief.
     *
     * <p>If the caller is not a {@link ForkJoinTask}, this method is
     * behaviorally equivalent to
     *  <pre> {@code
     * while (!blocker.isReleasable())
     *   if (blocker.block())
     *     return;
     * }</pre>
     *
     * If the caller is a {@code ForkJoinTask}, then the pool may
     * first be expanded to ensure parallelism, and later adjusted.
     *
     * @param blocker the blocker
     * @param maintainParallelism if {@code true} and supported by
     * this pool, attempt to maintain the pool's nominal parallelism;
     * otherwise activate a thread only if necessary to avoid
     * complete starvation.
     * @throws InterruptedException if blocker.block did so
     */
    public static void managedBlock(ManagedBlocker blocker,
                                    boolean maintainParallelism)
        throws InterruptedException {
        Thread t = Thread.currentThread();
        ForkJoinPool pool = ((t instanceof ForkJoinWorkerThread) ?
                             ((ForkJoinWorkerThread) t).pool : null);
        if (!blocker.isReleasable()) {
            try {
                if (pool == null ||
                    !pool.preBlock(blocker, maintainParallelism))
                    awaitBlocker(blocker);
            } finally {
                if (pool != null)
                    pool.updateRunningCount(1);
            }
        }
    }

    private static void awaitBlocker(ManagedBlocker blocker)
        throws InterruptedException {
        do {} while (!blocker.isReleasable() && !blocker.block());
    }

    // AbstractExecutorService overrides.  These rely on undocumented
    // fact that ForkJoinTask.adapt returns ForkJoinTasks that also
    // implement RunnableFuture.

    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return (RunnableFuture<T>) ForkJoinTask.adapt(runnable, value);
    }

    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return (RunnableFuture<T>) ForkJoinTask.adapt(callable);
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE = sun.misc.Unsafe.getUnsafe();
    private static final long eventCountOffset =
        objectFieldOffset("eventCount", ForkJoinPool.class);
    private static final long workerCountsOffset =
        objectFieldOffset("workerCounts", ForkJoinPool.class);
    private static final long runControlOffset =
        objectFieldOffset("runControl", ForkJoinPool.class);
    private static final long syncStackOffset =
        objectFieldOffset("syncStack",ForkJoinPool.class);
    private static final long spareStackOffset =
        objectFieldOffset("spareStack", ForkJoinPool.class);

    private boolean casEventCount(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, eventCountOffset, cmp, val);
    }
    private boolean casWorkerCounts(int cmp, int val) {
        return UNSAFE.compareAndSwapInt(this, workerCountsOffset, cmp, val);
    }
    private boolean casRunControl(int cmp, int val) {
        return UNSAFE.compareAndSwapInt(this, runControlOffset, cmp, val);
    }
    private boolean casSpareStack(WaitQueueNode cmp, WaitQueueNode val) {
        return UNSAFE.compareAndSwapObject(this, spareStackOffset, cmp, val);
    }
    private boolean casBarrierStack(WaitQueueNode cmp, WaitQueueNode val) {
        return UNSAFE.compareAndSwapObject(this, syncStackOffset, cmp, val);
    }

    private static long objectFieldOffset(String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }
}
