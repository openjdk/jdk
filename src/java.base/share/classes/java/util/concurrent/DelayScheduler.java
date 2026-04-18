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

import java.util.Arrays;
import jdk.internal.misc.Unsafe;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An add-on for ForkJoinPools that provides scheduling for
 * delayed and periodic tasks
 */
@jdk.internal.vm.annotation.Contended
final class DelayScheduler extends Thread {

    /*
     * A DelayScheduler maintains a 4-ary heap (see
     * https://en.wikipedia.org/wiki/D-ary_heap) based on trigger
     * times (field ScheduledForkJoinTask.when) along with a pending
     * queue of tasks submitted by other threads. When enabled (their
     * delays elapse), tasks are relayed to the pool, or run directly
     * if task.isImmediate.  Immediate mode is designed for internal
     * jdk usages in which the (non-blocking) action is to cancel,
     * unblock or differently relay another async task (in some cases,
     * this relies on user code to trigger async tasks).  If
     * processing encounters resource failures (possible when growing
     * heap or ForkJoinPool WorkQueue arrays), tasks are cancelled.
     *
     * To reduce memory contention, the current heap size is recorded
     * only when blocked to allow method canShutDown to conservatively
     * check emptiness, and to report approximate current size for
     * monitoring.
     *
     * The pending queue uses a design similar to ForkJoinTask.Aux
     * queues: Incoming requests prepend (Treiber-stack-style) to the
     * pending list. The scheduler thread takes and nulls out the
     * entire list per step to process them as a batch. The pending
     * queue may encounter contention and retries among requesters,
     * but much less so versus the scheduler. It is possible to use
     * multiple pending queues to reduce this form of contention but
     * it doesn't seem worthwhile even under heavy loads.
     *
     * The implementation relies on the scheduler being a non-virtual
     * final Thread subclass.  Field "active" records whether the
     * scheduler may have any pending tasks (and/or shutdown actions)
     * to process, otherwise parking either indefinitely or until the
     * next task deadline. Incoming pending tasks ensure active
     * status, unparking if necessary. The scheduler thread sets
     * status to inactive when there is apparently no work, and then
     * rechecks before actually parking.  The active field takes on a
     * negative value on termination, as a sentinel used in pool
     * termination checks as well as to suppress reactivation after
     * terminating.
     *
     * We avoid the need for auxiliary data structures by embedding
     * pending queue links, heap indices, and pool references inside
     * ScheduledForkJoinTasks. (We use the same structure for both
     * Runnable and Callable versions, since including an extra field
     * in either case doesn't hurt -- it seems mildly preferable for
     * these objects to be larger than other kinds of tasks to reduce
     * false sharing during possibly frequent bookkeeping updates.) To
     * reduce GC pressure and memory retention, these are nulled out
     * as soon as possible.
     *
     * The implementation is designed to accommodate usages in which
     * many or even most tasks are cancelled before executing (which
     * is typical with IO-based timeouts). The use of a 4-ary heap (in
     * which each element has up to 4 children) improves locality, and
     * reduces the need for array movement and memory writes compared
     * to a standard binary heap, at the expense of more expensive
     * replace() operations (with about half the writes but twice the
     * reads).  Especially in the presence of cancellations, this is
     * often faster because the replace method removes cancelled tasks
     * seen while performing sift-down operations, in which case these
     * elements are not further recorded or accessed, even before
     * processing the removal request generated by method
     * ScheduledForkJoinTask.cancel() (which is then a no-op or not
     * generated at all).
     *
     * To ensure that comparisons do not encounter integer wraparound
     * errors, times are offset with the most negative possible value
     * (nanoTimeOffset) determined during static initialization.
     * Negative delays are screened out before use.
     *
     * Upon noticing pool shutdown, delayed and/or periodic tasks are
     * purged according to pool configuration and policy; the
     * scheduler then tries to terminate the pool if the heap is
     * empty. The asynchronicity of these steps with respect to pool
     * runState weakens guarantees about exactly when purged tasks
     * report isCancelled to callers (they do not run, but there may
     * be a lag setting their status).
     */

    private ForkJoinPool pool;
    private ScheduledForkJoinTask<?>[] heapArray;
    int heapSize;
    volatile boolean cancelDelayedTasksOnShutdown; // policy control
    private boolean cleanedPeriodicTasks;

    @jdk.internal.vm.annotation.Contended("p")
    volatile ScheduledForkJoinTask<?> pending; // submitted adds and removes
    @jdk.internal.vm.annotation.Contended("p")
    volatile int active;             // 0: inactive, -1: stopped, +1: running

    private static final int INITIAL_HEAP_CAPACITY = 1 << 6;
    private static final int POOL_STOPPING = 1; // must match ForkJoinPool
    private static final long MIN_PARK = 255L; // spin vs park threshold in nanos
    static final long nanoTimeOffset = // Most negative possible time base
        Math.min(System.nanoTime(), 0L) + Long.MIN_VALUE;

    private static final Unsafe U;     // for atomic operations
    private static final long ACTIVE;
    private static final long PENDING;
    static {
        U = Unsafe.getUnsafe();
        Class<DelayScheduler> klass = DelayScheduler.class;
        ACTIVE = U.objectFieldOffset(klass, "active");
        PENDING = U.objectFieldOffset(klass, "pending");
    }

    DelayScheduler(ForkJoinPool p, String name) {
        super(name);
        setDaemon(true);
        pool = p;
        heapArray = new ScheduledForkJoinTask<?>[INITIAL_HEAP_CAPACITY];
    }

    /**
     * Returns System.nanoTime() with nanoTimeOffset
     */
    static final long now() {
        return nanoTimeOffset + System.nanoTime();
    }

    /**
     * Ensures the scheduler is not parked unless stopped.
     * Returns negative if already stopped
     */
    final int signal() {
        int state;
        if ((state = active) == 0 && U.getAndBitwiseOrInt(this, ACTIVE, 1) == 0)
            U.unpark(this);
        return state;
    }

    /**
     * Inserts the task (if nonnull) to pending queue, to add,
     * remove, or ignore depending on task status when processed.
     */
    final void pend(ScheduledForkJoinTask<?> task) {
        ScheduledForkJoinTask<?> f = pending;
        if (task != null) {
            do {} while (
                f != (f = (ScheduledForkJoinTask<?>)
                      U.compareAndExchangeReference(
                          this, PENDING, task.nextPending = f, task)));
            signal();
        }
    }

    /**
     * Returns true if (momentarily) inactive and heap is empty
     */
    final boolean canShutDown() {
        int state = active;
        return (state < 0 ||
                ((state == 0 || Thread.currentThread() == this) &&
                 pending == null && heapSize <= 0));
    }

    /**
     * Returns the number of elements in heap when last idle
     */
    final int lastStableSize() {
        return (active < 0) ? 0 : heapSize;
    }

    /**
     * Turns on cancelDelayedTasksOnShutdown policy
     */
    final void  cancelDelayedTasksOnShutdown() {
        cancelDelayedTasksOnShutdown = true;
        signal();
    }

    /**
     * Sets up and runs scheduling loop
     */
    public final void run() {
        active = 1;
        loop();
    }

    /**
     * Processes tasks until stopped
     */
    private void loop() {
        try {
            long parkTime;
            while ((parkTime = process()) >= 0L) {
                if (parkTime == 0L || parkTime > MIN_PARK) {
                    active = 0;
                    if (!Thread.interrupted() && pending == null && active == 0)
                        U.park(false, parkTime);
                    active= 1;
                }
                else {  // reduce wasteful park calls
                    do Thread.onSpinWait(); while (--parkTime > 0L);
                }
            }
        } finally {
            heapSize = 0;
            active = -1;
            clearPending();
            ForkJoinPool p = pool;
            if (p != null)
                p.tryStopIfShutdown(this);
            clearPending(); // cleanup
        }
    }

    /**
     * 1. Take pending tasks in batches, to add or remove from heap
     * 2. Trigger an enabled task by submitting to pool or run if immediate
     * 3. Check for shutdown, either exiting or preparing for shutdown when empty
     * 4. If apparently no work, return park time, else loop
     *
     * @return negative for terminate, 0 for unbounded, else park time in nanos
     */
    private long process() {
        ForkJoinPool p = pool;
        ScheduledForkJoinTask<?>[] h = heapArray;
        int n = heapSize;
        if (p == null || h == null)
            return Long.MIN_VALUE;            // only possible after stopping
        for (;;) {
            while (pending != null) {         // process pending tasks
                ScheduledForkJoinTask<?> t;
                if ((t = (ScheduledForkJoinTask<?>)
                     U.getAndSetReference(this, PENDING, null)) != null) {
                    for (ScheduledForkJoinTask<?> next;;) {
                        int i; ScheduledForkJoinTask<?>[] a;
                        if ((i = t.heapIndex) >= 0)
                            n = replace(h, i, t, n);
                        else if ((n = add(t, h, n)) >= h.length &&
                                 (a = tryResize(h)) != null)
                            h = heapArray = a;
                        if ((next = t.nextPending) == null)
                            break;
                        t.nextPending = null;
                        t = next;
                    }
                }
            }

            long parkTime, d; ScheduledForkJoinTask<?> f; int runStatus;
            if (n == 0 || h.length == 0 || (f = h[0]) == null)
                parkTime = 0L;
            else if ((d = f.when - now()) > 0L)
                parkTime = d;
            else {                          // run ready task
                parkTime = ((n = replace(h, 0, f, n)) == 0) ? 0L : -1L;
                if (f.isImmediate)
                    f.doExec();
                else
                    p.executeEnabledScheduledTask(f);
            }

            if ((runStatus = p.shutdownStatus(this)) != 0) {
                int stop = runStatus & POOL_STOPPING;
                if (n > 0) {
                    if (stop != 0 || cancelDelayedTasksOnShutdown)
                        n = cancelAll(h, n);
                    else if (!cleanedPeriodicTasks)
                        n = removePeriodicTasks(h, n);
                }
                if ((heapSize = n) == 0 &&
                    (stop != 0 || p.tryStopIfShutdown(this)))
                    return Long.MIN_VALUE;
                if (!cleanedPeriodicTasks && pending == null)
                    cleanedPeriodicTasks = true;
            }

            if (parkTime >= 0L && pending == null) {
                heapSize = n;
                return parkTime;
            }
        }
    }

    /**
     * Adds task t to heap unless full or cancelled
     * @return current heap size
     */
    private static int add(ScheduledForkJoinTask<?> t, ScheduledForkJoinTask<?>[] h,
                           int n) {
        if (t != null) {
            if (h == null || n < 0 || h.length <= n)
                t.trySetCancelled();
            else {
                long d = t.when;
                if (t.status >= 0) {
                    ScheduledForkJoinTask<?> parent;
                    int k = n++, pk;
                    while (k > 0 &&
                           (parent = h[pk = (k - 1) >>> 2]) != null &&
                           (parent.when > d)) {
                        parent.heapIndex = k;
                        h[k] = parent;
                        k = pk;
                    }
                    t.heapIndex = k;
                    h[k] = t;
                }
            }
        }
        assert checkHeap(h, n);
        return n;
    }

    /**
     * Replaces removed heap element at index k, along with other
     * cancelled nodes found while doing so.
     * @return current heap size
     */
    private static int replace(ScheduledForkJoinTask<?>[] h, int k,
                               ScheduledForkJoinTask<?> f, int n) {
        if (f != null)
            f.heapIndex = -1;
        if (h != null && h.length >= n) { // hoist checks
            while (k >= 0 && n > k) {
                int alsoReplace = -1;  // non-negative if cancelled task seen
                ScheduledForkJoinTask<?> t = null, u;
                long d = 0L;
                while (--n > k) {      // find uncancelled replacement
                    if ((u = h[n]) != null) {
                        h[n] = null;
                        d = u.when;
                        if (u.status >= 0) {
                            t = u;
                            break;
                        }
                        u.heapIndex = -1;
                    }
                }
                if (t != null) {
                    while (k > 0) {    // sift up if replaced with smaller value
                        ScheduledForkJoinTask<?> parent; int pk;
                        if ((parent = h[pk = (k - 1) >>> 2]) == null ||
                            parent.when <= d)
                            break;
                        parent.heapIndex = k;
                        h[k] = parent;
                        k = pk;
                    }
                    for (int cs; (cs = (k << 2) + 1) < n; ) { // sift down
                        ScheduledForkJoinTask<?> leastChild = null;
                        int leastIndex = 0;
                        long leastValue = d;    // at most 4 children
                        for (int ck, j = 0; j < 4 && (ck = j + cs) < n; ++j) {
                            ScheduledForkJoinTask<?> c; long cd;
                            if ((c = h[ck]) != null && (cd = c.when) < leastValue) {
                                leastValue = cd;
                                leastIndex = ck;
                                leastChild = c;
                            }
                        }
                        if (leastChild == null) // already ordered
                            break;
                        if ((h[k] = leastChild).status >= 0 || alsoReplace >= 0)
                            leastChild.heapIndex = k;
                        else {
                            leastChild.heapIndex = -1;
                            alsoReplace = k;
                        }
                        k = leastIndex;
                    }
                    t.heapIndex = k;
                }
                h[k] = t;
                k = alsoReplace;
            }
        }
        assert checkHeap(h, n);
        return n;
    }

    private static ScheduledForkJoinTask<?>[] tryResize(ScheduledForkJoinTask<?>[] h) {
        int cap, newCap;
        ScheduledForkJoinTask<?>[] a = null;
        if (h != null && (cap = h.length) > 0 && (newCap = cap << 1) > cap) {
            try {
                a = Arrays.copyOf(h, newCap);
            } catch (Error | RuntimeException ex) {
            }
        }
        return a;
    }

    private static int cancelAll(ScheduledForkJoinTask<?>[] h, int n) {
        if (h != null && h.length >= n) {
            ScheduledForkJoinTask<?> t;
            for (int i = 0; i < n; ++i) {
                if ((t = h[i]) != null) {
                    h[i] = null;
                    t.heapIndex = -1;
                    t.trySetCancelled();
                }
            }
        }
        return 0;
    }

    private static int removePeriodicTasks(ScheduledForkJoinTask<?>[] h, int n) {
        if (h != null && h.length >= n) {
            ScheduledForkJoinTask<?> t;
            for (int i = n - 1; i >= 0; --i) {
                if ((t = h[i]) != null && t.nextDelay != 0L) {
                    n = replace(h, i, t, n);
                    if (t.status >= 0)
                        t.trySetCancelled();
                }
            }
        }
        return n;
    }

    private void clearPending() {
        if (pending != null) {
            for (ScheduledForkJoinTask<?> a = (ScheduledForkJoinTask<?>)
                     U.getAndSetReference(this, PENDING, null);
                 a != null; a = a.nextPending)
                a.trySetCancelled();
        }
    }

    /**
     * Invariant checks
     */
    private static boolean checkHeap(ScheduledForkJoinTask<?>[] h, int n) {
        if (n < 0)
            return false;
        for (int i = 0; i < h.length; ++i) {
            ScheduledForkJoinTask<?> t = h[i];
            if (t == null) {         // unused slots all null
                if (i < n)
                    return false;
            }
            else {
                long v = t.when;
                int x = t.heapIndex;
                if (x != i && x >= 0) // valid index unless removing
                    return false;
                if (i > 0 && h[(i - 1) >>> 2].when > v) // ordered wrt parent
                    return false;
                int cs = (i << 2) + 1; // ordered wrt children
                for (int ck, j = 0; j < 4 && (ck = cs + j) < n; ++j) {
                    if (h[ck].when < v)
                        return false;
                }
            }
        }
        return true;
    }

    /**
     * Task class for DelayScheduler operations
     */
    @SuppressWarnings("serial")    // Not designed to be serializable
    static final class ScheduledForkJoinTask<T>
        extends ForkJoinTask.InterruptibleTask<T>
        implements ScheduledFuture<T> {
        ForkJoinPool pool;         // nulled out after use
        Runnable runnable;         // at most one of runnable or callable nonnull
        Callable<? extends T> callable;
        T result;
        ScheduledForkJoinTask<?> nextPending; // for DelayScheduler pending queue
        long when;                  // nanoTime-based trigger time
        final long nextDelay;       // 0: once; <0: fixedDelay; >0: fixedRate
        int heapIndex;              // if non-negative, index on heap
        final boolean isImmediate;  // run by scheduler vs submitted when ready

        /**
         * Creates a new ScheduledForkJoinTask
         * @param delay initial delay, in nanoseconds
         * @param nextDelay 0 for one-shot, negative for fixed delay,
         *        positive for fixed rate, in nanoseconds
         * @param isImmediate if action is to be performed
         *        by scheduler versus submitting to a WorkQueue
         * @param runnable action (null if implementing callable version)
         * @param callable function (null if implementing runnable versions)
         * @param pool the pool for resubmissions and cancellations
         *        (disabled if null)
         */
        public ScheduledForkJoinTask(long delay, long nextDelay,
                                     boolean isImmediate, Runnable runnable,
                                     Callable<T> callable, ForkJoinPool pool) {
            this.when = DelayScheduler.now() + Math.max(delay, 0L);
            this.heapIndex = -1;
            this.nextDelay = nextDelay;
            this.isImmediate = isImmediate;
            this.runnable = runnable;
            this.callable = callable;
            this.pool = pool;
        }

        public void schedule() { // relay to pool, to allow independent use
            ForkJoinPool p;
            if ((p = pool) != null) // else already run
                p.scheduleDelayedTask(this);
        }

        // InterruptibleTask methods
        public final T getRawResult() { return result; }
        public final void setRawResult(T v) { result = v; }
        final Object adaptee() { return (runnable != null) ? runnable : callable; }

        final T compute() throws Exception {
            Callable<? extends T> c; Runnable r;
            T res = null;
            if ((r = runnable) != null)
                r.run();
            else if ((c = callable) != null)
                res = c.call();
            return res;
        }

        final boolean postExec() {       // possibly resubmit
            long d; ForkJoinPool p; DelayScheduler ds;
            if ((d = nextDelay) != 0L && // is periodic
                status >= 0 &&           // not abnormally completed
                (p = pool) != null && (ds = p.delayScheduler) != null) {
                if (p.shutdownStatus(ds) == 0) {
                    heapIndex = -1;
                    if (d < 0L)
                        when = DelayScheduler.now() - d;
                    else
                        when += d;
                    ds.pend(this);
                    return false;
                }
                trySetCancelled();       // pool is shutdown
            }
            pool = null;                 // reduce memory retention
            runnable = null;
            callable = null;
            return true;
        }

        public final boolean cancel(boolean mayInterruptIfRunning) {
            int s; ForkJoinPool p; DelayScheduler ds;
            if ((s = trySetCancelled()) < 0)
                return ((s & (ABNORMAL | THROWN)) == ABNORMAL);
            if ((p = pool) != null &&
                !interruptIfRunning(mayInterruptIfRunning)) {
                pool = null;
                runnable = null;
                callable = null;
                if (heapIndex >= 0 && nextPending == null &&
                    (ds = p.delayScheduler) != null)
                    ds.pend(this);      // for heap cleanup
            }
            return true;
        }


        // ScheduledFuture methods
        public final long getDelay(TimeUnit unit) {
            return unit.convert(when - DelayScheduler.now(), NANOSECONDS);
        }
        public int compareTo(Delayed other) { // never used internally
            long diff = (other instanceof ScheduledForkJoinTask<?> t) ?
                when - t.when : // avoid nanoTime calls and conversions
                getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
            return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
        }
    }

}

