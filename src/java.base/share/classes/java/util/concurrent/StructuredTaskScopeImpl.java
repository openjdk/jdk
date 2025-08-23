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
import java.util.function.Function;
import jdk.internal.misc.ThreadFlock;
import jdk.internal.invoke.MhUtil;

/**
 * StructuredTaskScope implementation.
 */
final class StructuredTaskScopeImpl<T, R> implements StructuredTaskScope<T, R> {
    private static final VarHandle CANCELLED =
            MhUtil.findVarHandle(MethodHandles.lookup(), "cancelled", boolean.class);

    private final Joiner<? super T, ? extends R> joiner;
    private final ThreadFactory threadFactory;
    private final ThreadFlock flock;

    // state, only accessed by owner thread
    private static final int ST_NEW            = 0,
                             ST_FORKED         = 1,   // subtasks forked, need to join
                             ST_JOIN_STARTED   = 2,   // join started, can no longer fork
                             ST_JOIN_COMPLETED = 3,   // join completed
                             ST_CLOSED         = 4;   // closed
    private int state;

    // timer task, only accessed by owner thread
    private Future<?> timerTask;

    // set or read by any thread
    private volatile boolean cancelled;

    // set by the timer thread, read by the owner thread
    private volatile boolean timeoutExpired;

    @SuppressWarnings("this-escape")
    private StructuredTaskScopeImpl(Joiner<? super T, ? extends R> joiner,
                                    ThreadFactory threadFactory,
                                    String name) {
        this.joiner = joiner;
        this.threadFactory = threadFactory;
        this.flock = ThreadFlock.open((name != null) ? name : Objects.toIdentityString(this));
        this.state = ST_NEW;
    }

    /**
     * Returns a new {@code StructuredTaskScope} to use the given {@code Joiner} object
     * and with configuration that is the result of applying the given function to the
     * default configuration.
     */
    static <T, R> StructuredTaskScope<T, R> open(Joiner<? super T, ? extends R> joiner,
                                                 Function<Configuration, Configuration> configFunction) {
        Objects.requireNonNull(joiner);

        var config = (ConfigImpl) configFunction.apply(ConfigImpl.defaultConfig());
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
     * Throws IllegalStateException if already joined or scope is closed.
     */
    private void ensureNotJoined() {
        assert Thread.currentThread() == flock.owner();
        if (state > ST_FORKED) {
            throw new IllegalStateException("Already joined or scope is closed");
        }
    }

    /**
     * Throws IllegalStateException if invoked by the owner thread and the owner thread
     * has not joined.
     */
    private void ensureJoinedIfOwner() {
        if (Thread.currentThread() == flock.owner() && state <= ST_JOIN_STARTED) {
            throw new IllegalStateException("join not called");
        }
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
    private void onComplete(SubtaskImpl<? extends T> subtask) {
        assert subtask.state() != Subtask.State.UNAVAILABLE;
        if (joiner.onComplete(subtask)) {
            cancel();
        }
    }

    @Override
    public <U extends T> Subtask<U> fork(Callable<? extends U> task) {
        Objects.requireNonNull(task);
        ensureOwner();
        ensureNotJoined();

        var subtask = new SubtaskImpl<U>(this, task);

        // notify joiner, even if cancelled
        if (joiner.onFork(subtask)) {
            cancel();
        }

        if (!cancelled) {
            // create thread to run task
            Thread thread = threadFactory.newThread(subtask);
            if (thread == null) {
                throw new RejectedExecutionException("Rejected by thread factory");
            }

            // attempt to start the thread
            try {
                flock.start(thread);
            } catch (IllegalStateException e) {
                // shutdown by another thread, or underlying flock is shutdown due
                // to unstructured use
            }
        }

        // force owner to join
        state = ST_FORKED;
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
        ensureNotJoined();

        // join started
        state = ST_JOIN_STARTED;

        // wait for all subtasks, the scope to be cancelled, or interrupt
        flock.awaitAll();

        // throw if timeout expired
        if (timeoutExpired) {
            throw new TimeoutException();
        }
        cancelTimeout();

        // all subtasks completed or cancelled
        state = ST_JOIN_COMPLETED;

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

        SubtaskImpl(StructuredTaskScopeImpl<? super T, ?> scope, Callable<? extends T> task) {
            this.scope = scope;
            this.task = task;
        }

        @Override
        public void run() {
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
            scope.ensureJoinedIfOwner();
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
            scope.ensureJoinedIfOwner();
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
