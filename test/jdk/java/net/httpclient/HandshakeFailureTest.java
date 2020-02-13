/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.System.out;
import static java.net.http.HttpResponse.BodyHandlers.discarding;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/**
 * @test
 * @run main/othervm -Djdk.internal.httpclient.debug=false HandshakeFailureTest TLSv1.2
 * @run main/othervm -Djdk.internal.httpclient.debug=false HandshakeFailureTest TLSv1.3
 * @summary Verify SSLHandshakeException is received when the handshake fails,
 * either because the server closes (EOF) the connection during handshaking,
 * or no cipher suite can be negotiated (TLSv1.2) or no available authentication
 * scheme (TLSv1.3).
 */
// To switch on debugging use:
// @run main/othervm -Djdk.internal.httpclient.debug=true HandshakeFailureTest
public class HandshakeFailureTest {

    // The number of iterations each testXXXClient performs. Can be increased
    // when running standalone testing.
    static final int TIMES = 10;

    private static String tlsProtocol;

    public static void main(String[] args) throws Exception {
        tlsProtocol = args[0];

        HandshakeFailureTest test = new HandshakeFailureTest();
        List<AbstractServer> servers = List.of(new PlainServer(), new SSLServer());

        for (AbstractServer server : servers) {
            try (server) {
                out.format("%n%n------ Testing with server:%s ------%n", server);
                URI uri = new URI("https://localhost:" + server.getPort() + "/");

                test.testSyncSameClient(uri, Version.HTTP_1_1);
                test.testSyncSameClient(uri, Version.HTTP_2);
                test.testSyncDiffClient(uri, Version.HTTP_1_1);
                test.testSyncDiffClient(uri, Version.HTTP_2);

                test.testAsyncSameClient(uri, Version.HTTP_1_1);
                test.testAsyncSameClient(uri, Version.HTTP_2);
                test.testAsyncDiffClient(uri, Version.HTTP_1_1);
                test.testAsyncDiffClient(uri, Version.HTTP_2);
            }
        }
    }

    static HttpClient getClient() {
        SSLParameters params = new SSLParameters();
        params.setProtocols(new String[] { tlsProtocol });
        return HttpClient.newBuilder()
                .sslParameters(params)
                .build();
    }

    void testSyncSameClient(URI uri, Version version) throws Exception {
        out.printf("%n--- testSyncSameClient %s ---%n", version);
        HttpClient client = getClient();
        for (int i = 0; i < TIMES; i++) {
            out.printf("iteration %d%n", i);
            HttpRequest request = HttpRequest.newBuilder(uri)
                                             .version(version)
                                             .build();
            try {
                HttpResponse<Void> response = client.send(request, discarding());
                String msg = String.format("UNEXPECTED response=%s%n", response);
                throw new RuntimeException(msg);
            } catch (IOException expected) {
                out.printf("Client: caught expected exception: %s%n", expected);
                checkExceptionOrCause(expected);
            }
        }
    }

    void testSyncDiffClient(URI uri, Version version) throws Exception {
        out.printf("%n--- testSyncDiffClient %s ---%n", version);
        for (int i = 0; i < TIMES; i++) {
            out.printf("iteration %d%n", i);
            // a new client each time
            HttpClient client = getClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                                             .version(version)
                                             .build();
            try {
                HttpResponse<Void> response = client.send(request, discarding());
                String msg = String.format("UNEXPECTED response=%s%n", response);
                throw new RuntimeException(msg);
            } catch (IOException expected) {
                out.printf("Client: caught expected exception: %s%n", expected);
                checkExceptionOrCause(expected);
            }
        }
    }

    void testAsyncSameClient(URI uri, Version version) throws Exception {
        out.printf("%n--- testAsyncSameClient %s ---%n", version);
        HttpClient client = getClient();
        for (int i = 0; i < TIMES; i++) {
            out.printf("iteration %d%n", i);
            HttpRequest request = HttpRequest.newBuilder(uri)
                                             .version(version)
                                             .build();
            CompletableFuture<HttpResponse<Void>> response =
                        client.sendAsync(request, discarding());
            try {
                response.join();
                String msg = String.format("UNEXPECTED response=%s%n", response);
                throw new RuntimeException(msg);
            } catch (CompletionException ce) {
                Throwable expected = ce.getCause();
                out.printf("Client: caught expected exception: %s%n", expected);
                checkExceptionOrCause(expected);
            }
        }
    }

    void testAsyncDiffClient(URI uri, Version version) throws Exception {
        out.printf("%n--- testAsyncDiffClient %s ---%n", version);
        for (int i = 0; i < TIMES; i++) {
            out.printf("iteration %d%n", i);
            // a new client each time
            HttpClient client = getClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                                             .version(version)
                                             .build();
            CompletableFuture<HttpResponse<Void>> response =
                    client.sendAsync(request, discarding());
            try {
                response.join();
                String msg = String.format("UNEXPECTED response=%s%n", response);
                throw new RuntimeException(msg);
            } catch (CompletionException ce) {
                ce.printStackTrace(out);
                Throwable expected = ce.getCause();
                out.printf("Client: caught expected exception: %s%n", expected);
                checkExceptionOrCause(expected);
            }
        }
    }

    static void checkExceptionOrCause(Throwable t) {
        final Throwable original = t;
        do {
            if (SSLHandshakeException.class.isInstance(t)
                    // For TLSv1.3, possibly the server is (being) closed when
                    // the client read the input alert. In this case, the client
                    // just gets SocketException instead of SSLHandshakeException.
                    || (tlsProtocol.equalsIgnoreCase("TLSv1.3")
                            && SocketException.class.isInstance(t))) {
                System.out.println("Found expected exception/cause: " + t);
                return; // found
            }
        } while ((t = t.getCause()) != null);
        original.printStackTrace(System.out);
        throw new RuntimeException(
                "Not found expected SSLHandshakeException or SocketException in "
                        + original);
    }

    /** Common super type for PlainServer and SSLServer. */
    static abstract class AbstractServer extends Thread implements AutoCloseable {
        protected final ServerSocket ss;
        protected volatile boolean closed;

        AbstractServer(String name, ServerSocket ss) throws IOException {
            super(name);
            ss.setReuseAddress(false);
            ss.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            this.ss = ss;
            this.start();
        }

        int getPort() { return ss.getLocalPort(); }

        @Override
        public void close() {
            if (closed)
                return;
            closed = true;
            try {
                ss.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Unexpected", e);
            }
        }
    }

    /** Emulates a server-side, using plain cleartext Sockets, that just closes
     * the connection, after a small variable delay. */
    static class PlainServer extends AbstractServer {
        private volatile int count;

        PlainServer() throws IOException {
            super("PlainServer", new ServerSocket());
        }

        @Override
        public void run() {
            while (!closed) {
                try (Socket s = ss.accept()) {
                    count++;

                    /*   SSL record layer - contains the client hello
                    struct {
                        uint8 major, minor;
                    } ProtocolVersion;

                    enum {
                        change_cipher_spec(20), alert(21), handshake(22),
                        application_data(23), (255)
                    } ContentType;

                    struct {
                        ContentType type;
                        ProtocolVersion version;
                        uint16 length;
                        opaque fragment[SSLPlaintext.length];
                    } SSLPlaintext;   */
                    DataInputStream din =  new DataInputStream(s.getInputStream());
                    int contentType = din.read();
                    out.println("ContentType:" + contentType);
                    int majorVersion = din.read();
                    out.println("Major:" + majorVersion);
                    int minorVersion = din.read();
                    out.println("Minor:" + minorVersion);
                    int length = din.readShort();
                    out.println("length:" + length);
                    byte[] ba = new byte[length];
                    din.readFully(ba);

                    // simulate various delays in response
                    Thread.sleep(10 * (count % 10));
                    s.close(); // close without giving any reply
                } catch (IOException e) {
                    if (!closed)
                        out.println("Unexpected" + e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /** Emulates a server-side, using SSL Sockets, that will fail during
     * handshaking, as there are no cipher suites in common (TLSv1.2)
     * or no available authentication scheme (TLSv1.3). */
    static class SSLServer extends AbstractServer {
        static final SSLContext sslContext = createUntrustingContext();
        static final ServerSocketFactory factory = sslContext.getServerSocketFactory();

        static SSLContext createUntrustingContext() {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, null, null);
                return sslContext;
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }

        SSLServer() throws IOException {
            super("SSLServer", factory.createServerSocket());
        }

        @Override
        public void run() {
            while (!closed) {
                try (SSLSocket s = (SSLSocket)ss.accept()) {
                    s.getInputStream().read();  // will throw SHE here

                    throw new AssertionError("Should not reach here");
                } catch (SSLHandshakeException expected) {
                    // Expected: SSLHandshakeException: no cipher suites in common (TLSv1.2)
                    // or no available authentication scheme (TLSv1.3)
                    out.printf("Server: caught expected exception: %s%n", expected);
                } catch (IOException e) {
                    if (!closed)
                        out.printf("UNEXPECTED %s", e);
                }
            }
        }
    }
}
