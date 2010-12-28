/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4397096
 * @run main/othervm SetIfModifiedSince
 * @summary setIfModifiedSince() of HttpURLConnection sets invalid date of default locale
 */

import java.net.*;
import java.io.*;
import java.util.*;

public class SetIfModifiedSince {

    static class XServer extends Thread {
        ServerSocket srv;
        Socket s;
        InputStream is;
        OutputStream os;

        XServer (ServerSocket s) {
            srv = s;
        }

        Socket getSocket () {
            return (s);
        }

        public void run() {
            try {
                String x;
                s = srv.accept ();
                is = s.getInputStream ();
                BufferedReader r = new BufferedReader(new InputStreamReader(is));
                os = s.getOutputStream ();
                while ((x=r.readLine()) != null) {
                    String header = "If-Modified-Since: ";
                    if (x.startsWith(header)) {
                        if (x.charAt(header.length()) == '?') {
                            s.close ();
                            srv.close (); // or else the HTTPURLConnection will retry
                            throw new RuntimeException
                                    ("Invalid HTTP date specification");
                        }
                        break;
                    }
                }
                s.close ();
                srv.close (); // or else the HTTPURLConnection will retry
            } catch (IOException e) {}
        }
    }

    public static void main (String[] args) {
        try {
            Locale.setDefault(Locale.JAPAN);
            ServerSocket serversocket = new ServerSocket (0);
            int port = serversocket.getLocalPort ();
            XServer server = new XServer (serversocket);
            server.start ();
            Thread.sleep (2000);
            URL url = new URL ("http://localhost:"+port+"/index.html");
            URLConnection urlc = url.openConnection ();
            urlc.setIfModifiedSince (10000000);
            InputStream is = urlc.getInputStream ();
            int i=0, c;
            Thread.sleep (5000);
        } catch (Exception e) {
        }
    }
}
