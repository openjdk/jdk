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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8364733
 * @summary Verify all specified `HttpRequest.BodyPublishers::ofString` behavior
 * @build ByteBufferUtils
 *        RecordingSubscriber
 * @run junit OfStringTest
 */

class OfStringTest {

    private static final Charset CHARSET = StandardCharsets.US_ASCII;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void testContentOfLength(int length) throws InterruptedException {

        // Create the publisher
        char[] contentChars = new char[length];
        for (int i = 0; i < length; i++) {
            contentChars[i] = (char) ('a' + i);
        }
        String content = new String(contentChars);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(content, CHARSET);

        // Subscribe
        assertEquals(length, publisher.contentLength());
        RecordingSubscriber subscriber = new RecordingSubscriber();
        publisher.subscribe(subscriber);
        assertEquals("onSubscribe", subscriber.invocations.take());
        Flow.Subscription subscription = (Flow.Subscription) subscriber.invocations.take();

        // Verify the state after `request()`
        subscription.request(Long.MAX_VALUE);
        if (length > 0) {
            assertEquals("onNext", subscriber.invocations.take());
            String actualContent = CHARSET.decode((ByteBuffer) subscriber.invocations.take()).toString();
            assertEquals(content, actualContent);
        }
        assertEquals("onComplete", subscriber.invocations.take());

    }

    @ParameterizedTest
    @CsvSource({
            "a,UTF-8",
            "b,UTF-16",
            "ı,ISO-8859-9"
    })
    void testCharset(String content, Charset charset) throws InterruptedException {

        // Create the publisher
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(content, charset);

        // Subscribe
        ByteBuffer expectedBuffer = charset.encode(content);
        assertEquals(expectedBuffer.limit(), publisher.contentLength());
        RecordingSubscriber subscriber = new RecordingSubscriber();
        publisher.subscribe(subscriber);
        assertEquals("onSubscribe", subscriber.invocations.take());
        Flow.Subscription subscription = (Flow.Subscription) subscriber.invocations.take();

        // Verify the state after `request()`
        subscription.request(Long.MAX_VALUE);
        assertEquals("onNext", subscriber.invocations.take());
        ByteBuffer actualBuffer = (ByteBuffer) subscriber.invocations.take();
        ByteBufferUtils.assertEquals(expectedBuffer, actualBuffer, null);
        assertEquals("onComplete", subscriber.invocations.take());

    }

    @Test
    void testNullContent() {
        assertThrows(NullPointerException.class, () -> HttpRequest.BodyPublishers.ofString(null, CHARSET));
    }

    @Test
    void testNullCharset() {
        assertThrows(NullPointerException.class, () -> HttpRequest.BodyPublishers.ofString("foo", null));
    }

}
