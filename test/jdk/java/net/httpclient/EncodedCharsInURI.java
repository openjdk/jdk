/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8199683
 * @summary Tests that escaped characters in URI are correctly
 *          handled (not re-escaped and not unescaped)
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 *        EncodedCharsInURI
 * @run testng/othervm
 *        -Djdk.tls.acknowledgeCloseNotify=true
 *        -Djdk.internal.httpclient.debug=true
 *        -Djdk.httpclient.HttpClient.log=headers,errors EncodedCharsInURI
 */
//*        -Djdk.internal.httpclient.debug=true

import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.err;
import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class EncodedCharsInURI implements HttpServerAdapters {

    SSLContext sslContext;
    HttpTestServer httpTestServer;    // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;   // HTTPS/1.1
    HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    DummyServer    httpDummyServer;   // HTTP/1.1    [ 2 servers ]
    DummyServer    httpsDummyServer;  // HTTPS/1.1
    HttpTestServer http3TestServer;   // HTTP/3 ( h3  )
    String httpURI_fixed;
    String httpURI_chunk;
    String httpsURI_fixed;
    String httpsURI_chunk;
    String http2URI_fixed;
    String http2URI_chunk;
    String https2URI_fixed;
    String https2URI_chunk;
    String http3URI_fixed;
    String http3URI_chunk;
    String http3URI_head;
    String httpDummy;
    String httpsDummy;

    static final int ITERATION_COUNT = 1;
    // a shared executor helps reduce the amount of threads created by the test
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

    private volatile HttpClient sharedClient;

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

    @AfterClass
    static final void printFailedTests() {
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

    private String[] uris() {
        return new String[] {
                httpDummy,
                httpsDummy,
                http3URI_fixed,
                http3URI_chunk,
                httpURI_fixed,
                httpURI_chunk,
                httpsURI_fixed,
                httpsURI_chunk,
                http2URI_fixed,
                http2URI_chunk,
                https2URI_fixed,
                https2URI_chunk,
        };
    }

    @DataProvider(name = "noThrows")
    public Object[][] noThrows() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2][];
        //Object[][] result = new Object[uris.length][];
        int i = 0;
        for (boolean sameClient : List.of(false, true)) {
            //if (!sameClient) continue;
            for (String uri: uris()) {
                result[i++] = new Object[] {uri, sameClient};
            }
        }
        assert i == uris.length * 2;
        // assert i == uris.length ;
        return result;
    }

    static Version version(String uri) {
        if (uri.contains("/http1/") || uri.contains("/https1/"))
            return HTTP_1_1;
        if (uri.contains("/http2/") || uri.contains("/https2/"))
            return HTTP_2;
        if (uri.contains("/http3/"))
            return HTTP_3;
        return null;
    }

    HttpRequest.Builder newRequestBuilder(String uri) {
        var builder = HttpRequest.newBuilder(URI.create(uri));
        if (version(uri) == HTTP_3) {
            builder.version(HTTP_3);
            builder.setOption(H3_DISCOVERY, http3TestServer.h3DiscoveryConfig());
        }
        return builder;
    }

    HttpResponse<String> headRequest(HttpClient client)
            throws IOException, InterruptedException
    {
        out.println("\n" + now() + "--- Sending HEAD request ----\n");
        err.println("\n" + now() + "--- Sending HEAD request ----\n");

        var request = newRequestBuilder(http3URI_head)
                .HEAD().version(HTTP_2).build();
        var response = client.send(request, BodyHandlers.ofString());
        assertEquals(response.statusCode(), 200);
        assertEquals(response.version(), HTTP_2);
        out.println("\n" + now() + "--- HEAD request succeeded ----\n");
        err.println("\n" + now() + "--- HEAD request succeeded ----\n");
        return response;
    }

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        return newClientBuilderForH3()
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

    final String ENCODED = "/01%252F03/";

    record CloseableClient(HttpClient client, boolean shared)
            implements Closeable {
        public void close() {
            if (shared) return;
            client.close();
        }
    }

    @Test(dataProvider = "noThrows")
    public void testEncodedChars(String uri, boolean sameClient)
            throws Exception {
        HttpClient client = null;
        out.printf("%n%s testEncodedChars(%s, %b)%n", now(), uri, sameClient);
        uri = uri + ENCODED;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                BodyPublisher bodyPublisher = BodyPublishers.ofString(uri);

                HttpRequest req = newRequestBuilder(uri)
                        .POST(bodyPublisher)
                        .build();
                BodyHandler<String> handler = BodyHandlers.ofString();
                CompletableFuture<HttpResponse<String>> responseCF = client.sendAsync(req, handler);
                HttpResponse<String> response = responseCF.join();
                String body = response.body();
                if (!uri.contains(body)) {
                    err.println("Test failed: " + response);
                    throw new RuntimeException(uri + " doesn't contain '" + body + "'");
                } else {
                    out.println("Found expected " + body + " in " + uri);
                }
                assertEquals(response.version(), version(uri));
            }
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        out.println(now() + "begin setup");

        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        // HTTP/1.1
        HttpTestHandler h1_fixedLengthHandler = new HTTP_FixedLengthHandler();
        HttpTestHandler h1_chunkHandler = new HTTP_ChunkedHandler();
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(h1_fixedLengthHandler, "/http1/fixed");
        httpTestServer.addHandler(h1_chunkHandler, "/http1/chunk");
        httpURI_fixed = "http://" + httpTestServer.serverAuthority() + "/http1/fixed/x";
        httpURI_chunk = "http://" + httpTestServer.serverAuthority() + "/http1/chunk/x";

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(h1_fixedLengthHandler, "/https1/fixed");
        httpsTestServer.addHandler(h1_chunkHandler, "/https1/chunk");
        httpsURI_fixed = "https://" + httpsTestServer.serverAuthority() + "/https1/fixed/x";
        httpsURI_chunk = "https://" + httpsTestServer.serverAuthority() + "/https1/chunk/x";

        // HTTP/2
        HttpTestHandler h2_fixedLengthHandler = new HTTP_FixedLengthHandler();
        HttpTestHandler h2_chunkedHandler = new HTTP_ChunkedHandler();

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(h2_fixedLengthHandler, "/http2/fixed");
        http2TestServer.addHandler(h2_chunkedHandler, "/http2/chunk");
        http2URI_fixed = "http://" + http2TestServer.serverAuthority() + "/http2/fixed/x";
        http2URI_chunk = "http://" + http2TestServer.serverAuthority() + "/http2/chunk/x";

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(h2_fixedLengthHandler, "/https2/fixed");
        https2TestServer.addHandler(h2_chunkedHandler, "/https2/chunk");
        https2URI_fixed = "https://" + https2TestServer.serverAuthority() + "/https2/fixed/x";
        https2URI_chunk = "https://" + https2TestServer.serverAuthority() + "/https2/chunk/x";

        // DummyServer
        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        httpDummyServer = DummyServer.create(sa);
        httpsDummyServer = DummyServer.create(sa, sslContext);
        httpDummy = "http://" + httpDummyServer.serverAuthority() + "/http1/dummy/x";
        httpsDummy = "https://" + httpsDummyServer.serverAuthority() + "/https1/dummy/x";

        // HTTP/3
        HttpTestHandler h3_fixedLengthHandler = new HTTP_FixedLengthHandler();
        HttpTestHandler h3_chunkedHandler = new HTTP_ChunkedHandler();
        http3TestServer = HttpTestServer.create(HTTP_3, sslContext);
        http3TestServer.addHandler(h3_fixedLengthHandler, "/http3/fixed");
        http3TestServer.addHandler(h3_chunkedHandler, "/http3/chunk");
        http3TestServer.addHandler(new HttpHeadOrGetHandler(), "/http3/head");
        http3URI_fixed = "https://" + http3TestServer.serverAuthority() + "/http3/fixed/x";
        http3URI_chunk = "https://" + http3TestServer.serverAuthority() + "/http3/chunk/x";
        http3URI_head = "https://" + http3TestServer.serverAuthority() + "/http3/head/x";

        err.println(now() + "Starting servers");

        serverCount.addAndGet(7);
        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        httpDummyServer.start();
        httpsDummyServer.start();
        http3TestServer.start();

        out.println("HTTP/1.1 dummy server (http) listening at: " + httpDummyServer.serverAuthority());
        out.println("HTTP/1.1 dummy server (TLS)  listening at: " + httpsDummyServer.serverAuthority());
        out.println("HTTP/1.1 server       (http) listening at: " + httpTestServer.serverAuthority());
        out.println("HTTP/1.1 server       (TLS)  listening at: " + httpsTestServer.serverAuthority());
        out.println("HTTP/2   server       (h2c)  listening at: " + http2TestServer.serverAuthority());
        out.println("HTTP/2   server       (h2)   listening at: " + https2TestServer.serverAuthority());
        out.println("HTTP/3   server       (h2)   listening at: " + http3TestServer.serverAuthority());
        out.println(" + alt endpoint       (h3)   listening at: " + http3TestServer.getH3AltService()
                .map(Http3TestServer::getAddress));

        headRequest(newHttpClient(true));

        out.println(now() + "setup done");
        err.println(now() + "setup done");
    }

    @AfterTest
    public void teardown() throws Exception {
        sharedClient.close();
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
        http3TestServer.stop();
        httpDummyServer.stopServer();
        httpsDummyServer.stopServer();
    }

    static class HTTP_FixedLengthHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            out.println("HTTP_FixedLengthHandler received request to " + t.getRequestURI());
            byte[] req;
            try (InputStream is = t.getRequestBody()) {
                req = is.readAllBytes();
            }
            String uri = new String(req, UTF_8);
            byte[] resp = t.getRequestURI().toString().getBytes(UTF_8);
            if (!uri.contains(t.getRequestURI().toString())) {
                t.sendResponseHeaders(404, resp.length);
            } else {
                t.sendResponseHeaders(200, resp.length);  //fixed content length
            }
            try (OutputStream os = t.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    static class HTTP_ChunkedHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            out.println("HTTP_ChunkedHandler received request to " + t.getRequestURI());
            byte[] resp;
            try (InputStream is = t.getRequestBody()) {
                resp = is.readAllBytes();
            }
            t.sendResponseHeaders(200, -1); // chunked/variable
            try (OutputStream os = t.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    static class DummyServer extends Thread {
        final ServerSocket ss;
        final boolean secure;
        ConcurrentLinkedQueue<Socket> connections = new ConcurrentLinkedQueue<>();
        volatile boolean stopped;
        DummyServer(ServerSocket ss, boolean secure) {
            super("DummyServer[" + ss.getLocalPort()+"]");
            this.secure = secure;
            this.ss = ss;
        }

        // This is a bit shaky. It doesn't handle continuation
        // lines, but our client shouldn't send any.
        // Read a line from the input stream, swallowing the final
        // \r\n sequence. Stops at the first \n, doesn't complain
        // if it wasn't preceded by '\r'.
        //
        String readLine(InputStream r) throws IOException {
            StringBuilder b = new StringBuilder();
            int c;
            while ((c = r.read()) != -1) {
                if (c == '\n') break;
                b.appendCodePoint(c);
            }
            if (b.codePointAt(b.length() -1) == '\r') {
                b.delete(b.length() -1, b.length());
            }
            return b.toString();
        }

        @Override
        public void run() {
            try {
                while(!stopped) {
                    Socket clientConnection = ss.accept();
                    connections.add(clientConnection);
                    out.println(now() + getName() + ": Client accepted");
                    StringBuilder headers = new StringBuilder();
                    InputStream  ccis = clientConnection.getInputStream();
                    OutputStream ccos = clientConnection.getOutputStream();
                    out.println(now() + getName() + ": Reading request line");
                    String requestLine = readLine(ccis);
                    out.println(now() + getName() + ": Request line: " + requestLine);

                    StringTokenizer tokenizer = new StringTokenizer(requestLine);
                    String method = tokenizer.nextToken();
                    assert method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("GET");
                    String path = tokenizer.nextToken();
                    URI uri;
                    try {
                        String hostport = serverAuthority();
                        uri = new URI((secure ? "https" : "http") +"://" + hostport + path);
                    } catch (Throwable x) {
                        err.printf("Bad target address: \"%s\" in \"%s\"%n",
                                path, requestLine);
                        clientConnection.close();
                        continue;
                    }

                    // Read all headers until we find the empty line that
                    // signals the end of all headers.
                    String line = requestLine;
                    while (!line.equals("")) {
                        out.println(now() + getName() + ": Reading header: "
                                + (line = readLine(ccis)));
                        headers.append(line).append("\r\n");
                    }

                    StringBuilder response = new StringBuilder();

                    int index = headers.toString()
                            .toLowerCase(Locale.US)
                            .indexOf("content-length: ");
                    byte[] b = uri.toString().getBytes(UTF_8);
                    if (index >= 0) {
                        index = index + "content-length: ".length();
                        String cl = headers.toString().substring(index);
                        StringTokenizer tk = new StringTokenizer(cl);
                        int len = Integer.parseInt(tk.nextToken());
                        assert len < b.length * 2;
                        out.println(now() + getName()
                                + ": received body: "
                                + new String(ccis.readNBytes(len), UTF_8));
                    }
                    out.println(now()
                            + getName() + ": sending back " + uri);

                    response.append("HTTP/1.1 200 OK\r\nContent-Length: ")
                            .append(b.length)
                            .append("\r\n\r\n");

                    // Then send the 200 OK response to the client
                    out.println(now() + getName() + ": Sending "
                            + response);
                    ccos.write(response.toString().getBytes(UTF_8));
                    ccos.flush();
                    out.println(now() + getName() + ": sent response headers");
                    ccos.write(b);
                    ccos.flush();
                    ccos.close();
                    out.println(now() + getName() + ": sent " + b.length + " body bytes");
                    connections.remove(clientConnection);
                    clientConnection.close();
                }
            } catch (Throwable t) {
                if (!stopped) {
                    out.println(now() + getName() + ": failed: " + t);
                    t.printStackTrace();
                    try {
                        stopServer();
                    } catch (Throwable e) {

                    }
                }
            } finally {
                out.println(now() + getName() + ": exiting");
            }
        }

        void close(Socket s) {
            try {
                s.close();
            } catch(Throwable t) {

            }
        }
        public void stopServer() throws IOException {
            stopped = true;
            ss.close();
            connections.forEach(this::close);
        }

        public String serverAuthority() {
            return InetAddress.getLoopbackAddress().getHostName() + ":"
                    + ss.getLocalPort();
        }

        static DummyServer create(InetSocketAddress sa) throws IOException {
            ServerSocket ss = ServerSocketFactory.getDefault()
                    .createServerSocket(sa.getPort(), -1, sa.getAddress());
            return  new DummyServer(ss, false);
        }

        static DummyServer create(InetSocketAddress sa, SSLContext sslContext) throws IOException {
            ServerSocket ss = sslContext.getServerSocketFactory()
                    .createServerSocket(sa.getPort(), -1, sa.getAddress());
            return new DummyServer(ss, true);
        }

    }

}
