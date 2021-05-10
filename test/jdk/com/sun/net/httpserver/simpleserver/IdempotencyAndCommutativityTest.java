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
import java.util.*;
import java.util.function.Function;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * @summary Test idempotence and commutativity property of send requests with an exhaustive set of binary sequences
 *          and a non exhaustive, order randomized ternary sequences
 * @run testng/othervm IdempotencyAndCommutativityTest
 */
public class IdempotencyAndCommutativityTest {

    static final InetSocketAddress WILDCARD_ADDR = new InetSocketAddress(0);
    static final Path CWD = Path.of(".").toAbsolutePath();
    static final String FILE_NAME = "testFile.txt";
    static final String DIR_NAME = "";
    static final String MISSING_NAME = "doesNotExist";
    static HttpServer server;
    static HttpClient client;
    static Path root;
    static Path file;

    static final boolean ENABLE_LOGGING = false;
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
        server = SimpleFileServer.createFileServer(WILDCARD_ADDR, root, SimpleFileServer.OutputLevel.INFO);
        server.start();
        client = HttpClient.newBuilder().proxy(NO_PROXY).build();
    }

    record ExchangeValues(String method, String resource, int respCode, String contentType) {}

    @DataProvider(name= "allBinarySequences")
    public Object[][]  allBinarySequencesDataProvider() {
        List<ExchangeValues> sampleSpace = Stream.of("GET", "HEAD", "UNKNOWN")
                .flatMap(request ->
                        Stream.of(FILE_NAME, DIR_NAME, MISSING_NAME)
                                .map(resource ->
                                        createExchangeValuesFor(request, resource)
                                )
                ).collect(Collectors.toList());
        List<List<ExchangeValues>> allBinarySequences = sampleSpace.stream().map(firstEvent -> sampleSpace.stream()
                                                .map(secondEvent ->
                                                        Arrays.asList(firstEvent, secondEvent)
                                                ).collect(Collectors.toList())
        ).flatMap(List::stream).collect(Collectors.toList());
        Object[][] data = new Object[allBinarySequences.size()][];
        for (int i = 0; i < allBinarySequences.size(); i++) {
            data[i] = new Object[]{ new ArrayList<>(allBinarySequences.get(i)) };
        }
        return data;
    }

    public ExchangeValues createExchangeValuesFor(String method, String resource) {
        String contentType;
        int statusCode;
        switch (method) {
            case "GET":
            case "HEAD":
                statusCode = resource == MISSING_NAME ? 404 : 200;
                break;
            case "UNKNOWN":
                statusCode = 501;
                break;
            default: throw new IllegalArgumentException();
        }
        switch (method) {
            case "GET":
            case "HEAD":
                switch (resource) {
                    case MISSING_NAME:
                    case DIR_NAME:
                        contentType = "text/html; charset=UTF-8";
                        break;
                    case FILE_NAME:
                        contentType = "text/plain";
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
                break;
            case "UNKNOWN":
                contentType = null;
                break;
            default: throw new IllegalArgumentException();
        }
        return new ExchangeValues(method, resource, statusCode, contentType);
    }

    @DataProvider(name = "someTernarySequences")
    public Object[][] someTernarySequencesDataProvider() {
        return new Object[][] {
            new Object[] { new ArrayList<>(List.of(
                    new ExchangeValues("GET", FILE_NAME, 200, "text/plain"),
                    new ExchangeValues("HEAD", MISSING_NAME, 404, "text/html; charset=UTF-8"),
                    new ExchangeValues("UNKNOWN", FILE_NAME, 501, null)
            )) },
            new Object[] { new ArrayList<>(List.of(
                    new ExchangeValues("GET", FILE_NAME, 200, "text/plain"),
                    new ExchangeValues("HEAD", MISSING_NAME, 404, "text/html; charset=UTF-8"),
                    new ExchangeValues("UNKNOWN", DIR_NAME, 501, null)
            )) },
            new Object[] { new ArrayList<>(List.of(
                    new ExchangeValues("GET", FILE_NAME, 200, "text/plain"),
                    new ExchangeValues("HEAD", DIR_NAME, 200, "text/html; charset=UTF-8"),
                    new ExchangeValues("UNKNOWN", FILE_NAME, 501, null)
            )) },
            new Object[] { new ArrayList<>(List.of(
                    new ExchangeValues("GET", FILE_NAME, 200, "text/plain"),
                    new ExchangeValues("HEAD", DIR_NAME, 200, "text/html; charset=UTF-8"),
                    new ExchangeValues("UNKNOWN", DIR_NAME, 501, null)
            )) },
            new Object[] { new ArrayList<>(List.of(
                    new ExchangeValues("GET", DIR_NAME, 200, "text/html; charset=UTF-8"),
                    new ExchangeValues("HEAD", FILE_NAME, 200, "text/plain"),
                    new ExchangeValues("UNKNOWN", FILE_NAME, 501, null)
            )) },
            new Object[] { new ArrayList<>(List.of(
                    new ExchangeValues("GET", DIR_NAME, 200, "text/html; charset=UTF-8"),
                    new ExchangeValues("HEAD", FILE_NAME, 200, "text/plain"),
                    new ExchangeValues("UNKNOWN", DIR_NAME, 501, null)
            )) },
            new Object[] { new ArrayList<>(List.of(
                    new ExchangeValues("GET", DIR_NAME, 200, "text/html; charset=UTF-8"),
                    new ExchangeValues("HEAD", DIR_NAME, 200, "text/html; charset=UTF-8"),
                    new ExchangeValues("UNKNOWN", FILE_NAME, 501, null)
            )) },
            new Object[] { new ArrayList<>(List.of(
                new ExchangeValues("GET", DIR_NAME, 200, "text/html; charset=UTF-8"),
                new ExchangeValues("HEAD", DIR_NAME, 200, "text/html; charset=UTF-8"),
                new ExchangeValues("UNKNOWN", DIR_NAME, 501, null)
            )) }
        };
    }

    @Test(dataProvider = "allBinarySequences")
    public void exhaustiveTestWithBinarySequences(List<ExchangeValues> sequence) throws Exception {
         if (sequence.size() != 2) {
             throw new IllegalArgumentException();
         }
         for (var value : sequence) {
             executeExchange(value);
         }
     }

    @Test(dataProvider = "someTernarySequences")
     public void shuffleAndTest(List<ExchangeValues> sequence) throws Exception {
         if (sequence.size() != 3) {
             throw new IllegalArgumentException();
         }
         System.out.println(sequence);
         for (var value : sequence) {
             executeExchange(value);
         }
         for (int i = 0; i < 3; i++) {
             Collections.shuffle(sequence);
             System.out.println(sequence);
             for (var value : sequence) {
                 executeExchange(value);
             }
         }
     }

    public static void executeExchange(ExchangeValues e) throws Exception {
        var uri = URI.create("http://localhost:%s/%s".formatted(server.getAddress().getPort(), e.resource()));
        var request = HttpRequest.newBuilder(uri)
                                            .method(e.method(), HttpRequest.BodyPublishers.noBody())
                                            .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(response.statusCode(), e.respCode());
        if (e.contentType != null) {
            assertEquals(response.headers().firstValue("content-type").get(), e.contentType());
        }
        else {
            assertTrue(response.headers().firstValue("content-type").isEmpty());
        }
    }

    @AfterTest
    public static void teardown() {
        server.stop(0);
    }

}
