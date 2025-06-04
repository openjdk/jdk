/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/sun.net.www.protocol.file
 * @bug 5052093
 * @library /test/lib
 * @run main/othervm B5052093
 * @summary URLConnection doesn't support large files
 */

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import static java.net.Proxy.NO_PROXY;
import jdk.test.lib.net.URIBuilder;
import sun.net.www.protocol.file.FileURLConnection;

public class B5052093 {
    private static HttpServer server;
    static long testSize = ((long) (Integer.MAX_VALUE)) + 2;

    public static class LargeFile extends File {
        public LargeFile() {
            super("/dev/zero");
        }

        public long length() {
            return testSize;
        }
    }

    public static class LargeFileURLConnection extends FileURLConnection {
        public LargeFileURLConnection(LargeFile f) throws IOException {
                super(new URL("file:///dev/zero"), f);
        }
    }

    public static void main(String[] args) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 10, "/", new B5052093Handler());
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try {
            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(server.getAddress().getPort())
                    .path("/foo")
                    .build().toURL();
            URLConnection conn = url.openConnection(NO_PROXY);
            int i = conn.getContentLength();
            long l = conn.getContentLengthLong();
            if (i != -1 || l != testSize) {
                System.out.println("conn.getContentLength = " + i);
                System.out.println("conn.getContentLengthLong = " + l);
                System.out.println("testSize = " + testSize);
                throw new RuntimeException("Wrong content-length from http");
            }

            URLConnection fu = new LargeFileURLConnection(new LargeFile());
            i = fu.getContentLength();
            l = fu.getContentLengthLong();
            if (i != -1 || l != testSize) {
                System.out.println("fu.getContentLength = " + i);
                System.out.println("fu.getContentLengthLong = " + l);
                System.out.println("testSize = " + testSize);
                throw new RuntimeException("Wrong content-length from file");
            }
        } finally {
            server.stop(1);
        }
    }
}

class B5052093Handler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("content-length", Long.toString(B5052093.testSize));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
