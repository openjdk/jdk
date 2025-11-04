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
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 *        jdk.test.lib.net.URIBuilder
 * @run main/othervm Test9a
 * @run main/othervm -Djava.net.preferIPv6Addresses=true Test9a
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

public class Test9a extends Test {

    private static final String TEMP_FILE_PREFIX =
            HttpServer.class.getPackageName() + '-' + Test9a.class.getSimpleName() + '-';

    static SSLContext serverCtx;
    static volatile SSLContext clientCtx = null;
    static volatile boolean error = false;

    public static void main (String[] args) throws Exception {
        HttpsServer server = null;
        ExecutorService executor=null;
        Path smallFilePath = createTempFileOfSize(TEMP_FILE_PREFIX, null, 23);
        Path largeFilePath = createTempFileOfSize(TEMP_FILE_PREFIX, null, 2730088);
        try {
            System.out.print ("Test9a: ");
            InetAddress loopback = InetAddress.getLoopbackAddress();
            InetSocketAddress addr = new InetSocketAddress(loopback, 0);
            server = HttpsServer.create (addr, 0);
            // Assert that both files share the same parent and can be served from the same `FileServerHandler`
            assertEquals(smallFilePath.getParent(), largeFilePath.getParent());
            HttpHandler h = new FileServerHandler (smallFilePath.getParent().toString());
            HttpContext c1 = server.createContext ("/", h);
            executor = Executors.newCachedThreadPool();
            server.setExecutor (executor);
            serverCtx = new SimpleSSLContext().get();
            clientCtx = new SimpleSSLContext().get();
            server.setHttpsConfigurator(new HttpsConfigurator (serverCtx));
            server.start();

            int port = server.getAddress().getPort();
            error = false;
            Thread[] t = new Thread[100];

            t[0] = test (true, "https", port, smallFilePath);
            t[1] = test (true, "https", port, largeFilePath);
            t[2] = test (true, "https", port, smallFilePath);
            t[3] = test (true, "https", port, largeFilePath);
            t[4] = test (true, "https", port, smallFilePath);
            t[5] = test (true, "https", port, largeFilePath);
            t[6] = test (true, "https", port, smallFilePath);
            t[7] = test (true, "https", port, largeFilePath);
            t[8] = test (true, "https", port, smallFilePath);
            t[9] = test (true, "https", port, largeFilePath);
            t[10] = test (true, "https", port, smallFilePath);
            t[11] = test (true, "https", port, largeFilePath);
            t[12] = test (true, "https", port, smallFilePath);
            t[13] = test (true, "https", port, largeFilePath);
            t[14] = test (true, "https", port, smallFilePath);
            t[15] = test (true, "https", port, largeFilePath);
            for (int i=0; i<16; i++) {
                t[i].join();
            }
            if (error) {
                throw new RuntimeException ("error");
            }

            System.out.println ("OK");
        } finally {
            if (server != null)
                server.stop(0);
            if (executor != null)
                executor.shutdown();
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
                    urlcs.setSSLSocketFactory (clientCtx.getSocketFactory());
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
