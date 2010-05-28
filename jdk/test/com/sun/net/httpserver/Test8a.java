/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6270015
 * @summary  Light weight HTTP server
 */

import com.sun.net.httpserver.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.io.*;
import java.net.*;
import java.security.*;
import javax.security.auth.callback.*;
import javax.net.ssl.*;

/**
 * Test POST large file via fixed len encoding
 */

public class Test8a extends Test {

    public static void main (String[] args) throws Exception {
        //Logger log = Logger.getLogger ("com.sun.net.httpserver");
        //ConsoleHandler h = new ConsoleHandler();
        //h.setLevel (Level.INFO);
        //log.addHandler (h);
        //log.setLevel (Level.INFO);
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpsServer server = HttpsServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test", handler);
        ExecutorService executor = Executors.newCachedThreadPool();
        SSLContext ssl = new SimpleSSLContext(System.getProperty("test.src")).get();
        server.setHttpsConfigurator(new HttpsConfigurator (ssl));
        server.setExecutor (executor);
        server.start ();

        URL url = new URL ("https://localhost:"+server.getAddress().getPort()+"/test/foo.html");
        System.out.print ("Test8a: " );
        HttpsURLConnection urlc = (HttpsURLConnection)url.openConnection ();
        urlc.setDoOutput (true);
        urlc.setRequestMethod ("POST");
        urlc.setHostnameVerifier (new DummyVerifier());
        urlc.setSSLSocketFactory (ssl.getSocketFactory());
        OutputStream os = new BufferedOutputStream (urlc.getOutputStream(), 8000);
        for (int i=0; i<SIZE; i++) {
            os.write (i % 250);
        }
        os.close();
        int resp = urlc.getResponseCode();
        if (resp != 200) {
            throw new RuntimeException ("test failed response code");
        }
        InputStream is = urlc.getInputStream ();
        for (int i=0; i<SIZE; i++) {
            int f = is.read();
            if (f != (i % 250)) {
                System.out.println ("Setting error(" +f +")("+i+")" );
                error = true;
                break;
            }
        }
        is.close();

        delay();
        server.stop(2);
        executor.shutdown();
        if (error) {
            throw new RuntimeException ("test failed error");
        }
        System.out.println ("OK");

    }

    public static boolean error = false;
    //final static int SIZE = 999999;
    final static int SIZE = 9999;

    static class Handler implements HttpHandler {
        int invocation = 1;
        public void handle (HttpExchange t)
            throws IOException
        {
        System.out.println ("Handler.handle");
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            int c, count=0;
            while ((c=is.read ()) != -1) {
                if (c != (count % 250)) {
                System.out.println ("Setting error 1");
                    error = true;
                    break;
                }
                count ++;
            }
            if (count != SIZE) {
                System.out.println ("Setting error 2");
                error = true;
            }
            is.close();
            t.sendResponseHeaders (200, SIZE);
                System.out.println ("Sending 200 OK");
            OutputStream os = new BufferedOutputStream(t.getResponseBody(), 8000);
            for (int i=0; i<SIZE; i++) {
                os.write (i % 250);
            }
            os.close();
                System.out.println ("Finished");
        }
    }
}
