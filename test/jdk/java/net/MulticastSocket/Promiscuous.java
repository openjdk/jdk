/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 *

/* @test
 * @bug 8014499
 * @summary Test for interference when two sockets are bound to the same
 *          port but joined to different multicast groups
 * @run main Promiscuous
 * @run main/othervm -Djava.net.preferIPv4Stack=true Promiscuous
 */

import java.io.IOException;
import static java.lang.System.out;
import java.net.*;

public class Promiscuous {

    static final int TIMEOUT =  5 * 1000; // 5 secs
    static int id = 1000;

    static void receive(MulticastSocket mc, boolean datagramExpected, int id)
        throws IOException
    {
        byte[] ba = new byte[100];
        DatagramPacket p = new DatagramPacket(ba, ba.length);
        try {
            mc.receive(p);
            int recvId = Integer.parseInt(
                    new String(p.getData(), 0, p.getLength(), "UTF-8"));
            if (datagramExpected) {
                if (recvId != id)
                    throw new RuntimeException("Unexpected id, got " + recvId
                                               + ", expected: " + id);
                out.printf("Received message as expected, %s\n", p.getAddress());
            } else {
                throw new RuntimeException("Unexpected message received, "
                                           + p.getAddress());
            }
        } catch (SocketTimeoutException e) {
            if (datagramExpected)
                throw new RuntimeException("Expected message not received, "
                                            + e.getMessage());
            else
                out.printf("Message not received, as expected\n");
        }
    }

    static void test(InetAddress group1, InetAddress group2)
        throws IOException
    {
        try (MulticastSocket mc1 = new MulticastSocket();
             MulticastSocket mc2 = new MulticastSocket(mc1.getLocalPort());
             DatagramSocket ds = new DatagramSocket()) {
            final int port = mc1.getLocalPort();
            out.printf("Using port: %d\n", port);

            mc1.setSoTimeout(TIMEOUT);
            mc2.setSoTimeout(TIMEOUT);
            int nextId = id;
            byte[] msg = Integer.toString(nextId).getBytes("UTF-8");
            DatagramPacket p = new DatagramPacket(msg, msg.length);
            p.setAddress(group1);
            p.setPort(port);

            mc1.joinGroup(group1);
            out.printf("mc1 joined the MC group: %s\n", group1);
            mc2.joinGroup(group2);
            out.printf("mc2 joined the MC group: %s\n", group2);

            out.printf("Sending datagram to: %s/%d\n", group1, port);
            ds.send(p);

            // the packet should be received by mc1 only
            receive(mc1, true, nextId);
            receive(mc2, false, 0);

            nextId = ++id;
            msg = Integer.toString(nextId).getBytes("UTF-8");
            p = new DatagramPacket(msg, msg.length);
            p.setAddress(group2);
            p.setPort(port);

            out.printf("Sending datagram to: %s/%d\n", group2, port);
            ds.send(p);

            // the packet should be received by mc2 only
            receive(mc2, true, nextId);
            receive(mc1, false, 0);

            mc1.leaveGroup(group1);
            mc2.leaveGroup(group2);
        }
    }

    public static void main(String args[]) throws IOException {
        String os = System.getProperty("os.name");

        // Requires IP_MULTICAST_ALL on Linux (new since 2.6.31) so skip
        // on older kernels. Note that we skip on <= version 3 to keep the
        // parsing simple
        if (os.equals("Linux")) {
            String osversion = System.getProperty("os.version");
            String[] vers = osversion.split("\\.", 0);
            int major = Integer.parseInt(vers[0]);
            if (major < 3) {
                System.out.format("Kernel version is %s, test skipped%n", osversion);
                return;
            }
        }

        // multicast groups used for the test
        InetAddress ip4Group1 = InetAddress.getByName("224.7.8.9");
        InetAddress ip4Group2 = InetAddress.getByName("225.4.5.6");

        test(ip4Group1, ip4Group2);
    }
}
