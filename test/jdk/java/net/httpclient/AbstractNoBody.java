/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;

import static java.lang.System.err;
import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.testng.Assert.assertEquals;

public abstract class AbstractNoBody implements HttpServerAdapters {

    SSLContext sslContext;
    HttpTestServer httpTestServer;         // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;       // HTTPS/1.1
    HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    String httpURI_fixed;
    String httpURI_chunk;
    String httpsURI_fixed;
    String httpsURI_chunk;
    String http2URI_fixed;
    String http2URI_chunk;
    String https2URI_fixed;
    String https2URI_chunk;

    static final String SIMPLE_STRING = "Hello world. Goodbye world";
    static final int ITERATION_COUNT = 3;
    // a shared executor helps reduce the amount of threads created by the test
    static final ExecutorService executor = Executors.newFixedThreadPool(ITERATION_COUNT * 2);
    static final ExecutorService serverExecutor = Executors.newFixedThreadPool(ITERATION_COUNT * 4);
    static final AtomicLong clientCount = new AtomicLong();
    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    @DataProvider(name = "variants")
    public Object[][] variants() {
        return new Object[][]{
                { httpURI_fixed,    false },
                { httpURI_chunk,    false },
                { httpsURI_fixed,   false },
                { httpsURI_chunk,   false },
                { http2URI_fixed,   false },
                { http2URI_chunk,   false },
                { https2URI_fixed,  false,},
                { https2URI_chunk,  false },

                { httpURI_fixed,    true },
                { httpURI_chunk,    true },
                { httpsURI_fixed,   true },
                { httpsURI_chunk,   true },
                { http2URI_fixed,   true },
                { http2URI_chunk,   true },
                { https2URI_fixed,  true,},
                { https2URI_chunk,  true },
        };
    }

    private volatile HttpClient sharedClient;

    static Version version(String uri) {
        if (uri.contains("/http1/") || uri.contains("/https1/"))
            return HTTP_1_1;
        if (uri.contains("/http2/") || uri.contains("/https2/"))
            return HTTP_2;
        return null;
    }

    HttpRequest.Builder newRequestBuilder(String uri) {
        var builder = HttpRequest.newBuilder(URI.create(uri));
        return builder;
    }

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        return HttpClient.newBuilder()
                .executor(executor)
                .proxy(NO_PROXY)
                .sslContext(sslContext)
                .build();
    }

    HttpClient newHttpClient(boolean share) {
        if (!share) return makeNewClient();
        HttpClient shared = sharedClient;
        if (shared != null) return shared;
        synchronized (this) {
            shared = sharedClient;
            if (shared == null) {
                shared = sharedClient = makeNewClient();
            }
            return shared;
        }
    }

    record CloseableClient(HttpClient client, boolean shared)
            implements Closeable {
        public void close() {
            if (shared) return;
            client.close();
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        printStamp(START, "setup");
        HttpServerAdapters.enableServerLogging();
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        // HTTP/1.1
        HttpTestHandler h1_fixedLengthNoBodyHandler = new FixedLengthNoBodyHandler();
        HttpTestHandler h1_chunkNoBodyHandler = new ChunkedNoBodyHandler();

        httpTestServer = HttpTestServer.create(HTTP_1_1, null, serverExecutor);
        httpTestServer.addHandler(h1_fixedLengthNoBodyHandler,"/http1/noBodyFixed");
        httpTestServer.addHandler(h1_chunkNoBodyHandler, "/http1/noBodyChunk");
        httpURI_fixed = "http://" + httpTestServer.serverAuthority() + "/http1/noBodyFixed";
        httpURI_chunk = "http://" + httpTestServer.serverAuthority() + "/http1/noBodyChunk";

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext, serverExecutor);
        httpsTestServer.addHandler(h1_fixedLengthNoBodyHandler,"/https1/noBodyFixed");
        httpsTestServer.addHandler(h1_chunkNoBodyHandler, "/https1/noBodyChunk");
        httpsURI_fixed = "https://" + httpsTestServer.serverAuthority() + "/https1/noBodyFixed";
        httpsURI_chunk = "https://" + httpsTestServer.serverAuthority() + "/https1/noBodyChunk";

        // HTTP/2
        HttpTestHandler h2_fixedLengthNoBodyHandler = new FixedLengthNoBodyHandler();
        HttpTestHandler h2_chunkedNoBodyHandler = new ChunkedNoBodyHandler();

        http2TestServer = HttpTestServer.create(HTTP_2, null, serverExecutor);
        http2TestServer.addHandler(h2_fixedLengthNoBodyHandler, "/http2/noBodyFixed");
        http2TestServer.addHandler(h2_chunkedNoBodyHandler, "/http2/noBodyChunk");
        http2URI_fixed = "http://" + http2TestServer.serverAuthority() + "/http2/noBodyFixed";
        http2URI_chunk = "http://" + http2TestServer.serverAuthority() + "/http2/noBodyChunk";

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext, serverExecutor);
        https2TestServer.addHandler(h2_fixedLengthNoBodyHandler, "/https2/noBodyFixed");
        https2TestServer.addHandler(h2_chunkedNoBodyHandler, "/https2/noBodyChunk");
        https2URI_fixed = "https://" + https2TestServer.serverAuthority() + "/https2/noBodyFixed";
        https2URI_chunk = "https://" + https2TestServer.serverAuthority() + "/https2/noBodyChunk";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();

        var shared = newHttpClient(true);

        out.println("HTTP/1.1 server       (http) listening at: " + httpTestServer.serverAuthority());
        out.println("HTTP/1.1 server       (TLS)  listening at: " + httpsTestServer.serverAuthority());
        out.println("HTTP/2   server       (h2c)  listening at: " + http2TestServer.serverAuthority());
        out.println("HTTP/2   server       (h2)   listening at: " + https2TestServer.serverAuthority());

        out.println("Shared client is: " + shared);

        printStamp(END,"setup");
    }

    @AfterTest
    public void teardown() throws Exception {
        printStamp(START, "teardown");
        sharedClient.close();
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
        executor.close();
        serverExecutor.close();
        printStamp(END, "teardown");
    }

    static final String START = "start";
    static final String END   = "end  ";
    void printStamp(String what, String fmt, Object... args) {
        System.out.printf("%s: %s \t [%s]\t %s%n",
                getClass().getSimpleName(), what, now(), String.format(fmt,args));
    }


    static class FixedLengthNoBodyHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            //out.println("NoBodyHandler received request to " + t.getRequestURI());
            boolean echo = "echo".equals(t.getRequestURI().getRawQuery());
            byte[] reqbytes;
            try (InputStream is = t.getRequestBody()) {
                reqbytes = is.readAllBytes();
            }
            if (echo) {
                t.sendResponseHeaders(200, reqbytes.length);
                if (reqbytes.length > 0) {
                    try (var os = t.getResponseBody()) {
                        os.write(reqbytes);
                    }
                }
            } else {
                t.sendResponseHeaders(200, 0); // no body
            }
        }
    }

    static class ChunkedNoBodyHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            //out.println("NoBodyHandler received request to " + t.getRequestURI());
            boolean echo = "echo".equals(t.getRequestURI().getRawQuery());
            byte[] reqbytes;
            try (InputStream is = t.getRequestBody()) {
                reqbytes = is.readAllBytes();
            }
            if (echo) {
                t.sendResponseHeaders(200, -1);
                try (var os = t.getResponseBody()) {
                    os.write(reqbytes);
                }
            } else {
                t.sendResponseHeaders(200, -1); // chunked
                t.getResponseBody().close();  // write nothing
            }
        }
    }

    /*
     * Converts a ByteBuffer containing bytes encoded using
     * the given charset into a string.
     * This method does not throw but will replace
     * unrecognized sequences with the replacement character.
     */
    public static String asString(ByteBuffer buffer, Charset charset) {
        var decoded = charset.decode(buffer);
        char[] chars = new char[decoded.length()];
        decoded.get(chars);
        return new String(chars);
    }

    /*
     * Converts a ByteBuffer containing UTF-8 bytes into a
     * string. This method does not throw but will replace
     * unrecognized sequences with the replacement character.
     */
    public static String asString(ByteBuffer buffer) {
        return asString(buffer, StandardCharsets.UTF_8);
    }
}
