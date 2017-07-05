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
 * @library /lib/testlibrary/ /
 * @build jdk.testlibrary.SimpleSSLContext EchoHandler
 * @compile ../../../com/sun/net/httpserver/LogFilter.java
 * @compile ../../../com/sun/net/httpserver/FileServerHandler.java
 * @run main/othervm/timeout=40 -Djava.net.http.HttpClient.log=ssl ManyRequests
 * @summary Send a large number of requests asynchronously
 */

//package javaapplication16;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.logging.*;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.*;
import jdk.testlibrary.SimpleSSLContext;

public class ManyRequests {

    volatile static int counter = 0;

    public static void main(String[] args) throws Exception {
        Logger logger = Logger.getLogger("com.sun.net.httpserver");
        logger.setLevel(Level.ALL);
        logger.info("TEST");

        SSLContext ctx = new SimpleSSLContext().get();

        InetSocketAddress addr = new InetSocketAddress(0);
        HttpsServer server = HttpsServer.create(addr, 0);
        server.setHttpsConfigurator(new Configurator(ctx));

        HttpClient client = HttpClient.create()
                                      .sslContext(ctx)
                                      .build();
        try {
            test(server, client);
            System.out.println("OK");
        } finally {
            server.stop(0);
            client.executorService().shutdownNow();
        }
    }

    //static final int REQUESTS = 1000;
    static final int REQUESTS = 20;

    static void test(HttpsServer server, HttpClient client) throws Exception {
        int port = server.getAddress().getPort();
        URI uri = new URI("https://127.0.0.1:" + port + "/foo/x");
        server.createContext("/foo", new EchoHandler());
        server.start();

        RequestLimiter limiter = new RequestLimiter(40);
        Random rand = new Random();
        CompletableFuture<Void>[] results = new CompletableFuture[REQUESTS];
        HashMap<HttpRequest,byte[]> bodies = new HashMap<>();

        for (int i=0; i<REQUESTS; i++) {
            byte[] buf = new byte[i+1];  // different size bodies
            rand.nextBytes(buf);
            HttpRequest r = client.request(uri)
                                  .body(HttpRequest.fromByteArray(buf))
                                  .POST();
            bodies.put(r, buf);

            results[i] =
                limiter.whenOkToSend()
                       .thenCompose((v) -> r.responseAsync())
                       .thenCompose((resp) -> {
                           limiter.requestComplete();
                           if (resp.statusCode() != 200) {
                               resp.bodyAsync(HttpResponse.ignoreBody());
                               String s = "Expected 200, got: " + resp.statusCode();
                               return completedWithIOException(s);
                           } else {
                               counter++;
                               System.out.println("Result from " + counter);
                           }
                           return resp.bodyAsync(HttpResponse.asByteArray())
                                      .thenApply((b) -> new Pair<>(resp, b));
                       })
                      .thenAccept((pair) -> {
                          HttpRequest request = pair.t.request();
                          byte[] requestBody = bodies.get(request);
                          check(Arrays.equals(requestBody, pair.u),
                                "bodies not equal");

                      });
        }

        // wait for them all to complete and throw exception in case of error
        //try {
            CompletableFuture.allOf(results).join();
        //} catch (Exception  e) {
            //e.printStackTrace();
            //throw e;
        //}
    }

    static <T> CompletableFuture<T> completedWithIOException(String message) {
        return CompletableFuture.failedFuture(new IOException(message));
    }

    static final class Pair<T,U> {
        Pair(T t, U u) {
            this.t = t; this.u = u;
        }
        T t;
        U u;
    }

    /**
     * A simple limiter for controlling the number of requests to be run in
     * parallel whenOkToSend() is called which returns a CF<Void> that allows
     * each individual request to proceed, or block temporarily (blocking occurs
     * on the waiters list here. As each request actually completes
     * requestComplete() is called to notify this object, and allow some
     * requests to continue.
     */
    static class RequestLimiter {

        static final CompletableFuture<Void> COMPLETED_FUTURE =
                CompletableFuture.completedFuture(null);

        final int maxnumber;
        final LinkedList<CompletableFuture<Void>> waiters;
        int number;
        boolean blocked;

        RequestLimiter(int maximum) {
            waiters = new LinkedList<>();
            maxnumber = maximum;
        }

        synchronized void requestComplete() {
            number--;
            // don't unblock until number of requests has halved.
            if ((blocked && number <= maxnumber / 2) ||
                        (!blocked && waiters.size() > 0)) {
                int toRelease = Math.min(maxnumber - number, waiters.size());
                for (int i=0; i<toRelease; i++) {
                    CompletableFuture<Void> f = waiters.remove();
                    number ++;
                    f.complete(null);
                }
                blocked = number >= maxnumber;
            }
        }

        synchronized CompletableFuture<Void> whenOkToSend() {
            if (blocked || number + 1 >= maxnumber) {
                blocked = true;
                CompletableFuture<Void> r = new CompletableFuture<>();
                waiters.add(r);
                return r;
            } else {
                number++;
                return COMPLETED_FUTURE;
            }
        }
    }

    static void check(boolean cond, Object... msg) {
        if (cond)
            return;
        StringBuilder sb = new StringBuilder();
        for (Object o : msg)
            sb.append(o);
        throw new RuntimeException(sb.toString());
    }
}

class Configurator extends HttpsConfigurator {
    public Configurator(SSLContext ctx) {
        super(ctx);
    }

    public void configure (HttpsParameters params) {
        params.setSSLParameters (getSSLContext().getSupportedSSLParameters());
    }
}

