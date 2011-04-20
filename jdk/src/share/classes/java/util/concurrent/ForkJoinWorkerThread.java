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

import java.util.Collection;
import java.util.concurrent.RejectedExecutionException;

/**
 * A thread managed by a {@link ForkJoinPool}, which executes
 * {@link ForkJoinTask}s.
 * This class is subclassable solely for the sake of adding
 * functionality -- there are no overridable methods dealing with
 * scheduling or execution.  However, you can override initialization
 * and termination methods surrounding the main task processing loop.
 * If you do create such a subclass, you will also need to supply a
 * custom {@link ForkJoinPool.ForkJoinWorkerThreadFactory} to use it
 * in a {@code ForkJoinPool}.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class ForkJoinWorkerThread extends Thread {
    /*
     * Overview:
     *
     * ForkJoinWorkerThreads are managed by ForkJoinPools and perform
     * ForkJoinTasks. This class includes bookkeeping in support of
     * worker activation, suspension, and lifecycle control described
     * in more detail in the internal documentation of class
     * ForkJoinPool. And as described further below, this class also
     * includes special-cased support for some ForkJoinTask
     * methods. But the main mechanics involve work-stealing:
     *
     * Work-stealing queues are special forms of Deques that support
     * only three of the four possible end-operations -- push, pop,
     * and deq (aka steal), under the further constraints that push
     * and pop are called only from the owning thread, while deq may
     * be called from other threads.  (If you are unfamiliar with
     * them, you probably want to read Herlihy and Shavit's book "The
     * Art of Multiprocessor programming", chapter 16 describing these
     * in more detail before proceeding.)  The main work-stealing
     * queue design is roughly similar to those in the papers "Dynamic
     * Circular Work-Stealing Deque" by Chase and Lev, SPAA 2005
     * (http://research.sun.com/scalable/pubs/index.html) and
     * "Idempotent work stealing" by Michael, Saraswat, and Vechev,
     * PPoPP 2009 (http://portal.acm.org/citation.cfm?id=1504186).
     * The main differences ultimately stem from gc requirements that
     * we null out taken slots as soon as we can, to maintain as small
     * a footprint as possible even in programs generating huge
     * numbers of tasks. To accomplish this, we shift the CAS
     * arbitrating pop vs deq (steal) from being on the indices
     * ("queueBase" and "queueTop") to the slots themselves (mainly
     * via method "casSlotNull()"). So, both a successful pop and deq
     * mainly entail a CAS of a slot from non-null to null.  Because
     * we rely on CASes of references, we do not need tag bits on
     * queueBase or queueTop.  They are simple ints as used in any
     * circular array-based queue (see for example ArrayDeque).
     * Updates to the indices must still be ordered in a way that
     * guarantees that queueTop == queueBase means the queue is empty,
     * but otherwise may err on the side of possibly making the queue
     * appear nonempty when a push, pop, or deq have not fully
     * committed. Note that this means that the deq operation,
     * considered individually, is not wait-free. One thief cannot
     * successfully continue until another in-progress one (or, if
     * previously empty, a push) completes.  However, in the
     * aggregate, we ensure at least probabilistic non-blockingness.
     * If an attempted steal fails, a thief always chooses a different
     * random victim target to try next. So, in order for one thief to
     * progress, it suffices for any in-progress deq or new push on
     * any empty queue to complete.
     *
     * This approach also enables support for "async mode" where local
     * task processing is in FIFO, not LIFO order; simply by using a
     * version of deq rather than pop when locallyFifo is true (as set
     * by the ForkJoinPool).  This allows use in message-passing
     * frameworks in which tasks are never joined.  However neither
     * mode considers affinities, loads, cache localities, etc, so
     * rarely provide the best possible performance on a given
     * machine, but portably provide good throughput by averaging over
     * these factors.  (Further, even if we did try to use such
     * information, we do not usually have a basis for exploiting
     * it. For example, some sets of tasks profit from cache
     * affinities, but others are harmed by cache pollution effects.)
     *
     * When a worker would otherwise be blocked waiting to join a
     * task, it first tries a form of linear helping: Each worker
     * records (in field currentSteal) the most recent task it stole
     * from some other worker. Plus, it records (in field currentJoin)
     * the task it is currently actively joining. Method joinTask uses
     * these markers to try to find a worker to help (i.e., steal back
     * a task from and execute it) that could hasten completion of the
     * actively joined task. In essence, the joiner executes a task
     * that would be on its own local deque had the to-be-joined task
     * not been stolen. This may be seen as a conservative variant of
     * the approach in Wagner & Calder "Leapfrogging: a portable
     * technique for implementing efficient futures" SIGPLAN Notices,
     * 1993 (http://portal.acm.org/citation.cfm?id=155354). It differs
     * in that: (1) We only maintain dependency links across workers
     * upon steals, rather than use per-task bookkeeping.  This may
     * require a linear scan of workers array to locate stealers, but
     * usually doesn't because stealers leave hints (that may become
     * stale/wrong) of where to locate them. This isolates cost to
     * when it is needed, rather than adding to per-task overhead.
     * (2) It is "shallow", ignoring nesting and potentially cyclic
     * mutual steals.  (3) It is intentionally racy: field currentJoin
     * is updated only while actively joining, which means that we
     * miss links in the chain during long-lived tasks, GC stalls etc
     * (which is OK since blocking in such cases is usually a good
     * idea).  (4) We bound the number of attempts to find work (see
     * MAX_HELP) and fall back to suspending the worker and if
     * necessary replacing it with another.
     *
     * Efficient implementation of these algorithms currently relies
     * on an uncomfortable amount of "Unsafe" mechanics. To maintain
     * correct orderings, reads and writes of variable queueBase
     * require volatile ordering.  Variable queueTop need not be
     * volatile because non-local reads always follow those of
     * queueBase.  Similarly, because they are protected by volatile
     * queueBase reads, reads of the queue array and its slots by
     * other threads do not need volatile load semantics, but writes
     * (in push) require store order and CASes (in pop and deq)
     * require (volatile) CAS semantics.  (Michael, Saraswat, and
     * Vechev's algorithm has similar properties, but without support
     * for nulling slots.)  Since these combinations aren't supported
     * using ordinary volatiles, the only way to accomplish these
     * efficiently is to use direct Unsafe calls. (Using external
     * AtomicIntegers and AtomicReferenceArrays for the indices and
     * array is significantly slower because of memory locality and
     * indirection effects.)
     *
     * Further, performance on most platforms is very sensitive to
     * placement and sizing of the (resizable) queue array.  Even
     * though these queues don't usually become all that big, the
     * initial size must be large enough to counteract cache
     * contention effects across multiple queues (especially in the
     * presence of GC cardmarking). Also, to improve thread-locality,
     * queues are initialized after starting.
     */

    /**
     * Mask for pool indices encoded as shorts
     */
    private static final int  SMASK  = 0xffff;

    /**
     * Capacity of work-stealing queue array upon initialization.
     * Must be a power of two. Initial size must be at least 4, but is
     * padded to minimize cache effects.
     */
    private static final int INITIAL_QUEUE_CAPACITY = 1 << 13;

    /**
     * Maximum size for queue array. Must be a power of two
     * less than or equal to 1 << (31 - width of array entry) to
     * ensure lack of index wraparound, but is capped at a lower
     * value to help users trap runaway computations.
     */
    private static final int MAXIMUM_QUEUE_CAPACITY = 1 << 24; // 16M

    /**
     * The work-stealing queue array. Size must be a power of two.
     * Initialized when started (as oposed to when constructed), to
     * improve memory locality.
     */
    ForkJoinTask<?>[] queue;

    /**
     * The pool this thread works in. Accessed directly by ForkJoinTask.
     */
    final ForkJoinPool pool;

    /**
     * Index (mod queue.length) of next queue slot to push to or pop
     * from. It is written only by owner thread, and accessed by other
     * threads only after reading (volatile) queueBase.  Both queueTop
     * and queueBase are allowed to wrap around on overflow, but
     * (queueTop - queueBase) still estimates size.
     */
    int queueTop;

    /**
     * Index (mod queue.length) of least valid queue slot, which is
     * always the next position to steal from if nonempty.
     */
    volatile int queueBase;

    /**
     * The index of most recent stealer, used as a hint to avoid
     * traversal in method helpJoinTask. This is only a hint because a
     * worker might have had multiple steals and this only holds one
     * of them (usually the most current). Declared non-volatile,
     * relying on other prevailing sync to keep reasonably current.
     */
    int stealHint;

    /**
     * Index of this worker in pool array. Set once by pool before
     * running, and accessed directly by pool to locate this worker in
     * its workers array.
     */
    final int poolIndex;

    /**
     * Encoded record for pool task waits. Usages are always
     * surrounded by volatile reads/writes
     */
    int nextWait;

    /**
     * Complement of poolIndex, offset by count of entries of task
     * waits. Accessed by ForkJoinPool to manage event waiters.
     */
    volatile int eventCount;

    /**
     * Seed for random number generator for choosing steal victims.
     * Uses Marsaglia xorshift. Must be initialized as nonzero.
     */
    int seed;

    /**
     * Number of steals. Directly accessed (and reset) by pool when
     * idle.
     */
    int stealCount;

    /**
     * True if this worker should or did terminate
     */
    volatile boolean terminate;

    /**
     * Set to true before LockSupport.park; false on return
     */
    volatile boolean parked;

    /**
     * True if use local fifo, not default lifo, for local polling.
     * Shadows value from ForkJoinPool.
     */
    final boolean locallyFifo;

    /**
     * The task most recently stolen from another worker (or
     * submission queue).  All uses are surrounded by enough volatile
     * reads/writes to maintain as non-volatile.
     */
    ForkJoinTask<?> currentSteal;

    /**
     * The task currently being joined, set only when actively trying
     * to help other stealers in helpJoinTask. All uses are surrounded
     * by enough volatile reads/writes to maintain as non-volatile.
     */
    ForkJoinTask<?> currentJoin;

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     *
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        super(pool.nextWorkerName());
        this.pool = pool;
        int k = pool.registerWorker(this);
        poolIndex = k;
        eventCount = ~k & SMASK; // clear wait count
        locallyFifo = pool.locallyFifo;
        Thread.UncaughtExceptionHandler ueh = pool.ueh;
        if (ueh != null)
            setUncaughtExceptionHandler(ueh);
        setDaemon(true);
    }

    // Public methods

    /**
     * Returns the pool hosting this thread.
     *
     * @return the pool
     */
    public ForkJoinPool getPool() {
        return pool;
    }

    /**
     * Returns the index number of this thread in its pool.  The
     * returned value ranges from zero to the maximum number of
     * threads (minus one) that have ever been created in the pool.
     * This method may be useful for applications that track status or
     * collect results per-worker rather than per-task.
     *
     * @return the index number
     */
    public int getPoolIndex() {
        return poolIndex;
    }

    // Randomization

    /**
     * Computes next value for random victim probes and backoffs.
     * Scans don't require a very high quality generator, but also not
     * a crummy one.  Marsaglia xor-shift is cheap and works well
     * enough.  Note: This is manually inlined in FJP.scan() to avoid
     * writes inside busy loops.
     */
    private int nextSeed() {
        int r = seed;
        r ^= r << 13;
        r ^= r >>> 17;
        r ^= r << 5;
        return seed = r;
    }

    // Run State management

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
        queue = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];
        int r = pool.workerSeedGenerator.nextInt();
        seed = (r == 0) ? 1 : r; //  must be nonzero
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
        try {
            terminate = true;
            cancelTasks();
            pool.deregisterWorker(this, exception);
        } catch (Throwable ex) {        // Shouldn't ever happen
            if (exception == null)      // but if so, at least rethrown
                exception = ex;
        } finally {
            if (exception != null)
                UNSAFE.throwException(exception);
        }
    }

    /**
     * This method is required to be public, but should never be
     * called explicitly. It performs the main run loop to execute
     * {@link ForkJoinTask}s.
     */
    public void run() {
        Throwable exception = null;
        try {
            onStart();
            pool.work(this);
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            onTermination(exception);
        }
    }

    /*
     * Intrinsics-based atomic writes for queue slots. These are
     * basically the same as methods in AtomicReferenceArray, but
     * specialized for (1) ForkJoinTask elements (2) requirement that
     * nullness and bounds checks have already been performed by
     * callers and (3) effective offsets are known not to overflow
     * from int to long (because of MAXIMUM_QUEUE_CAPACITY). We don't
     * need corresponding version for reads: plain array reads are OK
     * because they are protected by other volatile reads and are
     * confirmed by CASes.
     *
     * Most uses don't actually call these methods, but instead
     * contain inlined forms that enable more predictable
     * optimization.  We don't define the version of write used in
     * pushTask at all, but instead inline there a store-fenced array
     * slot write.
     *
     * Also in most methods, as a performance (not correctness) issue,
     * we'd like to encourage compilers not to arbitrarily postpone
     * setting queueTop after writing slot.  Currently there is no
     * intrinsic for arranging this, but using Unsafe putOrderedInt
     * may be a preferable strategy on some compilers even though its
     * main effect is a pre-, not post- fence. To simplify possible
     * changes, the option is left in comments next to the associated
     * assignments.
     */

    /**
     * CASes slot i of array q from t to null. Caller must ensure q is
     * non-null and index is in range.
     */
    private static final boolean casSlotNull(ForkJoinTask<?>[] q, int i,
                                             ForkJoinTask<?> t) {
        return UNSAFE.compareAndSwapObject(q, (i << ASHIFT) + ABASE, t, null);
    }

    /**
     * Performs a volatile write of the given task at given slot of
     * array q.  Caller must ensure q is non-null and index is in
     * range. This method is used only during resets and backouts.
     */
    private static final void writeSlot(ForkJoinTask<?>[] q, int i,
                                        ForkJoinTask<?> t) {
        UNSAFE.putObjectVolatile(q, (i << ASHIFT) + ABASE, t);
    }

    // queue methods

    /**
     * Pushes a task. Call only from this thread.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final void pushTask(ForkJoinTask<?> t) {
        ForkJoinTask<?>[] q; int s, m;
        if ((q = queue) != null) {    // ignore if queue removed
            long u = (((s = queueTop) & (m = q.length - 1)) << ASHIFT) + ABASE;
            UNSAFE.putOrderedObject(q, u, t);
            queueTop = s + 1;         // or use putOrderedInt
            if ((s -= queueBase) <= 2)
                pool.signalWork();
            else if (s == m)
                growQueue();
        }
    }

    /**
     * Creates or doubles queue array.  Transfers elements by
     * emulating steals (deqs) from old array and placing, oldest
     * first, into new array.
     */
    private void growQueue() {
        ForkJoinTask<?>[] oldQ = queue;
        int size = oldQ != null ? oldQ.length << 1 : INITIAL_QUEUE_CAPACITY;
        if (size > MAXIMUM_QUEUE_CAPACITY)
            throw new RejectedExecutionException("Queue capacity exceeded");
        if (size < INITIAL_QUEUE_CAPACITY)
            size = INITIAL_QUEUE_CAPACITY;
        ForkJoinTask<?>[] q = queue = new ForkJoinTask<?>[size];
        int mask = size - 1;
        int top = queueTop;
        int oldMask;
        if (oldQ != null && (oldMask = oldQ.length - 1) >= 0) {
            for (int b = queueBase; b != top; ++b) {
                long u = ((b & oldMask) << ASHIFT) + ABASE;
                Object x = UNSAFE.getObjectVolatile(oldQ, u);
                if (x != null && UNSAFE.compareAndSwapObject(oldQ, u, x, null))
                    UNSAFE.putObjectVolatile
                        (q, ((b & mask) << ASHIFT) + ABASE, x);
            }
        }
    }

    /**
     * Tries to take a task from the base of the queue, failing if
     * empty or contended. Note: Specializations of this code appear
     * in locallyDeqTask and elsewhere.
     *
     * @return a task, or null if none or contended
     */
    final ForkJoinTask<?> deqTask() {
        ForkJoinTask<?> t; ForkJoinTask<?>[] q; int b, i;
        if (queueTop != (b = queueBase) &&
            (q = queue) != null && // must read q after b
            (i = (q.length - 1) & b) >= 0 &&
            (t = q[i]) != null && queueBase == b &&
            UNSAFE.compareAndSwapObject(q, (i << ASHIFT) + ABASE, t, null)) {
            queueBase = b + 1;
            return t;
        }
        return null;
    }

    /**
     * Tries to take a task from the base of own queue.  Called only
     * by this thread.
     *
     * @return a task, or null if none
     */
    final ForkJoinTask<?> locallyDeqTask() {
        ForkJoinTask<?> t; int m, b, i;
        ForkJoinTask<?>[] q = queue;
        if (q != null && (m = q.length - 1) >= 0) {
            while (queueTop != (b = queueBase)) {
                if ((t = q[i = m & b]) != null &&
                    queueBase == b &&
                    UNSAFE.compareAndSwapObject(q, (i << ASHIFT) + ABASE,
                                                t, null)) {
                    queueBase = b + 1;
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Returns a popped task, or null if empty.
     * Called only by this thread.
     */
    private ForkJoinTask<?> popTask() {
        int m;
        ForkJoinTask<?>[] q = queue;
        if (q != null && (m = q.length - 1) >= 0) {
            for (int s; (s = queueTop) != queueBase;) {
                int i = m & --s;
                long u = (i << ASHIFT) + ABASE; // raw offset
                ForkJoinTask<?> t = q[i];
                if (t == null)   // lost to stealer
                    break;
                if (UNSAFE.compareAndSwapObject(q, u, t, null)) {
                    queueTop = s; // or putOrderedInt
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Specialized version of popTask to pop only if topmost element
     * is the given task. Called only by this thread.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final boolean unpushTask(ForkJoinTask<?> t) {
        ForkJoinTask<?>[] q;
        int s;
        if ((q = queue) != null && (s = queueTop) != queueBase &&
            UNSAFE.compareAndSwapObject
            (q, (((q.length - 1) & --s) << ASHIFT) + ABASE, t, null)) {
            queueTop = s; // or putOrderedInt
            return true;
        }
        return false;
    }

    /**
     * Returns next task, or null if empty or contended.
     */
    final ForkJoinTask<?> peekTask() {
        int m;
        ForkJoinTask<?>[] q = queue;
        if (q == null || (m = q.length - 1) < 0)
            return null;
        int i = locallyFifo ? queueBase : (queueTop - 1);
        return q[i & m];
    }

    // Support methods for ForkJoinPool

    /**
     * Runs the given task, plus any local tasks until queue is empty
     */
    final void execTask(ForkJoinTask<?> t) {
        currentSteal = t;
        for (;;) {
            if (t != null)
                t.doExec();
            if (queueTop == queueBase)
                break;
            t = locallyFifo ? locallyDeqTask() : popTask();
        }
        ++stealCount;
        currentSteal = null;
    }

    /**
     * Removes and cancels all tasks in queue.  Can be called from any
     * thread.
     */
    final void cancelTasks() {
        ForkJoinTask<?> cj = currentJoin; // try to cancel ongoing tasks
        if (cj != null && cj.status >= 0)
            cj.cancelIgnoringExceptions();
        ForkJoinTask<?> cs = currentSteal;
        if (cs != null && cs.status >= 0)
            cs.cancelIgnoringExceptions();
        while (queueBase != queueTop) {
            ForkJoinTask<?> t = deqTask();
            if (t != null)
                t.cancelIgnoringExceptions();
        }
    }

    /**
     * Drains tasks to given collection c.
     *
     * @return the number of tasks drained
     */
    final int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
        int n = 0;
        while (queueBase != queueTop) {
            ForkJoinTask<?> t = deqTask();
            if (t != null) {
                c.add(t);
                ++n;
            }
        }
        return n;
    }

    // Support methods for ForkJoinTask

    /**
     * Returns an estimate of the number of tasks in the queue.
     */
    final int getQueueSize() {
        return queueTop - queueBase;
    }

    /**
     * Gets and removes a local task.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> pollLocalTask() {
        return locallyFifo ? locallyDeqTask() : popTask();
    }

    /**
     * Gets and removes a local or stolen task.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> pollTask() {
        ForkJoinWorkerThread[] ws;
        ForkJoinTask<?> t = pollLocalTask();
        if (t != null || (ws = pool.workers) == null)
            return t;
        int n = ws.length; // cheap version of FJP.scan
        int steps = n << 1;
        int r = nextSeed();
        int i = 0;
        while (i < steps) {
            ForkJoinWorkerThread w = ws[(i++ + r) & (n - 1)];
            if (w != null && w.queueBase != w.queueTop && w.queue != null) {
                if ((t = w.deqTask()) != null)
                    return t;
                i = 0;
            }
        }
        return null;
    }

    /**
     * The maximum stolen->joining link depth allowed in helpJoinTask,
     * as well as the maximum number of retries (allowing on average
     * one staleness retry per level) per attempt to instead try
     * compensation.  Depths for legitimate chains are unbounded, but
     * we use a fixed constant to avoid (otherwise unchecked) cycles
     * and bound staleness of traversal parameters at the expense of
     * sometimes blocking when we could be helping.
     */
    private static final int MAX_HELP = 16;

    /**
     * Possibly runs some tasks and/or blocks, until joinMe is done.
     *
     * @param joinMe the task to join
     * @return completion status on exit
     */
    final int joinTask(ForkJoinTask<?> joinMe) {
        ForkJoinTask<?> prevJoin = currentJoin;
        currentJoin = joinMe;
        for (int s, retries = MAX_HELP;;) {
            if ((s = joinMe.status) < 0) {
                currentJoin = prevJoin;
                return s;
            }
            if (retries > 0) {
                if (queueTop != queueBase) {
                    if (!localHelpJoinTask(joinMe))
                        retries = 0;           // cannot help
                }
                else if (retries == MAX_HELP >>> 1) {
                    --retries;                 // check uncommon case
                    if (tryDeqAndExec(joinMe) >= 0)
                        Thread.yield();        // for politeness
                }
                else
                    retries = helpJoinTask(joinMe) ? MAX_HELP : retries - 1;
            }
            else {
                retries = MAX_HELP;           // restart if not done
                pool.tryAwaitJoin(joinMe);
            }
        }
    }

    /**
     * If present, pops and executes the given task, or any other
     * cancelled task
     *
     * @return false if any other non-cancelled task exists in local queue
     */
    private boolean localHelpJoinTask(ForkJoinTask<?> joinMe) {
        int s, i; ForkJoinTask<?>[] q; ForkJoinTask<?> t;
        if ((s = queueTop) != queueBase && (q = queue) != null &&
            (i = (q.length - 1) & --s) >= 0 &&
            (t = q[i]) != null) {
            if (t != joinMe && t.status >= 0)
                return false;
            if (UNSAFE.compareAndSwapObject
                (q, (i << ASHIFT) + ABASE, t, null)) {
                queueTop = s;           // or putOrderedInt
                t.doExec();
            }
        }
        return true;
    }

    /**
     * Tries to locate and execute tasks for a stealer of the given
     * task, or in turn one of its stealers, Traces
     * currentSteal->currentJoin links looking for a thread working on
     * a descendant of the given task and with a non-empty queue to
     * steal back and execute tasks from.  The implementation is very
     * branchy to cope with potential inconsistencies or loops
     * encountering chains that are stale, unknown, or of length
     * greater than MAX_HELP links.  All of these cases are dealt with
     * by just retrying by caller.
     *
     * @param joinMe the task to join
     * @param canSteal true if local queue is empty
     * @return true if ran a task
     */
    private boolean helpJoinTask(ForkJoinTask<?> joinMe) {
        boolean helped = false;
        int m = pool.scanGuard & SMASK;
        ForkJoinWorkerThread[] ws = pool.workers;
        if (ws != null && ws.length > m && joinMe.status >= 0) {
            int levels = MAX_HELP;              // remaining chain length
            ForkJoinTask<?> task = joinMe;      // base of chain
            outer:for (ForkJoinWorkerThread thread = this;;) {
                // Try to find v, the stealer of task, by first using hint
                ForkJoinWorkerThread v = ws[thread.stealHint & m];
                if (v == null || v.currentSteal != task) {
                    for (int j = 0; ;) {        // search array
                        if ((v = ws[j]) != null && v.currentSteal == task) {
                            thread.stealHint = j;
                            break;              // save hint for next time
                        }
                        if (++j > m)
                            break outer;        // can't find stealer
                    }
                }
                // Try to help v, using specialized form of deqTask
                for (;;) {
                    ForkJoinTask<?>[] q; int b, i;
                    if (joinMe.status < 0)
                        break outer;
                    if ((b = v.queueBase) == v.queueTop ||
                        (q = v.queue) == null ||
                        (i = (q.length-1) & b) < 0)
                        break;                  // empty
                    long u = (i << ASHIFT) + ABASE;
                    ForkJoinTask<?> t = q[i];
                    if (task.status < 0)
                        break outer;            // stale
                    if (t != null && v.queueBase == b &&
                        UNSAFE.compareAndSwapObject(q, u, t, null)) {
                        v.queueBase = b + 1;
                        v.stealHint = poolIndex;
                        ForkJoinTask<?> ps = currentSteal;
                        currentSteal = t;
                        t.doExec();
                        currentSteal = ps;
                        helped = true;
                    }
                }
                // Try to descend to find v's stealer
                ForkJoinTask<?> next = v.currentJoin;
                if (--levels > 0 && task.status >= 0 &&
                    next != null && next != task) {
                    task = next;
                    thread = v;
                }
                else
                    break;  // max levels, stale, dead-end, or cyclic
            }
        }
        return helped;
    }

    /**
     * Performs an uncommon case for joinTask: If task t is at base of
     * some workers queue, steals and executes it.
     *
     * @param t the task
     * @return t's status
     */
    private int tryDeqAndExec(ForkJoinTask<?> t) {
        int m = pool.scanGuard & SMASK;
        ForkJoinWorkerThread[] ws = pool.workers;
        if (ws != null && ws.length > m && t.status >= 0) {
            for (int j = 0; j <= m; ++j) {
                ForkJoinTask<?>[] q; int b, i;
                ForkJoinWorkerThread v = ws[j];
                if (v != null &&
                    (b = v.queueBase) != v.queueTop &&
                    (q = v.queue) != null &&
                    (i = (q.length - 1) & b) >= 0 &&
                    q[i] ==  t) {
                    long u = (i << ASHIFT) + ABASE;
                    if (v.queueBase == b &&
                        UNSAFE.compareAndSwapObject(q, u, t, null)) {
                        v.queueBase = b + 1;
                        v.stealHint = poolIndex;
                        ForkJoinTask<?> ps = currentSteal;
                        currentSteal = t;
                        t.doExec();
                        currentSteal = ps;
                    }
                    break;
                }
            }
        }
        return t.status;
    }

    /**
     * Implements ForkJoinTask.getSurplusQueuedTaskCount().  Returns
     * an estimate of the number of tasks, offset by a function of
     * number of idle workers.
     *
     * This method provides a cheap heuristic guide for task
     * partitioning when programmers, frameworks, tools, or languages
     * have little or no idea about task granularity.  In essence by
     * offering this method, we ask users only about tradeoffs in
     * overhead vs expected throughput and its variance, rather than
     * how finely to partition tasks.
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
     * So, users will want to use values larger, but not much larger
     * than 1 to both smooth over transient shortages and hedge
     * against uneven progress; as traded off against the cost of
     * extra task overhead. We leave the user to pick a threshold
     * value to compare with the results of this call to guide
     * decisions, but recommend values such as 3.
     *
     * When all threads are active, it is on average OK to estimate
     * surplus strictly locally. In steady-state, if one thread is
     * maintaining say 2 surplus tasks, then so are others. So we can
     * just use estimated queue length (although note that (queueTop -
     * queueBase) can be an overestimate because of stealers lagging
     * increments of queueBase).  However, this strategy alone leads
     * to serious mis-estimates in some non-steady-state conditions
     * (ramp-up, ramp-down, other stalls). We can detect many of these
     * by further considering the number of "idle" threads, that are
     * known to have zero queued tasks, so compensate by a factor of
     * (#idle/#active) threads.
     */
    final int getEstimatedSurplusTaskCount() {
        return queueTop - queueBase - pool.idlePerActive();
    }

    /**
     * Runs tasks until {@code pool.isQuiescent()}. We piggyback on
     * pool's active count ctl maintenance, but rather than blocking
     * when tasks cannot be found, we rescan until all others cannot
     * find tasks either. The bracketing by pool quiescerCounts
     * updates suppresses pool auto-shutdown mechanics that could
     * otherwise prematurely terminate the pool because all threads
     * appear to be inactive.
     */
    final void helpQuiescePool() {
        boolean active = true;
        ForkJoinTask<?> ps = currentSteal; // to restore below
        ForkJoinPool p = pool;
        p.addQuiescerCount(1);
        for (;;) {
            ForkJoinWorkerThread[] ws = p.workers;
            ForkJoinWorkerThread v = null;
            int n;
            if (queueTop != queueBase)
                v = this;
            else if (ws != null && (n = ws.length) > 1) {
                ForkJoinWorkerThread w;
                int r = nextSeed(); // cheap version of FJP.scan
                int steps = n << 1;
                for (int i = 0; i < steps; ++i) {
                    if ((w = ws[(i + r) & (n - 1)]) != null &&
                        w.queueBase != w.queueTop) {
                        v = w;
                        break;
                    }
                }
            }
            if (v != null) {
                ForkJoinTask<?> t;
                if (!active) {
                    active = true;
                    p.addActiveCount(1);
                }
                if ((t = (v != this) ? v.deqTask() :
                     locallyFifo ? locallyDeqTask() : popTask()) != null) {
                    currentSteal = t;
                    t.doExec();
                    currentSteal = ps;
                }
            }
            else {
                if (active) {
                    active = false;
                    p.addActiveCount(-1);
                }
                if (p.isQuiescent()) {
                    p.addActiveCount(1);
                    p.addQuiescerCount(-1);
                    break;
                }
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long ABASE;
    private static final int ASHIFT;

    static {
        int s;
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class a = ForkJoinTask[].class;
            ABASE = UNSAFE.arrayBaseOffset(a);
            s = UNSAFE.arrayIndexScale(a);
        } catch (Exception e) {
            throw new Error(e);
        }
        if ((s & (s-1)) != 0)
            throw new Error("data type scale not a power of two");
        ASHIFT = 31 - Integer.numberOfLeadingZeros(s);
    }

}
