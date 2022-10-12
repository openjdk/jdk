/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.test.lib.net.URIBuilder;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @summary Test idempotency and commutativity of responses with an exhaustive
 *          set of binary request sequences
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder
 * @run testng/othervm IdempotencyAndCommutativityTest
 */
public class IdempotencyAndCommutativityTest {

    static final Path CWD = Path.of(".").toAbsolutePath().normalize();
    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final String FILE_NAME = "file.txt";
    static final String DIR_NAME = "";
    static final String MISSING_FILE_NAME = "doesNotExist";

    static HttpServer server;
    static HttpClient client;

    static final boolean ENABLE_LOGGING = true;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    @BeforeTest
    public void setup() throws IOException {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
        Path root = Files.createDirectories(CWD.resolve("testDirectory"));
        Files.writeString(root.resolve(FILE_NAME), "some text", CREATE);

        client = HttpClient.newBuilder().proxy(NO_PROXY).build();
        server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, SimpleFileServer.OutputLevel.VERBOSE);
        server.start();
    }

    // Container of expected response state for a given request
    record ExchangeValues(String method, String resource, int respCode, String contentType) {}

    // Creates an exhaustive set of binary exchange sequences
    @DataProvider
    public Object[][] allBinarySequences() {
        final List<ExchangeValues> sequences =  List.of(
                new ExchangeValues("GET",     FILE_NAME,         200, "text/plain"),
                new ExchangeValues("GET",     DIR_NAME,          200, "text/html; charset=UTF-8"),
                new ExchangeValues("GET",     MISSING_FILE_NAME, 404, "text/html; charset=UTF-8"),
                new ExchangeValues("HEAD",    FILE_NAME,         200, "text/plain"),
                new ExchangeValues("HEAD",    DIR_NAME,          200, "text/html; charset=UTF-8"),
                new ExchangeValues("HEAD",    MISSING_FILE_NAME, 404, "text/html; charset=UTF-8"),
                new ExchangeValues("UNKNOWN", FILE_NAME,         501, null),
                new ExchangeValues("UNKNOWN", DIR_NAME,          501, null),
                new ExchangeValues("UNKNOWN", MISSING_FILE_NAME, 501, null)
        );

        return sequences.stream()  // cartesian product
                        .flatMap(s1 -> sequences.stream().map(s2 -> new ExchangeValues[] { s1, s2 }))
                        .toArray(Object[][]::new);
    }

    @Test(dataProvider = "allBinarySequences")
    public void testBinarySequences(ExchangeValues e1, ExchangeValues e2) throws Exception {
        System.out.println("---");
        System.out.println(e1);
        executeExchange(e1);
        System.out.println(e2);
        executeExchange(e2);
    }

    private static void executeExchange(ExchangeValues e) throws Exception {
        var request = HttpRequest.newBuilder(uri(server, e.resource()))
                                            .method(e.method(), HttpRequest.BodyPublishers.noBody())
                                            .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(response.statusCode(), e.respCode());
        if (e.contentType != null) {
            assertEquals(response.headers().firstValue("content-type").get(), e.contentType());
        } else {
            assertTrue(response.headers().firstValue("content-type").isEmpty());
        }
    }

    @AfterTest
    public static void teardown() {
        server.stop(0);
    }

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .buildUnchecked();
    }
}
