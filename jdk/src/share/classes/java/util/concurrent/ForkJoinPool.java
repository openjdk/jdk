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
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An {@link ExecutorService} for running {@link ForkJoinTask}s.
 * A {@code ForkJoinPool} provides the entry point for submissions
 * from non-{@code ForkJoinTask} clients, as well as management and
 * monitoring operations.
 *
 * <p>A {@code ForkJoinPool} differs from other kinds of {@link
 * ExecutorService} mainly by virtue of employing
 * <em>work-stealing</em>: all threads in the pool attempt to find and
 * execute subtasks created by other active tasks (eventually blocking
 * waiting for work if none exist). This enables efficient processing
 * when most tasks spawn other subtasks (as do most {@code
 * ForkJoinTask}s). When setting <em>asyncMode</em> to true in
 * constructors, {@code ForkJoinPool}s may also be appropriate for use
 * with event-style tasks that are never joined.
 *
 * <p>A {@code ForkJoinPool} is constructed with a given target
 * parallelism level; by default, equal to the number of available
 * processors. The pool attempts to maintain enough active (or
 * available) threads by dynamically adding, suspending, or resuming
 * internal worker threads, even if some tasks are stalled waiting to
 * join others. However, no such adjustments are guaranteed in the
 * face of blocked IO or other unmanaged synchronization. The nested
 * {@link ManagedBlocker} interface enables extension of the kinds of
 * synchronization accommodated.
 *
 * <p>In addition to execution and lifecycle control methods, this
 * class provides status check methods (for example
 * {@link #getStealCount}) that are intended to aid in developing,
 * tuning, and monitoring fork/join applications. Also, method
 * {@link #toString} returns indications of pool state in a
 * convenient form for informal monitoring.
 *
 * <p> As is the case with other ExecutorServices, there are three
 * main task execution methods summarized in the following
 * table. These are designed to be used by clients not already engaged
 * in fork/join computations in the current pool.  The main forms of
 * these methods accept instances of {@code ForkJoinTask}, but
 * overloaded forms also allow mixed execution of plain {@code
 * Runnable}- or {@code Callable}- based activities as well.  However,
 * tasks that are already executing in a pool should normally
 * <em>NOT</em> use these pool execution methods, but instead use the
 * within-computation forms listed in the table.
 *
 * <table BORDER CELLPADDING=3 CELLSPACING=1>
 *  <tr>
 *    <td></td>
 *    <td ALIGN=CENTER> <b>Call from non-fork/join clients</b></td>
 *    <td ALIGN=CENTER> <b>Call from within fork/join computations</b></td>
 *  </tr>
 *  <tr>
 *    <td> <b>Arrange async execution</td>
 *    <td> {@link #execute(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork}</td>
 *  </tr>
 *  <tr>
 *    <td> <b>Await and obtain result</td>
 *    <td> {@link #invoke(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#invoke}</td>
 *  </tr>
 *  <tr>
 *    <td> <b>Arrange exec and obtain Future</td>
 *    <td> {@link #submit(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork} (ForkJoinTasks <em>are</em> Futures)</td>
 *  </tr>
 * </table>
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
 * {@link RejectedExecutionException}) only when the pool is shut down
 * or internal resources have been exhausted.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class ForkJoinPool extends AbstractExecutorService {

    /*
     * Implementation Overview
     *
     * This class provides the central bookkeeping and control for a
     * set of worker threads: Submissions from non-FJ threads enter
     * into a submission queue. Workers take these tasks and typically
     * split them into subtasks that may be stolen by other workers.
     * The main work-stealing mechanics implemented in class
     * ForkJoinWorkerThread give first priority to processing tasks
     * from their own queues (LIFO or FIFO, depending on mode), then
     * to randomized FIFO steals of tasks in other worker queues, and
     * lastly to new submissions. These mechanics do not consider
     * affinities, loads, cache localities, etc, so rarely provide the
     * best possible performance on a given machine, but portably
     * provide good throughput by averaging over these factors.
     * (Further, even if we did try to use such information, we do not
     * usually have a basis for exploiting it. For example, some sets
     * of tasks profit from cache affinities, but others are harmed by
     * cache pollution effects.)
     *
     * Beyond work-stealing support and essential bookkeeping, the
     * main responsibility of this framework is to take actions when
     * one worker is waiting to join a task stolen (or always held by)
     * another.  Because we are multiplexing many tasks on to a pool
     * of workers, we can't just let them block (as in Thread.join).
     * We also cannot just reassign the joiner's run-time stack with
     * another and replace it later, which would be a form of
     * "continuation", that even if possible is not necessarily a good
     * idea. Given that the creation costs of most threads on most
     * systems mainly surrounds setting up runtime stacks, thread
     * creation and switching is usually not much more expensive than
     * stack creation and switching, and is more flexible). Instead we
     * combine two tactics:
     *
     *   Helping: Arranging for the joiner to execute some task that it
     *      would be running if the steal had not occurred.  Method
     *      ForkJoinWorkerThread.helpJoinTask tracks joining->stealing
     *      links to try to find such a task.
     *
     *   Compensating: Unless there are already enough live threads,
     *      method helpMaintainParallelism() may create or
     *      re-activate a spare thread to compensate for blocked
     *      joiners until they unblock.
     *
     * It is impossible to keep exactly the target (parallelism)
     * number of threads running at any given time.  Determining
     * existence of conservatively safe helping targets, the
     * availability of already-created spares, and the apparent need
     * to create new spares are all racy and require heuristic
     * guidance, so we rely on multiple retries of each.  Compensation
     * occurs in slow-motion. It is triggered only upon timeouts of
     * Object.wait used for joins. This reduces poor decisions that
     * would otherwise be made when threads are waiting for others
     * that are stalled because of unrelated activities such as
     * garbage collection.
     *
     * The ManagedBlocker extension API can't use helping so relies
     * only on compensation in method awaitBlocker.
     *
     * The main throughput advantages of work-stealing stem from
     * decentralized control -- workers mostly steal tasks from each
     * other. We do not want to negate this by creating bottlenecks
     * implementing other management responsibilities. So we use a
     * collection of techniques that avoid, reduce, or cope well with
     * contention. These entail several instances of bit-packing into
     * CASable fields to maintain only the minimally required
     * atomicity. To enable such packing, we restrict maximum
     * parallelism to (1<<15)-1 (enabling twice this (to accommodate
     * unbalanced increments and decrements) to fit into a 16 bit
     * field, which is far in excess of normal operating range.  Even
     * though updates to some of these bookkeeping fields do sometimes
     * contend with each other, they don't normally cache-contend with
     * updates to others enough to warrant memory padding or
     * isolation. So they are all held as fields of ForkJoinPool
     * objects.  The main capabilities are as follows:
     *
     * 1. Creating and removing workers. Workers are recorded in the
     * "workers" array. This is an array as opposed to some other data
     * structure to support index-based random steals by workers.
     * Updates to the array recording new workers and unrecording
     * terminated ones are protected from each other by a lock
     * (workerLock) but the array is otherwise concurrently readable,
     * and accessed directly by workers. To simplify index-based
     * operations, the array size is always a power of two, and all
     * readers must tolerate null slots. Currently, all worker thread
     * creation is on-demand, triggered by task submissions,
     * replacement of terminated workers, and/or compensation for
     * blocked workers. However, all other support code is set up to
     * work with other policies.
     *
     * To ensure that we do not hold on to worker references that
     * would prevent GC, ALL accesses to workers are via indices into
     * the workers array (which is one source of some of the unusual
     * code constructions here). In essence, the workers array serves
     * as a WeakReference mechanism. Thus for example the event queue
     * stores worker indices, not worker references. Access to the
     * workers in associated methods (for example releaseEventWaiters)
     * must both index-check and null-check the IDs. All such accesses
     * ignore bad IDs by returning out early from what they are doing,
     * since this can only be associated with shutdown, in which case
     * it is OK to give up. On termination, we just clobber these
     * data structures without trying to use them.
     *
     * 2. Bookkeeping for dynamically adding and removing workers. We
     * aim to approximately maintain the given level of parallelism.
     * When some workers are known to be blocked (on joins or via
     * ManagedBlocker), we may create or resume others to take their
     * place until they unblock (see below). Implementing this
     * requires counts of the number of "running" threads (i.e., those
     * that are neither blocked nor artificially suspended) as well as
     * the total number.  These two values are packed into one field,
     * "workerCounts" because we need accurate snapshots when deciding
     * to create, resume or suspend.  Note however that the
     * correspondence of these counts to reality is not guaranteed. In
     * particular updates for unblocked threads may lag until they
     * actually wake up.
     *
     * 3. Maintaining global run state. The run state of the pool
     * consists of a runLevel (SHUTDOWN, TERMINATING, etc) similar to
     * those in other Executor implementations, as well as a count of
     * "active" workers -- those that are, or soon will be, or
     * recently were executing tasks. The runLevel and active count
     * are packed together in order to correctly trigger shutdown and
     * termination. Without care, active counts can be subject to very
     * high contention.  We substantially reduce this contention by
     * relaxing update rules.  A worker must claim active status
     * prospectively, by activating if it sees that a submitted or
     * stealable task exists (it may find after activating that the
     * task no longer exists). It stays active while processing this
     * task (if it exists) and any other local subtasks it produces,
     * until it cannot find any other tasks. It then tries
     * inactivating (see method preStep), but upon update contention
     * instead scans for more tasks, later retrying inactivation if it
     * doesn't find any.
     *
     * 4. Managing idle workers waiting for tasks. We cannot let
     * workers spin indefinitely scanning for tasks when none are
     * available. On the other hand, we must quickly prod them into
     * action when new tasks are submitted or generated.  We
     * park/unpark these idle workers using an event-count scheme.
     * Field eventCount is incremented upon events that may enable
     * workers that previously could not find a task to now find one:
     * Submission of a new task to the pool, or another worker pushing
     * a task onto a previously empty queue.  (We also use this
     * mechanism for configuration and termination actions that
     * require wakeups of idle workers).  Each worker maintains its
     * last known event count, and blocks when a scan for work did not
     * find a task AND its lastEventCount matches the current
     * eventCount. Waiting idle workers are recorded in a variant of
     * Treiber stack headed by field eventWaiters which, when nonzero,
     * encodes the thread index and count awaited for by the worker
     * thread most recently calling eventSync. This thread in turn has
     * a record (field nextEventWaiter) for the next waiting worker.
     * In addition to allowing simpler decisions about need for
     * wakeup, the event count bits in eventWaiters serve the role of
     * tags to avoid ABA errors in Treiber stacks. Upon any wakeup,
     * released threads also try to release at most two others.  The
     * net effect is a tree-like diffusion of signals, where released
     * threads (and possibly others) help with unparks.  To further
     * reduce contention effects a bit, failed CASes to increment
     * field eventCount are tolerated without retries in signalWork.
     * Conceptually they are merged into the same event, which is OK
     * when their only purpose is to enable workers to scan for work.
     *
     * 5. Managing suspension of extra workers. When a worker notices
     * (usually upon timeout of a wait()) that there are too few
     * running threads, we may create a new thread to maintain
     * parallelism level, or at least avoid starvation. Usually, extra
     * threads are needed for only very short periods, yet join
     * dependencies are such that we sometimes need them in
     * bursts. Rather than create new threads each time this happens,
     * we suspend no-longer-needed extra ones as "spares". For most
     * purposes, we don't distinguish "extra" spare threads from
     * normal "core" threads: On each call to preStep (the only point
     * at which we can do this) a worker checks to see if there are
     * now too many running workers, and if so, suspends itself.
     * Method helpMaintainParallelism looks for suspended threads to
     * resume before considering creating a new replacement. The
     * spares themselves are encoded on another variant of a Treiber
     * Stack, headed at field "spareWaiters".  Note that the use of
     * spares is intrinsically racy.  One thread may become a spare at
     * about the same time as another is needlessly being created. We
     * counteract this and related slop in part by requiring resumed
     * spares to immediately recheck (in preStep) to see whether they
     * should re-suspend.
     *
     * 6. Killing off unneeded workers. A timeout mechanism is used to
     * shed unused workers: The oldest (first) event queue waiter uses
     * a timed rather than hard wait. When this wait times out without
     * a normal wakeup, it tries to shutdown any one (for convenience
     * the newest) other spare or event waiter via
     * tryShutdownUnusedWorker. This eventually reduces the number of
     * worker threads to a minimum of one after a long enough period
     * without use.
     *
     * 7. Deciding when to create new workers. The main dynamic
     * control in this class is deciding when to create extra threads
     * in method helpMaintainParallelism. We would like to keep
     * exactly #parallelism threads running, which is an impossible
     * task. We always need to create one when the number of running
     * threads would become zero and all workers are busy. Beyond
     * this, we must rely on heuristics that work well in the
     * presence of transient phenomena such as GC stalls, dynamic
     * compilation, and wake-up lags. These transients are extremely
     * common -- we are normally trying to fully saturate the CPUs on
     * a machine, so almost any activity other than running tasks
     * impedes accuracy. Our main defense is to allow parallelism to
     * lapse for a while during joins, and use a timeout to see if,
     * after the resulting settling, there is still a need for
     * additional workers.  This also better copes with the fact that
     * some of the methods in this class tend to never become compiled
     * (but are interpreted), so some components of the entire set of
     * controls might execute 100 times faster than others. And
     * similarly for cases where the apparent lack of work is just due
     * to GC stalls and other transient system activity.
     *
     * Beware that there is a lot of representation-level coupling
     * among classes ForkJoinPool, ForkJoinWorkerThread, and
     * ForkJoinTask.  For example, direct access to "workers" array by
     * workers, and direct access to ForkJoinTask.status by both
     * ForkJoinPool and ForkJoinWorkerThread.  There is little point
     * trying to reduce this, since any associated future changes in
     * representations will need to be accompanied by algorithmic
     * changes anyway.
     *
     * Style notes: There are lots of inline assignments (of form
     * "while ((local = field) != 0)") which are usually the simplest
     * way to ensure the required read orderings (which are sometimes
     * critical). Also several occurrences of the unusual "do {}
     * while (!cas...)" which is the simplest way to force an update of
     * a CAS'ed variable. There are also other coding oddities that
     * help some methods perform reasonably even when interpreted (not
     * compiled), at the expense of some messy constructions that
     * reduce byte code counts.
     *
     * The order of declarations in this file is: (1) statics (2)
     * fields (along with constants used when unpacking some of them)
     * (3) internal control methods (4) callbacks and other support
     * for ForkJoinTask and ForkJoinWorkerThread classes, (5) exported
     * methods (plus a few little helpers).
     */

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
    static class DefaultForkJoinWorkerThreadFactory
        implements ForkJoinWorkerThreadFactory {
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new ForkJoinWorkerThread(pool);
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
     * The time to block in a join (see awaitJoin) before checking if
     * a new worker should be (re)started to maintain parallelism
     * level. The value should be short enough to maintain global
     * responsiveness and progress but long enough to avoid
     * counterproductive firings during GC stalls or unrelated system
     * activity, and to not bog down systems with continual re-firings
     * on GCs or legitimately long waits.
     */
    private static final long JOIN_TIMEOUT_MILLIS = 250L; // 4 per second

    /**
     * The wakeup interval (in nanoseconds) for the oldest worker
     * waiting for an event to invoke tryShutdownUnusedWorker to
     * shrink the number of workers.  The exact value does not matter
     * too much. It must be short enough to release resources during
     * sustained periods of idleness, but not so short that threads
     * are continually re-created.
     */
    private static final long SHRINK_RATE_NANOS =
        30L * 1000L * 1000L * 1000L; // 2 per minute

    /**
     * Absolute bound for parallelism level. Twice this number plus
     * one (i.e., 0xfff) must fit into a 16bit field to enable
     * word-packing for some counts and indices.
     */
    private static final int MAX_WORKERS   = 0x7fff;

    /**
     * Array holding all worker threads in the pool.  Array size must
     * be a power of two.  Updates and replacements are protected by
     * workerLock, but the array is always kept in a consistent enough
     * state to be randomly accessed without locking by workers
     * performing work-stealing, as well as other traversal-based
     * methods in this class. All readers must tolerate that some
     * array slots may be null.
     */
    volatile ForkJoinWorkerThread[] workers;

    /**
     * Queue for external submissions.
     */
    private final LinkedTransferQueue<ForkJoinTask<?>> submissionQueue;

    /**
     * Lock protecting updates to workers array.
     */
    private final ReentrantLock workerLock;

    /**
     * Latch released upon termination.
     */
    private final Phaser termination;

    /**
     * Creation factory for worker threads.
     */
    private final ForkJoinWorkerThreadFactory factory;

    /**
     * Sum of per-thread steal counts, updated only when threads are
     * idle or terminating.
     */
    private volatile long stealCount;

    /**
     * Encoded record of top of Treiber stack of threads waiting for
     * events. The top 32 bits contain the count being waited for. The
     * bottom 16 bits contains one plus the pool index of waiting
     * worker thread. (Bits 16-31 are unused.)
     */
    private volatile long eventWaiters;

    private static final int EVENT_COUNT_SHIFT = 32;
    private static final int WAITER_ID_MASK    = (1 << 16) - 1;

    /**
     * A counter for events that may wake up worker threads:
     *   - Submission of a new task to the pool
     *   - A worker pushing a task on an empty queue
     *   - termination
     */
    private volatile int eventCount;

    /**
     * Encoded record of top of Treiber stack of spare threads waiting
     * for resumption. The top 16 bits contain an arbitrary count to
     * avoid ABA effects. The bottom 16bits contains one plus the pool
     * index of waiting worker thread.
     */
    private volatile int spareWaiters;

    private static final int SPARE_COUNT_SHIFT = 16;
    private static final int SPARE_ID_MASK     = (1 << 16) - 1;

    /**
     * Lifecycle control. The low word contains the number of workers
     * that are (probably) executing tasks. This value is atomically
     * incremented before a worker gets a task to run, and decremented
     * when a worker has no tasks and cannot find any.  Bits 16-18
     * contain runLevel value. When all are zero, the pool is
     * running. Level transitions are monotonic (running -> shutdown
     * -> terminating -> terminated) so each transition adds a bit.
     * These are bundled together to ensure consistent read for
     * termination checks (i.e., that runLevel is at least SHUTDOWN
     * and active threads is zero).
     *
     * Notes: Most direct CASes are dependent on these bitfield
     * positions.  Also, this field is non-private to enable direct
     * performance-sensitive CASes in ForkJoinWorkerThread.
     */
    volatile int runState;

    // Note: The order among run level values matters.
    private static final int RUNLEVEL_SHIFT     = 16;
    private static final int SHUTDOWN           = 1 << RUNLEVEL_SHIFT;
    private static final int TERMINATING        = 1 << (RUNLEVEL_SHIFT + 1);
    private static final int TERMINATED         = 1 << (RUNLEVEL_SHIFT + 2);
    private static final int ACTIVE_COUNT_MASK  = (1 << RUNLEVEL_SHIFT) - 1;

    /**
     * Holds number of total (i.e., created and not yet terminated)
     * and running (i.e., not blocked on joins or other managed sync)
     * threads, packed together to ensure consistent snapshot when
     * making decisions about creating and suspending spare
     * threads. Updated only by CAS. Note that adding a new worker
     * requires incrementing both counts, since workers start off in
     * running state.
     */
    private volatile int workerCounts;

    private static final int TOTAL_COUNT_SHIFT  = 16;
    private static final int RUNNING_COUNT_MASK = (1 << TOTAL_COUNT_SHIFT) - 1;
    private static final int ONE_RUNNING        = 1;
    private static final int ONE_TOTAL          = 1 << TOTAL_COUNT_SHIFT;

    /**
     * The target parallelism level.
     * Accessed directly by ForkJoinWorkerThreads.
     */
    final int parallelism;

    /**
     * True if use local fifo, not default lifo, for local polling
     * Read by, and replicated by ForkJoinWorkerThreads
     */
    final boolean locallyFifo;

    /**
     * The uncaught exception handler used when any worker abruptly
     * terminates.
     */
    private final Thread.UncaughtExceptionHandler ueh;

    /**
     * Pool number, just for assigning useful names to worker threads
     */
    private final int poolNumber;

    // Utilities for CASing fields. Note that most of these
    // are usually manually inlined by callers

    /**
     * Increments running count part of workerCounts.
     */
    final void incrementRunningCount() {
        int c;
        do {} while (!UNSAFE.compareAndSwapInt(this, workerCountsOffset,
                                               c = workerCounts,
                                               c + ONE_RUNNING));
    }

    /**
     * Tries to increment running count part of workerCounts.
     */
    final boolean tryIncrementRunningCount() {
        int c;
        return UNSAFE.compareAndSwapInt(this, workerCountsOffset,
                                        c = workerCounts,
                                        c + ONE_RUNNING);
    }

    /**
     * Tries to decrement running count unless already zero.
     */
    final boolean tryDecrementRunningCount() {
        int wc = workerCounts;
        if ((wc & RUNNING_COUNT_MASK) == 0)
            return false;
        return UNSAFE.compareAndSwapInt(this, workerCountsOffset,
                                        wc, wc - ONE_RUNNING);
    }

    /**
     * Forces decrement of encoded workerCounts, awaiting nonzero if
     * (rarely) necessary when other count updates lag.
     *
     * @param dr -- either zero or ONE_RUNNING
     * @param dt -- either zero or ONE_TOTAL
     */
    private void decrementWorkerCounts(int dr, int dt) {
        for (;;) {
            int wc = workerCounts;
            if ((wc & RUNNING_COUNT_MASK)  - dr < 0 ||
                (wc >>> TOTAL_COUNT_SHIFT) - dt < 0) {
                if ((runState & TERMINATED) != 0)
                    return; // lagging termination on a backout
                Thread.yield();
            }
            if (UNSAFE.compareAndSwapInt(this, workerCountsOffset,
                                         wc, wc - (dr + dt)))
                return;
        }
    }

    /**
     * Tries decrementing active count; fails on contention.
     * Called when workers cannot find tasks to run.
     */
    final boolean tryDecrementActiveCount() {
        int c;
        return UNSAFE.compareAndSwapInt(this, runStateOffset,
                                        c = runState, c - 1);
    }

    /**
     * Advances to at least the given level. Returns true if not
     * already in at least the given level.
     */
    private boolean advanceRunLevel(int level) {
        for (;;) {
            int s = runState;
            if ((s & level) != 0)
                return false;
            if (UNSAFE.compareAndSwapInt(this, runStateOffset, s, s | level))
                return true;
        }
    }

    // workers array maintenance

    /**
     * Records and returns a workers array index for new worker.
     */
    private int recordWorker(ForkJoinWorkerThread w) {
        // Try using slot totalCount-1. If not available, scan and/or resize
        int k = (workerCounts >>> TOTAL_COUNT_SHIFT) - 1;
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            int n = ws.length;
            if (k < 0 || k >= n || ws[k] != null) {
                for (k = 0; k < n && ws[k] != null; ++k)
                    ;
                if (k == n)
                    ws = workers = Arrays.copyOf(ws, n << 1);
            }
            ws[k] = w;
            int c = eventCount; // advance event count to ensure visibility
            UNSAFE.compareAndSwapInt(this, eventCountOffset, c, c+1);
        } finally {
            lock.unlock();
        }
        return k;
    }

    /**
     * Nulls out record of worker in workers array.
     */
    private void forgetWorker(ForkJoinWorkerThread w) {
        int idx = w.poolIndex;
        // Locking helps method recordWorker avoid unnecessary expansion
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            if (idx >= 0 && idx < ws.length && ws[idx] == w) // verify
                ws[idx] = null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Final callback from terminating worker.  Removes record of
     * worker from array, and adjusts counts. If pool is shutting
     * down, tries to complete termination.
     *
     * @param w the worker
     */
    final void workerTerminated(ForkJoinWorkerThread w) {
        forgetWorker(w);
        decrementWorkerCounts(w.isTrimmed() ? 0 : ONE_RUNNING, ONE_TOTAL);
        while (w.stealCount != 0) // collect final count
            tryAccumulateStealCount(w);
        tryTerminate(false);
    }

    // Waiting for and signalling events

    /**
     * Releases workers blocked on a count not equal to current count.
     * Normally called after precheck that eventWaiters isn't zero to
     * avoid wasted array checks. Gives up upon a change in count or
     * upon releasing four workers, letting others take over.
     */
    private void releaseEventWaiters() {
        ForkJoinWorkerThread[] ws = workers;
        int n = ws.length;
        long h = eventWaiters;
        int ec = eventCount;
        int releases = 4;
        ForkJoinWorkerThread w; int id;
        while ((id = (((int)h) & WAITER_ID_MASK) - 1) >= 0 &&
               (int)(h >>> EVENT_COUNT_SHIFT) != ec &&
               id < n && (w = ws[id]) != null) {
            if (UNSAFE.compareAndSwapLong(this, eventWaitersOffset,
                                          h,  w.nextWaiter)) {
                LockSupport.unpark(w);
                if (--releases == 0)
                    break;
            }
            if (eventCount != ec)
                break;
            h = eventWaiters;
        }
    }

    /**
     * Tries to advance eventCount and releases waiters. Called only
     * from workers.
     */
    final void signalWork() {
        int c; // try to increment event count -- CAS failure OK
        UNSAFE.compareAndSwapInt(this, eventCountOffset, c = eventCount, c+1);
        if (eventWaiters != 0L)
            releaseEventWaiters();
    }

    /**
     * Adds the given worker to event queue and blocks until
     * terminating or event count advances from the given value
     *
     * @param w the calling worker thread
     * @param ec the count
     */
    private void eventSync(ForkJoinWorkerThread w, int ec) {
        long nh = (((long)ec) << EVENT_COUNT_SHIFT) | ((long)(w.poolIndex+1));
        long h;
        while ((runState < SHUTDOWN || !tryTerminate(false)) &&
               (((int)(h = eventWaiters) & WAITER_ID_MASK) == 0 ||
                (int)(h >>> EVENT_COUNT_SHIFT) == ec) &&
               eventCount == ec) {
            if (UNSAFE.compareAndSwapLong(this, eventWaitersOffset,
                                          w.nextWaiter = h, nh)) {
                awaitEvent(w, ec);
                break;
            }
        }
    }

    /**
     * Blocks the given worker (that has already been entered as an
     * event waiter) until terminating or event count advances from
     * the given value. The oldest (first) waiter uses a timed wait to
     * occasionally one-by-one shrink the number of workers (to a
     * minimum of one) if the pool has not been used for extended
     * periods.
     *
     * @param w the calling worker thread
     * @param ec the count
     */
    private void awaitEvent(ForkJoinWorkerThread w, int ec) {
        while (eventCount == ec) {
            if (tryAccumulateStealCount(w)) { // transfer while idle
                boolean untimed = (w.nextWaiter != 0L ||
                                   (workerCounts & RUNNING_COUNT_MASK) <= 1);
                long startTime = untimed ? 0 : System.nanoTime();
                Thread.interrupted();         // clear/ignore interrupt
                if (w.isTerminating() || eventCount != ec)
                    break;                    // recheck after clear
                if (untimed)
                    LockSupport.park(w);
                else {
                    LockSupport.parkNanos(w, SHRINK_RATE_NANOS);
                    if (eventCount != ec || w.isTerminating())
                        break;
                    if (System.nanoTime() - startTime >= SHRINK_RATE_NANOS)
                        tryShutdownUnusedWorker(ec);
                }
            }
        }
    }

    // Maintaining parallelism

    /**
     * Pushes worker onto the spare stack.
     */
    final void pushSpare(ForkJoinWorkerThread w) {
        int ns = (++w.spareCount << SPARE_COUNT_SHIFT) | (w.poolIndex + 1);
        do {} while (!UNSAFE.compareAndSwapInt(this, spareWaitersOffset,
                                               w.nextSpare = spareWaiters,ns));
    }

    /**
     * Tries (once) to resume a spare if the number of running
     * threads is less than target.
     */
    private void tryResumeSpare() {
        int sw, id;
        ForkJoinWorkerThread[] ws = workers;
        int n = ws.length;
        ForkJoinWorkerThread w;
        if ((sw = spareWaiters) != 0 &&
            (id = (sw & SPARE_ID_MASK) - 1) >= 0 &&
            id < n && (w = ws[id]) != null &&
            (runState >= TERMINATING ||
             (workerCounts & RUNNING_COUNT_MASK) < parallelism) &&
            spareWaiters == sw &&
            UNSAFE.compareAndSwapInt(this, spareWaitersOffset,
                                     sw, w.nextSpare)) {
            int c; // increment running count before resume
            do {} while (!UNSAFE.compareAndSwapInt
                         (this, workerCountsOffset,
                          c = workerCounts, c + ONE_RUNNING));
            if (w.tryUnsuspend())
                LockSupport.unpark(w);
            else   // back out if w was shutdown
                decrementWorkerCounts(ONE_RUNNING, 0);
        }
    }

    /**
     * Tries to increase the number of running workers if below target
     * parallelism: If a spare exists tries to resume it via
     * tryResumeSpare.  Otherwise, if not enough total workers or all
     * existing workers are busy, adds a new worker. In all cases also
     * helps wake up releasable workers waiting for work.
     */
    private void helpMaintainParallelism() {
        int pc = parallelism;
        int wc, rs, tc;
        while (((wc = workerCounts) & RUNNING_COUNT_MASK) < pc &&
               (rs = runState) < TERMINATING) {
            if (spareWaiters != 0)
                tryResumeSpare();
            else if ((tc = wc >>> TOTAL_COUNT_SHIFT) >= MAX_WORKERS ||
                     (tc >= pc && (rs & ACTIVE_COUNT_MASK) != tc))
                break;   // enough total
            else if (runState == rs && workerCounts == wc &&
                     UNSAFE.compareAndSwapInt(this, workerCountsOffset, wc,
                                              wc + (ONE_RUNNING|ONE_TOTAL))) {
                ForkJoinWorkerThread w = null;
                Throwable fail = null;
                try {
                    w = factory.newThread(this);
                } catch (Throwable ex) {
                    fail = ex;
                }
                if (w == null) { // null or exceptional factory return
                    decrementWorkerCounts(ONE_RUNNING, ONE_TOTAL);
                    tryTerminate(false); // handle failure during shutdown
                    // If originating from an external caller,
                    // propagate exception, else ignore
                    if (fail != null && runState < TERMINATING &&
                        !(Thread.currentThread() instanceof
                          ForkJoinWorkerThread))
                        UNSAFE.throwException(fail);
                    break;
                }
                w.start(recordWorker(w), ueh);
                if ((workerCounts >>> TOTAL_COUNT_SHIFT) >= pc)
                    break; // add at most one unless total below target
            }
        }
        if (eventWaiters != 0L)
            releaseEventWaiters();
    }

    /**
     * Callback from the oldest waiter in awaitEvent waking up after a
     * period of non-use. If all workers are idle, tries (once) to
     * shutdown an event waiter or a spare, if one exists. Note that
     * we don't need CAS or locks here because the method is called
     * only from one thread occasionally waking (and even misfires are
     * OK). Note that until the shutdown worker fully terminates,
     * workerCounts will overestimate total count, which is tolerable.
     *
     * @param ec the event count waited on by caller (to abort
     * attempt if count has since changed).
     */
    private void tryShutdownUnusedWorker(int ec) {
        if (runState == 0 && eventCount == ec) { // only trigger if all idle
            ForkJoinWorkerThread[] ws = workers;
            int n = ws.length;
            ForkJoinWorkerThread w = null;
            boolean shutdown = false;
            int sw;
            long h;
            if ((sw = spareWaiters) != 0) { // prefer killing spares
                int id = (sw & SPARE_ID_MASK) - 1;
                if (id >= 0 && id < n && (w = ws[id]) != null &&
                    UNSAFE.compareAndSwapInt(this, spareWaitersOffset,
                                             sw, w.nextSpare))
                    shutdown = true;
            }
            else if ((h = eventWaiters) != 0L) {
                long nh;
                int id = (((int)h) & WAITER_ID_MASK) - 1;
                if (id >= 0 && id < n && (w = ws[id]) != null &&
                    (nh = w.nextWaiter) != 0L && // keep at least one worker
                    UNSAFE.compareAndSwapLong(this, eventWaitersOffset, h, nh))
                    shutdown = true;
            }
            if (w != null && shutdown) {
                w.shutdown();
                LockSupport.unpark(w);
            }
        }
        releaseEventWaiters(); // in case of interference
    }

    /**
     * Callback from workers invoked upon each top-level action (i.e.,
     * stealing a task or taking a submission and running it).
     * Performs one or more of the following:
     *
     * 1. If the worker is active and either did not run a task
     *    or there are too many workers, try to set its active status
     *    to inactive and update activeCount. On contention, we may
     *    try again in this or a subsequent call.
     *
     * 2. If not enough total workers, help create some.
     *
     * 3. If there are too many running workers, suspend this worker
     *    (first forcing inactive if necessary).  If it is not needed,
     *    it may be shutdown while suspended (via
     *    tryShutdownUnusedWorker).  Otherwise, upon resume it
     *    rechecks running thread count and need for event sync.
     *
     * 4. If worker did not run a task, await the next task event via
     *    eventSync if necessary (first forcing inactivation), upon
     *    which the worker may be shutdown via
     *    tryShutdownUnusedWorker.  Otherwise, help release any
     *    existing event waiters that are now releasable,
     *
     * @param w the worker
     * @param ran true if worker ran a task since last call to this method
     */
    final void preStep(ForkJoinWorkerThread w, boolean ran) {
        int wec = w.lastEventCount;
        boolean active = w.active;
        boolean inactivate = false;
        int pc = parallelism;
        while (w.runState == 0) {
            int rs = runState;
            if (rs >= TERMINATING) {           // propagate shutdown
                w.shutdown();
                break;
            }
            if ((inactivate || (active && (rs & ACTIVE_COUNT_MASK) >= pc)) &&
                UNSAFE.compareAndSwapInt(this, runStateOffset, rs, --rs)) {
                inactivate = active = w.active = false;
                if (rs == SHUTDOWN) {          // all inactive and shut down
                    tryTerminate(false);
                    continue;
                }
            }
            int wc = workerCounts;             // try to suspend as spare
            if ((wc & RUNNING_COUNT_MASK) > pc) {
                if (!(inactivate |= active) && // must inactivate to suspend
                    workerCounts == wc &&
                    UNSAFE.compareAndSwapInt(this, workerCountsOffset,
                                             wc, wc - ONE_RUNNING))
                    w.suspendAsSpare();
            }
            else if ((wc >>> TOTAL_COUNT_SHIFT) < pc)
                helpMaintainParallelism();     // not enough workers
            else if (ran)
                break;
            else {
                long h = eventWaiters;
                int ec = eventCount;
                if (h != 0L && (int)(h >>> EVENT_COUNT_SHIFT) != ec)
                    releaseEventWaiters();     // release others before waiting
                else if (ec != wec) {
                    w.lastEventCount = ec;     // no need to wait
                    break;
                }
                else if (!(inactivate |= active))
                    eventSync(w, wec);         // must inactivate before sync
            }
        }
    }

    /**
     * Helps and/or blocks awaiting join of the given task.
     * See above for explanation.
     *
     * @param joinMe the task to join
     * @param worker the current worker thread
     * @param timed true if wait should time out
     * @param nanos timeout value if timed
     */
    final void awaitJoin(ForkJoinTask<?> joinMe, ForkJoinWorkerThread worker,
                         boolean timed, long nanos) {
        long startTime = timed ? System.nanoTime() : 0L;
        int retries = 2 + (parallelism >> 2); // #helpJoins before blocking
        boolean running = true;               // false when count decremented
        while (joinMe.status >= 0) {
            if (runState >= TERMINATING) {
                joinMe.cancelIgnoringExceptions();
                break;
            }
            running = worker.helpJoinTask(joinMe, running);
            if (joinMe.status < 0)
                break;
            if (retries > 0) {
                --retries;
                continue;
            }
            int wc = workerCounts;
            if ((wc & RUNNING_COUNT_MASK) != 0) {
                if (running) {
                    if (!UNSAFE.compareAndSwapInt(this, workerCountsOffset,
                                                  wc, wc - ONE_RUNNING))
                        continue;
                    running = false;
                }
                long h = eventWaiters;
                if (h != 0L && (int)(h >>> EVENT_COUNT_SHIFT) != eventCount)
                    releaseEventWaiters();
                if ((workerCounts & RUNNING_COUNT_MASK) != 0) {
                    long ms; int ns;
                    if (!timed) {
                        ms = JOIN_TIMEOUT_MILLIS;
                        ns = 0;
                    }
                    else { // at most JOIN_TIMEOUT_MILLIS per wait
                        long nt = nanos - (System.nanoTime() - startTime);
                        if (nt <= 0L)
                            break;
                        ms = nt / 1000000;
                        if (ms > JOIN_TIMEOUT_MILLIS) {
                            ms = JOIN_TIMEOUT_MILLIS;
                            ns = 0;
                        }
                        else
                            ns = (int) (nt % 1000000);
                    }
                    joinMe.internalAwaitDone(ms, ns);
                }
                if (joinMe.status < 0)
                    break;
            }
            helpMaintainParallelism();
        }
        if (!running) {
            int c;
            do {} while (!UNSAFE.compareAndSwapInt
                         (this, workerCountsOffset,
                          c = workerCounts, c + ONE_RUNNING));
        }
    }

    /**
     * Same idea as awaitJoin, but no helping, retries, or timeouts.
     */
    final void awaitBlocker(ManagedBlocker blocker)
        throws InterruptedException {
        while (!blocker.isReleasable()) {
            int wc = workerCounts;
            if ((wc & RUNNING_COUNT_MASK) == 0)
                helpMaintainParallelism();
            else if (UNSAFE.compareAndSwapInt(this, workerCountsOffset,
                                              wc, wc - ONE_RUNNING)) {
                try {
                    while (!blocker.isReleasable()) {
                        long h = eventWaiters;
                        if (h != 0L &&
                            (int)(h >>> EVENT_COUNT_SHIFT) != eventCount)
                            releaseEventWaiters();
                        else if ((workerCounts & RUNNING_COUNT_MASK) == 0 &&
                                 runState < TERMINATING)
                            helpMaintainParallelism();
                        else if (blocker.block())
                            break;
                    }
                } finally {
                    int c;
                    do {} while (!UNSAFE.compareAndSwapInt
                                 (this, workerCountsOffset,
                                  c = workerCounts, c + ONE_RUNNING));
                }
                break;
            }
        }
    }

    /**
     * Possibly initiates and/or completes termination.
     *
     * @param now if true, unconditionally terminate, else only
     * if shutdown and empty queue and no active workers
     * @return true if now terminating or terminated
     */
    private boolean tryTerminate(boolean now) {
        if (now)
            advanceRunLevel(SHUTDOWN); // ensure at least SHUTDOWN
        else if (runState < SHUTDOWN ||
                 !submissionQueue.isEmpty() ||
                 (runState & ACTIVE_COUNT_MASK) != 0)
            return false;

        if (advanceRunLevel(TERMINATING))
            startTerminating();

        // Finish now if all threads terminated; else in some subsequent call
        if ((workerCounts >>> TOTAL_COUNT_SHIFT) == 0) {
            advanceRunLevel(TERMINATED);
            termination.forceTermination();
        }
        return true;
    }

    /**
     * Actions on transition to TERMINATING
     *
     * Runs up to four passes through workers: (0) shutting down each
     * (without waking up if parked) to quickly spread notifications
     * without unnecessary bouncing around event queues etc (1) wake
     * up and help cancel tasks (2) interrupt (3) mop up races with
     * interrupted workers
     */
    private void startTerminating() {
        cancelSubmissions();
        for (int passes = 0; passes < 4 && workerCounts != 0; ++passes) {
            int c; // advance event count
            UNSAFE.compareAndSwapInt(this, eventCountOffset,
                                     c = eventCount, c+1);
            eventWaiters = 0L; // clobber lists
            spareWaiters = 0;
            for (ForkJoinWorkerThread w : workers) {
                if (w != null) {
                    w.shutdown();
                    if (passes > 0 && !w.isTerminated()) {
                        w.cancelTasks();
                        LockSupport.unpark(w);
                        if (passes > 1 && !w.isInterrupted()) {
                            try {
                                w.interrupt();
                            } catch (SecurityException ignore) {
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Clears out and cancels submissions, ignoring exceptions.
     */
    private void cancelSubmissions() {
        ForkJoinTask<?> task;
        while ((task = submissionQueue.poll()) != null) {
            try {
                task.cancel(false);
            } catch (Throwable ignore) {
            }
        }
    }

    // misc support for ForkJoinWorkerThread

    /**
     * Returns pool number.
     */
    final int getPoolNumber() {
        return poolNumber;
    }

    /**
     * Tries to accumulate steal count from a worker, clearing
     * the worker's value if successful.
     *
     * @return true if worker steal count now zero
     */
    final boolean tryAccumulateStealCount(ForkJoinWorkerThread w) {
        int sc = w.stealCount;
        long c = stealCount;
        // CAS even if zero, for fence effects
        if (UNSAFE.compareAndSwapLong(this, stealCountOffset, c, c + sc)) {
            if (sc != 0)
                w.stealCount = 0;
            return true;
        }
        return sc == 0;
    }

    /**
     * Returns the approximate (non-atomic) number of idle threads per
     * active thread.
     */
    final int idlePerActive() {
        int pc = parallelism; // use parallelism, not rc
        int ac = runState;    // no mask -- artificially boosts during shutdown
        // Use exact results for small values, saturate past 4
        return ((pc <= ac) ? 0 :
                (pc >>> 1 <= ac) ? 1 :
                (pc >>> 2 <= ac) ? 3 :
                pc >>> 3);
    }

    // Public and protected methods

    // Constructors

    /**
     * Creates a {@code ForkJoinPool} with parallelism equal to {@link
     * java.lang.Runtime#availableProcessors}, using the {@linkplain
     * #defaultForkJoinWorkerThreadFactory default thread factory},
     * no UncaughtExceptionHandler, and non-async LIFO processing mode.
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool() {
        this(Runtime.getRuntime().availableProcessors(),
             defaultForkJoinWorkerThreadFactory, null, false);
    }

    /**
     * Creates a {@code ForkJoinPool} with the indicated parallelism
     * level, the {@linkplain
     * #defaultForkJoinWorkerThreadFactory default thread factory},
     * no UncaughtExceptionHandler, and non-async LIFO processing mode.
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
        this(parallelism, defaultForkJoinWorkerThreadFactory, null, false);
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parameters.
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
                        Thread.UncaughtExceptionHandler handler,
                        boolean asyncMode) {
        checkPermission();
        if (factory == null)
            throw new NullPointerException();
        if (parallelism <= 0 || parallelism > MAX_WORKERS)
            throw new IllegalArgumentException();
        this.parallelism = parallelism;
        this.factory = factory;
        this.ueh = handler;
        this.locallyFifo = asyncMode;
        int arraySize = initialArraySizeFor(parallelism);
        this.workers = new ForkJoinWorkerThread[arraySize];
        this.submissionQueue = new LinkedTransferQueue<ForkJoinTask<?>>();
        this.workerLock = new ReentrantLock();
        this.termination = new Phaser(1);
        this.poolNumber = poolNumberGenerator.incrementAndGet();
    }

    /**
     * Returns initial power of two size for workers array.
     * @param pc the initial parallelism level
     */
    private static int initialArraySizeFor(int pc) {
        // If possible, initially allocate enough space for one spare
        int size = pc < MAX_WORKERS ? pc + 1 : MAX_WORKERS;
        // See Hackers Delight, sec 3.2. We know MAX_WORKERS < (1 >>> 16)
        size |= size >>> 1;
        size |= size >>> 2;
        size |= size >>> 4;
        size |= size >>> 8;
        return size + 1;
    }

    // Execution methods

    /**
     * Submits task and creates, starts, or resumes some workers if necessary
     */
    private <T> void doSubmit(ForkJoinTask<T> task) {
        submissionQueue.offer(task);
        int c; // try to increment event count -- CAS failure OK
        UNSAFE.compareAndSwapInt(this, eventCountOffset, c = eventCount, c+1);
        helpMaintainParallelism();
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
        if (task == null)
            throw new NullPointerException();
        if (runState >= SHUTDOWN)
            throw new RejectedExecutionException();
        Thread t = Thread.currentThread();
        if ((t instanceof ForkJoinWorkerThread) &&
            ((ForkJoinWorkerThread)t).pool == this)
            return task.invoke();  // bypass submit if in same pool
        else {
            doSubmit(task);
            return task.join();
        }
    }

    /**
     * Unless terminating, forks task if within an ongoing FJ
     * computation in the current pool, else submits as external task.
     */
    private <T> void forkOrSubmit(ForkJoinTask<T> task) {
        if (runState >= SHUTDOWN)
            throw new RejectedExecutionException();
        Thread t = Thread.currentThread();
        if ((t instanceof ForkJoinWorkerThread) &&
            ((ForkJoinWorkerThread)t).pool == this)
            task.fork();
        else
            doSubmit(task);
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
        if (task == null)
            throw new NullPointerException();
        forkOrSubmit(task);
    }

    // AbstractExecutorService methods

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public void execute(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        ForkJoinTask<?> job;
        if (task instanceof ForkJoinTask<?>) // avoid re-wrap
            job = (ForkJoinTask<?>) task;
        else
            job = ForkJoinTask.adapt(task, null);
        forkOrSubmit(job);
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
        if (task == null)
            throw new NullPointerException();
        forkOrSubmit(task);
        return task;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        if (task == null)
            throw new NullPointerException();
        ForkJoinTask<T> job = ForkJoinTask.adapt(task);
        forkOrSubmit(job);
        return job;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        if (task == null)
            throw new NullPointerException();
        ForkJoinTask<T> job = ForkJoinTask.adapt(task, result);
        forkOrSubmit(job);
        return job;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public ForkJoinTask<?> submit(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        ForkJoinTask<?> job;
        if (task instanceof ForkJoinTask<?>) // avoid re-wrap
            job = (ForkJoinTask<?>) task;
        else
            job = ForkJoinTask.adapt(task, null);
        forkOrSubmit(job);
        return job;
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
        return ueh;
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
     * yet terminated.  The result returned by this method may differ
     * from {@link #getParallelism} when threads are created to
     * maintain parallelism when others are cooperatively blocked.
     *
     * @return the number of worker threads
     */
    public int getPoolSize() {
        return workerCounts >>> TOTAL_COUNT_SHIFT;
    }

    /**
     * Returns {@code true} if this pool uses local first-in-first-out
     * scheduling mode for forked tasks that are never joined.
     *
     * @return {@code true} if this pool uses async mode
     */
    public boolean getAsyncMode() {
        return locallyFifo;
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
        return workerCounts & RUNNING_COUNT_MASK;
    }

    /**
     * Returns an estimate of the number of threads that are currently
     * stealing or executing tasks. This method may overestimate the
     * number of active threads.
     *
     * @return the number of active threads
     */
    public int getActiveThreadCount() {
        return runState & ACTIVE_COUNT_MASK;
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
        return (runState & ACTIVE_COUNT_MASK) == 0;
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
        return stealCount;
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
        for (ForkJoinWorkerThread w : workers)
            if (w != null)
                count += w.getQueueSize();
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
        int count = submissionQueue.drainTo(c);
        for (ForkJoinWorkerThread w : workers)
            if (w != null)
                count += w.drainTasksTo(c);
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
        long st = getStealCount();
        long qt = getQueuedTaskCount();
        long qs = getQueuedSubmissionCount();
        int wc = workerCounts;
        int tc = wc >>> TOTAL_COUNT_SHIFT;
        int rc = wc & RUNNING_COUNT_MASK;
        int pc = parallelism;
        int rs = runState;
        int ac = rs & ACTIVE_COUNT_MASK;
        return super.toString() +
            "[" + runLevelToString(rs) +
            ", parallelism = " + pc +
            ", size = " + tc +
            ", active = " + ac +
            ", running = " + rc +
            ", steals = " + st +
            ", tasks = " + qt +
            ", submissions = " + qs +
            "]";
    }

    private static String runLevelToString(int s) {
        return ((s & TERMINATED) != 0 ? "Terminated" :
                ((s & TERMINATING) != 0 ? "Terminating" :
                 ((s & SHUTDOWN) != 0 ? "Shutting down" :
                  "Running")));
    }

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
        advanceRunLevel(SHUTDOWN);
        tryTerminate(false);
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
        tryTerminate(true);
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    public boolean isTerminated() {
        return runState >= TERMINATED;
    }

    /**
     * Returns {@code true} if the process of termination has
     * commenced but not yet completed.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, or are waiting for IO,
     * causing this executor not to properly terminate. (See the
     * advisory notes for class {@link ForkJoinTask} stating that
     * tasks should not normally entail blocking operations.  But if
     * they do, they must abort them on interrupt.)
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        return (runState & (TERMINATING|TERMINATED)) == TERMINATING;
    }

    /**
     * Returns true if terminating or terminated. Used by ForkJoinWorkerThread.
     */
    final boolean isAtLeastTerminating() {
        return runState >= TERMINATING;
    }

    /**
     * Returns {@code true} if this pool has been shut down.
     *
     * @return {@code true} if this pool has been shut down
     */
    public boolean isShutdown() {
        return runState >= SHUTDOWN;
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
        try {
            termination.awaitAdvanceInterruptibly(0, timeout, unit);
        } catch (TimeoutException ex) {
            return false;
        }
        return true;
    }

    /**
     * Interface for extending managed parallelism for tasks running
     * in {@link ForkJoinPool}s.
     *
     * <p>A {@code ManagedBlocker} provides two methods.  Method
     * {@code isReleasable} must return {@code true} if blocking is
     * not necessary. Method {@code block} blocks the current thread
     * if necessary (perhaps internally invoking {@code isReleasable}
     * before actually blocking). The unusual methods in this API
     * accommodate synchronizers that may, but don't usually, block
     * for long periods. Similarly, they allow more efficient internal
     * handling of cases in which additional workers may be, but
     * usually are not, needed to ensure sufficient parallelism.
     * Toward this end, implementations of method {@code isReleasable}
     * must be amenable to repeated invocation.
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
     *
     * <p>Here is a class that possibly blocks waiting for an
     * item on a given queue:
     *  <pre> {@code
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
         */
        boolean isReleasable();
    }

    /**
     * Blocks in accord with the given blocker.  If the current thread
     * is a {@link ForkJoinWorkerThread}, this method possibly
     * arranges for a spare thread to be activated if necessary to
     * ensure sufficient parallelism while the current thread is blocked.
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
     * @throws InterruptedException if blocker.block did so
     */
    public static void managedBlock(ManagedBlocker blocker)
        throws InterruptedException {
        Thread t = Thread.currentThread();
        if (t instanceof ForkJoinWorkerThread) {
            ForkJoinWorkerThread w = (ForkJoinWorkerThread) t;
            w.pool.awaitBlocker(blocker);
        }
        else {
            do {} while (!blocker.isReleasable() && !blocker.block());
        }
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
    private static final long workerCountsOffset =
        objectFieldOffset("workerCounts", ForkJoinPool.class);
    private static final long runStateOffset =
        objectFieldOffset("runState", ForkJoinPool.class);
    private static final long eventCountOffset =
        objectFieldOffset("eventCount", ForkJoinPool.class);
    private static final long eventWaitersOffset =
        objectFieldOffset("eventWaiters", ForkJoinPool.class);
    private static final long stealCountOffset =
        objectFieldOffset("stealCount", ForkJoinPool.class);
    private static final long spareWaitersOffset =
        objectFieldOffset("spareWaiters", ForkJoinPool.class);

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
