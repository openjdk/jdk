/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jdk.internal.javac.PreviewFeature;

/**
 * An API for <em>structured concurrency</em>. {@code StructuredTaskScope} supports cases
 * where execution of a <em>task</em> (a unit of work) splits into several concurrent
 * subtasks, and where the subtasks must complete before the task continues. A {@code
 * StructuredTaskScope} can be used to ensure that the lifetime of a concurrent operation
 * is confined by a <em>syntax block</em>, similar to that of a sequential operation in
 * structured programming.
 *
 * <p> {@code StructuredTaskScope} defines the static method {@link #open() open} to open
 * a new {@code StructuredTaskScope} and the {@link #close() close} method to close it.
 * The API is designed to be used with the {@code try}-with-resources statement where
 * the {@code StructuredTaskScope} is opened as a resource and then closed automatically.
 * The code inside the block uses the {@link #fork(Callable) fork} method to fork subtasks.
 * After forking, it uses the {@link #join() join} method to wait for all subtasks to
 * finish (or some other outcome) as a single operation. Forking a subtask starts a new
 * {@link Thread} to run the subtask. The thread executing the task does not continue
 * beyond the {@code close} method until all threads started to execute subtasks have finished.
 * To ensure correct usage, the {@code fork}, {@code join} and {@code close} methods may
 * only be invoked by the <em>owner thread</em> (the thread that opened the {@code
 * StructuredTaskScope}), the {@code fork} method may not be called after {@code join},
 * the {@code join} method may only be invoked once, and the {@code close} method throws
 * an exception after closing if the owner did not invoke the {@code join} method after
 * forking subtasks.
 *
 * <p> As a first example, consider a task that splits into two subtasks to concurrently
 * fetch resources from two URL locations "left" and "right". Both subtasks may complete
 * successfully, one subtask may succeed and the other may fail, or both subtasks may
 * fail. The task in this example is interested in the successful result from both
 * subtasks. It waits in the {@link #join() join} method for both subtasks to complete
 * successfully or for either subtask to fail.
 * {@snippet lang=java :
 *    // @link substring="open" target="#open()" :
 *    try (var scope = StructuredTaskScope.open()) {
 *
 *        // @link substring="fork" target="#fork(Callable)" :
 *        Subtask<String> subtask1 = scope.fork(() -> query(left));
 *        Subtask<Integer> subtask2 = scope.fork(() -> query(right));
 *
 *        // throws if either subtask fails
 *        scope.join();  // @link substring="join" target="#join()"
 *
 *        // both subtasks completed successfully
 *        // @link substring="get" target="Subtask#get()" :
 *        return new MyResult(subtask1.get(), subtask2.get());
 *
 *    // @link substring="close" target="#close()" :
 *    } // close
 * }
 *
 * <p> If both subtasks complete successfully then the {@code join} method completes
 * normally and the task uses the {@link Subtask#get() Subtask.get()} method to get
 * the result of each subtask. If one of the subtasks fails then the other subtask is
 * cancelled (this will {@linkplain Thread#interrupt() interrupt} the thread executing the
 * other subtask) and the {@code join} method throws {@link FailedException} with the
 * exception from the failed subtask as the {@linkplain Throwable#getCause() cause}.
 *
 * <p> To allow for cancellation, subtasks must be coded so that they finish as soon as
 * possible when interrupted. Subtasks that do not respond to interrupt, e.g. block on
 * methods that are not interruptible, may delay the closing of a scope indefinitely. The
 * {@link #close() close} method always waits for threads executing subtasks to finish,
 * even if the scope is cancelled, so execution cannot continue beyond the {@code close}
 * method until the interrupted threads finish.
 *
 * <p> In the example, the subtasks produce results of different types ({@code String} and
 * {@code Integer}). In other cases the subtasks may all produce results of the same type.
 * If the example had used {@code StructuredTaskScope.<String>open()} then it could
 * only be used to fork subtasks that return a {@code String} result.
 *
 * <h2>Joiners</h2>
 *
 * <p> In the example above, the task fails if any subtask fails. If all subtasks
 * succeed then the {@code join} method completes normally. Other policy and outcome is
 * supported by creating a {@code StructuredTaskScope} with a {@link Joiner} that
 * implements the desired policy. A {@code Joiner} handles subtask completion and produces
 * the outcome for the {@link #join() join} method. In the example above, {@code join}
 * returns {@code null}. Depending on the {@code Joiner}, {@code join} may return a
 * result, a stream of elements, or some other object. The {@code Joiner} interface defines
 * factory methods to create {@code Joiner}s for some common cases.
 *
 * <p> A {@code Joiner} may <a id="Cancallation">cancel</a> the scope (sometimes called
 * "short-circuiting") when some condition is reached that does not require the result of
 * subtasks that are still executing. Cancelling the scope prevents new threads from being
 * started to execute further subtasks, {@linkplain Thread#interrupt() interrupts} the
 * threads executing subtasks that have not completed, and causes the {@code join} method
 * to wakeup with the outcome (result or exception). In the above example, the outcome is
 * that {@code join} completes with a result of {@code null} when all subtasks succeed.
 * The scope is cancelled if any of the subtasks fail and {@code join} throws {@code
 * FailedException} with the exception from the failed subtask as the cause. Other {@code
 * Joiner} implementations may cancel the scope for other reasons.
 *
 * <p> Now consider another example that splits into two subtasks. In this example,
 * each subtask produces a {@code String} result and the task is only interested in
 * the result from the first subtask to complete successfully. The example uses {@link
 * Joiner#anySuccessfulResultOrThrow() Joiner.anySuccessfulResultOrThrow()} to
 * create a {@code Joiner} that makes available the result of the first subtask to
 * complete successfully. The type parameter in the example is "{@code String}" so that
 * only subtasks that return a {@code String} can be forked.
 * {@snippet lang=java :
 *    // @link substring="open" target="#open(Joiner)" :
 *    try (var scope = StructuredTaskScope.open(Joiner.<String>anySuccessfulResultOrThrow())) {
 *
 *        scope.fork(callable1);
 *        scope.fork(callable2);
 *
 *        // throws if both subtasks fail
 *        String firstResult = scope.join();
 *
 *    }
 * }
 *
 * <p> In the example, the task forks the two subtasks, then waits in the {@code
 * join} method for either subtask to complete successfully or for both subtasks to fail.
 * If one of the subtasks completes successfully then the {@code Joiner} causes the other
 * subtask to be cancelled (this will interrupt the thread executing the subtask), and
 * the {@code join} method returns the result from the successful subtask. Cancelling the
 * other subtask avoids the task waiting for a result that it doesn't care about. If
 * both subtasks fail then the {@code join} method throws {@code FailedException} with the
 * exception from one of the subtasks as the {@linkplain Throwable#getCause() cause}.
 *
 * <p> Whether code uses the {@code Subtask} returned from {@code fork} will depend on
 * the {@code Joiner} and usage. Some {@code Joiner} implementations are suited to subtasks
 * that return results of the same type and where the {@code join} method returns a result
 * for the task to use. Code that forks subtasks that return results of different
 * types, and uses a {@code Joiner} such as {@code Joiner.awaitAllSuccessfulOrThrow()} that
 * does not return a result, will use {@link Subtask#get() Subtask.get()} after joining.
 *
 * <h2>Exception handling</h2>
 *
 * <p> A {@code StructuredTaskScope} is opened with a {@link Joiner Joiner} that
 * handles subtask completion and produces the outcome for the {@link #join() join} method.
 * In some cases, the outcome will be a result, in other cases it will be an exception.
 * If the outcome is an exception then the {@code join} method throws {@link
 * FailedException} with the exception as the {@linkplain Throwable#getCause()
 * cause}. For many {@code Joiner} implementations, the exception will be an exception
 * thrown by a subtask that failed. In the case of {@link Joiner#allSuccessfulOrThrow()
 * allSuccessfulOrThrow} and {@link Joiner#awaitAllSuccessfulOrThrow() awaitAllSuccessfulOrThrow}
 * for example, the exception is from the first subtask to fail.
 *
 * <p> Many of the details for how exceptions are handled will depend on usage. In some
 * cases it may be useful to add a {@code catch} block to the {@code try}-with-resources
 * statement to catch {@code FailedException}. The exception handling may use {@code
 * instanceof} with pattern matching to handle specific causes.
 * {@snippet lang=java :
 *    try (var scope = StructuredTaskScope.open()) {
 *
 *        ..
 *
 *    } catch (StructuredTaskScope.FailedException e) {
 *
 *        Throwable cause = e.getCause();
 *        switch (cause) {
 *            case IOException ioe -> ..
 *            default -> ..
 *        }
 *
 *    }
 * }
 * In other cases it may not be useful to catch {@code FailedException} but instead leave
 * it to propagate to the configured {@linkplain Thread.UncaughtExceptionHandler uncaught
 * exception handler} for logging purposes.
 *
 * <p> For cases where a specific exception triggers the use of a default result then it
 * may be more appropriate to handle this in the subtask itself rather than the subtask
 * failing and the scope owner handling the exception.
 *
 * <h2>Configuration</h2>
 *
 *
 * A {@code StructuredTaskScope} is opened with {@linkplain Configuration configuration}
 * that consists of a {@link ThreadFactory} to create threads, an optional name for
 * monitoring and management purposes, and an optional timeout.
 *
 * <p> The {@link #open()} and {@link #open(Joiner)} methods create a {@code StructuredTaskScope}
 * with the <a id="DefaultConfiguration"> <em>default configuration</em></a>. The default
 * configuration has a {@code ThreadFactory} that creates unnamed
 * <a href="{@docRoot}/java.base/java/lang/Thread.html#virtual-threads">virtual threads</a>,
 * is unnamed for monitoring and management purposes, and has no timeout.
 *
 * <p> The 2-arg {@link #open(Joiner, Function) open} method can be used to create a
 * {@code StructuredTaskScope} that uses a different {@code ThreadFactory}, has a name for
 * the purposes of monitoring and management, or has a timeout that cancels the scope if
 * the timeout expires before or while waiting for subtasks to complete. The {@code open}
 * method is called with a {@linkplain Function function} that is applied to the default
 * configuration and returns a {@link Configuration Configuration} for the
 * {@code StructuredTaskScope} under construction.
 *
 * <p> The following example opens a new {@code StructuredTaskScope} with a {@code
 * ThreadFactory} that creates virtual threads {@linkplain Thread#setName(String) named}
 * "duke-0", "duke-1" ...
 * {@snippet lang = java:
 *    // @link substring="name" target="Thread.Builder#name(String, long)" :
 *    ThreadFactory factory = Thread.ofVirtual().name("duke-", 0).factory();
 *
 *    // @link substring="withThreadFactory" target="Configuration#withThreadFactory(ThreadFactory)" :
 *    try (var scope = StructuredTaskScope.open(joiner, cf -> cf.withThreadFactory(factory))) {
 *
 *        scope.fork( .. );   // runs in a virtual thread with name "duke-0"
 *        scope.fork( .. );   // runs in a virtual thread with name "duke-1"
 *
 *        scope.join();
 *
 *     }
 *}
 *
 * <p> A second example sets a timeout, represented by a {@link Duration}. The timeout
 * starts when the new scope is opened. If the timeout expires before the {@code join}
 * method has completed then the scope is <a href="#Cancallation">cancelled</a>. This
 * interrupts the threads executing the two subtasks and causes the {@link #join() join}
 * method to throw {@link TimeoutException}.
 * {@snippet lang=java :
 *    Duration timeout = Duration.ofSeconds(10);
 *
 *    // @link substring="allSuccessfulOrThrow" target="Joiner#allSuccessfulOrThrow()" :
 *    try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow(),
 *    // @link substring="withTimeout" target="Configuration#withTimeout(Duration)" :
 *                                              cf -> cf.withTimeout(timeout))) {
 *
 *        scope.fork(callable1);
 *        scope.fork(callable2);
 *
 *        List<String> result = scope.join()
 *                                   .map(Subtask::get)
 *                                   .toList();
 *
 *   }
 * }
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
 * executing the task then that binding is inherited by the threads created to
 * execute the subtasks. The thread executing the task does not continue beyond the
 * {@link #close() close} method until all threads executing the subtasks have finished.
 * This ensures that the {@code ScopedValue} is not reverted to being {@linkplain
 * ScopedValue#isBound() unbound} (or its previous value) while subtasks are executing.
 * In addition to providing a safe and efficient means to inherit a value into subtasks,
 * the inheritance allows sequential code using {@code ScopedValue} be refactored to use
 * structured concurrency.
 *
 * <p> To ensure correctness, opening a new {@code StructuredTaskScope} captures the
 * current thread's scoped value bindings. These are the scoped values bindings that are
 * inherited by the threads created to execute subtasks in the scope. Forking a
 * subtask checks that the bindings in effect at the time that the subtask is forked
 * match the bindings when the {@code StructuredTaskScope} was created. This check ensures
 * that a subtask does not inherit a binding that is reverted in the task before the
 * subtask has completed.
 *
 * <p> A {@code ScopedValue} that is shared across threads requires that the value be an
 * immutable object or for all access to the value to be appropriately synchronized.
 *
 * <p> The following example demonstrates the inheritance of scoped value bindings. The
 * scoped value USERNAME is bound to the value "duke" for the bounded period of a lambda
 * expression by the thread executing it. The code in the block opens a {@code
 * StructuredTaskScope} and forks two subtasks, it then waits in the {@code join} method
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
 * <p> A scoped value inherited into a subtask may be
 * <a href="{@docRoot}/java.base/java/lang/ScopedValue.html#rebind">rebound</a> to a new
 * value in the subtask for the bounded execution of some method executed in the subtask.
 * When the method completes, the value of the {@code ScopedValue} reverts to its previous
 * value, the value inherited from the thread executing the task.
 *
 * <p> A subtask may execute code that itself opens a new {@code StructuredTaskScope}.
 * A task executing in thread T1 opens a {@code StructuredTaskScope} and forks a
 * subtask that runs in thread T2. The scoped value bindings captured when T1 opens the
 * scope are inherited into T2. The subtask (in thread T2) executes code that opens a
 * new {@code StructuredTaskScope} and forks a subtask that runs in thread T3. The scoped
 * value bindings captured when T2 opens the scope are inherited into T3. These
 * include (or may be the same) as the bindings that were inherited from T1. In effect,
 * scoped values are inherited into a tree of subtasks, not just one level of subtask.
 *
 * <h2>Memory consistency effects</h2>
 *
 * <p> Actions in the owner thread of a {@code StructuredTaskScope} prior to
 * {@linkplain #fork forking} of a subtask
 * <a href="{@docRoot}/java.base/java/util/concurrent/package-summary.html#MemoryVisibility">
 * <i>happen-before</i></a> any actions taken by that subtask, which in turn
 * <i>happen-before</i> the subtask result is {@linkplain Subtask#get() retrieved}.
 *
 * <h2>General exceptions</h2>
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to a method in this
 * class will cause a {@link NullPointerException} to be thrown.
 *
 * @param <T> the result type of subtasks executed in the scope
 * @param <R> the result type of the scope
 *
 * @jls 17.4.5 Happens-before Order
 * @since 21
 */
@PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
public sealed interface StructuredTaskScope<T, R>
        extends AutoCloseable
        permits StructuredTaskScopeImpl {

    /**
     * Represents a subtask forked with {@link #fork(Callable)} or {@link #fork(Runnable)}.
     *
     * <p> Code that forks subtasks can use the {@link #get() get()} method after {@linkplain
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
         * Represents the state of a subtask.
         * @see Subtask#state()
         * @since 21
         */
        @PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
        enum State {
            /**
             * The subtask result or exception is not available. This state indicates that
             * the subtask was forked but has not completed, it completed after the scope
             * was cancelled, or it was forked after the scoped was cancelled (in which
             * case a thread was not created to execute the subtask).
             */
            UNAVAILABLE,
            /**
             * The subtask completed successfully. The {@link Subtask#get() Subtask.get()}
             * method can be used to get the result. This is a terminal state.
             */
            SUCCESS,
            /**
             * The subtask failed with an exception. The {@link Subtask#exception()
             * Subtask.exception()} method can be used to get the exception. This is a
             * terminal state.
             */
            FAILED,
        }

        /**
         * {@return the subtask state}
         */
        State state();

        /**
         * Returns the result of this subtask if it completed successfully. If the subtask
         * was forked with {@link #fork(Callable) fork(Callable)} then the result from the
         * {@link Callable#call() call} method is returned. If the subtask was forked with
         * {@link #fork(Runnable) fork(Runnable)} then {@code null} is returned.
         *
         * <p> Code executing in the scope owner thread can use this method to get the
         * result of a successful subtask only after it has {@linkplain #join() joined}.
         *
         * <p> Code executing in the {@code Joiner} {@link Joiner#onComplete(Subtask)
         * onComplete} method should test that the {@linkplain #state() subtask state} is
         * {@link State#SUCCESS SUCCESS} before using this method to get the result.
         *
         * @return the possibly-null result
         * @throws IllegalStateException if the subtask has not completed, did not complete
         * successfully, or the current thread is the scope owner invoking this
         * method before {@linkplain #join() joining}
         * @see State#SUCCESS
         */
        T get();

        /**
         * {@return the exception or error thrown by this subtask if it failed}
         * If the subtask was forked with {@link #fork(Callable) fork(Callable)} then the
         * exception or error thrown by the {@link Callable#call() call} method is returned.
         * If the subtask was forked with {@link #fork(Runnable) fork(Runnable)} then the
         * exception or error thrown by the {@link Runnable#run() run} method is returned.
         *
         * <p> Code executing in the scope owner thread can use this method to get the
         * exception thrown by a failed subtask only after it has {@linkplain #join() joined}.
         *
         * <p> Code executing in a {@code Joiner} {@link Joiner#onComplete(Subtask)
         * onComplete} method should test that the {@linkplain #state() subtask state} is
         * {@link State#FAILED FAILED} before using this method to get the exception.
         *
         * @throws IllegalStateException if the subtask has not completed, completed with
         * a result, or the current thread is the scope owner invoking this method
         * before {@linkplain #join() joining}
         * @see State#FAILED
         */
        Throwable exception();
    }

    /**
     * An object used with a {@link StructuredTaskScope} to handle subtask completion and
     * produce the result for the scope owner waiting in the {@link #join() join} method
     * for subtasks to complete.
     *
     * <p> Joiner defines static methods to create {@code Joiner} objects for common cases:
     * <ul>
     *   <li> {@link #allSuccessfulOrThrow() allSuccessfulOrThrow()} creates a {@code Joiner}
     *   that yields a stream of the completed subtasks for {@code join} to return when
     *   all subtasks complete successfully. It cancels the scope and causes {@code join}
     *   to throw if any subtask fails.
     *   <li> {@link #anySuccessfulResultOrThrow() anySuccessfulResultOrThrow()} creates a
     *   {@code Joiner} that yields the result of the first subtask to succeed for {@code
     *   join} to return. It causes {@code join} to throw if all subtasks fail.
     *   <li> {@link #awaitAllSuccessfulOrThrow() awaitAllSuccessfulOrThrow()} creates a
     *   {@code Joiner} that waits for all successful subtasks. It cancels the scope and
     *   causes {@code join} to throw if any subtask fails.
     *   <li> {@link #awaitAll() awaitAll()} creates a {@code Joiner} that waits for all
     *   subtasks. It does not cancel the scope or cause {@code join} to throw.
     * </ul>
     *
     * <p> In addition to the methods to create {@code Joiner} objects for common cases,
     * the {@link #allUntil(Predicate) allUntil(Predicate)} method is defined to create a
     * {@code Joiner} that yields a stream of all subtasks. It is created with a {@link
     * Predicate Predicate} that determines if the scope should continue or be cancelled.
     * This {@code Joiner} can be built upon to create custom policies that cancel the
     * scope based on some condition.
     *
     * <p> More advanced policies can be developed by implementing the {@code Joiner}
     * interface. The {@link #onFork(Subtask)} method is invoked when subtasks are forked.
     * The {@link #onComplete(Subtask)} method is invoked when subtasks complete with a
     * result or exception. These methods return a {@code boolean} to indicate if scope
     * should be cancelled. These methods can be used to collect subtasks, results, or
     * exceptions, and control when to cancel the scope. The {@link #result()} method
     * must be implemented to produce the result (or exception) for the {@code join}
     * method.
     *
     * <p> Unless otherwise specified, passing a {@code null} argument to a method
     * in this class will cause a {@link NullPointerException} to be thrown.
     *
     * @implSpec Implementations of this interface must be thread safe. The {@link
     * #onComplete(Subtask)} method defined by this interface may be invoked by several
     * threads concurrently.
     *
     * @apiNote It is very important that a new {@code Joiner} object is created for each
     * {@code StructuredTaskScope}. {@code Joiner} objects should never be shared with
     * different scopes or re-used after a task is closed.
     *
     * <p> Designing a {@code Joiner} should take into account the code at the use-site
     * where the results from the {@link StructuredTaskScope#join() join} method are
     * processed. It should be clear what the {@code Joiner} does vs. the application
     * code at the use-site. In general, the {@code Joiner} implementation is not the
     * place for "business logic". A {@code Joiner} should be designed to be as general
     * purpose as possible.
     *
     * @param <T> the result type of subtasks executed in the scope
     * @param <R> the result type of the scope
     * @since 25
     * @see #open(Joiner)
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
    @FunctionalInterface
    interface Joiner<T, R> {
        /**
         * Invoked by {@link #fork(Callable) fork(Callable)} and {@link #fork(Runnable)
         * fork(Runnable)} when forking a subtask. The method is invoked from the task
         * owner thread. The method is invoked before a thread is created to run the
         * subtask.
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
        default boolean onFork(Subtask<? extends T> subtask) {
            if (subtask.state() != Subtask.State.UNAVAILABLE) {
                throw new IllegalArgumentException("Subtask not in UNAVAILABLE state");
            }
            return false;
        }

        /**
         * Invoked by the thread started to execute a subtask after the subtask completes
         * successfully or fails with an exception. This method is not invoked if a
         * subtask completes after the scope is cancelled.
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
        default boolean onComplete(Subtask<? extends T> subtask) {
            if (subtask.state() == Subtask.State.UNAVAILABLE) {
                throw new IllegalArgumentException("Subtask has not completed");
            }
            return false;
        }

        /**
         * Invoked by the {@link #join() join()} method to produce the result (or exception)
         * after waiting for all subtasks to complete or the scope cancelled. The result
         * from this method is returned by the {@code join} method. If this method throws,
         * then {@code join} throws {@link FailedException} with the exception thrown by
         * this method as the cause.
         *
         * <p> In normal usage, this method will be called at most once by the {@code join}
         * method to produce the result (or exception). The behavior of this method when
         * invoked directly, and invoked more than once, is undefined. Where possible, an
         * implementation should return an equal result (or throw the same exception) on
         * second or subsequent calls to produce the outcome.
         *
         * @apiNote This method is invoked by the {@code join} method. It should not be
         * invoked directly.
         *
         * @return the result
         * @throws Throwable the exception
         */
        R result() throws Throwable;

        /**
         * {@return a new Joiner object that yields a stream of all subtasks when all
         * subtasks complete successfully}
         * The {@code Joiner} <a href="StructuredTaskScope.html#Cancallation">cancels</a>
         * the scope and causes {@code join} to throw if any subtask fails.
         *
         * <p> If all subtasks complete successfully, the joiner's {@link Joiner#result()}
         * method returns a stream of all subtasks in the order that they were forked.
         * If any subtask failed then the {@code result} method throws the exception from
         * the first subtask to fail.
         *
         * @apiNote Joiners returned by this method are suited to cases where all subtasks
         * return a result of the same type. Joiners returned by {@link
         * #awaitAllSuccessfulOrThrow()} are suited to cases where the subtasks return
         * results of different types.
         *
         * @param <T> the result type of subtasks
         */
        static <T> Joiner<T, Stream<Subtask<T>>> allSuccessfulOrThrow() {
            return new Joiners.AllSuccessful<>();
        }

        /**
         * {@return a new Joiner object that yields the result of any subtask that
         * completed successfully}
         * The {@code Joiner} causes {@code join} to throw if all subtasks fail.
         *
         * <p> The joiner's {@link Joiner#result()} method returns the result of a subtask
         * that completed successfully. If all subtasks fail then the {@code result} method
         * throws the exception from one of the failed subtasks. The {@code result} method
         * throws {@code NoSuchElementException} if no subtasks were forked.
         *
         * @param <T> the result type of subtasks
         */
        static <T> Joiner<T, T> anySuccessfulResultOrThrow() {
            return new Joiners.AnySuccessful<>();
        }

        /**
         * {@return a new Joiner object that waits for subtasks to complete successfully}
         * The {@code Joiner} <a href="StructuredTaskScope.html#Cancallation">cancels</a>
         * the scope and causes {@code join} to throw if any subtask fails.
         *
         * <p> The joiner's {@link Joiner#result() result} method returns {@code null}
         * if all subtasks complete successfully, or throws the exception from the first
         * subtask to fail.
         *
         * @apiNote Joiners returned by this method are suited to cases where subtasks
         * return results of different types. Joiners returned by {@link #allSuccessfulOrThrow()}
         * are suited to cases where the subtasks return a result of the same type.
         *
         * @param <T> the result type of subtasks
         */
        static <T> Joiner<T, Void> awaitAllSuccessfulOrThrow() {
            return new Joiners.AwaitSuccessful<>();
        }

        /**
         * {@return a new Joiner object that waits for all subtasks to complete}
         * The {@code Joiner} does not cancel the scope if a subtask fails.
         *
         * <p> The joiner's {@link Joiner#result() result} method returns {@code null}.
         *
         * @apiNote This Joiner is useful for cases where subtasks make use of
         * <em>side-effects</em> rather than return results or fail with exceptions.
         * The {@link #fork(Runnable) fork(Runnable)} method can be used to fork subtasks
         * that do not return a result.
         *
         * <p> This Joiner can also be used for <em>fan-in</em> scenarios where subtasks
         * are forked to handle incoming connections and the number of subtasks is unbounded.
         * In this example, the thread executing the {@code acceptLoop} method will only
         * stop when interrupted or the listener socket is closed asynchronously.
         * {@snippet lang=java :
         *   void acceptLoop(ServerSocket listener) throws IOException, InterruptedException {
         *       try (var scope = StructuredTaskScope.open(Joiner.<Socket>awaitAll())) {
         *           while (true) {
         *               Socket socket = listener.accept();
         *               scope.fork(() -> handle(socket));
         *           }
         *       }
         *   }
         * }
         *
         * @param <T> the result type of subtasks
         */
        static <T> Joiner<T, Void> awaitAll() {
            // ensure that new Joiner object is returned
            return new Joiner<T, Void>() {
                @Override
                public Void result() {
                    return null;
                }
            };
        }

        /**
         * {@return a new Joiner object that yields a stream of all subtasks when all
         * subtasks complete or a predicate returns {@code true} to cancel the scope}
         *
         * <p> The joiner's {@link Joiner#onComplete(Subtask)} method invokes the
         * predicate's {@link Predicate#test(Object) test} method with the subtask that
         * completed successfully or failed with an exception. If the {@code test} method
         * returns {@code true} then <a href="StructuredTaskScope.html#Cancallation">
         * the scope is cancelled</a>. The {@code test} method must be thread safe as it
         * may be invoked concurrently from several threads. If the {@code test} method
         * completes with an exception or error, then the thread that executed the subtask
         * invokes the {@linkplain Thread.UncaughtExceptionHandler uncaught exception handler}
         * with the exception or error before the thread terminates.
         *
         * <p> The joiner's {@link #result()} method returns the stream of all subtasks,
         * in fork order. The stream may contain subtasks that have completed
         * (in {@link Subtask.State#SUCCESS SUCCESS} or {@link Subtask.State#FAILED FAILED}
         * state) or subtasks in the {@link Subtask.State#UNAVAILABLE UNAVAILABLE} state
         * if the scope was cancelled before all subtasks were forked or completed.
         *
         * <p> The following example uses this method to create a {@code Joiner} that
         * <a href="StructuredTaskScope.html#Cancallation">cancels</a> the scope when
         * two or more subtasks fail.
         * {@snippet lang=java :
         *    class CancelAfterTwoFailures<T> implements Predicate<Subtask<? extends T>> {
         *         private final AtomicInteger failedCount = new AtomicInteger();
         *         @Override
         *         public boolean test(Subtask<? extends T> subtask) {
         *             return subtask.state() == Subtask.State.FAILED
         *                     && failedCount.incrementAndGet() >= 2;
         *         }
         *     }
         *
         *     var joiner = Joiner.all(new CancelAfterTwoFailures<String>());
         * }
         *
         * <p> The following example uses {@code allUntil} to wait for all subtasks to
         * complete without any cancellation. This is similar to {@link #awaitAll()}
         * except that it yields a stream of the completed subtasks.
         * {@snippet lang=java :
         *    <T> List<Subtask<T>> invokeAll(Collection<Callable<T>> tasks) throws InterruptedException {
         *        try (var scope = StructuredTaskScope.open(Joiner.<T>allUntil(_ -> false))) {
         *            tasks.forEach(scope::fork);
         *            return scope.join().toList();
         *        }
         *    }
         * }
         *
         * @param isDone the predicate to evaluate completed subtasks
         * @param <T> the result type of subtasks
         */
        static <T> Joiner<T, Stream<Subtask<T>>> allUntil(Predicate<Subtask<? extends T>> isDone) {
            return new Joiners.AllSubtasks<>(isDone);
        }
    }

    /**
     * Represents the configuration for a {@code StructuredTaskScope}.
     *
     * <p> The configuration for a {@code StructuredTaskScope} consists of a {@link
     * ThreadFactory} to create threads, an optional name for the purposes of monitoring
     * and management, and an optional timeout.
     *
     * <p> Creating a {@code StructuredTaskScope} with {@link #open()} or {@link #open(Joiner)}
     * uses the <a href="StructuredTaskScope.html#DefaultConfiguration">default
     * configuration</a>. The default configuration consists of a thread factory that
     * creates unnamed <a href="{@docRoot}/java.base/java/lang/Thread.html#virtual-threads">
     * virtual threads</a>, no name for monitoring and management purposes, and no timeout.
     *
     * <p> Creating a {@code StructuredTaskScope} with its 2-arg {@link #open(Joiner, Function)
     * open} method allows a different configuration to be used. The function specified
     * to the {@code open} method is applied to the default configuration and returns the
     * configuration for the {@code StructuredTaskScope} under construction. The function
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
         * @apiNote The thread factory will typically create
         * <a href="{@docRoot}/java.base/java/lang/Thread.html#virtual-threads">virtual threads</a>,
         * maybe with names for monitoring purposes, an {@linkplain Thread.UncaughtExceptionHandler
         * uncaught exception handler}, or other properties configured.
         *
         * @see #fork(Callable)
         */
        Configuration withThreadFactory(ThreadFactory threadFactory);

        /**
         * {@return a new {@code Configuration} object with the given name}
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
         */
        Configuration withTimeout(Duration timeout);
    }

    /**
     * Exception thrown by {@link #join()} when the outcome is an exception rather than a
     * result.
     *
     * @since 25
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
    final class FailedException extends RuntimeException {
        @java.io.Serial
        static final long serialVersionUID = -1533055100078459923L;

        /**
         * Constructs a {@code FailedException} with the specified cause.
         *
         * @param  cause the cause, can be {@code null}
         */
        FailedException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Exception thrown by {@link #join()} if the scope was created with a timeout and
     * the timeout expired before or while waiting in {@code join}.
     *
     * @since 25
     * @see Configuration#withTimeout(Duration)
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
    final class TimeoutException extends RuntimeException {
        @java.io.Serial
        static final long serialVersionUID = 705788143955048766L;

        /**
         * Constructs a {@code TimeoutException} with no detail message.
         */
        TimeoutException() { }
    }

    /**
     * Opens a new {@code StructuredTaskScope} to use the given {@code Joiner} object and
     * with configuration that is the result of applying the given function to the
     * <a href="#DefaultConfiguration">default configuration</a>.
     *
     * <p> The {@code configFunction} is called with the default configuration and returns
     * the configuration for the new scope. The function may, for example, set the
     * {@linkplain Configuration#withThreadFactory(ThreadFactory) ThreadFactory} or set a
     * {@linkplain Configuration#withTimeout(Duration) timeout}. If the function completes
     * with an exception or error then it is propagated by this method. If the function
     * returns {@code null} then {@code NullPointerException} is thrown.
     *
     * <p> If a {@code ThreadFactory} is set then its {@link ThreadFactory#newThread(Runnable)
     * newThread} method will be called to create threads when {@linkplain #fork(Callable)
     * forking} subtasks in this scope. If a {@code ThreadFactory} is not set then
     * forking subtasks will create an unnamed virtual thread for each subtask.
     *
     * <p> If a {@linkplain Configuration#withTimeout(Duration) timeout} is set then it
     * starts when the scope is opened. If the timeout expires before the scope has
     * {@linkplain #join() joined} then the scope is <a href="#Cancallation">cancelled</a>
     * and the {@code join} method throws {@link TimeoutException}.
     *
     * <p> The new scope is owned by the current thread. Only code executing in this
     * thread can {@linkplain #fork(Callable) fork}, {@linkplain #join() join}, or
     * {@linkplain #close close} the scope.
     *
     * <p> Construction captures the current thread's {@linkplain ScopedValue scoped
     * value} bindings for inheritance by threads started in the scope.
     *
     * @param joiner the joiner
     * @param configFunction a function to produce the configuration
     * @return a new scope
     * @param <T> the result type of subtasks executed in the scope
     * @param <R> the result type of the scope
     * @since 25
     */
    static <T, R> StructuredTaskScope<T, R> open(Joiner<? super T, ? extends R> joiner,
                                                 Function<Configuration, Configuration> configFunction) {
        return StructuredTaskScopeImpl.open(joiner, configFunction);
    }

    /**
     * Opens a new {@code StructuredTaskScope}to use the given {@code Joiner} object. The
     * scope is created with the <a href="#DefaultConfiguration">default configuration</a>.
     * The default configuration has a {@code ThreadFactory} that creates unnamed
     * <a href="{@docRoot}/java.base/java/lang/Thread.html#virtual-threads">virtual threads</a>,
     * is unnamed for monitoring and management purposes, and has no timeout.
     *
     * @implSpec
     * This factory method is equivalent to invoking the 2-arg open method with the given
     * joiner and the {@linkplain Function#identity() identity function}.
     *
     * @param joiner the joiner
     * @return a new scope
     * @param <T> the result type of subtasks executed in the scope
     * @param <R> the result type of the scope
     * @since 25
     */
    static <T, R> StructuredTaskScope<T, R> open(Joiner<? super T, ? extends R> joiner) {
        return open(joiner, Function.identity());
    }

    /**
     * Opens a new {@code StructuredTaskScope} that can be used to fork subtasks that return
     * results of any type. The scope's {@link #join()} method waits for all subtasks to
     * succeed or any subtask to fail.
     *
     * <p> The {@code join} method returns {@code null} if all subtasks complete successfully.
     * It throws {@link FailedException} if any subtask fails, with the exception from
     * the first subtask to fail as the cause.
     *
     * <p> The scope is created with the <a href="#DefaultConfiguration">default
     * configuration</a>. The default configuration has a {@code ThreadFactory} that creates
     * unnamed <a href="{@docRoot}/java.base/java/lang/Thread.html#virtual-threads">virtual
     * threads</a>, is unnamed for monitoring and management purposes, and has no timeout.
     *
     * @implSpec
     * This factory method is equivalent to invoking the 2-arg open method with a joiner
     * created with {@link Joiner#awaitAllSuccessfulOrThrow() awaitAllSuccessfulOrThrow()}
     * and the {@linkplain Function#identity() identity function}.
     *
     * @param <T> the result type of subtasks
     * @return a new scope
     * @since 25
     */
    static <T> StructuredTaskScope<T, Void> open() {
        return open(Joiner.awaitAllSuccessfulOrThrow(), Function.identity());
    }

    /**
     * Fork a subtask by starting a new thread in this scope to execute a value-returning
     * method. The new thread executes the subtask concurrently with the current thread.
     * The parameter to this method is a {@link Callable}, the new thread executes its
     * {@link Callable#call() call()} method.
     *
     * <p> This method first creates a {@link Subtask Subtask} object to represent the
     * <em>forked subtask</em>. It invokes the joiner's {@link Joiner#onFork(Subtask) onFork}
     * method with the subtask in the {@link Subtask.State#UNAVAILABLE UNAVAILABLE} state.
     * If the {@code onFork} completes with an exception or error then it is propagated by
     * the {@code fork} method without creating a thread. If the scope is already
     * <a href="#Cancallation">cancelled</a>, or {@code onFork} returns {@code true} to
     * cancel the scope, then this method returns the {@code Subtask}, in the
     * {@link Subtask.State#UNAVAILABLE UNAVAILABLE} state, without creating a thread to
     * execute the subtask.
     *
     * <p> If the scope is not cancelled, and the {@code onFork} method returns {@code false},
     * then a thread is created with the {@link ThreadFactory} configured when the scope
     * was opened, and the thread is started. Forking a subtask inherits the current thread's
     * {@linkplain ScopedValue scoped value} bindings. The bindings must match the bindings
     * captured when the scope was opened. If the subtask completes (successfully or with
     * an exception) before the scope is cancelled, then the thread invokes the joiner's
     * {@link Joiner#onComplete(Subtask) onComplete} method with the subtask in the
     * {@link Subtask.State#SUCCESS SUCCESS} or {@link Subtask.State#FAILED FAILED} state.
     * If the {@code onComplete} method completes with an exception or error, then the
     * {@linkplain Thread.UncaughtExceptionHandler uncaught exception handler} is invoked
     * with the exception or error before the thread terminates.
     *
     * <p> This method returns the {@link Subtask Subtask} object. In some usages, this
     * object may be used to get its result. In other cases it may be used for correlation
     * or be discarded. To ensure correct usage, the {@link Subtask#get() Subtask.get()}
     * method may only be called by the scope owner to get the result after it has
     * waited for subtasks to complete with the {@link #join() join} method and the subtask
     * completed successfully. Similarly, the {@link Subtask#exception() Subtask.exception()}
     * method may only be called by the scope owner after it has joined and the subtask
     * failed. If the scope was cancelled before the subtask was forked, or before it
     * completes, then neither method can be used to obtain the outcome.
     *
     * <p> This method may only be invoked by the scope owner.
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
     * thread to run the subtask
     */
    <U extends T> Subtask<U> fork(Callable<? extends U> task);

    /**
     * Fork a subtask by starting a new thread in this scope to execute a method that
     * does not return a result.
     *
     * <p> This method works exactly the same as {@link #fork(Callable)} except that the
     * parameter to this method is a {@link Runnable}, the new thread executes its
     * {@link Runnable#run() run} method, and {@link Subtask#get() Subtask.get()} returns
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
     * thread to run the subtask
     * @since 25
     */
    <U extends T> Subtask<U> fork(Runnable task);

    /**
     * Returns the result, or throws, after waiting for all subtasks to complete or
     * the scope to be <a href="#Cancallation">cancelled</a>.
     *
     * <p> This method waits for all subtasks started in this scope to complete or the
     * scope to be cancelled. If a {@linkplain Configuration#withTimeout(Duration) timeout}
     * is configured and the timeout expires before or while waiting, then the scope is
     * cancelled and {@link TimeoutException TimeoutException} is thrown. Once finished
     * waiting, the {@code Joiner}'s {@link Joiner#result() result()} method is invoked
     * to get the result or throw an exception. If the {@code result()} method throws
     * then this method throws {@code FailedException} with the exception as the cause.
     *
     * <p> This method may only be invoked by the scope owner, and only once.
     *
     * @return the result
     * @throws WrongThreadException if the current thread is not the scope owner
     * @throws IllegalStateException if already joined or this scope is closed
     * @throws FailedException if the <i>outcome</i> is an exception, thrown with the
     * exception from {@link Joiner#result() Joiner.result()} as the cause
     * @throws TimeoutException if a timeout is set and the timeout expires before or
     * while waiting
     * @throws InterruptedException if interrupted while waiting
     * @since 25
     */
    R join() throws InterruptedException;

    /**
     * {@return {@code true} if this scope is <a href="#Cancallation">cancelled</a> or in
     * the process of being cancelled, otherwise {@code false}}
     *
     * <p> Cancelling the scope prevents new threads from starting in the scope and
     * {@linkplain Thread#interrupt() interrupts} threads executing unfinished subtasks.
     * It may take some time before the interrupted threads finish execution; this
     * method may return {@code true} before all threads have been interrupted or before
     * all threads have finished.
     *
     * @apiNote A task with a lengthy "forking phase" (the code that executes before
     * it invokes {@link #join() join}) may use this method to avoid doing work in cases
     * where scope is cancelled by the completion of a previously forked subtask or timeout.
     *
     * @since 25
     */
    boolean isCancelled();

    /**
     * Closes this scope.
     *
     * <p> This method first <a href="#Cancallation">cancels</a> the scope, if not
     * already cancelled. This interrupts the threads executing unfinished subtasks. This
     * method then waits for all threads to finish. If interrupted while waiting then it
     * will continue to wait until the threads finish, before completing with the interrupt
     * status set.
     *
     * <p> This method may only be invoked by the scope owner. If the scope
     * is already closed then the scope owner invoking this method has no effect.
     *
     * <p> A {@code StructuredTaskScope} is intended to be used in a <em>structured
     * manner</em>. If this method is called to close a scope before nested task
     * scopes are closed then it closes the underlying construct of each nested scope
     * (in the reverse order that they were created in), closes this scope, and then
     * throws {@link StructureViolationException}.
     * Similarly, if this method is called to close a scope while executing with
     * {@linkplain ScopedValue scoped value} bindings, and the scope was created
     * before the scoped values were bound, then {@code StructureViolationException} is
     * thrown after closing the scope.
     * If a thread terminates without first closing scopes that it owns then
     * termination will cause the underlying construct of each of its open tasks scopes to
     * be closed. Closing is performed in the reverse order that the scopes were
     * created in. Thread termination may therefore be delayed when the scope owner
     * has to wait for threads forked in these scopes to finish.
     *
     * @throws IllegalStateException thrown after closing the scope if the scope
     * owner did not attempt to join after forking
     * @throws WrongThreadException if the current thread is not the scope owner
     * @throws StructureViolationException if a structure violation was detected
     */
    @Override
    void close();
}