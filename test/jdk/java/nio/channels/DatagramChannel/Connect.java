/*
 * Copyright (c) 2001, 2020, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4313882 7183800
 * @summary Test DatagramChannel's send and receive methods
 * @author Mike McCloskey
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

public class Connect {

    static PrintStream log = System.err;

    public static void main(String[] args) throws Exception {
        test();
    }

    static void test() throws Exception {
        Reactor r = new Reactor();
        Actor a = new Actor(r.port());
        invoke(a, r);
    }

    static void invoke(Runnable reader, Runnable writer) throws CompletionException {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        try {
            CompletableFuture<Void> f1 = CompletableFuture.runAsync(writer, threadPool);
            CompletableFuture<Void> f2 = CompletableFuture.runAsync(reader, threadPool);
            wait(f1, f2);
        } finally {
            threadPool.shutdown();
        }
    }

    // This method waits until one of the given CompletableFutures completes exceptionally. In which case, it stops waiting for the other futures and
    // throws a CompletionException. Otherwise, will wait for all futures to complete successfully.
    private static void wait(CompletableFuture<?>... futures) throws CompletionException {
        CompletableFuture<?> future = CompletableFuture.allOf(futures);
        Stream.of(futures)
                .forEach(f -> {
                    f.exceptionally(ex -> {
                        future.completeExceptionally(ex);
                        return null;
                    });
                });
        future.join();
    }

    public static class Actor implements Runnable {
        final int port;

        Actor(int port) {
            this.port = port;
        }

        public void run() {
            try (DatagramChannel dc = DatagramChannel.open()) {
                ByteBuffer bb = ByteBuffer.allocateDirect(256);
                bb.put("hello".getBytes());
                bb.flip();
                InetAddress address = InetAddress.getLoopbackAddress();
                InetSocketAddress isa = new InetSocketAddress(address, port);
                dc.connect(isa);

                // Send a message
                log.println("Actor attempting to write to Reactor at " + isa.toString());
                dc.write(bb);

                // Try to send to some other address
                try {
                    InetSocketAddress otherAddress = new InetSocketAddress(address, (port == 3333 ? 3332 : 3333));
                    log.println("Testing if Actor throws already connected exception when attempting to send to " + otherAddress.toString());
                    dc.send(bb, otherAddress);
                    throw new RuntimeException("Actor allowed send to other address while already connected");
                } catch (AlreadyConnectedException ace) {
                    // Correct behavior
                }

                // Read a reply
                bb.flip();
                log.println("Actor waiting to read");
                dc.read(bb);
                bb.flip();
                CharBuffer cb = StandardCharsets.US_ASCII.
                        newDecoder().decode(bb);
                log.println("Actor received from Reactor at " + isa + ": " + cb);

                // Clean up
                dc.disconnect();
            } catch (Exception ex) {
                log.println("Actor threw exception: " + ex);
                throw new RuntimeException(ex);
            } finally {
                log.println("Actor finished");
            }
        }
    }

    public static class Reactor implements Runnable {
        final DatagramChannel dc;

        Reactor() throws IOException {
            dc = DatagramChannel.open().bind(new InetSocketAddress(0));
        }

        int port() {
            return dc.socket().getLocalPort();
        }

        public void run() {
            try {
                // Listen for a message
                ByteBuffer bb = ByteBuffer.allocateDirect(100);
                log.println("Reactor waiting to receive");
                SocketAddress sa = dc.receive(bb);
                bb.flip();
                CharBuffer cb = StandardCharsets.US_ASCII.
                        newDecoder().decode(bb);
                log.println("Reactor received from Actor at" + sa +  ": " + cb);

                // Reply to sender
                dc.connect(sa);
                bb.flip();
                log.println("Reactor attempting to write: " + dc.getRemoteAddress().toString());
                dc.write(bb);

                // Clean up
                dc.disconnect();
                dc.close();
            } catch (Exception ex) {
                log.println("Reactor threw exception: " + ex);
                throw new RuntimeException(ex);
            } finally {
                log.println("Reactor finished");
            }
        }
    }
}
