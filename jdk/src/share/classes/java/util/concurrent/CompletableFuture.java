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
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * A {@link Future} that may be explicitly completed (setting its
 * value and status), and may be used as a {@link CompletionStage},
 * supporting dependent functions and actions that trigger upon its
 * completion.
 *
 * <p>When two or more threads attempt to
 * {@link #complete complete},
 * {@link #completeExceptionally completeExceptionally}, or
 * {@link #cancel cancel}
 * a CompletableFuture, only one of them succeeds.
 *
 * <p>In addition to these and related methods for directly
 * manipulating status and results, CompletableFuture implements
 * interface {@link CompletionStage} with the following policies: <ul>
 *
 * <li>Actions supplied for dependent completions of
 * <em>non-async</em> methods may be performed by the thread that
 * completes the current CompletableFuture, or by any other caller of
 * a completion method.</li>
 *
 * <li>All <em>async</em> methods without an explicit Executor
 * argument are performed using the {@link ForkJoinPool#commonPool()}
 * (unless it does not support a parallelism level of at least two, in
 * which case, a new Thread is used). To simplify monitoring,
 * debugging, and tracking, all generated asynchronous tasks are
 * instances of the marker interface {@link
 * AsynchronousCompletionTask}. </li>
 *
 * <li>All CompletionStage methods are implemented independently of
 * other public methods, so the behavior of one method is not impacted
 * by overrides of others in subclasses.  </li> </ul>
 *
 * <p>CompletableFuture also implements {@link Future} with the following
 * policies: <ul>
 *
 * <li>Since (unlike {@link FutureTask}) this class has no direct
 * control over the computation that causes it to be completed,
 * cancellation is treated as just another form of exceptional
 * completion.  Method {@link #cancel cancel} has the same effect as
 * {@code completeExceptionally(new CancellationException())}. Method
 * {@link #isCompletedExceptionally} can be used to determine if a
 * CompletableFuture completed in any exceptional fashion.</li>
 *
 * <li>In case of exceptional completion with a CompletionException,
 * methods {@link #get()} and {@link #get(long, TimeUnit)} throw an
 * {@link ExecutionException} with the same cause as held in the
 * corresponding CompletionException.  To simplify usage in most
 * contexts, this class also defines methods {@link #join()} and
 * {@link #getNow} that instead throw the CompletionException directly
 * in these cases.</li> </ul>
 *
 * @author Doug Lea
 * @since 1.8
 */
public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {

    /*
     * Overview:
     *
     * 1. Non-nullness of field result (set via CAS) indicates done.
     * An AltResult is used to box null as a result, as well as to
     * hold exceptions.  Using a single field makes completion fast
     * and simple to detect and trigger, at the expense of a lot of
     * encoding and decoding that infiltrates many methods. One minor
     * simplification relies on the (static) NIL (to box null results)
     * being the only AltResult with a null exception field, so we
     * don't usually need explicit comparisons with NIL. The CF
     * exception propagation mechanics surrounding decoding rely on
     * unchecked casts of decoded results really being unchecked,
     * where user type errors are caught at point of use, as is
     * currently the case in Java. These are highlighted by using
     * SuppressWarnings-annotated temporaries.
     *
     * 2. Waiters are held in a Treiber stack similar to the one used
     * in FutureTask, Phaser, and SynchronousQueue. See their
     * internal documentation for algorithmic details.
     *
     * 3. Completions are also kept in a list/stack, and pulled off
     * and run when completion is triggered. (We could even use the
     * same stack as for waiters, but would give up the potential
     * parallelism obtained because woken waiters help release/run
     * others -- see method postComplete).  Because post-processing
     * may race with direct calls, class Completion opportunistically
     * extends AtomicInteger so callers can claim the action via
     * compareAndSet(0, 1).  The Completion.run methods are all
     * written a boringly similar uniform way (that sometimes includes
     * unnecessary-looking checks, kept to maintain uniformity).
     * There are enough dimensions upon which they differ that
     * attempts to factor commonalities while maintaining efficiency
     * require more lines of code than they would save.
     *
     * 4. The exported then/and/or methods do support a bit of
     * factoring (see doThenApply etc). They must cope with the
     * intrinsic races surrounding addition of a dependent action
     * versus performing the action directly because the task is
     * already complete.  For example, a CF may not be complete upon
     * entry, so a dependent completion is added, but by the time it
     * is added, the target CF is complete, so must be directly
     * executed. This is all done while avoiding unnecessary object
     * construction in safe-bypass cases.
     */

    // preliminaries

    static final class AltResult {
        final Throwable ex; // null only for NIL
        AltResult(Throwable ex) { this.ex = ex; }
    }

    static final AltResult NIL = new AltResult(null);

    // Fields

    volatile Object result;    // Either the result or boxed AltResult
    volatile WaitNode waiters; // Treiber stack of threads blocked on get()
    volatile CompletionNode completions; // list (Treiber stack) of completions

    // Basic utilities for triggering and processing completions

    /**
     * Removes and signals all waiting threads and runs all completions.
     */
    final void postComplete() {
        WaitNode q; Thread t;
        while ((q = waiters) != null) {
            if (UNSAFE.compareAndSwapObject(this, WAITERS, q, q.next) &&
                (t = q.thread) != null) {
                q.thread = null;
                LockSupport.unpark(t);
            }
        }

        CompletionNode h; Completion c;
        while ((h = completions) != null) {
            if (UNSAFE.compareAndSwapObject(this, COMPLETIONS, h, h.next) &&
                (c = h.completion) != null)
                c.run();
        }
    }

    /**
     * Triggers completion with the encoding of the given arguments:
     * if the exception is non-null, encodes it as a wrapped
     * CompletionException unless it is one already.  Otherwise uses
     * the given result, boxed as NIL if null.
     */
    final void internalComplete(T v, Throwable ex) {
        if (result == null)
            UNSAFE.compareAndSwapObject
                (this, RESULT, null,
                 (ex == null) ? (v == null) ? NIL : v :
                 new AltResult((ex instanceof CompletionException) ? ex :
                               new CompletionException(ex)));
        postComplete(); // help out even if not triggered
    }

    /**
     * If triggered, helps release and/or process completions.
     */
    final void helpPostComplete() {
        if (result != null)
            postComplete();
    }

    /* ------------- waiting for completions -------------- */

    /** Number of processors, for spin control */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Heuristic spin value for waitingGet() before blocking on
     * multiprocessors
     */
    static final int SPINS = (NCPU > 1) ? 1 << 8 : 0;

    /**
     * Linked nodes to record waiting threads in a Treiber stack.  See
     * other classes such as Phaser and SynchronousQueue for more
     * detailed explanation. This class implements ManagedBlocker to
     * avoid starvation when blocking actions pile up in
     * ForkJoinPools.
     */
    static final class WaitNode implements ForkJoinPool.ManagedBlocker {
        long nanos;          // wait time if timed
        final long deadline; // non-zero if timed
        volatile int interruptControl; // > 0: interruptible, < 0: interrupted
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode(boolean interruptible, long nanos, long deadline) {
            this.thread = Thread.currentThread();
            this.interruptControl = interruptible ? 1 : 0;
            this.nanos = nanos;
            this.deadline = deadline;
        }
        public boolean isReleasable() {
            if (thread == null)
                return true;
            if (Thread.interrupted()) {
                int i = interruptControl;
                interruptControl = -1;
                if (i > 0)
                    return true;
            }
            if (deadline != 0L &&
                (nanos <= 0L || (nanos = deadline - System.nanoTime()) <= 0L)) {
                thread = null;
                return true;
            }
            return false;
        }
        public boolean block() {
            if (isReleasable())
                return true;
            else if (deadline == 0L)
                LockSupport.park(this);
            else if (nanos > 0L)
                LockSupport.parkNanos(this, nanos);
            return isReleasable();
        }
    }

    /**
     * Returns raw result after waiting, or null if interruptible and
     * interrupted.
     */
    private Object waitingGet(boolean interruptible) {
        WaitNode q = null;
        boolean queued = false;
        int spins = SPINS;
        for (Object r;;) {
            if ((r = result) != null) {
                if (q != null) { // suppress unpark
                    q.thread = null;
                    if (q.interruptControl < 0) {
                        if (interruptible) {
                            removeWaiter(q);
                            return null;
                        }
                        Thread.currentThread().interrupt();
                    }
                }
                postComplete(); // help release others
                return r;
            }
            else if (spins > 0) {
                int rnd = ThreadLocalRandom.nextSecondarySeed();
                if (rnd == 0)
                    rnd = ThreadLocalRandom.current().nextInt();
                if (rnd >= 0)
                    --spins;
            }
            else if (q == null)
                q = new WaitNode(interruptible, 0L, 0L);
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, WAITERS,
                                                     q.next = waiters, q);
            else if (interruptible && q.interruptControl < 0) {
                removeWaiter(q);
                return null;
            }
            else if (q.thread != null && result == null) {
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ex) {
                    q.interruptControl = -1;
                }
            }
        }
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param nanos time to wait
     * @return raw result
     */
    private Object timedAwaitDone(long nanos)
        throws InterruptedException, TimeoutException {
        WaitNode q = null;
        boolean queued = false;
        for (Object r;;) {
            if ((r = result) != null) {
                if (q != null) {
                    q.thread = null;
                    if (q.interruptControl < 0) {
                        removeWaiter(q);
                        throw new InterruptedException();
                    }
                }
                postComplete();
                return r;
            }
            else if (q == null) {
                if (nanos <= 0L)
                    throw new TimeoutException();
                long d = System.nanoTime() + nanos;
                q = new WaitNode(true, nanos, d == 0L ? 1L : d); // avoid 0
            }
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, WAITERS,
                                                     q.next = waiters, q);
            else if (q.interruptControl < 0) {
                removeWaiter(q);
                throw new InterruptedException();
            }
            else if (q.nanos <= 0L) {
                if (result == null) {
                    removeWaiter(q);
                    throw new TimeoutException();
                }
            }
            else if (q.thread != null && result == null) {
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ex) {
                    q.interruptControl = -1;
                }
            }
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (;;) {          // restart on removeWaiter race
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    }
                    else if (!UNSAFE.compareAndSwapObject(this, WAITERS, q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    /* ------------- Async tasks -------------- */

    /**
     * A marker interface identifying asynchronous tasks produced by
     * {@code async} methods. This may be useful for monitoring,
     * debugging, and tracking asynchronous activities.
     *
     * @since 1.8
     */
    public static interface AsynchronousCompletionTask {
    }

    /** Base class can act as either FJ or plain Runnable */
    abstract static class Async extends ForkJoinTask<Void>
        implements Runnable, AsynchronousCompletionTask {
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        public final void run() { exec(); }
    }

    /**
     * Starts the given async task using the given executor, unless
     * the executor is ForkJoinPool.commonPool and it has been
     * disabled, in which case starts a new thread.
     */
    static void execAsync(Executor e, Async r) {
        if (e == ForkJoinPool.commonPool() &&
            ForkJoinPool.getCommonPoolParallelism() <= 1)
            new Thread(r).start();
        else
            e.execute(r);
    }

    static final class AsyncRun extends Async {
        final Runnable fn;
        final CompletableFuture<Void> dst;
        AsyncRun(Runnable fn, CompletableFuture<Void> dst) {
            this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<Void> d; Throwable ex;
            if ((d = this.dst) != null && d.result == null) {
                try {
                    fn.run();
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                }
                d.internalComplete(null, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncSupply<U> extends Async {
        final Supplier<U> fn;
        final CompletableFuture<U> dst;
        AsyncSupply(Supplier<U> fn, CompletableFuture<U> dst) {
            this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<U> d; U u; Throwable ex;
            if ((d = this.dst) != null && d.result == null) {
                try {
                    u = fn.get();
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                    u = null;
                }
                d.internalComplete(u, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncApply<T,U> extends Async {
        final T arg;
        final Function<? super T,? extends U> fn;
        final CompletableFuture<U> dst;
        AsyncApply(T arg, Function<? super T,? extends U> fn,
                   CompletableFuture<U> dst) {
            this.arg = arg; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<U> d; U u; Throwable ex;
            if ((d = this.dst) != null && d.result == null) {
                try {
                    u = fn.apply(arg);
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                    u = null;
                }
                d.internalComplete(u, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncCombine<T,U,V> extends Async {
        final T arg1;
        final U arg2;
        final BiFunction<? super T,? super U,? extends V> fn;
        final CompletableFuture<V> dst;
        AsyncCombine(T arg1, U arg2,
                     BiFunction<? super T,? super U,? extends V> fn,
                     CompletableFuture<V> dst) {
            this.arg1 = arg1; this.arg2 = arg2; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<V> d; V v; Throwable ex;
            if ((d = this.dst) != null && d.result == null) {
                try {
                    v = fn.apply(arg1, arg2);
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                    v = null;
                }
                d.internalComplete(v, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncAccept<T> extends Async {
        final T arg;
        final Consumer<? super T> fn;
        final CompletableFuture<?> dst;
        AsyncAccept(T arg, Consumer<? super T> fn,
                    CompletableFuture<?> dst) {
            this.arg = arg; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<?> d; Throwable ex;
            if ((d = this.dst) != null && d.result == null) {
                try {
                    fn.accept(arg);
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                }
                d.internalComplete(null, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncAcceptBoth<T,U> extends Async {
        final T arg1;
        final U arg2;
        final BiConsumer<? super T,? super U> fn;
        final CompletableFuture<?> dst;
        AsyncAcceptBoth(T arg1, U arg2,
                        BiConsumer<? super T,? super U> fn,
                        CompletableFuture<?> dst) {
            this.arg1 = arg1; this.arg2 = arg2; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<?> d; Throwable ex;
            if ((d = this.dst) != null && d.result == null) {
                try {
                    fn.accept(arg1, arg2);
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                }
                d.internalComplete(null, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncCompose<T,U> extends Async {
        final T arg;
        final Function<? super T, ? extends CompletionStage<U>> fn;
        final CompletableFuture<U> dst;
        AsyncCompose(T arg,
                     Function<? super T, ? extends CompletionStage<U>> fn,
                     CompletableFuture<U> dst) {
            this.arg = arg; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<U> d, fr; U u; Throwable ex;
            if ((d = this.dst) != null && d.result == null) {
                try {
                    CompletionStage<U> cs = fn.apply(arg);
                    fr = (cs == null) ? null : cs.toCompletableFuture();
                    ex = (fr == null) ? new NullPointerException() : null;
                } catch (Throwable rex) {
                    ex = rex;
                    fr = null;
                }
                if (ex != null)
                    u = null;
                else {
                    Object r = fr.result;
                    if (r == null)
                        r = fr.waitingGet(false);
                    if (r instanceof AltResult) {
                        ex = ((AltResult)r).ex;
                        u = null;
                    }
                    else {
                        @SuppressWarnings("unchecked") U ur = (U) r;
                        u = ur;
                    }
                }
                d.internalComplete(u, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncWhenComplete<T> extends Async {
        final T arg1;
        final Throwable arg2;
        final BiConsumer<? super T,? super Throwable> fn;
        final CompletableFuture<T> dst;
        AsyncWhenComplete(T arg1, Throwable arg2,
                          BiConsumer<? super T,? super Throwable> fn,
                          CompletableFuture<T> dst) {
            this.arg1 = arg1; this.arg2 = arg2; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<T> d;
            if ((d = this.dst) != null && d.result == null) {
                Throwable ex = arg2;
                try {
                    fn.accept(arg1, ex);
                } catch (Throwable rex) {
                    if (ex == null)
                        ex = rex;
                }
                d.internalComplete(arg1, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /* ------------- Completions -------------- */

    /**
     * Simple linked list nodes to record completions, used in
     * basically the same way as WaitNodes. (We separate nodes from
     * the Completions themselves mainly because for the And and Or
     * methods, the same Completion object resides in two lists.)
     */
    static final class CompletionNode {
        final Completion completion;
        volatile CompletionNode next;
        CompletionNode(Completion completion) { this.completion = completion; }
    }

    // Opportunistically subclass AtomicInteger to use compareAndSet to claim.
    abstract static class Completion extends AtomicInteger implements Runnable {
    }

    static final class ThenApply<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final Function<? super T,? extends U> fn;
        final CompletableFuture<U> dst;
        final Executor executor;
        ThenApply(CompletableFuture<? extends T> src,
                  Function<? super T,? extends U> fn,
                  CompletableFuture<U> dst,
                  Executor executor) {
            this.src = src; this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final Function<? super T,? extends U> fn;
            final CompletableFuture<U> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                Executor e = executor;
                U u = null;
                if (ex == null) {
                    try {
                        if (e != null)
                            execAsync(e, new AsyncApply<T,U>(t, fn, dst));
                        else
                            u = fn.apply(t);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(u, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenAccept<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final Consumer<? super T> fn;
        final CompletableFuture<?> dst;
        final Executor executor;
        ThenAccept(CompletableFuture<? extends T> src,
                   Consumer<? super T> fn,
                   CompletableFuture<?> dst,
                   Executor executor) {
            this.src = src; this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final Consumer<? super T> fn;
            final CompletableFuture<?> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            execAsync(e, new AsyncAccept<T>(t, fn, dst));
                        else
                            fn.accept(t);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenRun extends Completion {
        final CompletableFuture<?> src;
        final Runnable fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        ThenRun(CompletableFuture<?> src,
                Runnable fn,
                CompletableFuture<Void> dst,
                Executor executor) {
            this.src = src; this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<?> a;
            final Runnable fn;
            final CompletableFuture<Void> dst;
            Object r; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult)
                    ex = ((AltResult)r).ex;
                else
                    ex = null;
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            execAsync(e, new AsyncRun(fn, dst));
                        else
                            fn.run();
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenCombine<T,U,V> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<? extends U> snd;
        final BiFunction<? super T,? super U,? extends V> fn;
        final CompletableFuture<V> dst;
        final Executor executor;
        ThenCombine(CompletableFuture<? extends T> src,
                    CompletableFuture<? extends U> snd,
                    BiFunction<? super T,? super U,? extends V> fn,
                    CompletableFuture<V> dst,
                    Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final CompletableFuture<? extends U> b;
            final BiFunction<? super T,? super U,? extends V> fn;
            final CompletableFuture<V> dst;
            Object r, s; T t; U u; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                (b = this.snd) != null &&
                (s = b.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                if (ex != null)
                    u = null;
                else if (s instanceof AltResult) {
                    ex = ((AltResult)s).ex;
                    u = null;
                }
                else {
                    @SuppressWarnings("unchecked") U us = (U) s;
                    u = us;
                }
                Executor e = executor;
                V v = null;
                if (ex == null) {
                    try {
                        if (e != null)
                            execAsync(e, new AsyncCombine<T,U,V>(t, u, fn, dst));
                        else
                            v = fn.apply(t, u);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(v, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenAcceptBoth<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<? extends U> snd;
        final BiConsumer<? super T,? super U> fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        ThenAcceptBoth(CompletableFuture<? extends T> src,
                       CompletableFuture<? extends U> snd,
                       BiConsumer<? super T,? super U> fn,
                       CompletableFuture<Void> dst,
                       Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final CompletableFuture<? extends U> b;
            final BiConsumer<? super T,? super U> fn;
            final CompletableFuture<Void> dst;
            Object r, s; T t; U u; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                (b = this.snd) != null &&
                (s = b.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                if (ex != null)
                    u = null;
                else if (s instanceof AltResult) {
                    ex = ((AltResult)s).ex;
                    u = null;
                }
                else {
                    @SuppressWarnings("unchecked") U us = (U) s;
                    u = us;
                }
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            execAsync(e, new AsyncAcceptBoth<T,U>(t, u, fn, dst));
                        else
                            fn.accept(t, u);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class RunAfterBoth extends Completion {
        final CompletableFuture<?> src;
        final CompletableFuture<?> snd;
        final Runnable fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        RunAfterBoth(CompletableFuture<?> src,
                     CompletableFuture<?> snd,
                     Runnable fn,
                     CompletableFuture<Void> dst,
                     Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<?> a;
            final CompletableFuture<?> b;
            final Runnable fn;
            final CompletableFuture<Void> dst;
            Object r, s; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                (b = this.snd) != null &&
                (s = b.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult)
                    ex = ((AltResult)r).ex;
                else
                    ex = null;
                if (ex == null && (s instanceof AltResult))
                    ex = ((AltResult)s).ex;
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            execAsync(e, new AsyncRun(fn, dst));
                        else
                            fn.run();
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AndCompletion extends Completion {
        final CompletableFuture<?> src;
        final CompletableFuture<?> snd;
        final CompletableFuture<Void> dst;
        AndCompletion(CompletableFuture<?> src,
                      CompletableFuture<?> snd,
                      CompletableFuture<Void> dst) {
            this.src = src; this.snd = snd; this.dst = dst;
        }
        public final void run() {
            final CompletableFuture<?> a;
            final CompletableFuture<?> b;
            final CompletableFuture<Void> dst;
            Object r, s; Throwable ex;
            if ((dst = this.dst) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                (b = this.snd) != null &&
                (s = b.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult)
                    ex = ((AltResult)r).ex;
                else
                    ex = null;
                if (ex == null && (s instanceof AltResult))
                    ex = ((AltResult)s).ex;
                dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ApplyToEither<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<? extends T> snd;
        final Function<? super T,? extends U> fn;
        final CompletableFuture<U> dst;
        final Executor executor;
        ApplyToEither(CompletableFuture<? extends T> src,
                      CompletableFuture<? extends T> snd,
                      Function<? super T,? extends U> fn,
                      CompletableFuture<U> dst,
                      Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final CompletableFuture<? extends T> b;
            final Function<? super T,? extends U> fn;
            final CompletableFuture<U> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (((a = this.src) != null && (r = a.result) != null) ||
                 ((b = this.snd) != null && (r = b.result) != null)) &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                Executor e = executor;
                U u = null;
                if (ex == null) {
                    try {
                        if (e != null)
                            execAsync(e, new AsyncApply<T,U>(t, fn, dst));
                        else
                            u = fn.apply(t);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(u, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AcceptEither<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<? extends T> snd;
        final Consumer<? super T> fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        AcceptEither(CompletableFuture<? extends T> src,
                     CompletableFuture<? extends T> snd,
                     Consumer<? super T> fn,
                     CompletableFuture<Void> dst,
                     Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final CompletableFuture<? extends T> b;
            final Consumer<? super T> fn;
            final CompletableFuture<Void> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (((a = this.src) != null && (r = a.result) != null) ||
                 ((b = this.snd) != null && (r = b.result) != null)) &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            execAsync(e, new AsyncAccept<T>(t, fn, dst));
                        else
                            fn.accept(t);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class RunAfterEither extends Completion {
        final CompletableFuture<?> src;
        final CompletableFuture<?> snd;
        final Runnable fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        RunAfterEither(CompletableFuture<?> src,
                       CompletableFuture<?> snd,
                       Runnable fn,
                       CompletableFuture<Void> dst,
                       Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<?> a;
            final CompletableFuture<?> b;
            final Runnable fn;
            final CompletableFuture<Void> dst;
            Object r; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (((a = this.src) != null && (r = a.result) != null) ||
                 ((b = this.snd) != null && (r = b.result) != null)) &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult)
                    ex = ((AltResult)r).ex;
                else
                    ex = null;
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            execAsync(e, new AsyncRun(fn, dst));
                        else
                            fn.run();
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class OrCompletion extends Completion {
        final CompletableFuture<?> src;
        final CompletableFuture<?> snd;
        final CompletableFuture<Object> dst;
        OrCompletion(CompletableFuture<?> src,
                     CompletableFuture<?> snd,
                     CompletableFuture<Object> dst) {
            this.src = src; this.snd = snd; this.dst = dst;
        }
        public final void run() {
            final CompletableFuture<?> a;
            final CompletableFuture<?> b;
            final CompletableFuture<Object> dst;
            Object r, t; Throwable ex;
            if ((dst = this.dst) != null &&
                (((a = this.src) != null && (r = a.result) != null) ||
                 ((b = this.snd) != null && (r = b.result) != null)) &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = r;
                }
                dst.internalComplete(t, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ExceptionCompletion<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final Function<? super Throwable, ? extends T> fn;
        final CompletableFuture<T> dst;
        ExceptionCompletion(CompletableFuture<? extends T> src,
                            Function<? super Throwable, ? extends T> fn,
                            CompletableFuture<T> dst) {
            this.src = src; this.fn = fn; this.dst = dst;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final Function<? super Throwable, ? extends T> fn;
            final CompletableFuture<T> dst;
            Object r; T t = null; Throwable ex, dx = null;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if ((r instanceof AltResult) &&
                    (ex = ((AltResult)r).ex) != null) {
                    try {
                        t = fn.apply(ex);
                    } catch (Throwable rex) {
                        dx = rex;
                    }
                }
                else {
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                dst.internalComplete(t, dx);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class WhenCompleteCompletion<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final BiConsumer<? super T, ? super Throwable> fn;
        final CompletableFuture<T> dst;
        final Executor executor;
        WhenCompleteCompletion(CompletableFuture<? extends T> src,
                                  BiConsumer<? super T, ? super Throwable> fn,
                                  CompletableFuture<T> dst,
                                  Executor executor) {
            this.src = src; this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final BiConsumer<? super T, ? super Throwable> fn;
            final CompletableFuture<T> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                Executor e = executor;
                Throwable dx = null;
                try {
                    if (e != null)
                        execAsync(e, new AsyncWhenComplete<T>(t, ex, fn, dst));
                    else
                        fn.accept(t, ex);
                } catch (Throwable rex) {
                    dx = rex;
                }
                if (e == null || dx != null)
                    dst.internalComplete(t, ex != null ? ex : dx);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenCopy<T> extends Completion {
        final CompletableFuture<?> src;
        final CompletableFuture<T> dst;
        ThenCopy(CompletableFuture<?> src,
                 CompletableFuture<T> dst) {
            this.src = src; this.dst = dst;
        }
        public final void run() {
            final CompletableFuture<?> a;
            final CompletableFuture<T> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                dst.internalComplete(t, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    // version of ThenCopy for CompletableFuture<Void> dst
    static final class ThenPropagate extends Completion {
        final CompletableFuture<?> src;
        final CompletableFuture<Void> dst;
        ThenPropagate(CompletableFuture<?> src,
                      CompletableFuture<Void> dst) {
            this.src = src; this.dst = dst;
        }
        public final void run() {
            final CompletableFuture<?> a;
            final CompletableFuture<Void> dst;
            Object r; Throwable ex;
            if ((dst = this.dst) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult)
                    ex = ((AltResult)r).ex;
                else
                    ex = null;
                dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class HandleCompletion<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final BiFunction<? super T, Throwable, ? extends U> fn;
        final CompletableFuture<U> dst;
        final Executor executor;
        HandleCompletion(CompletableFuture<? extends T> src,
                         BiFunction<? super T, Throwable, ? extends U> fn,
                         CompletableFuture<U> dst,
                          Executor executor) {
            this.src = src; this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final BiFunction<? super T, Throwable, ? extends U> fn;
            final CompletableFuture<U> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                Executor e = executor;
                U u = null;
                Throwable dx = null;
                try {
                    if (e != null)
                        execAsync(e, new AsyncCombine<T,Throwable,U>(t, ex, fn, dst));
                    else
                        u = fn.apply(t, ex);
                } catch (Throwable rex) {
                    dx = rex;
                }
                if (e == null || dx != null)
                    dst.internalComplete(u, dx);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenCompose<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final Function<? super T, ? extends CompletionStage<U>> fn;
        final CompletableFuture<U> dst;
        final Executor executor;
        ThenCompose(CompletableFuture<? extends T> src,
                    Function<? super T, ? extends CompletionStage<U>> fn,
                    CompletableFuture<U> dst,
                    Executor executor) {
            this.src = src; this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public final void run() {
            final CompletableFuture<? extends T> a;
            final Function<? super T, ? extends CompletionStage<U>> fn;
            final CompletableFuture<U> dst;
            Object r; T t; Throwable ex; Executor e;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                CompletableFuture<U> c = null;
                U u = null;
                boolean complete = false;
                if (ex == null) {
                    if ((e = executor) != null)
                        execAsync(e, new AsyncCompose<T,U>(t, fn, dst));
                    else {
                        try {
                            CompletionStage<U> cs = fn.apply(t);
                            c = (cs == null) ? null : cs.toCompletableFuture();
                            if (c == null)
                                ex = new NullPointerException();
                        } catch (Throwable rex) {
                            ex = rex;
                        }
                    }
                }
                if (c != null) {
                    ThenCopy<U> d = null;
                    Object s;
                    if ((s = c.result) == null) {
                        CompletionNode p = new CompletionNode
                            (d = new ThenCopy<U>(c, dst));
                        while ((s = c.result) == null) {
                            if (UNSAFE.compareAndSwapObject
                                (c, COMPLETIONS, p.next = c.completions, p))
                                break;
                        }
                    }
                    if (s != null && (d == null || d.compareAndSet(0, 1))) {
                        complete = true;
                        if (s instanceof AltResult) {
                            ex = ((AltResult)s).ex;  // no rewrap
                            u = null;
                        }
                        else {
                            @SuppressWarnings("unchecked") U us = (U) s;
                            u = us;
                        }
                    }
                }
                if (complete || ex != null)
                    dst.internalComplete(u, ex);
                if (c != null)
                    c.helpPostComplete();
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    // Implementations of stage methods with (plain, async, Executor) forms

    private <U> CompletableFuture<U> doThenApply
        (Function<? super T,? extends U> fn,
         Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = new CompletableFuture<U>();
        ThenApply<T,U> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p = new CompletionNode
                (d = new ThenApply<T,U>(this, fn, dst, e));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            U u = null;
            if (ex == null) {
                try {
                    if (e != null)
                        execAsync(e, new AsyncApply<T,U>(t, fn, dst));
                    else
                        u = fn.apply(t);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(u, ex);
        }
        helpPostComplete();
        return dst;
    }

    private CompletableFuture<Void> doThenAccept(Consumer<? super T> fn,
                                                 Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        ThenAccept<T> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p = new CompletionNode
                (d = new ThenAccept<T>(this, fn, dst, e));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            if (ex == null) {
                try {
                    if (e != null)
                        execAsync(e, new AsyncAccept<T>(t, fn, dst));
                    else
                        fn.accept(t);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        return dst;
    }

    private CompletableFuture<Void> doThenRun(Runnable action,
                                              Executor e) {
        if (action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        ThenRun d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p = new CompletionNode
                (d = new ThenRun(this, action, dst, e));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            Throwable ex;
            if (r instanceof AltResult)
                ex = ((AltResult)r).ex;
            else
                ex = null;
            if (ex == null) {
                try {
                    if (e != null)
                        execAsync(e, new AsyncRun(action, dst));
                    else
                        action.run();
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        return dst;
    }

    private <U,V> CompletableFuture<V> doThenCombine
        (CompletableFuture<? extends U> other,
         BiFunction<? super T,? super U,? extends V> fn,
         Executor e) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<V> dst = new CompletableFuture<V>();
        ThenCombine<T,U,V> d = null;
        Object r, s = null;
        if ((r = result) == null || (s = other.result) == null) {
            d = new ThenCombine<T,U,V>(this, other, fn, dst, e);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r == null && (r = result) == null) ||
                   (s == null && (s = other.result) == null)) {
                if (q != null) {
                    if (s != null ||
                        UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (r != null ||
                         UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p)) {
                    if (s != null)
                        break;
                    q = new CompletionNode(d);
                }
            }
        }
        if (r != null && s != null && (d == null || d.compareAndSet(0, 1))) {
            T t; U u; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            if (ex != null)
                u = null;
            else if (s instanceof AltResult) {
                ex = ((AltResult)s).ex;
                u = null;
            }
            else {
                @SuppressWarnings("unchecked") U us = (U) s;
                u = us;
            }
            V v = null;
            if (ex == null) {
                try {
                    if (e != null)
                        execAsync(e, new AsyncCombine<T,U,V>(t, u, fn, dst));
                    else
                        v = fn.apply(t, u);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(v, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    private <U> CompletableFuture<Void> doThenAcceptBoth
        (CompletableFuture<? extends U> other,
         BiConsumer<? super T,? super U> fn,
         Executor e) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        ThenAcceptBoth<T,U> d = null;
        Object r, s = null;
        if ((r = result) == null || (s = other.result) == null) {
            d = new ThenAcceptBoth<T,U>(this, other, fn, dst, e);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r == null && (r = result) == null) ||
                   (s == null && (s = other.result) == null)) {
                if (q != null) {
                    if (s != null ||
                        UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (r != null ||
                         UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p)) {
                    if (s != null)
                        break;
                    q = new CompletionNode(d);
                }
            }
        }
        if (r != null && s != null && (d == null || d.compareAndSet(0, 1))) {
            T t; U u; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            if (ex != null)
                u = null;
            else if (s instanceof AltResult) {
                ex = ((AltResult)s).ex;
                u = null;
            }
            else {
                @SuppressWarnings("unchecked") U us = (U) s;
                u = us;
            }
            if (ex == null) {
                try {
                    if (e != null)
                        execAsync(e, new AsyncAcceptBoth<T,U>(t, u, fn, dst));
                    else
                        fn.accept(t, u);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    private CompletableFuture<Void> doRunAfterBoth(CompletableFuture<?> other,
                                                   Runnable action,
                                                   Executor e) {
        if (other == null || action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        RunAfterBoth d = null;
        Object r, s = null;
        if ((r = result) == null || (s = other.result) == null) {
            d = new RunAfterBoth(this, other, action, dst, e);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r == null && (r = result) == null) ||
                   (s == null && (s = other.result) == null)) {
                if (q != null) {
                    if (s != null ||
                        UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (r != null ||
                         UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p)) {
                    if (s != null)
                        break;
                    q = new CompletionNode(d);
                }
            }
        }
        if (r != null && s != null && (d == null || d.compareAndSet(0, 1))) {
            Throwable ex;
            if (r instanceof AltResult)
                ex = ((AltResult)r).ex;
            else
                ex = null;
            if (ex == null && (s instanceof AltResult))
                ex = ((AltResult)s).ex;
            if (ex == null) {
                try {
                    if (e != null)
                        execAsync(e, new AsyncRun(action, dst));
                    else
                        action.run();
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    private <U> CompletableFuture<U> doApplyToEither
        (CompletableFuture<? extends T> other,
         Function<? super T, U> fn,
         Executor e) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = new CompletableFuture<U>();
        ApplyToEither<T,U> d = null;
        Object r;
        if ((r = result) == null && (r = other.result) == null) {
            d = new ApplyToEither<T,U>(this, other, fn, dst, e);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r = result) == null && (r = other.result) == null) {
                if (q != null) {
                    if (UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p))
                    q = new CompletionNode(d);
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            U u = null;
            if (ex == null) {
                try {
                    if (e != null)
                        execAsync(e, new AsyncApply<T,U>(t, fn, dst));
                    else
                        u = fn.apply(t);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(u, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    private CompletableFuture<Void> doAcceptEither
        (CompletableFuture<? extends T> other,
         Consumer<? super T> fn,
         Executor e) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        AcceptEither<T> d = null;
        Object r;
        if ((r = result) == null && (r = other.result) == null) {
            d = new AcceptEither<T>(this, other, fn, dst, e);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r = result) == null && (r = other.result) == null) {
                if (q != null) {
                    if (UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p))
                    q = new CompletionNode(d);
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            if (ex == null) {
                try {
                    if (e != null)
                        execAsync(e, new AsyncAccept<T>(t, fn, dst));
                    else
                        fn.accept(t);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    private CompletableFuture<Void> doRunAfterEither
        (CompletableFuture<?> other,
         Runnable action,
         Executor e) {
        if (other == null || action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        RunAfterEither d = null;
        Object r;
        if ((r = result) == null && (r = other.result) == null) {
            d = new RunAfterEither(this, other, action, dst, e);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r = result) == null && (r = other.result) == null) {
                if (q != null) {
                    if (UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p))
                    q = new CompletionNode(d);
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            Throwable ex;
            if (r instanceof AltResult)
                ex = ((AltResult)r).ex;
            else
                ex = null;
            if (ex == null) {
                try {
                    if (e != null)
                        execAsync(e, new AsyncRun(action, dst));
                    else
                        action.run();
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    private <U> CompletableFuture<U> doThenCompose
        (Function<? super T, ? extends CompletionStage<U>> fn,
         Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = null;
        ThenCompose<T,U> d = null;
        Object r;
        if ((r = result) == null) {
            dst = new CompletableFuture<U>();
            CompletionNode p = new CompletionNode
                (d = new ThenCompose<T,U>(this, fn, dst, e));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            if (ex == null) {
                if (e != null) {
                    if (dst == null)
                        dst = new CompletableFuture<U>();
                    execAsync(e, new AsyncCompose<T,U>(t, fn, dst));
                }
                else {
                    try {
                        CompletionStage<U> cs = fn.apply(t);
                        if (cs == null ||
                            (dst = cs.toCompletableFuture()) == null)
                            ex = new NullPointerException();
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
            }
            if (dst == null)
                dst = new CompletableFuture<U>();
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        dst.helpPostComplete();
        return dst;
    }

    private CompletableFuture<T> doWhenComplete
        (BiConsumer<? super T, ? super Throwable> fn,
         Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<T> dst = new CompletableFuture<T>();
        WhenCompleteCompletion<T> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p =
                new CompletionNode(d = new WhenCompleteCompletion<T>
                                   (this, fn, dst, e));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject(this, COMPLETIONS,
                                                p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            Throwable dx = null;
            try {
                if (e != null)
                    execAsync(e, new AsyncWhenComplete<T>(t, ex, fn, dst));
                else
                    fn.accept(t, ex);
            } catch (Throwable rex) {
                dx = rex;
            }
            if (e == null || dx != null)
                dst.internalComplete(t, ex != null ? ex : dx);
        }
        helpPostComplete();
        return dst;
    }

    private <U> CompletableFuture<U> doHandle
        (BiFunction<? super T, Throwable, ? extends U> fn,
         Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = new CompletableFuture<U>();
        HandleCompletion<T,U> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p =
                new CompletionNode(d = new HandleCompletion<T,U>
                                   (this, fn, dst, e));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject(this, COMPLETIONS,
                                                p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            U u = null;
            Throwable dx = null;
            try {
                if (e != null)
                    execAsync(e, new AsyncCombine<T,Throwable,U>(t, ex, fn, dst));
                else {
                    u = fn.apply(t, ex);
                    dx = null;
                }
            } catch (Throwable rex) {
                dx = rex;
                u = null;
            }
            if (e == null || dx != null)
                dst.internalComplete(u, dx);
        }
        helpPostComplete();
        return dst;
    }


    // public methods

    /**
     * Creates a new incomplete CompletableFuture.
     */
    public CompletableFuture() {
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the {@link ForkJoinPool#commonPool()} with
     * the value obtained by calling the given Supplier.
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletableFuture
     * @param <U> the function's return type
     * @return the new CompletableFuture
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        if (supplier == null) throw new NullPointerException();
        CompletableFuture<U> f = new CompletableFuture<U>();
        execAsync(ForkJoinPool.commonPool(), new AsyncSupply<U>(supplier, f));
        return f;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the given executor with the value obtained
     * by calling the given Supplier.
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @param <U> the function's return type
     * @return the new CompletableFuture
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier,
                                                       Executor executor) {
        if (executor == null || supplier == null)
            throw new NullPointerException();
        CompletableFuture<U> f = new CompletableFuture<U>();
        execAsync(executor, new AsyncSupply<U>(supplier, f));
        return f;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the {@link ForkJoinPool#commonPool()} after
     * it runs the given action.
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        if (runnable == null) throw new NullPointerException();
        CompletableFuture<Void> f = new CompletableFuture<Void>();
        execAsync(ForkJoinPool.commonPool(), new AsyncRun(runnable, f));
        return f;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the given executor after it runs the given
     * action.
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable,
                                                   Executor executor) {
        if (executor == null || runnable == null)
            throw new NullPointerException();
        CompletableFuture<Void> f = new CompletableFuture<Void>();
        execAsync(executor, new AsyncRun(runnable, f));
        return f;
    }

    /**
     * Returns a new CompletableFuture that is already completed with
     * the given value.
     *
     * @param value the value
     * @param <U> the type of the value
     * @return the completed CompletableFuture
     */
    public static <U> CompletableFuture<U> completedFuture(U value) {
        CompletableFuture<U> f = new CompletableFuture<U>();
        f.result = (value == null) ? NIL : value;
        return f;
    }

    /**
     * Returns {@code true} if completed in any fashion: normally,
     * exceptionally, or via cancellation.
     *
     * @return {@code true} if completed
     */
    public boolean isDone() {
        return result != null;
    }

    /**
     * Waits if necessary for this future to complete, and then
     * returns its result.
     *
     * @return the result value
     * @throws CancellationException if this future was cancelled
     * @throws ExecutionException if this future completed exceptionally
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     */
    public T get() throws InterruptedException, ExecutionException {
        Object r; Throwable ex, cause;
        if ((r = result) == null && (r = waitingGet(true)) == null)
            throw new InterruptedException();
        if (!(r instanceof AltResult)) {
            @SuppressWarnings("unchecked") T tr = (T) r;
            return tr;
        }
        if ((ex = ((AltResult)r).ex) == null)
            return null;
        if (ex instanceof CancellationException)
            throw (CancellationException)ex;
        if ((ex instanceof CompletionException) &&
            (cause = ex.getCause()) != null)
            ex = cause;
        throw new ExecutionException(ex);
    }

    /**
     * Waits if necessary for at most the given time for this future
     * to complete, and then returns its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the result value
     * @throws CancellationException if this future was cancelled
     * @throws ExecutionException if this future completed exceptionally
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        Object r; Throwable ex, cause;
        long nanos = unit.toNanos(timeout);
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((r = result) == null)
            r = timedAwaitDone(nanos);
        if (!(r instanceof AltResult)) {
            @SuppressWarnings("unchecked") T tr = (T) r;
            return tr;
        }
        if ((ex = ((AltResult)r).ex) == null)
            return null;
        if (ex instanceof CancellationException)
            throw (CancellationException)ex;
        if ((ex instanceof CompletionException) &&
            (cause = ex.getCause()) != null)
            ex = cause;
        throw new ExecutionException(ex);
    }

    /**
     * Returns the result value when complete, or throws an
     * (unchecked) exception if completed exceptionally. To better
     * conform with the use of common functional forms, if a
     * computation involved in the completion of this
     * CompletableFuture threw an exception, this method throws an
     * (unchecked) {@link CompletionException} with the underlying
     * exception as its cause.
     *
     * @return the result value
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if this future completed
     * exceptionally or a completion computation threw an exception
     */
    public T join() {
        Object r; Throwable ex;
        if ((r = result) == null)
            r = waitingGet(false);
        if (!(r instanceof AltResult)) {
            @SuppressWarnings("unchecked") T tr = (T) r;
            return tr;
        }
        if ((ex = ((AltResult)r).ex) == null)
            return null;
        if (ex instanceof CancellationException)
            throw (CancellationException)ex;
        if (ex instanceof CompletionException)
            throw (CompletionException)ex;
        throw new CompletionException(ex);
    }

    /**
     * Returns the result value (or throws any encountered exception)
     * if completed, else returns the given valueIfAbsent.
     *
     * @param valueIfAbsent the value to return if not completed
     * @return the result value, if completed, else the given valueIfAbsent
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if this future completed
     * exceptionally or a completion computation threw an exception
     */
    public T getNow(T valueIfAbsent) {
        Object r; Throwable ex;
        if ((r = result) == null)
            return valueIfAbsent;
        if (!(r instanceof AltResult)) {
            @SuppressWarnings("unchecked") T tr = (T) r;
            return tr;
        }
        if ((ex = ((AltResult)r).ex) == null)
            return null;
        if (ex instanceof CancellationException)
            throw (CancellationException)ex;
        if (ex instanceof CompletionException)
            throw (CompletionException)ex;
        throw new CompletionException(ex);
    }

    /**
     * If not already completed, sets the value returned by {@link
     * #get()} and related methods to the given value.
     *
     * @param value the result value
     * @return {@code true} if this invocation caused this CompletableFuture
     * to transition to a completed state, else {@code false}
     */
    public boolean complete(T value) {
        boolean triggered = result == null &&
            UNSAFE.compareAndSwapObject(this, RESULT, null,
                                        value == null ? NIL : value);
        postComplete();
        return triggered;
    }

    /**
     * If not already completed, causes invocations of {@link #get()}
     * and related methods to throw the given exception.
     *
     * @param ex the exception
     * @return {@code true} if this invocation caused this CompletableFuture
     * to transition to a completed state, else {@code false}
     */
    public boolean completeExceptionally(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        boolean triggered = result == null &&
            UNSAFE.compareAndSwapObject(this, RESULT, null, new AltResult(ex));
        postComplete();
        return triggered;
    }

    // CompletionStage methods

    public <U> CompletableFuture<U> thenApply
        (Function<? super T,? extends U> fn) {
        return doThenApply(fn, null);
    }

    public <U> CompletableFuture<U> thenApplyAsync
        (Function<? super T,? extends U> fn) {
        return doThenApply(fn, ForkJoinPool.commonPool());
    }

    public <U> CompletableFuture<U> thenApplyAsync
        (Function<? super T,? extends U> fn,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doThenApply(fn, executor);
    }

    public CompletableFuture<Void> thenAccept
        (Consumer<? super T> action) {
        return doThenAccept(action, null);
    }

    public CompletableFuture<Void> thenAcceptAsync
        (Consumer<? super T> action) {
        return doThenAccept(action, ForkJoinPool.commonPool());
    }

    public CompletableFuture<Void> thenAcceptAsync
        (Consumer<? super T> action,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doThenAccept(action, executor);
    }

    public CompletableFuture<Void> thenRun
        (Runnable action) {
        return doThenRun(action, null);
    }

    public CompletableFuture<Void> thenRunAsync
        (Runnable action) {
        return doThenRun(action, ForkJoinPool.commonPool());
    }

    public CompletableFuture<Void> thenRunAsync
        (Runnable action,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doThenRun(action, executor);
    }

    public <U,V> CompletableFuture<V> thenCombine
        (CompletionStage<? extends U> other,
         BiFunction<? super T,? super U,? extends V> fn) {
        return doThenCombine(other.toCompletableFuture(), fn, null);
    }

    public <U,V> CompletableFuture<V> thenCombineAsync
        (CompletionStage<? extends U> other,
         BiFunction<? super T,? super U,? extends V> fn) {
        return doThenCombine(other.toCompletableFuture(), fn,
                             ForkJoinPool.commonPool());
    }

    public <U,V> CompletableFuture<V> thenCombineAsync
        (CompletionStage<? extends U> other,
         BiFunction<? super T,? super U,? extends V> fn,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doThenCombine(other.toCompletableFuture(), fn, executor);
    }

    public <U> CompletableFuture<Void> thenAcceptBoth
        (CompletionStage<? extends U> other,
         BiConsumer<? super T, ? super U> action) {
        return doThenAcceptBoth(other.toCompletableFuture(), action, null);
    }

    public <U> CompletableFuture<Void> thenAcceptBothAsync
        (CompletionStage<? extends U> other,
         BiConsumer<? super T, ? super U> action) {
        return doThenAcceptBoth(other.toCompletableFuture(), action,
                                ForkJoinPool.commonPool());
    }

    public <U> CompletableFuture<Void> thenAcceptBothAsync
        (CompletionStage<? extends U> other,
         BiConsumer<? super T, ? super U> action,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doThenAcceptBoth(other.toCompletableFuture(), action, executor);
    }

    public CompletableFuture<Void> runAfterBoth
        (CompletionStage<?> other,
         Runnable action) {
        return doRunAfterBoth(other.toCompletableFuture(), action, null);
    }

    public CompletableFuture<Void> runAfterBothAsync
        (CompletionStage<?> other,
         Runnable action) {
        return doRunAfterBoth(other.toCompletableFuture(), action,
                              ForkJoinPool.commonPool());
    }

    public CompletableFuture<Void> runAfterBothAsync
        (CompletionStage<?> other,
         Runnable action,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doRunAfterBoth(other.toCompletableFuture(), action, executor);
    }


    public <U> CompletableFuture<U> applyToEither
        (CompletionStage<? extends T> other,
         Function<? super T, U> fn) {
        return doApplyToEither(other.toCompletableFuture(), fn, null);
    }

    public <U> CompletableFuture<U> applyToEitherAsync
        (CompletionStage<? extends T> other,
         Function<? super T, U> fn) {
        return doApplyToEither(other.toCompletableFuture(), fn,
                               ForkJoinPool.commonPool());
    }

    public <U> CompletableFuture<U> applyToEitherAsync
        (CompletionStage<? extends T> other,
         Function<? super T, U> fn,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doApplyToEither(other.toCompletableFuture(), fn, executor);
    }

    public CompletableFuture<Void> acceptEither
        (CompletionStage<? extends T> other,
         Consumer<? super T> action) {
        return doAcceptEither(other.toCompletableFuture(), action, null);
    }

    public CompletableFuture<Void> acceptEitherAsync
        (CompletionStage<? extends T> other,
         Consumer<? super T> action) {
        return doAcceptEither(other.toCompletableFuture(), action,
                              ForkJoinPool.commonPool());
    }

    public CompletableFuture<Void> acceptEitherAsync
        (CompletionStage<? extends T> other,
         Consumer<? super T> action,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doAcceptEither(other.toCompletableFuture(), action, executor);
    }

    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other,
                                                  Runnable action) {
        return doRunAfterEither(other.toCompletableFuture(), action, null);
    }

    public CompletableFuture<Void> runAfterEitherAsync
        (CompletionStage<?> other,
         Runnable action) {
        return doRunAfterEither(other.toCompletableFuture(), action,
                                ForkJoinPool.commonPool());
    }

    public CompletableFuture<Void> runAfterEitherAsync
        (CompletionStage<?> other,
         Runnable action,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doRunAfterEither(other.toCompletableFuture(), action, executor);
    }

    public <U> CompletableFuture<U> thenCompose
        (Function<? super T, ? extends CompletionStage<U>> fn) {
        return doThenCompose(fn, null);
    }

    public <U> CompletableFuture<U> thenComposeAsync
        (Function<? super T, ? extends CompletionStage<U>> fn) {
        return doThenCompose(fn, ForkJoinPool.commonPool());
    }

    public <U> CompletableFuture<U> thenComposeAsync
        (Function<? super T, ? extends CompletionStage<U>> fn,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doThenCompose(fn, executor);
    }

    public CompletableFuture<T> whenComplete
        (BiConsumer<? super T, ? super Throwable> action) {
        return doWhenComplete(action, null);
    }

    public CompletableFuture<T> whenCompleteAsync
        (BiConsumer<? super T, ? super Throwable> action) {
        return doWhenComplete(action, ForkJoinPool.commonPool());
    }

    public CompletableFuture<T> whenCompleteAsync
        (BiConsumer<? super T, ? super Throwable> action,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doWhenComplete(action, executor);
    }

    public <U> CompletableFuture<U> handle
        (BiFunction<? super T, Throwable, ? extends U> fn) {
        return doHandle(fn, null);
    }

    public <U> CompletableFuture<U> handleAsync
        (BiFunction<? super T, Throwable, ? extends U> fn) {
        return doHandle(fn, ForkJoinPool.commonPool());
    }

    public <U> CompletableFuture<U> handleAsync
        (BiFunction<? super T, Throwable, ? extends U> fn,
         Executor executor) {
        if (executor == null) throw new NullPointerException();
        return doHandle(fn, executor);
    }

    /**
     * Returns this CompletableFuture
     *
     * @return this CompletableFuture
     */
    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }

    // not in interface CompletionStage

    /**
     * Returns a new CompletableFuture that is completed when this
     * CompletableFuture completes, with the result of the given
     * function of the exception triggering this CompletableFuture's
     * completion when it completes exceptionally; otherwise, if this
     * CompletableFuture completes normally, then the returned
     * CompletableFuture also completes normally with the same value.
     * Note: More flexible versions of this functionality are
     * available using methods {@code whenComplete} and {@code handle}.
     *
     * @param fn the function to use to compute the value of the
     * returned CompletableFuture if this CompletableFuture completed
     * exceptionally
     * @return the new CompletableFuture
     */
    public CompletableFuture<T> exceptionally
        (Function<Throwable, ? extends T> fn) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<T> dst = new CompletableFuture<T>();
        ExceptionCompletion<T> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p =
                new CompletionNode(d = new ExceptionCompletion<T>
                                   (this, fn, dst));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject(this, COMPLETIONS,
                                                p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t = null; Throwable ex, dx = null;
            if (r instanceof AltResult) {
                if ((ex = ((AltResult)r).ex) != null) {
                    try {
                        t = fn.apply(ex);
                    } catch (Throwable rex) {
                        dx = rex;
                    }
                }
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r;
                t = tr;
            }
            dst.internalComplete(t, dx);
        }
        helpPostComplete();
        return dst;
    }

    /* ------------- Arbitrary-arity constructions -------------- */

    /*
     * The basic plan of attack is to recursively form binary
     * completion trees of elements. This can be overkill for small
     * sets, but scales nicely. The And/All vs Or/Any forms use the
     * same idea, but details differ.
     */

    /**
     * Returns a new CompletableFuture that is completed when all of
     * the given CompletableFutures complete.  If any of the given
     * CompletableFutures complete exceptionally, then the returned
     * CompletableFuture also does so, with a CompletionException
     * holding this exception as its cause.  Otherwise, the results,
     * if any, of the given CompletableFutures are not reflected in
     * the returned CompletableFuture, but may be obtained by
     * inspecting them individually. If no CompletableFutures are
     * provided, returns a CompletableFuture completed with the value
     * {@code null}.
     *
     * <p>Among the applications of this method is to await completion
     * of a set of independent CompletableFutures before continuing a
     * program, as in: {@code CompletableFuture.allOf(c1, c2,
     * c3).join();}.
     *
     * @param cfs the CompletableFutures
     * @return a new CompletableFuture that is completed when all of the
     * given CompletableFutures complete
     * @throws NullPointerException if the array or any of its elements are
     * {@code null}
     */
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
        int len = cfs.length; // Directly handle empty and singleton cases
        if (len > 1)
            return allTree(cfs, 0, len - 1);
        else {
            CompletableFuture<Void> dst = new CompletableFuture<Void>();
            CompletableFuture<?> f;
            if (len == 0)
                dst.result = NIL;
            else if ((f = cfs[0]) == null)
                throw new NullPointerException();
            else {
                ThenPropagate d = null;
                CompletionNode p = null;
                Object r;
                while ((r = f.result) == null) {
                    if (d == null)
                        d = new ThenPropagate(f, dst);
                    else if (p == null)
                        p = new CompletionNode(d);
                    else if (UNSAFE.compareAndSwapObject
                             (f, COMPLETIONS, p.next = f.completions, p))
                        break;
                }
                if (r != null && (d == null || d.compareAndSet(0, 1)))
                    dst.internalComplete(null, (r instanceof AltResult) ?
                                         ((AltResult)r).ex : null);
                f.helpPostComplete();
            }
            return dst;
        }
    }

    /**
     * Recursively constructs an And'ed tree of CompletableFutures.
     * Called only when array known to have at least two elements.
     */
    private static CompletableFuture<Void> allTree(CompletableFuture<?>[] cfs,
                                                   int lo, int hi) {
        CompletableFuture<?> fst, snd;
        int mid = (lo + hi) >>> 1;
        if ((fst = (lo == mid   ? cfs[lo] : allTree(cfs, lo,    mid))) == null ||
            (snd = (hi == mid+1 ? cfs[hi] : allTree(cfs, mid+1, hi))) == null)
            throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        AndCompletion d = null;
        CompletionNode p = null, q = null;
        Object r = null, s = null;
        while ((r = fst.result) == null || (s = snd.result) == null) {
            if (d == null)
                d = new AndCompletion(fst, snd, dst);
            else if (p == null)
                p = new CompletionNode(d);
            else if (q == null) {
                if (UNSAFE.compareAndSwapObject
                    (fst, COMPLETIONS, p.next = fst.completions, p))
                    q = new CompletionNode(d);
            }
            else if (UNSAFE.compareAndSwapObject
                     (snd, COMPLETIONS, q.next = snd.completions, q))
                break;
        }
        if ((r != null || (r = fst.result) != null) &&
            (s != null || (s = snd.result) != null) &&
            (d == null || d.compareAndSet(0, 1))) {
            Throwable ex;
            if (r instanceof AltResult)
                ex = ((AltResult)r).ex;
            else
                ex = null;
            if (ex == null && (s instanceof AltResult))
                ex = ((AltResult)s).ex;
            dst.internalComplete(null, ex);
        }
        fst.helpPostComplete();
        snd.helpPostComplete();
        return dst;
    }

    /**
     * Returns a new CompletableFuture that is completed when any of
     * the given CompletableFutures complete, with the same result.
     * Otherwise, if it completed exceptionally, the returned
     * CompletableFuture also does so, with a CompletionException
     * holding this exception as its cause.  If no CompletableFutures
     * are provided, returns an incomplete CompletableFuture.
     *
     * @param cfs the CompletableFutures
     * @return a new CompletableFuture that is completed with the
     * result or exception of any of the given CompletableFutures when
     * one completes
     * @throws NullPointerException if the array or any of its elements are
     * {@code null}
     */
    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
        int len = cfs.length; // Same idea as allOf
        if (len > 1)
            return anyTree(cfs, 0, len - 1);
        else {
            CompletableFuture<Object> dst = new CompletableFuture<Object>();
            CompletableFuture<?> f;
            if (len == 0)
                ; // skip
            else if ((f = cfs[0]) == null)
                throw new NullPointerException();
            else {
                ThenCopy<Object> d = null;
                CompletionNode p = null;
                Object r;
                while ((r = f.result) == null) {
                    if (d == null)
                        d = new ThenCopy<Object>(f, dst);
                    else if (p == null)
                        p = new CompletionNode(d);
                    else if (UNSAFE.compareAndSwapObject
                             (f, COMPLETIONS, p.next = f.completions, p))
                        break;
                }
                if (r != null && (d == null || d.compareAndSet(0, 1))) {
                    Throwable ex; Object t;
                    if (r instanceof AltResult) {
                        ex = ((AltResult)r).ex;
                        t = null;
                    }
                    else {
                        ex = null;
                        t = r;
                    }
                    dst.internalComplete(t, ex);
                }
                f.helpPostComplete();
            }
            return dst;
        }
    }

    /**
     * Recursively constructs an Or'ed tree of CompletableFutures.
     */
    private static CompletableFuture<Object> anyTree(CompletableFuture<?>[] cfs,
                                                     int lo, int hi) {
        CompletableFuture<?> fst, snd;
        int mid = (lo + hi) >>> 1;
        if ((fst = (lo == mid   ? cfs[lo] : anyTree(cfs, lo,    mid))) == null ||
            (snd = (hi == mid+1 ? cfs[hi] : anyTree(cfs, mid+1, hi))) == null)
            throw new NullPointerException();
        CompletableFuture<Object> dst = new CompletableFuture<Object>();
        OrCompletion d = null;
        CompletionNode p = null, q = null;
        Object r;
        while ((r = fst.result) == null && (r = snd.result) == null) {
            if (d == null)
                d = new OrCompletion(fst, snd, dst);
            else if (p == null)
                p = new CompletionNode(d);
            else if (q == null) {
                if (UNSAFE.compareAndSwapObject
                    (fst, COMPLETIONS, p.next = fst.completions, p))
                    q = new CompletionNode(d);
            }
            else if (UNSAFE.compareAndSwapObject
                     (snd, COMPLETIONS, q.next = snd.completions, q))
                break;
        }
        if ((r != null || (r = fst.result) != null ||
             (r = snd.result) != null) &&
            (d == null || d.compareAndSet(0, 1))) {
            Throwable ex; Object t;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                t = r;
            }
            dst.internalComplete(t, ex);
        }
        fst.helpPostComplete();
        snd.helpPostComplete();
        return dst;
    }

    /* ------------- Control and status methods -------------- */

    /**
     * If not already completed, completes this CompletableFuture with
     * a {@link CancellationException}. Dependent CompletableFutures
     * that have not already completed will also complete
     * exceptionally, with a {@link CompletionException} caused by
     * this {@code CancellationException}.
     *
     * @param mayInterruptIfRunning this value has no effect in this
     * implementation because interrupts are not used to control
     * processing.
     *
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = (result == null) &&
            UNSAFE.compareAndSwapObject
            (this, RESULT, null, new AltResult(new CancellationException()));
        postComplete();
        return cancelled || isCancelled();
    }

    /**
     * Returns {@code true} if this CompletableFuture was cancelled
     * before it completed normally.
     *
     * @return {@code true} if this CompletableFuture was cancelled
     * before it completed normally
     */
    public boolean isCancelled() {
        Object r;
        return ((r = result) instanceof AltResult) &&
            (((AltResult)r).ex instanceof CancellationException);
    }

    /**
     * Returns {@code true} if this CompletableFuture completed
     * exceptionally, in any way. Possible causes include
     * cancellation, explicit invocation of {@code
     * completeExceptionally}, and abrupt termination of a
     * CompletionStage action.
     *
     * @return {@code true} if this CompletableFuture completed
     * exceptionally
     */
    public boolean isCompletedExceptionally() {
        Object r;
        return ((r = result) instanceof AltResult) && r != NIL;
    }

    /**
     * Forcibly sets or resets the value subsequently returned by
     * method {@link #get()} and related methods, whether or not
     * already completed. This method is designed for use only in
     * error recovery actions, and even in such situations may result
     * in ongoing dependent completions using established versus
     * overwritten outcomes.
     *
     * @param value the completion value
     */
    public void obtrudeValue(T value) {
        result = (value == null) ? NIL : value;
        postComplete();
    }

    /**
     * Forcibly causes subsequent invocations of method {@link #get()}
     * and related methods to throw the given exception, whether or
     * not already completed. This method is designed for use only in
     * recovery actions, and even in such situations may result in
     * ongoing dependent completions using established versus
     * overwritten outcomes.
     *
     * @param ex the exception
     */
    public void obtrudeException(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        result = new AltResult(ex);
        postComplete();
    }

    /**
     * Returns the estimated number of CompletableFutures whose
     * completions are awaiting completion of this CompletableFuture.
     * This method is designed for use in monitoring system state, not
     * for synchronization control.
     *
     * @return the number of dependent CompletableFutures
     */
    public int getNumberOfDependents() {
        int count = 0;
        for (CompletionNode p = completions; p != null; p = p.next)
            ++count;
        return count;
    }

    /**
     * Returns a string identifying this CompletableFuture, as well as
     * its completion state.  The state, in brackets, contains the
     * String {@code "Completed Normally"} or the String {@code
     * "Completed Exceptionally"}, or the String {@code "Not
     * completed"} followed by the number of CompletableFutures
     * dependent upon its completion, if any.
     *
     * @return a string identifying this CompletableFuture, as well as its state
     */
    public String toString() {
        Object r = result;
        int count;
        return super.toString() +
            ((r == null) ?
             (((count = getNumberOfDependents()) == 0) ?
              "[Not completed]" :
              "[Not completed, " + count + " dependents]") :
             (((r instanceof AltResult) && ((AltResult)r).ex != null) ?
              "[Completed exceptionally]" :
              "[Completed normally]"));
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long RESULT;
    private static final long WAITERS;
    private static final long COMPLETIONS;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = CompletableFuture.class;
            RESULT = UNSAFE.objectFieldOffset
                (k.getDeclaredField("result"));
            WAITERS = UNSAFE.objectFieldOffset
                (k.getDeclaredField("waiters"));
            COMPLETIONS = UNSAFE.objectFieldOffset
                (k.getDeclaredField("completions"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
