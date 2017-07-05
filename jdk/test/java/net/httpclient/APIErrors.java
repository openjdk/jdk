/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8087112
 * @library /lib/testlibrary/
 * @build jdk.testlibrary.SimpleSSLContext ProxyServer
 * @compile ../../../com/sun/net/httpserver/LogFilter.java
 * @compile ../../../com/sun/net/httpserver/FileServerHandler.java
 * @run main/othervm APIErrors
 */
//package javaapplication16;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Does stupid things with API, to check appropriate errors/exceptions thrown
 */
public class APIErrors {

    static HttpServer s1 = null;
    static ExecutorService executor = null;
    static int port;
    static HttpClient client;
    static String httproot, fileuri, fileroot;
    static List<HttpClient> clients = new LinkedList<>();

    public static void main(String[] args) throws Exception {
        initServer();
        fileroot = System.getProperty("test.src") + "/docs";

        client = HttpClient.create().build();

        clients.add(HttpClient.getDefault());

        try {
            test1();
            test2();
            test3();
        } finally {
            s1.stop(0);
            executor.shutdownNow();
            for (HttpClient client : clients)
                client.executorService().shutdownNow();
        }
    }

    static void reject(Runnable r, Class<? extends Exception> extype) {
        try {
            r.run();
            throw new RuntimeException("Expected: " + extype);
        } catch (Throwable t) {
            if (!extype.isAssignableFrom(t.getClass())) {
                throw new RuntimeException("Wrong exception type: " + extype + " / "
                    +t.getClass());
            }
        }
    }

    static void accept(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            throw new RuntimeException("Unexpected exception: " + t);
        }
    }

    static void checkNonNull(Supplier<?> r) {
        if (r.get() == null)
            throw new RuntimeException("Unexpected null return:");
    }

    static void assertTrue(Supplier<Boolean> r) {
        if (r.get() == false)
            throw new RuntimeException("Assertion failure:");
    }

    // HttpClient.Builder
    static void test1() throws Exception {
        System.out.println("Test 1");
        HttpClient.Builder cb = HttpClient.create();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 5000);
        reject(() -> { cb.priority(-1);}, IllegalArgumentException.class);
        reject(() -> { cb.priority(500);}, IllegalArgumentException.class);
        accept(() -> { cb.priority(1);});
        accept(() -> { cb.priority(255);});

        accept(() -> {clients.add(cb.build()); clients.add(cb.build());});
    }

    static void test2() throws Exception {
        System.out.println("Test 2");
        HttpClient.Builder cb = HttpClient.create();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 5000);
        cb.proxy(ProxySelector.of(addr));
        HttpClient c = cb.build();
        clients.add(c);
        checkNonNull(()-> {return c.executorService();});
        assertTrue(()-> {return c.followRedirects() == HttpClient.Redirect.NEVER;});
        assertTrue(()-> {return !c.authenticator().isPresent();});
    }

    static URI accessibleURI() {
        return URI.create(fileuri);
    }

    static HttpRequest request() {
        return HttpRequest.create(accessibleURI())
                .GET();
    }

    static void test3() throws Exception {
        System.out.println("Test 3");
        reject(()-> {
            try {
                HttpRequest r1 = request();
                HttpResponse resp = r1.response();
                HttpResponse resp1 = r1.response();
            } catch (IOException |InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, IllegalStateException.class);

        reject(()-> {
            try {
                HttpRequest r1 = request();
                HttpResponse resp = r1.response();
                HttpResponse resp1 = r1.responseAsync().get();
            } catch (IOException |InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, IllegalStateException.class);
        reject(()-> {
            try {
                HttpRequest r1 = request();
                HttpResponse resp1 = r1.responseAsync().get();
                HttpResponse resp = r1.response();
            } catch (IOException |InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, IllegalStateException.class);
    }

    static class Auth extends java.net.Authenticator {
        int count = 0;
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (count++ == 0) {
                return new PasswordAuthentication("user", "passwd".toCharArray());
            } else {
                return new PasswordAuthentication("user", "goober".toCharArray());
            }
        }
        int count() {
            return count;
        }
    }

    public static void initServer() throws Exception {
        String root = System.getProperty ("test.src")+ "/docs";
        InetSocketAddress addr = new InetSocketAddress (0);
        s1 = HttpServer.create (addr, 0);
        if (s1 instanceof HttpsServer) {
            throw new RuntimeException ("should not be httpsserver");
        }
        HttpHandler h = new FileServerHandler(root);

        HttpContext c1 = s1.createContext("/files", h);

        executor = Executors.newCachedThreadPool();
        s1.setExecutor (executor);
        s1.start();

        port = s1.getAddress().getPort();
        System.out.println("HTTP server port = " + port);
        httproot = "http://127.0.0.1:" + port + "/files/";
        fileuri = httproot + "foo.txt";
    }
}
