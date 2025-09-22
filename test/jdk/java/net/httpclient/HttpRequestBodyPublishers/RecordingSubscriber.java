/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test subscriber recording received invocations.
 */
public final class RecordingSubscriber implements Flow.Subscriber<ByteBuffer> {

    public final BlockingQueue<Object> invocations = new LinkedBlockingQueue<>();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        invocations.add("onSubscribe");
        invocations.add(subscription);
    }

    @Override
    public void onNext(ByteBuffer item) {
        invocations.add("onNext");
        invocations.add(item);
    }

    @Override
    public synchronized void onError(Throwable throwable) {
        invocations.add("onError");
        invocations.add(throwable);
    }

    @Override
    public synchronized void onComplete() {
        invocations.add("onComplete");
    }

    /**
     * Verifies the content length of the given publisher and subscribes to it.
     */
    public Flow.Subscription verifyAndSubscribe(HttpRequest.BodyPublisher publisher, long contentLength)
            throws InterruptedException {
        assertEquals(contentLength, publisher.contentLength());
        publisher.subscribe(this);
        assertEquals("onSubscribe", invocations.take());
        return (Flow.Subscription) invocations.take();
    }

    /**
     * {@return the byte sequence collected by draining all emissions until completion}
     *
     * @param subscription a subscription to drain from
     * @param itemCount the number of items to request per iteration
     */
    public byte[] drainToByteArray(Flow.Subscription subscription, long itemCount) throws InterruptedException {
        return drainToByteArray(subscription, itemCount, new ArrayList<>());
    }

    /**
     * {@return the byte sequence collected by draining all emissions until completion}
     *
     * @param subscription a subscription to drain from
     * @param itemCount the number of items to request per iteration
     * @param buffers a list to accumulate the received content in
     */
    public byte[] drainToByteArray(Flow.Subscription subscription, long itemCount, List<ByteBuffer> buffers)
            throws InterruptedException {
        drainToAccumulator(subscription, itemCount, buffers::add);
        return flattenBuffers(buffers);
    }

    /**
     * Drains all emissions until completion to the given {@code accumulator}.
     *
     * @param subscription a subscription to drain from
     * @param itemCount the number of items to request per iteration
     * @param accumulator an accumulator to pass the received content to
     */
    public void drainToAccumulator(
            Flow.Subscription subscription, long itemCount, Consumer<ByteBuffer> accumulator)
            throws InterruptedException {
        boolean completed = false;
        while (!completed) {
            subscription.request(itemCount);
            String op = (String) invocations.take();
            if ("onNext".equals(op)) {
                ByteBuffer buffer = (ByteBuffer) invocations.take();
                accumulator.accept(buffer);
            } else if ("onComplete".equals(op)) {
                completed = true;
            } else {
                throw new AssertionError("Unexpected invocation: " + op);
            }
        }
    }

    private static byte[] flattenBuffers(List<ByteBuffer> buffers) {
        int arrayLength = buffers.stream().mapToInt(ByteBuffer::limit).sum();
        byte[] array = new byte[arrayLength];
        for (int bufferIndex = 0, arrayOffset = 0; bufferIndex < buffers.size(); bufferIndex++) {
            ByteBuffer buffer = buffers.get(bufferIndex);
            int bufferLimit = buffer.limit();
            buffer.get(array, arrayOffset, bufferLimit);
            arrayOffset += bufferLimit;
        }
        return array;
    }

}
