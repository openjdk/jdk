/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4759514
 * @modules java.base/sun.net.www
 * @library ../../../sun/net/www/httptest/
 * @build HttpCallback TestHttpServer ClosedChannelList HttpTransaction
 * @run main B4759514
 * @summary Digest Authentication is erroniously quoting the nc value, contrary to RFC 2617
 */

import java.io.*;
import java.net.*;

public class B4759514 implements HttpCallback {

    static int count = 0;
    static String authstring;

    void errorReply (HttpTransaction req, String reply) throws IOException {
        req.addResponseHeader ("Connection", "close");
        req.addResponseHeader ("WWW-Authenticate", reply);
        req.sendResponse (401, "Unauthorized");
        req.orderlyClose();
    }

    void okReply (HttpTransaction req) throws IOException {
        req.setResponseEntityBody ("Hello .");
        req.sendResponse (200, "Ok");
        req.orderlyClose();
    }

    public void request (HttpTransaction req) {
        try {
            authstring = req.getRequestHeader ("Authorization");
            switch (count) {
            case 0:
                errorReply (req, "Digest realm=\"wallyworld\", nonce=\"1234\", domain=\"/\"");
                break;
            case 1:
                int n = authstring.indexOf ("nc=");
                if (n != -1) {
                    if (authstring.charAt (n+3) == '\"') {
                        req.sendResponse (400, "Bad Request");
                        break;
                    }
                }
                okReply (req);
                break;
            }
            count ++;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void read (InputStream is) throws IOException {
        int c;
        while ((c=is.read()) != -1) {
            System.out.write (c);
        }
    }

    static void client (String u) throws Exception {
        URL url = new URL (u);
        System.out.println ("client opening connection to: " + u);
        URLConnection urlc = url.openConnection ();
        InputStream is = urlc.getInputStream ();
        read (is);
        is.close();
    }

    static TestHttpServer server;

    public static void main (String[] args) throws Exception {
        MyAuthenticator auth = new MyAuthenticator ();
        Authenticator.setDefault (auth);
        try {
            server = new TestHttpServer (new B4759514(), 1, 10, 0);
            System.out.println ("Server: listening on port: " + server.getLocalPort());
            client ("http://localhost:"+server.getLocalPort()+"/d1/foo.html");
        } catch (Exception e) {
            if (server != null) {
                server.terminate();
            }
            throw e;
        }
        int f = auth.getCount();
        if (f != 1) {
            except ("Authenticator was called "+f+" times. Should be 1");
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

        int count = 0;

        public PasswordAuthentication getPasswordAuthentication () {
            PasswordAuthentication pw;
            pw = new PasswordAuthentication ("user", "pass1".toCharArray());
            count ++;
            return pw;
        }

        public int getCount () {
            return (count);
        }
    }
}
