/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.util.Optional;
import java.util.concurrent.Flow;
import jdk.incubator.http.internal.common.Log;

/**
 * A Publisher which is assumed to run in its own thread.
 *
 * acceptData() may therefore block while waiting for subscriber demand
 */
class BlockingPushPublisher<T> extends AbstractPushPublisher<T> {
    volatile Subscription subscription;
    volatile Flow.Subscriber<? super T> subscriber;
    volatile SubscriptionState state;
    long demand;

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        state = SubscriptionState.OPENED;
        subscription = new Subscription(subscriber);
        subscriber.onSubscribe(subscription);
    }

    /**
     * Entry point for supplying items to publisher. This call will block
     * when no demand available.
     */
    @Override
    public void acceptData(Optional<T> item) throws InterruptedException {
        SubscriptionState s = this.state;

        // do not use switch(this.state): this.state could be null.
        if (s == SubscriptionState.CANCELLED) return;
        if (s == SubscriptionState.DONE) {
            throw new IllegalStateException("subscription complete");
        }

        if (!item.isPresent()) {
            subscriber.onComplete();
            this.state = SubscriptionState.DONE;
        } else {
            obtainPermit();
            if (this.state == SubscriptionState.CANCELLED) return;
            subscriber.onNext(item.get());
        }
    }

    /**
     * Terminates the publisher with given exception.
     */
    @Override
    public void acceptError(Throwable t) {
        if (this.state != SubscriptionState.OPENED) {
            Log.logError(t);
            return;
        }
        subscriber.onError(t);
        cancel();
    }

    private synchronized void obtainPermit() throws InterruptedException {
        while (demand == 0) {
            wait();
        }
        if (this.state == SubscriptionState.DONE) {
            throw new IllegalStateException("subscription complete");
        }
        demand --;
    }

    synchronized void addPermits(long n) {
        long old = demand;
        demand += n;
        if (old == 0) {
            notifyAll();
        }
    }

    synchronized void cancel() {
        this.state = SubscriptionState.CANCELLED;
        notifyAll();
    }

    private class Subscription implements Flow.Subscription {

        Subscription(Flow.Subscriber<? super T> subscriber) {
            BlockingPushPublisher.this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            addPermits(n);
        }

        @Override
        public void cancel() {
            BlockingPushPublisher.this.cancel();
        }
    }
}
