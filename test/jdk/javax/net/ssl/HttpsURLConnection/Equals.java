/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8055299
 * @library /test/lib
 * @modules jdk.httpserver
 * @build jdk.test.lib.net.SimpleSSLContext
 * @run main/othervm Equals
 */
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import javax.net.ssl.*;
import java.util.concurrent.*;
import jdk.test.lib.net.SimpleSSLContext;

public class Equals {

    /*
     * Enables the JSSE system debugging system property:
     *
     *     -Djavax.net.debug=ssl,handshake,record
     *
     * This gives a lot of low-level information about operations underway,
     * including specific handshake messages, and might be best examined
     * after gaining some familiarity with this application.
     */
    private static final boolean debug = false;

    private static final SSLContext ctx = SimpleSSLContext.findSSLContext();

    public static void main(String[] args) throws Exception {
        if (debug) {
            System.setProperty("javax.net.debug", "ssl,handshake,record");
        }

        HttpsServer s2 = null;
        ExecutorService executor = null;
        try {
            InetSocketAddress addr = new InetSocketAddress(0);
            s2 = HttpsServer.create(addr, 0);
            HttpHandler h = new Handler();
            HttpContext c2 = s2.createContext("/test1", h);
            executor = Executors.newCachedThreadPool();
            s2.setExecutor(executor);
            s2.setHttpsConfigurator(new HttpsConfigurator(ctx));
            s2.start();
            int httpsport = s2.getAddress().getPort();
            System.out.printf("%nServer address: %s%n", s2.getAddress());
            test(httpsport);
            System.out.println("OK");
        } finally {
            if (s2 != null) {
                s2.stop(2);
            }
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    static class Handler implements HttpHandler {

        int invocation = 1;

        public void handle(HttpExchange t)
                throws IOException {
            InputStream is = t.getRequestBody();
            while (is.read() != -1) {
            }
            is.close();
            t.sendResponseHeaders(200, 0);
            t.close();
        }
    }

    static void test(int port) throws Exception {
        System.out.printf("%nClient using port number: %s%n", port);
        String spec = String.format("https://localhost:%s/test1/", port);
        URL url = new URL(spec);
        HttpsURLConnection urlcs = (HttpsURLConnection) url.openConnection();
        urlcs.setHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String s, SSLSession s1) {
                return true;
            }
        });
        urlcs.setSSLSocketFactory(ctx.getSocketFactory());

        InputStream is = urlcs.getInputStream();
        while (is.read() != -1) {
        }
        is.close();
        if (!urlcs.equals(urlcs)) {
            throw new RuntimeException("Test failed");
        }
    }
}
