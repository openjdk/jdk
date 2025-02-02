/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8029354
 * @library /test/lib
 * @run main/othervm OpenURL
 */

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.test.lib.net.URIBuilder;
import static java.net.Proxy.NO_PROXY;

public class OpenURL {

    public static void main (String[] args) throws Exception {

        // minimal HTTP/1.1 reply
        final String reply = "HTTP/1.1 200 OK\r\n"+
                "Connection: close\r\n" +
                "Content-Length: 0\r\n\r\n";

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            final AtomicBoolean done = new AtomicBoolean();
            final Thread serverThread = new Thread(() -> {
                while (!done.get()) {
                    try (Socket ss = serverSocket.accept()) {
                        ss.getOutputStream().write(reply.getBytes(StandardCharsets.US_ASCII));
                        ss.getOutputStream().close();
                        // Give a chance to the peer to close the socket first...
                        Thread.sleep(100);
                        // Reads the request headers - avoids Connection reset
                        BufferedReader reader = new BufferedReader(new InputStreamReader(ss.getInputStream()));
                        String line;
                        do {
                            System.out.println("Server: " + (line = reader.readLine()));
                        } while (!line.isBlank());
                    } catch (Exception x) {
                        if (!done.get()) {
                            // Something else than the expected client
                            // might have connected...
                            x.printStackTrace();
                        }
                    }
                }
            });
            serverThread.start();
            try {
                URL url = URIBuilder.newBuilder()
                        .scheme("http")
                        .userInfo("joe")
                        .loopback()
                        .port(serverSocket.getLocalPort())
                        .path("/a/b")
                        .toURL();
                System.out.println("URL: " + url);

                // will throw if not fixed
                URLPermission perm = new URLPermission(url.toString(), "listen,read,resolve");
                System.out.println("Permission: " + perm);
                // may throw if not fixed
                HttpURLConnection urlc = (HttpURLConnection) url.openConnection(NO_PROXY);
                InputStream is = urlc.getInputStream();
            } finally {
                // make sure the server thread eventually exit
                done.set(true);
                serverSocket.close();
            }
            serverThread.join();
            System.out.println("OpenURL: OK");
        }

    }
}
