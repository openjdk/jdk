/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.Test;
import static java.time.Duration.*;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8209137 8326233
 * @summary HttpClient[.Builder] API and behaviour checks
 * @library /test/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 * @run testng HttpClientBuilderTest
 */

public class HttpClientBuilderTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    static final Class<UnsupportedOperationException> UOE = UnsupportedOperationException.class;

    @Test
    public void testDefaults() throws Exception {
        List<HttpClient> clients = List.of(HttpClient.newHttpClient(),
                                           HttpClient.newBuilder().build());

        for (HttpClient c : clients) {
            // Empty optionals and defaults
            try (var client = c) {
                assertFalse(client.authenticator().isPresent());
                assertFalse(client.cookieHandler().isPresent());
                assertFalse(client.connectTimeout().isPresent());
                assertFalse(client.executor().isPresent());
                assertFalse(client.proxy().isPresent());
                assertTrue(client.sslParameters() != null);
                assertTrue(client.followRedirects().equals(HttpClient.Redirect.NEVER));
                assertTrue(client.sslContext() == SSLContext.getDefault());
                assertTrue(client.version().equals(HttpClient.Version.HTTP_2));
            }
        }
    }

    @Test
    public void testNull() throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder();
        assertThrows(NPE, () -> builder.authenticator(null));
        assertThrows(NPE, () -> builder.cookieHandler(null));
        assertThrows(NPE, () -> builder.connectTimeout(null));
        assertThrows(NPE, () -> builder.executor(null));
        assertThrows(NPE, () -> builder.proxy(null));
        assertThrows(NPE, () -> builder.sslParameters(null));
        assertThrows(NPE, () -> builder.followRedirects(null));
        assertThrows(NPE, () -> builder.sslContext(null));
        assertThrows(NPE, () -> builder.version(null));
    }

    static class TestAuthenticator extends Authenticator { }

    static class Closer implements AutoCloseable {
        final HttpClient.Builder builder;
        HttpClient client;
        Closer(HttpClient.Builder builder) {
            this.builder = Objects.requireNonNull(builder);
        }
        HttpClient build() {
            if (client != null) client.close();
            return client = builder.build();
        }
        @Override
        public void close() {
            if (client != null) client.close();
        }
    }

    static Closer closeable(HttpClient.Builder builder) {
        return new Closer(builder);
    }

    @Test
    public void testAuthenticator() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        Authenticator a = new TestAuthenticator();
        builder.authenticator(a);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().authenticator().get() == a);
        }
        Authenticator b = new TestAuthenticator();
        builder.authenticator(b);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().authenticator().get() == b);
        }
        assertThrows(NPE, () -> builder.authenticator(null));
        Authenticator c = new TestAuthenticator();
        builder.authenticator(c);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().authenticator().get() == c);
        }
    }

    @Test
    public void testCookieHandler() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        CookieHandler a = new CookieManager();
        builder.cookieHandler(a);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().cookieHandler().get() == a);
        }
        CookieHandler b = new CookieManager();
        builder.cookieHandler(b);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().cookieHandler().get() == b);
        }
        assertThrows(NPE, () -> builder.cookieHandler(null));
        CookieManager c = new CookieManager();
        builder.cookieHandler(c);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().cookieHandler().get() == c);
        }
    }

    @Test
    public void testConnectTimeout() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        Duration a = Duration.ofSeconds(5);
        builder.connectTimeout(a);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().connectTimeout().get() == a);
        }
        Duration b = Duration.ofMinutes(1);
        builder.connectTimeout(b);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().connectTimeout().get() == b);
        }
        assertThrows(NPE, () -> builder.cookieHandler(null));
        Duration c = Duration.ofHours(100);
        builder.connectTimeout(c);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().connectTimeout().get() == c);
        }

        assertThrows(IAE, () -> builder.connectTimeout(ZERO));
        assertThrows(IAE, () -> builder.connectTimeout(ofSeconds(0)));
        assertThrows(IAE, () -> builder.connectTimeout(ofSeconds(-1)));
        assertThrows(IAE, () -> builder.connectTimeout(ofNanos(-100)));
    }

    static class TestExecutor implements Executor {
        public void execute(Runnable r) { r.run();}
    }

    @Test
    public void testExecutor() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        TestExecutor a = new TestExecutor();
        builder.executor(a);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().executor().get() == a);
        }
        TestExecutor b = new TestExecutor();
        builder.executor(b);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().executor().get() == b);
        }
        assertThrows(NPE, () -> builder.executor(null));
        TestExecutor c = new TestExecutor();
        builder.executor(c);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().executor().get() == c);
        }
    }

    @Test
    public void testProxySelector() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        ProxySelector a = ProxySelector.of(null);
        builder.proxy(a);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().proxy().get() == a);
        }
        ProxySelector b = ProxySelector.of(InetSocketAddress.createUnresolved("foo", 80));
        builder.proxy(b);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().proxy().get() == b);
        }
        assertThrows(NPE, () -> builder.proxy(null));
        ProxySelector c = ProxySelector.of(InetSocketAddress.createUnresolved("bar", 80));
        builder.proxy(c);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().proxy().get() == c);
        }
    }

    @Test
    public void testSSLParameters() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        SSLParameters a = new SSLParameters();
        a.setCipherSuites(new String[] { "A" });
        builder.sslParameters(a);
        a.setCipherSuites(new String[] { "Z" });
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslParameters() != (a));
        }
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslParameters().getCipherSuites()[0].equals("A"));
        }
        SSLParameters b = new SSLParameters();
        b.setEnableRetransmissions(true);
        builder.sslParameters(b);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslParameters() != b);
        }
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslParameters().getEnableRetransmissions());
        }
        assertThrows(NPE, () -> builder.sslParameters(null));
        SSLParameters c = new SSLParameters();
        c.setProtocols(new String[] { "C" });
        builder.sslParameters(c);
        c.setProtocols(new String[] { "D" });
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslParameters().getProtocols()[0].equals("C"));
        }
        // test defaults for needClientAuth and wantClientAuth
        builder.sslParameters(new SSLParameters());
        try (var closer = closeable(builder)) {
            assertFalse(closer.build().sslParameters().getNeedClientAuth(),
                    "needClientAuth() was expected to be false");
            assertFalse(closer.build().sslParameters().getWantClientAuth(),
                    "wantClientAuth() was expected to be false");
        }
        // needClientAuth = true and thus wantClientAuth = false
        SSLParameters needClientAuthParams = new SSLParameters();
        needClientAuthParams.setNeedClientAuth(true);
        builder.sslParameters(needClientAuthParams);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslParameters().getNeedClientAuth(),
                    "needClientAuth() was expected to be true");
            assertFalse(closer.build().sslParameters().getWantClientAuth(),
                    "wantClientAuth() was expected to be false");
        }
        // wantClientAuth = true and thus needClientAuth = false
        SSLParameters wantClientAuthParams = new SSLParameters();
        wantClientAuthParams.setWantClientAuth(true);
        builder.sslParameters(wantClientAuthParams);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslParameters().getWantClientAuth(),
                    "wantClientAuth() was expected to be true");
            assertFalse(closer.build().sslParameters().getNeedClientAuth(),
                    "needClientAuth() was expected to be false");
        }
    }

    @Test
    public void testSSLContext() throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder();
        SSLContext a = (new SimpleSSLContext()).get();
        builder.sslContext(a);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslContext() == a);
        }
        SSLContext b = (new SimpleSSLContext()).get();
        builder.sslContext(b);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslContext() == b);
        }
        assertThrows(NPE, () -> builder.sslContext(null));
        SSLContext c = (new SimpleSSLContext()).get();
        builder.sslContext(c);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().sslContext() == c);
        }
    }

    @Test
    public void testFollowRedirects() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        builder.followRedirects(Redirect.ALWAYS);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().followRedirects() == Redirect.ALWAYS);
        }
        builder.followRedirects(Redirect.NEVER);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().followRedirects() == Redirect.NEVER);
        }
        assertThrows(NPE, () -> builder.followRedirects(null));
        builder.followRedirects(Redirect.NORMAL);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().followRedirects() == Redirect.NORMAL);
        }
    }

    @Test
    public void testVersion() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        builder.version(Version.HTTP_2);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().version() == Version.HTTP_2);
        }
        builder.version(Version.HTTP_1_1);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().version() == Version.HTTP_1_1);
        }
        assertThrows(NPE, () -> builder.version(null));
        builder.version(Version.HTTP_2);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().version() == Version.HTTP_2);
        }
        builder.version(Version.HTTP_1_1);
        try (var closer = closeable(builder)) {
            assertTrue(closer.build().version() == Version.HTTP_1_1);
        }
    }

    @Test
    static void testPriority() throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder();
        assertThrows(IAE, () -> builder.priority(-1));
        assertThrows(IAE, () -> builder.priority(0));
        assertThrows(IAE, () -> builder.priority(257));
        assertThrows(IAE, () -> builder.priority(500));

        builder.priority(1);
        try (var httpClient = builder.build()) {}
        builder.priority(256);
        try (var httpClient = builder.build()) {}
    }

    /**
     * Tests the {@link java.net.http.HttpClient.Builder#localAddress(InetAddress)} method
     * behaviour when that method is called on a builder returned by {@link HttpClient#newBuilder()}
     */
    @Test
    public void testLocalAddress() throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder();
        // setting null should work fine
        builder.localAddress(null);
        builder.localAddress(InetAddress.getLoopbackAddress());
        // resetting back to null should work fine
        builder.localAddress(null);
    }

    /**
     * Tests that the default method implementation of
     * {@link java.net.http.HttpClient.Builder#localAddress(InetAddress)} throws
     * an {@link UnsupportedOperationException}
     */
    @Test
    public void testDefaultMethodImplForLocalAddress() throws Exception {
        HttpClient.Builder noOpBuilder = new HttpClient.Builder() {
            @Override
            public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
                return null;
            }

            @Override
            public HttpClient.Builder connectTimeout(Duration duration) {
                return null;
            }

            @Override
            public HttpClient.Builder sslContext(SSLContext sslContext) {
                return null;
            }

            @Override
            public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
                return null;
            }

            @Override
            public HttpClient.Builder executor(Executor executor) {
                return null;
            }

            @Override
            public HttpClient.Builder followRedirects(Redirect policy) {
                return null;
            }

            @Override
            public HttpClient.Builder version(Version version) {
                return null;
            }

            @Override
            public HttpClient.Builder priority(int priority) {
                return null;
            }

            @Override
            public HttpClient.Builder proxy(ProxySelector proxySelector) {
                return null;
            }

            @Override
            public HttpClient.Builder authenticator(Authenticator authenticator) {
                return null;
            }

            @Override
            public HttpClient build() {
                return null;
            }
        };
        // expected to throw a UnsupportedOperationException
        assertThrows(UOE, () -> noOpBuilder.localAddress(null));
        // a non-null address should also throw a UnsupportedOperationException
        assertThrows(UOE, () -> noOpBuilder.localAddress(InetAddress.getLoopbackAddress()));
    }

    // ---

    static final URI uri = URI.create("http://foo.com/");

    @Test
    static void testHttpClientSendArgs() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(uri).build();

            assertThrows(NPE, () -> client.send(null, BodyHandlers.discarding()));
            assertThrows(NPE, () -> client.send(request, null));
            assertThrows(NPE, () -> client.send(null, null));

            assertThrows(NPE, () -> client.sendAsync(null, BodyHandlers.discarding()));
            assertThrows(NPE, () -> client.sendAsync(request, null));
            assertThrows(NPE, () -> client.sendAsync(null, null));

            assertThrows(NPE, () -> client.sendAsync(null, BodyHandlers.discarding(), null));
            assertThrows(NPE, () -> client.sendAsync(request, null, null));
            assertThrows(NPE, () -> client.sendAsync(null, null, null));

            // CONNECT is disallowed in the implementation, since it is used for
            // tunneling, and is handled separately for security checks.
            HttpRequest connectRequest = new HttpConnectRequest();
            assertThrows(IAE, () -> client.send(connectRequest, BodyHandlers.discarding()));
            assertThrows(IAE, () -> client.sendAsync(connectRequest, BodyHandlers.discarding()));
            assertThrows(IAE, () -> client.sendAsync(connectRequest, BodyHandlers.discarding(), null));
        }
    }

    static class HttpConnectRequest extends HttpRequest {
        @Override public Optional<BodyPublisher> bodyPublisher() { return Optional.empty(); }
        @Override public String method() { return "CONNECT"; }
        @Override public Optional<Duration> timeout() { return Optional.empty(); }
        @Override public boolean expectContinue() { return false; }
        @Override public URI uri() { return URI.create("http://foo.com/"); }
        @Override public Optional<Version> version() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (x, y) -> true); }
    }

    // ---

    @Test
    static void testUnsupportedWebSocket() throws Exception {
        //  @implSpec The default implementation of this method throws
        // {@code UnsupportedOperationException}.
        assertThrows(UOE, () -> (new MockHttpClient()).newWebSocketBuilder());
    }

    @Test
    static void testDefaultShutdown() throws Exception {
        try (HttpClient client = new MockHttpClient()) {
            client.shutdown(); // does nothing
        }
    }

    @Test
    static void testDefaultShutdownNow() throws Exception {
        try (HttpClient client = new MockHttpClient()) {
            client.shutdownNow(); // calls shutdown, doesn't wait
        }

        AtomicInteger shutdownCalled = new AtomicInteger();
        HttpClient mock = new MockHttpClient() {
            @Override
            public void shutdown() {
                super.shutdown();
                shutdownCalled.incrementAndGet();
            }
        };
        try (HttpClient client = mock) {
            client.shutdownNow();  // calls shutdown, doesn't wait
        }

        // once from shutdownNow(), and once from close()
        assertEquals(shutdownCalled.get(), 2);
    }

    @Test
    static void testDefaultIsTerminated() throws Exception {
        try (HttpClient client = new MockHttpClient()) {
            assertFalse(client.isTerminated());
        }
    }

    @Test
    static void testDefaultAwaitTermination() throws Exception {
        try (HttpClient client = new MockHttpClient()) {
            assertTrue(client.awaitTermination(Duration.ofDays(1)));
        }
        try (HttpClient client = new MockHttpClient()) {
            assertThrows(NullPointerException.class,
                    () -> client.awaitTermination(null));
        }
    }

    @Test
    static void testDefaultClose() {
        AtomicInteger shutdownCalled = new AtomicInteger();
        AtomicInteger awaitTerminationCalled = new AtomicInteger();
        AtomicInteger shutdownNowCalled = new AtomicInteger();
        HttpClient mock = new MockHttpClient() {
            @Override
            public void shutdown() {
                super.shutdown();
                shutdownCalled.incrementAndGet();
            }
            @Override
            public void shutdownNow() {
                super.shutdownNow();
                shutdownNowCalled.incrementAndGet();
            }

            @Override
            public boolean awaitTermination(Duration duration) throws InterruptedException {
                int count = awaitTerminationCalled.incrementAndGet();
                if (count == 1) return false;
                if (count == 2) return true;
                if (count == 3) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedException();
                }
                return super.awaitTermination(duration);
            }
        };

        // first time around:
        //   close()
        //      shutdown() 0->1
        //      awaitTermination() 0->1 -> false
        //      awaitTermination() 1->2 -> true
        try (HttpClient client = mock) { }
        assertEquals(shutdownCalled.get(), 1); // called by close()
        assertEquals(shutdownNowCalled.get(), 0); // not called
        assertEquals(awaitTerminationCalled.get(), 2); // called by close() twice
        assertFalse(Thread.currentThread().isInterrupted());

        // second time around:
        //   close()
        //      shutdown() 1->2
        //      awaitTermination() 2->3 -> interrupt, throws
        //      shutdownNow() 0->1
        //         calls shutdown() 2->3
        //      awaitTermination() 3->4 -> true
        try (HttpClient client = mock) { }
        assertEquals(shutdownCalled.get(), 3); // called by close() and shutdownNow()
        assertEquals(shutdownNowCalled.get(), 1); // called by close() due to interrupt
        assertEquals(awaitTerminationCalled.get(), 4); // called by close twice
        assertTrue(Thread.currentThread().isInterrupted());
        assertTrue(Thread.interrupted());
    }

    static class MockHttpClient extends HttpClient {
        @Override public Optional<CookieHandler> cookieHandler() { return null; }
        @Override public Optional<Duration> connectTimeout() { return null; }
        @Override public Redirect followRedirects() { return null; }
        @Override public Optional<ProxySelector> proxy() { return null; }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return null; }
        @Override public Version version() { return null; }
        @Override public Optional<Executor> executor() { return null; }
        @Override public <T> HttpResponse<T>
        send(HttpRequest request, BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            return null;
        }
        @Override public <T> CompletableFuture<HttpResponse<T>>
        sendAsync(HttpRequest request, BodyHandler<T> responseBodyHandler) {
            return null;
        }
        @Override
        public <T> CompletableFuture<HttpResponse<T>>
        sendAsync(HttpRequest x, BodyHandler<T> y, PushPromiseHandler<T> z) {
            return null;
        }
    }

    /* ---- standalone entry point ---- */

    public static void main(String[] args) throws Exception {
        HttpClientBuilderTest test = new HttpClientBuilderTest();
        for (Method m : HttpClientBuilderTest.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Test.class)) {
                try {
                    m.invoke(test);
                    System.out.printf("test %s: success%n", m.getName());
                } catch (Throwable t ) {
                    System.out.printf("test %s: failed%n", m.getName());
                    t.printStackTrace();
                }
            }
        }
    }
}
