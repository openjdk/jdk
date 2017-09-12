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
import java.util.function.Consumer;

/**
 * A single threaded Publisher which runs in same thread as subscriber.
 */
class PushPublisher<T> extends AbstractPushPublisher<T> {
    Subscription subscription;
    Flow.Subscriber<? super T> subscriber;
    SubscriptionState state;
    long demand;

    /**
     * Pushes/consumes the incoming objects.
     * The consumer blocks until subscriber ready to receive.
     */
    @Override
    public void acceptData(Optional<T> item) {
        SubscriptionState s = this.state;

        if (s == SubscriptionState.CANCELLED) return;
        if (s == SubscriptionState.DONE) {
            throw new IllegalStateException("subscription complete");
        }

        if (!item.isPresent()) {
            subscriber.onComplete();
            this.state = SubscriptionState.DONE;
            return;
        }
        if (demand == 0) {
            throw new IllegalStateException("demand == 0");
        }
        demand--;
        subscriber.onNext(item.get());
    }

    @Override
    public Consumer<Optional<T>> asDataConsumer() {
        return this::acceptData;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        subscription = new Subscription(subscriber);
        subscriber.onSubscribe(subscription);
    }

    private class Subscription implements Flow.Subscription {

        Subscription(Flow.Subscriber<? super T> subscriber) {
            PushPublisher.this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            demand += n;
        }

        @Override
        public void cancel() {
            state = SubscriptionState.CANCELLED;
        }
    }

    @Override
    public void acceptError(Throwable t) {
        if (this.state == SubscriptionState.DONE) {
            throw new IllegalStateException("subscription complete");
        }
        this.state = SubscriptionState.CANCELLED;
        subscriber.onError(t);
    }
}
