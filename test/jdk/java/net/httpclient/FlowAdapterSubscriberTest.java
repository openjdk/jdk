/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.Function;
import java.util.function.Supplier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.BodySubscriber;
import jdk.testlibrary.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import javax.net.ssl.SSLContext;
import static java.nio.charset.StandardCharsets.UTF_8;
import static jdk.incubator.http.HttpRequest.BodyPublisher.fromString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @summary Basic tests for Flow adapter Subscribers
 * @modules java.base/sun.net.www.http
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.common
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.frame
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.hpack
 *          java.logging
 *          jdk.httpserver
 * @library /lib/testlibrary http2/server
 * @build Http2TestServer
 * @build jdk.testlibrary.SimpleSSLContext
 * @run testng/othervm FlowAdapterSubscriberTest
 */

public class FlowAdapterSubscriberTest {

    SSLContext sslContext;
    HttpServer httpTestServer;         // HTTP/1.1    [ 4 servers ]
    HttpsServer httpsTestServer;       // HTTPS/1.1
    Http2TestServer http2TestServer;   // HTTP/2 ( h2c )
    Http2TestServer https2TestServer;  // HTTP/2 ( h2  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

    @DataProvider(name = "uris")
    public Object[][] variants() {
        return new Object[][]{
                { httpURI   },
                { httpsURI  },
                { http2URI  },
                { https2URI },
        };
    }

    static final Class<NullPointerException> NPE = NullPointerException.class;

    @Test
    public void testNull() {
        assertThrows(NPE, () -> BodyHandler.fromSubscriber(null));
        assertThrows(NPE, () -> BodyHandler.fromSubscriber(null, Function.identity()));
        assertThrows(NPE, () -> BodyHandler.fromSubscriber(new ListSubscriber(), null));
        assertThrows(NPE, () -> BodyHandler.fromSubscriber(null, null));

        assertThrows(NPE, () -> BodySubscriber.fromSubscriber(null));
        assertThrows(NPE, () -> BodySubscriber.fromSubscriber(null, Function.identity()));
        assertThrows(NPE, () -> BodySubscriber.fromSubscriber(new ListSubscriber(), null));
        assertThrows(NPE, () -> BodySubscriber.fromSubscriber(null, null));

        Subscriber subscriber = BodySubscriber.fromSubscriber(new ListSubscriber());
        assertThrows(NPE, () -> subscriber.onSubscribe(null));
        assertThrows(NPE, () -> subscriber.onNext(null));
        assertThrows(NPE, () -> subscriber.onError(null));
    }

    // List<ByteBuffer>

    @Test(dataProvider = "uris")
    void testListWithFinisher(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("May the luck of the Irish be with you!")).build();

        ListSubscriber subscriber = new ListSubscriber();
        HttpResponse<String> response = client.sendAsync(request,
                BodyHandler.fromSubscriber(subscriber, Supplier::get)).join();
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "May the luck of the Irish be with you!");
    }

    @Test(dataProvider = "uris")
    void testListWithoutFinisher(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("May the luck of the Irish be with you!")).build();

        ListSubscriber subscriber = new ListSubscriber();
        HttpResponse<Void> response = client.sendAsync(request,
                BodyHandler.fromSubscriber(subscriber)).join();
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "May the luck of the Irish be with you!");
    }

    @Test(dataProvider = "uris")
    void testListWithFinisherBlocking(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("May the luck of the Irish be with you!")).build();

        ListSubscriber subscriber = new ListSubscriber();
        HttpResponse<String> response = client.send(request,
                BodyHandler.fromSubscriber(subscriber, Supplier::get));
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "May the luck of the Irish be with you!");
    }

    @Test(dataProvider = "uris")
    void testListWithoutFinisherBlocking(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("May the luck of the Irish be with you!")).build();

        ListSubscriber subscriber = new ListSubscriber();
        HttpResponse<Void> response = client.send(request,
                BodyHandler.fromSubscriber(subscriber));
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "May the luck of the Irish be with you!");
    }

    // Collection<ByteBuffer>

    @Test(dataProvider = "uris")
    void testCollectionWithFinisher(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("What's the craic?")).build();

        CollectionSubscriber subscriber = new CollectionSubscriber();
        HttpResponse<String> response = client.sendAsync(request,
                BodyHandler.fromSubscriber(subscriber, CollectionSubscriber::get)).join();
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "What's the craic?");
    }

    @Test(dataProvider = "uris")
    void testCollectionWithoutFinisher(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("What's the craic?")).build();

        CollectionSubscriber subscriber = new CollectionSubscriber();
        HttpResponse<Void> response = client.sendAsync(request,
                BodyHandler.fromSubscriber(subscriber)).join();
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "What's the craic?");
    }

    @Test(dataProvider = "uris")
    void testCollectionWithFinisherBlocking(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("What's the craic?")).build();

        CollectionSubscriber subscriber = new CollectionSubscriber();
        HttpResponse<String> response = client.send(request,
                BodyHandler.fromSubscriber(subscriber, CollectionSubscriber::get));
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "What's the craic?");
    }

    @Test(dataProvider = "uris")
    void testCollectionWithoutFinisheBlocking(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("What's the craic?")).build();

        CollectionSubscriber subscriber = new CollectionSubscriber();
        HttpResponse<Void> response = client.send(request,
                BodyHandler.fromSubscriber(subscriber));
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "What's the craic?");
    }

    // Iterable<ByteBuffer>

    @Test(dataProvider = "uris")
    void testIterableWithFinisher(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("We're sucking diesel now!")).build();

        IterableSubscriber subscriber = new IterableSubscriber();
        HttpResponse<String> response = client.sendAsync(request,
                BodyHandler.fromSubscriber(subscriber, Supplier::get)).join();
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "We're sucking diesel now!");
    }

    @Test(dataProvider = "uris")
    void testIterableWithoutFinisher(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("We're sucking diesel now!")).build();

        IterableSubscriber subscriber = new IterableSubscriber();
        HttpResponse<Void> response = client.sendAsync(request,
                BodyHandler.fromSubscriber(subscriber)).join();
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "We're sucking diesel now!");
    }

    @Test(dataProvider = "uris")
    void testIterableWithFinisherBlocking(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("We're sucking diesel now!")).build();

        IterableSubscriber subscriber = new IterableSubscriber();
        HttpResponse<String> response = client.send(request,
                BodyHandler.fromSubscriber(subscriber, Supplier::get));
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "We're sucking diesel now!");
    }

    @Test(dataProvider = "uris")
    void testIterableWithoutFinisherBlocking(String url) throws Exception{
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("We're sucking diesel now!")).build();

        IterableSubscriber subscriber = new IterableSubscriber();
        HttpResponse<Void> response = client.send(request,
                BodyHandler.fromSubscriber(subscriber));
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "We're sucking diesel now!");
    }

    // Subscriber<Object>

    @Test(dataProvider = "uris")
    void testObjectWithFinisher(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("May the wind always be at your back.")).build();

        ObjectSubscriber subscriber = new ObjectSubscriber();
        HttpResponse<String> response = client.sendAsync(request,
                BodyHandler.fromSubscriber(subscriber, ObjectSubscriber::get)).join();
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
    }

    @Test(dataProvider = "uris")
    void testObjectWithoutFinisher(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("May the wind always be at your back.")).build();

        ObjectSubscriber subscriber = new ObjectSubscriber();
        HttpResponse<Void> response = client.sendAsync(request,
                BodyHandler.fromSubscriber(subscriber)).join();
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
    }

    @Test(dataProvider = "uris")
    void testObjectWithFinisherBlocking(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("May the wind always be at your back.")).build();

        ObjectSubscriber subscriber = new ObjectSubscriber();
        HttpResponse<String> response = client.send(request,
                BodyHandler.fromSubscriber(subscriber, ObjectSubscriber::get));
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
    }

    @Test(dataProvider = "uris")
    void testObjectWithoutFinisherBlocking(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString("May the wind always be at your back.")).build();

        ObjectSubscriber subscriber = new ObjectSubscriber();
        HttpResponse<Void> response = client.send(request,
                BodyHandler.fromSubscriber(subscriber));
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
    }

    /** An abstract Subscriber that converts all received data into a String. */
    static abstract class AbstractSubscriber implements Supplier<String> {
        protected volatile Flow.Subscription subscription;
        protected volatile ByteArrayOutputStream baos = new ByteArrayOutputStream();
        protected volatile String text;

        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }
        public void onError(Throwable throwable) {
            throw new RuntimeException(throwable);
        }
        public void onComplete() {
            text = new String(baos.toByteArray(), UTF_8);
        }
        @Override public String get() { return text; }
    }

    static class ListSubscriber extends AbstractSubscriber
        implements Flow.Subscriber<List<ByteBuffer>>, Supplier<String>
    {
        @Override public void onNext(List<ByteBuffer> item) {
            for (ByteBuffer bb : item) {
                byte[] ba = new byte[bb.remaining()];
                bb.get(ba);
                uncheckedWrite(baos, ba);
            }
        }
    }

    static class CollectionSubscriber extends AbstractSubscriber
        implements Flow.Subscriber<Collection<ByteBuffer>>, Supplier<String>
    {
        @Override public void onNext(Collection<ByteBuffer> item) {
            for (ByteBuffer bb : item) {
                byte[] ba = new byte[bb.remaining()];
                bb.get(ba);
                uncheckedWrite(baos, ba);
            }
        }
    }

    static class IterableSubscriber extends AbstractSubscriber
        implements Flow.Subscriber<Iterable<ByteBuffer>>, Supplier<String>
    {
        @Override public void onNext(Iterable<ByteBuffer> item) {
            for (ByteBuffer bb : item) {
                byte[] ba = new byte[bb.remaining()];
                bb.get(ba);
                uncheckedWrite(baos, ba);
            }
        }
    }

    static class ObjectSubscriber extends AbstractSubscriber
        implements Flow.Subscriber<Object>, Supplier<String>
    {
        @Override public void onNext(Object item) {
            // What can anyone do with Object, cast or toString it ?
            uncheckedWrite(baos, item.toString().getBytes(UTF_8));
        }
    }

    static void uncheckedWrite(ByteArrayOutputStream baos, byte[] ba) {
        try {
            baos.write(ba);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        InetSocketAddress sa = new InetSocketAddress("localhost", 0);
        httpTestServer = HttpServer.create(sa, 0);
        httpTestServer.createContext("/http1/echo", new Http1EchoHandler());
        httpURI = "http://127.0.0.1:" + httpTestServer.getAddress().getPort() + "/http1/echo";

        httpsTestServer = HttpsServer.create(sa, 0);
        httpsTestServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        httpsTestServer.createContext("/https1/echo", new Http1EchoHandler());
        httpsURI = "https://127.0.0.1:" + httpsTestServer.getAddress().getPort() + "/https1/echo";

        http2TestServer = new Http2TestServer("127.0.0.1", false, 0);
        http2TestServer.addHandler(new Http2EchoHandler(), "/http2/echo");
        int port = http2TestServer.getAddress().getPort();
        http2URI = "http://127.0.0.1:" + port + "/http2/echo";

        https2TestServer = new Http2TestServer("127.0.0.1", true, 0);
        https2TestServer.addHandler(new Http2EchoHandler(), "/https2/echo");
        port = https2TestServer.getAddress().getPort();
        https2URI = "https://127.0.0.1:" + port + "/https2/echo";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        httpTestServer.stop(0);
        httpsTestServer.stop(0);
        http2TestServer.stop();
        https2TestServer.stop();
    }

    static class Http1EchoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }

    static class Http2EchoHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }
}
