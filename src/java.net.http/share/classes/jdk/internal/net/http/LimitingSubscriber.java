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

import java.net.http.HttpResponse.BodySubscriber;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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

    private final boolean discardExcess;

    private final AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();

    private long length;

    /**
     * @param downstreamSubscriber the downstream subscriber to pass received data to
     * @param capacity the maximum number of bytes that are allowed
     * @param discardExcess if {@code true}, excessive input will be discarded; otherwise, it will throw an exception
     * @throws IllegalArgumentException if {@code capacity < 0}
     */
    public LimitingSubscriber(BodySubscriber<T> downstreamSubscriber, long capacity, boolean discardExcess) {
        if (capacity < 0) {
            throw new IllegalArgumentException("was expecting \"capacity >= 0\", found: " + capacity);
        }
        this.downstreamSubscriber = requireNonNull(downstreamSubscriber, "downstreamSubscriber");
        this.capacity = capacity;
        this.discardExcess = discardExcess;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        requireNonNull(subscription, "subscription");
        boolean alreadySubscribed = !subscriptionRef.compareAndSet(null, subscription);
        if (alreadySubscribed) {
            subscription.cancel();
        } else {
            downstreamSubscriber.onSubscribe(subscription);
            length = 0;
            subscription.request(1);    // Request piecemeal
        }
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {

        // Check arguments
        requireNonNull(buffers, "buffers");
        assert Utils.hasRemaining(buffers);

        // See if we can consume the input completely
        boolean lengthAllocated = allocateLength(buffers);
        Subscription subscription = subscriptionRef.get();
        assert subscription != null;
        if (lengthAllocated) {
            downstreamSubscriber.onNext(buffers);
            subscription.request(1);    // Request piecemeal
        }

        // See if we can consume the input partially
        else if (discardExcess) {
            List<ByteBuffer> retainedBuffers = removeExcess(buffers);
            if (!retainedBuffers.isEmpty()) {
                downstreamSubscriber.onNext(retainedBuffers);
            }
            subscription.cancel();
            downstreamSubscriber.onComplete();
        }

        // Partial consumption is not allowed, trigger failure
        else {
            subscription.cancel();
            downstreamSubscriber.onError(new IllegalStateException(
                    "the maximum number of bytes that are allowed to be consumed is exceeded"));
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

    private List<ByteBuffer> removeExcess(List<ByteBuffer> buffers) {
        List<ByteBuffer> retainedBuffers = new ArrayList<>(buffers.size());
        long remaining = capacity - length;
        for (ByteBuffer buffer : buffers) {
            // No capacity left; stop
            if (remaining < 1) {
                break;
            }
            // Buffer fits as is; keep it
            else if (buffer.remaining() <= remaining) {
                retainedBuffers.add(buffer);
                remaining -= buffer.remaining();
            }
            // There is capacity, but the buffer doesn't fit; truncate and keep it
            else {
                buffer.limit(Math.toIntExact(Math.addExact(buffer.position(), remaining)));
                retainedBuffers.add(buffer);
                remaining = 0;
            }
        }
        return retainedBuffers;
    }

    @Override
    public void onError(Throwable throwable) {
        requireNonNull(throwable, "throwable");
        downstreamSubscriber.onError(throwable);
    }

    @Override
    public void onComplete() {
        downstreamSubscriber.onComplete();
    }

    @Override
    public CompletionStage<T> getBody() {
        return downstreamSubscriber.getBody();
    }

}
