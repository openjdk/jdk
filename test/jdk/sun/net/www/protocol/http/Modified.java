/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4092605
 * @summary Test HttpURLConnection setIfModifiedSince
 *
 */

import java.net.*;
import java.io.*;

public class Modified implements Runnable {

    ServerSocket ss;

    public void run() {
        try {
            Socket s = ss.accept();
            boolean gotIfModified = false;

            BufferedReader in = new BufferedReader(
                new InputStreamReader(s.getInputStream()) );

            String str = null;
            do {
                str = in.readLine();
                if (str.startsWith("If-Modified-Since")) {
                    gotIfModified = true;
                }
                if (str.equals("")) {
                    break;
                }
            } while (str != null);

            PrintStream out = new PrintStream(
                                 new BufferedOutputStream(
                                    s.getOutputStream() ));

            if (gotIfModified) {
                out.print("HTTP/1.1 304 Not Modified\r\n");
            } else {
                out.print("HTTP/1.1 200 OK\r\n");
            }

            out.print("Content-Type: text/html\r\n");
            out.print("Connection: close\r\n");
            out.print("\r\n");
            out.flush();

            s.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    Modified() throws Exception {

        ss = new ServerSocket(0);
        Thread thr = new Thread(this);
        thr.start();

        URL testURL = new URL("http://localhost:" + ss.getLocalPort() +
                              "/index.html");
        URLConnection URLConn = testURL.openConnection();
        HttpURLConnection httpConn;

        if (URLConn instanceof HttpURLConnection) {
            httpConn = (HttpURLConnection)URLConn;
            httpConn.setAllowUserInteraction(false);
            httpConn.setIfModifiedSince(9990000000000L);
            int response = httpConn.getResponseCode();
            if (response != 304)
                throw new RuntimeException("setModifiedSince failure.");
        }
    }

    public static void main(String args[]) throws Exception {
        new Modified();
    }
}
