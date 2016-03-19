/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URLPermission;
/*
 * Run the tests once without security manager and once with
 *
 * @test
 * @bug 8010464
 * @key intermittent
 * @library /lib/testlibrary/
 * @build jdk.testlibrary.SimpleSSLContext
 * @run main/othervm/java.security.policy=policy.1 URLTest one
 * @run main/othervm URLTest one
 * @run main/othervm/java.security.policy=policy.2 URLTest two
 * @run main/othervm URLTest two
 * @run main/othervm/java.security.policy=policy.3 URLTest three
 * @run main/othervm URLTest three
 */

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import jdk.testlibrary.SimpleSSLContext;

public class URLTest {
    static boolean failed = false;

    public static void main (String[] args) throws Exception {
        boolean no = false, yes = true;

        if (System.getSecurityManager() == null) {
            yes = false;
        }
        createServers();
        InetSocketAddress addr1 = httpServer.getAddress();
        int port1 = addr1.getPort();
        InetSocketAddress addr2 = httpsServer.getAddress();
        int port2 = addr2.getPort();

          // each of the following cases is run with a different policy file

        switch (args[0]) {
          case "one":
            String url1 = "http://127.0.0.1:"+ port1 + "/foo.html";
            String url2 = "https://127.0.0.1:"+ port2 + "/foo.html";
            String url3 = "http://127.0.0.1:"+ port1 + "/bar.html";
            String url4 = "https://127.0.0.1:"+ port2 + "/bar.html";

            // simple positive test. Should succceed
            test(url1, "GET", "X-Foo", no);
            test(url1, "GET", "Z-Bar", "X-Foo", no);
            test(url1, "GET", "X-Foo", "Z-Bar", no);
            test(url1, "GET", "Z-Bar", no);
            test(url2, "POST", "X-Fob", no);

            // reverse the methods, should fail
            test(url1, "POST", "X-Foo", yes);
            test(url2, "GET", "X-Fob", yes);

            // different URLs, should fail
            test(url3, "GET", "X-Foo", yes);
            test(url4, "POST", "X-Fob", yes);
            break;

          case "two":
            url1 = "http://127.0.0.1:"+ port1 + "/foo.html";
            url2 = "https://127.0.0.1:"+ port2 + "/foo.html";
            url3 = "http://127.0.0.1:"+ port1 + "/bar.html";
            url4 = "https://127.0.0.1:"+ port2 + "/bar.html";

            // simple positive test. Should succceed
            test(url1, "GET", "X-Foo", no);
            test(url2, "POST", "X-Fob", no);
            test(url3, "GET", "X-Foo", no);
            test(url4, "POST", "X-Fob", no);
            break;

          case "three":
            url1 = "http://127.0.0.1:"+ port1 + "/foo.html";
            url2 = "https://127.0.0.1:"+ port2 + "/a/c/d/e/foo.html";
            url3 = "http://127.0.0.1:"+ port1 + "/a/b/c";
            url4 = "https://127.0.0.1:"+ port2 + "/a/b/c";

            test(url1, "GET", "X-Foo", yes);
            test(url2, "POST", "X-Zxc", no);
            test(url3, "DELETE", "Y-Foo", no);
            test(url4, "POST", "Y-Foo", yes);
            break;
        }
        shutdown();
        if (failed) {
            throw new RuntimeException("Test failed");
        }
    }

    public static void test (
        String u, String method,
        String header, boolean exceptionExpected
    )
        throws Exception
    {
        test(u, method, header, null, exceptionExpected);
    }

    public static void test (
        String u, String method,
        String header1, String header2, boolean exceptionExpected
    )
        throws Exception
    {
        URL url = new URL(u);
        System.out.println ("url=" + u + " method="+method + " header1="+header1
                +" header2 = " + header2
                +" exceptionExpected="+exceptionExpected);
        HttpURLConnection urlc = (HttpURLConnection)url.openConnection();
        if (urlc instanceof HttpsURLConnection) {
            HttpsURLConnection ssl = (HttpsURLConnection)urlc;
            ssl.setHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String host, SSLSession sess) {
                    return true;
                }
            });
            ssl.setSSLSocketFactory (ctx.getSocketFactory());
        }
        urlc.setRequestMethod(method);
        if (header1 != null) {
            urlc.addRequestProperty(header1, "foo");
        }
        if (header2 != null) {
            urlc.addRequestProperty(header2, "bar");
        }
        try {
            int g = urlc.getResponseCode();
            if (exceptionExpected) {
                failed = true;
                System.out.println ("FAIL");
                return;
            }
            if (g != 200) {
                String s = Integer.toString(g);
                throw new RuntimeException("unexpected response "+ s);
            }
            InputStream is = urlc.getInputStream();
            int c,count=0;
            byte[] buf = new byte[1024];
            while ((c=is.read(buf)) != -1) {
                count += c;
            }
            is.close();
        } catch (RuntimeException e) {
            if (! (e instanceof SecurityException) &&
                        !(e.getCause() instanceof SecurityException)  ||
                        !exceptionExpected)
            {
                System.out.println ("FAIL");
                //e.printStackTrace();
                failed = true;
            }
        }
        System.out.println ("OK");
    }

    static HttpServer httpServer;
    static HttpsServer httpsServer;
    static HttpContext c, cs;
    static ExecutorService e, es;
    static SSLContext ctx;

    // These ports need to be hard-coded until we support port number
    // ranges in the permission class

    static final int PORT1 = 12567;
    static final int PORT2 = 12568;

    static void createServers() throws Exception {
        InetSocketAddress addr1 = new InetSocketAddress (PORT1);
        InetSocketAddress addr2 = new InetSocketAddress (PORT2);
        httpServer = HttpServer.create (addr1, 0);
        httpsServer = HttpsServer.create (addr2, 0);

        MyHandler h = new MyHandler();

        c = httpServer.createContext ("/", h);
        cs = httpsServer.createContext ("/", h);
        e = Executors.newCachedThreadPool();
        es = Executors.newCachedThreadPool();
        httpServer.setExecutor (e);
        httpsServer.setExecutor (es);

        ctx = new SimpleSSLContext().get();
        httpsServer.setHttpsConfigurator(new HttpsConfigurator (ctx));

        httpServer.start();
        httpsServer.start();
    }

    static void shutdown() {
        httpServer.stop(1);
        httpsServer.stop(1);
        e.shutdown();
        es.shutdown();
    }

    static class MyHandler implements HttpHandler {

        MyHandler() {
        }

        public void handle(HttpExchange x) throws IOException {
            x.sendResponseHeaders(200, -1);
            x.close();
        }
    }

}
