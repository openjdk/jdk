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

import java.util.Collection;

/**
 * A thread managed by a {@link ForkJoinPool}.  This class is
 * subclassable solely for the sake of adding functionality -- there
 * are no overridable methods dealing with scheduling or execution.
 * However, you can override initialization and termination methods
 * surrounding the main task processing loop.  If you do create such a
 * subclass, you will also need to supply a custom {@link
 * ForkJoinPool.ForkJoinWorkerThreadFactory} to use it in a {@code
 * ForkJoinPool}.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class ForkJoinWorkerThread extends Thread {
    /*
     * Algorithm overview:
     *
     * 1. Work-Stealing: Work-stealing queues are special forms of
     * Deques that support only three of the four possible
     * end-operations -- push, pop, and deq (aka steal), and only do
     * so under the constraints that push and pop are called only from
     * the owning thread, while deq may be called from other threads.
     * (If you are unfamiliar with them, you probably want to read
     * Herlihy and Shavit's book "The Art of Multiprocessor
     * programming", chapter 16 describing these in more detail before
     * proceeding.)  The main work-stealing queue design is roughly
     * similar to "Dynamic Circular Work-Stealing Deque" by David
     * Chase and Yossi Lev, SPAA 2005
     * (http://research.sun.com/scalable/pubs/index.html).  The main
     * difference ultimately stems from gc requirements that we null
     * out taken slots as soon as we can, to maintain as small a
     * footprint as possible even in programs generating huge numbers
     * of tasks. To accomplish this, we shift the CAS arbitrating pop
     * vs deq (steal) from being on the indices ("base" and "sp") to
     * the slots themselves (mainly via method "casSlotNull()"). So,
     * both a successful pop and deq mainly entail CAS'ing a non-null
     * slot to null.  Because we rely on CASes of references, we do
     * not need tag bits on base or sp.  They are simple ints as used
     * in any circular array-based queue (see for example ArrayDeque).
     * Updates to the indices must still be ordered in a way that
     * guarantees that (sp - base) > 0 means the queue is empty, but
     * otherwise may err on the side of possibly making the queue
     * appear nonempty when a push, pop, or deq have not fully
     * committed. Note that this means that the deq operation,
     * considered individually, is not wait-free. One thief cannot
     * successfully continue until another in-progress one (or, if
     * previously empty, a push) completes.  However, in the
     * aggregate, we ensure at least probabilistic
     * non-blockingness. If an attempted steal fails, a thief always
     * chooses a different random victim target to try next. So, in
     * order for one thief to progress, it suffices for any
     * in-progress deq or new push on any empty queue to complete. One
     * reason this works well here is that apparently-nonempty often
     * means soon-to-be-stealable, which gives threads a chance to
     * activate if necessary before stealing (see below).
     *
     * This approach also enables support for "async mode" where local
     * task processing is in FIFO, not LIFO order; simply by using a
     * version of deq rather than pop when locallyFifo is true (as set
     * by the ForkJoinPool).  This allows use in message-passing
     * frameworks in which tasks are never joined.
     *
     * Efficient implementation of this approach currently relies on
     * an uncomfortable amount of "Unsafe" mechanics. To maintain
     * correct orderings, reads and writes of variable base require
     * volatile ordering.  Variable sp does not require volatile write
     * but needs cheaper store-ordering on writes.  Because they are
     * protected by volatile base reads, reads of the queue array and
     * its slots do not need volatile load semantics, but writes (in
     * push) require store order and CASes (in pop and deq) require
     * (volatile) CAS semantics.  (See "Idempotent work stealing" by
     * Michael, Saraswat, and Vechev, PPoPP 2009
     * http://portal.acm.org/citation.cfm?id=1504186 for an algorithm
     * with similar properties, but without support for nulling
     * slots.)  Since these combinations aren't supported using
     * ordinary volatiles, the only way to accomplish these
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
     * queues are currently initialized immediately after the thread
     * gets the initial signal to start processing tasks.  However,
     * all queue-related methods except pushTask are written in a way
     * that allows them to instead be lazily allocated and/or disposed
     * of when empty. All together, these low-level implementation
     * choices produce as much as a factor of 4 performance
     * improvement compared to naive implementations, and enable the
     * processing of billions of tasks per second, sometimes at the
     * expense of ugliness.
     *
     * 2. Run control: The primary run control is based on a global
     * counter (activeCount) held by the pool. It uses an algorithm
     * similar to that in Herlihy and Shavit section 17.6 to cause
     * threads to eventually block when all threads declare they are
     * inactive. For this to work, threads must be declared active
     * when executing tasks, and before stealing a task. They must be
     * inactive before blocking on the Pool Barrier (awaiting a new
     * submission or other Pool event). In between, there is some free
     * play which we take advantage of to avoid contention and rapid
     * flickering of the global activeCount: If inactive, we activate
     * only if a victim queue appears to be nonempty (see above).
     * Similarly, a thread tries to inactivate only after a full scan
     * of other threads.  The net effect is that contention on
     * activeCount is rarely a measurable performance issue. (There
     * are also a few other cases where we scan for work rather than
     * retry/block upon contention.)
     *
     * 3. Selection control. We maintain policy of always choosing to
     * run local tasks rather than stealing, and always trying to
     * steal tasks before trying to run a new submission. All steals
     * are currently performed in randomly-chosen deq-order. It may be
     * worthwhile to bias these with locality / anti-locality
     * information, but doing this well probably requires more
     * lower-level information from JVMs than currently provided.
     */

    /**
     * Capacity of work-stealing queue array upon initialization.
     * Must be a power of two. Initial size must be at least 2, but is
     * padded to minimize cache effects.
     */
    private static final int INITIAL_QUEUE_CAPACITY = 1 << 13;

    /**
     * Maximum work-stealing queue array size.  Must be less than or
     * equal to 1 << 28 to ensure lack of index wraparound. (This
     * is less than usual bounds, because we need leftshift by 3
     * to be in int range).
     */
    private static final int MAXIMUM_QUEUE_CAPACITY = 1 << 28;

    /**
     * The pool this thread works in. Accessed directly by ForkJoinTask.
     */
    final ForkJoinPool pool;

    /**
     * The work-stealing queue array. Size must be a power of two.
     * Initialized when thread starts, to improve memory locality.
     */
    private ForkJoinTask<?>[] queue;

    /**
     * Index (mod queue.length) of next queue slot to push to or pop
     * from. It is written only by owner thread, via ordered store.
     * Both sp and base are allowed to wrap around on overflow, but
     * (sp - base) still estimates size.
     */
    private volatile int sp;

    /**
     * Index (mod queue.length) of least valid queue slot, which is
     * always the next position to steal from if nonempty.
     */
    private volatile int base;

    /**
     * Activity status. When true, this worker is considered active.
     * Must be false upon construction. It must be true when executing
     * tasks, and BEFORE stealing a task. It must be false before
     * calling pool.sync.
     */
    private boolean active;

    /**
     * Run state of this worker. Supports simple versions of the usual
     * shutdown/shutdownNow control.
     */
    private volatile int runState;

    /**
     * Seed for random number generator for choosing steal victims.
     * Uses Marsaglia xorshift. Must be nonzero upon initialization.
     */
    private int seed;

    /**
     * Number of steals, transferred to pool when idle
     */
    private int stealCount;

    /**
     * Index of this worker in pool array. Set once by pool before
     * running, and accessed directly by pool during cleanup etc.
     */
    int poolIndex;

    /**
     * The last barrier event waited for. Accessed in pool callback
     * methods, but only by current thread.
     */
    long lastEventCount;

    /**
     * True if use local fifo, not default lifo, for local polling
     */
    private boolean locallyFifo;

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     *
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        if (pool == null) throw new NullPointerException();
        this.pool = pool;
        // Note: poolIndex is set by pool during construction
        // Remaining initialization is deferred to onStart
    }

    // Public access methods

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

    /**
     * Establishes local first-in-first-out scheduling mode for forked
     * tasks that are never joined.
     *
     * @param async if true, use locally FIFO scheduling
     */
    void setAsyncMode(boolean async) {
        locallyFifo = async;
    }

    // Runstate management

    // Runstate values. Order matters
    private static final int RUNNING     = 0;
    private static final int SHUTDOWN    = 1;
    private static final int TERMINATING = 2;
    private static final int TERMINATED  = 3;

    final boolean isShutdown()    { return runState >= SHUTDOWN;  }
    final boolean isTerminating() { return runState >= TERMINATING;  }
    final boolean isTerminated()  { return runState == TERMINATED; }
    final boolean shutdown()      { return transitionRunStateTo(SHUTDOWN); }
    final boolean shutdownNow()   { return transitionRunStateTo(TERMINATING); }

    /**
     * Transitions to at least the given state.
     *
     * @return {@code true} if not already at least at given state
     */
    private boolean transitionRunStateTo(int state) {
        for (;;) {
            int s = runState;
            if (s >= state)
                return false;
            if (UNSAFE.compareAndSwapInt(this, runStateOffset, s, state))
                return true;
        }
    }

    /**
     * Tries to set status to active; fails on contention.
     */
    private boolean tryActivate() {
        if (!active) {
            if (!pool.tryIncrementActiveCount())
                return false;
            active = true;
        }
        return true;
    }

    /**
     * Tries to set status to inactive; fails on contention.
     */
    private boolean tryInactivate() {
        if (active) {
            if (!pool.tryDecrementActiveCount())
                return false;
            active = false;
        }
        return true;
    }

    /**
     * Computes next value for random victim probe.  Scans don't
     * require a very high quality generator, but also not a crummy
     * one.  Marsaglia xor-shift is cheap and works well.
     */
    private static int xorShift(int r) {
        r ^= (r << 13);
        r ^= (r >>> 17);
        return r ^ (r << 5);
    }

    // Lifecycle methods

    /**
     * This method is required to be public, but should never be
     * called explicitly. It performs the main run loop to execute
     * ForkJoinTasks.
     */
    public void run() {
        Throwable exception = null;
        try {
            onStart();
            pool.sync(this); // await first pool event
            mainLoop();
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            onTermination(exception);
        }
    }

    /**
     * Executes tasks until shut down.
     */
    private void mainLoop() {
        while (!isShutdown()) {
            ForkJoinTask<?> t = pollTask();
            if (t != null || (t = pollSubmission()) != null)
                t.quietlyExec();
            else if (tryInactivate())
                pool.sync(this);
        }
    }

    /**
     * Initializes internal state after construction but before
     * processing any tasks. If you override this method, you must
     * invoke super.onStart() at the beginning of the method.
     * Initialization requires care: Most fields must have legal
     * default values, to ensure that attempted accesses from other
     * threads work correctly even before this thread starts
     * processing tasks.
     */
    protected void onStart() {
        // Allocate while starting to improve chances of thread-local
        // isolation
        queue = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];
        // Initial value of seed need not be especially random but
        // should differ across workers and must be nonzero
        int p = poolIndex + 1;
        seed = p + (p << 8) + (p << 16) + (p << 24); // spread bits
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
        // Execute remaining local tasks unless aborting or terminating
        while (exception == null && pool.isProcessingTasks() && base != sp) {
            try {
                ForkJoinTask<?> t = popTask();
                if (t != null)
                    t.quietlyExec();
            } catch (Throwable ex) {
                exception = ex;
            }
        }
        // Cancel other tasks, transition status, notify pool, and
        // propagate exception to uncaught exception handler
        try {
            do {} while (!tryInactivate()); // ensure inactive
            cancelTasks();
            runState = TERMINATED;
            pool.workerTerminated(this);
        } catch (Throwable ex) {        // Shouldn't ever happen
            if (exception == null)      // but if so, at least rethrown
                exception = ex;
        } finally {
            if (exception != null)
                ForkJoinTask.rethrowException(exception);
        }
    }

    // Intrinsics-based support for queue operations.

    private static long slotOffset(int i) {
        return ((long) i << qShift) + qBase;
    }

    /**
     * Adds in store-order the given task at given slot of q to null.
     * Caller must ensure q is non-null and index is in range.
     */
    private static void setSlot(ForkJoinTask<?>[] q, int i,
                                ForkJoinTask<?> t) {
        UNSAFE.putOrderedObject(q, slotOffset(i), t);
    }

    /**
     * CAS given slot of q to null. Caller must ensure q is non-null
     * and index is in range.
     */
    private static boolean casSlotNull(ForkJoinTask<?>[] q, int i,
                                       ForkJoinTask<?> t) {
        return UNSAFE.compareAndSwapObject(q, slotOffset(i), t, null);
    }

    /**
     * Sets sp in store-order.
     */
    private void storeSp(int s) {
        UNSAFE.putOrderedInt(this, spOffset, s);
    }

    // Main queue methods

    /**
     * Pushes a task. Called only by current thread.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final void pushTask(ForkJoinTask<?> t) {
        ForkJoinTask<?>[] q = queue;
        int mask = q.length - 1;
        int s = sp;
        setSlot(q, s & mask, t);
        storeSp(++s);
        if ((s -= base) == 1)
            pool.signalWork();
        else if (s >= mask)
            growQueue();
    }

    /**
     * Tries to take a task from the base of the queue, failing if
     * either empty or contended.
     *
     * @return a task, or null if none or contended
     */
    final ForkJoinTask<?> deqTask() {
        ForkJoinTask<?> t;
        ForkJoinTask<?>[] q;
        int i;
        int b;
        if (sp != (b = base) &&
            (q = queue) != null && // must read q after b
            (t = q[i = (q.length - 1) & b]) != null &&
            casSlotNull(q, i, t)) {
            base = b + 1;
            return t;
        }
        return null;
    }

    /**
     * Tries to take a task from the base of own queue, activating if
     * necessary, failing only if empty. Called only by current thread.
     *
     * @return a task, or null if none
     */
    final ForkJoinTask<?> locallyDeqTask() {
        int b;
        while (sp != (b = base)) {
            if (tryActivate()) {
                ForkJoinTask<?>[] q = queue;
                int i = (q.length - 1) & b;
                ForkJoinTask<?> t = q[i];
                if (t != null && casSlotNull(q, i, t)) {
                    base = b + 1;
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Returns a popped task, or null if empty. Ensures active status
     * if non-null. Called only by current thread.
     */
    final ForkJoinTask<?> popTask() {
        int s = sp;
        while (s != base) {
            if (tryActivate()) {
                ForkJoinTask<?>[] q = queue;
                int mask = q.length - 1;
                int i = (s - 1) & mask;
                ForkJoinTask<?> t = q[i];
                if (t == null || !casSlotNull(q, i, t))
                    break;
                storeSp(s - 1);
                return t;
            }
        }
        return null;
    }

    /**
     * Specialized version of popTask to pop only if
     * topmost element is the given task. Called only
     * by current thread while active.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final boolean unpushTask(ForkJoinTask<?> t) {
        ForkJoinTask<?>[] q = queue;
        int mask = q.length - 1;
        int s = sp - 1;
        if (casSlotNull(q, s & mask, t)) {
            storeSp(s);
            return true;
        }
        return false;
    }

    /**
     * Returns next task or null if empty or contended
     */
    final ForkJoinTask<?> peekTask() {
        ForkJoinTask<?>[] q = queue;
        if (q == null)
            return null;
        int mask = q.length - 1;
        int i = locallyFifo ? base : (sp - 1);
        return q[i & mask];
    }

    /**
     * Doubles queue array size. Transfers elements by emulating
     * steals (deqs) from old array and placing, oldest first, into
     * new array.
     */
    private void growQueue() {
        ForkJoinTask<?>[] oldQ = queue;
        int oldSize = oldQ.length;
        int newSize = oldSize << 1;
        if (newSize > MAXIMUM_QUEUE_CAPACITY)
            throw new RejectedExecutionException("Queue capacity exceeded");
        ForkJoinTask<?>[] newQ = queue = new ForkJoinTask<?>[newSize];

        int b = base;
        int bf = b + oldSize;
        int oldMask = oldSize - 1;
        int newMask = newSize - 1;
        do {
            int oldIndex = b & oldMask;
            ForkJoinTask<?> t = oldQ[oldIndex];
            if (t != null && !casSlotNull(oldQ, oldIndex, t))
                t = null;
            setSlot(newQ, b & newMask, t);
        } while (++b != bf);
        pool.signalWork();
    }

    /**
     * Tries to steal a task from another worker. Starts at a random
     * index of workers array, and probes workers until finding one
     * with non-empty queue or finding that all are empty.  It
     * randomly selects the first n probes. If these are empty, it
     * resorts to a full circular traversal, which is necessary to
     * accurately set active status by caller. Also restarts if pool
     * events occurred since last scan, which forces refresh of
     * workers array, in case barrier was associated with resize.
     *
     * This method must be both fast and quiet -- usually avoiding
     * memory accesses that could disrupt cache sharing etc other than
     * those needed to check for and take tasks. This accounts for,
     * among other things, updating random seed in place without
     * storing it until exit.
     *
     * @return a task, or null if none found
     */
    private ForkJoinTask<?> scan() {
        ForkJoinTask<?> t = null;
        int r = seed;                    // extract once to keep scan quiet
        ForkJoinWorkerThread[] ws;       // refreshed on outer loop
        int mask;                        // must be power 2 minus 1 and > 0
        outer:do {
            if ((ws = pool.workers) != null && (mask = ws.length - 1) > 0) {
                int idx = r;
                int probes = ~mask;      // use random index while negative
                for (;;) {
                    r = xorShift(r);     // update random seed
                    ForkJoinWorkerThread v = ws[mask & idx];
                    if (v == null || v.sp == v.base) {
                        if (probes <= mask)
                            idx = (probes++ < 0) ? r : (idx + 1);
                        else
                            break;
                    }
                    else if (!tryActivate() || (t = v.deqTask()) == null)
                        continue outer;  // restart on contention
                    else
                        break outer;
                }
            }
        } while (pool.hasNewSyncEvent(this)); // retry on pool events
        seed = r;
        return t;
    }

    /**
     * Gets and removes a local or stolen task.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> pollTask() {
        ForkJoinTask<?> t = locallyFifo ? locallyDeqTask() : popTask();
        if (t == null && (t = scan()) != null)
            ++stealCount;
        return t;
    }

    /**
     * Gets a local task.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> pollLocalTask() {
        return locallyFifo ? locallyDeqTask() : popTask();
    }

    /**
     * Returns a pool submission, if one exists, activating first.
     *
     * @return a submission, if available
     */
    private ForkJoinTask<?> pollSubmission() {
        ForkJoinPool p = pool;
        while (p.hasQueuedSubmissions()) {
            ForkJoinTask<?> t;
            if (tryActivate() && (t = p.pollSubmission()) != null)
                return t;
        }
        return null;
    }

    // Methods accessed only by Pool

    /**
     * Removes and cancels all tasks in queue.  Can be called from any
     * thread.
     */
    final void cancelTasks() {
        ForkJoinTask<?> t;
        while (base != sp && (t = deqTask()) != null)
            t.cancelIgnoringExceptions();
    }

    /**
     * Drains tasks to given collection c.
     *
     * @return the number of tasks drained
     */
    final int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
        int n = 0;
        ForkJoinTask<?> t;
        while (base != sp && (t = deqTask()) != null) {
            c.add(t);
            ++n;
        }
        return n;
    }

    /**
     * Gets and clears steal count for accumulation by pool.  Called
     * only when known to be idle (in pool.sync and termination).
     */
    final int getAndClearStealCount() {
        int sc = stealCount;
        stealCount = 0;
        return sc;
    }

    /**
     * Returns {@code true} if at least one worker in the given array
     * appears to have at least one queued task.
     *
     * @param ws array of workers
     */
    static boolean hasQueuedTasks(ForkJoinWorkerThread[] ws) {
        if (ws != null) {
            int len = ws.length;
            for (int j = 0; j < 2; ++j) { // need two passes for clean sweep
                for (int i = 0; i < len; ++i) {
                    ForkJoinWorkerThread w = ws[i];
                    if (w != null && w.sp != w.base)
                        return true;
                }
            }
        }
        return false;
    }

    // Support methods for ForkJoinTask

    /**
     * Returns an estimate of the number of tasks in the queue.
     */
    final int getQueueSize() {
        // suppress momentarily negative values
        return Math.max(0, sp - base);
    }

    /**
     * Returns an estimate of the number of tasks, offset by a
     * function of number of idle workers.
     */
    final int getEstimatedSurplusTaskCount() {
        // The halving approximates weighting idle vs non-idle workers
        return (sp - base) - (pool.getIdleThreadCount() >>> 1);
    }

    /**
     * Scans, returning early if joinMe done.
     */
    final ForkJoinTask<?> scanWhileJoining(ForkJoinTask<?> joinMe) {
        ForkJoinTask<?> t = pollTask();
        if (t != null && joinMe.status < 0 && sp == base) {
            pushTask(t); // unsteal if done and this task would be stealable
            t = null;
        }
        return t;
    }

    /**
     * Runs tasks until {@code pool.isQuiescent()}.
     */
    final void helpQuiescePool() {
        for (;;) {
            ForkJoinTask<?> t = pollTask();
            if (t != null)
                t.quietlyExec();
            else if (tryInactivate() && pool.isQuiescent())
                break;
        }
        do {} while (!tryActivate()); // re-activate on exit
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE = sun.misc.Unsafe.getUnsafe();
    private static final long spOffset =
        objectFieldOffset("sp", ForkJoinWorkerThread.class);
    private static final long runStateOffset =
        objectFieldOffset("runState", ForkJoinWorkerThread.class);
    private static final long qBase;
    private static final int qShift;

    static {
        qBase = UNSAFE.arrayBaseOffset(ForkJoinTask[].class);
        int s = UNSAFE.arrayIndexScale(ForkJoinTask[].class);
        if ((s & (s-1)) != 0)
            throw new Error("data type scale not a power of two");
        qShift = 31 - Integer.numberOfLeadingZeros(s);
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
