/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.Objects;
import java.util.function.UnaryOperator;
import jdk.internal.misc.ThreadFlock;
import jdk.internal.invoke.MhUtil;
import jdk.internal.vm.annotation.Stable;

/**
 * StructuredTaskScope implementation.
 */
final class StructuredTaskScopeImpl<T, R> implements StructuredTaskScope<T, R> {
    private static final VarHandle CANCELLED =
            MhUtil.findVarHandle(MethodHandles.lookup(), "cancelled", boolean.class);

    private final Joiner<? super T, ? extends R> joiner;
    private final ThreadFactory threadFactory;
    private final ThreadFlock flock;

    // scope state, set by owner thread, read by any thread
    private static final int ST_FORKED         = 1,   // subtasks forked, need to join
                             ST_JOIN_STARTED   = 2,   // join started, can no longer fork
                             ST_JOIN_COMPLETED = 3,   // join completed
                             ST_CLOSED         = 4;   // closed
    private volatile int state;

    // set or read by any thread
    private volatile boolean cancelled;

    // timer task, only accessed by owner thread
    private Future<?> timerTask;

    // set by the timer thread, read by the owner thread
    private volatile boolean timeoutExpired;

    @SuppressWarnings("this-escape")
    private StructuredTaskScopeImpl(Joiner<? super T, ? extends R> joiner,
                                    ThreadFactory threadFactory,
                                    String name) {
        this.joiner = joiner;
        this.threadFactory = threadFactory;
        this.flock = ThreadFlock.open((name != null) ? name : Objects.toIdentityString(this));
    }

    /**
     * Returns a new {@code StructuredTaskScope} to use the given {@code Joiner} object
     * and with configuration that is the result of applying the given function to the
     * default configuration.
     */
    static <T, R> StructuredTaskScope<T, R> open(Joiner<? super T, ? extends R> joiner,
                                                 UnaryOperator<Configuration> configOperator) {
        Objects.requireNonNull(joiner);

        var config = (ConfigImpl) configOperator.apply(ConfigImpl.defaultConfig());
        var scope = new StructuredTaskScopeImpl<T, R>(joiner, config.threadFactory(), config.name());

        // schedule timeout
        Duration timeout = config.timeout();
        if (timeout != null) {
            boolean scheduled = false;
            try {
                scope.scheduleTimeout(timeout);
                scheduled = true;
            } finally {
                if (!scheduled) {
                    scope.close();  // pop if scheduling timeout failed
                }
            }
        }

        return scope;
    }

    /**
     * Throws WrongThreadException if the current thread is not the owner thread.
     */
    private void ensureOwner() {
        if (Thread.currentThread() != flock.owner()) {
            throw new WrongThreadException("Current thread not owner");
        }
    }

    /**
     * Returns true if join has been invoked and there is an outcome.
     */
    private boolean isJoinCompleted() {
        return state >= ST_JOIN_COMPLETED;
    }

    /**
     * Interrupts all threads in this scope, except the current thread.
     */
    private void interruptAll() {
        flock.threads()
                .filter(t -> t != Thread.currentThread())
                .forEach(t -> {
                    try {
                        t.interrupt();
                    } catch (Throwable ignore) { }
                });
    }

    /**
     * Cancel the scope if not already cancelled.
     */
    private void cancel() {
        if (!cancelled && CANCELLED.compareAndSet(this, false, true)) {
            // prevent new threads from starting
            flock.shutdown();

            // interrupt all unfinished threads
            interruptAll();

            // wakeup join
            flock.wakeup();
        }
    }

    /**
     * Schedules a task to cancel the scope on timeout.
     */
    private void scheduleTimeout(Duration timeout) {
        assert Thread.currentThread() == flock.owner() && timerTask == null;
        long nanos = TimeUnit.NANOSECONDS.convert(timeout);
        timerTask = ForkJoinPool.commonPool().schedule(() -> {
            if (!cancelled) {
                timeoutExpired = true;
                cancel();
            }
        }, nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Cancels the timer task if set.
     */
    private void cancelTimeout() {
        assert Thread.currentThread() == flock.owner();
        if (timerTask != null) {
            timerTask.cancel(false);
        }
    }

    /**
     * Invoked by the thread for a subtask when the subtask completes before scope is cancelled.
     */
    private <U extends T> void onComplete(SubtaskImpl<U> subtask) {
        assert subtask.state() != Subtask.State.UNAVAILABLE;
        @SuppressWarnings("unchecked")
        var j = (Joiner<U, ? extends R>) joiner;
        if (j.onComplete(subtask)) {
            cancel();
        }
    }

    @Override
    public <U extends T> Subtask<U> fork(Callable<? extends U> task) {
        Objects.requireNonNull(task);
        ensureOwner();
        int s = state;
        if (s > ST_FORKED) {
            throw new IllegalStateException("join already called or scope is closed");
        }

        var subtask = new SubtaskImpl<U>(this, task);

        // notify joiner, even if cancelled
        @SuppressWarnings("unchecked")
        var j = (Joiner<U, ? extends R>) joiner;
        if (j.onFork(subtask)) {
            cancel();
        }

        if (!cancelled) {
            // create thread to run task
            Thread thread = threadFactory.newThread(subtask);
            if (thread == null) {
                throw new RejectedExecutionException("Rejected by thread factory");
            }

            // attempt to start the thread
            subtask.setThread(thread);
            try {
                flock.start(thread);
            } catch (IllegalStateException e) {
                // shutdown by another thread, or underlying flock is shutdown due
                // to unstructured use
            }
        }

        // force owner to join
        if (s < ST_FORKED) {
            state = ST_FORKED;
        }
        return subtask;
    }

    @Override
    public <U extends T> Subtask<U> fork(Runnable task) {
        Objects.requireNonNull(task);
        return fork(() -> { task.run(); return null; });
    }

    @Override
    public R join() throws InterruptedException {
        ensureOwner();
        if (state >= ST_JOIN_COMPLETED) {
            throw new IllegalStateException("Already joined or scope is closed");
        }

        // wait for all subtasks, the scope to be cancelled, or interrupt
        try {
            flock.awaitAll();
        } catch (InterruptedException e) {
            state = ST_JOIN_STARTED;  // joining not completed, prevent new forks
            throw e;
        }

        // all subtasks completed or scope cancelled
        state = ST_JOIN_COMPLETED;

        // invoke joiner onTimeout if timeout expired
        if (timeoutExpired) {
            cancel();  // ensure cancelled before calling onTimeout
            joiner.onTimeout();
        } else {
            cancelTimeout();
        }

        // invoke joiner to get result
        try {
            return joiner.result();
        } catch (Throwable e) {
            throw new FailedException(e);
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void close() {
        ensureOwner();
        int s = state;
        if (s == ST_CLOSED) {
            return;
        }

        // cancel the scope if join did not complete
        if (s < ST_JOIN_COMPLETED) {
            cancel();
            cancelTimeout();
        }

        // wait for stragglers
        try {
            flock.close();
        } finally {
            state = ST_CLOSED;
        }

        // throw ISE if the owner didn't join after forking
        if (s == ST_FORKED) {
            throw new IllegalStateException("Owner did not join after forking");
        }
    }

    @Override
    public String toString() {
        return flock.name();
    }

    /**
     * Subtask implementation, runs the task specified to the fork method.
     */
    static final class SubtaskImpl<T> implements Subtask<T>, Runnable {
        private static final AltResult RESULT_NULL = new AltResult(Subtask.State.SUCCESS);

        private record AltResult(Subtask.State state, Throwable exception) {
            AltResult(Subtask.State state) {
                this(state, null);
            }
        }

        private final StructuredTaskScopeImpl<? super T, ?> scope;
        private final Callable<? extends T> task;
        private volatile Object result;
        @Stable private Thread thread;

        SubtaskImpl(StructuredTaskScopeImpl<? super T, ?> scope, Callable<? extends T> task) {
            this.scope = scope;
            this.task = task;
        }

        /**
         * Sets the thread for this subtask.
         */
        void setThread(Thread thread) {
            assert thread.getState() == Thread.State.NEW;
            this.thread = thread;
        }

        /**
         * Throws IllegalStateException if the caller thread is not the subtask and
         * the scope owner has not joined.
         */
        private void ensureJoinedIfNotSubtask() {
            if (Thread.currentThread() != thread && !scope.isJoinCompleted()) {
                throw new IllegalStateException();
            }
        }

        @Override
        public void run() {
            if (Thread.currentThread() != thread) {
                throw new WrongThreadException();
            }

            T result = null;
            Throwable ex = null;
            try {
                result = task.call();
            } catch (Throwable e) {
                ex = e;
            }

            // nothing to do if scope is cancelled
            if (scope.isCancelled())
                return;

            // set result/exception and invoke onComplete
            if (ex == null) {
                this.result = (result != null) ? result : RESULT_NULL;
            } else {
                this.result = new AltResult(State.FAILED, ex);
            }
            scope.onComplete(this);
        }

        @Override
        public Subtask.State state() {
            Object result = this.result;
            if (result == null) {
                return State.UNAVAILABLE;
            } else if (result instanceof AltResult alt) {
                // null or failed
                return alt.state();
            } else {
                return State.SUCCESS;
            }
        }

        @Override
        public T get() {
            ensureJoinedIfNotSubtask();
            Object result = this.result;
            if (result instanceof AltResult) {
                if (result == RESULT_NULL) return null;
            } else if (result != null) {
                @SuppressWarnings("unchecked")
                T r = (T) result;
                return r;
            }
            throw new IllegalStateException(
                    "Result is unavailable or subtask did not complete successfully");
        }

        @Override
        public Throwable exception() {
            ensureJoinedIfNotSubtask();
            Object result = this.result;
            if (result instanceof AltResult alt && alt.state() == State.FAILED) {
                return alt.exception();
            }
            throw new IllegalStateException(
                    "Exception is unavailable or subtask did not complete with exception");
        }

        @Override
        public String toString() {
            String stateAsString = switch (state()) {
                case UNAVAILABLE -> "[Unavailable]";
                case SUCCESS     -> "[Completed successfully]";
                case FAILED      -> "[Failed: " + ((AltResult) result).exception() + "]";
            };
            return Objects.toIdentityString(this) + stateAsString;
        }
    }

    /**
     * Configuration implementation.
     */
    record ConfigImpl(ThreadFactory threadFactory,
                      String name,
                      Duration timeout) implements Configuration {
        static Configuration defaultConfig() {
            return new ConfigImpl(Thread.ofVirtual().factory(), null, null);
        }

        @Override
        public Configuration withThreadFactory(ThreadFactory threadFactory) {
            return new ConfigImpl(Objects.requireNonNull(threadFactory), name, timeout);
        }

        @Override
        public Configuration withName(String name) {
            return new ConfigImpl(threadFactory, Objects.requireNonNull(name), timeout);
        }

        @Override
        public Configuration withTimeout(Duration timeout) {
            return new ConfigImpl(threadFactory, name, Objects.requireNonNull(timeout));
        }
    }
}
