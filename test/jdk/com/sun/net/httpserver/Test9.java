/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6270015
 * @library /test/lib
 * @build jdk.test.lib.Asserts
 *        jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 *        jdk.test.lib.net.URIBuilder
 * @run main/othervm Test9
 * @run main/othervm -Djava.net.preferIPv6Addresses=true Test9
 * @summary Light weight HTTP server
 */

import com.sun.net.httpserver.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;

import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertFileContentsEqual;
import static jdk.test.lib.Utils.createTempFileOfSize;

/* Same as Test1 but requests run in parallel.
 */

public class Test9 extends Test {

    private static final String TEMP_FILE_PREFIX =
            HttpServer.class.getPackageName() + '-' + Test9.class.getSimpleName() + '-';

    static SSLContext ctx;
    static boolean error = false;

    public static void main (String[] args) throws Exception {
        HttpServer s1 = null;
        HttpsServer s2 = null;
        ExecutorService executor=null;
        Path smallFilePath = createTempFileOfSize(TEMP_FILE_PREFIX, null, 23);
        Path largeFilePath = createTempFileOfSize(TEMP_FILE_PREFIX, null, 2730088);
        try {
            System.out.print ("Test9: ");
            InetAddress loopback = InetAddress.getLoopbackAddress();
            InetSocketAddress addr = new InetSocketAddress(loopback, 0);
            s1 = HttpServer.create (addr, 0);
            s2 = HttpsServer.create (addr, 0);
            // Assert that both files share the same parent and can be served from the same `FileServerHandler`
            assertEquals(smallFilePath.getParent(), largeFilePath.getParent());
            HttpHandler h = new FileServerHandler (smallFilePath.getParent().toString());
            HttpContext c1 = s1.createContext ("/", h);
            HttpContext c2 = s2.createContext ("/", h);
            executor = Executors.newCachedThreadPool();
            s1.setExecutor (executor);
            s2.setExecutor (executor);
            ctx = new SimpleSSLContext().get();
            s2.setHttpsConfigurator(new HttpsConfigurator (ctx));
            s1.start();
            s2.start();

            int p1 = s1.getAddress().getPort();
            int p2 = s2.getAddress().getPort();
            error = false;
            Thread[] t = new Thread[100];

            t[0] = test (true, "http", p1, smallFilePath);
            t[1] = test (true, "http", p1, largeFilePath);
            t[2] = test (true, "https", p2, smallFilePath);
            t[3] = test (true, "https", p2, largeFilePath);
            t[4] = test (false, "http", p1, smallFilePath);
            t[5] = test (false, "http", p1, largeFilePath);
            t[6] = test (false, "https", p2, smallFilePath);
            t[7] = test (false, "https", p2, largeFilePath);
            t[8] = test (true, "http", p1, smallFilePath);
            t[9] = test (true, "http", p1, largeFilePath);
            t[10] = test (true, "https", p2, smallFilePath);
            t[11] = test (true, "https", p2, largeFilePath);
            t[12] = test (false, "http", p1, smallFilePath);
            t[13] = test (false, "http", p1, largeFilePath);
            t[14] = test (false, "https", p2, smallFilePath);
            t[15] = test (false, "https", p2, largeFilePath);
            for (int i=0; i<16; i++) {
                t[i].join();
            }
            if (error) {
                throw new RuntimeException ("error");
            }

            System.out.println ("OK");
        } finally {
            if (s1 != null)
                s1.stop(0);
            if (s2 != null)
                s2.stop(0);
            if (executor != null)
                executor.shutdown ();
            Files.delete(smallFilePath);
            Files.delete(largeFilePath);
        }
    }

    static int foo = 1;

    static ClientThread test (boolean fixedLen, String protocol, int port, Path filePath) throws Exception {
        ClientThread t = new ClientThread (fixedLen, protocol, port, filePath);
        t.start();
        return t;
    }

    static Object fileLock = new Object();

    static class ClientThread extends Thread {

        boolean fixedLen;
        String protocol;
        int port;
        private final Path filePath;

        ClientThread (boolean fixedLen, String protocol, int port, Path filePath) {
            this.fixedLen = fixedLen;
            this.protocol = protocol;
            this.port = port;
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

                String s = "chunk";
                if (fixedLen) {
                    urlc.setRequestProperty ("XFixed", "yes");
                    s = "fixed";
                }
                InputStream is = urlc.getInputStream();
                File temp;
                synchronized (fileLock) {
                    temp = File.createTempFile (s, null);
                    temp.deleteOnExit();
                }
                OutputStream fout = new BufferedOutputStream (new FileOutputStream(temp));
                int c, count = 0;
                while ((c=is.read(buf)) != -1) {
                    count += c;
                    fout.write (buf, 0, c);
                }
                is.close();
                fout.close();

                if (count != filePath.toFile().length()) {
                    System.out.println ("wrong amount of data returned");
                    System.out.println ("fixedLen = "+fixedLen);
                    System.out.println ("protocol = "+protocol);
                    System.out.println ("port = "+port);
                    System.out.println ("file = " + filePath);
                    System.out.println ("temp = "+temp);
                    System.out.println ("count = "+count);
                    error = true;
                }
                assertFileContentsEqual(filePath, temp.toPath());
                temp.delete();
            } catch (Exception e) {
                e.printStackTrace();
                error = true;
            }
        }
    }

}
