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
import com.sun.net.httpserver.HttpServer;

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
import java.util.stream.Collectors;
import com.sun.net.httpserver.SimpleFileServer;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.file.StandardOpenOption.CREATE;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.*;

/*
 * @test
 * @summary Randomly testing commutativity and idempotence of send request operation for a randomized set of
 *           request sequences for a given sequence length and set size. A non random run will execute an exhaustive
 *           search for sequences of length one (testing the test).
 * @run testng/othervm IdempotencyAndCommutativityPropertyTest
 */
public class IdempotencyAndCommutativityPropertyTest {

    static final int SEQUENCE_LENGTH = 5;
    static final int INPUT_SET_SIZE = 100;
    static final boolean RANDOM = false;

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final InetSocketAddress WILDCARD_ADDR = new InetSocketAddress(0);

    static final boolean ENABLE_LOGGING = true;

    private static final Path ACTUAL_ROOT = createSimpleFileTree(
            IdempotencyAndCommutativityPropertyTest.class,
                                                "actual-root"
    );
    private static final Path EXPECTED_ROOT = createSimpleFileTree(
            IdempotencyAndCommutativityPropertyTest.class,
                                                    "expected-root"
    );
    private static final HttpClient CLIENT = createClient();
    private static final HttpServer SERVER_UNDER_TEST = createNoOutputWildcardBoundServer(ACTUAL_ROOT);

    static final String FILE_NAME = "aFile.txt";
    static final String FILE_CONTENTS = "Quoted file content";
    private static final String DIRECTORY = "";
    private static final String NONEXISTING = "nonexisting";

    private static final List<String> FILES = Collections.unmodifiableList(
            Arrays.asList(DIRECTORY, FILE_NAME, NONEXISTING)
    );

    private enum REQUEST {
        //GET, HEAD, UNKNOWN;
        GET, HEAD;
        //UNKNOWN;

        static List<String> asList() {
            return Arrays.asList(REQUEST.values()).stream().map(REQUEST::name).collect(Collectors.toList());
        }
    }

    private static final Map<Map.Entry<String,String>,Integer> EXPECTED_STATUS_CODES = initStatusCodes();/*
    private static Map<Map.Entry<String,String>,Integer> expectedHeaders = initHeaders();
    private static Map<Map.Entry<String,String>,Integer> expectedBodies = initBodies();
*/

    private static Map<Map.Entry<String,String>,Integer> initStatusCodes() {
        return Collections.unmodifiableMap(
                Map.ofEntries(
                    Map.entry(
                          Map.entry("GET",DIRECTORY),
                          200
                    ),
                    Map.entry(
                            Map.entry("GET",FILE_NAME),
                            200
                    ),
                    Map.entry(
                            Map.entry("GET",NONEXISTING),
                            404
                    ),
                    Map.entry(
                            Map.entry("HEAD",DIRECTORY),
                            200
                    ),
                    Map.entry(
                            Map.entry("HEAD",FILE_NAME),
                            200
                    ),
                    Map.entry(
                            Map.entry("HEAD",NONEXISTING),
                            404
                    ),
                    Map.entry(
                            Map.entry("UNKNOWN",DIRECTORY),
                            405
                    ),
                    Map.entry(
                            Map.entry("UNKNOWN",FILE_NAME),
                            405
                    ),
                    Map.entry(
                            Map.entry("UNKNOWN",NONEXISTING),
                            405
                    )
                )
        );
    }

    @BeforeTest
    void before() {
        if (ENABLE_LOGGING) {
            Logger logger = Logger.getLogger("com.sun.net.httpserver");
            ConsoleHandler ch = new ConsoleHandler();
            logger.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            logger.addHandler(ch);
        }
        SERVER_UNDER_TEST.start();
    }

    @DataProvider(name = "dataProvider")
    public Object[][] data() throws Exception {
        final List<String> requests = REQUEST.asList();
        final Random random = new Random();
        final int TEST_CASE_COUNT = RANDOM ? INPUT_SET_SIZE : requests.size() * FILES.size();
        final int ACTUAL_SEQUENCE_LENGTH = RANDOM ? SEQUENCE_LENGTH : 1;

        final Object[][] data = new Object[TEST_CASE_COUNT][];
        for (int caseIndex = 0; caseIndex < TEST_CASE_COUNT; caseIndex++) {
            List<Map.Entry<String,String>> inputs = new ArrayList<>();
            for (int sequenceIndex = 0; sequenceIndex < ACTUAL_SEQUENCE_LENGTH; sequenceIndex++) {
                int requestIndex,fileIndex;
                if (RANDOM) {
                    requestIndex = random.ints(0, requests.size()).findFirst().getAsInt();
                    fileIndex = random.ints(0, FILES.size()).findFirst().getAsInt();
                }
                else {
                    requestIndex = caseIndex / FILES.size();
                    fileIndex = caseIndex % FILES.size();
                }
                inputs.add(Map.entry(requests.get(requestIndex), FILES.get(fileIndex)));
            }
            data[caseIndex] = new Object[] {inputs};
        }
        return data;
    }

    @Test (dataProvider = "dataProvider")
    public void test2(List<Map.Entry<String, String>> sequence) throws Exception {
        for (Map.Entry<String, String> input : sequence) {
            final HttpRequest request = createRequest(
                    SERVER_UNDER_TEST,
                    input.getValue(),
                    input.getKey(),
                    HttpRequest.BodyPublishers.noBody()
            );
            final HttpResponse<String> actualResponse = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(actualResponse.statusCode(), (long)EXPECTED_STATUS_CODES.get(input));
            //  assertEquals(actualResponse.headers(), EXPECTED_RESPONSE.get(input).headers());
            //assertEquals(actualResponse.body(), EXPECTED_RESPONSE.get(input).body());
            //todo assertEquals(ACTUAL_ROOT, EXPECTED_ROOT)
        }
    }

    static HttpServer createNoOutputWildcardBoundServer(Path root) {
        return SimpleFileServer.createFileServer(WILDCARD_ADDR, root, SimpleFileServer.OutputLevel.NONE);
    }

    static HttpClient createClient() {
        return HttpClient.newBuilder().proxy(NO_PROXY).build();
    }
    static Path createSimpleFileTree(Class caller,String id)  {
        try {
            Path directory = Files.createDirectory(CWD.resolve("data-%s%s".formatted(caller.getSimpleName(),id)));
            Files.writeString(directory.resolve(FILE_NAME), FILE_CONTENTS, CREATE);
            return directory;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static HttpRequest createRequest(HttpServer server, String path, String method,
                                     HttpRequest.BodyPublisher bodyPublisher) {
        return HttpRequest.newBuilder(
                URI.create("http://localhost:%s/%s".formatted(server.getAddress().getPort(), path))
        )
        .method(method,bodyPublisher)
        .build();
    }

}
