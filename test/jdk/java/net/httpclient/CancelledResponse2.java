/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.*;
import static jdk.httpclient.test.lib.common.HttpServerAdapters.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.internal.httpclient.debug=true CancelledResponse2
 */

public class CancelledResponse2 {


    HttpTestServer h2TestServer;
    URI h2TestServerURI;
    private SSLContext sslContext;
    private static final Random random = RandomFactory.getRandom();
    private static final int MAX_CLIENT_DELAY = 160;

    @DataProvider(name = "versions")
    public Object[][] positive() {
        return new Object[][]{
                { HTTP_2, h2TestServerURI },
        };
    }

    private static void delay() {
        int delay = random.nextInt(MAX_CLIENT_DELAY);
        try {
            System.out.println("client delay: " + delay);
            Thread.sleep(delay);
        } catch (InterruptedException x) {
            out.println("Unexpected exception: " + x);
        }
    }

    @Test(dataProvider = "versions")
    public void test(Version version, URI uri) throws Exception {
        for (int i = 0; i < 5; i++) {
            HttpClient httpClient = HttpClient.newBuilder().sslContext(sslContext).version(version).build();
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .version(version)
                    .GET()
                    .build();
            AtomicBoolean cancelled = new AtomicBoolean();
            BodyHandler<String> bh = ofString(response, cancelled);
            CompletableFuture<HttpResponse<String>> cf = httpClient.sendAsync(httpRequest, bh);
            try {
                cf.get();
            } catch (Exception e) {
                e.printStackTrace();
                assertTrue(e.getCause() instanceof IOException, "HTTP/2 should cancel with an IOException when the Subscription is cancelled.");
            }
            assertTrue(cf.isCompletedExceptionally());
            assertTrue(cancelled.get());
        }
    }

    @BeforeTest
    public void setup() throws IOException {
        sslContext = new SimpleSSLContext().get();
        h2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        h2TestServer.addHandler(new CancelledResponseHandler(), "/h2");
        h2TestServerURI = URI.create("https://" + h2TestServer.serverAuthority() + "/h2");

        h2TestServer.start();
    }

    @AfterTest
    public void teardown() {
        h2TestServer.stop();
    }

    BodyHandler<String> ofString(String expected, AtomicBoolean cancelled) {
        return new CancellingHandler(expected, cancelled);
    }

    static class CancelledResponseHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange t) throws IOException {

            byte[] resp = response.getBytes(StandardCharsets.UTF_8);

            t.sendResponseHeaders(200, resp.length);
            System.err.println(resp.length);
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                for (byte b : resp) {
                    // This can be used to verify that varying amounts of the response data are sent.
                    System.err.print((char) b);
                    os.write(b);
                    os.flush();
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    static final String response = "Lorem ipsum dolor sit amet consectetur adipiscing elit, sed do eiusmod tempor quis" +
            " nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.";

    record CancellingHandler(String expected, AtomicBoolean cancelled) implements BodyHandler<String> {
        @Override
        public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo rinfo) {
            assert !cancelled.get();
            return new CancellingBodySubscriber(expected, cancelled);
        }
    }


    static class CancellingBodySubscriber implements HttpResponse.BodySubscriber<String> {
        private final String expected;
        private final CompletableFuture<String> result;
        private Flow.Subscription subscription;
        final AtomicInteger index = new AtomicInteger();
        final AtomicBoolean cancelled;
        CancellingBodySubscriber(String expected, AtomicBoolean cancelled) {
            this.cancelled = cancelled;
            this.expected = expected;
            result = new CompletableFuture<>();
        }

        @Override
        public CompletionStage<String> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            //if (result.isDone())
            // Max Delay is 180ms as there is 160 characters in response which gives at least 160ms in some test cases and
            // allows a response to complete fully with a lee-way of 20ms in other cases. Otherwise, response body is
            // usually partial. Each character is written by the server handler with a 1ms delay.
            delay();
            for (ByteBuffer b : item) {
                while (b.hasRemaining() && !result.isDone()) {
                    int i = index.getAndIncrement();
                    char at = expected.charAt(i);
                    byte[] data = new byte[b.remaining()];
                    b.get(data); // we know that the server writes 1 char
                    String s = new String(data);
                    char c = s.charAt(0);
                    System.err.print(c);
                    if (c != at) {
                        Throwable x = new IllegalStateException("char at "
                                + i + " is '" + c + "' expected '"
                                + at + "' for \"" + expected +"\"");
                        out.println("unexpected char received, cancelling");
                        subscription.cancel();
                        result.completeExceptionally(x);
                        return;
                    }
                }
                System.err.println();
            }
            if (index.get() > 0 && !result.isDone()) {
                // we should complete the result here, but let's
                // see if we get something back...
                out.println("Cancelling subscription after reading " + index.get());
                cancelled.set(true);
                subscription.cancel();
                result.completeExceptionally(new CancelException());
                return;
            }
            if (!result.isDone()) {
                out.println("requesting 1 more");
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            int len = index.get();
            if (len == expected.length()) {
                result.complete(expected);
            } else {
                Throwable x = new IllegalStateException("received only "
                        + len + " chars, expected " + expected.length()
                        + " for \"" + expected +"\"");
                result.completeExceptionally(x);
            }
        }
    }

    static class CancelException extends IOException {
    }
}
