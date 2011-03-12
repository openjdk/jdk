/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
    static final int TIMEOUT = 10 * 1000;
    static final int NUM_REDIRECTS = 9;

    static final String reply1 = "HTTP/1.1 307 Temporary Redirect\r\n" +
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Location: http://localhost:";
    static final String reply2 = ".html\r\n" +
        "Connection: close\r\n" +
        "Content-Type: text/html; charset=iso-8859-1\r\n\r\n" +
        "<html>Hello</html>";
    static final String reply3 = "HTTP/1.1 200 Ok\r\n" +
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Connection: close\r\n" +
        "Content-Type: text/html; charset=iso-8859-1\r\n\r\n" +
        "World";

    final ServerSocket ss;
    final int port;

    RedirLimitServer(ServerSocket ss) {
        this.ss = ss;
        port = ss.getLocalPort();
    }

    public void run() {
        try {
            ss.setSoTimeout(TIMEOUT);
            for (int i=0; i<NUM_REDIRECTS; i++) {
                try (Socket s = ss.accept()) {
                    s.setSoTimeout(TIMEOUT);
                    InputStream is = s.getInputStream();
                    OutputStream os = s.getOutputStream();
                    is.read();
                    String reply = reply1 + port + "/redirect" + i + reply2;
                    os.write(reply.getBytes());
                }
            }
            try (Socket s = ss.accept()) {
                InputStream is = s.getInputStream();
                OutputStream os = s.getOutputStream();
                is.read();
                os.write(reply3.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { ss.close(); } catch (IOException unused) {}
        }
    }
};

public class RedirectLimit {
    public static void main(String[] args) throws Exception {
        ServerSocket ss = new ServerSocket (0);
        int port = ss.getLocalPort();
        RedirLimitServer server = new RedirLimitServer(ss);
        server.start();

        InputStream in = null;
        try {
            URL url = new URL("http://localhost:" + port);
            URLConnection conURL =  url.openConnection();

            conURL.setDoInput(true);
            conURL.setAllowUserInteraction(false);
            conURL.setUseCaches(false);

            in = conURL.getInputStream();
            if ((in.read() != (int)'W') || (in.read()!=(int)'o')) {
                throw new RuntimeException("Unexpected string read");
            }
        } finally {
            if ( in != null ) { in.close(); }
        }
    }
}
