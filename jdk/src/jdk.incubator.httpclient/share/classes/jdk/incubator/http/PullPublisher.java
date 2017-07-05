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

import java.util.Iterator;
import java.util.concurrent.Flow;

/**
 * A Publisher that is expected to run in same thread as subscriber.
 * Items are obtained from Iterable. Each new subscription gets a new Iterator.
 */
class PullPublisher<T> implements Flow.Publisher<T> {

    private final Iterable<T> iterable;

    PullPublisher(Iterable<T> iterable) {
        this.iterable = iterable;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new Subscription(subscriber, iterable.iterator()));
    }

    private class Subscription implements Flow.Subscription {

        private final Flow.Subscriber<? super T> subscriber;
        private final Iterator<T> iter;
        private boolean done = false;
        private long demand = 0;
        private int recursion = 0;

        Subscription(Flow.Subscriber<? super T> subscriber, Iterator<T> iter) {
            this.subscriber = subscriber;
            this.iter = iter;
        }

        @Override
        public void request(long n) {
            if (done) {
                subscriber.onError(new IllegalArgumentException("request(" + n + ")"));
            }
            demand += n;
            recursion ++;
            if (recursion > 1) {
                return;
            }
            while (demand > 0) {
                done = !iter.hasNext();
                if (done) {
                    subscriber.onComplete();
                    recursion --;
                    return;
                }
                subscriber.onNext(iter.next());
                demand --;
            }
        }

        @Override
        public void cancel() {
            done = true;
        }

    }
}
