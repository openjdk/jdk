/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.streams;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.AbstractQueuedLongSynchronizer;
import java.util.function.Function;

import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;

/**
 * Quic specifies limits on the number of uni and bidi streams that an endpoint can create.
 * This {@code StreamCreationPermit} is used to keep track of that limit and is expected to be
 * used before attempting to open a Quic stream. Either of {@link #tryAcquire()} or
 * {@link #tryAcquire(long, TimeUnit, Executor)} must be used before attempting to open a new stream. Stream
 * must only be opened if that method returns {@code true} which implies the stream creation limit
 * hasn't yet reached.
 * <p>
 * It is expected that for each of the stream types (remote uni, remote bidi, local uni
 * and local bidi) a separate instance of {@code StreamCreationPermit} will be used.
 * <p>
 * An instance of {@code StreamCreationPermit} starts with an initial limit and that limit can be
 * increased to newer higher values whenever necessary. The limit however cannot be reduced to a
 * lower value.
 * <p>
 * None of the methods, including {@link #tryAcquire(long, TimeUnit, Executor)} and {@link #tryAcquire()}
 * block the caller thread.
 */
final class StreamCreationPermit {

    private final InternalSemaphore semaphore;
    private final SequentialScheduler permitAcquisitionScheduler =
            SequentialScheduler.lockingScheduler(new TryAcquireTask());

    private final ConcurrentLinkedQueue<Waiter> acquirers = new ConcurrentLinkedQueue<>();

    /**
     * @param initialMaxStreams the initial max streams limit
     * @throws IllegalArgumentException if {@code initialMaxStreams} is less than 0
     * @throws NullPointerException     if executor is null
     */
    StreamCreationPermit(final long initialMaxStreams) {
        if (initialMaxStreams < 0) {
            throw new IllegalArgumentException("Invalid max streams limit: " + initialMaxStreams);
        }
        this.semaphore = new InternalSemaphore(initialMaxStreams);
    }

    /**
     * Attempts to increase the limit to {@code newLimit}. The limit will be atomically increased
     * to the {@code newLimit}. If the {@linkplain #currentLimit() current limit} is higher than
     * the {@code newLimit}, then the limit isn't changed and this method returns {@code false}.
     *
     * @param newLimit the new limit
     * @return true if the limit was successfully increased to {@code newLimit}, false otherwise.
     */
    boolean tryIncreaseLimitTo(final long newLimit) {
        final boolean increased = this.semaphore.tryIncreaseLimitTo(newLimit);
        if (increased) {
            // let any waiting acquirers attempt acquiring a permit
            permitAcquisitionScheduler.runOrSchedule();
        }
        return increased;
    }

    /**
     * Attempts to acquire a permit to open a new stream. This method does not block and returns
     * immediately. A stream should only be opened if the permit was successfully acquired.
     *
     * @return true if the permit was acquired and a new stream is allowed to be opened.
     * false otherwise.
     */
    boolean tryAcquire() {
        return this.semaphore.tryAcquireShared(1) >= 0;
    }

    /**
     * Attempts to acquire a permit to open a new stream. If the permit is available then this method
     * returns immediately with a {@link CompletableFuture} whose result is {@code true}. If the
     * permit isn't currently available then this method returns a {@code CompletableFuture} which
     * completes with a result of {@code false} if no permits were available for the duration
     * represented by the {@code timeout}. If during this {@code timeout} period, a permit is
     * acquired, because of an increase in the stream limit, then the returned
     * {@code CompletableFuture} completes with a result of {@code true}.
     *
     * @param timeout  the maximum amount of time to attempt acquiring a permit, after which the
     *                 {@code CompletableFuture} will complete with a result of {@code false}
     * @param unit     the timeout unit
     * @param executor the executor that will be used to asynchronously complete the
     *                 returned {@code CompletableFuture} if a permit is acquired after this
     *                 method has returned
     * @return a {@code CompletableFuture} whose result will be {@code true} if the permit was
     * acquired and {@code false} otherwise
     * @throws IllegalArgumentException if {@code timeout} is negative
     * @throws NullPointerException     if the {@code executor} is null
     */
    CompletableFuture<Boolean> tryAcquire(final long timeout, final TimeUnit unit,
                                          final Executor executor) {
        Objects.requireNonNull(executor);
        if (timeout < 0) {
            throw new IllegalArgumentException("invalid timeout: " + timeout);
        }
        if (tryAcquire()) {
            return MinimalFuture.completedFuture(true);
        }
        final CompletableFuture<Boolean> future = new MinimalFuture<Boolean>()
                .orTimeout(timeout, unit)
                .handle((acquired, t) -> {
                    if (t instanceof TimeoutException te) {
                        // timed out
                        return MinimalFuture.completedFuture(false);
                    }
                    if (t == null) {
                        // completed normally
                        return MinimalFuture.completedFuture(acquired);
                    }
                    return MinimalFuture.<Boolean>failedFuture(t);
                }).thenComposeAsync(Function.identity(), executor);
        var waiter = new Waiter(future, executor);
        this.acquirers.add(waiter);
        // if the future completes in timeout the Waiter should be removed from the list.
        // because this is a queue it might not be too efficient...
        future.whenComplete((r,t) -> { if (r != null && !r) acquirers.remove(waiter);});
        // if stream limit might have increased in the meantime,
        // trigger the task to have this newly registered waiter notified
        // TODO: should we call runOrSchedule(executor) here instead?
        permitAcquisitionScheduler.runOrSchedule();
        return future;
    }

    /**
     * {@return the current limit for stream creation}
     */
    long currentLimit() {
        return this.semaphore.currentLimit();
    }

    private final record Waiter(CompletableFuture<Boolean> acquirer,
                                Executor executor) {
        Waiter {
            assert acquirer != null : "Acquirer cannot be null";
            assert executor != null : "Executor cannot be null";
        }
    }

    /**
     * A task which iterates over the waiting acquirers and attempt
     * to acquire a permit. If successful, the waiting acquirer(s) (i.e. the CompletableFuture(s))
     * are completed successfully. If not, the waiting acquirers continue to stay in the wait list
     */
    private final class TryAcquireTask implements Runnable {

        @Override
        public void run() {
            Waiter waiter = null;
            while ((waiter = acquirers.peek()) != null) {
                final CompletableFuture<Boolean> acquirer = waiter.acquirer;
                if (acquirer.isCancelled() || acquirer.isDone()) {
                    // no longer interested, or already completed, remove it
                    acquirers.remove(waiter);
                    continue;
                }
                if (!tryAcquire()) {
                    // limit reached, no permits available yet
                    break;
                }
                // compose a step which rolls back the acquired permit if the
                // CompletableFuture completed in some other thread, after the permit was acquired.
                acquirer.whenComplete((acquired, t) -> {
                    final boolean shouldRollback = acquirer.isCancelled()
                            || t != null
                            || !acquired;
                    if (shouldRollback) {
                        final boolean released = StreamCreationPermit.this.semaphore.releaseShared(1);
                        assert released : "acquired permit wasn't released";
                        // an additional permit is now available due to the release, let any waiters
                        // acquire it if needed
                        permitAcquisitionScheduler.runOrSchedule();
                    }
                });
                // got a permit, complete the waiting acquirer
                acquirers.remove(waiter);
                acquirer.completeAsync(() -> true, waiter.executor);
            }
        }
    }

    /**
     * A {@link AbstractQueuedLongSynchronizer} whose {@linkplain #getState() state} represents
     * the number of permits that have currently been acquired. This {@code Semaphore} only
     * supports "shared" mode; i.e. exclusive mode isn't supported.
     * <p>
     * The {@code Semaphore} maintains a {@linkplain #limit limit} which represents
     * the maximum number of permits that can be acquired through an instance of this class.
     * The {@code limit} can be {@linkplain #tryIncreaseLimitTo(long) increased} but cannot be
     * reduced from the previous set limit.
     */
    private static final class InternalSemaphore extends AbstractQueuedLongSynchronizer {
        private static final long serialVersionUID = 4280985311770761500L;

        private final AtomicLong limit;

        /**
         * @param initialLimit the initial limit, must be >=0
         */
        private InternalSemaphore(final long initialLimit) {
            assert initialLimit >= 0 : "not a positive initial limit: " + initialLimit;
            this.limit = new AtomicLong(initialLimit);
            setState(0 /* num acquired */);
        }

        /**
         * Attempts to acquire additional permits. If no permits can be acquired,
         * then this method returns -1. Upon successfully acquiring the
         * {@code additionalAcquisitions} this method returns a value {@code >=0} which represents
         * the additional number of permits that are available for acquisition.
         *
         * @param additionalAcquisitions the additional permits that are requested
         * @return -1 If no permits can be acquired. Value >=0, representing the permits that are
         * still available for acquisition.
         */
        @Override
        protected long tryAcquireShared(final long additionalAcquisitions) {
            while (true) {
                final long alreadyAcquired = getState();
                final long totalOnAcquisition = alreadyAcquired + additionalAcquisitions;
                final long currentLimit = limit.get();
                if (totalOnAcquisition > currentLimit) {
                    return -1; // exceeds limit, so cannot acquire
                }
                final long numAvailableUponAcquisition = currentLimit - totalOnAcquisition;
                if (compareAndSetState(alreadyAcquired, totalOnAcquisition)) {
                    return numAvailableUponAcquisition;
                }
            }
        }

        /**
         * Attempts to release permits
         *
         * @param releases the number of permits to release
         * @return true if the permits were released, false otherwise
         * @throws IllegalArgumentException if the number of {@code releases} exceeds the total
         *                                  number of permits that have been acquired
         */
        @Override
        protected boolean tryReleaseShared(final long releases) {
            while (true) {
                final long currentAcquisitions = getState();
                final long totalAfterRelease = currentAcquisitions - releases;
                if (totalAfterRelease < 0) {
                    // we attempted to release more permits than what was acquired
                    throw new IllegalArgumentException("cannot release " + releases
                            + " permits from " + currentAcquisitions + " acquisitions");
                }
                if (compareAndSetState(currentAcquisitions, totalAfterRelease)) {
                    return true;
                }
            }
        }

        /**
         * Tries to increase the limit to the {@code newLimit}. If the {@code newLimit} is lesser
         * than the current limit, then this method returns false. Otherwise, this method will attempt
         * to atomically increase the limit to {@code newLimit}.
         *
         * @param newLimit The new limit to set
         * @return true if the limit was increased to {@code newLimit}. false otherwise
         */
        private boolean tryIncreaseLimitTo(final long newLimit) {
            long currentLimit = this.limit.get();
            while (currentLimit < newLimit) {
                if (this.limit.compareAndSet(currentLimit, newLimit)) {
                    return true;
                }
                currentLimit = this.limit.get();
            }
            return false;
        }

        /**
         * {@return the current limit}
         */
        private long currentLimit() {
            return this.limit.get();
        }
    }
}
