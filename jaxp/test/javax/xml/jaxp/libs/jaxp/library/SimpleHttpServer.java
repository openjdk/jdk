/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package jaxp.library;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A simple HTTP Server
 */
public class SimpleHttpServer {
    HttpServer _httpserver;
    ExecutorService _executor;

    String _address;

    String _context, _docroot;
    int _port;

    public SimpleHttpServer(String context, String docroot) {
        //let the system pick up an ephemeral port in a bind operation
        this(0, context, docroot);
    }

    public SimpleHttpServer(int port, String context, String docroot) {
        _port = port;
        _context = context;
        _docroot = docroot;
    }

    public void start() {
        MyHttpHandler handler = new MyHttpHandler(_docroot);
        InetSocketAddress addr = new InetSocketAddress(_port);
        try {
            _httpserver = HttpServer.create(addr, 0);
        } catch (IOException ex) {
            throw new RuntimeException("cannot create httpserver", ex);
        }

        //TestHandler is mapped to /test
        HttpContext ctx = _httpserver.createContext(_context, handler);

        _executor = Executors.newCachedThreadPool();
        _httpserver.setExecutor(_executor);
        _httpserver.start();

        _address = "http://localhost:" + _httpserver.getAddress().getPort();
    }

    public void stop() {
        _httpserver.stop(2);
        _executor.shutdown();
    }

    public String getAddress() {
        return _address;
    }

    static class MyHttpHandler implements HttpHandler {

        String _docroot;

        public MyHttpHandler(String docroot) {
            _docroot = docroot;
        }

        public void handle(HttpExchange t)
                throws IOException {
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            OutputStream os = t.getResponseBody();
            URI uri = t.getRequestURI();
            String path = uri.getPath();


            while (is.read() != -1) ;
            is.close();

            File f = new File(_docroot, path);
            if (!f.exists()) {
                notfound(t, path);
                return;
            }

            String method = t.getRequestMethod();
            if (method.equals("HEAD")) {
                rmap.set("Content-Length", Long.toString(f.length()));
                t.sendResponseHeaders(200, -1);
                t.close();
            } else if (!method.equals("GET")) {
                t.sendResponseHeaders(405, -1);
                t.close();
                return;
            }

            if (path.endsWith(".html") || path.endsWith(".htm")) {
                rmap.set("Content-Type", "text/html");
            } else {
                rmap.set("Content-Type", "text/plain");
            }

            t.sendResponseHeaders (200, f.length());

            FileInputStream fis = new FileInputStream(f);
            int count = 0;
            try {
                byte[] buf = new byte[16 * 1024];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    os.write(buf, 0, len);
                    count += len;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            fis.close();
            os.close();
        }

        void moved(HttpExchange t) throws IOException {
            Headers req = t.getRequestHeaders();
            Headers map = t.getResponseHeaders();
            URI uri = t.getRequestURI();
            String host = req.getFirst("Host");
            String location = "http://" + host + uri.getPath() + "/";
            map.set("Content-Type", "text/html");
            map.set("Location", location);
            t.sendResponseHeaders(301, -1);
            t.close();
        }

        void notfound(HttpExchange t, String p) throws IOException {
            t.getResponseHeaders().set("Content-Type", "text/html");
            t.sendResponseHeaders(404, 0);
            OutputStream os = t.getResponseBody();
            String s = "<h2>File not found</h2>";
            s = s + p + "<p>";
            os.write(s.getBytes());
            os.close();
            t.close();
        }
    }

}
