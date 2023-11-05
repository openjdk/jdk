/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6262486
 * @library /test/lib
 * @run main/othervm -Dhttp.keepAlive=false ResponseCacheStream
 * @summary COMPATIBILITY: jagex_com - Monkey Puzzle applet fails to load
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;

public class ResponseCacheStream implements HttpHandler {

    void okReply (HttpExchange req) throws IOException {
        req.sendResponseHeaders(200, 0);
        try(PrintWriter pw = new PrintWriter(req.getResponseBody())) {
            pw.print("Hello, This is the response body. Let's make it as long as possible since we need to test the cache mechanism.");
        }
        System.out.println ("Server: sent response");
    }


    @Override
    public void handle(HttpExchange exchange) throws IOException {
        okReply(exchange);
        exchange.close();
    }

    static class MyCacheRequest extends CacheRequest {
        private OutputStream buf = null;

        public MyCacheRequest(OutputStream out) {
            buf = out;
        }

        public OutputStream getBody() throws IOException {
            return buf;
        }

        /**
         * Aborts the attempt to cache the response. If an IOException is
         * encountered while reading the response or writing to the cache,
         * the current cache store operation will be abandoned.
         */
        public void abort() {
        }

    }

    static class MyResponseCache extends ResponseCache {
        private ByteArrayOutputStream buf = new ByteArrayOutputStream(1024);

        public MyResponseCache() {
        }

        public CacheRequest put(URI uri, URLConnection conn) throws IOException {
            return new MyCacheRequest(buf);
        }

        public CacheResponse get(URI uri, String rqstMethod, Map<String, List<String>> rqstHeaders) throws IOException {
            return null;
        }

        public byte[] getBuffer() {
            return buf.toByteArray();
        }
    }

    static HttpServer server;

    public static void main(String[] args) throws Exception {
        MyResponseCache cache = new MyResponseCache();
        try {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            ResponseCache.setDefault(cache);
            server = HttpServer.create(new InetSocketAddress(loopback, 0), 10);
            server.createContext("/", new ResponseCacheStream());
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            System.out.println("Server: listening on port: " + server.getAddress().getPort());
            URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(server.getAddress().getPort())
                .path("/")
                .toURL();
            System.out.println ("Client: connecting to " + url);
            HttpURLConnection urlc = (HttpURLConnection)url.openConnection();
            InputStream is = urlc.getInputStream();
            System.out.println("is is " + is.getClass() + ". And markSupported: " + is.markSupported());
            if (is.markSupported()) {
                byte[] b = new byte[1024];
                byte[] b2 = new byte[32];
                int len;
                int count;
                is.mark(10);
                len = is.read(b, 0, 10);
                is.reset();
                len = 0;
                count = 0;
                do {
                    len = is.read(b, count, 40 - count);
                    if (len > 0)
                        count += len;
                } while (len > 0);
                is.mark(20);
                len = is.read(b2, 0, 20);
                is.reset();
                len = is.read(b, count, 10);
                count += len;
                is.mark(20);
                len = is.read(b2, 0, 20);
                is.reset();
                do {
                    len = is.read(b, count, 1024 - count);
                    if (len > 0)
                        count += len;
                } while (len > 0);
                is.close();
                String s1 = new String(b, 0 , count);
                String s2 = new String(cache.getBuffer(), 0 , count);
                if (! s1.equals(s2))
                    throw new RuntimeException("cache got corrupted!");
            }
        } catch (Exception e) {
            if (server != null) {
                server.stop(1);
            }
            throw e;
        }
        server.stop(1);
    }
}
