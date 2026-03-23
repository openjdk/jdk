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
final class DelayScheduler extends Thread {

    /*
     * overview omitted for now
     */

    ForkJoinPool pool;
    ScheduledForkJoinTask<?>[] heapArray;
    long nextWakeup;
    int prevRunStatus;
    int heapSize;
    volatile int cancelDelayedTasksOnShutdown; // policy control

    @jdk.internal.vm.annotation.Contended("p")
    volatile ScheduledForkJoinTask<?> pending; // submitted adds and removes

    @jdk.internal.vm.annotation.Contended("s")
    volatile int sstate;

    private static final int INITIAL_HEAP_CAPACITY = 1 << 6;
    private static final int POOL_STOPPING = 1; // must match ForkJoinPool
    static final long nanoTimeOffset = // Most negative possible time base
        Math.min(System.nanoTime(), 0L) + Long.MIN_VALUE;

    private static final int BUSY       = 1;
    private static final int ACTIVATING = 2;
    private static final int STOPPED    = 1 << 31;

    private static final Unsafe U;     // for atomic operations
    private static final long SSTATE;
    private static final long PENDING;
    static {
        U = Unsafe.getUnsafe();
        Class<DelayScheduler> klass = DelayScheduler.class;
        SSTATE = U.objectFieldOffset(klass, "sstate");
        PENDING = U.objectFieldOffset(klass, "pending");
    }

    DelayScheduler(ForkJoinPool p, String name) {
        super(name);
        setDaemon(true);
	heapArray = new ScheduledForkJoinTask<?>[INITIAL_HEAP_CAPACITY];
        nextWakeup = Long.MAX_VALUE;
        pool = p;
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
        int ss;
        if ((ss = sstate) >= 0)
            U.unpark(this);
        return ss;
    }

    private void onStop() {
        ForkJoinPool p;
        heapSize = 0;
        U.getAndBitwiseOrInt(this, SSTATE, STOPPED);
        if ((p = pool) != null)
            p.tryStopIfShutdown(this);
    }

    /**
     * Inserts the task (if nonnull) to pending queue, to add,
     * remove, or ignore depending on task status when processed.
     */
    final void pend(ScheduledForkJoinTask<?> task) {
        ScheduledForkJoinTask<?> f = pending; int ss;
        if (task != null) {
            do {} while (
                f != (f = (ScheduledForkJoinTask<?>)
                      U.compareAndExchangeReference(
                          this, PENDING, task.nextPending = f, task)));
	    if (((ss = sstate) & (BUSY | STOPPED)) == 0)
                helpProcess(ss);
        }
    }

    private void helpProcess(int ss) {
        if (U.compareAndSetInt(this, SSTATE, ss, ss | BUSY)) {
            long triggerTime = Long.MIN_VALUE, wakeupTime = Long.MIN_VALUE;
            try {
                triggerTime = process();
                wakeupTime = nextWakeup;
            } finally {
                ss = U.getAndBitwiseAndInt(this, SSTATE, ~BUSY);
                if (ss < 0 || triggerTime == Long.MIN_VALUE)
                    onStop();
                else if ((ss & ACTIVATING) != 0 || triggerTime < wakeupTime)
                    U.unpark(this);
            }
        }
    }

    /**
     * Returns true if (momentarily) inactive and heap is empty
     */
    final boolean canShutDown() {
        int ss = sstate; // todo: avoid false alarms
        return (ss < 0 || (pending == null && heapSize <= 0));
    }

    /**
     * Returns the number of elements in heap when last idle
     */
    final int lastStableSize() {
        return heapSize;
    }

    /**
     * Turns on cancelDelayedTasksOnShutdown policy
     */
    final void  cancelDelayedTasksOnShutdown() {
        cancelDelayedTasksOnShutdown = 1;
        signal();
    }

    /**
     * Sets up and runs scheduling loop
     */
    public final void run() {
        outer: for (long triggerTime = Long.MIN_VALUE;;) {
            Thread.interrupted();                 // clear status
            long d = (triggerTime == Long.MAX_VALUE) ? 0L : triggerTime - now();
            triggerTime = Long.MIN_VALUE;
            if (d >= 0L && pending == null)       // await timer or signal
                U.park(false, d);
            for (int ss;;) {                      // acquire lock
                if ((ss = sstate) < 0)
                    break outer;
                else if (((ss = sstate) & BUSY) == 0) {
                    if (U.compareAndSetInt(this, SSTATE, ss, BUSY))
                        break;
                }
                else if ((ss & ACTIVATING) == 0)   // request signal
                    U.getAndBitwiseOrInt(this, SSTATE, ACTIVATING);
                else
                    U.park(false, 0L);
            }
            try {
                triggerTime = nextWakeup = process();
            } finally {
                if (U.getAndBitwiseAndInt(this, SSTATE, ~BUSY) < 0 ||
                    triggerTime == Long.MIN_VALUE)
                    break;
            }
        }
        onStop();
    }

    /**
     * 1. Process pending tasks in batches, to add or remove from heap
     * 2. Check for shutdown, either exiting or preparing for shutdown when empty
     * 3. Trigger all enabled tasks by submitting them to pool or run if immediate
     * return time of next scheduler wakeup
     */
    final long process() {
        ForkJoinPool p = pool;
        ScheduledForkJoinTask<?>[] h = heapArray;
        int n = heapSize, cap;
        if (p == null || h == null || n < 0 || (cap = h.length) <= 0)
            return Long.MIN_VALUE;         // possible after stopping
        ScheduledForkJoinTask<?> q, t; int runStatus;
        while (pending != null &&          // process pending tasks
               (t = (ScheduledForkJoinTask<?>)
                U.getAndSetReference(this, PENDING, null)) != null) {
            ScheduledForkJoinTask<?> next;
            do {
                int i;
                if ((next = t.nextPending) != null)
                    t.nextPending = null;
                if ((i = t.heapIndex) >= 0) {
                    t.heapIndex = -1;      // remove cancelled task
                    if (i < cap && h[i] == t)
                        n = replace(h, i, n);
                }
                else if (n >= cap || n < 0)
                    t.trySetCancelled();   // couldn't resize
                else {
                    long d = t.when;       // add and sift up
                    if (t.status >= 0) {
                        ScheduledForkJoinTask<?> parent;
                        int k = n++, pk, newCap;
                        while (k > 0 &&
                               (parent = h[pk = (k - 1) >>> 2]) != null &&
                               (parent.when > d)) {
                            parent.heapIndex = k;
                            h[k] = parent;
                            k = pk;
                        }
                        t.heapIndex = k;
                        h[k] = t;
                        if (n >= cap && (newCap = cap << 1) > cap) {
                            ScheduledForkJoinTask<?>[] a = null;
                            try {          // try to resize
                                a = Arrays.copyOf(h, newCap);
                            } catch (Error | RuntimeException ex) {
                            }
                            if (a != null && a.length == newCap) {
                                cap = newCap; // else keep using old array
                                h = heapArray = a;
                            }
                        }
                    }
                }
            } while ((t = next) != null);
        }

        if ((runStatus = p.shutdownStatus(this)) != 0) {
            if ((n = tryStop(p, h, n, runStatus, prevRunStatus)) < 0)
                return Long.MIN_VALUE;
            prevRunStatus = runStatus;
        }

        long triggerTime = Long.MAX_VALUE;
        if (n > 0 && h.length > 0) {    // submit enabled tasks
            long now = now();
            do {
                ScheduledForkJoinTask<?> f; int stat;
                if ((f = h[0]) != null) {
                    long d = (triggerTime = f.when) - now;
                    if ((stat = f.status) >= 0 && d > 0L)
                        break;
                    f.heapIndex = -1;
                    if (stat < 0)
                        ;               // already cancelled
                    else if (f.isImmediate)
                        f.doExec();
                    else {
                        try {
                            p.executeEnabledScheduledTask(f);
                        }
                        catch (Error | RuntimeException ex) {
                            f.trySetCancelled();
                        }
                    }
                }
                triggerTime = Long.MAX_VALUE;
            } while ((n = heapSize = replace(h, 0, n)) > 0);
        }

        heapSize = n;
        return triggerTime;
    }

    /**
     * Replaces removed heap element at index k, along with other
     * cancelled nodes found while doing so.
     * @return current heap size
     */
    private static int replace(ScheduledForkJoinTask<?>[] h, int k, int n) {
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

    /**
     * Call only when pool run status is nonzero. Possibly cancels
     * tasks and stops during pool shutdown and termination. If called
     * when shutdown but not stopping, removes tasks according to
     * policy if not already done so, and if not empty or pool not
     * terminating, returns.  Otherwise, cancels all tasks in heap and
     * pending queue.
     * @return negative if stop, else current heap size.
     */
    private int tryStop(ForkJoinPool p, ScheduledForkJoinTask<?>[] h, int n,
                        int runStatus, int prevRunStatus) {
        if ((runStatus & POOL_STOPPING) == 0) {
            if (n > 0) {
                if (cancelDelayedTasksOnShutdown != 0) {
                    cancelAll(h, n);
                    n = 0;
                }
                else if (prevRunStatus == 0 && h != null && h.length >= n) {
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
            if (n > 0 || p == null || !p.tryStopIfShutdown(this))
                return heapSize = n;       // check for quiescent shutdown
        }
        heapSize = 0;
        if (n > 0)
            cancelAll(h, n);
        for (ScheduledForkJoinTask<?> a = (ScheduledForkJoinTask<?>)
                 U.getAndSetReference(this, PENDING, null);
             a != null; a = a.nextPending)
            a.trySetCancelled(); // clear pending requests
        return -1;
    }

    private static void cancelAll(ScheduledForkJoinTask<?>[] h, int n) {
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
    }

    /**
     * Invariant checks
     */
    private static boolean checkHeap(ScheduledForkJoinTask<?>[] h, int n) {
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

