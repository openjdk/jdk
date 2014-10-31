/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8042622
 * @summary Check for CRL results in IllegalArgumentException "white space not allowed"
 * @run main/othervm Test2
 */

import com.sun.net.httpserver.*;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.security.*;
import javax.security.auth.callback.*;
import javax.net.ssl.*;

public class Test2 {

    static volatile boolean failed = false;

    static class Cache extends ResponseCache {
        public CacheResponse get(URI uri, String method, Map<String,List<String>> headers) {
            Set<String> keys = headers.keySet();
            for (String key : keys) {
                if (key.indexOf(' ') != -1 || key.indexOf('\t') != -1
                        || key.indexOf(':') != -1)
                {
                    failed = true;
                }
            }
            return null;
        }

        public CacheRequest put(URI uri, URLConnection c) throws IOException {
            return null;
        }
    }

    static int port;

    static String urlstring, redirstring;

    public static void main (String[] args) throws Exception {
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpServer server = HttpServer.create (addr, 0);
        port = server.getAddress().getPort();
        HttpContext ctx = server.createContext ("/test", handler);
        System.out.println ("Server: " + server.getAddress().getPort());
        ResponseCache.setDefault(new Cache());

        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();

        urlstring = "http://127.0.0.1:" + Integer.toString(port)+"/test/foo";
        redirstring = urlstring + "/redirect/bar";

        URL url = new URL (urlstring);
        HttpURLConnection urlc = (HttpURLConnection)url.openConnection();
        urlc.addRequestProperty("X-Foo", "bar");
        urlc.setInstanceFollowRedirects(true);
        System.out.println(urlc.getResponseCode());
        InputStream i = urlc.getInputStream();
        int count=0;
        for (int c=i.read(); c!=-1; c=i.read()) {
            //System.out.write(c);
            count++;
        }
        System.out.println("Read " + count);
        System.out.println("FINISHED");
        server.stop(0);
        executor.shutdownNow();
        if (failed) {
            throw new RuntimeException("Test failed");
        }
    }

    public static boolean error = false;
    public static int count = 0;

    static class Handler implements HttpHandler {
        int invocation = 0;
        public void handle (HttpExchange t)
            throws IOException
        {
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            invocation ++;
            if (invocation == 1) {
                rmap.add("Location", redirstring);
                while (is.read () != -1) ;
                is.close();
                System.out.println ("sending response");
                t.sendResponseHeaders (301, 0);
            } else {
                byte[] buf = "Hello world".getBytes();
                t.sendResponseHeaders (200, buf.length);
                OutputStream os = t.getResponseBody();
                try {
                        os.write(buf);
                } catch (IOException e) {
                        System.out.println ("EX 1 " + e);
                }
            }
            System.out.println ("Closing");
            t.close();
        }
    }
}
