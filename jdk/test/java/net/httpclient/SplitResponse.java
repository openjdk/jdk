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

//package javaapplication16;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * @test
 * @bug 8087112
 * @build Server
 * @run main/othervm -Djava.net.HttpClient.log=all SplitResponse
 */

/**
 * Similar test to QuickResponses except that each byte of the response
 * is sent in a separate packet, which tests the stability of the implementation
 * for receiving unusual packet sizes.
 */
public class SplitResponse {

    static Server server;

    static String response(String body) {
        return "HTTP/1.1 200 OK\r\nConnection: Close\r\nContent-length: "
                + Integer.toString(body.length())
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

    public static void main(String[] args) throws Exception {
        server = new Server(0);
        URI uri = new URI(server.getURL());

        HttpRequest request;
        HttpResponse r;
        CompletableFuture<HttpResponse> cf1;

        for (int i=0; i<responses.length; i++) {
            cf1 = HttpRequest.create(uri)
                    .GET()
                    .responseAsync();
            String body = responses[i];

            Server.Connection c = server.activity();
            sendSplitResponse(response(body), c);
            r = cf1.get();
            if (r.statusCode()!= 200)
                throw new RuntimeException("Failed");

            String rxbody = r.body(HttpResponse.asString());
            System.out.println("received " + rxbody);
            if (!rxbody.equals(body))
                throw new RuntimeException("Failed");
            c.close();
        }
        HttpClient.getDefault().executorService().shutdownNow();
        System.out.println("OK");
    }

    // send the response one byte at a time with a small delay between bytes
    // to ensure that each byte is read in a separate read
    static void sendSplitResponse(String s, Server.Connection conn) {
        System.out.println("Sending: ");
        Thread t = new Thread(() -> {
            try {
                int len = s.length();
                for (int i = 0; i < len; i++) {
                    String onechar = s.substring(i, i + 1);
                    conn.send(onechar);
                    Thread.sleep(30);
                }
                System.out.println("sent");
            } catch (IOException | InterruptedException e) {
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
