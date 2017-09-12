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
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import jdk.incubator.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import static java.lang.System.out;
import static jdk.incubator.http.HttpResponse.BodyHandler.discard;

/**
 * @test
 * @summary Basic tests for response timeouts
 * @run main/othervm TimeoutBasic
 * @ignore
 */

public class TimeoutBasic {

    static List<Duration> TIMEOUTS = List.of(/*Duration.ofSeconds(1),
                                             Duration.ofMillis(100),*/
                                             Duration.ofNanos(99)
                                            /* Duration.ofNanos(1)*/);

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        try (ServerSocket ss = new ServerSocket(0, 20)) {
            int port = ss.getLocalPort();
            URI uri = new URI("http://127.0.0.1:" + port + "/");

//            out.println("--- TESTING Async");
//            for (Duration duration : TIMEOUTS) {
//                out.println("  with duration of " + duration);
//                HttpRequest request = HttpRequest.newBuilder(uri)
//                                                 .timeout(duration)
//                                                 .GET().build();
//                try {
//                    HttpResponse<?> resp = client.sendAsync(request, discard(null)).join();
//                    throw new RuntimeException("Unexpected response: " + resp.statusCode());
//                } catch (CompletionException e) {
//                    if (!(e.getCause() instanceof HttpTimeoutException)) {
//                        throw new RuntimeException("Unexpected exception: " + e.getCause());
//                    } else {
//                        out.println("Caught expected timeout: " + e.getCause());
//                    }
//                }
//            }

            out.println("--- TESTING Sync");
            for (Duration duration : TIMEOUTS) {
                out.println("  with duration of " + duration);
                HttpRequest request = HttpRequest.newBuilder(uri)
                                                 .timeout(duration)
                                                 .GET()
                                                 .build();
                try {
                    client.send(request, discard(null));
                } catch (HttpTimeoutException e) {
                    out.println("Caught expected timeout: " + e);
                }
            }
        } finally {
            ((ExecutorService) client.executor()).shutdownNow();
        }
    }
}
