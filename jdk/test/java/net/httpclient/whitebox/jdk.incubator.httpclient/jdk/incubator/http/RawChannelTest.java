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

package jdk.incubator.http;

import jdk.incubator.http.internal.websocket.RawChannel;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static jdk.incubator.http.HttpResponse.BodyHandler.discard;
import static org.testng.Assert.assertEquals;

/*
 * This test exercises mechanics of _independent_ reads and writes on the
 * RawChannel. It verifies that the underlying implementation can manage more
 * than a single type of notifications at the same time.
 */
public class RawChannelTest {

    private final AtomicLong clientWritten = new AtomicLong();
    private final AtomicLong serverWritten = new AtomicLong();
    private final AtomicLong clientRead = new AtomicLong();
    private final AtomicLong serverRead = new AtomicLong();

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
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            new TestServer(server).start();

            final RawChannel chan = channelOf(port);
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
                        } catch (IOException e) {
                            e.printStackTrace();
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
                            e.printStackTrace();
                        }
                        if (read == null) {
                            print("OP_READ EOF");
                            break;
                        } else if (!read.hasRemaining()) {
                            print("OP_READ stall");
                            try {
                                chan.registerEvent(this);
                            } catch (IOException e) {
                                e.printStackTrace();
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
            exit.await(); // All done, we need to compare results:
            assertEquals(clientRead.get(), serverWritten.get());
            assertEquals(serverRead.get(), clientWritten.get());
        }
    }

    private static RawChannel channelOf(int port) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port + "/");
        print("raw channel to %s", uri.toString());
        HttpRequest req = HttpRequest.newBuilder(uri).build();
        HttpResponse<?> r = HttpClient.newHttpClient().send(req, discard(null));
        r.body();
        return ((HttpResponseImpl) r).rawChannel();
    }

    private class TestServer extends Thread { // Powered by Slowpokes

        private final ServerSocket server;

        TestServer(ServerSocket server) throws IOException {
            this.server = server;
        }

        @Override
        public void run() {
            try (Socket s = server.accept()) {
                InputStream is = s.getInputStream();
                OutputStream os = s.getOutputStream();

                processHttp(is, os);

                Thread reader = new Thread(() -> {
                    try {
                        long n = readSlowly(is);
                        print("Server read %s bytes", n);
                        serverRead.addAndGet(n);
                        s.shutdownInput();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                Thread writer = new Thread(() -> {
                    try {
                        long n = writeSlowly(os);
                        print("Server written %s bytes", n);
                        serverWritten.addAndGet(n);
                        s.shutdownOutput();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                reader.start();
                writer.start();

                reader.join();
                writer.join();
            } catch (Exception e) {
                e.printStackTrace();
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

            // wait until initial bytes were read
            initialReadStall.await();

            // make sure there is something to read, otherwise readStall
            // will never be counted down.
            first = byteArrayOfSize(1024);
            os.write(first);
            os.flush();
            total += first.length;

            // Let's wait for the signal from the raw channel that its read has
            // stalled, and then continue sending a bit more stuff
            readStall.await();
            for (int i = 0; i < 32; i++) {
                byte[] b = byteArrayOfSize(1024);
                os.write(b);
                os.flush();
                total += b.length;
                TimeUnit.MILLISECONDS.sleep(1);
            }
            return total;
        }

        private long readSlowly(InputStream is) throws Exception {
            // Wait for the raw channel to fill up its send buffer
            writeStall.await();
            long overall = 0;
            byte[] array = new byte[1024];
            for (int n = 0; n != -1; n = is.read(array)) {
                TimeUnit.MILLISECONDS.sleep(1);
                overall += n;
            }
            return overall;
        }
    }

    private static void print(String format, Object... args) {
        System.out.println(Thread.currentThread() + ": " + String.format(format, args));
    }

    private static byte[] byteArrayOfSize(int bound) {
        return new byte[new Random().nextInt(1 + bound)];
    }
}
