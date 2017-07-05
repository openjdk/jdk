/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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
import java.util.concurrent.locks.*;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the <tt>get</tt>
 * method will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled.
 *
 * <p>A <tt>FutureTask</tt> can be used to wrap a {@link Callable} or
 * {@link java.lang.Runnable} object.  Because <tt>FutureTask</tt>
 * implements <tt>Runnable</tt>, a <tt>FutureTask</tt> can be
 * submitted to an {@link Executor} for execution.
 *
 * <p>In addition to serving as a standalone class, this class provides
 * <tt>protected</tt> functionality that may be useful when creating
 * customized task classes.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this FutureTask's <tt>get</tt> method
 */
public class FutureTask<V> implements RunnableFuture<V> {
    /** Synchronization control for FutureTask */
    private final Sync sync;

    /**
     * Creates a <tt>FutureTask</tt> that will, upon running, execute the
     * given <tt>Callable</tt>.
     *
     * @param  callable the callable task
     * @throws NullPointerException if callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        sync = new Sync(callable);
    }

    /**
     * Creates a <tt>FutureTask</tt> that will, upon running, execute the
     * given <tt>Runnable</tt>, and arrange that <tt>get</tt> will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * <tt>Future&lt;?&gt; f = new FutureTask&lt;Object&gt;(runnable, null)</tt>
     * @throws NullPointerException if runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        sync = new Sync(Executors.callable(runnable, result));
    }

    public boolean isCancelled() {
        return sync.innerIsCancelled();
    }

    public boolean isDone() {
        return sync.innerIsDone();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return sync.innerCancel(mayInterruptIfRunning);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {
        return sync.innerGet();
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return sync.innerGet(unit.toNanos(timeout));
    }

    /**
     * Protected method invoked when this task transitions to state
     * <tt>isDone</tt> (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }

    /**
     * Sets the result of this Future to the given value unless
     * this future has already been set or has been cancelled.
     * This method is invoked internally by the <tt>run</tt> method
     * upon successful completion of the computation.
     * @param v the value
     */
    protected void set(V v) {
        sync.innerSet(v);
    }

    /**
     * Causes this future to report an <tt>ExecutionException</tt>
     * with the given throwable as its cause, unless this Future has
     * already been set or has been cancelled.
     * This method is invoked internally by the <tt>run</tt> method
     * upon failure of the computation.
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        sync.innerSetException(t);
    }

    // The following (duplicated) doc comment can be removed once
    //
    // 6270645: Javadoc comments should be inherited from most derived
    //          superinterface or superclass
    // is fixed.
    /**
     * Sets this Future to the result of its computation
     * unless it has been cancelled.
     */
    public void run() {
        sync.innerRun();
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this Future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     * @return true if successfully run and reset
     */
    protected boolean runAndReset() {
        return sync.innerRunAndReset();
    }

    /**
     * Synchronization control for FutureTask. Note that this must be
     * a non-static inner class in order to invoke the protected
     * <tt>done</tt> method. For clarity, all inner class support
     * methods are same as outer, prefixed with "inner".
     *
     * Uses AQS sync state to represent run status
     */
    private final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -7828117401763700385L;

        /** State value representing that task is ready to run */
        private static final int READY     = 0;
        /** State value representing that task is running */
        private static final int RUNNING   = 1;
        /** State value representing that task ran */
        private static final int RAN       = 2;
        /** State value representing that task was cancelled */
        private static final int CANCELLED = 4;

        /** The underlying callable */
        private final Callable<V> callable;
        /** The result to return from get() */
        private V result;
        /** The exception to throw from get() */
        private Throwable exception;

        /**
         * The thread running task. When nulled after set/cancel, this
         * indicates that the results are accessible.  Must be
         * volatile, to ensure visibility upon completion.
         */
        private volatile Thread runner;

        Sync(Callable<V> callable) {
            this.callable = callable;
        }

        private boolean ranOrCancelled(int state) {
            return (state & (RAN | CANCELLED)) != 0;
        }

        /**
         * Implements AQS base acquire to succeed if ran or cancelled
         */
        protected int tryAcquireShared(int ignore) {
            return innerIsDone() ? 1 : -1;
        }

        /**
         * Implements AQS base release to always signal after setting
         * final done status by nulling runner thread.
         */
        protected boolean tryReleaseShared(int ignore) {
            runner = null;
            return true;
        }

        boolean innerIsCancelled() {
            return getState() == CANCELLED;
        }

        boolean innerIsDone() {
            return ranOrCancelled(getState()) && runner == null;
        }

        V innerGet() throws InterruptedException, ExecutionException {
            acquireSharedInterruptibly(0);
            if (getState() == CANCELLED)
                throw new CancellationException();
            if (exception != null)
                throw new ExecutionException(exception);
            return result;
        }

        V innerGet(long nanosTimeout) throws InterruptedException, ExecutionException, TimeoutException {
            if (!tryAcquireSharedNanos(0, nanosTimeout))
                throw new TimeoutException();
            if (getState() == CANCELLED)
                throw new CancellationException();
            if (exception != null)
                throw new ExecutionException(exception);
            return result;
        }

        void innerSet(V v) {
            for (;;) {
                int s = getState();
                if (s == RAN)
                    return;
                if (s == CANCELLED) {
                    // aggressively release to set runner to null,
                    // in case we are racing with a cancel request
                    // that will try to interrupt runner
                    releaseShared(0);
                    return;
                }
                if (compareAndSetState(s, RAN)) {
                    result = v;
                    releaseShared(0);
                    done();
                    return;
                }
            }
        }

        void innerSetException(Throwable t) {
            for (;;) {
                int s = getState();
                if (s == RAN)
                    return;
                if (s == CANCELLED) {
                    // aggressively release to set runner to null,
                    // in case we are racing with a cancel request
                    // that will try to interrupt runner
                    releaseShared(0);
                    return;
                }
                if (compareAndSetState(s, RAN)) {
                    exception = t;
                    releaseShared(0);
                    done();
                    return;
                }
            }
        }

        boolean innerCancel(boolean mayInterruptIfRunning) {
            for (;;) {
                int s = getState();
                if (ranOrCancelled(s))
                    return false;
                if (compareAndSetState(s, CANCELLED))
                    break;
            }
            if (mayInterruptIfRunning) {
                Thread r = runner;
                if (r != null)
                    r.interrupt();
            }
            releaseShared(0);
            done();
            return true;
        }

        void innerRun() {
            if (!compareAndSetState(READY, RUNNING))
                return;

            runner = Thread.currentThread();
            if (getState() == RUNNING) { // recheck after setting thread
                V result;
                try {
                    result = callable.call();
                } catch (Throwable ex) {
                    setException(ex);
                    return;
                }
                set(result);
            } else {
                releaseShared(0); // cancel
            }
        }

        boolean innerRunAndReset() {
            if (!compareAndSetState(READY, RUNNING))
                return false;
            try {
                runner = Thread.currentThread();
                if (getState() == RUNNING)
                    callable.call(); // don't set result
                runner = null;
                return compareAndSetState(RUNNING, READY);
            } catch (Throwable ex) {
                setException(ex);
                return false;
            }
        }
    }
}
