/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.http3.Http3TestServer
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @compile ../ReferenceTracker.java
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=ssl,requests,responses,errors
 *       -Djdk.internal.httpclient.debug=true
 *       HTTP3NoBodyTest
 * @summary this is a copy of http2/NoBodyTest over HTTP/3
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import javax.net.ssl.*;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

@Test
public class HTTP3NoBodyTest {
    static int http3Port, https2Port;
    static Http3TestServer http3OnlyServer;
    static Http2TestServer https2AltSvcServer;
    static HttpClient client = null;
    static ExecutorService clientExec;
    static ExecutorService serverExec;
    static SSLContext sslContext;
    static final String TEST_STRING = "The quick brown fox jumps over the lazy dog ";

    static volatile String http3URIString, https2URIString;

    static void initialize() throws Exception {
        try {
            SimpleSSLContext sslct = new SimpleSSLContext();
            sslContext = sslct.get();
            client = getClient();

            // server that only supports HTTP/3
            http3OnlyServer = new Http3TestServer(sslContext, serverExec);
            http3OnlyServer.addHandler("/", new Handler());
            http3Port = http3OnlyServer.getAddress().getPort();
            System.out.println("HTTP/3 server started at localhost:" + http3Port);

            // server that supports both HTTP/2 and HTTP/3, with HTTP/3 on an altSvc port.
            https2AltSvcServer = new Http2TestServer(true, 0, serverExec, sslContext);
            https2AltSvcServer.enableH3AltServiceOnEphemeralPort();
            https2AltSvcServer.addHandler(new Handler(), "/");
            https2Port = https2AltSvcServer.getAddress().getPort();
            System.out.println("HTTP/2 server started at localhost:" + https2Port);

            http3URIString = "https://localhost:" + http3Port + "/foo/";
            https2URIString = "https://localhost:" + https2Port + "/bar/";

            http3OnlyServer.start();
            https2AltSvcServer.start();
        } catch (Throwable e) {
            System.err.println("Throwing now");
            e.printStackTrace(System.err);
            throw e;
        }
    }

    @Test
    public static void runtest() throws Exception {
        try {
            initialize();
            warmup(false);
            warmup(true);
            test(false);
            test(true);
            if (client != null) {
                var tracker = ReferenceTracker.INSTANCE;
                tracker.track(client);
                client = null;
                System.gc();
                var error = tracker.check(1500);
                if (error != null) throw error;
            }
        } catch (Throwable tt) {
            System.err.println("Unexpected Throwable caught");
            tt.printStackTrace(System.err);
            throw tt;
        } finally {
            http3OnlyServer.stop();
            https2AltSvcServer.stop();
            serverExec.close();
            clientExec.close();
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
        return getURI(altSvc, -1);
    }

    static URI getURI(boolean altSvc, int step) {
        return URI.create(getURIString(altSvc, step));
    }

    static String getURIString(boolean altSvc, int step) {
        var uriStr = altSvc ? https2URIString : http3URIString;
        return step >= 0 ? (uriStr + step) : uriStr;
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
        return switch (count.getAndIncrement() %3) {
            case 1 -> ANY;
            case 2 -> ALT_SVC;
            default -> null;
        };
    }

    static final int LOOPS = 13;

    static void warmup(boolean altSvc) throws Exception {
        URI uri = getURI(altSvc);
        String type = altSvc ? "http2" : "http3";
        System.out.println("warmup: " + type);
        System.err.println("Request to " + uri);
        var http3Only = altSvc == false;
        var config = config(http3Only);

        // Do a simple warmup request

        HttpClient client = getClient();
        var builder = HttpRequest.newBuilder(uri);
        HttpRequest req = builder
                .POST(BodyPublishers.ofString("Random text"))
                .setOption(H3_DISCOVERY, config)
                .build();
        HttpResponse<String> response = client.send(req, BodyHandlers.ofString());
        checkStatus(200, response.statusCode());
        String responseBody = response.body();
        HttpHeaders h = response.headers();
        checkStrings(TEST_STRING + type, responseBody);
        System.out.println("warmup: " + type + " done");
        System.err.println("warmup: " + type  + " done");
    }

    static void test(boolean http2) throws Exception {
        URI uri = getURI(http2);
        String type = http2 ? "http2" : "http3";
        System.err.println("Request to " + uri);
        var http3Only = http2 == false;
        for (int i = 0; i < LOOPS; i++) {
            var config = config(http3Only);
            URI uri2 = getURI(http2, i);
            HttpRequest request = HttpRequest.newBuilder(uri2)
                    .POST(BodyPublishers.ofString(TEST_STRING))
                    .setOption(H3_DISCOVERY, config)
                    .build();
            System.out.println(type + ": Loop " + i + ", config: " + config + ", uri: " + uri2);
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            int expectedResponse = (i % 2) == 0 ? 200 : 204;
            if (response.statusCode() != expectedResponse)
                throw new RuntimeException("wrong response code " + response.statusCode());
            if (expectedResponse == 200 && !response.body().equals(TEST_STRING + type)) {
                System.err.printf(type + " response received/expected %s/%s\n", response.body(), TEST_STRING + type);
                throw new RuntimeException("wrong response body");
            }
            if (response.version() != HTTP_3) {
                throw new RuntimeException("wrong response version: " + response.version());
            }
            System.out.println(type + ": Loop " + i + " done");
        }
        System.err.println("test: " + type + " DONE");
    }

    static URI base(URI uri) {
        var uriStr = uri.toString();
        if (uriStr.startsWith(http3URIString)) {
            if (uriStr.equals(http3URIString)) return uri;
            return URI.create(http3URIString);
        } else if (uri.toString().startsWith(https2URIString)) {
            if (uriStr.equals(https2URIString)) return uri;
            return URI.create(https2URIString);
        } else return uri;
    }

    static class Handler implements Http2Handler {

        public Handler() {}

        volatile int invocation = 0;

        @Override
        public void handle(Http2TestExchange t)
                throws IOException {
            try {
                URI uri = t.getRequestURI();
                System.err.printf("Handler received request to %s from %s\n",
                        uri, t.getRemoteAddress());
                String type = uri.toString().startsWith(http3URIString)
                        ? "http3" : "http2";
                InputStream is = t.getRequestBody();
                while (is.read() != -1);
                is.close();

                // every second response is 204.
                var base = base(uri);
                int step = base == uri ? 0 : Integer.parseInt(base.relativize(uri).toString());
                invocation++;

                if ((step++ % 2) == 1) {
                    System.err.println("Server sending 204");
                    t.sendResponseHeaders(204, -1);
                } else {
                    System.err.println("Server sending 200");
                    String body = TEST_STRING + type;
                    t.sendResponseHeaders(200, body.length());
                    OutputStream os = t.getResponseBody();
                    os.write(body.getBytes());
                    os.close();
                }
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                throw new IOException(e);
            }
        }
    }
}
