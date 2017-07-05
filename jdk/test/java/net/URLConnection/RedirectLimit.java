/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4458085
 * @summary  Redirects Limited to 5
 */

/*
 * Simulate a server that redirects ( to a different URL) 9 times
 * and see if the client correctly follows the trail
 */

import java.io.*;
import java.net.*;

class RedirLimitServer extends Thread {

    ServerSocket s;
    Socket   s1;
    InputStream  is;
    OutputStream os;
    int port;
    int nredirects = 9;

    String reply1 = "HTTP/1.1 307 Temporary Redirect\r\n" +
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Location: http://localhost:";
    String reply2 = ".html\r\n" +
        "Connection: close\r\n" +
        "Content-Type: text/html; charset=iso-8859-1\r\n\r\n" +
        "<html>Hello</html>";

    RedirLimitServer (ServerSocket y) {
        s = y;
        port = s.getLocalPort();
    }

    String reply3 = "HTTP/1.1 200 Ok\r\n" +
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Connection: close\r\n" +
        "Content-Type: text/html; charset=iso-8859-1\r\n\r\n" +
        "World";

    public void run () {
        try {
            s.setSoTimeout (2000);
            for (int i=0; i<nredirects; i++) {
                s1 = s.accept ();
                s1.setSoTimeout (2000);
                is = s1.getInputStream ();
                os = s1.getOutputStream ();
                is.read ();
                String reply = reply1 + port + "/redirect" + i + reply2;
                os.write (reply.getBytes());
                os.close();
            }
            s1 = s.accept ();
            is = s1.getInputStream ();
            os = s1.getOutputStream ();
            is.read ();
            os.write (reply3.getBytes());
            os.close();
        }
        catch (Exception e) {
            /* Just need thread to terminate */
        } finally {
            try { s.close(); } catch (IOException unused) {}
        }
    }
};


public class RedirectLimit {

    public static final int DELAY = 10;

    public static void main(String[] args) throws Exception {
        int nLoops = 1;
        int nSize = 10;
        int port, n =0;
        byte b[] = new byte[nSize];
        RedirLimitServer server;
        ServerSocket sock;

        try {
            sock = new ServerSocket (0);
            port = sock.getLocalPort ();
        }
        catch (Exception e) {
            System.out.println ("Exception: " + e);
            return;
        }

        server = new RedirLimitServer(sock);
        server.start ();

        try  {

            String s = "http://localhost:" + port;
            URL url = new URL(s);
            URLConnection conURL =  url.openConnection();

            conURL.setDoInput(true);
            conURL.setAllowUserInteraction(false);
            conURL.setUseCaches(false);

            InputStream in = conURL.getInputStream();
            if ((in.read() != (int)'W') || (in.read()!=(int)'o')) {
                throw new RuntimeException ("Unexpected string read");
            }
        }
        catch(IOException e) {
            throw new RuntimeException ("Exception caught " + e);
        }
    }
}
