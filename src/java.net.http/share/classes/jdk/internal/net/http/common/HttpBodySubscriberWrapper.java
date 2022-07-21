/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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

    static final AtomicLong IDS = new AtomicLong();
    final long id = IDS.incrementAndGet();
    final BodySubscriber<T> userSubscriber;
    final AtomicBoolean completed = new AtomicBoolean();
    final AtomicBoolean subscribed = new AtomicBoolean();
    volatile Subscription subscription;
    volatile Throwable withError;
    public HttpBodySubscriberWrapper(BodySubscriber<T> userSubscriber) {
        this.userSubscriber = userSubscriber;
    }

    final long id() { return id; }

    @Override
    public boolean needsExecutor() {
        return TrustedSubscriber.needsExecutor(userSubscriber);
    }

    // propagate the error to the user subscriber, even if not
    // subscribed yet.
    private void propagateError(Throwable t) {
        assert t != null;
        try {
            // if unsubscribed at this point, it will not
            // get subscribed later - so do it now and
            // propagate the error
            // Race condition with onSubscribe: we need to wait until
            // subscription is finished before calling onError;
            synchronized (this) {
                if (subscribed.compareAndSet(false, true)) {
                    userSubscriber.onSubscribe(NOP);
                }
            }
        } finally  {
            // if onError throws then there is nothing to do
            // here: let the caller deal with it by logging
            // and closing the connection.
            userSubscriber.onError(t);
        }
    }

    /**
     * Complete the subscriber, either normally or exceptionally
     * ensure that the subscriber is completed only once.
     * @param t a throwable, or {@code null}
     */
    protected void complete(Throwable t) {
        if (completed.compareAndSet(false, true)) {
            t  = withError = Utils.getCompletionCause(t);
            if (t == null) {
                try {
                    assert subscribed.get();
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

    @Override
    public CompletionStage<T> getBody() {
        return userSubscriber.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        // race condition with propagateError: we need to wait until
        // subscription is finished before calling onError;
        synchronized (this) {
            if (subscribed.compareAndSet(false, true)) {
                userSubscriber.onSubscribe(subscription);
            } else {
                // could be already subscribed and completed
                // if an unexpected error occurred before the actual
                // subscription - though that's not supposed
                // happen.
                assert completed.get();
            }
        }
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        if (completed.get()) {
            if (subscription != null) {
                subscription.cancel();
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
