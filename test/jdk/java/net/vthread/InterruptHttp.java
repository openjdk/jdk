/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test that HTTP connections can be interrupted
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} InterruptHttp.java
 * @run main/othervm --enable-preview InterruptHttp
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import jdk.test.lib.net.URIBuilder;

public class InterruptHttp {

    public static void main(String[] args) throws Exception {
        try (Server server = new Server().start()) {
            AtomicReference<Exception> exception = new AtomicReference<>();

            // http://host:port/hello
            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(server.port())
                    .path("/hello")
                    .toURL();

            // start thread to connect to server
            Thread thread = Thread.ofVirtual().start(() -> {
                try {
                    try (InputStream in = url.openStream()) { }
                } catch (Exception e) {
                    exception.set(e);
                }
            });

            // give time for thread to block waiting for HTTP server
            while (server.connectionCount() == 0) {
                Thread.sleep(100);
            }
            thread.interrupt();
            thread.join();

            // HTTP request should fail with a SocketException
            Exception e = exception.get();
            if (!(e instanceof SocketException))
                throw new RuntimeException("Expected SocketException, got: " + e);

            // server should have accepted one connection
            if (server.connectionCount() != 1)
                throw new RuntimeException("Expected 1 HTTP connection");
        }
    }

    /**
     * Simple server that accepts connections but does not reply.
     */
    static class Server implements Closeable {
        private final ServerSocket listener;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();

        Server() throws IOException {
            InetAddress lb = InetAddress.getLoopbackAddress();
            listener = new ServerSocket(0, -1, lb);
        }

        Server start() {
            Thread.ofVirtual().start(this::run);
            return this;
        }

        private void run() {
            try {
                while (true) {
                    Socket s = listener.accept();
                    connections.add(s);
                }
            } catch (Exception e) {
                if (!listener.isClosed()) {
                    e.printStackTrace();
                }
            }
        }

        int connectionCount() {
            return connections.size();
        }

        int port() {
            return listener.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            connections.forEach(s -> {
                try { s.close(); } catch (Exception ignore) { }
            });
            listener.close();
        }
    }
}
