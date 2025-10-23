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

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8364733
 * @summary Verify all specified `HttpRequest.BodyPublishers::ofByteArray` behavior
 * @build RecordingSubscriber
 * @run junit OfByteArrayTest
 *
 * @comment Using `main/othervm` to initiate tests that depend on a custom-configured JVM
 * @run main/othervm -Djdk.httpclient.bufsize=3 OfByteArrayTest testChunking "" 0 0 ""
 * @run main/othervm -Djdk.httpclient.bufsize=3 OfByteArrayTest testChunking a 0 0 ""
 * @run main/othervm -Djdk.httpclient.bufsize=3 OfByteArrayTest testChunking a 1 0 ""
 * @run main/othervm -Djdk.httpclient.bufsize=3 OfByteArrayTest testChunking a 0 1 a
 * @run main/othervm -Djdk.httpclient.bufsize=3 OfByteArrayTest testChunking ab 0 1 a
 * @run main/othervm -Djdk.httpclient.bufsize=3 OfByteArrayTest testChunking ab 1 1 b
 * @run main/othervm -Djdk.httpclient.bufsize=3 OfByteArrayTest testChunking ab 0 2 ab
 * @run main/othervm -Djdk.httpclient.bufsize=1 OfByteArrayTest testChunking abc 0 3 a:b:c
 * @run main/othervm -Djdk.httpclient.bufsize=2 OfByteArrayTest testChunking abc 0 3 ab:c
 * @run main/othervm -Djdk.httpclient.bufsize=2 OfByteArrayTest testChunking abcdef 2 4 cd:ef
 */

public class OfByteArrayTest {

    private static final Charset CHARSET = StandardCharsets.US_ASCII;

    @Test
    void testNullContent() {
        assertThrows(NullPointerException.class, () -> HttpRequest.BodyPublishers.ofByteArray(null));
        assertThrows(NullPointerException.class, () -> HttpRequest.BodyPublishers.ofByteArray(null, 1, 2));
    }

    @ParameterizedTest
    @CsvSource({
            "abc,-1,1",     // Negative offset
            "abc,1,-1",     // Negative length
            "'',1,1",       // Offset overflow on empty string
            "a,2,1",        // Offset overflow
            "'',0,1",       // Length overflow on empty string
            "a,0,2",        // Length overflow
    })
    void testInvalidOffsetOrLength(String contentText, int offset, int length) {
        byte[] content = contentText.getBytes(CHARSET);
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> HttpRequest.BodyPublishers.ofByteArray(content, offset, length));
    }

    /**
     * Initiates tests that depend on a custom-configured JVM.
     */
    public static void main(String[] args) throws InterruptedException {
        switch (args[0]) {
            case "testChunking" -> testChunking(
                    parseStringArg(args[1]),
                    Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]),
                    parseStringArg(args[4]));
            default -> throw new IllegalArgumentException("Unexpected arguments: " + List.of(args));
        }
    }

    private static String parseStringArg(String arg) {
        return arg == null || arg.trim().equals("\"\"") ? "" : arg;
    }

    private static void testChunking(
            String contentText, int offset, int length, String expectedBuffersText)
            throws InterruptedException {

        // Create the publisher
        byte[] content = contentText.getBytes(CHARSET);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArray(content, offset, length);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, length);

        // Verify the state after `request()`
        String[] expectedBuffers = expectedBuffersText.isEmpty() ? new String[0] : expectedBuffersText.split(":");
        subscription.request(Long.MAX_VALUE);
        for (int bufferIndex = 0; bufferIndex < expectedBuffers.length; bufferIndex++) {
            assertEquals("onNext", subscriber.invocations.take());
            String actualBuffer = CHARSET.decode((ByteBuffer) subscriber.invocations.take()).toString();
            String expectedBuffer = expectedBuffers[bufferIndex];
            assertEquals(expectedBuffer, actualBuffer, "buffer mismatch at index " + bufferIndex);
        }
        assertEquals("onComplete", subscriber.invocations.take());

    }

}
