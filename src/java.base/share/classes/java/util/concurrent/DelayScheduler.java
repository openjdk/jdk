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
@jdk.internal.vm.annotation.Contended()
final class DelayScheduler extends Thread {

    /*
     * A DelayScheduler maintains a binary heap based on trigger times
     * (field ScheduledForkJoinTask.when) along with a pending queue
     * of tasks submitted by other threads. When ready, tasks are
     * relayed to the pool (or run directly if in task.isImmediate).
     *
     * To reduce memory contention, the heap is maintained solely via
     * local variables in method loop() (forcing noticeable code
     * sprawl), recording only the current heap size when blocked to
     * allow method canShutDown to conservatively check emptiness, and
     * to report approximate current size for monitoring.
     *
     * The pending queue uses a design similar to ForkJoinTask.Aux
     * queues: Incoming requests prepend (Treiber-stack-style) to the
     * pending list. The scheduler thread takes and nulls out the
     * entire list per step to process them as a batch. The pending
     * queue may encounter contention and retries among requesters,
     * but much less so versus the scheduler.
     *
     * Field "active" records whether the scheduler may have any
     * pending tasks (and/or shutdown actions) to process, otherwise
     * parking either indefinitely or until the next task
     * deadline. Incoming pending tasks ensure active status,
     * unparking if necessary. The scheduler thread sets status to
     * inactive when there is apparently no work, and then rechecks
     * before actually parking.  The active field takes on a negative
     * value on termination, as a sentinel used in pool tryTerminate
     * checks as well as to suppress reactivation while terminating.
     *
     * The implementation is designed to accommodate usages in which
     * many or even most tasks are cancelled before executing (mainly
     * IO-based timeouts).  Cancellations are added to the pending
     * queue in method ScheduledForkJoinTask.cancel(), to remove them
     * from the heap. (This requires some safeguards to deal with
     * tasks cancelled while they are still pending.)  In addition,
     * the heap replace method removes any cancelled tasks seen while
     * performing sift-down operations, in which case elements are
     * removed even before processing the removal request (which is
     * then a no-op).
     *
     * To ensure that comparisons do not encounter integer wrap
     * errors, times are offset with the most negative possible value
     * (nanoTimeOffset) determined during static initialization.
     * Negative delays are screened out before use.
     *
     * For the sake of compatibility with ScheduledThreadPoolExecutor,
     * shutdown follows the same rules, which add some further steps
     * beyond the cleanup associated with shutdownNow.  Upon noticing
     * pool shutdown, delayed and/or periodic tasks are purged; the
     * scheduler then tries to terminate the pool if the heap is
     * empty. The asynchronicity of these steps with respect to pool
     * runState weakens guarantees about exactly when purged tasks
     * report isCancelled to callers (they do not run, but there may
     * be a lag setting their status).
     */

    private static final int INITIAL_HEAP_CAPACITY = 1 << 6;
    private ForkJoinPool pool;       // read once and detached upon starting
    volatile ScheduledForkJoinTask<?> pending; // for submited adds and removes
    volatile int active;             // 0: inactive, -1: stopped, +1: running
    int restingSize;                 // written only before parking

    private static final Unsafe U;
    private static final long ACTIVE;
    private static final long PENDING;
    static final long nanoTimeOffset;
    static {
        U = Unsafe.getUnsafe();
        Class<DelayScheduler> klass = DelayScheduler.class;
        ACTIVE = U.objectFieldOffset(klass, "active");
        PENDING = U.objectFieldOffset(klass, "pending");
        long ns = System.nanoTime(); // ensure negative to avoid overflow
        nanoTimeOffset = Long.MIN_VALUE + Math.min(ns, 0L);
    }

    DelayScheduler(ForkJoinPool p, String name) {
        super(name);
        setDaemon(true);
        pool = p;
    }

    /**
     * Returns System.nanoTime() with nanoTimeOffset
     */
    static final long now() {
        return nanoTimeOffset + System.nanoTime();
    }

    /**
     * Ensure active, unparking if necessary, unless stopped.
     * Returns the status as observed prior to activating
     */
    final int ensureActive() {
        int state;
        if ((state = active) == 0 && U.getAndBitwiseOrInt(this, ACTIVE, 1) == 0)
            U.unpark(this);
        return state;
    }

    /**
     * Inserts the task to pending queue, to add, remove, or ignore
     * depending on task status when processed.
     */
    final void pend(ScheduledForkJoinTask<?> task) {
        ScheduledForkJoinTask<?> f = pending;
        if (task != null) {
            do {} while (
                f != (f = (ScheduledForkJoinTask<?>)
                      U.compareAndExchangeReference(
                          this, PENDING, task.nextPending = f, task)));
            ensureActive();
        }
    }

    /**
     * Returns true if (momentarily) inactive and heap is empty
     */
    final boolean canShutDown() {
        return (active <= 0 && restingSize <= 0);
    }

    /**
     * Returns an approximate number of elements in heap
     */
    final int approximateSize() {
        return (active < 0) ? 0 : restingSize;
    }

    /**
     * Sets up and runs scheduling loop
     */
    public final void run() {
        ForkJoinPool p = pool;
        pool = null;     // detach
        if (p != null) { // currently always true
            try {
                loop(p);
            } finally {
                restingSize = 0;
                active = -1;
                p.tryStopIfEnabled();
            }
        }
    }

    /**
     * After initialization, repeatedly:
     * 1. Process pending tasks in batches, to add or remove from heap,
     * 2. Check for shutdown, either exiting or preparing for shutdown when empty
     * 3. Trigger all ready tasks by externally submitting them to pool
     * 4. If active, set tentatively inactive,
     *    else park until next trigger time, or indefinitely if none
     */
    private void loop(ForkJoinPool p) {
        p.onDelaySchedulerStart();
        ScheduledForkJoinTask<?>[] h =         // initial heap array
            new ScheduledForkJoinTask<?>[INITIAL_HEAP_CAPACITY];
        int cap = h.length, n = 0, prevRunStatus = 0; // n is heap size
        for (;;) {                             // loop until stopped
            ScheduledForkJoinTask<?> t; int runStatus;
            while (pending != null &&          // process pending tasks
                   (t = (ScheduledForkJoinTask<?>)
                    U.getAndSetReference(this, PENDING, null)) != null) {
                ScheduledForkJoinTask<?> next;
                do {
                    next = t.nextPending;
                    long d = t.when;
                    int i = t.heapIndex, stat = t.status;
                    if (next != null)
                        t.nextPending = null;
                    if (i >= 0) {
                        t.heapIndex = -1;
                        if (i < n && i < cap && h[i] == t)
                            n = replace(h, i, n);
                    }
                    else if (stat >= 0) {
                        if (n >= cap || n < 0) // couldn't resize
                            t.trySetCancelled();
                        else {                 // add and sift up
                            ScheduledForkJoinTask<?> parent;
                            ScheduledForkJoinTask<?>[] nh;
                            int k = n++, pk, nc;
                            while (k > 0 &&
                                   (parent = h[pk = (k - 1) >>> 1]) != null &&
                                   (parent.when > d)) {
                                parent.heapIndex = k;
                                h[k] = parent;
                                k = pk;
                            }
                            t.heapIndex = k;
                            h[k] = t;
                            if (n >= cap && (nh = growHeap(h, cap)) != null &&
                                (nc = nh.length) > cap) {
                                cap = nc;     // else keep using old array
                                h = nh;
                            }
                        }
                    }
                } while ((t = next) != null);
            }

            if ((runStatus = p.delaySchedulerRunStatus()) != 0) {
                if ((n = tryStop(p, h, n, runStatus, prevRunStatus)) < 0)
                    break;
                prevRunStatus = runStatus;
            }

            long parkTime = 0L;             // zero for untimed park
            while (n > 0 && h.length > 0) { // submit ready tasks
                ScheduledForkJoinTask<?> f; int stat;
                if ((f = h[0]) != null) {
                    long d = f.when - now();
                    if ((stat = f.status) >= 0 && d > 0L) {
                        parkTime = d;
                        break;
                    }
                    f.heapIndex = -1;
                    if (stat >= 0) {
                        if (f.isImmediate)
                            f.doExec();
                        else
                            p.executeReadyScheduledTask(f);
                    }
                }
                n = replace(h, 0, n);
            }

            if (pending == null) {
                restingSize = n;
                Thread.interrupted();       // clear before park
                if (active == 0)
                    U.park(false, parkTime);
                else
                    U.compareAndSetInt(this, ACTIVE, 1, 0);
            }
        }
    }

    /**
     * Tries to reallocate the heap array; returning null on failure
     */
    private ScheduledForkJoinTask<?>[] growHeap(ScheduledForkJoinTask<?>[] h,
                                                int cap) {
        int newCap = cap << 1;
        ScheduledForkJoinTask<?>[] nh = null;
        if (h != null && h.length == cap && cap < newCap) {
            try {
                nh = Arrays.copyOf(h, newCap);
            } catch (Error | RuntimeException ex) {
            }
        }
        return nh;
    }

    /**
     * Replaces removed heap element at index k, along with other
     * cancelled nodes found while doing so.
     * @return current heap size
     */
    private static int replace(ScheduledForkJoinTask<?>[] h, int k, int n) {
        if (h != null && h.length >= n) {
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
                if (t != null) {                 // sift down
                    int ck, rk; long cd, rd; ScheduledForkJoinTask<?> c, r;
                    while ((ck = (k << 1) + 1) < n && ck >= 0 &&
                           (c = h[ck]) != null) {
                        cd = c.when;
                        if (c.status < 0 && alsoReplace < 0) {
                            alsoReplace = ck;    // at most one per pass
                            c.heapIndex = -1;
                            cd = Long.MAX_VALUE; // prevent swap below
                        }
                        if ((rk = ck + 1) < n && (r = h[rk]) != null) {
                            rd = r.when;
                            if (r.status < 0 && alsoReplace < 0) {
                                alsoReplace = rk;
                                r.heapIndex = -1;
                            }
                            else if (rd < cd) {  // use right child
                                cd = rd; c = r; ck = rk;
                            }
                        }
                        if (d <= cd)
                            break;
                        c.heapIndex = k;
                        h[k] = c;
                        k = ck;
                    }
                    t.heapIndex = k;
                }
                h[k] = t;
                k = alsoReplace;
            }
        }
        return n;
    }

    /**
     * Call only when pool is shutdown. If called when not stopping,
     * removes tasks according to policy if not already done so, and
     * if not empty or pool not terminating, returns.  Otherwise,
     * cancels all tasks in heap and pending queue.
     * @return negative if stop, else current heap size.
     */
    private int tryStop(ForkJoinPool p, ScheduledForkJoinTask<?>[] h, int n,
                        int runStatus, int prevRunStatus) {
        if (runStatus > 0) {
            if (n > 0) {
                if (runStatus > 1)
                    n = cancelAll(h, n);
                else if (runStatus != prevRunStatus && h != null && h.length >= n) {
                    ScheduledForkJoinTask<?> t; int stat; // remove periodic tasks
                    for (int i = n - 1; i >= 0; --i) {
                        if ((t = h[i]) != null &&
                            ((stat = t.status) < 0 || t.nextDelay != 0L)) {
                            t.heapIndex = -1;
                            if (stat >= 0)
                                t.trySetCancelled();
                            n = replace(h, i, n);
                        }
                    }
                }
            }
            if (n > 0 || p == null || !p.tryStopIfEnabled())
                return n;
        }
        if (n > 0)
            cancelAll(h, n);
        for (ScheduledForkJoinTask<?> a = (ScheduledForkJoinTask<?>)
                 U.getAndSetReference(this, PENDING, null);
             a != null; a = a.nextPending)
            a.trySetCancelled(); // clear pending requests
        return -1;
    }

    private int cancelAll(ScheduledForkJoinTask<?>[] h, int n) {
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

    /**
     * Task class for DelayScheduler operations
     */
    @SuppressWarnings("serial")
    static final class ScheduledForkJoinTask<T>
        extends ForkJoinTask.InterruptibleTask<T>
        implements ScheduledFuture<T> {
        final Runnable runnable;   // only one of runnable or callable nonnull
        final Callable<? extends T> callable;
        final ForkJoinPool pool;
        T result;
        ScheduledForkJoinTask<?> nextPending; // for DelayScheduler submissions
        long when;                  // nanoTime-based trigger time
        final long nextDelay;       // 0: once; <0: fixedDelay; >0: fixedRate
        int heapIndex;              // if non-negative, index on heap
        final boolean isImmediate;  // run by scheduler vs submitted when ready

        public ScheduledForkJoinTask(long when, long nextDelay, boolean isImmediate,
                                     Runnable runnable, Callable<T> callable,
                                     ForkJoinPool pool) {
            heapIndex = -1;
            this.when = when;
            this.isImmediate = isImmediate;
            this.nextDelay = nextDelay;
            this.runnable = runnable;
            this.callable = callable;
            this.pool = pool;
        }

        public void schedule() { // relay to pool, to allow independent use
            pool.scheduleDelayedTask(this);
        }

        @Override
        public final T getRawResult() { return result; }
        @Override
        public final void setRawResult(T v) { result = v; }
        @Override
        final Object adaptee() { return (runnable != null) ? runnable : callable; }

        @Override
        final T compute() throws Exception {
            Callable<? extends T> c; Runnable r;
            T res = null;
            if ((r = runnable) != null)
                r.run();
            else if ((c = callable) != null)
                res = c.call();
            return res;
        }

        @Override
        final boolean postExec() { // resubmit if periodic
            long d; ForkJoinPool p; DelayScheduler ds;
            if ((d = nextDelay) != 0L && status >= 0 &&
                (p = pool) != null && (ds = p.delayScheduler) != null) {
                if (p.delaySchedulerRunStatus() == 0) {
                    heapIndex = -1;
                    if (d < 0L)
                        when = DelayScheduler.now() - d;
                    else
                        when += d;
                    ds.pend(this);
                    return false;
                }
                trySetCancelled();
            }
            return true;
        }

        @Override
        public final boolean cancel(boolean mayInterruptIfRunning) {
            int s; boolean isCancelled; Thread t;
            ForkJoinPool p; DelayScheduler ds;
            if ((s = trySetCancelled()) < 0)
                isCancelled = ((s & (ABNORMAL | THROWN)) == ABNORMAL);
            else {
                isCancelled = true;
                if ((t = runner) != null) {
                    if (mayInterruptIfRunning) {
                        try {
                            t.interrupt();
                        } catch (Throwable ignore) {
                        }
                    }
                }
                else if (heapIndex >= 0 && nextPending == null &&
                         (p = pool) != null && (ds = p.delayScheduler) != null)
                    ds.pend(this);         // for heap cleanup
            }
            return isCancelled;
        }

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

