/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary  Verify that some special headers - such as User-Agent
 *           can be specified by the caller.
 * @bug 8203771 8218546 8297200
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.test.lib.net.SimpleSSLContext
 * @requires (vm.compMode != "Xcomp")
 * @run junit/othervm/timeout=480
 *       -Djdk.httpclient.HttpClient.log=requests,headers,errors
 *       SpecialHeadersTest
 * @run junit/othervm/timeout=480 -Djdk.httpclient.allowRestrictedHeaders=Host
 *       -Djdk.httpclient.HttpClient.log=requests,headers,errors
 *       SpecialHeadersTest
 */

import jdk.internal.net.http.common.OperationTrackers.Tracker;
import jdk.test.lib.net.SimpleSSLContext;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.err;
import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.nio.charset.StandardCharsets.US_ASCII;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class SpecialHeadersTest implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static HttpTestServer httpTestServer;         // HTTP/1.1    [ 4 servers ]
    private static HttpTestServer httpsTestServer;        // HTTPS/1.1
    private static HttpTestServer http2TestServer;        // HTTP/2 ( h2c )
    private static HttpTestServer https2TestServer;       // HTTP/2 ( h2  )
    private static HttpTestServer http3TestServer;        // HTTP/3
    private static String httpURI;
    private static String httpsURI;
    private static String http2URI;
    private static String https2URI;
    private static String https3URI;

    static final String[][] headerNamesAndValues = new String[][]{
            {"User-Agent: <DEFAULT>"},
            {"User-Agent: camel-cased"},
            {"user-agent: all-lower-case"},
            {"user-Agent: mixed"},
            // headers which were restricted before and are now allowable
            {"referer: lower"},
            {"Referer: normal"},
            {"REFERER: upper"},
            {"origin: lower"},
            {"Origin: normal"},
            {"ORIGIN: upper"},
    };

    // Needs net.property enabled for this part of test
    static final String[][] headerNamesAndValues1 = new String[][]{
            {"Host: <DEFAULT>"},
            {"Host: camel-cased"},
            {"host: all-lower-case"},
            {"hoSt: mixed"}
    };

    public static Object[][] variants() {
        String prop = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
        boolean hostTest = prop != null && prop.equalsIgnoreCase("host");
        final String[][] testInput = hostTest ? headerNamesAndValues1 : headerNamesAndValues;

        List<Object[]> list = new ArrayList<>();

        for (boolean sameClient : new boolean[] { false, true }) {
            Arrays.asList(testInput).stream()
                    .map(e -> new Object[] {httpURI, e[0], sameClient})
                    .forEach(list::add);
            Arrays.asList(testInput).stream()
                    .map(e -> new Object[] {httpsURI, e[0], sameClient})
                    .forEach(list::add);
            Arrays.asList(testInput).stream()
                    .map(e -> new Object[] {http2URI, e[0], sameClient})
                    .forEach(list::add);
            Arrays.asList(testInput).stream()
                    .map(e -> new Object[] {https2URI, e[0], sameClient})
                    .forEach(list::add);
            Arrays.asList(testInput).stream()
                    .map(e -> new Object[] {https3URI, e[0], sameClient})
                    .forEach(list::add);
        }
        return list.stream().toArray(Object[][]::new);
    }

    static final int ITERATION_COUNT = 3; // checks upgrade and re-use
    // a shared executor helps reduce the amount of threads created by the test
    static final TestExecutor executor = new TestExecutor(Executors.newCachedThreadPool());
    static final ConcurrentMap<String, Throwable> FAILURES = new ConcurrentHashMap<>();
    static volatile boolean tasksFailed;
    static final AtomicLong serverCount = new AtomicLong();
    static final AtomicLong clientCount = new AtomicLong();
    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    private static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
    private static volatile HttpClient sharedClient;

    static class TestExecutor implements Executor {
        final AtomicLong tasks = new AtomicLong();
        Executor executor;
        TestExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
        public void execute(Runnable command) {
            long id = tasks.incrementAndGet();
            executor.execute(() -> {
                try {
                    command.run();
                } catch (Throwable t) {
                    tasksFailed = true;
                    out.printf(now() + "Task %s failed: %s%n", id, t);
                    err.printf(now() + "Task %s failed: %s%n", id, t);
                    FAILURES.putIfAbsent("Task " + id, t);
                    throw t;
                }
            });
        }

        public void shutdown() throws InterruptedException {
            if (executor instanceof ExecutorService service) {
                service.shutdown();
                service.awaitTermination(1000, TimeUnit.MILLISECONDS);
            }
        }


    }

    private static boolean stopAfterFirstFailure() {
        return Boolean.getBoolean("jdk.internal.httpclient.debug");
    }

    static final class TestStopper implements TestWatcher, BeforeEachCallback {
        final AtomicReference<String> failed = new AtomicReference<>();
        TestStopper() { }
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            if (stopAfterFirstFailure()) {
                String msg = "Aborting due to: " + cause;
                failed.compareAndSet(null, msg);
                FAILURES.putIfAbsent(context.getDisplayName(), cause);
                System.out.printf("%nTEST FAILED: %s%s%n\tAborting due to %s%n%n",
                        now(), context.getDisplayName(), cause);
                System.err.printf("%nTEST FAILED: %s%s%n\tAborting due to %s%n%n",
                        now(), context.getDisplayName(), cause);
            }
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            String msg = failed.get();
            Assumptions.assumeTrue(msg == null, msg);
        }
    }

    @RegisterExtension
    static final TestStopper stopper = new TestStopper();

    @AfterAll
    static void printFailedTests() {
        out.println("\n=========================");
        try {
            out.printf("%n%sCreated %d servers and %d clients%n",
                    now(), serverCount.get(), clientCount.get());
            if (FAILURES.isEmpty()) return;
            out.println("Failed tests: ");
            FAILURES.entrySet().forEach((e) -> {
                out.printf("\t%s: %s%n", e.getKey(), e.getValue());
                e.getValue().printStackTrace(out);
            });
            if (tasksFailed) {
                out.println("WARNING: Some tasks failed");
            }
        } finally {
            out.println("\n=========================\n");
        }
    }

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        return newClientBuilderForH3()
                .proxy(NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build();
    }

    static volatile String lastMethod;
    HttpClient newHttpClient(String method, boolean share) {
        if (!share) return TRACKER.track(makeNewClient());
        HttpClient shared = sharedClient;
        String last = lastMethod;
        if (shared != null && Objects.equals(last, method)) return shared;
        synchronized (this) {
            shared = sharedClient;
            last = lastMethod;
            if (!Objects.equals(last, method)) {
                // reset sharedClient to avoid side effects
                // between methods. This is needed to keep the test
                // expectation that the first HTTP/2 clear request
                // will be an upgrade
                if (shared != null) {
                    TRACKER.track(shared);
                    shared = sharedClient = null;
                }
            }
            if (shared == null) {
                shared = sharedClient = makeNewClient();
                last = lastMethod = method;
            }
            return shared;
        }
    }


    static String userAgent() {
        return "Java-http-client/" + System.getProperty("java.version");
    }

    static final Map<String, Function<URI,String>> DEFAULTS = Map.of(
        "USER-AGENT", u -> userAgent(), "HOST", u -> u.getRawAuthority());

    static void throwIfNotNull(Throwable throwable) throws Exception {
        if (throwable instanceof Exception ex) throw ex;
        if (throwable instanceof Error e) throw e;
    }

    @ParameterizedTest
    @MethodSource("variants")
    void test(String uriString,
              String headerNameAndValue,
              boolean sameClient)
        throws Exception
    {
        out.println("\n--- Starting test " + now());

        int index = headerNameAndValue.indexOf(":");
        String name = headerNameAndValue.substring(0, index);
        String v = headerNameAndValue.substring(index+1).trim();
        String key = name.toUpperCase(Locale.ROOT);
        boolean useDefault = "<DEFAULT>".equals(v);

        URI uri = URI.create(uriString+"?name="+key);
        String value =  useDefault ? DEFAULTS.get(key).apply(uri) : v;

        HttpClient client = null;
        Tracker tracker = null;
        Throwable thrown = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            try {
                if (!sameClient || client == null) {
                    client = newHttpClient("test", sameClient);
                    tracker = TRACKER.getTracker(client);
                }

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri);
                if (uriString.contains("/http3")) {
                    requestBuilder.version(HTTP_3);
                    requestBuilder.setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
                }
                if (!useDefault) {
                    requestBuilder.header(name, value);
                }
                HttpRequest request = requestBuilder.build();
                HttpResponse<String> resp = client.send(request, BodyHandlers.ofString());

                out.println("Got response: " + resp);
                out.println("Got body: " + resp.body());
                assertEquals(200, resp.statusCode(),
                        "Expected 200, got:" + resp.statusCode());

                boolean isInitialRequest = i == 0;
                boolean isSecure = uri.getScheme().equalsIgnoreCase("https");
                boolean isHTTP1 = resp.version() == HTTP_1_1;
                boolean isNotH2CUpgrade = isSecure || (sameClient == true && !isInitialRequest);
                boolean isDefaultHostHeader = name.equalsIgnoreCase("host") && useDefault;

                // By default, HTTP/2 sets the `:authority:` pseudo-header, instead
                // of the `Host` header. Therefore, there should be no "X-Host"
                // header in the response, except the response to the h2c Upgrade
                // request which will have been sent through HTTP/1.1.

                if (isDefaultHostHeader && !isHTTP1 && isNotH2CUpgrade) {
                    assertTrue(resp.headers().firstValue("X-" + key).isEmpty());
                    assertTrue(resp.headers().allValues("X-" + key).isEmpty());
                    out.println("No X-" + key + " header received, as expected");
                } else {
                    String receivedHeaderString = value == null ? null
                            : resp.headers().firstValue("X-" + key).get();
                    out.println("Got X-" + key + ": " + resp.headers().allValues("X-" + key));
                    if (value != null) {
                        assertEquals(value, receivedHeaderString);
                        assertEquals(List.of(value), resp.headers().allValues("X-" + key));
                    } else {
                        assertEquals(0, resp.headers().allValues("X-" + key).size());
                    }
                }
            } catch (Throwable x) {
                thrown = x;
            } finally {
                if (!sameClient) {
                    client = null;
                    System.gc();
                    var error = TRACKER.check(tracker, 500);
                    if (error != null) {
                        if (thrown != null) error.addSuppressed(thrown);
                        throw error;
                    }
                }
            }
            throwIfNotNull(thrown);
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    void testHomeMadeIllegalHeader(String uriString,
                                   String headerNameAndValue,
                                   boolean sameClient)
        throws Exception
    {
        out.println("\n--- Starting testHomeMadeIllegalHeader " + now());
        final URI uri = URI.create(uriString);

        HttpClient client = newHttpClient("testHomeMadeIllegalHeader", sameClient);
        Tracker tracker = TRACKER.getTracker(client);
        Throwable thrown = null;
        try {
            // Test a request which contains an illegal header created
            HttpRequest req = new HttpRequest() {
                @Override
                public Optional<BodyPublisher> bodyPublisher() {
                    return Optional.of(BodyPublishers.noBody());
                }

                @Override
                public String method() {
                    return "GET";
                }

                @Override
                public Optional<Duration> timeout() {
                    return Optional.empty();
                }

                @Override
                public boolean expectContinue() {
                    return false;
                }

                @Override
                public URI uri() {
                    return uri;
                }

                @Override
                public Optional<HttpClient.Version> version() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    Map<String, List<String>> map = Map.of("upgrade", List.of("http://foo.com"));
                    return HttpHeaders.of(map, (x, y) -> true);
                }
            };

            try {
                HttpResponse<String> response = client.send(req, BodyHandlers.ofString());
                Assertions.fail("Unexpected reply: " + response);
            } catch (IllegalArgumentException ee) {
                out.println("Got IAE as expected");
            }
        } catch (Throwable x) {
            thrown = x;
        } finally {
            if (!sameClient) {
                client = null;
                System.gc();
                var error = TRACKER.check(tracker, 500);
                if (error != null) {
                    if (thrown != null) error.addSuppressed(thrown);
                    throw error;
                }
            }
        }
        throwIfNotNull(thrown);
    }



    @ParameterizedTest
    @MethodSource("variants")
    void testAsync(String uriString, String headerNameAndValue, boolean sameClient)
            throws Exception
    {
        out.println("\n--- Starting testAsync " + now());
        int index = headerNameAndValue.indexOf(":");
        String name = headerNameAndValue.substring(0, index);
        String v = headerNameAndValue.substring(index+1).trim();
        String key = name.toUpperCase(Locale.ROOT);
        boolean useDefault = "<DEFAULT>".equals(v);

        URI uri = URI.create(uriString+"?name="+key);
        String value =  useDefault ? DEFAULTS.get(key).apply(uri) : v;

        HttpClient client = null;
        Tracker tracker = null;
        Throwable thrown = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            try {
                if (!sameClient || client == null) {
                    client = newHttpClient("testAsync", sameClient);
                    tracker = TRACKER.getTracker(client);
                }

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri);
                if (uriString.contains("/http3")) {
                    requestBuilder.version(HTTP_3);
                    requestBuilder.setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
                }
                if (!useDefault) {
                    requestBuilder.header(name, value);
                }
                HttpRequest request = requestBuilder.build();

                boolean isInitialRequest = i == 0;
                boolean isSecure = uri.getScheme().equalsIgnoreCase("https");
                boolean isNotH2CUpgrade = isSecure || (sameClient == true && !isInitialRequest);
                boolean isDefaultHostHeader = name.equalsIgnoreCase("host") && useDefault;

                client.sendAsync(request, BodyHandlers.ofString())
                        .thenApply(response -> {
                            out.println("Got response: " + response);
                            out.println("Got body: " + response.body());
                            assertEquals(200, response.statusCode());
                            return response;
                        })
                        .thenAccept(resp -> {
                            // By default, HTTP/2 sets the `:authority:` pseudo-header, instead
                            // of the `Host` header. Therefore, there should be no "X-Host"
                            // header in the response, except the response to the h2c Upgrade
                            // request which will have been sent through HTTP/1.1.

                            if (isDefaultHostHeader && resp.version() != HTTP_1_1 && isNotH2CUpgrade) {
                                assertTrue(resp.headers().firstValue("X-" + key).isEmpty());
                                assertTrue(resp.headers().allValues("X-" + key).isEmpty());
                                out.println("No X-" + key + " header received, as expected");
                            } else {
                                String receivedHeaderString = value == null ? null
                                        : resp.headers().firstValue("X-" + key).orElse(null);
                                out.println("Got X-" + key + ": " + resp.headers().allValues("X-" + key));
                                if (value != null) {
                                    assertEquals(value, receivedHeaderString);
                                    assertEquals(List.of(value), resp.headers().allValues("X-" + key));
                                } else {
                                    assertEquals(1, resp.headers().allValues("X-" + key).size());
                                }
                            }
                        })
                        .join();
            } catch (Throwable x) {
                thrown = x;
            } finally {
                if (!sameClient) {
                    client = null;
                    System.gc();
                    var error = TRACKER.check(tracker, 500);
                    if (error != null) {
                        if (thrown != null) error.addSuppressed(thrown);
                        throw error;
                    }
                }
            }
            throwIfNotNull(thrown);
        }
    }

    static String serverAuthority(HttpTestServer server) {
        return InetAddress.getLoopbackAddress().getHostName() + ":"
                + server.getAddress().getPort();
    }

    @BeforeAll
    public static void setup() throws Exception {
        out.println("--- Starting setup " + now());

        HttpTestHandler handler = new HttpUriStringHandler();
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(handler, "/http1");
        httpURI = "http://" + serverAuthority(httpTestServer) + "/http1";

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(handler, "/https1");
        httpsURI = "https://" + serverAuthority(httpsTestServer) + "/https1";

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(handler, "/http2");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2";

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(handler, "/https2");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2";

        http3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        http3TestServer.addHandler(handler, "/http3");
        https3URI = "https://" + http3TestServer.serverAuthority() + "/http3";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();
    }

    @AfterAll
    public static void teardown() throws Exception {
        out.println("\n--- Teardown " + now());
        HttpClient shared = sharedClient;
        String sharedClientName =
                shared == null ? null : shared.toString();
        if (shared != null) TRACKER.track(shared);
        shared = sharedClient = null;
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(2500);
        out.println("--- Stopping servers " + now());
        try {
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
            http3TestServer.start();
            executor.shutdown();
        } finally {
            if (fail != null) {
                if (sharedClientName != null) {
                    err.println("Shared client name is: " + sharedClientName);
                }
                throw fail;
            }
        }
    }

    /** A handler that returns, as its body, the exact received request URI.
     *  The header whose name is in the URI query and is set in the request is
     *  returned in the response with its name prefixed by X-
     */
    static class HttpUriStringHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            URI uri = t.getRequestURI();
            String uriString = uri.toString();
            out.println("HttpUriStringHandler received, uri: " + uriString);
            String query = uri.getQuery();
            String headerName = query.substring(query.indexOf("=")+1).trim();
            out.println("HttpUriStringHandler received, headerName: " + headerName
                    + "\n\theaders: " + t.getRequestHeaders());
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                is.readAllBytes();
                byte[] bytes = uriString.getBytes(US_ASCII);
                t.getRequestHeaders().keySet().stream()
                        .filter(headerName::equalsIgnoreCase)
                        .forEach(h -> {
                            for (String v : t.getRequestHeaders().get(headerName)) {
                                t.getResponseHeaders().addHeader("X-"+h, v);
                            }
                        });
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }
}
