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
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8364733
 * @summary Verify all specified `HttpRequest.BodyPublishers::ofInputStream` behavior
 * @build ByteBufferUtils
 *        RecordingSubscriber
 * @run junit OfInputStreamTest
 *
 * @comment Using `main/othervm` to initiate tests that depend on a custom-configured JVM
 * @run main/othervm -Xmx64m OfInputStreamTest testOOM
 */

public class OfInputStreamTest {

    @Test
    void testNullInputStreamSupplier() {
        assertThrows(NullPointerException.class, () -> HttpRequest.BodyPublishers.ofInputStream(null));
    }

    @Test
    void testThrowingInputStreamSupplier() throws InterruptedException {

        // Create the publisher
        RuntimeException exception = new RuntimeException();
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofInputStream(() -> { throw exception; });

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Verify the state after `request()`
        subscription.request(1);
        assertEquals("onError", subscriber.invocations.take());
        IOException actualException = (IOException) subscriber.invocations.take();
        assertEquals("Stream supplier has failed", actualException.getMessage());
        assertSame(exception, actualException.getCause());

    }

    @Test
    void testNullInputStream() throws InterruptedException {

        // Create the publisher
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofInputStream(() -> null);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Verify the state after `request()`
        subscription.request(1);
        assertEquals("onError", subscriber.invocations.take());
        IOException actualException = (IOException) subscriber.invocations.take();
        assertEquals("Stream supplier returned null", actualException.getMessage());

    }

    @Test
    void testInputStreamSupplierInvocations() throws InterruptedException {

        // Create a publisher from an `InputStream` supplier returning a different instance at each invocation
        byte[] buffer1 = ByteBufferUtils.byteArrayOfLength(10);
        byte[] buffer2 = ByteBufferUtils.byteArrayOfLength(10);
        int[] inputStreamSupplierInvocationCount = {0};
        Supplier<InputStream> inputStreamSupplier = () ->
                switch (++inputStreamSupplierInvocationCount[0]) {
                    case 1 -> new ByteArrayInputStream(buffer1);
                    case 2 -> new ByteArrayInputStream(buffer2);
                    default -> throw new AssertionError();
                };
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofInputStream(inputStreamSupplier);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription1 = subscriber.verifyAndSubscribe(publisher, -1);
        Flow.Subscription subscription2 = subscriber.verifyAndSubscribe(publisher, -1);

        // Drain each subscription and verify the received content
        byte[] actualBuffer1 = subscriber.drainToByteArray(subscription1, Long.MAX_VALUE);
        ByteBufferUtils.assertEquals(buffer1, actualBuffer1, null);
        byte[] actualBuffer2 = subscriber.drainToByteArray(subscription2, Long.MAX_VALUE);
        ByteBufferUtils.assertEquals(buffer2, actualBuffer2, null);

    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void testInputStreamOfLength(int length) throws InterruptedException {

        // Create the publisher
        byte[] content = ByteBufferUtils.byteArrayOfLength(length);
        InputStream inputStream = new ByteArrayInputStream(content);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofInputStream(() -> inputStream);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Drain emissions until completion, and verify the received content
        byte[] actualContent = subscriber.drainToByteArray(subscription, Long.MAX_VALUE);
        ByteBufferUtils.assertEquals(content, actualContent, null);

    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void testThrowingInputStream(int exceptionIndex) throws InterruptedException {

        // Create the publisher
        RuntimeException exception = new RuntimeException("failure for `read`");
        InputStream inputStream = new InputStreamThrowingOnCompletion(exceptionIndex, exception);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofInputStream(() -> inputStream);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Verify the failure
        subscription.request(1);
        assertEquals("onError", subscriber.invocations.take());
        Exception actualException = (Exception) subscriber.invocations.take();
        assertSame(exception, actualException);

    }

    private static final class InputStreamThrowingOnCompletion extends InputStream {

        private final int length;

        private final RuntimeException exception;

        private int position;

        private InputStreamThrowingOnCompletion(int length, RuntimeException exception) {
            this.length = length;
            this.exception = exception;
        }

        @Override
        public synchronized int read() {
            if (position < length) {
                return position++ & 0xFF;
            }
            throw exception;
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

    private static void testOOM() throws InterruptedException {

        // Create the publisher using an `InputStream` that emits content exceeding the maximum memory
        int length = ByteBufferUtils.findLengthExceedingMaxMemory();
        HttpRequest.BodyPublisher publisher =
                HttpRequest.BodyPublishers.ofInputStream(() -> new InputStream() {

                    private int position;

                    @Override
                    public synchronized int read() {
                        return position < length ? (position++ & 0xFF) : -1;
                    }

                });

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, -1);

        // Drain emissions until completion, and verify the received content length
        final int[] readLength = {0};
        subscriber.drainToAccumulator(subscription, 1, buffer -> readLength[0] += buffer.limit());
        assertEquals(length, readLength[0]);

    }

}
