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

import java.util.Random;
import java.util.Collection;
import java.util.concurrent.locks.LockSupport;

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
     * ("base" and "sp") to the slots themselves (mainly via method
     * "casSlotNull()"). So, both a successful pop and deq mainly
     * entail a CAS of a slot from non-null to null.  Because we rely
     * on CASes of references, we do not need tag bits on base or sp.
     * They are simple ints as used in any circular array-based queue
     * (see for example ArrayDeque).  Updates to the indices must
     * still be ordered in a way that guarantees that sp == base means
     * the queue is empty, but otherwise may err on the side of
     * possibly making the queue appear nonempty when a push, pop, or
     * deq have not fully committed. Note that this means that the deq
     * operation, considered individually, is not wait-free. One thief
     * cannot successfully continue until another in-progress one (or,
     * if previously empty, a push) completes.  However, in the
     * aggregate, we ensure at least probabilistic non-blockingness.
     * If an attempted steal fails, a thief always chooses a different
     * random victim target to try next. So, in order for one thief to
     * progress, it suffices for any in-progress deq or new push on
     * any empty queue to complete. One reason this works well here is
     * that apparently-nonempty often means soon-to-be-stealable,
     * which gives threads a chance to set activation status if
     * necessary before stealing.
     *
     * This approach also enables support for "async mode" where local
     * task processing is in FIFO, not LIFO order; simply by using a
     * version of deq rather than pop when locallyFifo is true (as set
     * by the ForkJoinPool).  This allows use in message-passing
     * frameworks in which tasks are never joined.
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
     * MAX_HELP_DEPTH) and fall back to suspending the worker and if
     * necessary replacing it with a spare (see
     * ForkJoinPool.awaitJoin).
     *
     * Efficient implementation of these algorithms currently relies
     * on an uncomfortable amount of "Unsafe" mechanics. To maintain
     * correct orderings, reads and writes of variable base require
     * volatile ordering.  Variable sp does not require volatile
     * writes but still needs store-ordering, which we accomplish by
     * pre-incrementing sp before filling the slot with an ordered
     * store.  (Pre-incrementing also enables backouts used in
     * joinTask.)  Because they are protected by volatile base reads,
     * reads of the queue array and its slots by other threads do not
     * need volatile load semantics, but writes (in push) require
     * store order and CASes (in pop and deq) require (volatile) CAS
     * semantics.  (Michael, Saraswat, and Vechev's algorithm has
     * similar properties, but without support for nulling slots.)
     * Since these combinations aren't supported using ordinary
     * volatiles, the only way to accomplish these efficiently is to
     * use direct Unsafe calls. (Using external AtomicIntegers and
     * AtomicReferenceArrays for the indices and array is
     * significantly slower because of memory locality and indirection
     * effects.)
     *
     * Further, performance on most platforms is very sensitive to
     * placement and sizing of the (resizable) queue array.  Even
     * though these queues don't usually become all that big, the
     * initial size must be large enough to counteract cache
     * contention effects across multiple queues (especially in the
     * presence of GC cardmarking). Also, to improve thread-locality,
     * queues are initialized after starting.  All together, these
     * low-level implementation choices produce as much as a factor of
     * 4 performance improvement compared to naive implementations,
     * and enable the processing of billions of tasks per second,
     * sometimes at the expense of ugliness.
     */

    /**
     * Generator for initial random seeds for random victim
     * selection. This is used only to create initial seeds. Random
     * steals use a cheaper xorshift generator per steal attempt. We
     * expect only rare contention on seedGenerator, so just use a
     * plain Random.
     */
    private static final Random seedGenerator = new Random();

    /**
     * The maximum stolen->joining link depth allowed in helpJoinTask.
     * Depths for legitimate chains are unbounded, but we use a fixed
     * constant to avoid (otherwise unchecked) cycles and bound
     * staleness of traversal parameters at the expense of sometimes
     * blocking when we could be helping.
     */
    private static final int MAX_HELP_DEPTH = 8;

    /**
     * Capacity of work-stealing queue array upon initialization.
     * Must be a power of two. Initial size must be at least 4, but is
     * padded to minimize cache effects.
     */
    private static final int INITIAL_QUEUE_CAPACITY = 1 << 13;

    /**
     * Maximum work-stealing queue array size.  Must be less than or
     * equal to 1 << (31 - width of array entry) to ensure lack of
     * index wraparound. The value is set in the static block
     * at the end of this file after obtaining width.
     */
    private static final int MAXIMUM_QUEUE_CAPACITY;

    /**
     * The pool this thread works in. Accessed directly by ForkJoinTask.
     */
    final ForkJoinPool pool;

    /**
     * The work-stealing queue array. Size must be a power of two.
     * Initialized in onStart, to improve memory locality.
     */
    private ForkJoinTask<?>[] queue;

    /**
     * Index (mod queue.length) of least valid queue slot, which is
     * always the next position to steal from if nonempty.
     */
    private volatile int base;

    /**
     * Index (mod queue.length) of next queue slot to push to or pop
     * from. It is written only by owner thread, and accessed by other
     * threads only after reading (volatile) base.  Both sp and base
     * are allowed to wrap around on overflow, but (sp - base) still
     * estimates size.
     */
    private int sp;

    /**
     * The index of most recent stealer, used as a hint to avoid
     * traversal in method helpJoinTask. This is only a hint because a
     * worker might have had multiple steals and this only holds one
     * of them (usually the most current). Declared non-volatile,
     * relying on other prevailing sync to keep reasonably current.
     */
    private int stealHint;

    /**
     * Run state of this worker. In addition to the usual run levels,
     * tracks if this worker is suspended as a spare, and if it was
     * killed (trimmed) while suspended. However, "active" status is
     * maintained separately and modified only in conjunction with
     * CASes of the pool's runState (which are currently sadly
     * manually inlined for performance.)  Accessed directly by pool
     * to simplify checks for normal (zero) status.
     */
    volatile int runState;

    private static final int TERMINATING = 0x01;
    private static final int TERMINATED  = 0x02;
    private static final int SUSPENDED   = 0x04; // inactive spare
    private static final int TRIMMED     = 0x08; // killed while suspended

    /**
     * Number of steals. Directly accessed (and reset) by
     * pool.tryAccumulateStealCount when idle.
     */
    int stealCount;

    /**
     * Seed for random number generator for choosing steal victims.
     * Uses Marsaglia xorshift. Must be initialized as nonzero.
     */
    private int seed;

    /**
     * Activity status. When true, this worker is considered active.
     * Accessed directly by pool.  Must be false upon construction.
     */
    boolean active;

    /**
     * True if use local fifo, not default lifo, for local polling.
     * Shadows value from ForkJoinPool.
     */
    private final boolean locallyFifo;

    /**
     * Index of this worker in pool array. Set once by pool before
     * running, and accessed directly by pool to locate this worker in
     * its workers array.
     */
    int poolIndex;

    /**
     * The last pool event waited for. Accessed only by pool in
     * callback methods invoked within this thread.
     */
    int lastEventCount;

    /**
     * Encoded index and event count of next event waiter. Accessed
     * only by ForkJoinPool for managing event waiters.
     */
    volatile long nextWaiter;

    /**
     * Number of times this thread suspended as spare. Accessed only
     * by pool.
     */
    int spareCount;

    /**
     * Encoded index and count of next spare waiter. Accessed only
     * by ForkJoinPool for managing spares.
     */
    volatile int nextSpare;

    /**
     * The task currently being joined, set only when actively trying
     * to help other stealers in helpJoinTask. Written only by this
     * thread, but read by others.
     */
    private volatile ForkJoinTask<?> currentJoin;

    /**
     * The task most recently stolen from another worker (or
     * submission queue).  Written only by this thread, but read by
     * others.
     */
    private volatile ForkJoinTask<?> currentSteal;

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     *
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        this.pool = pool;
        this.locallyFifo = pool.locallyFifo;
        setDaemon(true);
        // To avoid exposing construction details to subclasses,
        // remaining initialization is in start() and onStart()
    }

    /**
     * Performs additional initialization and starts this thread.
     */
    final void start(int poolIndex, UncaughtExceptionHandler ueh) {
        this.poolIndex = poolIndex;
        if (ueh != null)
            setUncaughtExceptionHandler(ueh);
        start();
    }

    // Public/protected methods

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
     * Initializes internal state after construction but before
     * processing any tasks. If you override this method, you must
     * invoke @code{super.onStart()} at the beginning of the method.
     * Initialization requires care: Most fields must have legal
     * default values, to ensure that attempted accesses from other
     * threads work correctly even before this thread starts
     * processing tasks.
     */
    protected void onStart() {
        int rs = seedGenerator.nextInt();
        seed = rs == 0? 1 : rs; // seed must be nonzero

        // Allocate name string and arrays in this thread
        String pid = Integer.toString(pool.getPoolNumber());
        String wid = Integer.toString(poolIndex);
        setName("ForkJoinPool-" + pid + "-worker-" + wid);

        queue = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];
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
            ForkJoinPool p = pool;
            if (active) {
                int a; // inline p.tryDecrementActiveCount
                active = false;
                do {} while (!UNSAFE.compareAndSwapInt
                             (p, poolRunStateOffset, a = p.runState, a - 1));
            }
            cancelTasks();
            setTerminated();
            p.workerTerminated(this);
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
     * ForkJoinTasks.
     */
    public void run() {
        Throwable exception = null;
        try {
            onStart();
            mainLoop();
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            onTermination(exception);
        }
    }

    // helpers for run()

    /**
     * Finds and executes tasks, and checks status while running.
     */
    private void mainLoop() {
        boolean ran = false; // true if ran a task on last step
        ForkJoinPool p = pool;
        for (;;) {
            p.preStep(this, ran);
            if (runState != 0)
                break;
            ran = tryExecSteal() || tryExecSubmission();
        }
    }

    /**
     * Tries to steal a task and execute it.
     *
     * @return true if ran a task
     */
    private boolean tryExecSteal() {
        ForkJoinTask<?> t;
        if ((t = scan()) != null) {
            t.quietlyExec();
            UNSAFE.putOrderedObject(this, currentStealOffset, null);
            if (sp != base)
                execLocalTasks();
            return true;
        }
        return false;
    }

    /**
     * If a submission exists, try to activate and run it.
     *
     * @return true if ran a task
     */
    private boolean tryExecSubmission() {
        ForkJoinPool p = pool;
        // This loop is needed in case attempt to activate fails, in
        // which case we only retry if there still appears to be a
        // submission.
        while (p.hasQueuedSubmissions()) {
            ForkJoinTask<?> t; int a;
            if (active || // inline p.tryIncrementActiveCount
                (active = UNSAFE.compareAndSwapInt(p, poolRunStateOffset,
                                                   a = p.runState, a + 1))) {
                if ((t = p.pollSubmission()) != null) {
                    UNSAFE.putOrderedObject(this, currentStealOffset, t);
                    t.quietlyExec();
                    UNSAFE.putOrderedObject(this, currentStealOffset, null);
                    if (sp != base)
                        execLocalTasks();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Runs local tasks until queue is empty or shut down.  Call only
     * while active.
     */
    private void execLocalTasks() {
        while (runState == 0) {
            ForkJoinTask<?> t = locallyFifo ? locallyDeqTask() : popTask();
            if (t != null)
                t.quietlyExec();
            else if (sp == base)
                break;
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
     * Most uses don't actually call these methods, but instead contain
     * inlined forms that enable more predictable optimization.  We
     * don't define the version of write used in pushTask at all, but
     * instead inline there a store-fenced array slot write.
     */

    /**
     * CASes slot i of array q from t to null. Caller must ensure q is
     * non-null and index is in range.
     */
    private static final boolean casSlotNull(ForkJoinTask<?>[] q, int i,
                                             ForkJoinTask<?> t) {
        return UNSAFE.compareAndSwapObject(q, (i << qShift) + qBase, t, null);
    }

    /**
     * Performs a volatile write of the given task at given slot of
     * array q.  Caller must ensure q is non-null and index is in
     * range. This method is used only during resets and backouts.
     */
    private static final void writeSlot(ForkJoinTask<?>[] q, int i,
                                        ForkJoinTask<?> t) {
        UNSAFE.putObjectVolatile(q, (i << qShift) + qBase, t);
    }

    // queue methods

    /**
     * Pushes a task. Call only from this thread.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final void pushTask(ForkJoinTask<?> t) {
        ForkJoinTask<?>[] q = queue;
        int mask = q.length - 1; // implicit assert q != null
        int s = sp++;            // ok to increment sp before slot write
        UNSAFE.putOrderedObject(q, ((s & mask) << qShift) + qBase, t);
        if ((s -= base) == 0)
            pool.signalWork();   // was empty
        else if (s == mask)
            growQueue();         // is full
    }

    /**
     * Tries to take a task from the base of the queue, failing if
     * empty or contended. Note: Specializations of this code appear
     * in locallyDeqTask and elsewhere.
     *
     * @return a task, or null if none or contended
     */
    final ForkJoinTask<?> deqTask() {
        ForkJoinTask<?> t;
        ForkJoinTask<?>[] q;
        int b, i;
        if (sp != (b = base) &&
            (q = queue) != null && // must read q after b
            (t = q[i = (q.length - 1) & b]) != null && base == b &&
            UNSAFE.compareAndSwapObject(q, (i << qShift) + qBase, t, null)) {
            base = b + 1;
            return t;
        }
        return null;
    }

    /**
     * Tries to take a task from the base of own queue. Assumes active
     * status.  Called only by this thread.
     *
     * @return a task, or null if none
     */
    final ForkJoinTask<?> locallyDeqTask() {
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
            ForkJoinTask<?> t;
            int b, i;
            while (sp != (b = base)) {
                if ((t = q[i = (q.length - 1) & b]) != null && base == b &&
                    UNSAFE.compareAndSwapObject(q, (i << qShift) + qBase,
                                                t, null)) {
                    base = b + 1;
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Returns a popped task, or null if empty. Assumes active status.
     * Called only by this thread.
     */
    private ForkJoinTask<?> popTask() {
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
            int s;
            while ((s = sp) != base) {
                int i = (q.length - 1) & --s;
                long u = (i << qShift) + qBase; // raw offset
                ForkJoinTask<?> t = q[i];
                if (t == null)   // lost to stealer
                    break;
                if (UNSAFE.compareAndSwapObject(q, u, t, null)) {
                    sp = s; // putOrderedInt may encourage more timely write
                    // UNSAFE.putOrderedInt(this, spOffset, s);
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Specialized version of popTask to pop only if topmost element
     * is the given task. Called only by this thread while active.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final boolean unpushTask(ForkJoinTask<?> t) {
        int s;
        ForkJoinTask<?>[] q = queue;
        if ((s = sp) != base && q != null &&
            UNSAFE.compareAndSwapObject
            (q, (((q.length - 1) & --s) << qShift) + qBase, t, null)) {
            sp = s; // putOrderedInt may encourage more timely write
            // UNSAFE.putOrderedInt(this, spOffset, s);
            return true;
        }
        return false;
    }

    /**
     * Returns next task, or null if empty or contended.
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
            writeSlot(newQ, b & newMask, t);
        } while (++b != bf);
        pool.signalWork();
    }

    /**
     * Computes next value for random victim probe in scan().  Scans
     * don't require a very high quality generator, but also not a
     * crummy one.  Marsaglia xor-shift is cheap and works well enough.
     * Note: This is manually inlined in scan().
     */
    private static final int xorShift(int r) {
        r ^= r << 13;
        r ^= r >>> 17;
        return r ^ (r << 5);
    }

    /**
     * Tries to steal a task from another worker. Starts at a random
     * index of workers array, and probes workers until finding one
     * with non-empty queue or finding that all are empty.  It
     * randomly selects the first n probes. If these are empty, it
     * resorts to a circular sweep, which is necessary to accurately
     * set active status. (The circular sweep uses steps of
     * approximately half the array size plus 1, to avoid bias
     * stemming from leftmost packing of the array in ForkJoinPool.)
     *
     * This method must be both fast and quiet -- usually avoiding
     * memory accesses that could disrupt cache sharing etc other than
     * those needed to check for and take tasks (or to activate if not
     * already active). This accounts for, among other things,
     * updating random seed in place without storing it until exit.
     *
     * @return a task, or null if none found
     */
    private ForkJoinTask<?> scan() {
        ForkJoinPool p = pool;
        ForkJoinWorkerThread[] ws;        // worker array
        int n;                            // upper bound of #workers
        if ((ws = p.workers) != null && (n = ws.length) > 1) {
            boolean canSteal = active;    // shadow active status
            int r = seed;                 // extract seed once
            int mask = n - 1;
            int j = -n;                   // loop counter
            int k = r;                    // worker index, random if j < 0
            for (;;) {
                ForkJoinWorkerThread v = ws[k & mask];
                r ^= r << 13; r ^= r >>> 17; r ^= r << 5; // inline xorshift
                ForkJoinTask<?>[] q; ForkJoinTask<?> t; int b, a;
                if (v != null && (b = v.base) != v.sp &&
                    (q = v.queue) != null) {
                    int i = (q.length - 1) & b;
                    long u = (i << qShift) + qBase; // raw offset
                    int pid = poolIndex;
                    if ((t = q[i]) != null) {
                        if (!canSteal &&  // inline p.tryIncrementActiveCount
                            UNSAFE.compareAndSwapInt(p, poolRunStateOffset,
                                                     a = p.runState, a + 1))
                            canSteal = active = true;
                        if (canSteal && v.base == b++ &&
                            UNSAFE.compareAndSwapObject(q, u, t, null)) {
                            v.base = b;
                            v.stealHint = pid;
                            UNSAFE.putOrderedObject(this,
                                                    currentStealOffset, t);
                            seed = r;
                            ++stealCount;
                            return t;
                        }
                    }
                    j = -n;
                    k = r;                // restart on contention
                }
                else if (++j <= 0)
                    k = r;
                else if (j <= n)
                    k += (n >>> 1) | 1;
                else
                    break;
            }
        }
        return null;
    }

    // Run State management

    // status check methods used mainly by ForkJoinPool
    final boolean isRunning()     { return runState == 0; }
    final boolean isTerminating() { return (runState & TERMINATING) != 0; }
    final boolean isTerminated()  { return (runState & TERMINATED) != 0; }
    final boolean isSuspended()   { return (runState & SUSPENDED) != 0; }
    final boolean isTrimmed()     { return (runState & TRIMMED) != 0; }

    /**
     * Sets state to TERMINATING. Does NOT unpark or interrupt
     * to wake up if currently blocked. Callers must do so if desired.
     */
    final void shutdown() {
        for (;;) {
            int s = runState;
            if ((s & (TERMINATING|TERMINATED)) != 0)
                break;
            if ((s & SUSPENDED) != 0) { // kill and wakeup if suspended
                if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                             (s & ~SUSPENDED) |
                                             (TRIMMED|TERMINATING)))
                    break;
            }
            else if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                              s | TERMINATING))
                break;
        }
    }

    /**
     * Sets state to TERMINATED. Called only by onTermination().
     */
    private void setTerminated() {
        int s;
        do {} while (!UNSAFE.compareAndSwapInt(this, runStateOffset,
                                               s = runState,
                                               s | (TERMINATING|TERMINATED)));
    }

    /**
     * If suspended, tries to set status to unsuspended.
     * Does NOT wake up if blocked.
     *
     * @return true if successful
     */
    final boolean tryUnsuspend() {
        int s;
        while (((s = runState) & SUSPENDED) != 0) {
            if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                         s & ~SUSPENDED))
                return true;
        }
        return false;
    }

    /**
     * Sets suspended status and blocks as spare until resumed
     * or shutdown.
     */
    final void suspendAsSpare() {
        for (;;) {                  // set suspended unless terminating
            int s = runState;
            if ((s & TERMINATING) != 0) { // must kill
                if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                             s | (TRIMMED | TERMINATING)))
                    return;
            }
            else if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                              s | SUSPENDED))
                break;
        }
        ForkJoinPool p = pool;
        p.pushSpare(this);
        while ((runState & SUSPENDED) != 0) {
            if (p.tryAccumulateStealCount(this)) {
                interrupted();          // clear/ignore interrupts
                if ((runState & SUSPENDED) == 0)
                    break;
                LockSupport.park(this);
            }
        }
    }

    // Misc support methods for ForkJoinPool

    /**
     * Returns an estimate of the number of tasks in the queue.  Also
     * used by ForkJoinTask.
     */
    final int getQueueSize() {
        int n; // external calls must read base first
        return (n = -base + sp) <= 0 ? 0 : n;
    }

    /**
     * Removes and cancels all tasks in queue.  Can be called from any
     * thread.
     */
    final void cancelTasks() {
        ForkJoinTask<?> cj = currentJoin; // try to cancel ongoing tasks
        if (cj != null) {
            currentJoin = null;
            cj.cancelIgnoringExceptions();
            try {
                this.interrupt(); // awaken wait
            } catch (SecurityException ignore) {
            }
        }
        ForkJoinTask<?> cs = currentSteal;
        if (cs != null) {
            currentSteal = null;
            cs.cancelIgnoringExceptions();
        }
        while (base != sp) {
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
        while (base != sp) {
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
     * Gets and removes a local task.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> pollLocalTask() {
        ForkJoinPool p = pool;
        while (sp != base) {
            int a; // inline p.tryIncrementActiveCount
            if (active ||
                (active = UNSAFE.compareAndSwapInt(p, poolRunStateOffset,
                                                   a = p.runState, a + 1)))
                return locallyFifo ? locallyDeqTask() : popTask();
        }
        return null;
    }

    /**
     * Gets and removes a local or stolen task.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> pollTask() {
        ForkJoinTask<?> t = pollLocalTask();
        if (t == null) {
            t = scan();
            // cannot retain/track/help steal
            UNSAFE.putOrderedObject(this, currentStealOffset, null);
        }
        return t;
    }

    /**
     * Possibly runs some tasks and/or blocks, until task is done.
     *
     * @param joinMe the task to join
     */
    final void joinTask(ForkJoinTask<?> joinMe) {
        // currentJoin only written by this thread; only need ordered store
        ForkJoinTask<?> prevJoin = currentJoin;
        UNSAFE.putOrderedObject(this, currentJoinOffset, joinMe);
        if (sp != base)
            localHelpJoinTask(joinMe);
        if (joinMe.status >= 0)
            pool.awaitJoin(joinMe, this);
        UNSAFE.putOrderedObject(this, currentJoinOffset, prevJoin);
    }

    /**
     * Run tasks in local queue until given task is done.
     *
     * @param joinMe the task to join
     */
    private void localHelpJoinTask(ForkJoinTask<?> joinMe) {
        int s;
        ForkJoinTask<?>[] q;
        while (joinMe.status >= 0 && (s = sp) != base && (q = queue) != null) {
            int i = (q.length - 1) & --s;
            long u = (i << qShift) + qBase; // raw offset
            ForkJoinTask<?> t = q[i];
            if (t == null)  // lost to a stealer
                break;
            if (UNSAFE.compareAndSwapObject(q, u, t, null)) {
                /*
                 * This recheck (and similarly in helpJoinTask)
                 * handles cases where joinMe is independently
                 * cancelled or forced even though there is other work
                 * available. Back out of the pop by putting t back
                 * into slot before we commit by writing sp.
                 */
                if (joinMe.status < 0) {
                    UNSAFE.putObjectVolatile(q, u, t);
                    break;
                }
                sp = s;
                // UNSAFE.putOrderedInt(this, spOffset, s);
                t.quietlyExec();
            }
        }
    }

    /**
     * Unless terminating, tries to locate and help perform tasks for
     * a stealer of the given task, or in turn one of its stealers.
     * Traces currentSteal->currentJoin links looking for a thread
     * working on a descendant of the given task and with a non-empty
     * queue to steal back and execute tasks from.
     *
     * The implementation is very branchy to cope with potential
     * inconsistencies or loops encountering chains that are stale,
     * unknown, or of length greater than MAX_HELP_DEPTH links.  All
     * of these cases are dealt with by just returning back to the
     * caller, who is expected to retry if other join mechanisms also
     * don't work out.
     *
     * @param joinMe the task to join
     */
    final void helpJoinTask(ForkJoinTask<?> joinMe) {
        ForkJoinWorkerThread[] ws;
        int n;
        if (joinMe.status < 0)                // already done
            return;
        if ((runState & TERMINATING) != 0) {  // cancel if shutting down
            joinMe.cancelIgnoringExceptions();
            return;
        }
        if ((ws = pool.workers) == null || (n = ws.length) <= 1)
            return;                           // need at least 2 workers

        ForkJoinTask<?> task = joinMe;        // base of chain
        ForkJoinWorkerThread thread = this;   // thread with stolen task
        for (int d = 0; d < MAX_HELP_DEPTH; ++d) { // chain length
            // Try to find v, the stealer of task, by first using hint
            ForkJoinWorkerThread v = ws[thread.stealHint & (n - 1)];
            if (v == null || v.currentSteal != task) {
                for (int j = 0; ; ++j) {      // search array
                    if (j < n) {
                        ForkJoinTask<?> vs;
                        if ((v = ws[j]) != null &&
                            (vs = v.currentSteal) != null) {
                            if (joinMe.status < 0 || task.status < 0)
                                return;       // stale or done
                            if (vs == task) {
                                thread.stealHint = j;
                                break;        // save hint for next time
                            }
                        }
                    }
                    else
                        return;               // no stealer
                }
            }
            for (;;) { // Try to help v, using specialized form of deqTask
                if (joinMe.status < 0)
                    return;
                int b = v.base;
                ForkJoinTask<?>[] q = v.queue;
                if (b == v.sp || q == null)
                    break;
                int i = (q.length - 1) & b;
                long u = (i << qShift) + qBase;
                ForkJoinTask<?> t = q[i];
                int pid = poolIndex;
                ForkJoinTask<?> ps = currentSteal;
                if (task.status < 0)
                    return;                   // stale or done
                if (t != null && v.base == b++ &&
                    UNSAFE.compareAndSwapObject(q, u, t, null)) {
                    if (joinMe.status < 0) {
                        UNSAFE.putObjectVolatile(q, u, t);
                        return;               // back out on cancel
                    }
                    v.base = b;
                    v.stealHint = pid;
                    UNSAFE.putOrderedObject(this, currentStealOffset, t);
                    t.quietlyExec();
                    UNSAFE.putOrderedObject(this, currentStealOffset, ps);
                }
            }
            // Try to descend to find v's stealer
            ForkJoinTask<?> next = v.currentJoin;
            if (task.status < 0 || next == null || next == task ||
                joinMe.status < 0)
                return;
            task = next;
            thread = v;
        }
    }

    /**
     * Implements ForkJoinTask.getSurplusQueuedTaskCount().
     * Returns an estimate of the number of tasks, offset by a
     * function of number of idle workers.
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
     * just use estimated queue length (although note that (sp - base)
     * can be an overestimate because of stealers lagging increments
     * of base).  However, this strategy alone leads to serious
     * mis-estimates in some non-steady-state conditions (ramp-up,
     * ramp-down, other stalls). We can detect many of these by
     * further considering the number of "idle" threads, that are
     * known to have zero queued tasks, so compensate by a factor of
     * (#idle/#active) threads.
     */
    final int getEstimatedSurplusTaskCount() {
        return sp - base - pool.idlePerActive();
    }

    /**
     * Runs tasks until {@code pool.isQuiescent()}.
     */
    final void helpQuiescePool() {
        ForkJoinTask<?> ps = currentSteal; // to restore below
        for (;;) {
            ForkJoinTask<?> t = pollLocalTask();
            if (t != null || (t = scan()) != null)
                t.quietlyExec();
            else {
                ForkJoinPool p = pool;
                int a; // to inline CASes
                if (active) {
                    if (!UNSAFE.compareAndSwapInt
                        (p, poolRunStateOffset, a = p.runState, a - 1))
                        continue;   // retry later
                    active = false; // inactivate
                    UNSAFE.putOrderedObject(this, currentStealOffset, ps);
                }
                if (p.isQuiescent()) {
                    active = true; // re-activate
                    do {} while (!UNSAFE.compareAndSwapInt
                                 (p, poolRunStateOffset, a = p.runState, a+1));
                    return;
                }
            }
        }
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE = sun.misc.Unsafe.getUnsafe();
    private static final long spOffset =
        objectFieldOffset("sp", ForkJoinWorkerThread.class);
    private static final long runStateOffset =
        objectFieldOffset("runState", ForkJoinWorkerThread.class);
    private static final long currentJoinOffset =
        objectFieldOffset("currentJoin", ForkJoinWorkerThread.class);
    private static final long currentStealOffset =
        objectFieldOffset("currentSteal", ForkJoinWorkerThread.class);
    private static final long qBase =
        UNSAFE.arrayBaseOffset(ForkJoinTask[].class);
    private static final long poolRunStateOffset = // to inline CAS
        objectFieldOffset("runState", ForkJoinPool.class);

    private static final int qShift;

    static {
        int s = UNSAFE.arrayIndexScale(ForkJoinTask[].class);
        if ((s & (s-1)) != 0)
            throw new Error("data type scale not a power of two");
        qShift = 31 - Integer.numberOfLeadingZeros(s);
        MAXIMUM_QUEUE_CAPACITY = 1 << (31 - qShift);
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
