/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6744329
 * @summary  Exception in light weight Http server
 */

import com.sun.net.httpserver.*;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

public class B6744329 {

    public static void main (String[] args) throws Exception {
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpServer server = HttpServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test", handler);
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();

        URL url = new URL ("http://localhost:"+server.getAddress().getPort()+"/test/foo.html");
        HttpURLConnection urlc = (HttpURLConnection)url.openConnection ();
        try {
            InputStream is = urlc.getInputStream();
            int c = 0;
            while (is.read()!= -1) {
                c ++;
            }
            System.out.println ("OK");
        } catch (IOException e) {
            System.out.println ("exception");
            error = true;
        }
        server.stop(2);
        executor.shutdown();
        if (error) {
            throw new RuntimeException ("Test failed");
        }
    }

    public static boolean error = false;

    /* this must be the same size as in ChunkedOutputStream.java
     */
    final static int CHUNK_SIZE = 4096;

    static class Handler implements HttpHandler {
        int invocation = 1;
        public void handle (HttpExchange t)
            throws IOException
        {
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            while (is.read () != -1) ;
            is.close();
            /* chunked response */
            t.sendResponseHeaders (200, 0);
            OutputStream os = t.getResponseBody();
            byte[] first = new byte [CHUNK_SIZE * 2];
            byte[] second = new byte [2];
            os.write (first);
            os.write ('x');
            os.write ('x');
            /* An index out of bounds exception will be thrown
             * below, which is caught by server, and connection
             * will be closed. resulting in IOException to client
             * - if bug present
             */
            os.write ('x');
            os.write ('x');
            os.write ('x');
            t.close();
        }
    }
}
