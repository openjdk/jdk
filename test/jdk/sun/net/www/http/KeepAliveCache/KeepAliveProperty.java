/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @bug 8278067
 * @run main/othervm -Dhttp.keepAlive.time.server=30 KeepAliveProperty long
 * @run main/othervm -Dhttp.keepAlive.time.server=1 KeepAliveProperty short
 * @run main/othervm -ea -Dhttp.keepAlive.time.server=0 KeepAliveProperty short
 */

import java.net.*;
import java.io.*;
import java.nio.charset.*;
import java.util.logging.*;
import jdk.test.lib.net.URIBuilder;
import static java.net.Proxy.NO_PROXY;

public class KeepAliveProperty {

    static volatile boolean pass = false;

    static class Server extends Thread {
        final ServerSocket server;

        Server (ServerSocket server) {
            super ();
            this.server = server;
        }

        void readAll (Socket s) throws IOException {
            byte[] buf = new byte [128];
            int c;
            String request = "";
            InputStream is = s.getInputStream ();
            while ((c=is.read(buf)) > 0) {
                request += new String(buf, 0, c, StandardCharsets.US_ASCII);
                if (request.contains("\r\n\r\n")) {
                    return;
                }
            }
            if (c == -1)
                throw new IOException("Socket closed");
        }

        Socket s = null;
        String BODY;
        String CLEN;
        PrintStream out;

        public void run() {
            try {
                s = server.accept();
                readAll(s);

                BODY = "Hello world";
                CLEN = "Content-Length: " + BODY.length() + "\r\n";
                out = new PrintStream(new BufferedOutputStream(s.getOutputStream() ));

                /* send the header */
                out.print("HTTP/1.1 200 OK\r\n");
                out.print("Content-Type: text/plain; charset=iso-8859-1\r\n");
                out.print(CLEN);
                out.print("\r\n");
                out.print(BODY);
                out.flush();
            } catch (Exception e) {
                pass = false;
                try {
                    if (s != null)
                        s.close();
                    server.close();
                } catch (IOException unused) {}
                return;
            }

            // second request may legitimately fail

            try (Socket s2 = s; ServerSocket server2 = server; PrintStream out2 = out) {
                // wait for second request.
                readAll(s2);

                BODY = "Goodbye world";
                CLEN = "Content-Length: " + BODY.length() + "\r\n";

                /* send the header */
                out2.print("HTTP/1.1 200 OK\r\n");
                out2.print("Content-Type: text/plain; charset=iso-8859-1\r\n");
                out2.print(CLEN);
                out2.print("\r\n");
                out2.print(BODY);
                out2.flush();
                pass = !expectClose;
                if (!pass) System.out.println("Failed: expected close");
            } catch (Exception e) {
                pass = expectClose;
                if (!pass) System.out.println("Failed: did not expect close");
            }
        }
    }

    static String fetch(URL url) throws Exception {
        InputStream in = url.openConnection(NO_PROXY).getInputStream();
        String s = "";
        byte b[] = new byte[128];
        int n;
        do {
            n = in.read(b);
            if (n > 0)
                s += new String(b, 0, n, StandardCharsets.US_ASCII);
        } while (n > 0);
        in.close();
        return s;
    }

    static volatile boolean expectClose;

    public static void main(String args[]) throws Exception {
        // exercise the logging code
        Logger logger = Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");
        logger.setLevel(Level.FINEST);
        ConsoleHandler h = new ConsoleHandler();
        h.setLevel(Level.FINEST);
        logger.addHandler(h);

        expectClose = args[0].equals("short");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        ServerSocket ss = new ServerSocket();
        ss.bind(new InetSocketAddress(loopback, 0));
        Server s = new Server(ss);
        s.start();

        URL url = URIBuilder.newBuilder()
            .scheme("http")
            .loopback()
            .port(ss.getLocalPort())
            .toURL();
        System.out.println("URL: " + url);

        if (!fetch(url).equals("Hello world"))
            throw new RuntimeException("Failed on first request");

        // Wait a while to see if connection is closed
        Thread.sleep(3 * 1000);

        try {
            if (!fetch(url).equals("Goodbye world"))
                throw new RuntimeException("Failed on second request");
        } catch (Exception e) {
            if (!expectClose)
                throw e;
        }

        if (!pass)
            throw new RuntimeException("Failed in server");
    }
}
