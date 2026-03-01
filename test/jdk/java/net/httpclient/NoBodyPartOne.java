/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm
 *      -Djdk.internal.httpclient.debug=true
 *      -Djdk.httpclient.HttpClient.log=all
 *      NoBodyPartOne
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.nio.charset.StandardCharsets.UTF_8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// @TestInstance(TestInstance.Lifecycle.PER_CLASS)
// is inherited from the super class
public class NoBodyPartOne extends AbstractNoBody {

    @ParameterizedTest
    @MethodSource("variants")
    public void testAsString(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testAsString(\"%s\", %s)", uri, sameClient);
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
                BodyHandler<String> handler = i % 2 == 0 ? BodyHandlers.ofString()
                        : BodyHandlers.ofString(UTF_8);
                HttpResponse<String> response = client.send(req, handler);
                String body = response.body();
                assertEquals("", body);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    public void testAsFile(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testAsFile(\"%s\", %s)", uri, sameClient);
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
                Path p = Paths.get("NoBody_testAsFile.txt");
                HttpResponse<Path> response = client.send(req, BodyHandlers.ofFile(p));
                Path bodyPath = response.body();
                assertEquals(200, response.statusCode());
                assertTrue(Files.exists(bodyPath));
                assertEquals(0, Files.size(bodyPath), Files.readString(bodyPath));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    public void testAsByteArray(String uri, boolean sameClient) throws Exception {
        printStamp(START, "testAsByteArray(\"%s\", %s)", uri, sameClient);
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
                HttpResponse<byte[]> response = client.send(req, BodyHandlers.ofByteArray());
                byte[] body = response.body();
                assertEquals(0, body.length);
            }
        }
    }
}
