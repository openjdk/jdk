/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import static org.testng.Assert.expectThrows;


/*
 * @test
 * @bug 8236105
 * @summary check that DatagramSocket, MulticastSocket,
 *          DatagramSocketAdaptor and DatagramChannel all
 *          throw expected Execption when passed a DatagramPacket
 *          with invalid details
 * @run testng/othervm SendCheck
 */

public class SendCheck {
    static final Class<IOException> IOE = IOException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;

    static final byte[] buf = {0, 1, 2};
    static DatagramSocket socket;

    @BeforeTest
    public void setUp() {
        try {
            socket = new DatagramSocket();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterTest
    public void closeDown() {
        socket.close();
    }

    static final class Packet {
        private Packet(String description, DatagramPacket packet) {
            this.description = description;
            this.packet = packet;
        }

        final String description;
        final DatagramPacket packet;

        public String toString() {
            return description;
        }

        public static Packet of(DatagramPacket packet) {
            InetAddress address = packet.getAddress();
            int port = packet.getPort();
            String description;
            if (address == null) {
                description = "<null>:" + port;
            } else if (port < 0) {
                description = packet.getAddress().toString() + ":" + port;
            } else {
                description = packet.getSocketAddress().toString();
            }
            return new Packet(description, packet);
        }
    }

    @DataProvider(name = "packets")
    static Object[][] providerIO() throws IOException {
        var wildcard = new InetSocketAddress(0);

        /*
        Commented until JDK-8236852 is fixed

        // loopback w/port 0 -- DC, DSA, MS, DS throws IO
        var pkt1 = new DatagramPacket(buf, 0, buf.length);
        pkt1.setAddress(InetAddress.getLoopbackAddress());
        pkt1.setPort(0);
         */

        /*
        Commented until JDK-8236852 is fixed

        // wildcard w/port 0 -- DC, DSA, MS, DS throws IO
        var pkt2 = new DatagramPacket(buf, 0, buf.length);
        pkt2.setAddress(wildcard.getAddress());
        pkt2.setPort(0);
        */

        // loopback w/port -1 -- DC, DSA, MS, DS throws IAE
        var pkt3 = new DatagramPacket(buf, 0, buf.length);
        pkt3.setAddress(InetAddress.getLoopbackAddress());

        // wildcard w/port -1 -- DC, DSA, MS, DS throws IAE
        var pkt4 = new DatagramPacket(buf, 0, buf.length);
        pkt4.setAddress(wildcard.getAddress());

        /*
        Commented until JDK-8236807 is fixed

        // wildcard addr w/valid port -- DS, MS throws IO ; DC, DSA doesn't throw
        var pkt5 = new DatagramPacket(buf, 0, buf.length);
        var addr1 = wildcard.getAddress();
        pkt5.setAddress(addr1);
        pkt5.setPort(socket.getLocalPort());
        */

        // PKTS 3 & 4: invalid port -1
        List<Packet> iaePackets = List.of(Packet.of(pkt3), Packet.of(pkt4));

        List<Sender> senders = List.of(
                Sender.of(new DatagramSocket(null)),
                Sender.of(new MulticastSocket(null), (byte) 0),
                Sender.of(DatagramChannel.open()),
                Sender.of(DatagramChannel.open().socket())
        );

        List<Object[]> testcases = new ArrayList<>();
        for (var p : iaePackets) {
            addTestCaseFor(testcases, senders, p, IAE);
        }

        return testcases.toArray(new Object[0][0]);
    }

    static void addTestCaseFor(List<Object[]> testcases,
                               List<Sender> senders, Packet p,
                               Class<? extends Throwable> exception) {
        for (var s : senders) {
            Object[] testcase = new Object[]{s, p, exception};
            testcases.add(testcase);
        }
    }

    @Test(dataProvider = "packets")
    public static void channelSendCheck(Sender<IOException> sender,
                                        Packet packet,
                                        Class<? extends Throwable> exception) {
        DatagramPacket pkt = packet.packet;
        if (exception != null) {
            Throwable t = expectThrows(exception, () -> sender.send(pkt));
            System.out.printf("%s got expected exception %s%n", packet.toString(), t);
        } else {
            try {
                sender.send(pkt);
            } catch (IOException x) {
                throw new AssertionError("Unexpected exception for " + sender + " / " + packet, x);
            }
        }
    }

    interface Sender<E extends Exception> extends AutoCloseable {
        void send(DatagramPacket p) throws E;

        void close() throws E;

        static Sender<IOException> of(DatagramSocket socket) {
            return new SenderImpl<>(socket, socket::send, socket::close);
        }

        static Sender<IOException> of(MulticastSocket socket, byte ttl) {
            SenderImpl.Send<IOException> send =
                    (pkt) -> socket.send(pkt, ttl);
            return new SenderImpl<>(socket, send, socket::close);
        }

        static Sender<IOException> of(DatagramChannel socket) {
            SenderImpl.Send<IOException> send =
                    (pkt) -> socket.send(ByteBuffer.wrap(pkt.getData()),
                            pkt.getSocketAddress());
            return new SenderImpl<>(socket, send, socket::close);
        }
    }

    static final class SenderImpl<E extends Exception> implements Sender<E> {
        @FunctionalInterface
        interface Send<E extends Exception> {
            void send(DatagramPacket p) throws E;
        }

        @FunctionalInterface
        interface Closer<E extends Exception> {
            void close() throws E;
        }

        private final Send<E> send;
        private final Closer<E> closer;
        private final Object socket;

        public SenderImpl(Object socket, Send<E> send, Closer<E> closer) {
            this.socket = socket;
            this.send = send;
            this.closer = closer;
        }

        @Override
        public void send(DatagramPacket p) throws E {
            send.send(p);
        }

        @Override
        public void close() throws E {
            closer.close();
        }

        @Override
        public String toString() {
            return socket.getClass().getSimpleName();
        }
    }
}
