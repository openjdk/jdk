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
 * @library /lib/testlibrary server
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules jdk.incubator.httpclient/jdk.incubator.http.internal.common
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.frame
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.hpack
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=ssl,requests,responses,errors FixedThreadPoolTest
 */

import java.net.*;
import jdk.incubator.http.*;
import static jdk.incubator.http.HttpClient.Version.HTTP_2;
import javax.net.ssl.*;
import java.nio.file.*;
import java.util.concurrent.*;
import jdk.testlibrary.SimpleSSLContext;
import static jdk.incubator.http.HttpRequest.BodyProcessor.fromFile;
import static jdk.incubator.http.HttpRequest.BodyProcessor.fromString;
import static jdk.incubator.http.HttpResponse.BodyHandler.asFile;
import static jdk.incubator.http.HttpResponse.BodyHandler.asString;

import org.testng.annotations.Test;

@Test
public class FixedThreadPoolTest {
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
            httpServer = new Http2TestServer(false, 0, exec, sslContext);
            httpServer.addHandler(new EchoHandler(), "/");
            httpPort = httpServer.getAddress().getPort();

            httpsServer = new Http2TestServer(true, 0, exec, sslContext);
            httpsServer.addHandler(new EchoHandler(), "/");

            httpsPort = httpsServer.getAddress().getPort();
            httpURIString = "http://127.0.0.1:" + httpPort + "/foo/";
            httpsURIString = "https://127.0.0.1:" + httpsPort + "/bar/";

            httpServer.start();
            httpsServer.start();
        } catch (Throwable e) {
            System.err.println("Throwing now");
            e.printStackTrace();
            throw e;
        }
    }

    @Test(timeOut=3000000)
    public static void test() throws Exception {
        try {
            initialize();
            simpleTest(false);
            simpleTest(true);
            streamTest(false);
            streamTest(true);
            paramsTest();
            Thread.sleep(1000 * 4);
        } catch (Exception | Error tt) {
            tt.printStackTrace();
            throw tt;
        } finally {
            httpServer.stop();
            httpsServer.stop();
            exec.shutdownNow();
        }
    }

    static HttpClient getClient() {
        if (client == null) {
            exec = Executors.newCachedThreadPool();
            client = HttpClient.newBuilder()
                               .executor(Executors.newFixedThreadPool(2))
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
        return TestUtil.compareFiles(path1, path2);
    }

    static Path tempFile() {
        return TestUtil.tempFile();
    }

    static final String SIMPLE_STRING = "Hello world Goodbye world";

    static final int LOOPS = 32;
    static final int FILESIZE = 64 * 1024 + 200;

    static void streamTest(boolean secure) throws Exception {
        URI uri = getURI(secure);
        System.err.printf("streamTest %b to %s\n" , secure, uri);

        HttpClient client = getClient();
        Path src = TestUtil.getAFile(FILESIZE * 4);
        HttpRequest req = HttpRequest.newBuilder(uri)
                                     .POST(fromFile(src))
                                     .build();

        Path dest = Paths.get("streamtest.txt");
        dest.toFile().delete();
        CompletableFuture<Path> response = client.sendAsync(req, asFile(dest))
                .thenApply(resp -> {
                    if (resp.statusCode() != 200)
                        throw new RuntimeException();
                    return resp.body();
                });
        response.join();
        compareFiles(src, dest);
        System.err.println("DONE");
    }

    static void paramsTest() throws Exception {
        System.err.println("paramsTest");
        Http2TestServer server = new Http2TestServer(true, 0, exec, sslContext);
        server.addHandler((t -> {
            SSLSession s = t.getSSLSession();
            String prot = s.getProtocol();
            if (prot.equals("TLSv1.2")) {
                t.sendResponseHeaders(200, -1);
            } else {
                System.err.printf("Protocols =%s\n", prot);
                t.sendResponseHeaders(500, -1);
            }
        }), "/");
        server.start();
        int port = server.getAddress().getPort();
        URI u = new URI("https://127.0.0.1:"+port+"/foo");
        HttpClient client = getClient();
        HttpRequest req = HttpRequest.newBuilder(u).build();
        HttpResponse<String> resp = client.sendAsync(req, asString()).get();
        int stat = resp.statusCode();
        if (stat != 200) {
            throw new RuntimeException("paramsTest failed "
                + Integer.toString(stat));
        }
    }

    static void simpleTest(boolean secure) throws Exception {
        URI uri = getURI(secure);
        System.err.println("Request to " + uri);

        // Do a simple warmup request

        HttpClient client = getClient();
        HttpRequest req = HttpRequest.newBuilder(uri)
                                     .POST(fromString(SIMPLE_STRING))
                                     .build();
        HttpResponse<String> response = client.sendAsync(req, asString()).get();
        HttpHeaders h = response.headers();

        checkStatus(200, response.statusCode());

        String responseBody = response.body();
        checkStrings(SIMPLE_STRING, responseBody);

        checkStrings(h.firstValue("x-hello").get(), "world");
        checkStrings(h.firstValue("x-bye").get(), "universe");

        // Do loops asynchronously

        CompletableFuture[] responses = new CompletableFuture[LOOPS];
        final Path source = TestUtil.getAFile(FILESIZE);
        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .POST(fromFile(source))
                                         .build();
        for (int i = 0; i < LOOPS; i++) {
            responses[i] = client.sendAsync(request, asFile(tempFile()))
                //.thenApply(resp -> compareFiles(resp.body(), source));
                .thenApply(resp -> {
                    System.out.printf("Resp status %d body size %d\n",
                                      resp.statusCode(), resp.body().toFile().length());
                    return compareFiles(resp.body(), source);
                });
        }
        CompletableFuture.allOf(responses).join();
        System.err.println("DONE");
    }
}
