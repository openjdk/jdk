/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Path;

import com.sun.net.httpserver.*;

/**
 * A simple HTTP Server.
 **/
public class SimpleHttpServer {
    private final HttpServer httpServer;
    private String address;

    public SimpleHttpServer(final InetSocketAddress inetSocketAddress, final String context, final String docRoot) {
        httpServer = SimpleFileServer.createFileServer(inetSocketAddress, Path.of(docRoot), SimpleFileServer.OutputLevel.INFO);
    }

    public void start() throws IOException, URISyntaxException {
        httpServer.start();
        address = "http:" + URIBuilder.newBuilder().host(httpServer.getAddress().getAddress()).
                port(httpServer.getAddress().getPort()).build().toString();
    }

    public void stop() {
        httpServer.stop(0);
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return httpServer.getAddress().getPort();
    }
}
