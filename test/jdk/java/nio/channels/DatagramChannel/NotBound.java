/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4512723 6621689
 * @summary Test that connect/send/receive with unbound DatagramChannel causes
 *     the channel's socket to be bound to a local address.
 * @run main/othervm NotBound
 */

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NotBound {

    static final CountDownLatch received = new CountDownLatch(1);

    static void checkBound(DatagramChannel dc) throws IOException {
        if (dc.getLocalAddress() == null)
            throw new RuntimeException("Not bound??");
        System.out.println("Bound to: " + dc.getLocalAddress());
    }

    // starts a thread to send a datagram to the given channel once the channel
    // is bound to a local address
    static void wakeupWhenBound(final DatagramChannel dc) {
        Runnable wakeupTask = new Runnable() {
            public void run() {
                try {
                    // poll for local address
                    InetSocketAddress local;
                    do {
                        Thread.sleep(50);
                        local = (InetSocketAddress)dc.getLocalAddress();
                    } while (local == null);
                    System.out.format("receiver bound to: %s%n", local);

                    boolean isAnyLocal = local.getAddress().isAnyLocalAddress();
                    int maxAttempts = 5;
                    int localPort = 0;
                    List<InetAddress> llh = isAnyLocal
                            ? List.of(InetAddress.getLocalHost(), InetAddress.getLoopbackAddress())
                            : List.of(local.getAddress());
                    SocketAddress target = null;
                    for (int i = 0 ; i < maxAttempts ; i++) {
                        InetAddress lh = llh.get(i % llh.size());
                        target = new InetSocketAddress(lh, local.getPort());
                        // send message to channel to wakeup receiver
                        try (DatagramChannel sender = DatagramChannel.open()) {
                            ByteBuffer bb = ByteBuffer.wrap("NotBound: hello".getBytes());
                            sender.send(bb, target);
                            System.out.format("Woke up receiver: sent datagram to %s from %s%n",
                                    target, sender.getLocalAddress());
                            localPort = ((InetSocketAddress)sender.getLocalAddress()).getPort();
                        }
                        if (received.await(250, TimeUnit.MILLISECONDS)) {
                            // The datagram has been received: no need to continue
                            // sending
                            break;
                        }
                        // if sender port and destination port were identical, which
                        // could happen on some systems, the receiver might not receive
                        // the datagram. So in that case we try again, bailing out if
                        // we had to retry too many times
                        if (localPort == local.getPort()) {
                            System.out.println("Local port and peer port are identical. Retrying...");
                        } else {
                            System.out.println("Datagram not received after 250ms. Retrying...");
                        }
                    }
                    if (localPort == local.getPort()) {
                        System.out.println("Couldn't find a port to send to " + target);
                    }
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }};
        new Thread(wakeupTask).start();
    }

    public static void main(String[] args) throws IOException {
        DatagramChannel dc;

        // connect
        dc = DatagramChannel.open();
        try {
            System.out.println("Check that connect() binds the socket");
            try (DatagramChannel peer = DatagramChannel.open()) {
                peer.bind(new InetSocketAddress(0));
                int peerPort = ((InetSocketAddress)(peer.getLocalAddress())).getPort();
                dc.connect(new InetSocketAddress(InetAddress.getLocalHost(), peerPort));
                checkBound(dc);
            }
        } finally {
            dc.close();
        }

        // send
        dc = DatagramChannel.open();
        try {
            System.out.println("Check that send() binds the socket");
            ByteBuffer bb = ByteBuffer.wrap("NotBound: ignore this".getBytes());
            SocketAddress target =
                new InetSocketAddress(InetAddress.getLocalHost(), 5000);
            dc.send(bb, target);
            checkBound(dc);
        } finally {
            dc.close();
        }

        // receive (blocking)
        dc = DatagramChannel.open();
        try {
            System.out.println("Check that blocking receive() binds the socket");
            ByteBuffer bb = ByteBuffer.allocateDirect(128);
            wakeupWhenBound(dc);
            SocketAddress sender = dc.receive(bb);
            received.countDown();
            if (sender == null)
                throw new RuntimeException("Sender should not be null");
            checkBound(dc);
        } finally {
            dc.close();
        }

        // receive (non-blocking)
        dc = DatagramChannel.open();
        try {
            System.out.println("Check that non-blocking receive() binds the socket");
            dc.configureBlocking(false);
            ByteBuffer bb = ByteBuffer.allocateDirect(128);
            SocketAddress sender = dc.receive(bb);
            if (sender != null)
                throw new RuntimeException("Sender should be null");
            checkBound(dc);
        } finally {
            dc.close();
        }
    }
}
