/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.UnsupportedProtocolVersionException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;


/*
 * @test
 * @summary  Verifies that HTTP_3 is downgraded to HTTP_2 in case of a proxy, except
 *           if HTTP3_ONLY is specified
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run main/othervm H3ProxyTest
 * @author danielfuchs
 */
public class H3ProxyTest implements HttpServerAdapters {

    static {
        try {
            SSLContext.setDefault(new SimpleSSLContext().get());
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static final String RESPONSE = "<html><body><p>Hello World!</body></html>";
    static final String PATH = "/foo/";

    static HttpTestServer createHttps2Server() throws Exception {
        HttpTestServer server = HttpTestServer.create(ANY, SSLContext.getDefault());
        server.addHandler(he -> {
            he.getResponseHeaders().addHeader("encoding", "UTF-8");
            he.sendResponseHeaders(200, RESPONSE.length());
            if (!he.getRequestMethod().equals("HEAD")) {
                he.getResponseBody().write(RESPONSE.getBytes(StandardCharsets.UTF_8));
            }
            he.close();
        }, PATH);

        return server;
    }

    static HttpTestServer createHttp3Server() throws Exception {
        HttpTestServer server = HttpTestServer.create(HTTP_3_URI_ONLY, SSLContext.getDefault());
        server.addHandler(he -> {
            he.getResponseHeaders().addHeader("encoding", "UTF-8");
            he.sendResponseHeaders(200, RESPONSE.length());
            if (!he.getRequestMethod().equals("HEAD")) {
                he.getResponseBody().write(RESPONSE.getBytes(StandardCharsets.UTF_8));
            }
            he.close();
        }, PATH);

        return server;
    }

    public static void main(String[] args)
            throws Exception
    {
        HttpTestServer server = createHttps2Server();
        HttpTestServer server3 = createHttp3Server();
        server.start();
        server3.start();
        try {
            if (server.supportsH3DirectConnection()) {
                test(server, ANY);
                try {
                    test(server, HTTP_3_URI_ONLY);
                    throw new AssertionError("expected UnsupportedProtocolVersionException not raised");
                } catch (UnsupportedProtocolVersionException upve) {
                    System.out.printf("%nGot expected exception: %s%n%n", upve);
                }
            }
            test(server, ALT_SVC);
            try {
                test(server3, HTTP_3_URI_ONLY);
                throw new AssertionError("expected UnsupportedProtocolVersionException not raised");
            } catch (UnsupportedProtocolVersionException upve) {
                System.out.printf("%nGot expected exception: %s%n%n", upve);
            }
        } finally {
            server.stop();
            server3.stop();
            System.out.println("Server stopped");
        }
    }

    public static void test(HttpTestServer server,
                            Http3DiscoveryMode config)
            throws Exception
    {
        System.out.println("""

                # --------------------------------------------------
                # Server is %s
                # Config is %s
                # --------------------------------------------------
                """.formatted(server.getAddress(), config));

        URI uri = new URI("https://" + server.serverAuthority() + PATH + "x");
        TunnelingProxy proxy = new TunnelingProxy(server);
        proxy.start();
        try {
            System.out.println("Proxy started");
            System.out.println("\nSetting up request with HttpClient for version: "
                    + config.name() + " URI=" + uri);
            ProxySelector ps = ProxySelector.of(
                    InetSocketAddress.createUnresolved("localhost", proxy.getAddress().getPort()));
            HttpClient client = HttpServerAdapters.createClientBuilderForH3()
                    .version(Version.HTTP_3)
                    .sslContext(new SimpleSSLContext().get())
                    .proxy(ps)
                    .build();
            try (client) {
                if (config == ALT_SVC) {
                    System.out.println("\nSending HEAD request to preload AltServiceRegistry");
                    HttpRequest head = HttpRequest.newBuilder()
                            .uri(uri)
                            .HEAD()
                            .version(Version.HTTP_2)
                            .build();
                    var headResponse = client.send(head, BodyHandlers.ofString());
                    System.out.println("Got head response: " + headResponse);
                    if (headResponse.statusCode() != 200) {
                        throw new AssertionError("bad status code: " + headResponse);
                    }
                    if (!headResponse.version().equals(Version.HTTP_2)) {
                        throw new AssertionError("bad protocol version: " + headResponse.version());
                    }

                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .GET()
                        .version(Version.HTTP_3)
                        .setOption(H3_DISCOVERY, config)
                        .build();

                System.out.println("\nSending request with HttpClient: " + config);
                HttpResponse<String> response
                        = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("Got response");
                if (response.statusCode() != 200) {
                    throw new AssertionError("bad status code: " + response);
                }
                if (!response.version().equals(Version.HTTP_2)) {
                    throw new AssertionError("bad protocol version: " + response.version());
                }

                String resp = response.body();
                System.out.println("Received: " + resp);
                if (!RESPONSE.equals(resp)) {
                    throw new AssertionError("Unexpected response");
                }
            }
        } catch (Throwable t) {
            System.out.println("Error: " + t);
            throw t;
        } finally {
            System.out.println("Stopping proxy");
            proxy.stop();
            System.out.println("Proxy stopped");
        }
    }

    static class TunnelingProxy {
        final Thread accept;
        final ServerSocket ss;
        final boolean DEBUG = false;
        final HttpTestServer serverImpl;
        final CopyOnWriteArrayList<CompletableFuture<Void>> connectionCFs
                = new CopyOnWriteArrayList<>();
        private volatile boolean stopped;
        TunnelingProxy(HttpTestServer serverImpl) throws IOException {
            this.serverImpl = serverImpl;
            ss = new ServerSocket();
            accept = new Thread(this::accept);
            accept.setDaemon(true);
        }

        void start() throws IOException {
            ss.setReuseAddress(false);
            ss.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            accept.start();
        }

        // Pipe the input stream to the output stream.
        private synchronized Thread pipe(InputStream is, OutputStream os,
                                         char tag, CompletableFuture<Void> end) {
            return new Thread("TunnelPipe("+tag+")") {
                @Override
                public void run() {
                    try {
                        try (os) {
                            int c;
                            while ((c = is.read()) != -1) {
                                os.write(c);
                                os.flush();
                                // if DEBUG prints a + or a - for each transferred
                                // character.
                                if (DEBUG) System.out.print(tag);
                            }
                            is.close();
                        }
                    } catch (IOException ex) {
                        if (DEBUG) ex.printStackTrace(System.out);
                    } finally {
                        end.complete(null);
                    }
                }
            };
        }

        public InetSocketAddress getAddress() {
            return new InetSocketAddress( InetAddress.getLoopbackAddress(), ss.getLocalPort());
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

        public void accept() {
            Socket clientConnection;
            try {
                while (!stopped) {
                    System.out.println("Tunnel: Waiting for client");
                    Socket toClose;
                    try {
                        toClose = clientConnection = ss.accept();
                    } catch (IOException io) {
                        if (DEBUG) io.printStackTrace(System.out);
                        break;
                    }
                    System.out.println("Tunnel: Client accepted");
                    Socket targetConnection;
                    InputStream  ccis = clientConnection.getInputStream();
                    OutputStream ccos = clientConnection.getOutputStream();
                    Writer w = new OutputStreamWriter(ccos, StandardCharsets.UTF_8);
                    PrintWriter pw = new PrintWriter(w);
                    System.out.println("Tunnel: Reading request line");
                    String requestLine = readLine(ccis);
                    System.out.println("Tunnel: Request status line: " + requestLine);
                    if (requestLine.startsWith("CONNECT ")) {
                        // We should probably check that the next word following
                        // CONNECT is the host:port of our HTTPS serverImpl.
                        // Some improvement for a followup!

                        // Read all headers until we find the empty line that
                        // signals the end of all headers.
                        while(!requestLine.equals("")) {
                            System.out.println("Tunnel: Reading header: "
                                    + (requestLine = readLine(ccis)));
                        }

                        // Open target connection
                        targetConnection = new Socket(
                                InetAddress.getLoopbackAddress(),
                                serverImpl.getAddress().getPort());

                        // Then send the 200 OK response to the client
                        System.out.println("Tunnel: Sending "
                                + "HTTP/1.1 200 OK\r\n\r\n");
                        pw.print("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
                        pw.flush();
                    } else {
                        // This should not happen. If it does then just print an
                        // error - both on out and err, and close the accepted
                        // socket
                        System.out.println("WARNING: Tunnel: Unexpected status line: "
                                + requestLine + " received by "
                                + ss.getLocalSocketAddress()
                                + " from "
                                + toClose.getRemoteSocketAddress()
                                + " - closing accepted socket");
                        // Print on err
                        System.err.println("WARNING: Tunnel: Unexpected status line: "
                                + requestLine + " received by "
                                + ss.getLocalSocketAddress()
                                + " from "
                                + toClose.getRemoteSocketAddress());
                        // close accepted socket.
                        toClose.close();
                        System.err.println("Tunnel: accepted socket closed.");
                        continue;
                    }

                    // Pipe the input stream of the client connection to the
                    // output stream of the target connection and conversely.
                    // Now the client and target will just talk to each other.
                    System.out.println("Tunnel: Starting tunnel pipes");
                    CompletableFuture<Void> end, end1, end2;
                    Thread t1 = pipe(ccis, targetConnection.getOutputStream(), '+',
                            end1 = new CompletableFuture<>());
                    Thread t2 = pipe(targetConnection.getInputStream(), ccos, '-',
                            end2 = new CompletableFuture<>());
                    end = CompletableFuture.allOf(end1, end2);
                    end.whenComplete(
                            (r,t) -> {
                                try { toClose.close(); } catch (IOException x) { }
                                finally {connectionCFs.remove(end);}
                            });
                    connectionCFs.add(end);
                    t1.start();
                    t2.start();
                }
            } catch (Throwable ex) {
                try {
                    ss.close();
                } catch (IOException ex1) {
                    ex.addSuppressed(ex1);
                }
                ex.printStackTrace(System.err);
            } finally {
                System.out.println("Tunnel: exiting (stopped=" + stopped + ")");
                connectionCFs.forEach(cf -> cf.complete(null));
            }
        }

        public void stop() throws IOException {
            stopped = true;
            ss.close();
            try {
                if (accept != Thread.currentThread()) accept.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

    }

}
