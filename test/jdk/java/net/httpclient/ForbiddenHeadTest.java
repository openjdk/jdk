/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary checks that receiving 403 for a HEAD request after
 *          401/407 doesn't cause any unexpected behavior.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build DigestEchoServer ForbiddenHeadTest jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run junit/othervm
 *       -Djdk.http.auth.tunneling.disabledSchemes
 *       -Djdk.httpclient.HttpClient.log=headers,requests
 *       -Djdk.internal.httpclient.debug=true
 *       ForbiddenHeadTest
 */

import jdk.test.lib.net.SimpleSSLContext;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.err;
import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.jupiter.api.AfterAll;

import static jdk.httpclient.test.lib.common.HttpServerAdapters.createClientBuilderForH3;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ForbiddenHeadTest implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static HttpTestServer httpTestServer;        // HTTP/1.1
    private static HttpTestServer httpsTestServer;       // HTTPS/1.1
    private static HttpTestServer http2TestServer;       // HTTP/2 ( h2c )
    private static HttpTestServer https2TestServer;      // HTTP/2 ( h2  )
    private static HttpTestServer http3TestServer;       // HTTP/3 ( h3  )
    private static DigestEchoServer.TunnelingProxy proxy;
    private static DigestEchoServer.TunnelingProxy authproxy;
    private static String httpURI;
    private static String httpsURI;
    private static String http2URI;
    private static String https2URI;
    private static String https3URI;
    private static HttpClient authClient;
    private static HttpClient noAuthClient;

    private static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
    static final long SLEEP_AFTER_TEST = 0; // milliseconds
    static final int ITERATIONS = 3;
    static final Executor executor = new TestExecutor(Executors.newCachedThreadPool());
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
            // Exceptions should already have been added to FAILURES
            // var failed = context.getFailedTests().getAllResults().stream()
            //        .collect(Collectors.toMap(r -> name(r), ITestResult::getThrowable));
            // FAILURES.putAll(failed);

            out.printf("%n%sCreated %d servers and %d clients%n",
                    now(), serverCount.get(), clientCount.get());
            if (FAILURES.isEmpty()) return;
            out.println("Failed tests: ");
            FAILURES.entrySet().forEach((e) -> {
                out.printf("\t%s: %s%n", e.getKey(), e.getValue());
                e.getValue().printStackTrace(out);
                e.getValue().printStackTrace();
            });
            if (tasksFailed) {
                out.println("WARNING: Some tasks failed");
            }
        } finally {
            out.println("\n=========================\n");
        }
    }

    static final int UNAUTHORIZED = 401;
    static final int PROXY_UNAUTHORIZED = 407;
    static final int FORBIDDEN = 403;
    static final int HTTP_OK = 200;
    static final String MESSAGE = "Unauthorized";


    public static Object[][] allcases() {
        List<Object[]> result = new ArrayList<>();
        for (boolean useAuth : List.of(true, false)) {
            for (boolean async : List.of(true, false)) {
                for (int code : List.of(UNAUTHORIZED, PROXY_UNAUTHORIZED)) {
                    var srv = code == PROXY_UNAUTHORIZED ? "/proxy" : "/server";
                    for (var auth : List.of("/auth", "/noauth")) {
                        var pcode = code;
                        if (auth.equals("/noauth")) {
                            if (useAuth) continue;
                            pcode = FORBIDDEN;
                        }
                        for (var uri : List.of(httpURI, httpsURI, http2URI, https2URI)) {
                            result.add(new Object[]{uri + srv + auth, pcode, async, useAuth});
                        }
                        if (code == PROXY_UNAUTHORIZED) continue; // no HTTP/3 with proxy
                        result.add(new Object[] {https3URI + srv + auth, pcode, async, useAuth});
                    }
                }
            }
        }
        return result.toArray(new Object[0][0]);
    }

    static final Authenticator authenticator = new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication("arthur",new char[] {'d', 'e', 'n', 't'});
        }
    };

    static final AtomicLong sleepCount = new AtomicLong();

    @ParameterizedTest
    @MethodSource("allcases")
    void test(String uriString, int code, boolean async, boolean useAuth) throws Throwable {
        HttpClient client = useAuth ? authClient : noAuthClient;
        var name = String.format("test(%s, %d, %s, %s)", uriString, code, async ? "async" : "sync",
                client.authenticator().isPresent() ? "authClient" : "noAuthClient");
        out.printf("%n---- starting %s ----%n", name);
        assert client.authenticator().isPresent() == useAuth;
        uriString = uriString + "/ForbiddenTest";
        for (int i=0; i<ITERATIONS; i++) {
            if (ITERATIONS > 1) out.printf("---- ITERATION %d%n",i);
            try {
                doTest(uriString, code, async, client);
                long count = sleepCount.incrementAndGet();
                System.err.println(now() + " Sleeping: " + count);
                Thread.sleep(SLEEP_AFTER_TEST);
                System.err.println(now() + " Waking up: " + count);
            } catch (Throwable x) {
                FAILURES.putIfAbsent(name, x);
                throw x;
            }
        }
    }

    static String authHeaderName(int code) {
        return switch (code) {
            case UNAUTHORIZED -> "WWW-Authenticate";
            case PROXY_UNAUTHORIZED -> "Proxy-Authenticate";
            default -> null;
        };
    }

    private void doTest(String uriString, int code, boolean async, HttpClient client) throws Throwable {
        URI uri = URI.create(uriString);

        HttpRequest.Builder requestBuilder = HttpRequest
                .newBuilder(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody());

        if (uriString.contains("/http3/")) {
            requestBuilder.version(HTTP_3).setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        }

        HttpRequest request = requestBuilder.build();
        out.println("Initial request: " + request.uri());

        String header = authHeaderName(code);
        // the request is expected to return 403 Forbidden if the client is authenticated,
        // or the server doesn't require authentication, 401 or 407 otherwise.
        boolean forbidden = client.authenticator().isPresent() || code == FORBIDDEN;

        HttpResponse<String> response = null;
        if (async) {
            response = client.send(request, BodyHandlers.ofString());
        } else {
           try {
               response = client.sendAsync(request, BodyHandlers.ofString()).get();
           } catch (ExecutionException ex) {
               throw ex.getCause();
           }
        }

        String prefix = uriString.contains("/proxy/") ? "Proxy-" : "WWW-";
        String expectedValue;
        if (forbidden) {
            // The message body is generated by the server, after authentication was
            // successful.
            expectedValue =  prefix + "FORBIDDEN";
        } else if (uriString.contains("/proxy/") && uri.getScheme().equalsIgnoreCase("https")) {
            // In that case the tunnelling proxy itself is expected to return 407,
            // and the message will have no body (since the CONNECT request fails).
            assert code == PROXY_UNAUTHORIZED;
            expectedValue = null;
        } else {
            // the message body is generated by our fake server pretending to be
            // a proxy.
            expectedValue = prefix + MESSAGE;
        }


        out.println("  Got response: " + response);
        assertEquals(forbidden? FORBIDDEN : code, response.statusCode());
        assertEquals(expectedValue == null ? null : "", response.body());
        assertEquals(Optional.ofNullable(expectedValue), response.headers().firstValue("X-value"));
        // when the CONNECT request fails, its body is discarded - but
        // the response header may still contain its content length.
        // don't check content length in that case.
        if (expectedValue != null) {
            String clen = String.valueOf(expectedValue.getBytes(UTF_8).length);
            assertEquals(Optional.of(clen), response.headers().firstValue("Content-Length"));
        }

    }

    // -- Infrastructure

    @BeforeAll
    public static void setup() throws Exception {
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new UnauthorizedHandler(), "/http1/");
        httpTestServer.addHandler(new UnauthorizedHandler(), "/http2/proxy/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1";
        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new UnauthorizedHandler(),"/https1/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1";

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new UnauthorizedHandler(), "/http2/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2";
        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new UnauthorizedHandler(), "/https2/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2";

        http3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        http3TestServer.addHandler(new UnauthorizedHandler(), "/http3/");
        https3URI = "https://" + http3TestServer.serverAuthority() + "/http3";

        proxy = DigestEchoServer.createHttpsProxyTunnel(DigestEchoServer.HttpAuthSchemeType.NONE);
        authproxy = DigestEchoServer.createHttpsProxyTunnel(DigestEchoServer.HttpAuthSchemeType.BASIC);

        authClient = TRACKER.track(createClientBuilderForH3()
                .proxy(TestProxySelector.of(proxy, authproxy, httpTestServer))
                .sslContext(sslContext)
                .executor(executor)
                .authenticator(authenticator)
                .build());
        clientCount.incrementAndGet();

        noAuthClient = TRACKER.track(createClientBuilderForH3()
                .proxy(TestProxySelector.of(proxy, authproxy, httpTestServer))
                .sslContext(sslContext)
                .executor(executor)
                .build());
        clientCount.incrementAndGet();

        httpTestServer.start();
        serverCount.incrementAndGet();
        httpsTestServer.start();
        serverCount.incrementAndGet();
        http2TestServer.start();
        serverCount.incrementAndGet();
        https2TestServer.start();
        serverCount.incrementAndGet();
        http3TestServer.start();
        serverCount.incrementAndGet();
    }

    @AfterAll
    public static void teardown() throws Exception {
        authClient = noAuthClient = null;
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(1500);
        try {
            proxy.stop();
            authproxy.stop();
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
            http3TestServer.stop();
        } finally {
            if (fail != null) throw fail;
        }
    }

    static class TestProxySelector extends ProxySelector {
        final DigestEchoServer.TunnelingProxy proxy;
        final DigestEchoServer.TunnelingProxy authproxy;
        final HttpTestServer plain;
        private TestProxySelector(DigestEchoServer.TunnelingProxy proxy,
                                  DigestEchoServer.TunnelingProxy authproxy,
                                  HttpTestServer plain) {
            this.proxy = proxy;
            this.authproxy = authproxy;
            this.plain = plain;
        }
        @Override
        public List<Proxy> select(URI uri) {
            String path = uri.getPath();
            out.println("Selecting proxy for: " + uri);
            if (path.contains("/proxy/")) {
                if (path.contains("/http1/")) {
                    // Simple proxying - in our test the server pretends
                    // to be the proxy.
                    System.out.print("PROXY is server for " + uri);
                    return List.of(new Proxy(Proxy.Type.HTTP,
                            new InetSocketAddress(uri.getHost(), uri.getPort())));
                } else if (path.contains("/http2/")) {
                    // HTTP/2 is downgraded to HTTP/1.1 if there is a proxy
                    System.out.print("PROXY is plain server for " + uri);
                    return List.of(new Proxy(Proxy.Type.HTTP, plain.getAddress()));
                } else {
                    // Both HTTPS or HTTPS/2 require tunnelling
                    var p = path.contains("/auth/") ? authproxy : proxy;
                    if (p == authproxy) {
                        out.println("PROXY is authenticating tunneling proxy for " + uri);
                    } else {
                        out.println("PROXY is plain tunneling proxy for " + uri);
                    }
                    return List.of(new Proxy(Proxy.Type.HTTP, p.getProxyAddress()));
                }
            }
            System.out.print("NO_PROXY for " + uri);
            return List.of(Proxy.NO_PROXY);
        }
        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            System.err.printf("Connect failed for: uri=\"%s\", sa=\"%s\", ioe=%s%n", uri, sa, ioe);
        }
        public static TestProxySelector of(DigestEchoServer.TunnelingProxy proxy,
                                           DigestEchoServer.TunnelingProxy authproxy,
                                           HttpTestServer plain) {
            return new TestProxySelector(proxy, authproxy, plain);
        }
    }

    static class UnauthorizedHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            readAllRequestData(t); // shouldn't be any
            String method = t.getRequestMethod();
            String path = t.getRequestURI().getPath();
            HttpTestRequestHeaders  reqh = t.getRequestHeaders();
            HttpTestResponseHeaders rsph = t.getResponseHeaders();

            String xValue;
            boolean noAuthRequired = path.contains("/noauth/");
            boolean authenticated  = path.contains("/server/") && reqh.containsKey("Authorization")
                    || path.contains("/proxy/") && reqh.containsKey("Proxy-Authorization")
                    || path.contains("/proxy/") && (path.contains("/https1/") || path.contains("/https2/"));
            String srv = path.contains("/proxy/") ? "proxy" : "server";
            String prefix = path.contains("/proxy/") ? "Proxy-" : "WWW-";
            int authcode = path.contains("/proxy/") ? PROXY_UNAUTHORIZED : UNAUTHORIZED;
            int code = (authenticated || noAuthRequired) ? FORBIDDEN : authcode;
            if (authenticated || noAuthRequired) {
                xValue = prefix + "FORBIDDEN";
            } else {
                xValue = prefix + MESSAGE;
                rsph.addHeader(prefix + "Authenticate", "Basic realm=\"earth\", charset=\"UTF-8\"");
            }

            t.getResponseHeaders().addHeader("X-value", xValue);
            t.getResponseHeaders().addHeader("Content-Length", String.valueOf(xValue.getBytes(UTF_8).length));
            t.sendResponseHeaders(code, 0);
        }
    }

    static void readAllRequestData(HttpTestExchange t) throws IOException {
        try (InputStream is = t.getRequestBody()) {
            is.readAllBytes();
        }
    }
}
