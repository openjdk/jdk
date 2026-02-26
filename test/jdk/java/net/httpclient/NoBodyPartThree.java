/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8308024
 * @summary Test request and response body handlers/subscribers when there is no body
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.http2.Http2TestServer
 * @run junit/othervm
 *      -Djdk.httpclient.HttpClient.log=quic,errors
 *      -Djdk.httpclient.HttpClient.log=all
 *      NoBodyPartThree
 */

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import jdk.internal.net.http.common.Utils;

import static java.net.http.HttpClient.Version.HTTP_3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// @TestInstance(TestInstance.Lifecycle.PER_CLASS)
// is inherited from the super class
public class NoBodyPartThree extends AbstractNoBody {

    static final AtomicInteger REQID = new AtomicInteger();

    volatile boolean consumerHasBeenCalled;
    @ParameterizedTest
    @MethodSource("variants")
    public void testAsByteArrayPublisher(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testAsByteArrayPublisher(\"%s\", %s)", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                var u = uri + "/testAsByteArrayPublisher/first/" + REQID.getAndIncrement();
                HttpRequest req = newRequestBuilder(u + "?echo")
                        .PUT(BodyPublishers.ofByteArrays(List.of()))
                        .build();
                System.out.println("sending " + req);
                Consumer<Optional<byte[]>> consumer = oba -> {
                    consumerHasBeenCalled = true;
                    oba.ifPresent(ba -> fail("Unexpected non-empty optional:"
                            + Utils.asString(ByteBuffer.wrap(ba))));
                };
                consumerHasBeenCalled = false;
                var response = client.send(req, BodyHandlers.ofByteArrayConsumer(consumer));
                assertTrue(consumerHasBeenCalled);
                assertEquals(200, response.statusCode());

                u = uri + "/testAsByteArrayPublisher/second/" + REQID.getAndIncrement();
                req = newRequestBuilder(u + "?echo")
                        .PUT(BodyPublishers.ofByteArrays(List.of(new byte[0])))
                        .build();
                System.out.println("sending " + req);
                consumerHasBeenCalled = false;
                response = client.send(req, BodyHandlers.ofByteArrayConsumer(consumer));
                assertTrue(consumerHasBeenCalled);
                assertEquals(200, response.statusCode());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    public void testStringPublisher(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testStringPublisher(\"%s\", %s)", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                var u = uri + "/testStringPublisher/" + REQID.getAndIncrement();
                HttpRequest req = newRequestBuilder(u + "?echo")
                        .PUT(BodyPublishers.ofString(""))
                        .build();
                System.out.println("sending " + req);
                HttpResponse<InputStream> response = client.send(req, BodyHandlers.ofInputStream());
                assertEquals(200, response.statusCode());
                byte[] body = response.body().readAllBytes();
                assertEquals(0, body.length);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    public void testInputStreamPublisherBuffering(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testInputStreamPublisherBuffering(\"%s\", %s)", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                var u = uri + "/testInputStreamPublisherBuffering/" + REQID.getAndIncrement();
                HttpRequest req = newRequestBuilder(u + "?echo")
                        .PUT(BodyPublishers.ofInputStream(InputStream::nullInputStream))
                        .build();
                System.out.println("sending " + req);
                HttpResponse<byte[]> response = client.send(req,
                        BodyHandlers.buffering(BodyHandlers.ofByteArray(), 1024));
                assertEquals(200, response.statusCode());
                byte[] body = response.body();
                assertEquals(0, body.length);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    public void testEmptyArrayPublisher(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testEmptyArrayPublisher(\"%s\", %s)", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                var u = uri + "/testEmptyArrayPublisher/" + REQID.getAndIncrement();
                HttpRequest req = newRequestBuilder(u + "?echo")
                        .PUT(BodyPublishers.ofByteArray(new byte[0]))
                        .build();
                System.out.println("sending " + req);
                var response = client.send(req, BodyHandlers.ofLines());
                assertEquals(200, response.statusCode());
                assertEquals(List.of(), response.body().toList());
            }
        }
    }
}
