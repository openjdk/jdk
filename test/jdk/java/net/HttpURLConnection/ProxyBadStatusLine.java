/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.List;

import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static java.net.Proxy.Type.HTTP;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.assertThrows;

/* @test
 * @bug 8373778
 * @summary Verify that a IOException gets thrown from HttpURLConnection, if the proxy returns
 *          an invalid status line in response to a CONNECT request
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder
 * @run junit ${test.main.class}
 */
class ProxyBadStatusLine {

    static List<Arguments> badStatusLines() {
        return List.of(
                Arguments.of("", "Unexpected end of file from server"),
                Arguments.of(" ", "Unexpected end of file from server"),
                Arguments.of("\t", "Unexpected end of file from server"),
                Arguments.of("\r\n", "Unexpected end of file from server"),

                Arguments.of("HTTP/1.", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.0", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1     ", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1\r\n", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1\n", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1 301 ", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1 404 ", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1 503 ", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1\n200 ", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1\r200 ", "Unable to tunnel through proxy"),
                Arguments.of("HTTP/1.1\f200 ", "Unable to tunnel through proxy")
        );
    }

    /*
     * Uses HttpURLConnection to initiate a HTTP request that results in a CONNECT
     * request to a proxy server. The proxy server then responds with a bad status line.
     * The test expects that an IOException gets thrown back to the application (instead
     * of some unspecified exception).
     */
    @ParameterizedTest
    @MethodSource(value = "badStatusLines")
    void testProxyConnectResponse(final String badStatusLine, final String expectedExceptionMsg)
            throws Exception {
        final InetSocketAddress irrelevantTargetServerAddr =
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345);
        final URL url = URIBuilder.newBuilder()
                .scheme("https")
                .host(irrelevantTargetServerAddr.getAddress())
                .port(irrelevantTargetServerAddr.getPort())
                .path("/doesnotmatter")
                .build().toURL();

        Thread proxyServerThread = null;
        try (final BadProxyServer proxy = new BadProxyServer(badStatusLine)) {

            proxyServerThread = Thread.ofPlatform().name("proxy-server").start(proxy);
            final HttpURLConnection urlc = (HttpURLConnection)
                    url.openConnection(new Proxy(HTTP, proxy.getAddress()));

            final IOException ioe = assertThrows(IOException.class, () -> urlc.getInputStream());
            final String exMsg = ioe.getMessage();
            if (exMsg == null || !exMsg.contains(expectedExceptionMsg)) {
                // unexpected message in the exception, propagate the exception
                throw ioe;
            }
            System.err.println("got excepted exception: " + ioe);
        } finally {
            if (proxyServerThread != null) {
                System.err.println("waiting for proxy server thread to complete");
                proxyServerThread.join();
            }
        }
    }

    private static final class BadProxyServer implements Runnable, AutoCloseable {
        private static final int CR = '\r';
        private static final int LF = '\n';

        private final ServerSocket serverSocket;
        private final String connectRespStatusLine;
        private volatile boolean closed;

        /**
         *
         * @param connectRespStatusLine the status line that this server writes
         *                              out in response to a CONNECT request
         * @throws IOException
         */
        BadProxyServer(final String connectRespStatusLine) throws IOException {
            this.connectRespStatusLine = connectRespStatusLine;
            final int port = 0;
            final int backlog = 0;
            this.serverSocket = new ServerSocket(port, backlog, InetAddress.getLoopbackAddress());
        }

        InetSocketAddress getAddress() {
            return (InetSocketAddress) this.serverSocket.getLocalSocketAddress();
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            synchronized (this) {
                if (this.closed) {
                    return;
                }
                this.closed = true;
            }
            try {
                this.serverSocket.close();
            } catch (IOException e) {
                System.err.println("failed to close proxy server: " + e);
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                doRun();
            } catch (Throwable t) {
                if (!closed) {
                    System.err.println("Proxy server ran into exception: " + t);
                    t.printStackTrace();
                }
            }
            System.err.println("Proxy server " + this.serverSocket + " exiting");
        }

        private void doRun() throws IOException {
            while (!closed) {
                System.err.println("waiting for incoming connection at " + this.serverSocket);
                try (final Socket accepted = this.serverSocket.accept()) {
                    System.err.println("accepted incoming connection from " + accepted);
                    handleIncomingConnection(accepted);
                }
            }
        }

        private static int findCRLF(final byte[] b) {
            for (int i = 0; i < b.length - 1; i++) {
                if (b[i] == CR && b[i + 1] == LF) {
                    return i;
                }
            }
            return -1;
        }

        // writes out a status line in response to a CONNECT request
        private void handleIncomingConnection(final Socket acceptedSocket) throws IOException {
            final byte[] req = readRequest(acceptedSocket.getInputStream());
            final int crlfIndex = findCRLF(req);
            if (crlfIndex < 0) {
                System.err.println("unexpected request content from " + acceptedSocket);
                // nothing to process, ignore this connection
                return;
            }
            final String requestLine = new String(req, 0, crlfIndex, ISO_8859_1);
            System.err.println("received request line: \"" + requestLine + "\"");
            final String[] parts = requestLine.split(" ");
            if (parts[0].equals("CONNECT")) {
                // reply back with the status line
                try (final OutputStream os = acceptedSocket.getOutputStream()) {
                    System.err.println("responding to CONNECT request from " + acceptedSocket
                            + ", response status line: \"" + connectRespStatusLine + "\"");
                    final byte[] statusLine = connectRespStatusLine.getBytes(ISO_8859_1);
                    os.write(statusLine);
                }
            } else {
                System.err.println("unexpected request from " + acceptedSocket + ": \"" + requestLine + "\"");
                return;
            }
        }

        private static byte[] readRequest(InputStream is) throws IOException {
            // we don't expect the HTTP request body in this test to be larger than this size
            final byte[] buff = new byte[4096];
            int crlfcount = 0;
            int numRead = 0;
            int c;
            while ((c = is.read()) != -1 && numRead < buff.length) {
                buff[numRead++] = (byte) c;
                //
                // HTTP-message = start-line CRLF
                //               *( field-line CRLF )
                //               CRLF
                //               [ message-body ]
                //
                // start-line = request-line / status-line
                //
                // we are not interested in the message body, so this loop is
                // looking for the CRLFCRLF sequence to stop parsing the request
                // content
                if (c == CR || c == LF) {
                    switch (crlfcount) {
                        case 0, 2 -> {
                            if (c == CR) {
                                crlfcount++;
                            }
                        }
                        case 1, 3 -> {
                            if (c == LF) {
                                crlfcount++;
                            }
                        }
                    }
                } else {
                    crlfcount = 0;
                }
                if (crlfcount == 4) {
                    break;
                }
            }
            if (crlfcount != 4) {
                throw new IOException("Could not locate a CRLFCRLF sequence in the request");
            }
            final byte[] request = new byte[numRead];
            System.arraycopy(buff, 0, request, 0, numRead);
            return request;
        }
    }
}
