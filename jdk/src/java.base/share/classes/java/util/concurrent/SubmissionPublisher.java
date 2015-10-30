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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * A {@link Flow.Publisher} that asynchronously issues submitted
 * (non-null) items to current subscribers until it is closed.  Each
 * current subscriber receives newly submitted items in the same order
 * unless drops or exceptions are encountered.  Using a
 * SubmissionPublisher allows item generators to act as compliant <a
 * href="http://www.reactive-streams.org/"> reactive-streams</a>
 * Publishers relying on drop handling and/or blocking for flow
 * control.
 *
 * <p>A SubmissionPublisher uses the {@link Executor} supplied in its
 * constructor for delivery to subscribers. The best choice of
 * Executor depends on expected usage. If the generator(s) of
 * submitted items run in separate threads, and the number of
 * subscribers can be estimated, consider using a {@link
 * Executors#newFixedThreadPool}. Otherwise consider using the
 * default, normally the {@link ForkJoinPool#commonPool}.
 *
 * <p>Buffering allows producers and consumers to transiently operate
 * at different rates.  Each subscriber uses an independent buffer.
 * Buffers are created upon first use and expanded as needed up to the
 * given maximum. (The enforced capacity may be rounded up to the
 * nearest power of two and/or bounded by the largest value supported
 * by this implementation.)  Invocations of {@link
 * Flow.Subscription#request(long) request} do not directly result in
 * buffer expansion, but risk saturation if unfilled requests exceed
 * the maximum capacity.  The default value of {@link
 * Flow#defaultBufferSize()} may provide a useful starting point for
 * choosing a capacity based on expected rates, resources, and usages.
 *
 * <p>Publication methods support different policies about what to do
 * when buffers are saturated. Method {@link #submit(Object) submit}
 * blocks until resources are available. This is simplest, but least
 * responsive.  The {@code offer} methods may drop items (either
 * immediately or with bounded timeout), but provide an opportunity to
 * interpose a handler and then retry.
 *
 * <p>If any Subscriber method throws an exception, its subscription
 * is cancelled.  If a handler is supplied as a constructor argument,
 * it is invoked before cancellation upon an exception in method
 * {@link Flow.Subscriber#onNext onNext}, but exceptions in methods
 * {@link Flow.Subscriber#onSubscribe onSubscribe},
 * {@link Flow.Subscriber#onError(Throwable) onError} and
 * {@link Flow.Subscriber#onComplete() onComplete} are not recorded or
 * handled before cancellation.  If the supplied Executor throws
 * {@link RejectedExecutionException} (or any other RuntimeException
 * or Error) when attempting to execute a task, or a drop handler
 * throws an exception when processing a dropped item, then the
 * exception is rethrown. In these cases, not all subscribers will
 * have been issued the published item. It is usually good practice to
 * {@link #closeExceptionally closeExceptionally} in these cases.
 *
 * <p>Method {@link #consume(Consumer)} simplifies support for a
 * common case in which the only action of a subscriber is to request
 * and process all items using a supplied function.
 *
 * <p>This class may also serve as a convenient base for subclasses
 * that generate items, and use the methods in this class to publish
 * them.  For example here is a class that periodically publishes the
 * items generated from a supplier. (In practice you might add methods
 * to independently start and stop generation, to share Executors
 * among publishers, and so on, or use a SubmissionPublisher as a
 * component rather than a superclass.)
 *
 * <pre> {@code
 * class PeriodicPublisher<T> extends SubmissionPublisher<T> {
 *   final ScheduledFuture<?> periodicTask;
 *   final ScheduledExecutorService scheduler;
 *   PeriodicPublisher(Executor executor, int maxBufferCapacity,
 *                     Supplier<? extends T> supplier,
 *                     long period, TimeUnit unit) {
 *     super(executor, maxBufferCapacity);
 *     scheduler = new ScheduledThreadPoolExecutor(1);
 *     periodicTask = scheduler.scheduleAtFixedRate(
 *       () -> submit(supplier.get()), 0, period, unit);
 *   }
 *   public void close() {
 *     periodicTask.cancel(false);
 *     scheduler.shutdown();
 *     super.close();
 *   }
 * }}</pre>
 *
 * <p>Here is an example of a {@link Flow.Processor} implementation.
 * It uses single-step requests to its publisher for simplicity of
 * illustration. A more adaptive version could monitor flow using the
 * lag estimate returned from {@code submit}, along with other utility
 * methods.
 *
 * <pre> {@code
 * class TransformProcessor<S,T> extends SubmissionPublisher<T>
 *   implements Flow.Processor<S,T> {
 *   final Function<? super S, ? extends T> function;
 *   Flow.Subscription subscription;
 *   TransformProcessor(Executor executor, int maxBufferCapacity,
 *                      Function<? super S, ? extends T> function) {
 *     super(executor, maxBufferCapacity);
 *     this.function = function;
 *   }
 *   public void onSubscribe(Flow.Subscription subscription) {
 *     (this.subscription = subscription).request(1);
 *   }
 *   public void onNext(S item) {
 *     subscription.request(1);
 *     submit(function.apply(item));
 *   }
 *   public void onError(Throwable ex) { closeExceptionally(ex); }
 *   public void onComplete() { close(); }
 * }}</pre>
 *
 * @param <T> the published item type
 * @author Doug Lea
 * @since 1.9
 */
public class SubmissionPublisher<T> implements Flow.Publisher<T>,
                                               AutoCloseable {
    /*
     * Most mechanics are handled by BufferedSubscription. This class
     * mainly tracks subscribers and ensures sequentiality, by using
     * built-in synchronization locks across public methods. (Using
     * built-in locks works well in the most typical case in which
     * only one thread submits items).
     */

    /** The largest possible power of two array size. */
    static final int BUFFER_CAPACITY_LIMIT = 1 << 30;

    /** Round capacity to power of 2, at most limit. */
    static final int roundCapacity(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n <= 0) ? 1 : // at least 1
            (n >= BUFFER_CAPACITY_LIMIT) ? BUFFER_CAPACITY_LIMIT : n + 1;
    }

    // default Executor setup; nearly the same as CompletableFuture

    /**
     * Default executor -- ForkJoinPool.commonPool() unless it cannot
     * support parallelism.
     */
    private static final Executor ASYNC_POOL =
        (ForkJoinPool.getCommonPoolParallelism() > 1) ?
        ForkJoinPool.commonPool() : new ThreadPerTaskExecutor();

    /** Fallback if ForkJoinPool.commonPool() cannot support parallelism */
    private static final class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) { new Thread(r).start(); }
    }

    /**
     * Clients (BufferedSubscriptions) are maintained in a linked list
     * (via their "next" fields). This works well for publish loops.
     * It requires O(n) traversal to check for duplicate subscribers,
     * but we expect that subscribing is much less common than
     * publishing. Unsubscribing occurs only during traversal loops,
     * when BufferedSubscription methods return negative values
     * signifying that they have been disabled.  To reduce
     * head-of-line blocking, submit and offer methods first call
     * BufferedSubscription.offer on each subscriber, and place
     * saturated ones in retries list (using nextRetry field), and
     * retry, possibly blocking or dropping.
     */
    BufferedSubscription<T> clients;

    /** Run status, updated only within locks */
    volatile boolean closed;
    /** If non-null, the exception in closeExceptionally */
    volatile Throwable closedException;

    // Parameters for constructing BufferedSubscriptions
    final Executor executor;
    final BiConsumer<? super Flow.Subscriber<? super T>, ? super Throwable> onNextHandler;
    final int maxBufferCapacity;

    /**
     * Creates a new SubmissionPublisher using the given Executor for
     * async delivery to subscribers, with the given maximum buffer size
     * for each subscriber, and, if non-null, the given handler invoked
     * when any Subscriber throws an exception in method {@link
     * Flow.Subscriber#onNext(Object) onNext}.
     *
     * @param executor the executor to use for async delivery,
     * supporting creation of at least one independent thread
     * @param maxBufferCapacity the maximum capacity for each
     * subscriber's buffer (the enforced capacity may be rounded up to
     * the nearest power of two and/or bounded by the largest value
     * supported by this implementation; method {@link #getMaxBufferCapacity}
     * returns the actual value)
     * @param handler if non-null, procedure to invoke upon exception
     * thrown in method {@code onNext}
     * @throws NullPointerException if executor is null
     * @throws IllegalArgumentException if maxBufferCapacity not
     * positive
     */
    public SubmissionPublisher(Executor executor, int maxBufferCapacity,
                               BiConsumer<? super Flow.Subscriber<? super T>, ? super Throwable> handler) {
        if (executor == null)
            throw new NullPointerException();
        if (maxBufferCapacity <= 0)
            throw new IllegalArgumentException("capacity must be positive");
        this.executor = executor;
        this.onNextHandler = handler;
        this.maxBufferCapacity = roundCapacity(maxBufferCapacity);
    }

    /**
     * Creates a new SubmissionPublisher using the given Executor for
     * async delivery to subscribers, with the given maximum buffer size
     * for each subscriber, and no handler for Subscriber exceptions in
     * method {@link Flow.Subscriber#onNext(Object) onNext}.
     *
     * @param executor the executor to use for async delivery,
     * supporting creation of at least one independent thread
     * @param maxBufferCapacity the maximum capacity for each
     * subscriber's buffer (the enforced capacity may be rounded up to
     * the nearest power of two and/or bounded by the largest value
     * supported by this implementation; method {@link #getMaxBufferCapacity}
     * returns the actual value)
     * @throws NullPointerException if executor is null
     * @throws IllegalArgumentException if maxBufferCapacity not
     * positive
     */
    public SubmissionPublisher(Executor executor, int maxBufferCapacity) {
        this(executor, maxBufferCapacity, null);
    }

    /**
     * Creates a new SubmissionPublisher using the {@link
     * ForkJoinPool#commonPool()} for async delivery to subscribers
     * (unless it does not support a parallelism level of at least two,
     * in which case, a new Thread is created to run each task), with
     * maximum buffer capacity of {@link Flow#defaultBufferSize}, and no
     * handler for Subscriber exceptions in method {@link
     * Flow.Subscriber#onNext(Object) onNext}.
     */
    public SubmissionPublisher() {
        this(ASYNC_POOL, Flow.defaultBufferSize(), null);
    }

    /**
     * Adds the given Subscriber unless already subscribed.  If already
     * subscribed, the Subscriber's {@link
     * Flow.Subscriber#onError(Throwable) onError} method is invoked on
     * the existing subscription with an {@link IllegalStateException}.
     * Otherwise, upon success, the Subscriber's {@link
     * Flow.Subscriber#onSubscribe onSubscribe} method is invoked
     * asynchronously with a new {@link Flow.Subscription}.  If {@link
     * Flow.Subscriber#onSubscribe onSubscribe} throws an exception, the
     * subscription is cancelled. Otherwise, if this SubmissionPublisher
     * was closed exceptionally, then the subscriber's {@link
     * Flow.Subscriber#onError onError} method is invoked with the
     * corresponding exception, or if closed without exception, the
     * subscriber's {@link Flow.Subscriber#onComplete() onComplete}
     * method is invoked.  Subscribers may enable receiving items by
     * invoking the {@link Flow.Subscription#request(long) request}
     * method of the new Subscription, and may unsubscribe by invoking
     * its {@link Flow.Subscription#cancel() cancel} method.
     *
     * @param subscriber the subscriber
     * @throws NullPointerException if subscriber is null
     */
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        if (subscriber == null) throw new NullPointerException();
        BufferedSubscription<T> subscription =
            new BufferedSubscription<T>(subscriber, executor,
                                        onNextHandler, maxBufferCapacity);
        synchronized (this) {
            for (BufferedSubscription<T> b = clients, pred = null;;) {
                if (b == null) {
                    Throwable ex;
                    subscription.onSubscribe();
                    if ((ex = closedException) != null)
                        subscription.onError(ex);
                    else if (closed)
                        subscription.onComplete();
                    else if (pred == null)
                        clients = subscription;
                    else
                        pred.next = subscription;
                    break;
                }
                BufferedSubscription<T> next = b.next;
                if (b.isDisabled()) { // remove
                    b.next = null;    // detach
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else if (subscriber.equals(b.subscriber)) {
                    b.onError(new IllegalStateException("Duplicate subscribe"));
                    break;
                }
                else
                    pred = b;
                b = next;
            }
        }
    }

    /**
     * Publishes the given item to each current subscriber by
     * asynchronously invoking its {@link Flow.Subscriber#onNext(Object)
     * onNext} method, blocking uninterruptibly while resources for any
     * subscriber are unavailable. This method returns an estimate of
     * the maximum lag (number of items submitted but not yet consumed)
     * among all current subscribers. This value is at least one
     * (accounting for this submitted item) if there are any
     * subscribers, else zero.
     *
     * <p>If the Executor for this publisher throws a
     * RejectedExecutionException (or any other RuntimeException or
     * Error) when attempting to asynchronously notify subscribers,
     * then this exception is rethrown, in which case not all
     * subscribers will have been issued this item.
     *
     * @param item the (non-null) item to publish
     * @return the estimated maximum lag among subscribers
     * @throws IllegalStateException if closed
     * @throws NullPointerException if item is null
     * @throws RejectedExecutionException if thrown by Executor
     */
    public int submit(T item) {
        if (item == null) throw new NullPointerException();
        int lag = 0;
        boolean complete;
        synchronized (this) {
            complete = closed;
            BufferedSubscription<T> b = clients;
            if (!complete) {
                BufferedSubscription<T> pred = null, r = null, rtail = null;
                while (b != null) {
                    BufferedSubscription<T> next = b.next;
                    int stat = b.offer(item);
                    if (stat < 0) {           // disabled
                        b.next = null;
                        if (pred == null)
                            clients = next;
                        else
                            pred.next = next;
                    }
                    else {
                        if (stat > lag)
                            lag = stat;
                        else if (stat == 0) { // place on retry list
                            b.nextRetry = null;
                            if (rtail == null)
                                r = b;
                            else
                                rtail.nextRetry = b;
                            rtail = b;
                        }
                        pred = b;
                    }
                    b = next;
                }
                while (r != null) {
                    BufferedSubscription<T> nextRetry = r.nextRetry;
                    r.nextRetry = null;
                    int stat = r.submit(item);
                    if (stat > lag)
                        lag = stat;
                    else if (stat < 0 && clients == r)
                        clients = r.next; // postpone internal unsubscribes
                    r = nextRetry;
                }
            }
        }
        if (complete)
            throw new IllegalStateException("Closed");
        else
            return lag;
    }

    /**
     * Publishes the given item, if possible, to each current subscriber
     * by asynchronously invoking its {@link
     * Flow.Subscriber#onNext(Object) onNext} method. The item may be
     * dropped by one or more subscribers if resource limits are
     * exceeded, in which case the given handler (if non-null) is
     * invoked, and if it returns true, retried once.  Other calls to
     * methods in this class by other threads are blocked while the
     * handler is invoked.  Unless recovery is assured, options are
     * usually limited to logging the error and/or issuing an {@link
     * Flow.Subscriber#onError(Throwable) onError} signal to the
     * subscriber.
     *
     * <p>This method returns a status indicator: If negative, it
     * represents the (negative) number of drops (failed attempts to
     * issue the item to a subscriber). Otherwise it is an estimate of
     * the maximum lag (number of items submitted but not yet
     * consumed) among all current subscribers. This value is at least
     * one (accounting for this submitted item) if there are any
     * subscribers, else zero.
     *
     * <p>If the Executor for this publisher throws a
     * RejectedExecutionException (or any other RuntimeException or
     * Error) when attempting to asynchronously notify subscribers, or
     * the drop handler throws an exception when processing a dropped
     * item, then this exception is rethrown.
     *
     * @param item the (non-null) item to publish
     * @param onDrop if non-null, the handler invoked upon a drop to a
     * subscriber, with arguments of the subscriber and item; if it
     * returns true, an offer is re-attempted (once)
     * @return if negative, the (negative) number of drops; otherwise
     * an estimate of maximum lag
     * @throws IllegalStateException if closed
     * @throws NullPointerException if item is null
     * @throws RejectedExecutionException if thrown by Executor
     */
    public int offer(T item,
                     BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        return doOffer(0L, item, onDrop);
    }

    /**
     * Publishes the given item, if possible, to each current subscriber
     * by asynchronously invoking its {@link
     * Flow.Subscriber#onNext(Object) onNext} method, blocking while
     * resources for any subscription are unavailable, up to the
     * specified timeout or until the caller thread is interrupted, at
     * which point the given handler (if non-null) is invoked, and if it
     * returns true, retried once. (The drop handler may distinguish
     * timeouts from interrupts by checking whether the current thread
     * is interrupted.)  Other calls to methods in this class by other
     * threads are blocked while the handler is invoked.  Unless
     * recovery is assured, options are usually limited to logging the
     * error and/or issuing an {@link Flow.Subscriber#onError(Throwable)
     * onError} signal to the subscriber.
     *
     * <p>This method returns a status indicator: If negative, it
     * represents the (negative) number of drops (failed attempts to
     * issue the item to a subscriber). Otherwise it is an estimate of
     * the maximum lag (number of items submitted but not yet
     * consumed) among all current subscribers. This value is at least
     * one (accounting for this submitted item) if there are any
     * subscribers, else zero.
     *
     * <p>If the Executor for this publisher throws a
     * RejectedExecutionException (or any other RuntimeException or
     * Error) when attempting to asynchronously notify subscribers, or
     * the drop handler throws an exception when processing a dropped
     * item, then this exception is rethrown.
     *
     * @param item the (non-null) item to publish
     * @param timeout how long to wait for resources for any subscriber
     * before giving up, in units of {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     * {@code timeout} parameter
     * @param onDrop if non-null, the handler invoked upon a drop to a
     * subscriber, with arguments of the subscriber and item; if it
     * returns true, an offer is re-attempted (once)
     * @return if negative, the (negative) number of drops; otherwise
     * an estimate of maximum lag
     * @throws IllegalStateException if closed
     * @throws NullPointerException if item is null
     * @throws RejectedExecutionException if thrown by Executor
     */
    public int offer(T item, long timeout, TimeUnit unit,
                     BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        return doOffer(unit.toNanos(timeout), item, onDrop);
    }

    /** Common implementation for both forms of offer */
    final int doOffer(long nanos, T item,
                      BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        if (item == null) throw new NullPointerException();
        int lag = 0, drops = 0;
        boolean complete;
        synchronized (this) {
            complete = closed;
            BufferedSubscription<T> b = clients;
            if (!complete) {
                BufferedSubscription<T> pred = null, r = null, rtail = null;
                while (b != null) {
                    BufferedSubscription<T> next = b.next;
                    int stat = b.offer(item);
                    if (stat < 0) {
                        b.next = null;
                        if (pred == null)
                            clients = next;
                        else
                            pred.next = next;
                    }
                    else {
                        if (stat > lag)
                            lag = stat;
                        else if (stat == 0) {
                            b.nextRetry = null;
                            if (rtail == null)
                                r = b;
                            else
                                rtail.nextRetry = b;
                            rtail = b;
                        }
                        else if (stat > lag)
                            lag = stat;
                        pred = b;
                    }
                    b = next;
                }
                while (r != null) {
                    BufferedSubscription<T> nextRetry = r.nextRetry;
                    r.nextRetry = null;
                    int stat = (nanos > 0L) ? r.timedOffer(item, nanos) :
                        r.offer(item);
                    if (stat == 0 && onDrop != null &&
                        onDrop.test(r.subscriber, item))
                        stat = r.offer(item);
                    if (stat == 0)
                        ++drops;
                    else if (stat > lag)
                        lag = stat;
                    else if (stat < 0 && clients == r)
                        clients = r.next;
                    r = nextRetry;
                }
            }
        }
        if (complete)
            throw new IllegalStateException("Closed");
        else
            return (drops > 0) ? -drops : lag;
    }

    /**
     * Unless already closed, issues {@link
     * Flow.Subscriber#onComplete() onComplete} signals to current
     * subscribers, and disallows subsequent attempts to publish.
     * Upon return, this method does <em>NOT</em> guarantee that all
     * subscribers have yet completed.
     */
    public void close() {
        if (!closed) {
            BufferedSubscription<T> b;
            synchronized (this) {
                b = clients;
                clients = null;
                closed = true;
            }
            while (b != null) {
                BufferedSubscription<T> next = b.next;
                b.next = null;
                b.onComplete();
                b = next;
            }
        }
    }

    /**
     * Unless already closed, issues {@link
     * Flow.Subscriber#onError(Throwable) onError} signals to current
     * subscribers with the given error, and disallows subsequent
     * attempts to publish.  Future subscribers also receive the given
     * error. Upon return, this method does <em>NOT</em> guarantee
     * that all subscribers have yet completed.
     *
     * @param error the {@code onError} argument sent to subscribers
     * @throws NullPointerException if error is null
     */
    public void closeExceptionally(Throwable error) {
        if (error == null)
            throw new NullPointerException();
        if (!closed) {
            BufferedSubscription<T> b;
            synchronized (this) {
                b = clients;
                clients = null;
                closed = true;
                closedException = error;
            }
            while (b != null) {
                BufferedSubscription<T> next = b.next;
                b.next = null;
                b.onError(error);
                b = next;
            }
        }
    }

    /**
     * Returns true if this publisher is not accepting submissions.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns the exception associated with {@link
     * #closeExceptionally(Throwable) closeExceptionally}, or null if
     * not closed or if closed normally.
     *
     * @return the exception, or null if none
     */
    public Throwable getClosedException() {
        return closedException;
    }

    /**
     * Returns true if this publisher has any subscribers.
     *
     * @return true if this publisher has any subscribers
     */
    public boolean hasSubscribers() {
        boolean nonEmpty = false;
        if (!closed) {
            synchronized (this) {
                for (BufferedSubscription<T> b = clients; b != null;) {
                    BufferedSubscription<T> next = b.next;
                    if (b.isDisabled()) {
                        b.next = null;
                        b = clients = next;
                    }
                    else {
                        nonEmpty = true;
                        break;
                    }
                }
            }
        }
        return nonEmpty;
    }

    /**
     * Returns the number of current subscribers.
     *
     * @return the number of current subscribers
     */
    public int getNumberOfSubscribers() {
        int count = 0;
        if (!closed) {
            synchronized (this) {
                BufferedSubscription<T> pred = null, next;
                for (BufferedSubscription<T> b = clients; b != null; b = next) {
                    next = b.next;
                    if (b.isDisabled()) {
                        b.next = null;
                        if (pred == null)
                            clients = next;
                        else
                            pred.next = next;
                    }
                    else {
                        pred = b;
                        ++count;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Returns the Executor used for asynchronous delivery.
     *
     * @return the Executor used for asynchronous delivery
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Returns the maximum per-subscriber buffer capacity.
     *
     * @return the maximum per-subscriber buffer capacity
     */
    public int getMaxBufferCapacity() {
        return maxBufferCapacity;
    }

    /**
     * Returns a list of current subscribers for monitoring and
     * tracking purposes, not for invoking {@link Flow.Subscriber}
     * methods on the subscribers.
     *
     * @return list of current subscribers
     */
    public List<Flow.Subscriber<? super T>> getSubscribers() {
        ArrayList<Flow.Subscriber<? super T>> subs = new ArrayList<>();
        synchronized (this) {
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                next = b.next;
                if (b.isDisabled()) {
                    b.next = null;
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else
                    subs.add(b.subscriber);
            }
        }
        return subs;
    }

    /**
     * Returns true if the given Subscriber is currently subscribed.
     *
     * @param subscriber the subscriber
     * @return true if currently subscribed
     * @throws NullPointerException if subscriber is null
     */
    public boolean isSubscribed(Flow.Subscriber<? super T> subscriber) {
        if (subscriber == null) throw new NullPointerException();
        if (!closed) {
            synchronized (this) {
                BufferedSubscription<T> pred = null, next;
                for (BufferedSubscription<T> b = clients; b != null; b = next) {
                    next = b.next;
                    if (b.isDisabled()) {
                        b.next = null;
                        if (pred == null)
                            clients = next;
                        else
                            pred.next = next;
                    }
                    else if (subscriber.equals(b.subscriber))
                        return true;
                    else
                        pred = b;
                }
            }
        }
        return false;
    }

    /**
     * Returns an estimate of the minimum number of items requested
     * (via {@link Flow.Subscription#request(long) request}) but not
     * yet produced, among all current subscribers.
     *
     * @return the estimate, or zero if no subscribers
     */
    public long estimateMinimumDemand() {
        long min = Long.MAX_VALUE;
        boolean nonEmpty = false;
        synchronized (this) {
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                int n; long d;
                next = b.next;
                if ((n = b.estimateLag()) < 0) {
                    b.next = null;
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else {
                    if ((d = b.demand - n) < min)
                        min = d;
                    nonEmpty = true;
                    pred = b;
                }
            }
        }
        return nonEmpty ? min : 0;
    }

    /**
     * Returns an estimate of the maximum number of items produced but
     * not yet consumed among all current subscribers.
     *
     * @return the estimate
     */
    public int estimateMaximumLag() {
        int max = 0;
        synchronized (this) {
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                int n;
                next = b.next;
                if ((n = b.estimateLag()) < 0) {
                    b.next = null;
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else {
                    if (n > max)
                        max = n;
                    pred = b;
                }
            }
        }
        return max;
    }

    /**
     * Processes all published items using the given Consumer function.
     * Returns a CompletableFuture that is completed normally when this
     * publisher signals {@link Flow.Subscriber#onComplete()
     * onComplete}, or completed exceptionally upon any error, or an
     * exception is thrown by the Consumer, or the returned
     * CompletableFuture is cancelled, in which case no further items
     * are processed.
     *
     * @param consumer the function applied to each onNext item
     * @return a CompletableFuture that is completed normally
     * when the publisher signals onComplete, and exceptionally
     * upon any error or cancellation
     * @throws NullPointerException if consumer is null
     */
    public CompletableFuture<Void> consume(Consumer<? super T> consumer) {
        if (consumer == null)
            throw new NullPointerException();
        CompletableFuture<Void> status = new CompletableFuture<>();
        subscribe(new ConsumerSubscriber<T>(status, consumer));
        return status;
    }

    /** Subscriber for method consume */
    private static final class ConsumerSubscriber<T>
            implements Flow.Subscriber<T> {
        final CompletableFuture<Void> status;
        final Consumer<? super T> consumer;
        Flow.Subscription subscription;
        ConsumerSubscriber(CompletableFuture<Void> status,
                           Consumer<? super T> consumer) {
            this.status = status; this.consumer = consumer;
        }
        public final void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            status.whenComplete((v, e) -> subscription.cancel());
            if (!status.isDone())
                subscription.request(Long.MAX_VALUE);
        }
        public final void onError(Throwable ex) {
            status.completeExceptionally(ex);
        }
        public final void onComplete() {
            status.complete(null);
        }
        public final void onNext(T item) {
            try {
                consumer.accept(item);
            } catch (Throwable ex) {
                subscription.cancel();
                status.completeExceptionally(ex);
            }
        }
    }

    /**
     * A task for consuming buffer items and signals, created and
     * executed whenever they become available. A task consumes as
     * many items/signals as possible before terminating, at which
     * point another task is created when needed. The dual Runnable
     * and ForkJoinTask declaration saves overhead when executed by
     * ForkJoinPools, without impacting other kinds of Executors.
     */
    @SuppressWarnings("serial")
    static final class ConsumerTask<T> extends ForkJoinTask<Void>
        implements Runnable {
        final BufferedSubscription<T> consumer;
        ConsumerTask(BufferedSubscription<T> consumer) {
            this.consumer = consumer;
        }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) {}
        public final boolean exec() { consumer.consume(); return false; }
        public final void run() { consumer.consume(); }
    }

    /**
     * A bounded (ring) buffer with integrated control to start a
     * consumer task whenever items are available.  The buffer
     * algorithm is similar to one used inside ForkJoinPool (see its
     * internal documentation for details) specialized for the case of
     * at most one concurrent producer and consumer, and power of two
     * buffer sizes. This allows methods to operate without locks even
     * while supporting resizing, blocking, task-triggering, and
     * garbage-free buffers (nulling out elements when consumed),
     * although supporting these does impose a bit of overhead
     * compared to plain fixed-size ring buffers.
     *
     * The publisher guarantees a single producer via its lock.  We
     * ensure in this class that there is at most one consumer.  The
     * request and cancel methods must be fully thread-safe but are
     * coded to exploit the most common case in which they are only
     * called by consumers (usually within onNext).
     *
     * Execution control is managed using the ACTIVE ctl bit. We
     * ensure that a task is active when consumable items (and
     * usually, SUBSCRIBE, ERROR or COMPLETE signals) are present and
     * there is demand (unfilled requests).  This is complicated on
     * the creation side by the possibility of exceptions when trying
     * to execute tasks. These eventually force DISABLED state, but
     * sometimes not directly. On the task side, termination (clearing
     * ACTIVE) that would otherwise race with producers or request()
     * calls uses the CONSUME keep-alive bit to force a recheck.
     *
     * The ctl field also manages run state. When DISABLED, no further
     * updates are possible. Disabling may be preceded by setting
     * ERROR or COMPLETE (or both -- ERROR has precedence), in which
     * case the associated Subscriber methods are invoked, possibly
     * synchronously if there is no active consumer task (including
     * cases where execute() failed). The cancel() method is supported
     * by treating as ERROR but suppressing onError signal.
     *
     * Support for blocking also exploits the fact that there is only
     * one possible waiter. ManagedBlocker-compatible control fields
     * are placed in this class itself rather than in wait-nodes.
     * Blocking control relies on the "waiter" field. Producers set
     * the field before trying to block, but must then recheck (via
     * offer) before parking. Signalling then just unparks and clears
     * waiter field. If the producer and consumer are both in the same
     * ForkJoinPool, or consumers are running in commonPool, the
     * producer attempts to help run consumer tasks that it forked
     * before blocking.  To avoid potential cycles, only one level of
     * helping is currently supported.
     *
     * This class uses @Contended and heuristic field declaration
     * ordering to reduce false-sharing-based memory contention among
     * instances of BufferedSubscription, but it does not currently
     * attempt to avoid memory contention among buffers. This field
     * and element packing can hurt performance especially when each
     * publisher has only one client operating at a high rate.
     * Addressing this may require allocating substantially more space
     * than users expect.
     */
    @SuppressWarnings("serial")
    @sun.misc.Contended
    private static final class BufferedSubscription<T>
        implements Flow.Subscription, ForkJoinPool.ManagedBlocker {
        // Order-sensitive field declarations
        long timeout;                      // > 0 if timed wait
        volatile long demand;              // # unfilled requests
        int maxCapacity;                   // reduced on OOME
        int putStat;                       // offer result for ManagedBlocker
        int helpDepth;                     // nested helping depth (at most 1)
        volatile int ctl;                  // atomic run state flags
        volatile int head;                 // next position to take
        int tail;                          // next position to put
        Object[] array;                    // buffer: null if disabled
        Flow.Subscriber<? super T> subscriber; // null if disabled
        Executor executor;                 // null if disabled
        BiConsumer<? super Flow.Subscriber<? super T>, ? super Throwable> onNextHandler;
        volatile Throwable pendingError;   // holds until onError issued
        volatile Thread waiter;            // blocked producer thread
        T putItem;                         // for offer within ManagedBlocker
        BufferedSubscription<T> next;      // used only by publisher
        BufferedSubscription<T> nextRetry; // used only by publisher

        // ctl values
        static final int ACTIVE    = 0x01; // consumer task active
        static final int CONSUME   = 0x02; // keep-alive for consumer task
        static final int DISABLED  = 0x04; // final state
        static final int ERROR     = 0x08; // signal onError then disable
        static final int SUBSCRIBE = 0x10; // signal onSubscribe
        static final int COMPLETE  = 0x20; // signal onComplete when done

        static final long INTERRUPTED = -1L; // timeout vs interrupt sentinel

        /**
         * Initial buffer capacity used when maxBufferCapacity is
         * greater. Must be a power of two.
         */
        static final int DEFAULT_INITIAL_CAP = 32;

        BufferedSubscription(Flow.Subscriber<? super T> subscriber,
                             Executor executor,
                             BiConsumer<? super Flow.Subscriber<? super T>,
                             ? super Throwable> onNextHandler,
                             int maxBufferCapacity) {
            this.subscriber = subscriber;
            this.executor = executor;
            this.onNextHandler = onNextHandler;
            this.maxCapacity = maxBufferCapacity;
            this.array = new Object[maxBufferCapacity < DEFAULT_INITIAL_CAP ?
                                    (maxBufferCapacity < 2 ? // at least 2 slots
                                     2 : maxBufferCapacity) :
                                    DEFAULT_INITIAL_CAP];
        }

        final boolean isDisabled() {
            return ctl == DISABLED;
        }

        /**
         * Returns estimated number of buffered items, or -1 if
         * disabled.
         */
        final int estimateLag() {
            int n;
            return (ctl == DISABLED) ? -1 : ((n = tail - head) > 0) ? n : 0;
        }

        /**
         * Tries to add item and start consumer task if necessary.
         * @return -1 if disabled, 0 if dropped, else estimated lag
         */
        final int offer(T item) {
            int h = head, t = tail, cap, size, stat;
            Object[] a = array;
            if (a != null && (cap = a.length) > 0 && cap >= (size = t + 1 - h)) {
                a[(cap - 1) & t] = item;    // relaxed writes OK
                tail = t + 1;
                stat = size;
            }
            else
                stat = growAndAdd(a, item);
            return (stat > 0 &&
                    (ctl & (ACTIVE | CONSUME)) != (ACTIVE | CONSUME)) ?
                startOnOffer(stat) : stat;
        }

        /**
         * Tries to create or expand buffer, then adds item if possible.
         */
        private int growAndAdd(Object[] a, T item) {
            boolean alloc;
            int cap, stat;
            if ((ctl & (ERROR | DISABLED)) != 0) {
                cap = 0;
                stat = -1;
                alloc = false;
            }
            else if (a == null || (cap = a.length) <= 0) {
                cap = 0;
                stat = 1;
                alloc = true;
            }
            else {
                U.fullFence();                   // recheck
                int h = head, t = tail, size = t + 1 - h;
                if (cap >= size) {
                    a[(cap - 1) & t] = item;
                    tail = t + 1;
                    stat = size;
                    alloc = false;
                }
                else if (cap >= maxCapacity) {
                    stat = 0;                    // cannot grow
                    alloc = false;
                }
                else {
                    stat = cap + 1;
                    alloc = true;
                }
            }
            if (alloc) {
                int newCap = (cap > 0) ? cap << 1 : 1;
                if (newCap <= cap)
                    stat = 0;
                else {
                    Object[] newArray = null;
                    try {
                        newArray = new Object[newCap];
                    } catch (Throwable ex) {     // try to cope with OOME
                    }
                    if (newArray == null) {
                        if (cap > 0)
                            maxCapacity = cap;   // avoid continuous failure
                        stat = 0;
                    }
                    else {
                        array = newArray;
                        int t = tail;
                        int newMask = newCap - 1;
                        if (a != null && cap > 0) {
                            int mask = cap - 1;
                            for (int j = head; j != t; ++j) {
                                long k = ((long)(j & mask) << ASHIFT) + ABASE;
                                Object x = U.getObjectVolatile(a, k);
                                if (x != null && // races with consumer
                                    U.compareAndSwapObject(a, k, x, null))
                                    newArray[j & newMask] = x;
                            }
                        }
                        newArray[t & newMask] = item;
                        tail = t + 1;
                    }
                }
            }
            return stat;
        }

        /**
         * Spins/helps/blocks while offer returns 0.  Called only if
         * initial offer return 0.
         */
        final int submit(T item) {
            int stat; Executor e; ForkJoinWorkerThread w;
            if ((stat = offer(item)) == 0 && helpDepth == 0 &&
                ((e = executor) instanceof ForkJoinPool)) {
                helpDepth = 1;
                Thread thread = Thread.currentThread();
                if ((thread instanceof ForkJoinWorkerThread) &&
                    ((w = (ForkJoinWorkerThread)thread)).getPool() == e)
                    stat = internalHelpConsume(w.workQueue, item);
                else if (e == ForkJoinPool.commonPool())
                    stat = externalHelpConsume
                        (ForkJoinPool.commonSubmitterQueue(), item);
                helpDepth = 0;
            }
            if (stat == 0 && (stat = offer(item)) == 0) {
                putItem = item;
                timeout = 0L;
                try {
                    ForkJoinPool.managedBlock(this);
                } catch (InterruptedException ie) {
                    timeout = INTERRUPTED;
                }
                stat = putStat;
                if (timeout < 0L)
                    Thread.currentThread().interrupt();
            }
            return stat;
        }

        /**
         * Tries helping for FJ submitter.
         */
        private int internalHelpConsume(ForkJoinPool.WorkQueue w, T item) {
            int stat = 0;
            if (w != null) {
                ForkJoinTask<?> t;
                while ((t = w.peek()) != null && (t instanceof ConsumerTask)) {
                    if ((stat = offer(item)) != 0 || !w.tryUnpush(t))
                        break;
                    ((ConsumerTask<?>)t).consumer.consume();
                }
            }
            return stat;
        }

        /**
         * Tries helping for non-FJ submitter.
         */
        private int externalHelpConsume(ForkJoinPool.WorkQueue w, T item) {
            int stat = 0;
            if (w != null) {
                ForkJoinTask<?> t;
                while ((t = w.peek()) != null && (t instanceof ConsumerTask)) {
                    if ((stat = offer(item)) != 0 || !w.trySharedUnpush(t))
                        break;
                    ((ConsumerTask<?>)t).consumer.consume();
                }
            }
            return stat;
        }

        /**
         * Timeout version; similar to submit.
         */
        final int timedOffer(T item, long nanos) {
            int stat; Executor e;
            if ((stat = offer(item)) == 0 && helpDepth == 0 &&
                ((e = executor) instanceof ForkJoinPool)) {
                Thread thread = Thread.currentThread();
                if (((thread instanceof ForkJoinWorkerThread) &&
                     ((ForkJoinWorkerThread)thread).getPool() == e) ||
                    e == ForkJoinPool.commonPool()) {
                    helpDepth = 1;
                    ForkJoinTask<?> t;
                    long deadline = System.nanoTime() + nanos;
                    while ((t = ForkJoinTask.peekNextLocalTask()) != null &&
                           (t instanceof ConsumerTask)) {
                        if ((stat = offer(item)) != 0 ||
                            (nanos = deadline - System.nanoTime()) <= 0L ||
                            !t.tryUnfork())
                            break;
                        ((ConsumerTask<?>)t).consumer.consume();
                    }
                    helpDepth = 0;
                }
            }
            if (stat == 0 && (stat = offer(item)) == 0 &&
                (timeout = nanos) > 0L) {
                putItem = item;
                try {
                    ForkJoinPool.managedBlock(this);
                } catch (InterruptedException ie) {
                    timeout = INTERRUPTED;
                }
                stat = putStat;
                if (timeout < 0L)
                    Thread.currentThread().interrupt();
            }
            return stat;
        }

        /**
         * Tries to start consumer task after offer.
         * @return -1 if now disabled, else argument
         */
        private int startOnOffer(int stat) {
            for (;;) {
                Executor e; int c;
                if ((c = ctl) == DISABLED || (e = executor) == null) {
                    stat = -1;
                    break;
                }
                else if ((c & ACTIVE) != 0) { // ensure keep-alive
                    if ((c & CONSUME) != 0 ||
                        U.compareAndSwapInt(this, CTL, c,
                                            c | CONSUME))
                        break;
                }
                else if (demand == 0L || tail == head)
                    break;
                else if (U.compareAndSwapInt(this, CTL, c,
                                             c | (ACTIVE | CONSUME))) {
                    try {
                        e.execute(new ConsumerTask<T>(this));
                        break;
                    } catch (RuntimeException | Error ex) { // back out
                        do {} while (((c = ctl) & DISABLED) == 0 &&
                                     (c & ACTIVE) != 0 &&
                                     !U.compareAndSwapInt(this, CTL, c,
                                                          c & ~ACTIVE));
                        throw ex;
                    }
                }
            }
            return stat;
        }

        private void signalWaiter(Thread w) {
            waiter = null;
            LockSupport.unpark(w);    // release producer
        }

        /**
         * Nulls out most fields, mainly to avoid garbage retention
         * until publisher unsubscribes, but also to help cleanly stop
         * upon error by nulling required components.
         */
        private void detach() {
            Thread w = waiter;
            executor = null;
            subscriber = null;
            pendingError = null;
            signalWaiter(w);
        }

        /**
         * Issues error signal, asynchronously if a task is running,
         * else synchronously.
         */
        final void onError(Throwable ex) {
            for (int c;;) {
                if (((c = ctl) & (ERROR | DISABLED)) != 0)
                    break;
                else if ((c & ACTIVE) != 0) {
                    pendingError = ex;
                    if (U.compareAndSwapInt(this, CTL, c, c | ERROR))
                        break; // cause consumer task to exit
                }
                else if (U.compareAndSwapInt(this, CTL, c, DISABLED)) {
                    Flow.Subscriber<? super T> s = subscriber;
                    if (s != null && ex != null) {
                        try {
                            s.onError(ex);
                        } catch (Throwable ignore) {
                        }
                    }
                    detach();
                    break;
                }
            }
        }

        /**
         * Tries to start consumer task upon a signal or request;
         * disables on failure.
         */
        private void startOrDisable() {
            Executor e;
            if ((e = executor) != null) { // skip if already disabled
                try {
                    e.execute(new ConsumerTask<T>(this));
                } catch (Throwable ex) {  // back out and force signal
                    for (int c;;) {
                        if ((c = ctl) == DISABLED || (c & ACTIVE) == 0)
                            break;
                        if (U.compareAndSwapInt(this, CTL, c, c & ~ACTIVE)) {
                            onError(ex);
                            break;
                        }
                    }
                }
            }
        }

        final void onComplete() {
            for (int c;;) {
                if ((c = ctl) == DISABLED)
                    break;
                if (U.compareAndSwapInt(this, CTL, c,
                                        c | (ACTIVE | CONSUME | COMPLETE))) {
                    if ((c & ACTIVE) == 0)
                        startOrDisable();
                    break;
                }
            }
        }

        final void onSubscribe() {
            for (int c;;) {
                if ((c = ctl) == DISABLED)
                    break;
                if (U.compareAndSwapInt(this, CTL, c,
                                        c | (ACTIVE | CONSUME | SUBSCRIBE))) {
                    if ((c & ACTIVE) == 0)
                        startOrDisable();
                    break;
                }
            }
        }

        /**
         * Causes consumer task to exit if active (without reporting
         * onError unless there is already a pending error), and
         * disables.
         */
        public void cancel() {
            for (int c;;) {
                if ((c = ctl) == DISABLED)
                    break;
                else if ((c & ACTIVE) != 0) {
                    if (U.compareAndSwapInt(this, CTL, c,
                                            c | (CONSUME | ERROR)))
                        break;
                }
                else if (U.compareAndSwapInt(this, CTL, c, DISABLED)) {
                    detach();
                    break;
                }
            }
        }

        /**
         * Adds to demand and possibly starts task.
         */
        public void request(long n) {
            if (n > 0L) {
                for (;;) {
                    long prev = demand, d;
                    if ((d = prev + n) < prev) // saturate
                        d = Long.MAX_VALUE;
                    if (U.compareAndSwapLong(this, DEMAND, prev, d)) {
                        for (int c, h;;) {
                            if ((c = ctl) == DISABLED)
                                break;
                            else if ((c & ACTIVE) != 0) {
                                if ((c & CONSUME) != 0 ||
                                    U.compareAndSwapInt(this, CTL, c,
                                                        c | CONSUME))
                                    break;
                            }
                            else if ((h = head) != tail) {
                                if (U.compareAndSwapInt(this, CTL, c,
                                                        c | (ACTIVE|CONSUME))) {
                                    startOrDisable();
                                    break;
                                }
                            }
                            else if (head == h && tail == h)
                                break;          // else stale
                            if (demand == 0L)
                                break;
                        }
                        break;
                    }
                }
            }
            else if (n < 0L)
                onError(new IllegalArgumentException(
                            "negative subscription request"));
        }

        public final boolean isReleasable() { // for ManagedBlocker
            T item = putItem;
            if (item != null) {
                if ((putStat = offer(item)) == 0)
                    return false;
                putItem = null;
            }
            return true;
        }

        public final boolean block() { // for ManagedBlocker
            T item = putItem;
            if (item != null) {
                putItem = null;
                long nanos = timeout;
                long deadline = (nanos > 0L) ? System.nanoTime() + nanos : 0L;
                while ((putStat = offer(item)) == 0) {
                    if (Thread.interrupted()) {
                        timeout = INTERRUPTED;
                        if (nanos > 0L)
                            break;
                    }
                    else if (nanos > 0L &&
                             (nanos = deadline - System.nanoTime()) <= 0L)
                        break;
                    else if (waiter == null)
                        waiter = Thread.currentThread();
                    else {
                        if (nanos > 0L)
                            LockSupport.parkNanos(this, nanos);
                        else
                            LockSupport.park(this);
                        waiter = null;
                    }
                }
            }
            waiter = null;
            return true;
        }

        /**
         * Consumer loop, called from ConsumerTask, or indirectly
         * when helping during submit.
         */
        final void consume() {
            Flow.Subscriber<? super T> s;
            int h = head;
            if ((s = subscriber) != null) {           // else disabled
                for (;;) {
                    long d = demand;
                    int c; Object[] a; int n; long i; Object x; Thread w;
                    if (((c = ctl) & (ERROR | SUBSCRIBE | DISABLED)) != 0) {
                        if (!checkControl(s, c))
                            break;
                    }
                    else if ((a = array) == null || h == tail ||
                             (n = a.length) == 0 ||
                             (x = U.getObjectVolatile
                              (a, (i = ((long)((n - 1) & h) << ASHIFT) + ABASE)))
                             == null) {
                        if (!checkEmpty(s, c))
                            break;
                    }
                    else if (d == 0L) {
                        if (!checkDemand(c))
                            break;
                    }
                    else if (((c & CONSUME) != 0 ||
                              U.compareAndSwapInt(this, CTL, c, c | CONSUME)) &&
                             U.compareAndSwapObject(a, i, x, null)) {
                        U.putOrderedInt(this, HEAD, ++h);
                        U.getAndAddLong(this, DEMAND, -1L);
                        if ((w = waiter) != null)
                            signalWaiter(w);
                        try {
                            @SuppressWarnings("unchecked") T y = (T) x;
                            s.onNext(y);
                        } catch (Throwable ex) {
                            handleOnNext(s, ex);
                        }
                    }
                }
            }
        }

        /**
         * Responds to control events in consume().
         */
        private boolean checkControl(Flow.Subscriber<? super T> s, int c) {
            boolean stat = true;
            if ((c & ERROR) != 0) {
                Throwable ex = pendingError;
                ctl = DISABLED;           // no need for CAS
                if (ex != null) {         // null if errorless cancel
                    try {
                        if (s != null)
                            s.onError(ex);
                    } catch (Throwable ignore) {
                    }
                }
            }
            else if ((c & SUBSCRIBE) != 0) {
                if (U.compareAndSwapInt(this, CTL, c, c & ~SUBSCRIBE)) {
                    try {
                        if (s != null)
                            s.onSubscribe(this);
                    } catch (Throwable ex) {
                        onError(ex);
                    }
                }
            }
            else {
                detach();
                stat = false;
            }
            return stat;
        }

        /**
         * Responds to apparent emptiness in consume().
         */
        private boolean checkEmpty(Flow.Subscriber<? super T> s, int c) {
            boolean stat = true;
            if (head == tail) {
                if ((c & CONSUME) != 0)
                    U.compareAndSwapInt(this, CTL, c, c & ~CONSUME);
                else if ((c & COMPLETE) != 0) {
                    if (U.compareAndSwapInt(this, CTL, c, DISABLED)) {
                        try {
                            if (s != null)
                                s.onComplete();
                        } catch (Throwable ignore) {
                        }
                    }
                }
                else if (U.compareAndSwapInt(this, CTL, c, c & ~ACTIVE))
                    stat = false;
            }
            return stat;
        }

        /**
         * Responds to apparent zero demand in consume().
         */
        private boolean checkDemand(int c) {
            boolean stat = true;
            if (demand == 0L) {
                if ((c & CONSUME) != 0)
                    U.compareAndSwapInt(this, CTL, c, c & ~CONSUME);
                else if (U.compareAndSwapInt(this, CTL, c, c & ~ACTIVE))
                    stat = false;
            }
            return stat;
        }

        /**
         * Processes exception in Subscriber.onNext.
         */
        private void handleOnNext(Flow.Subscriber<? super T> s, Throwable ex) {
            BiConsumer<? super Flow.Subscriber<? super T>, ? super Throwable> h;
            if ((h = onNextHandler) != null) {
                try {
                    h.accept(s, ex);
                } catch (Throwable ignore) {
                }
            }
            onError(ex);
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe U = sun.misc.Unsafe.getUnsafe();
        private static final long CTL;
        private static final long TAIL;
        private static final long HEAD;
        private static final long DEMAND;
        private static final int ABASE;
        private static final int ASHIFT;

        static {
            try {
                CTL = U.objectFieldOffset
                    (BufferedSubscription.class.getDeclaredField("ctl"));
                TAIL = U.objectFieldOffset
                    (BufferedSubscription.class.getDeclaredField("tail"));
                HEAD = U.objectFieldOffset
                    (BufferedSubscription.class.getDeclaredField("head"));
                DEMAND = U.objectFieldOffset
                    (BufferedSubscription.class.getDeclaredField("demand"));

                ABASE = U.arrayBaseOffset(Object[].class);
                int scale = U.arrayIndexScale(Object[].class);
                if ((scale & (scale - 1)) != 0)
                    throw new Error("data type scale not a power of two");
                ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
            } catch (ReflectiveOperationException e) {
                throw new Error(e);
            }

            // Reduce the risk of rare disastrous classloading in first call to
            // LockSupport.park: https://bugs.openjdk.java.net/browse/JDK-8074773
            Class<?> ensureLoaded = LockSupport.class;
        }
    }
}
