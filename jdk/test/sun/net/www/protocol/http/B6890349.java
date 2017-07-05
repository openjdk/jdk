/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/**
 * @test
 * @bug 6890349
 * @run main/othervm B6890349
 * @summary  Light weight HTTP server
 */

import java.net.*;
import java.io.*;

public class B6890349 extends Thread {
    public static final void main(String[] args) throws Exception {

        try {
            ServerSocket server = new ServerSocket (0);
            int port = server.getLocalPort();
            System.out.println ("listening on "  + port);
            B6890349 t = new B6890349 (server);
            t.start();
            URL u = new URL ("http://127.0.0.1:"+port+"/foo\nbar");
            HttpURLConnection urlc = (HttpURLConnection)u.openConnection ();
            InputStream is = urlc.getInputStream();
            throw new RuntimeException ("Test failed");
        } catch (IOException e) {
            System.out.println ("OK");
        }
    }

    ServerSocket server;

    B6890349 (ServerSocket server) {
        this.server = server;
    }

    String resp = "HTTP/1.1 200 Ok\r\nContent-length: 0\r\n\r\n";

    public void run () {
        try {
            Socket s = server.accept ();
            OutputStream os = s.getOutputStream();
            os.write (resp.getBytes());
        } catch (IOException e) {
            System.out.println (e);
        }
    }
}
