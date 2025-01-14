/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import jdk.internal.net.http.ResponseSubscribers.TrustedSubscriber;
import jdk.internal.net.http.common.Utils;

import java.io.IOException;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * A subscriber limiting the maximum number of bytes that are allowed to be consumed by a downstream subscriber.
 *
 * @param <T> the response type
 */
public final class LimitingSubscriber<T> implements TrustedSubscriber<T> {

    private final BodySubscriber<T> downstreamSubscriber;

    private final long capacity;

    private final AtomicReference<State> stateRef = new AtomicReference<>();

    private long length;

    private interface State {

        enum Terminated implements State { INSTANCE }

        record Subscribed(Subscription subscription) implements State {}

    }

    /**
     * @param downstreamSubscriber the downstream subscriber to pass received data to
     * @param capacity the maximum number of bytes that are allowed
     * @throws IllegalArgumentException if {@code capacity < 0}
     */
    public LimitingSubscriber(BodySubscriber<T> downstreamSubscriber, long capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("was expecting \"capacity >= 0\", found: " + capacity);
        }
        this.downstreamSubscriber = requireNonNull(downstreamSubscriber, "downstreamSubscriber");
        this.capacity = capacity;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        requireNonNull(subscription, "subscription");
        boolean subscribed = stateRef.compareAndSet(null, new State.Subscribed(subscription));
        if (!subscribed) {
            subscription.cancel();
        } else {
            downstreamSubscriber.onSubscribe(subscription);
        }
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {

        // Check arguments
        requireNonNull(buffers, "buffers");
        assert Utils.hasRemaining(buffers);

        // Short-circuit if not subscribed
        if (!(stateRef.get() instanceof State.Subscribed subscribed)) {
            return;
        }

        // See if we may consume the input
        if (allocateLength(buffers)) {
            downstreamSubscriber.onNext(buffers);
        }

        // Otherwise, trigger failure
        else if (stateRef.compareAndSet(subscribed, State.Terminated.INSTANCE)) {
            downstreamSubscriber.onError(new IOException(
                    "the maximum number of bytes that are allowed to be consumed is exceeded"));
            subscribed.subscription.cancel();
        }

    }

    private boolean allocateLength(List<ByteBuffer> buffers) {
        long bufferLength = buffers.stream().mapToLong(Buffer::remaining).sum();
        long nextReceivedByteCount = Math.addExact(length, bufferLength);
        if (nextReceivedByteCount > capacity) {
            return false;
        }
        length = nextReceivedByteCount;
        return true;
    }

    @Override
    public void onError(Throwable throwable) {
        requireNonNull(throwable, "throwable");
        if (terminate()) {
            downstreamSubscriber.onError(throwable);
        }
    }

    @Override
    public void onComplete() {
        if (terminate()) {
            downstreamSubscriber.onComplete();
        }
    }

    private boolean terminate() {
        return stateRef.get() instanceof State.Subscribed subscribed &&
                stateRef.compareAndSet(subscribed, State.Terminated.INSTANCE);
    }

    @Override
    public CompletionStage<T> getBody() {
        return downstreamSubscriber.getBody();
    }

}
