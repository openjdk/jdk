/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6660405
 * @summary HttpURLConnection returns the wrong InputStream
 */

import java.net.*;
import java.util.*;
import java.io.*;
import com.sun.net.httpserver.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class B6660405
{
    com.sun.net.httpserver.HttpServer httpServer;
    ExecutorService executorService;

    static class MyCacheResponse extends CacheResponse {
        private byte[] buf = new byte[1024];

        public MyCacheResponse() {
        }

        @Override
        public Map<String, List<String>> getHeaders() throws IOException
        {
            Map<String, List<String>> h = new HashMap<String, List<String>>();
            ArrayList<String> l = new ArrayList<String>();
            l.add("HTTP/1.1 200 OK");
            h.put(null, l);
            l = new ArrayList<String>();
            l.add("1024");
            h.put("Content-Length", l);
            return h;
        }

        @Override
        public InputStream getBody() throws IOException
        {
            return new ByteArrayInputStream(buf);
        }

    }
    static class MyResponseCache extends ResponseCache {

        public MyResponseCache() {
        }

        @Override
        public CacheResponse get(URI uri, String rqstMethod, Map<String, List<String>> rqstHeaders) throws IOException
        {
            if (uri.getPath().equals("/redirect/index.html")) {
                return new MyCacheResponse();
            }
            return null;
        }

        @Override
        public CacheRequest put(URI uri, URLConnection conn) throws IOException
        {
            return null;
        }

    }

    public static void main(String[] args)
    {
        new B6660405();
    }

    public B6660405()
    {
        try {
            startHttpServer();
            doClient();
        } catch (IOException ioe) {
            System.err.println(ioe);
        }
    }

    void doClient() {
        ResponseCache.setDefault(new MyResponseCache());
        try {
            InetSocketAddress address = httpServer.getAddress();

            // GET Request
            URL url = new URL("http://localhost:" + address.getPort() + "/test/index.html");
            HttpURLConnection uc = (HttpURLConnection)url.openConnection();
            int code = uc.getResponseCode();
            System.err.println("response code = " + code);
            int l = uc.getContentLength();
            System.err.println("content-length = " + l);
            InputStream in = uc.getInputStream();
            int i = 0;
            // Read till end of stream
            do {
                i = in.read();
            } while (i != -1);
            in.close();
        } catch (IOException e) {
            throw new RuntimeException("Got the wrong InputStream after checking headers");
        } finally {
            httpServer.stop(1);
            executorService.shutdown();
        }
    }

    /**
     * Http Server
     */
    public void startHttpServer() throws IOException {
        httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);

        // create HttpServer context
        HttpContext ctx = httpServer.createContext("/test/", new MyHandler());

        executorService = Executors.newCachedThreadPool();
        httpServer.setExecutor(executorService);
        httpServer.start();
    }

    class MyHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            InputStream is = t.getRequestBody();
            Headers reqHeaders = t.getRequestHeaders();
            Headers resHeaders = t.getResponseHeaders();

            int i = 0;
            // Read till end of stream
            do {
                i = is.read();
            } while (i != -1);
            is.close();
            resHeaders.add("Location", "http://foo.bar/redirect/index.html");
            t.sendResponseHeaders(302, -1);
            t.close();
        }
    }
}
