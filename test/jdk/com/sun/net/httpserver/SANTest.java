/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278312
 * @library /test/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 * @run main/othervm SANTest
 * @summary Update SimpleSSLContext keystore to use SANs for localhost IP addresses
 */

import com.sun.net.httpserver.*;

import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;

/*
 * Will fail if the testkeys file belonging to SimpleSSLContext
 * does not have SAN entries for 127.0.0.1
 */

public class SANTest {

    static SSLContext ctx;

    public static void main (String[] args) throws Exception {
        HttpsServer s1 = null;
        HttpsServer s2 = null;
        ExecutorService executor=null;
        try {
            String root = System.getProperty ("test.src")+ "/docs";
            System.out.print ("SANTest: ");
            InetAddress l1 = InetAddress.getByName("::1");
            InetAddress l2 = InetAddress.getByName("127.0.0.1");
            InetSocketAddress addr1 = new InetSocketAddress (l1, 0);
            InetSocketAddress addr2 = new InetSocketAddress (l2, 0);
            s1 = HttpsServer.create (addr1, 0);
            s2 = HttpsServer.create (addr2, 0);
            HttpHandler h = new FileServerHandler (root);
            HttpContext c1 = s1.createContext ("/test1", h);
            HttpContext c2 = s2.createContext ("/test1", h);
            executor = Executors.newCachedThreadPool();
            s1.setExecutor (executor);
            s2.setExecutor (executor);
            ctx = new SimpleSSLContext().get();
            s1.setHttpsConfigurator(new HttpsConfigurator (ctx));
            s2.setHttpsConfigurator(new HttpsConfigurator (ctx));
            s1.start();
            s2.start();
            int port1 = s1.getAddress().getPort();
            int port2 = s2.getAddress().getPort();
            test ("127.0.0.1", root+"/test1", port2, "smallfile.txt", 23);
            test ("::1", root+"/test1", port1, "smallfile.txt", 23);
            System.out.println ("OK");
        } finally {
            if (s1 != null)
                s1.stop(2);
            if (s2 != null)
                s2.stop(2);
            if (executor != null)
                executor.shutdown ();
        }
    }

    static void test (String host, String root, int port, String f, int size) throws Exception {
        URL url = URIBuilder.newBuilder()
                 .scheme("https")
                 .host(host)
                 .port(port)
                 .path("/test1/"+f)
                 .toURL();
        System.out.println("URL = " + url);
        HttpURLConnection urlc = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        System.out.println("urlc = " + urlc);
        if (urlc instanceof HttpsURLConnection) {
            HttpsURLConnection urlcs = (HttpsURLConnection) urlc;
            urlcs.setSSLSocketFactory (ctx.getSocketFactory());
        }

        InputStream is = urlc.getInputStream();
        is.readAllBytes();
        is.close();
    }
}
