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
 * @test
 * @bug 8010464
 * @modules jdk.httpserver
 * @library /lib/testlibrary/
 * @build jdk.testlibrary.SimpleSSLContext
 * @run main/othervm URLTest
 * @summary check URLPermission with Http(s)URLConnection
 */

import java.net.*;
import java.io.*;
import java.security.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import jdk.testlibrary.SimpleSSLContext;

public class URLTest {

    static boolean failed;

    public static void main (String[] args) throws Exception {
        createServers();

        try {
            // Verify without a Security Manager
            test1();
            test2();
            test3();

            // Set the security manager. Each test will set its own policy.
            Policy.setPolicy(new CustomPolicy());
            System.setSecurityManager(new SecurityManager());
            System.out.println("\n Security Manager has been set.");

            test1();
            test2();
            test3();

            if (failed)
                throw new RuntimeException("Test failed");
        } finally {
            shutdown();
        }
    }

    static void test1() throws IOException {
        System.out.println("\n--- Test 1 ---");

        boolean expectException = false;
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            expectException = true;
            Policy.setPolicy(new CustomPolicy(
                new URLPermission("http://127.0.0.1:"+httpPort+"/foo.html", "GET:X-Foo,Z-Bar"),
                new URLPermission("https://127.0.0.1:"+httpsPort+"/foo.html", "POST:X-Fob,T-Bar")));
        }

        String url1 = "http://127.0.0.1:"+httpPort+"/foo.html";
        String url2 = "https://127.0.0.1:"+httpsPort+"/foo.html";
        String url3 = "http://127.0.0.1:"+httpPort+"/bar.html";
        String url4 = "https://127.0.0.1:"+httpsPort+"/bar.html";

        // simple positive test. Should succeed
        test(url1, "GET", "X-Foo");
        test(url1, "GET", "Z-Bar", "X-Foo");
        test(url1, "GET", "X-Foo", "Z-Bar");
        test(url1, "GET", "Z-Bar");
        test(url2, "POST", "X-Fob");

        // reverse the methods, should fail
        test(url1, "POST", "X-Foo", expectException);
        test(url2, "GET", "X-Fob", expectException);

        // different URLs, should fail
        test(url3, "GET", "X-Foo", expectException);
        test(url4, "POST", "X-Fob", expectException);
    }

    static void test2() throws IOException {
        System.out.println("\n--- Test 2 ---");

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            Policy.setPolicy(new CustomPolicy(
                new URLPermission("http://127.0.0.1:"+httpPort+"/*", "GET:X-Foo"),
                new URLPermission("https://127.0.0.1:"+httpsPort+"/*", "POST:X-Fob")));
        }

        String url1 = "http://127.0.0.1:"+httpPort+"/foo.html";
        String url2 = "https://127.0.0.1:"+httpsPort+"/foo.html";
        String url3 = "http://127.0.0.1:"+httpPort+"/bar.html";
        String url4 = "https://127.0.0.1:"+httpsPort+"/bar.html";

        // simple positive test. Should succeed
        test(url1, "GET", "X-Foo");
        test(url2, "POST", "X-Fob");
        test(url3, "GET", "X-Foo");
        test(url4, "POST", "X-Fob");
    }

    static void test3() throws IOException {
        System.out.println("\n--- Test 3 ---");

        boolean expectException = false;
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            expectException = true;
            Policy.setPolicy(new CustomPolicy(
                new URLPermission("http://127.0.0.1:"+httpPort+"/a/b/-", "DELETE,GET:X-Foo,Y-Foo"),
                new URLPermission("https://127.0.0.1:"+httpsPort+"/a/c/-", "POST:*")));
        }

        String url1 = "http://127.0.0.1:"+httpPort+"/foo.html";
        String url2 = "https://127.0.0.1:"+httpsPort+"/a/c/d/e/foo.html";
        String url3 = "http://127.0.0.1:"+httpPort+"/a/b/c";
        String url4 = "https://127.0.0.1:"+httpsPort+"/a/b/c";

        test(url1, "GET", "X-Foo", expectException);
        test(url2, "POST", "X-Zxc");
        test(url3, "DELETE", "Y-Foo");
        test(url4, "POST", "Y-Foo", expectException);
    }

    // Convenience methods to simplify previous explicit test scenarios.
    static void test(String u, String method, String header) throws IOException {
        test(u, method, header, null, false);
    }

    static void test(String u, String method, String header, boolean expectException)
        throws IOException
    {
        test(u, method, header, null, expectException);
    }

    static void test(String u, String method, String header1, String header2)
        throws IOException
    {
        test(u, method, header1, header2, false);
    }

    static void test(String u,
                     String method,
                     String header1,
                     String header2,
                     boolean expectException)
        throws IOException
    {
        URL url = new URL(u);
        System.out.println("url=" + u + " method=" + method +
                           " header1=" + header1 + " header2=" + header2 +
                           " expectException=" + expectException);
        HttpURLConnection urlc = (HttpURLConnection)url.openConnection();
        if (urlc instanceof HttpsURLConnection) {
            HttpsURLConnection ssl = (HttpsURLConnection)urlc;
            ssl.setHostnameVerifier((host, sess) -> true);
            ssl.setSSLSocketFactory(ctx.getSocketFactory());
        }
        urlc.setRequestMethod(method);
        if (header1 != null)
            urlc.addRequestProperty(header1, "foo");
        if (header2 != null)
            urlc.addRequestProperty(header2, "bar");

        try {
            int code = urlc.getResponseCode();
            if (expectException) {
                failed = true;
                System.out.println("FAIL");
                return;
            }
            if (code != 200)
                throw new RuntimeException("Unexpected response " + code);

            InputStream is = urlc.getInputStream();
            is.readAllBytes();
            is.close();
        } catch (RuntimeException e) {
            if (!expectException || !(e.getCause() instanceof SecurityException)) {
                System.out.println ("FAIL. Unexpected: " + e.getMessage());
                e.printStackTrace();
                failed = true;
                return;
            } else {
                System.out.println("Got expected exception: " + e.getMessage());
            }
        }
        System.out.println ("PASS");
    }

    static HttpServer httpServer;
    static HttpsServer httpsServer;
    static HttpContext c, cs;
    static ExecutorService e, es;
    static SSLContext ctx;
    static int httpPort;
    static int httpsPort;

    static void createServers() throws Exception {
        InetSocketAddress any = new InetSocketAddress(0);
        httpServer = HttpServer.create(any, 0);
        httpsServer = HttpsServer.create(any, 0);

        OkHandler h = new OkHandler();

        c = httpServer.createContext("/", h);
        cs = httpsServer.createContext("/", h);
        e = Executors.newCachedThreadPool();
        es = Executors.newCachedThreadPool();
        httpServer.setExecutor(e);
        httpsServer.setExecutor(es);

        ctx = new SimpleSSLContext().get();
        httpsServer.setHttpsConfigurator(new HttpsConfigurator (ctx));

        httpServer.start();
        httpsServer.start();

        httpPort = httpServer.getAddress().getPort();
        httpsPort = httpsServer.getAddress().getPort();
    }

    static void shutdown() {
        httpServer.stop(1);
        httpsServer.stop(1);
        e.shutdown();
        es.shutdown();
    }

    static class OkHandler implements HttpHandler {
        public void handle(HttpExchange x) throws IOException {
            x.sendResponseHeaders(200, -1);
            x.close();
        }
    }

    static class CustomPolicy extends Policy {
        final PermissionCollection perms = new Permissions();
        CustomPolicy(Permission... permissions) {
            java.util.Arrays.stream(permissions).forEach(perms::add);

            // needed for the HTTP(S) server
            perms.add(new SocketPermission("localhost:1024-", "listen,resolve,accept"));
            // needed by the test to reset the policy, per testX method
            perms.add(new SecurityPermission("setPolicy"));
            // needed to shutdown the ThreadPoolExecutor ( used by the servers )
            perms.add(new RuntimePermission("modifyThread"));
            // needed by the client code forHttpsURLConnection.setSSLSocketFactory
            perms.add(new RuntimePermission("setFactory"));
        }

        public PermissionCollection getPermissions(ProtectionDomain domain) {
            return perms;
        }

        public PermissionCollection getPermissions(CodeSource codesource) {
            return perms;
        }

        public boolean implies(ProtectionDomain domain, Permission perm) {
            return perms.implies(perm);
        }
    }
}
