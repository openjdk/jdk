/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import jdk.httpclient.test.lib.common.HttpServerAdapters;

import com.sun.net.httpserver.HttpsServer;
import jdk.httpclient.test.lib.common.TestServerConfigurator;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8232853
 * @summary AuthenticationFilter.Cache::remove may throw ConcurrentModificationException
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 *        DigestEchoServer ReferenceTracker jdk.httpclient.test.lib.common.TestServerConfigurator
 * @run testng/othervm -Dtest.requiresHost=true
 * -Djdk.httpclient.HttpClient.log=requests,headers,errors,quic
 * -Djdk.internal.httpclient.debug=false
 * AuthFilterCacheTest
 */

public class AuthFilterCacheTest implements HttpServerAdapters {

    static final String RESPONSE_BODY = "Hello World!";
    static final int REQUEST_COUNT = 5;
    static final int URI_COUNT = 8;
    static final CyclicBarrier barrier = new CyclicBarrier(REQUEST_COUNT * URI_COUNT);
    private static final SSLContext context = jdk.test.lib.net.SimpleSSLContext.findSSLContext();

    static {
        SSLContext.setDefault(context);
    }

    HttpTestServer http1Server;
    HttpTestServer http2Server;
    HttpTestServer https1Server;
    HttpTestServer https2Server;
    HttpTestServer h3onlyServer;
    HttpTestServer h3altSvcServer;
    DigestEchoServer.TunnelingProxy proxy;
    URI http1URI;
    URI https1URI;
    URI http2URI;
    URI https2URI;
    URI h3onlyURI;
    URI h3altSvcURI;
    InetSocketAddress proxyAddress;
    ProxySelector proxySelector;
    MyAuthenticator auth;
    HttpClient client;
    ExecutorService serverExecutor = Executors.newCachedThreadPool();
    ExecutorService virtualExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
            .name("HttpClient-Worker", 0).factory());

    @DataProvider(name = "uris")
    Object[][] testURIs() {
        Object[][] uris = new Object[][]{
                {List.of(http1URI.resolve("direct/orig/"),
                        https1URI.resolve("direct/orig/"),
                        https1URI.resolve("proxy/orig/"),
                        http2URI.resolve("direct/orig/"),
                        https2URI.resolve("direct/orig/"),
                        https2URI.resolve("proxy/orig/"),
                        h3onlyURI.resolve("direct/orig/"),
                        h3altSvcURI.resolve("direct/orig/"))}
        };
        return uris;
    }

    public HttpClient newHttpClient(ProxySelector ps, Authenticator auth) {
        HttpClient.Builder builder = newClientBuilderForH3()
                .executor(virtualExecutor)
                .sslContext(context)
                .authenticator(auth)
                .proxy(ps);
        return builder.build();
    }

    @BeforeClass
    public void setUp() throws Exception {
        try {
            InetSocketAddress sa =
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            auth = new MyAuthenticator();

            // HTTP/1.1
            http1Server = HttpTestServer.create(HTTP_1_1, null, serverExecutor);
            http1Server.addHandler(new TestHandler(), "/AuthFilterCacheTest/http1/");
            http1Server.start();
            http1URI = new URI("http://" + http1Server.serverAuthority()
                    + "/AuthFilterCacheTest/http1/");

            // HTTPS/1.1
            HttpsServer sserver1 = HttpsServer.create(sa, 100);
            sserver1.setExecutor(serverExecutor);
            sserver1.setHttpsConfigurator(new TestServerConfigurator(sa.getAddress(), context));
            https1Server = HttpTestServer.of(sserver1);
            https1Server.addHandler(new TestHandler(), "/AuthFilterCacheTest/https1/");
            https1Server.start();
            https1URI = new URI("https://" + https1Server.serverAuthority()
                    + "/AuthFilterCacheTest/https1/");

            // HTTP/2.0
            http2Server = HttpTestServer.create(HTTP_2);
            http2Server.addHandler(new TestHandler(), "/AuthFilterCacheTest/http2/");
            http2Server.start();
            http2URI = new URI("http://" + http2Server.serverAuthority()
                    + "/AuthFilterCacheTest/http2/");

            // HTTPS/2.0
            https2Server = HttpTestServer.create(HTTP_2, SSLContext.getDefault());
            https2Server.addHandler(new TestHandler(), "/AuthFilterCacheTest/https2/");
            https2Server.start();
            https2URI = new URI("https://" + https2Server.serverAuthority()
                    + "/AuthFilterCacheTest/https2/");

            h3onlyServer = HttpTestServer.create(HTTP_3_URI_ONLY, SSLContext.getDefault());
            h3onlyServer.addHandler(new TestHandler(), "/AuthFilterCacheTest/h3-only/");
            h3onlyURI = new URI("https://" + h3onlyServer.serverAuthority()
                    + "/AuthFilterCacheTest/h3-only/");
            h3onlyServer.start();

            h3altSvcServer = HttpTestServer.create(ANY, SSLContext.getDefault());
            h3altSvcServer.addHandler(new TestHandler(), "/AuthFilterCacheTest/h3-alt-svc/");
            h3altSvcServer.addHandler(new HttpHeadOrGetHandler(RESPONSE_BODY),
                    "/AuthFilterCacheTest/h3-alt-svc/direct/head/");
            h3altSvcURI = new URI("https://" + h3altSvcServer.serverAuthority()
                    + "/AuthFilterCacheTest/h3-alt-svc/");
            h3altSvcServer.start();

            proxy = DigestEchoServer.createHttpsProxyTunnel(
                    DigestEchoServer.HttpAuthSchemeType.NONE);
            proxyAddress = proxy.getProxyAddress();
            proxySelector = new HttpProxySelector(proxyAddress);
            client = newHttpClient(proxySelector, auth);

            HttpRequest headRequest = HttpRequest.newBuilder(h3altSvcURI.resolve("direct/head/h2"))
                    .HEAD()
                    .version(HTTP_2).build();
            System.out.println("Sending head request: " + headRequest);
            var headResponse = client.send(headRequest, BodyHandlers.ofString());
            assertEquals(headResponse.statusCode(), 200);
            assertEquals(headResponse.version(), HTTP_2);

            System.out.println("Setup: done");
        } catch (Exception x) {
            tearDown();
            throw x;
        } catch (Error e) {
            tearDown();
            throw e;
        }
    }

    @AfterClass
    public void tearDown() {
        proxy = stop(proxy, DigestEchoServer.TunnelingProxy::stop);
        http1Server = stop(http1Server, HttpTestServer::stop);
        https1Server = stop(https1Server, HttpTestServer::stop);
        http2Server = stop(http2Server, HttpTestServer::stop);
        https2Server = stop(https2Server, HttpTestServer::stop);
        h3onlyServer = stop(h3onlyServer, HttpTestServer::stop);
        h3altSvcServer = stop(h3altSvcServer, HttpTestServer::stop);
        client.close();
        virtualExecutor.close();

        System.out.println("Teardown: done");
    }

    private interface Stoppable<T> {
        void stop(T service) throws Exception;
    }

    static <T> T stop(T service, Stoppable<T> stop) {
        try {
            if (service != null) stop.stop(service);
        } catch (Throwable x) {
        }
        return null;
    }

    static class HttpProxySelector extends ProxySelector {
        private static final List<Proxy> NO_PROXY = List.of(Proxy.NO_PROXY);
        private final List<Proxy> proxyList;

        HttpProxySelector(InetSocketAddress proxyAddress) {
            proxyList = List.of(new Proxy(Proxy.Type.HTTP, proxyAddress));
        }

        @Override
        public List<Proxy> select(URI uri) {
            // Our proxy only supports tunneling
            if (uri.getScheme().equalsIgnoreCase("https")) {
                if (uri.getPath().contains("/proxy/")) {
                    return proxyList;
                }
            }
            return NO_PROXY;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            System.err.println("Connection to proxy failed: " + ioe);
            System.err.println("Proxy: " + sa);
            System.err.println("\tURI: " + uri);
            ioe.printStackTrace();
        }
    }

    public static class TestHandler implements HttpTestHandler {
        static final AtomicLong respCounter = new AtomicLong();

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            var count = respCounter.incrementAndGet();
            System.out.println("Server got request: " + t.getRequestURI());
            System.out.println("Responses handled: " + count);
            t.getRequestBody().readAllBytes();

            if (t.getRequestMethod().equalsIgnoreCase("GET")) {
                if (!t.getRequestHeaders().containsKey("Authorization")) {
                    t.getResponseHeaders()
                            .addHeader("WWW-Authenticate", "Basic realm=\"Earth\"");
                    t.sendResponseHeaders(401, 0);
                    System.out.println("Server sent 401 for " + t.getRequestURI());
                } else {
                    byte[] resp = RESPONSE_BODY.getBytes(StandardCharsets.UTF_8);
                    t.sendResponseHeaders(200, resp.length);
                    System.out.println("Server sent 200 for " + t.getRequestURI() + "; awaiting barrier");
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new IOException(e);
                    }
                    t.getResponseBody().write(resp);
                    System.out.println("Server sent body for " + t.getRequestURI());
                }
            }
            t.close();
        }
    }

    void doClient(List<URI> uris) {
        assert uris.size() == URI_COUNT;
        barrier.reset();
        System.out.println("Client will connect " + REQUEST_COUNT + " times to: "
                + uris.stream().map(URI::toString)
                .collect(Collectors.joining("\n\t", "\n\t", "\n")));

        List<CompletableFuture<HttpResponse<String>>> cfs = new ArrayList<>();

        int count = 0;
        for (int i = 0; i < REQUEST_COUNT; i++) {
            for (URI uri : uris) {
                String uriStr = uri.toString() + (++count);
                var builder = HttpRequest.newBuilder()
                        .uri(URI.create(uriStr));
                var config = uriStr.contains("h3-only") ? HTTP_3_URI_ONLY
                        : uriStr.contains("h3-alt-svc") ? ALT_SVC
                        : null;
                if (config != null) {
                    builder = builder.setOption(H3_DISCOVERY, config).version(HTTP_3);
                } else {
                    builder = builder.version(HTTP_2);
                }
                HttpRequest req = builder.build();
                System.out.printf("Sending request %s (version=%s, config=%s)%n",
                        req, req.version(), config);
                cfs.add(client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                        .handleAsync((r, t) -> logResponse(req, r, t))
                        .thenCompose(Function.identity()));
            }
        }
        CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0])).join();
    }

    CompletableFuture<HttpResponse<String>> logResponse(HttpRequest req,
                                                        HttpResponse<String> resp,
                                                        Throwable t) {
        if (t != null) {
            System.out.printf("Request failed: %s (version=%s, config=%s): %s%n",
                    req, req.version(), req.getOption(H3_DISCOVERY).orElse(null), t);
            t.printStackTrace(System.out);
            return CompletableFuture.failedFuture(t);
        } else {
            System.out.printf("Request succeeded: %s (version=%s, config=%s): %s%n",
                    req, req.version(), req.getOption(H3_DISCOVERY).orElse(null), resp);
            return CompletableFuture.completedFuture(resp);
        }
    }

    static final class MyAuthenticator extends Authenticator {
        private final AtomicInteger count = new AtomicInteger();

        MyAuthenticator() {
            super();
        }

        public PasswordAuthentication getPasswordAuthentication() {
            return (new PasswordAuthentication("user" + count,
                    ("passwordNotCheckedAnyway" + count).toCharArray()));
        }

        @Override
        public PasswordAuthentication requestPasswordAuthenticationInstance(String host,
                                                                            InetAddress addr,
                                                                            int port,
                                                                            String protocol,
                                                                            String prompt,
                                                                            String scheme,
                                                                            URL url,
                                                                            RequestorType reqType) {
            PasswordAuthentication passwordAuthentication;
            int count;
            synchronized (this) {
                count = this.count.incrementAndGet();
                passwordAuthentication = super.requestPasswordAuthenticationInstance(
                        host, addr, port, protocol, prompt, scheme, url, reqType);
            }
            // log outside of synchronized block
            System.out.println("Authenticator called: " + count);
            return passwordAuthentication;
        }

        public int getCount() {
            return count.get();
        }
    }

    @Test(dataProvider = "uris")
    public void test(List<URI> uris) throws Exception {
        System.out.println("Servers listening at "
                + uris.stream().map(URI::toString)
                .collect(Collectors.joining("\n\t", "\n\t", "\n")));
        System.out.println("h3-alt-svc server listening for h3 at: "
                + h3altSvcServer.getH3AltService().map(s -> s.getAddress()).orElse(null));
        doClient(uris);
    }
}
