/*
 * Copyright (c) 2002, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4722333
 * @modules java.base/sun.net.www
 * @library ../../../sun/net/www/httptest/
 * @build HttpCallback TestHttpServer ClosedChannelList HttpTransaction
 * @run main B4722333
 * @summary JRE Proxy Authentication Not Working with ISA2000
 */

import java.io.*;
import java.net.*;

public class B4722333 implements HttpCallback {

    static int count = 0;

    static String [][] expected = {
       /* scheme  realm/prompt */
        {"basic", "foo"},
        {"basic", "foobar"},
        {"digest", "biz"},
        {"digest", "bizbar"},
        {"digest", "foobiz"}
    };

    public void request (HttpTransaction req) {
        try {
            if (count % 2 == 1 ) {
                req.setResponseEntityBody ("Hello .");
                req.sendResponse (200, "Ok");
                req.orderlyClose();
            } else {
                switch (count) {
                  case 0:
                    req.addResponseHeader ("Connection", "close");
                    req.addResponseHeader ("WWW-Authenticate", "Basic realm=\"foo\"");
                    req.addResponseHeader ("WWW-Authenticate", "Foo realm=\"bar\"");
                    req.sendResponse (401, "Unauthorized");
                    req.orderlyClose();
                    break;
                  case 2:
                    req.addResponseHeader ("Connection", "close");
                    req.addResponseHeader ("WWW-Authenticate", "Basic realm=\"foobar\" Foo realm=\"bar\"");
                    req.sendResponse (401, "Unauthorized");
                    break;
                  case 4:
                    req.addResponseHeader ("Connection", "close");
                    req.addResponseHeader ("WWW-Authenticate", "Digest realm=biz domain=/foo nonce=thisisanonce ");
                    req.addResponseHeader ("WWW-Authenticate", "Basic realm=bizbar");
                    req.sendResponse (401, "Unauthorized");
                    req.orderlyClose();
                    break;
                  case 6:
                    req.addResponseHeader ("Connection", "close");
                    req.addResponseHeader ("WWW-Authenticate", "Digest realm=\"bizbar\" domain=/biz nonce=\"hereisanonce\" Basic realm=\"foobar\" Foo realm=\"bar\"");
                    req.sendResponse (401, "Unauthorized");
                    req.orderlyClose();
                    break;
                  case 8:
                    req.addResponseHeader ("Connection", "close");
                    req.addResponseHeader ("WWW-Authenticate", "Foo p1=1 p2=2 p3=3 p4=4 p5=5 p6=6 p7=7 p8=8 p9=10 Digest realm=foobiz domain=/foobiz nonce=newnonce");
                    req.addResponseHeader ("WWW-Authenticate", "Basic realm=bizbar");
                    req.sendResponse (401, "Unauthorized");
                    req.orderlyClose();
                    break;
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

    static TestHttpServer server;

    public static void main (String[] args) throws Exception {
        MyAuthenticator auth = new MyAuthenticator ();
        Authenticator.setDefault (auth);
        try {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            server = new TestHttpServer (new B4722333(), 1, 10, loopback, 0);
            System.out.println ("Server started: listening on port: " + server.getLocalPort());
            client ("http://" + server.getAuthority() + "/d1/d2/d3/foo.html");
            client ("http://" + server.getAuthority() + "/ASD/d3/x.html");
            client ("http://" + server.getAuthority() + "/biz/d3/x.html");
            client ("http://" + server.getAuthority() + "/bar/d3/x.html");
            client ("http://" + server.getAuthority() + "/fuzz/d3/x.html");
        } catch (Exception e) {
            if (server != null) {
                server.terminate();
            }
            throw e;
        }
        int f = auth.getCount();
        if (f != expected.length) {
            except ("Authenticator was called "+f+" times. Should be " + expected.length);
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

        public PasswordAuthentication getPasswordAuthentication ()
            {
            System.out.println ("Auth called");
            String scheme = getRequestingScheme();
            System.out.println ("getRequestingScheme() returns " + scheme);
            String prompt = getRequestingPrompt();
            System.out.println ("getRequestingPrompt() returns " + prompt);

            if (!scheme.equals (expected [count][0])) {
                B4722333.except ("wrong scheme received, " + scheme + " expected " + expected [count][0]);
            }
            if (!prompt.equals (expected [count][1])) {
                B4722333.except ("wrong realm received, " + prompt + " expected " + expected [count][1]);
            }
            count ++;
            return (new PasswordAuthentication ("user", "passwordNotCheckedAnyway".toCharArray()));
        }

        public int getCount () {
            return (count);
        }
    }

}
