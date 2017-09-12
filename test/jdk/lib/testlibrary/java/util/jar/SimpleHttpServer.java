/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Extremely simple server that only performs one task.  The server listens for
 * requests on the ephemeral port.  If it sees a request that begins with
 * "/multi-release.jar", it consumes the request and returns a stream of bytes
 * representing the jar file multi-release.jar found in "userdir".
 */
class SimpleHttpServer {
    private static final String userdir = System.getProperty("user.dir", ".");
    private static final Path multirelease = Paths.get(userdir, "multi-release.jar");

    private final HttpServer server;

    public SimpleHttpServer() throws IOException {
        server = HttpServer.create();
    }

    public void start() throws IOException {
        server.bind(new InetSocketAddress(0), 0);
        server.createContext("/multi-release.jar", t -> {
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();  // probably not necessary to consume request
                byte[] bytes = Files.readAllBytes(multirelease);
                t.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    int getPort() {
        return server.getAddress().getPort();
    }
}

