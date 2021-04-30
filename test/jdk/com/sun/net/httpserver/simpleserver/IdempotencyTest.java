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
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.testng.Assert.assertEquals;

/*
 * @test
 * @summary Test idempotence of responses with a reasonable non-exhaustive set
 *          of randomized request sequences
 * @run testng/othervm IdempotencyTest
 */
public class IdempotencyTest {

    static final InetSocketAddress WILDCARD_ADDR = new InetSocketAddress(0);
    static final Path CWD = Path.of(".").toAbsolutePath();
    static final String FILE_NAME = "testFile.txt";
    static final String DIR_NAME = "";
    static HttpServer server;
    static Path root;
    static Path file;

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
        root = Files.createDirectory(CWD.resolve("testDirectory"));
        file = Files.writeString(root.resolve(FILE_NAME), "some text", CREATE);
        server = SimpleFileServer.createFileServer(WILDCARD_ADDR, root, SimpleFileServer.OutputLevel.NONE);
        server.start();
    }

    public static ArrayList<ExchangeValues> requestResponsePairs() {
        return new ArrayList<>(List.of(
            // request method, requested resource, response code, content-type
            new ExchangeValues("GET",  FILE_NAME, 200, "text/plain"),
            new ExchangeValues("HEAD", FILE_NAME, 200, "text/plain"),
            new ExchangeValues("GET",  DIR_NAME,  200, "text/html; charset=UTF-8"),
            new ExchangeValues("HEAD", DIR_NAME,  200, "text/html; charset=UTF-8")));
    }

    record ExchangeValues(String method, String resource, int respCode, String contentType) {}

    @Test
    public static void shuffleAndTest() throws Exception {
        var values = requestResponsePairs();
        for (var e : values) {
            executeExchange(e);
        }
        for (int i = 0; i < 5; i++) {
            Collections.shuffle(values);
            for (var e : values) {
                executeExchange(e);
            }
        }
    }

    public static void executeExchange(ExchangeValues e) throws Exception {
        var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
        var request = HttpRequest.newBuilder(uri(server, e.resource()))
                .method(e.method(), HttpRequest.BodyPublishers.noBody())
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(response.statusCode(), e.respCode());
        assertEquals(response.headers().firstValue("content-type").get(), e.contentType());
    }

    @AfterTest
    public static void teardown() {
        server.stop(0);
    }

    static URI uri(HttpServer server, String path) {
        return URI.create("http://localhost:%s/%s".formatted(server.getAddress().getPort(), path));
    }
}
