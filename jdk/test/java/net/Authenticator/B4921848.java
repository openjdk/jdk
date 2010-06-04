/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4921848
 * @library ../../../sun/net/www/httptest/
 * @build HttpCallback HttpServer ClosedChannelList HttpTransaction
 * @run main/othervm -Dhttp.auth.preference=basic B4921848
 * @summary Allow user control over authentication schemes
 */

import java.io.*;
import java.net.*;

public class B4921848 implements HttpCallback {

    static int count = 0;

    public void request (HttpTransaction req) {
        try {
            if (count == 0 ) {
                req.addResponseHeader ("Connection", "close");
                req.addResponseHeader ("WWW-Authenticate", "Basic realm=\"foo\"");
                req.addResponseHeader ("WWW-Authenticate", "Digest realm=\"bar\" domain=/biz nonce=\"hereisanonce\"");
                req.sendResponse (401, "Unauthorized");
                req.orderlyClose();
            } else {
                String authheader = req.getRequestHeader ("Authorization");
                if (authheader.startsWith ("Basic")) {
                    req.setResponseEntityBody ("Hello .");
                    req.sendResponse (200, "Ok");
                    req.orderlyClose();
                } else {
                    req.sendResponse (400, "Bad Request");
                    req.orderlyClose();
                }
            }
            count ++;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
        URLConnection urlc = url.openConnection ();
        InputStream is = urlc.getInputStream ();
        read (is);
        is.close();
    }

    static HttpServer server;

    public static void main (String[] args) throws Exception {
        MyAuthenticator auth = new MyAuthenticator ();
        Authenticator.setDefault (auth);
        try {
            server = new HttpServer (new B4921848(), 1, 10, 0);
            System.out.println ("Server started: listening on port: " + server.getLocalPort());
            client ("http://localhost:"+server.getLocalPort()+"/d1/d2/d3/foo.html");
        } catch (Exception e) {
            if (server != null) {
                server.terminate();
            }
            throw e;
        }
        server.terminate();
    }

    public static void except (String s) {
        server.terminate();
        throw new RuntimeException (s);
    }

    static class MyAuthenticator extends Authenticator {
        MyAuthenticator () {
            super ();
        }

        public PasswordAuthentication getPasswordAuthentication () {
            return (new PasswordAuthentication ("user", "passwordNotCheckedAnyway".toCharArray()));
        }

    }

}
