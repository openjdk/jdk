/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @compile ../../../../../com/sun/net/httpserver/SimpleSSLContext.java RedirectOnPost.java
 * @run main/othervm RedirectOnPost
 * @bug 8029127
 * @summary A redirect POST request does not work and illegalStateException on HttpURLConnection.getInputStream
 */

import java.net.*;
import java.io.*;
import java.util.*;
import com.sun.net.httpserver.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

public class RedirectOnPost {


    public static void main(String[] args) throws Exception {
            ExecutorService e= Executors.newFixedThreadPool(5);
        String keysdir = System.getProperty("test.src")
                  + "/../../../../../com/sun/net/httpserver/";
        SSLContext ctx = new SimpleSSLContext(keysdir).get();
            HttpServer httpServer = getHttpServer(e);
            HttpsServer httpsServer = getHttpsServer(e, ctx);

        try {
            // take the keystore from elsewhere in test hierarchy
            int port = httpServer.getAddress().getPort();
            int sslPort = httpsServer.getAddress().getPort();
            httpServer.start();
            httpsServer.start();
            runTest("http://127.0.0.1:"+port+"/test/", null);
            runTest("https://127.0.0.1:"+sslPort+"/test/", ctx);
            System.out.println("Main thread waiting");
        } finally {
            httpServer.stop(0);
            httpsServer.stop(0);
            e.shutdownNow();
        }
    }

    public static void runTest(String baseURL, SSLContext ctx) throws Exception
    {
        byte[] buf = "Hello world".getBytes();
        URL url = new URL(baseURL + "a");
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        if (con instanceof HttpsURLConnection) {
            HttpsURLConnection ssl = (HttpsURLConnection)con;
            ssl.setHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String host, SSLSession sess) {
                    return true;
                }
            });
            ssl.setSSLSocketFactory (ctx.getSocketFactory());
        }
        con.setDoOutput(true);
        con.setDoInput(true);
        con.setRequestMethod("POST");
        try (OutputStream out = con.getOutputStream()) {
            out.write(buf);
        }
        try (InputStream in = con.getInputStream()) {
            byte[] newBuf = readFully(in);
        }
    }

    private static byte[] readFully(InputStream istream) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int num = 0;

        if (istream != null) {
            while ((num = istream.read(buf)) != -1) {
                bout.write(buf, 0, num);
            }
        }
        byte[] ret = bout.toByteArray();
        return ret;
    }


    static class Handler implements HttpHandler {

        String baseURL;

        Handler(String baseURL) {
            this.baseURL = baseURL;
        }

        int calls = 0;

        public void handle(HttpExchange msg) {
            try {
                String method = msg.getRequestMethod();
                System.out.println ("Server: " + baseURL);
                if (calls++ == 0) {
                    System.out.println ("Server: redirecting");
                    InputStream is = msg.getRequestBody();
                    byte[] buf = readFully(is);
                    is.close();
                    Headers h = msg.getResponseHeaders();
                    h.add("Location", baseURL + "b");
                    msg.sendResponseHeaders(302, -1);
                    msg.close();
                } else {
                    System.out.println ("Server: second call");
                    InputStream is = msg.getRequestBody();
                    byte[] buf = readFully(is);
                    is.close();
                    msg.sendResponseHeaders(200, -1);
                    msg.close();
                }
            }
            catch(Exception e) {
                e.printStackTrace();
            }
            finally {
                msg.close();
            }
        }
    }

    private static HttpServer getHttpServer(ExecutorService execs)
        throws Exception
    {
        InetSocketAddress inetAddress = new InetSocketAddress(0);
        HttpServer testServer = HttpServer.create(inetAddress, 15);
        int port = testServer.getAddress().getPort();
        testServer.setExecutor(execs);
        String base = "http://127.0.0.1:"+port+"/test";
        HttpContext context = testServer.createContext("/test");
        context.setHandler(new Handler(base));
        return testServer;
    }

    private static HttpsServer getHttpsServer(
        ExecutorService execs, SSLContext ctx
    )
        throws Exception
    {
        InetSocketAddress inetAddress = new InetSocketAddress(0);
        HttpsServer testServer = HttpsServer.create(inetAddress, 15);
        int port = testServer.getAddress().getPort();
        testServer.setExecutor(execs);
        testServer.setHttpsConfigurator(new HttpsConfigurator (ctx));
        String base = "https://127.0.0.1:"+port+"/test";
        HttpContext context = testServer.createContext("/test");
        context.setHandler(new Handler(base));
        return testServer;
    }
}
