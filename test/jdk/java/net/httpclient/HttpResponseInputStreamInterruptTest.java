/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8294047
 * @library /test/lib
 * @run junit HttpResponseInputStreamInterruptTest
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HttpResponseInputStreamInterruptTest {

    HttpServer server;
    int port;
    private final CountDownLatch interruptReadyLatch = new CountDownLatch(2);
    private final CountDownLatch interruptDoneLatch = new CountDownLatch(1);
    static final String FIRST_MESSAGE = "Should be received";
    static final String SECOND_MESSAGE = "Shouldn't be received";

    @BeforeAll
    void before() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        InetSocketAddress addr = new InetSocketAddress(loopback, 0);
        server = HttpServer.create(addr, 0);
        port = server.getAddress().getPort();
        Handler handler = new Handler(interruptReadyLatch, interruptDoneLatch);
        server.createContext("/HttpResponseInputStreamInterruptTest/", handler);
        server.start();
    }

    @AfterAll
    void after() throws Exception {
        server.stop(0);
    }

    @Test
    public void test() throws Exception {
        // create client and interrupter threads
        Thread clientThread = createClientThread(interruptReadyLatch, port);
        Thread interrupterThread = new Thread(() -> {
            try {
                // wait until the clientThread is just about to read the second message sent by the server
                // then interrupt the thread to cause an error to be thrown
                interruptReadyLatch.await();
                clientThread.interrupt();
                interruptDoneLatch.countDown();
            } catch (InterruptedException e) {
                System.out.println("interrupterThread failed");
                throw new RuntimeException(e);
            }
        });

        // Start the threads then wait until clientThread completes
        clientThread.start();
        interrupterThread.start();
        clientThread.join();
    }

    static class Handler implements HttpHandler {

        CountDownLatch interruptReadyLatch;
        CountDownLatch interruptDoneLatch;

        public Handler(CountDownLatch interruptReadyLatch, CountDownLatch interruptDoneLatch) {
            this.interruptReadyLatch = interruptReadyLatch;
            this.interruptDoneLatch = interruptDoneLatch;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] workingResponse = FIRST_MESSAGE.getBytes();
                byte[] errorResponse = SECOND_MESSAGE.getBytes();
                exchange.sendResponseHeaders(200, workingResponse.length + errorResponse.length);

                // write and flush the first message which is expected to be received successfully
                os.write(workingResponse);
                os.flush();

                // await the interrupt threads completion, then write the second message
                interruptReadyLatch.countDown();
                interruptDoneLatch.await();
                os.write(errorResponse);
            } catch (InterruptedException e) {
                System.out.println("interruptDoneLatch await failed");
                throw new RuntimeException(e);
            }
        }
    }

    static Thread createClientThread(CountDownLatch interruptReadyLatch, int port) {
        return new Thread(() -> {
            try {
                HttpClient client = HttpClient
                        .newBuilder()
                        .proxy(HttpClient.Builder.NO_PROXY)
                        .build();

                URI uri = URIBuilder.newBuilder()
                        .scheme("http")
                        .loopback()
                        .port(port)
                        .path("/HttpResponseInputStreamInterruptTest/")
                        .build();

                HttpRequest request = HttpRequest
                        .newBuilder(uri)
                        .GET()
                        .build();

                // Send a httpRequest and assert the first response is received as expected
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                String firstOutput = new String(response.body().readNBytes(FIRST_MESSAGE.getBytes().length));
                assertEquals(firstOutput, FIRST_MESSAGE);

                // countdown on latch, and assert that an IOException is throw due to the interrupt
                // and assert that the cause is a InterruptedException
                interruptReadyLatch.countDown();
                var thrown = assertThrows(IOException.class, () -> response.body().readAllBytes(), "expected IOException");
                var cause = thrown.getCause();
                assertTrue(cause instanceof InterruptedException, cause + " is not an InterruptedException");
                var thread = Thread.currentThread();
                assertTrue(thread.isInterrupted(), "Thread " + thread + " is not interrupted");
            } catch (Throwable t) {
                t.printStackTrace();
                fail();
            }
        });
    }
}
