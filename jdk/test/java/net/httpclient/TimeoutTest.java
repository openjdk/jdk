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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @test
 * @bug 8087112
 * @run main/othervm TimeoutTest
 */

public class TimeoutTest {

    static int[] timeouts = {6, 4, 8, 6, 6, 4};
    static HttpRequest[] rqs = new HttpRequest[timeouts.length];
    static LinkedBlockingQueue<HttpRequest> queue = new LinkedBlockingQueue<>();
    static volatile boolean error = false;
    static ExecutorService executor = Executors.newCachedThreadPool();

    public static void main(String[] args) throws Exception {
        try {
            dotest();
        } finally {
            HttpClient.getDefault().executorService().shutdownNow();
            executor.shutdownNow();
        }
    }
    public static void dotest() throws Exception {
        System.out.println("Test takes over 40 seconds");
        ServerSocket ss = new ServerSocket(0, 20);
        int port = ss.getLocalPort();

        URI uri = new URI("http://127.0.0.1:" + Integer.toString(port) + "/foo");
        int i = 0;
        for (int timeout : timeouts) {
            HttpRequest request;
            (request = rqs[i] = HttpRequest.create(uri)
                .timeout(TimeUnit.SECONDS, timeout)
                .GET())
                .responseAsync()
                .whenComplete((HttpResponse r, Throwable t) -> {
                    if (!(t.getCause() instanceof HttpTimeoutException)) {
                        System.out.println("Wrong exception type:" + t.toString());
                        error = true;
                    }
                    if (t != null) {
                        queue.add(request);
                    }
                })
                .thenAccept((HttpResponse r) -> {
                    r.bodyAsync(HttpResponse.ignoreBody());
                });
            i++;
        }

        System.out.println("SUBMITTED");

        checkReturnOrder();

        if (error)
            throw new RuntimeException("Failed");

        // Repeat blocking in separate threads. Use queue to wait.
        System.out.println("DOING BLOCKING");

        i = 0;
        for (int timeout : timeouts) {
            HttpRequest req = HttpRequest.create(uri)
                .timeout(TimeUnit.SECONDS, timeout)
                .GET();
            rqs[i] = req;
            executor.execute(() -> {
                try {
                    req.response().body(HttpResponse.ignoreBody());
                } catch (HttpTimeoutException e) {
                    queue.offer(req);
                } catch (IOException | InterruptedException ee) {
                    error = true;
                }
            });
            i++;
        }

        checkReturnOrder();

        if (error)
            throw new RuntimeException("Failed");
    }

    static void checkReturnOrder() throws InterruptedException {
        // wait for exceptions and check order
        for (int j = 0; j < timeouts.length; j++) {
            HttpRequest req = queue.take();
            switch (j) {
                case 0:
                case 1:
                    if (req != rqs[1] && req != rqs[5]) {
                        System.out.printf("Expected 1 or 5. Got %s\n", getRequest(req));
                        throw new RuntimeException("Error");
                    }
                    break;
                case 2:
                case 3:
                case 4:
                    if (req != rqs[0] && req != rqs[3] && req != rqs[4]) {
                        System.out.printf("Expected r1, r4 or r5. Got %s\n", getRequest(req));
                        throw new RuntimeException("Error");
                    }
                    break;
                case 5:
                    if (req != rqs[2]) {
                        System.out.printf("Expected r3. Got %s\n", getRequest(req));
                        throw new RuntimeException("Error");
                    }
            }
        }
        System.out.println("Return order ok");
    }

    static String getRequest(HttpRequest req) {
        for (int i=0; i<rqs.length; i++) {
            if (req == rqs[i]) {
                return "[" + Integer.toString(i) + "]";
            }
        }
        return "unknown";
    }
}
