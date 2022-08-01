/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import jdk.internal.misc.PreviewFeatures;
import jdk.internal.misc.ThreadFlock;

/**
 * A basic API for <em>structured concurrency</em>. {@code StructuredTaskScope} supports
 * cases where a task splits into several concurrent subtasks, to be executed in their
 * own threads, and where the subtasks must complete before the main task continues. A
 * {@code StructuredTaskScope} can be used to ensure that the lifetime of a concurrent
 * operation is confined by a <em>syntax block</em>, just like that of a sequential
 * operation in structured programming.
 *
 * <h2>Basic usage</h2>
 *
 * A {@code StructuredTaskScope} is created with one of its public constructors. It defines
 * the {@link #fork(Callable) fork} method to start a thread to execute a task, the {@link
 * #join() join} method to wait for all threads to finish, and the {@link #close() close}
 * method to close the task scope. The API is intended to be used with the {@code
 * try-with-resources} construct. The intention is that code in the <em>block</em> uses
 * the {@code fork} method to fork threads to execute the subtasks, wait for the threads
 * to finish with the {@code join} method, and then <em>process the results</em>.
 * Processing of results may include handling or re-throwing of exceptions.
 * {@snippet lang=java :
 *     try (var scope = new StructuredTaskScope<Object>()) {
 *
 *         Future<Integer> future1 = scope.fork(task1);   // @highlight substring="fork"
 *         Future<String> future2 = scope.fork(task2);    // @highlight substring="fork"
 *
 *         scope.join();                                  // @highlight substring="join"
 *
 *         ... process results/exceptions ...
 *
 *     } // close                                         // @highlight substring="close"
 * }
 * To ensure correct usage, the {@code join} and {@code close} methods may only be invoked
 * by the <em>owner</em> (the thread that opened/created the task scope}, and the
 * {@code close} method throws an exception after closing if the owner did not invoke the
 * {@code join} method after forking.
 *
 * <p> {@code StructuredTaskScope} defines the {@link #shutdown() shutdown} method to shut
 * down a task scope without closing it. Shutdown is useful for cases where a subtask
 * completes with a result (or exception) and the results of other unfinished subtasks are
 * no longer needed. If a subtask invokes {@code shutdown} while the owner is waiting in
 * the {@code join} method then it will cause {@code join} to wakeup, all unfinished
 * threads to be {@linkplain Thread#interrupt() interrupted} and prevents new threads
 * from starting in the task scope.
 *
 * <h2>Subclasses with policies for common cases</h2>
 *
 * Two subclasses of {@code StructuredTaskScope} are defined to implement policy for
 * common cases:
 * <ol>
 *   <li> {@link ShutdownOnSuccess ShutdownOnSuccess} captures the first result and
 *   shuts down the task scope to interrupt unfinished threads and wakeup the owner. This
 *   class is intended for cases where the result of any subtask will do ("invoke any")
 *   and where there is no need to wait for results of other unfinished tasks. It defines
 *   methods to get the first result or throw an exception if all subtasks fail.
 *   <li> {@link ShutdownOnFailure ShutdownOnFailure} captures the first exception and
 *   shuts down the task scope. This class is intended for cases where the results of all
 *   subtasks are required ("invoke all"); if any subtask fails then the results of other
 *   unfinished subtasks are no longer needed. If defines methods to throw an exception if
 *   any of the subtasks fail.
 * </ol>
 *
 * <p> The following are two examples that use the two classes. In both cases, a pair of
 * subtasks are forked to fetch resources from two URL locations "left" and "right". The
 * first example creates a ShutdownOnSuccess object to capture the result of the first
 * subtask to complete normally, cancelling the other by way of shutting down the task
 * scope. The main task waits in {@code join} until either subtask completes with a result
 * or both subtasks fail. It invokes {@link ShutdownOnSuccess#result(Function)
 * result(Function)} method to get the captured result. If both subtasks fail then this
 * method throws a {@code WebApplicationException} with the exception from one of the
 * subtasks as the cause.
 * {@snippet lang=java :
 *     try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
 *
 *         scope.fork(() -> fetch(left));
 *         scope.fork(() -> fetch(right));
 *
 *         scope.join();
 *
 *         // @link regex="result(?=\()" target="ShutdownOnSuccess#result" :
 *         String result = scope.result(e -> new WebApplicationException(e));
 *
 *         ...
 *     }
 * }
 * The second example creates a ShutdownOnFailure object to capture the exception of the
 * first subtask to fail, cancelling the other by way of shutting down the task scope. The
 * main task waits in {@link #joinUntil(Instant)} until both subtasks complete with a
 * result, either fails, or a deadline is reached. It invokes {@link
 * ShutdownOnFailure#throwIfFailed(Function) throwIfFailed(Function)} to throw an exception
 * when either subtask fails. This method is a no-op if no subtasks fail. The main task
 * uses {@code Future}'s {@link Future#resultNow() resultNow()} method to retrieve the
 * results.
 *
 * {@snippet lang=java :
 *    Instant deadline = ...
 *
 *    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
 *
 *         Future<String> future1 = scope.fork(() -> query(left));
 *         Future<String> future2 = scope.fork(() -> query(right));
 *
 *         scope.joinUntil(deadline);
 *
 *         // @link substring="throwIfFailed" target="ShutdownOnFailure#throwIfFailed" :
 *         scope.throwIfFailed(e -> new WebApplicationException(e));
 *
 *         // both subtasks completed successfully
 *         String result = Stream.of(future1, future2)
 *                 // @link substring="Future::resultNow" target="Future#resultNow" :
 *                 .map(Future::resultNow)
 *                 .collect(Collectors.joining(", ", "{ ", " }"));
 *
 *         ...
 *     }
 * }
 *
 * <h2>Extending StructuredTaskScope</h2>
 *
 * {@code StructuredTaskScope} can be extended, and the {@link #handleComplete(Future)
 * handleComplete} overridden, to implement policies other than those implemented by
 * {@code ShutdownOnSuccess} and {@code ShutdownOnFailure}. The method may be overridden
 * to, for example, collect the results of subtasks that complete with a result and ignore
 * subtasks that fail. It may collect exceptions when subtasks fail. It may invoke the
 * {@link #shutdown() shutdown} method to shut down and cause {@link #join() join} to
 * wakeup when some condition arises.
 *
 * <p> A subclass will typically define methods to make available results, state, or other
 * outcome to code that executes after the {@code join} method. A subclass that collects
 * results and ignores subtasks that fail may define a method that returns a collection of
 * results. A subclass that implements a policy to shut down when a subtask fails may
 * define a method to retrieve the exception of the first subtask to fail.
 *
 * <p> The following is an example of a {@code StructuredTaskScope} implementation that
 * collects the results of subtasks that complete successfully. It defines the method
 * <b>{@code results()}</b> to be used by the main task to retrieve the results.
 *
 * {@snippet lang=java :
 *     class MyScope<T> extends StructuredTaskScope<T> {
 *         private final Queue<T> results = new ConcurrentLinkedQueue<>();
 *
 *         MyScope() {
 *             super(null, Thread.ofVirtual().factory());
 *         }
 *
 *         @Override
 *         // @link substring="handleComplete" target="handleComplete" :
 *         protected void handleComplete(Future<T> future) {
 *             if (future.state() == Future.State.SUCCESS) {
 *                 T result = future.resultNow();
 *                 results.add(result);
 *             }
 *         }
 *
 *         // Returns a stream of results from the subtasks that completed successfully
 *         public Stream<T> results() {     // @highlight substring="results"
 *             return results.stream();
 *         }
 *     }
 *  }
 *
 * <h2><a id="TreeStructure">Tree structure</a></h2>
 *
 * StructuredTaskScopes form a tree where parent-child relations are established
 * implicitly when opening a new task scope:
 * <ul>
 *   <li> A parent-child relation is established when a thread started in a task scope
 *   opens its own task scope. A thread started in task scope "A" that opens task scope
 *   "B" establishes a parent-child relation where task scope "A" is the parent of task
 *   scope "B".
 *   <li> A parent-child relation is established with nesting. If a thread opens task
 *   scope "B", then opens task scope "C" (before it closes "B"), then the enclosing task
 *   scope "B" is the parent of the nested task scope "C".
 * </ul>
 *
 * <p> The tree structure supports confinement checks. The phrase "threads contained in
 * the task scope" in method descriptions means threads started in the task scope or
 * descendant scopes. {@code StructuredTaskScope} does not define APIs that exposes the
 * tree structure at this time.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be thrown.
 *
 * <h2>Memory consistency effects</h2>
 *
 * <p> Actions in the owner thread of, or a thread contained in, the task scope prior to
 * {@linkplain #fork forking} of a {@code Callable} task
 * <a href="../../../../java.base/java/util/concurrent/package-summary.html#MemoryVisibility">
 * <i>happen-before</i></a> any actions taken by that task, which in turn <i>happen-before</i>
 * the task result is retrieved via its {@code Future}, or <i>happen-before</i> any actions
 * taken in a thread after {@linkplain #join() joining} of the task scope.
 *
 * @jls 17.4.5 Happens-before Order
 *
 * @param <T> the result type of tasks executed in the scope
 * @since 19
 */
public class StructuredTaskScope<T> implements AutoCloseable {
    private static final VarHandle FUTURES;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            FUTURES = l.findVarHandle(StructuredTaskScope.class, "futures", Set.class);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    private final ThreadFactory factory;
    private final ThreadFlock flock;
    private final ReentrantLock shutdownLock = new ReentrantLock();

    // lazily created set of Future objects with threads waiting in Future::get
    private volatile Set<Future<?>> futures;

    // set by owner when it forks, reset by owner when it joins
    private boolean needJoin;

    // states: OPEN -> SHUTDOWN -> CLOSED
    private static final int OPEN     = 0;   // initial state
    private static final int SHUTDOWN = 1;
    private static final int CLOSED   = 2;

    // scope state, set by owner, read by any thread
    private volatile int state;

    /**
     * Creates a structured task scope with the given name and thread factory. The task
     * scope is optionally named for the purposes of monitoring and management. The thread
     * factory is used to {@link ThreadFactory#newThread(Runnable) create} threads when
     * tasks are {@linkplain #fork(Callable) forked}. The task scope is owned by the
     * current thread.
     *
     * @param name the name of the task scope, can be null
     * @param factory the thread factory
     */
    public StructuredTaskScope(String name, ThreadFactory factory) {
        this.factory = Objects.requireNonNull(factory, "'factory' is null");
        this.flock = ThreadFlock.open(name);
    }

    /**
     * Creates an unnamed structured task scope that creates virtual threads. The task
     * scope is owned by the current thread.
     *
     * <p> This constructor is equivalent to invoking the 2-arg constructor with a name
     * of {@code null} and a thread factory that creates virtual threads.
     *
     * @throws UnsupportedOperationException if preview features are not enabled
     */
    public StructuredTaskScope() {
        PreviewFeatures.ensureEnabled();
        this.factory = Thread.ofVirtual().factory();
        this.flock = ThreadFlock.open(null);
    }

    /**
     * Throws WrongThreadException if the current thread is not the owner.
     */
    private void ensureOwner() {
        if (Thread.currentThread() != flock.owner())
            throw new WrongThreadException("Current thread not owner");
    }

    /**
     * Throws WrongThreadException if the current thread is not the owner
     * or a thread contained in the tree.
     */
    private void ensureOwnerOrContainsThread() {
        Thread currentThread = Thread.currentThread();
        if (currentThread != flock.owner() && !flock.containsThread(currentThread))
            throw new WrongThreadException("Current thread not owner or thread in the tree");
    }

    /**
     * Tests if the task scope is shutdown.
     */
    private boolean isShutdown() {
        return state >= SHUTDOWN;
    }

    /**
     * Track the given Future.
     */
    private void track(Future<?> future) {
        // create the set of Futures if not already created
        Set<Future<?>> futures = this.futures;
        if (futures == null) {
            futures = ConcurrentHashMap.newKeySet();
            if (!FUTURES.compareAndSet(this, null, futures)) {
                // lost the race
                futures = this.futures;
            }
        }
        futures.add(future);
    }

    /**
     * Stop tracking the Future.
     */
    private void untrack(Future<?> future) {
        assert futures != null;
        futures.remove(future);
    }

    /**
     * Invoked when a task completes before the scope is shut down.
     *
     * <p> The {@code handleComplete} method should be thread safe. It may be invoked by
     * several threads concurrently.
     *
     * @implSpec The default implementation does nothing.
     *
     * @param future the completed task
     */
    protected void handleComplete(Future<T> future) { }

    /**
     * Starts a new thread to run the given task.
     *
     * <p> The new thread is created with the task scope's {@link ThreadFactory}.
     *
     * <p> If the task completes before the task scope is {@link #shutdown() shutdown}
     * then the {@link #handleComplete(Future) handle} method is invoked to consume the
     * completed task. The {@code handleComplete} method is run when the task completes
     * with a result or exception. If the {@code Future} {@link Future#cancel(boolean)
     * cancel} method is used the cancel a task before the task scope is shut down, then
     * the {@code handleComplete} method is run by the thread that invokes {@code cancel}.
     * If the task scope shuts down at or around the same time that the task completes or
     * is cancelled then the {@code handleComplete} method may or may not be invoked.
     *
     * <p> If this task scope is {@linkplain #shutdown() shutdown} (or in the process
     * of shutting down) then {@code fork} returns a {@code Future} representing a {@link
     * Future.State#CANCELLED cancelled} task that was not run.
     *
     * <p> This method may only be invoked by the task scope owner or threads contained
     * in the task scope. The {@link Future#cancel(boolean) cancel} method of the returned
     * {@code Future} object is also restricted to the task scope owner or threads contained
     * in the task scope. The {@code cancel} method throws {@link WrongThreadException}
     * if invoked from another thread. All other methods on the returned {@code Future}
     * object, such as {@link Future#get() get}, are not restricted.
     *
     * @param task the task to run
     * @param <U> the result type
     * @return a future
     * @throws IllegalStateException if this task scope is closed
     * @throws WrongThreadException if the current thread is not the owner or a thread
     * contained in the task scope
     * @throws RejectedExecutionException if the thread factory rejected creating a
     * thread to run the task
     */
    public <U extends T> Future<U> fork(Callable<? extends U> task) {
        Objects.requireNonNull(task, "'task' is null");

        // create future
        var future = new FutureImpl<U>(this, task);

        boolean shutdown = (state >= SHUTDOWN);

        if (!shutdown) {
            // create thread
            Thread thread = factory.newThread(future);
            if (thread == null) {
                throw new RejectedExecutionException("Rejected by thread factory");
            }

            // attempt to start the thread
            try {
                flock.start(thread);
            } catch (IllegalStateException e) {
                // shutdown or in the process of shutting down
                shutdown = true;
            }
        }

        if (shutdown) {
            if (state == CLOSED) {
                throw new IllegalStateException("Task scope is closed");
            } else {
                future.cancel(false);
            }
        }

        // if owner forks then it will need to join
        if (Thread.currentThread() == flock.owner() && !needJoin) {
            needJoin = true;
        }

        return future;
    }

    /**
     * Wait for all threads to finish or the task scope to shut down.
     */
    private void implJoin(Duration timeout)
        throws InterruptedException, TimeoutException
    {
        ensureOwner();
        needJoin = false;
        int s = state;
        if (s >= SHUTDOWN) {
            if (s == CLOSED)
                throw new IllegalStateException("Task scope is closed");
            return;
        }

        // wait for all threads, wakeup, interrupt, or timeout
        if (timeout != null) {
            flock.awaitAll(timeout);
        } else {
            flock.awaitAll();
        }
    }

    /**
     * Wait for all threads to finish or the task scope to shut down. This method waits
     * until all threads started in the task scope finish execution (of both task and
     * {@link #handleComplete(Future) handleComplete} method), the {@link #shutdown()
     * shutdown} method is invoked to shut down the task scope, or the current thread
     * is {@linkplain Thread#interrupt() interrupted}.
     *
     * <p> This method may only be invoked by the task scope owner.
     *
     * @return this task scope
     * @throws IllegalStateException if this task scope is closed
     * @throws WrongThreadException if the current thread is not the owner
     * @throws InterruptedException if interrupted while waiting
     */
    public StructuredTaskScope<T> join() throws InterruptedException {
        try {
            implJoin(null);
        } catch (TimeoutException e) {
            throw new InternalError();
        }
        return this;
    }

    /**
     * Wait for all threads to finish or the task scope to shut down, up to the given
     * deadline. This method waits until all threads started in the task scope finish
     * execution (of both task and {@link #handleComplete(Future) handleComplete} method),
     * the {@link #shutdown() shutdown} method is invoked to shut down the task scope,
     * the current thread is {@linkplain Thread#interrupt() interrupted}, or the deadline
     * is reached.
     *
     * <p> This method may only be invoked by the task scope owner.
     *
     * @param deadline the deadline
     * @return this task scope
     * @throws IllegalStateException if this task scope is closed
     * @throws WrongThreadException if the current thread is not the owner
     * @throws InterruptedException if interrupted while waiting
     * @throws TimeoutException if the deadline is reached while waiting
     */
    public StructuredTaskScope<T> joinUntil(Instant deadline)
        throws InterruptedException, TimeoutException
    {
        Duration timeout = Duration.between(Instant.now(), deadline);
        implJoin(timeout);
        return this;
    }

    /**
     * Cancel all tracked Future objects.
     */
    private void cancelTrackedFutures() {
        Set<Future<?>> futures = this.futures;
        if (futures != null) {
            futures.forEach(f -> f.cancel(false));
        }
    }

    /**
     * Interrupt all unfinished threads.
     */
    private void implInterruptAll() {
        flock.threads().forEach(t -> {
            if (t != Thread.currentThread()) {
                t.interrupt();
            }
        });
    }

    @SuppressWarnings("removal")
    private void interruptAll() {
        if (System.getSecurityManager() == null) {
            implInterruptAll();
        } else {
            PrivilegedAction<Void> pa = () -> {
                implInterruptAll();
                return null;
            };
            AccessController.doPrivileged(pa);
        }
    }

    /**
     * Shutdown the task scope if not already shutdown. Return true if this method
     * shutdowns the task scope, false if already shutdown.
     */
    private boolean implShutdown() {
        if (state < SHUTDOWN) {
            shutdownLock.lock();
            try {
                if (state < SHUTDOWN) {

                    // prevent new threads from starting
                    flock.shutdown();

                    // wakeup any threads waiting in Future::get
                    cancelTrackedFutures();

                    // interrupt all unfinished threads
                    interruptAll();

                    state = SHUTDOWN;
                    return true;
                }
            } finally {
                shutdownLock.unlock();
            }
        }
        assert state >= SHUTDOWN;
        return false;
    }

    /**
     * Shut down the task scope without closing it. Shutting down a task scope prevents
     * new threads from starting, interrupts all unfinished threads, and causes the
     * {@link #join() join} method to wakeup. Shutdown is useful for cases where the
     * results of unfinished subtasks are no longer needed.
     *
     * <p> More specifically, this method:
     * <ul>
     * <li> {@linkplain Future#cancel(boolean) Cancels} the tasks that have threads
     * {@linkplain Future#get() waiting} on a result so that the waiting threads wakeup.
     * <li> {@linkplain Thread#interrupt() Interrupts} all unfinished threads in the
     * task scope (except the current thread).
     * <li> Wakes up the owner if it is waiting in {@link #join()} or {@link
     * #joinUntil(Instant)}. If the owner is not waiting then its next call to {@code
     * join} or {@code joinUntil} will return immediately.
     * </ul>
     *
     * <p> When this method completes then the {@code Future} objects for all tasks will
     * be {@linkplain Future#isDone() done}, normally or abnormally. There may still
     * be threads that have not finished because they are executing code that did not
     * respond (or respond promptly) to thread interrupt. This method does not wait
     * for these threads. When the owner invokes the {@link #close() close} method
     * to close the task scope then it will wait for the remaining threads to finish.
     *
     * <p> This method may only be invoked by the task scope owner or threads contained
     * in the task scope.
     *
     * @throws IllegalStateException if this task scope is closed
     * @throws WrongThreadException if the current thread is not the owner or
     * a thread contained in the task scope
     */
    public void shutdown() {
        ensureOwnerOrContainsThread();
        if (state == CLOSED)
            throw new IllegalStateException("Task scope is closed");
        if (implShutdown())
            flock.wakeup();
    }

    /**
     * Closes this task scope.
     *
     * <p> This method first shuts down the task scope (as if by invoking the {@link
     * #shutdown() shutdown} method). It then waits for the threads executing any
     * unfinished tasks to finish. If interrupted then this method will continue to
     * wait for the threads to finish before completing with the interrupt status set.
     *
     * <p> This method may only be invoked by the task scope owner. If the task scope
     * is already closed then the owner invoking this method has no effect.
     *
     * <p> A {@code StructuredTaskScope} is intended to be used in a <em>structured
     * manner</em>. If this method is called to close a task scope before nested task
     * scopes are closed then it closes the underlying construct of each nested task scope
     * (in the reverse order that they were created in), closes this task scope, and then
     * throws {@link StructureViolationException}.
     * If a thread terminates without first closing task scopes that it owns then
     * termination will cause the underlying construct of each of its open tasks scopes to
     * be closed. Closing is performed in the reverse order that the task scopes were
     * created in. Thread termination may therefore be delayed when the owner has to wait
     * for threads forked in these task scopes to finish.
     *
     * @throws IllegalStateException thrown after closing the task scope if the owner
     * did not invoke join after forking
     * @throws WrongThreadException if the current thread is not the owner
     * @throws StructureViolationException if a structure violation was detected
     */
    @Override
    public void close() {
        ensureOwner();
        if (state == CLOSED)
            return;

        try {
            implShutdown();
            flock.close();
        } finally {
            state = CLOSED;
        }

        if (needJoin) {
            throw new IllegalStateException("Owner did not invoke join or joinUntil after fork");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String name = flock.name();
        if (name != null) {
            sb.append(name);
            sb.append('/');
        }
        sb.append(Objects.toIdentityString(this));
        int s = state;
        if (s == CLOSED)
            sb.append("/closed");
        else if (s == SHUTDOWN)
            sb.append("/shutdown");
        return sb.toString();
    }

    /**
     * The Future implementation returned by the fork methods. Most methods are
     * overridden to support cancellation when the task scope is shutdown.
     * The blocking get methods register the Future with the task scope so that they
     * are cancelled when the task scope shuts down.
     */
    private static final class FutureImpl<V> extends FutureTask<V> {
        private final StructuredTaskScope<V> scope;

        @SuppressWarnings("unchecked")
        FutureImpl(StructuredTaskScope<? super V> scope, Callable<? extends V> task) {
            super((Callable<V>) task);
            this.scope = (StructuredTaskScope<V>) scope;
        }

        @Override
        protected void done() {
            if (!scope.isShutdown()) {
                scope.handleComplete(this);
            }
        }

        private void cancelIfShutdown() {
            if (scope.isShutdown() && !super.isDone()) {
                super.cancel(false);
            }
        }

        @Override
        public boolean isDone() {
            cancelIfShutdown();
            return super.isDone();
        }

        @Override
        public boolean isCancelled() {
            cancelIfShutdown();
            return super.isCancelled();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            scope.ensureOwnerOrContainsThread();
            cancelIfShutdown();
            return super.cancel(mayInterruptIfRunning);
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            if (super.isDone())
                return super.get();
            scope.track(this);
            try {
                cancelIfShutdown();
                return super.get();
            } finally {
                scope.untrack(this);
            }
        }

        @Override
        public V get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            Objects.requireNonNull(unit);
            if (super.isDone())
                return super.get();
            scope.track(this);
            try {
                cancelIfShutdown();
                return super.get(timeout, unit);
            } finally {
                scope.untrack(this);
            }
        }

        @Override
        public V resultNow() {
            cancelIfShutdown();
            return super.resultNow();
        }

        @Override
        public Throwable exceptionNow() {
            cancelIfShutdown();
            return super.exceptionNow();
        }

        @Override
        public State state() {
            cancelIfShutdown();
            return super.state();
        }

        @Override
        public String toString() {
            cancelIfShutdown();
            return super.toString();
        }
    }

    /**
     * Maps a Future.State to an int that can be compared.
     * RUNNING < CANCELLED < FAILED < SUCCESS.
     */
    private static int futureStateToInt(Future.State s) {
        return switch (s) {
            case RUNNING   -> 0;
            case CANCELLED -> 1;
            case FAILED    -> 2;
            case SUCCESS   -> 3;
        };
    }

    // RUNNING < CANCELLED < FAILED < SUCCESS
    private static final Comparator<Future.State> FUTURE_STATE_COMPARATOR =
            Comparator.comparingInt(StructuredTaskScope::futureStateToInt);

    /**
     * A {@code StructuredTaskScope} that captures the result of the first subtask to
     * complete successfully. Once captured, it invokes the {@linkplain #shutdown() shutdown}
     * method to interrupt unfinished threads and wakeup the owner. The policy
     * implemented by this class is intended for cases where the result of any subtask
     * will do ("invoke any") and where the results of other unfinished subtask are no
     * longer needed.
     *
     * <p> Unless otherwise specified, passing a {@code null} argument to a method
     * in this class will cause a {@link NullPointerException} to be thrown.
     *
     * @param <T> the result type
     * @since 19
     */
    public static final class ShutdownOnSuccess<T> extends StructuredTaskScope<T> {
        private static final VarHandle FUTURE;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                FUTURE = l.findVarHandle(ShutdownOnSuccess.class, "future", Future.class);
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }
        private volatile Future<T> future;

        /**
         * Constructs a new {@code ShutdownOnSuccess} with the given name and thread factory.
         * The task scope is optionally named for the purposes of monitoring and management.
         * The thread factory is used to {@link ThreadFactory#newThread(Runnable) create}
         * threads when tasks are {@linkplain #fork(Callable) forked}. The task scope is
         * owned by the current thread.
         *
         * @param name the name of the task scope, can be null
         * @param factory the thread factory
         */
        public ShutdownOnSuccess(String name, ThreadFactory factory) {
            super(name, factory);
        }

        /**
         * Constructs a new unnamed {@code ShutdownOnSuccess} that creates virtual threads.
         *
         * <p> This constructor is equivalent to invoking the 2-arg constructor with a
         * name of {@code null} and a thread factory that creates virtual threads.
         */
        public ShutdownOnSuccess() {
            super(null, Thread.ofVirtual().factory());
        }

        /**
         * Shut down the given task scope when invoked for the first time with a {@code
         * Future} for a task that completed with a result.
         *
         * @param future the completed task
         * @see #shutdown()
         * @see Future.State#SUCCESS
         */
        @Override
        protected void handleComplete(Future<T> future) {
            Future.State state = future.state();
            if (state == Future.State.RUNNING) {
                throw new IllegalArgumentException("Task is not completed");
            }

            Future<T> f;
            while (((f = this.future) == null)
                    || FUTURE_STATE_COMPARATOR.compare(f.state(), state) < 0) {
                if (FUTURE.compareAndSet(this, f, future)) {
                    if (state == Future.State.SUCCESS)
                        shutdown();
                    break;
                }
            }
        }

        /**
         * {@inheritDoc}
         * @return this task scope
         * @throws IllegalStateException {@inheritDoc}
         * @throws WrongThreadException {@inheritDoc}
         */
        @Override
        public ShutdownOnSuccess<T> join() throws InterruptedException {
            super.join();
            return this;
        }

        /**
         * {@inheritDoc}
         * @return this task scope
         * @throws IllegalStateException {@inheritDoc}
         * @throws WrongThreadException {@inheritDoc}
         */
        @Override
        public ShutdownOnSuccess<T> joinUntil(Instant deadline)
            throws InterruptedException, TimeoutException
        {
            super.joinUntil(deadline);
            return this;
        }

        /**
         * {@return the result of the first subtask that completed with a result}
         *
         * <p> When no subtask completed with a result but a task completed with an
         * exception then {@code ExecutionException} is thrown with the exception as the
         * {@linkplain Throwable#getCause() cause}. If only cancelled subtasks were
         * notified to the {@code handleComplete} method then {@code CancellationException}
         * is thrown.
         *
         * @apiNote This method is intended to be invoked by the task scope owner after it
         * has invoked {@link #join() join} (or {@link #joinUntil(Instant) joinUntil}).
         * A future release may add enforcement to prevent the method being called by
         * other threads or before joining.
         *
         * @throws ExecutionException if no subtasks completed with a result but a subtask
         * completed with an exception
         * @throws CancellationException if all subtasks were cancelled
         * @throws IllegalStateException if the handle method was not invoked with a
         * completed subtask
         */
        public T result() throws ExecutionException {
            Future<T> f = future;
            if (f == null) {
                throw new IllegalStateException("No completed subtasks");
            }
            return switch (f.state()) {
                case SUCCESS   -> f.resultNow();
                case FAILED    -> throw new ExecutionException(f.exceptionNow());
                case CANCELLED -> throw new CancellationException();
                default        -> throw new InternalError("Unexpected state: " + f);
            };

        }

        /**
         * Returns the result of the first subtask that completed with a result, otherwise
         * throws an exception produced by the given exception supplying function.
         *
         * <p> When no subtask completed with a result but a subtask completed with an
         * exception then the exception supplying function is invoked with the exception.
         * If only cancelled subtasks were notified to the {@code handleComplete} method
         * then the exception supplying function is invoked with a {@code CancellationException}.
         *
         * @apiNote This method is intended to be invoked by the task scope owner after it
         * has invoked {@link #join() join} (or {@link #joinUntil(Instant) joinUntil}).
         * A future release may add enforcement to prevent the method being called by
         * other threads or before joining.
         *
         * @param esf the exception supplying function
         * @param <X> type of the exception to be thrown
         * @return the result of the first subtask that completed with a result
         * @throws X if no subtask completed with a result
         * @throws IllegalStateException if the handle method was not invoked with a
         * completed subtask
         */
        public <X extends Throwable> T result(Function<Throwable, ? extends X> esf) throws X {
            Objects.requireNonNull(esf);
            Future<T> f = future;
            if (f == null) {
                throw new IllegalStateException("No completed subtasks");
            }
            Future.State state = f.state();
            if (state == Future.State.SUCCESS) {
                return f.resultNow();
            } else {
                Throwable throwable = (state == Future.State.FAILED)
                        ? f.exceptionNow()
                        : new CancellationException();
                X ex = esf.apply(throwable);
                Objects.requireNonNull(ex, "esf returned null");
                throw ex;
            }
        }
    }

    /**
     * A {@code StructuredTaskScope} that captures the exception of the first subtask to
     * complete abnormally. Once captured, it invokes the {@linkplain #shutdown() shutdown}
     * method to interrupt unfinished threads and wakeup the owner. The policy implemented
     * by this class is intended for cases where the results for all subtasks are required
     * ("invoke all"); if any subtask fails then the results of other unfinished subtasks
     * are no longer needed.
     *
     * <p> Unless otherwise specified, passing a {@code null} argument to a method
     * in this class will cause a {@link NullPointerException} to be thrown.
     *
     * @since 19
     */
    public static final class ShutdownOnFailure extends StructuredTaskScope<Object> {
        private static final VarHandle FUTURE;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                FUTURE = l.findVarHandle(ShutdownOnFailure.class, "future", Future.class);
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }
        private volatile Future<Object> future;

        /**
         * Constructs a new {@code ShutdownOnFailure} with the given name and thread factory.
         * The task scope is optionally named for the purposes of monitoring and management.
         * The thread factory is used to {@link ThreadFactory#newThread(Runnable) create}
         * threads when tasks are {@linkplain #fork(Callable) forked}. The task scope
         * is owned by the current thread.
         *
         * @param name the name of the task scope, can be null
         * @param factory the thread factory
         */
        public ShutdownOnFailure(String name, ThreadFactory factory) {
            super(name, factory);
        }

        /**
         * Constructs a new unnamed {@code ShutdownOnFailure} that creates virtual threads.
         *
         * <p> This constructor is equivalent to invoking the 2-arg constructor with a
         * name of {@code null} and a thread factory that creates virtual threads.
         */
        public ShutdownOnFailure() {
            super(null, Thread.ofVirtual().factory());
        }

        /**
         * Shut down the given task scope when invoked for the first time with a {@code
         * Future} for a task that completed abnormally (exception or cancelled).
         *
         * @param future the completed task
         * @see #shutdown()
         * @see Future.State#FAILED
         * @see Future.State#CANCELLED
         */
        @Override
        protected void handleComplete(Future<Object> future) {
            Future.State state = future.state();
            if (state == Future.State.RUNNING) {
                throw new IllegalArgumentException("Task is not completed");
            } else if (state == Future.State.SUCCESS) {
                return;
            }

            // A failed task overrides a cancelled task.
            // The first failed or cancelled task causes the scope to shutdown.
            Future<Object> f;
            while (((f = this.future) == null)
                    || FUTURE_STATE_COMPARATOR.compare(f.state(), state) < 0) {
                if (FUTURE.compareAndSet(this, f, future)) {
                    shutdown();
                    break;
                }
            }
        }

        /**
         * {@inheritDoc}
         * @return this task scope
         * @throws IllegalStateException {@inheritDoc}
         * @throws WrongThreadException {@inheritDoc}
         */
        @Override
        public ShutdownOnFailure join() throws InterruptedException {
            super.join();
            return this;
        }

        /**
         * {@inheritDoc}
         * @return this task scope
         * @throws IllegalStateException {@inheritDoc}
         * @throws WrongThreadException {@inheritDoc}
         */
        @Override
        public ShutdownOnFailure joinUntil(Instant deadline)
            throws InterruptedException, TimeoutException
        {
            super.joinUntil(deadline);
            return this;
        }

        /**
         * Returns the exception for the first subtask that completed with an exception.
         * If no subtask completed with an exception but cancelled subtasks were notified
         * to the {@code handleComplete} method then a {@code CancellationException}
         * is returned. If no subtasks completed abnormally then an empty {@code Optional}
         * is returned.
         *
         * @apiNote This method is intended to be invoked by the task scope owner after it
         * has invoked {@link #join() join} (or {@link #joinUntil(Instant) joinUntil}).
         * A future release may add enforcement to prevent the method being called by
         * other threads or before joining.
         *
         * @return the exception for a subtask that completed abnormally or an empty
         * optional if no subtasks completed abnormally
         */
        public Optional<Throwable> exception() {
            Future<Object> f = future;
            if (f != null) {
                Throwable throwable = (f.state() == Future.State.FAILED)
                        ? f.exceptionNow()
                        : new CancellationException();
                return Optional.of(throwable);
            } else {
                return Optional.empty();
            }
        }

        /**
         * Throws if a subtask completed abnormally. If any subtask completed with an
         * exception then {@code ExecutionException} is thrown with the exception of the
         * first subtask to fail as the {@linkplain Throwable#getCause() cause}. If no
         * subtask completed with an exception but cancelled subtasks were notified to the
         * {@code handleComplete} method then {@code CancellationException} is thrown.
         * This method does nothing if no subtasks completed abnormally.
         *
         * @apiNote This method is intended to be invoked by the task scope owner after it
         * has invoked {@link #join() join} (or {@link #joinUntil(Instant) joinUntil}).
         * A future release may add enforcement to prevent the method being called by
         * other threads or before joining.
         *
         * @throws ExecutionException if a subtask completed with an exception
         * @throws CancellationException if no subtasks completed with an exception but
         * subtasks were cancelled
         */
        public void throwIfFailed() throws ExecutionException {
            Future<Object> f = future;
            if (f != null) {
                if (f.state() == Future.State.FAILED) {
                    throw new ExecutionException(f.exceptionNow());
                } else {
                    throw new CancellationException();
                }
            }
        }

        /**
         * Throws the exception produced by the given exception supplying function if
         * a subtask completed abnormally. If any subtask completed with an exception then
         * the function is invoked with the exception of the first subtask to fail.
         * If no subtask completed with an exception but cancelled subtasks were notified
         * to the {@code handleComplete} method then the function is called with a {@code
         * CancellationException}. The exception returned by the function is thrown.
         * This method does nothing if no subtasks completed abnormally.
         *
         * @apiNote This method is intended to be invoked by the task scope owner after it
         * has invoked {@link #join() join} (or {@link #joinUntil(Instant) joinUntil}).
         * A future release may add enforcement to prevent the method being called by
         * other threads or before joining.
         *
         * @param esf the exception supplying function
         * @param <X> type of the exception to be thrown
         * @throws X produced by the exception supplying function
         */
        public <X extends Throwable>
        void throwIfFailed(Function<Throwable, ? extends X> esf) throws X {
            Objects.requireNonNull(esf);
            Future<Object> f = future;
            if (f != null) {
                Throwable throwable = (f.state() == Future.State.FAILED)
                        ? f.exceptionNow()
                        : new CancellationException();
                X ex = esf.apply(throwable);
                Objects.requireNonNull(ex, "esf returned null");
                throw ex;
            }
        }
    }
}
