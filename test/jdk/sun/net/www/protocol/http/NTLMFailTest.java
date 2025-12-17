/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8372731
 * @library /test/lib
 * @run main/othervm NTLMFailTest
 * @run main/othervm -Djdk.includeInExceptions= NTLMFailTest
 * @summary check that the Authentication failure exception
 *     honors the jdk.includeInExceptions setting
 */

import jdk.test.lib.net.HttpHeaderParser;
import jdk.test.lib.net.URIBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class NTLMFailTest {

    static final int BODY_LEN = 8192;

    static final String RESP_SERVER_AUTH =
            "HTTP/1.1 401 Unauthorized\r\n" +
            "WWW-Authenticate: NTLM\r\n" +
            "Connection: close\r\n" +
            "Content-Length: " + BODY_LEN + "\r\n" +
            "\r\n";

    static final String RESP_SERVER_NTLM =
            "HTTP/1.1 401 Unauthorized\r\n" +
            "WWW-Authenticate: NTLM InvalidChallenge\r\n" +
            "Connection: Keep-Alive\r\n" +
            "Content-Length: " + BODY_LEN + "\r\n" +
            "\r\n";

    public static void main(String[] args) throws Exception {
        Authenticator.setDefault(new TestAuthenticator());
        try (NTLMServer server = startServer(new ServerSocket(0, 0, InetAddress.getLoopbackAddress()))) {
            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(server.getLocalPort())
                    .path("/")
                    .toURLUnchecked();
            HttpURLConnection uc = (HttpURLConnection) url.openConnection();
            uc.setRequestMethod("HEAD");
            uc.getInputStream().readAllBytes();
            throw new RuntimeException("Expected exception was not thrown");
        } catch (IOException e) {
            if (e.getMessage().contains("Authentication failure")) {
                System.err.println("Got expected exception:");
                e.printStackTrace();
                if (System.getProperty("jdk.includeInExceptions") == null) {
                    // detailed message enabled by default
                    if (e.getCause() == null) {
                        throw new RuntimeException("Expected a detailed exception", e);
                    }
                    // no checks on the detailed message; it's platform-specific and may be translated
                } else {
                    // detailed message disabled
                    if (e.getCause() != null) {
                        throw new RuntimeException("Unexpected detailed exception", e);
                    }
                }
            } else {
                throw e;
            }
        }
    }

    static class NTLMServer extends Thread implements AutoCloseable {
        final ServerSocket ss;
        volatile boolean closed;

        NTLMServer(ServerSocket serverSS) {
            super();
            setDaemon(true);
            this.ss = serverSS;
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
                    doServer(is, os, doing2ndStageNTLM);
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

    static NTLMServer startServer(ServerSocket serverSS) {
        NTLMServer server = new NTLMServer(serverSS);
        server.start();
        return server;
    }

    static void doServer(InputStream is, OutputStream os, boolean doing2ndStageNTLM) throws IOException {
        if (!doing2ndStageNTLM) {
            new HttpHeaderParser(is);
            os.write(RESP_SERVER_AUTH.getBytes("ASCII"));
        } else {
            new HttpHeaderParser(is);
            os.write(RESP_SERVER_NTLM.getBytes("ASCII"));
        }
    }

    static class TestAuthenticator extends Authenticator {
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication("test", "secret".toCharArray());
        }
    }
}
