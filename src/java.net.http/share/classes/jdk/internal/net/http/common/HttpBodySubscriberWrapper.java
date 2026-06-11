/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.common;

import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.ResponseSubscribers.TrustedSubscriber;

/**
 * A class that wraps a user supplied {@link BodySubscriber}, but on
 * which {@link #onError(Throwable)} can be invoked at any time,
 * even before {@link #onSubscribe(Subscription)} has not been called
 * yet.
 * @param <T> the type of the response body
 */
public class HttpBodySubscriberWrapper<T> implements TrustedSubscriber<T> {

    public static final Comparator<HttpBodySubscriberWrapper<?>> COMPARE_BY_ID
            = Comparator.comparing(HttpBodySubscriberWrapper::id);

    public static final Flow.Subscription NOP = new Flow.Subscription() {
        @Override
        public void request(long n) { }
        public void cancel() { }
    };

    static final int SUBSCRIBED = 1;
    static final int REGISTERED = 2;
    static final int COMPLETED = 4;
    static final int CANCELLED = 8;
    static final int UNREGISTERED = 16;

    static final AtomicLong IDS = new AtomicLong();
    final long id = IDS.incrementAndGet();
    final BodySubscriber<T> userSubscriber;
    private volatile int state;
    final ReentrantLock subscriptionLock = new ReentrantLock();
    volatile SubscriptionWrapper subscription;
    volatile Throwable withError;
    public HttpBodySubscriberWrapper(BodySubscriber<T> userSubscriber) {
        Objects.requireNonNull(userSubscriber, "BodySubscriber");
        this.userSubscriber = userSubscriber;
    }

    /**
     * A callback to be invoked <em>before</em> termination, whether due to the
     * completion or failure of the subscriber, or cancellation of the
     * subscription. To be precise, this method is called before
     * {@link #onComplete()}, {@link #onError(Throwable) onError()}, or
     * {@link #onCancel()}.
     */
    protected void onTermination() {
        // Do nothing
    }

    private final class SubscriptionWrapper implements Subscription {
        final Subscription subscription;
        SubscriptionWrapper(Subscription s) {
            this.subscription = Objects.requireNonNull(s);
        }
        @Override
        public void request(long n) {
            subscription.request(n);
        }

        @Override
        public void cancel() {
            try {
                try {
                    subscription.cancel();
                } finally {
                    if (markCancelled()) {
                        onTermination();
                        onCancel();
                    }
                }
            } catch (Throwable t) {
                onError(t);
            }
        }
    }

    private final boolean markState(final int flag) {
        int state = this.state;
        if ((state & flag) == flag) {
            return false;
        }
        synchronized (this) {
            state = this.state;
            if ((state & flag) == flag) {
                return false;
            }
            state = this.state = (state | flag);
        }
        assert (state & flag) == flag;
        return true;
    }

    private boolean markSubscribed() {
        return markState(SUBSCRIBED);
    }

    private boolean markCancelled() {
        return markState(CANCELLED);
    }

    private boolean markCompleted() {
        return markState(COMPLETED);
    }

    private boolean markRegistered() {
        return markState(REGISTERED);
    }

    private boolean markUnregistered() {
        return markState(UNREGISTERED);
    }

    final long id() { return id; }

    @Override
    public boolean needsExecutor() {
        return TrustedSubscriber.needsExecutor(userSubscriber);
    }

    // propagate the error to the user subscriber, even if not
    // subscribed yet.
    private void propagateError(Throwable t) {
        var state = this.state;
        assert t != null;
        assert (state & COMPLETED) != 0;
        try {
            // if unsubscribed at this point, it will not
            // get subscribed later - so do it now and
            // propagate the error
            // Race condition with onSubscribe: we need to wait until
            // subscription is finished before calling onError;
            subscriptionLock.lock();
            try {
                if (markSubscribed()) {
                    userSubscriber.onSubscribe(NOP);
                }
            } finally {
                subscriptionLock.unlock();
            }
        } finally  {
            // if onError throws then there is nothing to do
            // here: let the caller deal with it by logging
            // and closing the connection.
            userSubscriber.onError(t);
        }
    }

    /**
     * This method attempts to mark the state of this
     * object as registered, and then call the
     * {@link #register()} method.
     * <p>
     * The state will be marked as registered, and the
     * {@code register()} method will be called only
     * if not already registered or unregistered,
     * or cancelled, or completed.
     *
     * @return {@code true} if {@link #register()} was called,
     * false otherwise.
     */
    protected final boolean tryRegister() {
        subscriptionLock.lock();
        try {
            int state = this.state;
            if ((state & (REGISTERED | UNREGISTERED | CANCELLED | COMPLETED)) != 0) return false;
            if (markRegistered()) {
                register();
                return true;
            }
        } finally {
            subscriptionLock.unlock();
        }
        return false;
    }

    /**
     * This method attempts to mark the state of this
     * object as unregistered, and then call the
     * {@link #unregister()} method.
     * <p>
     * The {@code unregister()} method will be called only
     * if already registered and not yet unregistered.
     * Whether {@code unregister()} is called or not,
     * the state is marked as unregistered, to prevent
     * {@link #tryRegister()} from calling {@link #register()}
     * after {@link #tryUnregister()} has been called.
     *
     * @return {@code true} if {@link #unregister()} was called,
     * false otherwise.
     */
    protected final boolean tryUnregister() {
        subscriptionLock.lock();
        try {
            int state = this.state;
            if ((state & REGISTERED) == 0) {
                markUnregistered();
                return false;
            }
            if (markUnregistered()) {
                unregister();
                return true;
            }
        } finally {
            subscriptionLock.unlock();
        }
        return false;
    }

    /**
     * This method can be implemented by subclasses
     * to perform registration actions. It will not be
     * called if already registered or unregistered.
     * @apiNote
     * This method is called while holding a subscription
     * lock.
     * @see #tryRegister()
     */
    protected void register() {
        assert subscriptionLock.isHeldByCurrentThread();
    }

    /**
     * This method can be implemented by subclasses
     * to perform unregistration actions. It will not be
     * called if not already registered, or already unregistered.
     * @apiNote
     * This method is called while holding a subscription
     * lock.
     * @see #tryUnregister()
     */
    protected void unregister() {
        assert subscriptionLock.isHeldByCurrentThread();
    }

    /**
     * Called when the subscriber cancels its subscription.
     * @apiNote
     * This method may be used by subclasses to perform cleanup
     * actions after a subscription has been cancelled.
     * @implSpec
     * This method calls {@link #tryUnregister()}
     */
    protected void onCancel() {
        // If the subscription is cancelled the
        // subscriber may or may not get completed.
        // Therefore we need to unregister it
        tryUnregister();
    }

    /**
     * Complete the subscriber, either normally or exceptionally
     * ensure that the subscriber is completed only once.
     * @param t a throwable, or {@code null}
     * @implSpec
     * If not {@linkplain #completed()} yet, this method
     * calls {@link #tryUnregister()}
     */
    public final void complete(Throwable t) {
        if (markCompleted()) {
            onTermination();
            logComplete(t);
            tryUnregister();
            t  = withError = Utils.getCompletionCause(t);
            if (t == null) {
                try {
                    var state = this.state;
                    assert (state & SUBSCRIBED) != 0;
                    userSubscriber.onComplete();
                } catch (Throwable x) {
                    // Simply propagate the error by calling
                    // onError on the user subscriber, and let the
                    // connection be reused since we should have received
                    // and parsed all the bytes when we reach here.
                    // If onError throws in turn, then we will simply
                    // let that new exception flow up to the caller
                    // and let it deal with it.
                    // (i.e: log and close the connection)
                    // Note that rethrowing here could introduce a
                    // race that might cause the next send() operation to
                    // fail as the connection has already been put back
                    // into the cache when we reach here.
                    propagateError(t = withError = Utils.getCompletionCause(x));
                }
            } else {
                propagateError(t);
            }
        }
    }

    protected void logComplete(Throwable error) {

    }

    /**
     * {@return true if this subscriber has already completed, either normally
     * or abnormally}
     */
    public final boolean completed() {
        int state = this.state;
        return (state & COMPLETED) != 0;
    }

    /**
     * {@return true if this subscriber has already subscribed}
     */
    public final boolean subscribed() {
        int state = this.state;
        return (state & SUBSCRIBED) != 0;
    }

    /**
     * {@return true if this subscriber has already been registered}
     */
    public final boolean registered() {
        int state = this.state;
        return (state & REGISTERED) != 0;
    }

    /**
     * {@return true if this subscriber has already been unregistered}
     */
    public final boolean unregistered() {
        int state = this.state;
        return (state & UNREGISTERED) != 0;
    }

    /**
     * {@return true if this subscriber's subscription has already
     * been cancelled}
     */
    public final boolean cancelled() {
        int state = this.state;
        return (state & CANCELLED) != 0;
    }


    @Override
    public CompletionStage<T> getBody() {
        return userSubscriber.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        // race condition with propagateError: we need to wait until
        // subscription is finished before calling onError;
        subscriptionLock.lock();
        try {
            tryRegister();
            if (markSubscribed()) {
                SubscriptionWrapper wrapped = new SubscriptionWrapper(subscription);
                userSubscriber.onSubscribe(this.subscription = wrapped);
            } else {
                subscription.cancel();
            }
        } finally {
            subscriptionLock.unlock();
        }
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        var state = this.state;
        assert (state & SUBSCRIBED) != 0;
        if ((state & COMPLETED) != 0) {
            SubscriptionWrapper subscription = this.subscription;
            if (subscription != null) {
                subscription.subscription.cancel();
            }
        } else {
            userSubscriber.onNext(item);
        }
    }
    @Override
    public void onError(Throwable throwable) {
        complete(throwable);
    }

    @Override
    public void onComplete() {
        complete(null);
    }
}
