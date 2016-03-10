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

/**
 * @test
 * @bug 8151299
 * @summary Http client SelectorManager overwriting read and write events
 */
package java.net.http;

import java.net.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static java.lang.System.out;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.testng.annotations.Test;

/**
 * Whitebox test of selector mechanics. Currently only a simple test
 * setting one read and one write event is done. It checks that the
 * write event occurs first, followed by the read event and then no
 * further events occur despite the conditions actually still existing.
 */
@Test
public class SelectorTest {

    AtomicInteger counter = new AtomicInteger();
    volatile boolean error;
    static final CountDownLatch finishingGate = new CountDownLatch(1);

    String readSomeBytes(RawChannel chan) {
        try {
            ByteBuffer buf = ByteBuffer.allocate(1024);
            int t = chan.read(buf);
            if (t <= 0) {
                out.printf("chan read returned %d\n", t);
                return null;
            }
            byte[] bb = new byte[t];
            buf.get(bb);
            return new String(bb, US_ASCII);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    @Test(timeOut = 10000)
    public void test() throws Exception {

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();

            out.println("Listening on port " + server.getLocalPort());

            TestServer t = new TestServer(server);
            t.start();
            out.println("Started server thread");

            final RawChannel chan = getARawChannel(port);

            chan.registerEvent(new RawChannel.NonBlockingEvent() {
                @Override
                public int interestOps() {
                    return SelectionKey.OP_READ;
                }

                @Override
                public void handle() {
                    readSomeBytes(chan);
                    out.printf("OP_READ\n");
                    if (counter.get() != 1) {
                        out.printf("OP_READ error counter = %d\n", counter);
                        error = true;
                    }
                }
            });

            chan.registerEvent(new RawChannel.NonBlockingEvent() {
                @Override
                public int interestOps() {
                    return SelectionKey.OP_WRITE;
                }

                @Override
                public void handle() {
                    out.printf("OP_WRITE\n");
                    if (counter.get() != 0) {
                        out.printf("OP_WRITE error counter = %d\n", counter);
                        error = true;
                    } else {
                        ByteBuffer bb = ByteBuffer.wrap(TestServer.INPUT);
                        counter.incrementAndGet();
                        try {
                            chan.write(bb);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                }

            });
            out.println("Events registered. Waiting");
            finishingGate.await(30, SECONDS);
            if (error)
                throw new RuntimeException("Error");
            else
                out.println("No error");
        }
    }

    static RawChannel getARawChannel(int port) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port + "/");
        out.println("client connecting to " + uri.toString());
        HttpRequest req = HttpRequest.create(uri).GET();
        HttpResponse r = req.response();
        r.body(HttpResponse.ignoreBody());
        return ((HttpResponseImpl) r).rawChannel();
    }

    static class TestServer extends Thread {
        static final byte[] INPUT = "Hello world".getBytes(US_ASCII);
        static final byte[] OUTPUT = "Goodbye world".getBytes(US_ASCII);
        static final String FIRST_RESPONSE = "HTTP/1.1 200 OK\r\nContent-length: 0\r\n\r\n";
        final ServerSocket server;

        TestServer(ServerSocket server) throws IOException {
            this.server = server;
        }

        public void run() {
            try (Socket s = server.accept();
                 InputStream is = s.getInputStream();
                 OutputStream os = s.getOutputStream()) {

                out.println("Got connection");
                readRequest(is);
                os.write(FIRST_RESPONSE.getBytes());
                read(is);
                write(os);
                Thread.sleep(1000);
                // send some more data, and make sure WRITE op does not get called
                write(os);
                out.println("TestServer exiting");
                SelectorTest.finishingGate.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // consumes the HTTP request
        static void readRequest(InputStream is) throws IOException {
            out.println("starting readRequest");
            byte[] buf = new byte[1024];
            String s = "";
            while (true) {
                int n = is.read(buf);
                if (n <= 0)
                    throw new IOException("Error");
                s = s + new String(buf, 0, n);
                if (s.indexOf("\r\n\r\n") != -1)
                    break;
            }
            out.println("returning from readRequest");
        }

        static void read(InputStream is) throws IOException {
            out.println("starting read");
            for (int i = 0; i < INPUT.length; i++) {
                int c = is.read();
                if (c == -1)
                    throw new IOException("closed");
                if (INPUT[i] != (byte) c)
                    throw new IOException("Error. Expected:" + INPUT[i] + ", got:" + c);
            }
            out.println("returning from read");
        }

        static void write(OutputStream os) throws IOException {
            out.println("doing write");
            os.write(OUTPUT);
        }
    }
}
