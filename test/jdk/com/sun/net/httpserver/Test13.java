/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.logging.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;

import static jdk.test.lib.Asserts.assertFileContentsEqual;
import static jdk.test.lib.Utils.createTempFileOfSize;

/* basic http/s connectivity test
 * Tests:
 *      - same as Test12, but with 64 threads
 */

/*
 * @test
 * @bug 6270015
 * @summary Light weight HTTP server
 * @library /test/lib
 * @build jdk.test.lib.Asserts
 *        jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 *        jdk.test.lib.net.URIBuilder
 * @comment We use othervm because this test configures logging handlers
 *          for the system wide "com.sun.net.httpserver" logger
 * @run main/othervm ${test.main.class}
 * @run main/othervm -Djava.net.preferIPv6Addresses=true ${test.main.class}
 */
public class Test13 extends Test {

    private static final String TEMP_FILE_PREFIX =
            HttpServer.class.getPackageName() + '-' + Test13.class.getSimpleName() + '-';

    private static final SSLContext ctx = SimpleSSLContext.findSSLContext();

    private static final Logger logger = Logger.getLogger ("com.sun.net.httpserver");

    final static int NUM = 32; // was 32

    static boolean fail = false;

    private static void setupLogging() {
        final Handler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    public static void main (String[] args) throws Exception {
        HttpServer s1 = null;
        HttpsServer s2 = null;
        ExecutorService executor=null;
        Path filePath = createTempFileOfSize(TEMP_FILE_PREFIX, null, 23);
        setupLogging(); // merely for debugging
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try {
            System.out.print ("Test13: ");
            InetSocketAddress addr = new InetSocketAddress(loopback, 0);
            s1 = HttpServer.create (addr, 0);
            s2 = HttpsServer.create (addr, 0);
            HttpHandler h = new FileServerHandler (filePath.getParent().toString());
            HttpContext c1 = s1.createContext ("/", h);
            HttpContext c2 = s2.createContext ("/", h);
            executor = Executors.newCachedThreadPool();
            s1.setExecutor (executor);
            s2.setExecutor (executor);
            s2.setHttpsConfigurator(new HttpsConfigurator (ctx));
            s1.start();
            s2.start();

            int port = s1.getAddress().getPort();
            int httpsport = s2.getAddress().getPort();
            Runner r[] = new Runner[NUM*2];
            for (int i=0; i<NUM; i++) {
                r[i] = new Runner (true, "http", port, filePath);
                r[i+NUM] = new Runner (true, "https", httpsport, filePath);
            }
            start (r);
            join (r);
            System.out.println ("OK");
        } finally {
            if (s1 != null)
                s1.stop(0);
            if (s2 != null)
                s2.stop(0);
            if (executor != null)
                executor.shutdown ();
            Files.delete(filePath);
        }
    }

    static void start (Runner[] x) {
        for (int i=0; i<x.length; i++) {
            if (x[i] != null)
            x[i].start();
        }
    }

    static void join (Runner[] x) {
        for (int i=0; i<x.length; i++) {
            try {
                if (x[i] != null)
                x[i].join();
            } catch (InterruptedException e) {}
        }
    }


    static class Runner extends Thread {

        boolean fixedLen;
        String protocol;
        int port;
        private final Path filePath;

        Runner (boolean fixedLen, String protocol, int port, Path filePath) {
            this.fixedLen=fixedLen;
            this.protocol=protocol;
            this.port=port;
            this.filePath = filePath;
        }

        public void run () {
            try {
                URL url = URIBuilder.newBuilder()
                          .scheme(protocol)
                          .loopback()
                          .port(port)
                          .path("/" + filePath.getFileName())
                          .toURL();
                HttpURLConnection urlc = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
                if (urlc instanceof HttpsURLConnection) {
                    HttpsURLConnection urlcs = (HttpsURLConnection) urlc;
                    urlcs.setHostnameVerifier (new HostnameVerifier () {
                        public boolean verify (String s, SSLSession s1) {
                            return true;
                        }
                    });
                    urlcs.setSSLSocketFactory (ctx.getSocketFactory());
                }
                byte [] buf = new byte [4096];

                if (fixedLen) {
                    urlc.setRequestProperty ("XFixed", "yes");
                }
                InputStream is = urlc.getInputStream();
                File temp = File.createTempFile ("Test1", null);
                temp.deleteOnExit();
                OutputStream fout = new BufferedOutputStream (new FileOutputStream(temp));
                int c, count = 0;
                while ((c=is.read(buf)) != -1) {
                    count += c;
                    fout.write (buf, 0, c);
                }
                is.close();
                fout.close();

                if (count != filePath.toFile().length()) {
                    throw new RuntimeException ("wrong amount of data returned");
                }
                assertFileContentsEqual(filePath, temp.toPath());
                temp.delete();
            } catch (Exception e) {
                e.printStackTrace();
                fail = true;
            }
        }
    }

}
