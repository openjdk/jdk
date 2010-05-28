/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4258697
 * @summary Make sure that http CONTINUE status followed by invalid
 * response doesn't cause HttpClient to recursively loop and
 * eventually StackOverflow.
 *
 */
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;

public class HttpContinueStackOverflow {

    static class Server implements Runnable {
        int port;

        Server(int port) {
            this.port = port;
        }

        public void run() {
            try {
                /* bind to port and wait for connection */
                ServerSocket serverSock = new ServerSocket(     port );
                serverSock.setSoTimeout(10000);
                Socket sock = serverSock.accept();

                /* setup streams and read http request */
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(sock.getInputStream()));
                PrintStream out = new PrintStream( sock.getOutputStream() );
                String request = in.readLine();

                /* send continue followed by invalid response */
                out.println("HTTP/1.1 100 Continue\r");
                out.println("\r");
                out.println("junk junk junk");
                out.flush();

                sock.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    HttpContinueStackOverflow(int port) throws Exception {
        /* create the server */
        Server s = new Server(port);
        Thread thr = new Thread(s);
        thr.start();

        /* wait for server to bind to port */
        try {
            Thread.currentThread().sleep(2000);
        } catch (Exception e) { }

        /* connect to server, connect to server and get response code */
        URL url = new URL("http", "localhost", port, "anything.html");
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        int respCode = conn.getResponseCode();
        System.out.println("TEST PASSED");
    }

    public static void main(String args[]) throws Exception {
        int port = 4090;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        System.out.println("Testing 100-Continue");
        new HttpContinueStackOverflow(port);
    }
}
