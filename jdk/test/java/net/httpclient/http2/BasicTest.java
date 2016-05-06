/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8087112
 * @library /lib/testlibrary
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules java.httpclient
 * @compile/module=java.httpclient java/net/http/BodyOutputStream.java
 * @compile/module=java.httpclient java/net/http/BodyInputStream.java
 * @compile/module=java.httpclient java/net/http/EchoHandler.java
 * @compile/module=java.httpclient java/net/http/Http2Handler.java
 * @compile/module=java.httpclient java/net/http/Http2TestExchange.java
 * @compile/module=java.httpclient java/net/http/Http2TestServerConnection.java
 * @compile/module=java.httpclient java/net/http/Http2TestServer.java
 * @compile/module=java.httpclient java/net/http/OutgoingPushPromise.java
 * @compile/module=java.httpclient java/net/http/TestUtil.java
 * @run testng/othervm -Djava.net.http.HttpClient.log=ssl,requests,responses,errors BasicTest
 */

import java.io.*;
import java.net.*;
import java.net.http.*;
import static java.net.http.HttpClient.Version.HTTP_2;
import javax.net.ssl.*;
import java.nio.file.*;
import java.util.concurrent.*;
import jdk.testlibrary.SimpleSSLContext;


import org.testng.annotations.Test;
import org.testng.annotations.Parameters;

@Test
public class BasicTest {
    static int httpPort, httpsPort;
    static Http2TestServer httpServer, httpsServer;
    static HttpClient client = null;
    static ExecutorService exec;
    static SSLContext sslContext;

    static String httpURIString, httpsURIString;

    static void initialize() throws Exception {
        try {
            SimpleSSLContext sslct = new SimpleSSLContext();
            sslContext = sslct.get();
            client = getClient();
            exec = client.executorService();
            httpServer = new Http2TestServer(false, 0, new EchoHandler(),
                    exec, sslContext);
            httpPort = httpServer.getAddress().getPort();

            httpsServer = new Http2TestServer(true, 0, new EchoHandler(),
                    exec, sslContext);

            httpsPort = httpsServer.getAddress().getPort();
            httpURIString = "http://127.0.0.1:" + Integer.toString(httpPort) +
                    "/foo/";
            httpsURIString = "https://127.0.0.1:" + Integer.toString(httpsPort) +
                    "/bar/";

            httpServer.start();
            httpsServer.start();
        } catch (Throwable e) {
            System.err.println("Throwing now");
            e.printStackTrace();
            throw e;
        }
    }

    @Test(timeOut=30000)
    public static void test() throws Exception {
        try {
            initialize();
            simpleTest(false);
            simpleTest(true);
            streamTest(false);
            streamTest(true);
            Thread.sleep(1000 * 4);
        } finally {
            httpServer.stop();
            httpsServer.stop();
            exec.shutdownNow();
        }
    }

    static HttpClient getClient() {
        if (client == null) {
            client = HttpClient.create()
                .sslContext(sslContext)
                .version(HTTP_2)
                .build();
        }
        return client;
    }

    static URI getURI(boolean secure) {
        if (secure)
            return URI.create(httpsURIString);
        else
            return URI.create(httpURIString);
    }

    static void checkStatus(int expected, int found) throws Exception {
        if (expected != found) {
            System.err.printf ("Test failed: wrong status code %d/%d\n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static void checkStrings(String expected, String found) throws Exception {
        if (!expected.equals(found)) {
            System.err.printf ("Test failed: wrong string %s/%s\n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static Void compareFiles(Path path1, Path path2) {
        return java.net.http.TestUtil.compareFiles(path1, path2);
    }

    static Path tempFile() {
        return java.net.http.TestUtil.tempFile();
    }

    static final String SIMPLE_STRING = "Hello world Goodbye world";

    static final int LOOPS = 13;
    static final int FILESIZE = 64 * 1024;

    static void streamTest(boolean secure) throws Exception {
        URI uri = getURI(secure);
        System.err.printf("streamTest %b to %s\n" , secure, uri);

        HttpClient client = getClient();
        Path src = java.net.http.TestUtil.getAFile(FILESIZE * 4);
        HttpRequest req = client.request(uri)
                .body(HttpRequest.fromFile(src))
                .POST();

        CompletableFuture<InputStream> response = req.responseAsync()
                .thenCompose(resp -> {
                    if (resp.statusCode() != 200)
                        throw new RuntimeException();
                    return resp.bodyAsync(HttpResponse.asInputStream());
                });
        InputStream is = response.join();
        File dest = File.createTempFile("foo","bar");
        dest.deleteOnExit();
        FileOutputStream os = new FileOutputStream(dest);
        is.transferTo(os);
        is.close();
        os.close();
        int count = 0;
        compareFiles(src, dest.toPath());
        System.err.println("DONE");
    }


    static void simpleTest(boolean secure) throws Exception {
        URI uri = getURI(secure);
        System.err.println("Request to " + uri);

        // Do a simple warmup request

        HttpClient client = getClient();
        HttpRequest req = client.request(uri)
                .body(HttpRequest.fromString(SIMPLE_STRING))
                .POST();
        HttpResponse response = req.response();
        HttpHeaders h = response.headers();

        checkStatus(200, response.statusCode());

        String responseBody = response.body(HttpResponse.asString());
        checkStrings(SIMPLE_STRING, responseBody);

        checkStrings(h.firstValue("x-hello").get(), "world");
        checkStrings(h.firstValue("x-bye").get(), "universe");

        // Do loops asynchronously

        CompletableFuture[] responses = new CompletableFuture[LOOPS];
        final Path source = java.net.http.TestUtil.getAFile(FILESIZE);
        for (int i = 0; i < LOOPS; i++) {
            responses[i] = client.request(uri)
                .body(HttpRequest.fromFile(source))
                .version(HTTP_2)
                .POST()
                .responseAsync()
                .thenCompose(r -> r.bodyAsync(HttpResponse.asFile(tempFile())))
                .thenApply(path -> compareFiles(path, source));
            Thread.sleep(100);
        }
        CompletableFuture.allOf(responses).join();
        System.err.println("DONE");
    }
}
