/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpRequest.BodyPublisher;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for verifying that a request body publisher supports multiple subscriptions, aka. replayable.
 */
public abstract class ReplayTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {
            // 2 subscriptions
            "subscribe-cancel-subscribe-cancel",
            "subscribe-cancel-subscribe-request",
            "subscribe-request-subscribe-cancel",
            "subscribe-request-subscribe-request",
            // 3 subscriptions
            "subscribe-cancel-subscribe-cancel-subscribe-request",
            "subscribe-cancel-subscribe-request-subscribe-cancel",
            "subscribe-cancel-subscribe-request-subscribe-request",
            "subscribe-request-subscribe-cancel-subscribe-cancel",
            "subscribe-request-subscribe-cancel-subscribe-request",
            "subscribe-request-subscribe-request-subscribe-cancel",
            "subscribe-request-subscribe-request-subscribe-request",
    })
    void testReplay(String opSequence) throws Exception {
        for (ReplayTarget replayTarget : createReplayTargets()) {
            try (replayTarget) {
                System.err.printf("Executing test for replay target: %s%n", replayTarget);
                testReplay(opSequence, replayTarget);
            }
        }
    }

    private static void testReplay(String opSequence, ReplayTarget replayTarget) throws InterruptedException {

        // Create the publisher
        ByteBuffer expectedBuffer = replayTarget.expectedBuffer;
        BodyPublisher publisher = replayTarget.publisher;
        assertEquals(replayTarget.expectedContentLength, publisher.contentLength());

        // Execute the specified operations
        RecordingSubscriber subscriber = null;
        Flow.Subscription subscription = null;
        String[] ops = opSequence.split("-");
        for (int opIndex = 0; opIndex < ops.length; opIndex++) {
            String op = ops[opIndex];
            System.err.printf("Executing operation at index %s: %s%n", opIndex, op);
            switch (op) {

                case "subscribe": {
                    subscriber = new RecordingSubscriber();
                    publisher.subscribe(subscriber);
                    assertEquals("onSubscribe", subscriber.invocations.take());
                    subscription = (Flow.Subscription) subscriber.invocations.take();
                    break;
                }

                case "request": {
                    assert subscription != null;
                    subscription.request(Long.MAX_VALUE);
                    if (expectedBuffer.hasRemaining()) {
                        assertEquals("onNext", subscriber.invocations.take());
                        ByteBuffer actualBuffer = (ByteBuffer) subscriber.invocations.take();
                        ByteBufferUtils.assertEquals(expectedBuffer, actualBuffer, null);
                    }
                    assertEquals("onComplete", subscriber.invocations.take());
                    break;
                }

                case "cancel": {
                    assert subscription != null;
                    subscription.cancel();
                    break;
                }

                default: throw new IllegalArgumentException("Unknown operation: " + op);

            }
        }

    }

    abstract Iterable<ReplayTarget> createReplayTargets();

    public record ReplayTarget(
            ByteBuffer expectedBuffer,
            int expectedContentLength,
            BodyPublisher publisher,
            AutoCloseable resource)
            implements AutoCloseable {

        public ReplayTarget(ByteBuffer expectedBuffer, BodyPublisher publisher) {
            this(expectedBuffer, expectedBuffer.limit(), publisher, null);
        }

        @Override
        public void close() throws Exception {
            if (resource != null) {
                resource.close();
            }
        }

    }

}
