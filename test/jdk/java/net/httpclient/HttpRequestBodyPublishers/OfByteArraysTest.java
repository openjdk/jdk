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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8226303 8364733
 * @summary Verify all specified `HttpRequest.BodyPublishers::ofByteArrays` behavior
 * @build ByteBufferUtils
 *        RecordingSubscriber
 * @run junit OfByteArraysTest
 *
 * @comment Using `main/othervm` to initiate tests that depend on a custom-configured JVM
 * @run main/othervm -Xmx64m OfByteArraysTest testOOM
 */

public class OfByteArraysTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void testIteratorOfLength(int length) throws InterruptedException {

        // Create the publisher
        List<byte[]> buffers = IntStream
                .range(0, length)
                .mapToObj(i -> new byte[]{(byte) i})
                .toList();
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArrays(buffers::iterator);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Verify the state after `request()`
        subscription.request(Long.MAX_VALUE);
        for (int bufferIndex = 0; bufferIndex < length; bufferIndex++) {
            assertEquals("onNext", subscriber.invocations.take());
            byte[] expectedBuffer = buffers.get(bufferIndex);
            ByteBuffer actualBuffer = (ByteBuffer) subscriber.invocations.take();
            ByteBufferUtils.assertEquals(expectedBuffer, actualBuffer, "buffer mismatch at index " + bufferIndex);
        }
        assertEquals("onComplete", subscriber.invocations.take());

    }

    @Test
    void testDifferentIterators() throws InterruptedException {

        // Create a publisher using an iterable that returns a different iterator at each invocation
        byte[] buffer1 = ByteBufferUtils.byteArrayOfLength(9);
        byte[] buffer2 = ByteBufferUtils.byteArrayOfLength(9);
        int[] iteratorRequestCount = {0};
        Iterable<byte[]> iterable = () -> switch (++iteratorRequestCount[0]) {
            case 1 -> List.of(buffer1).iterator();
            case 2 -> List.of(buffer2).iterator();
            default -> throw new AssertionError();
        };
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArrays(iterable);

        // Subscribe twice (to force two `Iterable::iterator` invocations)
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription1 = subscriber.verifyAndSubscribe(publisher, -1);
        Flow.Subscription subscription2 = subscriber.verifyAndSubscribe(publisher, -1);

        // Drain emissions until completion, and verify the content
        byte[] actualBuffer1 = subscriber.drainToByteArray(subscription1, Long.MAX_VALUE);
        byte[] actualBuffer2 = subscriber.drainToByteArray(subscription2, Long.MAX_VALUE);
        ByteBufferUtils.assertEquals(buffer1, actualBuffer1, null);
        ByteBufferUtils.assertEquals(buffer2, actualBuffer2, null);

    }

    @Test
    void testNullIterable() {
        assertThrows(NullPointerException.class, () -> HttpRequest.BodyPublishers.ofByteArrays(null));
    }

    @Test
    void testNullIterator() throws InterruptedException {

        // Create the publisher
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArrays(() -> null);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Verify the NPE
        subscription.request(1);
        assertEquals("onError", subscriber.invocations.take());
        assertInstanceOf(NullPointerException.class, subscriber.invocations.take());

    }

    @Test
    void testNullArray() throws InterruptedException {

        // Create the publisher
        List<byte[]> iterable = new ArrayList<>();
        iterable.add(null);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArrays(iterable);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Verify the NPE
        subscription.request(1);
        assertEquals("onError", subscriber.invocations.take());
        assertInstanceOf(NullPointerException.class, subscriber.invocations.take());

    }

    @Test
    void testThrowingIterable() throws InterruptedException {

        // Create the publisher
        RuntimeException exception = new RuntimeException("failure for `testIteratorCreationException`");
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArrays(() -> {
            throw exception;
        });

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Verify the failure
        subscription.request(1);
        assertEquals("onError", subscriber.invocations.take());
        Exception actualException = (Exception) subscriber.invocations.take();
        assertSame(exception, actualException);

    }

    static Stream<Arguments> testThrowingIteratorArgs() {
        RuntimeException hasNextException = new RuntimeException("failure for `hasNext`");
        RuntimeException nextException = new RuntimeException("failure for `next`");
        return Stream.of(
                Arguments.of(0, hasNextException, null, hasNextException),
                Arguments.of(0, hasNextException, nextException, hasNextException),
                Arguments.of(1, hasNextException, null, hasNextException),
                Arguments.of(1, hasNextException, nextException, hasNextException),
                Arguments.of(1, null, nextException, nextException));
    }

    @ParameterizedTest
    @MethodSource("testThrowingIteratorArgs")
    void testThrowingIterator(
            int exceptionIndex, RuntimeException hasNextException, RuntimeException nextException, Exception expectedException)
            throws InterruptedException {

        // Create the publisher
        IteratorThrowingAtEnd iterator =
                new IteratorThrowingAtEnd(exceptionIndex, hasNextException, nextException);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArrays(() -> iterator);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Drain successful emissions
        subscription.request(Long.MAX_VALUE);
        for (int itemIndex = 0; itemIndex < exceptionIndex; itemIndex++) {
            assertEquals("onNext", subscriber.invocations.take());
            ByteBuffer actualBuffer = (ByteBuffer) subscriber.invocations.take();
            ByteBuffer expectedBuffer = ByteBuffer.wrap(iterator.content, itemIndex, 1);
            ByteBufferUtils.assertEquals(expectedBuffer, actualBuffer, null);
        }

        // Verify the result
        if (expectedException == null) {
            assertEquals("onComplete", subscriber.invocations.take());
        } else {
            assertEquals("onError", subscriber.invocations.take());
            Exception actualException = (Exception) subscriber.invocations.take();
            assertSame(expectedException, actualException);
        }

    }

    private static final class IteratorThrowingAtEnd implements Iterator<byte[]> {

        private final byte[] content;

        private final RuntimeException hasNextException;

        private final RuntimeException nextException;

        private int position;

        private IteratorThrowingAtEnd(
                int length,
                RuntimeException hasNextException,
                RuntimeException nextException) {
            this.content = ByteBufferUtils.byteArrayOfLength(length);
            this.hasNextException = hasNextException;
            this.nextException = nextException;
        }

        @Override
        public synchronized boolean hasNext() {
            if (position >= content.length && hasNextException != null) {
                throw hasNextException;
            }
            // We always instruct to proceed, so `next()` can throw
            return true;
        }

        @Override
        public synchronized byte[] next() {
            if (position < content.length) {
                return new byte[]{content[position++]};
            }
            assertNotNull(nextException);
            throw nextException;
        }

    }

    /**
     * Initiates tests that depend on a custom-configured JVM.
     */
    public static void main(String[] args) throws Exception {
        if ("testOOM".equals(args[0])) {
            testOOM();
        } else {
            throw new IllegalArgumentException("Unknown arguments: " + List.of(args));
        }
    }

    private static void testOOM() throws Exception {

        // Create the publisher
        int length = ByteBufferUtils.findLengthExceedingMaxMemory();
        Iterable<byte[]> iterable = createIterableOfLength(length);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArrays(iterable);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Drain emissions until completion, and verify the received content length
        final int[] readLength = {0};
        subscriber.drainToAccumulator(subscription, 1, buffer -> readLength[0] += buffer.limit());
        assertEquals(length, readLength[0]);

    }

    private static Iterable<byte[]> createIterableOfLength(int length) {
        return () -> new Iterator<>() {

            // Instead of emitting `length` at once, doing it gradually using a buffer to avoid OOM.
            private final byte[] buffer = new byte[8192];

            private volatile int remainingLength = length;

            @Override
            public boolean hasNext() {
                return remainingLength > 0;
            }

            @Override
            public synchronized byte[] next() {
                if (remainingLength >= buffer.length) {
                    remainingLength -= buffer.length;
                    return buffer;
                } else {
                    byte[] remainingBuffer = new byte[remainingLength];
                    remainingLength = 0;
                    return remainingBuffer;
                }
            }

        };
    }

}
