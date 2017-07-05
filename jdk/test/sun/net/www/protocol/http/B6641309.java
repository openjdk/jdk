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
 * @bug 6641309
 * @summary Wrong Cookie separator used in HttpURLConnection
 */

import java.net.*;
import java.util.*;
import java.io.*;
import com.sun.net.httpserver.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class B6641309
{
    com.sun.net.httpserver.HttpServer httpServer;
    ExecutorService executorService;

    public static void main(String[] args)
    {
        new B6641309();
    }

    public B6641309()
    {
        try {
            startHttpServer();
            doClient();
        } catch (IOException ioe) {
            System.err.println(ioe);
        }
    }

    void doClient() {
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        try {
            InetSocketAddress address = httpServer.getAddress();

            // GET Request
            URL url = new URL("http://localhost:" + address.getPort() + "/test/");
            CookieHandler ch = CookieHandler.getDefault();
            Map<String,List<String>> header = new HashMap<String,List<String>>();
            List<String> values = new LinkedList<String>();
            values.add("Test1Cookie=TEST1; path=/test/");
            values.add("Test2Cookie=TEST2; path=/test/");
            header.put("Set-Cookie", values);

            // preload the CookieHandler with a cookie for our URL
            // so that it will be sent during the first request
            ch.put(url.toURI(), header);
            HttpURLConnection uc = (HttpURLConnection)url.openConnection();
            int resp = uc.getResponseCode();
            if (resp != 200)
                throw new RuntimeException("Failed: Response code from GET is not 200");

            System.out.println("Response code from GET = 200 OK");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
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
            int i = 0;
            // Read till end of stream
            do {
                i = is.read();
            } while (i != -1);
            is.close();

            List<String> cookies = reqHeaders.get("Cookie");
            if (cookies != null) {
                for (String str : cookies) {
                    // The separator between the 2 cookies should be
                    // a semi-colon AND a space
                    if (str.equals("Test1Cookie=TEST1; Test2Cookie=TEST2"))
                        t.sendResponseHeaders(200, -1);
                }
            }
            t.sendResponseHeaders(400, -1);
            t.close();
        }
    }
}
