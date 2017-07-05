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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * @test
 * @bug 8087112
 * @build Server
 * @run main/othervm -Djava.net.HttpClient.log=all QuickResponses
 */

/**
 * Tests the buffering of data on connections across multiple
 * responses
 */
public class QuickResponses {

    static Server server;

    static String response(String body) {
        return "HTTP/1.1 200 OK\r\nContent-length: " + Integer.toString(body.length())
                + "\r\n\r\n" + body;
    }

    static final String responses[] = {
        "Lorem ipsum",
        "dolor sit amet",
        "consectetur adipiscing elit, sed do eiusmod tempor",
        "quis nostrud exercitation ullamco",
        "laboris nisi",
        "ut",
        "aliquip ex ea commodo consequat." +
        "Duis aute irure dolor in reprehenderit in voluptate velit esse" +
        "cillum dolore eu fugiat nulla pariatur.",
        "Excepteur sint occaecat cupidatat non proident."
    };

    static String entireResponse() {
        String s = "";
        for (String r : responses) {
            s += response(r);
        }
        return s;
    }

    public static void main(String[] args) throws Exception {
        server = new Server(0);
        URI uri = new URI(server.getURL());

        HttpRequest request = HttpRequest.create(uri)
                .GET();

        CompletableFuture<HttpResponse> cf1 = request.responseAsync();
        Server.Connection s1 = server.activity();
        s1.send(entireResponse());


        HttpResponse r = cf1.join();
        if (r.statusCode()!= 200 || !r.body(HttpResponse.asString()).equals(responses[0]))
            throw new RuntimeException("Failed on first response");

        //now get the same identical response, synchronously to ensure same connection
        int remaining = responses.length - 1;

        for (int i=0; i<remaining; i++) {
            r = HttpRequest.create(uri)
                    .GET()
                    .response();
            if (r.statusCode()!= 200)
                throw new RuntimeException("Failed");

            String body = r.body(HttpResponse.asString());
            if (!body.equals(responses[i+1]))
                throw new RuntimeException("Failed");
        }
        HttpClient.getDefault().executorService().shutdownNow();
        System.out.println("OK");
    }
}
