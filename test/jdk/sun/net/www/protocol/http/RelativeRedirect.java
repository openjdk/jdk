/*
 * Copyright (c) 2002, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4726087
 * @library /test/lib
 * @run main/othervm RelativeRedirect
 * @run main/othervm -Djava.net.preferIPv6Addresses=true RelativeRedirect
 * @summary URLConnection cannot handle redirects
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class RelativeRedirect implements HttpHandler {
    static int count = 0;
    static HttpServer server;


    static class MyAuthenticator extends Authenticator {
        public MyAuthenticator () {
            super ();
        }

        public PasswordAuthentication getPasswordAuthentication ()
        {
            return (new PasswordAuthentication ("user", "Wrongpassword".toCharArray()));
        }
    }

    void firstReply(HttpExchange req) throws IOException {
        req.getResponseHeaders().set("Connection", "close");
        req.getResponseHeaders().set("Location", "/redirect/file.html");
        req.sendResponseHeaders(302, -1);
    }

    void secondReply (HttpExchange req) throws IOException {
        if (req.getRequestURI().toString().equals("/redirect/file.html") &&
            req.getRequestHeaders().get("Host").get(0).equals(authority(server.getAddress().getPort()))) {
            req.sendResponseHeaders(200, 0);
            try(PrintWriter pw = new PrintWriter(req.getResponseBody())) {
                pw.print("Hello .");
            }
        } else {
            req.sendResponseHeaders(400, 0);
            try(PrintWriter pw = new PrintWriter(req.getResponseBody())) {
                pw.print(req.getRequestURI().toString());
            }
        }
    }

    @Override
    public void handle (HttpExchange req) {
        try {
            switch (count) {
            case 0:
                // server redirect to /redirect/file.html
                firstReply (req);
                break;
            case 1:
                // client retry to /redirect/file.html on same server
                secondReply (req);
                break;
            }
            count ++;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

   static String authority(int port) {
       InetAddress loopback = InetAddress.getLoopbackAddress();
       String hostaddr = loopback.getHostAddress();
       if (hostaddr.indexOf(':') > -1) {
           hostaddr = "[" + hostaddr + "]";
       }
       return hostaddr + ":" + port;
   }

    public static void main (String[] args) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        MyAuthenticator auth = new MyAuthenticator ();
        Authenticator.setDefault (auth);
        try {
            server = HttpServer.create(new InetSocketAddress(loopback, 0), 10);
            server.createContext("/", new RelativeRedirect());
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            System.out.println ("Server: listening on port: " + server.getAddress().getPort());
            URL url = new URL("http://" + authority(server.getAddress().getPort()));
            System.out.println ("client opening connection to: " + url);
            HttpURLConnection urlc = (HttpURLConnection)url.openConnection (Proxy.NO_PROXY);
            InputStream is = urlc.getInputStream ();
            is.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (server != null) {
                server.stop(1);
            }
        }
    }
}
