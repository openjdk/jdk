/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4804309
 * @library /test/lib
 * @run main/othervm AuthHeaderTest
 * @summary AuthHeaderTest bug
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;

public class AuthHeaderTest {
    static HttpServer server;

    static void read (InputStream is) throws IOException {
        int c;
        System.out.println ("reading");
        while ((c=is.read()) != -1) {
            System.out.write (c);
        }
        System.out.println ("");
        System.out.println ("finished reading");
    }

    static void client (String u) throws Exception {
        URL url = new URL (u);
        System.out.println ("client opening connection to: " + u);
        URLConnection urlc = url.openConnection (Proxy.NO_PROXY);
        InputStream is = urlc.getInputStream ();
        read (is);
        is.close();
    }

    public static void main (String[] args) throws Exception {
        MyAuthenticator auth = new MyAuthenticator ();
        Authenticator.setDefault (auth);
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try {
            server = HttpServer.create(new InetSocketAddress(loopback, 0), 10, "/", new AuthHeaderTestHandler());
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            System.out.println ("Server: listening on port: " + server.getAddress().getPort());

            String serverURL = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(server.getAddress().getPort())
                    .path("/")
                    .build()
                    .toString();
            client (serverURL + "d1/foo.html");
        } catch (Exception e) {
            if (server != null) {
                server.stop(1);
            }
            throw e;
        }
        int f = auth.getCount();
        if (f != 1) {
            except ("Authenticator was called "+f+" times. Should be 1");
        }
        server.stop(1);
    }

    public static void except (String s) {
        server.stop(1);
        throw new RuntimeException (s);
    }

    static class MyAuthenticator extends Authenticator {
        MyAuthenticator () {
            super ();
        }

        int count = 0;

        public PasswordAuthentication getPasswordAuthentication () {
            PasswordAuthentication pw;
            pw = new PasswordAuthentication ("user", "pass2".toCharArray());
            count ++;
            return pw;
        }

        public int getCount () {
            return (count);
        }
    }
}

class AuthHeaderTestHandler implements HttpHandler {
    static int count = 0;
    static String authstring;

    void errorReply (HttpExchange req, String reply) throws IOException {
        req.getResponseHeaders().set("Connection", "close");
        req.getResponseHeaders().set("Www-authenticate", reply);
        req.sendResponseHeaders(401, -1);
    }

    void okReply (HttpExchange req) throws IOException {
        req.sendResponseHeaders (200, 0);
        try(PrintWriter pw = new PrintWriter(req.getResponseBody(), false, Charset.forName("UTF-8"))) {
            pw.print("Hello .");
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if(exchange.getRequestHeaders().get("Authorization") != null) {
                authstring = exchange.getRequestHeaders().get("Authorization").get(0);
                System.out.println (authstring);
            }

            switch (count) {
                case 0:
                    errorReply (exchange, "Basic realm=\"wallyworld\"");
                    break;
                case 1:
                    /* client stores a username/pw for wallyworld
                     */
                    okReply (exchange);
                    break;
            }
            count ++;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}