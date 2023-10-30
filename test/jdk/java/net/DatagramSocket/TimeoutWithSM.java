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

/**
 * @test
 * @summary Test a timed DatagramSocket.receive with a SecurityManager set
 * @run main/othervm -Djava.security.manager=allow TimeoutWithSM
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.security.Permission;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeoutWithSM {

    private static final int TIMEOUT = 10_000;

    public static void main(String[] args) throws Exception {
        try (var socket = new DatagramSocket(null)) {
            InetAddress lb = InetAddress.getLoopbackAddress();
            socket.bind(new InetSocketAddress(lb, 0));

            // start sender to send datagrams to us
            var done = new AtomicBoolean();
            startSender(socket.getLocalSocketAddress(), done);

            // set a SecurityManager that blocks datagrams from sender
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(Permission p) {
                }
                @Override
                public void checkAccept(String host, int port) {
                    var isa = new InetSocketAddress(host, port);
                    System.out.println("checkAccept " + isa);
                    throw new SecurityException();
                }
            });

            // timed receive, should throw SocketTimeoutException
            try {
                socket.setSoTimeout(TIMEOUT);
                try {
                    byte[] bytes = new byte[1024];
                    DatagramPacket p = new DatagramPacket(bytes, bytes.length);
                    socket.receive(p);
                    throw new RuntimeException("Packet received, unexpected!!! "
                            + " sender=" + p.getSocketAddress() + ", len=" + p.getLength());
                } catch (SocketTimeoutException expected) {
                    System.out.println(expected + ", expected!!!");
                }
            } finally {
                done.set(true);
            }
        }
    }

    /**
     * Start a thread to send datagrams to the given target address at intervals of
     * one second. The sender stops when done is set to true.
     */
    static void startSender(SocketAddress target, AtomicBoolean done) throws Exception {
        assert target instanceof InetSocketAddress isa && isa.getAddress().isLoopbackAddress();
        var sender = new DatagramSocket(null);
        boolean started = false;
        try {
            InetAddress lb = InetAddress.getLoopbackAddress();
            sender.bind(new InetSocketAddress(lb, 0));
            Thread.ofPlatform().start(() -> {
                try {
                    try (sender) {
                        byte[] bytes = "hello".getBytes("UTF-8");
                        DatagramPacket p = new DatagramPacket(bytes, bytes.length);
                        p.setSocketAddress(target);
                        while (!done.get()) {
                            System.out.println("Send datagram to " + target + " ...");
                            sender.send(p);
                            Thread.sleep(Duration.ofSeconds(1));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            started = true;
        } finally {
            if (!started) {
                sender.close();
            }
        }
    }
}
