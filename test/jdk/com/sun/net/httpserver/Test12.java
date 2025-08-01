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

import com.sun.net.httpserver.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertFileContentsEqual;
import static jdk.test.lib.Utils.createTempFileOfSize;

/*
 * @test
 * @bug 6270015 8359477
 * @summary Light weight HTTP server - basic http/s connectivity test, same as Test1,
 *          but in parallel
 * @library /test/lib
 * @build jdk.test.lib.Asserts
 *        jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 *        jdk.test.lib.net.URIBuilder
 * @run main/othervm Test12
 * @run main/othervm -Djava.net.preferIPv6Addresses=true Test12
 */
public class Test12 extends Test {

    private static final String TEMP_FILE_PREFIX =
            HttpServer.class.getPackageName() + '-' + Test12.class.getSimpleName() + '-';

    static SSLContext ctx;

    public static void main (String[] args) throws Exception {
        HttpServer s1 = null;
        HttpsServer s2 = null;
        Path smallFilePath = createTempFileOfSize(TEMP_FILE_PREFIX, null, 23);
        Path largeFilePath = createTempFileOfSize(TEMP_FILE_PREFIX, null, 2730088);
        final ExecutorService executor = Executors.newCachedThreadPool();
        try {
            System.out.print ("Test12: ");
            InetAddress loopback = InetAddress.getLoopbackAddress();
            InetSocketAddress addr = new InetSocketAddress(loopback, 0);
            s1 = HttpServer.create (addr, 0);
            s2 = HttpsServer.create (addr, 0);
            // Assert that both files share the same parent and can be served from the same `FileServerHandler`
            assertEquals(smallFilePath.getParent(), largeFilePath.getParent());
            HttpHandler h = new FileServerHandler(smallFilePath.getParent().toString());
            HttpContext c1 = s1.createContext ("/", h);
            HttpContext c2 = s2.createContext ("/", h);
            s1.setExecutor (executor);
            s2.setExecutor (executor);
            ctx = new SimpleSSLContext().get();
            s2.setHttpsConfigurator(new HttpsConfigurator (ctx));
            s1.start();
            s2.start();

            int port = s1.getAddress().getPort();
            int httpsport = s2.getAddress().getPort();
            final Runner[] r = new Runner[8];
            r[0] = new Runner (true, "http", port, smallFilePath);
            r[1] = new Runner (true, "http", port, largeFilePath);
            r[2] = new Runner (true, "https", httpsport, smallFilePath);
            r[3] = new Runner (true, "https", httpsport, largeFilePath);
            r[4] = new Runner (false, "http", port, smallFilePath);
            r[5] = new Runner (false, "http", port, largeFilePath);
            r[6] = new Runner (false, "https", httpsport, smallFilePath);
            r[7] = new Runner (false, "https", httpsport, largeFilePath);
            // submit the tasks
            final List<Future<Void>> futures = new ArrayList<>();
            for (Runner runner : r) {
                futures.add(executor.submit(runner));
            }
            // wait for the tasks' completion
            for (Future<Void> f : futures) {
                f.get();
            }
            System.out.println ("All " + futures.size() + " tasks completed successfully");
        } finally {
            if (s1 != null) {
                s1.stop(0);
            }
            if (s2 != null) {
                s2.stop(0);
            }
            executor.close();
            // it's OK to delete these files since the server side handlers
            // serving these files have completed (guaranteed by the completion of Executor.close())
            System.out.println("deleting " + smallFilePath);
            Files.delete(smallFilePath);
            System.out.println("deleting " + largeFilePath);
            Files.delete(largeFilePath);
        }
    }

    static class Runner implements Callable<Void> {

        boolean fixedLen;
        String protocol;
        int port;
        private final Path filePath;

        Runner(boolean fixedLen, String protocol, int port, Path filePath) {
            this.fixedLen=fixedLen;
            this.protocol=protocol;
            this.port=port;
            this.filePath = filePath;
        }

        @Override
        public Void call() throws Exception {
            final URL url = URIBuilder.newBuilder()
                      .scheme(protocol)
                      .loopback()
                      .port(port)
                      .path("/" + filePath.getFileName())
                      .toURL();
            final HttpURLConnection urlc = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            if (urlc instanceof HttpsURLConnection) {
                HttpsURLConnection urlcs = (HttpsURLConnection) urlc;
                urlcs.setHostnameVerifier (new HostnameVerifier () {
                    public boolean verify (String s, SSLSession s1) {
                        return true;
                    }
                });
                urlcs.setSSLSocketFactory (ctx.getSocketFactory());
            }
            if (fixedLen) {
                urlc.setRequestProperty ("XFixed", "yes");
            }
            final Path temp = Files.createTempFile(Path.of("."), "Test12", null);
            final long numReceived;
            try (InputStream is = urlc.getInputStream();
                 OutputStream fout = new BufferedOutputStream(new FileOutputStream(temp.toFile()))) {
                numReceived = is.transferTo(fout);
            }
            System.out.println("received " + numReceived + " response bytes for " + url);
            final long expected = filePath.toFile().length();
            if (numReceived != expected) {
                throw new RuntimeException ("expected " + expected + " bytes, but received "
                        + numReceived);
            }
            assertFileContentsEqual(filePath, temp);
            Files.delete(temp);
            return null;
        }
    }
}
