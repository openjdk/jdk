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

/*
 * @test
 * @bug 8087112
 * @modules jdk.incubator.httpclient
 *          java.logging
 *          jdk.httpserver
 * @library /lib/testlibrary/
 * @build jdk.testlibrary.SimpleSSLContext ProxyServer
 * @build TestKit
 * @compile ../../../com/sun/net/httpserver/LogFilter.java
 * @compile ../../../com/sun/net/httpserver/FileServerHandler.java
 * @run main/othervm APIErrors
 * @summary  Basic checks for appropriate errors/exceptions thrown from the API
 */

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import static jdk.incubator.http.HttpResponse.BodyHandler.discard;

public class APIErrors {

    static ExecutorService serverExecutor = Executors.newCachedThreadPool();
    static String httproot, fileuri;
    static List<HttpClient> clients = new LinkedList<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = createServer();

        int port = server.getAddress().getPort();
        System.out.println("HTTP server port = " + port);

        httproot = "http://127.0.0.1:" + port + "/files/";
        fileuri = httproot + "foo.txt";

        HttpClient client = HttpClient.newHttpClient();

        try {
            test1();
            test2();
            //test3();
        } finally {
            server.stop(0);
            serverExecutor.shutdownNow();
            for (HttpClient c : clients)
                ((ExecutorService)c.executor()).shutdownNow();
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
        HttpClient.Builder cb = HttpClient.newBuilder();
        TestKit.assertThrows(IllegalArgumentException.class, () -> cb.priority(-1));
        TestKit.assertThrows(IllegalArgumentException.class, () -> cb.priority(0));
        TestKit.assertThrows(IllegalArgumentException.class, () -> cb.priority(257));
        TestKit.assertThrows(IllegalArgumentException.class, () -> cb.priority(500));
        TestKit.assertNotThrows(() -> cb.priority(1));
        TestKit.assertNotThrows(() -> cb.priority(256));
        TestKit.assertNotThrows(() -> {
            clients.add(cb.build());
            clients.add(cb.build());
        });
    }

    static void test2() throws Exception {
        System.out.println("Test 2");
        HttpClient.Builder cb = HttpClient.newBuilder();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 5000);
        cb.proxy(ProxySelector.of(addr));
        HttpClient c = cb.build();
        clients.add(c);
        checkNonNull(()-> c.executor());
        assertTrue(()-> c.followRedirects() == HttpClient.Redirect.NEVER);
        assertTrue(()-> !c.authenticator().isPresent());
    }

    static URI accessibleURI() {
        return URI.create(fileuri);
    }

    static HttpRequest request() {
        return HttpRequest.newBuilder(accessibleURI()).GET().build();
    }

//    static void test3() throws Exception {
//        System.out.println("Test 3");
//        TestKit.assertThrows(IllegalStateException.class, ()-> {
//            try {
//                HttpRequest r1 = request();
//                HttpResponse<Object> resp = r1.response(discard(null));
//                HttpResponse<Object> resp1 = r1.response(discard(null));
//            } catch (IOException |InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        });
//
//        TestKit.assertThrows(IllegalStateException.class, ()-> {
//            try {
//                HttpRequest r1 = request();
//                HttpResponse<Object> resp = r1.response(discard(null));
//                HttpResponse<Object> resp1 = r1.responseAsync(discard(null)).get();
//            } catch (IOException |InterruptedException | ExecutionException e) {
//                throw new RuntimeException(e);
//            }
//        });
//        TestKit.assertThrows(IllegalStateException.class, ()-> {
//            try {
//                HttpRequest r1 = request();
//                HttpResponse<Object> resp1 = r1.responseAsync(discard(null)).get();
//                HttpResponse<Object> resp = r1.response(discard(null));
//            } catch (IOException |InterruptedException | ExecutionException e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }

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

    static HttpServer createServer() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(0), 0);
        if (s instanceof HttpsServer)
            throw new RuntimeException ("should not be httpsserver");

        String root = System.getProperty("test.src") + "/docs";
        s.createContext("/files", new FileServerHandler(root));
        s.setExecutor(serverExecutor);
        s.start();

        return s;
    }
}
