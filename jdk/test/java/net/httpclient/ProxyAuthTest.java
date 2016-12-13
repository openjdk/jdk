/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

/*
 * @test
 * @bug 8163561
 * @modules java.base/sun.net.www
 *          jdk.incubator.httpclient
 * @summary Verify that Proxy-Authenticate header is correctly handled
 *
 * @run main/othervm ProxyAuthTest
 */

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import java.util.Base64;
import sun.net.www.MessageHeader;
import static jdk.incubator.http.HttpResponse.BodyHandler.discard;

public class ProxyAuthTest {
    private static final String AUTH_USER = "user";
    private static final String AUTH_PASSWORD = "password";

    public static void main(String[] args) throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            int port = ss.getLocalPort();
            MyProxy proxy = new MyProxy(ss);
            (new Thread(proxy)).start();
            System.out.println("Proxy listening port " + port);

            Auth auth = new Auth();
            InetSocketAddress paddr = new InetSocketAddress("localhost", port);

            URI uri = new URI("http://www.google.ie/");
            HttpClient client = HttpClient.newBuilder()
                                          .proxy(ProxySelector.of(paddr))
                                          .authenticator(auth)
                                          .build();
            HttpRequest req = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<?> resp = client.sendAsync(req, discard(null)).get();
            if (resp.statusCode() != 404) {
                throw new RuntimeException("Unexpected status code: " + resp.statusCode());
            }

            if (auth.isCalled) {
                System.out.println("Authenticator is called");
            } else {
                throw new RuntimeException("Authenticator is not called");
            }

            if (!proxy.matched) {
                throw new RuntimeException("Proxy authentication failed");
            }
        }
    }

    static class Auth extends Authenticator {
        private volatile boolean isCalled;

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            System.out.println("scheme: " + this.getRequestingScheme());
            isCalled = true;
            return new PasswordAuthentication(AUTH_USER,
                    AUTH_PASSWORD.toCharArray());
        }
    }

    static class MyProxy implements Runnable {
        final ServerSocket ss;
        private volatile boolean matched;

        MyProxy(ServerSocket ss) {
            this.ss = ss;
        }

        public void run() {
            for (int i = 0; i < 2; i++) {
                try (Socket s = ss.accept();
                     InputStream in = s.getInputStream();
                     OutputStream os = s.getOutputStream();
                     BufferedWriter writer = new BufferedWriter(
                             new OutputStreamWriter(os));
                     PrintWriter out = new PrintWriter(writer);) {
                    MessageHeader headers = new MessageHeader(in);
                    System.out.println("Proxy: received " + headers);

                    String authInfo = headers.findValue("Proxy-Authorization");
                    if (authInfo != null) {
                        authenticate(authInfo);
                        out.print("HTTP/1.1 404 Not found\r\n");
                        out.print("\r\n");
                        System.out.println("Proxy: 404");
                        out.flush();
                    } else {
                        out.print("HTTP/1.1 407 Proxy Authorization Required\r\n");
                        out.print(
                                "Proxy-Authenticate: Basic realm=\"a fake realm\"\r\n");
                        out.print("\r\n");
                        System.out.println("Proxy: Authorization required");
                        out.flush();
                    }
                } catch (IOException x) {
                    System.err.println("Unexpected IOException from proxy.");
                    x.printStackTrace();
                    break;
                }
            }
        }

        private void authenticate(String authInfo) throws IOException {
            try {
                authInfo.trim();
                int ind = authInfo.indexOf(' ');
                String recvdUserPlusPass = authInfo.substring(ind + 1).trim();
                // extract encoded username:passwd
                String value = new String(
                        Base64.getMimeDecoder().decode(recvdUserPlusPass));
                String userPlusPassword = AUTH_USER + ":" + AUTH_PASSWORD;
                if (userPlusPassword.equals(value)) {
                    matched = true;
                    System.out.println("Proxy: client authentication successful");
                } else {
                    System.err.println(
                            "Proxy: client authentication failed, expected ["
                                    + userPlusPassword + "], actual [" + value
                                    + "]");
                }
            } catch (Exception e) {
                throw new IOException(
                        "Proxy received invalid Proxy-Authorization value: "
                                + authInfo);
            }
        }
    }

}
