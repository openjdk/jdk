/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8270290
 * @library /test/lib
 * @run main/othervm NTLMHeadTest SERVER
 * @run main/othervm NTLMHeadTest PROXY
 * @run main/othervm NTLMHeadTest TUNNEL
 * @summary test for the incorrect logic in reading (and discarding) HTTP
 *      response body when processing NTLMSSP_CHALLENGE response
 *      (to CONNECT request) from proxy server. When this response is received
 *      by client, reset() is called on the connection to read and discard the
 *      response body. This code path was broken when initial client request
 *      uses HEAD method and HTTPS resource, in this case CONNECT is sent to
 *      proxy server (to establish TLS tunnel) and response body is not read
 *      from a socket (because initial method on client connection is HEAD).
 *      This does not cause problems with the majority of proxy servers because
 *      InputStream opened over the response socket is buffered with 8kb buffer
 *      size. Problem is only reproducible if the response size (headers +
 *      body) is larger than 8kb. The code path with HTTPS tunneling is checked
 *      with TUNNEL argument. Additional checks for HEAD handling are included
 *      for direct server (SERVER) and HTTP proxying (PROXY) code paths, in
 *      these (non-tunnel) cases client must NOT attempt to read response data
 *      (to not block on socket read) because HEAD is sent to server and
 *      NTLMSSP_CHALLENGE response includes Content-Length, but does not
 *      include the body.
 */

import java.net.*;
import java.io.*;
import java.util.*;

import jdk.test.lib.net.HttpHeaderParser;
import jdk.test.lib.net.URIBuilder;

public class NTLMHeadTest {

    enum Mode { SERVER, PROXY, TUNNEL }

    static final int BODY_LEN = 8192;

    static final String RESP_SERVER_AUTH =
            "HTTP/1.1 401 Unauthorized\r\n" +
            "WWW-Authenticate: NTLM\r\n" +
            "Connection: close\r\n" +
            "Content-Length: " + BODY_LEN + "\r\n" +
            "\r\n";

    static final String RESP_SERVER_NTLM =
            "HTTP/1.1 401 Unauthorized\r\n" +
            "WWW-Authenticate: NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==\r\n" +
            "Connection: Keep-Alive\r\n" +
            "Content-Length: " + BODY_LEN + "\r\n" +
            "\r\n";

    static final String RESP_SERVER_OR_PROXY_DEST =
            "HTTP/1.1 200 OK\r\n" +
            "Connection: close\r\n" +
            "Content-Length: 42\r\n" +
            "\r\n";

    static final String RESP_PROXY_AUTH =
            "HTTP/1.1 407 Proxy Authentication Required\r\n" +
            "Proxy-Authenticate: NTLM\r\n" +
            "Proxy-Connection: close\r\n" +
            "Connection: close\r\n" +
            "Content-Length: " + BODY_LEN + "\r\n" +
            "\r\n";

    static final String RESP_PROXY_NTLM =
            "HTTP/1.1 407 Proxy Authentication Required\r\n" +
            "Proxy-Authenticate: NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==\r\n" +
            "Proxy-Connection: Keep-Alive\r\n" +
            "Connection: Keep-Alive\r\n" +
            "Content-Length: " + BODY_LEN + "\r\n" +
            "\r\n";

    static final String RESP_TUNNEL_AUTH =
            "HTTP/1.1 407 Proxy Authentication Required\r\n" +
            "Proxy-Authenticate: NTLM\r\n" +
            "Proxy-Connection: close\r\n" +
            "Connection: close\r\n" +
            "Content-Length: " + BODY_LEN + "\r\n" +
            "\r\n" +
            generateBody(BODY_LEN);

    static final String RESP_TUNNEL_NTLM =
            "HTTP/1.1 407 Proxy Authentication Required\r\n" +
            "Proxy-Authenticate: NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==\r\n" +
            "Proxy-Connection: Keep-Alive\r\n" +
            "Connection: Keep-Alive\r\n" +
            "Content-Length: " + BODY_LEN + "\r\n" +
            "\r\n" +
            generateBody(BODY_LEN);

    static final String RESP_TUNNEL_ESTABLISHED =
            "HTTP/1.1 200 Connection Established\r\n\r\n";

    public static void main(String[] args) throws Exception {
        Authenticator.setDefault(new TestAuthenticator());
        if (1 != args.length) {
            throw new IllegalArgumentException("Mode value must be specified, one of: [SERVER, PROXY, TUNNEL]");
        }
        Mode mode = Mode.valueOf(args[0]);
        System.out.println("Running with mode: " + mode);
        switch (mode) {
            case SERVER: testSever(); return;
            case PROXY: testProxy(); return;
            case TUNNEL: testTunnel(); return;
            default: throw new IllegalArgumentException("Invalid mode: " + mode);
        }
    }

    static void testSever() throws Exception {
        try (NTLMServer server = startServer(new ServerSocket(0, 0, InetAddress.getLoopbackAddress()), Mode.SERVER)) {
            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(server.getLocalPort())
                    .path("/")
                    .toURLUnchecked();
            HttpURLConnection uc = (HttpURLConnection) url.openConnection();
            uc.setRequestMethod("HEAD");
            uc.getInputStream().readAllBytes();
        }
    }

    static void testProxy() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (NTLMServer server = startServer(new ServerSocket(0, 0, loopback), Mode.PROXY)) {
            SocketAddress proxyAddr = new InetSocketAddress(loopback, server.getLocalPort());
            Proxy proxy = new Proxy(java.net.Proxy.Type.HTTP, proxyAddr);
            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(8080)
                    .path("/")
                    .toURLUnchecked();
            HttpURLConnection uc = (HttpURLConnection) url.openConnection(proxy);
            uc.setRequestMethod("HEAD");
            uc.getInputStream().readAllBytes();
        }
    }

    static void testTunnel() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (NTLMServer server = startServer(new ServerSocket(0, 0, loopback), Mode.TUNNEL)) {
            SocketAddress proxyAddr = new InetSocketAddress(loopback, server.getLocalPort());
            Proxy proxy = new Proxy(java.net.Proxy.Type.HTTP, proxyAddr);
            URL url = URIBuilder.newBuilder()
                    .scheme("https")
                    .loopback()
                    .port(8443)
                    .path("/")
                    .toURLUnchecked();
            HttpURLConnection uc = (HttpURLConnection) url.openConnection(proxy);
            uc.setRequestMethod("HEAD");
            try {
                uc.getInputStream().readAllBytes();
            } catch (IOException e) {
                // can be SocketException or SSLHandshakeException
                // Tunnel established and closed by server
                System.out.println("Tunnel established successfully");
            } catch (NoSuchElementException e) {
                System.err.println("Error: cannot read 200 response code");
                throw e;
            }
        }
    }

    static class NTLMServer extends Thread implements AutoCloseable {
        final ServerSocket ss;
        final Mode mode;
        volatile boolean closed;

        NTLMServer(ServerSocket serverSS, Mode mode) {
            super();
            setDaemon(true);
            this.ss = serverSS;
            this.mode = mode;
        }

        int getLocalPort() { return ss.getLocalPort(); }

        @Override
        public void run() {
            boolean doing2ndStageNTLM = false;
            while (!closed) {
                try {
                    Socket s = ss.accept();
                    InputStream is = s.getInputStream();
                    OutputStream os = s.getOutputStream();
                    switch(mode) {
                        case SERVER:
                            doServer(is, os, doing2ndStageNTLM);
                            break;
                        case PROXY:
                            doProxy(is, os, doing2ndStageNTLM);
                            break;
                        case TUNNEL:
                            doTunnel(is, os, doing2ndStageNTLM);
                            break;
                        default: throw new IllegalArgumentException();
                    }
                    if (!doing2ndStageNTLM) {
                        doing2ndStageNTLM = true;
                    } else {
                        os.close();
                    }
                } catch (IOException ioe) {
                    if (!closed) {
                        ioe.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void close() {
           if (closed) return;
           synchronized(this) {
               if (closed) return;
               closed = true;
           }
           try { ss.close(); } catch (IOException x) { };
        }
    }

    static NTLMServer startServer(ServerSocket serverSS, Mode mode) {
        NTLMServer server = new NTLMServer(serverSS, mode);
        server.start();
        return server;
    }

    static String generateBody(int length) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < length; i++) {
            sb.append(i % 10);
        }
        return sb.toString();
    }

    static void doServer(InputStream is, OutputStream os, boolean doing2ndStageNTLM) throws IOException {
        if (!doing2ndStageNTLM) {
            new HttpHeaderParser(is);
            os.write(RESP_SERVER_AUTH.getBytes("ASCII"));
        } else {
            new HttpHeaderParser(is);
            os.write(RESP_SERVER_NTLM.getBytes("ASCII"));
            new HttpHeaderParser(is);
            os.write(RESP_SERVER_OR_PROXY_DEST.getBytes("ASCII"));
        }
    }

    static void doProxy(InputStream is, OutputStream os, boolean doing2ndStageNTLM) throws IOException {
        if (!doing2ndStageNTLM) {
            new HttpHeaderParser(is);
            os.write(RESP_PROXY_AUTH.getBytes("ASCII"));
        } else {
            new HttpHeaderParser(is);
            os.write(RESP_PROXY_NTLM.getBytes("ASCII"));
            new HttpHeaderParser(is);
            os.write(RESP_SERVER_OR_PROXY_DEST.getBytes("ASCII"));
        }
    }

    static void doTunnel(InputStream is, OutputStream os, boolean doing2ndStageNTLM) throws IOException {
        if (!doing2ndStageNTLM) {
            new HttpHeaderParser(is);
            os.write(RESP_TUNNEL_AUTH.getBytes("ASCII"));
        } else {
            new HttpHeaderParser(is);
            os.write(RESP_TUNNEL_NTLM.getBytes("ASCII"));
            new HttpHeaderParser(is);
            os.write(RESP_TUNNEL_ESTABLISHED.getBytes("ASCII"));
        }
    }

    static class TestAuthenticator extends java.net.Authenticator {
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication("test", "secret".toCharArray());
        }
    }
}
