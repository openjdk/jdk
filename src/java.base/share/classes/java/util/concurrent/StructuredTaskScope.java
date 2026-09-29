/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package java.util.concurrent;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import jdk.internal.javac.PreviewFeature;

/**
 * An API for <em>structured concurrency</em>. {@code StructuredTaskScope} supports cases
 * where execution of a <em>task</em> (a unit of work) splits into several concurrent
 * subtasks, and where the subtasks must complete before the task continues. A {@code
 * StructuredTaskScope} can be used to ensure that the lifetime of a concurrent operation
 * is confined by a <em>syntactic block</em>, similar to that of a sequential operation in
 * structured programming.
 *
 * <p> {@code StructuredTaskScope} defines the static {@link #open()} method to create
 * and open a new {@code StructuredTaskScope}. It defines the {@link #close() close()}
 * method to close it. The API is designed to be used with the {@code try}-with-resources
 * statement where a {@code StructuredTaskScope} is opened as a resource and then closed
 * automatically. The code inside the {@code try} block uses the {@link #fork(Callable)}
 * method to <em>fork</em> subtasks. Each call to the {@code fork(Callable)} method starts
 * a new {@link Thread} (typically a {@linkplain Thread##virtual-threads virtual thread})
 * to execute a subtask as a {@linkplain Callable value-returning method}. The subtask
 * executes concurrently with the code inside the {@code try} block, and concurrently with
 * other subtasks forked in the scope. After forking all subtasks, the code inside the
 * block uses the {@link #join() join()} method to wait for all subtasks to finish (or
 * some other outcome) as a single operation. The code after the {@code join()} method
 * processes the outcome. Execution does not continue beyond the {@code try} block (or
 * {@code close} method) until all threads started in the scope to execute subtasks have
 * finished.
 *
 * <p> To ensure correct usage, the {@link #fork(Callable)}, {@link #join()} and {@link
 * #close()} methods may only be invoked by the <em>owner thread</em> (the thread that
 * opened the {@code StructuredTaskScope}), the {@code fork(Callable)} method may not be
 * called after {@code join()}, the {@code join()} method must be invoked to get the outcome
 * after forking subtasks, and the {@code close()} method throws an exception after closing
 * if the owner did not invoke the {@code join()} method after forking subtasks.
 *
 * <p> As a first example, consider a "main" task that splits into two subtasks to
 * concurrently fetch values from two remote services. The main task aggregates the results
 * of both subtasks. The example invokes {@link #fork(Callable)} to fork the two subtasks.
 * Each call to {@code fork(Callable)} returns a {@link Subtask Subtask} as a handle to
 * the forked subtask. Both subtasks may complete successfully, one subtask may succeed
 * and the other may fail, or both subtasks may fail.
 *
 * <p> The main task in the example is interested in the successful result from both
 * subtasks. It waits in the {@link #join()} method for both subtasks to complete
 * successfully or for either subtask to fail. If both subtasks complete successfully then
 * the {@code join()} method completes normally and the task uses the {@link Subtask#get()
 * Subtask.get()} method to get the result of each subtask. If one of the subtasks fails
 * then the other subtask is cancelled, and the {@code join()} method throws {@link
 * ExecutionException} with the exception from the failed subtask as the {@linkplain
 * Throwable#getCause() cause}.
 * {@snippet lang=java :
 *    // @link substring="open()" target="#open()" :
 *    try (var scope = StructuredTaskScope.open()) {
 *
 *        // @link substring="fork" target="#fork(Callable)" :
 *        Subtask<String> subtask1 = scope.fork(() -> fetchFromRemoteService1());
 *        Subtask<Integer> subtask2 = scope.fork(() -> fetchFromRemoteService2());
 *
 *        // throws ExecutionException if either subtask fails
 *        scope.join();  // @link substring="join()" target="#join()"
 *
 *        // both subtasks completed successfully
 *        // @link substring="get()" target="Subtask#get()" :
 *        var result = new MyResult(subtask1.get(), subtask2.get());
 *
 *    // @link substring="close" target="#close()" :
 *    } // close
 * }
 *
 * <p> The {@link #close() close()} method always waits for threads executing subtasks to
 * finish, even if the {@code join()} method throws, so that execution cannot continue
 * beyond the {@code close()} method until the interrupted threads finish.
 *
 * <p> To allow for cancellation, subtasks must be coded so that they finish as soon as
 * possible when interrupted. Subtasks that do not respond to interrupt, e.g. block on
 * methods that are not interruptible, may delay the {@link #close() close()} method
 * indefinitely.
 *
 * <p> In the example, the subtasks produce results of different types ({@code String} and
 * {@code Integer}). In other cases the subtasks may all produce results of the same type.
 * If the example had used {@code StructuredTaskScope.<String>open()} to open the scope
 * then it could only be used to fork subtasks that return a {@code String} result.
 *
 * <h2>Joiners</h2>
 *
 * <p> The {@link #join()} method in the example above completes normally, and returns
 * {@code null}, if all subtasks succeed. It throws {@code ExecutionException} if any subtask
 * fails. Other policies and outcomes are possible by creating a {@code StructuredTaskScope}
 * with a {@link Joiner Joiner} that implements the desired policy and outcome. A {@code
 * Joiner} handles subtasks as they are forked and when they complete, and produces the
 * outcome for the {@code join()} method. Instead of {@code null}, a {@code Joiner} may
 * cause {@code join()} to return the result of a specific subtask, a collection of results,
 * or an object constructed from the results of some or all subtasks. When the outcome
 * is an exception, a {@code Joiner} may cause {@code join()} to throw an exception
 * other than {@code ExecutionException}.
 *
 * <p> A {@code Joiner} may <a id="Cancellation"><i>cancel</i></a> the scope (sometimes
 * called "short-circuiting") when some condition is reached, e.g. a subtask fails, that
 * does not require the outcome of other subtasks that are still executing. Cancelling the
 * scope prevents new threads from being started in the scope, cancels subtasks in the
 * scope that have not completed execution, and causes the {@code join()} method to wake up
 * with the outcome (result or exception). In the above example, the outcome is that {@code
 * join()} completes normally when all subtasks succeed. The scope is cancelled if any
 * subtask fails and {@code join()} throws {@code ExecutionException} with the exception
 * from the failed subtask as the {@linkplain Throwable#getCause() cause}. Other {@code
 * Joiner} implementations may cancel the scope for other reasons, and may cause {@code
 * join()} to throw a different exception when the outcome is an exception.
 *
 * <p> The {@link Joiner Joiner} interface defines static factory methods to create a
 * {@code Joiner} for a number of common cases. The interface can be implemented when a
 * more advanced or custom policy is required. A {@code Joiner} that returns a
 * non-{@code null} result may remove the need for <i>bookkeeping</i> and the need to keep
 * a reference to {@code Subtask} objects returned by the {@link #fork(Callable)} method.

 * <p> Now consider another example where a main task splits into two subtasks. In this
 * example, each subtask produces a {@code String} result and the main task is only
 * interested in the result from the first subtask to complete successfully. The example
 * uses {@link Joiner#anySuccessfulOrThrow() Joiner.anySuccessfulOrThrow()} to create a
 * {@code Joiner} that produces the result of any subtask that completes successfully.
 * {@snippet lang=java :
 *    // @link substring="anySuccessfulOrThrow()" target="Joiner#anySuccessfulOrThrow()" :
 *    try (var scope = StructuredTaskScope.open(Joiner.<String>anySuccessfulOrThrow())) {
 *
 *        // @link substring="fork" target="#fork(Callable)" :
 *        scope.fork(callable1);
 *        scope.fork(callable2);
 *
 *        // throws ExecutionException if both subtasks fail
 *        String firstResult = scope.join(); // @link substring="join" target="#join()"
 *
 *    // @link substring="close" target="#close()" :
 *    } // close
 * }
 *
 * <p> In the example, the task forks the two subtasks, then waits in the {@code
 * join()} method for either subtask to complete successfully or for both subtasks to fail.
 * If one of the subtasks completes successfully then the {@code Joiner} causes the other
 * subtask to be cancelled (this will interrupt the thread executing the subtask), and
 * the {@code join()} method returns the result from the successful subtask. Cancelling the
 * other subtask avoids the task waiting for a result that it doesn't care about. If
 * both subtasks fail then the {@code join()} method throws {@link ExecutionException} with
 * the exception from one of the subtasks as the {@linkplain Throwable#getCause() cause}.
 * {@link Joiner#anySuccessfulOrThrow(Function) Joiner.anySuccessfulOrThrow(Function)} can
 * be used with a function that produces an exception other than {@code ExecutionException}
 * to throw when all subtasks fail.
 *
 * <h2>Configuration</h2>
 *
 * A {@code StructuredTaskScope} is opened with {@linkplain Configuration configuration}
 * that consists of a {@link ThreadFactory} to create threads, an optional name for the
 * scope, and an optional timeout. The name is intended for monitoring and management
 * purposes.
 *
 * <p> The {@link #open()} and {@link #open(Joiner)} methods create a {@code StructuredTaskScope}
 * with the <a id="DefaultConfiguration"> <em>default configuration</em></a>. The default
 * configuration has a {@code ThreadFactory} that creates unnamed {@linkplain
 * Thread##virtual-threads virtual threads}, does not name the scope, and has no timeout.
 *
 * <p> The {@link #open(UnaryOperator)} and {@link #open(Joiner, UnaryOperator)} methods
 * can be used to create a {@code StructuredTaskScope} that uses a different {@code
 * ThreadFactory}, is named for monitoring and management purposes, or has a timeout that
 * cancels the scope if the timeout expires before or while waiting for subtasks to
 * complete. The {@code open} methods are called with an {@linkplain UnaryOperator operator}
 * that is applied to the default configuration and returns a {@link Configuration
 * Configuration} for the {@code StructuredTaskScope} under construction.
 *
 * <p> The following example opens a new {@code StructuredTaskScope} with a {@code
 * ThreadFactory} that creates virtual threads {@linkplain Thread#getName() named}
 * "duke-0", "duke-1" ...
 * {@snippet lang = java:
 *    // @link substring="name" target="Thread.Builder#name(String, long)" :
 *    ThreadFactory factory = Thread.ofVirtual().name("duke-", 0).factory();
 *
 *    // @link substring="withThreadFactory" target="Configuration#withThreadFactory(ThreadFactory)" :
 *    try (var scope = StructuredTaskScope.open(cf -> cf.withThreadFactory(factory))) {
 *
 *        var subtask1 = scope.fork( .. );   // runs in a virtual thread with name "duke-0"
 *        var subtask2 = scope.fork( .. );   // runs in a virtual thread with name "duke-1"
 *
 *        scope.join();
 *
 *        var result = new MyResult(subtask1.get(), subtask2.get());
 *
 *     }
 *}
 *
 * <p> A second example sets a timeout, represented by a {@link Duration}. The timeout
 * starts when the new scope is opened. If the timeout expires before or while waiting in
 * the {@link #join()} method then the scope is {@linkplain ##Cancellation cancelled}
 * (this interrupts the threads executing the subtasks that have not completed), and the
 * {@code join()} method throws {@link ExecutionException} with a {@link
 * CancelledByTimeoutException CancelledByTimeoutException} as the cause.
 * {@snippet lang=java :
 *    Duration timeout = Duration.ofSeconds(10);
 *
 *    // @link substring="allSuccessfulOrThrow" target="Joiner#allSuccessfulOrThrow()" :
 *    try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow(),
 *    // @link substring="withTimeout" target="Configuration#withTimeout(Duration)" :
 *                                              cf -> cf.withTimeout(timeout))) {
 *
 *        scope.fork(callable1);   // subtask takes a really long time
 *        scope.fork(callable2);
 *
 *        // throws ExecutionException with CancelledByTimeoutException as cause
 *        List<String> results = scope.join();
 *
 *    }
 * }
 *
 * <h2>Exception handling</h2>
 *
 * <p> The outcome of the {@link #join()} method is a result or exception. When the outcome
 * is an exception then its {@linkplain Throwable#getCause() cause} will typically be
 * the exception from a failed subtask or {@link CancelledByTimeoutException
 * CancelledByTimeoutException} if a timeout was configured.
 *
 * <p> In some cases it may be useful to add a {@code catch} block to the
 * {@code try}-with-resources statement to handle the exception. The following example
 * uses the {@link #open(UnaryOperator)} method to open a scope with a timeout configured.
 * The {@code join()} method in this example throws {@link ExecutionException} if any
 * subtask fails or the timeout expires. The exception cause is the exception from a failed
 * subtask or {@code CancelledByTimeoutException}. The example uses the {@code switch}
 * statement to select based on the cause.
 * {@snippet lang=java :
 *    try (var scope = StructuredTaskScope.open(cf -> cf.withTimeout(timeout))) {
 *
 *        ..
 *
 *    } catch (ExecutionException e) {
 *        switch (e.getCause()) {
 *            case CancelledByTimeoutException ->
 *            case IOException ioe -> ..
 *            default -> ..
 *        }
 *    }
 * }
 *
 * <p> In other cases it may not be useful to catch the exception but instead leave it to
 * propagate to the configured {@linkplain Thread.UncaughtExceptionHandler uncaught
 * exception handler} for logging purposes.
 *
 * <p> For cases where a specific exception triggers the use of a default result then it
 * may be more appropriate to handle this in the subtask itself rather than the subtask
 * failing and the scope owner handling the exception.
 *
 * <p> The {@link #join()} method throws {@link InterruptedException} when interrupted
 * before or while waiting in the {@code join()} method.
 * The {@link Thread##thread-interruption Thread Interruption} section of the {@code Thread}
 * specification provides guidance on handling this exception.
 *
 * <h2>Inheritance of scoped value bindings</h2>
 *
 * {@link ScopedValue} supports the execution of a method with a {@code ScopedValue} bound
 * to a value for the bounded period of execution of the method by the <em>current thread</em>.
 * It allows a value to be safely and efficiently shared to methods without using method
 * parameters.
 *
 * <p> When used in conjunction with a {@code StructuredTaskScope}, a {@code ScopedValue}
 * can also safely and efficiently share a value to methods executed by subtasks forked
 * in the scope. When a {@code ScopedValue} object is bound to a value in the thread
 * executing a "main" task then that binding is inherited by the threads created to
 * execute subtasks. The thread executing the main task does not continue beyond the
 * {@link #close() close()} method until all threads executing the subtasks have finished.
 * This ensures that the {@code ScopedValue} is not reverted to being {@linkplain
 * ScopedValue#isBound() unbound} (or its previous value) while subtasks are executing.
 * In addition to providing a safe and efficient means to inherit a value into subtasks,
 * the inheritance allows sequential code using {@code ScopedValue} to be refactored to
 * use structured concurrency.
 *
 * <p> To ensure correctness, opening a new {@code StructuredTaskScope} captures the
 * current thread's scoped value bindings. These are the scoped values bindings that are
 * inherited by the threads created to execute subtasks in the scope. Forking a
 * subtask checks that the bindings in effect at the time that the subtask is forked
 * match the bindings when the {@code StructuredTaskScope} was created. This check ensures
 * that a subtask does not inherit a binding that is reverted in the main task before the
 * subtask has completed.
 *
 * <p> A {@code ScopedValue} that is shared across threads requires that the value be an
 * immutable object or for all access to the value to be appropriately synchronized.
 *
 * <p> The following example demonstrates the inheritance of scoped value bindings. The
 * scoped value USERNAME is bound to the value "duke" for the bounded period of a lambda
 * expression by the thread executing it. The code in the block opens a {@code
 * StructuredTaskScope} and forks two subtasks, it then waits in the {@code join()} method
 * and aggregates the results from both subtasks. If code executed by the threads
 * running subtask1 and subtask2 uses {@link ScopedValue#get()}, to get the value of
 * USERNAME, then value "duke" will be returned.
 * {@snippet lang=java :
 *     // @link substring="newInstance" target="ScopedValue#newInstance()" :
 *     private static final ScopedValue<String> USERNAME = ScopedValue.newInstance();
 *
 *     // @link substring="where" target="ScopedValue#where(ScopedValue, Object)" :
 *     MyResult result = ScopedValue.where(USERNAME, "duke").call(() -> {
 *
 *         try (var scope = StructuredTaskScope.open()) {
 *
 *             Subtask<String> subtask1 = scope.fork( .. );    // inherits binding
 *             Subtask<Integer> subtask2 = scope.fork( .. );   // inherits binding
 *
 *             scope.join();
 *             return new MyResult(subtask1.get(), subtask2.get());
 *         }
 *
 *     });
 * }
 *
 * <p> A scoped value inherited into a subtask may be {@linkplain ScopedValue##rebind
 * rebound} to a new value in the subtask for the bounded execution of some method executed
 * in the subtask. When the method completes, the value of the {@code ScopedValue} reverts
 * to its previous value, the value inherited from the thread executing the main task.
 *
 * <p> A subtask may execute code that itself opens a new {@code StructuredTaskScope}.
 * A main task executing in thread T1 opens a {@code StructuredTaskScope} and forks a
 * subtask that runs in thread T2. The scoped value bindings captured when T1 opens the
 * scope are inherited into T2. The subtask (in thread T2) executes code that opens a
 * new {@code StructuredTaskScope} and forks a (sub-)subtask that runs in thread T3. The
 * scoped value bindings captured when T2 opens the scope are inherited into T3. These
 * include the bindings that were inherited from T1. In effect, scoped values are
 * inherited into a tree of subtasks, not just one level of subtask.
 *
 * <h2>Memory consistency effects</h2>
 *
 * <p> Actions in the owner thread of a {@code StructuredTaskScope} prior to {@linkplain
 * #fork forking} of a subtask {@linkplain java.util.concurrent##MemoryVisibility
 * <i>happen-before</i>} any actions taken by the thread that executes the subtask, which
 * in turn <i>happen-before</i> actions in any thread that successfully obtains the
 * subtask outcome with {@link Subtask#get() Subtask.get()} or {@link Subtask#exception()
 * Subtask.exception()}. If a subtask's outcome contributes to the result or exception
 * from {@link #join()}, then any actions taken by the thread executing that subtask
 * <i>happen-before</i> the owner thread returns from {@code join()} with the outcome.
 *
 * <h2>General exceptions</h2>
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to a method in this
 * class will cause a {@link NullPointerException} to be thrown.
 *
 * @param <T> the result type of subtasks {@linkplain #fork(Callable) forked} in the scope
 * @param <R> the type of the result returned by the {@link #join() join()} method
 * @param <R_X> the type of the exception thrown by the {@link #join() join()} method
 * @jls 17.4.5 Happens-before Order
 * @since 21
 */
@PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
public sealed interface StructuredTaskScope<T, R, R_X extends Throwable>
        extends AutoCloseable
        permits StructuredTaskScopeImpl {

    /**
     * Represents a subtask forked in a {@link StructuredTaskScope} with {@link
     * #fork(Callable) fork(Callable)} or {@link #fork(Runnable) fork(Runnable)}.
     *
     * <p> The scope owner can use the {@link #get() get()} method after {@linkplain
     * #join() joining} to obtain the result of a subtask that completed successfully. It
     * can use the {@link #exception()} method to obtain the exception thrown by a subtask
     * that failed.
     *
     * @param <T> the result type
     * @since 21
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
    sealed interface Subtask<T> extends Supplier<T> permits StructuredTaskScopeImpl.SubtaskImpl {
        /**
         * Represents the state of a {@link Subtask Subtask}.
         * @see Subtask#state()
         * @since 21
         */
        @PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
        enum State {
            /**
             * The subtask result or exception is not available. This state indicates that
             * the subtask was forked but has not completed, the subtask was forked after
             * the scope was {@linkplain StructuredTaskScope##Cancellation cancelled}, or
             * the subtask completed after the scope was cancelled.
             */
            UNAVAILABLE,
            /**
             * The subtask completed successfully. If the scope is {@linkplain
             * StructuredTaskScope##Cancellation cancelled}, the subtask completed
             * successfully before the scope was cancelled. The {@link Subtask#get()
             * Subtask.get()} method can be used to get the result. This is a terminal
             * state.
             */
            SUCCESS,
            /**
             * The subtask failed with an exception. If the scope is {@linkplain
             * StructuredTaskScope##Cancellation cancelled}, the subtask failed before
             * the scope was cancelled. The {@link Subtask#exception() Subtask.exception()}
             * method can be used to get the exception. This is a terminal state.
             */
            FAILED,
        }

        /**
         * {@return the subtask state}
         */
        State state();

        /**
         * Returns the result of this subtask if it completed successfully. If the scope
         * is {@linkplain StructuredTaskScope##Cancellation cancelled}, the subtask
         * completed successfully before the scope was cancelled. If the subtask was
         * forked with {@link #fork(Callable) fork(Callable)} then the result from the
         * {@link Callable#call() call()} method is returned. If the subtask was forked
         * with {@link #fork(Runnable) fork(Runnable)} then {@code null} is returned.
         *
         * <p> Code executing in the scope owner thread can use this method to get the
         * result of a successful subtask after it has {@linkplain #join() joined}.
         *
         * <p> Code executing in the {@code Joiner} {@link Joiner#onComplete(Subtask)
         * onComplete(Subtask)} method should test that the {@linkplain #state() state} is
         * {@link State#SUCCESS SUCCESS} before using this method to get the result.
         *
         * <p> This method may be invoked by any thread after the scope owner has joined.
         * The only case where this method can be used to get the result before the scope
         * owner has joined is when called from the {@code onComplete(Subtask)} method.
         *
         * @return the possibly-null result
         * @throws IllegalStateException if the subtask has not completed or did not
         * complete successfully, or this method is invoked outside the context of the
         * {@code onComplete(Subtask)} method before the owner thread has joined
         * @see State#SUCCESS
         */
        T get();

        /**
         * {@return the exception or error thrown by this subtask if it failed}
         * If the scope is {@linkplain StructuredTaskScope##Cancellation cancelled}, the
         * subtask failed before the scope was cancelled. If the subtask was forked with
         * {@link #fork(Callable) fork(Callable)} then the exception or error thrown by
         * the {@link Callable#call() call()} method is returned. If the subtask was
         * forked with {@link #fork(Runnable) fork(Runnable)} then the exception or error
         * thrown by the {@link Runnable#run() run()} method is returned.
         *
         * <p> Code executing in the scope owner thread can use this method to get the
         * exception thrown by a failed subtask after it has {@linkplain #join() joined}.
         *
         * <p> Code executing in a {@code Joiner} {@link Joiner#onComplete(Subtask)
         * onComplete(Subtask)} method should test that the {@linkplain #state() state} is
         * {@link State#FAILED FAILED} before using this method to get the exception.
         *
         * <p> This method may be invoked by any thread after the scope owner has joined.
         * The only case where this method can be used to get the exception before the scope
         * owner has joined is when called from the {@code onComplete(Subtask)} method.
         *
         * @throws IllegalStateException if the subtask has not completed or completed
         * with a result, or this method is invoked outside the context of the {@code
         * onComplete(Subtask)} method before the owner thread has joined
         * @see State#FAILED
         */
        Throwable exception();
    }

    /**
     * An object used with a {@link StructuredTaskScope} to produce the outcome for the
     * scope's {@link #join() join()} method.
     *
     * <p> A {@code StructuredTaskScope} is opened with a {@code Joiner} that handles
     * {@linkplain Subtask subtasks} as they are {@linkplain #fork(Callable) forked} in
     * the scope and again when they complete execution. The {@code Joiner} handles
     * subtasks that complete successfully with a result of type {@code T}, or fail with
     * any exception or error. The {@code Joiner} implements a <em>policy</em> that may
     * {@linkplain StructuredTaskScope##Cancellation cancel} the scope when some condition
     * is reached (for example, a subtask fails). When all subtasks complete execution, or
     * the scope is cancelled, the {@code Joiner} produces the outcome for the {@link
     * #join() join()} method. The outcome is a result of type {@code R} or an exception
     * of type {@code R_X}.
     *
     * <p> {@code Joiner} defines static methods to create {@code Joiner} objects for
     * common cases:
     * <ul>
     *   <li> {@link #allSuccessfulOrThrow()} and {@link #allSuccessfulOrThrow(Function)}
     *   create a {@code Joiner} that produces a list of all results for {@code join()} to
     *   return when all subtasks complete successfully. The {@code Joiner} cancels the
     *   scope and causes {@code join()} to throw {@link ExecutionException}, or an
     *   exception returned by an <i>exception supplying function</i>, if any subtask fails.
     *   <li> {@link #anySuccessfulOrThrow()} and {@link #anySuccessfulOrThrow(Function)}
     *   create a {@code Joiner} that produces the result of any successful subtask for
     *   {@code join()} to return. The {@code Joiner} causes {@code join()} to throw
     *   {@code ExecutionException}, or an exception returned by an exception supplying
     *   function, if all subtasks fail.
     *   <li> {@link #awaitAllSuccessfulOrThrow()} and {@link #awaitAllSuccessfulOrThrow(Function)}
     *   create a {@code Joiner} that waits for all subtasks to complete successfully.
     *   The {@code Joiner} does not produce a non-{@code null} result for {@code join()}
     *   to return. The {@code Joiner} cancels the scope and causes {@code join()} to throw
     *   {@code ExecutionException}, or an exception returned by an exception supplying
     *   function, if any subtask fails.
     * </ul>
     *
     * <p> In addition to the methods to create {@code Joiner} objects for common cases,
     * the {@link #allUntil(Predicate) allUntil(Predicate)} method can be used to create a
     * {@code Joiner} that implements a <em>cancellation policy</em>. The {@code Joiner}
     * is created with a {@linkplain Predicate predicate} that is evaluated on completed
     * subtasks. The {@code Joiner} {@linkplain StructuredTaskScope##Cancellation cancels}
     * the scope if the predicate evaluates to {@code true}. When using this {@code Joiner},
     * the outcome of the {@code join()} method is the list of all subtasks forked in the
     * scope.
     *
     * <p> More advanced policies can be developed by implementing the {@code Joiner}
     * interface. The {@link #onFork(Subtask)} method is invoked when subtasks are forked.
     * The {@link #onComplete(Subtask)} method is invoked when subtasks complete with a
     * result or exception. These methods return a {@code boolean} to indicate whether the
     * scope should be cancelled. These methods can be used to collect subtasks, results,
     * or exceptions, and control when to cancel the scope. The {@link #result()} method
     * must be implemented to produce the outcome (result or exception) for the {@code
     * join()} method. The {@link #timeout()} method must be implemented to produce the
     * outcome for the {@linkplain Configuration#withTimeout(Duration) timeout} case.
     *
     * <p> Unless otherwise specified, passing a {@code null} argument to a method
     * in this class will cause a {@link NullPointerException} to be thrown.
     *
     * @implSpec Implementations of this interface must be thread-safe. The {@link
     * #onComplete(Subtask)} method may be invoked concurrently as multiple subtasks can
     * complete at the same time. Additionally, the {@code onComplete(Subtask)} method may
     * be called concurrently with the scope owner thread invoking the {@link
     * #onFork(Subtask)}, {@link #result()}, or {@link #timeout()} methods.
     *
     * <p> A {@code Joiner} should clearly document how it handles {@linkplain
     * Configuration#withTimeout(Duration) timeouts}. In many cases, a timeout will cause
     * the {@code join()} method to throw an exception with {@link
     * CancelledByTimeoutException CancelledByTimeoutException} as the cause. Some
     * {@code Joiner} implementation may be capable of returning a result for the
     * timeout case.
     *
     * <p> Designing a {@code Joiner} should take into account the code at the use-site
     * where the results from the {@link StructuredTaskScope#join() join()} method are
     * processed. It should be clear what the {@code Joiner} does vs. the application
     * code at the use-site. In general, the {@code Joiner} implementation is not the
     * place for "business logic". A {@code Joiner} should be designed to be as general
     * purpose as possible.
     *
     * @apiNote It is very important that a new {@code Joiner} object is created for each
     * {@code StructuredTaskScope}. {@code Joiner} objects should never be shared with
     * different scopes or re-used after a scope is closed.
     *
     * @param <T> the result type of subtasks {@linkplain #fork(Callable) forked} in the scope
     * @param <R> the type of the result returned by the {@link #join() join()} method
     * @param <R_X> the type of the exception thrown by the {@link #join() join()} method
     * @since 25
     * @see #open(Joiner)
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
    interface Joiner<T, R, R_X extends Throwable> {
        /**
         * Invoked by {@link #fork(Callable) fork(Callable)} and {@link #fork(Runnable)
         * fork(Runnable)} when forking a subtask. The method is invoked before a thread
         * is created to execute the subtask.
         *
         * @implSpec The default implementation throws {@code NullPointerException} if the
         * subtask is {@code null}. It throws {@code IllegalArgumentException} if the
         * subtask is not in the {@link Subtask.State#UNAVAILABLE UNAVAILABLE} state, it
         * otherwise returns {@code false}.
         *
         * @apiNote This method is invoked by the {@code fork} methods. It should not be
         * invoked directly.
         *
         * @param subtask the subtask
         * @return {@code true} to cancel the scope, otherwise {@code false}
         */
        default boolean onFork(Subtask<T> subtask) {
            if (subtask.state() != Subtask.State.UNAVAILABLE) {
                throw new IllegalArgumentException("Subtask not in UNAVAILABLE state");
            }
            return false;
        }

        /**
         * Invoked by the thread that executed a subtask after the subtask completes
         * successfully or fails with an exception. This method is not invoked by subtasks
         * that complete after the scope is {@linkplain StructuredTaskScope##Cancellation
         * cancelled}.
         *
         * @implSpec The default implementation throws {@code NullPointerException} if the
         * subtask is {@code null}. It throws {@code IllegalArgumentException} if the
         * subtask is not in the {@link Subtask.State#SUCCESS SUCCESS} or {@link
         * Subtask.State#FAILED FAILED} state, it otherwise returns {@code false}.
         *
         * @apiNote This method is invoked by subtasks when they complete. It should not
         * be invoked directly.
         *
         * @param subtask the subtask
         * @return {@code true} to cancel the scope, otherwise {@code false}
         */
        default boolean onComplete(Subtask<T> subtask) {
            if (subtask.state() == Subtask.State.UNAVAILABLE) {
                throw new IllegalArgumentException("Subtask has not completed");
            }
            return false;
        }

        /**
         * Invoked by the {@link #join() join()} method to produce the outcome (result or
         * exception) after waiting for all subtasks to complete or the scope is {@linkplain
         * StructuredTaskScope##Cancellation cancelled}. This method is not invoked if the
         * scope was opened with a timeout and the timeout expires before or while waiting.
         *
         * <p> This method will be called at most once, by the {@code join()} method, to
         * produce the outcome. The behavior of this method when invoked directly is undefined.
         *
         * @apiNote This method is invoked by the {@code join()} method. It should not be
         * invoked directly.
         *
         * @return the result
         * @throws R_X if the outcome is an exception
         */
        R result() throws R_X;

        /**
         * Invoked by the {@link #join() join()} method to produce the outcome (result or
         * exception) when the scope was opened with a timeout and the timeout expires before
         * or while waiting in the {@code join()} method.
         *
         * <p> If the outcome is an exception, this method throws the exception with a
         * {@link CancelledByTimeoutException CancelledByTimeoutException} as the
         * {@link Throwable#getCause() cause}.
         *
         * <p> This method will be called at most once, by the {@code join()} method, to
         * produce the outcome. The behavior of this method when invoked directly is undefined.
         *
         * @apiNote This method is invoked by the {@code join()} method. It should not be
         * invoked directly.
         *
         * @return the result
         * @throws R_X with a cause of {@code CancelledByTimeoutException}, if the outcome
         * is an exception
         * @since 27
         */
        R timeout() throws R_X;

        /**
         * {@return a new Joiner that produces a list of all results when all subtasks
         * complete successfully}
         * The {@code Joiner} {@linkplain StructuredTaskScope##Cancellation cancels} the
         * scope and causes the {@link #join() join()} method to throw if any subtask fails.
         *
         * <p> The {@link #join() join()} method of a {@link StructuredTaskScope} opened
         * with this Joiner returns the list of the results, in the order that the subtasks
         * were {@linkplain #fork(Callable) forked}, when all subtasks complete successfully.
         * An empty list is returned if no subtasks were forked. If any subtask fails then
         * the {@code Joiner} causes the {@code join()} method to throw the exception
         * returned by the given exception supplying function when {@linkplain
         * Function#apply(Object) applied} to the exception from the first subtask to fail.
         * The function should return an exception with the exception from the failed
         * subtask (the function argument) as the {@linkplain Throwable#getCause() cause}.
         * If the function returns {@code null} then it causes the {@code join()} method
         * to throw {@code NullPointerException}.
         *
         * <p> <b>Timeout Handling:</b> The {@code Joiner} cannot produce a result when
         * the scope is cancelled by a timeout. If the scope was opened with a {@linkplain
         * Configuration#withTimeout(Duration) timeout}, and the timeout expires before or
         * while waiting for all subtasks to complete successfully, then the {@code Joiner}
         * causes the {@code join()} method to throw the exception returned by the
         * exception supplying function when applied to a {@link CancelledByTimeoutException
         * CancelledByTimeoutException}.
         *
         * @apiNote Joiners returned by this method are suited to cases where all subtasks
         * return a result of the same type. It removes the need for <i>bookkeeping</i>
         * and the need to keep a reference to the {@link Subtask Subtask} objects returned
         * by the {@link #fork(Callable) fork(Callable)} method. Joiners returned by {@link
         * #awaitAllSuccessfulOrThrow(Function)} and {@link #awaitAllSuccessfulOrThrow()}
         * are suited to cases where the subtasks return results of different types and
         * where it is necessary to keep a reference to the {@code Subtask} objects.
         *
         * <p> The following example is a method that opens a {@code StructuredTaskScope}
         * with a Joiner created with {@code allSuccessfulOrThrow(Function)}. The method
         * is invoked with a collection of {@linkplain Callable callables}. It {@linkplain
         * #fork(Callable) forks} a subtask to execute each callable, waits in {@link
         * #join() join()} for all subtasks to complete successfully, and then returns a
         * list of the results. It throws the runtime exception {@link CompletionException}
         * if any subtask fails, with the exception from the first subtask to fail as the
         * cause.
         * {@snippet lang=java :
         *   <T> List<T> invokeAll(Collection<Callable<T>> tasks) throws InterruptedException {
         *         try (var scope = StructuredTaskScope.open(
         *                 Joiner.<T, CompletionException>allSuccessfulOrThrow(CompletionException::new))) {
         *             tasks.forEach(scope::fork);
         *             List<T> results = scope.join();
         *             return results;
         *         }
         *   }
         * }
         *
         * @param esf the exception supplying function
         * @param <T> the result type of subtasks
         * @param <R_X> the type of the exception thrown by the {@link #join() join()} method
         * @since 27
         * @see #allSuccessfulOrThrow()
         */
        static <T, R_X extends Throwable> Joiner<T, List<T>, R_X>
        allSuccessfulOrThrow(Function<Throwable, R_X> esf) {
            return new Joiners.AllSuccessful<>(esf);
        }

        /**
         * {@return a new Joiner that produces a list of all results when all subtasks
         * complete successfully}
         * The {@code Joiner} {@linkplain StructuredTaskScope##Cancellation cancels} the
         * scope and causes the {@link #join() join()} method to throw {@link
         * ExecutionException} if any subtask fails.
         *
         * <p> The {@link #join() join()} method of a {@link StructuredTaskScope} opened
         * with this Joiner returns the list of the results, in the order that the subtasks
         * were {@linkplain #fork(Callable) forked}, when all subtasks complete successfully.
         * An empty list is returned if no subtasks were forked. If any subtask fails then
         * the Joiner causes the {@code join()} method to throw {@code ExecutionException}
         * with the exception from the first subtask to fail as the {@linkplain
         * Throwable#getCause() cause}.
         *
         * <p> <b>Timeout Handling:</b> The {@code Joiner} cannot produce a result when
         * the scope is cancelled by a timeout. If the scope was opened with a {@linkplain
         * Configuration#withTimeout(Duration) timeout}, and the timeout expires before or
         * while waiting for all subtasks to complete successfully, then the {@code Joiner}
         * causes the {@code join()} method to throw {@code ExecutionException} with
         * a {@link CancelledByTimeoutException CancelledByTimeoutException} as the cause.
         *
         * @implSpec A Joiner returned by this method is equivalent to invoking {@link
         * #allSuccessfulOrThrow(Function) allSuccessfulOrThrow(ExecutionException::new)}.
         *
         * @param <T> the result type of subtasks
         */
        static <T> Joiner<T, List<T>, ExecutionException> allSuccessfulOrThrow() {
            return allSuccessfulOrThrow(ExecutionException::new);
        }

        /**
         * {@return a new Joiner that produces the result of any successful subtask}
         * The {@code Joiner} {@linkplain StructuredTaskScope##Cancellation cancels} the
         * scope when a subtask completes successfully. It causes the {@link #join() join()}
         * method to throw if all subtasks fail.
         *
         * <p> The {@link #join() join()} method of a {@link StructuredTaskScope} opened
         * with this Joiner returns the result of a successful subtask. If a subtask
         * completes successfully then the scope is cancelled and the {@code join()}
         * method returns its result. If all subtasks fail then the Joiner causes the
         * {@code join()} method to throw the exception returned by the given exception
         * supplying function when {@linkplain Function#apply(Object) applied} to the
         * exception from one of the failed subtasks. The function should return an
         * exception with the exception from the failed subtask (the function argument) as
         * the {@linkplain Throwable#getCause() cause}. If the function returns {@code null}
         * then it causes the {@code join()} method to throw {@code NullPointerException}.
         * If no subtasks were forked then the Joiner causes the {@code join()} method to
         * throw the exception returned by the function when applied to an instance of
         * {@link java.util.NoSuchElementException}.
         *
         * <p> <b>Timeout Handling:</b> The {@code Joiner} cannot produce a result when
         * the scope is cancelled by a timeout. If the scope was opened with a {@linkplain
         * Configuration#withTimeout(Duration) timeout}, and the timeout expires before or
         * while waiting for a subtask to complete successfully, then the {@code Joiner}
         * causes the {@code join()} method to throw the exception returned by the
         * exception supplying function when applied to a {@link CancelledByTimeoutException
         * CancelledByTimeoutException}.
         *
         * @apiNote The following example is a method that opens a {@code StructuredTaskScope}
         * with a Joiner created with {@code anySuccessfulOrThrow(Function)}. The method
         * is invoked with a collection of {@linkplain Callable callables}. It {@linkplain
         * #fork(Callable) forks} a subtask to execute each callable, waits in {@link
         * #join() join()} for any subtask to complete successfully, returning its result.
         * It throws the runtime exception {@link CompletionException} if all subtasks fail,
         * with the exception from a failed subtask as the cause.
         * {@snippet lang=java :
         *   <T> T invokeAny(Collection<Callable<T>> tasks) throws InterruptedException {
         *         try (var scope = StructuredTaskScope.open(
         *               Joiner.<T, CompletionException>anySuccessfulOrThrow(CompletionException::new))) {
         *             tasks.forEach(scope::fork);
         *             T result = scope.join();
         *             return result;
         *         }
         *   }
         * }
         *
         * @param esf the exception supplying function
         * @param <T> the result type of subtasks
         * @param <R_X> the type of the exception thrown by the {@link #join() join()} method
         * @since 27
         * @see #anySuccessfulOrThrow()
         */
        static <T, R_X extends Throwable> Joiner<T, T, R_X> anySuccessfulOrThrow(Function<Throwable, R_X> esf) {
            return new Joiners.AnySuccessful<>(esf);
        }

        /**
         * {@return a new Joiner that produces the result of any successful subtask}
         * The {@code Joiner} {@linkplain StructuredTaskScope##Cancellation cancels} the
         * scope when a subtask completes successfully. It causes the {@link #join() join()}
         * method to throw {@link ExecutionException} if all subtasks fail.
         *
         * <p> The {@link #join() join()} method of a {@link StructuredTaskScope} opened
         * with this Joiner returns the result of a successful subtask. If a subtask
         * completes successfully then the scope is cancelled and the {@code join()} method
         * returns its result. If all subtasks fail then the Joiner causes the {@code
         * join()} method to throw {@code ExecutionException} with the exception from one
         * of the failed subtasks as the {@linkplain Throwable#getCause() cause}. If no
         * subtasks were forked then the {@code Joiner} causes the {@code join()} method
         * to throw {@code ExecutionException} with {@link java.util.NoSuchElementException}
         * as the cause.
         *
         * <p> <b>Timeout Handling:</b> The {@code Joiner} cannot produce a result when
         * the scope is cancelled by a timeout. If the scope was opened with a {@linkplain
         * Configuration#withTimeout(Duration) timeout}, and the timeout expires before or
         * while waiting for a subtask to complete successfully, then the {@code Joiner}
         * causes the {@code join()} method to throw {@code ExecutionException} with a
         * {@link CancelledByTimeoutException CancelledByTimeoutException} as the cause.
         *
         * @implSpec A Joiner returned by this method is equivalent to invoking {@link
         * #anySuccessfulOrThrow(Function) anySuccessfulOrThrow(ExecutionException::new)}.
         *
         * @param <T> the result type of subtasks
         * @since 26
         */
        static <T> Joiner<T, T, ExecutionException> anySuccessfulOrThrow() {
            return anySuccessfulOrThrow(ExecutionException::new);
        }

        /**
         * {@return a new Joiner that causes the {@link #join() join()} method to wait
         * for all subtasks to complete successfully}
         * The {@code Joiner} {@linkplain StructuredTaskScope##Cancellation cancels} the
         * scope and causes the {@link #join() join()} method to throw if any subtask fails.
         *
         * <p> The {@link #join() join()} method of a {@link StructuredTaskScope} opened
         * with this Joiner returns {@code null} when all subtasks complete successfully.
         * If any subtask fails then the Joiner causes the {@code join()} method to throw
         * the exception returned by the given exception supplying function when {@linkplain
         * Function#apply(Object) applied} to the exception from the first subtask to fail.
         * The function should return an exception with the exception from the failed
         * subtask (the function argument) as the {@linkplain Throwable#getCause() cause}.
         * If the function returns {@code null} then it causes the {@code join()} method
         * to throw {@code NullPointerException}.
         *
         * <p> <b>Timeout Handling:</b> The {@code Joiner} cannot produce a result when
         * the scope is cancelled by a timeout. If the scope was opened with a {@linkplain
         * Configuration#withTimeout(Duration) timeout}, and the timeout expires before or
         * while waiting for all subtasks to complete successfully, then the {@code Joiner}
         * causes the {@code join()} method to throw the exception returned by the
         * exception supplying function when applied to a {@link CancelledByTimeoutException
         * CancelledByTimeoutException}.
         *
         * @apiNote Joiners returned by this method are suited to cases where subtasks
         * return results of different types and where it is necessary to keep a reference
         * to each {@link Subtask Subtask}. Joiners returned by {@link
         * #allSuccessfulOrThrow(Function)} and {@link #allSuccessfulOrThrow()} are suited
         * to cases where the subtasks return a result of the same type.
         *
         * <p> The following example opens a {@code StructuredTaskScope} with a Joiner
         * created with {@code awaitAllSuccessfulOrThrow()}. It {@linkplain
         * #fork(Callable) forks} two subtasks, then waits in {@link #join() join()} for
         * both subtasks to complete successfully or either subtask to fail. If both
         * subtasks complete successfully then it invokes {@link Subtask#get() Subtask.get()}
         * on both subtasks to get their results. It throws the runtime exception {@link
         * CompletionException} if any subtask fails, with the exception from a failed
         * subtask as the cause.
         * {@snippet lang=java :
         *   try (var scope = StructuredTaskScope.open(
         *            Joiner.awaitAllSuccessfulOrThrow(CompletionException::new))) {
         *       Subtask<String> subtask1 = scope.fork(callable1);
         *       Subtask<Integer> subtask2 = scope.fork(callable2);
         *
         *       // throws CompletionException if either subtask fails
         *       scope.join();
         *
         *       // both subtasks completed successfully
         *       var result = new MyResult(subtask1.get(), subtask2.get());
         *   }
         * }
         * @param esf the exception supplying function
         * @param <T> the result type of subtasks
         * @param <R_X> the type of the exception thrown by the {@link #join() join()} method
         * @since 27
         */
        static <T, R_X extends Throwable> Joiner<T, Void, R_X>
        awaitAllSuccessfulOrThrow(Function<Throwable, R_X> esf) {
            return new Joiners.AwaitSuccessful<>(esf);
        }

        /**
         * {@return a new Joiner that causes the {@link #join() join()} method to wait
         * for all subtasks to complete successfully}
         * The {@code Joiner} {@linkplain StructuredTaskScope##Cancellation cancels} the
         * scope and causes the {@link #join() join()} method to throw {@link
         * ExecutionException} if any subtask fails.
         *
         * <p> The {@link #join() join()} method of a {@link StructuredTaskScope} opened
         * with this Joiner returns {@code null} when all subtasks complete successfully.
         * If any subtask fails then the Joiner causes the {@code join()} method to throw
         * {@code ExecutionException} with the exception from the first subtask to fail
         * as the {@linkplain Throwable#getCause() cause}.
         *
         * <p> <b>Timeout Handling:</b> The {@code Joiner} cannot produce a result when
         * the scope is cancelled by a timeout. If the scope was opened with a {@linkplain
         * Configuration#withTimeout(Duration) timeout}, and the timeout expires before or
         * while waiting for all subtasks to complete successfully, then the {@code Joiner}
         * causes the {@code join()} method to throw {@code ExecutionException} with a
         * {@link CancelledByTimeoutException CancelledByTimeoutException} as the cause.
         *
         * @implSpec A Joiner returned by this method is equivalent to invoking {@link
         * #awaitAllSuccessfulOrThrow(Function) awaitAllSuccessfulOrThrow(ExecutionException::new)}.
         *
         * @param <T> the result type of subtasks
         * @see #open()
         * @see #open(UnaryOperator)
         */
        static <T> Joiner<T, Void, ExecutionException> awaitAllSuccessfulOrThrow() {
            return awaitAllSuccessfulOrThrow(ExecutionException::new);
        }

        /**
         * {@return a new Joiner that produces a list of all subtasks when all subtasks
         * complete or {@linkplain Predicate#test(Object) evaluating} a predicate on a
         * completed subtask causes the scope to be {@linkplain
         * StructuredTaskScope##Cancellation cancelled}}
         * The {@code Joiner} does not cause the {@link #join() join()} method to throw
         * if subtasks fail or a configured {@linkplain Configuration#withTimeout(Duration)
         * timeout} expires. This method can be used to create a {@code Joiner} that
         * implements a <em>cancellation policy</em>.
         *
         * <p> The {@link #join() join()} method of a {@link StructuredTaskScope} opened
         * with this Joiner returns a list of all subtasks, in the order that they were
         * {@linkplain #fork(Callable) forked}, when all subtasks complete or the scope is
         * cancelled. The returned list may contain subtasks that completed (in the {@link
         * Subtask.State#SUCCESS SUCCESS} or {@link Subtask.State#FAILED FAILED} state),
         * or subtasks in the {@link Subtask.State#UNAVAILABLE UNAVAILABLE} state if
         * they were forked or completed after the scope was cancelled. The scope is
         * cancelled if the given predicate {@linkplain Predicate#test(Object) evaluates}
         * to {@code true}, or a configured timeout expires before or while waiting in the
         * {@code join()} method.
         *
         * <p> The given {@code Predicate}'s {@link Predicate#test(Object) test(Object)}
         * method is invoked on a completed subtask by the thread that executed the subtask.
         * The method is invoked after the subtask completes (successfully or with an
         * exception) before the thread terminates. The scope is cancelled if the {@code
         * test} method returns {@code true}. The {@code test} method must be thread safe.
         * It may be invoked concurrently from several threads as multiple subtasks can
         * complete at the same time. If the method throws an exception or error, the thread
         * invokes the {@linkplain Thread.UncaughtExceptionHandler uncaught exception handler}
         * with the exception or error before the thread terminates.
         *
         * <p> <b>Timeout Handling:</b> If used with a scope that has a {@linkplain
         * Configuration#withTimeout(Duration) timeout} set, and the timeout expires before
         * all subtasks complete then the {@code join()} method returns the list of
         * all subtasks. It does not throw an exception. Subtasks that did not complete
         * before the timeout expires will be in the {@code UNAVAILABLE} state.
         *
         * @apiNote The following example uses {@code allUntil} to create a Joiner that
         * {@linkplain StructuredTaskScope##Cancellation cancels} the scope when two or
         * more subtasks fail.
         * {@snippet lang=java :
         *    class CancelAfterTwoFailures implements Predicate<Subtask<?>> {
         *         private final AtomicInteger failedCount = new AtomicInteger();
         *         @Override
         *         public boolean test(Subtask<?> subtask) {
         *             return subtask.state() == Subtask.State.FAILED
         *                     && failedCount.incrementAndGet() >= 2;
         *         }
         *     }
         *
         *     var joiner = Joiner.<String>allUntil(new CancelAfterTwoFailures());
         * }
         *
         * <p> The following example uses {@code allUntil} to create a Joiner that
         * cancels the scope when any subtask completes successfully. The subtasks are
         * grouped according to their {@linkplain Subtask.State state} to produce a map
         * with up to three key-value mappings. The map key is the subtask state, the
         * value is the list of subtasks in that state.
         * {@snippet lang=java :
         *     Predicate<Subtask<?>> isSuccessful = s -> s.state() == Subtask.State.SUCCESS;
         *
         *     try (var scope = StructuredTaskScope.open(Joiner.<String>allUntil(isSuccessful))) {
         *         tasks.forEach(scope::fork);
         *
         *         Map<Subtask.State, List<Subtask<String>>> subtasksByState = scope.join()
         *                 .stream()
         *                 // @link substring="Collectors.groupingBy" target="java.util.stream.Collectors#groupingBy" :
         *                 .collect(Collectors.groupingBy(Subtask::state));
         *     }
         * }
         *
         * <p> The following example is a method that uses {@code allUntil} to create a
         * Joiner that does not cancel the scope. The method waits for all subtasks to
         * complete or a timeout to expire. It returns a list of all subtasks, in the
         * same order as the collection of callables, even if the timeout expires before
         * or while waiting in {@code join()}.
         * {@snippet lang=java :
         *    <T> List<Subtask<T>> invokeAll(Collection<Callable<T>> tasks, Duration timeout) throws InterruptedException {
         *        // @link substring="withTimeout" target="Configuration#withTimeout(Duration)" :
         *        try (var scope = StructuredTaskScope.open(Joiner.<T>allUntil(_ -> false), cf -> cf.withTimeout(timeout))) {
         *            tasks.forEach(scope::fork);
         *            return scope.join();
         *        }
         *    }
         * }
         *
         * @param isDone the predicate to evaluate completed subtasks
         * @param <T> the result type of subtasks
         */
        static <T> Joiner<T, List<Subtask<T>>, RuntimeException>
        allUntil(Predicate<? super Subtask<T>> isDone) {
            return new Joiners.AllSubtasks<>(isDone);
        }
    }

    /**
     * Represents the configuration for a {@code StructuredTaskScope}.
     *
     * <p> The configuration for a {@code StructuredTaskScope} consists of a {@link
     * ThreadFactory} to create threads, an optional name for the scope, and an optional
     * timeout. The name is intended for monitoring and management purposes.
     *
     * <p> Creating a {@code StructuredTaskScope} with the {@link #open(UnaryOperator)
     * open(UnaryOperator)} or {@link #open(Joiner, UnaryOperator) open(Joiner, UnaryOperator)}
     * method allows a different configuration to be used. The operator specified
     * to the {@code open} method is applied to the default configuration and returns the
     * configuration for the {@code StructuredTaskScope} under construction. The operator
     * can use the {@code with-} prefixed methods defined here to specify the components
     * of the configuration to use.
     *
     * <p> Unless otherwise specified, passing a {@code null} argument to a method
     * in this class will cause a {@link NullPointerException} to be thrown.
     *
     * @since 25
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
    sealed interface Configuration permits StructuredTaskScopeImpl.ConfigImpl {
        /**
         * {@return a new {@code Configuration} object with the given thread factory}
         * The other components are the same as this object. The thread factory is used by
         * a scope to create threads when {@linkplain #fork(Callable) forking} subtasks.
         * @param threadFactory the thread factory
         *
         * @apiNote The thread factory will typically create {@linkplain Thread##virtual-threads
         * virtual threads}, maybe with {@linkplain Thread#getName() thread names} for
         * monitoring purposes, an {@linkplain Thread.UncaughtExceptionHandler uncaught
         * exception handler}, or other properties configured.
         *
         * @see #fork(Callable)
         */
        Configuration withThreadFactory(ThreadFactory threadFactory);

        /**
         * {@return a new {@code Configuration} object with the given scope name}
         * The other components are the same as this object. A scope is optionally
         * named for the purposes of monitoring and management.
         * @param name the name
         */
        Configuration withName(String name);

        /**
         * {@return a new {@code Configuration} object with the given timeout}
         * The other components are the same as this object.
         * @param timeout the timeout
         *
         * @apiNote Applications using deadlines, expressed as an {@link java.time.Instant},
         * can use {@link Duration#between Duration.between(Instant.now(), deadline)} to
         * compute the timeout for this method.
         *
         * @see #join()
         * @see CancelledByTimeoutException
         */
        Configuration withTimeout(Duration timeout);
    }

    /**
     * The exception {@linkplain Throwable#getCause() cause} when {@link #join() join()}
     * throws because the scope was {@linkplain StructuredTaskScope##Cancellation cancelled}
     * by a timeout.
     *
     * @since 27
     * @see Configuration#withTimeout(Duration)
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
    final class CancelledByTimeoutException extends RuntimeException {
        @java.io.Serial
        static final long serialVersionUID = 705788143955048766L;

        /**
         * Constructs a {@code CancelledByTimeoutException} with no detail message.
         */
        public CancelledByTimeoutException() { }

    }

    /**
     * Opens a new {@code StructuredTaskScope} that uses the given {@code Joiner} object
     * and the {@code Configuration} that is the result of applying the given operator to
     * the {@linkplain ##DefaultConfiguration default configuration}.
     *
     * <p> The {@code Joiner} specified to this method implements the desired policy and
     * produces the outcome (result or exception) for the {@link #join()} method when all
     * subtasks forked in the scope complete execution or the scope is {@linkplain
     * ##Cancellation cancelled}.
     *
     * <p> This method invokes the operator's {@link UnaryOperator#apply(Object) apply}
     * method with the default configuration to produce the configuration for the new
     * scope:
     * <ul>
     * <li> If the {@code apply} method returns a {@code Configuration} with a {@link
     * ThreadFactory}, set using {@link Configuration#withThreadFactory(ThreadFactory)
     * withThreadFactory(ThreadFactory)}, its {@link ThreadFactory#newThread(Runnable)
     * newThread(Runnable)} method will be invoked to create threads when {@linkplain
     * #fork(Callable) forking} subtasks in the scope. If a {@code ThreadFactory} is not set
     * then forking subtasks will create an unnamed virtual thread for each subtask. </li>
     * <li> If the {@code apply} method returns a {@code Configuration} with a timeout, set
     * using {@link Configuration#withTimeout(Duration) withTimeout(Duration)}, the timeout
     * will start when the scope is opened. If the timeout expires before or while waiting in
     * {@link #join()} then the scope will be {@linkplain ##Cancellation cancelled}. It is
     * {@link Joiner Joiner} specific as to whether the {@code join()} method returns a
     * result or throws an exception when a timeout occurs. If the outcome is an exception
     * then it will be thrown with a {@link CancelledByTimeoutException
     * CancelledByTimeoutException} as the {@linkplain Throwable#getCause() cause}. </li>
     * <li> If the {@code apply} method returns a {@code Configuration} with a name, set
     * using {@linkplain Configuration#withName(String) withName(String)}, the name will
     * be used for monitoring and management purposes. </li>
     * <li> If the {@code apply} method throws an exception or error then it is propagated
     * by this method. </li>
     * <li> If the {@code apply} method returns {@code null} then {@code NullPointerException}
     * is thrown. </li>
     * </ul>
     *
     * <p> The new scope is owned by the current thread. Only code executing in this
     * thread can {@linkplain #fork(Callable) fork}, {@linkplain #join() join}, or
     * {@linkplain #close close} the scope.
     *
     * <p> Construction captures the current thread's {@linkplain ScopedValue scoped
     * value} bindings for inheritance by threads forked in the scope.
     *
     * @param joiner the Joiner
     * @param configOperator the operator to produce the configuration
     * @return a new scope
     * @param <T> the result type of subtasks {@linkplain #fork(Callable) forked} in the scope
     * @param <R> the type of the result returned by the {@link #join() join()} method
     * @param <R_X> the type of the exception thrown by the {@link #join() join()} method
     * @since 26
     */
    static <T, R, R_X extends Throwable> StructuredTaskScope<T, R, R_X>
    open(Joiner<? super T, ? extends R, R_X> joiner, UnaryOperator<Configuration> configOperator) {
        return StructuredTaskScopeImpl.open(joiner, configOperator);
    }

    /**
     * Opens a new {@code StructuredTaskScope} that uses the given {@code Joiner} object.
     * The {@code Joiner} implements the desired policy and produces the outcome (result
     * or exception) for the {@link #join()} method when all subtasks forked in the scope
     * complete execution or the scope is {@linkplain ##Cancellation cancelled}.
     *
     * <p> The scope is created with the {@linkplain ##DefaultConfiguration default
     * configuration}. The default configuration has a {@code ThreadFactory} that creates
     * unnamed {@linkplain Thread##virtual-threads virtual threads}, does not name the
     * scope, and has no timeout.
     *
     * <p> The new scope is owned by the current thread. Only code executing in this
     * thread can {@linkplain #fork(Callable) fork}, {@linkplain #join() join}, or
     * {@linkplain #close close} the scope.
     *
     * <p> Construction captures the current thread's {@linkplain ScopedValue scoped
     * value} bindings for inheritance by threads forked in the scope.
     *
     * @implSpec This factory method is equivalent to invoking the
     * {@linkplain #open(Joiner, UnaryOperator) 2-arg open} method with the given Joiner
     * and the {@linkplain UnaryOperator#identity() identity operator}.
     *
     * @param joiner the Joiner
     * @return a new scope
     * @param <T> the result type of subtasks {@linkplain #fork(Callable) forked} in the scope
     * @param <R> the type of the result returned by the {@link #join() join()} method
     * @param <R_X> the type of the exception thrown by the {@link #join() join()} method
     * @since 25
     */
    static <T, R, R_X extends Throwable> StructuredTaskScope<T, R, R_X>
    open(Joiner<? super T, ? extends R, R_X> joiner) {
        return open(joiner, UnaryOperator.identity());
    }

    /**
     * Opens a new {@code StructuredTaskScope} that uses the {@code Configuration} that is
     * the result of applying the given operator to the {@linkplain ##DefaultConfiguration
     * default configuration}. This method invokes the operator's {@link
     * UnaryOperator#apply(Object) apply} method to produce the configuration for the new
     * scope, as specified by the {@linkplain #open(Joiner, UnaryOperator) 2-arg open}
     * method.
     *
     * <p> The {@link #join()} method of the new scope waits for all subtasks to succeed
     * or any subtask to fail. The {@code join() method} returns {@code null} if all
     * subtasks complete successfully. It throws {@link ExecutionException} if any subtask
     * fails, with the exception from the first subtask to fail as the {@linkplain
     * Throwable#getCause() cause}. If a {@linkplain Configuration#withTimeout(Duration)
     * timeout} is configured, and the timeout expires before or while waiting in the
     * {@code join()} method, it throws {@code ExecutionException} with a {@link
     * CancelledByTimeoutException CancelledByTimeoutException} as the cause.
     *
     * <p> The new scope is owned by the current thread. Only code executing in this
     * thread can {@linkplain #fork(Callable) fork}, {@linkplain #join() join}, or
     * {@linkplain #close close} the scope.
     *
     * <p> Construction captures the current thread's {@linkplain ScopedValue scoped
     * value} bindings for inheritance by threads forked in the scope.
     *
     * @implSpec This factory method is equivalent to invoking the {@linkplain
     * #open(Joiner, UnaryOperator) 2-arg open} method with a Joiner created with {@link
     * Joiner#awaitAllSuccessfulOrThrow() awaitAllSuccessfulOrThrow()} and the given
     * configuration operator.
     *
     * @param configOperator the operator to produce the configuration
     * @return a new scope
     * @param <T> the result type of subtasks forked in the scope
     * @since 27
     */
    static <T> StructuredTaskScope<T, Void, ExecutionException>
    open(UnaryOperator<Configuration> configOperator) {
        return open(Joiner.awaitAllSuccessfulOrThrow(), configOperator);
    }

    /**
     * Opens a new {@code StructuredTaskScope} where {@link #join()} waits for all subtasks
     * to succeed or any subtask to fail. The {@code join()} method returns {@code null}
     * if all subtasks complete successfully. It throws {@link ExecutionException} if any
     * subtask fails, with the exception from the first subtask to fail as the {@linkplain
     * Throwable#getCause() cause}.
     *
     * <p> The scope is created with the {@linkplain ##DefaultConfiguration default
     * configuration}. The default configuration has a {@code ThreadFactory} that creates
     * unnamed {@linkplain Thread##virtual-threads virtual threads}, does not name the
     * scope, and has no timeout.
     *
     * <p> The new scope is owned by the current thread. Only code executing in this
     * thread can {@linkplain #fork(Callable) fork}, {@linkplain #join() join}, or
     * {@linkplain #close close} the scope.
     *
     * <p> Construction captures the current thread's {@linkplain ScopedValue scoped
     * value} bindings for inheritance by threads forked in the scope.
     *
     * @implSpec This factory method is equivalent to invoking the
     * {@linkplain #open(Joiner, UnaryOperator) 2-arg open} method with a Joiner created
     * with {@link Joiner#awaitAllSuccessfulOrThrow() awaitAllSuccessfulOrThrow()} and
     * the {@linkplain UnaryOperator#identity() identity operator}.
     *
     * @param <T> the result type of subtasks
     * @return a new scope
     * @since 25
     */
    static <T> StructuredTaskScope<T, Void, ExecutionException> open() {
        return open(Joiner.awaitAllSuccessfulOrThrow(), UnaryOperator.identity());
    }

    /**
     * Forks a subtask by starting a new thread in this scope to execute a value-returning
     * method. The new thread executes the subtask concurrently with the current thread.
     * The parameter to this method is a {@link Callable}, the new thread executes its
     * {@link Callable#call() call()} method. The thread inherits the current thread's
     * {@linkplain ScopedValue scoped value bindings} that must match the bindings
     * captured when the scope was opened.
     *
     * <p> If the scope was opened with a function to produce the {@link Configuration
     * Configuration} for the scope, and a {@link ThreadFactory} is {@linkplain
     * Configuration#withThreadFactory(ThreadFactory) set}, then its {@link
     * ThreadFactory#newThread(Runnable) newThread(Runnable)} method is invoked to create
     * the thread that will execute the subtask. {@link RejectedExecutionException} is
     * thrown if the {@code newThread(Runnable)} method returns {@code null}.
     * If a {@code ThreadFactory} is not set, the {@code fork(Callable)} method creates an
     * unnamed {@linkplain Thread##virtual-threads virtual thread} to execute the subtask.
     *
     * <p> This method returns a {@link Subtask Subtask} object as a handle to the
     * <em>forked subtask</em>. If the scope is {@linkplain ##Cancellation cancelled}, the
     * {@code Subtask} is returned in the {@link Subtask.State#UNAVAILABLE UNAVAILABLE}
     * state without creating a thread. In some usages, {@code Subtask} object will be used
     * by the "main" task (the scope owner) to get the subtask's outcome (result or
     * exception) after it has invoked {@link #join() join()} to wait for all subtasks to
     * complete. In other usages, the scope is created with a {@link Joiner Joiner} that
     * produces the outcome for the main task to process after joining. A {@code Joiner}
     * that produces a result reduces the need for <i>bookkeeping</i> and the need for the
     * main task to retain references to {@code Subtask} objects for correlation purposes.
     *
     * <p> To ensure correct usage, the {@link Subtask#get() Subtask.get()} method may
     * only be called by the scope owner to get the result of a successful subtask after
     * it has waited for subtasks to complete with the {@link #join() join()} method.
     * Similarly, the {@link Subtask#exception() Subtask.exception()} method may only be
     * called by the scope owner to get the exception (or error) of a failed subtask after
     * it has joined.
     *
     * <p> This method may only be invoked by the scope owner.
     *
     * <p> <b>Joiner Implementers:</b>
     *
     * <p> If the scope was opened with a {@link Joiner Joiner}, its {@link
     * Joiner#onFork(Subtask) onFork(Subtask)} method is invoked with the newly created
     * {@code Subtask} object before the thread is created. It is invoked with the subtask
     * in the {@code UNAVAILABLE} state. If the method throws an exception or error then
     * it is propagated by the {@code fork(Callable)} method without creating a thread to
     * execute the subtask. If the scope is not already cancelled, and the {@code onFork}
     * method return {@code false}, then a thread is created and {@linkplain Thread#start()
     * scheduled} to start execution of the subtask. If the scope is cancelled, or the
     * {@code onFork} method returns {@code true} to cancel the scope, {@code fork(Callable)}
     * returns the subtask in the {@code UNAVAILABLE} state.
     *
     * <p> If the subtask executes and completes (successfully or with an exception) before
     * the scope is cancelled, then the thread invokes the Joiner's {@link
     * Joiner#onComplete(Subtask) onComplete(Subtask)} method with the subtask in the
     * {@link Subtask.State#SUCCESS SUCCESS} or {@link Subtask.State#FAILED FAILED} state.
     * If the {@code onComplete(Subtask}) method returns {@code true} then the scope is
     * cancelled, if not already cancelled. If the {@code onComplete(Subtask)} method
     * completes with an exception or error, then the thread executes the {@linkplain
     * Thread.UncaughtExceptionHandler uncaught exception handler} before the thread
     * terminates.
     *
     * @param task the value-returning task for the thread to execute
     * @param <U> the result type
     * @return the subtask
     * @throws WrongThreadException if the current thread is not the scope owner
     * @throws IllegalStateException if the owner has already {@linkplain #join() joined}
     * or the scope is closed
     * @throws StructureViolationException if the current scoped value bindings are not
     * the same as when the scope was created
     * @throws RejectedExecutionException if the thread factory rejected creating a
     * thread to execute the subtask
     * @see Thread##inheritance Inheritance When Creating Threads
     */
    <U extends T> Subtask<U> fork(Callable<? extends U> task);

    /**
     * Forks a subtask by starting a new thread in this scope to execute a method that
     * does not return a result.
     *
     * <p> This method works exactly the same as {@link #fork(Callable)} except that the
     * parameter to this method is a {@link Runnable}, the new thread executes its
     * {@link Runnable#run() run()} method, and {@link Subtask#get() Subtask.get()} returns
     * {@code null} if the subtask completes successfully.
     *
     * @param task the task for the thread to execute
     * @param <U> the result type
     * @return the subtask
     * @throws WrongThreadException if the current thread is not the scope owner
     * @throws IllegalStateException if the owner has already {@linkplain #join() joined}
     * or the scope is closed
     * @throws StructureViolationException if the current scoped value bindings are not
     * the same as when the scope was created
     * @throws RejectedExecutionException if the thread factory rejected creating a
     * thread to execute the subtask
     * @since 25
     */
    <U extends T> Subtask<U> fork(Runnable task);

    /**
     * Returns a result, or throws, after waiting for all subtasks to complete or the
     * scope to be {@linkplain ##Cancellation cancelled}.
     *
     * <p> If the scope was opened with the {@link #open()} or {@link #open(UnaryOperator)}
     * method, then {@code join()} waits for all subtasks to succeed or any subtask to fail.
     * It returns {@code null} if all subtasks complete successfully. It throws {@link
     * ExecutionException} if any subtask fails, with exception from the first subtask to
     * fail as the {@linkplain Throwable#getCause() cause}. If a {@linkplain
     * Configuration#withTimeout(Duration) timeout} is configured and the timeout expires
     * before or while waiting, it throws {@code ExecutionException} with a {@link
     * CancelledByTimeoutException CancelledByTimeoutException} as the cause.
     *
     * <p> If the scope was opened with a {@link Joiner Joiner}, it is invoked after
     * waiting or cancellation to produce the outcome (result or exception). This includes
     * the <em>timeout case</em> where a timeout is configured and it expires before or
     * while waiting in the {@code join()} method.
     *
     * <p> This method may only be invoked by the scope owner. It may only be invoked once
     * to get the result, exception or timeout outcome, unless the previous invocation
     * resulted in an {@code InterruptedException} being thrown.
     *
     * <p> <b>Joiner Implementers:</b>
     *
     * <p> When all subtasks complete, or the scope cancelled, this method invokes the
     * {@code Joiner}'s {@link Joiner#result() result()} or {@link Joiner#timeout() timeout()}
     * method to produce the outcome (result or exception) for the {@code join()} method.
     * The {@code result()} method is invoked for the "no timeout" case. The {@code timeout()}
     * method is invoked for the timeout case. If the outcome for the timeout case is an
     * exception then it is thrown with a {@code CancelledByTimeoutException} as the cause.
     *
     * @apiNote When the outcome is an exception, the {@linkplain Throwable#getStackTrace()
     * stack trace} will be the stack trace of the call to the {@code join()} method.
     * Its {@linkplain Throwable#getCause() cause} will typically be the exception
     * thrown by a failed subtask with the stack trace of the failed subtask.
     *
     * @return the result
     * @throws WrongThreadException if the current thread is not the scope owner
     * @throws IllegalStateException if already joined or this scope is closed
     * @throws R_X when the outcome is an exception
     * @throws InterruptedException if the current thread is {@linkplain Thread#interrupt()
     * interrupted} while waiting or this method is invoked with the current thread's
     * {@linkplain Thread#isInterrupted() interrupted status} set. The current thread's
     * interrupted status is cleared when this exception is thrown.
     * @since 25
     * @see Thread##thread-interruption Thread Interruption
     */
    R join() throws R_X, InterruptedException;

    /**
     * {@return {@code true} if this scope is {@linkplain ##Cancellation cancelled} or in
     * the process of being cancelled, otherwise {@code false}}
     *
     * <p> Cancelling the scope prevents new threads from starting in the scope and
     * {@linkplain Thread#interrupt() interrupts} threads executing unfinished subtasks.
     * It may take some time before the interrupted threads finish execution; this
     * method may return {@code true} before all threads have been interrupted or before
     * all threads have finished.
     *
     * @apiNote A task with a lengthy "forking phase" (the code in the {@code try} block
     * that forks subtasks before the {@link #join()} method is invoked) may use this
     * method to avoid doing work in cases where the scope is cancelled by the completion
     * of a previously forked subtask or a timeout.
     *
     * @since 25
     */
    boolean isCancelled();

    /**
     * Closes this scope.
     *
     * <p> This method first {@linkplain ##Cancellation cancels} the scope, if not already
     * cancelled. This {@linkplain Thread#interrupt() interrupts} the threads executing
     * unfinished subtasks. This method then waits for all threads to finish. If interrupted
     * while waiting then it will continue to wait until the threads finish, before
     * completing with the {@linkplain Thread#isInterrupted() interrupted status} set.
     *
     * <p> This method may only be invoked by the scope owner. If the scope
     * is already closed then the scope owner invoking this method has no effect.
     *
     * <p> A {@code StructuredTaskScope} is intended to be used in a <em>structured
     * manner</em>. If this method is called to close a scope before nested scopes are
     * closed then it closes the underlying construct of each nested scope (in the reverse
     * order that they were created in), closes this scope, and then throws {@link
     * StructureViolationException}. Similarly, if this method is called to close a scope
     * while executing with {@linkplain ScopedValue scoped value} bindings, and the scope
     * was created before the scoped values were bound, then {@code StructureViolationException}
     * is thrown after closing the scope. If a thread terminates without first closing
     * scopes that it owns then termination will cause the underlying construct of each
     * of its open scopes to be closed. Closing is performed in the reverse order that the
     * scopes were created in. Thread termination may therefore be delayed when the scope
     * owner has to wait for threads forked in these scopes to finish.
     *
     * @throws IllegalStateException thrown after closing the scope if the scope
     * owner did not attempt to join after forking
     * @throws WrongThreadException if the current thread is not the scope owner
     * @throws StructureViolationException if a structure violation was detected
     */
    @Override
    void close();
}
