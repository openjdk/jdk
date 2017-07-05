/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4962064
 * @library ../../../sun/net/www/httptest/
 * @build HttpCallback TestHttpServer ClosedChannelList HttpTransaction
 * @run main/othervm B4962064
 * @summary Extend Authenticator to provide access to request URI and server/proxy
 */

import java.io.*;
import java.net.*;

public class B4962064 implements HttpCallback {

    static int count = 0;

    public void request (HttpTransaction req) {
        try {
            switch (count) {
              case 0:
                req.addResponseHeader ("Connection", "close");
                req.addResponseHeader ("WWW-Authenticate", "Basic realm=\"foo\"");
                req.sendResponse (401, "Unauthorized");
                req.orderlyClose();
                break;
              case 1:
              case 3:
                req.setResponseEntityBody ("Hello .");
                req.sendResponse (200, "Ok");
                req.orderlyClose();
                break;
              case 2:
                req.addResponseHeader ("Connection", "close");
                req.addResponseHeader ("Proxy-Authenticate", "Basic realm=\"foo\"");
                req.sendResponse (407, "Proxy Authentication Required");
                req.orderlyClose();
                break;
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
    static URL urlsave;

    public static void main (String[] args) throws Exception {
        try {
            server = new TestHttpServer (new B4962064(), 1, 10, 0);
            int port = server.getLocalPort();
            System.setProperty ("http.proxyHost", "localhost");
            System.setProperty ("http.proxyPort", Integer.toString (port));
            MyAuthenticator auth = new MyAuthenticator ();
            Authenticator.setDefault (auth);
            System.out.println ("Server started: listening on port: " + port);
            //String s = new String ("http://localhost:"+port+"/d1/d2/d3/foo.html");
            String s = new String ("http://foo.com/d1/d2/d3/foo.html");
            urlsave = new URL (s);
            client (s);
            //s = new String ("http://localhost:"+port+"/dr/d3/foo.html");
            s = new String ("http://bar.com/dr/d3/foo.html");
            urlsave = new URL (s);
            client (s);
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
        int count = 0;
        MyAuthenticator () {
            super ();
        }

        public PasswordAuthentication getPasswordAuthentication () {
            URL url = getRequestingURL ();
            if (!url.equals (urlsave)) {
                except ("urls not equal");
            }
            Authenticator.RequestorType expected;
            if (count == 0) {
                expected = Authenticator.RequestorType.SERVER;
            } else {
                expected = Authenticator.RequestorType.PROXY;
            }
            if (getRequestorType() != expected) {
                except ("wrong authtype");
            }
            count ++;
            return (new PasswordAuthentication ("user", "passwordNotCheckedAnyway".toCharArray()));
        }

    }

}
