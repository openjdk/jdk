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
import static java.util.concurrent.ForkJoinTask.InterruptibleTask;

/**
 * An add-on for ForkJoinPools that provides scheduling for
 * delayed and periodic tasks
 */
final class DelayScheduler extends Thread {

    /*
     * The DelayScheduler maintains a binary heap based on trigger times
     * (field DelayedTask.when) along with a pending queue of tasks
     * submitted by other threads. When ready, tasks are relayed
     * to the pool.
     *
     * To reduce memory contention, the heap is maintained solely via
     * local variables in method loop() (forcing noticeable code
     * sprawl), recording only the heap array to allow method
     * canShutDown to conservatively check emptiness.
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
     * unparking if necessary. The scheduler thread sets status to inactive
     * when apparently no work, and then rechecks before actually
     * parking.  The active field takes on a negative value on
     * termination, as a sentinel used in pool tryTerminate checks as
     * well as to suppress reactivation while terminating.
     *
     * The implementation is designed to accommodate usages in which
     * many or even most tasks are cancelled before executing (mainly
     * IO-based timeouts).  Cancellations are added to the pending
     * queue in method DelayedTask.cancel(), to remove them from the
     * heap. (This requires some safeguards to deal with tasks
     * cancelled while they are still pending.)  In addition,
     * cancelled tasks set their "when" fields to Long.MAX_VALUE,
     * which causes them to be pushed toward the bottom of the heap
     * where they can be simply swept out in the course of other add
     * and replace operations, even before processing the removal
     * request (which is then a no-op).
     *
     * To ensure that comparisons do not encounter integer wrap
     * errors, times are offset with the most negative possible value
     * (nanoTimeOffset) determined during static initialization, and
     * negative delays are screened out in public submission methods
     *
     * For the sake of compatibility with ScheduledThreadPoolExecutor,
     * shutdown follows the same rules, which add some further
     * complexity beyond the cleanup associated with shutdownNow
     * (runState STOP).  Upon noticing pool shutdown, all periodic
     * tasks are purged; the scheduler then triggers pool.tryTerminate
     * when the heap is empty. The asynchronicity of these steps with
     * respect to pool runState weakens guarantees about exactly when
     * remaining tasks report isCancelled to callers (they do not run,
     * but there may be a lag setting their status).
     */

    private static final int INITIAL_HEAP_CAPACITY = 1 << 6;
    private final ForkJoinPool pool; // read only once
    private DelayedTask<?>[] heap;   // written only when (re)allocated
    private volatile int active;     // 0: inactive, -1: stopped, +1: running
    @jdk.internal.vm.annotation.Contended()
    private volatile DelayedTask<?> pending;

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
        nanoTimeOffset = Long.MIN_VALUE + (ns < 0L ? ns : 0L);
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
     * Ensure active, unparking if necessary, unless stopped
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
    final void pend(DelayedTask<?> task) {
        DelayedTask<?> f = pending;
        if (task != null) {
            do {} while (
                f != (f = (DelayedTask<?>)
                      U.compareAndExchangeReference(
                          this, PENDING, task.nextPending = f, task)));
            ensureActive();
        }
    }

    /**
     * Returns true if (momentarily) inactive and heap is empty
     */
    final boolean canShutDown() {
        DelayedTask<?>[] h;
        return (active <= 0 &&
                ((h = heap) == null || h.length <= 0 || h[0] == null) &&
                active <= 0);
    }

    /**
     * Setup and run scheduling loop
     */
    public final void run() {
        ForkJoinPool p;
        ThreadLocalRandom.localInit();
        if ((p = pool) != null) {
            try {
                loop(p);
            } finally {
                active = -1;
                ForkJoinPool.canTerminate(p);
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
        DelayedTask<?>[] h = new DelayedTask<?>[INITIAL_HEAP_CAPACITY];
        heap = h;
        active = 1;
        boolean purgedPeriodic = false;
        for (int n = 0;;) {                    // n is heap size
            DelayedTask<?> t;
            while (pending != null &&          // process pending tasks
                   (t = (DelayedTask<?>)
                    U.getAndSetReference(this, PENDING, null)) != null) {
                DelayedTask<?> next;
                int cap = h.length;
                do {
                    int i = t.heapIndex;
                    long d = t.when;
                    if ((next = t.nextPending) != null)
                        t.nextPending = null;
                    if (i >= 0) {
                        t.heapIndex = -1;
                        if (i < n && i < cap && h[i] == t)
                            n = replace(h, i, n);
                    }
                    else if (t.status >= 0) {
                        DelayedTask<?> parent, u; int pk; DelayedTask<?>[] nh;
                        if (n >= cap || n < 0) // couldn't resize
                            t.trySetCancelled();
                        else {
                            while (n > 0 &&   // clear trailing cancellations
                                   (u = h[n - 1]) != null && u.status < 0) {
                                u.heapIndex = -1;
                                h[--n] = null;
                            }
                            int k = n++;
                            while (k > 0 &&   // sift up
                                   (parent = h[pk = (k - 1) >>> 1]) != null &&
                                   (parent.when > d)) {
                                parent.heapIndex = k;
                                h[k] = parent;
                                k = pk;
                            }
                            t.heapIndex = k;
                            h[k] = t;
                            if (n >= cap && (nh = growHeap(h)) != null)
                                cap = (h = nh).length;
                        }
                    }
                } while ((t = next) != null);
            }

            if (ForkJoinPool.poolIsShutdown(p)) {
                if ((n = tryStop(p, h, n, purgedPeriodic)) < 0)
                    break;
                purgedPeriodic = true;
            }

            long parkTime = 0L;             // zero for untimed park
            if (n > 0 && h.length > 0) {
                long now = now();
                do {                        // submit ready tasks
                    DelayedTask<?> f; int stat;
                    if ((f = h[0]) != null) {
                        long d = f.when - now;
                        if ((stat = f.status) >= 0 && d > 0L) {
                            parkTime = d;
                            break;
                        }
                        f.heapIndex = -1;
                        if (stat >= 0 && p != null)
                            p.executeReadyDelayedTask(f);
                    }
                } while ((n = replace(h, 0, n)) > 0);
            }

            if (pending == null) {
                Thread.interrupted();       // clear before park
                if (active == 0)
                    U.park(false, parkTime);
                else
                    U.compareAndSetInt(this, ACTIVE, 1, 0);
            }
        }
    }

    /**
     * Tries to reallocate the heap array, returning existing
     * array on failure.
     */
    private DelayedTask<?>[] growHeap(DelayedTask<?>[] h) {
        int cap, newCap;
        if (h != null && (cap = h.length) < (newCap = cap << 1)) {
            DelayedTask<?>[] a = null;
            try {
                a = Arrays.copyOf(h, newCap);
            } catch (Error | RuntimeException ex) {
            }
            if (a != null && a.length > cap) {
                heap = h = a;
                U.storeFence();
            }
        }
        return h;
    }

    /**
     * Replaces removed heap element at index k
     * @return current heap size
     */
    private static int replace(DelayedTask<?>[] h, int k, int n) {
        if (k >= 0 && n > 0 && h != null && n <= h.length) {
            DelayedTask<?> t = null, u;
            long d = 0L;
            while (--n > k) { // find uncancelled replacement
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
                int ck, rk; DelayedTask<?> c, r;
                while ((ck = (k << 1) + 1) < n && (c = h[ck]) != null) {
                    long cd = c.when, rd;
                    if ((rk = ck + 1) < n && (r = h[rk]) != null &&
                        (rd = r.when) < cd) {
                        c = r; ck = rk; cd = rd; // use right child
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
        }
        return n;
    }

    /**
     * Call only when pool is shutdown or stopping. If called when
     * shutdown but not stopping, removes periodic tasks if not
     * already done so, and if not empty or pool not terminating,
     * returns.  Otherwise, cancels all tasks in heap and pending
     * queue.
     * @return negative if stop, else current heap size.
     */
    private int tryStop(ForkJoinPool p, DelayedTask<?>[] h,
                        int n, boolean purgedPeriodic) {
        if (p != null && h != null && h.length >= n) {
            if (!ForkJoinPool.poolIsStopping(p)) {
                if (!purgedPeriodic && n > 0) {
                    DelayedTask<?> t; int stat; // remove periodic tasks
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
                if (n > 0 || !ForkJoinPool.canTerminate(p))
                    return n;
            }
            for (int i = 0; i < n; ++i) {
                DelayedTask<?> u = h[i];
                h[i] = null;
                if (u != null)
                    u.trySetCancelled();
            }
            for (DelayedTask<?> a = (DelayedTask<?>)
                     U.getAndSetReference(this, PENDING, null);
                 a != null; a = a.nextPending)
                a.trySetCancelled(); // clear pending requests
        }
        return -1;
    }

    /**
     * Returns an approximate count by finding the highest used
     * heap array slot. This is very racy and not fast, but
     * useful enough for monitoring purposes.
     */
    final int approximateSize() {
        DelayedTask<?>[] h;
        int size = 0;
        if (active >= 0 && (h = heap) != null) {
            for (int i = h.length - 1; i >= 0; --i) {
                if (h[i] != null) {
                    size = i + 1;
                    break;
                }
            }
        }
        return size;
    }

    /**
     * Task class for DelayScheduler operations
     */
    @SuppressWarnings("serial")
    static final class DelayedTask<T> extends InterruptibleTask<T>
        implements ScheduledFuture<T> {
        final Runnable runnable; // only one of runnable or callable nonnull
        final Callable<? extends T> callable;
        final ForkJoinPool pool;
        T result;
        DelayedTask<?> nextPending; // for DelayScheduler submissions
        final long nextDelay;     // 0: once; <0: fixedDelay; >0: fixedRate
        long when;                // nanoTime-based trigger time
        int heapIndex;            // if non-negative, index on heap

        DelayedTask(Runnable runnable, Callable<T> callable, ForkJoinPool pool,
                    long nextDelay, long delay) {
            heapIndex = -1;
            this.runnable = runnable;
            this.callable = callable;
            this.pool = pool;
            this.nextDelay = nextDelay;
            this.when = delay + DelayScheduler.now();
        }
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

        final boolean postExec() { // resubmit if periodic
            long d; ForkJoinPool p; DelayScheduler ds;
            if ((d = nextDelay) != 0L && status >= 0 &&
                (p = pool) != null && (ds = p.delayScheduler) != null) {
                if (!ForkJoinPool.poolIsShutdown(p)) {
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
                         (p = pool) != null && (ds = p.delayScheduler) != null) {
                    when = Long.MAX_VALUE; // set to max delay
                    ds.pend(this);         // for heap cleanup
                }
            }
            return isCancelled;
        }

        public final long getDelay(TimeUnit unit) {
            return unit.convert(when - DelayScheduler.now(), NANOSECONDS);
        }
        public int compareTo(Delayed other) { // never used internally
            long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
            return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
        }
    }

}

