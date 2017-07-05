/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

/**
 * @test
 * @bug 8087112
 * @library /lib/testlibrary/
 * @build jdk.testlibrary.SimpleSSLContext
 * @compile ../../../../com/sun/net/httpserver/LogFilter.java
 * @compile ../../../../com/sun/net/httpserver/FileServerHandler.java
 * @compile ../ProxyServer.java
 *
 * @run main/othervm/secure=java.lang.SecurityManager/policy=0.policy Security 0
 * @run main/othervm/secure=java.lang.SecurityManager/policy=1.policy Security 1
 * @run main/othervm/secure=java.lang.SecurityManager/policy=2.policy Security 2
 * @run main/othervm/secure=java.lang.SecurityManager/policy=3.policy Security 3
 * @run main/othervm/secure=java.lang.SecurityManager/policy=4.policy Security 4
 * @run main/othervm/secure=java.lang.SecurityManager/policy=5.policy Security 5
 * @run main/othervm/secure=java.lang.SecurityManager/policy=6.policy Security 6
 * @run main/othervm/secure=java.lang.SecurityManager/policy=7.policy Security 7
 * @run main/othervm/secure=java.lang.SecurityManager/policy=8.policy Security 8
 * @run main/othervm/secure=java.lang.SecurityManager/policy=9.policy Security 9
 * @run main/othervm/secure=java.lang.SecurityManager/policy=10.policy Security 10
 * @run main/othervm/secure=java.lang.SecurityManager/policy=11.policy Security 11
 * @run main/othervm/secure=java.lang.SecurityManager/policy=12.policy Security 12
 * @run main/othervm/secure=java.lang.SecurityManager/policy=0.policy Security 13
 * @run main/othervm/secure=java.lang.SecurityManager/policy=1.policy Security 14
 * @run main/othervm/secure=java.lang.SecurityManager/policy=15.policy Security 15
 */

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Security checks test
 */
public class Security {

    static HttpServer s1 = null;
    static ExecutorService executor=null;
    static int port;
    static HttpClient client;
    static String httproot, fileuri, fileroot, redirectroot;
    static List<HttpClient> clients = new LinkedList<>();
    static URI uri;

    interface Test {
        public void execute() throws IOException, InterruptedException;
    }

    static class TestAndResult {
        Test test;
        boolean result;

        TestAndResult (Test t, boolean result) {
            this.test = t;
            this.result = result;
        }
    }

    static TestAndResult test(boolean result, Test t) {
        return new TestAndResult(t, result);
    }

    static TestAndResult[] tests;
    static String testclasses;
    static File subdir;

    /**
     * The ProxyServer class is compiled by jtreg, but we want to
     * move it so it is not on the application claspath. We want to
     * load it through a separate classloader so that it has a separate
     * protection domain and security permissions.
     *
     * Its permissions are in the second grant block in each policy file
     */
    static void setupProxy() throws IOException, ClassNotFoundException, NoSuchMethodException {
        testclasses = System.getProperty("test.classes");
        subdir = new File (testclasses, "proxydir");
        subdir.mkdir();

        movefile("ProxyServer.class");
        movefile("ProxyServer$Connection.class");
        movefile("ProxyServer$1.class");

        URL url = subdir.toURL();
        System.out.println("URL for class loader = " + url);
        URLClassLoader urlc = new URLClassLoader(new URL[] {url});
        proxyClass = Class.forName("ProxyServer", true, urlc);
        proxyConstructor = proxyClass.getConstructor(Integer.class, Boolean.class);
    }

    static void movefile(String f) throws IOException {
        Path src = Paths.get(testclasses, f);
        Path dest = subdir.toPath().resolve(f);
        if (!dest.toFile().exists()) {
            System.out.printf("moving %s to %s\n", src.toString(), dest.toString());
            Files.move(src, dest,  StandardCopyOption.REPLACE_EXISTING);
        } else {
            System.out.printf("NOT moving %s to %s\n", src.toString(), dest.toString());
        }
    }

    static Object getProxy(int port, boolean b) throws Exception {
        return proxyConstructor.newInstance(port, b);
    }

    static Class<?> proxyClass;
    static Constructor<?> proxyConstructor;

    static void setupTests() {
        tests = new TestAndResult[]{
            // (0) policy does not have permission for file. Should fail
            test(false, () -> { // Policy 0
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                HttpResponse response = request.response();
            }),
            // (1) policy has permission for file URL
            test(true, () -> { //Policy 1
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                HttpResponse response = request.response();
            }),
            // (2) policy has permission for all file URLs under /files
            test(true, () -> { // Policy 2
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                HttpResponse response = request.response();
            }),
            // (3) policy has permission for first URL but not redirected URL
            test(false, () -> { // Policy 3
                URI u = URI.create("http://127.0.0.1:" + port + "/redirect/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                HttpResponse response = request.response();
            }),
            // (4) policy has permission for both first URL and redirected URL
            test(true, () -> { // Policy 4
                URI u = URI.create("http://127.0.0.1:" + port + "/redirect/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                HttpResponse response = request.response();
            }),
            // (5) policy has permission for redirected but not first URL
            test(false, () -> { // Policy 5
                URI u = URI.create("http://127.0.0.1:" + port + "/redirect/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                HttpResponse response = request.response();
            }),
            // (6) policy has permission for file URL, but not method
            test(false, () -> { //Policy 6
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                HttpResponse response = request.response();
            }),
            // (7) policy has permission for file URL, method, but not header
            test(false, () -> { //Policy 7
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .header("X-Foo", "bar")
                        .GET();
                HttpResponse response = request.response();
            }),
            // (8) policy has permission for file URL, method and header
            test(true, () -> { //Policy 8
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .header("X-Foo", "bar")
                        .GET();
                HttpResponse response = request.response();
            }),
            // (9) policy has permission for file URL, method and header
            test(true, () -> { //Policy 9
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .headers("X-Foo", "bar", "X-Bar", "foo")
                        .GET();
                HttpResponse response = request.response();
            }),
            // (10) policy has permission for destination URL but not for proxy
            test(false, () -> { //Policy 10
                directProxyTest(27208, true);
            }),
            // (11) policy has permission for both destination URL and proxy
            test(true, () -> { //Policy 11
                directProxyTest(27301, true);
            }),
            // (12) policy has permission for both destination URL and proxy
            test(false, () -> { //Policy 11
                directProxyTest(28301, false);
            }),
            // (13) async version of test 0
            test(false, () -> { // Policy 0
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                try {
                    HttpResponse response = request.responseAsync().get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof SecurityException) {
                        throw (SecurityException)e.getCause();
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }),
            // (14) async version of test 1
            test(true, () -> { //Policy 1
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                try {
                    HttpResponse response = request.responseAsync().get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof SecurityException) {
                        throw (SecurityException)e.getCause();
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }),
            // (15) check that user provided unprivileged code running on a worker
            //      thread does not gain ungranted privileges.
            test(false, () -> { //Policy 12
                URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
                HttpRequest request = client.request(u)
                        .GET();
                HttpResponse response = request.response();
                HttpResponse.BodyProcessor<String> stproc = HttpResponse.asString();

                CompletableFuture<String> cf;
                cf = response.bodyAsync(new HttpResponse.BodyProcessor<String>() {
                        public void onResponseBodyChunk(ByteBuffer b) throws IOException {
                            // do some mischief here
                            SecurityManager sm = System.getSecurityManager();
                            System.setSecurityManager(null);
                            System.setSecurityManager(sm);
                            // problem if we get this far
                            stproc.onResponseBodyChunk(b);
                        }
                        public String onResponseBodyStart(long contentLength,
                                        HttpHeaders responseHeaders,
                                        LongConsumer fc) throws IOException {

                            SecurityManager sm = System.getSecurityManager();
                            // should succeed.
                            sm.checkPermission(new RuntimePermission("foobar"));
                            return stproc.onResponseBodyStart(contentLength,responseHeaders, fc);
                        }
                        public String onResponseComplete() throws IOException {
                            return stproc.onResponseComplete();
                        }
                        public void onResponseError(Throwable t) {
                            stproc.onResponseError(t);
                        }
                    }
                );
                try {
                    System.out.println("Body = " + cf.get());// should not reach here
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof SecurityException) {
                        throw (SecurityException)e.getCause();
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            })
        };
    }

    private static void directProxyTest(int proxyPort, boolean samePort) throws IOException, InterruptedException {
        Object proxy = null;
        try {
            proxy = getProxy(proxyPort, true);
        } catch (IOException e) {
            System.out.println("Cannot bind. Not running test");
            throw new SecurityException("test not run");
        } catch (Exception ee) {
            throw new RuntimeException(ee);
        }
        System.out.println("Proxy port = " + proxyPort);
        if (!samePort)
            proxyPort++;

        HttpClient cl = HttpClient.create()
                .proxy(ProxySelector.of(
                                new InetSocketAddress("127.0.0.1", proxyPort)))
                .build();
        clients.add(cl);

        URI u = URI.create("http://127.0.0.1:" + port + "/files/foo.txt");
        HttpRequest request = cl.request(u)
                .headers("X-Foo", "bar", "X-Bar", "foo")
                .GET();
        HttpResponse response = request.response();
    }

    static void runtest(Test r, String policy, boolean succeeds) {
        System.out.println("Using policy file: " + policy);
        try {
            r.execute();
            if (!succeeds) {
                System.out.println("FAILED: expected security exception");
                throw new RuntimeException("Failed");
            }
            System.out.println (policy + " succeeded as expected");
        } catch (SecurityException e) {
            if (succeeds) {
                System.out.println("FAILED");
                throw new RuntimeException(e);
            }
            System.out.println (policy + " threw exception as expected");
        } catch (IOException | InterruptedException ee) {
            throw new RuntimeException(ee);
        }
    }

    public static void main(String[] args) throws Exception {
        initServer();
        setupProxy();
        fileroot = System.getProperty ("test.src")+ "/docs";
        int testnum = Integer.parseInt(args[0]);
        String policy = args[0];

        client = HttpClient
            .create()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

        clients.add(HttpClient.getDefault());
        clients.add(client);

        try {
            setupTests();
            TestAndResult tr = tests[testnum];
            runtest(tr.test, policy, tr.result);
        } finally {
            s1.stop(0);
            //executor.shutdownNow();
            for (HttpClient client : clients)
                client.executorService().shutdownNow();
        }
    }

    // create Http Server on port range below. So, we can
    HttpServer createServer() {
        HttpServer server;
        for (int i=25800; i<26800; i++) {
            InetSocketAddress a = new InetSocketAddress(i);
            try {
                server = HttpServer.create(a, 0);
                return server;
            } catch (IOException e) {}
        }
        return null;
    }

    public static void initServer() throws Exception {
        Logger logger = Logger.getLogger("com.sun.net.httpserver");
        ConsoleHandler ch = new ConsoleHandler();
        logger.setLevel(Level.ALL);
        ch.setLevel(Level.ALL);
        logger.addHandler(ch);
        String root = System.getProperty ("test.src")+ "/docs";
        InetSocketAddress addr = new InetSocketAddress (0);
        s1 = HttpServer.create (addr, 0);
        if (s1 instanceof HttpsServer) {
            throw new RuntimeException ("should not be httpsserver");
        }
        HttpHandler h = new FileServerHandler (root);
        HttpContext c = s1.createContext ("/files", h);

        HttpHandler h1 = new RedirectHandler ("/redirect");
        HttpContext c1 = s1.createContext ("/redirect", h1);

        executor = Executors.newCachedThreadPool();
        s1.setExecutor (executor);
        s1.start();

        port = s1.getAddress().getPort();
        System.out.println("HTTP server port = " + port);
        httproot = "http://127.0.0.1:" + port + "/files/";
        redirectroot = "http://127.0.0.1:" + port + "/redirect/";
        uri = new URI(httproot);
        fileuri = httproot + "foo.txt";
    }

    static class RedirectHandler implements HttpHandler {

        String root;
        int count = 0;

        RedirectHandler(String root) {
            this.root = root;
        }

        synchronized int count() {
            return count;
        }

        synchronized void increment() {
            count++;
        }

        @Override
        public synchronized void handle(HttpExchange t)
                throws IOException {
            byte[] buf = new byte[2048];
            System.out.println("Server: " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                while (is.read(buf) != -1) ;
            }
            increment();
            if (count() == 1) {
                Headers map = t.getResponseHeaders();
                String redirect = "/redirect/bar.txt";
                map.add("Location", redirect);
                t.sendResponseHeaders(301, -1);
                t.close();
            } else {
                String response = "Hello world";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes(StandardCharsets.ISO_8859_1));
                t.close();
            }
        }
    }
}
