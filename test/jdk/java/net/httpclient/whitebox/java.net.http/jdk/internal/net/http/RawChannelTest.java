/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import jdk.internal.net.http.websocket.RawChannel;
import org.testng.annotations.Test;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;

/*
 * This test exercises mechanics of _independent_ reads and writes on the
 * RawChannel. It verifies that the underlying implementation can manage more
 * than a single type of notifications at the same time.
 */
public class RawChannelTest {

    // can't use jdk.test.lib when injected in java.net.httpclient
    // Seed can be specified on the @run line with -Dseed=<seed>
    private static class RandomFactory {
        private static long getSeed() {
            long seed = Long.getLong("seed", new Random().nextLong());
            System.out.println("Seed from RandomFactory = "+seed+"L");
            return seed;
        }
        public static Random getRandom() {
            return new Random(getSeed());
        }
    }

    private static final Random RANDOM = RandomFactory.getRandom();
    private final AtomicLong clientWritten = new AtomicLong();
    private final AtomicLong serverWritten = new AtomicLong();
    private final AtomicLong clientRead = new AtomicLong();
    private final AtomicLong serverRead = new AtomicLong();
    private CompletableFuture<Void> outputCompleted = new CompletableFuture<>();
    private CompletableFuture<Void> inputCompleted = new CompletableFuture<>();

    /*
     * Since at this level we don't have any control over the low level socket
     * parameters, this latch ensures a write to the channel will stall at least
     * once (socket's send buffer filled up).
     */
    private final CountDownLatch writeStall = new CountDownLatch(1);
    private final CountDownLatch initialWriteStall = new CountDownLatch(1);

    /*
     * This one works similarly by providing means to ensure a read from the
     * channel will stall at least once (no more data available on the socket).
     */
    private final CountDownLatch readStall = new CountDownLatch(1);
    private final CountDownLatch initialReadStall = new CountDownLatch(1);

    private final AtomicInteger writeHandles = new AtomicInteger();
    private final AtomicInteger readHandles = new AtomicInteger();

    private final CountDownLatch exit = new CountDownLatch(1);

    @Test
    public void test() throws Exception {
        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(false);
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            int port = server.getLocalPort();
            TestServer testServer = new TestServer(server);
            testServer.start();

            final RawChannel chan = channelOf(port);
            print("RawChannel is %s", String.valueOf(chan));
            initialWriteStall.await();

            // It's very important not to forget the initial bytes, possibly
            // left from the HTTP thingy
            int initialBytes = chan.initialByteBuffer().remaining();
            print("RawChannel has %s initial bytes", initialBytes);
            clientRead.addAndGet(initialBytes);

            // tell the server we have read the initial bytes, so
            // that it makes sure there is something for us to
            // read next in case the initialBytes have already drained the

            // channel dry.
            initialReadStall.countDown();

            chan.registerEvent(new RawChannel.RawEvent() {

                private final ByteBuffer reusableBuffer = ByteBuffer.allocate(32768);

                @Override
                public int interestOps() {
                    return SelectionKey.OP_WRITE;
                }

                @Override
                public void handle() {
                    int i = writeHandles.incrementAndGet();
                    print("OP_WRITE #%s", i);
                    if (i > 3) { // Fill up the send buffer not more than 3 times
                        try {
                            chan.shutdownOutput();
                            outputCompleted.complete(null);
                        } catch (IOException e) {
                            outputCompleted.completeExceptionally(e);
                            e.printStackTrace();
                            closeChannel(chan);
                        }
                        return;
                    }
                    long total = 0;
                    try {
                        long n;
                        do {
                            ByteBuffer[] array = {reusableBuffer.slice()};
                            n = chan.write(array, 0, 1);
                            total += n;
                        } while (n > 0);
                        print("OP_WRITE clogged SNDBUF with %s bytes", total);
                        clientWritten.addAndGet(total);
                        chan.registerEvent(this);
                        writeStall.countDown(); // signal send buffer is full
                    } catch (IOException e) {
                        print("OP_WRITE failed: " + e);
                        outputCompleted.completeExceptionally(e);
                        closeChannel(chan);
                        throw new UncheckedIOException(e);
                    }
                }
            });

            chan.registerEvent(new RawChannel.RawEvent() {

                @Override
                public int interestOps() {
                    return SelectionKey.OP_READ;
                }

                @Override
                public void handle() {
                    int i = readHandles.incrementAndGet();
                    print("OP_READ #%s", i);
                    ByteBuffer read = null;
                    long total = 0;
                    while (true) {
                        try {
                            read = chan.read();
                        } catch (IOException e) {
                            inputCompleted.completeExceptionally(e);
                            closeChannel(chan);
                            e.printStackTrace();
                        }
                        if (read == null) {
                            print("OP_READ EOF");
                            inputCompleted.complete(null);
                            break;
                        } else if (!read.hasRemaining()) {
                            print("OP_READ stall");
                            try {
                                chan.registerEvent(this);
                            } catch (IOException e) {
                                print("OP_READ failed to register event: " + e);
                                inputCompleted.completeExceptionally(e);
                                closeChannel(chan);
                                throw new UncheckedIOException(e);
                            }
                            readStall.countDown();
                            break;
                        }
                        int r = read.remaining();
                        total += r;
                        clientRead.addAndGet(r);
                    }
                    print("OP_READ read %s bytes (%s total)", total, clientRead.get());
                }
            });

            CompletableFuture.allOf(outputCompleted,inputCompleted)
                    .whenComplete((r,t) -> {
                        closeChannel(chan);
                    });
            exit.await(); // All done, we need to compare results:
            assertEquals(clientRead.get(), serverWritten.get());
            assertEquals(serverRead.get(), clientWritten.get());
            Throwable serverError = testServer.failed.get();
            if (serverError != null) {
                throw new AssertionError("TestServer failed: "
                        + serverError, serverError);
            }
        }
    }

    private static void closeChannel(RawChannel chan) {
        print("closing channel");
        try {
            chan.close();
        } catch (IOException x) {
            print("Failed to close channel: " + x);
            x.printStackTrace();
        }
    }


    private static RawChannel channelOf(int port) throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/");
        print("raw channel to %s", uri.toString());
        HttpRequest req = HttpRequest.newBuilder(uri).build();
        // Switch on isWebSocket flag to prevent the connection from
        // being returned to the pool.
        HttpClient client = HttpClient.newHttpClient();
        HttpClientImpl clientImpl = ((HttpClientFacade)client).impl;
        HttpRequestImpl requestImpl = new HttpRequestImpl(req, null);
        requestImpl.isWebSocket(true);
        try {
            MultiExchange<Void> mex = new MultiExchange<>(req,
                    requestImpl,
                    clientImpl,
                    discarding(),
                    null);
            HttpResponse<?> r = mex.responseAsync(clientImpl.theExecutor())
                                   .get(30, SECONDS);
            return ((HttpResponseImpl) r).rawChannel();
        } finally {
           // Need to hold onto the client until the RawChannel is
           // created. This would not be needed if we had created
           // a WebSocket, but here we are fiddling directly
           // with the internals of HttpResponseImpl!
           java.lang.ref.Reference.reachabilityFence(client);
        }
    }

    private class TestServer extends Thread { // Powered by Slowpokes

        private final ServerSocket server;
        private final AtomicReference<Throwable> failed = new AtomicReference<>();

        TestServer(ServerSocket server) throws IOException {
            this.server = server;
        }

        private void fail(Closeable s, String actor, Throwable t) {
            failed.compareAndSet(null, t);
            print("Server %s got exception: %s", actor, t);
            t.printStackTrace();
            try {
                s.close();
            } catch (Exception x) {
                print("Server %s failed to close socket: %s", actor, t);
            }

        }

        @Override
        public void run() {
            try (Socket s = server.accept()) {
                InputStream is = s.getInputStream();
                OutputStream os = s.getOutputStream();

                processHttp(is, os);

                Thread reader = new Thread(() -> {
                    try {
                        print("Server reader started");
                        long n = readSlowly(is);
                        print("Server read %s bytes", n);
                        s.shutdownInput();
                    } catch (Exception e) {
                        fail(s, "reader", e);
                    }
                });

                Thread writer = new Thread(() -> {
                    try {
                        print("Server writer started");
                        long n = writeSlowly(os);
                        print("Server written %s bytes", n);
                        s.shutdownOutput();
                    } catch (Exception e) {
                        fail(s, "writer", e);
                    }
                });

                reader.start();
                writer.start();

                reader.join();
                writer.join();
            } catch (Exception e) {
                fail(server,"acceptor", e);
            } finally {
                exit.countDown();
            }
        }

        private void processHttp(InputStream is, OutputStream os)
                throws IOException
        {
            os.write("HTTP/1.1 200 OK\r\nContent-length: 0\r\n\r\n".getBytes());

            // write some initial bytes
            byte[] initial = byteArrayOfSize(1024);
            os.write(initial);
            os.flush();
            serverWritten.addAndGet(initial.length);
            initialWriteStall.countDown();

            byte[] buf = new byte[1024];
            String s = "";
            while (true) {
                int n = is.read(buf);
                if (n <= 0) {
                    throw new RuntimeException("Unexpected end of request");
                }
                s = s + new String(buf, 0, n);
                if (s.contains("\r\n\r\n")) {
                    break;
                }
            }
        }

        private long writeSlowly(OutputStream os) throws Exception {
            byte[] first = byteArrayOfSize(1024);
            long total = first.length;
            os.write(first);
            os.flush();
            serverWritten.addAndGet(first.length);

            // wait until initial bytes were read
            print("Server wrote total %d: awaiting initialReadStall", total);
            initialReadStall.await();

            // make sure there is something to read, otherwise readStall
            // will never be counted down.
            first = byteArrayOfSize(1024);
            os.write(first);
            os.flush();
            total += first.length;
            serverWritten.addAndGet(first.length);

            // Let's wait for the signal from the raw channel that its read has
            // stalled, and then continue sending a bit more stuff
            print("Server wrote total %d: awaiting readStall", total);
            readStall.await();
            print("readStall unblocked, writing 32k");
            for (int i = 0; i < 32; i++) {
                byte[] b = byteArrayOfSize(1024);
                os.write(b);
                os.flush();
                serverWritten.addAndGet(b.length);
                total += b.length;
                print("Server wrote total %d", total);
                TimeUnit.MILLISECONDS.sleep(1);
            }
            return total;
        }

        private long readSlowly(InputStream is) throws Exception {
            // Wait for the raw channel to fill up its send buffer
            writeStall.await();
            print("writingStall unblocked, start reading");
            long overall = 0;
            byte[] array = new byte[1024];
            for (int n = 0; n != -1; n = is.read(array)) {
                serverRead.addAndGet(n);
                TimeUnit.MILLISECONDS.sleep(1);
                overall += n;
                print("Server read total: %d", overall);
            }
            return overall;
        }
    }

    private static void print(String format, Object... args) {
        System.out.println(Thread.currentThread() + ": " + String.format(format, args));
    }

    private static byte[] byteArrayOfSize(int bound) {
        // bound must be > 1; No need to check it,
        // nextInt will throw IllegalArgumentException if needed
        return new byte[RANDOM.nextInt(1, bound + 1)];
    }
}
