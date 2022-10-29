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
 * @library /test/lib /test/jdk/java/net/httpclient /test/jdk/java/net/httpclient/http2/server
 * @build jdk.test.lib.net.SimpleSSLContext HttpServerAdapters Http2Handler
 *          jdk.test.lib.net.IPSupport
 *          Http2TestExchange
 *
 * @modules java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.logging
 *          java.base/sun.net.www.http
 *          java.base/sun.net.www
 *          java.base/sun.net
 *
 * @run main/othervm SANTest
 * @summary Update SimpleSSLContext keystore to use SANs for localhost IP addresses
 */

import com.sun.net.httpserver.*;

import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.*;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.net.IPSupport;

/*
 * Will fail if the testkeys file belonging to SimpleSSLContext
 * does not have SAN entries for 127.0.0.1 or ::1
 */
public class SANTest implements HttpServerAdapters {

    static SSLContext ctx;

    static HttpServer getHttpsServer(InetSocketAddress addr, Executor exec, SSLContext ctx) throws Exception {
        HttpsServer server = HttpsServer.create(addr, 0);
        server.setExecutor(exec);
        server.setHttpsConfigurator(new HttpsConfigurator (ctx));
        return server;
    }

    static final boolean hasIPv4 = IPSupport.hasIPv4();
    static final boolean hasIPv6 = IPSupport.hasIPv6();

    static HttpTestServer initServer(boolean h2, InetAddress addr, SSLContext ctx,
                String sni, ExecutorService e) throws Exception {
        HttpTestServer s = null;
        InetSocketAddress ia = new InetSocketAddress (addr, 0);
        if ((addr instanceof Inet4Address) && !hasIPv4)
                return null;
        if ((addr instanceof Inet6Address) && !hasIPv6)
                return null;

        if (!h2) {
            s = HttpTestServer.of(getHttpsServer(ia, e, ctx));
            HttpTestHandler h = new HttpTestEchoHandler();
            s.addHandler(h, "/test1");
            s.start();
            return s;
        } else {
            s = HttpTestServer.of(new Http2TestServer(addr, sni, true, 0, e,
                        10, null, ctx, false));
            HttpTestHandler h = new HttpTestEchoHandler();
            s.addHandler(h, "/test1");
            s.start();
            return s;
        }
    }

    public static void main (String[] args) throws Exception {
        // Http/1.1 servers
        HttpTestServer h1s1 = null;
        HttpTestServer h1s2 = null;

        // Http/2 servers
        HttpTestServer h2s1 = null;
        HttpTestServer h2s2 = null;

        ExecutorService executor=null;
        try {
            System.out.print ("SANTest: ");
            ctx = new SimpleSSLContext().get();
            executor = Executors.newCachedThreadPool();

            InetAddress l1 = InetAddress.getByName("::1");
            InetAddress l2 = InetAddress.getByName("127.0.0.1");

            h1s1 = initServer(false, l1, ctx, "::1", executor);
            h1s2 = initServer(false, l2, ctx, "127.0.0.1", executor);

            h2s1 = initServer(true, l1, ctx, "::1", executor);
            h2s2 = initServer(true, l2, ctx, "127.0.0.1", executor);

            test("127.0.0.1", h1s2);
            test("::1", h1s1);
            testNew("127.0.0.1", h2s2, executor);
            testNew("::1", h2s1, executor);
            System.out.println ("OK");
        } finally {
            if (h1s1 != null)
                h1s1.stop();
            if (h1s2 != null)
                h1s2.stop();
            if (h2s1 != null)
                h2s1.stop();
            if (h2s2 != null)
                h2s2.stop();
            if (executor != null)
                executor.shutdown ();
        }
    }

    static void test (String host, HttpTestServer server) throws Exception {
        if (server == null)
            return;
        int port = server.getAddress().getPort();
        String body = "Yellow world";
        URL url = URIBuilder.newBuilder()
                 .scheme("https")
                 .host(host)
                 .port(port)
                 .path("/test1/foo.txt")
                 .toURL();
        System.out.println("URL = " + url);
        HttpURLConnection urlc = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        System.out.println("urlc = " + urlc);
        if (urlc instanceof HttpsURLConnection) {
            HttpsURLConnection urlcs = (HttpsURLConnection) urlc;
            urlcs.setSSLSocketFactory (ctx.getSocketFactory());
        }

        urlc.setRequestMethod("POST");
        urlc.setDoOutput(true);

        OutputStream os = urlc.getOutputStream();
        os.write(body.getBytes(StandardCharsets.ISO_8859_1));
        os.close();
        InputStream is = urlc.getInputStream();
        byte[] vv = is.readAllBytes();
        String ff = new String(vv, StandardCharsets.ISO_8859_1);
        System.out.println("resp = " + ff);
        if (!ff.equals(body))
            throw new RuntimeException();
        is.close();
    }

    static void testNew (String host, HttpTestServer server, Executor exec) throws Exception {
        if (server == null)
            return;
        int port = server.getAddress().getPort();
        String body = "Red and Yellow world";
        URI uri = URIBuilder.newBuilder()
                 .scheme("https")
                 .host(host)
                 .port(port)
                 .path("/test1/foo.txt")
                 .build();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(ctx)
                .executor(exec)
                .build();
        HttpRequest req = HttpRequest.newBuilder(uri)
                .version(HttpClient.Version.HTTP_2)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("resp = " + resp.body());
        if (!resp.body().equals(body))
            throw new RuntimeException();
    }
}
