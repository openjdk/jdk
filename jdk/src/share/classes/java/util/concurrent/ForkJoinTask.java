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

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Abstract base class for tasks that run within a {@link ForkJoinPool}.
 * A {@code ForkJoinTask} is a thread-like entity that is much
 * lighter weight than a normal thread.  Huge numbers of tasks and
 * subtasks may be hosted by a small number of actual threads in a
 * ForkJoinPool, at the price of some usage limitations.
 *
 * <p>A "main" {@code ForkJoinTask} begins execution when submitted
 * to a {@link ForkJoinPool}.  Once started, it will usually in turn
 * start other subtasks.  As indicated by the name of this class,
 * many programs using {@code ForkJoinTask} employ only methods
 * {@link #fork} and {@link #join}, or derivatives such as {@link
 * #invokeAll}.  However, this class also provides a number of other
 * methods that can come into play in advanced usages, as well as
 * extension mechanics that allow support of new forms of fork/join
 * processing.
 *
 * <p>A {@code ForkJoinTask} is a lightweight form of {@link Future}.
 * The efficiency of {@code ForkJoinTask}s stems from a set of
 * restrictions (that are only partially statically enforceable)
 * reflecting their intended use as computational tasks calculating
 * pure functions or operating on purely isolated objects.  The
 * primary coordination mechanisms are {@link #fork}, that arranges
 * asynchronous execution, and {@link #join}, that doesn't proceed
 * until the task's result has been computed.  Computations should
 * avoid {@code synchronized} methods or blocks, and should minimize
 * other blocking synchronization apart from joining other tasks or
 * using synchronizers such as Phasers that are advertised to
 * cooperate with fork/join scheduling. Tasks should also not perform
 * blocking IO, and should ideally access variables that are
 * completely independent of those accessed by other running
 * tasks. Minor breaches of these restrictions, for example using
 * shared output streams, may be tolerable in practice, but frequent
 * use may result in poor performance, and the potential to
 * indefinitely stall if the number of threads not waiting for IO or
 * other external synchronization becomes exhausted. This usage
 * restriction is in part enforced by not permitting checked
 * exceptions such as {@code IOExceptions} to be thrown. However,
 * computations may still encounter unchecked exceptions, that are
 * rethrown to callers attempting to join them. These exceptions may
 * additionally include {@link RejectedExecutionException} stemming
 * from internal resource exhaustion, such as failure to allocate
 * internal task queues.
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
 * <p>The execution status of tasks may be queried at several levels
 * of detail: {@link #isDone} is true if a task completed in any way
 * (including the case where a task was cancelled without executing);
 * {@link #isCompletedNormally} is true if a task completed without
 * cancellation or encountering an exception; {@link #isCancelled} is
 * true if the task was cancelled (in which case {@link #getException}
 * returns a {@link java.util.concurrent.CancellationException}); and
 * {@link #isCompletedAbnormally} is true if a task was either
 * cancelled or encountered an exception, in which case {@link
 * #getException} will return either the encountered exception or
 * {@link java.util.concurrent.CancellationException}.
 *
 * <p>The ForkJoinTask class is not usually directly subclassed.
 * Instead, you subclass one of the abstract classes that support a
 * particular style of fork/join processing, typically {@link
 * RecursiveAction} for computations that do not return results, or
 * {@link RecursiveTask} for those that do.  Normally, a concrete
 * ForkJoinTask subclass declares fields comprising its parameters,
 * established in a constructor, and then defines a {@code compute}
 * method that somehow uses the control methods supplied by this base
 * class. While these methods have {@code public} access (to allow
 * instances of different task subclasses to call each other's
 * methods), some of them may only be called from within other
 * ForkJoinTasks (as may be determined using method {@link
 * #inForkJoinPool}).  Attempts to invoke them in other contexts
 * result in exceptions or errors, possibly including
 * {@code ClassCastException}.
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
 * computational steps. If tasks are too big, then parallelism cannot
 * improve throughput. If too small, then memory and internal task
 * maintenance overhead may overwhelm processing.
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
 * @since 1.7
 * @author Doug Lea
 */
public abstract class ForkJoinTask<V> implements Future<V>, Serializable {

    /*
     * See the internal documentation of class ForkJoinPool for a
     * general implementation overview.  ForkJoinTasks are mainly
     * responsible for maintaining their "status" field amidst relays
     * to methods in ForkJoinWorkerThread and ForkJoinPool. The
     * methods of this class are more-or-less layered into (1) basic
     * status maintenance (2) execution and awaiting completion (3)
     * user-level methods that additionally report results. This is
     * sometimes hard to see because this file orders exported methods
     * in a way that flows well in javadocs. In particular, most
     * join mechanics are in method quietlyJoin, below.
     */

    /*
     * The status field holds run control status bits packed into a
     * single int to minimize footprint and to ensure atomicity (via
     * CAS).  Status is initially zero, and takes on nonnegative
     * values until completed, upon which status holds value
     * NORMAL, CANCELLED, or EXCEPTIONAL. Tasks undergoing blocking
     * waits by other threads have the SIGNAL bit set.  Completion of
     * a stolen task with SIGNAL set awakens any waiters via
     * notifyAll. Even though suboptimal for some purposes, we use
     * basic builtin wait/notify to take advantage of "monitor
     * inflation" in JVMs that we would otherwise need to emulate to
     * avoid adding further per-task bookkeeping overhead.  We want
     * these monitors to be "fat", i.e., not use biasing or thin-lock
     * techniques, so use some odd coding idioms that tend to avoid
     * them.
     */

    /** The run status of this task */
    volatile int status; // accessed directly by pool and workers

    private static final int NORMAL      = -1;
    private static final int CANCELLED   = -2;
    private static final int EXCEPTIONAL = -3;
    private static final int SIGNAL      =  1;

    /**
     * Table of exceptions thrown by tasks, to enable reporting by
     * callers. Because exceptions are rare, we don't directly keep
     * them with task objects, but instead use a weak ref table.  Note
     * that cancellation exceptions don't appear in the table, but are
     * instead recorded as status values.
     * TODO: Use ConcurrentReferenceHashMap
     */
    static final Map<ForkJoinTask<?>, Throwable> exceptionMap =
        Collections.synchronizedMap
        (new WeakHashMap<ForkJoinTask<?>, Throwable>());

    // Maintaining completion status

    /**
     * Marks completion and wakes up threads waiting to join this task,
     * also clearing signal request bits.
     *
     * @param completion one of NORMAL, CANCELLED, EXCEPTIONAL
     */
    private void setCompletion(int completion) {
        int s;
        while ((s = status) >= 0) {
            if (UNSAFE.compareAndSwapInt(this, statusOffset, s, completion)) {
                if (s != 0)
                    synchronized (this) { notifyAll(); }
                break;
            }
        }
    }

    /**
     * Records exception and sets exceptional completion.
     *
     * @return status on exit
     */
    private void setExceptionalCompletion(Throwable rex) {
        exceptionMap.put(this, rex);
        setCompletion(EXCEPTIONAL);
    }

    /**
     * Blocks a worker thread until completion. Called only by
     * pool. Currently unused -- pool-based waits use timeout
     * version below.
     */
    final void internalAwaitDone() {
        int s;         // the odd construction reduces lock bias effects
        while ((s = status) >= 0) {
            try {
                synchronized(this) {
                    if (UNSAFE.compareAndSwapInt(this, statusOffset, s,SIGNAL))
                        wait();
                }
            } catch (InterruptedException ie) {
                cancelIfTerminating();
            }
        }
    }

    /**
     * Blocks a worker thread until completed or timed out.  Called
     * only by pool.
     *
     * @return status on exit
     */
    final int internalAwaitDone(long millis) {
        int s;
        if ((s = status) >= 0) {
            try {
                synchronized(this) {
                    if (UNSAFE.compareAndSwapInt(this, statusOffset, s,SIGNAL))
                        wait(millis, 0);
                }
            } catch (InterruptedException ie) {
                cancelIfTerminating();
            }
            s = status;
        }
        return s;
    }

    /**
     * Blocks a non-worker-thread until completion.
     */
    private void externalAwaitDone() {
        int s;
        while ((s = status) >= 0) {
            synchronized(this) {
                if (UNSAFE.compareAndSwapInt(this, statusOffset, s, SIGNAL)){
                    boolean interrupted = false;
                    while (status >= 0) {
                        try {
                            wait();
                        } catch (InterruptedException ie) {
                            interrupted = true;
                        }
                    }
                    if (interrupted)
                        Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Unless done, calls exec and records status if completed, but
     * doesn't wait for completion otherwise. Primary execution method
     * for ForkJoinWorkerThread.
     */
    final void quietlyExec() {
        try {
            if (status < 0 || !exec())
                return;
        } catch (Throwable rex) {
            setExceptionalCompletion(rex);
            return;
        }
        setCompletion(NORMAL); // must be outside try block
    }

    // public methods

    /**
     * Arranges to asynchronously execute this task.  While it is not
     * necessarily enforced, it is a usage error to fork a task more
     * than once unless it has completed and been reinitialized.
     * Subsequent modifications to the state of this task or any data
     * it operates on are not necessarily consistently observable by
     * any thread other than the one executing it unless preceded by a
     * call to {@link #join} or related methods, or a call to {@link
     * #isDone} returning {@code true}.
     *
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @return {@code this}, to simplify usage
     */
    public final ForkJoinTask<V> fork() {
        ((ForkJoinWorkerThread) Thread.currentThread())
            .pushTask(this);
        return this;
    }

    /**
     * Returns the result of the computation when it {@link #isDone is done}.
     * This method differs from {@link #get()} in that
     * abnormal completion results in {@code RuntimeException} or
     * {@code Error}, not {@code ExecutionException}.
     *
     * @return the computed result
     */
    public final V join() {
        quietlyJoin();
        Throwable ex;
        if (status < NORMAL && (ex = getException()) != null)
            UNSAFE.throwException(ex);
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
        quietlyInvoke();
        Throwable ex;
        if (status < NORMAL && (ex = getException()) != null)
            UNSAFE.throwException(ex);
        return getRawResult();
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
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @param t1 the first task
     * @param t2 the second task
     * @throws NullPointerException if any task is null
     */
    public static void invokeAll(ForkJoinTask<?> t1, ForkJoinTask<?> t2) {
        t2.fork();
        t1.invoke();
        t2.join();
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
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @param tasks the tasks
     * @throws NullPointerException if any task is null
     */
    public static void invokeAll(ForkJoinTask<?>... tasks) {
        Throwable ex = null;
        int last = tasks.length - 1;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t = tasks[i];
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            }
            else if (i != 0)
                t.fork();
            else {
                t.quietlyInvoke();
                if (ex == null && t.status < NORMAL)
                    ex = t.getException();
            }
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = tasks[i];
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else {
                    t.quietlyJoin();
                    if (ex == null && t.status < NORMAL)
                        ex = t.getException();
                }
            }
        }
        if (ex != null)
            UNSAFE.throwException(ex);
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
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @param tasks the collection of tasks
     * @return the tasks argument, to simplify usage
     * @throws NullPointerException if tasks or any element are null
     */
    public static <T extends ForkJoinTask<?>> Collection<T> invokeAll(Collection<T> tasks) {
        if (!(tasks instanceof RandomAccess) || !(tasks instanceof List<?>)) {
            invokeAll(tasks.toArray(new ForkJoinTask<?>[tasks.size()]));
            return tasks;
        }
        @SuppressWarnings("unchecked")
        List<? extends ForkJoinTask<?>> ts =
            (List<? extends ForkJoinTask<?>>) tasks;
        Throwable ex = null;
        int last = ts.size() - 1;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t = ts.get(i);
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            }
            else if (i != 0)
                t.fork();
            else {
                t.quietlyInvoke();
                if (ex == null && t.status < NORMAL)
                    ex = t.getException();
            }
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = ts.get(i);
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else {
                    t.quietlyJoin();
                    if (ex == null && t.status < NORMAL)
                        ex = t.getException();
                }
            }
        }
        if (ex != null)
            UNSAFE.throwException(ex);
        return tasks;
    }

    /**
     * Attempts to cancel execution of this task. This attempt will
     * fail if the task has already completed, has already been
     * cancelled, or could not be cancelled for some other reason. If
     * successful, and this task has not started when cancel is
     * called, execution of this task is suppressed, {@link
     * #isCancelled} will report true, and {@link #join} will result
     * in a {@code CancellationException} being thrown.
     *
     * <p>This method may be overridden in subclasses, but if so, must
     * still ensure that these minimal properties hold. In particular,
     * the {@code cancel} method itself must not throw exceptions.
     *
     * <p>This method is designed to be invoked by <em>other</em>
     * tasks. To terminate the current task, you can just return or
     * throw an unchecked exception from its computation method, or
     * invoke {@link #completeExceptionally}.
     *
     * @param mayInterruptIfRunning this value is ignored in the
     * default implementation because tasks are not
     * cancelled via interruption
     *
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        setCompletion(CANCELLED);
        return status == CANCELLED;
    }

    /**
     * Cancels, ignoring any exceptions thrown by cancel. Used during
     * worker and pool shutdown. Cancel is spec'ed not to throw any
     * exceptions, but if it does anyway, we have no recourse during
     * shutdown, so guard against this case.
     */
    final void cancelIgnoringExceptions() {
        try {
            cancel(false);
        } catch (Throwable ignore) {
        }
    }

    /**
     * Cancels if current thread is a terminating worker thread,
     * ignoring any exceptions thrown by cancel.
     */
    final void cancelIfTerminating() {
        Thread t = Thread.currentThread();
        if ((t instanceof ForkJoinWorkerThread) &&
            ((ForkJoinWorkerThread) t).isTerminating()) {
            try {
                cancel(false);
            } catch (Throwable ignore) {
            }
        }
    }

    public final boolean isDone() {
        return status < 0;
    }

    public final boolean isCancelled() {
        return status == CANCELLED;
    }

    /**
     * Returns {@code true} if this task threw an exception or was cancelled.
     *
     * @return {@code true} if this task threw an exception or was cancelled
     */
    public final boolean isCompletedAbnormally() {
        return status < NORMAL;
    }

    /**
     * Returns {@code true} if this task completed without throwing an
     * exception and was not cancelled.
     *
     * @return {@code true} if this task completed without throwing an
     * exception and was not cancelled
     */
    public final boolean isCompletedNormally() {
        return status == NORMAL;
    }

    /**
     * Returns the exception thrown by the base computation, or a
     * {@code CancellationException} if cancelled, or {@code null} if
     * none or if the method has not yet completed.
     *
     * @return the exception, or {@code null} if none
     */
    public final Throwable getException() {
        int s = status;
        return ((s >= NORMAL)    ? null :
                (s == CANCELLED) ? new CancellationException() :
                exceptionMap.get(this));
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
        setExceptionalCompletion((ex instanceof RuntimeException) ||
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
            setExceptionalCompletion(rex);
            return;
        }
        setCompletion(NORMAL);
    }

    public final V get() throws InterruptedException, ExecutionException {
        quietlyJoin();
        if (Thread.interrupted())
            throw new InterruptedException();
        int s = status;
        if (s < NORMAL) {
            Throwable ex;
            if (s == CANCELLED)
                throw new CancellationException();
            if (s == EXCEPTIONAL && (ex = exceptionMap.get(this)) != null)
                throw new ExecutionException(ex);
        }
        return getRawResult();
    }

    public final V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        Thread t = Thread.currentThread();
        ForkJoinPool pool;
        if (t instanceof ForkJoinWorkerThread) {
            ForkJoinWorkerThread w = (ForkJoinWorkerThread) t;
            if (status >= 0 && w.unpushTask(this))
                quietlyExec();
            pool = w.pool;
        }
        else
            pool = null;
        /*
         * Timed wait loop intermixes cases for FJ (pool != null) and
         * non FJ threads. For FJ, decrement pool count but don't try
         * for replacement; increment count on completion. For non-FJ,
         * deal with interrupts. This is messy, but a little less so
         * than is splitting the FJ and nonFJ cases.
         */
        boolean interrupted = false;
        boolean dec = false; // true if pool count decremented
        long nanos = unit.toNanos(timeout);
        for (;;) {
            if (pool == null && Thread.interrupted()) {
                interrupted = true;
                break;
            }
            int s = status;
            if (s < 0)
                break;
            if (UNSAFE.compareAndSwapInt(this, statusOffset, s, SIGNAL)) {
                long startTime = System.nanoTime();
                long nt; // wait time
                while (status >= 0 &&
                       (nt = nanos - (System.nanoTime() - startTime)) > 0) {
                    if (pool != null && !dec)
                        dec = pool.tryDecrementRunningCount();
                    else {
                        long ms = nt / 1000000;
                        int ns = (int) (nt % 1000000);
                        try {
                            synchronized(this) {
                                if (status >= 0)
                                    wait(ms, ns);
                            }
                        } catch (InterruptedException ie) {
                            if (pool != null)
                                cancelIfTerminating();
                            else {
                                interrupted = true;
                                break;
                            }
                        }
                    }
                }
                break;
            }
        }
        if (pool != null && dec)
            pool.incrementRunningCount();
        if (interrupted)
            throw new InterruptedException();
        int es = status;
        if (es != NORMAL) {
            Throwable ex;
            if (es == CANCELLED)
                throw new CancellationException();
            if (es == EXCEPTIONAL && (ex = exceptionMap.get(this)) != null)
                throw new ExecutionException(ex);
            throw new TimeoutException();
        }
        return getRawResult();
    }

    /**
     * Joins this task, without returning its result or throwing its
     * exception. This method may be useful when processing
     * collections of tasks when some have been cancelled or otherwise
     * known to have aborted.
     */
    public final void quietlyJoin() {
        Thread t;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            ForkJoinWorkerThread w = (ForkJoinWorkerThread) t;
            if (status >= 0) {
                if (w.unpushTask(this)) {
                    boolean completed;
                    try {
                        completed = exec();
                    } catch (Throwable rex) {
                        setExceptionalCompletion(rex);
                        return;
                    }
                    if (completed) {
                        setCompletion(NORMAL);
                        return;
                    }
                }
                w.joinTask(this);
            }
        }
        else
            externalAwaitDone();
    }

    /**
     * Commences performing this task and awaits its completion if
     * necessary, without returning its result or throwing its
     * exception.
     */
    public final void quietlyInvoke() {
        if (status >= 0) {
            boolean completed;
            try {
                completed = exec();
            } catch (Throwable rex) {
                setExceptionalCompletion(rex);
                return;
            }
            if (completed)
                setCompletion(NORMAL);
            else
                quietlyJoin();
        }
    }

    /**
     * Possibly executes tasks until the pool hosting the current task
     * {@link ForkJoinPool#isQuiescent is quiescent}. This method may
     * be of use in designs in which many tasks are forked, but none
     * are explicitly joined, instead executing them until all are
     * processed.
     *
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     */
    public static void helpQuiesce() {
        ((ForkJoinWorkerThread) Thread.currentThread())
            .helpQuiescePool();
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
     */
    public void reinitialize() {
        if (status == EXCEPTIONAL)
            exceptionMap.remove(this);
        status = 0;
    }

    /**
     * Returns the pool hosting the current task execution, or null
     * if this task is executing outside of any ForkJoinPool.
     *
     * @see #inForkJoinPool
     * @return the pool, or {@code null} if none
     */
    public static ForkJoinPool getPool() {
        Thread t = Thread.currentThread();
        return (t instanceof ForkJoinWorkerThread) ?
            ((ForkJoinWorkerThread) t).pool : null;
    }

    /**
     * Returns {@code true} if the current thread is executing as a
     * ForkJoinPool computation.
     *
     * @return {@code true} if the current thread is executing as a
     * ForkJoinPool computation, or false otherwise
     */
    public static boolean inForkJoinPool() {
        return Thread.currentThread() instanceof ForkJoinWorkerThread;
    }

    /**
     * Tries to unschedule this task for execution. This method will
     * typically succeed if this task is the most recently forked task
     * by the current thread, and has not commenced executing in
     * another thread.  This method may be useful when arranging
     * alternative local processing of tasks that could have been, but
     * were not, stolen.
     *
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @return {@code true} if unforked
     */
    public boolean tryUnfork() {
        return ((ForkJoinWorkerThread) Thread.currentThread())
            .unpushTask(this);
    }

    /**
     * Returns an estimate of the number of tasks that have been
     * forked by the current worker thread but not yet executed. This
     * value may be useful for heuristic decisions about whether to
     * fork other tasks.
     *
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @return the number of tasks
     */
    public static int getQueuedTaskCount() {
        return ((ForkJoinWorkerThread) Thread.currentThread())
            .getQueueSize();
    }

    /**
     * Returns an estimate of how many more locally queued tasks are
     * held by the current worker thread than there are other worker
     * threads that might steal them.  This value may be useful for
     * heuristic decisions about whether to fork other tasks. In many
     * usages of ForkJoinTasks, at steady state, each worker should
     * aim to maintain a small constant surplus (for example, 3) of
     * tasks, and to process computations locally if this threshold is
     * exceeded.
     *
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @return the surplus number of tasks, which may be negative
     */
    public static int getSurplusQueuedTaskCount() {
        return ((ForkJoinWorkerThread) Thread.currentThread())
            .getEstimatedSurplusTaskCount();
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
     * Immediately performs the base action of this task.  This method
     * is designed to support extensions, and should not in general be
     * called otherwise. The return value controls whether this task
     * is considered to be done normally. It may return false in
     * asynchronous actions that require explicit invocations of
     * {@link #complete} to become joinable. It may also throw an
     * (unchecked) exception to indicate abnormal exit.
     *
     * @return {@code true} if completed normally
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
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> peekNextLocalTask() {
        return ((ForkJoinWorkerThread) Thread.currentThread())
            .peekTask();
    }

    /**
     * Unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed.  This method
     * is designed primarily to support extensions, and is unlikely to
     * be useful otherwise.
     *
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> pollNextLocalTask() {
        return ((ForkJoinWorkerThread) Thread.currentThread())
            .pollLocalTask();
    }

    /**
     * Unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed, if one is
     * available, or if not available, a task that was forked by some
     * other thread, if available. Availability may be transient, so a
     * {@code null} result does not necessarily imply quiescence
     * of the pool this task is operating in.  This method is designed
     * primarily to support extensions, and is unlikely to be useful
     * otherwise.
     *
     * <p>This method may be invoked only from within {@code
     * ForkJoinTask} computations (as may be determined using method
     * {@link #inForkJoinPool}).  Attempts to invoke in other contexts
     * result in exceptions or errors, possibly including {@code
     * ClassCastException}.
     *
     * @return a task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> pollTask() {
        return ((ForkJoinWorkerThread) Thread.currentThread())
            .pollTask();
    }

    /**
     * Adaptor for Runnables. This implements RunnableFuture
     * to be compliant with AbstractExecutorService constraints
     * when used in ForkJoinPool.
     */
    static final class AdaptedRunnable<T> extends ForkJoinTask<T>
        implements RunnableFuture<T> {
        final Runnable runnable;
        final T resultOnCompletion;
        T result;
        AdaptedRunnable(Runnable runnable, T result) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
            this.resultOnCompletion = result;
        }
        public T getRawResult() { return result; }
        public void setRawResult(T v) { result = v; }
        public boolean exec() {
            runnable.run();
            result = resultOnCompletion;
            return true;
        }
        public void run() { invoke(); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adaptor for Callables
     */
    static final class AdaptedCallable<T> extends ForkJoinTask<T>
        implements RunnableFuture<T> {
        final Callable<? extends T> callable;
        T result;
        AdaptedCallable(Callable<? extends T> callable) {
            if (callable == null) throw new NullPointerException();
            this.callable = callable;
        }
        public T getRawResult() { return result; }
        public void setRawResult(T v) { result = v; }
        public boolean exec() {
            try {
                result = callable.call();
                return true;
            } catch (Error err) {
                throw err;
            } catch (RuntimeException rex) {
                throw rex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        public void run() { invoke(); }
        private static final long serialVersionUID = 2838392045355241008L;
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * a null result upon {@link #join}.
     *
     * @param runnable the runnable action
     * @return the task
     */
    public static ForkJoinTask<?> adapt(Runnable runnable) {
        return new AdaptedRunnable<Void>(runnable, null);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * the given result upon {@link #join}.
     *
     * @param runnable the runnable action
     * @param result the result upon completion
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
     * @return the task
     */
    public static <T> ForkJoinTask<T> adapt(Callable<? extends T> callable) {
        return new AdaptedCallable<T>(callable);
    }

    // Serialization support

    private static final long serialVersionUID = -7721805057305804111L;

    /**
     * Saves the state to a stream (that is, serializes it).
     *
     * @serialData the current run status and the exception thrown
     * during execution, or {@code null} if none
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        s.defaultWriteObject();
        s.writeObject(getException());
    }

    /**
     * Reconstitutes the instance from a stream (that is, deserializes it).
     *
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        Object ex = s.readObject();
        if (ex != null)
            setExceptionalCompletion((Throwable) ex);
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE = sun.misc.Unsafe.getUnsafe();
    private static final long statusOffset =
        objectFieldOffset("status", ForkJoinTask.class);

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
