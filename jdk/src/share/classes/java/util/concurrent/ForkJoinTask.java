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
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.reflect.Constructor;

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
 * #invokeAll(ForkJoinTask...) invokeAll}.  However, this class also
 * provides a number of other methods that can come into play in
 * advanced usages, as well as extension mechanics that allow
 * support of new forms of fork/join processing.
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
 * internal task queues. Rethrown exceptions behave in the same way as
 * regular exceptions, but, when possible, contain stack traces (as
 * displayed for example using {@code ex.printStackTrace()}) of both
 * the thread that initiated the computation as well as the thread
 * actually encountering the exception; minimally only the latter.
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
 * <p>Method {@link #join} and its variants are appropriate for use
 * only when completion dependencies are acyclic; that is, the
 * parallel computation can be described as a directed acyclic graph
 * (DAG). Otherwise, executions may encounter a form of deadlock as
 * tasks cyclically wait for each other.  However, this framework
 * supports other methods and techniques (for example the use of
 * {@link Phaser}, {@link #helpQuiesce}, and {@link #complete}) that
 * may be of use in constructing custom subclasses for problems that
 * are not statically structured as DAGs.
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
     * in a way that flows well in javadocs.
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
     * Marks completion and wakes up threads waiting to join this task,
     * also clearing signal request bits.
     *
     * @param completion one of NORMAL, CANCELLED, EXCEPTIONAL
     * @return completion status on exit
     */
    private int setCompletion(int completion) {
        for (int s;;) {
            if ((s = status) < 0)
                return s;
            if (UNSAFE.compareAndSwapInt(this, statusOffset, s, completion)) {
                if (s != 0)
                    synchronized (this) { notifyAll(); }
                return completion;
            }
        }
    }

    /**
     * Tries to block a worker thread until completed or timed out.
     * Uses Object.wait time argument conventions.
     * May fail on contention or interrupt.
     *
     * @param millis if > 0, wait time.
     */
    final void tryAwaitDone(long millis) {
        int s;
        try {
            if (((s = status) > 0 ||
                 (s == 0 &&
                  UNSAFE.compareAndSwapInt(this, statusOffset, 0, SIGNAL))) &&
                status > 0) {
                synchronized (this) {
                    if (status > 0)
                        wait(millis);
                }
            }
        } catch (InterruptedException ie) {
            // caller must check termination
        }
    }

    /**
     * Blocks a non-worker-thread until completion.
     * @return status upon completion
     */
    private int externalAwaitDone() {
        int s;
        if ((s = status) >= 0) {
            boolean interrupted = false;
            synchronized (this) {
                while ((s = status) >= 0) {
                    if (s == 0)
                        UNSAFE.compareAndSwapInt(this, statusOffset,
                                                 0, SIGNAL);
                    else {
                        try {
                            wait();
                        } catch (InterruptedException ie) {
                            interrupted = true;
                        }
                    }
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
        return s;
    }

    /**
     * Blocks a non-worker-thread until completion or interruption or timeout.
     */
    private int externalInterruptibleAwaitDone(long millis)
        throws InterruptedException {
        int s;
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((s = status) >= 0) {
            synchronized (this) {
                while ((s = status) >= 0) {
                    if (s == 0)
                        UNSAFE.compareAndSwapInt(this, statusOffset,
                                                 0, SIGNAL);
                    else {
                        wait(millis);
                        if (millis > 0L)
                            break;
                    }
                }
            }
        }
        return s;
    }

    /**
     * Primary execution method for stolen tasks. Unless done, calls
     * exec and records status if completed, but doesn't wait for
     * completion otherwise.
     */
    final void doExec() {
        if (status >= 0) {
            boolean completed;
            try {
                completed = exec();
            } catch (Throwable rex) {
                setExceptionalCompletion(rex);
                return;
            }
            if (completed)
                setCompletion(NORMAL); // must be outside try block
        }
    }

    /**
     * Primary mechanics for join, get, quietlyJoin.
     * @return status upon completion
     */
    private int doJoin() {
        Thread t; ForkJoinWorkerThread w; int s; boolean completed;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            if ((s = status) < 0)
                return s;
            if ((w = (ForkJoinWorkerThread)t).unpushTask(this)) {
                try {
                    completed = exec();
                } catch (Throwable rex) {
                    return setExceptionalCompletion(rex);
                }
                if (completed)
                    return setCompletion(NORMAL);
            }
            return w.joinTask(this);
        }
        else
            return externalAwaitDone();
    }

    /**
     * Primary mechanics for invoke, quietlyInvoke.
     * @return status upon completion
     */
    private int doInvoke() {
        int s; boolean completed;
        if ((s = status) < 0)
            return s;
        try {
            completed = exec();
        } catch (Throwable rex) {
            return setExceptionalCompletion(rex);
        }
        if (completed)
            return setCompletion(NORMAL);
        else
            return doJoin();
    }

    // Exception table support

    /**
     * Table of exceptions thrown by tasks, to enable reporting by
     * callers. Because exceptions are rare, we don't directly keep
     * them with task objects, but instead use a weak ref table.  Note
     * that cancellation exceptions don't appear in the table, but are
     * instead recorded as status values.
     *
     * Note: These statics are initialized below in static block.
     */
    private static final ExceptionNode[] exceptionTable;
    private static final ReentrantLock exceptionTableLock;
    private static final ReferenceQueue<Object> exceptionTableRefQueue;

    /**
     * Fixed capacity for exceptionTable.
     */
    private static final int EXCEPTION_MAP_CAPACITY = 32;

    /**
     * Key-value nodes for exception table.  The chained hash table
     * uses identity comparisons, full locking, and weak references
     * for keys. The table has a fixed capacity because it only
     * maintains task exceptions long enough for joiners to access
     * them, so should never become very large for sustained
     * periods. However, since we do not know when the last joiner
     * completes, we must use weak references and expunge them. We do
     * so on each operation (hence full locking). Also, some thread in
     * any ForkJoinPool will call helpExpungeStaleExceptions when its
     * pool becomes isQuiescent.
     */
    static final class ExceptionNode extends WeakReference<ForkJoinTask<?>>{
        final Throwable ex;
        ExceptionNode next;
        final long thrower;  // use id not ref to avoid weak cycles
        ExceptionNode(ForkJoinTask<?> task, Throwable ex, ExceptionNode next) {
            super(task, exceptionTableRefQueue);
            this.ex = ex;
            this.next = next;
            this.thrower = Thread.currentThread().getId();
        }
    }

    /**
     * Records exception and sets exceptional completion.
     *
     * @return status on exit
     */
    private int setExceptionalCompletion(Throwable ex) {
        int h = System.identityHashCode(this);
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            expungeStaleExceptions();
            ExceptionNode[] t = exceptionTable;
            int i = h & (t.length - 1);
            for (ExceptionNode e = t[i]; ; e = e.next) {
                if (e == null) {
                    t[i] = new ExceptionNode(this, ex, t[i]);
                    break;
                }
                if (e.get() == this) // already present
                    break;
            }
        } finally {
            lock.unlock();
        }
        return setCompletion(EXCEPTIONAL);
    }

    /**
     * Removes exception node and clears status
     */
    private void clearExceptionalCompletion() {
        int h = System.identityHashCode(this);
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            ExceptionNode[] t = exceptionTable;
            int i = h & (t.length - 1);
            ExceptionNode e = t[i];
            ExceptionNode pred = null;
            while (e != null) {
                ExceptionNode next = e.next;
                if (e.get() == this) {
                    if (pred == null)
                        t[i] = next;
                    else
                        pred.next = next;
                    break;
                }
                pred = e;
                e = next;
            }
            expungeStaleExceptions();
            status = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a rethrowable exception for the given task, if
     * available. To provide accurate stack traces, if the exception
     * was not thrown by the current thread, we try to create a new
     * exception of the same type as the one thrown, but with the
     * recorded exception as its cause. If there is no such
     * constructor, we instead try to use a no-arg constructor,
     * followed by initCause, to the same effect. If none of these
     * apply, or any fail due to other exceptions, we return the
     * recorded exception, which is still correct, although it may
     * contain a misleading stack trace.
     *
     * @return the exception, or null if none
     */
    private Throwable getThrowableException() {
        if (status != EXCEPTIONAL)
            return null;
        int h = System.identityHashCode(this);
        ExceptionNode e;
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            expungeStaleExceptions();
            ExceptionNode[] t = exceptionTable;
            e = t[h & (t.length - 1)];
            while (e != null && e.get() != this)
                e = e.next;
        } finally {
            lock.unlock();
        }
        Throwable ex;
        if (e == null || (ex = e.ex) == null)
            return null;
        if (e.thrower != Thread.currentThread().getId()) {
            Class ec = ex.getClass();
            try {
                Constructor<?> noArgCtor = null;
                Constructor<?>[] cs = ec.getConstructors();// public ctors only
                for (int i = 0; i < cs.length; ++i) {
                    Constructor<?> c = cs[i];
                    Class<?>[] ps = c.getParameterTypes();
                    if (ps.length == 0)
                        noArgCtor = c;
                    else if (ps.length == 1 && ps[0] == Throwable.class)
                        return (Throwable)(c.newInstance(ex));
                }
                if (noArgCtor != null) {
                    Throwable wx = (Throwable)(noArgCtor.newInstance());
                    wx.initCause(ex);
                    return wx;
                }
            } catch (Exception ignore) {
            }
        }
        return ex;
    }

    /**
     * Poll stale refs and remove them. Call only while holding lock.
     */
    private static void expungeStaleExceptions() {
        for (Object x; (x = exceptionTableRefQueue.poll()) != null;) {
            if (x instanceof ExceptionNode) {
                ForkJoinTask<?> key = ((ExceptionNode)x).get();
                ExceptionNode[] t = exceptionTable;
                int i = System.identityHashCode(key) & (t.length - 1);
                ExceptionNode e = t[i];
                ExceptionNode pred = null;
                while (e != null) {
                    ExceptionNode next = e.next;
                    if (e == x) {
                        if (pred == null)
                            t[i] = next;
                        else
                            pred.next = next;
                        break;
                    }
                    pred = e;
                    e = next;
                }
            }
        }
    }

    /**
     * If lock is available, poll stale refs and remove them.
     * Called from ForkJoinPool when pools become quiescent.
     */
    static final void helpExpungeStaleExceptions() {
        final ReentrantLock lock = exceptionTableLock;
        if (lock.tryLock()) {
            try {
                expungeStaleExceptions();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Report the result of invoke or join; called only upon
     * non-normal return of internal versions.
     */
    private V reportResult() {
        int s; Throwable ex;
        if ((s = status) == CANCELLED)
            throw new CancellationException();
        if (s == EXCEPTIONAL && (ex = getThrowableException()) != null)
            UNSAFE.throwException(ex);
        return getRawResult();
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
     * ForkJoinPool} computations (as may be determined using method
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
     * Returns the result of the computation when it {@link #isDone is
     * done}.  This method differs from {@link #get()} in that
     * abnormal completion results in {@code RuntimeException} or
     * {@code Error}, not {@code ExecutionException}, and that
     * interrupts of the calling thread do <em>not</em> cause the
     * method to abruptly return by throwing {@code
     * InterruptedException}.
     *
     * @return the computed result
     */
    public final V join() {
        if (doJoin() != NORMAL)
            return reportResult();
        else
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
        if (doInvoke() != NORMAL)
            return reportResult();
        else
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
     * ForkJoinPool} computations (as may be determined using method
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
     * ForkJoinPool} computations (as may be determined using method
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
            else if (t.doInvoke() < NORMAL && ex == null)
                ex = t.getException();
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = tasks[i];
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else if (t.doJoin() < NORMAL && ex == null)
                    ex = t.getException();
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
     * ForkJoinPool} computations (as may be determined using method
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
            else if (t.doInvoke() < NORMAL && ex == null)
                ex = t.getException();
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = ts.get(i);
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else if (t.doJoin() < NORMAL && ex == null)
                    ex = t.getException();
            }
        }
        if (ex != null)
            UNSAFE.throwException(ex);
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
     * invoke {@link #completeExceptionally}.
     *
     * @param mayInterruptIfRunning this value has no effect in the
     * default implementation because interrupts are not used to
     * control cancellation.
     *
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return setCompletion(CANCELLED) == CANCELLED;
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
                getThrowableException());
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
        int s = (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            doJoin() : externalInterruptibleAwaitDone(0L);
        Throwable ex;
        if (s == CANCELLED)
            throw new CancellationException();
        if (s == EXCEPTIONAL && (ex = getThrowableException()) != null)
            throw new ExecutionException(ex);
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
        Thread t = Thread.currentThread();
        if (t instanceof ForkJoinWorkerThread) {
            ForkJoinWorkerThread w = (ForkJoinWorkerThread) t;
            long nanos = unit.toNanos(timeout);
            if (status >= 0) {
                boolean completed = false;
                if (w.unpushTask(this)) {
                    try {
                        completed = exec();
                    } catch (Throwable rex) {
                        setExceptionalCompletion(rex);
                    }
                }
                if (completed)
                    setCompletion(NORMAL);
                else if (status >= 0 && nanos > 0)
                    w.pool.timedAwaitJoin(this, nanos);
            }
        }
        else {
            long millis = unit.toMillis(timeout);
            if (millis > 0)
                externalInterruptibleAwaitDone(millis);
        }
        int s = status;
        if (s != NORMAL) {
            Throwable ex;
            if (s == CANCELLED)
                throw new CancellationException();
            if (s != EXCEPTIONAL)
                throw new TimeoutException();
            if ((ex = getThrowableException()) != null)
                throw new ExecutionException(ex);
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
        doJoin();
    }

    /**
     * Commences performing this task and awaits its completion if
     * necessary, without returning its result or throwing its
     * exception.
     */
    public final void quietlyInvoke() {
        doInvoke();
    }

    /**
     * Possibly executes tasks until the pool hosting the current task
     * {@link ForkJoinPool#isQuiescent is quiescent}. This method may
     * be of use in designs in which many tasks are forked, but none
     * are explicitly joined, instead executing them until all are
     * processed.
     *
     * <p>This method may be invoked only from within {@code
     * ForkJoinPool} computations (as may be determined using method
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
     *
     * <p>Upon completion of this method, {@code isDone()} reports
     * {@code false}, and {@code getException()} reports {@code
     * null}. However, the value returned by {@code getRawResult} is
     * unaffected. To clear this value, you can invoke {@code
     * setRawResult(null)}.
     */
    public void reinitialize() {
        if (status == EXCEPTIONAL)
            clearExceptionalCompletion();
        else
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
     * typically succeed if this task is the most recently forked task
     * by the current thread, and has not commenced executing in
     * another thread.  This method may be useful when arranging
     * alternative local processing of tasks that could have been, but
     * were not, stolen.
     *
     * <p>This method may be invoked only from within {@code
     * ForkJoinPool} computations (as may be determined using method
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
     * ForkJoinPool} computations (as may be determined using method
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
     * ForkJoinPool} computations (as may be determined using method
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
     * ForkJoinPool} computations (as may be determined using method
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
     * ForkJoinPool} computations (as may be determined using method
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
     * ForkJoinPool} computations (as may be determined using method
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
            setExceptionalCompletion((Throwable)ex);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long statusOffset;
    static {
        exceptionTableLock = new ReentrantLock();
        exceptionTableRefQueue = new ReferenceQueue<Object>();
        exceptionTable = new ExceptionNode[EXCEPTION_MAP_CAPACITY];
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            statusOffset = UNSAFE.objectFieldOffset
                (ForkJoinTask.class.getDeclaredField("status"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
