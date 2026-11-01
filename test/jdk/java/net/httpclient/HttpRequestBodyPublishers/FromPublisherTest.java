/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.net.http.HttpRequest.BodyPublisher;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.net.http.HttpRequest.BodyPublishers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8364733
 * @summary Verify all specified `HttpRequest.BodyPublishers::fromPublisher` behavior
 * @build ByteBufferUtils
 *        RecordingSubscriber
 *        ReplayTestSupport
 * @run junit ${test.main.class}
 */

class FromPublisherTest extends ReplayTestSupport {

    @Test
    void testNullPublisher() {
        assertThrows(NullPointerException.class, () -> fromPublisher(null));
        assertThrows(NullPointerException.class, () -> fromPublisher(null, 1));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void testInvalidContentLength(long contentLength) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fromPublisher(null, contentLength));
        String exceptionMessage = exception.getMessage();
        assertTrue(
                exceptionMessage.contains("non-positive contentLength"),
                "Unexpected exception message: " + exceptionMessage);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4})
    void testValidContentLength(long contentLength) {
        BodyPublisher publisher = fromPublisher(noBody(), contentLength);
        assertEquals(contentLength, publisher.contentLength());
    }

    @Test
    void testNoContentLength() {
        BodyPublisher publisher = fromPublisher(noBody());
        assertEquals(-1, publisher.contentLength());
    }

    @Test
    void testNullSubscriber() {
        BodyPublisher publisher = fromPublisher(noBody());
        assertThrows(NullPointerException.class, () -> publisher.subscribe(null));
    }

    @Test
    void testDelegation() throws InterruptedException {
        BlockingQueue<Object> publisherInvocations = new LinkedBlockingQueue<>();
        BodyPublisher publisher = fromPublisher(subscriber -> {
            publisherInvocations.add("subscribe");
            publisherInvocations.add(subscriber);
        });
        RecordingSubscriber subscriber = new RecordingSubscriber();
        publisher.subscribe(subscriber);
        assertEquals("subscribe", publisherInvocations.take());
        assertEquals(subscriber, publisherInvocations.take());
        assertTrue(subscriber.invocations.isEmpty());
    }

    @Override
    Iterable<ReplayTarget> createReplayTargets() {
        byte[] content = ByteBufferUtils.byteArrayOfLength(10);
        ByteBuffer expectedBuffer = ByteBuffer.wrap(content);
        BodyPublisher delegatePublisher = ofByteArray(content);
        BodyPublisher publisher = fromPublisher(delegatePublisher, delegatePublisher.contentLength());
        return List.of(new ReplayTarget(expectedBuffer, publisher));
    }

}
