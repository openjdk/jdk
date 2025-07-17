/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8335135
 * @library /test/lib
 * @summary Check that reading from inputStream throws an IOException
 *          if the fixed response stream is closed before reading all bytes.
 */

import jdk.test.lib.net.URIBuilder;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class TruncatedFixedResponse implements Runnable {

    ServerSocket ss;

    /*
     * Our "http" server to return a truncated fixed response
     */
    public void run() {
        try {
            Socket s = ss.accept();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream()));
            while (true) {
                String req = in.readLine();
                if (req.isEmpty()) {
                    break;
                }
            }
            PrintStream out = new PrintStream(
                    new BufferedOutputStream(s.getOutputStream()));

            /* send the header */
            out.print("HTTP/1.1 200\r\n");
            out.print("Content-Length: 100\r\n");
            out.print("Content-Type: text/html\r\n");
            out.print("\r\n");
            out.print("Some content, but too short");
            out.close();
            s.close();
            ss.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    TruncatedFixedResponse() throws Exception {
        /* start the server */
        ss = new ServerSocket();
        ss.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        new Thread(this).start();

        /* establish http connection to server */
        URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(ss.getLocalPort())
                .path("/foo")
                .toURL();
        HttpURLConnection http = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);

        try (InputStream in = http.getInputStream()) {
            while (in.read() != -1) {
                // discard response
            }
            throw new AssertionError("Expected IOException was not thrown");
        } catch (IOException ex) {
            System.out.println("Got expected exception: " + ex);
        }
    }

    public static void main(String args[]) throws Exception {
        new TruncatedFixedResponse();
    }
}
