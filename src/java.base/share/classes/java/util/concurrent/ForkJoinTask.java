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

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.concurrent.locks.LockSupport;
import jdk.internal.misc.Unsafe;

/**
 * Abstract base class for tasks that run within a {@link ForkJoinPool}.
 * A {@code ForkJoinTask} is a thread-like entity that is much
 * lighter weight than a normal thread.  Huge numbers of tasks and
 * subtasks may be hosted by a small number of actual threads in a
 * ForkJoinPool, at the price of some usage limitations.
 *
 * <p>A "main" {@code ForkJoinTask} begins execution when it is
 * explicitly submitted to a {@link ForkJoinPool}, or, if not already
 * engaged in a ForkJoin computation, commenced in the {@link
 * ForkJoinPool#commonPool()} via {@link #fork}, {@link #invoke}, or
 * related methods.  Once started, it will usually in turn start other
 * subtasks.  As indicated by the name of this class, many programs
 * using {@code ForkJoinTask} employ only methods {@link #fork} and
 * {@link #join}, or derivatives such as {@link
 * #invokeAll(ForkJoinTask...) invokeAll}.  However, this class also
 * provides a number of other methods that can come into play in
 * advanced usages, as well as extension mechanics that allow support
 * of new forms of fork/join processing.
 *
 * <p>A {@code ForkJoinTask} is a lightweight form of {@link Future}.
 * The efficiency of {@code ForkJoinTask}s stems from a set of
 * restrictions (that are only partially statically enforceable)
 * reflecting their main use as computational tasks calculating pure
 * functions or operating on purely isolated objects.  The primary
 * coordination mechanisms are {@link #fork}, that arranges
 * asynchronous execution, and {@link #join}, that doesn't proceed
 * until the task's result has been computed.  Computations should
 * ideally avoid {@code synchronized} methods or blocks, and should
 * minimize other blocking synchronization apart from joining other
 * tasks or using synchronizers such as Phasers that are advertised to
 * cooperate with fork/join scheduling. Subdividable tasks should also
 * not perform blocking I/O, and should ideally access variables that
 * are completely independent of those accessed by other running
 * tasks. These guidelines are loosely enforced by not permitting
 * checked exceptions such as {@code IOExceptions} to be
 * thrown. However, computations may still encounter unchecked
 * exceptions, that are rethrown to callers attempting to join
 * them. These exceptions may additionally include {@link
 * RejectedExecutionException} stemming from internal resource
 * exhaustion, such as failure to allocate internal task
 * queues. Rethrown exceptions behave in the same way as regular
 * exceptions, but, when possible, contain stack traces (as displayed
 * for example using {@code ex.printStackTrace()}) of both the thread
 * that initiated the computation as well as the thread actually
 * encountering the exception; minimally only the latter.
 *
 * <p>It is possible to define and use ForkJoinTasks that may block,
 * but doing so requires three further considerations: (1) Completion
 * of few if any <em>other</em> tasks should be dependent on a task
 * that blocks on external synchronization or I/O. Event-style async
 * tasks that are never joined (for example, those subclassing {@link
 * CountedCompleter}) often fall into this category.  (2) To minimize
 * resource impact, tasks should be small; ideally performing only the
 * (possibly) blocking action. (3) Unless the {@link
 * ForkJoinPool.ManagedBlocker} API is used, or the number of possibly
 * blocked tasks is known to be less than the pool's {@link
 * ForkJoinPool#getParallelism} level, the pool cannot guarantee that
 * enough threads will be available to ensure progress or good
 * performance.
 *
 * <p>The primary method for awaiting completion and extracting
 * results of a task is {@link #join}, but there are several variants:
 * The {@link Future#get} methods support interruptible and/or timed
 * waits for completion and report results using {@code Future}
 * conventions. Method {@link #invoke} is semantically
 * equivalent to {@code fork(); join()} but always attempts to begin
 * execution in the current thread. The "<em>quiet</em>" forms of
 * these methods do not extract results or report exceptions. These
 * may be useful when a set of tasks are being executed, and you need
 * to delay processing of results or exceptions until all complete.
 * Method {@code invokeAll} (available in multiple versions)
 * performs the most common form of parallel invocation: forking a set
 * of tasks and joining them all.
 *
 * <p>In the most typical usages, a fork-join pair act like a call
 * (fork) and return (join) from a parallel recursive function. As is
 * the case with other forms of recursive calls, returns (joins)
 * should be performed innermost-first. For example, {@code a.fork();
 * b.fork(); b.join(); a.join();} is likely to be substantially more
 * efficient than joining {@code a} before {@code b}.
 *
 * <p>The execution status of tasks may be queried at several levels
 * of detail: {@link #isDone} is true if a task completed in any way
 * (including the case where a task was cancelled without executing);
 * {@link #isCompletedNormally} is true if a task completed without
 * cancellation or encountering an exception; {@link #isCancelled} is
 * true if the task was cancelled (in which case {@link #getException}
 * returns a {@link CancellationException}); and
 * {@link #isCompletedAbnormally} is true if a task was either
 * cancelled or encountered an exception, in which case {@link
 * #getException} will return either the encountered exception or
 * {@link CancellationException}.
 *
 * <p>The ForkJoinTask class is not usually directly subclassed.
 * Instead, you subclass one of the abstract classes that support a
 * particular style of fork/join processing, typically {@link
 * RecursiveAction} for most computations that do not return results,
 * {@link RecursiveTask} for those that do, and {@link
 * CountedCompleter} for those in which completed actions trigger
 * other actions.  Normally, a concrete ForkJoinTask subclass declares
 * fields comprising its parameters, established in a constructor, and
 * then defines a {@code compute} method that somehow uses the control
 * methods supplied by this base class.
 *
 * <p>Method {@link #join} and its variants are appropriate for use
 * only when completion dependencies are acyclic; that is, the
 * parallel computation can be described as a directed acyclic graph
 * (DAG). Otherwise, executions may encounter a form of deadlock as
 * tasks cyclically wait for each other.  However, this framework
 * supports other methods and techniques (for example the use of
 * {@link Phaser}, {@link #helpQuiesce}, and {@link #complete}) that
 * may be of use in constructing custom subclasses for problems that
 * are not statically structured as DAGs. To support such usages, a
 * ForkJoinTask may be atomically <em>tagged</em> with a {@code short}
 * value using {@link #setForkJoinTaskTag} or {@link
 * #compareAndSetForkJoinTaskTag} and checked using {@link
 * #getForkJoinTaskTag}. The ForkJoinTask implementation does not use
 * these {@code protected} methods or tags for any purpose, but they
 * may be of use in the construction of specialized subclasses.  For
 * example, parallel graph traversals can use the supplied methods to
 * avoid revisiting nodes/tasks that have already been processed.
 * (Method names for tagging are bulky in part to encourage definition
 * of methods that reflect their usage patterns.)
 *
 * <p>Most base support methods are {@code final}, to prevent
 * overriding of implementations that are intrinsically tied to the
 * underlying lightweight task scheduling framework.  Developers
 * creating new basic styles of fork/join processing should minimally
 * implement {@code protected} methods {@link #exec}, {@link
 * #setRawResult}, and {@link #getRawResult}, while also introducing
 * an abstract computational method that can be implemented in its
 * subclasses, possibly relying on other {@code protected} methods
 * provided by this class.
 *
 * <p>ForkJoinTasks should perform relatively small amounts of
 * computation. Large tasks should be split into smaller subtasks,
 * usually via recursive decomposition. As a very rough rule of thumb,
 * a task should perform more than 100 and less than 10000 basic
 * computational steps, and should avoid indefinite looping. If tasks
 * are too big, then parallelism cannot improve throughput. If too
 * small, then memory and internal task maintenance overhead may
 * overwhelm processing.
 *
 * <p>This class provides {@code adapt} methods for {@link Runnable}
 * and {@link Callable}, that may be of use when mixing execution of
 * {@code ForkJoinTasks} with other kinds of tasks. When all tasks are
 * of this form, consider using a pool constructed in <em>asyncMode</em>.
 *
 * <p>ForkJoinTasks are {@code Serializable}, which enables them to be
 * used in extensions such as remote execution frameworks. It is
 * sensible to serialize tasks only before or after, but not during,
 * execution. Serialization is not relied on during execution itself.
 *
 * @param <V> the type of the result of the task
 *
 * @since 1.7
 * @author Doug Lea
 */
public abstract class ForkJoinTask<V> implements Future<V>, Serializable {

    /*
     * See the internal documentation of class ForkJoinPool for a
     * general implementation overview.  ForkJoinTasks are mainly
     * responsible for maintaining their "status" field amidst relays
     * to methods in ForkJoinWorkerThread and ForkJoinPool, along with
     * recording and reporting exceptions.  The status field mainly
     * holds bits recording completion status.  Note that there is no
     * status bit representing "running", recording whether incomplete
     * tasks are queued vs executing. However these cases can be
     * distinguished in subclasses of InterruptibleTask that adds this
     * capability by recording the running thread.  Cancellation is
     * recorded in status bits (ABNORMAL but not THROWN), but reported
     * in joining methods by throwing an exception. Other exceptions
     * of completed (THROWN) tasks are recorded in the "aux" field,
     * but are reconstructed (in getException) to produce more useful
     * stack traces when reported. Sentinels for interruptions or
     * timeouts while waiting for completion are not recorded as
     * status bits but are included in return values of methods in
     * which they occur.
     *
     * The methods of this class are more-or-less layered into
     * (1) basic status maintenance
     * (2) execution and awaiting completion
     * (3) user-level methods that additionally report results.
     * (4) Subclasses for adaptors and internal usages
     * This is sometimes hard to see because this file orders exported
     * methods in a way that flows well in javadocs.
     */

    /**
     * Nodes for threads waiting for completion, or holding a thrown
     * exception (never both). Waiting threads prepend nodes
     * Treiber-stack-style.  Signallers detach and unpark
     * waiters. Cancelled waiters try to unsplice.
     */
    static final class Aux {
        Thread thread;          // thrower or waiter
        final Throwable ex;
        Aux next;               // accessed only via memory-acquire chains
        Aux(Thread thread, Throwable ex) {
            this.thread = thread;
            this.ex = ex;
        }
        final boolean casNext(Aux c, Aux v) { // used only in cancellation
            return U.compareAndSetReference(this, NEXT, c, v);
        }
        private static final Unsafe U;
        private static final long NEXT;
        static {
            U = Unsafe.getUnsafe();
            NEXT = U.objectFieldOffset(Aux.class, "next");
        }
    }

    /*
     * The status field holds bits packed into a single int to ensure
     * atomicity.  Status is initially zero, and takes on nonnegative
     * values until completed, upon which it holds (sign bit) DONE,
     * possibly with ABNORMAL (cancelled or exceptional) and THROWN
     * (in which case an exception has been stored). A value of
     * ABNORMAL without DONE signifies an interrupted wait.  These
     * control bits occupy only (some of) the upper half (16 bits) of
     * status field. The lower bits are used for user-defined tags.
     */
    static final int DONE           = 1 << 31; // must be negative
    static final int ABNORMAL       = 1 << 16;
    static final int THROWN         = 1 << 17;
    static final int HAVE_EXCEPTION = DONE | ABNORMAL | THROWN;
    static final int MARKER         = 1 << 30; // utility marker
    static final int SMASK          = 0xffff;  // short bits for tags
    static final int UNCOMPENSATE   = 1 << 16; // helpJoin sentinel

    // Fields
    volatile int status;                // accessed directly by pool and workers
    private transient volatile Aux aux; // either waiters or thrown Exception

    // Support for atomic operations
    private static final Unsafe U;
    private static final long STATUS;
    private static final long AUX;
    private int getAndBitwiseOrStatus(int v) {
        return U.getAndBitwiseOrInt(this, STATUS, v);
    }
    private boolean casStatus(int c, int v) {
        return U.compareAndSetInt(this, STATUS, c, v);
    }

    // Support for waiting and signalling

    private boolean casAux(Aux c, Aux v) {
        return U.compareAndSetReference(this, AUX, c, v);
    }
    private Aux compareAndExchangeAux(Aux c, Aux v) {
        return (Aux)U.compareAndExchangeReference(this, AUX, c, v);
    }
    /** Removes and unparks waiters */
    private void signalWaiters() {
        for (Aux a = aux;;) {
            if (a == null || a.ex != null)
                break;
            if (a == (a = compareAndExchangeAux(a, null))) {
                do {                // detach entire list
                    LockSupport.unpark(a.thread);
                } while ((a = a.next) != null);
                break;
            }
        }
    }

    /**
     * Sets DONE status and wakes up threads waiting to join this task.
     */
    private void setDone() {
        getAndBitwiseOrStatus(DONE);
        signalWaiters();
    }

    /**
     * Sets ABNORMAL DONE status unless already done, and wakes up threads
     * waiting to join this task.
     * @return previous status
     */
    final int trySetCancelled() {
        int s;
        for (;;) {
            if ((s = status) < 0)
                break;
            if (casStatus(s, s | (DONE | ABNORMAL))) {
                signalWaiters();
                break;
            }
        }
        return s;
    }

    /**
     * Records exception and sets ABNORMAL THROWN DONE status unless
     * already done, and wakes up threads waiting to join this task.
     * If losing a race with setDone or trySetCancelled, the exception
     * may be recorded but not reported.
     *
     * @return true if set
     */
    final boolean trySetThrown(Throwable ex) {
        int s;
        boolean set = false, installed = false;
        if ((s = status) >= 0) {
            Aux a, p = null, h = new Aux(Thread.currentThread(), ex);
            do {
                if (!installed && ((a = aux) == null || a.ex == null) &&
                    (installed = casAux(a, h)))
                    p = a; // list of waiters replaced by h
                if (installed && (set = casStatus(s, s | HAVE_EXCEPTION)))
                    break;
            } while ((s = status) >= 0);
            for (; p != null; p = p.next)
                LockSupport.unpark(p.thread);
        }
        return set;
    }

    /**
     * Overridable action on setting exception
     */
    void onAuxExceptionSet(Throwable ex) {
    }

    /**
     * Tries to set exception, if so invoking onAuxExceptionSet
     */
    final void trySetException(Throwable ex) {
        if (trySetThrown(ex))
            onAuxExceptionSet(ex);
    }

    /*
     * Waits for signal, interrupt, timeout, or pool termination.
     *
     * @param pool if nonnull, the pool of ForkJoinWorkerThread caller
     * @param compensation result from a helping method
     * @param interruptible if wait is interruptible
     * @param deadline if nonzero, timeout deadline
     * @return ABNORMAL if interrupted, 0 on timeout, else status on exit
     */
    private int awaitDone(ForkJoinPool pool, int compensation,
                          boolean interruptible, long deadline) {
        int s;
        if ((s = status) >= 0) {
            Aux node = null;
            try {                             // spinwait if out of memory
                node = new Aux(Thread.currentThread(), null);
            } catch (OutOfMemoryError ex) {
            }
            boolean queued = false;
            for (Aux a;;) {                   // try to install node
                if ((s = status) < 0)
                    break;
                else if (node == null)
                    Thread.onSpinWait();
                else if (((a = aux) == null || a.ex == null) &&
                         (queued = casAux(node.next = a, node)))
                    break;
            }
            if (queued) {                     // await signal or interrupt
                LockSupport.setCurrentBlocker(this);
                int interrupts = 0;           // < 0 : throw; > 0 : re-interrupt
                for (;;) {
                    if ((s = status) < 0)
                        break;
                    else if (interrupts < 0) {
                        s = ABNORMAL;         // interrupted and not done
                        break;
                    }
                    else if (Thread.interrupted()) {
                        if (!ForkJoinPool.poolIsStopping(pool))
                            interrupts = interruptible ? -1 : 1;
                        else {
                            interrupts = 1;   // re-assert if cleared
                            try {
                                cancel(true);
                            } catch (Throwable ignore) {
                            }
                        }
                    }
                    else if (deadline != 0L) {
                        long ns;
                        if ((ns = deadline - System.nanoTime()) <= 0) {
                            s = 0;
                            break;
                        }
                        LockSupport.parkNanos(ns);
                    }
                    else
                        LockSupport.park();
                }
                node.thread = null;           // help clean aux; raciness OK
                clean: for (Aux a;;) {        // remove node if still present
                    if ((a = aux) == null || a.ex != null)
                        break;
                    for (Aux prev = null;;) {
                        Aux next = a.next;
                        if (a == node) {
                            if (prev != null)
                                prev.casNext(prev, next);
                            else if (casAux(a, next))
                                break clean;
                            break;            // check for failed or stale CAS
                        }
                        prev = a;
                        if ((a = next) == null)
                            break clean;      // not found
                    }
                }
                LockSupport.setCurrentBlocker(null);
                if (interrupts > 0)
                    Thread.currentThread().interrupt();
            }
        }
        if (compensation == UNCOMPENSATE && pool != null)
            pool.uncompensate();
        return s;
    }

    /**
     * Tries applicable helping steps while joining this task,
     * otherwise invokes blocking version of awaitDone. Called only
     * when pre-checked not to be done, and pre-screened for
     * interrupts and timeouts, if applicable.
     *
     * @param interruptible if wait is interruptible
     * @param deadline if nonzero, timeout deadline
     * @return ABNORMAL if interrupted, else status on exit
     */
    private int awaitDone(boolean interruptible, long deadline) {
        ForkJoinWorkerThread wt; ForkJoinPool p; ForkJoinPool.WorkQueue q;
        Thread t; boolean internal; int s;
        if (internal =
            (t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            p = (wt = (ForkJoinWorkerThread)t).pool;
            q = wt.workQueue;
        }
        else
            q = ForkJoinPool.externalQueue(p = ForkJoinPool.common);
        return (((s = (p == null) ? 0 :
                  ((this instanceof CountedCompleter) ?
                   p.helpComplete(this, q, internal) :
                   (this instanceof InterruptibleTask) && !internal ? status :
                   p.helpJoin(this, q, internal))) < 0)) ? s :
            awaitDone(internal ? p : null, s, interruptible, deadline);
    }

    /**
     * Runs a task body: Unless done, calls exec and records status if
     * completed, but doesn't wait for completion otherwise.
     */
    final void doExec() {
        if (status >= 0) {
            boolean completed = false;
            try {
                completed = exec();
            } catch (Throwable rex) {
                trySetException(rex);
            }
            if (completed)
                setDone();
        }
    }

    // Reporting Exceptions

    /**
     * Returns a rethrowable exception for this task, if available.
     * To provide accurate stack traces, if the exception was not
     * thrown by the current thread, we try to create a new exception
     * of the same type as the one thrown, but with the recorded
     * exception as its cause. If there is no such constructor, we
     * instead try to use a no-arg constructor, followed by initCause,
     * to the same effect. If none of these apply, or any fail due to
     * other exceptions, we return the recorded exception, which is
     * still correct, although it may contain a misleading stack
     * trace.
     *
     * @param asExecutionException true if wrap as ExecutionException
     * @return the exception, or null if none
     */
    private Throwable getException(boolean asExecutionException) {
        int s; Throwable ex; Aux a;
        if ((s = status) >= 0 || (s & ABNORMAL) == 0)
            return null;
        else if ((s & THROWN) == 0 || (a = aux) == null || (ex = a.ex) == null) {
            ex = new CancellationException();
            if (!asExecutionException || !(this instanceof InterruptibleTask))
                return ex;         // else wrap below
        }
        else if (a.thread != Thread.currentThread()) {
            try {
                Constructor<?> noArgCtor = null, oneArgCtor = null;
                for (Constructor<?> c : ex.getClass().getConstructors()) {
                    Class<?>[] ps = c.getParameterTypes();
                    if (ps.length == 0)
                        noArgCtor = c;
                    else if (ps.length == 1 && ps[0] == Throwable.class) {
                        oneArgCtor = c;
                        break;
                    }
                }
                if (oneArgCtor != null)
                    ex = (Throwable)oneArgCtor.newInstance(ex);
                else if (noArgCtor != null) {
                    Throwable rx = (Throwable)noArgCtor.newInstance();
                    rx.initCause(ex);
                    ex = rx;
                }
            } catch (Exception ignore) {
            }
        }
        return (asExecutionException) ? new ExecutionException(ex) : ex;
    }

    /**
     * Throws thrown exception, or CancellationException if none
     * recorded.
     */
    private void reportException(boolean asExecutionException) {
        ForkJoinTask.<RuntimeException>
            uncheckedThrow(getException(asExecutionException));
    }

    /**
     * A version of "sneaky throw" to relay exceptions in other
     * contexts.
     */
    static void rethrow(Throwable ex) {
        ForkJoinTask.<RuntimeException>uncheckedThrow(ex);
    }

    /**
     * The sneaky part of sneaky throw, relying on generics
     * limitations to evade compiler complaints about rethrowing
     * unchecked exceptions. If argument null, throws
     * CancellationException.
     */
    @SuppressWarnings("unchecked") static <T extends Throwable>
    void uncheckedThrow(Throwable t) throws T {
        if (t == null)
            t = new CancellationException();
        throw (T)t; // rely on vacuous cast
    }

    // Utilities shared among ForkJoinTask, ForkJoinPool

    /**
     * Sets MARKER bit, returning nonzero if previously set
     */
    final int setForkJoinTaskStatusMarkerBit() {
        return getAndBitwiseOrStatus(MARKER) & MARKER;
    }

    /**
     * Returns nonzero if MARKER bit set.
     */
    final int getForkJoinTaskStatusMarkerBit() {
        return status & MARKER;
    }

    // public methods

    /**
     * Constructor for subclasses to call.
     */
    public ForkJoinTask() {}

    /**
     * Arranges to asynchronously execute this task in the pool the
     * current task is running in, if applicable, or using the {@link
     * ForkJoinPool#commonPool()} if not {@link #inForkJoinPool}.  While
     * it is not necessarily enforced, it is a usage error to fork a
     * task more than once unless it has completed and been
     * reinitialized.  Subsequent modifications to the state of this
     * task or any data it operates on are not necessarily
     * consistently observable by any thread other than the one
     * executing it unless preceded by a call to {@link #join} or
     * related methods, or a call to {@link #isDone} returning {@code
     * true}.
     *
     * @return {@code this}, to simplify usage
     */
    public final ForkJoinTask<V> fork() {
        Thread t; ForkJoinWorkerThread wt;
        ForkJoinPool p; ForkJoinPool.WorkQueue q; boolean internal;
        if (internal =
            (t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            q = (wt = (ForkJoinWorkerThread)t).workQueue;
            p = wt.pool;
        }
        else
            q = (p = ForkJoinPool.common).externalSubmissionQueue();
        q.push(this, p, internal);
        return this;
    }

    /**
     * Returns the result of the computation when it
     * {@linkplain #isDone is done}.
     * This method differs from {@link #get()} in that abnormal
     * completion results in {@code RuntimeException} or {@code Error},
     * not {@code ExecutionException}, and that interrupts of the
     * calling thread do <em>not</em> cause the method to abruptly
     * return by throwing {@code InterruptedException}.
     *
     * @return the computed result
     */
    public final V join() {
        int s;
        if ((((s = status) < 0 ? s : awaitDone(false, 0L)) & ABNORMAL) != 0)
            reportException(false);
        return getRawResult();
    }

    /**
     * Commences performing this task, awaits its completion if
     * necessary, and returns its result, or throws an (unchecked)
     * {@code RuntimeException} or {@code Error} if the underlying
     * computation did so.
     *
     * @return the computed result
     */
    public final V invoke() {
        doExec();
        return join();
    }

    /**
     * Forks the given tasks, returning when {@code isDone} holds for
     * each task or an (unchecked) exception is encountered, in which
     * case the exception is rethrown. If more than one task
     * encounters an exception, then this method throws any one of
     * these exceptions. If any task encounters an exception, the
     * other may be cancelled. However, the execution status of
     * individual tasks is not guaranteed upon exceptional return. The
     * status of each task may be obtained using {@link
     * #getException()} and related methods to check if they have been
     * cancelled, completed normally or exceptionally, or left
     * unprocessed.
     *
     * @param t1 the first task
     * @param t2 the second task
     * @throws NullPointerException if any task is null
     */
    public static void invokeAll(ForkJoinTask<?> t1, ForkJoinTask<?> t2) {
        int s1, s2;
        if (t1 == null || t2 == null)
            throw new NullPointerException();
        t2.fork();
        t1.doExec();
        if ((((s1 = t1.status) < 0 ? s1 :
              t1.awaitDone(false, 0L)) & ABNORMAL) != 0) {
            t2.cancel(false);
            t1.reportException(false);
        }
        else if ((((s2 = t2.status) < 0 ? s2 :
                   t2.awaitDone(false, 0L)) & ABNORMAL) != 0)
            t2.reportException(false);
    }

    /**
     * Forks the given tasks, returning when {@code isDone} holds for
     * each task or an (unchecked) exception is encountered, in which
     * case the exception is rethrown. If more than one task
     * encounters an exception, then this method throws any one of
     * these exceptions. If any task encounters an exception, others
     * may be cancelled. However, the execution status of individual
     * tasks is not guaranteed upon exceptional return. The status of
     * each task may be obtained using {@link #getException()} and
     * related methods to check if they have been cancelled, completed
     * normally or exceptionally, or left unprocessed.
     *
     * @param tasks the tasks
     * @throws NullPointerException if any task is null
     */
    public static void invokeAll(ForkJoinTask<?>... tasks) {
        Throwable ex = null;
        int last = tasks.length - 1;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t; int s;
            if ((t = tasks[i]) == null) {
                ex = new NullPointerException();
                break;
            }
            if (i == 0) {
                t.doExec();
                if ((((s = t.status) < 0 ? s :
                      t.awaitDone(false, 0L)) & ABNORMAL) != 0)
                    ex = t.getException(false);
                break;
            }
            t.fork();
        }
        if (ex == null) {
            for (int i = 1; i <= last; ++i) {
                ForkJoinTask<?> t; int s;
                if ((t = tasks[i]) != null &&
                    ((((s = t.status) < 0 ? s :
                       t.awaitDone(false, 0L)) & ABNORMAL) != 0) &&
                    (ex = t.getException(false)) != null)
                    break;
            }
        }
        if (ex != null) {
            for (int i = 1; i <= last; ++i) {
                ForkJoinTask<?> t;
                if ((t = tasks[i]) != null)
                    t.cancel(false);
            }
            rethrow(ex);
        }
    }

    /**
     * Forks all tasks in the specified collection, returning when
     * {@code isDone} holds for each task or an (unchecked) exception
     * is encountered, in which case the exception is rethrown. If
     * more than one task encounters an exception, then this method
     * throws any one of these exceptions. If any task encounters an
     * exception, others may be cancelled. However, the execution
     * status of individual tasks is not guaranteed upon exceptional
     * return. The status of each task may be obtained using {@link
     * #getException()} and related methods to check if they have been
     * cancelled, completed normally or exceptionally, or left
     * unprocessed.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return the tasks argument, to simplify usage
     * @throws NullPointerException if tasks or any element are null
     */
    public static <T extends ForkJoinTask<?>> Collection<T> invokeAll(Collection<T> tasks) {
        if (!(tasks instanceof RandomAccess) || !(tasks instanceof List<?>)) {
            invokeAll(tasks.toArray(new ForkJoinTask<?>[0]));
            return tasks;
        }
        @SuppressWarnings("unchecked")
        List<? extends ForkJoinTask<?>> ts =
            (List<? extends ForkJoinTask<?>>) tasks;
        Throwable ex = null;
        int last = ts.size() - 1;  // nearly same as array version
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t; int s;
            if ((t = ts.get(i)) == null) {
                ex = new NullPointerException();
                break;
            }
            if (i == 0) {
                t.doExec();
                if ((((s = t.status) < 0 ? s :
                      t.awaitDone(false, 0L)) & ABNORMAL) != 0)
                    ex = t.getException(false);
                break;
            }
            t.fork();
        }
        if (ex == null) {
            for (int i = 1; i <= last; ++i) {
                ForkJoinTask<?> t; int s;
                if ((t = ts.get(i)) != null &&
                    ((((s = t.status) < 0 ? s :
                       t.awaitDone(false, 0L)) & ABNORMAL) != 0) &&
                    (ex = t.getException(false)) != null)
                    break;
            }
        }
        if (ex != null) {
            for (int i = 1; i <= last; ++i) {
                ForkJoinTask<?> t;
                if ((t = ts.get(i)) != null)
                    t.cancel(false);
            }
            rethrow(ex);
        }
        return tasks;
    }

    /**
     * Attempts to cancel execution of this task. This attempt will
     * fail if the task has already completed or could not be
     * cancelled for some other reason. If successful, and this task
     * has not started when {@code cancel} is called, execution of
     * this task is suppressed. After this method returns
     * successfully, unless there is an intervening call to {@link
     * #reinitialize}, subsequent calls to {@link #isCancelled},
     * {@link #isDone}, and {@code cancel} will return {@code true}
     * and calls to {@link #join} and related methods will result in
     * {@code CancellationException}.
     *
     * <p>This method may be overridden in subclasses, but if so, must
     * still ensure that these properties hold. In particular, the
     * {@code cancel} method itself must not throw exceptions.
     *
     * <p>This method is designed to be invoked by <em>other</em>
     * tasks. To terminate the current task, you can just return or
     * throw an unchecked exception from its computation method, or
     * invoke {@link #completeExceptionally(Throwable)}.
     *
     * @param mayInterruptIfRunning this value has no effect in the
     * default implementation because interrupts are not used to
     * control cancellation.
     *
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        int s = trySetCancelled();
        return (s >= 0 || (s & (ABNORMAL | THROWN)) == ABNORMAL);
    }

    public final boolean isDone() {
        return status < 0;
    }

    public final boolean isCancelled() {
        return (status & (ABNORMAL | THROWN)) == ABNORMAL;
    }

    /**
     * Returns {@code true} if this task threw an exception or was cancelled.
     *
     * @return {@code true} if this task threw an exception or was cancelled
     */
    public final boolean isCompletedAbnormally() {
        return (status & ABNORMAL) != 0;
    }

    /**
     * Returns {@code true} if this task completed without throwing an
     * exception and was not cancelled.
     *
     * @return {@code true} if this task completed without throwing an
     * exception and was not cancelled
     */
    public final boolean isCompletedNormally() {
        return (status & (DONE | ABNORMAL)) == DONE;
    }

    @Override
    public State state() {
        int s = status;
        return (s >= 0) ? State.RUNNING :
            ((s & (DONE | ABNORMAL)) == DONE) ? State.SUCCESS:
            ((s & (ABNORMAL | THROWN)) == (ABNORMAL | THROWN)) ? State.FAILED :
            State.CANCELLED;
    }

    @Override
    public V resultNow() {
        int s = status;
        if ((s & DONE) == 0)
             throw new IllegalStateException("Task has not completed");
        if ((s & ABNORMAL) != 0) {
            if ((s & THROWN) != 0)
                throw new IllegalStateException("Task completed with exception");
            else
                throw new IllegalStateException("Task was cancelled");
        }
        return getRawResult();
    }

    @Override
    public Throwable exceptionNow() {
        Throwable ex;
        if ((status & HAVE_EXCEPTION) != HAVE_EXCEPTION ||
            (ex = getException(false)) == null)
            throw new IllegalStateException();
        return ex;
    }

    /**
     * Returns the exception thrown by the base computation, or a
     * {@code CancellationException} if cancelled, or {@code null} if
     * none or if the method has not yet completed.
     *
     * @return the exception, or {@code null} if none
     */
    public final Throwable getException() {
        return getException(false);
    }

    /**
     * Completes this task abnormally, and if not already aborted or
     * cancelled, causes it to throw the given exception upon
     * {@code join} and related operations. This method may be used
     * to induce exceptions in asynchronous tasks, or to force
     * completion of tasks that would not otherwise complete.  Its use
     * in other situations is discouraged.  This method is
     * overridable, but overridden versions must invoke {@code super}
     * implementation to maintain guarantees.
     *
     * @param ex the exception to throw. If this exception is not a
     * {@code RuntimeException} or {@code Error}, the actual exception
     * thrown will be a {@code RuntimeException} with cause {@code ex}.
     */
    public void completeExceptionally(Throwable ex) {
        trySetException((ex instanceof RuntimeException) ||
                        (ex instanceof Error) ? ex :
                        new RuntimeException(ex));
    }

    /**
     * Completes this task, and if not already aborted or cancelled,
     * returning the given value as the result of subsequent
     * invocations of {@code join} and related operations. This method
     * may be used to provide results for asynchronous tasks, or to
     * provide alternative handling for tasks that would not otherwise
     * complete normally. Its use in other situations is
     * discouraged. This method is overridable, but overridden
     * versions must invoke {@code super} implementation to maintain
     * guarantees.
     *
     * @param value the result value for this task
     */
    public void complete(V value) {
        try {
            setRawResult(value);
        } catch (Throwable rex) {
            trySetException(rex);
            return;
        }
        setDone();
    }

    /**
     * Completes this task normally without setting a value. The most
     * recent value established by {@link #setRawResult} (or {@code
     * null} by default) will be returned as the result of subsequent
     * invocations of {@code join} and related operations.
     *
     * @since 1.8
     */
    public final void quietlyComplete() {
        setDone();
    }

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an
     * exception
     * @throws InterruptedException if the current thread is not a
     * member of a ForkJoinPool and was interrupted while waiting
     */
    public final V get() throws InterruptedException, ExecutionException {
        int stat = status;
        int s = ((stat < 0) ? stat :
                 (Thread.interrupted()) ? ABNORMAL :
                 awaitDone(true, 0L));
        if (s == ABNORMAL)
            throw new InterruptedException();
        else if ((s & ABNORMAL) != 0)
            reportException(true);
        return getRawResult();
    }

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an
     * exception
     * @throws InterruptedException if the current thread is not a
     * member of a ForkJoinPool and was interrupted while waiting
     * @throws TimeoutException if the wait timed out
     */
    public final V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        long nanos = unit.toNanos(timeout);
        int stat = status;
        int s = ((stat < 0) ? stat :
                 (Thread.interrupted()) ? ABNORMAL :
                 (nanos <= 0L) ? 0 :
                 awaitDone(true,  (System.nanoTime() + nanos) | 1L));
        if (s == ABNORMAL)
            throw new InterruptedException();
        else if (s >= 0)
            throw new TimeoutException();
        else if ((s & ABNORMAL) != 0)
            reportException(true);
        return getRawResult();
    }

    /**
     * Joins this task, without returning its result or throwing its
     * exception. This method may be useful when processing
     * collections of tasks when some have been cancelled or otherwise
     * known to have aborted.
     */
    public final void quietlyJoin() {
        if (status >= 0)
            awaitDone(false, 0L);
    }

    /**
     * Commences performing this task and awaits its completion if
     * necessary, without returning its result or throwing its
     * exception.
     */
    public final void quietlyInvoke() {
        doExec();
        if (status >= 0)
            awaitDone(false, 0L);
    }

    /**
     * Tries to join this task, returning true if it completed
     * (possibly exceptionally) before the given timeout and
     * the current thread has not been interrupted.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return true if this task completed
     * @throws InterruptedException if the current thread was
     * interrupted while waiting
     * @since 19
     */
    public final boolean quietlyJoin(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        int stat = status;
        int s = ((stat < 0) ? stat :
                 (Thread.interrupted()) ? ABNORMAL :
                 (nanos <= 0L) ? 0 :
                 awaitDone(true, (System.nanoTime() + nanos) | 1L));
        if (s == ABNORMAL)
            throw new InterruptedException();
        return (s < 0);
    }

    /**
     * Tries to join this task, returning true if it completed
     * (possibly exceptionally) before the given timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return true if this task completed
     * @since 19
     */
    public final boolean quietlyJoinUninterruptibly(long timeout,
                                                    TimeUnit unit) {
        int s;
        long nanos = unit.toNanos(timeout);
        if ((s = status) >= 0 && nanos > 0L)
            s = awaitDone(false, (System.nanoTime() + nanos) | 1L);
        return (s < 0);
    }

    /**
     * Utility for possibly-timed ForkJoinPool.invokeAll
     */
    final void quietlyJoinPoolInvokeAllTask(long deadline)
        throws InterruptedException {
        int s;
        if ((s = status) >= 0) {
            if (Thread.interrupted())
                s = ABNORMAL;
            else if (deadline == 0L || deadline - System.nanoTime() > 0L)
                s = awaitDone(true, deadline);
            if (s == ABNORMAL)
                throw new InterruptedException();
            else if (s >= 0)
                cancel(true);
        }
    }

    /**
     * Possibly executes tasks until the pool hosting the current task
     * {@linkplain ForkJoinPool#isQuiescent is quiescent}.  This
     * method may be of use in designs in which many tasks are forked,
     * but none are explicitly joined, instead executing them until
     * all are processed.
     */
    public static void helpQuiesce() {
        ForkJoinPool.helpQuiescePool(null, Long.MAX_VALUE, false);
    }

    /**
     * Resets the internal bookkeeping state of this task, allowing a
     * subsequent {@code fork}. This method allows repeated reuse of
     * this task, but only if reuse occurs when this task has either
     * never been forked, or has been forked, then completed and all
     * outstanding joins of this task have also completed. Effects
     * under any other usage conditions are not guaranteed.
     * This method may be useful when executing
     * pre-constructed trees of subtasks in loops.
     *
     * <p>Upon completion of this method, {@code isDone()} reports
     * {@code false}, and {@code getException()} reports {@code
     * null}. However, the value returned by {@code getRawResult} is
     * unaffected. To clear this value, you can invoke {@code
     * setRawResult(null)}.
     */
    public void reinitialize() {
        aux = null;
        status = 0;
    }

    /**
     * Returns the pool hosting the current thread, or {@code null}
     * if the current thread is executing outside of any ForkJoinPool.
     *
     * <p>This method returns {@code null} if and only if {@link
     * #inForkJoinPool} returns {@code false}.
     *
     * @return the pool, or {@code null} if none
     */
    public static ForkJoinPool getPool() {
        Thread t;
        return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread) t).pool : null);
    }

    /**
     * Returns {@code true} if the current thread is a {@link
     * ForkJoinWorkerThread} executing as a ForkJoinPool computation.
     *
     * @return {@code true} if the current thread is a {@link
     * ForkJoinWorkerThread} executing as a ForkJoinPool computation,
     * or {@code false} otherwise
     */
    public static boolean inForkJoinPool() {
        return Thread.currentThread() instanceof ForkJoinWorkerThread;
    }

    /**
     * Tries to unschedule this task for execution. This method will
     * typically (but is not guaranteed to) succeed if this task is
     * the most recently forked task by the current thread, and has
     * not commenced executing in another thread.  This method may be
     * useful when arranging alternative local processing of tasks
     * that could have been, but were not, stolen.
     *
     * @return {@code true} if unforked
     */
    public boolean tryUnfork() {
        Thread t; ForkJoinPool.WorkQueue q; boolean internal;
        if (internal =
            (t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread)t).workQueue;
        else
            q = ForkJoinPool.commonQueue();
        return (q != null && q.tryUnpush(this, internal));
    }

    /**
     * Returns an estimate of the number of tasks that have been
     * forked by the current worker thread but not yet executed. This
     * value may be useful for heuristic decisions about whether to
     * fork other tasks.
     *
     * @return the number of tasks
     */
    public static int getQueuedTaskCount() {
        Thread t; ForkJoinPool.WorkQueue q;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread)t).workQueue;
        else
            q = ForkJoinPool.commonQueue();
        return (q == null) ? 0 : q.queueSize();
    }

    /**
     * Returns an estimate of how many more locally queued tasks are
     * held by the current worker thread than there are other worker
     * threads that might steal them, or zero if this thread is not
     * operating in a ForkJoinPool. This value may be useful for
     * heuristic decisions about whether to fork other tasks. In many
     * usages of ForkJoinTasks, at steady state, each worker should
     * aim to maintain a small constant surplus (for example, 3) of
     * tasks, and to process computations locally if this threshold is
     * exceeded.
     *
     * @return the surplus number of tasks, which may be negative
     */
    public static int getSurplusQueuedTaskCount() {
        return ForkJoinPool.getSurplusQueuedTaskCount();
    }

    // Extension methods

    /**
     * Returns the result that would be returned by {@link #join}, even
     * if this task completed abnormally, or {@code null} if this task
     * is not known to have been completed.  This method is designed
     * to aid debugging, as well as to support extensions. Its use in
     * any other context is discouraged.
     *
     * @return the result, or {@code null} if not completed
     */
    public abstract V getRawResult();

    /**
     * Forces the given value to be returned as a result.  This method
     * is designed to support extensions, and should not in general be
     * called otherwise.
     *
     * @param value the value
     */
    protected abstract void setRawResult(V value);

    /**
     * Immediately performs the base action of this task and returns
     * true if, upon return from this method, this task is guaranteed
     * to have completed. This method may return false otherwise, to
     * indicate that this task is not necessarily complete (or is not
     * known to be complete), for example in asynchronous actions that
     * require explicit invocations of completion methods. This method
     * may also throw an (unchecked) exception to indicate abnormal
     * exit. This method is designed to support extensions, and should
     * not in general be called otherwise.
     *
     * @return {@code true} if this task is known to have completed normally
     */
    protected abstract boolean exec();

    /**
     * Returns, but does not unschedule or execute, a task queued by
     * the current thread but not yet executed, if one is immediately
     * available. There is no guarantee that this task will actually
     * be polled or executed next. Conversely, this method may return
     * null even if a task exists but cannot be accessed without
     * contention with other threads.  This method is designed
     * primarily to support extensions, and is unlikely to be useful
     * otherwise.
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> peekNextLocalTask() {
        Thread t; ForkJoinPool.WorkQueue q;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread)t).workQueue;
        else
            q = ForkJoinPool.commonQueue();
        return (q == null) ? null : q.peek();
    }

    /**
     * Unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed, if the
     * current thread is operating in a ForkJoinPool.  This method is
     * designed primarily to support extensions, and is unlikely to be
     * useful otherwise.
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> pollNextLocalTask() {
        Thread t;
        return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread)t).workQueue.nextLocalTask() : null);
    }

    /**
     * If the current thread is operating in a ForkJoinPool,
     * unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed, if one is
     * available, or if not available, a task that was forked by some
     * other thread, if available. Availability may be transient, so a
     * {@code null} result does not necessarily imply quiescence of
     * the pool this task is operating in.  This method is designed
     * primarily to support extensions, and is unlikely to be useful
     * otherwise.
     *
     * @return a task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> pollTask() {
        Thread t; ForkJoinWorkerThread w;
        return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                (w = (ForkJoinWorkerThread)t).pool.nextTaskFor(w.workQueue) :
                null);
    }

    /**
     * If the current thread is operating in a ForkJoinPool,
     * unschedules and returns, without executing, a task externally
     * submitted to the pool, if one is available. Availability may be
     * transient, so a {@code null} result does not necessarily imply
     * quiescence of the pool.  This method is designed primarily to
     * support extensions, and is unlikely to be useful otherwise.
     *
     * @return a task, or {@code null} if none are available
     * @since 9
     */
    protected static ForkJoinTask<?> pollSubmission() {
        Thread t;
        return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread)t).pool.pollSubmission() : null);
    }

    // tag operations

    /**
     * Returns the tag for this task.
     *
     * @return the tag for this task
     * @since 1.8
     */
    public final short getForkJoinTaskTag() {
        return (short)status;
    }

    /**
     * Atomically sets the tag value for this task and returns the old value.
     *
     * @param newValue the new tag value
     * @return the previous value of the tag
     * @since 1.8
     */
    public final short setForkJoinTaskTag(short newValue) {
        for (int s;;) {
            if (casStatus(s = status, (s & ~SMASK) | (newValue & SMASK)))
                return (short)s;
        }
    }

    /**
     * Atomically conditionally sets the tag value for this task.
     * Among other applications, tags can be used as visit markers
     * in tasks operating on graphs, as in methods that check: {@code
     * if (task.compareAndSetForkJoinTaskTag((short)0, (short)1))}
     * before processing, otherwise exiting because the node has
     * already been visited.
     *
     * @param expect the expected tag value
     * @param update the new tag value
     * @return {@code true} if successful; i.e., the current value was
     * equal to {@code expect} and was changed to {@code update}.
     * @since 1.8
     */
    public final boolean compareAndSetForkJoinTaskTag(short expect, short update) {
        for (int s;;) {
            if ((short)(s = status) != expect)
                return false;
            if (casStatus(s, (s & ~SMASK) | (update & SMASK)))
                return true;
        }
    }

    // Factory methods for adaptors below

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * a null result upon {@link #join}.
     *
     * @param runnable the runnable action
     * @return the task
     */
    public static ForkJoinTask<?> adapt(Runnable runnable) {
        return new AdaptedRunnableAction(runnable);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * the given result upon {@link #join}.
     *
     * @param runnable the runnable action
     * @param result the result upon completion
     * @param <T> the type of the result
     * @return the task
     */
    public static <T> ForkJoinTask<T> adapt(Runnable runnable, T result) {
        return new AdaptedRunnable<T>(runnable, result);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code call}
     * method of the given {@code Callable} as its action, and returns
     * its result upon {@link #join}, translating any checked exceptions
     * encountered into {@code RuntimeException}.
     *
     * @param callable the callable action
     * @param <T> the type of the callable's result
     * @return the task
     */
    public static <T> ForkJoinTask<T> adapt(Callable<? extends T> callable) {
        return new AdaptedCallable<T>(callable);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code call}
     * method of the given {@code Callable} as its action, and returns
     * its result upon {@link #join}, translating any checked exceptions
     * encountered into {@code RuntimeException}.  Additionally,
     * invocations of {@code cancel} with {@code mayInterruptIfRunning
     * true} will attempt to interrupt the thread performing the task.
     *
     * @param callable the callable action
     * @param <T> the type of the callable's result
     * @return the task
     *
     * @since 19
     */
    public static <T> ForkJoinTask<T> adaptInterruptible(Callable<? extends T> callable) {
        return new AdaptedInterruptibleCallable<T>(callable);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * the given result upon {@link #join}, translating any checked exceptions
     * encountered into {@code RuntimeException}.  Additionally,
     * invocations of {@code cancel} with {@code mayInterruptIfRunning
     * true} will attempt to interrupt the thread performing the task.
     *
     * @param runnable the runnable action
     * @param result the result upon completion
     * @param <T> the type of the result
     * @return the task
     *
     * @since 22
     */
    public static <T> ForkJoinTask<T> adaptInterruptible(Runnable runnable, T result) {
        return new AdaptedInterruptibleRunnable<T>(runnable, result);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code
     * run} method of the given {@code Runnable} as its action, and
     * returns null upon {@link #join}, translating any checked
     * exceptions encountered into {@code RuntimeException}.
     * Additionally, invocations of {@code cancel} with {@code
     * mayInterruptIfRunning true} will attempt to interrupt the
     * thread performing the task.
     *
     * @param runnable the runnable action
     * @return the task
     *
     * @since 22
     */
    public static ForkJoinTask<?> adaptInterruptible(Runnable runnable) {
        return new AdaptedInterruptibleRunnable<Void>(runnable, null);
    }

    // Serialization support

    private static final long serialVersionUID = -7721805057305804111L;

    /**
     * Saves this task to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData the current run status and the exception thrown
     * during execution, or {@code null} if none
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        Aux a;
        s.defaultWriteObject();
        s.writeObject((a = aux) == null ? null : a.ex);
    }

    /**
     * Reconstitutes this task from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        Object ex = s.readObject();
        if (ex != null)
            aux = new Aux(Thread.currentThread(), (Throwable)ex);
    }

    static {
        U = Unsafe.getUnsafe();
        STATUS = U.objectFieldOffset(ForkJoinTask.class, "status");
        AUX = U.objectFieldOffset(ForkJoinTask.class, "aux");
        Class<?> dep1 = LockSupport.class; // ensure loaded
        Class<?> dep2 = Aux.class;
    }

    // Special subclasses for adaptors and internal tasks

    /**
     * Adapter for Runnables. This implements RunnableFuture
     * to be compliant with AbstractExecutorService constraints
     * when used in ForkJoinPool.
     */
    static final class AdaptedRunnable<T> extends ForkJoinTask<T>
        implements RunnableFuture<T> {
        @SuppressWarnings("serial") // Conditionally serializable
        final Runnable runnable;
        @SuppressWarnings("serial") // Conditionally serializable
        T result;
        AdaptedRunnable(Runnable runnable, T result) {
            Objects.requireNonNull(runnable);
            this.runnable = runnable;
            this.result = result; // OK to set this even before completion
        }
        public final T getRawResult() { return result; }
        public final void setRawResult(T v) { result = v; }
        public final boolean exec() { runnable.run(); return true; }
        public final void run() { invoke(); }
        public String toString() {
            return super.toString() + "[Wrapped task = " + runnable + "]";
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adapter for Runnables without results.
     */
    static final class AdaptedRunnableAction extends ForkJoinTask<Void>
        implements RunnableFuture<Void> {
        @SuppressWarnings("serial") // Conditionally serializable
        final Runnable runnable;
        AdaptedRunnableAction(Runnable runnable) {
            Objects.requireNonNull(runnable);
            this.runnable = runnable;
        }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        public final boolean exec() { runnable.run(); return true; }
        public final void run() { invoke(); }
        public String toString() {
            return super.toString() + "[Wrapped task = " + runnable + "]";
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adapter for Callables.
     */
    static final class AdaptedCallable<T> extends ForkJoinTask<T>
        implements RunnableFuture<T> {
        @SuppressWarnings("serial") // Conditionally serializable
        final Callable<? extends T> callable;
        @SuppressWarnings("serial") // Conditionally serializable
        T result;
        AdaptedCallable(Callable<? extends T> callable) {
            Objects.requireNonNull(callable);
            this.callable = callable;
        }
        public final T getRawResult() { return result; }
        public final void setRawResult(T v) { result = v; }
        public final boolean exec() {
            try {
                result = callable.call();
                return true;
            } catch (RuntimeException rex) {
                throw rex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        public final void run() { invoke(); }
        public String toString() {
            return super.toString() + "[Wrapped task = " + callable + "]";
        }
        private static final long serialVersionUID = 2838392045355241008L;
    }

    /**
     * Tasks with semantics conforming to ExecutorService conventions.
     * Tasks are interruptible when cancelled, including cases of
     * cancellation upon pool termination. In addition to recording
     * the running thread to enable interrupt in cancel(true), the
     * task checks for termination before executing the compute
     * method, to cover shutdown races in which the task has not yet
     * been cancelled on entry and might not otherwise be cancelled by
     * others.
     */
    abstract static class InterruptibleTask<T> extends ForkJoinTask<T>
        implements RunnableFuture<T> {
        transient volatile Thread runner;
        abstract T compute() throws Exception;
        public final boolean exec() {
            Thread.interrupted();
            Thread t = runner = Thread.currentThread();
            try {
                if ((t instanceof ForkJoinWorkerThread) &&
                    ForkJoinPool.poolIsStopping(((ForkJoinWorkerThread)t).pool))
                    cancel(true);
                else {
                    try {
                        if (status >= 0)
                            setRawResult(compute());
                    } catch (Exception ex) {
                        trySetException(ex);
                    }
                }
            } finally {
                runner = null;
            }
            return true;
        }
        public boolean cancel(boolean mayInterruptIfRunning) {
            Thread t;
            if (trySetCancelled() >= 0) {
                if (mayInterruptIfRunning && (t = runner) != null) {
                    try {
                        t.interrupt();
                    } catch (Throwable ignore) {
                    }
                }
                return true;
            }
            return isCancelled();
        }
        public final void run() { quietlyInvoke(); }
        Object adaptee() { return null; } // for printing and diagnostics
        public String toString() {
            Object a = adaptee();
            String s = super.toString();
            return ((a == null) ? s :
                    (s + "[Wrapped task = " + a.toString() + "]"));
        }
        private static final long serialVersionUID = 2838392045355241008L;
    }

    /**
     * Adapter for Callable-based interruptible tasks.
     */
    static final class AdaptedInterruptibleCallable<T> extends InterruptibleTask<T> {
        @SuppressWarnings("serial") // Conditionally serializable
        final Callable<? extends T> callable;
        @SuppressWarnings("serial") // Conditionally serializable
        T result;
        AdaptedInterruptibleCallable(Callable<? extends T> callable) {
            Objects.requireNonNull(callable);
            this.callable = callable;
        }
        public final T getRawResult() { return result; }
        public final void setRawResult(T v) { result = v; }
        final T compute() throws Exception { return callable.call(); }
        final Object adaptee() { return callable; }
        private static final long serialVersionUID = 2838392045355241008L;
    }

    /**
     * Adapter for Runnable-based interruptible tasks.
     */
    static final class AdaptedInterruptibleRunnable<T> extends InterruptibleTask<T> {
        @SuppressWarnings("serial") // Conditionally serializable
        final Runnable runnable;
        @SuppressWarnings("serial") // Conditionally serializable
        final T result;
        AdaptedInterruptibleRunnable(Runnable runnable, T result) {
            Objects.requireNonNull(runnable);
            this.runnable = runnable;
            this.result = result;
        }
        public final T getRawResult() { return result; }
        public final void setRawResult(T v) { }
        final T compute() { runnable.run(); return result; }
        final Object adaptee() { return runnable; }
        private static final long serialVersionUID = 2838392045355241008L;
    }

    /**
     * Adapter for Runnables in which failure forces worker exception.
     */
    static final class RunnableExecuteAction extends InterruptibleTask<Void> {
        @SuppressWarnings("serial") // Conditionally serializable
        final Runnable runnable;
        RunnableExecuteAction(Runnable runnable) {
            Objects.requireNonNull(runnable);
            this.runnable = runnable;
        }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        final Void compute() { runnable.run(); return null; }
        final Object adaptee() { return runnable; }
        void onAuxExceptionSet(Throwable ex) { // if a handler, invoke it
            Thread t; java.lang.Thread.UncaughtExceptionHandler h;
            if ((h = ((t = Thread.currentThread()).
                      getUncaughtExceptionHandler())) != null) {
                try {
                    h.uncaughtException(t, ex);
                } catch (Throwable ignore) {
                }
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Task (that is never forked) to hold results for
     * ForkJoinPool.invokeAny, or to report exception if all subtasks
     * fail or are cancelled or the pool is terminating. Both
     * InvokeAnyRoot and InvokeAnyTask objects exist only transiently
     * during invokeAny invocations, so serialization support would be
     * nonsensical and is omitted.
     */
    @SuppressWarnings("serial")
    static final class InvokeAnyRoot<T> extends InterruptibleTask<T> {
        volatile T result;
        volatile int count; // number of tasks; decremented in case all tasks fail
        InvokeAnyRoot() { }
        final void tryComplete(InvokeAnyTask<T> f, T v, Throwable ex,
                               boolean completed) {
            if (f != null && !isDone()) {
                if (ForkJoinPool.poolIsStopping(getPool()))
                    trySetCancelled();
                else if (f.setForkJoinTaskStatusMarkerBit() == 0) {
                    if (completed) {
                        result = v;
                        quietlyComplete();
                    }
                    else if (U.getAndAddInt(this, COUNT, -1) <= 1) {
                        if (ex == null)
                            trySetCancelled();
                        else
                            trySetException(ex);
                    }
                }
            }
        }
        public final T compute()            { return null; } // never forked
        public final T getRawResult()       { return result; }
        public final void setRawResult(T v) { }

        // Common support for timed and untimed versions of invokeAny
        final T invokeAny(Collection<? extends Callable<T>> tasks,
                          ForkJoinPool pool, boolean timed, long nanos)
            throws InterruptedException, ExecutionException, TimeoutException {
            if ((count = tasks.size()) <= 0)
                throw new IllegalArgumentException();
            if (pool == null)
                throw new NullPointerException();
            InvokeAnyTask<T> t = null; // list of submitted tasks
            try {
                for (Callable<T> c : tasks)
                    pool.execute((ForkJoinTask<?>)
                                 (t = new InvokeAnyTask<T>(c, this, t)));
                return timed ? get(nanos, TimeUnit.NANOSECONDS) : get();
            } finally {
                for (; t != null; t = t.pred)
                    t.onRootCompletion();
            }
        }

        private static final Unsafe U;
        private static final long COUNT;
        static {
            U = Unsafe.getUnsafe();
            COUNT = U.objectFieldOffset(InvokeAnyRoot.class, "count");
        }
    }

    /**
     * Task with results in InvokeAnyRoot (and never independently
     * joined).
     */
    @SuppressWarnings("serial")
    static final class InvokeAnyTask<T> extends InterruptibleTask<Void> {
        final Callable<? extends T> callable;
        final InvokeAnyRoot<T> root;
        final InvokeAnyTask<T> pred; // to traverse on cancellation
        InvokeAnyTask(Callable<T> callable, InvokeAnyRoot<T> root,
                      InvokeAnyTask<T> pred) {
            Objects.requireNonNull(callable);
            this.callable = callable;
            this.root = root;
            this.pred = pred;
        }
        final Void compute() throws Exception {
            InvokeAnyRoot<T> r = root;
            T v = null; Throwable ex = null; boolean completed = false;
            if (r != null && !r.isDone()) {
                try {
                    v = callable.call();
                    completed = true;
                } catch (Exception rex) {
                    ex = rex;
                } finally {
                    r.tryComplete(this, v, ex, completed);
                }
            }
            return null;
        }
        public final boolean cancel(boolean mayInterruptIfRunning) {
            InvokeAnyRoot<T> r;
            boolean stat = super.cancel(mayInterruptIfRunning);
            if ((r = root) != null)
                r.tryComplete(this, null, null, false);
            return stat;
        }
        final void onRootCompletion() {
            if (!isDone())
                super.cancel(true); // no need for tryComplete
        }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        final Object adaptee() { return callable; }
    }
}
