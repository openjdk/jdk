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

/**
 * A {@code Future} represents the result of an asynchronous
 * computation.  Methods are provided to check if the computation is
 * complete, to wait for its completion, and to retrieve the result of
 * the computation.  The result can only be retrieved using method
 * {@code get} when the computation has completed, blocking if
 * necessary until it is ready.  Cancellation is performed by the
 * {@code cancel} method.  Additional methods are provided to
 * determine if the task completed normally or was cancelled. Once a
 * computation has completed, the computation cannot be cancelled.
 * If you would like to use a {@code Future} for the sake
 * of cancellability but not provide a usable result, you can
 * declare types of the form {@code Future<?>} and
 * return {@code null} as a result of the underlying task.
 *
 * <p>Cancellation of a Future need not abruptly terminate its
 * computation. Method {@code cancel} causes {@code isCancelled()} to
 * return {@code true} unless already {@code isDone()}; in either case
 * {@code isDone()} subsequently reports {@code true}. This suppresses
 * execution by an {@link ExecutorService} if not already started.
 * There are several options for suppressing unnecessary computation
 * or unblocking a running Future that will not generate a
 * result. When task bodies are simple and short, no special attention
 * is warranted.  Computational methods in Future-aware code bodies
 * (for example {@link ForkJoinTask}, {@link FutureTask}) may inspect
 * their own {@code isDone()} status before or while engaging in
 * expensive computations. In blocking I/O or communication contexts,
 * the optional {@code mayInterruptIfRunning} argument of {@code
 * cancel} may be used to support conventions that tasks should
 * unblock and exit when {@link Thread#interrupted}, whether checked
 * inside a task body or as a response to an {@link
 * InterruptedException}.  It is still preferable to additionally
 * check {@code isDone()} status when possible to avoid unintended
 * effects of other uses of {@link Thread#interrupt}.
 *
 * <p><b>Sample Usage</b> (Note that the following classes are all
 * made-up.)
 *
 * <pre> {@code
 * interface ArchiveSearcher { String search(String target); }
 * class App {
 *   ExecutorService executor = ...;
 *   ArchiveSearcher searcher = ...;
 *   void showSearch(String target) throws InterruptedException {
 *     Callable<String> task = () -> searcher.search(target);
 *     Future<String> future = executor.submit(task);
 *     displayOtherThings(); // do other things while searching
 *     try {
 *       displayText(future.get()); // use future
 *     } catch (ExecutionException ex) { cleanup(); return; }
 *   }
 * }}</pre>
 *
 * The {@link FutureTask} class is an implementation of {@code Future} that
 * implements {@code Runnable}, and so may be executed by an {@code Executor}.
 * For example, the above construction with {@code submit} could be replaced by:
 * <pre> {@code
 * FutureTask<String> future = new FutureTask<>(task);
 * executor.execute(future);}</pre>
 *
 * <p>Memory consistency effects: Actions taken by the asynchronous computation
 * <a href="package-summary.html#MemoryVisibility"> <i>happen-before</i></a>
 * actions following the corresponding {@code Future.get()} in another thread.
 *
 * @see FutureTask
 * @see Executor
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this Future's {@code get} method
 */
public interface Future<V> {

    /**
     * Attempts to cancel execution of this task.  This method has no
     * effect if the task is already completed or cancelled, or could
     * not be cancelled for some other reason.  Otherwise, if this
     * task has not started when {@code cancel} is called, this task
     * should never run.  If the task has already started, then the
     * {@code mayInterruptIfRunning} parameter determines whether the
     * thread executing this task (when known by the implementation)
     * is interrupted in an attempt to stop the task.
     *
     * <p>The return value from this method does not necessarily
     * indicate whether the task is now cancelled; use {@link
     * #isCancelled}.
     *
     * @param mayInterruptIfRunning {@code true} if the thread
     * executing this task should be interrupted (if the thread is
     * known to the implementation); otherwise, in-progress tasks are
     * allowed to complete
     * @return {@code false} if the task could not be cancelled,
     * typically because it has already completed; {@code true}
     * otherwise. If two or more threads cause a task to be cancelled,
     * then at least one of them returns {@code true}. Implementations
     * may provide stronger guarantees.
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Returns {@code true} if this task was cancelled before it completed
     * normally.
     *
     * @return {@code true} if this task was cancelled before it completed
     */
    boolean isCancelled();

    /**
     * Returns {@code true} if this task completed.
     *
     * Completion may be due to normal termination, an exception, or
     * cancellation -- in all of these cases, this method will return
     * {@code true}.
     *
     * @return {@code true} if this task completed
     */
    boolean isDone();

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an
     * exception
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     */
    V get() throws InterruptedException, ExecutionException;

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
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;

    /**
     * Returns the computed result, without waiting.
     *
     * <p> This method is for cases where the caller knows that the task has
     * already completed successfully, for example when filtering a stream
     * of Future objects for the successful tasks and using a mapping
     * operation to obtain a stream of results.
     * {@snippet lang=java :
     *     results = futures.stream()
     *                .filter(f -> f.state() == Future.State.SUCCESS)
     *                .map(Future::resultNow)
     *                .toList();
     * }
     *
     * @implSpec
     * The default implementation invokes {@code isDone()} to test if the task
     * has completed. If done, it invokes {@code get()} to obtain the result.
     *
     * @return the computed result
     * @throws IllegalStateException if the task has not completed or the task
     * did not complete with a result
     * @since 19
     */
    default V resultNow() {
        if (!isDone())
            throw new IllegalStateException("Task has not completed");
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return get();
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Task completed with exception");
                } catch (CancellationException e) {
                    throw new IllegalStateException("Task was cancelled");
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the exception thrown by the task, without waiting.
     *
     * <p> This method is for cases where the caller knows that the task
     * has already completed with an exception.
     *
     * @implSpec
     * The default implementation invokes {@code isDone()} to test if the task
     * has completed. If done and not cancelled, it invokes {@code get()} and
     * catches the {@code ExecutionException} to obtain the exception.
     *
     * @return the exception thrown by the task
     * @throws IllegalStateException if the task has not completed, the task
     * completed normally, or the task was cancelled
     * @since 19
     */
    default Throwable exceptionNow() {
        if (!isDone())
            throw new IllegalStateException("Task has not completed");
        if (isCancelled())
            throw new IllegalStateException("Task was cancelled");
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    get();
                    throw new IllegalStateException("Task completed with a result");
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    return e.getCause();
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /**
     * Represents the computation state.
     * @since 19
     */
    enum State {
        /**
         * The task has not completed.
         */
        RUNNING,
        /**
         * The task completed with a result.
         * @see Future#resultNow()
         */
        SUCCESS,
        /**
         * The task completed with an exception.
         * @see Future#exceptionNow()
         */
        FAILED,
        /**
         * The task was cancelled.
         * @see #cancel(boolean)
         */
        CANCELLED
    }

    /**
     * {@return the computation state}
     *
     * @implSpec
     * The default implementation uses {@code isDone()}, {@code isCancelled()},
     * and {@code get()} to determine the state.
     *
     * @since 19
     */
    default State state() {
        if (!isDone())
            return State.RUNNING;
        if (isCancelled())
            return State.CANCELLED;
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    get();  // may throw InterruptedException when done
                    return State.SUCCESS;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    return State.FAILED;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
