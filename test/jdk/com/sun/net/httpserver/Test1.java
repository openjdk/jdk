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
 * @run main/othervm Test1
 * @run main/othervm -Djava.net.preferIPv6Addresses=true Test1
 * @run main/othervm -Dsun.net.httpserver.maxReqTime=10 Test1
 * @run main/othervm -Dsun.net.httpserver.nodelay=true Test1
 * @summary  Light weight HTTP server
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

/* basic http/s connectivity test
 * Tests:
 *      - client/server
 *      - send/receive large/small file
 *      - chunked encoding
 *      - via http and https
 *
 * The test is also run with sun.net.httpserver.nodelay simply to exercise
 * this option. There is no specific pass or failure related to running with
 * this option.
 */

public class Test1 extends Test {

    private static final String TEMP_FILE_PREFIX =
            HttpServer.class.getPackageName() + '-' + Test1.class.getSimpleName() + '-';

    static SSLContext ctx;

    public static void main (String[] args) throws Exception {
        HttpServer s1 = null;
        HttpsServer s2 = null;
        ExecutorService executor=null;
        Path smallFilePath = createTempFileOfSize(TEMP_FILE_PREFIX, null, 23);
        Path largeFilePath = createTempFileOfSize(TEMP_FILE_PREFIX, null, 2730088);
        try {
            System.out.print ("Test1: ");
            InetAddress loopback = InetAddress.getLoopbackAddress();
            InetSocketAddress addr = new InetSocketAddress (loopback, 0);
            s1 = HttpServer.create (addr, 0);
            if (s1 instanceof HttpsServer) {
                throw new RuntimeException ("should not be httpsserver");
            }
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

            int port = s1.getAddress().getPort();
            int httpsport = s2.getAddress().getPort();
            test (true, "http", port, smallFilePath);
            test (true, "http", port, largeFilePath);
            test (true, "https", httpsport, smallFilePath);
            test (true, "https", httpsport, largeFilePath);
            test (false, "http", port, smallFilePath);
            test (false, "http", port, largeFilePath);
            test (false, "https", httpsport, smallFilePath);
            test (false, "https", httpsport, largeFilePath);
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

    static void test (boolean fixedLen, String protocol, int port, Path filePath) throws Exception {
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

        assertEquals(filePath.toFile().length(), (long) count, "wrong amount of data returned");
        assertFileContentsEqual(filePath, temp.toPath());
        temp.delete();
    }

}
