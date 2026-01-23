/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8087112 8177935
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 * @compile ../ReferenceTracker.java
 *
 * @comment This test failed on Tier 7, but the failure could not be reproduced.
 *          The QUIC idle timeout has been increased to a value higher than the
 *          JTreg on Tier 7 so that, if the client becomes wedged again, the
 *          JTreg timeout handlers can collect more diagnostic information.
 *
 * @run testng/othervm  -Djdk.internal.httpclient.debug=err
 *                      -Djdk.httpclient.HttpClient.log=ssl,headers,requests,responses,errors
 *                      -Djdk.httpclient.quic.idleTimeout=666666
 *                      -Djdk.test.server.quic.idleTimeout=666666
 *                      ${test.main.class}
 */

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static jdk.test.lib.Asserts.assertFileContentsEqual;
import static jdk.test.lib.Utils.createTempFileOfSize;

import org.testng.annotations.Test;

public class H3FixedThreadPoolTest implements HttpServerAdapters {

    private static final String CLASS_NAME = H3FixedThreadPoolTest.class.getSimpleName();

    static int http3Port, https2Port;
    static HttpTestServer http3Server, https2Server;
    static volatile HttpClient client = null;
    static ExecutorService exec;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();

    static String http3URIString, https2URIString;

    static void initialize() throws Exception {
        try {
            client = getClient();
            exec = Executors.newCachedThreadPool();
            http3Server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext, exec);
            http3Server.addHandler(new HttpTestFileEchoHandler(), "/H3FixedThreadPoolTest/http3-only/");
            http3Port = http3Server.getAddress().getPort();

            https2Server = HttpTestServer.create(ALT_SVC, sslContext, exec);
            https2Server.addHandler(new HttpTestFileEchoHandler(), "/H3FixedThreadPoolTest/http3-alt-svc/");
            https2Server.addHandler((t) -> {
                t.getRequestBody().readAllBytes();
                t.sendResponseHeaders(200, 0);
            }, "/H3FixedThreadPoolTest/http3-alt-svc/bar/head");
            https2Port = https2Server.getAddress().getPort();
            http3URIString = "https://" + http3Server.serverAuthority() + "/H3FixedThreadPoolTest/http3-only/foo/";
            https2URIString = "https://" + https2Server.serverAuthority() + "/H3FixedThreadPoolTest/http3-alt-svc/bar/";

            http3Server.start();
            https2Server.start();

            // warmup client to populate AltServiceRegistry
            var head = HttpRequest.newBuilder(URI.create(https2URIString + "head"))
                    .setOption(H3_DISCOVERY, ALT_SVC).build();
            var resp = client.send(head, BodyHandlers.ofString());
            assert resp.statusCode() == 200;

        } catch (Throwable e) {
            System.err.println("Throwing now");
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public static void test() throws Exception {
        try {
            initialize();
            simpleTest(false);
            simpleTest(true);
            streamTest(false);
            streamTest(true);
            paramsTest();
            if (client != null) {
                ReferenceTracker.INSTANCE.track(client);
                client = null;
                System.gc();
                var error = ReferenceTracker.INSTANCE.check(4000);
                if (error != null) throw error;
            }
        } catch (Exception | Error tt) {
            tt.printStackTrace();
            throw tt;
        } finally {
            http3Server.stop();
            https2Server.stop();
            exec.shutdownNow();
        }
    }

    static HttpClient getClient() {
        if (client == null) {
            // Executor e1 = Executors.newFixedThreadPool(1);
            // Executor e = (Runnable r) -> e1.execute(() -> {
            //    System.out.println("[" + Thread.currentThread().getName()
            //                       + "] Executing: "
            //                       + r.getClass().getName());
            //    r.run();
            // });
            client = HttpServerAdapters.createClientBuilderForH3()
                               .executor(Executors.newFixedThreadPool(2))
                               .sslContext(sslContext)
                               .version(HTTP_3)
                               .build();
        }
        return client;
    }

    static URI getURI(boolean http3Only) {
        if (http3Only)
            return URI.create(http3URIString);
        else
            return URI.create(https2URIString);
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
            System.err.printf ("Test failed: wrong string \"%s\" != \"%s\"%n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static final String SIMPLE_STRING = "Hello world Goodbye world";

    static final int LOOPS = 32;
    static final int FILESIZE = 64 * 1024 + 200;

    static void streamTest(boolean http3only) throws Exception {
        URI uri = getURI(http3only);
        System.out.printf("%nstreamTest %b to %s%n" , http3only, uri);
        System.err.printf("%nstreamTest %b to %s%n" , http3only, uri);
        var config = http3only ? HTTP_3_URI_ONLY : ALT_SVC;

        HttpClient client = getClient();
        Path src = createTempFileOfSize(CLASS_NAME, ".dat", FILESIZE * 4);
        HttpRequest req = HttpRequest.newBuilder(uri)
                                     .setOption(H3_DISCOVERY, config)
                                     .POST(BodyPublishers.ofFile(src))
                                     .build();

        Path dest = Path.of("streamtest.txt");
        dest.toFile().delete();
        CompletableFuture<Path> response = client.sendAsync(req, BodyHandlers.ofFile(dest))
                .thenApply(resp -> {
                    if (resp.statusCode() != 200)
                        throw new RuntimeException();
                    return resp.body();
                });
        response.join();
        assertFileContentsEqual(src, dest);
        System.err.println("DONE");
    }

    // expect highest supported version we know about
    static String expectedTLSVersion(SSLContext ctx) {
        SSLParameters params = ctx.getSupportedSSLParameters();
        String[] protocols = params.getProtocols();
        for (String prot : protocols) {
            if (prot.equals("TLSv1.3"))
                return "TLSv1.3";
        }
        return "TLSv1.2";
    }

    static void paramsTest() throws Exception {
        System.out.println("\nparamsTest");
        System.err.println("\nparamsTest");
        HttpTestServer server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        server.addHandler((t -> {
            SSLSession s = t.getSSLSession();
            String prot = s.getProtocol();
            System.err.println("Server: paramsTest: " + prot);
            if (prot.equals(expectedTLSVersion(sslContext))) {
                t.sendResponseHeaders(200, 0);
            } else {
                System.err.printf("Protocols =%s%n", prot);
                t.sendResponseHeaders(500, 0);
            }
        }), "/");
        server.start();
        try {
            URI u = new URI("https://" + server.serverAuthority() + "/paramsTest");
            HttpClient client = getClient();
            HttpRequest req = HttpRequest.newBuilder(u).setOption(H3_DISCOVERY, HTTP_3_URI_ONLY).build();
            HttpResponse<String> resp = client.sendAsync(req, BodyHandlers.ofString()).get();
            int stat = resp.statusCode();
            if (stat != 200) {
                throw new RuntimeException("paramsTest failed " + stat);
            }
        } finally {
            server.stop();
        }
    }

    static void simpleTest(boolean http3only) throws Exception {
        System.out.println("\nsimpleTest http3-only=" + http3only);
        System.err.println("\nsimpleTest http3-only=" + http3only);
        URI uri = getURI(http3only);
        var config = http3only ? HTTP_3_URI_ONLY : ALT_SVC;
        System.err.println("Request to " + uri);

        // Do a simple warmup request

        HttpClient client = getClient();
        HttpRequest req = HttpRequest.newBuilder(uri)
                                     .POST(BodyPublishers.ofString(SIMPLE_STRING))
                                     .setOption(H3_DISCOVERY, config)
                                     .build();
        HttpResponse<String> response = client.sendAsync(req, BodyHandlers.ofString()).get();
        HttpHeaders h = response.headers();

        checkStatus(200, response.statusCode());

        String responseBody = response.body();
        checkStrings(SIMPLE_STRING, responseBody);

        checkStrings(h.firstValue("x-hello").get(), "world");
        checkStrings(h.firstValue("x-bye").get(), "universe");

        // Do loops asynchronously

        CompletableFuture<?>[] responses = new CompletableFuture[LOOPS];
        final Path source = createTempFileOfSize(CLASS_NAME, ".dat", FILESIZE);
        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .setOption(H3_DISCOVERY, config)
                                         .POST(BodyPublishers.ofFile(source))
                                         .build();
        for (int i = 0; i < LOOPS; i++) {
            Path requestPayloadFile = Utils.createTempFile(CLASS_NAME, ".dat");
            responses[i] = client.sendAsync(request, BodyHandlers.ofFile(requestPayloadFile))
                //.thenApply(resp -> compareFiles(resp.body(), source));
                .thenApply(resp -> {
                    System.out.printf("Resp status %d body size %d\n",
                                      resp.statusCode(), resp.body().toFile().length());
                    assertFileContentsEqual(resp.body(), source);
                    return null;
                });
        }
        CompletableFuture.allOf(responses).join();
        System.err.println("DONE");
    }
}
