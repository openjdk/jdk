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
 * @key randomness
 * @bug 8087112
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @compile ../ReferenceTracker.java
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=ssl,requests,responses,errors
 *                     -Djdk.internal.httpclient.debug=true
 *                     H3BasicTest
 */
// -Dseed=-163464189156654174

import java.io.IOException;
import java.net.*;
import javax.net.ssl.*;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.*;
import java.util.Random;
import java.util.concurrent.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static jdk.test.lib.Asserts.assertFileContentsEqual;
import static jdk.test.lib.Utils.createTempFile;
import static jdk.test.lib.Utils.createTempFileOfSize;

public class H3BasicTest implements HttpServerAdapters {

    private static final Random RANDOM = RandomFactory.getRandom();

    private static final String CLASS_NAME = H3BasicTest.class.getSimpleName();

    static int http3Port, https2Port;
    static HttpTestServer http3OnlyServer;
    static HttpTestServer https2AltSvcServer;
    static HttpClient client = null;
    static ExecutorService clientExec;
    static ExecutorService serverExec;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    static volatile String http3URIString, pingURIString, https2URIString;

    static void initialize() throws Exception {
        try {
            client = getClient();

            // server that only supports HTTP/3
            http3OnlyServer = HttpTestServer.of(new Http3TestServer(sslContext, serverExec));
            http3OnlyServer.createContext("/", new HttpTestFileEchoHandler());
            http3OnlyServer.createContext("/ping", new EchoWithPingHandler());
            http3Port = http3OnlyServer.getAddress().getPort();
            System.out.println("HTTP/3 server started at localhost:" + http3Port);

            // server that supports both HTTP/2 and HTTP/3, with HTTP/3 on an altSvc port.
            Http2TestServer http2ServerImpl = new Http2TestServer(true, 0, serverExec, sslContext);
            if (RANDOM.nextBoolean()) {
                http2ServerImpl.enableH3AltServiceOnEphemeralPort();
            } else {
                http2ServerImpl.enableH3AltServiceOnSamePort();
            }
            https2AltSvcServer = HttpTestServer.of(http2ServerImpl);
            https2AltSvcServer.addHandler(new HttpTestFileEchoHandler(), "/");
            https2Port = https2AltSvcServer.getAddress().getPort();
            if (https2AltSvcServer.supportsH3DirectConnection()) {
                System.out.println("HTTP/2 server (same HTTP/3 origin) started at localhost:" + https2Port);
            } else {
                System.out.println("HTTP/2 server (different HTTP/3 origin) started at localhost:" + https2Port);
            }

            http3URIString = "https://" + http3OnlyServer.serverAuthority() + "/foo/";
            pingURIString = "https://" + http3OnlyServer.serverAuthority() + "/ping/";
            https2URIString = "https://" + https2AltSvcServer.serverAuthority() + "/bar/";

            http3OnlyServer.start();
            https2AltSvcServer.start();
        } catch (Throwable e) {
            System.err.println("Throwing now");
            e.printStackTrace();
            throw e;
        }
    }

    static final List<CompletableFuture<Long>> cfs = Collections
        .synchronizedList( new LinkedList<>());

    static CompletableFuture<Long> currentCF;

    static class EchoWithPingHandler extends HttpTestFileEchoHandler {
        private final Object lock = new Object();

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            // for now only one ping active at a time. don't want to saturate
            System.out.println("PING handler invoked for " + exchange.getRequestURI());
            synchronized(lock) {
                CompletableFuture<Long> cf = currentCF;
                if (cf == null || cf.isDone()) {
                    cf = exchange.sendPing();
                    assert cf != null;
                    cfs.add(cf);
                    currentCF = cf;
                }
            }
            super.handle(exchange);
        }
    }

    @Test
    public static void test() throws Exception {
        try {
            initialize();
            System.out.println("servers initialized");
            warmup(false);
            warmup(true);
            System.out.println("warmup finished");
            simpleTest(false, false);
            System.out.println("simpleTest(false, false): done");
            simpleTest(false, true);
            System.out.println("simpleTest(false, true): done");
            simpleTest(true, false);
            System.out.println("simpleTest(true, false): done");
            System.out.println("simple tests finished");
            streamTest(false);
            streamTest(true);
            System.out.println("stream tests finished");
            paramsTest();
            System.out.println("params test finished");
            CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0])).join();
            synchronized (cfs) {
                for (CompletableFuture<Long> cf : cfs) {
                    System.out.printf("Ping ack received in %d millisec\n", cf.get());
                }
            }
            System.out.println("closing client");
            if (client != null) {
                var tracker = ReferenceTracker.INSTANCE;
                tracker.track(client);
                client = null;
                System.gc();
                var error = tracker.check(1500);
                clientExec.close();
                if (error != null) throw error;
            }
        } catch (Throwable tt) {
            System.err.println("tt caught");
            tt.printStackTrace();
            throw tt;
        } finally {
            http3OnlyServer.stop();
            https2AltSvcServer.stop();
            serverExec.close();
        }
    }

    static HttpClient getClient() {
        if (client == null) {
            serverExec = Executors.newCachedThreadPool();
            clientExec = Executors.newCachedThreadPool();
            client = HttpServerAdapters.createClientBuilderForH3()
                    .executor(clientExec)
                    .sslContext(sslContext)
                    .version(HTTP_3)
                    .build();
        }
        return client;
    }

    static URI getURI(boolean altSvc) {
        return getURI(altSvc, false);
    }

    static URI getURI(boolean altsvc, boolean ping) {
        if (altsvc)
            return URI.create(https2URIString);
        else
            return URI.create(ping ? pingURIString: http3URIString);
    }

    static void checkStatus(int expected, int found) throws Exception {
        if (expected != found) {
            System.err.printf ("Test failed: wrong status code %d/%d\n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static void checkStrings(String expected, String found) throws Exception {
        if (!expected.equals(found)) {
            System.err.printf ("Test failed: wrong string %s/%s\n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static final AtomicInteger count = new AtomicInteger();
    static Http3DiscoveryMode config(boolean http3only) {
        if (http3only) return HTTP_3_URI_ONLY;
        // if the server supports H3 direct connection, we can
        // additionally use HTTP_3_URI_ONLY; Otherwise we can
        // only use ALT_SVC - or ANY (given that we should have
        // preloaded an ALT_SVC in warmup)
        int bound = https2AltSvcServer.supportsH3DirectConnection() ? 4 : 3;
        int rand = RANDOM.nextInt(bound);
        count.getAndIncrement();
        return switch (rand) {
            case 1 -> ANY;
            case 2 -> ALT_SVC;
            case 3 -> HTTP_3_URI_ONLY;
            default -> null;
        };
    }

    static final String SIMPLE_STRING = "Hello world Goodbye world";

    static final int LOOPS = 13;
    static final int FILESIZE = 64 * 1024 + 200;

    static void streamTest(boolean altSvc) throws Exception {
        URI uri = getURI(altSvc);
        System.err.printf("streamTest %b to %s\n" , altSvc, uri);
        System.out.printf("streamTest %b to %s\n" , altSvc, uri);

        HttpClient client = getClient();
        Path src = createTempFileOfSize(CLASS_NAME, ".dat", FILESIZE * 4);
        var http3Only = altSvc == false;
        var config = config(http3Only);
        HttpRequest req = HttpRequest.newBuilder(uri)
                                     .POST(BodyPublishers.ofFile(src))
                                     .setOption(H3_DISCOVERY, config)
                                     .build();

        Path dest = Paths.get("streamtest.txt");
        dest.toFile().delete();
        CompletableFuture<Path> response = client.sendAsync(req, BodyHandlers.ofFile(dest))
                .thenApply(resp -> {
                    if (resp.statusCode() != 200)
                        throw new RuntimeException();
                    if (resp.version() != HTTP_3) {
                        throw new RuntimeException("wrong response version: " + resp.version());
                    }
                    return resp.body();
                });
        response.join();
        assertFileContentsEqual(src, dest);
        System.err.println("streamTest: DONE");
    }

    static void paramsTest() throws Exception {
        URI u = new URI("https://" + https2AltSvcServer.serverAuthority() + "/foo");
        System.out.println("paramsTest: Request to " + u);
        https2AltSvcServer.addHandler(((HttpTestExchange t) -> {
            SSLSession s = t.getSSLSession();
            String prot = s.getProtocol();
            if (prot.equals("TLSv1.3")) {
                t.sendResponseHeaders(200, HttpTestExchange.RSPBODY_EMPTY);
            } else {
                System.err.printf("Protocols =%s\n", prot);
                t.sendResponseHeaders(500, HttpTestExchange.RSPBODY_EMPTY);
            }
        }), "/");
        HttpClient client = getClient();
        HttpRequest req = HttpRequest.newBuilder(u).build();
        HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
        int stat = resp.statusCode();
        if (stat != 200) {
            throw new RuntimeException("paramsTest failed " + stat);
        }
        if (resp.version() != HTTP_3) {
            throw new RuntimeException("wrong response version: " + resp.version());
        }
        System.err.println("paramsTest: DONE");
    }

    static void warmup(boolean altSvc) throws Exception {
        URI uri = getURI(altSvc);
        System.out.println("Warmup: Request to " + uri);
        System.err.println("Warmup: Request to " + uri);

        // Do a simple warmup request

        HttpClient client = getClient();
        var http3Only = altSvc == false;
        var config = config(http3Only);

        // in the warmup phase, we want to make sure
        // to preload the ALT_SVC, otherwise the first
        // request that uses ALT_SVC might go through HTTP/2
        if (altSvc) config = ALT_SVC;

        HttpRequest req = HttpRequest.newBuilder(uri)
                                     .POST(BodyPublishers.ofString(SIMPLE_STRING))
                                     .setOption(H3_DISCOVERY, config)
                                     .build();
        HttpResponse<String> response = client.send(req, BodyHandlers.ofString());
        checkStatus(200, response.statusCode());
        String responseBody = response.body();
        HttpHeaders h = response.headers();
        checkStrings(SIMPLE_STRING, responseBody);
        checkStrings(h.firstValue("x-hello").get(), "world");
        checkStrings(h.firstValue("x-bye").get(), "universe");
    }

    static <T> T logExceptionally(String desc, Throwable t) {
        System.out.println(desc + " failed: " + t);
        System.err.println(desc + " failed: " + t);
        if (t instanceof RuntimeException r) throw r;
        if (t instanceof Error e) throw e;
        throw new CompletionException(t);
    }

    static void simpleTest(boolean altSvc, boolean ping) throws Exception {
        URI uri = getURI(altSvc, ping);
        System.err.printf("simpleTest(altSvc:%s, ping:%s) Request to %s%n",
                altSvc, ping, uri);
        System.out.printf("simpleTest(altSvc:%s, ping:%s) Request to %s%n",
                altSvc, ping, uri);
        String type = altSvc ? "altSvc" : (ping ? "ping" : "http3");

        // Do loops asynchronously

        CompletableFuture<HttpResponse<Path>>[] responses = new CompletableFuture[LOOPS];
        final Path source = createTempFileOfSize(H3BasicTest.class.getSimpleName(), ".dat", FILESIZE);
        var http3Only = altSvc == false;
        for (int i = 0; i < LOOPS; i++) {
            var config = config(http3Only);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("X-Compare", source.toString())
                    .POST(BodyPublishers.ofFile(source))
                    .setOption(H3_DISCOVERY, config)
                    .build();
            String desc = type + ": Loop " + i;
            System.out.printf("%s simpleTest(altSvc:%s, ping:%s) config(%s) Request to %s%n",
                    desc, altSvc, ping, config, uri);
            System.err.printf("%s simpleTest(altSvc:%s, ping:%s) config(%s) Request to %s%n",
                    desc, altSvc, ping, config, uri);
            Path requestBodyFile = createTempFile(CLASS_NAME, ".dat");
            responses[i] = client.sendAsync(request, BodyHandlers.ofFile(requestBodyFile))
                //.thenApply(resp -> assertFileContentsEqual(resp.body(), source));
                .exceptionally((t) -> logExceptionally(desc, t))
                .thenApply(resp -> {
                    System.out.printf("Resp %s status %d body size %d\n",
                                      resp.version(), resp.statusCode(),
                                      resp.body().toFile().length()
                    );
                    assertFileContentsEqual(resp.body(), source);
                    if (resp.version() != HTTP_3) {
                        throw new RuntimeException("wrong response version: " + resp.version());
                    }
                    return resp;
                });
            Thread.sleep(100);
            System.out.println(type + ": Loop " + i + " done");
        }
        CompletableFuture.allOf(responses).join();
        System.err.println(type + " simpleTest: DONE");
    }
}
