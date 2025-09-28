/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8161157
 * @summary Test response body handlers/subscribers when there is no body
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm
 *      -Djdk.internal.httpclient.debug=true
 *      -Djdk.httpclient.HttpClient.log=all
 *      NoBodyPartTwo
 */

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import jdk.internal.net.http.common.Utils;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Version.HTTP_3;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class NoBodyPartTwo extends AbstractNoBody {

    volatile boolean consumerHasBeenCalled;
    @Test(dataProvider = "variants")
    public void testAsByteArrayConsumer(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testAsByteArrayConsumer(\"%s\", %s)", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                HttpRequest req = newRequestBuilder(uri)
                        .PUT(BodyPublishers.ofString(SIMPLE_STRING))
                        .build();
                Consumer<Optional<byte[]>> consumer = oba -> {
                    consumerHasBeenCalled = true;
                    oba.ifPresent(ba -> fail("Unexpected non-empty optional: "
                            + Utils.asString(ByteBuffer.wrap(ba))));
                };
                consumerHasBeenCalled = false;
                client.send(req, BodyHandlers.ofByteArrayConsumer(consumer));
                assertTrue(consumerHasBeenCalled);
            }
        }
    }

    @Test(dataProvider = "variants")
    public void testAsInputStream(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testAsInputStream(\"%s\", %s)", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                HttpRequest req = newRequestBuilder(uri)
                        .PUT(BodyPublishers.ofString(SIMPLE_STRING))
                        .build();
                HttpResponse<InputStream> response = client.send(req, BodyHandlers.ofInputStream());
                byte[] body = response.body().readAllBytes();
                assertEquals(body.length, 0);
            }
        }
    }

    @Test(dataProvider = "variants")
    public void testBuffering(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testBuffering(\"%s\", %s)", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                HttpRequest req = newRequestBuilder(uri)
                        .PUT(BodyPublishers.ofString(SIMPLE_STRING))
                        .build();
                HttpResponse<byte[]> response = client.send(req,
                        BodyHandlers.buffering(BodyHandlers.ofByteArray(), 1024));
                byte[] body = response.body();
                assertEquals(body.length, 0);
            }
        }
    }

    @Test(dataProvider = "variants")
    public void testDiscard(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testDiscard(\"%s\", %s)", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                HttpRequest req = newRequestBuilder(uri)
                        .PUT(BodyPublishers.ofString(SIMPLE_STRING))
                        .build();
                Object obj = new Object();
                HttpResponse<Object> response = client.send(req, BodyHandlers.replacing(obj));
                assertEquals(response.body(), obj);
            }
        }
    }
}
