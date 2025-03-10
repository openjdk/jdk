/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @build jdk.test.lib.Platform Connect
 * @run main/othervm Connect
 * @summary Test DatagramChannel's send and receive methods
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import jdk.test.lib.Platform;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class Connect {

    static final PrintStream err = System.err;
    static final String TIME_STAMP = Instant.now().toString();
    static final String MESSAGE = "Hello " + TIME_STAMP;
    static final String OTHER = "Hey " + TIME_STAMP;
    static final String RESPONSE = "Hi " + TIME_STAMP;
    static final int MAX = Math.max(256, MESSAGE.getBytes(US_ASCII).length + 16);

    public static void main(String[] args) throws Exception {
        assert MAX > MESSAGE.getBytes(US_ASCII).length;
        assert MAX > OTHER.getBytes(US_ASCII).length;
        assert MAX > RESPONSE.getBytes(US_ASCII).length;
        test();
    }

    static void test() throws Exception {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        try (Responder r = new Responder();
             Initiator a = new Initiator(r.getSocketAddress())
        ) {
            invoke(threadPool, a, r);
        } finally {
            threadPool.shutdown();
        }
    }

    static void invoke(ExecutorService e, Runnable reader, Runnable writer) throws CompletionException {
        CompletableFuture<Void> f1 = CompletableFuture.runAsync(writer, e);
        CompletableFuture<Void> f2 = CompletableFuture.runAsync(reader, e);
        wait(f1, f2);
    }


    // This method waits for either one of the given futures to complete exceptionally
    // or for all of the given futures to complete successfully.
    private static void wait(CompletableFuture<?>... futures) throws CompletionException {
        CompletableFuture<?> future = CompletableFuture.allOf(futures);
        Stream.of(futures)
                .forEach(f -> f.exceptionally(ex -> {
                    future.completeExceptionally(ex);
                    return null;
                }));
        future.join();
    }

    private static SocketAddress toConnectAddress(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            var inet = (InetSocketAddress) address;
            if (inet.getAddress().isAnyLocalAddress()) {
                // if the peer is bound to the wildcard address, use
                // the loopback address to connect.
                var loopback = InetAddress.getLoopbackAddress();
                return new InetSocketAddress(loopback, inet.getPort());
            }
        }
        return address;
    }

    public static class Initiator implements AutoCloseable, Runnable {
        final SocketAddress connectSocketAddress;
        final DatagramChannel dc;

        Initiator(SocketAddress peerSocketAddress) throws IOException {
            this.connectSocketAddress = toConnectAddress(peerSocketAddress);
            dc = DatagramChannel.open();
        }

        public void run() {
            try {
                byte[] bytes = MESSAGE.getBytes(US_ASCII);
                ByteBuffer bb = ByteBuffer.allocateDirect(MAX);
                bb.put(bytes);
                bb.flip();
                // When connecting an unbound datagram channel, the underlying
                // socket will first be bound to the wildcard address. On macOS,
                // the system may allocate the same port on which another socket
                // is already bound with a more specific address. This may prevent
                // datagrams directed at the connected socket to reach it.
                // To avoid this, when on macOS, we preemptively bind `dc` to the
                // specific address instead of letting it bind to the wildcard.
                if (Platform.isOSX()) {
                    dc.bind(new InetSocketAddress(((InetSocketAddress)connectSocketAddress).getAddress(), 0));
                    err.println("Initiator bound to: " + connectSocketAddress);
                }
                err.println("Initiator connecting to: " + connectSocketAddress);
                dc.connect(connectSocketAddress);
                err.println("Initiator bound to: " + dc.getLocalAddress());
                assert !connectSocketAddress.equals(dc.getLocalAddress());

                // Send a message
                err.println("Initiator attempting to write to Responder at " + connectSocketAddress);
                dc.write(bb);

                // Try to send to some other address
                try {
                    int port = dc.socket().getLocalPort();
                    InetAddress loopback = InetAddress.getLoopbackAddress();
                    try (DatagramChannel other = DatagramChannel.open()) {
                        InetSocketAddress otherAddress = new InetSocketAddress(loopback, 0);
                        other.bind(otherAddress);
                        err.println("Testing if Initiator throws AlreadyConnectedException");
                        otherAddress = (InetSocketAddress) other.getLocalAddress();
                        assert port != otherAddress.getPort();
                        assert !connectSocketAddress.equals(otherAddress);
                        err.printf("Initiator sending \"%s\" to other address %s%n", OTHER, otherAddress);
                        dc.send(ByteBuffer.wrap(OTHER.getBytes(US_ASCII)), otherAddress);
                    }
                    throw new RuntimeException("Initiator allowed send to other address while already connected");
                } catch (AlreadyConnectedException ace) {
                    // Correct behavior
                    err.println("Initiator got expected " + ace);
                }

                // wait for response
                while (true) {
                    // zero out buffer
                    bb.clear();
                    bb.put(new byte[bb.remaining()]);
                    bb.flip();

                    // Read a reply
                    err.println("Initiator waiting to read");
                    dc.read(bb);
                    bb.flip();
                    CharBuffer cb = US_ASCII.newDecoder().decode(bb);
                    err.println("Initiator received from Responder at " + connectSocketAddress + ": " + cb);
                    if (!RESPONSE.equals(cb.toString())) {
                        err.println("Initiator received unexpected message: continue waiting");
                        continue;
                    }
                    break;
                }
            } catch (Exception ex) {
                err.println("Initiator threw exception: " + ex);
                throw new RuntimeException(ex);
            } finally {
                err.println("Initiator finished");
            }
        }

        @Override
        public void close() throws IOException {
            dc.close();
        }
    }

    public static class Responder implements AutoCloseable, Runnable {
        final DatagramChannel dc;

        Responder() throws IOException {
            var address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            dc = DatagramChannel.open().bind(address);
        }

        SocketAddress getSocketAddress() throws IOException {
            return dc.getLocalAddress();
        }

        public void run() {
            ByteBuffer bb = ByteBuffer.allocateDirect(MAX);
            try {
                while (true) {
                    // Listen for a message
                    err.println("Responder waiting to receive");
                    SocketAddress sa = dc.receive(bb);
                    bb.flip();
                    CharBuffer cb = US_ASCII.
                            newDecoder().decode(bb);
                    err.println("Responder received from Initiator at " + sa + ": " + cb);
                    if (!MESSAGE.equals(cb.toString())) {
                        err.println("Responder received unexpected message: continue waiting");
                        bb.clear();
                        continue;
                    }

                    // Reply to sender
                    dc.connect(sa);
                    bb.clear();
                    bb.put(RESPONSE.getBytes(US_ASCII));
                    bb.flip();
                    err.println("Responder attempting to write: " + dc.getRemoteAddress());
                    dc.write(bb);
                    bb.flip();
                    break;
                }
            } catch (Exception ex) {
                err.println("Responder threw exception: " + ex);
                throw new RuntimeException(ex);
            } finally {
                err.println("Responder finished");
            }
        }

        @Override
        public void close() throws IOException {
            dc.close();
        }
    }
}
