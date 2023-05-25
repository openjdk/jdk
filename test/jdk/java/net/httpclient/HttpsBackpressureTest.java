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

import jdk.test.lib.net.SimpleSSLContext;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.err;

/**
 * @test
 * @summary This test verifies that when performing an HTTPS request, there
 * is no uncontrolled read of the response.
 * @bug 8308144
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext HttpsBackpressureTest
 * @run main/othervm -Dtest.requiresHost=true
 * -Djdk.httpclient.HttpClient.log=headers
 * -Djdk.internal.httpclient.debug=true HttpsBackpressureTest
 */

public class HttpsBackpressureTest {
    static int WRITE_BUFFER_SIZE = 300_000;
    static int WRITES = 10;
    static int ALLOWED_WRITES = 2;

    static final SSLContext context;

    static {
        try {
            context = new SimpleSSLContext().get();
            SSLContext.setDefault(context);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    public static void main(String[] args) throws Exception {
        var server = new DummyHttpsServer(WRITE_BUFFER_SIZE, WRITES, context);

        var client = HttpClient.newBuilder().sslContext(context).build();
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + server.addr + "/"))
                    .build();
            client.sendAsync(request, (info) -> new NoopSubscriber());

            Thread.sleep(2000);

            var writes = server.writes.get();
            if (writes > ALLOWED_WRITES) {
                throw new RuntimeException("Too large intermediate buffer, server sent " +
                        writes + "x" + WRITE_BUFFER_SIZE + " bytes");
            }
        } catch (Throwable t) {
            err.println("Unexpected exception: exiting: " + t);
            t.printStackTrace();
            throw t;
        } finally {
            client.shutdownNow();
            server.close();
        }
    }

    static class NoopSubscriber implements HttpResponse.BodySubscriber<NoopSubscriber> {
        @Override
        public CompletionStage<NoopSubscriber> getBody() {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }
    }

    static class DummyHttpsServer implements AutoCloseable {
        private final int bufSize;
        private final int bufCount;
        final AtomicInteger writes = new AtomicInteger();

        final ServerSocket serverSocket;
        final Thread serverThread;
        final String addr;

        public DummyHttpsServer(int bufSize, int bufCount, SSLContext ctx) throws IOException {
            this.bufSize = bufSize;
            this.bufCount = bufCount;

            serverSocket = ctx.getServerSocketFactory().createServerSocket(0, 10, InetAddress.getLoopbackAddress());
            addr = InetAddress.getLoopbackAddress().getHostAddress() + ":" + serverSocket.getLocalPort();

            serverThread = new Thread(this::handleConnection);
            serverThread.setDaemon(false);
            serverThread.start();
        }

        void readHeaders(InputStream is) throws IOException {
            var sb = new StringBuilder();
            var buf = new byte[128];
            while (sb.indexOf("\r\n\r\n") == -1) {
                if (sb.length() > 3) {
                    sb.delete(0, sb.length() - 3);
                }
                int c = is.read(buf);
                sb.append(new String(buf, 0, c, StandardCharsets.ISO_8859_1));
            }
        }

        public void handleConnection() {
            try {
                var socket = serverSocket.accept();

                readHeaders(socket.getInputStream());

                var os = socket.getOutputStream();
                var headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-length: " + (bufSize * bufCount) + "\r\n" +
                        "\r\n";
                os.write(headers.getBytes());

                var buf = new byte[bufSize];
                for (int i = 0; i < bufCount; i++) {
                    ThreadLocalRandom.current().nextBytes(buf);
                    os.write(buf);
                    writes.incrementAndGet();
                }

                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            serverThread.interrupt();
        }
    }
}
