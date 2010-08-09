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
 * @bug 4380568
 * @summary  HttpURLConnection does not support 307 redirects
 */
import java.io.*;
import java.net.*;

class RedirServer extends Thread {

    ServerSocket s;
    Socket   s1;
    InputStream  is;
    OutputStream os;
    int port;

    String reply1Part1 = "HTTP/1.1 307 Temporary Redirect\r\n" +
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Location: http://localhost:";
    String reply1Part2 = "/redirected.html\r\n" +
        "Connection: close\r\n" +
        "Content-Type: text/html; charset=iso-8859-1\r\n\r\n" +
        "<html>Hello</html>";

    RedirServer (ServerSocket y) {
        s = y;
        port = s.getLocalPort();
        System.out.println("Server created listening on " + port);
    }

    String reply2 = "HTTP/1.1 200 Ok\r\n" +
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Connection: close\r\n" +
        "Content-Type: text/html; charset=iso-8859-1\r\n\r\n" +
        "World";

    public void run () {
        try {
            s1 = s.accept ();
            is = s1.getInputStream ();
            os = s1.getOutputStream ();
            is.read ();
            String reply = reply1Part1 + port + reply1Part2;
            os.write (reply.getBytes());
            os.close();
            /* wait for redirected connection */
            s.setSoTimeout (5000);
            s1 = s.accept ();
            is = s1.getInputStream ();
            os = s1.getOutputStream ();
            is.read();
            os.write (reply2.getBytes());
            os.close();
        }
        catch (Exception e) {
            /* Just need thread to terminate */
            System.out.println("Server: caught " + e);
            e.printStackTrace();
        } finally {
            try { s.close(); } catch (IOException unused) {}
        }
    }
};


public class Redirect307Test {

    public static final int DELAY = 10;

    public static void main(String[] args) throws Exception {
        int port;
        RedirServer server;
        ServerSocket sock;

        try {
            sock = new ServerSocket (0);
            port = sock.getLocalPort ();
        }
        catch (Exception e) {
            System.out.println ("Exception: " + e);
            return;
        }

        server = new RedirServer(sock);
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
            e.printStackTrace();
            throw new RuntimeException ("Exception caught + " + e);
        }
    }
}
